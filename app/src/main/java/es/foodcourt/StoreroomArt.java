package es.foodcourt;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Drawings for the card storeroom: tiled wall, metal shelving, cardboard boxes and brand emblems. */
final class StoreroomArt {
    private StoreroomArt() { }

    private abstract static class Art extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float dp;

        Art(float density) { dp = density; }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** White kitchen tiles with grout lines, or dark tiles in the OLED theme. */
    static final class TileWall extends Art {
        private final boolean oled;

        TileWall(boolean oled, float density) {
            super(density);
            this.oled = oled;
        }

        @Override public void draw(Canvas canvas) {
            Rect area = getBounds();
            paint.setShader(null);
            paint.setColor(oled ? 0xFF050607 : 0xFFD9D3C7);
            canvas.drawRect(area, paint);
            float width = 46 * dp, height = 23 * dp, grout = 1.6f * dp;
            int row = 0;
            for (float y = area.top; y < area.bottom; y += height, row++) {
                float shift = row % 2 == 0 ? 0 : -width / 2;
                for (float x = area.left + shift; x < area.right; x += width) {
                    RectF tile = new RectF(x + grout, y + grout, x + width - grout, y + height - grout);
                    paint.setShader(new LinearGradient(0, tile.top, 0, tile.bottom,
                        oled ? 0xFF1A1D22 : 0xFFFFFFFF, oled ? 0xFF111317 : 0xFFF1EEE7, Shader.TileMode.CLAMP));
                    canvas.drawRoundRect(tile, 2 * dp, 2 * dp, paint);
                }
            }
            paint.setShader(null);
        }
    }

    /** Brushed stainless steel, like a kitchen pass or worktop, with rivets in the corners. */
    static final class SteelPanel extends Art {
        private final boolean oled;

        SteelPanel(boolean oled, float density) {
            super(density);
            this.oled = oled;
        }

