package com.example.weatherjavaapp;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import com.example.weatherjavaapp.databinding.ActivityMainBinding;

import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    ActivityMainBinding binding;

    // Zmienne Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic characteristic;

    private static final String DEVICE_NAME = "HC-05"; // lub nazwa Twojego urządzenia BLE
    private static final UUID SERVICE_UUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB"); // Przykładowy UUID usługi
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB"); // Przykładowy UUID charakterystyki

    private BluetoothConnectionCallback connectionCallback;

    // Flagi do śledzenia, czy komendy zostały wysłane
    private boolean sentDane = false;
    private boolean sentSwiatlo = false;

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
                replaceFragment(new ChartsFragment()); // Odwołanie do ChartsFragment
            } else if (id == R.id.settings) {
                replaceFragment(new SettingsFragment());
            }
            return true;
        });

        // Inicjalizacja BluetoothAdapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private void replaceFragment (Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.frameLayout, fragment);
        fragmentTransaction.commit();
    }

    // Metody obsługi Bluetooth
    public boolean isBluetoothConnected() {
        return bluetoothGatt != null;
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
        this.connectionCallback = callback;

        // Sprawdzenie uprawnień
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            // Obsłuż brak uprawnień
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

        // Nawiązanie połączenia BLE
        bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback);
    }

    // Ustawiamy metodę sendCommand jako publiczną
    public void sendCommand(String command) throws SecurityException {
        if (characteristic != null && bluetoothGatt != null) {
            characteristic.setValue(command.getBytes());
            boolean writeResult = bluetoothGatt.writeCharacteristic(characteristic);
            Log.d("BluetoothDebug", "sendCommand: " + command.trim() + ", writeCharacteristic result: " + writeResult);
        } else {
            Log.e("BluetoothDebug", "Cannot send command, characteristic or bluetoothGatt is null");
        }
    }

    // Metoda do resetowania flag
    public void resetFlags() {
        sentDane = false;
        sentSwiatlo = false;
    }

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
                    resetFlags();
                }
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
                        // Sprawdź, czy charakterystyka obsługuje powiadomienia
                        int properties = characteristic.getProperties();
                        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            Log.d("BluetoothDebug", "Charakterystyka obsługuje powiadomienia");
                        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                            Log.d("BluetoothDebug", "Charakterystyka obsługuje wskazania (indications)");
                        } else {
                            Log.e("BluetoothDebug", "Charakterystyka nie obsługuje powiadomień ani wskazań");
                        }

                        // Włącz powiadomienia o zmianie charakterystyki
                        bluetoothGatt.setCharacteristicNotification(characteristic, true);

                        // Zapisz desygnator, aby włączyć powiadomienia
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            boolean success = bluetoothGatt.writeDescriptor(descriptor);
                            Log.d("BluetoothDebug", "writeDescriptor success: " + success);
                            // Resetujemy flagi
                            resetFlags();
                        } else {
                            Log.e("BluetoothDebug", "Nie znaleziono descriptor dla charakterystyki");
                        }

                        // Przekaż informację o połączeniu
                        if (connectionCallback != null) {
                            connectionCallback.onConnected(bluetoothGatt, characteristic);
                        }
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
                // Wysyłamy komendę "Dane" do urządzenia
                sendCommand("Dane\n");
                sentDane = true;
            } else {
                Log.e("BluetoothDebug", "Descriptor write failed with status " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            Log.d("BluetoothDebug", "onCharacteristicChanged called");
            // Odbierz dane z urządzenia
            byte[] data = characteristic.getValue();
            String receivedData = new String(data);
            Log.d("BluetoothData", "Odebrano: " + receivedData);
            if (connectionCallback != null) {
                connectionCallback.onDataReceived(receivedData);
            }

            // Wysyłamy kolejną komendę, jeśli to konieczne
            if (sentDane && !sentSwiatlo) {
                sentSwiatlo = true;
                sendCommand("Swiatlo\n");
            }
        }

        @Override
        public void onCharacteristicWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BluetoothDebug", "Characteristic write successful");
            } else {
                Log.e("BluetoothDebug", "Characteristic write failed with status " + status);
            }
        }
    };

    @Override
    protected void onDestroy() throws SecurityException {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
            characteristic = null;
            resetFlags();
        }
    }
}
