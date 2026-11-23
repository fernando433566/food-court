package es.foodcourt;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.List;

public final class AmiciActivity extends Activity {
    static final String SECTION = "es.foodcourt.amici_section";
    static final String COUPON_REQUESTED = "es.foodcourt.amici_coupon_requested";
    static final String COUPON_DETAILS = "es.foodcourt.amici_coupon_details";
    static final String SYNC_RESULT = "es.foodcourt.amici_sync_result";
    private static final int TICKET_IMAGE = 1372;
    private static final Amici FRANCHISE = new Amici();
    private WalletTheme theme;
    private LinearLayout page;
    private Franchise.Section section;
    private SenderClient sender;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sender = new SenderClient(this);
        theme = new WalletTheme(this);
        theme.apply(this);
        if (state != null) {
            section = Franchise.Section.valueOf(state.getString(SECTION, Franchise.Section.POINTS.name()));
        }
        renderIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        section = null;
        renderIntent(intent);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(SECTION, section.name());
        super.onSaveInstanceState(state);
    }

    private void renderIntent(Intent intent) {
        if (section == null) {
            String target = intent.getStringExtra(SECTION);
            section = target == null ? Franchise.Section.POINTS : Franchise.Section.valueOf(target);
        }
        render();
        if (intent.getBooleanExtra(COUPON_REQUESTED, false)) {
            String details = intent.getStringExtra(COUPON_DETAILS);
            intent.removeExtra(COUPON_REQUESTED);
            intent.removeExtra(COUPON_DETAILS);
            org.json.JSONObject coupon = null;
            try {
                if (details != null) coupon = new org.json.JSONObject(details);
            } catch (org.json.JSONException damaged) {
                coupon = null;
            }
            if (coupon == null || !coupon.optString("error").isEmpty()) {
                toast("Amici no mostró un código para esta oferta.");
            } else {
                showCoupon(coupon);
            }
        }
        if (intent.hasExtra(SYNC_RESULT)) {
            if (!intent.getBooleanExtra(SYNC_RESULT, false)) {
                toast("No se pudo leer el nivel. Abre Amici, inicia sesión y vuelve a actualizar.");
            }
            intent.removeExtra(SYNC_RESULT);
        }
    }

    private void render() {
        layout(section == Franchise.Section.LEISURE ? "Ocio"
            : section == Franchise.Section.REWARDS ? "Restaurante" : "Escanear ticket");
        AmiciBenefits data = AmiciBenefits.load(this);
        label(data.level.isEmpty() ? "Nivel Amici pendiente de sincronizar" : "Amici " + data.level,
            17, theme.muted);
        LinearLayout tabs = new LinearLayout(this);
        page.addView(tabs);
        tab(tabs, "Ticket", Franchise.Section.POINTS);
        tab(tabs, "Restaurante", Franchise.Section.REWARDS);
        tab(tabs, "Ocio", Franchise.Section.LEISURE);
        if (section == Franchise.Section.POINTS) {
            button("Registrar ticket en el emisor", true, () -> showManualTicket(null));
            button("Escanear ticket con Food Court", false, this::scan);
            button("Leer ticket desde una imagen", false, this::selectImage);
            button("Abrir lector de Amici en el emisor", false, () -> openEmitter(label("", 16, theme.muted)));
            if (AmiciAccessService.available()) button("Usar lector en este móvil", false, () -> openOfficial(true));
        } else {
            List<AmiciBenefits.Offer> offers = section == Franchise.Section.LEISURE ? data.leisure : data.restaurant;
            if (offers.isEmpty()) {
                label(section == Franchise.Section.LEISURE
                    ? "Actualiza desde Amici para ver las promociones de sus socios."
                    : "Actualiza desde Amici para ver tus cupones de La Tagliatella.", 16, theme.muted);
            }
            for (AmiciBenefits.Offer offer : offers) {
                label(offer.title, 20, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
                if (!offer.description.isEmpty()) label(offer.description, 15, theme.muted);
                button("Ver código · " + offer.title, false, () -> openCoupon(offer));
            }
            button("Abrir Amici para canjear", false, this::launchOfficial);
        }
        button("Actualizar nivel y ventajas", true, () -> openOfficial(false));
        if (!AmiciAccessService.available()) {
            button("Configurar acceso a Amici", false, () -> startActivity(
                new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
    }

    private void openCoupon(AmiciBenefits.Offer offer) {
        if (sender.isPaired()) {
            toast("Consultando el código en el emisor…");
            sender.sendJson(CodeExchange.Operation.AMICI_COUPON, offer.title, this::showCoupon, this::toast);
            return;
        }
        if (!AmiciAccessService.available()) {
            toast("Activa Food Court · Amici en Accesibilidad para consultar el código.");
            return;
        }
        if (launchOfficial()) AmiciAccessService.readCoupon(section, offer.title);
    }

    /** Shows a coupon as {@link AmiciBenefitsTask} reports it: title, code, available, discount and rules. */
    private void showCoupon(org.json.JSONObject coupon) {
        String title = coupon.optString("title");
        String code = coupon.optString("code");
        String validity = coupon.optString("available");
        String discount = coupon.optString("discount");
        String rules = coupon.optString("rules");
        layout("Código de descuento");
        label(title, 22, theme.ink);
        if (!discount.isEmpty()) label(discount, 16, theme.muted);
        if (!validity.isEmpty()) label(validity, 15, theme.muted);
        boolean expired = DiscountWallet.isExpired(validity);
        if (expired) label("Caducado según la fecha indicada por Amici.", 16, FRANCHISE.accent());
        label(code, 25, theme.ink).setTextIsSelectable(true);
        if (!expired) {
            button("Copiar código", true, () -> {
                Ui.copy(this, "Cupón Amici", code);
                toast("Código copiado.");
            });
            button("Guardar en Mis códigos", false, () -> {
                DiscountWallet.save(this, FRANCHISE.name(), title, code, "Texto", validity);
                toast("Código guardado.");
            });
        }
        if (!rules.isEmpty()) label(rules, 15, theme.muted);
        button("Volver a las ventajas", false, this::render);
    }

    private void openOfficial(boolean scan) {
        if (!scan && sender.isPaired()) {
            toast("Sincronizando Amici desde el emisor…");
            sender.sendJson(CodeExchange.Operation.AMICI_SYNC, "", answer -> {
                AmiciBenefits.fromRemote(answer).save(this);
                render();
                toast("Nivel y ventajas actualizados desde el emisor.");
            }, this::toast);
            return;
        }
        if (!AmiciAccessService.available()) {
            if (scan && launchOfficial()) toast("En Amici, pulsa Añadir visita y Escanear ticket.");
            else if (!scan) toast("Activa Food Court · Amici en Accesibilidad para sincronizar.");
            return;
        }
        if (launchOfficial()) AmiciAccessService.start(scan, section);
    }

    private boolean launchOfficial() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FRANCHISE.officialUri(section)))
                .setPackage(FRANCHISE.officialPackage()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException unavailable) {
            new android.app.AlertDialog.Builder(this)
                .setMessage("Instala Amici e inicia sesión para registrar visitas y consultar tus ventajas.")
                .setPositiveButton("Google Play", (dialog, index) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://play.google.com/store/apps/details?id=" + FRANCHISE.officialPackage())));
                    } catch (ActivityNotFoundException ignored) {
                        toast("Google Play no está disponible.");
                    }
                })
                .setNegativeButton("Cerrar", null).show();
            return false;
        }
    }

    private void scan() {
        Ui.scan(this, "Escanea el QR del ticket de La Tagliatella", true);
    }

    private void selectImage() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), TICKET_IMAGE);
        } catch (ActivityNotFoundException unavailable) {
            toast("No se pudo abrir el selector de imágenes.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TICKET_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                new Thread(() -> {
                    try {
                        String code = TicketImageReader.read(getContentResolver(), uri, true);
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) showManualTicket(code);
                        });
                    } catch (java.io.IOException | com.google.zxing.ReaderException | RuntimeException error) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) toast("No se encontró un QR legible.");
                        });
                    }
                }, "food-court-amici-ticket").start();
            }
            return;
        }
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) super.onActivityResult(requestCode, resultCode, data);
        else if (result.getContents() != null) showManualTicket(result.getContents());
    }

    private void showTicket(String value) {
        layout("Ticket leído");
        ImageView qr = new ImageView(this);
        qr.setContentDescription("QR del ticket de Amici");
        qr.setImageBitmap(QrCode.render(value));
        page.addView(qr, new LinearLayout.LayoutParams(-1, dp(240)));
        label("El emisor lee este QR con la cámara de Amici. Coloca ambos móviles cerca.", 16, theme.muted);
        TextView status = label("", 16, theme.muted);
        button("Abrir lector de Amici en el emisor", true, () -> openEmitter(status));
        button("Copiar código", false, () -> {
            Ui.copy(this, "Ticket Amici", value);
            toast("Código copiado.");
        });
        button("Volver a Amici en Food Court", false, this::render);
    }

    /** Sends number, date and total so the sender fills «Añadir datos manualmente» in Amici. */
    private void showManualTicket(String scanned) {
        layout("Registrar ticket");
        AmiciTicketQr.Guess guess = scanned == null ? new AmiciTicketQr.Guess()
            : AmiciTicketQr.guess(scanned, new java.util.Date());
        label("El emisor rellena «Añadir datos manualmente» en Amici con estos datos del ticket. "
            + "Amici admite un ticket al día, de los últimos " + AmiciTicket.MAX_AGE_DAYS + " días.", 15, theme.muted);
        android.widget.EditText number = input("Número del ticket", android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        android.widget.EditText amount = input("Cantidad total (por ejemplo 23,50)",
            android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        amount.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,."));
        label("Fecha de la visita", 15, theme.muted);
        java.util.List<java.util.Date> days = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("EEEE d 'de' MMMM", new java.util.Locale("es", "ES"));
        java.util.Calendar day = java.util.Calendar.getInstance();
        for (int i = 0; i <= AmiciTicket.MAX_AGE_DAYS; i++) {
            days.add(day.getTime());
            String name = format.format(day.getTime());
            names.add((i == 0 ? "Hoy · " : i == 1 ? "Ayer · " : "") + name);
            day.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        android.widget.Spinner date = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        date.setAdapter(adapter);
        page.addView(date, new LinearLayout.LayoutParams(-1, dp(52)));
        number.setText(guess.number);
        amount.setText(guess.amount.replace('.', ','));
        String dateWarning = "";
        if (guess.date != null) {
            java.text.SimpleDateFormat dayKey = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT);
            int match = -1;
            for (int i = 0; i < days.size(); i++) {
                if (dayKey.format(days.get(i)).equals(dayKey.format(guess.date))) match = i;
            }
            if (match >= 0) date.setSelection(match);
            else dateWarning = "\nEl QR indica el " + new java.text.SimpleDateFormat("dd/MM/yyyy",
                java.util.Locale.ROOT).format(guess.date) + ": Amici solo admite tickets de los últimos "
                + AmiciTicket.MAX_AGE_DAYS + " días.";
        }
        if (scanned != null) {
            label((guess.isEmpty() ? "No se reconocieron los datos en el QR. Escríbelos a mano."
                : "Datos deducidos del QR. Revísalos con el ticket antes de enviarlos.") + dateWarning,
                15, guess.isEmpty() || !dateWarning.isEmpty() ? FRANCHISE.accent() : theme.muted);
            TextView raw = label("Contenido del QR: " + scanned, 13, theme.muted);
            raw.setTextIsSelectable(true);
        }
        TextView status = label("", 16, theme.muted);
        button("Registrar en el emisor", true, () -> {
            String ticketNumber = number.getText().toString().trim();
            String total = AmiciTicket.normalizeAmount(amount.getText().toString());
            if (!ticketNumber.matches("[A-Za-z0-9/._-]{1,40}")) {
                status.setText("Escribe el número del ticket tal como aparece impreso.");
                return;
            }
            if (total.isEmpty()) {
                status.setText("Escribe la cantidad total del ticket, por ejemplo 23,50.");
                return;
            }
            sendManualTicket(AmiciTicket.encode(ticketNumber, days.get(date.getSelectedItemPosition()), total), status);
        });
        if (scanned != null) {
            button("Mostrar el QR para la cámara del emisor", false, () -> showTicket(scanned));
        }
        button("Volver a Amici en Food Court", false, this::render);
    }

    private void sendManualTicket(String ticket, TextView status) {
        if (!sender.isPaired()) {
            openPairing();
            return;
        }
        status.setText("Registrando el ticket en el emisor…");
        sender.send(CodeExchange.Operation.AMICI_TICKET, ticket, result -> status.setText(result.isEmpty()
            ? "El emisor no confirmó el registro. Revisa Amici en el emisor." : result), status::setText);
    }

    private android.widget.EditText input(String hint, int type) {
        android.widget.EditText view = new android.widget.EditText(this);
        view.setSingleLine();
        view.setInputType(type);
        view.setHint(hint);
        view.setTextColor(theme.ink);
        view.setHintTextColor(theme.muted);
        page.addView(view);
        return view;
    }

    private void openEmitter(TextView status) {
        if (!sender.isPaired()) {
            openPairing();
            return;
        }
        status.setText("Abriendo el lector en el emisor…");
        sender.send(CodeExchange.Operation.AMICI_SCANNER, "", result -> status.setText(result.isEmpty()
            ? "Comprueba que el emisor esté desbloqueado." : result), status::setText);
    }

    private void openPairing() {
        startActivity(new Intent(this, ExchangeActivity.class).putExtra(ExchangeActivity.PAIR_ONLY, true));
    }

    private void layout(String title) {
        page = Ui.page(this, theme, 18);
        button("‹  Cartera", false, this::finish);
        label(FRANCHISE.name(), 18, theme.muted);
        label(title, 35, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD);
    }

    private void tab(LinearLayout row, String label, Franchise.Section target) {
        Button tab = styledButton(label, section == target, () -> {
            section = target;
            render();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMarginEnd(dp(4));
        row.addView(tab, params);
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(7), 0, dp(6));
        page.addView(view);
        return view;
    }

    private void button(String title, boolean primary, Runnable action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        page.addView(styledButton(title, primary, action), params);
    }

    private Button styledButton(String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(primary ? FRANCHISE.onAccent() : theme.ink);
        button.setMinWidth(0);
        button.setMinHeight(dp(48));
        button.setStateListAnimator(null);
        button.setBackground(WalletTheme.button(primary ? FRANCHISE.accent() : theme.secondary, dp(15)));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void toast(String text) {
        Ui.toast(this, text);
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
