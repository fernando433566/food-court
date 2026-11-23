package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * Runs one {@link AppTask} at a time on an accessibility service: opens the official app,
 * steps the task on the main thread, captures on-screen QR codes and enforces the deadline.
 */
final class TaskRunner {
    interface Done { void finished(String answer, String unavailable); }

    private static final long BRING_FORWARD_AFTER_MS = 1500;
    private final AccessibilityService service;
    private final Handler main;
    private Run current;

    TaskRunner(AccessibilityService service, Handler main) {
        this.service = service;
        this.main = main;
    }

    boolean isBusy() { return current != null; }

    /** Starts {@code task}; {@code done} is called once on the main thread. Must be called on the main thread. */
    void start(AppTask task, long deadline, Done done) {
        Run run = new Run(task, deadline, done);
        current = run;
        String early = task.before(window(task.packageName()));
        if (early != null) {
            finish(run, early);
            return;
        }
        try {
            service.startActivity(task.launchIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | NullPointerException unavailable) {
            finish(run, task.timeout());
            return;
        }
        main.postDelayed(() -> tick(run), task.firstStepDelayMs());
        if (task.bringsAppForward()) main.postDelayed(() -> bringForward(run), BRING_FORWARD_AFTER_MS);
    }

    /** Ends the running task with an empty answer and the given reason (may be null). */
    void cancel(String reason) {
        Run run = current;
        if (run == null) return;
        current = null;
        run.done.finished("", reason);
    }

    private void tick(Run run) {
        if (current != run) return;
        if (SystemClock.elapsedRealtime() >= run.deadline) {
            finish(run, run.task.timeout());
            return;
        }
        if (!run.capturing) {
            AccessibilityNodeInfo root = window(run.task.packageName());
            if (root != null) {
                String answer = run.task.step(service, root);
                if (answer != null) {
                    finish(run, answer);
                    return;
                }
                Rect area = run.task.takeCapture();
                if (area != null) capture(run, area);
            }
        }
        main.postDelayed(() -> tick(run), run.task.stepIntervalMs());
    }

    /** The official app's window: the active one, or a focused window of that package. */
    private AccessibilityNodeInfo window(String packageName) {
        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (AppTask.isApp(root, packageName)) return root;
        for (AccessibilityWindowInfo window : service.getWindows()) {
            if (!window.isFocused()) continue;
            AccessibilityNodeInfo candidate = window.getRoot();
            if (AppTask.isApp(candidate, packageName)) return candidate;
        }
        return null;
    }

    private void bringForward(Run run) {
        if (current != run || window(run.task.packageName()) != null) return;
        try {
            service.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(run.task.packageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException unavailable) {
            // The deep link may still open; the deadline ends the task otherwise.
        }
    }

    private void capture(Run run, Rect area) {
        if (Build.VERSION.SDK_INT < 30) {
            deliverQr(run, "");
            return;
        }
        run.capturing = true;
        service.takeScreenshot(Display.DEFAULT_DISPLAY, main::post, new AccessibilityService.TakeScreenshotCallback() {
            @Override public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                AccessibilityNodeInfo root = window(run.task.packageName());
                if (current != run || root == null || !run.task.captureStillValid(root, area)) {
                    screenshot.getHardwareBuffer().close();
                    run.capturing = false;
                    return;
                }
                new Thread(() -> {
                    String code = OnScreenQr.decode(screenshot, area);
                    main.post(() -> {
                        run.capturing = false;
                        deliverQr(run, code);
                    });
                }, "food-court-qr-capture").start();
            }

            @Override public void onFailure(int error) {
                run.capturing = false;
            }
        });
    }

    private void deliverQr(Run run, String code) {
        if (current != run) return;
        String answer = run.task.onQr(code);
        if (answer != null) finish(run, answer);
    }

    private void finish(Run run, String answer) {
        if (current == run) current = null;
        run.done.finished(answer, run.task.unavailableMessage());
    }

    private static final class Run {
        final AppTask task;
        final long deadline;
        final Done done;
        boolean capturing;

        Run(AppTask task, long deadline, Done done) {
            this.task = task;
            this.deadline = deadline;
            this.done = done;
        }
    }
}
