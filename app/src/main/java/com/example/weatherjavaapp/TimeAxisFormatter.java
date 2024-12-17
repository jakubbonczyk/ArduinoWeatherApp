package com.example.weatherjavaapp;

import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeAxisFormatter extends ValueFormatter {

    private final SimpleDateFormat dateFormat;

    public TimeAxisFormatter() {
        // Używany format daty - możesz go zmodyfikować
        this.dateFormat = new SimpleDateFormat("HH:mm\ndd/MM", Locale.getDefault());
    }

    @Override
    public String getFormattedValue(float value) {
        // Przekształca wartość float (timestamp w milisekundach) na czytelną datę
        long timestamp = (long) value;
        return dateFormat.format(new Date(timestamp));
    }
}
