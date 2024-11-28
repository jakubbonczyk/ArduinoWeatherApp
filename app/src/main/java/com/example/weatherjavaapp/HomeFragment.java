package com.example.weatherjavaapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
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

import android.bluetooth.BluetoothProfile;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.weatherjavaapp.databinding.FragmentHomeBinding;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    private static final String DEVICE_NAME = "HC-05"; // lub nazwa Twojego urządzenia BLE
    private static final UUID SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"); // Przykładowy UUID usługi
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"); // Przykładowy UUID charakterystyki

    private static final int REQUEST_ENABLE_BT = 2;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private BluetoothGattCharacteristic characteristic;

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
                connectToBluetooth();
            } else {
                Toast.makeText(getContext(), "Wymagane uprawnienia do połączenia Bluetooth", Toast.LENGTH_LONG).show();
            }
        });

        return binding.getRoot();
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
        } else {
            checkPermissionsAndConnect();
        }
    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Dla Androida 12 i wyżej
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                // Żądanie uprawnień
                permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                });
            } else {
                connectToBluetooth();
            }
        } else {
            // Dla Androida poniżej wersji 12
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Żądanie uprawnień
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION
                });
            } else {
                connectToBluetooth();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ENABLE_BT){
            if(resultCode == Activity.RESULT_OK){
                checkPermissionsAndConnect();
            } else {
                Toast.makeText(getContext(), "Bluetooth musi być włączony, aby kontynuować", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void connectToBluetooth() {
        BluetoothDevice device = null;

        // Sprawdzenie uprawnień
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            if (isAdded()) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity(), "Brak uprawnień Bluetooth", Toast.LENGTH_LONG).show()
                );
            }
            return;
        }

        // Znalezienie urządzenia po nazwie
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothAdapter.startDiscovery();
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bondedDevice : pairedDevices) {
            if (bondedDevice.getName() != null && bondedDevice.getName().contains(DEVICE_NAME)) {
                device = bondedDevice;
                break;
            }
        }

        if (device == null) {
            Toast.makeText(getActivity(), "Nie znaleziono urządzenia " + DEVICE_NAME, Toast.LENGTH_LONG).show();
            return;
        }

        // Nawiązanie połączenia BLE
        bluetoothGatt = device.connectGatt(getContext(), false, bluetoothGattCallback);
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) throws SecurityException {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BluetoothDebug", "Połączono z urządzeniem!");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BluetoothDebug", "Rozłączono z urządzeniem");
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) throws SecurityException {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothDebug", "Odkryto usługi");
                // Znajdź odpowiednią usługę i charakterystykę
                BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
                if (service != null) {
                    characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        // Włącz powiadomienia o zmianie charakterystyki
                        bluetoothGatt.setCharacteristicNotification(characteristic, true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            bluetoothGatt.writeDescriptor(descriptor);
                        }
                    }
                }
            } else {
                Log.w("BluetoothDebug", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            // Odbierz dane z urządzenia
            byte[] data = characteristic.getValue();
            String receivedData = new String(data);
            Log.d("BluetoothData", "Odebrano: " + receivedData);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> updateUI(receivedData));
            }
        }
    };

    private void updateUI(String data) {
        if (data.contains("Temperatura")) {
            String temperature = data.substring(data.indexOf(":") + 1, data.indexOf("°")).trim();
            binding.textView.setText(temperature + "°C");
        }
        if (data.contains("Wilgotność")) {
            String humidity = data.substring(data.indexOf(":") + 1, data.indexOf("%")).trim();
            binding.textView2.setText(humidity + "%");
        }
        if (data.contains("Natężenie światła")) {
            String lux = data.substring(data.indexOf(":") + 1, data.indexOf("lx")).trim();
            binding.textView4.setText(lux + " lx");
        }
    }

    @Override
    public void onDestroy() throws SecurityException {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
