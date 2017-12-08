package io.jeti.measure.utils;

public interface ThrottleInterface {

    /** Poll the current throttle value. */
    boolean poll();

    /** Set the value of the throttle, returning the previous value. */
    boolean set(boolean ready);

    /**
     * Indicate whether the throttle is ready. If the value returned is true,
     * then the returned value will be flipped to false.
     */
    boolean ready();
}
