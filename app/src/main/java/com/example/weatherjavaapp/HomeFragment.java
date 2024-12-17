package com.example.weatherjavaapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.weatherjavaapp.databinding.FragmentHomeBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private BluetoothAdapter bluetoothAdapter;

    private static final int REQUEST_ENABLE_BT = 2;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private MainActivity mainActivity;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;

    private DatabaseHelper dbHelper;

    // Referencja do callbacku
    private MainActivity.BluetoothConnectionCallback connectionCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Singleton DatabaseHelper
        dbHelper = DatabaseHelper.getInstance(getContext());

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            for (Boolean granted : result.values()) {
                if (!granted) {
                    Toast.makeText(getContext(), "Wymagane uprawnienia do Bluetooth", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof MainActivity) {
            mainActivity = (MainActivity) context;
        } else {
            throw new RuntimeException(context.toString() + " musi być MainActivity");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getActivity(), "Bluetooth nie jest dostępny", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        binding.refreshButton.setOnClickListener(v -> {
            if (mainActivity.isBluetoothConnected()) {
                boolean commandSent = mainActivity.sendCommand("{\"cmd\": \"getData\"}\n");
                if (!commandSent) {
                    redirectToBluetoothSettings();
                }
            } else {
                connectToBluetooth();
            }
        });

        updateUIFromDatabase(); // Odśwież UI na starcie
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUIFromDatabase(); // Odśwież UI po powrocie na fragment
    }

    private void redirectToBluetoothSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
        Toast.makeText(getContext(), "Połącz się z urządzeniem HC-05", Toast.LENGTH_LONG).show();
    }

    private void connectToBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(new String[]{Manifest.permission.BLUETOOTH_CONNECT});
                return;
            }
        }

        connectionCallback = new MainActivity.BluetoothConnectionCallback() {
            @Override
            public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                bluetoothGatt = gatt;
                HomeFragment.this.characteristic = characteristic;
                mainActivity.sendCommand("{\"cmd\": \"getData\"}\n");
            }

            @Override
            public void onDataReceived(String data) {
                if (getActivity() != null) { // Upewnij się, że aktywność nie jest null
                    getActivity().runOnUiThread(() -> updateUI(data));
                } else {
                    Log.w("HomeFragment", "Fragment nie jest już podłączony do aktywności.");
                }
            }
        };

        mainActivity.connectToBluetooth(connectionCallback);
    }

    private void updateUI(String data) {
        Log.d("BluetoothDebug", "Otrzymano dane: " + data);

        try {
            JSONObject jsonObject = new JSONObject(data);

            double temperature = jsonObject.optDouble("temp", -1);
            double humidity = jsonObject.optDouble("hum", -1);
            double luminance = jsonObject.optDouble("lux", -1);

            Log.d("BluetoothDebug", "Parsowane wartości: temp=" + temperature + ", hum=" + humidity + ", lux=" + luminance);

            long currentTime = System.currentTimeMillis();

            dbHelper.insertMeasurement(
                    currentTime,
                    temperature != -1 ? temperature : null,
                    humidity != -1 ? humidity : null,
                    luminance != -1 ? luminance : null
            );

            updateUIFromDatabase(); // Aktualizacja UI z bazy danych

        } catch (JSONException e) {
            Log.e("BluetoothDebug", "Błąd parsowania JSON: " + e.getMessage());
        }
    }

    private void updateUIFromDatabase() {
        Cursor cursor = dbHelper.getLastMeasurement();
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") double temp = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_TEMPERATURE));
            @SuppressLint("Range") double hum = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_HUMIDITY));
            @SuppressLint("Range") double lux = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_LUMINANCE));

            Log.d("HomeFragment", "Ostatnie wartości: temp=" + temp + ", hum=" + hum + ", lux=" + lux);

            if (temp != 0.0) binding.textView.setText(String.format(Locale.getDefault(), "%.1f °C", temp));
            if (hum != 0.0) binding.textView2.setText(String.format(Locale.getDefault(), "%.1f %%", hum));
            if (lux != 0.0) binding.textView4.setText(String.format(Locale.getDefault(), "%.2f lx", lux));

            cursor.close();
        } else {
            Log.d("HomeFragment", "Brak danych w bazie.");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainActivity != null && connectionCallback != null) {
            mainActivity.removeBluetoothConnectionCallback(connectionCallback);
            connectionCallback = null; // Usuń referencję do callbacku
        }
    }

}
