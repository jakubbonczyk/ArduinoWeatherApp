package com.example.weatherjavaapp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.widget.Toast;

import com.example.weatherjavaapp.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    // Zmienne Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;

    private static final String DEVICE_NAME = "HC-05";
    private static final UUID SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"); // UUID usługi
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"); // UUID charakterystyki

    // Lista callbacków
    private List<BluetoothConnectionCallback> connectionCallbacks = new ArrayList<>();
    private Queue<String> commandQueue = new LinkedList<>();
    private boolean isWriting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inicjalizacja interfejsu użytkownika
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        replaceFragment(new HomeFragment());

        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.home) {
                replaceFragment(new HomeFragment());
            } else if (id == R.id.charts) {
                replaceFragment(new ChartsFragment());
            } else if (id == R.id.settings) {
                replaceFragment(new SettingsFragment());
            }
            return true;
        });

        // Inicjalizacja BluetoothManager
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        // Inicjalizacja BluetoothAdapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        String fragmentTag = fragment.getClass().getSimpleName();
        Fragment existingFragment = fragmentManager.findFragmentByTag(fragmentTag);

        if (existingFragment != null) {
            fragmentTransaction.replace(R.id.frameLayout, existingFragment, fragmentTag);
        } else {
            fragmentTransaction.replace(R.id.frameLayout, fragment, fragmentTag);
        }

        fragmentTransaction.commit();
    }

    // Metody obsługi Bluetooth
    public boolean isBluetoothConnected() throws SecurityException {
        if (bluetoothGatt != null && bluetoothManager != null) {
            int connectionState = bluetoothManager.getConnectionState(bluetoothGatt.getDevice(), BluetoothProfile.GATT);
            return connectionState == BluetoothProfile.STATE_CONNECTED;
        }
        return false;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    public interface BluetoothConnectionCallback {
        void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

        void onDataReceived(String data);
    }

    public void connectToBluetooth(BluetoothConnectionCallback callback) {
        if (!connectionCallbacks.contains(callback)) {
            connectionCallbacks.add(callback);
        }

        // Sprawdzenie uprawnień
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Brak uprawnień Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }

        // Znalezienie urządzenia po nazwie
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        BluetoothDevice device = null;
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice bondedDevice : pairedDevices) {
            if (bondedDevice.getName() != null && bondedDevice.getName().contains(DEVICE_NAME)) {
                device = bondedDevice;
                break;
            }
        }

        if (device == null) {
            Toast.makeText(this, "Nie znaleziono urządzenia " + DEVICE_NAME, Toast.LENGTH_LONG).show();
            return;
        }

        // Nawiązanie połączenia tylko jeśli nie jest już połączone
        if (bluetoothGatt == null) {
            bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
        } else {
            notifyConnected();
        }
    }

    public void removeBluetoothConnectionCallback(BluetoothConnectionCallback callback) {
        connectionCallbacks.remove(callback);
    }

    // Metoda do wysyłania komend
    public boolean sendCommand(String command) {
        if (characteristic != null && bluetoothGatt != null) {
            commandQueue.add(command);
            processCommandQueue();
            return true;
        } else {
            Log.e("BluetoothDebug", "Cannot send command, characteristic or bluetoothGatt is null");
            return false;
        }
    }

    // Metody do powiadamiania callbacków
    private void notifyConnected() {
        for (BluetoothConnectionCallback callback : connectionCallbacks) {
            if (callback != null) {
                callback.onConnected(bluetoothGatt, characteristic);
            }
        }
    }

    private void notifyDataReceived(String data) {
        for (BluetoothConnectionCallback callback : connectionCallbacks) {
            if (callback != null) {
                callback.onDataReceived(data);
            }
        }
    }

    // Implementacja BluetoothGattCallback
    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) throws SecurityException {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BluetoothDebug", "Połączono z urządzeniem!");
                bluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BluetoothDebug", "Rozłączono z urządzeniem");
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                    characteristic = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) throws SecurityException {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothDebug", "Odkryto usługi");
                BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
                if (service != null) {
                    characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if (characteristic != null) {
                        int properties = characteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            Log.d("BluetoothDebug", "Charakterystyka obsługuje powiadomienia");
                        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            Log.d("BluetoothDebug", "Charakterystyka obsługuje wskazania (indications)");
                        } else {
                            Log.e("BluetoothDebug", "Charakterystyka nie obsługuje powiadomień ani wskazań");
                        }

                        bluetoothGatt.setCharacteristicNotification(characteristic, true);

                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            boolean success = bluetoothGatt.writeDescriptor(descriptor);
                            Log.d("BluetoothDebug", "writeDescriptor success: " + success);
                        } else {
                            Log.e("BluetoothDebug", "Nie znaleziono desygnatora dla charakterystyki");
                        }

                        notifyConnected();
                    } else {
                        Log.e("BluetoothDebug", "Nie znaleziono charakterystyki");
                    }
                } else {
                    Log.e("BluetoothDebug", "Nie znaleziono usługi");
                }
            } else {
                Log.w("BluetoothDebug", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onDescriptorWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothDebug", "Descriptor write successful");
            } else {
                Log.e("BluetoothDebug", "Descriptor write failed with status " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            Log.d("BluetoothDebug", "onCharacteristicChanged called");
            byte[] data = characteristic.getValue();
            String receivedData = new String(data);
            Log.d("BluetoothData", "Odebrano: " + receivedData);

            notifyDataReceived(receivedData);
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            isWriting = false;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothDebug", "Characteristic write successful");
                commandQueue.poll();
            } else {
                Log.e("BluetoothDebug", "Characteristic write failed with status " + status);
                commandQueue.poll();
            }
            processCommandQueue();
        }
    };

    @Override
    protected void onDestroy() throws SecurityException {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
            characteristic = null;
        }
    }

    private void processCommandQueue() throws SecurityException {
        if (isWriting || commandQueue.isEmpty()) {
            return;
        }
        String command = commandQueue.peek();
        characteristic.setValue(command.getBytes());
        boolean writeResult = bluetoothGatt.writeCharacteristic(characteristic);
        isWriting = writeResult;
        Log.d("BluetoothDebug", "sendCommand: " + command.trim() + ", writeCharacteristic result: " + writeResult);
        if (!writeResult) {
            commandQueue.poll();
            processCommandQueue();
        }
    }
}