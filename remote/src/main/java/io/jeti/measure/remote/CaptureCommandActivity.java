package io.jeti.measure.remote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import io.jeti.measure.R;
import io.jeti.measure.utils.PortBroadcaster;
import io.jeti.measure.utils.ZMQSource;

public class CaptureCommandActivity extends Activity {

    private static final String TAG            = CaptureCommandActivity.class.getSimpleName();
    private final Object        zmqSourceLock  = new Object();
    private ZMQSource           zmqSource      = null;
    private String              addressAndPort = null;
    private final byte[]        captureBytes   = new byte[0];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.capture_command_activity);
        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        findViewById(R.id.capture_animation).setOnClickListener((View v) -> {

            vibrator.vibrate(20);
            /* TODO: This should be a read lock */
            synchronized (zmqSourceLock) {
                if (zmqSource != null) {
                    zmqSource.send(captureBytes);
                    log("Sent capture request to address:port = " + addressAndPort);
                } else {
                    log("Could not send capture request. The source is not opened. Address:Port = "
                            + addressAndPort);
                }
            }
        });

        Intent intent;
        if ((intent = getIntent()) != null) {
            if ((addressAndPort = intent.getStringExtra(
                io.jeti.measure.remote.GetClientAddressActivity.SCAN_RESULT)) != null) {
                createPublisherIfNull();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        createPublisherIfNull();
    }

    @Override
    public void onDestroy() {
        synchronized (zmqSourceLock) {
            if (zmqSource != null) {
                zmqSource.stopAndDestroy();
            }
        }
        super.onDestroy();
    }

    public void createPublisherIfNull() {
        synchronized (zmqSourceLock) {
            if (zmqSource == null && addressAndPort != null) {
                zmqSource = ZMQSource.createAndStart(addressAndPort);
            }
            PortBroadcaster.broadcast(this, zmqSource.getPort());
        }
    }

    private void log(String s) {
        Log.e(TAG, s);
    }
}
