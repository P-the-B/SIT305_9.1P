package com.example.lostfound.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.lostfound.model.LostItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/*
    DBHelper
    - SQLite wrapper for lost/found items
    - v3: added timestamp column
*/
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME    = "LostFound.db";
    private static final int    DB_VERSION = 3;

    public static final String TABLE      = "items";
    public static final String COL_ID          = "id";
    public static final String COL_TITLE       = "title";
    public static final String COL_DESCRIPTION = "description";
    public static final String COL_DATE        = "date";
    public static final String COL_LOCATION    = "location_text";
    public static final String COL_NAME        = "name";
    public static final String COL_PHONE       = "phone";
    public static final String COL_IS_LOST     = "is_lost";
    public static final String COL_IMAGE_URI   = "image_uri";
    public static final String COL_LAT         = "latitude";
    public static final String COL_LNG         = "longitude";
    public static final String COL_TIMESTAMP   = "timestamp";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID          + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_TITLE       + " TEXT,"
                + COL_DESCRIPTION + " TEXT,"
                + COL_DATE        + " TEXT,"
                + COL_LOCATION    + " TEXT,"
                + COL_NAME        + " TEXT,"
                + COL_PHONE       + " TEXT,"
                + COL_IS_LOST     + " INTEGER,"
                + COL_IMAGE_URI   + " TEXT,"
                + COL_LAT         + " REAL,"
                + COL_LNG         + " REAL,"
                + COL_TIMESTAMP   + " TEXT"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // ─── INSERT ────────────────────────────────────────────────────────────────

    public long insertItem(String title,
                           String description,
                           String date,
                           String locationText,
                           String name,
                           String phone,
                           boolean isLost,
                           String imageUri,
                           double latitude,
                           double longitude) {

        String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(new Date());

        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put(COL_TITLE,       title);
        v.put(COL_DESCRIPTION, description);
        v.put(COL_DATE,        date);
        v.put(COL_LOCATION,    locationText);
        v.put(COL_NAME,        name);
        v.put(COL_PHONE,       phone);
        v.put(COL_IS_LOST,     isLost ? 1 : 0);
        v.put(COL_IMAGE_URI,   imageUri);
        v.put(COL_LAT,         latitude);
        v.put(COL_LNG,         longitude);
        v.put(COL_TIMESTAMP,   timestamp);

        return db.insert(TABLE, null, v);
    }

    // ─── QUERIES ───────────────────────────────────────────────────────────────

    /** All items, newest first */
    public List<LostItem> getAllItems() {
        List<LostItem> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM " + TABLE + " ORDER BY " + COL_ID + " DESC", null);
        if (c.moveToFirst()) {
            do { list.add(fromCursor(c)); } while (c.moveToNext());
        }
        c.close();
        return list;
    }

    /**
     * Items within radiusKm of the given lat/lng.
     * Used by MainActivity map — pulls all then filters in Java via Haversine.
     */
    public List<LostItem> getItemsWithinRadius(double lat, double lng, double radiusKm) {
        List<LostItem> result = new ArrayList<>();
        for (LostItem item : getAllItems()) {
            if (item.getLatitude() == 0 && item.getLongitude() == 0) continue;
            if (haversineKm(lat, lng, item.getLatitude(), item.getLongitude()) <= radiusKm) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Single-item distance check — used by ItemListActivity for inline filtering.
     * Exposed as a public helper so the activity doesn't need to duplicate the math.
     */
    public boolean withinRadius(double userLat, double userLng,
                                double itemLat, double itemLng,
                                double radiusKm) {
        return haversineKm(userLat, userLng, itemLat, itemLng) <= radiusKm;
    }

    /** Lost item count */
    public int getLostCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_IS_LOST + "=1", null);
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    /** Found item count */
    public int getFoundCount() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_IS_LOST + "=0", null);
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    /** Most recent date string, or null if table is empty */
    public String getLatestDate() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + COL_DATE + " FROM " + TABLE
                        + " ORDER BY " + COL_ID + " DESC LIMIT 1", null);
        String date = null;
        if (c.moveToFirst()) date = c.getString(0);
        c.close();
        return date;
    }

    // ─── DELETE ────────────────────────────────────────────────────────────────

    public int deleteItem(int id) {
        return getWritableDatabase().delete(TABLE, COL_ID + "=?",
                new String[]{String.valueOf(id)});
    }

    // ─── HELPERS ───────────────────────────────────────────────────────────────

    private LostItem fromCursor(Cursor c) {
        return new LostItem(
                c.getInt(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
                c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION)),
                c.getString(c.getColumnIndexOrThrow(COL_DATE)),
                c.getString(c.getColumnIndexOrThrow(COL_LOCATION)),
                c.getString(c.getColumnIndexOrThrow(COL_NAME)),
                c.getString(c.getColumnIndexOrThrow(COL_PHONE)),
                c.getInt(c.getColumnIndexOrThrow(COL_IS_LOST)) == 1,
                c.getString(c.getColumnIndexOrThrow(COL_IMAGE_URI)),
                c.getDouble(c.getColumnIndexOrThrow(COL_LAT)),
                c.getDouble(c.getColumnIndexOrThrow(COL_LNG)),
                c.getString(c.getColumnIndexOrThrow(COL_TIMESTAMP))
        );
    }

    /** Haversine formula — returns distance in km between two lat/lng points */
    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}