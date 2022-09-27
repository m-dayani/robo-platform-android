package com.dayani.m.roboplatform.dump;

/*
  Activity to record and store only sensor data:
       IMU, Magnet, GPS, -> No Camera. -> TODO: Add camera
  All Sensors are onboard, no external USB device attached to the cellphone.
 */

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.SensorRecordService;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.SensorsContainer;


public class RecordSensorsActivity_old extends AppCompatActivity
        implements View.OnClickListener {

    private static final String TAG = "RecordSensorsActivity";

    public static final String KEY_IS_RECORDING_STATE = AppGlobals.PACKAGE_BASE_NAME
            +".KEY_IS_RECORDING_STATE";

    private Button mBtnRecord;
    private TextView reportTxt;

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecording = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.z_activity_record_sensors);

        mBtnRecord = findViewById(R.id.record);
        mBtnRecord.setOnClickListener(this);
        findViewById(R.id.info).setOnClickListener(this);
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
        //mCam.onResume();

        // Within {@code onPause()}, we remove location updates. Here, we resume receiving
        // location updates if the user has requested them.
        //if (mLocation.checkRequestingLocUpdates() && mLocation.checkPermissions()) {
        //mLocation.startLocationUpdates();
        //} else if (!mLocation.checkPermissions()) {
        //    reportTxt.setText("You've denied location permissions!");
        //}
        //Registers sensor callbacks
        //mSensorManager.onResume();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        //mLocation.setBundleData(outState);
        // ...
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        //with foreground service we can run this indefinitly so
        //won't need this
        //if (mIsRecording) {
            //stopService();
        //}
        //mUsb.unregisterUsbSensorReciever();
        //mUsb.unregisterUsbPermission();
        //mCam.clean();

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
        //mUsb.clean();
        //mUsb.close();
        //mUsb = null;
        //cleaning sensor's thread.
        //mSensorManager.clean();

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");
        //ToDo: find a way to notify service of this or maybe not
        //if (mLocation != null) {
            //mLocation.onActivityResult(requestCode, resultCode, data);
        //}
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
    //If permission are not available, we don't do anything.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        //mCam.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //mStore.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //mLocation.onRequestPermissionsResult(requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }*/



    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.record: {
                if (mIsRecording) {
                    stopService();
                } else {
                    startService();
                }
                break;
            }
            case R.id.info: {
                Toast.makeText(this,"Record All sensors except camera.",
                        Toast.LENGTH_LONG).show();
                break;
            }
        }
    }

    private void updateButtonState(boolean state) {
        if (state) {
            //mChronometer.setVisibility(View.VISIBLE);
            //mChronometer.start();
            mBtnRecord.setText("Stop");
            //mButtonVideo.setImageResource(R.drawable.ic_action_pause_over_video);
        } else {
            //mChronometer.stop();
            //mChronometer.setVisibility(View.INVISIBLE);
            mBtnRecord.setText("Start");
            //mButtonVideo.setImageResource(R.drawable.ic_action_play_over_video);
        }
    }

    public void startService() {
        Intent serviceIntent = new Intent(this, SensorRecordService.class);
        serviceIntent.putExtra("inputExtra", "Foreground Service Example in Android");

        ContextCompat.startForegroundService(this, serviceIntent);

        mIsRecording = true;
        MyStateManager.setBoolPref(this, KEY_IS_RECORDING_STATE, mIsRecording);
        updateButtonState(mIsRecording);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, SensorRecordService.class);
        stopService(serviceIntent);

        mIsRecording = false;
        MyStateManager.setBoolPref(this, KEY_IS_RECORDING_STATE, mIsRecording);
        updateButtonState(mIsRecording);
    }

//    @Override
//    public boolean usesActivityRequirementsInterface() {
//        return true;
//    }

    /*============================ Static ActivityRequirements interface =========================*/

    public static SensorsContainer getSensorRequirements(Context mContext) {

        SensorsContainer sensors = new SensorsContainer();

//        MyStorageManager.getSensorRequirements(mContext, sensors);
//        CameraFlyVideo.getSensorRequirements(mContext, sensors);
//        MyLocationManager.getSensorRequirements(mContext, sensors);
//        MySensorManager.getSensorRequirements(mContext, sensors);

        return sensors;
    }

    public void toastMsgShort(String msg) {
        Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

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

