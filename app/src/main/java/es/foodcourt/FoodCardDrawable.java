package es.foodcourt;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Fondo ilustrado de las tarjetas de la cartera: la propia tarjeta adopta la forma
 * del producto (pan con ingredientes, cubo de pollo, marco de trattoria o taco envuelto).
 * Todos los motivos son genéricos y originales; no reproducen logotipos ni personajes.
 */
final class FoodCardDrawable extends Drawable {
    enum Kind { STACKED_BURGER, GRILLED_BURGER, STRIPED_BUCKET, SPICY_BUCKET, TRATTORIA, WRAPPED_TACO }

    private final Kind kind;
    private final boolean oled;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    FoodCardDrawable(Kind kind, boolean oled, float density) {
        this.kind = kind;
        this.oled = oled;
        this.d = density;
    }

    /** Relleno interior para que el texto quede sobre la zona lisa de la ilustración. */
    Rect contentPadding() {
        switch (kind) {
            case STACKED_BURGER:
            case GRILLED_BURGER: return px(0, 1, 0, 40);
            case STRIPED_BUCKET:
            case SPICY_BUCKET: return px(14, 22, 14, 4);
            case TRATTORIA: return px(6, 6, 6, 6);
            case WRAPPED_TACO: return px(0, 30, 0, 4);
        }
        throw new AssertionError(kind);
    }

    int ink() {
        switch (kind) {
            case STACKED_BURGER:
            case GRILLED_BURGER: return oled ? 0xFFFFF1D6 : 0xFF29180F;
            case STRIPED_BUCKET: return oled ? 0xFFFFF6ED : 0xFF3B2421;
            case SPICY_BUCKET: return oled ? 0xFFFFF3DD : 0xFF2E1405;
            case TRATTORIA: return 0xFFFFF1D6;
            case WRAPPED_TACO: return 0xFFF7EDFF;
        }
        throw new AssertionError(kind);
    }

    int hint() {
        switch (kind) {
            case STACKED_BURGER: return oled ? 0xFFFFD466 : 0xFF8E2219;
            case GRILLED_BURGER: return oled ? 0xFFFFC9A3 : 0xFF8A1A00;
            case STRIPED_BUCKET: return oled ? 0xFFFFB3BE : 0xFFAC1730;
            case SPICY_BUCKET: return oled ? 0xFFFFC27A : 0xFF6E2208;
            case TRATTORIA: return 0xFFE7BE6A;
            case WRAPPED_TACO: return 0xFFFFD25A;
        }
        throw new AssertionError(kind);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.save();
        canvas.translate(b.left, b.top);
        float w = b.width(), h = b.height();
        switch (kind) {
            case STACKED_BURGER: burger(canvas, w, h, false); break;
            case GRILLED_BURGER: burger(canvas, w, h, true); break;
            case STRIPED_BUCKET: bucket(canvas, w, h, true); break;
            case SPICY_BUCKET: bucket(canvas, w, h, false); break;
            case TRATTORIA: trattoria(canvas, w, h); break;
            case WRAPPED_TACO: taco(canvas, w, h); break;
        }
        canvas.restore();
    }

    // ---------- Hamburguesas ----------

