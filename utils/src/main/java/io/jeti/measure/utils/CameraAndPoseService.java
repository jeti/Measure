package io.jeti.measure.utils;

import android.content.Intent;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.Log;
import io.jeti.measure.utils.CameraService.FrameAndPoseListener;
import io.jeti.measure.utils.CameraWrapper.CAMERA;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CameraAndPoseService extends CameraService implements FrameAndPoseListener {

    private static final String          TAG                 = CameraAndPoseService.class
            .getSimpleName();
    public static final String           LOGGER_ADDRESS_PORT = "LOGGER_ADDRESS_PORT";
    private ZMQSource                    imageSource         = null;

    public static final CameraWrapper[]  cameraWrappers      = { new CameraWrapper(CAMERA.BACK) };
    private final StampedImageSerializer serializer          = new StampedImageSerializer();
    private final PoseSerializer         poseSerializer      = new PoseSerializer();

    private static final double          aspectRatio         = 4.0 / 3.0;
    private static final double          aspectRatioTol      = 0.001;

    @Override
    public FrameAndPoseListener getFrameAndPoseListener() {
        return this;
    }

    @Override
    public CameraWrapper[] getCameraWrappers() {
        return cameraWrappers;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (imageSource == null) {
            String loggerAddressAndPort = intent.getStringExtra(LOGGER_ADDRESS_PORT);
            if (loggerAddressAndPort != null) {
                imageSource = ZMQSource.createAndStart(loggerAddressAndPort);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (imageSource != null) {
            imageSource.stopAndDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void setParameters(CameraWrapper cameraWrapper) {
        // TODO Enforce aspect ratio.
        Parameters params = cameraWrapper.getParameters();
        List<Size> validSizes = new ArrayList<>();
        for (Size size : params.getSupportedPreviewSizes()) {
            if (Math.abs(size.width / (double) size.height - aspectRatio) < aspectRatioTol) {
                log("Valid size: " + sizeToString(size) + ", "
                        + Math.abs(size.width / (double) size.height - aspectRatio));
                validSizes.add(size);
            }
        }
        /* Now sort by area */
        Collections.sort(validSizes, CameraWrapper.areaComparator);

        log(sizesToString(validSizes));
        Size size = validSizes.get(0);
        params.setPreviewSize(size.width, size.height);
    }

    private String sizesToString(Collection<Size> sizes) {
        StringBuilder builder = new StringBuilder();
        for (Size size : sizes) {
            builder.append(sizeToString(size));
            builder.append(", ");
        }
        return builder.toString();
    }

    private String sizeToString(Size size) {
        return size.width + " x " + size.height;
    }

    @Override
    public void onEvent(byte[] data, int width, int height, int format, CAMERA type, Pose pose) {
        if (imageSource != null) {
            imageSource.send(serializer.encode(data, width, height, format,
                    poseSerializer.getR(pose), poseSerializer.getQ(pose)));
        } else {
            log("Got a stamped frame, but the imageSource is null");
        }
    }

    @Override
    public void log(String s) {
        Log.e(TAG, s);
    }
}
