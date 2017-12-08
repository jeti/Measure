package io.jeti.measure.client;

import static io.jeti.measure.utils.PoseService.POSE_ADDRESS_PORT;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.google.zxing.Result;
import io.jeti.measure.utils.QRScannerActivity;
import io.jeti.measure.utils.IPGetter;
import java.util.concurrent.atomic.AtomicReference;

public class PoseScannerActivity extends QRScannerActivity {

    private static final String     TAG    = PoseScannerActivity.class.getSimpleName();
    private AtomicReference<Bundle> extras = new AtomicReference<>(new Bundle());

    @Override
    public String getHeader() {
        return "Tango";
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        Intent intent;
        Bundle bundle;
        if ((intent = getIntent()) != null && (bundle = intent.getExtras()) != null) {
            extras.set(bundle);
        }
    }

    @Override
    public void handleResult(Result rawResult) {

        if (!IPGetter.validAddressAndPort(rawResult.getText())) {
            resumePreview();
        } else {
            stopCamera();
            Intent intent = new Intent(this, LoggerSetupActivity.class);
            intent.putExtras(extras.get());
            intent.putExtra(POSE_ADDRESS_PORT, rawResult.getText());
            startActivity(intent);
            /*
             * The ImageCaptureActivity has the behavior that a back-press will
             * stop the CaptureService (if it was started). Hence we need to
             * remove this activity from the back stack so that a back-press on
             * the ImageCaptureActivity will push the user back to the recording
             * mode selector (where the captureService is recreated.)
             */
            finish();
        }
    }

    private void log(String s) {
        Log.e(TAG, s);
    }
}