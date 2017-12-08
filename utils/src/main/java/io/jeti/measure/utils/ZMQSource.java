package io.jeti.measure.utils;

import java.nio.ByteBuffer;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

public class ZMQSource {

    private final Object zmqLock = new Object();
    private Context      context;
    private Socket       publisher;
    private final int    port;

    private ZMQSource(final String address, final int port) {
        context = ZMQ.context(1);
        publisher = context.socket(ZMQ.PUB);
        publisher.setTCPKeepAlive(1);
        publisher.setSndHWM(100);
        if (address != null) {
            String addressAndPort = "tcp://" + address + ":" + port;
            publisher.connect(addressAndPort);
            this.port = port;
        } else {
            this.port = publisher.bindToRandomPort("tcp://*");
        }
    }

    public static ZMQSource createAndStart() {
        return new ZMQSource(null, 0);
    }

    public static ZMQSource createAndStart(String addressAndPort) {
        String[] split = addressAndPort.split(":");
        return createAndStart(split[0], Integer.parseInt(split[1]));
    }

    public static ZMQSource createAndStart(String address, int port) {
        return new ZMQSource(address, port);
    }

    public void stopAndDestroy() {
        synchronized (zmqLock) {
            if (publisher != null) {
                publisher.close();
                publisher = null;
            }
            if (context != null) {
                context.term();
                context = null;
            }
        }
    }

    public boolean send(byte[] bytes) {
        if (publisher != null) {
            publisher.send(bytes);
            return true;
        } else {
            return false;
        }
    }

    public boolean send(ByteBuffer bb) {
        return send(bb, 0);
    }

    public boolean send(ByteBuffer bb, int flags) {
        if (publisher != null) {
            publisher.sendByteBuffer(bb, flags);
            return true;
        } else {
            return false;
        }
    }

    public int getPort() {
        return port;
    }
}
