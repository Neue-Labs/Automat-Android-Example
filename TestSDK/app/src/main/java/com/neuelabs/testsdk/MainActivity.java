package com.neuelabs.testsdk;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.neuelabs.neuelabsautomat.AutomatConnectionHandler;
import com.neuelabs.neuelabsautomat.AutomatConnectionManager;
import com.neuelabs.neuelabsautomat.AutomatDevice;
import com.neuelabs.neuelabsautomat.automatservices.automatmotion.AutomatMotionData;
import com.neuelabs.neuelabsautomat.automatservices.automatmotion.AutomatMotionHandler;
import com.neuelabs.neuelabsautomat.automatservices.oad.AutomatFirmwareVersionHandler;
import com.neuelabs.neuelabsautomat.devices.AutomatBaseboard;
import com.neuelabs.neuelabsautomat.devices.MemoryBoard;

import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private AutomatConnectionManager connectionManager;
    private HashMap<String, BluetoothDevice> connectingDevices;
    private HashMap<String, AutomatBaseboard> baseBoards;

    private static String DEVICE_NAME_AUTOMAT = "AUTOMA";
    private static String TAG = "Automat";
    private AutomatBaseboard mBaseboard;


    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });


        connectingDevices = new HashMap<String , BluetoothDevice>();
        baseBoards = new HashMap<String, AutomatBaseboard>();





        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect Automat devices.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);

                    }
                });
                builder.show();
            }
            else {
                Intent gattServiceIntent = new Intent(this, AutomatConnectionManager.class);
                bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
            }
        }
        else {
            Intent gattServiceIntent = new Intent(this, AutomatConnectionManager.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity", "coarse location permission granted");
                    Intent gattServiceIntent = new Intent(this, AutomatConnectionManager.class);
                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {



        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            connectionManager = ((AutomatConnectionManager.LocalBinder) service).getService();
            if (!connectionManager.initialize()) {
                finish();
            }


        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback(){
                @Override
                public void onLeScan (BluetoothDevice device, int rssi, byte[] scanRecord) {



                    if (device.getName() != null) {
                        //Log.d("Automat","Adv device with name: " + device.getName() + ", address: " + device.getAddress());
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < scanRecord.length; i++) {


                            sb.append(("0" + scanRecord[i]).toCharArray());
                        }
                        //printScanRecord(scanRecord);
                        String msg = "payload = ";
                        for (byte b : scanRecord)
                            msg += String.format("%02x ", b);
                        // Log.d("Automat","Scan record: " + msg);


//                        if (device.getUuids() != null) {
//                            Log.d("Automat","Parcel uuids");
//                            for (ParcelUuid uuid : device.getUuids()) {
//                                Log.d("Automat", "UUID: " + uuid.getUuid().toString());
//                            }
//
//                        }
//                        else
//                            Log.d("Automat","Parcel uuids was null");
                        //FFF0 -            02 01 06 03 02 f0 ff 0b 09
                        // 1803 1802 1804 - 02 01 06 07 02 03 18 02 18 04 18 0f 09

                        if (device.getName().contains(DEVICE_NAME_AUTOMAT) && !connectingDevices.containsKey(device.getAddress())) {


                            connectingDevices.put(device.getAddress(), device);
                            AutomatDevice automatDevice = connectionManager.getAutomatDevice(device.getAddress());
                            AutomatBaseboard board = (AutomatBaseboard) automatDevice;
                            mBaseboard = board;


                            board.setConnectionHandler(connHandler);
                            board.connect();

                        }
                    }
                    else {
                        //Log.d("Automat","Unknown named device with address: " + device.getAddress());
                    }

                }

            };

    private AutomatConnectionHandler connHandler = new AutomatConnectionHandler() {
        @Override
        public void didConnectAutomatDevice() {
           Log.d(TAG, "Did connect Automat device");
            if (mBaseboard.getConnectionState() == AutomatDevice.AutomatConnectionState.CONNECTED) {
                mBaseboard.setMotionSensorODR(AutomatBaseboard.MotionSensorODRValue.NLAMotionLowPowerMode13Hz);
                mBaseboard.registerAutomatMotionHandler(motionHandler);
            }


        }

        @Override
        public void didDisconnectAutomatDevice(int i) {

        }

        @Override
        public void bluetoothGattFail(int i, String s) {

        }

        @Override
        public void bluetoothStatusChange(int i, String s) {

        }
    };

    private AutomatMotionHandler motionHandler = new AutomatMotionHandler() {
        @Override
        public void didReceiveMotionData(AutomatMotionData automatMotionData) {
            Log.d(TAG,"Did receive motion data: " + automatMotionData.getAcceleration().getX());
        }
    };

}
