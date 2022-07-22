package com.dayani.m.roboplatform.managers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import com.dayani.m.roboplatform.utils.AppGlobals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.content.Context.WIFI_SERVICE;

public class MyWifiManager {

    private static final String TAG = MyWifiManager.class.getSimpleName();

    private static final int WIFI_AP_STATE_FAILED = -1;

    private static final String DEFAULT_IP_ADDRESS = "0.0.0.0";
    private static final String DEFAULT_HOTSPOT_IP = "192.168.43.1";
    private static final int DEFAULT_PORT = 27015;

    private static final String DEFAULT_TEST_COMMAND = "test";

    private static final String KEY_DEFAULT_HOTSPOT_IP = AppGlobals.PACKAGE_BASE_NAME+
            ".KEY_DEFAULT_HOTSPOT_IP";
    private static final String KEY_DEFAULT_PORT = AppGlobals.PACKAGE_BASE_NAME+".KEY_DEFAULT_PORT";

    static final String KEY_WRITE_SETTINGS_PERMISSION = AppGlobals.PACKAGE_BASE_NAME +
            ".WRITE_SETTINGS_PERMISSION_KEY";

    static final int REQUEST_WRITE_SETTINGS_PERMISSION = 234;

    static final String[] WRITE_SETTINGS_PERMISSIONS = {
            android.Manifest.permission.WRITE_SETTINGS,
    };

    private boolean isWriteSettingGranted = false;

    private static Context appContext;

    private WifiManager mWifiManager;
    private ServerSocket mServerSocket;
    private BroadcastReceiver mWifiStateChangedReceiver;
    private String mIpAddress;
    private OnWifiNetworkInteractionListener mListener;

    private Method wifiControlMethod;
    private Method wifiApConfigurationMethod;
    private Method wifiApState;

    private String command = "x";
    private PrintWriter output;
    private BufferedReader input;

    private HandlerThread mServerThread;
    private HandlerThread mInputThread;
    private HandlerThread mOutputThread;
    private Handler mServerHandler;
    private Handler mInputHandler;
    private Handler mOutputHandler;
    private Handler mUiHandler = new Handler();

    private ServerTask mServerTask;


    public MyWifiManager(Context context, OnWifiNetworkInteractionListener listener) {
        appContext = context;
        mListener = listener;
        //mUiHandler = new Handler(appContext.getMainLooper());
        mWifiManager = (WifiManager) appContext.getSystemService(WIFI_SERVICE);

        //isWriteSettingGranted = this.hasWriteSettingsPermission(appContext);
    }

    public boolean getAvailableFlag() {
        return isWriteSettingGranted;
    }

    public static int getRequestPermissionCode() {
        return REQUEST_WRITE_SETTINGS_PERMISSION;
    }

    public static String[] getPermissions() {
        return WRITE_SETTINGS_PERMISSIONS;
    }

    public static String getPermissionKey() {
        return KEY_WRITE_SETTINGS_PERMISSION;
    }

    public String getMessage() {
        return command;
    }

    public static String getDefaultIpAddress() {
        return DEFAULT_IP_ADDRESS;
    }

    public static String getDefaultHotspotIp(Context context) {
        return MyStateManager.getStringPref(context,KEY_DEFAULT_HOTSPOT_IP,DEFAULT_HOTSPOT_IP);
    }

    public static void setDefaultHotspotIp(Context context, String hIp) {
        MyStateManager.setStringPref(context,KEY_DEFAULT_HOTSPOT_IP,hIp);
    }

    public static int getDefaultPort(Context context) {
        return MyStateManager.getIntegerPref(context,KEY_DEFAULT_PORT,DEFAULT_PORT);
    }

    public static void setDefaultPort(Context context, int port) {
        MyStateManager.setIntegerPref(context,KEY_DEFAULT_PORT,port);
    }

    public static String getDefaultTestCommand() {
        return DEFAULT_TEST_COMMAND;
    }

