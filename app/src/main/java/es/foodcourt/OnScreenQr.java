package es.foodcourt;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.common.HybridBinarizer;

final class OnScreenQr {
    static String decode(ScreenshotResult screenshot, Rect bounds) {
        try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (hardware == null) return "";
            Rect image = new Rect(0, 0, hardware.getWidth(), hardware.getHeight());
            if (!image.intersect(bounds) || image.width() < 40 || image.height() < 40) {
                hardware.recycle();
                return "";
            }
            Bitmap soft = hardware.copy(Bitmap.Config.ARGB_8888, false);
            hardware.recycle();
            if (soft == null) return "";
            int[] pixels = new int[image.width() * image.height()];
            soft.getPixels(pixels, 0, image.width(), image.left, image.top, image.width(), image.height());
            soft.recycle();
            BinaryBitmap qr = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(
                image.width(), image.height(), pixels)));
            return new QRCodeReader().decode(qr).getText();
        } catch (com.google.zxing.ReaderException | RuntimeException unreadable) {
            return "";
        }
    }
}
