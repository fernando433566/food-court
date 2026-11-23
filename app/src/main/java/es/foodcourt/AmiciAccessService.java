package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Local Amici access for the phone that has Amici itself: syncs the level and coupons, reads a
 * coupon code or opens the ticket camera, using the same tasks the sender runs for receivers.
 * Results come back to {@link AmiciActivity} as intent extras.
 *
 * <p>The class name is kept because Android stores enabled accessibility services by name.
 */
public final class AmiciAccessService extends AccessibilityService {
    private static final long BUDGET_MS = 15000;
    private static AmiciAccessService connected;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TaskRunner runner;

    static boolean available() { return connected != null; }

    /** Opens the ticket camera ({@code scan}) or syncs the level and coupons. */
    static void start(boolean scan, Franchise.Section section) {
        if (connected == null) return;
        if (scan) connected.run(new AmiciScannerTask(), null);
        else connected.run(new AmiciBenefitsTask(null), answer -> connected.returnSync(answer, section));
    }

    static void readCoupon(Franchise.Section section, String title) {
        if (connected == null) return;
        connected.run(new AmiciBenefitsTask(title), answer -> connected.returnCoupon(answer, section));
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        runner = new TaskRunner(this, main);
    }

    private interface Result { void accept(String answer); }

    private void run(AppTask task, Result onResult) {
        runner.cancel(null);
        runner.start(task, SystemClock.elapsedRealtime() + BUDGET_MS, (answer, unavailable) -> {
            if (onResult != null) onResult.accept(answer);
        });
    }

    private void returnSync(String answer, Franchise.Section section) {
        boolean loaded = false;
        try {
            org.json.JSONObject json = new org.json.JSONObject(answer);
            if (json.optString("error").isEmpty()) {
                AmiciBenefits.fromRemote(json).save(this);
                loaded = !json.optString("level").isEmpty();
            }
        } catch (org.json.JSONException incomplete) {
            loaded = false;
        }
        startActivity(backToWallet(section).putExtra(AmiciActivity.SYNC_RESULT, loaded));
    }

    private void returnCoupon(String answer, Franchise.Section section) {
        startActivity(backToWallet(section).putExtra(AmiciActivity.COUPON_REQUESTED, true)
            .putExtra(AmiciActivity.COUPON_DETAILS, answer));
    }

    private Intent backToWallet(Franchise.Section section) {
        return new Intent(this, AmiciActivity.class).putExtra(AmiciActivity.SECTION, section.name())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() {
        if (runner != null) runner.cancel(null);
    }

    @Override public void onDestroy() {
        onInterrupt();
        if (connected == this) connected = null;
        super.onDestroy();
    }
}
