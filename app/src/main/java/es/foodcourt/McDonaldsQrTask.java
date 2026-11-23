package es.foodcourt;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the loyalty code that MyMcDonald’s shows («Text code, M 1 2 3 4 5») for a receiver. */
final class McDonaldsQrTask extends AppTask {
    private static final McDonalds FRANCHISE = new McDonalds();
    /** «M» followed by 5 to 8 digits, possibly spaced, inside the code's content description. */
    private static final Pattern CODE = Pattern.compile(
        "(?:^|[,.:]\\s*)(M(?:[\\s\\p{Z}]*[0-9]){5,8})(?![\\s\\p{Z}]*[0-9])", Pattern.CASE_INSENSITIVE);
    private String lastVisible = "";
    private boolean pressedCollect, openedLoyaltyTab;

    @Override String packageName() { return FRANCHISE.officialPackage(); }

    @Override Intent launchIntent() {
        return view(FRANCHISE.officialUri(Franchise.Section.POINTS), packageName())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override long firstStepDelayMs() { return 450; }

    @Override long stepIntervalMs() { return 350; }

    @Override boolean bringsAppForward() { return true; }

    @Override String before(AccessibilityNodeInfo root) {
        String code = visibleCode(root);
        return code.isEmpty() ? null : code;
    }

    /** Answers once the same code is read twice in a row, so a code still loading is not sent. */
    @Override String step(AccessibilityService service, AccessibilityNodeInfo root) {
        String code = visibleCode(root);
        if (!code.isEmpty() && code.equals(lastVisible)) return code;
        if (code.isEmpty() && !pressedCollect) {
            List<AccessibilityNodeInfo> nodes = collect(root);
            pressedCollect = clickLabel(nodes, "Collect points", "Acumular puntos", "Sumar puntos");
            if (!pressedCollect && !openedLoyaltyTab) {
                openedLoyaltyTab = clickLabel(nodes, "MyM", "MyMcDonald's", "MyMcDonald’s");
            }
        }
        lastVisible = code;
        return null;
    }

    @Override String timeout() { return lastVisible; }

    private String visibleCode(AccessibilityNodeInfo root) {
        if (!isApp(root, packageName())) return "";
        for (AccessibilityNodeInfo node : collect(root)) {
            CharSequence description = node.getContentDescription();
            if (description == null) continue;
            String label = description.toString();
            if (!label.contains("Text code") && !label.contains("Código")) continue;
            Matcher match = CODE.matcher(label);
            if (match.find()) return match.group(1).replaceAll("[\\s\\p{Z}]", "").toUpperCase(Locale.ROOT);
        }
        return "";
    }
}
