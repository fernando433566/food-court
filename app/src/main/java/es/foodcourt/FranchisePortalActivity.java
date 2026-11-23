package es.foodcourt;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

/** In-app browser limited to a brand's official website (brands without app integration). */
public final class FranchisePortalActivity extends Activity {
    static final String SECTION = "es.foodcourt.portal_section";
    static final String PUBLIC_URL = "es.foodcourt.portal_public_url";
    static final String BRAND = "es.foodcourt.portal_brand";
    private Franchise franchise;
    private String officialHost;
    private WebView web;
    private boolean openKfcQr;

    @Override @SuppressWarnings("SetJavaScriptEnabled")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        franchise = Franchises.byId(getIntent().getStringExtra(BRAND));
        if (franchise == null) franchise = new BurgerKing();
        officialHost = Uri.parse(franchise.officialWebsite()).getHost();
        WalletTheme theme = new WalletTheme(this);
        theme.apply(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(theme.paper);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            }
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setPadding(dp(12), dp(6), dp(12), dp(6));
        layout.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        addButton(toolbar, "Cartera", theme, this::finish);
        if (!(franchise instanceof TacoBell) && !(franchise instanceof ClubBy)) {
            addButton(toolbar, franchise instanceof Kfc ? "Mi QR" : "Código", theme, () -> open(Franchise.Section.POINTS));
            addButton(toolbar, franchise instanceof Kfc ? "Club" : "Premios", theme,
                () -> open(Franchise.Section.REWARDS));
            addButton(toolbar, franchise instanceof Kfc ? "Cupones" : "Ofertas", theme,
                () -> open(Franchise.Section.DEALS));
        }

        web = new WebView(this);
        web.setBackgroundColor(theme.surface);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (openKfcQr && isOfficial(Uri.parse(url))) openVisibleKfcQr(12);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (isOfficial(url)) return false;
                if (request.isForMainFrame() && "https".equalsIgnoreCase(url.getScheme())
                        && request.hasGesture()) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, url));
                    } catch (android.content.ActivityNotFoundException ignored) {
                        // Keep the official page available when no external handler exists.
                    }
                }
                return true;
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(layout);
        if (state == null || web.restoreState(state) == null) {
            String requested = getIntent().getStringExtra(SECTION);
            String address = getIntent().getStringExtra(PUBLIC_URL);
            if (franchise instanceof ClubBy && address != null && ClubByCatalog.isTrusted(address)) {
                web.loadUrl(address);
            } else {
                open(requested == null ? Franchise.Section.POINTS : Franchise.Section.valueOf(requested));
            }
        }
    }

    private boolean isOfficial(Uri url) {
        if (franchise instanceof ClubBy) return ClubByCatalog.isTrusted(url.toString());
        return "https".equalsIgnoreCase(url.getScheme())
            && (officialHost.equalsIgnoreCase(url.getHost())
                || officialHost.replaceFirst("^www\\.", "").equalsIgnoreCase(url.getHost())
                || ("www." + officialHost).equalsIgnoreCase(url.getHost()))
            && (url.getPort() == -1 || url.getPort() == 443);
    }

    private void open(Franchise.Section section) {
        openKfcQr = franchise instanceof Kfc && section == Franchise.Section.POINTS;
        web.loadUrl(franchise.officialUri(section));
    }

    private void openVisibleKfcQr(int attempts) {
        if (!openKfcQr || attempts == 0 || web.getUrl() == null
                || !isOfficial(Uri.parse(web.getUrl()))) return;
        web.evaluateJavascript(
            "(() => { const links = document.querySelectorAll('a,button');"
            + " for (const link of links) {"
            + " if (link.textContent.trim() === 'Mi QR' && link.getClientRects().length) {"
            + " link.click(); return true; } } return false; })()",
            result -> {
                if ("true".equals(result)) openKfcQr = false;
                else if (openKfcQr) web.postDelayed(() -> openVisibleKfcQr(attempts - 1), 400);
            });
    }

    private void addButton(LinearLayout row, String label, WalletTheme theme, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(theme.ink);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinHeight(dp(44));
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setStateListAnimator(null);
        button.setBackground(WalletTheme.button(theme.secondary, dp(14)));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
        size.setMarginEnd(dp(3));
        row.addView(button, size);
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        web.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        openKfcQr = false;
        ((LinearLayout) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
