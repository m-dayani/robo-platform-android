package com.dayani.m.roboplatform.managers;

//TODO: add availability checks.
// also change thread/runnable to handler/message.

/*
    1. TODO: This class can't recognize some events (like blth enabled)
    automatically because we don't register br receivers -> fix this.
 */

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;
import static android.content.Context.BLUETOOTH_SERVICE;

import static com.dayani.m.roboplatform.drivers.MyDrvWireless.DEFAULT_TEST_RESPONSE;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.dayani.m.roboplatform.drivers.MyDrvWireless;
import com.dayani.m.roboplatform.requirements.BluetoothReqFragment;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.HandleEnableSettingsRequirement;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.ManagerRequirementBroadcastReceiver;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class MyBluetoothManager extends MyBaseManager implements HandleEnableSettingsRequirement,
        ActivityRequirements.ConnectivityTest {

    /* ===================================== Variables ========================================== */

    private static final String TAG = MyBluetoothManager.class.getSimpleName();


    private static final String KEY_DEFAULT_UUID_STRING = AppGlobals.PACKAGE_BASE_NAME +
            ".KEY_DEFAULT_UUID_STRING";

    private static final String DEFAULT_UUID_STRING = "00001101-0000-1000-8000-00805f9b34fb";
    //or: "e8e10f95-1a70-4b27-9ccf-02010264e9c8" or: "e6a53cbc-b105-43db-9fc8-769b6b857e33"

    private static final String BLUETOOTH_SERVICE_NAME = AppGlobals.APPLICATION_NAME;
    private static final UUID BLUETOOTH_SERVICE_UUID = UUID.fromString(DEFAULT_UUID_STRING);

    private static final int REQUEST_ENABLE_BT = 239;

    private static final int SELECT_DEVICE_REQUEST_CODE = 0;
    private static final int ANDROID_COMPANION_DEVICE_VERSION = Build.VERSION_CODES.O;

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        int MESSAGE_READ = 0;
        int MESSAGE_WRITE = 1;
        int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed)
    }

    private static final int BT_SENSOR_ID = 0;


    private boolean mbBluetoothSettingsEnabled = false;
    private boolean mbIsBtAvailable = false;
    private boolean mbPassedConnTest = false;

    private boolean mbIsServerMode = false;

    private final BluetoothAdapter mBluetoothAdapter;

    private BluetoothDiscoverReceiver mDiscoverReceiver = null;
    private ManagerRequirementBroadcastReceiver mSettingsChangeReceiver = null;

    private BluetoothHeadset bluetoothHeadset;
    private BluetoothDevice mBluetoothDevice;

    private ConnectThread mConnectThread;
    //private AcceptThread mServerThread;
    private BluetoothServerSocket mServerSocket;

    private BluetoothSocket mSocket = null;

    private MyWifiManager.InputTask mInputTask;
    private PrintWriter output;
    private BufferedReader input;

    private Map<String, BluetoothDevice> mDeviceMap = new HashMap<>();


    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
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

    /* ==================================== Construction ======================================== */

    public MyBluetoothManager(Context context) {

        super(context);

        BluetoothManager mBtManager = (BluetoothManager) context.
                getApplicationContext().getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBtManager.getAdapter();
        //mDiscoverReceiver = new BluetoothDiscoverReceiver();
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    @Override
    protected boolean resolveSupport(Context context) {

        BluetoothManager btManager = (BluetoothManager) context.
                getApplicationContext().getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        return btAdapter != null;
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    @Override
    protected List<ActivityRequirements.Requirement> getRequirements() {

        // A wireless connection req means adapter is enabled and a comm socket can be opened
        return Collections.singletonList(ActivityRequirements.Requirement.WIRELESS_CONNECTION);
    }

    @Override
    public boolean passedAllRequirements() {

        // requirements are passed when:
        // [optional]: required permissions are met
        // bluetooth setting is enabled
        // bt service is available -> which means:
        //      can pair and connect devices and acquire a comm socket +
        //      pass the wireless comm test (the communicating app is robo-platform)
        return isSettingsEnabled() && mbIsBtAvailable;
    }

    @Override
    protected void updateRequirementsState(Context context) {

        List<ActivityRequirements.Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to update");
            return;
        }

        // permissions
        if (requirements.contains(ActivityRequirements.Requirement.PERMISSIONS)) {
            updatePermissionsState(context);
        }

        // bluetooth settings is enabled
        if (requirements.contains(ActivityRequirements.Requirement.WIRELESS_CONNECTION)) {
            updateSettingsEnabled();
            mbIsBtAvailable = isSettingsEnabled() && mSocket != null && mbPassedConnTest;
        }
    }

    @Override
    protected void resolveRequirements(Context context) {

        List<ActivityRequirements.Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to resolve");
            return;
        }

        // permissions
        if (requirements.contains(ActivityRequirements.Requirement.PERMISSIONS)) {
            if (!hasAllPermissions()) {
                resolvePermissions();
                return;
            }
        }

        // bluetooth setting enabled
        if (requirements.contains(ActivityRequirements.Requirement.WIRELESS_CONNECTION)) {
            if (mRequirementRequestListener != null) {
                // let the requirement fragment take care of everything
                mRequirementRequestListener.requestResolution(BluetoothReqFragment.newInstance());
            }
        }
    }

    // todo: override onActivityResult??

    @Override
    public List<String> getPermissions() {
        //return Collections.singletonList(Manifest.permission.WRITE_SETTINGS);
        // no permissions is required
        return new ArrayList<>();
    }

    @Override
    public void onSettingsChanged(Context context, Intent intent) {

        if (intent.getAction().matches(BluetoothAdapter.ACTION_STATE_CHANGED)) {

            //int currState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);

            Log.i(TAG, "Bluetooth settings is changed");

            updateSettingsEnabled();

            if (mRequirementResponseListener != null) {
                mRequirementResponseListener.onAvailabilityStateChanged(this);
            }
        }
    }

    @Override
    public boolean isSettingsEnabled() {
        return mbBluetoothSettingsEnabled;
    }

    @Override
    public void updateSettingsEnabled() {
        mbBluetoothSettingsEnabled = isBluetoothEnabled();
    }

    @Override
    public void enableSettingsRequirement(Context context) {

        if (!this.isSettingsEnabled()) {
            // prompt the user to enable Bluetooth
            requestBluetoothEnabled(context);
        }
    }

    private boolean isBluetoothEnabled() {

        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.isEnabled();
        }
        return false;
    }

    //TODO: or listen for a receiver instead of this.
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (requestCode == MyBluetoothManager.getRequestEnableBt()) {
                Log.d(TAG, "Bluetooth enable result...");
                //do your code
                switch (resultCode) {
                    case RESULT_OK:
                        //updateAvailability();
                        //mListener.onBluetoothEnabled();
                        if (mRequirementResponseListener != null) {
                            mRequirementResponseListener.onAvailabilityStateChanged(this);
                        }
                        break;
                    case RESULT_CANCELED:
                        break;
                    default:
                        break;
                }
            }
        }
        if (requestCode == SELECT_DEVICE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                mBluetoothDevice = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);

                if (mBluetoothDevice != null) {
                    mBluetoothDevice.createBond();
                    // ... Continue interacting with the paired device.
                }
            }
        } else {
            //super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /* -------------------------------- Lifecycle Management ------------------------------------ */

    @Override
    public void execute(Context context, LifeCycleState state) {

        if (state == LifeCycleState.ACT_CREATED) {

            super.execute(context, state); // register/unregister br receivers
        }
        else if (state == LifeCycleState.ACT_DESTROYED) {

            this.close();
            super.execute(context, state);
        }
        else {
            super.execute(context, state);
        }
    }

    @Override
    public void registerBrReceivers(Context context, LifeCycleState state) {

        if (state == LifeCycleState.RESUMED) {

            registerDiscoverReceiver(context);
        }
        else if (state == LifeCycleState.PAUSED) {

            unregisterDiscoverReceiver(context);
        }
        else if (state == LifeCycleState.ACT_CREATED) {

            registerStateChangeReceiver(context);
        }
        else if (state == LifeCycleState.ACT_DESTROYED) {

            unregisterStateChangeReceiver(context);
        }
    }

    // different br receivers:
    // bt state change, discover,
    private void registerDiscoverReceiver(Context context) {

        if (mDiscoverReceiver == null) {
            mDiscoverReceiver = new BluetoothDiscoverReceiver();
        }
        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mDiscoverReceiver, filter);
        Log.d(TAG, "discovery receiver is registered");
    }

    private void unregisterDiscoverReceiver(Context context) {

        if (mDiscoverReceiver != null) {
            // Don't forget to unregister the ACTION_FOUND receiver.
            context.unregisterReceiver(mDiscoverReceiver);
            mDiscoverReceiver = null;
            Log.d(TAG, "discovery receiver is unregistered");
        }
    }

    private void registerStateChangeReceiver(Context context) {

        if (mSettingsChangeReceiver == null) {
            mSettingsChangeReceiver = new ManagerRequirementBroadcastReceiver(this);
        }
        IntentFilter intent = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        context.registerReceiver(mSettingsChangeReceiver, intent);
        Log.d(TAG, "state change receiver is registered");
    }

    private void unregisterStateChangeReceiver(Context context) {

        if (mSettingsChangeReceiver != null) {
            context.unregisterReceiver(mSettingsChangeReceiver);
            mSettingsChangeReceiver = null;
            Log.d(TAG, "state change receiver is unregistered");
        }
    }

    private void registerDevConnectedReceiver(Context context) {

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(mDevConnReceiver, filter);
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    protected String getResourceId(MyResourceIdentifier resId) {

        if (resId.getId() == BT_SENSOR_ID) {
            return "bt-sensor";
        } else {
            return "unknown";
        }
    }

    @Override
    protected List<Pair<String, MyMessages.MsgConfig>> getStorageConfigMessages(MySensorInfo sensor) {
        return new ArrayList<>();
    }

    /* ====================================== Bluetooth ========================================= */

    /* ----------------------------------- Getters/Setters -------------------------------------- */

    @Override
    public List<MySensorGroup> getSensorGroups(Context context) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        List<MySensorGroup> sensorGroups = new ArrayList<>();
        List<MySensorInfo> sensors = new ArrayList<>();

        // add sensors:
        // we can have different sensors for different purposes
        // (Wifi-direct, Hotspot, Server, Client, ...), but this is enough for now
        sensors.add(new MySensorInfo(BT_SENSOR_ID, "Bluetooth 0"));

        sensorGroups.add(new MySensorGroup(MySensorGroup.getNextGlobalId(),
                MySensorGroup.SensorType.TYPE_WIRELESS_NETWORK, "Bluetooth Connectivity", sensors));

        return sensorGroups;
    }

    public static int getRequestEnableBt() {
        return REQUEST_ENABLE_BT;
    }

    public static String getDefaultDeviceName() {

        String name = "?";

        try {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            name = mBluetoothAdapter.getName();
            if (name == null) {
                Log.d(TAG, "Name is null!");
                //name = mBluetoothAdapter.getAddress();
            }
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }

        return name;
    }

    public static String getDefaultServiceName() {
        return BLUETOOTH_SERVICE_NAME;
    }

    public boolean isServerMode() { return mbIsServerMode; }
    public void setServerMode(boolean state) { mbIsServerMode = state; }

    /* --------------------------------- Enable & Query Devices --------------------------------- */

