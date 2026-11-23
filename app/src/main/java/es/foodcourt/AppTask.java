package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One job done by driving an official app through accessibility, step by step.
 *
 * <p>{@link TaskRunner} opens the app with {@link #launchIntent()}, then calls {@link #step} on
 * every tick with the app's window until it returns an answer or the time budget runs out
 * ({@link #timeout()}). A task that needs a QR drawn on screen calls {@link #requestCapture};
 * the runner takes a screenshot, decodes that area and hands the text to {@link #onQr}.
 *
 * <p>Answers are plain text for most operations. Tasks behind the JSON operations answer with
 * a JSON object and report failures with {@link #error}.
 */
abstract class AppTask {
    private Rect capture;
    private String unavailable;

    /** Package of the official app this task drives. */
    abstract String packageName();

    /** Opens the official app at the right screen. */
    abstract Intent launchIntent();

    /** Answers before opening the app (for example when the code is already visible), or null. */
    String before(AccessibilityNodeInfo root) { return null; }

    long firstStepDelayMs() { return 900; }

    long stepIntervalMs() { return 550; }

    /** Whether to open the app's launcher screen if the deep link left another app in front. */
    boolean bringsAppForward() { return false; }

    /** Advances one step on the app's window. Returns the answer when finished, or null to keep going. */
    abstract String step(AccessibilityService service, AccessibilityNodeInfo root);

    /** Answer when the time budget runs out. */
    abstract String timeout();

    /** Receives the QR decoded after {@link #requestCapture}; empty when unreadable. Null keeps going. */
    String onQr(String code) { return null; }

    /** Whether a screenshot of {@code area} still shows what was requested (the screen may have moved). */
    boolean captureStillValid(AccessibilityNodeInfo root, Rect area) { return true; }

    final void requestCapture(Rect area) { capture = area; }

    /** Area of the screen to decode as a QR, consumed once. */
    final Rect takeCapture() {
        Rect area = capture;
        capture = null;
        return area;
    }

    /**
     * Marks the request as impossible right now (for example «No registrado»). For member QR
     * requests the receiver shows this message; returns an empty answer for convenience.
     */
    final String unavailable(String message) {
        unavailable = message;
        return "";
    }

    final String unavailableMessage() { return unavailable; }

    // ---- JSON answers

    static String error(String message) {
        return json("error", message);
    }

    static String message(String text) {
        return json("message", text);
    }

    private static String json(String field, String value) {
        try {
            return new JSONObject().put(field, value).toString();
        } catch (JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ---- Accessibility helpers shared by every task

    static boolean isApp(AccessibilityNodeInfo root, String packageName) {
        return root != null && packageName.contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
    }

    /** Every visible node under {@code root}, in depth-first order. */
    static List<AccessibilityNodeInfo> collect(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        if (root != null) collect(root, all);
        return all;
    }

    private static void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> all) {
        if (node.isVisibleToUser()) all.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) collect(child, all);
        }
    }

    /**
     * Finds a node by resource id: «android:id/x» or «pkg:id/x» written in full, the short name
     * of a native view («x»), or the testID of a React Native view.
     */
    static AccessibilityNodeInfo byId(List<AccessibilityNodeInfo> nodes, String id) {
        for (AccessibilityNodeInfo node : nodes) {
            String name = node.getViewIdResourceName();
            if (name != null && (name.equals(id) || name.endsWith(":id/" + id))) return node;
        }
        return null;
    }

    /** First node whose text or content description equals one of {@code labels}, ignoring case. */
    static AccessibilityNodeInfo byLabel(List<AccessibilityNodeInfo> nodes, String... labels) {
        for (AccessibilityNodeInfo node : nodes) {
            for (String label : labels) {
                if (label.equalsIgnoreCase(String.valueOf(node.getText()))
                        || label.equalsIgnoreCase(String.valueOf(node.getContentDescription()))) return node;
            }
        }
        return null;
    }

    static boolean clickLabel(List<AccessibilityNodeInfo> nodes, String... labels) {
        return click(byLabel(nodes, labels));
    }

    /** Clicks the node or its nearest clickable ancestor. */
    static boolean click(AccessibilityNodeInfo node) {
        for (; node != null; node = node.getParent()) {
            if (node.isClickable() && node.isEnabled()
                    && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    /** Text the user would read, ignoring the hint some empty fields report as text. */
    static String text(AccessibilityNodeInfo node) {
        if (node == null || node.getText() == null) return "";
        if (android.os.Build.VERSION.SDK_INT >= 26 && node.isShowingHintText()) return "";
        return node.getText().toString().trim();
    }

    static boolean setText(AccessibilityNodeInfo node, String value) {
        Bundle data = new Bundle();
        data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, data);
    }

    /** The first EditText inside a container (some apps wrap the field in layouts with the id). */
    static AccessibilityNodeInfo editTextIn(AccessibilityNodeInfo container) {
        for (AccessibilityNodeInfo node : collect(container)) {
            if ("android.widget.EditText".contentEquals(node.getClassName() == null ? "" : node.getClassName())) {
                return node;
            }
        }
        return null;
    }

    static AccessibilityNodeInfo largestScrollable(List<AccessibilityNodeInfo> nodes) {
        AccessibilityNodeInfo best = null;
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isScrollable() && (best == null || bounds(node).height() > bounds(best).height())) best = node;
        }
        return best;
    }

    static Rect bounds(AccessibilityNodeInfo node) {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        return rect;
    }

    static Intent view(String uri, String packageName) {
        return new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).setPackage(packageName);
    }
}
