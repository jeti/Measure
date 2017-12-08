package io.jeti.measure.server.area;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class Area extends RealmObject {

    private static final SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm");

    public String getDateString() {
        if (date != null) {
            return formatter.format(date);
        } else {
            return null;
        }
    }

    public static String generateKey() {
        return UUID.randomUUID().toString();
    }

    public static final String primaryKeyFieldName = "uuid";

    public String getPrimaryKey() {
        return getUuid();
    }


    @PrimaryKey
    @Required
    private String uuid;
    private String name;
    private Date date;
    private String file;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getDate() {
        /* Dates are mutable. So return a copy */
        return new Date(date.getTime());
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }
}
