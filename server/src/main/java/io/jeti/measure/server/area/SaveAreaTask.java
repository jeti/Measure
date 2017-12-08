package io.jeti.measure.server.area;

import android.os.AsyncTask;
import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoAreaDescriptionMetaData;
import io.jeti.measure.utils.TangoContainer;

/**
 * This {@link AsyncTask} saves the area description file on a background
 * thread. Note that you must pass a non-null {@link TangoContainer} to this
 * task, which will make sure to add itself as a parent, and call the
 * {@link TangoContainer#finish()} when it is finished saving.
 */
public class SaveAreaTask extends AsyncTask<Void, Integer, Void> {

    private final TangoContainer tangoContainer;
    private final String         name;

    public SaveAreaTask(TangoContainer tangoContainer, String name) {
        if (tangoContainer == null) {
            throw new NullPointerException("The TangoContainer cannot be null");
        }
        this.tangoContainer = tangoContainer;
        this.tangoContainer.addParent();
        this.name = name;
    }

    @Override
    protected Void doInBackground(Void... params) {
        synchronized (tangoContainer.getLock()) {

            try {
                /* Save the ADF. */
                Tango tango = tangoContainer.getTango();
                String id = tango.saveAreaDescription();

                /* Now stash the name in the area description's metadata */
                TangoAreaDescriptionMetaData metadata = tango.loadAreaDescriptionMetaData(id);
                metadata.set(TangoAreaDescriptionMetaData.KEY_NAME, name.getBytes());
                tango.saveAreaDescriptionMetadata(id, metadata);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (tangoContainer != null) {
                    tangoContainer.finish();
                }
            }
            return null;
        }
    }
}
