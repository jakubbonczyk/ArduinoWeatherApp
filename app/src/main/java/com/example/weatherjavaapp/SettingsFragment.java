package com.example.weatherjavaapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

public class SettingsFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Przycisk wyboru urządzenia Bluetooth
        Button buttonBluetooth = view.findViewById(R.id.button_select_bluetooth_device);
        buttonBluetooth.setOnClickListener(v -> openBluetoothDeviceSelection());

        // Spinner interwału
        Spinner spinnerInterval = view.findViewById(R.id.spinner_interval);
        setupIntervalSpinner(spinnerInterval);

        return view;
    }

    private void openBluetoothDeviceSelection() {
        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
        Toast.makeText(getContext(), "Wybierz urządzenie Bluetooth", Toast.LENGTH_SHORT).show();
    }

    private void setupIntervalSpinner(Spinner spinner) {
        String[] intervals = {"15 minut", "30 minut", "1 godzina", "2 godziny"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, intervals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Pobierz zapisane ustawienie
        String savedInterval = getActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .getString("data_interval", "15 minut");
        int selectedIndex = java.util.Arrays.asList(intervals).indexOf(savedInterval);
        spinner.setSelection(selectedIndex);

        // Obsługa wyboru
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedInterval = intervals[position];
                getActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                        .edit()
                        .putString("data_interval", selectedInterval)
                        .apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }


    @Override
    public void onResume() {
        super.onResume();
        String savedInterval = getActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .getString("data_interval", "15 minut");
        Toast.makeText(getContext(), "Zapisany interwał: " + savedInterval, Toast.LENGTH_SHORT).show();
    }



}