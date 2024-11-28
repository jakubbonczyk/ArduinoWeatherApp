package com.example.weatherjavaapp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.IBinder;

public class BluetoothLeService extends Service {
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;

    // Metody do zarządzania połączeniem Bluetooth

    public void connect(String deviceAddress) {
        // Implementacja nawiązania połączenia
    }

    public void disconnect() throws SecurityException{
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
    }

    public void close() throws SecurityException{
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    // Inne metody, np. do wysyłania i odbierania danych

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