    private void burger(Canvas c, float w, float h, boolean grilled) {
        int bun = grilled ? (oled ? 0xFF5A3014 : 0xFFF2A94E) : (oled ? 0xFF5C3518 : 0xFFF5BD62);
        int toast = grilled ? (oled ? 0xFF3E200C : 0xFFD9822F) : (oled ? 0xFF432611 : 0xFFE2923A);
        float bunBottom = h - 40 * d;

        // Pan inferior
        path.reset();
        rect.set(4 * d, bunBottom + 27 * d, w - 4 * d, h);
        path.addRoundRect(rect, radii(5, 5, 14, 14), Path.Direction.CW);
        fill(c, toast);
        rect.set(4 * d, bunBottom + 27 * d, w - 4 * d, h - 3 * d);
        path.reset();
        path.addRoundRect(rect, radii(5, 5, 12, 12), Path.Direction.CW);
        fill(c, bun);

        if (grilled) {
            // Tomate en rodajas
            int tomato = oled ? 0xFFB3301F : 0xFFD9412E;
            for (int i = 0; i < 4; i++) {
                float l = w * i / 4f + 2 * d, r = w * (i + 1) / 4f - 2 * d;
                paint.setColor(tomato);
                c.drawRoundRect(l, bunBottom + 5 * d, r, bunBottom + 15 * d, 4.5f * d, 4.5f * d, paint);
                paint.setColor(oled ? 0xFFE0735A : 0xFFF28A6E);
                float mid = (l + r) / 2;
                c.drawOval(mid - 7 * d, bunBottom + 8 * d, mid - 2 * d, bunBottom + 11 * d, paint);
                c.drawOval(mid + 2 * d, bunBottom + 8.5f * d, mid + 7 * d, bunBottom + 11.5f * d, paint);
            }
            // Carne a la parrilla con marcas diagonales
            paint.setColor(oled ? 0xFF4A2518 : 0xFF4A2418);
            c.drawRoundRect(0, bunBottom + 13 * d, w, bunBottom + 29 * d, 7 * d, 7 * d, paint);
            paint.setColor(0xFF22100A);
            paint.setStrokeWidth(2.4f * d);
            paint.setStrokeCap(Paint.Cap.ROUND);
            for (float x = 14 * d; x < w - 8 * d; x += 18 * d) {
                c.drawLine(x, bunBottom + 16 * d, x + 8 * d, bunBottom + 26 * d, paint);
            }
            // Cebolla
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f * d);
            paint.setColor(0xFFF4E9F2);
            c.drawOval(w * .30f, bunBottom + 3 * d, w * .30f + 24 * d, bunBottom + 9 * d, paint);
            c.drawOval(w * .66f, bunBottom + 3.5f * d, w * .66f + 20 * d, bunBottom + 9 * d, paint);
            paint.setStyle(Paint.Style.FILL);
            lettuce(c, w, bunBottom - 3 * d, 8 * d, oled ? 0xFF4E8A31 : 0xFF5E9E3A);
        } else {
            // Doble carne con queso fundido
            paint.setColor(oled ? 0xFF5A2E1E : 0xFF5A2E1E);
            c.drawRoundRect(0, bunBottom + 12 * d, w, bunBottom + 29 * d, 7 * d, 7 * d, paint);
            paint.setColor(0xFF7A4532);
            for (float x = 12 * d; x < w - 8 * d; x += 23 * d) {
                c.drawCircle(x, bunBottom + 19 * d + (x % 5) * d, 1.6f * d, paint);
            }
            cheese(c, w, bunBottom + 5 * d, oled ? 0xFFE0A91F : 0xFFFFC832);
            lettuce(c, w, bunBottom - 3 * d, 8 * d, oled ? 0xFF5E9A3F : 0xFF6FAE4A);
        }

        // Pan superior con cúpula redondeada
        path.reset();
        path.moveTo(2 * d, bunBottom);
        path.lineTo(2 * d, 30 * d);
        path.cubicTo(2 * d, 9 * d, w * .12f, 1 * d, w * .40f, 0);
        path.lineTo(w * .60f, 0);
        path.cubicTo(w * .88f, 1 * d, w - 2 * d, 9 * d, w - 2 * d, 30 * d);
        path.lineTo(w - 2 * d, bunBottom);
        path.close();
        fill(c, bun);
        paint.setColor(toast);
        c.drawRoundRect(2 * d, bunBottom - 4 * d, w - 2 * d, bunBottom + 1 * d, 2 * d, 2 * d, paint);
        paint.setColor(oled ? 0x22FFE7B5 : 0x55FFF3D6);
        c.drawOval(w * .12f, 3 * d, w * .48f, 11 * d, paint);

