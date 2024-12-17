package com.example.weatherjavaapp;

import com.github.mikephil.charting.formatter.ValueFormatter;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class TimeAxisFormatter extends ValueFormatter {

    private long baseTime; // earliestTimestamp w milisekundach
    private SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM", Locale.getDefault());

    public TimeAxisFormatter(long baseTime) {
        this.baseTime = baseTime;
    }

    @Override
    public String getFormattedValue(float value) {
        // value to sekundy od baseTime
        long actualTimeMillis = baseTime + (long) (value * 1000L);
        return sdf.format(actualTimeMillis);
    }
}
