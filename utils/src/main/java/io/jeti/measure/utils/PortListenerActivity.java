package io.jeti.measure.utils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

abstract public class PortListenerActivity extends Activity {

    private BroadcastReceiver portListener = null;

    public abstract void onPortOpen(int port);

    public abstract void onIPAcquired(String ip);

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        /* Create a broadcast receiver that will listen for a PortBroadcast */
        portListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onPortOpen(intent.getIntExtra(PortBroadcaster.PORT_EXTRA, 0));
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.registerReceiver(portListener, new IntentFilter(PortBroadcaster.PORT_INTENT));
        new IPGetter(ip -> onIPAcquired(ip)).execute();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.unregisterReceiver(portListener);
        super.onPause();
    }
}