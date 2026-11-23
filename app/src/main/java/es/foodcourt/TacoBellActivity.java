package es.foodcourt;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.integration.android.IntentIntegrator;

public final class TacoBellActivity extends Activity {
    static final String SECTION = "es.foodcourt.taco_bell_section";
    private static final int PICK_IMAGE = 1371;
    private static final String MEMBER_QR = "memberQr";
    private WalletTheme theme;
    private LinearLayout page;
    private SharedPreferences wallet;
    private boolean memberScan;
    private static final TacoBell FRANCHISE = new TacoBell();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = new WalletTheme(this);
        theme.apply(this);
        wallet = getSharedPreferences("taco_bell_wallet", MODE_PRIVATE);
        if (state != null) memberScan = state.getBoolean("memberScan");
        showDashboard();
        if (state == null && Franchise.Section.REWARDS.name().equals(getIntent().getStringExtra(SECTION))) {
            page.post(() -> scan(false));
        } else if (state == null && Franchise.Section.DEALS.name().equals(getIntent().getStringExtra(SECTION))) {
            showOffers();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("memberScan", memberScan);
        super.onSaveInstanceState(state);
    }

    private void showDashboard() {
        layout(FRANCHISE.name());
        label(FRANCHISE.program(), 16, theme.muted);
        button("Mi QR", true, this::showMember);
        button("Escanear ticket", false, () -> scan(false));
        button("Leer ticket desde una imagen", false, () -> pickImage(false));
        button("Ofertas Mild, Hot y Fire", false, this::showOffers);
    }

    private void showMember() {
        layout("QR de socio");
        button("Solicitar QR al emisor", true, () ->
            requestRelay(CodeExchange.Operation.TACO_POINTS, "", this::showLiveMember));
        String code = wallet.getString(MEMBER_QR, "");
        if (code.isEmpty()) {
            label("También puedes importar una copia de tu QR de socio.", 16, theme.muted);
        } else {
            ImageView qr = new ImageView(this);
            qr.setContentDescription("QR de socio guardado");
            qr.setImageBitmap(QrCode.render(code));
            page.addView(qr, new LinearLayout.LayoutParams(-1, dp(240)));
            label("QR guardado. Taco Bell determina si sigue vigente.", 15, theme.muted);
            button("Eliminar QR guardado", false, () -> {
                wallet.edit().remove(MEMBER_QR).apply();
                showMember();
            });
        }
        button(code.isEmpty() ? "Guardar mi QR" : "Actualizar mi QR", true, () -> scan(true));
        button("Importar imagen del QR", false, () -> pickImage(true));
        button("Volver a Taco Bell", false, this::showDashboard);
    }

    private void showOffers() {
        layout("Ofertas por nivel");
        LinearLayout tiers = new LinearLayout(this);
        page.addView(tiers);
        TextView selected = label("", 23, theme.ink);
        selected.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        for (String tier : new String[] { "Mild", "Hot", "Fire" }) {
            Button item = styledButton(tier, false, () -> selected.setText(tier));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            size.setMarginEnd(dp(5));
            tiers.addView(item, size);
        }
        selected.setText("Mild");
        label("El catálogo por nivel depende de tu cuenta. Aquí puedes consultar las promociones públicas.", 16, theme.muted);
        button("Ver promociones de Taco Bell", true, this::openPublicPage);
        button("Volver a Taco Bell", false, this::showDashboard);
    }

    private void pickImage(boolean member) {
        memberScan = member;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
        try {
            startActivityForResult(picker, PICK_IMAGE);
        } catch (android.content.ActivityNotFoundException unavailable) {
            showMessage("No se pudo abrir el selector de imágenes.");
        }
    }

