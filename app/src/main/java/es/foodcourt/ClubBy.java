package es.foodcourt;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Club·by groups VIPS, Foster’s Hollywood, Ginos and Fridays under one loyalty app. */
final class ClubBy implements Franchise {
    static final String PACKAGE = "com.clubvips.app";

    public String id() { return "club-by-es"; }
    public String name() { return "Club·by"; }
    public String program() { return "VIPS · Foster’s · Ginos · Fridays"; }
    public int accent() { return 0xFF152C4E; }
    public int onAccent() { return 0xFFE4F7FF; }
    public String officialPackage() { return PACKAGE; }
    public String officialWebsite() { return "https://www.clubby.es/"; }
    public String officialUri(Section section) { return officialWebsite() + "#promociones"; }
    public int demoStartingPoints() { return 0; }
    public List<Benefit> rewards() { return Collections.emptyList(); }
    public List<Benefit> deals() { return Collections.emptyList(); }

    /**
     * Opens the app through its verified link: Android 11+ hides other apps' launcher intents
     * from Food Court, but an explicit VIEW intent to a link Club·by owns always resolves.
     */
    static Intent appIntent() {
        return new Intent(Intent.ACTION_VIEW, Uri.parse("https://club-by.onelink.me"))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(PACKAGE);
    }

    /** Links Club·by accepts outside its camera: clubby://… and https://club-by.onelink.me/…. */
    static boolean isAppLink(Uri link) {
        String scheme = link.getScheme() == null ? "" : link.getScheme().toLowerCase(Locale.ROOT);
        String host = link.getHost() == null ? "" : link.getHost().toLowerCase(Locale.ROOT);
        return "clubby".equals(scheme) || ("https".equals(scheme) && "club-by.onelink.me".equals(host));
    }

    /** Opens a scanned QR in Club·by when it is one of its links; otherwise explains why not. */
    static String openScannedLink(Context context, String value) {
        String text = value.trim();
        Uri link = Uri.parse(text);
        if (text.isEmpty() || text.length() > 2000 || !isAppLink(link)) {
            String shown = text.length() > 160 ? text.substring(0, 160) + "…" : text;
            return "Club·by solo acepta este QR desde su cámara. Contenido del QR: " + shown;
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, link).setPackage(PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "Enlace abierto en Club·by del emisor.";
        } catch (android.content.ActivityNotFoundException unavailable) {
            return "Instala Club·by e inicia sesión en el emisor.";
        }
    }
}
