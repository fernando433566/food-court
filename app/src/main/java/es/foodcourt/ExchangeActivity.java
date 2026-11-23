package es.foodcourt;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * Sets up the exchange: the sender screen (key, addresses, service status) and the receiver
 * screen (pairing and the MyMcDonald’s QR).
 */
public final class ExchangeActivity extends Activity {
    static final String OPEN_RECEIVER = "es.foodcourt.open_receiver";
    static final String OPEN_SENDER = "es.foodcourt.open_sender";
    static final String PAIR_ONLY = "es.foodcourt.pair_only";
    static final String SET_UP = "es.foodcourt.set_up";
    private boolean pairOnly;
    private CodeExchange.QrRequest qrRequest;
    private boolean setUp;
    private WalletTheme theme;
    private SharedPreferences settings;
    private SenderClient sender;
    private TextView senderEndpoint;
    private TextView senderStatus;
    private LinearLayout page;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = SenderClient.settings(this);
        sender = new SenderClient(this);
        theme = new WalletTheme(this);
        theme.apply(this);
        setUp = getIntent().getBooleanExtra(SET_UP, false);
        boolean openPairing = getIntent().getBooleanExtra(PAIR_ONLY, false);
        pairOnly = openPairing || setUp;
        if (getIntent().getBooleanExtra(OPEN_SENDER, false)) {
            showSender();
        } else if (openPairing || getIntent().getBooleanExtra(OPEN_RECEIVER, false)) {
            showReceiver(null);
        } else {
            showHome();
        }
    }

    private void showHome() {
        senderStatus = null;
        layout("Intercambio");
        button("Emisor", this::showSender);
        button("Receptor", () -> showReceiver(null));
        button("Volver a la cartera", this::finish);
    }

    private void showSender() {
        layout("Emisor");
        String key = settings.getString(CodeExchange.SENDER_KEY, "");
        if (key.isEmpty()) {
            key = CodeExchange.newKey();
            settings.edit().putString(CodeExchange.SENDER_KEY, key).apply();
        }
        String pairingKey = key;
        senderEndpoint = text("", 16);
        button("Copiar clave de emparejamiento", () -> {
            Ui.copy(this, "Clave de emparejamiento", pairingKey);
            Ui.toast(this, "Clave copiada");
        });
        button("Mostrar clave de emparejamiento", () -> new android.app.AlertDialog.Builder(this)
            .setTitle("Clave de emparejamiento").setMessage(pairingKey)
            .setPositiveButton("Cerrar", null).show());
        text("El emisor muestra los QR de socio de MyMcDonald’s, Taco Bell y Club·by, registra tickets de Taco Bell y Amici, "
            + "sincroniza Amici y las promociones de Club·by y abre sus lectores. Deja las sesiones iniciadas. "
            + "Si el teléfono no tiene PIN, patrón ni contraseña, se enciende solo; si lo tiene, déjalo desbloqueado.", 15);
        text("En el receptor escribe la IP local, la IP o el nombre de Netbird, o una URL https. "
            + "Para la URL, apunta un túnel HTTPS (cloudflared, Caddy…) a http://localhost:"
            + HttpsOrigin.PORT + " del emisor.", 14).setTextColor(theme.muted);
        senderStatus = text("", 15);
        updateSenderStatus();
        button("Abrir ajustes de accesibilidad", () -> startActivity(
            new android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("Activar emisor automático", () -> {
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 1);
            }
            settings.edit().putBoolean(CodeExchange.SENDER_ENABLED, true).apply();
            senderStatus.postDelayed(this::updateSenderStatus, 400);
        });
        button("Quitar restricciones de batería", this::requestUnrestrictedBattery);
        button("Desactivar emisor automático", () -> {
            settings.edit().putBoolean(CodeExchange.SENDER_ENABLED, false).apply();
            updateSenderStatus();
        });
        button("Revocar emparejamiento", () -> {
            settings.edit().putString(CodeExchange.SENDER_KEY, CodeExchange.newKey()).apply();
            showSender();
        });
        button("Cambiar de modo", this::showHome);
    }

    private void showReceiver(String initialCode) {
        senderStatus = null;
        layout(pairOnly ? "Emparejar emisor" : "QR de puntos");
        if (!pairOnly) text("MyMcDonald’s", 17).setTextColor(theme.muted);
        String savedKey = sender.key();
        EditText host = field("IP local, IP/nombre de Netbird o URL https");
        host.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        host.setText(sender.host());
        EditText pairing = savedKey.isEmpty() ? field("Clave del emisor") : null;
        TextView status = text(savedKey.isEmpty() ? "Empareja el emisor la primera vez."
            : "Emisor emparejado.", 15);
        status.setTextColor(theme.muted);
        Button request = button(pairOnly ? "Comprobar conexión" : "Solicitar código", () -> { });
        request.setTextColor(theme.onAccent);
        request.setBackground(WalletTheme.button(theme.accent, dp(18)));
        LinearLayout pass = new LinearLayout(this);
        pass.setOrientation(LinearLayout.VERTICAL);
        pass.setBackground(WalletTheme.shape(theme.surface, dp(26)));
        pass.setPadding(dp(18), dp(12), dp(18), dp(16));
        LinearLayout.LayoutParams passSize = new LinearLayout.LayoutParams(-1, -2);
        passSize.topMargin = dp(14);
        page.addView(pass, passSize);
        pass.setVisibility(android.view.View.GONE);

        ImageView qr = new ImageView(this);
        qr.setContentDescription("QR recibido");
        pass.addView(qr, new LinearLayout.LayoutParams(-1, dp(220)));
        TextView value = new TextView(this);
        value.setTextColor(theme.ink);
        value.setTextSize(27);
        value.setLetterSpacing(.12f);
        value.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        value.setGravity(android.view.Gravity.CENTER);
        value.setPadding(0, dp(12), 0, dp(4));
        pass.addView(value);
        request.setOnClickListener(v -> {
            if (qrRequest != null) {
                cancelQr();
                request.setText("Solicitar código");
                status.setText("Consulta cancelada.");
                return;
            }
            CodeExchange.QrRequest reading = pairOnly ? null : new CodeExchange.QrRequest();
            qrRequest = reading;
            String address = host.getText().toString().trim();
            String key = pairing == null ? savedKey : pairing.getText().toString().trim();
            request.setEnabled(reading != null);
            if (reading != null) request.setText("Cancelar");
            value.setText("");
            pass.setVisibility(android.view.View.GONE);
            status.setText("Consultando al emisor…");
            CodeExchange.Operation operation = pairOnly ? CodeExchange.Operation.PAIR : CodeExchange.Operation.MCD_POINTS;
            sender.send(address, key, operation, "", reading, code -> {
                if (!completeQr(reading, status, request)) return;
                sender.save(address, key);
                if (pairOnly) {
                    if (pairing != null) showReceiver(null);
                    else {
                        status.setText("Emisor conectado.");
                        request.setEnabled(true);
                    }
                } else if (pairing != null) {
                    showReceiver(code);
                } else {
                    present(code, status, value, qr, pass);
                    request.setEnabled(true);
                }
            }, message -> {
                if (completeQr(reading, status, request)) requestFailed(status, request, message);
            });
        });
        if (initialCode != null) present(initialCode, status, value, qr, pass);
        button("Olvidar emparejamiento", () -> {
            sender.forget();
            showReceiver(null);
        });
        if (setUp) button("Volver a la cartera", this::finish);
        else if (pairOnly) button("Volver", this::finish);
        else button("Configurar intercambio", this::showHome);
    }

    private void present(String code, TextView status, TextView value, ImageView qr, View pass) {
        if (code.isEmpty()) {
            status.setText("MyMcDonald’s no muestra un código en el emisor. Comprueba la sesión y el desbloqueo.");
            return;
        }
        status.setText("Código recibido del emisor. Su vigencia depende de MyMcDonald’s.");
        value.setText(code);
        qr.setImageBitmap(QrCode.render(code));
        pass.setVisibility(android.view.View.VISIBLE);
    }

    private boolean completeQr(CodeExchange.QrRequest reading, TextView status, Button request) {
        if (reading != null) {
            if (qrRequest != reading) return false;
            qrRequest = null;
            request.setText("Solicitar código");
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

    private void requestFailed(TextView status, Button button, String message) {
        if (isFinishing() || !status.isAttachedToWindow()) return;
        status.setText(message);
        button.setEnabled(true);
    }

    private void layout(String title) {
        cancelQr();
        page = Ui.page(this, theme, 24);
        Button back = button("‹  Cartera", this::finish);
        back.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        text(title, 40).setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(theme.ink);
        view.setPadding(0, dp(6), 0, dp(8));
        page.addView(view);
        return view;
    }

    private EditText field(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine();
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setHint(hint);
        view.setTextColor(theme.ink);
        view.setHintTextColor(theme.muted);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(theme.muted));
        page.addView(view);
        return view;
    }

    private Button button(String title, Runnable click) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(title);
        button.setTextColor(theme.ink);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setMinHeight(dp(48));
        button.setStateListAnimator(null);
        button.setBackground(WalletTheme.button(theme.secondary, dp(17)));
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.topMargin = dp(9);
        page.addView(button, size);
        return button;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }

    private String localAddresses() {
        StringBuilder lan = new StringBuilder();
        StringBuilder overlay = new StringBuilder();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (network == null || !network.isUp() || network.isLoopback()) continue;
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!(address instanceof Inet4Address)) continue;
                    if (CodeExchange.isOverlay(address)) {
                        append(overlay, address.getHostAddress());
                    } else if (address.isSiteLocalAddress()) {
                        append(lan, address.getHostAddress());
                    }
                }
            }
        } catch (IOException ignored) {
            // The receiver can still use an address provided by the network settings.
        }
        return "IP local · " + (lan.length() == 0 ? "sin red local" : lan)
            + "\nIP Netbird · " + (overlay.length() == 0 ? "Netbird no conectado" : overlay);
    }

    private static void append(StringBuilder list, String value) {
        if (list.length() > 0) list.append(", ");
        list.append(value);
    }

    private void requestUnrestrictedBattery() {
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23 && power != null
                    && !power.isIgnoringBatteryOptimizations(getPackageName())) {
                startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + getPackageName())));
            } else {
                startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
            }
        } catch (android.content.ActivityNotFoundException unavailable) {
            Ui.toast(this, "Abre Ajustes › Aplicaciones › Food Court › Batería");
        }
    }

    private boolean batteryRestricted() {
        if (android.os.Build.VERSION.SDK_INT < 23) return false;
        android.os.PowerManager power = getSystemService(android.os.PowerManager.class);
        return power != null && !power.isIgnoringBatteryOptimizations(getPackageName());
    }

    @Override protected void onResume() {
        super.onResume();
        updateSenderStatus();
    }

    private void updateSenderStatus() {
        if (senderStatus == null) return;
        senderEndpoint.setText(localAddresses()
            + "\nPuerto TCP · " + CodeExchange.PORT + "  ·  Origen HTTPS · " + HttpsOrigin.PORT);
        if (!OfficialCodeService.isConnected()) {
            senderStatus.setText("Activa «" + getString(R.string.code_service_name) + "» en Accesibilidad.");
        } else if (OfficialCodeService.isListening()) {
            senderStatus.setText("Emisor automático preparado."
                + (OfficialCodeService.isHttpsOriginReady() ? " Origen HTTPS abierto." : " El origen HTTPS no está disponible.")
                + (batteryRestricted() ? "\nQuita las restricciones de batería para que Android no lo suspenda." : ""));
        } else if (settings.getBoolean(CodeExchange.SENDER_ENABLED, false)) {
            senderStatus.setText("No se pudo iniciar el emisor.");
        } else {
            senderStatus.setText("Emisor automático desactivado.");
        }
    }
}
