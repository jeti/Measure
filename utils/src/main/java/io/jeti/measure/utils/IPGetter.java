package io.jeti.measure.utils;

import android.os.AsyncTask;
import java.lang.ref.WeakReference;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class IPGetter extends AsyncTask<Void, String, String> {

    public interface Listener {
        void onReady(String ip);
    }

    private final WeakReference<Listener> listenerReference;

    public IPGetter(Listener listener) {
        listenerReference = new WeakReference<>(listener);
    }

    @Override
    protected String doInBackground(Void... voids) {
        return getIpv4();
    }

    @Override
    protected void onPostExecute(String s) {
        super.onPostExecute(s);
        Listener listener;
        if ((listener = listenerReference.get()) != null) {
            listener.onReady(s);
        }
    }

    public String getIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {

                Enumeration<InetAddress> inetAddresses = interfaces.nextElement()
                        .getInetAddresses();
                while (inetAddresses.hasMoreElements()) {

                    InetAddress address = inetAddresses.nextElement();
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address) {

                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean validAddressAndPort(String s) {
        return s.contains(":") && !s.contains("[a-zA-Z]");
    }
}