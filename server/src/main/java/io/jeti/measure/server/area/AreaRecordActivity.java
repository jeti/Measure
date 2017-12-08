package io.jeti.measure.server.area;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;
import com.airbnb.lottie.LottieAnimationView;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.Tango.TangoUpdateCallback;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.atap.tangoservice.TangoXyzIjData;
import io.jeti.measure.R;
import io.jeti.measure.utils.TangoContainer;
import java.util.ArrayList;

/**
 * This Activity records a new area. Specifically, it will create an area file
 * descriptor. These files are important because memorizing an area will prevent
 * drift when taking subsequent measurements in that area.
 */
public class AreaRecordActivity extends Activity {

    private static final String TAG                = AreaRecordActivity.class.getSimpleName();
    private TangoContainer      tangoContainer;
    private final Object        tangoLock          = new Object();

    private LottieAnimationView recordButton;
    private EditText            nameEditText;
    private FrameLayout         background;

    private final Object        stateLock          = new Object();
    private boolean             localized          = false;
    private boolean             recording          = false;
    private int                 red;
    private int                 green;

    private static final int    SECS_TO_MILLISECS  = 1000;
    private double              mPreviousPoseTimeStamp;
    private double              mTimeToNextUpdate  = UPDATE_INTERVAL_MS;
    private static final double UPDATE_INTERVAL_MS = 1000.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.area_record_activity);

        background = findViewById(R.id.area_record_layout);
        recordButton = findViewById(R.id.area_record_animation);
        nameEditText = findViewById(R.id.area_save_name);
        nameEditText.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN)
                    && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                /* ENTER button was pressed. */
                new SaveAreaTask(tangoContainer, nameEditText.getText().toString()).execute();
                Toast.makeText(AreaRecordActivity.this, "Saving", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        red = ResourcesCompat.getColor(getResources(), R.color.red, null);
        green = ResourcesCompat.getColor(getResources(), R.color.green, null);

        FrameLayout recordSaveButton = findViewById(R.id.area_record_save_button);
        recordSaveButton.setOnClickListener(v -> {

            synchronized (stateLock) {

                if (!recording) {
                    /* Start recording */
                    recording = true;
                    localized = false;
                    background.setBackgroundColor(red);
                    recordButton.setVisibility(View.INVISIBLE);
                    nameEditText.setVisibility(View.VISIBLE);
                    final Tango tango = new Tango(AreaRecordActivity.this.getApplicationContext(),
                            () -> {
                                synchronized (tangoLock) {
                                    try {
                                        connect(tangoContainer.getTango());
                                        startupTango(tangoContainer.getTango());
                                    } catch (TangoErrorException | TangoInvalidException
                                            | SecurityException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                    synchronized (tangoLock) {
                        tangoContainer = new TangoContainer(tango, tangoLock);
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        synchronized (tangoLock) {
            if (tangoContainer != null) {
                tangoContainer.finish();
            }
        }
    }

    private void resetState() {
        synchronized (stateLock) {
            recording = false;
            localized = false;
            background.setBackgroundColor(Color.WHITE);
            recordButton.setVisibility(View.VISIBLE);
            nameEditText.setVisibility(View.INVISIBLE);
        }
    }

    private void connect(Tango tango) {
        boolean learning = true;
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, learning);
        tango.connect(config);
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other
     * parameters required after Tango connection. See
     * https://developers.google.com/tango/apis/java/java-area-learning for a
     * description of the coordinate frames.
     */
    private void startupTango(Tango tango) {

        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<>();

        /* This will be sent only once, when localization occurs */
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE));

        /*
         * These poses should have drift correction since they are with respect
         * to the learned area
         */
        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        tango.connectListener(framePairs, new TangoUpdateCallback() {

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                Log.i(TAG, "onPoseAvailable: " + pose);
                if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION
                        && pose.targetFrame == TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE) {
                    synchronized (stateLock) {
                        localized = pose.statusCode == TangoPoseData.POSE_VALID;
                    }
                }

                final double deltaTime = (pose.timestamp - mPreviousPoseTimeStamp)
                        * SECS_TO_MILLISECS;
                mPreviousPoseTimeStamp = pose.timestamp;
                mTimeToNextUpdate -= deltaTime;

                if (mTimeToNextUpdate < 0.0) {
                    mTimeToNextUpdate = UPDATE_INTERVAL_MS;

                    runOnUiThread(() -> {
                        synchronized (stateLock) {
                            if (localized) {
                                background.setBackgroundColor(green);
                            } else {
                                background.setBackgroundColor(red);
                            }
                        }
                    });
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                Log.i(TAG, "onXyzIjAvailable: " + xyzIj);
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData xyzij) {
                Log.i(TAG, "onPointCloudAvailable: " + xyzij);
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                Log.i(TAG, "onTangoEvent: " + event);
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                Log.i(TAG, "onFrameAvailable: " + cameraId);
            }
        });
    }
}
