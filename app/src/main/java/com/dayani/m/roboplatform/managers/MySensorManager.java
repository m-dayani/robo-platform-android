package com.dayani.m.roboplatform.managers;

/**
 * TODO: We can receive and process sensor values from
 *      another thread.
 * (We don't do this here because it's straightforward).
 *
 * TODO: This is a test acquisition program.
 *      In real programs, test to see if all sensors are
 *      present in device. If not, use the IMU connected to
 *      USB. If it is, only work with device's sensors.
 *
 * ** Availability:
 *      1. For full operation: Accel, Mangent, even Gyro sensors
 *      2. For just record: At least Accel
 *      Availability depends on the RECORD_MODE.
 * ** Resources:
 *      1. Internal HandlerThread
 *      2. Sensors (registered callbacks)
 * ** State Management:
 *      1. isAvailable (availability)
 *      2. isAccelAvailable
 *      3. +Other sensors
 *
 * The big difference here (compared to location or USB)
 * is that the device is either has a sensor or not and
 * this can't be changed during time.
 *
 * 4 Stages of sensor processing:
 *      1. Init (check availability & getting sensors)
 *      2. Start (running sensor capture)
 *      3. process the result
 *      4. Stop (unregister listeners)
 */

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MySensorManager {

    private static final String TAG = "MySensorManager";

    private static final int MAX_SENSOR_READ_INTERVAL = SensorManager.SENSOR_DELAY_GAME;

    public enum RecordMode {
        MODE_RECORD_ANY,
        MODE_RECORD_ANY_RAW,
        MODE_RECORD_ALL,
        MODE_RECORD_IMU,
    }

    private static Context appContext;
    //private LocalBroadcastManager mLocalBrManager;

    private StringBuffer mSensorString;

    private HandlerThread mBackgroundThread;
    private Handler mSensorHandler;

    private SensorManager mSensorManager;
    private Sensor mAccel = null;
    private Sensor mMagent = null;
    private Sensor mGyro = null;
    private SensorEvent mSensorEvent;
    private long mCurrentTimeStamp;

    private RecordMode mRecordMode;
    private int mRecordDelay;

    private boolean isAvailable = false;
    private boolean isAccelAvailable = false;

    private float[] gravity = {0,0,9.81f};
    private final float alpha = 0.8f;
    private float[] mRotationMatrix = new float[9];
    private float[] mOrientationAngles = new float[3];
    private float[] mAccelerometerReading = new float[3];
    private float[] mMagentometerReading = new float[3];

    private SensorEventListener mSensorCallback = new SensorEventListener() {

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

    private Runnable sensorReceiveTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG,"Processing Sensor receive job in background.");
            handleEvent(mSensorEvent);
        }

        private void handleEvent(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED: {
                    System.arraycopy(event.values, 0, mAccelerometerReading,
                            0, mAccelerometerReading.length);

                    // Do something with this sensor value.
                    String sVal = getSensorString("RawAccel, ", event);
                    //just store the raw data without any change
                    mSensorString.append(sVal);
                    Log.v(TAG, sVal);
                    break;
                }
                case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED: {
                    System.arraycopy(event.values, 0, mMagentometerReading,
                            0, mMagentometerReading.length);

                    String sVal = getSensorString("RawMagnet, ", event);
                    mSensorString.append(sVal);
                    break;
                }
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED: {
                    String sVal = getSensorString("RawGyro, ", event);
                    mSensorString.append(sVal);
                    break;
                }
                case Sensor.TYPE_ACCELEROMETER: {
                    System.arraycopy(event.values, 0, mAccelerometerReading,
                            0, mAccelerometerReading.length);

                    String sVal = getSensorString("Accel, ", event);
                    mSensorString.append(sVal);
                    Log.v(TAG, sVal);
                    break;
                }
                case Sensor.TYPE_GYROSCOPE: {
                    String sVal = getSensorString("Gyro, ", event);
                    mSensorString.append(sVal);
                    break;
                }
                case Sensor.TYPE_MAGNETIC_FIELD: {
                    System.arraycopy(event.values, 0, mMagentometerReading,
                            0, mMagentometerReading.length);

                    String sVal = getSensorString("Magnet, ", event);
                    mSensorString.append(sVal);
                    break;
                }
                default:
                    Log.e(TAG, "Unhandled sensor type");
            }
        }

        // Compute the three orientation angles based on the most recent readings from
        // the device's accelerometer and magnetometer.
        private void updateOrientationAngles() {
            // Update rotation matrix, which is needed to update orientation angles.
            SensorManager.getRotationMatrix(mRotationMatrix, null,
                    mAccelerometerReading, mMagentometerReading);

            // "mRotationMatrix" now has up-to-date information.
            SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);

            // "mOrientationAngles" now has up-to-date information.
            // Do something with this sensor value.
            //String sVal = getSensorString("Orientation_Angles_", mOrientationAngles);
            //just store the raw data without any change
            //mSensorString.append(sVal);
        }

    };

    /*======================================= Construction =======================================*/

    public MySensorManager(Context context, StringBuffer sb,
                           @Nullable RecordMode recordMode, int sensorSpeed) {
        super();
        this.appContext = context;
        //mLocalBrManager = LocalBroadcastManager.getInstance(appContext);
        this.mSensorString = sb;
        //ToDo: this init here is wrong because log file is shared between managers!
        //this.initSensorString();
        this.mRecordMode = RecordMode.MODE_RECORD_ANY;
        if (recordMode != null) {
            mRecordMode = recordMode;
        }
        this.mRecordDelay = MAX_SENSOR_READ_INTERVAL;
        if (sensorSpeed != 0) {
            mRecordDelay = sensorSpeed;
        }
        mSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        //Record mode: all is the default mode
        this.initSensors(this.mRecordMode);
        //isAvailable = hasOrientationSensors();
        //this.init();
    }

    public MySensorManager(Context context, StringBuffer sb) {
        super();
        this.appContext = context;
        //mLocalBrManager = LocalBroadcastManager.getInstance(appContext);
        this.mSensorString = sb;
        //this.initSensorString();
        this.mRecordMode = RecordMode.MODE_RECORD_ANY;
        mSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        this.initSensors(this.mRecordMode);
        //this.init();
    }

    /*------------------------------------------- Init -------------------------------------------*/

    private void initSensors(RecordMode recMode) {
        switch (recMode) {
            case MODE_RECORD_ANY:
                initModeRecordAny();
                break;
            case MODE_RECORD_ANY_RAW:
                initModeRecordAnyRaw();
                break;
            case MODE_RECORD_ALL:
                initModeRecordAll();
                break;
//            case MODE_RECORD_IMU:
//                initModeRecordIMU();
//                break;
            default:
                initModeRecordAny();
                break;
        }
    }

    private void initModeRecordAnyRaw() {
        if (hasRawAccelerometer()) {
            mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
            setAvailableFlag(true);
        }
        else if (hasAccelerometer()) {
            //This is android dependent and acts mysteriously!!!?????
            mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            setAvailableFlag(true);
        }
        if (hasRawMagneticField()) {
            mMagent = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
            setAvailableFlag(true);
        }
        if (hasRawGyroscope()) {
            mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
            setAvailableFlag(true);
        }
//        if (hasMagneticField()) {
//            mMagent = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//            setAvailableFlag(true);
//        }
    }

    private void initModeRecordAny() {
        if (hasAccelerometer()) {
            mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            setAvailableFlag(true);
        }
        if (hasMagneticField()) {
            mMagent = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            setAvailableFlag(true);
        }
        if (hasGyroscope()) {
            mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            setAvailableFlag(true);
        }
    }

    private void initModeRecordAll() {
        if (hasAccelerometer() && hasMagneticField() && hasGyroscope() &&
                hasRotationVector()) {
            mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mMagent = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            //mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            setAvailableFlag(true);
        }
    }

