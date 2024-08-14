package com.dayani.m.roboplatform.managers;

/*
 * Note1: Two methods to enable wifi:
 *      1. prompt the user to enable wifi and listen for changes (recommended)
 *      2. request change settings permission and enable wifi by brute force
 *          (also good for other settings like location or bluetooth)
 * Note2: Two modes of connection:
 *      1. Both server and client connect to an intermediary AP
 *      2. P2P connection
 * Note3: Each device can be either a server or client ->
 *      better to set the desktop as server and the Android app as client
 * Note4: This can already work with LAN, Wifi-Direct, and Hotspot, although
 *      the last choice is a bit problematic on devices that can't enable
 *      wifi and hotspot at the same time
 *
 * todo: more robust lifecycle and resource management
 *
 * Note 2024: This version supports bidirectional connection (both Client/Server at the same time)
 */


import static android.content.Context.WIFI_SERVICE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.dayani.m.roboplatform.drivers.MyDrvWireless;
import com.dayani.m.roboplatform.requirements.WiNetReqFragment;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.helpers.TestCommSpecs;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.HandleEnableSettingsRequirement;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MyWifiManager extends MyBaseManager implements HandleEnableSettingsRequirement,
        ActivityRequirements.ConnectivityTest {

    /* ===================================== Variables ========================================== */

    private static final String TAG = MyWifiManager.class.getSimpleName();

    private static final String PACKAGE_NAME = AppGlobals.PACKAGE_BASE_NAME;

    private static final int WIFI_AP_STATE_FAILED = -1;

    private static final String DEFAULT_IP_ADDRESS = "192.168.1.100";
    private static final String DEFAULT_HOTSPOT_IP = "192.168.43.1";
    private static final int DEFAULT_PORT = 27015;

//    public static final String DEFAULT_TEST_COMMAND = "wl-8749";
//    public static final String DEFAULT_TEST_RESPONSE = "wl-0462";

    private static final String KEY_DEFAULT_HOTSPOT_IP = PACKAGE_NAME+".KEY_DEFAULT_HOTSPOT_IP";
    private static final String KEY_DEFAULT_REMOTE_IP = PACKAGE_NAME+".KEY_DEFAULT_REMOTE_IP";
    private static final String KEY_DEFAULT_PORT = PACKAGE_NAME+".KEY_DEFAULT_PORT";
    private static final String KEY_WRITE_SETTINGS_PERMISSION = PACKAGE_NAME+".WRITE_SETTINGS_PERMISSION_KEY";

    private static final int REQUEST_WRITE_SETTINGS_PERMISSION = 234;

    private static final int SENSOR_ID_NETWORK = 0;

    //private boolean isWriteSettingGranted = false;
    private boolean mbWifiSettingsEnabled = false;
    //private boolean mbHotspotSettingsEnabled = false;
    private boolean mbIsWifiAvailable = false;
    private boolean mbPassedConnTest = false;

    private boolean mbIsServerMode = false;

    private final WifiManager mWifiManager;

    private BroadcastReceiver mWifiStateChangedReceiver;

    private Method wifiControlMethod;
    private Method wifiApConfigurationMethod;
    private Method wifiApState;

    private String mIpAddress;
    private int mPort;

    private ServerSocket mServerSocket;
    private Socket mConnSocket;

    private PrintWriter output;
    private BufferedReader input;

    //private ServerTask mServerTask;
    private InputTask mInputTask;

    //private String command = "x";

    /* ==================================== Construction ======================================== */

    public MyWifiManager(Context context) {

        super(context);
        mWifiManager = (WifiManager) context.getApplicationContext().getSystemService(WIFI_SERVICE);

        mIpAddress = getDefaultIpAddress(context);
        mPort = getDefaultPort(context);
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    @Override
    protected boolean resolveSupport(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    public static int getRequestPermissionCode() {
        return REQUEST_WRITE_SETTINGS_PERMISSION;
    }

    public static String getPermissionKey() {
        return KEY_WRITE_SETTINGS_PERMISSION;
    }

    @Override
    protected List<Requirement> getRequirements() {

        // A wireless connection req means adapter is enabled,
        // a comm socket can be opened, and
        // the remote server responds to test command
        return Collections.singletonList(Requirement.WIRELESS_CONNECTION);
    }

    @Override
    public boolean passedAllRequirements() {

        return isSettingsEnabled() && mbIsWifiAvailable;
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

        // settings is enabled
        if (requirements.contains(ActivityRequirements.Requirement.WIRELESS_CONNECTION)) {
            updateSettingsEnabled();
            mbIsWifiAvailable = isSettingsEnabled() && mConnSocket != null && mbPassedConnTest;
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

        // wifi setting enabled & a connection is established
        if (requirements.contains(Requirement.WIRELESS_CONNECTION)) {
            if (mRequirementRequestListener != null) {
                // the fragment should deal with attached state and permission (request permissions)
                mRequirementRequestListener.requestResolution(WiNetReqFragment.newInstance());
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

        String action = intent.getAction();
        if (action.matches(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION) ||
                action.matches(WifiManager.NETWORK_STATE_CHANGED_ACTION))  {

            Log.i(TAG, "Network Providers changed");
            updateSettingsEnabled();
        }
    }

    @Override
    public boolean isSettingsEnabled() { return isWifiEnabled(); }// || isHotspotEnabled(); }

    public boolean isWifiEnabled() { return mbWifiSettingsEnabled; }
    //public boolean isHotspotEnabled() { return mbHotspotSettingsEnabled; }

    @Override
    public void updateSettingsEnabled() {

        // wifi
        mbWifiSettingsEnabled = mWifiManager.isWifiEnabled();

        // hotspot
        //mbHotspotSettingsEnabled = checkHsEnabled();
    }

    @Override
    public void enableSettingsRequirement(Context context) {

        if (!isSettingsEnabled()) {
            // prompt the user to enable wifi
            Toast.makeText(context, "Please Enable the WiFi Network", Toast.LENGTH_SHORT).show();
        }
    }


    private boolean checkHsEnabled() {

        try {
            Method method = mWifiManager.getClass().getDeclaredMethod("getWifiApState");
            method.setAccessible(true);
            int actualState = (Integer) method.invoke(mWifiManager, (Object[]) null);
            return actualState != WIFI_AP_STATE_FAILED;
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean hasWriteSettingsPermission(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(context);
        }
        else {
            return this.hasAllPermissions();
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
    }

    @Override
    public void registerBrReceivers(Context context, LifeCycleState state) {

        if (state == LifeCycleState.ACT_CREATED) {

            if (mWifiStateChangedReceiver == null) {
                mWifiStateChangedReceiver = new WifiStateBrReceiver(this);
            }
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            context.registerReceiver(mWifiStateChangedReceiver, intentFilter);
            Log.i(TAG, "registered WifiStateReceiver");
        }
        else if (state == LifeCycleState.ACT_DESTROYED) {

            if (mWifiStateChangedReceiver != null) {
                context.unregisterReceiver(mWifiStateChangedReceiver);
                mWifiStateChangedReceiver = null;//new WifiStateBrReceiver();
                Log.i(TAG, "unregistered WifiStateReceiver");
            }
        }
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    protected String getResourceId(MyResourceIdentifier resId) {

        if (resId == null) {
            return "Unknown_Sensor";
        }

        int id = resId.getId();
        if (id == SENSOR_ID_NETWORK) {
            return "Wifi_Network";
        }
        else {
            return "Unknown_Sensor";
        }
    }

    @Override
    protected List<Pair<String, MyMessages.MsgConfig>> getStorageConfigMessages(MySensorInfo sensor) {

        // no storage for now
        return new ArrayList<>();
    }

    /* ====================================== Networking ======================================== */

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
        sensors.add(new MySensorInfo(SENSOR_ID_NETWORK, "Wifi Network"));

        sensorGroups.add(new MySensorGroup(MySensorGroup.getNextGlobalId(),
                MySensorGroup.SensorType.TYPE_WIRELESS_NETWORK, "Wireless Network", sensors));

        return sensorGroups;
    }


    public void setDefaultIp(String ip) { if (isValidIP(ip)) mIpAddress = ip; }
    public String getDefaultIp() { return mIpAddress; }

    public void setDefaultPort(int port) { mPort = port; }
    public int getDefaultPort() { return mPort; }

    private static String getDefaultIpAddress(Context context) {
        return MyStateManager.getStringPref(context,KEY_DEFAULT_REMOTE_IP,DEFAULT_IP_ADDRESS);
    }

    private static String getDefaultHotspotIp(Context context) {
        return MyStateManager.getStringPref(context,KEY_DEFAULT_HOTSPOT_IP,DEFAULT_HOTSPOT_IP);
    }

    private static int getDefaultPort(Context context) {
        return MyStateManager.getIntegerPref(context,KEY_DEFAULT_PORT,DEFAULT_PORT);
    }

    public static String getDefaultTestCommand() {
        return MyDrvWireless.DEFAULT_TEST_COMMAND;
    }

    public void saveRemoteIpAndPort(Context context) {
        MyStateManager.setStringPref(context, KEY_DEFAULT_REMOTE_IP, mIpAddress);
        MyStateManager.setIntegerPref(context, KEY_DEFAULT_PORT, mPort);
    }

    public void setServerMode(boolean state) { mbIsServerMode = state; }

    public boolean isServerMode() { return mbIsServerMode; }

    public boolean isConnected() {

        return isWifiEnabled() && mConnSocket != null;
    }

    /*-------------------------------------- Wifi Hotspot ----------------------------------------*/

    // todo: use P2P instead of hotspot

    private void getHotspotMethods() {
        try {
            wifiControlMethod = mWifiManager.getClass().
                    getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            //wifiControlMethod.setAccessible(true);
            wifiApConfigurationMethod = mWifiManager.getClass().
                    getMethod("getWifiApConfiguration",null);
            wifiApState = mWifiManager.getClass().getMethod("getWifiApState");
            //or Method method = mWifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            //            method.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public boolean setWifiApState(Context context, boolean enabled) {

        if (mWifiManager == null) return false;

        if (wifiControlMethod == null) {
            //if (this.hasWriteSettingsPermission(context)) {
            //    this.getHotspotMethods();
            //}
            //else {
                return false;
            //}
        }
        WifiConfiguration config = this.getWifiApConfiguration();
        try {
            if (enabled) {
                mWifiManager.setWifiEnabled(!enabled);
            }
            return (Boolean) wifiControlMethod.invoke(mWifiManager, config, enabled);
        }
        catch (Exception e) {
            Log.e(TAG, "", e);
            return false;
        }
    }

    private WifiConfiguration getWifiApConfiguration() {

        if (wifiApConfigurationMethod == null) return null;

        try {
            return (WifiConfiguration)wifiApConfigurationMethod.invoke(mWifiManager, null);
        }
        catch(Exception e) {
            return null;
        }
    }

    public int getWifiApState() {

        if (wifiApState == null) return WIFI_AP_STATE_FAILED;
        try {
            return (Integer) wifiApState.invoke(mWifiManager);
        }
        catch (Exception e) {
            Log.e(TAG, "", e);
            return WIFI_AP_STATE_FAILED;
        }
    }

    /*------------------------------------- Wifi Network -----------------------------------------*/

    public void setWifiState(boolean state) {
        if (mWifiManager != null){
            if (!mWifiManager.isWifiEnabled()) {
                mWifiManager.setWifiEnabled(state);
                Log.i(TAG, "Wifi State: " + state);
            } else {
                //mListener.onWifiEnabled();
                if (mRequirementResponseListener != null) {
                    mRequirementResponseListener.onAvailabilityStateChanged(this);
                }
                Log.i(TAG, "Wifi has already enabled!");
            }
        }
    }

    public void startServer() {

        if (!isSettingsEnabled() || mConnSocket != null) {
            return;
        }

        mIpAddress = getLocalIpAddress();
        Log.i(TAG, "Server IP: "+ mIpAddress);

        if (mPort < 0) {
            mPort = DEFAULT_PORT;
        }

        doInBackground(new ServerTask(this, mPort));
    }

    public void stopServer() {

        if (mServerSocket != null) {
            try {
                Log.i(TAG, "Stopping server");
                mServerSocket.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                mServerSocket = null;
            }
        }
    }

    public void connectClient() {

        if (!isSettingsEnabled() || mConnSocket != null) {
            return;
        }

        if (mPort < 0) {
            mPort = DEFAULT_PORT;
        }
        //SERVER_IP = ip;
        doInBackground(new ConnectionTask(this, mIpAddress, mPort));
    }

    public String getLocalIpAddress() {

        assert mWifiManager != null;
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();

        String res = null;
        try {
            res = InetAddress.getByAddress(ByteBuffer.allocate(4).
                    order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
        }
        catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return res;
    }

    @Override
    public void handleTestSynchronous(MyMessages.MyMessage msg) {

    }

    @Override
    public void handleTestAsynchronous(MyMessages.MyMessage msg) {

        if (!isSettingsEnabled() || mConnSocket == null) {
            mbPassedConnTest = false;
            return;
        }

        if (msg == null) {
            // init. test & send the test command
            doInBackground(new OutputTask(output, MyDrvWireless.getTestRequest()));
            // get the response elsewhere and check
        }
        else if (msg instanceof MsgWireless){

            MsgWireless wlMsg = (MsgWireless) msg;

            if (MyDrvWireless.matchesTestRequest(wlMsg)) {
                MsgWireless res = new MsgWireless(MsgWireless.WirelessCommand.TEST,
                        MyDrvWireless.DEFAULT_TEST_RESPONSE);
                doInBackground(new OutputTask(output, MyDrvWireless.encodeMessage(res)));
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
        if (mConnSocket != null) {
            try {
                mConnSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mConnSocket = null;
        }
        if (output != null) {
            output.close();
            output = null;
        }
        if (input != null) {
            //input.close();
            input = null;
        }

        mbIsWifiAvailable = false;
        updateSettingsEnabled();
    }

    @Override
    public void onMessageReceived(MyMessages.MyMessage msg) {

        if (msg == null) {
            return;
        }

        // send module's messages to the remote server
        if (msg instanceof MsgWireless) {
            doInBackground(new OutputTask(output, MyDrvWireless.encodeMessage((MsgWireless) msg)));
        }
    }

    /* ======================================== Helpers ========================================= */

    public static boolean isValidIP(String ip) {
        try {
            if ( ip == null || ip.isEmpty() ) {
                return false;
            }

            String[] parts = ip.split( "\\." );
            if ( parts.length != 4 ) {
                return false;
            }

            for ( String s : parts ) {
                int i = Integer.parseInt( s );
                if ( (i < 0) || (i > 255) ) {
                    return false;
                }
            }
            return !ip.endsWith(".");

        }
        catch (NumberFormatException nfe) {
            return false;
        }
    }

    public static String getSuggestedInterfaces(boolean useIPv4, String sp) {

        StringBuilder sb = new StringBuilder();

        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr != null && sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4) {
                                sb.append(sp).append(intf.getDisplayName()).append(":\t").append(sAddr).append("\n");
                            }
                        }
                        else {
                            if (!isIPv4 && sAddr != null) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                String res = delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                                sb.append(sp).append(intf.getDisplayName()).append(":\t").append(res).append("\n");
                            }
                        }
                    }
                }
            }
        }
        catch (Exception ignored) {
            sb.append("Error while trying to read interfaces\n");
        } // for now eat exceptions
        return sb.toString();
    }

    /* ======================================= Data Types ======================================= */

    private class ServerTask implements Runnable {

        private final MyBaseManager mManager;
        private final int mPort;

        public ServerTask(MyBaseManager manager, int port) {

            mManager = manager;
            mPort = port;
        }

        @Override
        public void run() {

            Log.i(TAG, "Starting server service...");
            try {
                mServerSocket = new ServerSocket(mPort);

                Log.i(TAG, "Waiting for client to connect...");

                mConnSocket = mServerSocket.accept();

                output = new PrintWriter(mConnSocket.getOutputStream());
                input = new BufferedReader(new InputStreamReader(mConnSocket.getInputStream()));

                String mClientIp = mConnSocket.getRemoteSocketAddress().toString();
                Log.i(TAG, mClientIp+" connected successfully.");

                mInputTask = new InputTask(mManager, input);
                doInBackground(mInputTask);

                if (mRequirementResponseListener != null) {
                    mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ConnectionTask implements Runnable {

        private final MyBaseManager mManager;
        private final String connIp;
        private final int connPort;

        ConnectionTask(MyBaseManager manager, String ip, int port) {

            mManager = manager;
            connIp = ip;
            connPort = port;
        }

        public void run() {

            try {
                Log.i(TAG, "Connection task started...");

                mConnSocket = new Socket(connIp, connPort);

                output = new PrintWriter(mConnSocket.getOutputStream());
                input = new BufferedReader(new InputStreamReader(mConnSocket.getInputStream()));

                String clientIp = mConnSocket.getLocalSocketAddress().toString();
                Log.i(TAG, clientIp+" connected to "+ connIp +':'+connPort);

                // read data from the server
                mInputTask = new InputTask(mManager, input);
                doInBackground(mInputTask);

                // initiate test sequence
                //initTestSequence();

                if (mRequirementResponseListener != null) {
                    mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                }
            }
            catch (IOException e) {
                close();
                e.printStackTrace();
            }
        }
    }

    public static class InputTask implements Runnable {

        private boolean isRunning = true;
        private final BufferedReader mInput;
        private final WeakReference<MyBaseManager> mManager;

        private MyWifiCommTest mWlCommTest;

        public InputTask(MyBaseManager manager, BufferedReader input) {

            mManager = new WeakReference<>(manager);
            mInput = input;
        }

        @Override
        public void run() {

            Log.i(TAG, "Input service started successfully.");
            MyBaseManager manager = mManager.get();

            while (isRunning) {

                try {
                    if (mInput == null) {
                        this.stop();
                        //close();
                        return;
                    }

                    String command = mInput.readLine();
                    if (command != null) {

                        MsgWireless msg = MyDrvWireless.decodeMessage(command);
                        String msgStr = msg.toString();

                        if (MsgWireless.WirelessCommand.TEST.equals(msg.getCmd())) {
                            if (manager instanceof ActivityRequirements.ConnectivityTest) {
                                ((ActivityRequirements.ConnectivityTest) manager).handleTestAsynchronous(msg);
                            }
                        }
                        else if (MyWifiCommTest.isCommTestRequest(msg)) {
                            if (msgStr.contains("start")) {
                                startCommTest(msg);
                            }
                            else {
                                mWlCommTest.processWlInput(msg);
                            }
                        }
                        else if (manager != null) {
                            manager.publishMessage(msg);
                        }
                        //Log.v(TAG, "Remote: "+msg);
                    }
                    else {
                        //startServer();
                        return;
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void startCommTest(MsgWireless msg) {

            String msgStr = msg.toString();
            MyBaseManager manager = mManager.get();

            if (msgStr.contains("test:ltc")) {
                //Log.d(TAG, "Remote: start latency test");
                mWlCommTest = new MyWifiCommTest(manager, TestCommSpecs.TestMode.LATENCY);
            }
            else if (msgStr.contains("test:tp")) {
                //Log.d(TAG, "Remote: start throughput test");
                mWlCommTest = new MyWifiCommTest(manager, TestCommSpecs.TestMode.THROUGHPUT);
            }
            if (manager != null) {
                manager.doInBackground(mWlCommTest);
            }
        }

        public void stop() {
            isRunning = false;
        }
    }

    public static class OutputTask implements Runnable {

        private final PrintWriter mOutput;
        private final String message;

        OutputTask(PrintWriter output, String message) {
            mOutput = output;
            this.message = message;
        }

        @Override
        public void run() {

            if (mOutput == null) {
                //close();
                return;
            }

            //Log.i(TAG, "Output service started successfully.");
            mOutput.write(message);
            mOutput.flush();
            //Log.i(TAG, "message: "+message+" sent successfully.");
        }
    }

    /**
     * Remember: enabling Wifi and Being connected to the network is two
     * different things!
     */
    private class WifiStateBrReceiver extends BroadcastReceiver {

        //private static final String TAG = "WifiStateBrReceiver";

        private final MyBaseManager mManager;

        private WifiStateBrReceiver(MyBaseManager manager) {
            this.mManager = manager;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            final String action = intent.getAction();
            if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
                Log.d(TAG, "SUPPLICANT_CONNECTION_CHANGE_ACTION");

                boolean extraWifiEnabled = intent.
                        getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
                if (extraWifiEnabled) {
                    //do stuff
                    //mListener.onWifiEnabled();
                } else {
                    // wifi connection was lost
                }
                int extraWifiState = intent.
                        getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                switch (extraWifiState) {
                    case WifiManager.WIFI_STATE_DISABLED:
                    case WifiManager.WIFI_STATE_DISABLING:
                        Log.i(TAG, "WIFI_STATE_DISABLING");
                        //enableUI(false);
                        break;
                    case WifiManager.WIFI_STATE_ENABLED:
                        Log.i(TAG, "WIFI_STATE_ENABLED");
                        //checkNetworkConnectivity();
                        //update();
                        //enableUI(true);
                        break;
                    case WifiManager.WIFI_STATE_ENABLING:
                        Log.i(TAG, "WIFI_STATE_ENABLING");
                        break;
                    case WifiManager.WIFI_STATE_UNKNOWN:
                        //this happens when user activates hotspot manually!
                        Log.i(TAG, "WIFI_STATE_UNKNOWN");
                        //mListener.onWifiEnabled();
                        if (mRequirementResponseListener != null) {
                            mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                        }
                        break;
                }
            }
            else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(TAG, "CONNECTIVITY_ACTION");

                isConnectedViaWifi(intent);

                isConnectedViaWifi(context);
            }
            else if (action.equalsIgnoreCase(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                Log.d(TAG, "WIFI_STATE_CHANGED_ACTION");

                /*int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (wifiState == WifiManager.WIFI_STATE_DISABLED)
                {
                    Log.e(TAG, " ----- Wifi  Disconnected ----- ");
                }*/
            }
            //***************** this is the most important one! ********************//
            else if(action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)){
                Log.d(TAG, "NETWORK_STATE_CHANGED_ACTION");

                NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                //boolean connected = info.isConnected();
                if (netInfo.getDetailedState().equals(NetworkInfo.DetailedState.CONNECTED)) {
                    Log.d(TAG, "Network state: connected");
                    //mListener.onWifiEnabled();
                    if (mRequirementResponseListener != null) {
                        mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                    }
                }

                //call your method
            }
            //appContext.unregisterReceiver(this);
            //mWifiStateChangedReceiver = null;
        }

        private boolean isConnectedViaWifi(Context context) {
            ConnectivityManager conManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mWifiInfo = conManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            if (mWifiInfo != null &&
                    mWifiInfo.getType() == ConnectivityManager.TYPE_WIFI &&
                    mWifiInfo.isConnected()) {

                return mWifiInfo.isConnected();

                // Wifi is connected
                //String ssid = getWifiNetSSID();

                //Log.e(TAG, " -- Wifi connected --- " + " SSID " + ssid );
            }

            return mWifiInfo.isConnected();
        }

        private void checkNetworkConnectivity(Context context) {
            ConnectivityManager conMan =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            while (conMan.getActiveNetworkInfo() == null ||
                    conMan.getActiveNetworkInfo().getState() != NetworkInfo.State.CONNECTED) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private boolean isConnectedViaWifi(Intent intent) {
            int networkType = intent.getIntExtra(
                    android.net.ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
            if (ConnectivityManager.TYPE_WIFI == networkType) {
                NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    if (networkInfo.isConnected()) {

                        // TODO: wifi is connected
                        return true;
                    } else {
                        // TODO: wifi is not connected
                    }
                }
            }
            return false;
        }

        private String getWifiNetSSID(Context context) {
            // e.g. To check the Network Name or other info:
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getSSID();
        }
    }

    private static class MyWifiCommTest extends TestCommSpecs {

        private WeakReference<MyBaseManager> mManager;

        public MyWifiCommTest(MyBaseManager manager, TestMode mode) {
            super(mode);
            mManager = new WeakReference<>(manager);
        }

        @Override
        protected void fillSendBufferForThroughput() {
            for (int i = 0; i < buffLen; i++) {
                if (i % 2 == 0) {
                    mSendBuffer[i] = (byte) 0x55;
                }
                else {
                    // ASCII has no 0xAA!
                    mSendBuffer[i] = (byte) 0x2A;
                }
            }
        }

        @Override
        public void send(byte[] buffer) {

            if (mManager.get() == null) {
                return;
            }

            String msg = "";
            if (mTestMode == TestMode.LATENCY) {
                msg = "test:ltc:" + buffer[0];
            }
            else if (mTestMode == TestMode.THROUGHPUT) {
                String buffStr = new String(buffer, StandardCharsets.US_ASCII);
                msg = "test:tp:" + buffStr;
            }
            MyMessages.MsgWireless msgWl = new MsgWireless(MsgWireless.WirelessCommand.CMD_WORD, msg);

            if (mManager.get() != null) {
                mManager.get().onMessageReceived(msgWl);
            }
        }

        @Override
        public void reportResults(String msg) {

            if (mManager.get() == null) {
                return;
            }
            MyBaseManager manager = mManager.get();

            if (manager.mBackgroundJobListener != null) {
                manager.mBackgroundJobListener.getUiHandler().post(() ->
                        manager.publishMessage(new MyMessages.MsgLogging(msg, "logging")));
            }
        }

        protected void processWlInput(MsgWireless msg) {

            MyBaseManager manager = mManager.get();
            String msgStr = msg.toString();
            String[] msgParts = msgStr.split(":");

            if (msgStr.contains("rp") && msgParts.length > 3) {
                int res = Integer.parseInt(msgParts[3]);
                    updateState(res);
                }
            else if (msgParts.length > 2) {
                // When WlManager acts as a server
                String newMsgStr = "test:ltc:rp:" + msgParts[2];
                if (msgStr.contains("test:tp")) {
                    newMsgStr = "test:tp:rp:" + countCodeFields(msgParts[2]);
                }
                MsgWireless msgWl = new MsgWireless(MsgWireless.WirelessCommand.CMD_WORD, newMsgStr);
                if (manager != null) {
                    manager.onMessageReceived(msgWl);
                }
            }
        }

        private int countCodeFields(String msg) {
            int i = 0;
            int cnt = 0;
            for (char c : msg.toCharArray()) {
                if (i % 2 == 0) {
                    if (c == 0x55) {
                        cnt++;
                    }
                }
                else {
                    if (c == 0x2A) {
                        cnt++;
                    }
                }
                i++;
            }
            return cnt;
        }

        protected static boolean isCommTestRequest(MsgWireless msg) {

            String msgStr = msg.toString();
            return MsgWireless.WirelessCommand.CMD_WORD.equals(msg.getCmd()) &&
                    (msgStr.contains("test:ltc") || msgStr.contains("test:tp"));
        }
    }
}