    public boolean hasWriteSettingsPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(context);
        } else {
            return MyPermissionManager.hasAllPermissions(
                context,getPermissions(),getPermissionKey());
        }
    }

    public void init() {
        registerBrReceivers();
        startBackgroundThreads();
    }

    public void clean() {
        unregisterBrReceivers();
        stopBackgroundThreads();
    }

    private void startBackgroundThreads() {
        startServerThread();
        startInputThread();
        startOutputThread();
    }

    private void stopBackgroundThreads() {
        stopServerThread();
        stopInputThread();
        stopOutputThread();
    }

    private void registerBrReceivers() {
        registerWifiStateReciever();
    }

    private void unregisterBrReceivers() {
        unregisterWifiStateReciever();
    }

    private void registerWifiStateReciever() {
        if (mWifiStateChangedReceiver == null) {
            mWifiStateChangedReceiver = new WifiStateBrReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        appContext.registerReceiver(mWifiStateChangedReceiver, intentFilter);
        Log.i(TAG, "registered WifiStateReceiver");
    }
    private void unregisterWifiStateReciever() {
        if (mWifiStateChangedReceiver != null) {
            appContext.unregisterReceiver(mWifiStateChangedReceiver);
            mWifiStateChangedReceiver = null;//new WifiStateBrReceiver();
            Log.i(TAG, "unregistered WifiStateReceiver");
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startServerThread() {

        mServerThread = new HandlerThread(TAG+"ServerBackground");
        mServerThread.start();
        Looper mServerLooper = mServerThread.getLooper();
        mServerHandler = new Handler(mServerLooper);
        Log.i(TAG, "Server Thread started successfully.");
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopServerThread() {

        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mServerThread == null) return;
        mServerThread.quitSafely();
        try {
            mServerThread.join();
            mServerThread = null;
            mServerHandler = null;
            Log.i(TAG, "Server Thread stopped successfully.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startInputThread() {

        mInputThread = new HandlerThread(TAG+"InputBackground");
        mInputThread.start();
        Looper mInputLooper = mInputThread.getLooper();
        mInputHandler = new Handler(mInputLooper);
        Log.i(TAG, "Input Thread started successfully.");
    }

    private void stopInputThread() {

        if (mInputHandler == null) return;
        mInputThread.quitSafely();
        try {
            mInputThread.join();
            mInputThread = null;
            mInputHandler = null;
            Log.i(TAG, "Input Thread stopped successfully.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startOutputThread() {

        mOutputThread = new HandlerThread(TAG+"OutputBackground");
        mOutputThread.start();
        Looper mOutputLooper = mOutputThread.getLooper();
        mOutputHandler = new Handler(mOutputLooper);
        Log.i(TAG, "Output Thread started successfully.");
    }

    private void stopOutputThread() {

        if (mOutputThread == null) return;
        mOutputThread.quitSafely();
        try {
            mOutputThread.join();
            mOutputThread = null;
            mOutputHandler = null;
            Log.i(TAG, "Output Thread stopped successfully.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*-------------------------------------- Wifi Hotspot ----------------------------------------*/

    private void getHotspotMethods() {
        try {
            wifiControlMethod = mWifiManager.getClass().
                    getMethod("setWifiApEnabled", WifiConfiguration.class,boolean.class);
            //wifiControlMethod.setAccessible(true);
            wifiApConfigurationMethod = mWifiManager.getClass().
                    getMethod("getWifiApConfiguration",null);
            wifiApState = mWifiManager.getClass().getMethod("getWifiApState");
            //or Method method = mWifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
            //            method.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public boolean setWifiApState(boolean enabled) {
        if (mWifiManager == null) return false;
        if (wifiControlMethod == null) {
            if (this.hasWriteSettingsPermission(appContext)) {
                this.getHotspotMethods();
            }
            else {
                return false;
            }
        }
        WifiConfiguration config = this.getWifiApConfiguration();
        try {
            if (enabled) {
                mWifiManager.setWifiEnabled(!enabled);
            }
            return (Boolean) wifiControlMethod.invoke(mWifiManager, config, enabled);
        } catch (Exception e) {
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
        } catch (Exception e) {
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
                mListener.onWifiEnabled();
                Log.i(TAG, "Wifi has already enabled!");
            }
        }
    }

    public void startServer() {
        //Maybe first enable wifi then do this!
        mIpAddress = getLocalIpAddress();
        Log.i(TAG, "Server IP: "+mIpAddress);
        if (mServerTask == null) {
            mServerTask = new ServerTask();
        }
        mServerHandler.post(mServerTask);
    }

    public void stopServer() {
        if (mServerTask != null) {
            mServerHandler.removeCallbacks(mServerTask);
            mServerTask = null;
        }
    }

    public void connectClient(final String addr, int port) {
        if (port == -1) {
            port = DEFAULT_PORT;
        }
        //SERVER_IP = addr;
        mServerHandler.post(new ConnectionTask(addr,port));
    }

    public String getLocalIpAddress() {
        assert mWifiManager != null;
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        int ipInt = wifiInfo.getIpAddress();
        String res = null;
        try {
            res = InetAddress.getByAddress(ByteBuffer.allocate(4).
                    order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()).getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return res;
    }

    private void sendMessage(String msg) {
        mOutputHandler.post(new OutputTask(msg));
    }

    public interface OnWifiNetworkInteractionListener {
        void onWifiEnabled();
        void onClientConnected();
        void onInputReceived(String msg);
    }

    class ServerTask implements Runnable {
        @Override
        public void run() {
            Log.i(TAG, "Starting server service...");
            Socket socket;
            try {
                mServerSocket = new ServerSocket(DEFAULT_PORT);
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        /*tvMessages.setText("Not connected");
                        tvIP.setText("IP: " + SERVER_IP);
                        tvPort.setText("Port: " + String.valueOf(SERVER_PORT));*/
                    }
                });
                try {
                    Log.i(TAG, "Waiting for client to connect...");
                    socket = mServerSocket.accept();
                    output = new PrintWriter(socket.getOutputStream());
                    input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    mListener.onClientConnected();
                    Log.i(TAG, "Client connected successfully.");
//                    mUiHandler.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            //tvMessages.setText("Connected\n");
//                        }
//                    });
                    mInputHandler.post(new InputTask());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class ConnectionTask implements Runnable {

        private String connAddr;
        private int connPort;

        ConnectionTask(String addr, int port) {
            connAddr = addr;
            connPort = port;
        }

        public void run() {
            Log.i(TAG, "Connection task started...");
            Socket socket;
            try {
                socket = new Socket(connAddr, connPort);
                output = new PrintWriter(socket.getOutputStream());
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                Log.i(TAG, "Connected to "+connAddr+':'+connPort);
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        //tvMessages.setText("Connected\n");
                    }
                });
                //say hello to server
                mOutputHandler.post(new OutputTask("Hello Server!"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class InputTask implements Runnable {

        char inChar = 0;
        boolean isRunning = true;

        @Override
        public void run() {
            Log.i(TAG, "Input service started successfully.");
            while (isRunning) {
                try {
                    command = input.readLine();
                    //inChar = (char) input.read();
                    if (command != null) {
                        mListener.onInputReceived(command);
                        //inChar = command.charAt(0);
                        Log.v(TAG, "Client: "+command);
                        /*mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                //tvMessages.append("client:" + message + "\n");
                            }
                        });*/
                        //mOutputHandler.post(new OutputTask("Hello alie."));
                    }
                    else {
                        //startServer();
                        return;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void stop() {
            isRunning = false;
        }
    }

    class OutputTask implements Runnable {
        private String message;
        OutputTask(String message) {
            this.message = message;
        }
        @Override
        public void run() {
            Log.i(TAG, "Output service started successfully.");
            output.write(message);
            output.flush();
            Log.i(TAG, "message: "+message+" sent successfully.");
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    //tvMessages.append("server: " + message + "\n");
                    //etMessage.setText("");
                }
            });
        }
    }

    /**
     * Remember: enabling Wifi and Being connected to the network is two
     * different things!
     */
    private class WifiStateBrReceiver extends BroadcastReceiver {

        //private static final String TAG = "WifiStateBrReceiver";

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
                        mListener.onWifiEnabled();
                        break;
                }
            }
            else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.d(TAG, "CONNECTIVITY_ACTION");

                isConnectedViaWifi(intent);

                isConnectedViaWifi();
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
                    mListener.onWifiEnabled();
                }

                //call your method
            }
            //appContext.unregisterReceiver(this);
            //mWifiStateChangedReceiver = null;
        }

        private boolean isConnectedViaWifi() {
            ConnectivityManager conManager =
                    (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
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

        private void checkNetworkConnectivity() {
            ConnectivityManager conMan =
                    (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
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

        private String getWifiNetSSID() {
            // e.g. To check the Network Name or other info:
            WifiManager wifiManager = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            String ssid = wifiInfo.getSSID();
            return ssid;
        }
    }
}
