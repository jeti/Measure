package io.jeti.measure.utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.dm7.barcodescanner.zxing.ZXingScannerView;
import me.dm7.barcodescanner.zxing.ZXingScannerView.ResultHandler;

abstract public class QRScannerActivity extends Activity implements ResultHandler {

    public String getHeader() {
        return null;
    };

    public View bottomThird() {
        return null;
    }

    private ZXingScannerView scannerView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        final FrameLayout frameLayout = new FrameLayout(this);
        scannerView = new ZXingScannerView(this);
        frameLayout.addView(scannerView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        final String header = getHeader();
        final View bottomThird = bottomThird();
        if (header != null || bottomThird != null) {

            /* Create the layout that will hold the children */
            final LinearLayout linearLayout = new LinearLayout(this);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            frameLayout.addView(linearLayout,
                    new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            /* Create the top third */
            if (header == null) {
                linearLayout.addView(new View(this),
                        new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
            } else {
                final TextView textView = new TextView(this);
                textView.setTextColor(Color.WHITE);
                textView.setText(header);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 25);
                textView.setGravity(Gravity.CENTER);
                textView.setTypeface(Typeface.create("monospace", Typeface.NORMAL));
                linearLayout.addView(textView, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
            }

            /* Create the middle third */
            linearLayout.addView(new View(this), new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));

            /* Create the bottom third */
            if (bottomThird == null) {
                linearLayout.addView(new View(this),
                        new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
            } else {
                LinearLayout bottomThirdLayout = new LinearLayout(this);
                bottomThirdLayout.setOrientation(LinearLayout.VERTICAL);
                linearLayout.addView(bottomThirdLayout,
                        new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT,
                        WRAP_CONTENT);
                params.gravity = Gravity.CENTER;
                bottomThirdLayout.addView(bottomThird, params);
            }
        }
        setContentView(frameLayout);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (scannerView != null) {
            scannerView.setResultHandler(this);
            scannerView.startCamera();
        }
    }

    @Override
    public void onPause() {
        if (scannerView != null) {
            scannerView.stopCamera();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (scannerView != null) {
            scannerView = null;
        }
        super.onDestroy();
    }

    protected void stopCamera() {
        if (scannerView != null) {
            scannerView.stopCameraPreview();
            scannerView.stopCamera();
        }
    }

    protected void resumePreview() {
        if (scannerView != null) {
            scannerView.resumeCameraPreview(this);
        }
    }
}