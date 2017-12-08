package io.jeti.measure.server.utils;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import io.realm.Realm;

/**
 * This activity does two things:
 *
 * First of all, it opens a realm instance in onCreate and closes it in
 * onDestroy.
 * 
 * Secondly, it deletes the realm instance if something in the model changed
 * since all of the old data will be unusable if that occurred and you did not
 * migrate your objects.
 */
abstract public class RealmActivity extends Activity {

    protected Realm realm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        try {
            realm = Realm.getDefaultInstance();
        } catch (Exception e) {
            Realm.deleteRealm(Realm.getDefaultConfiguration());
            realm = Realm.getDefaultInstance();
            Log.e(RealmActivity.class.getSimpleName(),
                    "The default realm model appears to have changed. We had to delete the old Realm and replace it with a new one.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        realm.close();
    }
}
