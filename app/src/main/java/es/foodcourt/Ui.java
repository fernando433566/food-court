package es.foodcourt;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

/** Layout pieces shared by the wallet's screens, which build their views in code. */
final class Ui {
    private Ui() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Replaces the activity's content with a scrolling vertical page and returns the page. */
    static LinearLayout page(Activity activity, WalletTheme theme, int topPaddingDp) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(theme.paper);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(activity, 22), dp(activity, topPaddingDp), dp(activity, 22), dp(activity, 24));
        scroll.addView(page);
        activity.setContentView(scroll);
        return page;
    }

    /** Copies text to the clipboard marked as sensitive, so Android does not preview it. */
    static void copy(Context context, String label, String value) {
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, value);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
        }
        context.getSystemService(android.content.ClipboardManager.class).setPrimaryClip(clip);
    }

    /** Opens the camera scanner; the result arrives in {@code onActivityResult} (IntentIntegrator). */
    static void scan(Activity activity, String prompt, boolean qrOnly) {
        com.google.zxing.integration.android.IntentIntegrator scanner =
            new com.google.zxing.integration.android.IntentIntegrator(activity);
        scanner.setBeepEnabled(false);
        scanner.setPrompt(prompt);
        scanner.setOrientationLocked(false);
        if (qrOnly) {
            scanner.setDesiredBarcodeFormats(java.util.Collections.singleton(
                com.google.zxing.integration.android.IntentIntegrator.QR_CODE));
        }
        scanner.initiateScan();
    }

    static void toast(Context context, String message) {
        Toast.makeText(context, message, message.length() > 40 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }
}
