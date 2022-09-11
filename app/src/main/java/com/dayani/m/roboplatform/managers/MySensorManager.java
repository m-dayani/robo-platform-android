package com.dayani.m.roboplatform.managers;

/*
 * ** Availability:
 *      1. For full operation: Accel, Magnet, even Gyro sensors
 *      2. For just record: At least Accel
 * ** Resources:
 *      1. Internal HandlerThread
 *      2. Sensors (registered callbacks)
 * ** State Management:
 *      1. isAvailable (availability)
 *      2. isAccelAvailable
 *      3. +Other sensors
 *
 * 4 Stages of sensor processing:
 *      1. Init (check availability & getting sensors)
 *      2. Start (running sensor capture)
 *      3. process the result
 *      4. Stop (unregister listeners)
 *
 * TODO: Maybe add barometer recording
 */

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MySensorManager extends MyBaseManager {

    /* ===================================== Variables ========================================== */

    private static final String TAG = MySensorManager.class.getSimpleName();

    private static final int MAX_SENSOR_READ_INTERVAL = SensorManager.SENSOR_DELAY_FASTEST;

    public static final int ANDROID_VERSION_UNCALIB_SENSORS = Build.VERSION_CODES.O;
    public static final int ANDROID_VERSION_ACQ_MODE = Build.VERSION_CODES.N;

    private static final List<Integer> mCalibratedTypes = initCalibratedTypes();
    private static final List<Integer> mUncalibratedTypes = initUncalibratedTypes();
    private static final List<Integer> mAllSensorTypes = initAllSensorTypes();


    private static int currSensorId = 0;

    private final SensorManager mSensorManager;

    private SensorEvent mSensorEvent;

    private final SensorEventListener mSensorCallback = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {
            // Do something here if sensor accuracy changes.
        }

        @Override
        public void onSensorChanged(SensorEvent event) {

            // The light sensor returns a single value.
            // Many sensors return 3 values, one for each axis.
            mSensorEvent = event;
            doInBackground(sensorReceiveTask);
        }
    };

    private final Runnable sensorReceiveTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG,"Processing Sensor receive job in background.");
            handleEvent(mSensorEvent);
        }

        private void handleEvent(SensorEvent event) {

            MyResourceIdentifier rId = new MyResourceIdentifier(event.sensor.getType(), -1);
            int targetId = getTargetId(rId);

            MyMessage sensorMsg = new MyMessages.MsgSensor(event, targetId);
            publishMessage(sensorMsg);
        }
    };

    /* ==================================== Construction ======================================== */

    public MySensorManager(Context context) {

        super(context);
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        //init(context);
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    /**
     * This manager is supported if device has at least one sensor module
     * @param context activity context
     * @return boolean
     */
    @Override
    protected boolean resolveSupport(Context context) {

        SensorManager sensorManager = getSensorManager(context);

        int countSupported = 0;

        for (Integer sensorTypeCode: mAllSensorTypes) {

            if (sensorManager.getDefaultSensor(sensorTypeCode) != null) {
                countSupported++;
            }
        }

        return countSupported > 0;
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    @Override
    public List<Requirement> getRequirements() {
        return new ArrayList<>();
    }

    @Override
    protected void updateRequirementsState(Context context) {
        updatePermissionsState(context);
    }

    @Override
    public List<String> getPermissions() {
        return new ArrayList<>();
    }

    @Override
    public void updatePermissionsState(Context context) {
        mbIsPermitted = true;
    }

    /*------------------------------------- Setters/Getters --------------------------------------*/

    private SensorManager getSensorManager(Context context) {

        if (mSensorManager != null) {
            return mSensorManager;
        }
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    /*------------------------------------- Init. Sensors ----------------------------------------*/

    private static List<Integer> initCalibratedTypes() {
        return Arrays.asList(
                android.hardware.Sensor.TYPE_ACCELEROMETER,
                android.hardware.Sensor.TYPE_GYROSCOPE,
                android.hardware.Sensor.TYPE_MAGNETIC_FIELD
        );
    }

    private static List<Integer> initUncalibratedTypes() {

        List<Integer> lTypes = new ArrayList<>();

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {

            lTypes = Arrays.asList(
                    android.hardware.Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                    android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                    android.hardware.Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
            );
        }

        return lTypes;
    }

    private static List<Integer> initAllSensorTypes() {

        List<Integer> lTypes = new ArrayList<>();

        lTypes.addAll(mCalibratedTypes);
        lTypes.addAll(mUncalibratedTypes);

        return lTypes;
    }

    private static void getSensorsInfo(SensorManager sensorManager, int sensorTypeCode, List<MySensorInfo> lSensorInfo) {

        if (sensorManager == null || lSensorInfo == null) {
            return;
        }

        android.hardware.Sensor defSensor = sensorManager.getDefaultSensor(sensorTypeCode);

        if (defSensor != null) {

            String defSensorName = defSensor.getName();
            List<android.hardware.Sensor> allSensors = sensorManager.getSensorList(sensorTypeCode);

            for (android.hardware.Sensor sensor : allSensors) {

                // resolve sensor name
                String sensorName = sensor.getName();
                String name = (sensorName.equals(defSensorName)) ? sensorName : sensorName + " (default)";

                // resolve acquisition mode
                String acqMode = "NA";
                if (SDK_INT >= ANDROID_VERSION_ACQ_MODE) {
                    if (sensor.isDynamicSensor()) {
                        acqMode = "Dynamic";
                    }
                    else if (sensor.isWakeUpSensor()) {
                        acqMode = "WakeUp";
                    }
                }

                // resolve ID
                int sensorId = currSensorId++;
                int androidId = sensorId;
                if (SDK_INT >= ANDROID_VERSION_ACQ_MODE) {
                    androidId = sensor.getId();
                }
                String strId = String.format(Locale.US,"%d", androidId);

                // resolve description info
                Map<String, String> descInfo = new HashMap<>();
                descInfo.put("Name", sensor.getName());
                descInfo.put("Android_ID", strId);
                descInfo.put("Vendor", sensor.getVendor());
                descInfo.put("Version", String.format(Locale.US,"%d", sensor.getVersion()));
                descInfo.put("Type", sensor.getStringType());
                descInfo.put("Acq_Mode", acqMode);

                // resolve calibration info
                Map<String, String> calibInfo = new HashMap<>();
                calibInfo.put("Name", sensor.getName());
                calibInfo.put("App_ID", String.format(Locale.US, "%d", sensorId));
                calibInfo.put("Resolution", String.format(Locale.US,"%.9f", sensor.getResolution()));
                calibInfo.put("Max_Range",String.format(Locale.US,"%.6f", sensor.getMaximumRange()));
                calibInfo.put("Min_Delay_us", String.format(Locale.US,"%d", sensor.getMinDelay()));
                calibInfo.put("Max_Delay_us", String.format(Locale.US,"%d", sensor.getMaxDelay()));
                calibInfo.put("Power_mA", String.format(Locale.US,"%.6f", sensor.getPower()));

                // create new sensor
                MySensorInfo sensorInfo = new MotionSensor(sensorId, name, sensor);
                sensorInfo.setDescInfo(descInfo);
                sensorInfo.setCalibInfo(calibInfo);

                lSensorInfo.add(sensorInfo);
            }
        }
    }

    /**
     * This retrieves all IMU sensors (both calibrated and uncalibrated)
     * Use filters to remove the unwanted sensor types
     * @param sensorManager Android sensor manager
     * @return List of IMU sensors (info)
     */
    private static List<MySensorInfo> getImuSensors(SensorManager sensorManager) {

        ArrayList<MySensorInfo> mImu = new ArrayList<>();

        if (sensorManager == null) {
            return mImu;
        }

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {
            getSensorsInfo(sensorManager, android.hardware.Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, mImu);
            getSensorsInfo(sensorManager, android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED, mImu);
        }

        getSensorsInfo(sensorManager, android.hardware.Sensor.TYPE_ACCELEROMETER, mImu);
        getSensorsInfo(sensorManager, android.hardware.Sensor.TYPE_GYROSCOPE, mImu);

        return mImu;
    }

    private static List<MySensorInfo> getMagnetometerSensors(SensorManager sensorManager) {

        ArrayList<MySensorInfo> mMag = new ArrayList<>();

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {
            getSensorsInfo(sensorManager, android.hardware.Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, mMag);
        }

        getSensorsInfo(sensorManager, android.hardware.Sensor.TYPE_MAGNETIC_FIELD, mMag);

        return mMag;
    }

    /**
     * Since this is called at first (base constructor) don't use any internal variables here
     * @param mContext context activity
     * @return list of all supported sensor groups
     */
    @Override
    public List<MySensorGroup> getSensorGroups(Context context) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        SensorManager sensorManager = getSensorManager(context);

        List<MySensorGroup> sensorGroups = new ArrayList<>();

        List<MySensorInfo> mImu = getImuSensors(sensorManager);
        List<MySensorInfo> mMag = getMagnetometerSensors(sensorManager);

        MySensorGroup imuGrp = new MySensorGroup(MySensorGroup.getNextGlobalId(),
                SensorType.TYPE_IMU, "IMU", mImu);
        sensorGroups.add(imuGrp);

        // Usually, magnetometer is considered a separate sensor
        MySensorGroup magGrp = new MySensorGroup(MySensorGroup.getNextGlobalId(),
                SensorType.TYPE_MAGNET, "Magnetometer", mMag);
        sensorGroups.add(magGrp);

        return sensorGroups;
    }

    /*---------------------------------- Lifecycle Management ------------------------------------*/

    @Override
    public void start(Context context) {

        if (!this.isAvailableAndChecked()) {
            Log.w(TAG, "Sensors are not available, abort");
            return;
        }

        super.start(context);
        openStorageChannels();
        registerSensors();
    }

    @Override
    public void stop(Context context) {

        if (!this.isAvailableAndChecked() || !this.isProcessing()) {
            Log.d(TAG, "Sensors are not running");
            return;
        }

        unregisterSensors();
        closeStorageChannels();
        super.stop(context);
    }

    private void registerSensors() {

        if (!this.isAvailableAndChecked() || mSensorManager == null ||
                mlSensorGroup == null || mlSensorGroup.isEmpty()) {
            Log.d(TAG, "No sensors to register, abort");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensor : sensorGroup.getSensors()) {

                if (sensor.isChecked() && sensor instanceof MotionSensor) {

                    MotionSensor motionSensor = (MotionSensor) sensor;
                    mSensorManager.registerListener(mSensorCallback, motionSensor.getSensor(),
                                                    MAX_SENSOR_READ_INTERVAL);
                }
            }
        }
    }

    private void unregisterSensors() {

        if (!this.isAvailableAndChecked() || mSensorManager == null) {
            Log.d(TAG, "No sensors to unregister");
            return;
        }

        mSensorManager.unregisterListener(mSensorCallback);
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    protected String getResourceId(MyResourceIdentifier resId) {

        // resId.getId() is initialized with sensors type
        switch (resId.getId()) {
            case Sensor.TYPE_ACCELEROMETER:
                return "Accel";
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                return "Accel_Uncalibrated";
            case Sensor.TYPE_GYROSCOPE:
                return "Gyro";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                return "Gyro_Uncalibrated";
            case Sensor.TYPE_MAGNETIC_FIELD:
                return "Magnet";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                return "Magnet_Uncalibrated";
            default:
                return null;
        }
    }

    @Override
    protected List<Pair<String, MsgConfig>> getStorageConfigMessages(MySensorInfo sensor) {

        List<Pair<String, MsgConfig>> lMsgConfigPairs = new ArrayList<>();

        if (!(sensor instanceof MotionSensor)) {
            return lMsgConfigPairs;
        }

        MotionSensor motionSensor = (MotionSensor) sensor;
        int sensorType = motionSensor.getSensor().getType();

        List<String> imuDirs = Collections.singletonList("imu");
        List<String> magDirs = Collections.singletonList("magnetic_field");

        StorageInfo.StreamType ss = StorageInfo.StreamType.STREAM_STRING;

        StorageInfo storageInfo;
        String header;

        switch (sensorType) {
            case android.hardware.Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                header = "# timestamp_ns, ax_m_s2, ay_m_s2, az_m_s2, b_ax_m_s2, b_ay_m_s2, b_az_m_s2, sensor_id\n";
                storageInfo = new StorageInfo(imuDirs, "accel_raw.txt", ss);
                break;
            case android.hardware.Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                header = "# timestamp_ns, rx_rad_s, ry_rad_s, rz_rad_s, b_rx_rad_s, b_ry_rad_s, b_rz_rad_s, sensor_id\n";
                storageInfo = new StorageInfo(imuDirs, "gyro_raw.txt", ss);
                break;
            case android.hardware.Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                header = "# timestamp_ns, mx_uT, my_uT, mz_uT, b_mx_uT, b_my_uT, b_mz_uT, sensor_id\n";
                storageInfo = new StorageInfo(magDirs, "mag_raw.txt", ss);
                break;
            case android.hardware.Sensor.TYPE_ACCELEROMETER:
                header = "# timestamp_ns, ax_m_s2, ay_m_s2, az_m_s2, sensor_id\n";
                storageInfo = new StorageInfo(imuDirs, "accel.txt", ss);
                break;
            case android.hardware.Sensor.TYPE_GYROSCOPE:
                header = "# timestamp_ns, rx_rad_s, ry_rad_s, rz_rad_s, sensor_id\n";
                storageInfo = new StorageInfo(imuDirs, "gyro.txt", ss);
                break;
            case android.hardware.Sensor.TYPE_MAGNETIC_FIELD:
                header = "# timestamp_ns, mx_uT, my_uT, mz_uT, sensor_id\n";
                storageInfo = new StorageInfo(magDirs, "mag.txt", ss);
                break;
            default:
                header = "# unknown sensor type\n";
                storageInfo = new StorageInfo(imuDirs, "undefined_sensor.txt", ss);
                break;
        }

        String sensorTag = getResourceId(new MyResourceIdentifier(sensorType, -1));
        StorageConfig config = new StorageConfig(MsgConfig.ConfigAction.OPEN, TAG, storageInfo);
        config.setStringMessage(header);

        lMsgConfigPairs.add(new Pair<>(sensorTag, config));

        return lMsgConfigPairs;
    }

    /*========================================= Helpers ==========================================*/

    /*======================================== Data Types ========================================*/

    private static class MotionSensor extends MySensorInfo {

        public MotionSensor(int id, String name) {

            super(id, name);
        }

        public MotionSensor(int id, String name, android.hardware.Sensor sensor) {

            this(id, name);
            mMotionSensor = sensor;
        }

        public void setSensor(android.hardware.Sensor sensor) { mMotionSensor = sensor; }
        public android.hardware.Sensor getSensor() { return mMotionSensor; }

        private android.hardware.Sensor mMotionSensor;
    }
}
