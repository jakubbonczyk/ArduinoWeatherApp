package com.example.weatherjavaapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "weather_station.db";
    private static final int DATABASE_VERSION = 24;

    public static final String TABLE_MEASUREMENTS = "measurements";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_TEMPERATURE = "temperature";
    public static final String COLUMN_HUMIDITY = "humidity";
    public static final String COLUMN_LUMINANCE = "luminance";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_MEASUREMENTS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_TEMPERATURE + " REAL, " +
                    COLUMN_HUMIDITY + " REAL, " +
                    COLUMN_LUMINANCE + " REAL" +
                    ");";

    private static DatabaseHelper instance;

    // Singleton - jedna instancja DatabaseHelper
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENTS);
        onCreate(database);
    }

    // Wstawianie danych pomiarowych
    public void insertMeasurement(long timestamp, Double temperature, Double humidity, Double luminance) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_TEMPERATURE, temperature != null ? temperature : null);
        values.put(COLUMN_HUMIDITY, humidity != null ? humidity : null);
        values.put(COLUMN_LUMINANCE, luminance != null ? luminance : null);

        long result = db.insert(TABLE_MEASUREMENTS, null, values);

        if (result == -1) {
            Log.e("DatabaseHelper", "Błąd podczas zapisu danych do bazy.");
        } else {
            Log.d("DatabaseHelper", "Zapisano dane do bazy: temp=" + temperature + ", hum=" + humidity + ", lux=" + luminance);
        }
    }

    // Pobieranie danych pomiarowych w zakresie dat
    public Cursor getMeasurements(long startDate, long endDate) {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {
                COLUMN_ID,
                COLUMN_TIMESTAMP,
                COLUMN_TEMPERATURE,
                COLUMN_HUMIDITY,
                COLUMN_LUMINANCE
        };

        String selection = COLUMN_TIMESTAMP + " BETWEEN ? AND ?";
        String[] selectionArgs = {String.valueOf(startDate), String.valueOf(endDate)};
        String orderBy = COLUMN_TIMESTAMP + " ASC";

        Cursor cursor = db.query(
                TABLE_MEASUREMENTS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
        );

        if (cursor != null) {
            Log.d("DatabaseHelper", "Znaleziono wierszy: " + cursor.getCount());
        }
        return cursor;
    }

    // Pobieranie ostatniego zapisanego rekordu
    public Cursor getLastMeasurement() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_MEASUREMENTS +
                " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT 1";
        Cursor cursor = db.rawQuery(query, null);

        if (cursor != null) {
            Log.d("DatabaseHelper", "Pobrano ostatni pomiar z bazy.");
        }
        return cursor;
    }

    // Czyszczenie wszystkich danych
    public void clearAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_MEASUREMENTS, null, null);
        Log.d("DatabaseHelper", "Wyczyszczono wszystkie dane z tabeli.");
    }
}