//    public void disableBluetooth(Context context) {
//        if (mBluetoothAdapter.isEnabled()) {
//            mBluetoothAdapter.disable();
//        }
//    }

    public void requestBluetoothEnabled(Context context) {

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        ActivityCompat.startActivityForResult((Activity) context,
                enableBtIntent, REQUEST_ENABLE_BT, null);
    }

    @RequiresApi(api = ANDROID_COMPANION_DEVICE_VERSION)
    public void findRemoteDevice(Context context) {

        CompanionDeviceManager deviceManager =
                (CompanionDeviceManager) context.getSystemService(Context.COMPANION_DEVICE_SERVICE);

        // To skip filtering based on name and supported feature flags,
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        BluetoothDeviceFilter deviceFilter =
                new BluetoothDeviceFilter.Builder()
                        //.setNamePattern(Pattern.compile("My device"))
                        .addServiceUuid(new ParcelUuid(BLUETOOTH_SERVICE_UUID), null)
                        .build();

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        AssociationRequest pairingRequest = new AssociationRequest.Builder()
                .addDeviceFilter(deviceFilter)
                .setSingleDevice(true)
                .build();

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        deviceManager.associate(pairingRequest,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        try {
                            ((Activity) context).startIntentSenderForResult(chooserLauncher,
                                    SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            // failed to send the intent
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        // handle failure to find the companion device
                    }
                }, null);
    }

    public void requestDiscoverabilityEnabled(Context context) {

        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            context.startActivity(discoverableIntent);
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    public String[] queryPairedDevices() {


        Set<BluetoothDevice> pairedDevices;
        try {
            pairedDevices = mBluetoothAdapter.getBondedDevices();
        }
        catch (SecurityException e) {
            e.printStackTrace();
            return new String[0];
        }

        StringBuilder sb = new StringBuilder();
        Map<String, BluetoothDevice> devNames = new HashMap<>();

        if (pairedDevices.size() > 0) {

            try {
                // There are paired devices. Get the name and address of each paired device.
                for (BluetoothDevice device : pairedDevices) {

                    String deviceName = device.getName();

                    //String deviceHardwareAddress = device.getAddress(); // MAC address
                    devNames.put(deviceName, device);
                    sb.append(deviceName).append(", ");
                }
            }
            catch (SecurityException e) {
                e.printStackTrace();
            }
        }

        Log.v(TAG, "Paired devs: "+ sb);
        mDeviceMap = devNames;
        if (devNames.size() > 0) {
            return devNames.keySet().toArray(new String[0]);
        }
        return new String[0];
    }

    public void setDefaultDevice(String deviceName) {
        mBluetoothDevice = mDeviceMap.get(deviceName);
    }

    private BluetoothDevice getPairedDeviceByName(String deviceName) {

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

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

    public void startDiscovery() {

        if (mBluetoothAdapter != null) {
            try {
                mBluetoothAdapter.startDiscovery();
                Log.i(TAG, "Discovery is started");
            }
            catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    /* ------------------------------ Connectivity (Open/Close) --------------------------------- */

    public void startServer() {

        // Is bluetooth supported and enabled?
        if (!isSettingsEnabled() || mSocket != null) {
            Log.e(TAG,"Bluetooth is not supported or enabled.");
            return;
        }

        doInBackground(new AcceptThread(this));
    }

    public void stopServer() {

        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mServerSocket = null;
            }
        }
    }

    public void connectToRemote() {

        // Is bluetooth supported and enabled?
        if (!isSettingsEnabled() || mSocket != null) {
            Log.e(TAG,"Bluetooth is not supported or enabled.");
            return;
        }

        if (mBluetoothDevice != null) {
            doInBackground(new ConnectThread(this, mBluetoothDevice));
        }
    }

    public void close() {

        if (mInputTask != null) {
            mInputTask.stop();
            mInputTask = null;
        }
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mServerSocket = null;
        }
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = null;
        }
        if (output != null) {
            output.close();
            output = null;
        }
        if (input != null) {
            //input.close();
            input = null;
        }

        mbIsBtAvailable = false;
        updateSettingsEnabled();
    }

    @Override
    public void handleTestSynchronous(MyMessages.MyMessage msg) {

    }

    @Override
    public void handleTestAsynchronous(MyMessages.MyMessage msg) {

        if (!isSettingsEnabled() || mSocket == null) {
            mbPassedConnTest = false;
            return;
        }

        if (msg == null) {
            // init. test & send the test command
            doInBackground(new MyWifiManager.OutputTask(output, MyDrvWireless.getTestRequest()));
            // get the response elsewhere and check
        }
        else if (msg instanceof MyMessages.MsgWireless){

            MyMessages.MsgWireless wlMsg = (MyMessages.MsgWireless) msg;

            if (MyDrvWireless.matchesTestRequest(wlMsg)) {
                MyMessages.MsgWireless res = new MyMessages.MsgWireless(
                        MyMessages.MsgWireless.WirelessCommand.TEST, DEFAULT_TEST_RESPONSE);
                doInBackground(new MyWifiManager.OutputTask(output, MyDrvWireless.encodeMessage(res)));
            }
            else if (MyDrvWireless.matchesTestResponse(wlMsg)) {

                Log.v(TAG, "Test response received successfully");

                mbPassedConnTest = true;

                if (mRequirementResponseListener != null) {
                    mRequirementResponseListener.onAvailabilityStateChanged(this);
                }
            }
        }
    }

    @Override
    public boolean passedConnectivityTest() {
        return mbPassedConnTest;
    }

    @Override
    public void onMessageReceived(MyMessages.MyMessage msg) {

        if (msg == null) {
            return;
        }

        // send module's messages to the remote server
        if (msg instanceof MyMessages.MsgWireless) {
            doInBackground(new MyWifiManager.OutputTask(output, MyDrvWireless.encodeMessage((MyMessages.MsgWireless) msg)));
        }
    }

    private void connectHeadsetProxy(Context context) {
        // Establish connection to the proxy.
        mBluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET);
    }

    // ... call functions on bluetoothHeadset

    private void disconnectHeadsetProxy() {
        // Close proxy connection after use.
        mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
    }

    /* --------------------------------- Classes & Data Types ----------------------------------- */

    private class AcceptThread implements Runnable {

        private final MyBaseManager mManager;

        public AcceptThread(MyBaseManager manager) {

            mManager = manager;

            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(BLUETOOTH_SERVICE_NAME,
                        BLUETOOTH_SERVICE_UUID);

            }
            catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
            mServerSocket = tmp;
        }

        @Override
        public void run() {

            Log.d(TAG, "Running Bluetooth AcceptThread...");
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    mSocket = mServerSocket.accept();

                    output = new PrintWriter(mSocket.getOutputStream());
                    input = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));

                    // read data from the server
                    mInputTask = new MyWifiManager.InputTask(mManager, input);
                    doInBackground(mInputTask);

                    if (mRequirementResponseListener != null) {
                        mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                    }
                }
                catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (mSocket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    try {
                        mServerSocket.close();

//                        ConnectedThread thConnected = new ConnectedThread(mSocket);
//                        thConnected.start();
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
                mServerSocket.close();
            }
            catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectThread implements Runnable {

        private final MyBaseManager mManager;

        public ConnectThread(MyBaseManager manager, BluetoothDevice device) {

            mManager = manager;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                mSocket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SERVICE_UUID);
            }
            catch (IOException | SecurityException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {

            try {
                Log.d(TAG, "Running Bluetooth ConnectThread...");
                // Cancel discovery because it otherwise slows down the connection.
                mBluetoothAdapter.cancelDiscovery();

                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mSocket.connect();

                output = new PrintWriter(mSocket.getOutputStream());
                input = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));

                // read data from the server
                mInputTask = new MyWifiManager.InputTask(mManager, input);
                doInBackground(mInputTask);

                Log.d(TAG, "Connect to remote server: connection established");

                if (mRequirementResponseListener != null) {
                    mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                }
            }
            catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mSocket.close();
                }
                catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
            }
            catch (SecurityException e) {
                e.printStackTrace();
            }
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mSocket.close();
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
                String msg = new String(mmBuffer, 0, numBytes, StandardCharsets.US_ASCII);
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
                    String msg = new String(mmBuffer, 0, numBytes, StandardCharsets.US_ASCII);
                    Log.v(TAG, "Bluetooth out: "+numBytes+": "+msg);

                    //mListener.onMessageReceived(msg); // todo: publish a wireless message instead
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
                String msg = new String(bytes, 0, bytes.length, StandardCharsets.US_ASCII);
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
                        getBgHandler().obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                getBgHandler().sendMessage(writeErrorMsg);
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

    // Create a BroadcastReceiver for ACTION_FOUND.
    private static class BluetoothDiscoverReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = "?";
                try {
                    deviceName = device.getName();
                }
                catch (SecurityException e) {
                    e.printStackTrace();
                }
                String deviceHardwareAddress = device.getAddress(); // MAC address

                Log.d(TAG, "Found device: "+deviceName);
            }
        }
    };

    private final BroadcastReceiver mDevConnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
           //... //Device found
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
           //... //Device is now connected
            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
           //... //Done searching
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
           //... //Device is about to disconnect
            }
            else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
           //... //Device has disconnected
            }
        }
    };

}
