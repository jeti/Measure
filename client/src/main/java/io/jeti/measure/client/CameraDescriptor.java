package io.jeti.measure.client;

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import java.util.Arrays;

public class CameraDescriptor {

    public static String toString(Camera camera) {
        Parameters params = camera.getParameters();
        StringBuilder builder = new StringBuilder();
        builder.append("PreviewFrameRate: " + params.getPreviewFrameRate() + "\n");
        builder.append("SupportedPreviewFrameRates: "
                + Arrays.toString(params.getSupportedPreviewFrameRates().toArray()) + "\n");
        return builder.toString();
    }

}
