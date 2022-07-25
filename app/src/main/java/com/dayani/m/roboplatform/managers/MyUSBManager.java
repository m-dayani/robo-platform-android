package com.dayani.m.roboplatform.managers;

/*
 * Note1: Unlike other sensor manager classes, USB
 *      doesn't provide a callback for receiving USB data.
 *      To overcome this problem, currently a broadcast
 *      mechanism is used.
 *      There could be other options like acquiring data periodically
 *      using a service or job schedulers.
 *
 * Note2: Done: methods to interpret byte buffer content.
 *      (turn bytes to sensor values).
 *      Also program device to send meaningful values.
 *
 * Note3: This is how to debug USB transactions
 * 		(https://developer.android.com/guide/topics/connectivity/usb)
 *
 *		Connect the Android-powered device via USB to your computer.
 *		$ adb tcpip 5555
 *	    Disconnect the cable
 *		$ adb connect <device-ip-address>:5555
 *
 * 		the Android-powered device and can issue the usual adb commands like adb logcat.
 *		To set your device to listen on USB, enter adb usb.
 *
 * Note4: Still don't know why launching 2 activities with
 *      this class instantiated in them, will cause fatal exception
 *      when turning back?
 *
 * Note5: The better model to work with USB devices:
 *      1. find the usb device
 *      2. ask for permissions
 *      3. onPermissionGranted, open the device
 *      4. get reports and ...
 *      5. USB permission br receiver is handled in this class.
 *
 * Note6: If an instance of this class is instantiated, must
 *      call clean in an appropriate place.
 *
 * ** Availability:
 *      1. Target device is connected
 *      2. Target device is permitted
 *      3. Target device responds correctly
 * ** Resources:
 *      1. Internal HandlerThread
 *      2. USB connection
 *      3. Lots of broadcast receivers! -> Leaky!
 * ** State Management:
 *      1. isAvailable (availability)
 *      2. permissionsGranted
 *      3. isPolling (running)
 *
 * This is robust even when unplug during operation!
 * TODO: More work on preventing resource leaks.
 */

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dayani.m.roboplatform.utils.ActivityRequirements;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.SensorsContainer;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;


public class MyUSBManager {

    private static final String TAG = MyUSBManager.class.getSimpleName();

    private static final String DEFAULT_TEST_IN_MESSAGE = "in_msg"; //sent to usb device
    private static final String DEFAULT_TEST_OUT_NEG_MESSAGE = "outn";
    private static final String DEFAULT_TEST_OUT_POS_MESSAGE = "outp";

    private static final int DEFAULT_VENDOR_ID = 5824; //V-USB VID //1659; //Omega VID //0x2341; //Arduino VID
    private static final int DEFAULT_DEVICE_ID = 2002; //V-USB led device (avr)

    private static final String KEY_DEFAULT_DEVICE_PERMISSION = AppGlobals.PACKAGE_BASE_NAME +
            ".MyUSBManager_DefaultUSBDevice."+DEFAULT_VENDOR_ID+':'+DEFAULT_DEVICE_ID;
    private static final String KEY_DEFAULT_VENDOR_ID =
            AppGlobals.PACKAGE_BASE_NAME+".KEY_DEFAULT_VENDOR_ID";
    private static final String KEY_DEFAULT_DEVICE_ID =
            AppGlobals.PACKAGE_BASE_NAME+".KEY_DEFAULT_DEVICE_ID";

    private static final String ACTION_USB_PERMISSION =
            AppGlobals.PACKAGE_BASE_NAME+".USB_PERMISSION";
    public static final String ACTION_USB_AVAILABILITY =
            AppGlobals.PACKAGE_BASE_NAME+".ACTION_USB_AVAILABILITY";
    public static final String ACTION_SENSOR_RECEIVE =
            AppGlobals.PACKAGE_BASE_NAME+".USB_SENSOR_RECIEVE";

    protected static final int STD_USB_REQUEST_GET_DESCRIPTOR = 0x06;
    // http://libusb.sourceforge.net/api-1.0/group__desc.html
    protected static final int LIBUSB_DT_STRING = 0x03;

