package io.jeti.measure.client;

import static io.jeti.measure.utils.CameraAndPoseService.LOGGER_ADDRESS_PORT;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import com.google.zxing.Result;
import io.jeti.measure.utils.PoseService;
import io.jeti.measure.utils.QRScannerActivity;
import io.jeti.measure.utils.IPGetter;
import java.util.concurrent.atomic.AtomicReference;

public class LoggerSetupActivity extends QRScannerActivity {

    private AtomicReference<Bundle> extras = new AtomicReference<>(new Bundle());

    @Override
    public String getHeader() {
        return "Logger";
    }

    @Override
    public View bottomThird() {
        Button button = new Button(this);
        button.setText("Do not use a logger");
        button.setGravity(Gravity.CENTER);
        return button;
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

        /*
         * If this is the same address and port as the pose listener, then that
         * can't be correct... Resume scanning.
         */
        Bundle bundle = extras.get();
        if ((bundle != null
                && rawResult.getText().equals(bundle.getString(PoseService.POSE_ADDRESS_PORT)))
                || !IPGetter.validAddressAndPort(rawResult.getText())) {

            resumePreview();

        } else {

            stopCamera();
            Intent intent = new Intent(this, ImageCaptureActivity.class);
            intent.putExtras(extras.get());
            intent.putExtra(LOGGER_ADDRESS_PORT, rawResult.getText());
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

}
