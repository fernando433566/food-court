package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Opens Amici's ticket camera (Añadir visita → Escanear ticket) so a QR can be shown to it. */
final class AmiciScannerTask extends AppTask {
    private static final Amici FRANCHISE = new Amici();
    private boolean pressedScan, openedLoyalty;

    @Override String packageName() { return FRANCHISE.officialPackage(); }

    @Override Intent launchIntent() { return view(FRANCHISE.officialUri(Franchise.Section.POINTS), packageName()); }

    @Override long firstStepDelayMs() { return 650; }

    @Override long stepIntervalMs() { return 650; }

    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = collect(root);
        if (pressedScan && byId(nodes, "android:id/alertTitle") != null) {
            return "El lector oficial mostró un aviso. Comprueba la cámara de Amici en el emisor.";
        }
        if (pressedScan && (byId(nodes, "zxing_barcode_scanner") != null || byId(nodes, "zxing_barcode_surface") != null)) {
            return "Lector de Amici abierto. Acerca el QR a la cámara del emisor.";
        }
        if (!pressedScan) {
            pressedScan = click(byId(nodes, "scanTicketButton"));
            if (!pressedScan && !click(byId(nodes, "addVisitButton")) && !openedLoyalty) {
                openedLoyalty = click(byId(nodes, "loyalty_nav_graph"));
            }
        }
        return null;
    }

    @Override String timeout() {
        return "Amici no confirmó que la cámara estuviera lista. Comprueba la sesión y los permisos del emisor.";
    }
}
