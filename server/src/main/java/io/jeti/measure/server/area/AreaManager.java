package io.jeti.measure.server.area;

import android.util.Log;
import io.realm.Realm;

public class AreaManager {

    static Area get(Realm realm, String primaryKey) {
        return realm.where(Area.class).equalTo(Area.primaryKeyFieldName, primaryKey).findFirst();
    }

    static void save(Realm realm, final String name) {
        /*
         * Note that we want to auto-generate the id, so are not passing that
         * here.
         */
        realm.executeTransactionAsync(realm1 -> {
            Area area = realm1.createObject(Area.class, Area.generateKey());
            area.setName(name);
        }, () -> Log.v(AreaManager.class.getSimpleName(), "Transaction was a success."), error -> Log.e(AreaManager.class.getSimpleName(),
                "Transaction failed and was automatically canceled:" + error.getMessage()));
    }

    static void delete(Realm realm, final Area area) {
        try {
            realm.executeTransaction(
                realm1 -> realm1.where(Area.class).equalTo(Area.primaryKeyFieldName, area.getPrimaryKey())
                        .findAll().deleteAllFromRealm());
        } catch (Exception e) {
            Log.e(AreaManager.class.getSimpleName(), "Woah, couldn't remove that object.");
            e.printStackTrace();
        }
    }
}