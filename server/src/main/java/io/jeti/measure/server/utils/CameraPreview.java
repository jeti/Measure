package io.jeti.measure.server.utils;

import static android.content.Context.WINDOW_SERVICE;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import java.io.IOException;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private final SurfaceHolder holder;
    private final Camera        camera;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        this.camera = camera;

        // Install a SurfaceHolder.FrameListener so we get notified when the
        // underlying surface is created and destroyed.
        holder = getHolder();
        holder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the
        // preview.
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            Log.d(CameraPreview.class.getSimpleName(),
                    "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (this.holder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            camera.stopPreview();
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here
        Parameters parameters = camera.getParameters();
        Display display = ((WindowManager) getContext().getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay();

        if (display.getRotation() == Surface.ROTATION_0) {
            parameters.setPreviewSize(height, width);
            camera.setDisplayOrientation(90);
        }

        if (display.getRotation() == Surface.ROTATION_90) {
            parameters.setPreviewSize(width, height);
        }

        if (display.getRotation() == Surface.ROTATION_180) {
            parameters.setPreviewSize(height, width);
        }

        if (display.getRotation() == Surface.ROTATION_270) {
            parameters.setPreviewSize(width, height);
            camera.setDisplayOrientation(180);
        }

        // start preview with new settings
        try {
            camera.setPreviewDisplay(this.holder);
            camera.startPreview();

        } catch (Exception e) {
            Log.d(CameraPreview.class.getSimpleName(),
                    "Error starting camera preview: " + e.getMessage());
        }
    }
}