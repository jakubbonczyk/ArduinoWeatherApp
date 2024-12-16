package com.example.weatherjavaapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    // Nazwa bazy danych
    private static final String DATABASE_NAME = "weather_station.db";
    // Wersja bazy danych
    private static final int DATABASE_VERSION = 3;

    // Nazwa tabeli
    public static final String TABLE_MEASUREMENTS = "measurements";

    // Kolumny tabeli
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_TEMPERATURE = "temperature";
    public static final String COLUMN_HUMIDITY = "humidity";
    public static final String COLUMN_LUMINANCE = "luminance";

    // Tworzenie tabeli
    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_MEASUREMENTS + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_TEMPERATURE + " REAL, " +
                    COLUMN_HUMIDITY + " REAL, " +
                    COLUMN_LUMINANCE + " REAL" +
                    ");";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        // Tworzenie tabeli
        database.execSQL(TABLE_CREATE);

        // Dodanie sztucznych danych
        insertDummyData(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Aktualizacja bazy danych
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENTS);
        onCreate(database);
    }

    private void insertDummyData(SQLiteDatabase db) {
        // Przygotowanie sztucznych danych
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < 720; i++) {
            long timestamp = currentTime - (i * 3600 * 1000); // Co godzinę wstecz
            double temperature = 20 + Math.random() * 10; // Temperatura między 20 a 30
            double humidity = 40 + Math.random() * 20; // Wilgotność między 40 a 60
            double luminance = 100 + Math.random() * 900; // Luminancja między 100 a 1000

            ContentValues values = new ContentValues();
            values.put(COLUMN_TIMESTAMP, timestamp);
            values.put(COLUMN_TEMPERATURE, temperature);
            values.put(COLUMN_HUMIDITY, humidity);
            values.put(COLUMN_LUMINANCE, luminance);

            db.insert(TABLE_MEASUREMENTS, null, values);
        }
    }

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
        String[] selectionArgs = { String.valueOf(startDate), String.valueOf(endDate) };
        String orderBy = COLUMN_TIMESTAMP + " ASC";

        return db.query(
                TABLE_MEASUREMENTS,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
        );
    }

}
