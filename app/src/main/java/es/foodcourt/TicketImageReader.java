package es.foodcourt;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

final class TicketImageReader {
    static String read(ContentResolver resolver, Uri uri, boolean member)
            throws IOException, ReaderException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    try (java.io.InputStream input = resolver.openInputStream(uri)) {
        BitmapFactory.decodeStream(input, null, options);
    }
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2000) {
        options.inSampleSize *= 2;
    }
    options.inJustDecodeBounds = false;
    Bitmap bitmap;
    try (java.io.InputStream input = resolver.openInputStream(uri)) {
        bitmap = BitmapFactory.decodeStream(input, null, options);
    }
    if (bitmap == null) throw new java.io.IOException("Imagen no disponible");
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    bitmap.recycle();
    Map<DecodeHintType, ?> hints = member
        ? Collections.singletonMap(DecodeHintType.POSSIBLE_FORMATS, Collections.singleton(BarcodeFormat.QR_CODE))
        : Collections.singletonMap(DecodeHintType.TRY_HARDER, true);
    BinaryBitmap source = new BinaryBitmap(new HybridBinarizer(
        new RGBLuminanceSource(width, height, pixels)));
    return new MultiFormatReader().decode(source, hints).getText();
    }
}
