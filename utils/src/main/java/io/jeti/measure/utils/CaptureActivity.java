package io.jeti.measure.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

abstract public class CaptureActivity extends PortListenerActivity {

    private BroadcastReceiver captureListener = null;

    public abstract void onCapture();

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        /* Create a broadcast receiver that will listen for capture events. */
        captureListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onCapture();
            }
        };

        /* Start the CaptureService */
        startService(new Intent(this, CaptureService.class));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopService(new Intent(this, CaptureService.class));
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(captureListener, new IntentFilter(CaptureBroadcaster.CAPTURE_EVENT));
        startService(new Intent(this, CaptureService.class));
    }

    @Override
    public void onPause() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.unregisterReceiver(captureListener);
        super.onPause();
    }
}