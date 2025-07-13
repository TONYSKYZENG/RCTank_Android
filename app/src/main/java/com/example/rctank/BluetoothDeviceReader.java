package com.example.rctank;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothDeviceReader {

    public static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final long SCAN_PERIOD = 5000; // BLE扫描时间10秒

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private final List<String> deviceNames = new ArrayList<>();
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private boolean isScanning = false;
    private final Handler handler = new Handler();

    public BluetoothDeviceReader(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
        if (bluetoothAdapter != null) {
            this.bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    public void populateConnectedDevicesToSpinner(Spinner spinner) {
        // 检查权限
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions();
            deviceNames.add("需要蓝牙权限");
            setupSpinnerAdapter(spinner);
            return;
        }

        // 检查蓝牙是否可用和启用
        if (bluetoothAdapter == null) {
            deviceNames.add("设备不支持蓝牙");
            setupSpinnerAdapter(spinner);
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            deviceNames.add("蓝牙未启用");
            setupSpinnerAdapter(spinner);
            return;
        }

        // 清空设备列表
        deviceNames.clear();
        devices.clear();

        // 获取已配对设备(经典蓝牙)
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                addDeviceToList(device);
            }
        }

        // 开始BLE扫描
        scanLeDevices(spinner);
    }

    private void addDeviceToList(BluetoothDevice device) {
        if (!containsDevice(device)) {
            devices.add(device);
            String deviceName = device.getName();
            deviceNames.add((deviceName != null ? deviceName : "未知设备") +
                    (device.getType() == BluetoothDevice.DEVICE_TYPE_LE ? " (BLE)" : ""));
        }
    }

    private boolean containsDevice(BluetoothDevice newDevice) {
        for (BluetoothDevice device : devices) {
            if (device.getAddress().equals(newDevice.getAddress())) {
                return true;
            }
        }
        return false;
    }

    private void scanLeDevices(final Spinner spinner) {
        if (bleScanner == null || isScanning) {
            setupSpinnerAdapter(spinner);
            return;
        }

        handler.postDelayed(() -> {
            if (isScanning) {
                isScanning = false;
                if (bleScanner != null) {
                    bleScanner.stopScan(leScanCallback);
                }
                setupSpinnerAdapter(spinner);
            }
        }, SCAN_PERIOD);

        isScanning = true;
        bleScanner.startScan(leScanCallback);
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            addDeviceToList(device);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            isScanning = false;
            if (deviceNames.isEmpty()) {
                deviceNames.add("BLE扫描失败");
            }
        }
    };

    private boolean checkBluetoothPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (context instanceof Activity) {
            ActivityCompat.requestPermissions(
                    (Activity) context,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS
            );
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "蓝牙权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "需要蓝牙权限才能扫描设备", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupSpinnerAdapter(Spinner spinner) {
        if (deviceNames.isEmpty()) {
            deviceNames.add("没有找到设备");
        }

        spinnerAdapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_item,
                deviceNames
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
    }

    public void refreshDeviceList(Spinner spinner) {
        deviceNames.clear();
        devices.clear();
        populateConnectedDevicesToSpinner(spinner);
    }

    public BluetoothDevice getDeviceAtPosition(int position) {
        if (position >= 0 && position < devices.size()) {
            return devices.get(position);
        }
        return null;
    }
}