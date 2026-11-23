package es.foodcourt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

final class ClubByTableArtwork extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ClubByTableArtwork(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 180f, getHeight() / 130f);
        canvas.save();
        canvas.translate((getWidth() - 180 * scale) / 2, (getHeight() - 130 * scale) / 2);
        canvas.scale(scale, scale);

        paint.setColor(0x40485C75);
        canvas.drawOval(25, 101, 156, 122, paint);
        paint.setColor(0xFF956C54);
        canvas.drawOval(31, 38, 149, 103, paint);
        paint.setColor(0xFFD5A779);
        canvas.drawOval(31, 34, 149, 95, paint);
        plate(canvas, 73, 61, 0xFFF1AE54);
        plate(canvas, 109, 65, 0xFFD77949);

        ball(canvas, 36, 38, 24, 0xFFE62740, "VIPS", 11);
        ball(canvas, 145, 37, 25, 0xFF263870, "FOSTER’S", 8);
        ball(canvas, 43, 101, 24, 0xFF196C51, "GINOS", 9);
        ball(canvas, 139, 101, 25, 0xFFB2293A, "FRIDAYS", 8);
        paint.setColor(0xFF321B23);
        canvas.drawArc(27, 26, 47, 47, 15, 76, false, paint);
        canvas.restore();
    }

    private void plate(Canvas canvas, float x, float y, int color) {
        paint.setColor(0xFFFDF3D8);
        canvas.drawOval(x - 12, y - 7, x + 12, y + 7, paint);
        paint.setColor(color);
        canvas.drawOval(x - 7, y - 4, x + 7, y + 4, paint);
    }

    private void ball(Canvas canvas, float x, float y, float r, int color, String label, int size) {
        paint.setColor(0x37502732);
        canvas.drawCircle(x + 1, y + 3, r, paint);
        paint.setColor(color);
        canvas.drawCircle(x, y, r, paint);
        paint.setColor(0x34FFFFFF);
        canvas.drawOval(x - r * .68f, y - r * .76f, x + r * .24f, y - r * .43f, paint);
        paint.setColor(0xFFFFF5E9);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, x, y + size * .37f, paint);
    }
}