        // Semillas fuera de la zona del texto
        int seedColor = oled ? 0xFFB99368 : 0xFFFFF0C8;
        float[][] seeds = grilled
            ? new float[][] { {.16f, 6}, {.30f, 4}, {.44f, 6}, {.58f, 4}, {.70f, 7}, {.82f, 5}, {.62f, 18},
                {.76f, 24}, {.90f, 16}, {.68f, 36}, {.84f, 42}, {.93f, 32}, {.60f, 48} }
            : new float[][] { {.12f, 7}, {.24f, 4}, {.38f, 5}, {.52f, 4}, {.64f, 6}, {.78f, 4}, {.88f, 9},
                {.60f, 20}, {.72f, 16}, {.84f, 24}, {.66f, 32}, {.80f, 40}, {.92f, 36}, {.72f, 50} };
        for (float[] s : seeds) {
            float y = s[1] * d;
            if (y > bunBottom - 8 * d) continue;
            seed(c, w * s[0], y, (s[0] * 97) % 60 - 30, seedColor);
        }
    }

    private void lettuce(Canvas c, float w, float y, float depth, int color) {
        paint.setColor(color);
        path.reset();
        path.moveTo(0, y);
        path.lineTo(w, y);
        float step = 16 * d;
        float x = w;
        boolean down = true;
        while (x > 0) {
            float next = Math.max(0, x - step);
            path.quadTo((x + next) / 2, y + (down ? depth * 1.6f : depth * .4f), next, y + depth);
            down = !down;
            x = next;
        }
        path.close();
        c.drawPath(path, paint);
    }

    private void cheese(Canvas c, float w, float y, int color) {
        paint.setColor(color);
        path.reset();
        path.moveTo(0, y);
        path.lineTo(w, y);
        path.lineTo(w, y + 8 * d);
        float[] drips = { .88f, .71f, .52f, .34f, .17f };
        for (float f : drips) {
            float x = w * f;
            path.lineTo(x + 7 * d, y + 8 * d);
            path.lineTo(x, y + 16 * d);
            path.lineTo(x - 7 * d, y + 8 * d);
        }
        path.lineTo(0, y + 8 * d);
        path.close();
        c.drawPath(path, paint);
    }

    private void seed(Canvas c, float x, float y, float tilt, int color) {
        c.save();
        c.rotate(tilt, x, y);
        paint.setColor(color);
        c.drawOval(x - 3.2f * d, y - 1.2f * d, x + 3.2f * d, y + 1.2f * d, paint);
        c.restore();
    }

    // ---------- Cubos de pollo ----------

    private void bucket(Canvas c, float w, float h, boolean striped) {
        float rimTop = 12 * d, rimBottom = 21 * d;
        float topInset = 4 * d, bottomInset = 15 * d;

        // Piezas de pollo asomando por encima del borde
        int crust = striped ? 0xFFC0622A : 0xFFC9702A;
        int crunch = striped ? 0xFFEBA24A : 0xFFF2AE52;
        float[][] pieces = { {.58f, -14}, {.68f, 12}, {.78f, -6}, {.88f, 18}, {.95f, -20}, {.10f, 10}, {.20f, -12} };
        for (float[] p : pieces) piece(c, w * p[0], rimTop + 3 * d, p[1], crust, crunch);

        // Cuerpo trapezoidal
        path.reset();
        path.moveTo(topInset, rimBottom - 2 * d);
        path.lineTo(w - topInset, rimBottom - 2 * d);
        path.lineTo(w - bottomInset + 2 * d, h - 10 * d);
        path.quadTo(w - bottomInset, h, w - bottomInset - 10 * d, h);
        path.lineTo(bottomInset + 10 * d, h);
        path.quadTo(bottomInset, h, bottomInset - 2 * d, h - 10 * d);
        path.close();
        int body = striped ? (oled ? 0xFF2A0F15 : 0xFFFFF6ED) : (oled ? 0xFF4A1F08 : 0xFFF7963F);
        fill(c, body);

        c.save();
        c.clipPath(path);
        float cx = w / 2, top = rimBottom - 2 * d;
        float spread = (w / 2 - bottomInset) / (w / 2 - topInset);
        if (striped) {
            paint.setColor(0xFFC72035);
            stripe(c, cx, spread, top, h, -1, topInset + 13 * d);
            for (float f : new float[] { .62f, .74f, .86f }) stripe(c, cx, spread, top, h, w * f, w * f + w * .06f);
            stripe(c, cx, spread, top, h, w - topInset - 13 * d, w + 1);
            letterBadge(c, w * .78f, (top + h) / 2 + 1 * d, Math.min(23 * d, (h - top) / 2 - 5 * d), "KFC");
        } else {
            paint.setColor(oled ? 0xFF6A2E0C : 0xFFE3772A);
            for (float f : new float[] { .60f, .70f, .80f, .90f }) {
                stripe(c, cx, spread, top, h, w * f, w * f + 9 * d);
            }
            // Zigzag picante
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f * d);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(oled ? 0xFFE96413 : 0xFFFFE8CE);
            path.reset();
            float zy = h - 11 * d;
            path.moveTo(0, zy);
            boolean up = true;
            for (float x = 8 * d; x < w + 8 * d; x += 8 * d) {
                path.lineTo(x, up ? zy - 4 * d : zy);
                up = !up;
            }
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            // Chiles estilizados
            chili(c, w * .80f, 48 * d);
        }
        paint.setColor(0x22000000);
        c.drawRect(0, top, w, top + 4 * d, paint);
        c.restore();

        // Borde superior del cubo
        paint.setColor(striped ? 0xFFC72035 : (oled ? 0xFFB8430E : 0xFFB8430E));
        c.drawRoundRect(0, rimTop, w, rimBottom, 4.5f * d, 4.5f * d, paint);
        paint.setColor(0x40FFFFFF);
        c.drawRoundRect(6 * d, rimTop + 2 * d, w - 6 * d, rimTop + 4 * d, d, d, paint);
    }

    private void letterBadge(Canvas c, float x, float y, float r, String letters) {
        paint.setColor(0x33000000);
        c.drawCircle(x, y + 1.5f * d, r, paint);
        paint.setColor(0xFFFFF6ED);
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * d);
        paint.setColor(0xFFC72035);
        c.drawCircle(x, y, r - 3 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(r * .78f);
        c.drawText(letters, x, y + r * .28f, paint);
        paint.setTypeface(null);
    }

    private void stripe(Canvas c, float cx, float spread, float top, float bottom, float l, float r) {
        path.reset();
        path.moveTo(l, top);
        path.lineTo(r, top);
        path.lineTo(cx + (r - cx) * spread, bottom);
        path.lineTo(cx + (l - cx) * spread, bottom);
        path.close();
        c.drawPath(path, paint);
    }

    private void piece(Canvas c, float x, float y, float rotation, int crust, int crunch) {
        c.save();
        c.translate(x, y);
        c.rotate(rotation);
        paint.setColor(crust);
        c.drawOval(-15 * d, -11 * d, 15 * d, 9 * d, paint);
        paint.setColor(crunch);
        c.drawOval(-11 * d, -9 * d, 9 * d, 3 * d, paint);
        paint.setColor(crust);
        c.drawCircle(-4 * d, -5 * d, 1.4f * d, paint);
        c.drawCircle(3 * d, -2 * d, 1.2f * d, paint);
        c.drawCircle(4 * d, -7 * d, d, paint);
        c.restore();
    }

    private void chili(Canvas c, float x, float y) {
        c.save();
        c.translate(x, y);
        c.rotate(-25);
        paint.setColor(oled ? 0xFFE0402A : 0xFFC9281A);
        path.reset();
        path.moveTo(-9 * d, -2 * d);
        path.quadTo(0, -6 * d, 10 * d, 4 * d);
        path.quadTo(1 * d, 3 * d, -9 * d, 3 * d);
        path.close();
        c.drawPath(path, paint);
        paint.setColor(0xFF3F8A3A);
        c.drawRoundRect(-12 * d, -2 * d, -8 * d, 2.5f * d, d, d, paint);
        c.restore();
    }

    // ---------- Trattoria ----------

    private void trattoria(Canvas c, float w, float h) {
        int red = oled ? 0xFF5A1322 : 0xFF8E1F33;
        int gold = 0xFFD9AE5B;
        paint.setColor(red);
        c.drawRoundRect(0, 0, w, h, 20 * d, 20 * d, paint);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
            oled ? 0x26FFD9A0 : 0x24FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(0, 0, w, h, 20 * d, 20 * d, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(gold);
        paint.setStrokeWidth(1.6f * d);
        c.drawRoundRect(6 * d, 6 * d, w - 6 * d, h - 6 * d, 15 * d, 15 * d, paint);
        paint.setStrokeWidth(.8f * d);
        paint.setAlpha(150);
        c.drawRoundRect(10 * d, 10 * d, w - 10 * d, h - 10 * d, 11 * d, 11 * d, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);

        flourish(c, 6 * d, h / 2, 1, gold);
        flourish(c, w - 6 * d, h / 2, -1, gold);
        diamond(c, w / 2, 6 * d, 3.2f * d, gold);
        diamond(c, w / 2, h - 6 * d, 3.2f * d, gold);

        // Medallón con hojas de laurel
        float mx = w - 44 * d, my = h / 2, r = Math.min(26 * d, h / 2 - 14 * d);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * d);
        paint.setColor(gold);
        c.drawCircle(mx, my, r, paint);
        paint.setStrokeWidth(.9f * d);
        c.drawCircle(mx, my, r - 4 * d, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 5; i++) {
                float angle = 115 + i * 26;
                c.save();
                c.translate(mx, my);
                c.rotate(side < 0 ? angle : 180 - angle);
                c.translate(r - 10 * d, 0);
                c.rotate(side * 40);
                c.drawOval(-3.6f * d, -1.6f * d, 3.6f * d, 1.6f * d, paint);
                c.restore();
            }
        }
        // Tres espigas de trigo en el centro
        paint.setStrokeWidth(1.2f * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = -1; i <= 1; i++) {
            float bx = mx + i * 5 * d;
            c.drawLine(bx, my + 9 * d, bx + i * 2 * d, my - 6 * d, paint);
            for (int g = 0; g < 3; g++) {
                float gy = my - 6 * d + g * 4 * d;
                float gx = bx + i * 2 * d * (1 - g / 4f);
                c.drawOval(gx - 1.4f * d, gy - 2.2f * d, gx + 1.4f * d, gy + 2.2f * d, paint);
            }
        }
    }

    private void flourish(Canvas c, float x, float y, int dir, int gold) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(gold);
        paint.setStrokeWidth(1.4f * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        path.moveTo(x, y - 12 * d);
        path.cubicTo(x + dir * 7 * d, y - 10 * d, x + dir * 7 * d, y - 3 * d, x + dir * 3 * d, y - 2 * d);
        path.moveTo(x, y + 12 * d);
        path.cubicTo(x + dir * 7 * d, y + 10 * d, x + dir * 7 * d, y + 3 * d, x + dir * 3 * d, y + 2 * d);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x + dir * 3 * d, y, 1.8f * d, paint);
    }

    private void diamond(Canvas c, float x, float y, float s, int color) {
        paint.setColor(color);
        path.reset();
        path.moveTo(x, y - s);
        path.lineTo(x + s * 1.6f, y);
        path.lineTo(x, y + s);
        path.lineTo(x - s * 1.6f, y);
        path.close();
        c.drawPath(path, paint);
    }

    // ---------- Taco envuelto ----------

    private void taco(Canvas c, float w, float h) {
        Path outline = new Path();
        outline.moveTo(0, h - 16 * d);
        outline.lineTo(0, 28 * d);
        outline.cubicTo(0, 6 * d, w * .10f, 0, w * .30f, 0);
        outline.lineTo(w * .70f, 0);
        outline.cubicTo(w * .90f, 0, w, 6 * d, w, 28 * d);
        outline.lineTo(w, h - 16 * d);
        outline.quadTo(w, h, w - 16 * d, h);
        outline.lineTo(16 * d, h);
        outline.quadTo(0, h, 0, h - 16 * d);
        outline.close();

        c.save();
        c.clipPath(outline);
        // Concha de maíz
        paint.setColor(0xFFF2B541);
        c.drawRect(0, 0, w, h, paint);
        // Relleno asomando: lechuga, tomate y queso
        lettuce(c, w, 0, 7 * d, 0xFF67A845);
        paint.setColor(0xFF5A9A3A);
        c.drawRect(0, 0, w, 3 * d, paint);
        paint.setColor(0xFFD8493D);
        for (float f = .07f; f < 1; f += .13f) {
            c.drawRoundRect(w * f, 5 * d, w * f + 6 * d, 10 * d, 1.5f * d, 1.5f * d, paint);
        }
        paint.setColor(0xFFFFD84A);
        paint.setStrokeWidth(1.6f * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (float f = .12f; f < 1; f += .11f) {
            c.drawLine(w * f, 8 * d, w * f + 5 * d, 11 * d, paint);
        }
        paint.setColor(0xFFD89432);
        for (float f = .05f; f < 1; f += .09f) {
            c.drawCircle(w * f, 15 * d + (f * 40 % 6) * d, 1.4f * d, paint);
        }

        // Envoltorio de papel con pliegue diagonal
        int wrap = oled ? 0xFF3F1C63 : 0xFF633296;
        int wrapLight = oled ? 0xFF55297F : 0xFF7A45AD;
        paint.setColor(wrapLight);
        path.reset();
        path.moveTo(0, 26 * d);
        path.lineTo(w * .55f, 21 * d);
        path.lineTo(w, 15 * d);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        c.drawPath(path, paint);
        paint.setColor(wrap);
        path.reset();
        path.moveTo(0, 29 * d);
        path.lineTo(w * .55f, 25 * d);
        path.lineTo(w, 19 * d);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        c.drawPath(path, paint);
        // Pliegue triangular del papel
        paint.setColor(wrapLight);
        path.reset();
        path.moveTo(w * .55f, 25 * d);
        path.lineTo(w * .62f, h);
        path.lineTo(w * .50f, h);
        path.close();
        paint.setAlpha(90);
        c.drawPath(path, paint);
        paint.setAlpha(255);
        c.restore();

        emblem(c, w - 40 * d, (29 * d + h) / 2 - 2 * d, Math.min(15 * d, (h - 29 * d) / 3));
    }

    /** Campana genérica de dibujo propio, sobre una placa redondeada del envoltorio. */
    private void emblem(Canvas c, float cx, float cy, float s) {
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(cx - s * 1.6f, cy - s * 1.6f, cx + s * 1.6f, cy + s * 1.6f, s * .7f, s * .7f, paint);
        paint.setColor(0xFFFFF0FD);
        path.reset();
        path.moveTo(cx - s, cy + s * .55f);
        path.cubicTo(cx - s * .55f, cy + s * .35f, cx - s * .75f, cy - s * .95f, cx, cy - s * .95f);
        path.cubicTo(cx + s * .75f, cy - s * .95f, cx + s * .55f, cy + s * .35f, cx + s, cy + s * .55f);
        path.close();
        c.drawPath(path, paint);
        c.drawRoundRect(cx - s * 1.08f, cy + s * .5f, cx + s * 1.08f, cy + s * .72f, s * .1f, s * .1f, paint);
        c.drawCircle(cx, cy - s * 1.05f, s * .18f, paint);
        c.drawCircle(cx, cy + s * .95f, s * .22f, paint);
        paint.setColor(0xFFFFD25A);
        c.drawRect(cx - s * .55f, cy - s * .1f, cx + s * .55f, cy + s * .05f, paint);
    }

    // ---------- Utilidades ----------

    private void fill(Canvas c, int color) {
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    private float[] radii(float tl, float tr, float br, float bl) {
        return new float[] { tl * d, tl * d, tr * d, tr * d, br * d, br * d, bl * d, bl * d };
    }

    private Rect px(int l, int t, int r, int b) {
        return new Rect(Math.round(l * d), Math.round(t * d), Math.round(r * d), Math.round(b * d));
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
