package io.jeti.measure.utils;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@SuppressWarnings("deprecation")
public class CameraWrapper {

    public interface FrameListener {
        void onFrame(byte[] data, int width, int height, int format, CAMERA type);
    }

    /*
     * ---------------------------------------------
     *
     * Private Fields
     *
     * ---------------------------------------------
     */
    private static final String          TAG           = CameraWrapper.class.getSimpleName();
    private CAMERA                       type          = CAMERA.BACK;
    private int                          cameraType    = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Camera                       camera        = null;
    private final ReadWriteLock          cameraLock    = new ReentrantReadWriteLock();
    private final Lock                   readLock      = cameraLock.readLock();
    private final Lock                   writeLock     = cameraLock.writeLock();
    private boolean                      opened        = false;
    private boolean                      resumed       = false;
    private FrameListener                frameListener = null;
    private SurfaceHolder                surfaceHolder = null;
    private Parameters                   parameters    = null;
    public static final Comparator<Size> areaComparator;
    static {
        /*
         * Sorts a Collection of Sizes in order of increasing area, that is, the
         * smallest size will be at position 0, and largest will be the last
         * element.
         */
        areaComparator = (a1, a2) -> a1.width * a1.height - a2.width * a2.height;
    }

    public enum CAMERA {
        BACK, FRONT
    }

