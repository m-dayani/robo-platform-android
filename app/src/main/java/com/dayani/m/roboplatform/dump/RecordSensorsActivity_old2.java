package com.dayani.m.roboplatform.dump;

/**
 * Note1: If using multiple threads for handling work
 *      in each "manager" class in future, use StringBuffer
 *      instead of StringBuilder (for synchronized operation).
 * Note2: TODO: better CPU and background managment +
 *      Better handling critical device states (memory, battery,
 *      device wake and sleep states, ...).
 * Note3: TODO: use StringBuffer instead of StringBuilder.
 *
 * Note4: When we get here, all permissions are granted and
 *      USB device is attached and available! So just do
 *      the checking internally (in each utility class).
 */

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.cutom_views.AutoFitTextureView;
import com.dayani.m.roboplatform.utils.data_types.SensorsContainer;

import java.io.File;


public class RecordSensorsActivity_old2 extends AppCompatActivity
        implements View.OnClickListener,
        MyUSBManager.OnUsbConnectionListener {

    private static final String TAG = "RecordAllActivity";

    private static final String KEY_IS_RECORDING_STATE = AppGlobals.PACKAGE_BASE_NAME
            +".KEY_IS_RECORDING_STATE";

    // TODO: Transfer folder management logic to StorageManager Class
    private static final String VIDEO_FILE_BASE_PATH = "/DCIM/Files";
    private static final String SENSOR_FILE_BASE_PATH = "/DCIM/Files";
    private static final String VIDEO_FILE_BASE_NAME = "video_";
    private static final String SENSOR_FILE_BASE_NAME = "sensor_";

    /**
     * Button to record video
     */
    private Button mBtnRecord;
    private Button mButtonVideo;
    private Chronometer mChronometer;
    private AutoFitTextureView mTextureView;
    private TextView reportTxt;

    private MyStorageManager mStore;
    private File mVideoFile;
    private File mSensorFile;
    private String mTimePerfix;
    private StringBuffer mSensorString;

    private CameraFlyVideo mCam;
    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecording = false;

    private MyUSBManager mUsb;

    private MyLocationManager mLocation;

    private MySensorManager mSensorManager;

    private HandlerThread mSensorThread;
    private Looper mSensorLooper;
    private Handler mSensorWriteHandler;

    private Runnable sensorWriteTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "Writing sensor string file in background...");

            //Store sensor values
            //devise a unified optimum method for setting and
            //updating compatible video & sensor file names
            synchronized (this) {
//                mStore.writeBuffered(
//                        new File(MyStorageManager.getNextFilePath(
//                                mSensorFile.getAbsolutePath(),mTimePerfix,"csv")),
//                        mSensorString.toString());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_all);

        mButtonVideo = (Button) findViewById(R.id.video);
        mChronometer = (Chronometer) findViewById(R.id.chronometer);
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mButtonVideo.setOnClickListener(this);
        findViewById(R.id.info).setOnClickListener(this);
        //reportTxt = (TextView) findViewById(R.id.reportText);
        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);

        mStore = new MyStorageManager(this);

        //mVideoFile = mStore.getPublicInternalFile(VIDEO_FILE_BASE_PATH, VIDEO_FILE_BASE_NAME);
        //mSensorFile = mStore.getPublicInternalFile(SENSOR_FILE_BASE_PATH, SENSOR_FILE_BASE_NAME);
        mTimePerfix = MyStorageManager.getTimePrefix();
        mSensorString = new StringBuffer();

