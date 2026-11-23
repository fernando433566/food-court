package es.foodcourt;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;

final class WalletTheme {
    final boolean oled;
    final int paper;
    final int ink;
    final int muted;
    final int surface;
    final int secondary;
    final int accent;
    final int onAccent;

    WalletTheme(Context context) {
        oled = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            .getBoolean("oled", false);
        paper = oled ? 0xFF000000 : 0xFFFFF4E3;
        ink = oled ? 0xFFFFF1D6 : 0xFF29180F;
        muted = oled ? 0xFFB9A892 : 0xFF765B47;
        surface = oled ? 0xFF181410 : 0xFFFFFBF3;
        secondary = oled ? 0xFF221B15 : 0xFFF2DFBD;
        accent = oled ? 0xFFFFD075 : 0xFF852D1C;
        onAccent = oled ? 0xFF25150B : 0xFFFFF4E5;
    }

    static void save(Context context, boolean oled) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            .edit().putBoolean("oled", oled).apply();
    }

    void apply(Activity activity) {
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        View decor = activity.getWindow().getDecorView();
        decor.setSystemUiVisibility(oled ? decor.getSystemUiVisibility() & ~lightFlags
            : decor.getSystemUiVisibility() | lightFlags);
    }

    static GradientDrawable shape(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    static RippleDrawable button(int color, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x427D5F44), shape(color, radius), null);
    }
}
