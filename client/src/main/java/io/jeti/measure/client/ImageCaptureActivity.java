package io.jeti.measure.client;

import static io.jeti.measure.utils.CameraAndPoseService.LOGGER_ADDRESS_PORT;
import static io.jeti.measure.utils.PoseService.POSE_ADDRESS_PORT;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import io.jeti.measure.R;
import io.jeti.measure.utils.CameraAndPoseService;
import io.jeti.measure.utils.CameraPreview;
import io.jeti.measure.utils.PortListenerActivity;
import io.jeti.measure.utils.PoseSerializer;
import io.jeti.measure.utils.ZMQSink;
import java.util.concurrent.atomic.AtomicReference;

public class ImageCaptureActivity extends PortListenerActivity {

    private static final String     TAG                = ImageCaptureActivity.class.getSimpleName();
    private CameraPreview           cameraPreview      = null;
    private TextView                qrTextView         = null;
    private TextView                textView           = null;
    private ZMQSink                 poseSink           = null;
    private String                  poseAddressAndPort = null;
    private AtomicReference<Bundle> extras             = new AtomicReference<>(new Bundle());

    @Override
    public void onPortOpen(int port) {
    }

    @Override
    public void onIPAcquired(String ip) {
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.image_capture_activity);
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.setCameraWrapper(CameraAndPoseService.cameraWrappers[0]);
        qrTextView = findViewById(R.id.qr_text);
        textView = findViewById(R.id.text);

        Intent intent;
        if ((intent = getIntent()) != null) {
            String tmpPoseAddressAndPort = intent.getStringExtra(POSE_ADDRESS_PORT);
            if (tmpPoseAddressAndPort != null) {
                poseAddressAndPort = tmpPoseAddressAndPort;
                qrTextView.setText("Tango\n" + poseAddressAndPort + "\n");
                createSubscriberIfNull();
                extras.set(intent.getExtras());
            }

            String loggerAddressAndPort = intent.getStringExtra(LOGGER_ADDRESS_PORT);
            if (loggerAddressAndPort != null) {
                qrTextView.setText(qrTextView.getText() + "Logger\n" + loggerAddressAndPort);
                extras.set(intent.getExtras());
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopCameraServices();
    }

    @Override
    public void onResume() {
        super.onResume();
        createSubscriberIfNull();
        startCameraServices();
    }

    @Override
    public void onPause() {
        if (poseSink != null) {
            poseSink.stopAndDestroy();
            poseSink = null;
        }
        super.onPause();
    }

    public void createSubscriberIfNull() {
        if (poseSink == null && poseAddressAndPort != null) {
            poseSink = ZMQSink.createAndStart(textView, new PoseSerializer(), poseAddressAndPort);
        }
    }

    private void log(String s) {
        Log.e(TAG, s);
    }

    private void startCameraServices() {
        Bundle bundle;
        if ((bundle = extras.get()) != null) {
            startService(new Intent(this, CameraAndPoseService.class).putExtras(bundle));
        }
    }

    private void stopCameraServices() {
        stopService(new Intent(this, CameraAndPoseService.class));
    }
}