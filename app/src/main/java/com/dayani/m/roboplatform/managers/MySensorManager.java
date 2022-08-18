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

import com.dayani.m.roboplatform.managers.MyStorageManager.StorageInfo;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.MySensorInfo;

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

    private static final int MAX_SENSOR_READ_INTERVAL = SensorManager.SENSOR_DELAY_GAME;

    private static final int ANDROID_VERSION_UNCALIB_SENSORS = Build.VERSION_CODES.O;
    private static final int ANDROID_VERSION_ACQ_MODE = Build.VERSION_CODES.N;

//    private final List<SensorType> mSupportedSensorGroupTypes = Arrays.asList(
//            SensorType.TYPE_IMU,
//            SensorType.TYPE_MAGNET
//    );

    private static final List<Integer> mCalibratedTypes = Arrays.asList(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD
    );

    private static final List<Integer> mUncalibratedTypes = initUncalibratedTypes();

    private static final List<Integer> mAllSensorTypes = initAllSensorTypes();


    private static int currSensorId = 0;

    private final SensorManager mSensorManager;

    //private final Map<Integer, Integer> mmPublishers = new HashMap<>();
    //private final Map<Integer, Pair<List<String>, String>> mmFileNames = initStorageFileNames();

    private SensorEvent mSensorEvent;

    private final SensorEventListener mSensorCallback = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something here if sensor accuracy changes.
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // The light sensor returns a single value.
            // Many sensors return 3 values, one for each axis.
            mSensorEvent = event;
            //mCurrentTimeStamp = System.nanoTime();
            mHandler.post(sensorReceiveTask);
        }
    };

    private final Runnable sensorReceiveTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG,"Processing Sensor receive job in background.");
            handleEvent(mSensorEvent);
        }

        private void handleEvent(SensorEvent event) {

            String sVal = getSensorString(event);
            int sensorId = mapSensorTypeToId(event.sensor.getType());
            writeStorageChannel(getChannelId(sensorId), sVal);
        }
    };

    /* ==================================== Construction ======================================== */

    public MySensorManager(Context context) {

        super(context);

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        init(context);
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

        SensorManager sensorManager = getStorageManager(context);

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
    protected boolean hasAllRequirements(Context context) {
        return true;
    }

    @Override
    protected void resolveRequirements(Context context) {}

    @Override
    public List<String> getPermissions() {
        return new ArrayList<>();
    }

    @Override
    protected boolean hasAllPermissions(Context context) {
        return true;
    }

    /*-------------------------------------- Availability ----------------------------------------*/

    /*------------------------------------- Setters/Getters --------------------------------------*/

    private SensorManager getStorageManager(Context context) {

        if (mSensorManager != null) {
            return mSensorManager;
        }
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    /**
     * Update all state that depends on sensor groups or availability and requirements
     * @param context Context activity
     */
    @Override
    public void updateState(Context context) {

        //removeAllStorageChannels();
        //updateStorageChannels();
    }

    /*public List<Integer> getCalibratedTypes() {
        return mCalibratedTypes;
    }

    public List<Integer> getUncalibratedTypes() {
        return mUncalibratedTypes;
    }*/

    /*------------------------------------- Init. Sensors ----------------------------------------*/

    private static void getSensorsInfo(SensorManager sensorManager, int sensorTypeCode, List<MySensorInfo> lSensorInfo) {

        Sensor defSensor = sensorManager.getDefaultSensor(sensorTypeCode);

        if (defSensor != null) {
            List<Sensor> allSensors = sensorManager.getSensorList(sensorTypeCode);

            for (Sensor sensor : allSensors) {

                String name = (sensor.getName().equals(defSensor.getName())) ?
                        sensor.getName() : sensor.getName()+" (default)";

                String acqMode = "NA";
                if (SDK_INT >= ANDROID_VERSION_ACQ_MODE) {
                    if (sensor.isDynamicSensor()) {
                        acqMode = "Dynamic";
                    }
                    else if (sensor.isWakeUpSensor()) {
                        acqMode = "WakeUp";
                    }
                }

                int sensorId = currSensorId++;
                int androidId = sensorId;
                if (SDK_INT >= ANDROID_VERSION_ACQ_MODE) {
                    androidId = sensor.getId();
                }
                String strId = String.format(Locale.US,"%d", androidId);

                Map<String, String> descInfo = new HashMap<>();
                descInfo.put("Name", sensor.getName());
                descInfo.put("Android_ID", strId);
                descInfo.put("Vendor", sensor.getVendor());
                descInfo.put("Version", String.format(Locale.US,"%d", sensor.getVersion()));
                descInfo.put("Type", sensor.getStringType());
                descInfo.put("Acq_Mode", acqMode);

                Map<String, String> calibInfo = new HashMap<>();
                calibInfo.put("Name", sensor.getName());
                calibInfo.put("App_ID", String.format(Locale.US, "%d", sensorId));
                calibInfo.put("Resolution", String.format(Locale.US,"%.9f", sensor.getResolution()));
                calibInfo.put("Max_Range",String.format(Locale.US,"%.6f", sensor.getMaximumRange()));
                calibInfo.put("Min_Delay_us", String.format(Locale.US,"%d", sensor.getMinDelay()));
                calibInfo.put("Max_Delay_us", String.format(Locale.US,"%d", sensor.getMaxDelay()));
                calibInfo.put("Power_mA", String.format(Locale.US,"%.6f", sensor.getPower()));

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

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {
            getSensorsInfo(sensorManager, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, mImu);
            getSensorsInfo(sensorManager, Sensor.TYPE_GYROSCOPE_UNCALIBRATED, mImu);
        }

        getSensorsInfo(sensorManager, Sensor.TYPE_ACCELEROMETER, mImu);
        getSensorsInfo(sensorManager, Sensor.TYPE_GYROSCOPE, mImu);

        return mImu;
    }

    private static List<MySensorInfo> getMagnetometerSensors(SensorManager sensorManager) {

        ArrayList<MySensorInfo> mMag = new ArrayList<>();

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {
            getSensorsInfo(sensorManager, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, mMag);
        }

        getSensorsInfo(sensorManager, Sensor.TYPE_MAGNETIC_FIELD, mMag);

        return mMag;
    }

    /**
     * Since this is called at first (base constructor) don't use any internal variables here
     * @param mContext context activity
     * @return list of all supported sensor groups
     */
    @Override
    public List<MySensorGroup> getSensorGroups(Context mContext) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        SensorManager sensorManager = getStorageManager(mContext);

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
    public void clean() {}

    @Override
    protected void init(Context context) {

        updateCheckedSensors(context);
    }

    private static List<Integer> initUncalibratedTypes() {

        List<Integer> lTypes = new ArrayList<>();

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {

            lTypes = Arrays.asList(
                    Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                    Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                    Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
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

    @Override
    public void start(Context context) {

        super.start(context);
        openStorageChannels();
        startBackgroundThread(TAG);
        registerSensors();
    }

    @Override
    public void stop(Context context) {

        unregisterSensors();
        stopBackgroundThread();
        closeStorageChannels();
        super.stop(context);
    }

    private void registerSensors() {

        if (mSensorManager == null || mlSensorGroup == null || mlSensorGroup.isEmpty()) {
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
        mSensorManager.unregisterListener(mSensorCallback);
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    private int mapSensorTypeToId(int sensorType) {

        return sensorType;
    }

    @Override
    protected Map<Integer, MyStorageManager.StorageInfo> initStorageChannels() {

        Map<Integer, StorageInfo> mFileNames = new HashMap<>();

        List<String> imuDirs = Collections.singletonList("imu");
        List<String> magDirs = Collections.singletonList("magnetic_field");

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {
            mFileNames.put(
                    mapSensorTypeToId(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED),
                    new StorageInfo(imuDirs, "accel_raw.txt"));
            mFileNames.put(
                    mapSensorTypeToId(Sensor.TYPE_GYROSCOPE_UNCALIBRATED),
                    new StorageInfo(imuDirs, "gyro_raw.txt"));
            mFileNames.put(
                    mapSensorTypeToId(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED),
                    new StorageInfo(magDirs, "mag_raw.txt"));
        }
        mFileNames.put(
                mapSensorTypeToId(Sensor.TYPE_ACCELEROMETER),
                new StorageInfo(imuDirs, "accel.txt"));
        mFileNames.put(
                mapSensorTypeToId(Sensor.TYPE_GYROSCOPE),
                new StorageInfo(imuDirs, "gyro.txt"));
        mFileNames.put(
                mapSensorTypeToId(Sensor.TYPE_MAGNETIC_FIELD),
                new StorageInfo(magDirs, "mag.txt"));

        return mFileNames;
    }

    /**
     * Requirements: Availability, Storage listener, Map of file names, Map of channels
     * Depends on: The last state of sensors (availability and checked state)
     * Opens them for available sensors
     */
    @Override
    protected void openStorageChannels() {

        if (mStorageListener == null || mmStorageChannels == null || mlSensorGroup == null) {
            Log.w(TAG, "Either sensors are not available or no storage listener found");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo.isChecked() && sensorInfo instanceof MotionSensor) {

                    Sensor sensor = ((MotionSensor) sensorInfo).getSensor();

                    int sensorType = sensor.getType();
                    int sensorId = mapSensorTypeToId(sensorType);
                    StorageInfo storageInfo = mmStorageChannels.get(sensorId);

                    if (storageInfo != null) {

                        int chId = mStorageListener.getStorageChannel(
                                storageInfo.getFolders(), storageInfo.getFileName(), false);
                        storageInfo.setChannelId(chId);
                        writeFileHeader(sensorType, chId);
                    }
                }
            }
        }
    }

    /*========================================= Helpers ==========================================*/

    /*public void filterBySensorTypeCode(List<Integer> lFilteredTypes) {

        if (mlSensorGroup == null) {
            Log.w(TAG, "Empty sensor groups, abort");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo instanceof MotionSensor) {

                    MotionSensor sensor = (MotionSensor) sensorInfo;

                    if (lFilteredTypes.contains(sensor.getSensor().getType())) {

                        //sensor.setAvailability(false);
                    }
                }
            }
        }
        // TODO: Maybe update other things like the availability of group or manager's state???
    }*/

    private void writeFileHeader(int sensorType, int channelId) {

        String header;

        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                header = "# timestamp_ns, ax_m_s2, ay_m_s2, az_m_s2, b_ax_m_s2, b_ay_m_s2, b_az_m_s2, sensor_id\n";
                break;
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                header = "# timestamp_ns, rx_rad_s, ry_rad_s, rz_rad_s, b_rx_rad_s, b_ry_rad_s, b_rz_rad_s, sensor_id\n";
                break;
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                header = "# timestamp_ns, mx_uT, my_uT, mz_uT, b_mx_uT, b_my_uT, b_mz_uT, sensor_id\n";
                break;
            case Sensor.TYPE_ACCELEROMETER:
                header = "# timestamp_ns, ax_m_s2, ay_m_s2, az_m_s2, sensor_id\n";
                break;
            case Sensor.TYPE_GYROSCOPE:
                header = "# timestamp_ns, rx_rad_s, ry_rad_s, rz_rad_s, sensor_id\n";
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                header = "# timestamp_ns, mx_uT, my_uT, mz_uT, sensor_id\n";
                break;
            default:
                header = "# unknown sensor type\n";
                break;
        }

        mStorageListener.publishMessage(channelId, header);
    }

    public String getSensorString(SensorEvent event) {

        StringBuilder res = new StringBuilder(String.format(Locale.US, "%d", event.timestamp));

        for (int i = 0; i < event.values.length; i++) {
            res.append(", ").append(event.values[i]);
        }

        if (SDK_INT >= ANDROID_VERSION_ACQ_MODE) {
            res.append(", ").append(event.sensor.getId());
        }

        res.append('\n');

        return res.toString();
    }

    /*======================================== Data Types ========================================*/

    private static class MotionSensor extends MySensorInfo {

        public MotionSensor(int id, String name) {

            super(id, name);
        }

        public MotionSensor(int id, String name, Sensor sensor) {

            this(id, name);
            mMotionSensor = sensor;
        }

        public void setSensor(Sensor sensor) { mMotionSensor = sensor; }
        public Sensor getSensor() { return mMotionSensor; }

        private Sensor mMotionSensor;
    }
}
