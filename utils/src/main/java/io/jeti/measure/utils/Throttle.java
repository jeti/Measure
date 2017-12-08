package io.jeti.measure.utils;

public class Throttle implements ThrottleInterface {

    private static final int defaultHZ  = 10;
    private final long       intervalMillis;
    private long             lastMillis = 0;

    public Throttle() {
        this(defaultHZ);
    }

    public Throttle(int hz) {
        this.intervalMillis = (long) Math.ceil(1000.0 / hz);
        lastMillis = System.currentTimeMillis();
    }

    @Override
    public boolean poll() {
        return System.currentTimeMillis() > lastMillis + intervalMillis;
    }

    @Override
    public boolean set(boolean ready) {
        lastMillis = ready ? 0 : Long.MAX_VALUE;
        return poll();
    }

    @Override
    public boolean ready() {
        boolean ready = poll();
        if (ready) {
            lastMillis = System.currentTimeMillis();
        }
        return ready;
    }
}
