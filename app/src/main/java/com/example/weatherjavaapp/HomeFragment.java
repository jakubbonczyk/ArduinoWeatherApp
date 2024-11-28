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

import android.bluetooth.BluetoothProfile;
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
        } else {
            checkPermissionsAndConnect();
        }

        // Ustawienie listenera dla przycisku refreshButton
        binding.refreshButton.setOnClickListener(v -> {
            if (mainActivity.isBluetoothConnected()) {
                mainActivity.resetFlags();
                mainActivity.sendCommand("Dane\n");
            } else {
                Toast.makeText(getContext(), "Brak połączenia Bluetooth", Toast.LENGTH_LONG).show();
            }
        });
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
        // Sprawdź, czy połączenie już istnieje
        if (mainActivity.isBluetoothConnected()) {
            bluetoothGatt = mainActivity.getBluetoothGatt();
            characteristic = mainActivity.getCharacteristic();
            // Jeśli połączenie istnieje, możesz od razu korzystać z danych
            return;
        }

        // Jeśli połączenie nie istnieje, nawiąż nowe
        mainActivity.connectToBluetooth(new MainActivity.BluetoothConnectionCallback() {
            @Override
            public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                bluetoothGatt = gatt;
                HomeFragment.this.characteristic = characteristic;
            }

            @Override
            public void onDataReceived(String data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateUI(data));
                }
            }
        });
    }

    private void updateUI(String data) {
        Log.d("BluetoothDebug", "updateUI called with data: " + data);
        // Podziel dane na linie, jeśli jest ich więcej
        String[] lines = data.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains("Temperatura:")) {
                String temperature = line.substring(line.indexOf(":") + 1).trim();
                binding.textView.setText(temperature + " °C");
            } else if (line.contains("Wilgotność:")) {
                String humidity = line.substring(line.indexOf(":") + 1).trim();
                binding.textView2.setText(humidity + " %");
            } else if (line.contains("Natężenie światła:")) {
                String lux = line.substring(line.indexOf(":") + 1).trim();
                binding.textView4.setText(lux + " lx");
            }
        }
    }

    // Usuń metodę onDestroy(), aby nie zamykać połączenia
}