    /*
     * ---------------------------------------------
     *
     * Constructors
     *
     * ---------------------------------------------
     */
    /**
     * Create a FRONT OR BACK camera wrapper.
     */
    public CameraWrapper(CAMERA camera) {
        type = camera;
        if (type == CAMERA.BACK) {
            this.cameraType = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else {
            this.cameraType = CameraInfo.CAMERA_FACING_FRONT;
        }
    }

    /*
     * ---------------------------------------------
     *
     * Lifecycle Methods
     *
     * ---------------------------------------------
     */
    /**
     * Open the camera, but do not {@link #resume()} it. You would use this
     * function if you want to open the camera, but aren't set to get frames
     * yet.
     *
     * To start getting frames from the camera, you need to call
     * {@link #resume()} after this function. Alternatively (and preferably),
     * you can also call {@link #resume()} directly (without first calling
     * {@link #create()}) since {@link #resume()} will internally call
     * {@link #create()} if the camera has not already been opened.
     * 
     * Therefore, since you defer holding a camera instance until you absolutely
     * need it, typically we would advise not calling {@link #create()}, but to
     * simply call {@link #resume()} in the {@link Activity}'s (or
     * {@link Fragment}'s) {@link Activity#onResume()} method, and
     * {@link #destroy()} in the {@link Activity#onPause()} method.
     *
     * We understand that this is a little confusing since we are recommending a
     * camera lifecycle different from the Activity lifecycle, so in summary,
     * your code should typically look like this:
     *
     * <pre>
     * <code>
     *
     * public class MyActivity extends Activity implements PreviewCallback {
     *     private CameraWrapper wrapper;
     *     onCreate(){ wrapper = new CameraWrapper(...); wrapper.setPreviewCallback(this); }
     *     onResume(){ wrapper.resume(); }
     *     onPause(){  wrapper.destroy(); }
     *     onDestroy(){ // No need to do anything  }
     * }
     * </code>
     * </pre>
     *
     * While you might be inclined to call (create,resume,pause,destroy) in the
     * equivalent {@link Activity} or {@link Fragment} methods, the danger is
     * that, if the user navigates away from your app to another app that wants
     * the camera, then the Activity's onDestroy method may not have been
     * called. This means that your app will be sitting in the background,
     * holding on to a camera instance, which could cause errors in both apps.
     */
    public boolean create() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int cameraIndex = 0; cameraIndex < Camera.getNumberOfCameras(); cameraIndex++) {
            Camera.getCameraInfo(cameraIndex, cameraInfo);
            if (cameraInfo.facing == cameraType) {
                int index = cameraIndex;
                // writeSafe(() -> {
                log("Opening camera: " + index);
                camera = Camera.open(index);
                log("Successfully opened camera: " + index);

                /* Set the initial size to the smallest possible area. */
                parameters = camera.getParameters();
                List<Size> sizes = parameters.getSupportedPreviewSizes();
                Collections.sort(sizes, areaComparator);
                parameters.setPreviewSize(sizes.get(0).width, sizes.get(0).height);
                camera.setParameters(parameters);
                opened = true;
                // });
                return true;
            }
        }
        return false;
    }

    /**
     * Resume getting frames from the camera. If the camera has not yet been
     * opened (via {@link #create()}), then we will do that now.
     */
    public void resume() {

        writeSafe(() -> {

            if (isResumed()) {
                /*
                 * If already running, pause the camera to stop the old preview
                 * and remove callbacks.
                 */
                pause();
            } else if (!isOpened()) {
                /* Open the camera if not already opened. */
                create();
            }

            /* Get the camera parameters and image size. */
            Size size = parameters.getPreviewSize();

            /* Turn off the flash, if possible */
            List<String> flashModes = parameters.getSupportedFlashModes();
            if (flashModes != null && flashModes.contains(Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            }

            /* Turn off the autofocus if possible. */
            if (parameters.getSupportedFocusModes().contains(Parameters.FOCUS_MODE_FIXED)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_FIXED);
            }

            /*
             * Set the camera parameters. Do not simply call this method's
             * setParameters method. That will result in an infinite loop since
             * setParameters will try to call resume.
             */
            camera.setDisplayOrientation(90);
            camera.setParameters(parameters);
            int format = parameters.getPreviewFormat();
            int frameByteLength;
            if (format == ImageFormat.YV12) {
                int yStride = (int) Math.ceil(size.width / 16.0) * 16;
                int ySize = yStride * size.height;
                int uvStride = (int) Math.ceil((yStride / 2) / 16.0) * 16;
                int uvSize = uvStride * size.height / 2;
                frameByteLength = ySize + uvSize * 2;
            } else {
                int bitsperpixel = ImageFormat.getBitsPerPixel(format);
                frameByteLength = (int) (size.width * size.height * bitsperpixel / 8.0);
            }

            /* Create the frameListener with two buffers if it is not null */
            if (frameListener != null) {
                camera.setPreviewCallbackWithBuffer(createPreviewCallback(frameListener, size.width,
                        size.height, format, frameByteLength));
                camera.addCallbackBuffer(new byte[frameByteLength]);
                camera.addCallbackBuffer(new byte[frameByteLength]);
            }
            if (surfaceHolder != null) {
                try {
                    camera.setPreviewDisplay(surfaceHolder);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            camera.startPreview();
            resumed = true;
        });
    }

    /**
     * Initialize the previewCallback only once. We put it here to maintain the
     * "flow" of the code, since the previewCallback method should get called
     * right after resume().
     */
    private PreviewCallback createPreviewCallback(FrameListener frameListener, int width,
            int height, int format, int byteLength) {
        return (frame, camera) -> {

            if (frame == null || frame.length == 0) {
                Log.d(TAG, "Incoming frame is null or empty: " + frame);
            } else if (frame.length != byteLength) {
                Log.d(TAG, "Incoming frame byte array length changed: " + frame.length
                        + " instead of " + byteLength);
            } else if (frameListener != null) {
                frameListener.onFrame(frame, width, height, format, type);
            }
            if (camera != null) {
                camera.addCallbackBuffer(frame);
            }
        };
    };

    /**
     * Pause getting frames from the camera, but do not release the camera yet.
     * This is called, for instance, when changing the frame size.
     */
    public void pause() {
        writeSafe(() -> {
            if (camera != null) {
                camera.setPreviewCallbackWithBuffer(null);
                camera.stopPreview();
            }
            resumed = false;
        });
    }

    /** {@link #pause()} the camera, and release it. */
    public void destroy() {
        writeSafe(() -> {
            pause();
            if (camera != null) {
                camera.release();
                camera = null;
            }
            opened = false;
        });
    }

    /*
     * ---------------------------------------------
     *
     * Getters
     *
     * ---------------------------------------------
     */
    public boolean isOpened() {
        return readSafeNoThrow(() -> opened);
    }

    public boolean isResumed() {
        return readSafeNoThrow(() -> resumed);
    }

    public Parameters getParameters() {
        return readSafeNoThrow(() -> parameters);
    }

    public CAMERA getType() {
        return type;
    }

    /*
     * ---------------------------------------------
     *
     * Setters
     *
     * ---------------------------------------------
     */
    /**
     * If the parameters are null, return immediately. Otherwise, set the camera
     * parameters and {@link #resume()} if the camera is open.
     */
    public void setParameters(Parameters parameters) {
        if (parameters == null) {
            log("Ignoring request to set the camera parameters to null");
            return;
        }
        writeSafe(() -> {
            pause();
            this.parameters = parameters;
            if (camera != null) {
                resume();
            }
        });
    }

    /** Set the frameListener and {@link #resume()} if the camera is open. */
    public void setFrameListener(FrameListener frameListener) {
        writeSafe(() -> {
            pause();
            this.frameListener = frameListener;
            if (camera != null) {
                resume();
            }
        });
    }

    /** Set the surfaceHolder and {@link #resume()} if the camera is open. */
    public void setPreviewDisplay(SurfaceHolder surfaceHolder) {
        writeSafe(() -> {
            pause();
            this.surfaceHolder = surfaceHolder;
            if (camera != null) {
                resume();
            }
        });
    }

    /*
     * ---------------------------------------------
     *
     * Other Methods
     *
     * ---------------------------------------------
     */
    private void writeSafe(Runnable runnable) {
        try {
            writeLock.lock();
            runnable.run();
        } finally {
            writeLock.unlock();
        }
    }

    private void readSafe(Runnable runnable) {
        try {
            readLock.lock();
            runnable.run();
        } finally {
            readLock.unlock();
        }
    }

    private <T> T writeSafeNoThrow(Callable<T> callable) {
        try {
            writeLock.lock();
            return callable.call();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            writeLock.unlock();
        }
        return null;
    }

    private <T> T readSafeNoThrow(Callable<T> callable) {
        try {
            readLock.lock();
            return callable.call();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            readLock.unlock();
        }
        return null;
    }

    private <T> T writeSafeRethrow(Callable<T> callable) throws Exception {
        try {
            writeLock.lock();
            return callable.call();
        } catch (Exception e) {
            throw e;
        } finally {
            writeLock.unlock();
        }
    }

    private <T> T readSafeRethrow(Callable<T> callable) throws Exception {
        try {
            readLock.lock();
            return callable.call();
        } catch (Exception e) {
            throw e;
        } finally {
            readLock.unlock();
        }
    }

    private void log(String s) {
        Log.e(TAG, s);
    }
}