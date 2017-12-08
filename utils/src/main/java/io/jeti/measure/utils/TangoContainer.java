package io.jeti.measure.utils;

import com.google.atap.tangoservice.Tango;

/**
 * A tango container simply holds a tango instance shared between multiple
 * parents. When a parent is finished with Tango, it should call the thread-safe
 * {@link #finish()} method, which indicates to the TangoContainer that that
 * parent is finished. When all of the parents have finished, then the tango
 * instance will be disconnected.
 * <p>
 * Note that by default, the object which creates the container is counted as a
 * parent. If more objects would like to register as parents, then either you
 * can set the number of initial parents directly in the
 * {@link #TangoContainer(Tango, int)} constructor (the default is 1), or you
 * can call the {@link #addParent()} method.
 * <p>
 * Furthermore, to get the tango instance, you can simply call the
 * {@link #getTango()} method. However, you should surround anything you do to
 * the tango instance by synchronizing with lock returned from
 * {@link #getLock()}. You should do this because this object is used as the
 * lock for disconnecting the tango instance, and you do not want tango
 * disconnected in the middle of an operation.
 */
public class TangoContainer {

    private Tango        tango;
    private int          parents = 1;
    private final Object lock;

    public TangoContainer(Tango tango) {
        this(tango, 1);
    }

    public TangoContainer(Tango tango, int parents) {
        this(tango, parents, new Object());
    }

    public TangoContainer(Tango tango, Object tangoLock) {
        this(tango, 1, tangoLock);
    }

    public TangoContainer(Tango tango, int parents, Object tangoLock) {
        if (tangoLock == null) {
            throw new NullPointerException("The synchronization lock cannot null. "
                    + "Otherwise, we cannot synchronize access to the Tango instance.");
        }
        this.tango = tango;
        this.parents = parents;
        this.lock = tangoLock;
    }

    /**
     * @return the {@link Tango} instance. This will be null if either you
     *         created the {@link TangoContainer} with a null instance, or if
     *         the Tango instance was shutdown because all of the parents called
     *         {@link #finish()}.
     */
    public Tango getTango() {
        synchronized (lock) {
            return tango;
        }
    }

    /**
     * Get the lock that we use to lock access to the Tango instance.
     */
    public Object getLock() {
        return lock;
    }

    /**
     * Increment the number of times that {@link #finish()} needs to be called
     * before the {@link Tango} instance is disconnected.
     */
    public void addParent() {
        parents += 1;
    }

    /**
     * Decrement the number of parents that need to be called before the
     * {@link Tango} instance is disconnected. If the number of parents is one
     * before this is called, then the tango instance will be disconnected.
     */
    public void finish() {
        synchronized (lock) {
            parents -= 1;
            if (parents == 0 && tango != null) {
                tango.disconnect();
                tango = null;
            }
        }
    }
}
