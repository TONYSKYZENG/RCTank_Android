package com.example.rctankw;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BluetoothDeviceReader {

    public static final int REQUEST_BLUETOOTH_PERMISSIONS =1 ;
   // private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final List<String> deviceNames = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;

    public BluetoothDeviceReader(Context context) {
        this.context = context;
        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        this.bluetoothAdapter = bluetoothManager != null ? bluetoothManager.getAdapter() : null;
    }

    public void populateConnectedDevicesToSpinner(Spinner spinner) {
        // 检查权限
        if (!checkBluetoothPermissions()) {
            requestBluetoothPermissions();
            deviceNames.add("需要蓝牙权限");
            //setupSpinnerAdapter(spinner);
            return;
        }

        // 检查蓝牙是否可用和启用
        if (bluetoothAdapter == null) {
            deviceNames.add("设备不支持蓝牙");
          //  setupSpinnerAdapter(spinner);
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            deviceNames.add("蓝牙未启用");
           // setupSpinnerAdapter(spinner);
            return;
        }

        // 获取已配对设备
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.isEmpty()) {
            deviceNames.add("没有已配对的设备");
        } else {
            deviceNames.clear();
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                deviceNames.add(deviceName != null ? deviceName : "未知设备");
            }
        }

       setupSpinnerAdapter(spinner);
    }

    private boolean checkBluetoothPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (context instanceof Activity) {
            ActivityCompat.requestPermissions(
                    (Activity) context,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
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
        populateConnectedDevicesToSpinner(spinner);
    }
}