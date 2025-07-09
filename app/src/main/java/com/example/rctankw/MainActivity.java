package com.example.rctankw;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Spinner;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
public class MainActivity extends Activity implements BluetoothTerminal.BluetoothConnectionListener,
        BluetoothTerminal.BluetoothMessageListener{  // 改为继承普通的 Activity
    private BluetoothDeviceReader deviceReader;
    private BluetoothTerminal bluetoothTerminal;
    private Button sendBtn,connectBtn,disConnectBtn;
    private Spinner btSpinner;
    private Button P3Btn,P2Btn,P1Btn,SBtn,NBtn,LBtn,RBtn,CamBtn,TRBtn,TLBtn,TMBtn,batBtn;
    private BluetoothScanner bluetoothScanner;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connectBtn =findViewById(R.id.connectBtn);

        P2Btn = findViewById(R.id.P2Btn);
        P1Btn = findViewById(R.id.P1Btn);
        SBtn = findViewById(R.id.stopBtn);

        NBtn =findViewById(R.id.NBtn);
        LBtn = findViewById(R.id.LBtn);
        RBtn = findViewById(R.id.RBtn);
        TRBtn = findViewById(R.id.TRBtn);
        TLBtn = findViewById(R.id.TLBtn);
        batBtn = findViewById(R.id.batBtn);
        btSpinner = findViewById(R.id.btSpinner);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    },
                    BluetoothDeviceReader.REQUEST_BLUETOOTH_PERMISSIONS
            );
        } else {
            // 已经有权限，直接加载设备
            bluetoothTerminal = new BluetoothTerminal(this);
           bluetoothTerminal.setConnectionListener(this);
            bluetoothTerminal.setMessageListener(this);
           //deviceReader.populateConnectedDevicesToSpinner();
            // 设置设备列
        }
        bluetoothScanner = new BluetoothScanner(this, btSpinner);
        bluetoothScanner.checkPermissionsAndStartScan();
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在这里处理按钮点击事件
                // Toast.makeText(MainActivity.this, "Button Clicked", Toast.LENGTH_SHORT).show();
                /*String ip = ipInput.getText().toString();
                String port = portInput.getText().toString();
                client.connectToServer(ip,port);
                 */
                String deviceName = "ESP_M1A2";
                bluetoothTerminal.connectToDeviceByName(deviceName);
            }
        });
        batBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 在这里处理按钮点击事件
                // Toast.makeText(MainActivity.this, "Button Clicked", Toast.LENGTH_SHORT).show();
                String message = "BAT";
                bluetoothTerminal.sendMessage(message);
            }
        });
        P2Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MOTOR_P2";
                bluetoothTerminal.sendMessage(message);
            }
        });
        P1Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MOTOR_P1";
                bluetoothTerminal.sendMessage(message);
            }
        });
        SBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MOTOR_STOP";
                bluetoothTerminal.sendMessage(message);
            }
        });
        NBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MOTOR_N1";
                bluetoothTerminal.sendMessage(message);
            }
        });
        LBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MOTOR_L";
                bluetoothTerminal.sendMessage(message);
            }
        });
        RBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MOTOR_R";
                bluetoothTerminal.sendMessage(message);
            }
        });
        TLBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "MUSIC";
                bluetoothTerminal.sendMessage(message);
            }
        });
        TRBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = "CANNON";
                bluetoothTerminal.sendMessage(message);
            }
        });

       /* TextView textView = findViewById(R.id.);
        textView.setText("Hi, Pixel Watch 2!");*/
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
            Log.d("Bluetooth", "Connected");
            //ipInput.setText("BT Connected");
            // 更新UI状态
        });

    }

    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection failed: " + error, Toast.LENGTH_SHORT).show();
            Log.e("Bluetooth", "Connection failed: " + error);
            // 更新UI状态
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
            Log.d("Bluetooth", "Disconnected");
            // 更新UI状态
        });
    }

    // 实现 BluetoothMessageListener 接口方法
    @Override
    public void onMessageReceived(String message) {
        runOnUiThread(() -> {
            Log.d("Bluetooth", "Received: " + message);
            Toast.makeText(this,message, Toast.LENGTH_SHORT).show();
            // 显示接收到的消息
        });
    }

    @Override
    public void onMessageSent(String message) {
        runOnUiThread(() -> {
            Log.d("Bluetooth", "Sent: " + message);
            /* Toast.makeText(this, "Sent: " + message, Toast.LENGTH_SHORT).show();*/
            // 可选：显示发送成功的消息
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
            Log.e("Bluetooth", "Error: " + error);
            // 处理错误
        });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }
}
