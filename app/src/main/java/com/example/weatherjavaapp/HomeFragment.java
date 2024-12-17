package com.example.weatherjavaapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler; // DODANE DO ODŚWIEŻANIA ZEGARA
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.example.weatherjavaapp.databinding.FragmentHomeBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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
    private MainActivity.BluetoothConnectionCallback connectionCallback;

    // Handler i Runnable do obsługi interwału
    private Handler intervalHandler = new Handler();
    private Runnable intervalRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d("HomeFragment", "Czas interwału minął. Próba wysłania komendy getData");
            if (mainActivity.isBluetoothConnected()) {
                boolean commandSent = mainActivity.sendCommand("{\"cmd\": \"getData\"}\n");
                if (commandSent) {
                    Log.d("HomeFragment", "Komenda getData wysłana pomyślnie.");
                } else {
                    Log.w("HomeFragment", "Nie udało się wysłać komendy getData. Spróbuj ponownie połączyć?");
                }
            } else {
                Log.w("HomeFragment", "Brak połączenia Bluetooth, nie można pobrać danych.");
                Toast.makeText(getActivity(), "Nie pobrano według interwału, kliknij ikonę odświeżania", Toast.LENGTH_LONG).show();
            }
            // Po wysłaniu komendy ponownie ustaw opóźnienie
            intervalHandler.postDelayed(this, currentIntervalMillis);
        }
    };

    // Aktualnie wybrany interwał w milisekundach
    private long currentIntervalMillis = 0;

    // DODANE DO ODŚWIEŻANIA ZEGARA:
    private Handler timeHandler = new Handler();
    private Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            // Odśwież tylko zegar i tło, dane nie są za każdym razem pobierane z bazy
            updateDateTimeAndBackground();
            timeHandler.postDelayed(this, 1000); // odśwież co 1 sekundę
        }
    };
    // KONIEC DODANEGO FRAGMENTU

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
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
            Log.d("HomeFragment", "Ręczne odświeżanie danych.");
            if (mainActivity.isBluetoothConnected()) {
                boolean commandSent = mainActivity.sendCommand("{\"cmd\": \"getData\"}\n");
                if (!commandSent) {
                    redirectToBluetoothSettings();
                } else {
                    Log.d("HomeFragment", "Komenda getData wysłana pomyślnie (ręczne odświeżenie).");
                }
            } else {
                connectToBluetooth();
            }
        });

        updateUIFromDatabase();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainActivity.isBluetoothConnected() && connectionCallback == null) {
            connectionCallback = new MainActivity.BluetoothConnectionCallback() {
                @Override
                public void onConnected(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    bluetoothGatt = gatt;
                    HomeFragment.this.characteristic = characteristic;
                    Log.d("HomeFragment", "Połączono z urządzeniem Bluetooth.");
                }

                @Override
                public void onDataReceived(String data) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> updateUI(data));
                    }
                }
            };
            mainActivity.addBluetoothConnectionCallback(connectionCallback);
        }
        updateUIFromDatabase();

        // Odczytaj interwał z SharedPreferences
        String savedInterval = requireActivity().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .getString("data_interval", "5 sekund");
        Log.d("HomeFragment", "Odczytano interwał z SharedPreferences: " + savedInterval);
        currentIntervalMillis = convertIntervalToMillis(savedInterval);
        Log.d("HomeFragment", "Interwał w ms: " + currentIntervalMillis);

        // Uruchom okresowe pobieranie danych po wybranym interwale
        if (currentIntervalMillis > 0) {
            intervalHandler.removeCallbacks(intervalRunnable);
            intervalHandler.postDelayed(intervalRunnable, currentIntervalMillis);
            Log.d("HomeFragment", "Uruchomiono Handler do okresowego pobierania danych.");
        } else {
            Log.d("HomeFragment", "Interwał = 0, nie uruchamiam okresowego pobierania danych.");
        }

        // DODANE DO ODŚWIEŻANIA ZEGARA:
        timeHandler.post(timeUpdater);
        // KONIEC DODANEGO FRAGMENTU
    }

    @Override
    public void onPause() {
        super.onPause();
        // Zatrzymaj odświeżanie po interwale, gdy fragment nie jest widoczny
        intervalHandler.removeCallbacks(intervalRunnable);
        Log.d("HomeFragment", "Fragment nieaktywny - zatrzymuję okresowe pobieranie danych.");

        // DODANE DO ODŚWIEŻANIA ZEGARA:
        timeHandler.removeCallbacks(timeUpdater);
        // KONIEC DODANEGO FRAGMENTU
    }

    private void redirectToBluetoothSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
        Toast.makeText(getContext(), "Połącz się z urządzeniem HC-05", Toast.LENGTH_LONG).show();
    }

    private void connectToBluetooth() {
        Log.d("HomeFragment", "Próba połączenia Bluetooth.");
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
                boolean commandSent = mainActivity.sendCommand("{\"cmd\": \"getData\"}\n");
                Log.d("HomeFragment", "Połączono i wysłano komendę getData: " + commandSent);
            }

            @Override
            public void onDataReceived(String data) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> updateUI(data));
                } else {
                    Log.w("HomeFragment", "Fragment nie jest już podłączony do aktywności.");
                }
            }
        };

        mainActivity.connectToBluetooth(connectionCallback);
    }

    private void updateUI(String data) {
        Log.d("HomeFragment", "Otrzymano dane: " + data);
        try {
            JSONObject jsonObject = new JSONObject(data);
            double temperature = jsonObject.optDouble("temp", -1);
            double humidity = jsonObject.optDouble("hum", -1);
            double luminance = jsonObject.optDouble("lux", -1);

            Log.d("HomeFragment", "Parsowane wartości: temp=" + temperature + ", hum=" + humidity + ", lux=" + luminance);

            long currentTime = System.currentTimeMillis();
            dbHelper.insertMeasurement(
                    currentTime,
                    temperature != -1 ? temperature : null,
                    humidity != -1 ? humidity : null,
                    luminance != -1 ? luminance : null
            );
            Log.d("HomeFragment", "Zapisano pomiar do bazy.");

            updateUIFromDatabase();

        } catch (JSONException e) {
            Log.e("HomeFragment", "Błąd parsowania JSON: " + e.getMessage());
        }
    }

    private void updateUIFromDatabase() {
        Cursor cursor = dbHelper.getLastMeasurement();
        if (cursor != null && cursor.moveToFirst()) {
            @SuppressLint("Range") double temp = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_TEMPERATURE));
            @SuppressLint("Range") double hum = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_HUMIDITY));
            @SuppressLint("Range") double lux = cursor.getDouble(cursor.getColumnIndex(DatabaseHelper.COLUMN_LUMINANCE));

            Log.d("HomeFragment", "Ostatnie wartości z bazy: temp=" + temp + ", hum=" + hum + ", lux=" + lux);

            if (temp != 0.0) binding.textView.setText(String.format(Locale.getDefault(), "%.1f °C", temp));
            if (hum != 0.0) binding.textView2.setText(String.format(Locale.getDefault(), "%.1f %%", hum));
            if (lux != 0.0) binding.textView4.setText(String.format(Locale.getDefault(), "%.2f lx", lux));

            cursor.close();
        } else {
            Log.d("HomeFragment", "Brak danych w bazie.");
        }

        // Aktualizacja daty/godziny/tła
        updateDateTimeAndBackground();
    }

    private void updateDateTimeAndBackground() {
        Calendar now = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        String currentDate = dateFormat.format(now.getTime());
        String currentTime = timeFormat.format(now.getTime());

        binding.textView6.setText(currentDate);
        binding.textView7.setText(currentTime);

        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour >= 21 || hour < 6) {
            binding.frameLayout.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.gradient_night));
            binding.imageViewWeatherIcon.setImageResource(R.drawable.moon);
        } else {
            binding.frameLayout.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.gradient_day));
            binding.imageViewWeatherIcon.setImageResource(R.drawable.sun);
        }
    }

    private long convertIntervalToMillis(String intervalLabel) {
        // Dodajemy obsługę "5 sekund (only for devs)"
        switch (intervalLabel) {
            case "5 sekund (only for devs)":
                return 5 * 1000; // 5 sekund
            case "15 minut":
                return 15 * 60 * 1000;
            case "30 minut":
                return 30 * 60 * 1000;
            case "1 godzina":
                return 60 * 60 * 1000;
            case "2 godziny":
                return 120 * 60 * 1000;
            default:
                return 5 * 1000; // domyślnie 5 sekund, jeśli coś poszło nie tak
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        intervalHandler.removeCallbacks(intervalRunnable);
        Log.d("HomeFragment", "onDestroyView - zatrzymuję Handler intervalowy.");
    }
}
