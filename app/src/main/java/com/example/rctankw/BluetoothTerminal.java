package com.example.rctankw;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler mainHandler;
    private BluetoothSocket socket;
    private BluetoothDevice targetDevice;
    private ConnectedThread connectedThread;
    private String targetDeviceName;

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
        this.targetDeviceName = deviceName;
        this.targetDevice = null;

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
            if (deviceName.equals(device.getName())) {
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

        new Thread(() -> {
            try {
                socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();

                connectedThread = new ConnectedThread(socket);
                connectedThread.start();

                notifyConnected();
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                closeSocket();
                notifyConnectionFailed(e.getMessage());
            }
        }).start();
    }

    public void disconnect() {
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        closeSocket();
        notifyDisconnected();
    }

    private void closeSocket() {
        try {
            if (socket != null) {
                socket.close();
                socket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }

    public void sendMessage(String message) {
        if (connectedThread != null) {
            connectedThread.write(message.getBytes());
            notifyMessageSent(message);
        } else {
            notifyError("Not connected to any device");
        }
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