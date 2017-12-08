package io.jeti.measure.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import io.jeti.measure.utils.ZMQSink.Listener;

public class CaptureService extends Service {

    private static final String TAG             = CaptureService.class.getSimpleName();
    private static final int    notificationID  = View.generateViewId();
    private final Object        captureSinkLock = new Object();
    private ZMQSink             captureSink     = null;

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

        /* Start up the subscriber. */
        createSubscriberIfNull();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createSubscriberIfNull();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        synchronized (captureSinkLock) {
            if (captureSink != null) {
                captureSink.stopAndDestroy();
            }
        }
        stopForeground(true);
        super.onDestroy();
    }

    public void createSubscriberIfNull() {
        synchronized (captureSinkLock) {
            if (captureSink == null) {
                captureSink = ZMQSink.createAndStart(new Listener() {
                    @Override
                    public void onConnected(String address, int port) {
                        PortBroadcaster.broadcast(CaptureService.this, port);
                        log(address + ":" + port);
                    }

                    @Override
                    public void received(byte[] bytes) {
                        /*
                         * We received a command to snap an image. Broadcast the
                         * request. This is not meant to be a low-latency
                         * channel, so we can handle some delays here.
                         */
                        CaptureBroadcaster.broadcast(CaptureService.this);
                    }
                });
            }
            int port;
            if ((port = captureSink.getPort()) != 0) {
                PortBroadcaster.broadcast(this, port);
            }
        }
    }
}
