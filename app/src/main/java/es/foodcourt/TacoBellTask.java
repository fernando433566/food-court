package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * In Taco Bell: captures the expanded member QR, or types a ticket code into
 * «Introducir manualmente» and reports the app's answer.
 */
final class TacoBellTask extends AppTask {
    private static final TacoBell FRANCHISE = new TacoBell();
    private final String ticket;
    private boolean openedHome, typed, sent;

    /** @param ticket null to capture the member QR, or the ticket code to register. */
    TacoBellTask(String ticket) {
        this.ticket = ticket;
    }

    static boolean isValidTicket(String code) {
        return !code.isEmpty() && code.length() <= 300 && code.equals(code.trim())
            && !code.matches("(?s).*\\p{Cntrl}.*");
    }

    @Override String packageName() { return FRANCHISE.officialPackage(); }

    @Override Intent launchIntent() { return view("https://tacobell.es/qr", packageName()); }

    @Override long firstStepDelayMs() { return 700; }

    /** Capturing the QR needs screenshots (Android 11). */
    @Override String before(AccessibilityNodeInfo root) {
        return ticket == null && Build.VERSION.SDK_INT < 30 ? "" : null;
    }

    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = collect(root);
        if (ticket != null) return register(nodes);
        AccessibilityNodeInfo qr = expandedQr(root);
        if (qr != null) requestCapture(captureArea(qr));
        else openQr(nodes);
        return null;
    }

    @Override String onQr(String code) { return code.isEmpty() ? null : code; }

    @Override boolean captureStillValid(AccessibilityNodeInfo root, Rect area) {
        AccessibilityNodeInfo qr = expandedQr(root);
        return qr != null && captureArea(qr).equals(area);
    }

    @Override String timeout() {
        if (sent) return "Ticket enviado. Taco Bell no confirmó la acreditación.";
        return ticket != null ? "No se pudo registrar. Comprueba la sesión de Taco Bell en el emisor." : "";
    }

    private static Rect captureArea(AccessibilityNodeInfo qr) {
        Rect area = bounds(qr);
        area.inset(-14, -14); // The quiet zone around the QR helps decoding.
        return area;
    }

    private void openQr(List<AccessibilityNodeInfo> nodes) {
        if (click(byId(nodes, "llContainerQr"))) return;
        if (!openedHome) openedHome = click(byId(nodes, "rlHome"));
    }

    /** The full-size QR; the home tile contains a miniature with the same id, so that subtree is skipped. */
    private AccessibilityNodeInfo expandedQr(AccessibilityNodeInfo root) {
        String tile = packageName() + ":id/llContainerQr", image = packageName() + ":id/ivQr";
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            AccessibilityNodeInfo node = pending.removeFirst();
            String id = node.getViewIdResourceName();
            if (tile.equals(id)) continue;
            if (node.isVisibleToUser() && image.equals(id)) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) pending.add(child);
            }
        }
        return null;
    }

    private String register(List<AccessibilityNodeInfo> nodes) {
        if (sent) {
            String message = result(nodes);
            return message.isEmpty() ? null : message;
        }
        AccessibilityNodeInfo field = byId(nodes, "etCodigoTicket");
        if (field != null) {
            if (ticket.equals(text(field))) {
                if (click(byId(nodes, "btnAnyadir"))) sent = true;
            } else if (!typed) {
                typed = setText(field, ticket);
            }
            return null;
        }
        if (!click(byId(nodes, "btnIntroducirManualmente"))) openQr(nodes);
        return null;
    }

    /** Taco Bell's confirmation or error after sending a ticket, or empty while it is still working. */
    private static String result(List<AccessibilityNodeInfo> nodes) {
        for (AccessibilityNodeInfo node : nodes) {
            String text = text(node);
            String normalized = text.toLowerCase(Locale.ROOT);
            if (normalized.matches("(?s).*(ticket.*(no válido|ya (registrado|utilizado)|caducado)"
                    + "|código.*(incorrecto|no válido)|error al (registrar|añadir)).*")) return text;
            if (normalized.matches("(?s).*(puntos (añadidos|sumados|acreditados)"
                    + "|ticket (registrado|añadido) correctamente).*")) return text;
        }
        return "";
    }
}
