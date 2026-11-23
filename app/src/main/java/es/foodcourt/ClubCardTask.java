package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * In Club·by: captures the member card QR, opens the QR reader, or opens «Mis descuentos de ocio».
 */
final class ClubCardTask extends AppTask {
    enum Goal { MEMBER_QR, READER, LEISURE }

    /**
     * Whether a previous request left Club·by's camera reader open. It is closed before looking for
     * the card; static because each request creates a new task.
     */
    private static boolean readerLeftOpen;
    private final Goal goal;
    private boolean openedClubTab;

    ClubCardTask(Goal goal) {
        this.goal = goal;
    }

    @Override String packageName() { return ClubBy.PACKAGE; }

    @Override Intent launchIntent() {
        return ClubBy.appIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override long firstStepDelayMs() { return 650; }

    @Override long stepIntervalMs() { return 650; }

    @Override boolean bringsAppForward() { return true; }

    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = collect(root);
        if (goal == Goal.LEISURE) {
            if (clickLabel(nodes, "Mis descuentos de ocio")) return "Se ha pulsado «Mis descuentos de ocio» en el emisor.";
            if (!openedClubTab) openedClubTab = clickLabel(nodes, "Club-by");
            return null;
        }
        AccessibilityNodeInfo card = byId(nodes, "club-by-card");
        if (goal == Goal.MEMBER_QR && byLabel(nodes, "No registrado") != null) {
            return unavailable("La tarjeta de Club·by del emisor muestra «No registrado». Inicia sesión allí.");
        }
        if (goal == Goal.MEMBER_QR && card != null) {
            readerLeftOpen = false;
            if (Build.VERSION.SDK_INT < 30) return ""; // Capturing the QR needs screenshots (Android 11).
            requestCapture(bounds(card));
        } else if (goal == Goal.READER && (click(byId(nodes, "qr-scan-button")) || clickLabel(nodes, "Escanear código QR"))) {
            readerLeftOpen = true;
            return "Club·by recibió la orden de abrir el lector. Comprueba la cámara del emisor.";
        } else if (clickLabel(nodes, "Usar mi tarjeta Club·by", "Mi tarjeta", "Tarjeta") || clickLabel(nodes, "Club-by")) {
            readerLeftOpen = false;
        } else if (readerLeftOpen) {
            readerLeftOpen = false;
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        }
        return null;
    }

    @Override String onQr(String code) { return code.isEmpty() ? null : code; }

    @Override boolean captureStillValid(AccessibilityNodeInfo root, Rect area) {
        AccessibilityNodeInfo card = byId(collect(root), "club-by-card");
        return card != null && bounds(card).equals(area);
    }

    @Override String timeout() { return ""; }
}
