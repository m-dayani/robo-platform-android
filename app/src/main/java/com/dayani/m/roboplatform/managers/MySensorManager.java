package com.dayani.m.roboplatform.managers;

/*
 * ** Availability:
 *      1. For full operation: Accel, Mangent, even Gyro sensors
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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;

import androidx.activity.result.ActivityResult;

import com.dayani.m.roboplatform.utils.ActivityRequirements;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.MySensorInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MySensorManager extends MyBaseManager {

    private static final String TAG = MySensorManager.class.getSimpleName();

    private static final int MAX_SENSOR_READ_INTERVAL = SensorManager.SENSOR_DELAY_GAME;

    private static final int ANDROID_VERSION_UNCALIB_SENSORS = Build.VERSION_CODES.O;
    private static final int ANDROID_VERSION_ACQ_MODE = Build.VERSION_CODES.N;

    private final List<SensorType> mSupportedSensorTypes = Arrays.asList(
            SensorType.TYPE_IMU, SensorType.TYPE_MAGNET);

    private static final List<Integer> mCalibratedTypes = Arrays.asList(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD
    );

    private static final List<Integer> mUncalibratedTypes = initUncalibratedTypes();

    private static int currSensorId = 0;

    private final SensorManager mSensorManager;

    private MyStorageManager.StorageChannel mStorageListener;

    private final Map<Integer, Integer> mmPublishers = new HashMap<>();
    private final Map<Integer, Pair<List<String>, String>> mmFileNames = initStorageFileNames();

    private SensorEvent mSensorEvent;

    private HandlerThread mBackgroundThread;
    private Handler mSensorHandler;

    private final SensorEventListener mSensorCallback = new SensorEventListener() {

        @Override
        public final void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do something here if sensor accuracy changes.
        }

        @Override
        public final void onSensorChanged(SensorEvent event) {
            // The light sensor returns a single value.
            // Many sensors return 3 values, one for each axis.
            mSensorEvent = event;
            //mCurrentTimeStamp = System.nanoTime();
            mSensorHandler.post(sensorReceiveTask);
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
            mStorageListener.publishMessage(getStorageChannelId(event.sensor.getType()), sVal);
        }
    };

    /*======================================= Construction =======================================*/

    public MySensorManager(Context context) {

        super(context);

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        init(context);
        checkAvailability(context);
    }

    @Override
    public void clean() {}

    @Override
    protected void init(Context context) {

        setStorageListener(context);
    }

    private static Map<Integer, Pair<List<String>, String>> initStorageFileNames() {


        Map<Integer, Pair<List<String>, String>> mFileNames = new HashMap<>();

        List<String> imuDirs = Collections.singletonList("imu");
        List<String> magDirs = Collections.singletonList("magnetic_field");

        if (SDK_INT >= ANDROID_VERSION_UNCALIB_SENSORS) {
            mFileNames.put(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, Pair.create(imuDirs, "accel_raw.txt"));
            mFileNames.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, Pair.create(imuDirs, "gyro_raw.txt"));
            mFileNames.put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Pair.create(magDirs, "mag_raw.txt"));
        }
        mFileNames.put(Sensor.TYPE_ACCELEROMETER, Pair.create(imuDirs, "accel.txt"));
        mFileNames.put(Sensor.TYPE_GYROSCOPE, Pair.create(imuDirs, "gyro.txt"));
        mFileNames.put(Sensor.TYPE_MAGNETIC_FIELD, Pair.create(magDirs, "mag.txt"));

        return mFileNames;
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

    /*-------------------------------------- Availability ----------------------------------------*/

    @Override
    public void checkAvailability(Context context) {

        // for this to be available:
        // 1. has at least one type of sensor
        boolean hasMotionSensors = MySensorGroup.countAvailableSensors(mlSensorGroup, mSupportedSensorTypes) > 0;

        // 2. can write sensor data
        boolean canWrite = mStorageListener != null;

        mIsAvailable = canWrite && hasMotionSensors;
    }

    @Override
    public void resolveAvailability(Context context) {

    }

    /*-------------------------------------- Requirements ----------------------------------------*/

    @Override
    public void onActivityResult(Context context, ActivityResult result) {

    }

    @Override
    public void onPermissionsResult(Context context, Map<String, Boolean> permissions) {

    }

    /*------------------------------------- Setters/Getters --------------------------------------*/

    private int getStorageChannelId(int sensorType) {

        Integer idObj = -1;

        if (!this.isAvailable() || mmPublishers == null) {
            return idObj;
        }

        idObj = mmPublishers.get(sensorType);

        if (idObj == null) {
            return -1;
        }
        return idObj;
    }

    /**
     * Requirements: Availability, Storage listener, Map of file names, Map of channels
     * Depends on: The last state of sensors (availability and checked state)
     * Opens them for available sensors
     */
    private void updateStorageChannels() {

        if (!this.isAvailable() || mStorageListener == null) {
            Log.w(TAG, "Either sensors are not available or no storage listener found");
            return;
        }

        if (mmFileNames == null || mmPublishers == null) {
            Log.w(TAG, "File names or storage channel maps not initialized");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo.isAvailable() && sensorInfo.isChecked() && sensorInfo instanceof MotionSensor) {

                    Sensor sensor = ((MotionSensor) sensorInfo).getSensor();
                    int sensorType = sensor.getType();
                    Pair<List<String>, String> filePath = mmFileNames.get(sensorType);

                    if (filePath != null) {
                        int chId = mStorageListener.getStorageChannel(filePath.first, filePath.second, false);
                        mmPublishers.put(sensorType, chId);
                        writeFileHeader(sensorType, chId);
                    }
                }
            }
        }
    }

    private void removeAllStorageChannels() {

        if (mmPublishers == null || mStorageListener == null) {
            Log.w(TAG, "Channels map or storage listener not initialized");
            return;
        }

        Iterator<Map.Entry<Integer, Integer>> chIter = mmPublishers.entrySet().iterator();

        while (chIter.hasNext()) {

            Map.Entry<Integer, Integer> chEntry = chIter.next();

            if (chEntry != null) {
                mStorageListener.removeChannel(chEntry.getValue());
                chIter.remove();
            }
        }
    }

    public void setStorageListener(Context context) {

        if (context instanceof MyStorageManager.StorageChannel) {
            mStorageListener = (MyStorageManager.StorageChannel) context;
        }
    }

    /**
     * Update all state that depends on sensor groups or availability and requirements
     * @param context Context activity
     */
    @Override
    public void updateState(Context context) {

        checkAvailability(context);
        removeAllStorageChannels();
        updateStorageChannels();
    }

    public List<Integer> getCalibratedTypes() {
        return mCalibratedTypes;
    }

    public List<Integer> getUncalibratedTypes() {
        return mUncalibratedTypes;
    }

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

                MySensorInfo sensorInfo = new MotionSensor(sensorId, name,true, sensor);
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
    public List<MySensorGroup> getSensorRequirements(Context mContext) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        SensorManager sensorManager = mSensorManager;
        if (sensorManager == null) {
            sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        }

        List<MySensorGroup> sensorGroups = new ArrayList<>();

        List<ActivityRequirements.Requirement> reqs = new ArrayList<>();
        List<String> perms = new ArrayList<>();

        List<MySensorInfo> mImu = getImuSensors(sensorManager);
        List<MySensorInfo> mMag = getMagnetometerSensors(sensorManager);

        MySensorGroup imuGrp = new MySensorGroup(MySensorGroup.getNextGlobalId(),
                SensorType.TYPE_IMU, "IMU", mImu, reqs, perms);
        sensorGroups.add(imuGrp);

        // Usually, magnetometer is considered a separate sensor
        MySensorGroup magGrp = new MySensorGroup(MySensorGroup.getNextGlobalId(),
                SensorType.TYPE_MAGNET, "Magnetometer", mMag, reqs, perms);
        sensorGroups.add(magGrp);

        return sensorGroups;
    }

    /*---------------------------------- Lifecycle Management ------------------------------------*/

    @Override
    public void start() {
        startBackgroundThread();
        registerSensors();
    }

    @Override
    public void stop() {
        unregisterSensors();
        stopBackgroundThread();
    }

    private void registerSensors() {

        if (!isAvailable() || mSensorManager == null ||
                mlSensorGroup == null || mlSensorGroup.isEmpty()) {
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensor : sensorGroup.getSensors()) {

                if (sensor.isAvailable() && sensor.isChecked() && sensor instanceof MotionSensor) {

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

    // TODO: Maybe move these two methods to the base class
    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {

        mBackgroundThread = new HandlerThread(TAG);
        mBackgroundThread.start();
        mSensorHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {

        if (mBackgroundThread == null) {
            return;
        }

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mSensorHandler = null;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*========================================= Helpers ==========================================*/

    public void filterBySensorTypeCode(List<Integer> lFilteredTypes) {

        if (mlSensorGroup == null) {
            Log.w(TAG, "Empty sensor groups, abort");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo instanceof MotionSensor) {

                    MotionSensor sensor = (MotionSensor) sensorInfo;

                    if (lFilteredTypes.contains(sensor.getSensor().getType())) {

                        sensor.setAvailability(false);
                    }
                }
            }
        }
        // TODO: Maybe update other things like the availability of group or manager's state???
    }

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

        public MotionSensor(int id, String name, boolean isAvailable) {

            super(id, name, isAvailable);
        }

        public MotionSensor(int id, String name, boolean isAvailable, Sensor sensor) {

            this(id, name, isAvailable);
            mMotionSensor = sensor;
        }

        public void setSensor(Sensor sensor) { mMotionSensor = sensor; }
        public Sensor getSensor() { return mMotionSensor; }

        private Sensor mMotionSensor;
    }
}