    private void readImage(Uri uri, boolean member) {
        new Thread(() -> {
            try {
                String content = TicketImageReader.read(getContentResolver(), uri, member);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) handleCode(content, member);
                });
            } catch (java.io.IOException | com.google.zxing.ReaderException | RuntimeException failure) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) showMessage("No se encontró un código legible.");
                });
            }
        }, "food-court-taco-scan").start();
    }

    private void scan(boolean member) {
        memberScan = member;
        Ui.scan(this, member ? "Escanea tu QR de socio" : "Escanea el QR o código de barras del ticket", member);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                readImage(data.getData(), memberScan);
            }
            return;
        }
        com.google.zxing.integration.android.IntentResult result =
            IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        handleCode(result.getContents(), memberScan);
    }

    private void handleCode(String code, boolean member) {
        if (code == null || code.isEmpty()) return;
        if (member) {
            if (code.length() > 1000) {
                showMessage("El QR es demasiado largo para guardarlo.");
                return;
            }
            wallet.edit().putString(MEMBER_QR, code).apply();
            showMember();
        } else {
            showTicket(code);
        }
    }

    private void showTicket(String code) {
        layout("Ticket leído");
        label(code, 19, theme.ink).setTextIsSelectable(true);
        button("Registrar ticket en Taco Bell", true, () -> new android.app.AlertDialog.Builder(this)
            .setMessage("El emisor enviará este ticket a la cuenta de Taco Bell abierta en ese dispositivo.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Registrar", (dialog, index) -> requestRelay(
                CodeExchange.Operation.TACO_TICKET, code, result -> {
                    layout("Resultado del ticket");
                    label(result, 18, theme.ink);
                    button("Volver a Taco Bell", false, this::showDashboard);
                })).show());
        button("Copiar código", true, () -> {
            Ui.copy(this, "Código del ticket", code);
            showMessage("Código copiado");
        });
        button("Escanear otro ticket", false, () -> scan(false));
        button("Volver a Taco Bell", false, this::showDashboard);
    }

    private void showLiveMember(String code) {
        layout("QR vigente de Taco Bell");
        ImageView qr = new ImageView(this);
        qr.setContentDescription("QR vigente recibido de Taco Bell");
        qr.setImageBitmap(QrCode.render(code));
        page.addView(qr, new LinearLayout.LayoutParams(-1, dp(240)));
        button("Solicitar otro QR", true, this::showMember);
        button("Volver a Taco Bell", false, this::showDashboard);
    }

    private interface RelayResult { void show(String value); }

    private CodeExchange.QrRequest qrRequest;

    private void requestRelay(CodeExchange.Operation operation, String value,
                              RelayResult onResult) {
        if (qrRequest != null) return;
        SenderClient sender = new SenderClient(this);
        if (!sender.isPaired()) {
            new android.app.AlertDialog.Builder(this)
                .setMessage("Empareja un emisor que tenga Taco Bell abierto con tu sesión.")
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Configurar emisor", (dialog, index) -> startActivity(
                    new Intent(this, ExchangeActivity.class).putExtra(ExchangeActivity.PAIR_ONLY, true))).show();
            return;
        }
        TextView status = label("Consultando al emisor…", 16, theme.muted);
        CodeExchange.QrRequest reading = operation == CodeExchange.Operation.TACO_POINTS
            ? new CodeExchange.QrRequest() : null;
        qrRequest = reading;
        if (reading != null) {
            button("Cancelar", false, () -> {
                cancelQr();
                showMember();
            });
        }
        SenderClient.Answer onAnswer = response -> {
            if (!finishQr(reading, status)) return;
            if (response.isEmpty()) {
                status.setText("El emisor no mostró el código. Comprueba su sesión y que esté desbloqueado.");
            } else {
                onResult.show(response);
            }
        };
        SenderClient.Failure onFailure = message -> {
            if (finishQr(reading, status)) status.setText(message);
        };
        if (reading != null) sender.sendQr(operation, reading, onAnswer, onFailure);
        else sender.send(operation, value, onAnswer, onFailure);
    }

    private void showMessage(String message) {
        Ui.toast(this, message);
    }

    private void openPublicPage() {
        startActivity(new Intent(this, FranchisePortalActivity.class)
            .putExtra(FranchisePortalActivity.BRAND, FRANCHISE.id())
            .putExtra(FranchisePortalActivity.SECTION, Franchise.Section.DEALS.name()));
    }

    private boolean finishQr(CodeExchange.QrRequest reading, TextView status) {
        if (reading != null) {
            if (qrRequest != reading) return false;
            qrRequest = null;
            page.removeViewAt(page.getChildCount() - 1);
        }
        return !isFinishing() && status.isAttachedToWindow();
    }

    private void cancelQr() {
        if (qrRequest != null) qrRequest.cancel();
        qrRequest = null;
    }

    @Override protected void onDestroy() {
        cancelQr();
        super.onDestroy();
    }

    private void layout(String title) {
        cancelQr();
        page = Ui.page(this, theme, 18);
        button("‹  Cartera", false, this::finish);
        label(title, 37, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(9), 0, dp(7));
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
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
