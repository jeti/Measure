package io.jeti.measure.utils;

import android.app.Activity;
import android.util.Log;
import android.widget.TextView;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class ZMQSink {

    public interface Listener {
        void onConnected(String address, int port);

        void received(byte[] bytes);
    }

    private static final String TAG  = ZMQSink.class.getSimpleName();
    private final Object        zmqLock;
    private final Thread        zmqThread;
    private final AtomicInteger port = new AtomicInteger(0);

    private ZMQSink(final Listener listener, final String address, final int port) {

        zmqLock = new Object();
        zmqThread = new Thread(() -> {

            Context context = null;
            Socket subscriber = null;
            try {
                context = ZMQ.context(1);
                subscriber = context.socket(ZMQ.SUB);
                subscriber.setTCPKeepAlive(1);
                subscriber.setHWM(20);
                if (address != null) {
                    String addressAndPort = "tcp://" + address + ":" + port;
                    subscriber.connect(addressAndPort);
                    this.port.set(port);
                } else {
                    this.port.set(subscriber.bindToRandomPort("tcp://*"));
                }
                subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
                listener.onConnected(address, this.port.get());
                while (true) {
                    final byte[] bytes = subscriber.recv();
                    listener.received(bytes);
                }
            } catch (Exception e) {
                e.printStackTrace();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                log(sw.toString()); // stack trace as a string
            } finally {
                log("Shutting down. Entered the finally block...");
                synchronized (zmqLock) {
                    try {
                        if (subscriber != null) {
                            subscriber.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        if (context != null) {
                            context.term();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public static ZMQSink createAndStart(final TextView tv, final Serializer serializer) {
        return createAndStart(tv, serializer, null, 0);
    }

    public static ZMQSink createAndStart(final TextView tv, final Serializer serializer,
            String addressAndPort) {
        String[] split = addressAndPort.split(":");
        return createAndStart(tv, serializer, split[0], Integer.parseInt(split[1]));
    }

    public static ZMQSink createAndStart(final TextView tv, final Serializer serializer,
            String address, int port) {

        Listener listener = new Listener() {

            private long                          messages    = 0;
            private final Throttle                throttle    = new Throttle();
            private final WeakReference<TextView> textViewRef = new WeakReference<>(tv);

            @Override
            public void onConnected(String address, int port) {
                setTextView("Connected (address:port) = " + address + ":" + port);
            }

            @Override
            public void received(byte[] bytes) {
                messages++;
                if (throttle.ready()) {
                    if (serializer != null) {
                        setTextView(messages + " messages\n" + serializer.toString(bytes));
                    } else {
                        setTextView(messages + " messages\n" + bytes);
                    }
                }
            }

            private void setTextView(String s) {
                TextView textView = textViewRef.get();
                if (textView != null) {
                    ((Activity) textView.getContext()).runOnUiThread(() -> {
                        TextView textView1 = textViewRef.get();
                        if (textView1 != null) {
                            textView1.setText(s);
                        }
                    });
                }
            }

        };
        return createAndStart(listener, address, port);
    }

    public static ZMQSink createAndStart(final Listener listener) {
        return createAndStart(listener, null, 0);
    }

    public static ZMQSink createAndStart(final Listener listener, String addressAndPort) {
        String[] split = addressAndPort.split(":");
        return createAndStart(listener, split[0], Integer.parseInt(split[1]));
    }

    public static ZMQSink createAndStart(final Listener listener, String address, int port) {
        ZMQSink zmqSink = new ZMQSink(listener, address, port);
        synchronized (zmqSink.zmqLock) {
            zmqSink.zmqThread.start();
        }
        return zmqSink;
    }

    public void stopAndDestroy() {
        synchronized (zmqLock) {
            zmqThread.interrupt();
        }
    }

    /** This value will be 0 before the port is set. */
    public int getPort() {
        return port.get();
    }

    private void log(byte[] bytes) {
        log(new String(bytes));
    }

    private void log(String s) {
        Log.d(TAG, s);
    }

}