//    private void initModeRecordIMU() {
//        if (hasOrientationSensors()) {
//            mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//            mMagent = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//            setAvailableFlag(true);
//        }
//    }

    /*------------------------------------- Setters/Getters --------------------------------------*/

    private void setAvailableFlag(boolean state) {
        isAvailable = state;
    }
    private boolean getAvailableFlag() {
        return isAvailable;
    }

    public boolean checkAvailability() {
        return getAvailableFlag();
    }

    /*---------------------------------- Lifecycle Management ------------------------------------*/

//    public void onResume() {
//        mSensorManager.registerListener(mSensorCallback, mAccel, MAX_SENSOR_READ_INTERVAL);
//        //sensorManager.registerListener()
//    }
//
//    public void onPause() {
//        mSensorManager.unregisterListener(mSensorCallback);
//    }

    public void clean() {
        stopBackgroundThread();
    }

    public void start() {
        startBackgroundThread();
        registerSensors(mRecordMode);
    }

    public void stop() {
        unregisterSensors();
        stopBackgroundThread();
    }

    //TODO
    private void registerSensors(RecordMode recMode) {
        if (!getAvailableFlag()) {
            return;
        }
        switch (recMode) {
            case MODE_RECORD_ANY:
            case MODE_RECORD_ANY_RAW:
                registerModeRecAny();
                break;
            case MODE_RECORD_ALL:
                registerModeRecAll();
                break;
//            case MODE_RECORD_IMU:
//                registerModeRecOrientation();
//                break;
            default:
                registerModeRecAny();
                break;
        }
    }

    private void unregisterSensors() {
        mSensorManager.unregisterListener(mSensorCallback);
    }

    private void registerModeRecAny() {
        if (mAccel != null) {
            mSensorManager.registerListener(mSensorCallback, mAccel, mRecordDelay);
        }
        if (mMagent != null) {
            mSensorManager.registerListener(mSensorCallback, mMagent, mRecordDelay);
        }
        if (mGyro != null) {
            mSensorManager.registerListener(mSensorCallback, mGyro, mRecordDelay);
        }
    }

    private void registerModeRecAll() {
        if (mAccel != null && mMagent != null && mGyro != null) {
            mSensorManager.registerListener(mSensorCallback, mAccel, mRecordDelay);
            mSensorManager.registerListener(mSensorCallback, mMagent, mRecordDelay);
            mSensorManager.registerListener(mSensorCallback, mGyro, mRecordDelay);
            //mSensorManager.registerListener(mSensorCallback, mRotationVector, mRecordDelay);
        }
    }

