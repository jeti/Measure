package io.jeti.measure.utils;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

public class PortBroadcaster {

    public static final String PORT_INTENT = PortBroadcaster.class.getSimpleName();
    public static final String PORT_EXTRA  = "PORT_EXTRA";

    public static void broadcast(Context context, int port) {
        Intent in = new Intent();
        in.putExtra(PORT_EXTRA, port);
        in.setAction(PORT_INTENT);
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(in);
    }

}
