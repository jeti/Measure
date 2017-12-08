package io.jeti.measure.remote;

import io.jeti.components.SplashScreenActivity;

public class SplashScreen extends SplashScreenActivity {

    @Override
    public Class getNextActivityClass() {
        return GetClientAddressActivity.class;
    }

    @Override
    public int getTimeoutMillis() {
        return 0;
    }
}
