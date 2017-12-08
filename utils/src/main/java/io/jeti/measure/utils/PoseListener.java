package io.jeti.measure.utils;

import io.jeti.measure.utils.ZMQSink.Listener;
import java.util.concurrent.atomic.AtomicReference;

public class PoseListener implements Listener {

    private final AtomicReference<Pose> lastPose   = new AtomicReference<>(null);
    private final PoseSerializer        serializer = new PoseSerializer();
    private final ZMQSink               poseSink;

    public PoseListener(String poseAddressAndPort) {
        if (poseAddressAndPort == null) {
            throw new NullPointerException("The pose source's address and port cannot be null");
        }
        poseSink = ZMQSink.createAndStart(this, poseAddressAndPort);
    }

    public void destroy() {
        poseSink.stopAndDestroy();
    }

    @Override
    public void onConnected(String address, int port) {
    }

    @Override
    public void received(byte[] bytes) {
        lastPose.set(serializer.decode(bytes));
    }

    public Pose get() {
        return lastPose.get();
    }
}