    private static final int SENSOR_UPDATE_INTERVAL_MILLIS = 256;
    private static final int TARGET_STATE_BUFFER_BYTES = 8;

    private static Context appContext;

    private LocalBroadcastManager mUsbBrManager;
    private BroadcastReceiver mUsbSensorReceiver = null;
    private BroadcastReceiver mUsbPermissionReceiver = null;
    private PendingIntent mPermissionIntent;

    private UsbManager mUsbManager;
    private OnUsbConnectionListener mConnListener;
    private UsbDevice mDevice = null;
    private UsbInterface mInterface = null;
    private UsbDeviceConnection mConnection = null;

    private byte[] mSensorBuffer = new byte[256];
    private byte[] mStateBuffer = new byte[256];
    private String mRecMsg = "";
    private StringBuffer mSensorString;

    public enum UsbCommands {
        BROADCAST,
        REPORT_SENSOR,	//send long msg to device
        UPDATE_STATE,	//get message (state updates) from device
        RUN_TEST		//run predefined test sequence:
        //test: read, write, sensor availability, output modification
    }

    private boolean isAvailable = false;

    //2nd Method Handler/Runnable (preferred)
    //runs without timer be reposting self
    private long starttime = 0;
    private HandlerThread mBackgroundThread;
    private Handler mUSBSensorHandler;
    private Runnable sensorReceiveTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "Message received, processing...");
            Log.d(TAG,"Doing Sensor receive job in background.");

            mRecMsg = receiveSensor();
            //because we updating Sensor string from another thread.
            //don't need this if use StringBuffer instead.
            //synchronized (this) {
                mSensorString.append(getSensorString(mRecMsg));
            //}
            Log.d(TAG, mRecMsg);

            mUSBSensorHandler.postDelayed(this, SENSOR_UPDATE_INTERVAL_MILLIS);
        }
    };

    /*======================================== Management ========================================*/

    /**
     * If we call clean explicitly, init also needs to be
     * called explicitly.
     * @param context
     * @param sb
     */
    public MyUSBManager(Context context, OnUsbConnectionListener connListener, StringBuffer sb) {

        appContext = context;
        mConnListener = connListener;
        mSensorString = sb;

        mUsbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        mUsbBrManager = LocalBroadcastManager.getInstance(appContext);
        mPermissionIntent = PendingIntent.getBroadcast(appContext, 0,
                new Intent(ACTION_USB_PERMISSION), 0);

        //startBackgroundThread();
    }

    /**
     * With this implicitly called in constructor, we need to
     * call clean() explicitly in onDestroy of an activity.
     */
    /*public void init() {
        checkDefaultDeviceAvailability();
    }*/

    /**
     * Don't call close here, because we don't call open in init!
     */
    public void clean() {

        if (mUsbPermissionReceiver != null) {
            unregisterUsbPermission();
        }
        //stopBackgroundThread();
        close();
        mPermissionIntent = null;
        mUsbPermissionReceiver = null;
        appContext = null;
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {

        mBackgroundThread = new HandlerThread(TAG);
        mBackgroundThread.start();
        mUSBSensorHandler = new Handler(mBackgroundThread.getLooper());
        Log.d(TAG, "background thread started successfully.");
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mUSBSensorHandler = null;
            Log.d(TAG, "background thread stopped successfully.");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void registerUsbPermission() {
        if (mUsbPermissionReceiver == null) {
            mUsbPermissionReceiver = new UsbPermissionReceiver();
        }
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        appContext.registerReceiver(mUsbPermissionReceiver, filter);
        Log.d(TAG, "USB Permission Broadcast Receiver Registered.");
    }

    private void unregisterUsbPermission() {
        if (mUsbPermissionReceiver != null) {
            appContext.unregisterReceiver(mUsbPermissionReceiver);
            mUsbPermissionReceiver = null;
            Log.d(TAG, "USB Permission Broadcast Receiver Unregistered.");
        }
    }

    public void registerUsbSensorReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_SENSOR_RECEIVE);
        //filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mUsbBrManager.registerReceiver(mUsbSensorReceiver, filter);
        Log.d(TAG, "USB Sensor Broadcast Receiver Registered.");
    }

    public void unregisterUsbSensorReceiver() {
        mUsbBrManager.unregisterReceiver(mUsbSensorReceiver);
        Log.d(TAG, "USB Sensor Broadcast Receiver Unregistered.");
    }

    public void startPeriodicSensorPoll() {
        startBackgroundThread();
        //tryOpenDefaultDevice();
        starttime = System.currentTimeMillis();
        mUSBSensorHandler.postDelayed(sensorReceiveTask, 0);
    }

    public void stopPeriodicSensorPoll() {
        mUSBSensorHandler.removeCallbacks(sensorReceiveTask);
        stopBackgroundThread();
        close();
    }

    public boolean checkConnection() {
        if (mConnection == null) {
            //toastMsgShort("First, open a connection.", appContext);
            Log.e(TAG, "No USB connection available.");
            return false;
        }
        return true;
    }

    /*----------------------------------- Getters & Setters --------------------------------------*/

    private static String getDefaultPermissionKey() {
        return KEY_DEFAULT_DEVICE_PERMISSION;
    }

    public static String getPermissionKey() {
        return getDefaultPermissionKey();
    }

    public static int getDefaultVendorId() {
        return MyStateManager.getIntegerPref(appContext,KEY_DEFAULT_VENDOR_ID,DEFAULT_VENDOR_ID);
    }

    public static void setDefaultVendorId(int val) {
        MyStateManager.setIntegerPref(appContext,KEY_DEFAULT_VENDOR_ID,val);
    }

    public static int getDefaultDeviceId() {
        return MyStateManager.getIntegerPref(appContext,KEY_DEFAULT_DEVICE_ID,DEFAULT_DEVICE_ID);
    }

    public static void setDefaultDeviceId(int val) {
        MyStateManager.setIntegerPref(appContext,KEY_DEFAULT_DEVICE_ID,val);
    }

    public boolean getAvailableFlag() {
        return isAvailable;
    }
    private void setAvailableFlag(boolean state) {
        isAvailable = state;
    }

    public byte[] getRawSensor() {
        return mSensorBuffer;
    }

    public void setStateBuffer(byte state, int index) {
        if (index >= mStateBuffer.length) {
            return;
        }
        this.mStateBuffer[index] = state;
    }

    public void setStateBuffer(byte[] buffer) {
        int minLength = Math.min(buffer.length, mStateBuffer.length);
        for (int i = 0; i < minLength; i++) {
            mStateBuffer[i] = buffer[i];
        }
    }

    public static void getSensorRequirements(Context mContext, SensorsContainer sensors) {

        // add requirements
        sensors.addRequirement(ActivityRequirements.Requirement.USB_DEVICE);

        // add permissions
        // no permissions

        // add sensors (external ADC)
        // TODO:
    }

    /*========================================== Close ===========================================*/

    public void close() {
        if (mConnection != null) {
            mConnection.releaseInterface(mInterface);
            mConnection.close();
            Log.d(TAG, "USB device closed successfully.");
        }
    }

    /*=========================================== Open ===========================================*/

    public String enumerateDevices() {

        StringBuilder res = new StringBuilder();
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            mUsbManager.requestPermission(device, mPermissionIntent);
            res.append(getDeviceSimpleReport(device));
        }
        return res.toString();
    }

    private UsbDevice findDevice(int vendorID, int deviceID) {

        if (vendorID <= 0 || deviceID <= 0) {
            //toastMsgShort("Enter valid Vendor & Device IDs.", appContext);
            Log.e(TAG, "Error in device and vendor IDs.");
            return null;
        }
        //Find the device

        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        //In case you know device name
        //UsbDevice device = deviceList.get("deviceName");
        //else
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (device.getDeviceId() == deviceID && device.getVendorId() == vendorID) {
                //mDevice = device;
                return device;
            }
        }
        //toastMsgShort("No Device found", appContext);
        Log.e(TAG, "No device found with: "+vendorID+':'+deviceID);
        return null;
    }

    /**
     * @warning This class registers a receiver implicitly.
     *      Need to unregister it (either with receiver itself
     *      or calling clean explicitly) when done.
     * @param mDevice
     */
    private void requestDevicePermission(UsbDevice mDevice) {
        if (mDevice != null) {
            registerUsbPermission();
            mUsbManager.requestPermission(mDevice, mPermissionIntent);
        }
    }

    private String openDevice(UsbDevice mDevice) {

        if (mDevice == null) {
            Log.e(TAG, "Input device is null reference.");
            return null;
        }
        String res = "";
        //Open Interface to the device

        mInterface = mDevice.getInterface(0);
        if (mInterface == null) {
            toastMsgShort("Unable to establish an interface.", appContext);
            Log.e(TAG, "Unable to establish an interface.");
            return null;
        }
        int epc = mInterface.getEndpointCount();

        //Open a connection

        mConnection = mUsbManager.openDevice(mDevice);
        if (null == mConnection) {
            toastMsgShort("Unable to establish a connection!", appContext);
            Log.e(TAG, "Unable to establish a connection!");
            return null;
        }

        //Communicate over the connection

        // Claims exclusive access to a UsbInterface.
        // This must be done before sending or receiving data on
        // any UsbEndpoints belonging to the interface.
        mConnection.claimInterface(mInterface, true);

        res = getConnectionReport(mConnection);

        return res;
    }

    /**
     * Possible approaches:
     * 1. Open device with default vendor and dev id
     * 2. Open first connected device (since our device only has
     *      one port).
     * @return same as openDevice()
     */
    public String tryOpenDefaultDevice() {

        if (mDevice == null) {
            Log.e(TAG, "No device found.");
            return null;
        }
        String res = openDevice(mDevice);
        return res;
    }

    public void tryOpenDevice(int vendorId, int deviceId) {
        //maybe first close last connection
        close();
        mDevice = findDevice(vendorId, deviceId);
        requestDevicePermission(mDevice);
    }

    public void updateDefaultDeviceAvailability() {
        close();
        mDevice = findDevice(DEFAULT_VENDOR_ID, DEFAULT_DEVICE_ID);
        requestDevicePermission(mDevice);
    }

    /*======================================== Messaging =========================================*/

    public int sendControlMsg(int dir, UsbCommands cmd, byte[] buff, int buffLen) {
        if (!checkConnection()) {
            return -1;
        }
        int flag = cmd.ordinal();
        try {
            int rdo = mConnection.controlTransfer(dir | UsbConstants.USB_TYPE_VENDOR,
                    flag, 0, 0, buff, buffLen, 5000);
            return rdo;
        }
        catch(Exception e) {
            e.printStackTrace();
            return -2;
        }
    }

    public int sendControlMsgInfo(int dir, UsbCommands cmd, int value, int index,
                                  byte[] buff, int buffLen) {
        if (!checkConnection()) {
            return -1;
        }
        int flag = cmd.ordinal();
        try {
            int rdo = mConnection.controlTransfer(dir | UsbConstants.USB_TYPE_VENDOR,
                    flag, value, index, buff, buffLen, 5000);
            return rdo;
        }
        catch(Exception e) {
            e.printStackTrace();
            return -2;
        }
    }

    public String receiveSensor() {

        int res = 0;
        res = sendControlMsg(UsbConstants.USB_DIR_IN, // _IN for read
                UsbCommands.REPORT_SENSOR, mSensorBuffer, mSensorBuffer.length);
        //String msg = "";
        //mRecMsg = getDoublesString(mSensorBuffer);
        Log.d(TAG, "got: "+res+" Bytes, msg: "+mRecMsg);
        return mRecMsg;
    }

    public void sendStateUpdates() {
        int res = 0;
        res = sendControlMsg(UsbConstants.USB_DIR_OUT, // _OUT for write
                UsbCommands.UPDATE_STATE, mStateBuffer, TARGET_STATE_BUFFER_BYTES);

        int msg = toInteger(mStateBuffer[1], mStateBuffer[0]);
        Log.d(TAG, "sent: "+res+" Bytes, msg: "+Integer.toString(msg));
    }

    public boolean testDevice() {
        byte[] msg = DEFAULT_TEST_IN_MESSAGE.getBytes();
        int res = 0;
        res = sendControlMsg(UsbConstants.USB_DIR_OUT, // _OUT for write
                UsbCommands.RUN_TEST, msg, msg.length);
        Log.d(TAG, "sent: "+res+" Bytes, msg: "+DEFAULT_TEST_IN_MESSAGE);
        res = sendControlMsg(UsbConstants.USB_DIR_IN, // _IN for read
                UsbCommands.REPORT_SENSOR, mSensorBuffer, mSensorBuffer.length);
        mRecMsg = getASCIIMessage(Arrays.copyOfRange(mSensorBuffer,0,4));
        Log.d(TAG, "got: "+res+" Bytes, msg: "+mRecMsg+", cool, right?!");
        return mRecMsg.equals(DEFAULT_TEST_OUT_NEG_MESSAGE) ||
                mRecMsg.equals(DEFAULT_TEST_OUT_POS_MESSAGE);
    }

    /*================================== Helper Classes & methods ================================*/

    private String getDeviceReport(UsbDevice device) {
        //res += ("Model:" + device.getDeviceName() + "\n");
        String res = ("VendorID:" + device.getVendorId() + "\n") +
                ("DeviceID:" + device.getDeviceId() + "\n") +
                ("Product:" + device.getProductId() + "\n") +
                ("Class:" + device.getDeviceClass() + "\n") +
                ("Subclass:" + device.getDeviceSubclass() + "\n") +
                ("Protocol:" + device.getDeviceProtocol() + "\n") +
                ("------------------------------------\n");
        return res;
    }

    private String getDeviceSimpleReport(UsbDevice device) {
        return device.getProductName()+", "+device.getVendorId()+':'+device.getDeviceId()+'\n';
    }

    private String getConnectionReport(UsbDeviceConnection mConnection) {

        if (mConnection == null) {
            Log.e(TAG, "getConnectionReport: no connection.");
            return null;
        }
        String res = "";
        // getRawDescriptors can be used to access descriptors
        // not supported directly via the higher level APIs,
        // like getting the manufacturer and product names.
        // because it returns bytes, you can get a variety of
        // different data types.
        byte[] rawDescs = mConnection.getRawDescriptors();
        String manufacturer = "", product = "";

        try {
            int idxMan = rawDescs[14];
            int idxPrd = rawDescs[15];

            int rdo = mConnection.controlTransfer(UsbConstants.USB_DIR_IN
                            | UsbConstants.USB_TYPE_STANDARD, STD_USB_REQUEST_GET_DESCRIPTOR,
                    (LIBUSB_DT_STRING << 8) | idxMan, 0, mSensorBuffer, 0xFF, 0);
            manufacturer = new String(mSensorBuffer, 2, rdo - 2, "UTF-16LE");

            rdo = mConnection.controlTransfer(UsbConstants.USB_DIR_IN
                            | UsbConstants.USB_TYPE_STANDARD, STD_USB_REQUEST_GET_DESCRIPTOR,
                    (LIBUSB_DT_STRING << 8) | idxPrd, 0, mSensorBuffer, 0xFF, 0);
            product = new String(mSensorBuffer, 2, rdo - 2, "UTF-16LE");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }

        res = ("Permission: " + Boolean.toString(mUsbManager.hasPermission(mDevice)) + "\n") +
                ("Interface opened successfully.\n") +
                ("Endpoints:" + mInterface.getEndpointCount() + "\n") +
                ("Manufacturer:" + manufacturer + "\n") +
                ("Product:" + product + "\n") +
                ("Serial#:" + mConnection.getSerial() + "\n");
        return res;
    }

    /**
     * Methods to work with byte sent and received.
     * @param value
     * @return
     */
    public static byte[] toByteArray(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value);
        return bytes;
    }

    /**
     * Error: there's a limit on input to byte array size when
     *      sent to ByteBuffer.wrap method (lim = 8);
     *      Only work with this when have byte arr returned from
     *      #toByteArray().
     * @param bytes
     * @param offset
     * @param length
     * @return
     */
    public static double toDouble(byte[] bytes, int offset, int length) {
        //byte[] slice = Arrays.copyOfRange(bytes,offset,offset+length);
        return ByteBuffer.wrap(bytes).getDouble();
    }

    public static byte[] getBufferSlice(byte[] inArray, int offset, int length, int outSize) {
        byte[] outBuff = new byte[outSize];
        for (int i = 0; i < outSize; i++) {
            if (i >= length) {
                outBuff[i] = 0;
            } else {
                outBuff[i] = inArray[i+offset];
            }
        }
        return outBuff;
    }

    /**
     * For raw custom byte array, use a custom method like this.
     * @param high
     * @param low
     * @return
     */
    public static int toInteger(byte high, byte low) {
        return high*256+low;
    }

    public String getASCIIMessage(byte[] buff) {
        try {
            return new String(buff, 0, buff.length, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Assumes every 2 byte in buffer byte array is
     * a double number and there are at most 8 numbers.
     * @param buff (of size at least 16 bytes)
     * @return
     */
    public String getDoublesString(byte[] buff) {
        String msg = "";
        String mark = ", ";
        for (int i = 0; i < 8; i++) {
            if (i == 7) {
                mark = "\n";
            }
            msg += this.toInteger(buff[2*i+1],buff[2*i]) + mark;
        }
        return msg;
    }

    /**
     *
     * @param msg
     * @return
     */
    public String getSensorString(String msg) {
        return "USB_" +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS").format(new Date()) +
                ", " + msg;
    }

    public void toastMsgShort(String msg, Context context) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    /*--------------------------------------------------------------------------------------------*/

    public interface OnUsbConnectionListener {
        void onUsbConnection(boolean connStat);
    }

    public class UsbSensorReceiver extends BroadcastReceiver {
        private static final String TAG = "UsbSensorReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Message recieved, processing...");
            final PendingResult pendingResult = goAsync();
            SensorRecTask asyncTask = new SensorRecTask(pendingResult, intent);
            asyncTask.execute();
        }

        private class SensorRecTask extends AsyncTask<String, Integer, String> {

            private final PendingResult pendingResult;
            private final Intent intent;

            private SensorRecTask(PendingResult pendingResult, Intent intent) {
                this.pendingResult = pendingResult;
                this.intent = intent;
            }

            @Override
            protected String doInBackground(String... strings) {
                Log.d(TAG,"Doing Sensor receive job in background.");
//                StringBuilder sb = new StringBuilder();
//                sb.append("Action: " + intent.getAction() + "\n");
//                sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
//                String log = sb.toString();
                mRecMsg = receiveSensor();
                mSensorString.append(getSensorString(mRecMsg));
                Log.d(TAG, mRecMsg);
                return mRecMsg;
            }

            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                // Must call finish() so the BroadcastReceiver can be recycled.
                if (this.pendingResult != null) {
                    this.pendingResult.finish();
                }
            }
        }
    }

    /**
     * Because we don't want to expose the device
     * and openDevice methods, we use a different receiver in
     * this class than the global availability receiver.
     */
    private class UsbPermissionReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            //call method to set up device communication
                            String res = openDevice(device);
                            if (res != null & res.length() > 0) {
                                //this is only a check so close the connection immediately.
                                //close();
                                setAvailableFlag(true);
                                MyStateManager.setBoolPref(appContext,
                                        KEY_DEFAULT_DEVICE_PERMISSION, getAvailableFlag());
                                mUsbBrManager.sendBroadcast(new Intent(ACTION_USB_AVAILABILITY));
                                mConnListener.onUsbConnection(true);
                            }
                        }
                    } else {
                        setAvailableFlag(false);
                        MyStateManager.setBoolPref(appContext,
                                KEY_DEFAULT_DEVICE_PERMISSION, getAvailableFlag());
                        //toastMsgShort("permission denied for device " + device, appContext);
                        mConnListener.onUsbConnection(false);
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
            context.unregisterReceiver(this);
            mUsbPermissionReceiver = null;
        }
    }
}
