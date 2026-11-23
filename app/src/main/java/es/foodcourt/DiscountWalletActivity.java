package es.foodcourt;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.List;

public final class DiscountWalletActivity extends Activity {
    private WalletTheme theme;
    private LinearLayout page;
    private String[] brands;
    private EditText titleInput;
    private EditText codeInput;
    private int selectedBrand;
    private String format = "Texto";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        theme = new WalletTheme(this);
        theme.apply(this);
        brands = getIntent().getStringArrayExtra("brands");
        if (brands == null || brands.length == 0) {
            finish();
            return;
        }
        if (state != null && state.getBoolean("editing")) {
            selectedBrand = state.getInt("brand");
            format = state.getString("format", "Texto");
            edit(state.getString("title", ""), state.getString("code", ""));
        } else {
            showList();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("editing", titleInput != null);
        if (titleInput != null) {
            state.putInt("brand", selectedBrand);
            state.putString("format", format);
            state.putString("title", titleInput.getText().toString());
            state.putString("code", codeInput.getText().toString());
        }
    }

    private void showList() {
        layout("Mis códigos");
        button("Guardar un código", true, () -> {
            format = "Texto";
            edit("", "");
        });
        List<DiscountWallet.Code> codes = DiscountWallet.load(this);
        if (codes.isEmpty()) label("Los descuentos que guardes aparecerán aquí.", 16, theme.muted);
        for (DiscountWallet.Code code : codes) {
            button(code.brand + " · " + code.title, false, () -> showCode(code));
        }
    }

    private void edit(String title, String value) {
        layout("Guardar código");
        Button brand = button(brands[selectedBrand], false, () -> {});
        brand.setOnClickListener(view -> new AlertDialog.Builder(this)
            .setTitle("Marca")
            .setSingleChoiceItems(brands, selectedBrand, (dialog, option) -> {
                selectedBrand = option;
                brand.setText(brands[option]);
                dialog.dismiss();
            }).show());
        titleInput = input("Nombre de la oferta");
        titleInput.setText(title);
        codeInput = input("Código de descuento");
        codeInput.setText(value);
        button("Escanear QR de descuento", false, () -> {
            Ui.scan(this, "Escanea el QR de la oferta", true);
        });
        button("Guardar código", true, () -> {
            String titleText = titleInput.getText().toString().trim();
            String valueText = codeInput.getText().toString().trim();
            if (titleText.isEmpty() || valueText.isEmpty() || valueText.length() > 1000) {
                toast("Introduce el nombre de la oferta y un código válido.");
                return;
            }
            DiscountWallet.save(this, brands[selectedBrand], titleText, valueText, format, "");
            showList();
        });
        button("Volver a Mis códigos", false, this::showList);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) {
            super.onActivityResult(requestCode, resultCode, data);
        } else if (result.getContents() != null && codeInput != null) {
            format = "QR";
            codeInput.setText(result.getContents());
        }
    }

    private void showCode(DiscountWallet.Code code) {
        layout(code.title);
        label(code.brand, 17, theme.muted);
        if (code.format.equals("QR")) {
            ImageView qr = new ImageView(this);
            qr.setContentDescription("QR guardado");
            qr.setImageBitmap(QrCode.render(code.value));
            page.addView(qr, new LinearLayout.LayoutParams(-1, dp(220)));
        }
        label(code.value, 23, theme.ink).setTextIsSelectable(true);
        if (!code.validity.isEmpty()) label(code.validity, 15, theme.muted);
        if (DiscountWallet.isExpired(code.validity)) {
            label("Caducado según la fecha de la oferta.", 16, theme.muted);
        } else {
            button("Copiar código", true, () -> {
                Ui.copy(this, "Código de descuento", code.value);
                toast("Código copiado.");
            });
        }
        button("Eliminar código", false, () -> new AlertDialog.Builder(this)
            .setTitle("Eliminar código guardado")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar", (dialog, index) -> {
                DiscountWallet.remove(this, code.id);
                showList();
            }).show());
        button("Volver a Mis códigos", false, this::showList);
    }

    private void layout(String heading) {
        titleInput = null;
        codeInput = null;
        page = Ui.page(this, theme, 18);
        button("‹  Cartera", false, this::finish);
        label(heading, 35, theme.ink).setTypeface(android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD);
    }

    private Button button(String title, boolean primary, Runnable action) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(title);
        view.setTextColor(primary ? theme.onAccent : theme.ink);
        view.setMinHeight(dp(48));
        view.setGravity(Gravity.CENTER);
        view.setBackground(WalletTheme.button(primary ? theme.accent : theme.secondary, dp(16)));
        view.setOnClickListener(unused -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        page.addView(view, params);
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, dp(8), 0, dp(8));
        page.addView(view);
        return view;
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setHint(hint);
        view.setTextColor(theme.ink);
        view.setHintTextColor(theme.muted);
        view.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        page.addView(view);
        return view;
    }

    private void toast(String text) {
        Ui.toast(this, text);
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
