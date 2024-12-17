package com.example.weatherjavaapp;

import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ChartsFragment extends Fragment {

    private TextView textViewStartDate;
    private TextView textViewEndDate;
    private Button buttonUpdateCharts;

    private Calendar startDateCalendar;
    private Calendar endDateCalendar;

    private DatabaseHelper dbHelper;

    private LineChart temperatureChart;
    private LineChart humidityChart;
    private LineChart luminanceChart;

    public ChartsFragment() {
        // Wymagany pusty konstruktor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = DatabaseHelper.getInstance(getContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_charts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textViewStartDate = view.findViewById(R.id.textViewStartDate);
        textViewEndDate = view.findViewById(R.id.textViewEndDate);
        buttonUpdateCharts = view.findViewById(R.id.buttonUpdateCharts);

        temperatureChart = view.findViewById(R.id.temperatureChart);
        humidityChart = view.findViewById(R.id.humidityChart);
        luminanceChart = view.findViewById(R.id.luminanceChart);

        startDateCalendar = Calendar.getInstance();
        endDateCalendar = Calendar.getInstance();
        startDateCalendar.add(Calendar.DAY_OF_MONTH, -1);

        updateDateInView(true);
        updateDateInView(false);

        textViewStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        textViewEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        buttonUpdateCharts.setOnClickListener(v -> loadChartData());

        // Załaduj dane przy starcie
        loadChartData();
    }

    private void showDatePickerDialog(boolean isStartDate) {
        final Calendar currentCalendar = isStartDate ? startDateCalendar : endDateCalendar;
        int year = currentCalendar.get(Calendar.YEAR);
        int month = currentCalendar.get(Calendar.MONTH);
        int day = currentCalendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                getContext(),
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    currentCalendar.set(Calendar.YEAR, selectedYear);
                    currentCalendar.set(Calendar.MONTH, selectedMonth);
                    currentCalendar.set(Calendar.DAY_OF_MONTH, selectedDay);
                    updateDateInView(isStartDate);
                },
                year, month, day);

        datePickerDialog.show();
    }

    private void updateDateInView(boolean isStartDate) {
        String dateFormat = "dd/MM/yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.getDefault());
        if (isStartDate) {
            textViewStartDate.setText(sdf.format(startDateCalendar.getTime()));
        } else {
            textViewEndDate.setText(sdf.format(endDateCalendar.getTime()));
        }
    }

    private void loadChartData() {
        long startDate = startDateCalendar.getTimeInMillis();
        long endDate = endDateCalendar.getTimeInMillis();

        Cursor cursor = dbHelper.getMeasurements(startDate, endDate);

        List<Measurement> measurements = new ArrayList<>();
        long earliestTimestamp = Long.MAX_VALUE;

        if (cursor != null && cursor.moveToFirst()) {
            Log.d("ChartsFragment", "Pobrano dane z bazy: " + cursor.getCount() + " rekordów.");

            int timestampIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TIMESTAMP);
            int tempIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TEMPERATURE);
            int humIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_HUMIDITY);
            int lumIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_LUMINANCE);

            do {
                long timestamp = cursor.getLong(timestampIndex);
                double temp = cursor.getDouble(tempIndex);
                double hum = cursor.getDouble(humIndex);
                double lum = cursor.getDouble(lumIndex);

                if (timestamp < earliestTimestamp) {
                    earliestTimestamp = timestamp;
                }
                measurements.add(new Measurement(timestamp, temp, hum, lum));
            } while (cursor.moveToNext());

            cursor.close();
        } else {
            Log.d("ChartsFragment", "Brak danych do wyświetlenia.");
        }

        List<Entry> temperatureEntries = new ArrayList<>();
        List<Entry> humidityEntries = new ArrayList<>();
        List<Entry> luminanceEntries = new ArrayList<>();

        for (Measurement m : measurements) {
            float normalizedX = (float) ((m.timestamp - earliestTimestamp) / 1000.0);
            if (m.temp > 0) {
                temperatureEntries.add(new Entry(normalizedX, (float) m.temp));
            }
            if (m.hum > 0) {
                humidityEntries.add(new Entry(normalizedX, (float) m.hum));
            }
            if (m.lum > 0) {
                luminanceEntries.add(new Entry(normalizedX, (float) m.lum));
            }
        }

        updateChart(temperatureChart, temperatureEntries, "Temperatura", earliestTimestamp);
        updateChart(humidityChart, humidityEntries, "Wilgotność", earliestTimestamp);
        updateChart(luminanceChart, luminanceEntries, "Luminancja", earliestTimestamp);
    }

    private void updateChart(LineChart chart, List<Entry> entries, String label, long earliestTimestamp) {
        if (entries.isEmpty()) {
            Log.d("ChartsFragment", "Brak danych do wyświetlenia dla wykresu: " + label);
            chart.clear();
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(android.graphics.Color.WHITE);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        chart.getXAxis().setTextColor(android.graphics.Color.WHITE);
        chart.getAxisLeft().setTextColor(android.graphics.Color.WHITE);
        chart.getAxisRight().setTextColor(android.graphics.Color.WHITE);
        chart.getLegend().setTextColor(android.graphics.Color.WHITE);

        chart.getXAxis().setGranularityEnabled(true);
        chart.getXAxis().setGranularity(1f);

        // Przekazujemy earliestTimestamp do formatera, aby poprawnie sformatować daty
        chart.getXAxis().setValueFormatter(new TimeAxisFormatter(earliestTimestamp));

        chart.invalidate(); // Odśwież wykres
    }

    // Klasa pomocnicza do przechowywania pomiarów
    private static class Measurement {
        long timestamp;
        double temp;
        double hum;
        double lum;

        Measurement(long timestamp, double temp, double hum, double lum) {
            this.timestamp = timestamp;
            this.temp = temp;
            this.hum = hum;
            this.lum = lum;
        }
    }
}
