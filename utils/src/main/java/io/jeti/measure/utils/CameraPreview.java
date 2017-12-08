package io.jeti.measure.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private final SurfaceHolder holder;
    private CameraWrapper       cameraWrapper;

    public CameraPreview(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
    }

    public CameraPreview(Context context, AttributeSet set) {
        super(context, set);
        holder = getHolder();
        holder.addCallback(this);
    }

    public void setCameraWrapper(CameraWrapper cameraWrapper) {
        this.cameraWrapper = cameraWrapper;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (cameraWrapper != null) {
            cameraWrapper.setPreviewDisplay(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (cameraWrapper != null) {
            cameraWrapper.setPreviewDisplay(null);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        return;
    }
}