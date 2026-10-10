package com.example.bluetoothcar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinkedList<String> logEntries = new LinkedList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private TextView tvStatus;
    private TextView tvSpeedLabel;
    private MaterialButton btnConnect;
    private SwitchMaterial switchCrashPrevention;
    private MaterialCardView cardCommunicationLog;
    private TextView tvLogConsole;
    private ScrollView svLogContainer;
    private boolean isConnected = false;
    private String connectedDeviceName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBluetooth();
        setupControlListeners();
    }

    private void initViews() {
        View headerTitleLayout = findViewById(R.id.header_title_layout);
        tvStatus = findViewById(R.id.tv_status);
        tvSpeedLabel = findViewById(R.id.tv_speed_label);
        btnConnect = findViewById(R.id.btn_connect);
        SeekBar sbSpeed = findViewById(R.id.sb_speed);
        switchCrashPrevention = findViewById(R.id.switch_crash_prevention);

        cardCommunicationLog = findViewById(R.id.card_communication_log);
        tvLogConsole = findViewById(R.id.tv_log_console);
        svLogContainer = findViewById(R.id.sv_log_container);
        MaterialButton btnClearLog = findViewById(R.id.btn_clear_log);

        // Crash Prevention default ON
        switchCrashPrevention.setChecked(true);

        // Long press header title to toggle live communication log
        headerTitleLayout.setOnLongClickListener(v -> {
            toggleCommunicationLog();
            return true;
        });

        btnClearLog.setOnClickListener(v -> {
            logEntries.clear();
            tvLogConsole.setText("-- Communication Log Cleared --\n");
        });

        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnectBluetooth();
            } else {
                checkPermissionsAndConnect();
            }
        });

        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSpeedLabel.setText(getString(R.string.speed_label, progress));
                if (fromUser && isConnected) {
                    // Exact format from block logic: "E" + thumbPosition + "n"
                    sendCommand("E" + progress + "n");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        // Switch1 logic: HC-SR04_ON / HC-SR04_OFF
        switchCrashPrevention.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                sendCommand("HC-SR04_ON");
            } else {
                sendCommand("HC-SR04_OFF");
            }
        });
    }

    private void toggleCommunicationLog() {
        if (cardCommunicationLog.getVisibility() == View.VISIBLE) {
            cardCommunicationLog.setVisibility(View.GONE);
            Toast.makeText(this, R.string.log_closed, Toast.LENGTH_SHORT).show();
        } else {
            cardCommunicationLog.setVisibility(View.VISIBLE);
            Toast.makeText(this, R.string.log_opened, Toast.LENGTH_SHORT).show();
            // Scroll to bottom when shown
            svLogContainer.post(() -> svLogContainer.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    private void logMessage(String message) {
        String timestamp = timeFormat.format(new Date());
        String entry = "[" + timestamp + "] " + message;

        mainHandler.post(() -> {
            logEntries.add(entry);
            if (logEntries.size() > 100) {
                logEntries.removeFirst();
            }

            StringBuilder sb = new StringBuilder("-- Communication Log --\n");
            for (String log : logEntries) {
                sb.append(log).append("\n");
            }
            tvLogConsole.setText(sb.toString());
            svLogContainer.post(() -> svLogContainer.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    private void initBluetooth() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
            btnConnect.setEnabled(false);
            logMessage("System: Bluetooth hardware not available");
        }
    }

    private void checkPermissionsAndConnect() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // On Android 12+, Nearby Devices permissions (BLUETOOTH_CONNECT & BLUETOOTH_SCAN) are used without Location
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            // On Android 11 and older, Location permission is required for Bluetooth device discovery
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            showDeviceSelectionDialog();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                showDeviceSelectionDialog();
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_SHORT).show();
                logMessage("System: Permission denied by user");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void showDeviceSelectionDialog() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.bluetooth_disabled, Toast.LENGTH_SHORT).show();
            logMessage("System: Bluetooth is disabled");
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(this, R.string.no_paired_devices, Toast.LENGTH_SHORT).show();
            logMessage("System: No paired devices found");
            return;
        }

        List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        String[] deviceNames = new String[deviceList.size()];
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice device = deviceList.get(i);
            String name = device.getName() != null ? device.getName() : "Unknown Device";
            deviceNames[i] = name + "\n" + device.getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_device)
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothDevice selectedDevice = deviceList.get(which);
                    connectToDevice(selectedDevice);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        connectedDeviceName = device.getName() != null ? device.getName() : device.getAddress();
        tvStatus.setText(R.string.status_connecting);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_orange));
        logMessage("System: Connecting to " + connectedDeviceName + " (" + device.getAddress() + ")...");

        new Thread(() -> {
            try {
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException ignored) {
                    }
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();

                mainHandler.post(() -> {
                    isConnected = true;
                    tvStatus.setText(getString(R.string.status_connected, connectedDeviceName));
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                    btnConnect.setText(R.string.disconnect);
                    Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show();
                    logMessage("System: Connected to " + connectedDeviceName);

                    // Send initial state for crash prevention if default ON
                    if (switchCrashPrevention.isChecked()) {
                        sendCommand("HC-SR04_ON");
                    }
                });

            } catch (IOException e) {
                try {
                    bluetoothSocket.close();
                } catch (IOException ignored) {
                }

                mainHandler.post(() -> {
                    isConnected = false;
                    tvStatus.setText(R.string.status_disconnected);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                    btnConnect.setText(R.string.connect);
                    Toast.makeText(this, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    logMessage("System: Connection failed - " + e.getMessage());
                });
            }
        }).start();
    }

    private void disconnectBluetooth() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException ignored) {
            }
            bluetoothSocket = null;
        }
        isConnected = false;
        tvStatus.setText(R.string.status_disconnected);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red));
        btnConnect.setText(R.string.connect);
        Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        logMessage("System: Disconnected");
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupControlListeners() {
        // TouchDown -> command, TouchUp -> "stop"
        setupDirectionButton(findViewById(R.id.btn_forward), "forward");
        setupDirectionButton(findViewById(R.id.btn_backward), "backward");
        setupDirectionButton(findViewById(R.id.btn_left), "left");
        setupDirectionButton(findViewById(R.id.btn_right), "right");
        setupDirectionButton(findViewById(R.id.btn_up_left), "upLeft");
        setupDirectionButton(findViewById(R.id.btn_up_right), "upRight");
        setupDirectionButton(findViewById(R.id.btn_down_left), "downLeft");
        setupDirectionButton(findViewById(R.id.btn_down_right), "downRight");
        setupDirectionButton(findViewById(R.id.btn_center), "center");
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDirectionButton(ImageButton button, String touchDownCommand) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    sendCommand(touchDownCommand);
                    v.setPressed(true);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sendCommand("stop");
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
    }

    private void sendCommand(String cmd) {
        logMessage("TX: \"" + cmd + "\"");

        if (!isConnected || outputStream == null) {
            return;
        }

        new Thread(() -> {
            try {
                outputStream.write(cmd.getBytes());
            } catch (IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "Error sending data", Toast.LENGTH_SHORT).show();
                    logMessage("System: Error writing to socket");
                    disconnectBluetooth();
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectBluetooth();
    }
}
