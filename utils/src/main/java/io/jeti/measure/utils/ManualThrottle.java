package io.jeti.measure.utils;

import java.util.concurrent.atomic.AtomicBoolean;

public class ManualThrottle implements ThrottleInterface {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Override
    public boolean poll() {
        return ready.get();
    }

    @Override
    public boolean set(boolean ready) {
        return this.ready.getAndSet(ready);
    }

    @Override
    public boolean ready() {
        return ready.getAndSet(false);
    }
}