//        mCam = new CameraFlyVideo(this, mTextureView,
//                new File(MyStorageManager.getNextFilePath(
//                        mVideoFile.getAbsolutePath(),mTimePerfix,"mp4")));

        //mLocation = new MyLocationManager(this, mSensorString);

        mSensorManager = new MySensorManager(this);

        mUsb = new MyUSBManager(this, this, mSensorString);
        mUsb.updateDefaultDeviceAvailability();

        startSensorThread();
    }

    @Override
    public void onStart() {
        super.onStart();
        /*if (!mLocation.checkPermissions()) {
            reportTxt.setText("You've denied location permissions!");
        } else {
            //mLocation.getLastLocation();
        }*/
        mIsRecording = MyStateManager.getBoolPref(this, KEY_IS_RECORDING_STATE, false);
        updateButtonState(mIsRecording);
    }

    @Override
    public void onResume() {
        super.onResume();

        //mUsb.registerUsbSensorReciever();
        //mUsb.registerUsbPermission();
        //mUsb.tryOpenDefaultDevice();
        //actually, starts preview
        mCam.onResume();

        // Within {@code onPause()}, we remove location updates. Here, we resume receiving
        // location updates if the user has requested them.
        //if (mLocation.checkRequestingLocUpdates() && mLocation.checkPermissions()) {
        //mLocation.startLocationUpdates();
        /*} else if (!mLocation.checkPermissions()) {
            reportTxt.setText("You've denied location permissions!");
        }*/
        //Registers sensor callbacks
        //mSensorManager.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //mLocation.setBundleData(outState);
        // ...
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        if (mIsRecording) {
            stopRecording();
        }
        //mUsb.unregisterUsbSensorReciever();
        //mUsb.unregisterUsbPermission();
        mCam.clean();

        //mLocation.stopLocationUpdates();
        //mSensorManager.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        //call the superclass method first
        //mUsb.usbClose();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        //mUsb.unregisterUsbPermission();
        mUsb.clean();
        //mUsb.close();
        //mUsb = null;
        //cleaning sensor's thread.
        //mSensorManager.clean();
        stopSensorThread();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");
        //mLocation.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

     /*
    //Here we don't deal with any permission handling/request &...
    //If permissions are not available, we don't do anything.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        //mCam.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //mStore.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //mLocation.onRequestPermissionsResult(requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }*/

    private void startRecording() {
        //startSensorThread();
        //Keep the device screen on while recording
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Registers sensor callbacks
        mSensorManager.start(this);
        mUsb.startPeriodicSensorPoll();
        //while we're not saving files, use our handlerThread for location updates
        //mLocation.startLocationUpdates(mSensorLooper);
        mCam.startRecordingVideo();
        mIsRecording = true;
        MyStateManager.setBoolPref(this, KEY_IS_RECORDING_STATE, mIsRecording);
        // UI
        updateButtonState(mIsRecording);
    }

    private void stopRecording() {
        //stopSensorThread();
        //Allow device screen to be turned off
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager.stop(this);
        mUsb.stopPeriodicSensorPoll();
        mLocation.stopLocationUpdates(this);
        mCam.stopRecordingVideo();
        mIsRecording = false;
        MyStateManager.setBoolPref(this, KEY_IS_RECORDING_STATE, mIsRecording);
        //Store sensor values
        //devise a unified optimum method for setting and
        //updating compatible video & sensor file names
        //mStore.writeBuffered(new File(getNextSensorFilePath(mTimePerfix)),
        //mSensorString.toString());
        //TODO: or use an asyncTask instead
        mSensorWriteHandler.post(sensorWriteTask);
        //preparing the next file
        mTimePerfix = MyStorageManager.getTimePrefix();
//        mCam.setOutputFile(new File(MyStorageManager.getNextFilePath(
//                mVideoFile.getAbsolutePath(),mTimePerfix,"mp4")));
        // UI
        updateButtonState(mIsRecording);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.video: {
                if (mIsRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
                break;
            }
            case R.id.info: {
                Toast.makeText(this,"Showing sensor information!",
                        Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    private void updateButtonState(boolean state) {
        if (state) {
            mChronometer.setVisibility(View.VISIBLE);
            mChronometer.start();
            mButtonVideo.setText(R.string.btn_txt_stop);
            //mButtonVideo.setImageResource(R.drawable.ic_action_pause_over_video);
        } else {
            mChronometer.stop();
            mChronometer.setVisibility(View.INVISIBLE);
            mButtonVideo.setText(R.string.btn_txt_start);
            //mButtonVideo.setImageResource(R.drawable.ic_action_play_over_video);
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startSensorThread() {

        mSensorThread = new HandlerThread(TAG+"SensorBackground");
        mSensorThread.start();
        mSensorLooper = mSensorThread.getLooper();
        mSensorWriteHandler = new Handler(mSensorLooper);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopSensorThread() {

        mSensorThread.quitSafely();
        try {
            mSensorThread.join();
            mSensorThread = null;
            mSensorWriteHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*============================ Static ActivityRequirements interface =========================*/

    public static SensorsContainer getSensorRequirements(Context mContext, boolean useUSB) {

        SensorsContainer sensors = new SensorsContainer();

//        MyStorageManager.getSensorRequirements(mContext, sensors);
//        CameraFlyVideo.getSensorRequirements(mContext, sensors);
//        MyLocationManager.getSensorRequirements(mContext, sensors);
//        MySensorManager.getSensorRequirements(mContext, sensors);
//        if (useUSB) {
//            MyUSBManager.getSensorRequirements(mContext, sensors);
//        }

        //Log.i(TAG, Arrays.toString(sensors.getPermissions().toArray(new String[0])));
        return sensors;
    }

    public void toastMsgShort(String msg) {
        Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUsbConnection(boolean connStat) {

    }

    /*--------------------------------------------------------------------------------------------*/

    /**
     * Maybe it's better to use handlerThread instead of this
     *  because we don't have post UI work.
     */
    /*private class SensorWriteTask extends AsyncTask<String, Integer, String> {

        //private final BroadcastReceiver.PendingResult pendingResult;
        //private final Intent intent;

        /private SensorRecTask(BroadcastReceiver.PendingResult pendingResult, Intent intent) {
            this.pendingResult = pendingResult;
            this.intent = intent;
        }/

        @Override
        protected String doInBackground(String... strings) {

            Log.d(TAG,"Doing Sensor write job in background.");

            mStore.writeBuffered(new File(MyStorageManager.getNextFilePath(
                    mSensorFile.getAbsolutePath(),mTimePerfix,"csv")),
                    mSensorString.toString());
            return "";
        }

        /@Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            // Must call finish() so the BroadcastReceiver can be recycled.
            if (this.pendingResult != null) {
                this.pendingResult.finish();
            }
        }*
    }*/
}

