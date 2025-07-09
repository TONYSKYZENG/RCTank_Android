package com.example.rctankw;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    private final Activity activity;
    private final Spinner deviceSpinner;
    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> deviceList = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    public BluetoothScanner(Activity activity, Spinner deviceSpinner) {
        this.activity = activity;
        this.deviceSpinner = deviceSpinner;
        initializeBluetooth();
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(activity, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions();
                return;
            }
            activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            checkPermissionsAndStartScan();
        }
    }

    private void requestPermissions() {
        String[] requiredPermissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            requiredPermissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        ActivityCompat.requestPermissions(activity, requiredPermissions, REQUEST_PERMISSIONS);
    }

    private boolean hasAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void checkPermissionsAndStartScan() {
        if (!hasAllPermissions()) {
            requestPermissions();
            return;
        }

        startBluetoothScan();
    }

    private void startBluetoothScan() {
        // Clear previous results
        deviceList.clear();

        // Initialize spinner adapter
        spinnerAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, new ArrayList<String>());
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(spinnerAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        activity.registerReceiver(bluetoothReceiver, filter);

        // Start discovery
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        boolean started = bluetoothAdapter.startDiscovery();
        if (!started) {
            Toast.makeText(activity, "Failed to start Bluetooth scan", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "Scanning for Bluetooth devices...", Toast.LENGTH_SHORT).show();
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.ECLAIR)
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                String deviceName = device.getName();
                String deviceAddress = device.getAddress();

                if (deviceName != null && !deviceList.contains(device)) {
                    deviceList.add(device);
                    String displayText = deviceName + " (" + deviceAddress + ")";
                    spinnerAdapter.add(displayText);
                    spinnerAdapter.notifyDataSetChanged();
                    Log.d(TAG, "Found device: " + displayText);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(activity, "Scan completed. Found " + deviceList.size() + " devices", Toast.LENGTH_SHORT).show();
                activity.unregisterReceiver(bluetoothReceiver);
            }
        }
    };

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                checkPermissionsAndStartScan();
            } else {
                Toast.makeText(activity, "Permissions denied - Bluetooth scanning unavailable", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                checkPermissionsAndStartScan();
            } else {
                Toast.makeText(activity, "Bluetooth must be enabled to scan for devices", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void cleanup() {
        try {
            activity.unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver wasn't registered
        }

        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothAdapter.cancelDiscovery();
        }
    }

    public BluetoothDevice getSelectedDevice() {
        int position = deviceSpinner.getSelectedItemPosition();
        if (position >= 0 && position < deviceList.size()) {
            return deviceList.get(position);
        }
        return null;
    }
}