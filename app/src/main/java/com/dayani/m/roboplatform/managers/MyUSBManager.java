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
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageInfo;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgStorage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.UsbCommand;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.MyControlTransferInfo;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyUsbInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MyUSBManager extends MyBaseManager {

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
    public static final String ACTION_USB_AVAILABILITY =
            AppGlobals.PACKAGE_BASE_NAME+".ACTION_USB_AVAILABILITY";
//    public static final String ACTION_SENSOR_RECEIVE =
//            AppGlobals.PACKAGE_BASE_NAME+".USB_SENSOR_RECEIVE";

    //private static final int SENSOR_UPDATE_INTERVAL_MILLIS = 256;

    private static final int SENSOR_ID = 0;

    private int mVendorId;
    private int mDeviceId;

    private BroadcastReceiver mUsbPermissionReceiver = null;
    private final PendingIntent mPermissionIntent;

    private OnUsbConnectionListener mConnListener;

    private final UsbManager mUsbManager;
    private UsbDevice mDevice = null;
    private UsbInterface mInterface = null;
    private UsbDeviceConnection mConnection = null;

    private final byte[] mInputBuffer = new byte[256];

    private boolean mbIsPermitted = false;
    private boolean mbUsbDeviceAvailable = false;

    // detect device detach events
    private BroadcastReceiver mUsbDetachedListener = null;


    private MyUsbInfo mUsbInfo = null;

    /* ====================================== Construction ====================================== */

    /**
     * If we call clean explicitly, init also needs to be
     * called explicitly.
     * @param context Activity's context
     */
    @SuppressLint("UnspecifiedImmutableFlag")
    public MyUSBManager(Context context) {

        super(context);

        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        mPermissionIntent = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_USB_PERMISSION), 0);

        mVendorId = getDefaultVendorId(context);
        mDeviceId = getDefaultDeviceId(context);
    }

    public MyUSBManager(Context context, OnUsbConnectionListener connListener) {

        this(context);
        mConnListener = connListener;
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

        // 3. try open default device
        if (tryOpenDeviceAndUpdateInfo()) {

            // 4. is this a V-USB device?
            if (testDevice()) {
                setUsbAvailability(true);
            }

            // since this is a simple update operation, don't retain the communication
            close();
        }
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
    protected List<String> getPermissions() {
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

    private void registerUsbPermission(Context context) {

        if (mUsbPermissionReceiver == null) {
            mUsbPermissionReceiver = new UsbPermissionReceiver();
        }
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(mUsbPermissionReceiver, filter);
        Log.d(TAG, "USB Permission Broadcast Receiver Registered.");
    }

    private void unregisterUsbPermission(Context context) {

        if (mUsbPermissionReceiver != null) {
            context.unregisterReceiver(mUsbPermissionReceiver);
            mUsbPermissionReceiver = null;
            Log.d(TAG, "USB Permission Broadcast Receiver Unregistered.");
        }
    }

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
    public void init(Context context) {

        super.init(context);

        registerUsbPermission(context);
    }

    @Override
    public void clean(Context context) {

        // close doesn't hurt if USB hasn't already been opened
        close();
        unregisterUsbPermission(context);

        super.clean(context);
    }

    @Override
    public void initConfigurations(Context context) {

        registerUsbDetachedListener(context);
        // open usb connection
        tryOpenDeviceAndUpdateInfo();
    }

    @Override
    public void cleanConfigurations(Context context) {

        // close usb connection
        close();
        unregisterUsbDetachedListener(context);
    }

    @Override
    public void start(Context context) {

        if (!this.isAvailableAndChecked()) {
            Log.w(TAG, "Cameras are not available, abort");
            return;
        }

        super.start(context);
        openStorageChannels();
        startAdcSensorLoop();
    }

    @Override
    public void stop(Context context) {

        if (!this.isAvailableAndChecked() || !this.isProcessing()) {
            Log.d(TAG, "Camera Sensors are not running");
            return;
        }

        // call first to stop the process (isProcessing)
        super.stop(context);
        stopAdcSensorLoop();
        closeStorageChannels();
    }

    private void registerUsbDetachedListener(Context context) {

        if (mUsbDetachedListener == null) {
            mUsbDetachedListener = new UsbDetachReceiver();
        }
        // register usb detached listener
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbDetachedListener, filter);
    }

    private void unregisterUsbDetachedListener(Context context) {

        if (mUsbDetachedListener != null) {
            // unregister usb detached listener
            context.unregisterReceiver(mUsbDetachedListener);
            mUsbDetachedListener = null;
        }
    }

    public void startPeriodicSensorPoll() {

        //tryOpenDefaultDevice();
        //startTime = System.currentTimeMillis();
        //getBgHandler().postDelayed(sensorReceiveTask, 0);
    }

    public void stopPeriodicSensorPoll() {

        //doInBackground(sensorReceiveTask);
        close();
    }

    private void startAdcSensorLoop() {

        // start adc device
        MsgUsb msgUsb = sendDataCommand(UsbCommand.CMD_ADC_START, null);
        byte[] resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
        if (resByte != null && resByte.length > 0 && resByte[0] == 1) {
            doInBackground(new SensorReceiveTask());
        }
    }

    private void stopAdcSensorLoop() {

        MsgUsb msgUsb = sendDataCommand(UsbCommand.CMD_ADC_STOP, null);
        byte[] resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
        if (resByte == null || resByte.length <= 0 || resByte[0] != 1) {
            Log.w(TAG, "stopAdcSensorLoop: USB device couldn't stop the ADC");
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

    @Override
    public void onMessageReceived(MyMessage msg) {

        // todo: send the message to the USB device

        // don't respond if is processing
        if (isProcessing()) {
            return;
        }

        if (msg instanceof MsgUsb) {

            MsgUsb usbMsg = (MsgUsb) msg;

            if (usbMsg.getCmd() == null) {
                usbMsg.setCmd(UsbCommand.CMD_UPDATE_OUTPUT);
            }

            int res = sendControlMsg(usbMsg);
            Log.v(TAG, "Sent "+res+" bytes, cmd: "+usbMsg.getCmd());
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

    public byte[] getRawSensor() {
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
    }

    public void setConnectionListener(OnUsbConnectionListener connListener) {
        mConnListener = connListener;
    }


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

    private MySensorInfo getUsbSensor() {

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
            Log.d(TAG, "USB device closed successfully.");
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
            return -1;
        }

        MyControlTransferInfo ctrlTransInfo = usbMsg.getCtrlTransInfo();
        if (ctrlTransInfo == null) {
            return -1;
        }

        // resolve transfer buffer and its length
        byte[] buff = usbMsg.getRawBuffer();

        try {
            int res = mConnection.controlTransfer(
                    ctrlTransInfo.mCtrlTransDir | ctrlTransInfo.mCtrlTransType,
                    usbMsg.getCmdFlag(),
                    ctrlTransInfo.mCtrlMsgValue,
                    ctrlTransInfo.mCtrlMsgIndex,
                    buff, buff.length,
                    ctrlTransInfo.mCtrlTransTimeout
            );

            usbMsg.setTimestamp(SystemClock.elapsedRealtimeNanos());
            usbMsg.setRawBuffer(buff);

            return res;
        }
        catch(Exception e) {
            e.printStackTrace();
            return -2;
        }
    }

    // two-way command: makes a request and gets a response
    private MsgUsb sendDataCommand(UsbCommand cmd, String cmdData) {

        // send the command
        MsgUsb usbOutMsg = MyDrvUsb.getCommandMessage(cmd, cmdData);

        sendControlMsg(usbOutMsg);
        //Log.d(TAG, "sendDataCommand, sent: "+res+" bytes");

        // get response
        MsgUsb usbInMsg = MyDrvUsb.getInputMessage(UsbCommand.CMD_GET_CMD_RES, mInputBuffer);

        sendControlMsg(usbInMsg);
        //Log.d(TAG, "sendDataCommand, got: "+res+" bytes");

        return usbInMsg;
    }

    private MsgUsb receiveSensor() {

        // reducing a two-way message to just an input message won't help the performance very much
        // note the class header
        MsgUsb usbInMsg = sendDataCommand(UsbCommand.CMD_ADC_READ, null);
        int[] adcReadings = MyDrvUsb.decodeAdcSensorMsg(usbInMsg.getRawBuffer());
        usbInMsg.setAdcData(adcReadings);

        return usbInMsg;
    }

    public boolean testDevice() {

        // send a two way command to query the device's internal code
        MsgUsb usbInMsg = sendDataCommand(UsbCommand.CMD_RUN_TEST, DEFAULT_TEST_IN_MESSAGE);
        //private final byte[] mOutputBuffer = new byte[256];
        String mRecMsg = MyDrvUsb.decodeUsbCommandStr(usbInMsg.getRawBuffer());
        Log.d(TAG, "testDevice, test result: "+ mRecMsg);

        // if response has expected values, return true
        return mRecMsg != null && mRecMsg.equals(DEFAULT_TEST_OUT_MESSAGE);
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

    private void populateCalibrationInfo() {

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

        // get response
        MsgUsb usbInMsg = sendDataCommand(UsbCommand.CMD_GET_SENSOR_INFO, null);
        MyDrvUsb.decodeUsbSensorConfigInfo(usbInMsg);

        mUsbInfo = usbInMsg.getUsbInfo();

        if (mUsbInfo != null) {
            calibInfo.put("ADC_Num_Channels", String.format(Locale.US, "%d", mUsbInfo.numAdcChannels));
            calibInfo.put("ADC_Sample_Rate_Hz", String.format(Locale.US, "%f", mUsbInfo.adcSampleRate));
            calibInfo.put("ADC_Resolution_Bits", String.format(Locale.US, "%d", mUsbInfo.adcResolution));
        }

        sensorInfo.setCalibInfo(calibInfo);
    }

    private void populateSensorInfo() {
        populateDescriptionInfo();
        populateCalibrationInfo();
    }

    public void initiateAdcSingleRead() {

        if (!this.isAvailable()) {
            Log.w(TAG, "Cameras are not available, abort");
            return;
        }

        // start adc device
        MsgUsb msgUsb = sendDataCommand(UsbCommand.CMD_ADC_START, null);
        byte[] resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
        if (resByte == null || resByte.length <= 0 || resByte[0] != 1) {
            Log.d(TAG, "Unable to start ADC device");
            return;
        }

        // publish the message
        MsgUsb usbMsg = receiveSensor();
        publishMessage(usbMsg);

        // close the adc device
        msgUsb = sendDataCommand(UsbCommand.CMD_ADC_STOP, null);
        resByte = MyDrvUsb.decodeUsbCommand(msgUsb.getRawBuffer());
        if (resByte == null || resByte.length <= 0 || resByte[0] != 1) {
            Log.w(TAG, "stopAdcSensorLoop: USB device couldn't stop the ADC");
        }
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

        String res = "";
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

        res =   ("Permission: " + mUsbManager.hasPermission(mDevice) + "\n") +
                ("Interface opened successfully.\n") +
                ("Endpoints:" + mInterface.getEndpointCount() + "\n") +
                ("Manufacturer:" + manufacturer + "\n") +
                ("Product:" + product + "\n") +
                ("Serial#:" + mConnection.getSerial() + "\n");

        return res;
    }

    /* ====================================== Data Types ======================================== */

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

                MsgUsb usbMsg = receiveSensor();

                String sensorStr = usbMsg.getAdcSensorString();
                int targetId = getTargetId(new MyResourceIdentifier(SENSOR_ID, -1));

                MsgStorage storageMsg = new MsgStorage(sensorStr, null, targetId);

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

    public interface OnUsbConnectionListener {
        void onUsbConnection(boolean connStat);
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

                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean permState = false;

                synchronized (this) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        if (device != null) {
                            // all you should do here is to update permission state
                            setIsDevicePermitted(true);
                            Log.d(TAG, "permission granted for device " + device);
                            permState = true;
                        }
                    }
                    else {
                        setIsDevicePermitted(false);
                        Log.d(TAG, "permission denied for device " + device);
                    }

                    if (mConnListener != null) {
                        mConnListener.onUsbConnection(permState);
                    }
                }
            }
        }
    }

    private class UsbDetachReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
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
}
