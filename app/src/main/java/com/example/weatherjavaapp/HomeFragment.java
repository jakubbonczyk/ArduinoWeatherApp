package com.example.weatherjavaapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.pm.PackageManager;
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

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private BluetoothAdapter bluetoothAdapter;

    private static final int REQUEST_ENABLE_BT = 2;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private MainActivity mainActivity;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;

    // Referencja do callbacku
    private MainActivity.BluetoothConnectionCallback connectionCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        // Inicjalizacja permissionLauncher
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            boolean allPermissionsGranted = true;
            for (Boolean granted : result.values()) {
                if (!granted) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (allPermissionsGranted) {
                // Nie łączymy się automatycznie
            } else {
                Toast.makeText(getContext(), "Wymagane uprawnienia do połączenia Bluetooth", Toast.LENGTH_LONG).show();
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
            // Poproś o włączenie Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Ustawienie listenera dla przycisku refreshButton
        binding.refreshButton.setOnClickListener(v -> {
            if (mainActivity.isBluetoothConnected()) {
                mainActivity.sendCommand("Dane\n");
                mainActivity.sendCommand("Swiatlo\n");
            } else {
                // Spróbuj nawiązać połączenie
                checkPermissionsAndConnect();
            }
        });
    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Dla Androida 12 i wyżej
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

                // Żądanie uprawnień
                permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                });
            } else {
                connectToBluetooth();
            }
        } else {
            // Dla Androida poniżej wersji 12
            connectToBluetooth();
        }
    }

    private void connectToBluetooth() {
        // Tworzymy nowy callback
        connectionCallback = new MainActivity.BluetoothConnectionCallback() {
            @Override
            public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                bluetoothGatt = gatt;
                HomeFragment.this.characteristic = characteristic;

                // Po połączeniu, wysyłamy komendy
                mainActivity.sendCommand("Dane\n");
                mainActivity.sendCommand("Swiatlo\n");
            }

            @Override
            public void onDataReceived(String data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateUI(data));
                }
            }
        };

        // Rejestrujemy callback w MainActivity
        mainActivity.connectToBluetooth(connectionCallback);
    }

    private void updateUI(String data) {
        Log.d("BluetoothDebug", "updateUI called with data: " + data);
        // Podziel dane na linie, jeśli jest ich więcej
        String[] lines = data.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains("Temperatura:")) {
                String temperature = line.substring(line.indexOf(":") + 1).trim();
                binding.textView.setText(temperature);
            } else if (line.contains("Wilgotność:")) {
                String humidity = line.substring(line.indexOf(":") + 1).trim();
                binding.textView2.setText(humidity);
            } else if (line.contains("Natężenie światła:")) {
                String lux = line.substring(line.indexOf(":") + 1).trim();
                binding.textView4.setText(lux);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Usuwamy callback z MainActivity
        if (mainActivity != null && connectionCallback != null) {
            mainActivity.removeBluetoothConnectionCallback(connectionCallback);
        }
    }
}
