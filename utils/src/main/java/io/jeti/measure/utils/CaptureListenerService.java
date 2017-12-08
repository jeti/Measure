package io.jeti.measure.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;

public abstract class CaptureListenerService extends Service {

    private static final String TAG             = CaptureListenerService.class.getSimpleName();
    private static final int    notificationID  = View.generateViewId();
    private BroadcastReceiver   captureListener = null;

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

        /* Start the CaptureService */
        startService(new Intent(this, CaptureService.class));

        /* Create a broadcast receiver that will listen for capture events. */
        captureListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onCapture();
            }
        };
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(captureListener, new IntentFilter(CaptureBroadcaster.CAPTURE_EVENT));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService(new Intent(this, CaptureService.class));
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {

        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.unregisterReceiver(captureListener);

        stopService(new Intent(this, CaptureService.class));
        stopForeground(true);
        super.onDestroy();
    }

    abstract public void onCapture();
}
