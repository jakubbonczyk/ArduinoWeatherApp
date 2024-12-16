package com.example.weatherjavaapp;

import android.app.DatePickerDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
// Dodane importy
import android.widget.DatePicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Dodany import
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart; // Dodany import
import com.github.mikephil.charting.data.Entry; // Dodany import
import com.github.mikephil.charting.data.LineData; // Dodany import
import com.github.mikephil.charting.data.LineDataSet; // Dodany import

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ChartsFragment extends Fragment {

    private TextView textViewStartDate;
    private TextView textViewEndDate;
    private Button buttonUpdateCharts;

    private Calendar startDateCalendar;
    private Calendar endDateCalendar;

    private DatabaseHelper dbHelper;

    // Dodaj zmienne dla wykresów
    private LineChart temperatureChart;
    private LineChart humidityChart;
    private LineChart luminanceChart;

    public ChartsFragment() {
        // Wymagany pusty konstruktor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inicjalizacja DatabaseHelper
        dbHelper = new DatabaseHelper(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflatujemy layout fragment_charts.xml
        return inflater.inflate(R.layout.fragment_charts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicjalizacja widoków
        textViewStartDate = view.findViewById(R.id.textViewStartDate);
        textViewEndDate = view.findViewById(R.id.textViewEndDate);
        buttonUpdateCharts = view.findViewById(R.id.buttonUpdateCharts);

        // Inicjalizacja wykresów
        temperatureChart = view.findViewById(R.id.temperatureChart);
        humidityChart = view.findViewById(R.id.humidityChart);
        luminanceChart = view.findViewById(R.id.luminanceChart);

        // Inicjalizacja kalendarzy z aktualną datą
        startDateCalendar = Calendar.getInstance();
        endDateCalendar = Calendar.getInstance();

        // Ustawienie domyślnego zakresu dat (np. ostatnie 24 godziny)
        startDateCalendar.add(Calendar.DAY_OF_MONTH, -1); // Odejmij jeden dzień

        // Wyświetlenie domyślnych dat w TextView
        updateDateInView(true);
        updateDateInView(false);

        // Ustawienie listenerów na TextView dla wyboru dat
        textViewStartDate.setOnClickListener(v -> showDatePickerDialog(true));
        textViewEndDate.setOnClickListener(v -> showDatePickerDialog(false));

        // Listener dla przycisku aktualizacji wykresów
        buttonUpdateCharts.setOnClickListener(v -> loadChartData());

        // Załaduj dane przy pierwszym uruchomieniu
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
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(dateFormat);
        if (isStartDate) {
            textViewStartDate.setText(sdf.format(startDateCalendar.getTime()));
        } else {
            textViewEndDate.setText(sdf.format(endDateCalendar.getTime()));
        }
    }

    private void loadChartData() {
        // Pobierz dane z bazy
        long startDate = startDateCalendar.getTimeInMillis();
        long endDate = endDateCalendar.getTimeInMillis();

        Cursor cursor = dbHelper.getMeasurements(startDate, endDate);

        // Listy do przechowywania danych
        List<Entry> temperatureEntries = new ArrayList<>();
        List<Entry> humidityEntries = new ArrayList<>();
        List<Entry> luminanceEntries = new ArrayList<>();

        if (cursor != null && cursor.moveToFirst()) {
            int index = 0;

            int timestampIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TIMESTAMP);
            int tempIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_TEMPERATURE);
            int humIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_HUMIDITY);
            int lumIndex = cursor.getColumnIndex(DatabaseHelper.COLUMN_LUMINANCE);

            if (timestampIndex >= 0 && tempIndex >= 0 && humIndex >= 0 && lumIndex >= 0) {
                do {
                    long time = cursor.getLong(timestampIndex);
                    double temp = cursor.getDouble(tempIndex);
                    double hum = cursor.getDouble(humIndex);
                    double lum = cursor.getDouble(lumIndex);

                    // Dodanie danych do list
                    temperatureEntries.add(new Entry(index, (float) temp));
                    humidityEntries.add(new Entry(index, (float) hum));
                    luminanceEntries.add(new Entry(index, (float) lum));

                    index++;
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        // Aktualizacja wykresów
        updateChart(temperatureChart, temperatureEntries, "Temperatura");
        updateChart(humidityChart, humidityEntries, "Wilgotność");
        updateChart(luminanceChart, luminanceEntries, "Luminancja");
    }

    private void updateChart(LineChart chart, List<Entry> entries, String label) {
        LineDataSet dataSet = new LineDataSet(entries, label);
        dataSet.setColor(android.graphics.Color.WHITE); // Kolor linii wykresu
        dataSet.setValueTextColor(android.graphics.Color.WHITE); // Kolor wartości na wykresie

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // Ustawienia koloru osi i innych elementów
        chart.getXAxis().setTextColor(android.graphics.Color.WHITE); // Kolor tekstu osi X
        chart.getAxisLeft().setTextColor(android.graphics.Color.WHITE); // Kolor tekstu osi Y (lewa strona)
        chart.getAxisRight().setTextColor(android.graphics.Color.WHITE); // Kolor tekstu osi Y (prawa strona)
        chart.getLegend().setTextColor(android.graphics.Color.WHITE); // Kolor legendy
//        chart.getDescription().setTextColor(android.graphics.Color.WHITE); // Kolor opisu wykresu

        chart.invalidate(); // Odśwież wykres
    }

}
