package es.foodcourt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClubByActivity extends Activity {
    static final String SECTION = "es.foodcourt.club_by_section";
    private static final ClubBy CLUB = new ClubBy();
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final Map<ClubByCatalog.Brand, List<ClubByCatalog.Offer>> catalogs =
        new EnumMap<>(ClubByCatalog.Brand.class);
    private final List<ClubByCatalog.Brand> failed = new ArrayList<>();
    private WalletTheme theme;
    private SenderClient sender;
    enum Page {
        POINTS("Mi tarjeta"), READER("Lector QR"), REWARDS("Promociones"), LEISURE("Ocio");

        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private Page section;
    private ClubByCatalog.Brand brand = ClubByCatalog.Brand.ALL;
    private LinearLayout page;
    private LinearLayout results;
    private boolean requested;
    private int remaining;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = new WalletTheme(this);
        theme.apply(this);
        sender = new SenderClient(this);
        String page = getIntent().getStringExtra(SECTION);
        section = Page.valueOf(state == null
            ? (page == null ? Page.POINTS.name() : page)
            : state.getString(SECTION, Page.POINTS.name()));
        if (state != null) brand = ClubByCatalog.Brand.valueOf(state.getString("brand", "ALL"));
        render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(SECTION, section.name());
        state.putString("brand", brand.name());
        super.onSaveInstanceState(state);
    }

    private void render() {
        layout(section.title);
        LinearLayout sections = new LinearLayout(this);
        page.addView(sections);
        tab(sections, "Tarjeta", Page.POINTS);
        tab(sections, "Lector QR", Page.READER);
        tab(sections, "Promociones", Page.REWARDS);
        tab(sections, "Ocio", Page.LEISURE);
        switch (section) {
            case POINTS:
                showEmitterAction("Solicitar QR al emisor", CodeExchange.Operation.CLUB_POINTS);
                break;
            case READER:
                label("Escanea aquí el QR (mesa, carta, Pay&Go o promoción) y el emisor lo abre en Club·by. "
                    + "Si Club·by no acepta ese QR fuera de su cámara, abre su lector y preséntalo a la cámara del emisor.",
                    16, theme.muted);
                linkStatus = label("", 16, theme.muted);
                makeAction("Escanear QR y enviarlo al emisor", true, this::scanLink);
                showEmitterAction("Abrir lector en el emisor", CodeExchange.Operation.CLUB_SCANNER);
                break;
            case LEISURE:
                label("Consulta los descuentos de socios y colaboradores en «Mis descuentos de ocio» de Club·by.",
                    16, theme.muted);
                showEmitterAction("Abrir ocio en el emisor", CodeExchange.Operation.CLUB_LEISURE);
                break;
            case REWARDS:
                showMyPromotions();
                label("Promociones para todos", 20, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
                label("Catálogo público de las webs de VIPS, Foster’s, Ginos y Fridays.", 14, theme.muted);
                showFilters();
                break;
        }
    }

    private void tab(LinearLayout row, String title, Page target) {
        Button view = makeButton(title, target == section, () -> {
            section = target;
            render();
        });
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
        size.setMarginEnd(dp(4));
        row.addView(view, size);
    }

    private void showFilters() {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        strip.addView(row);
        page.addView(strip);
        for (ClubByCatalog.Brand option : ClubByCatalog.Brand.values()) {
            Button filter = makeButton(option.label, brand == option, () -> {
                brand = option;
                for (int i = 0; i < row.getChildCount(); i++) {
                    style((Button) row.getChildAt(i), ClubByCatalog.Brand.values()[i] == brand);
                }
                renderCatalog();
            });
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginEnd(dp(5));
            row.addView(filter, size);
        }
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);
        renderCatalog();
        if (!requested) load();
    }

    private void load() {
        requested = true;
        remaining = ClubByCatalog.Brand.values().length - 1;
        failed.clear();
        for (ClubByCatalog.Brand source : ClubByCatalog.Brand.values()) {
            if (source == ClubByCatalog.Brand.ALL) continue;
            worker.submit(() -> {
                List<ClubByCatalog.Offer> offers;
                try {
                    offers = ClubByCatalog.read(source);
                } catch (java.io.IOException | RuntimeException unavailable) {
                    offers = null;
                }
                List<ClubByCatalog.Offer> result = offers;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (result == null) failed.add(source);
                    else catalogs.put(source, result);
                    remaining--;
                    if (results != null) renderCatalog();
                });
            });
        }
        renderCatalog();
    }

    private void renderCatalog() {
        results.removeAllViews();
        Map<String, ClubByCatalog.Offer> visible = new LinkedHashMap<>();
        for (List<ClubByCatalog.Offer> catalog : catalogs.values()) {
            for (ClubByCatalog.Offer offer : catalog) {
                if (brand == ClubByCatalog.Brand.ALL ? offer.collective
                        : !offer.collective && offer.source == brand) {
                    String key = offer.key();
                    if (offer.collective) key = "club|" + offer.title + "|" + offer.end;
                    ClubByCatalog.Offer other = visible.get(key);
                    if (other == null || (other.code.isEmpty() && !offer.code.isEmpty())) visible.put(key, offer);
                }
            }
        }
        // The «Promociones para todos» heading above already names the ALL filter.
        if (brand != ClubByCatalog.Brand.ALL) {
            addLabel(results, brand.label, 18, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        }
        for (ClubByCatalog.Offer offer : visible.values()) {
            addButton(results, (offer.collective ? "Club·by · " : "") + offer.title, false, () -> showOffer(offer));
        }
        if (remaining > 0) addLabel(results, "Cargando promociones…", 15, theme.muted);
        else if (brand == ClubByCatalog.Brand.ALL ? !failed.isEmpty() : failed.contains(brand)) {
            addLabel(results, "No se pudieron consultar todos los catálogos de esta sección.", 15, theme.muted);
        } else if (visible.isEmpty()) {
            addLabel(results, "No se encontraron promociones públicas vigentes.", 15, theme.muted);
        }
        if (remaining == 0 && !failed.isEmpty()) {
            addButton(results, "Reintentar actualización", false, this::load);
        }
    }

    private void showOffer(ClubByCatalog.Offer offer) {
        layout(offer.title);
        label(offer.collective ? "Club·by · Colectiva" : offer.source.label, 18, theme.muted);
        if (!offer.channel.isEmpty()) label(offer.channel, 15, theme.muted);
        String validity = offer.end.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
            ? "Disponible hasta " + offer.end.substring(8) + "/" + offer.end.substring(5, 7)
                + "/" + offer.end.substring(0, 4)
            : "";
        if (!validity.isEmpty()) label(validity, 15, theme.muted);
        if (!offer.description.isEmpty()) label(offer.description, 16, theme.ink);
        if (!offer.code.isEmpty()) {
            label(offer.code, 26, theme.ink).setTextIsSelectable(true);
            button("Copiar código", true, () -> {
                Ui.copy(this, "Cupón Club·by", offer.code);
                toast("Código copiado.");
            });
            button("Guardar en Mis códigos", false, () -> {
                DiscountWallet.save(this, CLUB.name(), offer.title, offer.code, "Texto", validity);
                toast("Código guardado.");
            });
        }
        button("Ver ficha oficial", true, () -> startActivity(new Intent(this, FranchisePortalActivity.class)
            .putExtra(FranchisePortalActivity.BRAND, CLUB.id())
            .putExtra(FranchisePortalActivity.PUBLIC_URL, offer.url)));
        if (!offer.terms.isEmpty()) label(offer.terms, 15, theme.muted);
        button("Volver a promociones", false, this::render);
    }

    private CodeExchange.QrRequest qrRequest;

    // ---- «Mis promociones» of Club·by on the sender

    private android.content.SharedPreferences promotionStore() {
        return getSharedPreferences("clubby_promotions", MODE_PRIVATE);
    }

    private void showMyPromotions() {
        label("Tus promociones", 20, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD);
        String updated = promotionStore().getString("updated", "");
        TextView status = label(updated.isEmpty() ? "Pide al emisor tus promociones de «Mis promociones» en Club·by."
            : "Actualizado " + updated + " desde el emisor.", 14, theme.muted);
        try {
            org.json.JSONArray list = new org.json.JSONArray(promotionStore().getString("list", "[]"));
            for (int i = 0; i < list.length(); i++) {
                org.json.JSONObject promotion = list.getJSONObject(i);
                String title = promotion.optString("title");
                StringBuilder meta = new StringBuilder(promotion.optString("level").isEmpty()
                    ? "Personal" : "Nivel " + promotion.optString("level"));
                for (String field : new String[] { "until", "location" }) {
                    if (!promotion.optString(field).isEmpty()) meta.append(" · ").append(promotion.optString(field));
                }
                makeAction(title, false, () -> requestPromotionQr(title, status));
                label(meta.toString(), 13, theme.muted);
            }
        } catch (org.json.JSONException damaged) {
            promotionStore().edit().clear().apply();
        }
        makeAction("Actualizar desde el emisor", true, () -> refreshPromotions(status));
        android.widget.EditText code = new android.widget.EditText(this);
        code.setSingleLine();
        code.setHint("Código de promoción");
        code.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setTextColor(theme.ink);
        code.setHintTextColor(theme.muted);
        page.addView(code);
        makeAction("Añadir código en el emisor", false, () -> {
            String value = code.getText().toString().trim();
            if (value.isEmpty() || value.length() > 60) {
                status.setText("Escribe el código de la promoción.");
                return;
            }
            relay(CodeExchange.Operation.CLUB_PROMO_ADD, value, status, answer -> {
                status.setText(answer.optString("message", "Código enviado."));
                code.setText("");
            });
        });
    }

    private void refreshPromotions(TextView status) {
        status.setText("Leyendo «Mis promociones» en el emisor…");
        relay(CodeExchange.Operation.CLUB_PROMOS, "", status, answer -> {
            org.json.JSONArray list = answer.optJSONArray("promotions");
            if (list == null) list = new org.json.JSONArray();
            promotionStore().edit().putString("list", list.toString())
                .putString("updated", new java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ROOT)
                    .format(new java.util.Date())).apply();
            render();
        });
    }

    private void requestPromotionQr(String title, TextView status) {
        status.setText("Abriendo «" + title + "» en el emisor…");
        relay(CodeExchange.Operation.CLUB_PROMO_QR, title, status, this::showPromotion);
    }

    private void showPromotion(org.json.JSONObject answer) {
        layout(answer.optString("title"));
        String code = answer.optString("qr");
        if (!code.isEmpty()) {
            android.widget.ImageView qr = new android.widget.ImageView(this);
            qr.setContentDescription("QR de la promoción");
            qr.setImageBitmap(QrCode.render(code));
            page.addView(qr, new LinearLayout.LayoutParams(-1, dp(260)));
            label("Enséñalo en el restaurante igual que en Club·by.", 14, theme.muted);
        } else {
            label("Esta promoción no muestra un QR en Club·by.", 15, theme.muted);
        }
        org.json.JSONArray details = answer.optJSONArray("details");
        for (int i = 0; details != null && i < details.length(); i++) {
            String line = details.optString(i);
            if (line.matches("Términos y condiciones de uso|Canjea tu promoción: paso a paso|Ver condiciones")) continue;
            label(line, line.length() > 60 ? 15 : 16, line.length() > 60 ? theme.muted : theme.ink);
        }
        button("Volver a promociones", false, this::render);
    }

    /** Sends a request whose answer is JSON; errors are shown in {@code status}. */
    private void relay(CodeExchange.Operation operation, String input, TextView status,
                       SenderClient.JsonAnswer onAnswer) {
        if (!sender.isPaired()) {
            openPairing();
            return;
        }
        sender.sendJson(operation, input, onAnswer, message -> {
            if (status.isAttachedToWindow()) status.setText(message);
        });
    }

    private TextView linkStatus;

    private void makeAction(String title, boolean primary, Runnable action) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(8);
        page.addView(makeButton(title, primary, action), size);
    }

    private void scanLink() {
        Ui.scan(this, "Escanea el QR para Club·by", true);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        com.google.zxing.integration.android.IntentResult result =
            com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (result.getContents() == null || linkStatus == null) return;
        sendLink(result.getContents(), linkStatus);
    }

    private void sendLink(String value, TextView status) {
        if (!sender.isPaired()) {
            openPairing();
            return;
        }
        status.setText("Enviando el QR al emisor…");
        sender.send(CodeExchange.Operation.CLUB_LINK, value, status::setText, status::setText);
    }

    private void showEmitterAction(String title, CodeExchange.Operation operation) {
        TextView status = label("", 16, theme.muted);
        android.widget.ImageView qr = new android.widget.ImageView(this);
        if (operation == CodeExchange.Operation.CLUB_POINTS) {
            qr.setContentDescription("QR de socio de Club·by recibido del emisor");
            qr.setVisibility(android.view.View.GONE);
            page.addView(qr, new LinearLayout.LayoutParams(-1, dp(240)));
        }
        Button request = makeButton(title, true, () -> { });
        page.addView(request);
        request.setOnClickListener(view -> {
            if (qrRequest != null) {
                cancelQr();
                request.setText(title);
                status.setText("Consulta cancelada.");
                return;
            }
            if (!sender.isPaired()) {
                openPairing();
                return;
            }
            CodeExchange.QrRequest reading = operation == CodeExchange.Operation.CLUB_POINTS
                ? new CodeExchange.QrRequest() : null;
            qrRequest = reading;
            request.setEnabled(reading != null);
            if (reading != null) request.setText("Cancelar");
            status.setText("Consultando al emisor…");
            qr.setImageDrawable(null);
            qr.setVisibility(android.view.View.GONE);
            SenderClient.Answer onAnswer = result -> {
                if (!finishRequest(reading, request, title) || !status.isAttachedToWindow()) return;
                if (result.isEmpty()) {
                    status.setText("No se encontró la opción o el QR. Abre Club·by con sesión iniciada en el emisor.");
                } else if (operation == CodeExchange.Operation.CLUB_POINTS) {
                    qr.setImageBitmap(QrCode.render(result));
                    qr.setVisibility(android.view.View.VISIBLE);
                    status.setText("QR de socio recibido del emisor.");
                } else {
                    status.setText(result);
                }
            };
            SenderClient.Failure onFailure = message -> {
                if (finishRequest(reading, request, title) && status.isAttachedToWindow()) status.setText(message);
            };
            if (reading != null) sender.sendQr(operation, reading, onAnswer, onFailure);
            else sender.send(operation, "", onAnswer, onFailure);
        });
        button("Configurar emisor", false, this::openPairing);
    }

    /** Re-enables the button; false when the answer belongs to a cancelled QR request. */
    private boolean finishRequest(CodeExchange.QrRequest reading, Button request, String title) {
        if (reading != null) {
            if (qrRequest != reading) return false;
            qrRequest = null;
            request.setText(title);
        }
        request.setEnabled(true);
        return true;
    }

    private void openPairing() {
        startActivity(new Intent(this, ExchangeActivity.class)
            .putExtra(ExchangeActivity.PAIR_ONLY, true));
    }

    private void cancelQr() {
        if (qrRequest != null) qrRequest.cancel();
        qrRequest = null;
    }

    private void layout(String title) {
        cancelQr();
        results = null;
        page = Ui.page(this, theme, 18);
        button("‹  Cartera", false, this::finish);
        label(CLUB.name(), 17, theme.muted);
        label(title, 33, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    }

    private Button makeButton(String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setStateListAnimator(null);
        style(button, primary);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void style(Button button, boolean primary) {
        button.setTextColor(primary ? (theme.oled ? theme.onAccent : CLUB.onAccent()) : theme.ink);
        button.setBackground(WalletTheme.button(primary ? (theme.oled ? theme.accent : CLUB.accent())
            : theme.secondary, dp(15)));
    }

    private void button(String title, boolean primary, Runnable action) {
        addButton(page, title, primary, action);
    }

    private void addButton(LinearLayout container, String title, boolean primary, Runnable action) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(7);
        container.addView(makeButton(title, primary, action), size);
    }

    private TextView label(String text, int size, int color) {
        return addLabel(page, text, size, color);
    }

    private TextView addLabel(LinearLayout container, String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(6));
        container.addView(view);
        return view;
    }

    private void toast(String message) {
        Ui.toast(this, message);
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }

    @Override protected void onDestroy() {
        cancelQr();
        worker.shutdownNow();
        super.onDestroy();
    }
}
