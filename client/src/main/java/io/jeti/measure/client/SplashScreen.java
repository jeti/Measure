package io.jeti.measure.client;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import io.jeti.components.SplashScreenActivity;
import io.jeti.measure.R;

public class SplashScreen extends SplashScreenActivity {

    @Override
    public int getTimeoutMillis() {
        return 0;
    }

    @Override
    public Bitmap getBitmap() {
        return BitmapFactory.decodeResource(getResources(), R.drawable.robot);
    }

    @Override
    public Class getNextActivityClass() {
        return RecordingModeActivity.class;
    }
}
