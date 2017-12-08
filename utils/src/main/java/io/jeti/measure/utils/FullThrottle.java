package io.jeti.measure.utils;

public class FullThrottle implements ThrottleInterface {

    @Override
    public boolean poll() {
        return true;
    }

    /** Note that this method has no effect. */
    @Override
    public boolean set(boolean ready) {
        return true;
    }

    @Override
    public boolean ready() {
        return true;
    }
}
