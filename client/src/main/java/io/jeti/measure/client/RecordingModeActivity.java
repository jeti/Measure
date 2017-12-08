package io.jeti.measure.client;

import static io.jeti.measure.utils.CameraService.INTENT_FPS;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.WriterException;
import com.shawnlin.numberpicker.NumberPicker;
import io.jeti.measure.R;
import io.jeti.measure.utils.CameraAndPoseService;
import io.jeti.measure.utils.CaptureActivity;
import io.jeti.measure.utils.CaptureService;
import io.jeti.measure.utils.QR;

public class RecordingModeActivity extends CaptureActivity {

    private static final String TAG = RecordingModeActivity.class.getSimpleName();

    private NumberPicker        picker;
    private ImageView           qrImage;
    private TextView            qrText;
    private String              ip;
    private int                 port;

    @Override
    public void onCapture() {
        /* Start the next Activity in manual capture mode */
        Intent intent = new Intent(RecordingModeActivity.this, PoseScannerActivity.class);
        intent.putExtra(INTENT_FPS, -1);
        startActivity(intent);
    }

    @Override
    public void onPortOpen(int port) {
        this.port = port;
        attemptQRGeneration();
    }

    @Override
    public void onIPAcquired(String ip) {
        if (ip != null) {
            this.ip = ip;
            attemptQRGeneration();
        } else {
            Toast.makeText(this, "Couldn't obtain the IP address", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recording_mode_activity);

        qrImage = findViewById(R.id.qr_image);
        qrText = findViewById(R.id.qr_text);
        picker = findViewById(R.id.fps_picker);
        picker.setMinValue(1);
        picker.setMaxValue(30);

        final View pickerContainer = findViewById(R.id.fps_picker_container);
        final View qrContainer = findViewById(R.id.qr_container);
        pickerContainer.setVisibility(View.VISIBLE);
        qrContainer.setVisibility(View.INVISIBLE);

        final Switch automatic = findViewById(R.id.recording_mode_switch);
        automatic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                pickerContainer.setVisibility(View.VISIBLE);
                qrContainer.setVisibility(View.INVISIBLE);
            } else {
                pickerContainer.setVisibility(View.INVISIBLE);
                qrContainer.setVisibility(View.VISIBLE);
            }
        });

        findViewById(R.id.record_animation).setOnClickListener(v -> {
            /*
             * Turn off the capture service. We will be proceeding in automatic
             * mode. Then start the next activity.
             */
            stopService(new Intent(this, CaptureService.class));
            Intent intent = new Intent(RecordingModeActivity.this, PoseScannerActivity.class);
            intent.putExtra(INTENT_FPS, picker.getValue());
            startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopService(new Intent(this, CameraAndPoseService.class));
    }

    private void attemptQRGeneration() {
        if (ip != null && port != 0) {
            String addressAndPort = null;
            try {
                addressAndPort = ip + ":" + port;
                qrText.setText(addressAndPort);
                qrImage.setImageBitmap(QR.toBitmap(addressAndPort));
            } catch (WriterException e) {
                e.printStackTrace();
                Toast.makeText(RecordingModeActivity.this,
                        "Couldn't create a QR code from the text: " + addressAndPort,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void log(String s) {
        Log.e(TAG, s);
    }
}
