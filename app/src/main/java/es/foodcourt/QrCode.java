package es.foodcourt;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.EncodeHintType;
import java.util.Collections;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

final class QrCode {
    static Bitmap render(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 360, 360,
                content.matches("[\\x00-\\x7F]*") ? Collections.emptyMap()
                    : Collections.singletonMap(EncodeHintType.CHARACTER_SET, "UTF-8"));
            int size = matrix.getWidth();
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = matrix.get(x, y) ? 0xFF171411 : 0xFFFFFFFF;
                }
            }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        } catch (WriterException error) {
            throw new IllegalStateException("Error al generar el QR", error);
        }
    }
}
