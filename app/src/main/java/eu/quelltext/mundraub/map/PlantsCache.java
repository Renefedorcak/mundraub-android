package eu.quelltext.mundraub.map;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.quelltext.mundraub.R;
import eu.quelltext.mundraub.api.API;
import eu.quelltext.mundraub.api.progress.Progress;
import eu.quelltext.mundraub.api.progress.Progressable;
import eu.quelltext.mundraub.error.ErrorAware;
import eu.quelltext.mundraub.error.Logger;
import eu.quelltext.mundraub.initialization.Initialization;

/*
 * This class is following the tutorial at
 * https://www.androidauthority.com/creating-sqlite-databases-in-your-app-719366/
 */
public class PlantsCache extends ErrorAware {

    private static Context context;
    private static Logger.Log log = Logger.newFor("PlantsCache");
    private static Progress updateProgress;

    static {
        Initialization.provideActivityFor(new Initialization.ActivityInitialized() {
            @Override
            public void setActivity(Activity context) {
                PlantsCache.context = context;
            }
        });
    }

    static double getLongitude(double longitude) {
        longitude = (longitude + 180) % 360 - 180;
        while (longitude < -180) {
            longitude += 360;
        }
        return longitude;
    }

    static double getLatitude(double latitude) {
        if (latitude > 90 || latitude < -90) {
            log.e("Invalid latitude", latitude + "");
        }
        return latitude;
    }

