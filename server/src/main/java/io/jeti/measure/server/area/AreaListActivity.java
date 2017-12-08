package io.jeti.measure.server.area;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.View.OnClickListener;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import com.google.atap.tangoservice.TangoErrorException;

import io.jeti.measure.server.PoseCaptureActivity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import io.jeti.measure.R;
import io.jeti.measure.server.utils.RealmActivity;
import io.realm.RealmResults;

public class AreaListActivity extends RealmActivity {

    private Tango              tango;
    private RecyclerView       recyclerView;
    private RealmResults<Area> areas;
    public static final String PRIMARY_KEY_EXTRA = "PRIMARY_KEY";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.area_list_activity);

        /* Retrieve all of the areas in the database. */
        areas = realm.where(Area.class).findAll();

        /* Create and fill the recyclerview */
        recyclerView = findViewById(R.id.area_recycler_view);
        AreaAdapter areaAdapter = new AreaAdapter(areas, new AreasListener());
        recyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.setAdapter(areaAdapter);

        /*
         * Clicking the floating action button opens a new activity for
         * recording a new area.
         */
        findViewById(R.id.area_button).setOnClickListener(
                view -> startActivity(new Intent(AreaListActivity.this, AreaRecordActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        synchronized (AreaListActivity.this) {
            tango = new Tango(AreaListActivity.this, () -> runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    synchronized (AreaListActivity.this) {
                        updateList();
                    }
                }
            }));
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (AreaListActivity.this) {
            if (tango != null) {
                try {
                    tango.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Retrieve all of the tango areas and make sure that they are in the
     * database.
     */
    private void updateList() {
        realm.beginTransaction();
        try {
            /* Get all of the areas. */
            ArrayList<String> areaUuids = tango.listAreaDescriptions();

            /*
             * All of the areas in the database which are not in the list need
             * to be removed.
             */
            RealmResults<Area> oldAreas;
            if (!areaUuids.isEmpty()) {
                String[] areaArray = areaUuids.toArray(new String[0]);
                oldAreas = realm.where(Area.class).not().in("uuid", areaArray).findAll();
            } else {
                oldAreas = realm.where(Area.class).findAll();

            }
            oldAreas.deleteAllFromRealm();

            /*
             * Retrieve the area fields, such as the name, from the metadata.
             * Then make sure that each area is in the database.
             */
            for (String uuid : areaUuids) {
                String name;
                TangoAreaDescriptionMetaData metadata = new TangoAreaDescriptionMetaData();
                try {
                    metadata = tango.loadAreaDescriptionMetaData(uuid);
                } catch (TangoErrorException e) {
                    e.printStackTrace();
                }
                Area area = new Area();
                area.setUuid(uuid);
                area.setName(new String(metadata.get(TangoAreaDescriptionMetaData.KEY_NAME)));
                byte[] dateBytes = metadata
                        .get(TangoAreaDescriptionMetaData.KEY_DATE_MS_SINCE_EPOCH);
                long unixTime = ByteBuffer.wrap(dateBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
                area.setDate(new java.util.Date(unixTime));
                realm.copyToRealmOrUpdate(area);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            realm.commitTransaction();
        }
    }

    class AreasListener implements OnClickListener {
        @Override
        public void onClick(View view) {
            /* If an area is clicked, pass its ID to PoseCaptureActivity */
            Area area = areas.get(recyclerView.getChildAdapterPosition(view));
            Intent intent = new Intent(AreaListActivity.this, PoseCaptureActivity.class);
            intent.putExtra(PRIMARY_KEY_EXTRA, area.getPrimaryKey());
            startActivity(intent);
        }
    }
}