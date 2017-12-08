package io.jeti.measure.server;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import com.google.atap.tangoservice.Tango;
import io.jeti.measure.R;
import io.jeti.measure.server.area.AreaListActivity;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class SplashScreen extends Activity {

    private static final int      timeoutMillis    = 500;
    private static final int      TANGO_PERMISSION = 4949;
    private static final int      DROID_PERMISSION = 9494;
    private static final Class    nextActivity     = AreaListActivity.class;
    private static final String[] tangoPermissions = { Tango.PERMISSIONTYPE_ADF_LOAD_SAVE,
            Tango.PERMISSIONTYPE_DATASET, Tango.PERMISSIONTYPE_MOTION_TRACKING };

    private long                  startTimeMillis  = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_screen);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    /** This is the callback we get when requesting an Android permission */
    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        if (requestCode == DROID_PERMISSION) {
            checkPermissions();
        }
    }

    /** This is the callback we get when requesting a Tango permission */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TANGO_PERMISSION) {
            if (resultCode == RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(
                        "The app cannot proceed without permission to load area files. We need this permission so that we can correct for drift.")
                        .setCancelable(false).setNeutralButton("Ok", null).create().show();
            }
            checkPermissions();
        }
    }

    /**
     * @return the list of required permissions in the manifest. If you don't
     *         think the default behavior is working, then you could try
     *         overriding this function to return something like:
     * 
     *         <pre>
     * <code>
     * return new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
     * </code>
     *         </pre>
     */
    public String[] getAndroidPermissions() {
        String[] permissions = new String[0];
        try {
            permissions = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_PERMISSIONS).requestedPermissions;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return Arrays.copyOf(permissions, permissions.length);
    }

    @TargetApi(23)
    private void checkPermissions() {
        String permission;
        if ((permission = nextUngrantedTangoPermission()) != null) {
            startActivityForResult(Tango.getRequestPermissionIntent(permission), TANGO_PERMISSION);
            return;
        }
        if ((permission = nextUngrantedAndroidPermission()) != null) {
            requestPermissions(new String[] { permission }, DROID_PERMISSION);
            return;
        }
        startNextActivity();
    }

    private void startNextActivity() {
        long delayMillis = timeoutMillis - (System.currentTimeMillis() - startTimeMillis);
        if (delayMillis < 0) {
            delayMillis = 0;
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashScreen.this, nextActivity));
                finish();
            }
        }, delayMillis);

    }

    private String nextUngrantedTangoPermission() {
        for (String permission : tangoPermissions) {
            if (!Tango.hasPermission(getApplicationContext(), permission)) {
                return permission;
            }
        }
        return null;
    }

    @TargetApi(23)
    private String nextUngrantedAndroidPermission() {
        Set<String> permissions = new HashSet<>();
        Collections.addAll(permissions, getAndroidPermissions());
        for (Iterator<String> i = permissions.iterator(); i.hasNext();) {
            String permission = i.next();
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return permission;
            }
        }
        return null;
    }
}
