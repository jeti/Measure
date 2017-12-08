package io.jeti.measure.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.MediaActionSound;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import io.jeti.measure.utils.CameraWrapper.CAMERA;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This Service captures camera images. If the stated frame rate is positive,
 * then the frames will be saved automatically. Otherwise, we will listen for
 * capture broadcasts. Note that we have to use the CaptureListenerService
 * instead of the CaptureService directly because we want to be able to start
 * the CaptureService much earlier in the app lifecycle. Plus, the capture
 * events are allowed to accrue some latency. Only the poses are really meant to
 * be low-latency channel.
 */
public abstract class CameraService extends CaptureListenerService {

    public interface FrameAndPoseListener {
        void onEvent(byte[] data, int width, int height, int format, CAMERA type, Pose pose);
    }

    abstract public FrameAndPoseListener getFrameAndPoseListener();

    abstract public CameraWrapper[] getCameraWrappers();

    public static final String      INTENT_FPS     = "INTENT_FPS";

    private static final String     TAG            = CameraService.class.getSimpleName();
    private static final int        notificationID = View.generateViewId();

    private PoseListener            poseListener   = null;

    private List<ThrottleInterface> throttles      = new CopyOnWriteArrayList<>();
    private MediaActionSound        sound          = new MediaActionSound();

    public Notification getNotification() {
        return new Notification.Builder(this).setTicker(TAG).setContentTitle(TAG).setOngoing(true)
                .setSmallIcon(android.R.drawable.stat_notify_sync).build();
    }

    public void log(String s) {
        Log.e(TAG, s);
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
    }

    /**
     * When we start, we need a few things...
     *
     * 1. We need the poses. We can't record without them, so if we know the
     * pose source's address and port, try to start the poseListener regardless
     * of anything else. Specifically, we want to start this up even if the
     * cameras aren't ready yet.
     *
     * 2. We need the camera images. We can't record without them, so we can
     * start the camera regardless of anything else.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {
            if (poseListener == null) {
                String poseAddressAndPort = intent.getStringExtra(PoseService.POSE_ADDRESS_PORT);
                if (poseAddressAndPort != null) {
                    poseListener = new PoseListener(poseAddressAndPort);
                }
            }
            if (throttles.isEmpty()) {
                int fps = intent.getIntExtra(INTENT_FPS, 0);
                if (fps != 0) {
                    startCameras(fps);
                } else {
                    log("0 is an invalid frame rate. Use -1 to indicate manual capture.");
                }
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {

        for (CameraWrapper wrapper : getCameraWrappers()) {
            wrapper.destroy();
        }
        if (throttles.isEmpty()) {
            throttles.clear();
        }
        if (poseListener != null) {
            poseListener.destroy();
            poseListener = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    public void onCapture() {
        if (throttles.isEmpty()) {
            log("The throttle has not been initialized. We will ignore this capture command.");
            return;
        }
        for (ThrottleInterface throttle : throttles) {
            if (throttle instanceof ManualThrottle) {
                throttle.set(true);
                sound.play(MediaActionSound.SHUTTER_CLICK);
            }
        }
    }

    public void setParameters(CameraWrapper cameraWrapper) {
    }

    private void startCameras(int fps) {
        for (CameraWrapper wrapper : getCameraWrappers()) {

            final ThrottleInterface throttle;
            if (fps > 0) {
                throttle = new Throttle(fps);
            } else {
                throttle = new ManualThrottle();
            }
            throttles.add(throttle);

            try {
                wrapper.create();
            } catch (Exception e) {
                e.printStackTrace();
            }
            setParameters(wrapper);
            wrapper.setFrameListener((data, width, height, format, type) -> {
                if (poseListener != null) {
                    final Pose pose = poseListener.get();
                    if (pose != null) {
                        if (throttle.ready()) {
                            getFrameAndPoseListener().onEvent(data, width, height, format, type,
                                    pose);
                        }
                    } else {
                        log("Got a frame, but the pose is null");
                    }
                } else {
                    log("Got a frame, but the pose listener is null");
                }
            });
            wrapper.resume();
        }
    }
}
