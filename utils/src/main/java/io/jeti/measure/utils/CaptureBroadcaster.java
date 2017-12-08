package io.jeti.measure.utils;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public class CaptureBroadcaster {

    public static final String CAPTURE_EVENT = CaptureBroadcaster.class.getCanonicalName();

    public static void broadcast(Context context) {
        Intent in = new Intent();
        in.setAction(CAPTURE_EVENT);
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(in);
    }

}