//    private void registerModeRecOrientation() {
//        if (mAccel != null && mMagent != null) {
//            mSensorManager.registerListener(mSensorCallback, mAccel, MAX_SENSOR_READ_INTERVAL);
//            mSensorManager.registerListener(mSensorCallback, mMagent, MAX_SENSOR_READ_INTERVAL);
//        }
//    }

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

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mSensorHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*--------------------------------------- Sensor Check ---------------------------------------*/

    private boolean hasAccelerometer() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
    }

    private boolean hasRawAccelerometer() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED) != null;
    }

    private boolean hasMagneticField() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    private boolean hasRawMagneticField() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) != null;
    }

    private boolean hasGyroscope() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
    }

    private boolean hasRawGyroscope() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED) != null;
    }

    private boolean hasRotationVector() {
        return mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null;
    }

    /**
     * based on google documentation:
     *  @link https://developer.android.com/guide/topics/sensors/sensors_position.html
     * we need only magnetic field and accelerometer to calculate orientation.
     * @return
     */
    private boolean hasOrientationSensors() {
        if (mSensorManager == null) {
            return false;
        }
        boolean accel = hasAccelerometer();
        boolean magnet = hasMagneticField();
        return accel && magnet;
    }

    /*========================================= Helpers ==========================================*/

    /**
     *
     * @param event
     * @return String("data-time-format: xAccel, yAccel, zAccel") zAccel is realVal+9.71
     */
    public String getSensorString(SensorEvent event) {
        return "Accel_" +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSS").format(new Date()) +
                ", " + event.values[0] + ", " + event.values[1] + ", " + event.values[2] + '\n';
    }

    public String getSensorString(String prefix, SensorEvent event) {
        String res = prefix + System.nanoTime() + "," + event.timestamp + "," + event.accuracy;
                //new SimpleDateFormat("HH:mm:ss.SSSSSS").format(new Date());
        for (int i = 0; i < event.values.length; i++) {
            res += "," + event.values[i];
        }
        res += '\n';
        return res;
    }

    private void initSensorString() {
        this.mSensorString.append("# "+"Sensor measurements: Available IMU and Magnetometer\n");
        this.mSensorString.append("# "+"Accelerometer readings: m/s^2\n");
        this.mSensorString.append("# "+"Gyroscope readings: rad/s\n");
        this.mSensorString.append("# "+"Magnetometer readings: uT\n");
        this.mSensorString.append("SensorType,SystemTimestampNs,EventTimestamp,Accuracy,Val0,Val1,Val2\n");
    }

    public void writeSensorHeader() {
        this.initSensorString();
    }

    public void processAccelSensor(SensorEvent event) {

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // Remove the gravity contribution with the high-pass filter.
        mAccelerometerReading[0] = event.values[0] - gravity[0];
        mAccelerometerReading[1] = event.values[1] - gravity[1];
        mAccelerometerReading[2] = event.values[2] - gravity[2];
    }

    private void handleUsbAlso() {
        //Also get sensor values from usb?
        //Intent intent = new Intent();
        //intent.setAction(MyUSBManager.ACTION_SENSOR_RECIEVE);
        //intent.putExtra("data","Notice me senpai!");
        //mLocalBrManager.sendBroadcast(intent);
        //Log.d(TAG, "USB sensor recieve request is sent.");
    }

    public interface SensorListener {
        void onSensorChanged(SensorEvent sensorEvent);
    }
}
