package com.example.rctank;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothTerminal {
    private static final String TAG = "BluetoothTerminal";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // 常用的BLE服务和特征UUID
// UUID定义 (使用ESP-IDF BLE SPP默认UUID)
    private static final UUID BLE_SPP_SERVICE_UUID = UUID.fromString("0000abf0-0000-1000-8000-00805f9b34fb");
    private static final UUID BLE_SPP_CHARACTERISTIC_UUID = UUID.fromString("0000abf1-0000-1000-8000-00805f9b34fb");



    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler mainHandler;
    private BluetoothSocket classicSocket;
    private BluetoothGatt bleGatt;
    private BluetoothGattCharacteristic bleCharacteristic;
    private BluetoothDevice targetDevice;
    private ConnectedThread connectedThread;
    private String targetDeviceName;
    private boolean isBleDevice = false;

    // 定义回调接口
    public interface BluetoothConnectionListener {
        void onConnected();
        void onConnectionFailed(String error);
        void onDisconnected();
    }

    public interface BluetoothMessageListener {
        void onMessageReceived(String message);
        void onMessageSent(String message);
        void onError(String error);
    }

    private BluetoothConnectionListener connectionListener;
    private BluetoothMessageListener messageListener;

    // 广播接收器
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                if (deviceName != null && deviceName.equals(targetDeviceName)) {
                    bluetoothAdapter.cancelDiscovery();
                    targetDevice = device;
                    connectToDevice();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (targetDevice == null) {
                    notifyConnectionFailed("Device not found");
                }
            }
        }
    };

    // BLE GATT回调
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                notifyDisconnected();
                closeBleConnection();
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 查找BLE SPP服务
                BluetoothGattService sppService = gatt.getService(BLE_SPP_SERVICE_UUID);
                if (sppService != null) {
                    // 查找读写特征
                    bleCharacteristic = sppService.getCharacteristic(BLE_SPP_CHARACTERISTIC_UUID);
                    if (bleCharacteristic != null) {
                        // 启用通知
                        gatt.setCharacteristicNotification(bleCharacteristic, true);

                        // 获取并设置客户端特征配置描述符
                        BluetoothGattDescriptor descriptor = bleCharacteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        } else {
                            Log.w(TAG, "Client Characteristic Configuration descriptor not found");
                            // 即使没有描述符也继续连接，某些设备可能不需要
                            notifyConnected();
                        }
                    } else {
                        notifyConnectionFailed("BLE SPP characteristic not found");
                    }
                } else {
                    notifyConnectionFailed("BLE SPP service not found");
                }
            } else {
                notifyConnectionFailed("Service discovery failed: " + status);
            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                String message = new String(data);
                notifyMessageReceived(message);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String message = new String(characteristic.getValue());
                notifyMessageSent(message);
            } else {
                notifyError("Failed to send BLE message");
            }
        }
    };

    public BluetoothTerminal(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setConnectionListener(BluetoothConnectionListener listener) {
        this.connectionListener = listener;
    }

    public void setMessageListener(BluetoothMessageListener listener) {
        this.messageListener = listener;
    }

    public void connectToDeviceByName(String deviceName) {
        // 移除可能的"(BLE)"后缀得到原始设备名
        String originalDeviceName = deviceName.replace(" (BLE)", "");
        this.targetDeviceName = originalDeviceName;  // 存储原始设备名
        this.targetDevice = null;
        this.isBleDevice = deviceName.contains("(BLE)"); // 根据传入的名称判断是否为BLE设备

        if (bluetoothAdapter == null) {
            notifyConnectionFailed("Bluetooth not supported");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            notifyConnectionFailed("Bluetooth is disabled");
            return;
        }

        // 检查已配对设备
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            String name = device.getName();
            if (name != null && name.equals(originalDeviceName)) {
                targetDevice = device;
                break;
            }
        }

        if (targetDevice != null) {
            connectToDevice();
        } else {
            registerReceiver();
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            boolean discoveryStarted = bluetoothAdapter.startDiscovery();
            if (!discoveryStarted) {
                notifyConnectionFailed("Failed to start discovery");
            }
        }
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);
    }

    private void unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
    }

    private void connectToDevice() {
        unregisterReceiver();

        if (targetDevice == null) {
            notifyConnectionFailed("Target device is null");
            return;
        }

        if (isBleDevice) {
            connectToBleDevice();
        } else {
            connectToClassicDevice();
        }
    }

    private void connectToClassicDevice() {
        new Thread(() -> {
            try {
                classicSocket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                classicSocket.connect();

                connectedThread = new ConnectedThread(classicSocket);
                connectedThread.start();

                notifyConnected();
            } catch (IOException e) {
                Log.e(TAG, "Classic connection failed", e);
                closeClassicConnection();
                notifyConnectionFailed(e.getMessage());
            }
        }).start();
    }

    private void connectToBleDevice() {
        mainHandler.post(() -> {
            bleGatt = targetDevice.connectGatt(context, false, gattCallback);
        });
    }

    public void disconnect() {
        if (isBleDevice) {
            closeBleConnection();
        } else {
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }
            closeClassicConnection();
        }
        notifyDisconnected();
    }

    private void closeClassicConnection() {
        try {
            if (classicSocket != null) {
                classicSocket.close();
                classicSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not close the classic socket", e);
        }
    }

    private void closeBleConnection() {
        if (bleGatt != null) {
            bleGatt.disconnect();
            bleGatt.close();
            bleGatt = null;
            bleCharacteristic = null;
        }
    }

    public void sendMessage(String message) {
        if (isBleDevice) {
            sendBleMessage(message);
        } else {
            sendClassicMessage(message);
        }
    }

    private void sendClassicMessage(String message) {
        if (connectedThread != null) {
            connectedThread.write(message.getBytes());
            notifyMessageSent(message);
        } else {
            notifyError("Not connected to any classic device");
        }
    }

    private void sendBleMessage(String message) {
        if (bleGatt == null || bleCharacteristic == null) {
            notifyError("Not connected to any BLE device");
            return;
        }

        mainHandler.post(() -> {
            bleCharacteristic.setValue(message.getBytes());
            bleGatt.writeCharacteristic(bleCharacteristic);
        });
    }

    private void notifyConnected() {
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onConnected();
            }
        });
    }

    private void notifyConnectionFailed(final String error) {
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onConnectionFailed(error);
            }
        });
    }

    private void notifyDisconnected() {
        mainHandler.post(() -> {
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
        });
    }

    private void notifyMessageReceived(final String message) {
        mainHandler.post(() -> {
            if (messageListener != null) {
                messageListener.onMessageReceived(message);
            }
        });
    }

    private void notifyMessageSent(final String message) {
        mainHandler.post(() -> {
            if (messageListener != null) {
                messageListener.onMessageSent(message);
            }
        });
    }

    private void notifyError(final String error) {
        mainHandler.post(() -> {
            if (messageListener != null) {
                messageListener.onError(error);
            }
        });
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error getting streams", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String receivedMessage = new String(buffer, 0, bytes);
                    notifyMessageReceived(receivedMessage);
                } catch (IOException e) {
                    Log.e(TAG, "Connection lost", e);
                    disconnect();
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream", e);
                notifyError("Failed to send message");
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}