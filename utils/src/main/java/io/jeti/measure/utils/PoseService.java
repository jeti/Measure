package io.jeti.measure.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

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

import java.util.ArrayList;

public class PoseService extends Service {

    public static final String   PRIMARY_KEY_EXTRA = "PRIMARY_KEY";
    public static final String   POSE_ADDRESS_PORT = "POSE_ADDRESS_PORT";

    private static final String  TAG               = PoseService.class.getSimpleName();
    private static final int     notificationID    = View.generateViewId();

    private final Object         zmqSourceLock     = new Object();
    private ZMQSource            zmqSource         = null;

    private long                 sent              = 0;
    private Throttle             throttle          = new Throttle(2);

    private TangoContainer       tangoContainer;
    private final Object         tangoLock         = new Object();
    private final PoseSerializer poseSerializer    = new PoseSerializer();

    public Notification getNotification() {
        return new Notification.Builder(this).setTicker(TAG).setContentTitle(TAG).setOngoing(true)
                .setSmallIcon(android.R.drawable.stat_notify_sync).build();
    }

    public void log(String s) {
        Log.e("PoseService", s);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Push this Service to the Foreground to minimize the chance that it
         * gets cleaned up.
         */
        Notification notification = getNotification();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(notificationID,
                notification);
        startForeground(notificationID, notification);

        /* Start up the publisher */
        createPublisherIfNull();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        /* If we got an area uuid, try to connect to Tango. */
        String areaUUID;
        if (intent != null && (areaUUID = intent.getStringExtra(PRIMARY_KEY_EXTRA)) != null) {
            log(areaUUID);
            createTangoIfNull(areaUUID);
        } else {
            Toast.makeText(this, "Could not start Tango. No area UUID received on service startup.",
                    Toast.LENGTH_SHORT).show();
        }

        /* Start up the publisher if it doesn't already exist */
        createPublisherIfNull();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        synchronized (zmqSourceLock) {
            if (zmqSource != null) {
                zmqSource.stopAndDestroy();
            }
        }
        synchronized (tangoLock) {
            if (tangoContainer != null) {
                tangoContainer.finish();
            }
        }
        stopForeground(true);
        super.onDestroy();
    }

    public void createPublisherIfNull() {
        synchronized (zmqSourceLock) {
            if (zmqSource == null) {
                zmqSource = ZMQSource.createAndStart();
            }
            PortBroadcaster.broadcast(this, zmqSource.getPort());
        }
    }

    private void createTangoIfNull(final String areaUUID) {
        synchronized (tangoLock) {
            if (tangoContainer == null) {
                final Tango tango = new Tango(PoseService.this.getApplicationContext(), () -> {
                    synchronized (tangoLock) {
                        try {
                            connect(tangoContainer.getTango(), areaUUID);
                            startupTango(tangoContainer.getTango());
                        } catch (TangoErrorException | TangoInvalidException
                                | SecurityException e) {
                            e.printStackTrace();
                        }
                    }
                });
                tangoContainer = new TangoContainer(tango, tangoLock);
            } else {
                Toast.makeText(this,
                        "A Tango connection already exists. If you want to use a different area, destroy that connection first.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void connect(Tango tango, String areaUUID) {
        boolean learning = true;
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_LEARNINGMODE, learning);
        // config.putString(TangoConfig.KEY_STRING_AREADESCRIPTION, areaUUID);
        tango.connect(config);
        log("Tango connected");
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

        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        tango.connectListener(framePairs, new TangoUpdateCallback() {

            @Override
            public void onPoseAvailable(TangoPoseData pose) {

                byte[] bytes = poseSerializer.encode(pose.translation, pose.rotation);
                if (zmqSource != null) {
                    sent++;
                    zmqSource.send(bytes);
                    /* Log at a slower frequency */
                    if (throttle.ready()) {
                        log("Sent " + String.format("%6d", sent) + " messages. Current "
                                + poseSerializer.toString(poseSerializer.decode(bytes)));
                    }
                }

                if (pose.baseFrame == TangoPoseData.COORDINATE_FRAME_AREA_DESCRIPTION) {
                    if (pose.statusCode == TangoPoseData.POSE_VALID) {
                        /*
                         * When everything is working, the outer code should be
                         * placed here.
                         */
                    }
                }
            }

            @Override
            public void onXyzIjAvailable(TangoXyzIjData xyzIj) {
                log("onXyzIjAvailable: " + xyzIj);
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData xyzij) {
                log("onPointCloudAvailable: " + xyzij);
            }

            @Override
            public void onTangoEvent(final TangoEvent event) {
                log("onTangoEvent: " + event);
            }

            @Override
            public void onFrameAvailable(int cameraId) {
                log("onFrameAvailable: " + cameraId);
            }
        });
    }
}
