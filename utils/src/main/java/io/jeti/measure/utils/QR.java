package io.jeti.measure.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

public class QR {

    private final static int defaultQRSize = 500;
    private final static int qrForeground  = Color.argb(255, 0, 0, 0);
    private final static int transparent   = Color.argb(0, 255, 255, 255);

    public static Bitmap toBitmap(String string) throws WriterException {
        return toBitmap(string, defaultQRSize);
    }

    public static Bitmap toBitmap(String string, int qrSize) throws WriterException {
        BitMatrix bitMatrix;
        try {
            bitMatrix = new MultiFormatWriter().encode(string, BarcodeFormat.DATA_MATRIX.QR_CODE,
                    qrSize, qrSize, null);
        } catch (IllegalArgumentException Illegalargumentexception) {
            return null;
        }
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? qrForeground : transparent;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_4444);
        bitmap.setPixels(pixels, 0, qrSize, 0, 0, width, height);
        return bitmap;
    }

}
