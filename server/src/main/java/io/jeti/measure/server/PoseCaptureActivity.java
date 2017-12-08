package io.jeti.measure.server;

import static io.jeti.measure.server.area.AreaListActivity.PRIMARY_KEY_EXTRA;
import static io.jeti.measure.utils.PortBroadcaster.PORT_INTENT;
import static io.jeti.measure.utils.PortBroadcaster.PORT_EXTRA;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.WriterException;
import io.jeti.measure.R;
import io.jeti.measure.utils.PoseSerializer;
import io.jeti.measure.utils.PoseService;
import io.jeti.measure.utils.ZMQSink;
import io.jeti.measure.utils.IPGetter;
import io.jeti.measure.utils.QR;

/**
 * This Activity shows you the camera output with a QR code overlaid. In the
 * background, this Activity starts a foreground {@link PoseService} that
 * provides poses of the current device localized with respect to the chosen
 * area. The QR code contains the ip address and port of a server on this device
 * in the format (ip:port).
 */
public class PoseCaptureActivity extends Activity {

    private final int         qrBackground     = Color.argb(127, 255, 255, 255);

    private BroadcastReceiver portListener     = null;
    private boolean           showingQR        = true;
    private String            ip;
    private int               port;

    private ImageView         imageView;
    private TextView          qrTextView;
    private TextView          textView;

    private final Object      poseListenerLock = new Object();
    private ZMQSink           poseSink         = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pose_capture_activity);

        FrameLayout frameLayout = findViewById(R.id.measure_activity_layout);

        final LinearLayout layout = findViewById(R.id.qr_container);
        layout.setBackgroundColor(qrBackground);

        imageView = findViewById(R.id.qr_image);
        qrTextView = findViewById(R.id.qr_text);
        textView = findViewById(R.id.text);

        frameLayout.setOnClickListener(v -> {
            /* TODO: you may want to enable this. */
            // showingQR = !showingQR;
            if (showingQR) {
                layout.setVisibility(View.VISIBLE);
            } else {
                layout.setVisibility(View.INVISIBLE);
            }
        });

        /*
         * Create a broadcast receiver that will be registered/unregistered in
         * onResume/onPause. This broadcast receiver simply listens for the port
         * of the Server created by the PoseService.
         */
        portListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                port = intent.getIntExtra(PORT_EXTRA, 0);
                attemptQRGeneration();
            }
        };

        /*
         * If we got an area uuid, start the pose service which localizes
         * measurements with respect to that area. Note that to stop the
         * service, you MUST press the back button.
         */
        Intent intent;
        if ((intent = getIntent()) != null && intent.getStringExtra(PRIMARY_KEY_EXTRA) != null) {
            startService(new Intent(this, PoseService.class).putExtras(intent));
        } else {
            Toast.makeText(this,
                    "Could not start the background service. "
                            + "No area UUID received on activity startup.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopService(new Intent(this, PoseService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(portListener,
                new IntentFilter(PORT_INTENT));
        new IPGetter(ip -> {
            this.ip = ip;
            if (ip == null) {
                Toast.makeText(PoseCaptureActivity.this, "Couldn't obtain the IP address",
                        Toast.LENGTH_SHORT).show();
            }
            attemptQRGeneration();
        }).execute();
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(portListener);
        synchronized (poseListenerLock) {
            if (poseSink != null) {
                poseSink.stopAndDestroy();
                poseSink = null;
            }
        }
        super.onPause();
    }

    private void attemptQRGeneration() {
        if (ip != null && port != 0) {
            String addressAndPort = null;
            try {
                addressAndPort = ip + ":" + port;
                qrTextView.setText(addressAndPort);
                imageView.setImageBitmap(QR.toBitmap(addressAndPort));
                createSubscriberIfNull(addressAndPort);
            } catch (WriterException e) {
                e.printStackTrace();
                Toast.makeText(PoseCaptureActivity.this,
                        "Couldn't create a QR code from the text: " + addressAndPort,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void createSubscriberIfNull(final String addressAndPort) {
        synchronized (poseListenerLock) {
            if (poseSink == null) {
                poseSink = ZMQSink.createAndStart(textView, new PoseSerializer(), addressAndPort);
            }
        }
    }
}