    public static JSONArray getPlantsInBoundingBox(double minLon, double minLat, double maxLon, double maxLat) throws JSONException {
        minLon = getLongitude(minLon);
        minLat = getLatitude(minLat);
        maxLon = getLongitude(maxLon);
        maxLat = getLatitude(maxLat);
        log.d("minLon", minLon);
        log.d("minLat", minLat);
        log.d("maxLon", maxLon);
        log.d("maxLat", maxLat);
        SQLiteDatabase database = getReadableDatabase();
        try {
            //log.d("number of plants in database", Marker.getCount(database));
            String[] projection = {
                    Marker.COLUMN_LONGITUDE,
                    Marker.COLUMN_LATITUDE,
                    Marker.COLUMN_TYPE_ID,
                    Marker.COLUMN_NODE_ID
            };

            String selection = "";
            if (minLon < maxLon) {
                selection +=
                        Marker.COLUMN_LONGITUDE + " > " + minLon + " and " +
                                Marker.COLUMN_LONGITUDE + " < " + maxLon;
            } else {
                selection +=
                        Marker.COLUMN_LONGITUDE + " < " + minLon + " and " +
                                Marker.COLUMN_LONGITUDE + " > " + maxLon;
                Log.d("rare case", "minLon " + minLon + " > maxLon" + maxLon);
            }
            if (minLat < maxLat) {
                selection += " and " +
                        Marker.COLUMN_LATITUDE + " > " + minLat + " and " +
                        Marker.COLUMN_LATITUDE + " < " + maxLat;
            } else {
                selection += " and " +
                        Marker.COLUMN_LATITUDE + " < " + minLat + " and " +
                        Marker.COLUMN_LATITUDE + " > " + maxLat;
                Log.d("rare case", "minLat " + minLat + " > maxLat" + maxLat);
            }
            //selection = /*" and " +*/ Marker.COLUMN_TYPE_ID + " = 6";
        /*selection =
                Marker.COLUMN_LONGITUDE + " > ? and " +
                        Marker.COLUMN_LONGITUDE + " < ? and " +
                        Marker.COLUMN_LATITUDE + " > ? and " +
                        Marker.COLUMN_LATITUDE + " < ?" +
                "";*/

            String[] selectionArgs = {
                    Double.toString(minLon),
                    Double.toString(maxLon),
                    Double.toString(minLat),
                    Double.toString(maxLat)
            };
            selectionArgs = null;
            //selection = null;

            Cursor cursor = database.query(
                    Marker.TABLE_NAME,  // The table to query
                    projection,         // The columns to return
                    selection,          // The columns for the WHERE clause
                    selectionArgs,      // The values for the WHERE clause
                    null,       // don't group the rows
                    null,        // don't filter by row groups
                    null        // don't sort
            );
            log.d("getPlantsInBoundingBox", "The total cursor count is " + cursor.getCount());
            JSONArray result = new JSONArray();
            for (int i = 0; i < cursor.getCount() /*&& i < 100*/; i++) {
                cursor.moveToPosition(i);
                double latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Marker.COLUMN_LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(Marker.COLUMN_LONGITUDE));
                String typeId = Integer.toString(cursor.getInt(cursor.getColumnIndexOrThrow(Marker.COLUMN_TYPE_ID)));
                String nodeId = Integer.toString(cursor.getInt(cursor.getColumnIndexOrThrow(Marker.COLUMN_NODE_ID)));
                JSONObject marker = new JSONObject();
                JSONObject properties = new JSONObject();
                JSONArray position = new JSONArray();
                position.put(latitude); // latitude before longitude
                position.put(longitude);
                Log.d("pos", latitude + "lat " + longitude + "lon " + typeId + "=type " + nodeId + "= node");
                marker.put(JSON_POSITION, position);
                properties.put(JSON_TYPE_ID, typeId);
                properties.put(JSON_NODE_ID, nodeId);
                marker.put(JSON_PROPERTIES, properties);
                if (i < 100) result.put(marker);
            }
            return result;
        } finally {
            database.close();
        }
    }

    public static void clear() {
        SQLiteDatabase database = getWritableDatabase();
        try {
            new MarkerDBSQLiteHelper().clearTable(database);
        } finally {
            database.close();
        }
    }

    public static Progress update(API.Callback callback) {
        if (updateProgress == null || updateProgress.isDone()) {
            updateProgress = API.instance().updateAllPlantMarkers(callback);
        } else {
            updateProgress.addCallback(callback);
        }
        return updateProgress;
    }

    public static Progress getUpdateProgressOrNull() {
        return updateProgress;
    }

    public static class Marker implements BaseColumns {
        public static final String TABLE_NAME = "marker";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_TYPE_ID = "type";
        public static final String COLUMN_NODE_ID = "node";

        public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " +
                TABLE_NAME + " (" +
                _ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_LONGITUDE + " DOUBLE, " +
                COLUMN_LATITUDE + " DOUBLE, " +
                COLUMN_TYPE_ID + " INTEGER, " +
                COLUMN_NODE_ID + " INTEGER" + ")";

        private final double longitude;
        private final double latitude;
        private final int type;
        private final int node;

        private Marker(double longitude, double latitude, int type, int node) {
            this.longitude = getLongitude(longitude);
            this.latitude = getLatitude(latitude);
            this.type = type;
            this.node = node;
        }

        private void saveToDB(SQLiteDatabase database) {
            ContentValues values = new ContentValues();
            values.put(COLUMN_LONGITUDE, longitude);
            values.put(COLUMN_LATITUDE,  latitude);
            values.put(COLUMN_TYPE_ID,   type);
            values.put(COLUMN_NODE_ID,   node);
            /*long rowId = */database.insert(TABLE_NAME, null, values);
        }

        public static int getCount(SQLiteDatabase database) {
            return database.rawQuery("SELECT " + _ID  + " from " + TABLE_NAME, null).getCount();
        }

        public static int getCount() {
            SQLiteDatabase database = getReadableDatabase();
            try {
                return getCount(database);
            } finally {
                database.close();
            }
        }
    }

    public static class MarkerDBSQLiteHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "marker_database.db";

        public MarkerDBSQLiteHelper() {
            super(PlantsCache.context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL(Marker.CREATE_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
            clearTable(sqLiteDatabase);
        }

        private void clearTable(SQLiteDatabase sqLiteDatabase) {
            dropTable(sqLiteDatabase);
            onCreate(sqLiteDatabase);
        }

        private void dropTable(SQLiteDatabase sqLiteDatabase) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + Marker.TABLE_NAME);
        }
    }

    private static final String JSON_FEATURES = "features";
    private static final String JSON_POSITION = "pos";
    private static final String JSON_TYPE_ID = "tid";
    private static final String JSON_NODE_ID = "nid";
    private static final String JSON_PROPERTIES = "properties";
    private static final int JSON_INDEX_LONGITUDE = 1;
    private static final int JSON_INDEX_LATITUDE = 0;
    private static final int BULK_INSERT_MARKERS = 500;

    public static void updatePlantMarkers(JSONObject json, Progressable fraction) throws API.ErrorWithExplanation {
        // this is called form the API with all markers needed.
        SQLiteDatabase database = getWritableDatabase();
        int markersAdded = 0;
        database.beginTransaction();
        try {
            JSONObject invalidMarker = null;
            if (json == null || !json.has(JSON_FEATURES)) {
                return;
            }
            // see the api for the response
            // https://github.com/niccokunzmann/mundraub-android/blob/master/docs/api.md#markers
            try {
                JSONArray markers = json.getJSONArray(JSON_FEATURES);
                log.d("number of markers to add", markers.length());
                for (int i = 0; i < markers.length(); i++) {
                    JSONObject markerJSON = markers.getJSONObject(i);
                    if (!markerJSON.has(JSON_POSITION) || !markerJSON.has(JSON_PROPERTIES)) {
                        invalidMarker = markerJSON;
                        continue;
                    }
                    JSONArray position = markerJSON.getJSONArray(JSON_POSITION);
                    JSONObject properties = markerJSON.getJSONObject(JSON_PROPERTIES);
                    if (!properties.has(JSON_TYPE_ID) || !properties.has(JSON_NODE_ID) ||
                        position.length() != 2) {
                        invalidMarker = markerJSON;
                        continue;
                    }
                    Marker marker = new Marker(
                            position.getDouble(JSON_INDEX_LONGITUDE),
                            position.getDouble(JSON_INDEX_LATITUDE),
                            Integer.parseInt(properties.getString(JSON_TYPE_ID)),
                            Integer.parseInt(properties.getString(JSON_NODE_ID))
                    );
                    marker.saveToDB(database);
                    fraction.setProgress(1.0 * i / markers.length());
                    markersAdded++;
                    if (markersAdded % BULK_INSERT_MARKERS == 0) {
                        database.setTransactionSuccessful();
                        database.endTransaction();
                        log.d("bulk insert markers", BULK_INSERT_MARKERS + " " + markersAdded + " of " + markers.length());
                        database.beginTransaction();
                    }
                }
            } catch (JSONException e) {
                log.printStackTrace(e);
                API.abortOperation(R.string.error_invalid_json_for_markers);
                return;
            }
            if (invalidMarker != null) {
                log.e("invalidMarker", invalidMarker.toString());
            }
            database.setTransactionSuccessful(); // from https://stackoverflow.com/a/32088155
            log.d("markers in database", Marker.getCount(database));
        } finally {
            database.endTransaction();
            database.close();
            log.d("markers added", markersAdded);
        }
    }

    private static SQLiteDatabase getWritableDatabase() {
        return new MarkerDBSQLiteHelper().getWritableDatabase();
    }

    private static SQLiteDatabase getReadableDatabase() {
        return new MarkerDBSQLiteHelper().getReadableDatabase();
    }


}