        @Override public void draw(Canvas canvas) {
            RectF area = new RectF(getBounds());
            float radius = 14 * dp;
            paint.setShader(new LinearGradient(area.left, area.top, area.right, area.bottom,
                oled ? new int[] { 0xFF2C3036, 0xFF1C1F23, 0xFF30343A, 0xFF1A1C20 }
                    : new int[] { 0xFFE9ECEF, 0xFFC9CED4, 0xFFE2E6EA, 0xFFBFC5CC },
                null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(area, radius, radius, paint);
            paint.setShader(null);
            paint.setStrokeWidth(dp * .6f);
            paint.setColor(oled ? 0x14FFFFFF : 0x22FFFFFF);
            for (float y = area.top + 3 * dp; y < area.bottom - 2 * dp; y += 3 * dp) {
                canvas.drawLine(area.left + radius / 2, y, area.right - radius / 2, y, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * dp);
            paint.setColor(oled ? 0xFF454B53 : 0xFF9AA2AB);
            canvas.drawRoundRect(area, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            float inset = 9 * dp;
            for (float[] p : new float[][] { { area.left + inset, area.top + inset }, { area.right - inset, area.top + inset },
                    { area.left + inset, area.bottom - inset }, { area.right - inset, area.bottom - inset } }) {
                paint.setColor(oled ? 0xFF0E1012 : 0xFF8C949D);
                canvas.drawCircle(p[0], p[1] + .6f * dp, 2.6f * dp, paint);
                paint.setColor(oled ? 0xFF5A616A : 0xFFF4F6F8);
                canvas.drawCircle(p[0], p[1], 2.2f * dp, paint);
            }
        }
    }

    /**
     * One level of a metal shelving unit: perforated uprights on both sides and a steel shelf
     * along the bottom. Stacked rows join into a single unit.
     */
    static final class ShelfRow extends Art {
        private final boolean oled, top;

        ShelfRow(boolean oled, boolean top, float density) {
            super(density);
            this.oled = oled;
            this.top = top;
        }

        static int postWidth(float density) { return Math.round(11 * density); }
        static int shelfHeight(float density) { return Math.round(15 * density); }

        @Override public void draw(Canvas canvas) {
            Rect area = getBounds();
            float post = postWidth(dp), shelf = shelfHeight(dp);
            // Back panel: a darker recess between the uprights.
            paint.setShader(new LinearGradient(0, area.top, 0, area.bottom,
                oled ? 0xCC0B0D10 : 0x66A9A49A, oled ? 0xCC101317 : 0x40BDB7AC, Shader.TileMode.CLAMP));
            canvas.drawRect(area.left + post, area.top, area.right - post, area.bottom - shelf, paint);
            paint.setShader(null);
            drawPost(canvas, area.left, area.top, area.bottom);
            drawPost(canvas, area.right - post, area.top, area.bottom);
            if (top) {
                // Cap plate on the top of the unit.
                paint.setColor(oled ? 0xFF5C636C : 0xFF8E969F);
                canvas.drawRect(area.left, area.top, area.right, area.top + 4 * dp, paint);
            }
            // Steel shelf with a front lip, highlight and a soft shadow below.
            float y = area.bottom - shelf;
            paint.setShader(new LinearGradient(0, y, 0, y + shelf,
                oled ? new int[] { 0xFF6D757F, 0xFF3B4148, 0xFF262A2F } : new int[] { 0xFFF3F5F7, 0xFFB8BFC7, 0xFF8F98A2 },
                new float[] { 0, .45f, 1 }, Shader.TileMode.CLAMP));
            canvas.drawRect(area.left, y, area.right, y + shelf, paint);
            paint.setShader(null);
            paint.setColor(oled ? 0x33FFFFFF : 0xCCFFFFFF);
            canvas.drawRect(area.left, y, area.right, y + 1.4f * dp, paint);
            paint.setColor(oled ? 0xFF15181B : 0xFF7A838D);
            canvas.drawRect(area.left, y + shelf - 2 * dp, area.right, y + shelf, paint);
            // Slots along the lip, like a wire/slotted steel shelf.
            paint.setColor(oled ? 0x55000000 : 0x33000000);
            for (float x = area.left + post + 10 * dp; x < area.right - post - 10 * dp; x += 16 * dp) {
                canvas.drawRoundRect(new RectF(x, y + shelf * .45f, x + 9 * dp, y + shelf * .62f), dp, dp, paint);
            }
        }

        private void drawPost(Canvas canvas, float left, float topY, float bottom) {
            float post = postWidth(dp);
            paint.setShader(new LinearGradient(left, 0, left + post, 0,
                oled ? new int[] { 0xFF3A4047, 0xFF6E7781, 0xFF2D3238 } : new int[] { 0xFF8C959F, 0xFFE6EAEE, 0xFF8A939D },
                null, Shader.TileMode.CLAMP));
            canvas.drawRect(left, topY, left + post, bottom, paint);
            paint.setShader(null);
            paint.setColor(oled ? 0xFF0B0C0E : 0xFF5F6872);
            for (float y = topY + 6 * dp; y < bottom - 4 * dp; y += 9 * dp) {
                canvas.drawRoundRect(new RectF(left + post * .32f, y, left + post * .68f, y + 4.5f * dp),
                    1.5f * dp, 1.5f * dp, paint);
            }
        }
    }

    /** A cardboard box with flaps, packing tape and a white label where the brand goes. */
    static final class Box extends Art {
        private final boolean oled;
        private final int brand;

        Box(boolean oled, int brand, float density) {
            super(density);
            this.oled = oled;
            this.brand = brand;
        }

        /** Area of the white label, relative to the box bounds. */
        static RectF label(Rect box) {
            return new RectF(box.left + box.width() * .12f, box.top + box.height() * .30f,
                box.right - box.width() * .12f, box.bottom - box.height() * .14f);
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            RectF box = new RectF(b.left + 2 * dp, b.top + 4 * dp, b.right - 2 * dp, b.bottom);
            paint.setColor(0x40000000);
            canvas.drawRoundRect(new RectF(box.left + 2 * dp, box.top + 3 * dp, box.right + dp, box.bottom),
                4 * dp, 4 * dp, paint);
            paint.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                oled ? 0xFF8A6A45 : 0xFFD9AE76, oled ? 0xFF6B4F31 : 0xFFC08E55, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(box, 4 * dp, 4 * dp, paint);
            paint.setShader(null);
            // Top flaps meeting in the middle.
            float flap = box.height() * .2f;
            paint.setColor(oled ? 0xFF7A5C3B : 0xFFCD9F64);
            canvas.drawRect(box.left, box.top, box.right, box.top + flap, paint);
            paint.setColor(oled ? 0x55000000 : 0x40704A1E);
            paint.setStrokeWidth(1.2f * dp);
            canvas.drawLine(box.left, box.top + flap, box.right, box.top + flap, paint);
            // Packing tape across the flaps, tinted with the brand colour.
            float tape = box.width() * .16f, cx = box.centerX();
            paint.setColor((brand & 0x00FFFFFF) | 0xB0000000);
            canvas.drawRect(cx - tape / 2, box.top, cx + tape / 2, box.top + flap + 6 * dp, paint);
            paint.setColor(0x33FFFFFF);
            canvas.drawRect(cx - tape / 2, box.top, cx - tape / 2 + 2 * dp, box.top + flap + 6 * dp, paint);
            // Label.
            RectF label = label(b);
            paint.setColor(oled ? 0xFFE9E3D6 : 0xFFFFFCF5);
            canvas.drawRoundRect(label, 4 * dp, 4 * dp, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(oled ? 0x553A2A18 : 0x339A7A50);
            canvas.drawRoundRect(label, 4 * dp, 4 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
            // «This side up» arrows printed on the cardboard.
            paint.setColor(oled ? 0x66000000 : 0x55704A1E);
            float ax = box.right - 9 * dp, ay = box.top + flap + 9 * dp;
            Path arrow = new Path();
            arrow.moveTo(ax, ay - 4 * dp);
            arrow.lineTo(ax + 3 * dp, ay);
            arrow.lineTo(ax - 3 * dp, ay);
            arrow.close();
            canvas.drawPath(arrow, paint);
        }
    }

    /** Round badge with a stylised mark per brand (colours, initials and a simple symbol). */
    static final class Emblem extends Art {
        private final String id;

        Emblem(String id, float density) {
            super(density);
            this.id = id;
        }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height());
            float cx = b.exactCenterX(), cy = b.exactCenterY(), r = size / 2;
            canvas.save();
            canvas.translate(cx - r, cy - r);
            canvas.scale(size / 100f, size / 100f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33000000);
            canvas.drawCircle(50, 53, 48, paint);
            switch (id) {
                case "mcdonalds": mcdonalds(canvas); break;
                case "burger-king-es": burgerKing(canvas); break;
                case "popeyes-es": popeyes(canvas); break;
                case "kfc-es": kfc(canvas); break;
                case "taco-bell-es": tacoBell(canvas); break;
                case "tagliatella-amici": tagliatella(canvas); break;
                case "club-by-es": clubBy(canvas); break;
                default: disc(canvas, 0xFF7A5C44); break;
            }
            // Glossy highlight.
            paint.setShader(new RadialGradient(36, 26, 40, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawCircle(50, 50, 47, paint);
            paint.setShader(null);
            canvas.restore();
        }

        private void disc(Canvas canvas, int color) {
            paint.setColor(color);
            canvas.drawCircle(50, 50, 47, paint);
        }

        private void text(Canvas canvas, String value, float x, float y, float size, int color, Typeface face) {
            paint.setColor(color);
            paint.setTypeface(face);
            paint.setTextSize(size);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(value, x, y, paint);
        }

        private void mcdonalds(Canvas canvas) {
            disc(canvas, 0xFFDA291C);
            // Two golden arches.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStrokeWidth(9);
            paint.setColor(0xFFFFC72C);
            Path arches = new Path();
            arches.moveTo(22, 74);
            arches.cubicTo(22, 20, 47, 20, 50, 56);
            arches.cubicTo(53, 20, 78, 20, 78, 74);
            canvas.drawPath(arches, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void burgerKing(Canvas canvas) {
            disc(canvas, 0xFFF5EBDC);
            paint.setColor(0xFFF2A24A); // Top bun.
            canvas.drawArc(new RectF(16, 16, 84, 70), 180, 180, true, paint);
            paint.setColor(0xFFF2A24A); // Bottom bun.
            canvas.drawRoundRect(new RectF(18, 64, 82, 82), 10, 10, paint);
            paint.setColor(0xFFD62300);
            canvas.drawRoundRect(new RectF(14, 38, 86, 64), 8, 8, paint);
            text(canvas, "BK", 50, 59, 24, 0xFFFFF4E0, Typeface.create("sans-serif-black", Typeface.BOLD));
        }

        private void popeyes(Canvas canvas) {
            disc(canvas, 0xFFFF7900);
            // Drumstick.
            paint.setColor(0xFFFFE2BF);
            canvas.drawCircle(70, 68, 7, paint);
            canvas.drawCircle(77, 63, 6, paint);
            paint.setColor(0xFFB54A10);
            canvas.drawOval(new RectF(48, 46, 74, 66), paint);
            text(canvas, "P", 38, 68, 52, 0xFFFFFFFF, Typeface.create("serif", Typeface.BOLD_ITALIC));
        }

        private void kfc(Canvas canvas) {
            disc(canvas, 0xFFFFFFFF);
            // Striped bucket.
            Path bucket = new Path();
            bucket.moveTo(24, 30);
            bucket.lineTo(76, 30);
            bucket.lineTo(68, 86);
            bucket.lineTo(32, 86);
            bucket.close();
            canvas.save();
            canvas.clipPath(bucket);
            for (int i = 0; i < 7; i++) {
                paint.setColor(i % 2 == 0 ? 0xFFE4002B : 0xFFFFFFFF);
                canvas.drawRect(24 + i * 7.5f, 28, 24 + (i + 1) * 7.5f, 88, paint);
            }
            canvas.restore();
            paint.setColor(0xFFE4002B);
            canvas.drawRoundRect(new RectF(20, 22, 80, 33), 4, 4, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(new RectF(30, 48, 70, 66), 4, 4, paint);
            text(canvas, "KFC", 50, 63, 15, 0xFF1F1A17, Typeface.create("sans-serif-black", Typeface.BOLD));
        }

        private void tacoBell(Canvas canvas) {
            disc(canvas, 0xFF702082);
            // Bell.
            paint.setColor(0xFFFFFFFF);
            Path bell = new Path();
            bell.moveTo(50, 20);
            bell.cubicTo(32, 20, 30, 40, 30, 54);
            bell.lineTo(22, 70);
            bell.lineTo(78, 70);
            bell.lineTo(70, 54);
            bell.cubicTo(70, 40, 68, 20, 50, 20);
            canvas.drawPath(bell, paint);
            canvas.drawCircle(50, 76, 6, paint);
            paint.setColor(0xFFA77BCA);
            canvas.drawRect(26, 62, 74, 66, paint);
        }

        private void tagliatella(Canvas canvas) {
            disc(canvas, 0xFF7A1F2E);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(0xFFE8C178);
            canvas.drawCircle(50, 50, 40, paint);
            paint.setStyle(Paint.Style.FILL);
            text(canvas, "LT", 50, 60, 32, 0xFFF4D79B, Typeface.create("serif", Typeface.BOLD));
            // Small olive branch.
            paint.setColor(0xFF9DB36A);
            canvas.drawOval(new RectF(38, 68, 48, 74), paint);
            canvas.drawOval(new RectF(52, 68, 62, 74), paint);
        }

        private void clubBy(Canvas canvas) {
            disc(canvas, 0xFF152C4E);
            // Four seats around a table, one per brand colour.
            int[] seats = { 0xFFE62740, 0xFF2F57B8, 0xFF1F8F64, 0xFFC8283C };
            float[][] at = { { 50, 22 }, { 78, 50 }, { 50, 78 }, { 22, 50 } };
            for (int i = 0; i < 4; i++) {
                paint.setColor(seats[i]);
                canvas.drawCircle(at[i][0], at[i][1], 10, paint);
            }
            paint.setColor(0xFFD5A779);
            canvas.drawCircle(50, 50, 17, paint);
            text(canvas, "c·b", 50, 56, 15, 0xFF152C4E, Typeface.create("sans-serif", Typeface.BOLD));
        }
    }
}
