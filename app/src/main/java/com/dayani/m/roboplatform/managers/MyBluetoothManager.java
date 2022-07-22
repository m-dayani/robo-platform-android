package com.dayani.m.roboplatform.managers;

//TODO: add availability checks.
// also change thread/runnables to handler/messages.

/*
    1. TODO: This class can't recognize some events (like blth enabled)
    automatically because we don't register br receivers -> can fix this.
    2. TODO: Use BufferedReader and ... instead of what currently is used.
 */

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.dayani.m.roboplatform.utils.AppGlobals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

public class MyBluetoothManager {

    private static final String TAG = MyBluetoothManager.class.getSimpleName();

    private static final String DEFAULT_UUID_STRING = "00001101-0000-1000-8000-00805f9b34fb";
    //or: "e8e10f95-1a70-4b27-9ccf-02010264e9c8" or: "e6a53cbc-b105-43db-9fc8-769b6b857e33"

    private static final String KEY_DEFAULT_UUID_STRING = AppGlobals.PACKAGE_BASE_NAME+
            ".KEY_DEFAULT_UUID_STRING";

    private static final int REQUEST_ENABLE_BT = 239;
    private static final String BLUETOOTH_SERVICE_NAME = AppGlobals.PACKAGE_BASE_NAME;
    private static final UUID BLUETOOTH_SERVICE_UUID = UUID.fromString(DEFAULT_UUID_STRING);
    //        new UUID(0xe6a53cbcb10543dbL, 0x9fc8769b6b857e33L);

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }

    //name of the server device: android powered robo platform
    private static final String DEFAULT_DEVICE_NAME = "RBN_MASTER";
    private static final String DEFAULT_CLIENT_NAME = "RBN_MASTER";

    private static Context appContext;
    private Handler handler = new Handler(); // handler that gets info from Bluetooth service
    private BluetoothSocket mSocket = null;

    private boolean isAvailable = false;

    private BluetoothDiscoverReceiver receiver;

    BluetoothHeadset bluetoothHeadset;
    BluetoothDevice mBluetoothDevice;

    // Get the default adapter
    BluetoothAdapter bluetoothAdapter;

    OnBluetoothInteractionListener mListener;

    private BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = (BluetoothHeadset) proxy;
                //hash()
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null;
            }
        }
    };

    public MyBluetoothManager(Context context, OnBluetoothInteractionListener listener) {
        appContext = context;
        mListener = listener;

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        receiver = new BluetoothDiscoverReceiver();
        updateAvailability();
    }

    public static int getRequestEnableBt() {
        return REQUEST_ENABLE_BT;
    }

    public static String getDefaultDeviceName() {
        return DEFAULT_DEVICE_NAME;
    }

    public static String getDefaultServiceUuid(Context context) {
        return MyStateManager.getStringPref(context, KEY_DEFAULT_UUID_STRING, DEFAULT_UUID_STRING);
    }

    public static void setDefaultServiceUuid(Context context, String uuid) {
        MyStateManager.setStringPref(context, KEY_DEFAULT_UUID_STRING, uuid);
    }


    private boolean isBluetoothSupported() {
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            return false;
        }
        return true;
    }

    private boolean isBluetoothEnabled() {
        if (bluetoothAdapter != null) {
            return bluetoothAdapter.isEnabled();
        }
        return false;
    }

    public boolean updateAvailability() {
        isAvailable = isBluetoothSupported() && isBluetoothEnabled();
        return isAvailable;
    }

    public void requestBluetoothEnabled() {
        if (!isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ActivityCompat.startActivityForResult((Activity) appContext,
                    enableBtIntent, REQUEST_ENABLE_BT, null);
        }
    }

    public void disableBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        }
    }

    //TODO: or listen for a receiver instead of this.
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requestCode == MyBluetoothManager.getRequestEnableBt()) {
                Log.d(TAG, "Bluetooth enable result...");
                //do your code
                switch (resultCode) {
                    case RESULT_OK:
                        updateAvailability();
                        mListener.onBluetoothEnabled();
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public void requestDiscoverabilityEnabled() {
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        appContext.startActivity(discoverableIntent);
    }

    private void queryPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    }

    private BluetoothDevice getPairedDeviceByName(String deviceName) {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(deviceName)) {
                    return device;
                }
            }
        }
        return null;
    }

    private void registerDiscoverReceiver() {
        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        appContext.registerReceiver(receiver, filter);
    }

    private void unregisterDiscoverReceiver() {
        // Don't forget to unregister the ACTION_FOUND receiver.
        appContext.unregisterReceiver(receiver);
    }

    public void startServer() {
        //Is bluetooth supported and enabled?
        if (!updateAvailability()) {
            Log.e(TAG,"Bluetooth is not supported or enabled.");
            return;
        }
        //check if default device is paired
        mBluetoothDevice = getPairedDeviceByName(DEFAULT_CLIENT_NAME);
        //for unpaired devices this server must be visible to client
        if (mBluetoothDevice == null) {
            requestDiscoverabilityEnabled();
        }
        AcceptThread bili = new AcceptThread();
        bili.start();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            //Log.d(TAG,"AcceptThread started successfully...");
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(BLUETOOTH_SERVICE_NAME,
                        BLUETOOTH_SERVICE_UUID);

            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "Running Bluetooth AcceptThread...");
            //BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    //TODO: Still can't move past this point
                    mSocket = mmServerSocket.accept();
                    mListener.onClientConnection();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (mSocket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    //manageMyConnectedSocket(socket);
                    try {
                        mmServerSocket.close();

                        //my contribution
                        //mBluetoothDevice = mSocket.getRemoteDevice();
                        ConnectedThread bili = new ConnectedThread(mSocket);
                        bili.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(BLUETOOTH_SERVICE_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.d(TAG, "Running Bluetooth ConnectThread...");
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();

                //my Contribution
                ConnectedThread bili = new ConnectedThread(mmSocket);
                bili.start();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d(TAG, "Running Bluetooth ConnectedThread (Input) ...");
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            //first, test the connection
            try {
                numBytes = mmInStream.read(mmBuffer);
                String msg = new String(mmBuffer, 0, numBytes, "US-ASCII");
                this.write(msg.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.

                    numBytes = mmInStream.read(mmBuffer);
                    //String msg = Arrays.toString(mmBuffer);
                    if (numBytes >= 2) {
                        //somehow java desktop program sends msg.length+2 chars!
                        numBytes -= 2;
                    }
                    String msg = new String(mmBuffer, 0, numBytes, "US-ASCII");
                    Log.v(TAG, "Bluetooth out: "+numBytes+": "+msg);

                    mListener.onMessageReceived(msg);
                    // Send the obtained bytes to the UI activity.
                    /*Message readMsg = handler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();*/

                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                String msg = new String(bytes, 0, bytes.length, "US-ASCII");
                Log.i(TAG, "writing buffer to Outstream: "+msg);
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                /*Message writtenMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();*/
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    public interface OnBluetoothInteractionListener {
        void onBluetoothEnabled();
        void onClientConnection();
        void onMessageReceived(String msg);
    }


    private void connectHeadsetProxy() {
        // Establish connection to the proxy.
        bluetoothAdapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET);
    }

    // ... call functions on bluetoothHeadset

    private void disconnectHeadsetProxy() {
        // Close proxy connection after use.
        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private class BluetoothDiscoverReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    };

}