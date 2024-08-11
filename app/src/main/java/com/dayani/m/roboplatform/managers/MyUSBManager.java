package com.dayani.m.roboplatform.managers;

/*
 * Note3: This is how to debug USB transactions
 * 		(https://developer.android.com/guide/topics/connectivity/usb)
 *
 *		Connect the Android-powered device via USB to your computer.
 *		$ adb tcpip <port>
 *	    Disconnect the cable
 *		$ adb connect <device-ip-address>:<port>
 *
 * Note4: The transfer and processing rate is not enough for high frequency sensor acquisition
 *      TODO: Options:
 *          - Accumulate ADC values in a large buffer and decode USB bulk transfers
 *              (cons: decoding time + timestamp of individual readings??)
 *
 * Note5: Both USB Manager and Driver must be blind to the meaning of commands and data
 *      Currently, this manager plays three different roles:
 *          1. A V-USB Driver
 *          2. A USB Serial Driver
 *          3. A Sensor Device (ADC) -> A separate sub-class now
 *
 * ** Availability:
 *      1. Target device is connected (can be found)
 *      2. Target device is permitted (USB device permission)
 *      3. Target device can be opened (Android app is the host)
 *      3. Target device responds correctly to the test sequence (is a V-USB device)
 * ** Resources:
 *      1. External HandlerThread
 *      2. USB connection
 *      3. Lots of broadcast receivers! -> Leaky!
 *
 * This is robust even when unplug during operation!
 * TODO: More work on preventing resource leaks.
 */

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.requirements.UsbReqFragment;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.helpers.TestCommSpecs;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.MyControlTransferInfo;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.UsbCommand;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageInfo;
import com.felhr.usbserial.CDCSerialDevice;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MyUSBManager extends MyBaseManager implements ActivityRequirements.ConnectivityTest {

    /* ===================================== Variables ========================================== */

    private static final String TAG = MyUSBManager.class.getSimpleName();

    private static final int ANDROID_USB_ATTR_VERSION = Build.VERSION_CODES.M;

    // NOTE: These codes must be the same between this class and USB device to pass the test
    // send to usb device & compare with in_msg stored in device
    private static final String DEFAULT_TEST_IN_MESSAGE = "in-code-9372";
    private static final String DEFAULT_TEST_OUT_MESSAGE = "out-code-6334";

    private static final int DEFAULT_VENDOR_ID = 5824; //V-USB VID //1659; //Omega VID //0x2341; //Arduino VID
    private static final int DEFAULT_DEVICE_ID = 2002; //V-USB led device (avr)

    private static final String KEY_DEFAULT_VENDOR_ID =
            AppGlobals.PACKAGE_BASE_NAME+".KEY_DEFAULT_VENDOR_ID";
    private static final String KEY_DEFAULT_DEVICE_ID =
            AppGlobals.PACKAGE_BASE_NAME+".KEY_DEFAULT_DEVICE_ID";

    private static final String ACTION_USB_PERMISSION =
            AppGlobals.PACKAGE_BASE_NAME+".USB_PERMISSION";
//    public static final String ACTION_USB_AVAILABILITY =
//            AppGlobals.PACKAGE_BASE_NAME+".ACTION_USB_AVAILABILITY";

    protected static final int SENSOR_ID = 0;

    private int mVendorId;
    private int mDeviceId;

    private BroadcastReceiver mUsbPermissionReceiver = null;
    private final PendingIntent mPermissionIntent;

    private final UsbManager mUsbManager;
    private UsbDevice mDevice = null;
    private UsbInterface mInterface = null;
    private UsbDeviceConnection mConnection = null;

    private final byte[] mInputBuffer = new byte[256];

    //private boolean mbIsPermitted = false;
    private boolean mbUsbDeviceAvailable = false;
    private boolean mbPassedConnTest = false;

    // USB Sensor (ADC)
    private final MyUSBSensor mUsbSensor;

    // detect device detach events
    private BroadcastReceiver mUsbDetachedListener = null;

    // Support Arduino Serial Connection
    private boolean mbIsSerial;
    private TinySerialManager mSerialManager;

    /* ====================================== Construction ====================================== */

    /**
     * If we call clean explicitly, init also needs to be
     * called explicitly.
     * @param context Activity's context
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    public MyUSBManager(Context context) {

        super(context);

        mUsbManager = (UsbManager) context.getApplicationContext().getSystemService(Context.USB_SERVICE);

        mPermissionIntent = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_USB_PERMISSION), 0);

        mVendorId = getDefaultVendorId(context);
        mDeviceId = getDefaultDeviceId(context);

        mUsbSensor = new MyUSBSensor();

        mbIsSerial = false;
        mSerialManager = new TinySerialManager(this);
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    @Override
    protected boolean resolveSupport(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    @Override
    protected List<ActivityRequirements.Requirement> getRequirements() {

        return Collections.singletonList(ActivityRequirements.Requirement.USB_DEVICE);
    }

    @Override
    public boolean passedAllRequirements() {
        return isUsbDeviceAvailable();
    }

    @Override
    protected void updateRequirementsState(Context context) {

        List<ActivityRequirements.Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to update");
            return;
        }

        if (requirements.contains(ActivityRequirements.Requirement.USB_DEVICE)) {
            // query for custom USB device in three steps
            updateUsbAvailabilityState();
        }
    }

    public void updateUsbAvailabilityState() {

        // This method must not manage resources (open/close)
        if (mUsbManager == null) {
            return;
        }

        setIsDevicePermitted(false);
        setUsbAvailability(false);

        // 1. device attached?
        mDevice = findDevice(mVendorId, mDeviceId);
        if (mDevice == null) {
            return;
        }

        // 2. is device permitted
        if (!mUsbManager.hasPermission(mDevice)) {
            return;
        }
        setIsDevicePermitted(true);

        // 3. is device connected and Android is host
        if (mConnection == null) {
            return;
        }

        //if (tryOpenDeviceAndUpdateInfo()) {

        // 4. is this a V-USB device? (test is passed)
        handleTest(null);
        if (passedConnectivityTest()) {

            setUsbAvailability(true);

            if (mRequirementResponseListener != null) {
                mRequirementResponseListener.onAvailabilityStateChanged(this);
            }
        }

        // todo: this seems wrong
        // since this is a simple update operation, don't retain the communication
        //close();
        //}
    }

    @Override
    protected void resolveRequirements(Context context) {

        List<ActivityRequirements.Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to resolve");
            return;
        }

        if (requirements.contains(ActivityRequirements.Requirement.USB_DEVICE)) {
            if (mRequirementRequestListener != null) {
                // the fragment should deal with attached state and permission (request permissions)
                mRequirementRequestListener.requestResolution(UsbReqFragment.newInstance());
            }
        }
    }

    @Override
    public List<String> getPermissions() {
        // no permissions required
        return new ArrayList<>();
    }

    public boolean isUsbDeviceAvailable() {
        return mbUsbDeviceAvailable;
    }
    private void setUsbAvailability(boolean state) {
        mbUsbDeviceAvailable = state;
    }

    private void setIsDevicePermitted(boolean state) { mbIsPermitted = state; }
    public boolean isDevicePermitted() { return mbIsPermitted; }

    /**
     * WARNING: This class registers a receiver implicitly.
     *      Need to unregister it (either with receiver itself
     *      or calling clean explicitly) when done.
     * @param mDevice USB device to ask for permission
     */
    public void requestDevicePermission() {

        mDevice = findDevice(mVendorId, mDeviceId);

        if (mUsbManager != null && mDevice != null) {
            //registerUsbPermission(context);
            mUsbManager.requestPermission(mDevice, mPermissionIntent);
        }
    }

    /* -------------------------------- Lifecycle Management ------------------------------------ */

    @Override
    public void execute(Context context, LifeCycleState state) {

        switch (state) {
            case START_RECORDING: {

                // without this you need req. frag. between all tasks!
                tryOpenDeviceAndUpdateInfo();
                updateRequirementsState(context);
                if (this.isNotAvailableAndChecked() || mUsbSensor == null) {
                    Log.w(TAG, "USB is not available, abort");
                    return;
                }

                super.execute(context, state);
                openStorageChannels();
                mUsbSensor.startAdcSensorLoop();
                break;
            }
            case STOP_RECORDING: {

                if (this.isNotAvailableAndChecked() || !this.isProcessing() || mUsbSensor == null) {
                    Log.d(TAG, "Camera Sensors are not running");
                    return;
                }

                // call first to stop the process (isProcessing)
                super.execute(context, state);
                mUsbSensor.stopAdcSensorLoop();
                closeStorageChannels();
                break;
            }
            case RESUMED: {

                super.execute(context, state);
                // open usb connection
                // todo: this has no PAUSED state par
                //tryOpenDeviceAndUpdateInfo();
                break;
            }
            case ACT_DESTROYED: {
                // close doesn't hurt if USB hasn't already been opened
                close();
                super.execute(context, state);
                break;
            }
            case ACT_CREATED: {
                //tryOpenDeviceAndUpdateInfo();
                //updateRequirementsState(context);
                super.execute(context, state);
                break;
            }
            case PAUSED:
            default: {
                super.execute(context, state);
                break;
            }
        }
    }

    @Override
    public void registerBrReceivers(Context context, LifeCycleState state) {

        switch (state) {
            case RESUMED: {

                if (mUsbDetachedListener == null) {
                    mUsbDetachedListener = new UsbDetachReceiver();
                }
                // register usb detached listener
                IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
                context.registerReceiver(mUsbDetachedListener, filter);
                break;
            }
            case PAUSED: {

                if (mUsbDetachedListener != null) {
                    // unregister usb detached listener
                    context.unregisterReceiver(mUsbDetachedListener);
                    mUsbDetachedListener = null;
                }
                break;
            }
            case ACT_CREATED: {

                if (mUsbPermissionReceiver == null) {
                    mUsbPermissionReceiver = new UsbPermissionReceiver(this);
                }
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                context.registerReceiver(mUsbPermissionReceiver, filter);
                Log.d(TAG, "USB Permission Broadcast Receiver Registered.");
                break;
            }
            case ACT_DESTROYED: {

                if (mUsbPermissionReceiver != null) {
                    context.unregisterReceiver(mUsbPermissionReceiver);
                    mUsbPermissionReceiver = null;
                    Log.d(TAG, "USB Permission Broadcast Receiver Unregistered.");
                }
                break;
            }
            default:
                break;
        }
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    protected String getResourceId(MyResourceIdentifier resId) {

        return ((resId.getId() == SENSOR_ID) ? "USB-0" : "Unknown_Sensor");
    }

    @Override
    protected List<Pair<String, MsgConfig>> getStorageConfigMessages(MySensorInfo sensor) {

        int sensorId = sensor.getId();

        List<Pair<String, MsgConfig>> lMsgConfigPairs = new ArrayList<>();

        List<String> usbFolder = Collections.singletonList("usb");

        StorageInfo.StreamType ss = StorageInfo.StreamType.STREAM_STRING;
        MsgConfig.ConfigAction configAction = MsgConfig.ConfigAction.OPEN;

        String header = "# unknown USB message\n";
        StorageInfo storageInfo =
                new StorageInfo(usbFolder, "unknown_sensor.txt", ss);

        if (sensorId == SENSOR_ID) {
            header = "# ADC readings are numbers (0~255) for each channel (ch0, ch1, ...)\n" +
                    "# timestamp, adc_readings\n";
            storageInfo = new StorageInfo(usbFolder, "adc_readings.txt", ss);
        }

        String sensorTag = getResourceId(new MyResourceIdentifier(sensorId, -1));

        MsgConfig config = new StorageConfig(configAction, TAG, storageInfo);
        config.setStringMessage(header);
        lMsgConfigPairs.add(new Pair<>(sensorTag, config));

        return lMsgConfigPairs;
    }

    private void sendUsbMessage(MsgUsb msg) {
        if (msg == null) {
            Log.d(TAG, "sendUsbMessage: null message received");
            return;
        }
        if (mbIsSerial) {
            if (mSerialManager != null) {
                mSerialManager.writeRawBuffer(msg.getRawBuffer());
            }
        }
        else {
            int res = sendControlMsg(msg);
            Log.v(TAG, "Sent "+res+" bytes, cmd: "+msg.getCmd());
        }
    }

    @Override
    public void onMessageReceived(MyMessage msg) {

        // todo: send the message to the USB device

        // don't respond if is processing
        if (isProcessing()) {
            return;
        }

        if (msg instanceof MsgUsb) {
            // convert
            MsgUsb usbMsg = (MsgUsb) msg;
            // send
            sendUsbMessage(usbMsg);
        }
        else if (msg instanceof MyMessages.MsgWireless) {

            // Most of these messages come from the control panel which is
            // agnostic to the type of manager
            Log.w(TAG, "Wireless messages should be interpreted by the controller middleware");
            // convert
            MsgUsb usbMsg = MyDrvUsb.wirelessToUsb((MyMessages.MsgWireless) msg);
            // send
            sendUsbMessage(usbMsg);
        }
    }

    /* ========================================= USB ============================================ */

    /*----------------------------------- Getters & Setters --------------------------------------*/

    private static int getDefaultVendorId(Context context) {
        return MyStateManager.getIntegerPref(context, KEY_DEFAULT_VENDOR_ID, DEFAULT_VENDOR_ID);
    }
    private static void saveDefaultVendorId(Context context, int val) {
        MyStateManager.setIntegerPref(context, KEY_DEFAULT_VENDOR_ID, val);
    }
    public int getVendorId() { return mVendorId; }
    public void setVendorId(int id) { mVendorId = id; }

    private static int getDefaultDeviceId(Context context) {
        return MyStateManager.getIntegerPref(context, KEY_DEFAULT_DEVICE_ID, DEFAULT_DEVICE_ID);
    }
    private static void saveDefaultDeviceId(Context context, int val) {
        MyStateManager.setIntegerPref(context, KEY_DEFAULT_DEVICE_ID, val);
    }
    public int getDeviceId() { return mDeviceId; }
    public void setDeviceId(int id) { mDeviceId = id; }

    public void saveVendorAndDeviceId(Context context) {
        saveDefaultVendorId(context, mVendorId);
        saveDefaultDeviceId(context, mDeviceId);
    }

    /*public byte[] getRawSensor() {
        return mInputBuffer;
    }

    public void setStateBuffer(byte state, int index) {

//        if (index >= mOutputBuffer.length || index < 0) {
//            return;
//        }
//        this.mOutputBuffer[index] = state;
    }

    // publish a usb message instead
    public void setStateBuffer(byte[] buffer) {

//        int minLength = Math.min(buffer.length, mOutputBuffer.length);
//        System.arraycopy(buffer, 0, mOutputBuffer, 0, minLength);
    }*/

    @Override
    public List<MySensorGroup> getSensorGroups(Context mContext) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        ArrayList<MySensorGroup> sensorGroups = new ArrayList<>();
        ArrayList<MySensorInfo> mSensors = new ArrayList<>();

        // add sensors:
        MySensorInfo usbSensor = new MySensorInfo(SENSOR_ID, "V-USB Device");
        usbSensor.setChecked(false);
        mSensors.add(usbSensor);

        sensorGroups.add(new MySensorGroup(MySensorGroup.getNextGlobalId(),
                MySensorGroup.SensorType.TYPE_EXTERNAL, "External (USB)", mSensors));

        return sensorGroups;
    }

    protected MySensorInfo getUsbSensor() {

        if (mlSensorGroup == null) {
            return null;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            if (sensorGroup != null) {

                for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                    if (sensorInfo != null && sensorInfo.getId() == MyUSBManager.SENSOR_ID) {
                        return sensorInfo;
                    }
                }
            }
        }

        return null;
    }


    public boolean canFindTargetDevice() {
        return findDevice(mVendorId, mDeviceId) != null;
    }

    public boolean hasNoConnection() {

        if (mConnection == null) {
            Log.e(TAG, "No USB connection available.");
            return true;
        }
        return false;
    }

    /* ---------------------------------------- Close ------------------------------------------- */

    public void close() {

        if (mConnection != null) {
            mConnection.releaseInterface(mInterface);
            mConnection.close();
            mInterface = null;
            mConnection = null;
            mDevice = null;
            Log.d(TAG, "USB device closed successfully.");
        }
        if (mSerialManager != null) {
            mSerialManager.close();
            mSerialManager = null;
            Log.d(TAG, "USB Serial device closed successfully.");
        }
    }

    /* ---------------------------------------- Open -------------------------------------------- */

    public List<UsbDevice> enumerateDevices() {

        Map<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

        if (deviceList != null) {
            return new ArrayList<>(deviceList.values());
        }
        else {
            return null;
        }
    }

    private UsbDevice findDevice(int vendorID, int deviceID) {

        if (vendorID <= 0 || deviceID <= 0) {

            Log.e(TAG, "Error in device and vendor IDs.");
            return null;
        }

        //Find the device
        Map<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

        // In case you know device name:
        //UsbDevice device = deviceList.get("deviceName");
        // else:
        for (UsbDevice device : deviceList.values()) {

            if (device.getDeviceId() == deviceID && device.getVendorId() == vendorID) {
                return device;
            }
        }

        Log.e(TAG, "No device found with: "+vendorID+':'+deviceID);
        return null;
    }

    private boolean openDevice(UsbDevice mDevice) {

        if (mUsbManager == null || mDevice == null) {
            Log.e(TAG, "Usb manager or input device is null reference.");
            return false;
        }

        // is device permitted?
        if (!mUsbManager.hasPermission(mDevice)) {
            Log.w(TAG, "Target device is not permitted");
            return false;
        }

        if (mConnection != null) {
            Log.i(TAG, "Device is already open");
            return true;
        }

        //Open Interface to the device
        mInterface = mDevice.getInterface(0);
        if (mInterface == null) {
            Log.e(TAG, "Unable to establish an interface.");
            return false;
        }

        int epc = mInterface.getEndpointCount();
        Log.i(TAG, "USB interface endpoint count: "+epc);

        //Open a connection
        mConnection = mUsbManager.openDevice(mDevice);
        if (null == mConnection) {
            Log.e(TAG, "Unable to establish a connection!");
            return false;
        }

        //Communicate over the connection

        // Claims exclusive access to a UsbInterface.
        // This must be done before sending or receiving data on
        // any UsbEndpoints belonging to the interface.
        mConnection.claimInterface(mInterface, true);

        // Arduino devices usually have null product name
        // todo: find a better way for this check
        String prodName = mDevice.getProductName();
        Log.i(TAG, "openDevice, product name: " + prodName);
        mbIsSerial = prodName == null || prodName.equals("USB Serial");
        if (mbIsSerial) {
            if (mSerialManager == null) {
                mSerialManager = new TinySerialManager(this);
            }
            mSerialManager.connect();
        }

        return true;
    }

    public boolean tryOpenDeviceAndUpdateInfo() {

        //maybe first close last connection
        close();

        mDevice = findDevice(mVendorId, mDeviceId);

        if (openDevice(mDevice)) {

            populateSensorInfo();
            return true;
        }
        return false;
    }

    /* -------------------------------------- Messaging ----------------------------------------- */

    public int sendControlMsg(MsgUsb usbMsg) {

        if (hasNoConnection() || usbMsg == null) {
            Log.d(TAG, "sendControlMsg: No connection or null USB message detected");
            return -1;
        }

        MyControlTransferInfo ctrlTransInfo = usbMsg.getCtrlTransInfo();
        if (ctrlTransInfo == null) {
            Log.d(TAG, "sendControlMsg: ctrlTransInfo is null");
            return -1;
        }

        // resolve transfer buffer and its length
        byte[] buff = usbMsg.getRawBuffer();
//        if (buff == null) {
//            Log.d(TAG, "sendControlMsg: input buffer is null");
//            return -1;
//        }

        try {
            Log.d(TAG, "sendControlMsg, buff size: " + buff.length);
            int res = mConnection.controlTransfer(
                    ctrlTransInfo.mCtrlTransDir | ctrlTransInfo.mCtrlTransType,
                    usbMsg.getCmdFlag(),
                    ctrlTransInfo.mCtrlMsgValue,
                    ctrlTransInfo.mCtrlMsgIndex,
                    buff, buff.length,
                    ctrlTransInfo.mCtrlTransTimeout
            );
            debug_cnt++;

            usbMsg.setTimestamp(SystemClock.elapsedRealtimeNanos());
            usbMsg.setRawBuffer(buff);

            return res;
        }
        catch(Exception e) {
            e.printStackTrace();
            return -2;
        }
    }

    int debug_cnt = 0;

    // two-way command: makes a request and gets a response
    protected MsgUsb sendDataCommand(UsbCommand cmd, String cmdData) {

        debug_cnt = 0;
        // send the command
        MsgUsb usbOutMsg = MyDrvUsb.getCommandMessage(cmd, cmdData);

        int res = sendControlMsg(usbOutMsg);
        //Log.d(TAG, "sendDataCommand, sent: "+res+" bytes");

        // get response
        MsgUsb usbInMsg = MyDrvUsb.getInputMessage(UsbCommand.CMD_GET_CMD_RES, mInputBuffer);

//        if (res >= 0) {
        sendControlMsg(usbInMsg);
        //Log.d(TAG, "sendDataCommand, got: "+res+" bytes");
//        }
//        else {
//            Log.d(TAG, "sendDataCommand, couldn't send message");
//        }

        return usbInMsg;
    }

    public void handleTest(MyMessage msg) {
        if (mbIsSerial) {
            handleTestAsynchronous(msg);
        }
        else {
            handleTestSynchronous(msg);
        }
        //mbPassedConnTest = true;
    }

    @Override
    public void handleTestSynchronous(MyMessage msg) {

        // we don't use the msg here
        String mRecMsg = null;
        if (!mbIsSerial) {
            // V-USB device
            // send a two-way command to query the device's internal code
            MsgUsb usbInMsg = sendDataCommand(UsbCommand.CMD_RUN_TEST, DEFAULT_TEST_IN_MESSAGE);
            mRecMsg = MyDrvUsb.decodeUsbCommandStr(usbInMsg.getRawBuffer());
            Log.d(TAG, "testDevice, test result: " + mRecMsg);
        }

        // if response has expected values, return true
        if (mRecMsg != null && !mRecMsg.isEmpty()) {
            mbPassedConnTest = mRecMsg.equals(DEFAULT_TEST_OUT_MESSAGE);
            if (mbPassedConnTest) {
                Log.d(TAG, "Serial Test Successful");
            }
        }
    }

    @Override
    public void handleTestAsynchronous(MyMessage msg) {
        if (mbIsSerial) {
            // Arduino Device
            // This is asynchronous in nature
            // You should use: 1. a br, 2. and interface or handler, or 3. msg passing
            if (mbPassedConnTest) {
                // nothing to do
                Log.d(TAG, "Serial Test Successful");
                return;
            }
            if (mSerialManager == null || !mSerialManager.isConnected()) {
                Log.w(TAG, "Serial manager is not connected");
                return;
            }
            mSerialManager.write(DEFAULT_TEST_IN_MESSAGE);
            // You'll receive a response in a callback
        }
    }

    @Override
    public boolean passedConnectivityTest() {
        return mbPassedConnTest;
    }

    public void initiateAdcSingleRead() {

        if (!this.isAvailable() || mUsbSensor == null) {
            Log.w(TAG, "USB is not available, abort");
            return;
        }
        mUsbSensor.initiateAdcSingleRead();
    }

    public String sendMsgSync(String msg) {

        String mSerialData = "";
        if (mbIsSerial && mSerialManager != null) {
            int res = mSerialManager.writeSync(msg);
            if (res >= 0) {
                res = mSerialManager.readSync();
                if (res >= 0) {
                    mSerialData = new String(mSerialManager.mSerialBuffer, StandardCharsets.UTF_8);
                }
                else {
                    Log.d(TAG, "sendMsgSync: readSync was not successful");
                }
            }
            else {
                Log.d(TAG, "sendMsgSync: writeSync was not successful");
            }
        }
        return mSerialData;
    }

    public String sendMsgAsync(String msg) {

        if (mbIsSerial && mSerialManager != null) {
            mSerialManager.write(msg);
            return mSerialManager.mSerialData;
        }
        return null;
    }

    /* ================================ Helper Classes & methods ================================ */

    private static String getDeviceSimpleReport(UsbDevice device) {
        return device.getProductName()+", "+device.getVendorId()+':'+device.getDeviceId()+'\n';
    }

    public static String usbDeviceListToString(List<UsbDevice> lUsbDevices) {

        if (lUsbDevices == null) {
            return "";
        }

        StringBuilder res = new StringBuilder();

        for (UsbDevice device : lUsbDevices) {
            res.append(getDeviceSimpleReport(device));
        }

        return res.toString();
    }

    public String getDeviceReport() {

        if (mDevice == null) {
            return "No USB device detected";
        }

        return  ("VendorID:" + mDevice.getVendorId() + "\n") +
                ("DeviceID:" + mDevice.getDeviceId() + "\n") +
                ("Product:" + mDevice.getProductId() + "\n") +
                ("Class:" + mDevice.getDeviceClass() + "\n") +
                ("Subclass:" + mDevice.getDeviceSubclass() + "\n") +
                ("Protocol:" + mDevice.getDeviceProtocol() + "\n") +
                ("------------------------------------\n");
    }

    public String getConnectionReport() {

        if (hasNoConnection()) {
            Log.e(TAG, "getConnectionReport: no connection.");
            return null;
        }

        // getRawDescriptors can be used to access descriptors
        // not supported directly via the higher level APIs,
        // like getting the manufacturer and product names.
        // because it returns bytes, you can get a variety of
        // different data types.
        byte[] rawDescriptors = mConnection.getRawDescriptors();
        int idxMan = rawDescriptors[14];
        int idxPrd = rawDescriptors[15];

        MsgUsb manufacturerMsg = MyDrvUsb.getUsbDescriptorQueryMessage(mInputBuffer, idxMan, 0, 0);
        sendControlMsg(manufacturerMsg);
        String manufacturer = MyDrvUsb.decodeUsbDescriptorInfo(manufacturerMsg);

        MsgUsb productMsg = MyDrvUsb.getUsbDescriptorQueryMessage(mInputBuffer, idxPrd, 0, 0);
        sendControlMsg(productMsg);
        String product = MyDrvUsb.decodeUsbDescriptorInfo(productMsg);

        return ("Permission: " + mUsbManager.hasPermission(mDevice) + "\n") +
                ("Interface opened successfully.\n") +
                ("Endpoints:" + mInterface.getEndpointCount() + "\n") +
                ("Manufacturer:" + manufacturer + "\n") +
                ("Product:" + product + "\n") +
                ("Serial#:" + mConnection.getSerial() + "\n");
    }

    private void populateDescriptionInfo() {

        if (mDevice == null) {
            return;
        }

        MySensorInfo sensorInfo = getUsbSensor();
        if (sensorInfo == null) {
            return;
        }

        Map<String, String> descInfo = new HashMap<>();
        descInfo.put("Vendor_Id", String.format(Locale.US, "%d", mDevice.getVendorId()));
        descInfo.put("Device_Id", String.format(Locale.US, "%d", mDevice.getDeviceId()));
        descInfo.put("Device_Class", String.format(Locale.US, "%d", mDevice.getDeviceClass()));
        descInfo.put("Device_Subclass", String.format(Locale.US, "%d", mDevice.getDeviceSubclass()));
        descInfo.put("Device_Protocol", String.format(Locale.US, "%d", mDevice.getDeviceProtocol()));
        descInfo.put("Device_Name", mDevice.getDeviceName());
        descInfo.put("Product", mDevice.getProductName());
        descInfo.put("Manufacturer", mDevice.getManufacturerName());
        descInfo.put("Serial_Number", mDevice.getSerialNumber());

        if (Build.VERSION.SDK_INT >= ANDROID_USB_ATTR_VERSION) {
            descInfo.put("Version", mDevice.getVersion());
        }

        sensorInfo.setDescInfo(descInfo);
    }

    protected void populateCalibrationInfo() {

        if (hasNoConnection() || mlSensorGroup == null || mlSensorGroup.isEmpty()) {
            return;
        }

        MySensorInfo sensorInfo = getUsbSensor();
        if (sensorInfo == null) {
            return;
        }

        Map<String, String> calibInfo = new HashMap<>();
        calibInfo.put("App_Id", String.format(Locale.US, "%d", SENSOR_ID));
        calibInfo.put("Vendor_Id", String.format(Locale.US, "%d", mVendorId));
        calibInfo.put("Device_Id", String.format(Locale.US, "%d", mDeviceId));

        sensorInfo.setCalibInfo(calibInfo);

        if (mUsbSensor != null) {
            mUsbSensor.populateCalibrationInfo();
        }
    }

    private void populateSensorInfo() {
        populateDescriptionInfo();
        populateCalibrationInfo();
    }

    /* ====================================== Data Types ======================================== */

    /**
     * Because we don't want to expose the device
     * and openDevice methods, we use a different receiver in
     * this class than the global availability receiver.
     */
    private class UsbPermissionReceiver extends BroadcastReceiver {

        private final MyBaseManager mManager;

        public UsbPermissionReceiver(MyBaseManager manager) {

            mManager = manager;
        }

        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {

                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                //boolean permState = false;

                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        if (device != null) {
                            // all you should do here is to update permission state
                            setIsDevicePermitted(true);
                            Log.d(TAG, "permission granted for device " + device);
                            //permState = true;
                        }
                    }
                    else {
                        setIsDevicePermitted(false);
                        Log.d(TAG, "permission denied for device " + device);
                    }

                    if (mRequirementResponseListener != null) {
                        mRequirementResponseListener.onAvailabilityStateChanged(mManager);
                    }
                }
            }
        }
    }

    private class UsbDetachReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // todo: stop any ongoing action (sensor recording)

                    // close the communication
                    close();
                    // update the state
                    updateRequirementsState(context);
                }
            }
        }
    }

    private class TinySerialManager {

        private final String TAG = TinySerialManager.class.getSimpleName();
        //public static final int MESSAGE_FROM_SERIAL_PORT = 0;
        //public static final int CTS_CHANGE = 1;
        //public static final int DSR_CHANGE = 2;
        private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need

        // Support Arduino Serial Connection
        private String mSerialData = "";
        private final byte[] mSerialBuffer = new byte[64];
        private int mSerialBufferIdx = 0;
        private int mSerialDataSz = 0;
        //private Handler mHandler;
        private UsbSerialDevice serialPort;
        private boolean serialPortConnected;
        private final WeakReference<MyBaseManager> mUsbManager;

        private void handleSerialMessage(byte[] arg0) {
            int buffLen = arg0.length;
            if (buffLen > 0) {
                if (mSerialBufferIdx == 0) {
                    mSerialDataSz = arg0[0];
                    mSerialBuffer[mSerialBufferIdx++] = arg0[0];
                    buffLen--;
                    Log.d("UsbCb.onReceivedData", "data size is " + mSerialDataSz);
                    byte[] newArg0 = new byte[buffLen];
                    System.arraycopy(arg0, 1, newArg0, 0, buffLen);
                    handleSerialMessage(newArg0);
                    return;
                }
                if (mSerialBufferIdx == 1) {
                    // get command
                    mSerialBuffer[mSerialBufferIdx++] = arg0[0];
                    buffLen--;
                    Log.d("UsbCb.onReceivedData", "Buffer Idx is " + mSerialBufferIdx);
                    byte[] newArg0 = new byte[buffLen];
                    System.arraycopy(arg0, 1, newArg0, 0, buffLen);
                    handleSerialMessage(newArg0);
                    return;
                }
                if (mSerialBufferIdx >= 2) {
                    for (int i = 0; i < buffLen && mSerialBufferIdx < mSerialDataSz + 2; i++, mSerialBufferIdx++) {
                        mSerialBuffer[mSerialBufferIdx] = arg0[i];
//                    if (mSerialBufferIdx >= mSerialDataSz + 2) {
//                        mSerialBufferIdx = 0;
//                        mSerialDataSz = 0;
//                        byte[] newArg0 = new byte[mSerialBuffer[0]];
//                        System.arraycopy(mSerialBuffer, 2, newArg0, 0, mSerialDataSz);
//                        mSerialData = new String(newArg0, StandardCharsets.UTF_8);
//                        Log.d("UsbCb.onReceivedData", "reconst data: " + Arrays.toString(newArg0));
//                        break;
//                    }
                    }
                }
                if (mSerialBufferIdx >= mSerialDataSz + 2) {

                    byte[] newArg0 = new byte[mSerialDataSz];
                    System.arraycopy(mSerialBuffer, 2, newArg0, 0, mSerialDataSz);
                    mSerialData = new String(newArg0, StandardCharsets.UTF_8);
                    Log.d("UsbCb.onReceivedData", "reconst data: " + Arrays.toString(newArg0));
                    Log.d("UsbCb.onReceivedData", "out message: " + mSerialData);

                    mSerialBufferIdx = 0;
                    mSerialDataSz = 0;
                    // todo: update parent availability state in this (in case of test command)
                }
            }
            else {
                Log.d("UsbCb.onReceivedData", "arg0 is empty");
            }
        }

        private void handleSerialMessageOrig(byte[] arg0) {

            if (arg0 == null || arg0.length == 0) {
                return;
            }

            mSerialData = MyDrvUsb.decodeUsbCommandStr(arg0);
            if (mSerialData.isEmpty()) {
                return;
            }

            if (mSerialData.equals(DEFAULT_TEST_OUT_MESSAGE)) {
                mbPassedConnTest = true;
                //handleTest(null);
                updateUsbAvailabilityState();
                if (mRequirementResponseListener != null)
                    mRequirementResponseListener.onAvailabilityStateChanged(mUsbManager.get());
            }
            else {
                MyMessage msg = new MyMessage(MyChannels.ChannelType.DATA,
                        "usb-response", mSerialData);
                publishMessage(msg);
            }

            mSerialData = "";
        }

        /*
         *  Data received from serial port will be received here. Just populate onReceivedData with your code
         *  In this particular example. byte stream is converted to String and send to UI thread to
         *  be treated there.
         */
        private final UsbSerialInterface.UsbReadCallback mCallback = arg0 -> {
            Log.d(TAG, "UsbSerialInterface.UsbReadCallback: data received: " + Arrays.toString(arg0));
            if (arg0 == null) {
                Log.d(TAG, "UsbSerialInterface.UsbReadCallback: arg0 is null");
                return;
            }
            handleSerialMessageOrig(arg0);
//                if (mHandler != null && mSerialData != null) {
//                    Log.d("UsbCb.onReceivedData", "sending data to handler");
//                    mHandler.obtainMessage(MESSAGE_FROM_SERIAL_PORT, mSerialData).sendToTarget();
//                }
        };

        /*
         * State changes in the CTS line will be received here
         */
        private final UsbSerialInterface.UsbCTSCallback ctsCallback = state -> {
//                if(mHandler != null)
//                    mHandler.obtainMessage(CTS_CHANGE).sendToTarget();
        };

        /*
         * State changes in the DSR line will be received here
         */
        private final UsbSerialInterface.UsbDSRCallback dsrCallback = state -> {
//                if(mHandler != null)
//                    mHandler.obtainMessage(DSR_CHANGE).sendToTarget();
        };

        public TinySerialManager(MyBaseManager mUsbManager_) {

            serialPortConnected = false;
            serialPort = null;
            mUsbManager = new WeakReference<>(mUsbManager_);
        }

        public void connect() {
            new ConnectionThread().start();
        }

        public void close() {
            if (serialPort != null) {
                serialPort.close();
                Log.d(TAG, "Serial port closed successfully.");
            }
        }

        public String runTest() {
            String mRecMsg = "";
            Log.d(TAG, "testDevice, message is empty");
            if (serialPort != null) {
                //serialPort.write(MyDrvUsb.encodeUsbCommand(DEFAULT_TEST_IN_MESSAGE));
                write(DEFAULT_TEST_IN_MESSAGE);
                Log.d(TAG, "TinySerialManager:runTest: sent " + DEFAULT_TEST_IN_MESSAGE);
//                if (mSerialData!=null && !mSerialData.isEmpty()) {
//                    mRecMsg = mSerialData;
//                    mbIsSerial = true;
//                    Log.d(TAG, "testDevice, received data: " + mSerialData);
//                }
//                else {
//                    Log.d(TAG, "testDevice, serial data is empty");
//                }
                return mSerialData;
            }
            else {
                Log.d(TAG, "testDevice, serial port is null");
            }
            return mRecMsg;
        }

        private byte[] encodeStringMsg(String msg) {
            msg += "\n";
            return msg.getBytes(StandardCharsets.US_ASCII);
        }

        public void write(String msg) {
            if (serialPort != null) {
                byte[] outbytes = MyDrvUsb.encodeUsbCommand(msg); //encodeStringMsg(msg)
                serialPort.write(outbytes);
            }
        }

        public void writeRawBuffer(byte[] msg) {
            if (serialPort != null) {
                serialPort.write(msg);
            }
        }

        public int writeSync(String msg) {
            if (serialPort != null) {
                return serialPort.syncWrite(encodeStringMsg(msg), 1000);
            }
            return -1;
        }

        public int readSync() {
            if (serialPort != null) {
                return serialPort.syncRead(mSerialBuffer, 1000);
            }
            return -1;
        }

        public boolean isConnected() { return serialPortConnected; }

        /*public void setHandler(Handler mHandler) {
            this.mHandler = mHandler;
        }*/

        private class ConnectionThread extends Thread {
            @Override
            public void run() {
                if (mDevice == null || mConnection == null) {
                    Log.e(TAG, "TinySerialManager:ConnectionThread: Connection is null");
                    return;
                }
                Log.d(TAG, "creating serial connection");
                serialPort = UsbSerialDevice.createUsbSerialDevice(mDevice, mConnection);
                if (serialPort != null) {
                    Log.d(TAG, "created serial port");
                    if (serialPort.open()) {
                        Log.d(TAG, "opened serial port");
                        serialPortConnected = true;
                        serialPort.setBaudRate(BAUD_RATE);
                        serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                        serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                        serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                        /*
                         * Current flow control Options:
                         * UsbSerialInterface.FLOW_CONTROL_OFF
                         * UsbSerialInterface.FLOW_CONTROL_RTS_CTS only for CP2102 and FT232
                         * UsbSerialInterface.FLOW_CONTROL_DSR_DTR only for CP2102 and FT232
                         */
                        serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                        serialPort.read(mCallback);
                        serialPort.getCTS(ctsCallback);
                        serialPort.getDSR(dsrCallback);

                        //
                        // Some Arduinos would need some sleep because firmware wait some time to know whether a new sketch is going
                        // to be uploaded or not
                        //Thread.sleep(2000); // sleep some. YMMV with different chips.

                        // Everything went as expected. Send an intent to MainActivity
                        //Intent intent = new Intent(ACTION_USB_READY);
                        //context.sendBroadcast(intent);
                    }
                    else {
                        // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                        // Send an Intent to Main Activity
                        if (serialPort instanceof CDCSerialDevice) {
                            //Intent intent = new Intent(ACTION_CDC_DRIVER_NOT_WORKING);
                            //context.sendBroadcast(intent);
                            Log.d(TAG, "UsbSerial:ConnectionThread, ACTION_CDC_DRIVER_NOT_WORKING");
                        }
                        else {
                            //Intent intent = new Intent(ACTION_USB_DEVICE_NOT_WORKING);
                            //context.sendBroadcast(intent);
                            Log.d(TAG, "UsbSerial:ConnectionThread, ACTION_USB_DEVICE_NOT_WORKING");
                        }
                    }
                }
                else {
                    // No driver for given device, even generic CDC driver could not be loaded
                    //Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
                    //context.sendBroadcast(intent);
                    Log.d(TAG, "UsbSerial:ConnectionThread, ACTION_USB_NOT_SUPPORTED");
                }
            }
        }
    }

    private MyMessages.MyUsbInfo mUsbInfo = null;

    private class MyUSBSensor {

        private final String TAG = MyUSBSensor.class.getSimpleName();

        //private MyMessages.MyUsbInfo mUsbInfo = null;

        public MyUSBSensor() {
        }

        /*public void startPeriodicSensorPoll() {

            //tryOpenDefaultDevice();
            //startTime = System.currentTimeMillis();
            //getBgHandler().postDelayed(sensorReceiveTask, 0);
        }

        public void stopPeriodicSensorPoll() {

            //doInBackground(sensorReceiveTask);
            close();
        }*/

        private void startAdcSensorLoop() {

            // start adc device
            MyMessages.MsgUsb msgUsb = sendDataCommand(MyMessages.MsgUsb.UsbCommand.CMD_ADC_START, null);
            byte[] resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
            if (resByte != null && resByte.length > 0 && resByte[0] == 1) {
                doInBackground(new SensorReceiveTask());
            }
        }

        private void stopAdcSensorLoop() {

            MyMessages.MsgUsb msgUsb = sendDataCommand(MyMessages.MsgUsb.UsbCommand.CMD_ADC_STOP, null);
            byte[] resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
            if (resByte == null || resByte.length <= 0 || resByte[0] != 1) {
                Log.w(TAG, "stopAdcSensorLoop: USB device couldn't stop the ADC");
            }
        }

        private MyMessages.MsgUsb receiveSensor() {

            // reducing a two-way message to just an input message won't help the performance very much
            // note the class header
            MyMessages.MsgUsb usbInMsg = sendDataCommand(MyMessages.MsgUsb.UsbCommand.CMD_ADC_READ, null);
            int[] adcReadings = MyDrvUsb.decodeAdcSensorMsg(usbInMsg.getRawBuffer());
            usbInMsg.setAdcData(adcReadings);

            return usbInMsg;
        }

        public void initiateAdcSingleRead() {

            // start adc device
            MyMessages.MsgUsb msgUsb = sendDataCommand(MyMessages.MsgUsb.UsbCommand.CMD_ADC_START, null);
            byte[] resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
            if (resByte == null || resByte.length <= 0 || resByte[0] != 1) {
                Log.d(TAG, "Unable to start ADC device");
                return;
            }

            // publish the message
            MyMessages.MsgUsb usbMsg = receiveSensor();
            publishMessage(usbMsg);

            // close the adc device
            msgUsb = sendDataCommand(MyMessages.MsgUsb.UsbCommand.CMD_ADC_STOP, null);
            resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
            if (resByte == null || resByte.length <= 0 || resByte[0] != 1) {
                Log.w(TAG, "stopAdcSensorLoop: USB device couldn't stop the ADC");
            }
        }

        protected void populateCalibrationInfo() {
            //super.populateCalibrationInfo();

            if (hasNoConnection() || mlSensorGroup == null || mlSensorGroup.isEmpty()) {
                return;
            }

            MySensorInfo sensorInfo = getUsbSensor();
            if (sensorInfo == null) {
                return;
            }

            Map<String, String> calibInfo = new HashMap<>();

            // get response
            MyMessages.MsgUsb usbInMsg = sendDataCommand(MyMessages.MsgUsb.UsbCommand.CMD_GET_SENSOR_INFO, "");
            MyDrvUsb.decodeUsbSensorConfigInfo(usbInMsg);

            mUsbInfo = usbInMsg.getUsbInfo();

            if (mUsbInfo != null) {
                calibInfo.put("ADC_Num_Channels", String.format(Locale.US, "%d", mUsbInfo.numAdcChannels));
                calibInfo.put("ADC_Sample_Rate_Hz", String.format(Locale.US, "%f", mUsbInfo.adcSampleRate));
                calibInfo.put("ADC_Resolution_Bits", String.format(Locale.US, "%d", mUsbInfo.adcResolution));
            }

            sensorInfo.setCalibInfo(calibInfo);
        }

        private class SensorReceiveTask implements Runnable {

            @Override
            public void run() {

                double samplePeriod_ns = 15000000; // 15 ms
                if (mUsbInfo != null) {
                    samplePeriod_ns = 1e9 / mUsbInfo.adcSampleRate;
                }
                Log.v(TAG, "ADC readings period is: "+samplePeriod_ns);

                while (isProcessing()) {

                    //long startTime = SystemClock.elapsedRealtimeNanos();

                    MyMessages.MsgUsb usbMsg = receiveSensor();

                    String sensorStr = usbMsg.getAdcSensorString();
                    int targetId = getTargetId(new MyResourceIdentifier(SENSOR_ID, -1));

                    MyMessages.MsgStorage storageMsg = new MyMessages.MsgStorage(sensorStr, null, targetId);

                    publishMessage(storageMsg);

                    //Log.v(TAG, "got msg: "+sensorStr);

                    //long endTime = SystemClock.elapsedRealtimeNanos();
                    //long timeDiff = endTime - startTime;
                    //Log.v(TAG, "Took "+timeDiff+" ns to complete one iteration");

                    // sleep until next adc reading is available? ->
                    // not necessary, already too slow!
                }
            }
        }
    }

    private class MyUsbCommTest extends TestCommSpecs {

        @Override
        public void receiveAsync() {

            if (!mbIsSerial) {
                return;
            }

            if (mTestMode == TestMode.LATENCY) {
                Integer recLatencyCode = 0;
                long lastTs = mTsMap.get(recLatencyCode);
                long currTs = SystemClock.elapsedRealtimeNanos();
                long tsDiff = currTs - lastTs;
                mAvgLatency += tsDiff;
            }
            else if (mTestMode == TestMode.THROUGHPUT) {
                int recNumBytes = 0;
                mnBytes += recNumBytes;
            }
        }

        @Override
        public void receiveSync() {

            if (mbIsSerial) {
                return;
            }

            MsgUsb usbInMsg = sendDataCommand(UsbCommand.CMD_RUN_TEST, DEFAULT_TEST_IN_MESSAGE);
        }

        @Override
        public void send(MyMessage mgs) {

        }
    }
}
