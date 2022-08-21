package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.ControlInput;
import com.dayani.m.roboplatform.utils.data_types.SensorsContainer;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;

import java.util.ArrayList;
import java.util.Arrays;

public class CarManualControlActivity extends AppCompatActivity
        implements View.OnClickListener,
        MyUSBManager.OnUsbConnectionListener, MyWifiManager.OnWifiNetworkInteractionListener {

    private static final String TAG = CarManualControlActivity.class.getSimpleName();

    private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
            +'.'+TAG+".KEY_STARTED_STATE";

    private static final ArrayList<Requirement> REQUIREMENTS = new ArrayList<>(
            Arrays.asList(//Requirement.PERMISSIONS,
            Requirement.USB_DEVICE,
            Requirement.WIRELESS_CONNECTION)
    );

    private static final int DEFAULT_CTRL_INTERVAL_MILIS = 128;

    private Button mBtnStart;
    //private Chronometer mChronometer;
    //private AutoFitTextureView mTextureView;
    private TextView reportTxt;

    private boolean mIsStarted = false;

    private MyUSBManager mUsb;

    private MySensorManager mSensorManager;

    private StringBuffer mSensorString;

    private MyWifiManager mWifiManager;

    private HandlerThread mControllerThread;
    private Looper mControllerLooper;
    private Handler mController;

    private ControlInput mControlInput = new ControlInput();

    private Runnable controlTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "Message recieved, processing...");

            mControlInput.setKeyboardInput(mWifiManager.getMessage());
            mControlInput.setSensorInput(mUsb.getRawSensor());
            mUsb.setStateBuffer(this.control(mControlInput));
            mUsb.sendStateUpdates();

            mController.postDelayed(this, DEFAULT_CTRL_INTERVAL_MILIS);
        }

        /**
         *
         * @param input sensor readings
         * @return output state to controller device
         */
        protected byte[] control(ControlInput input) {

            byte[] outState = new byte[64];

            if (input.getKeyboardInput() == null) {
                outState[0] = 0;
                return outState;
            }
            switch (input.getKeyboardInput().charAt(0)) {
                case 'w':   //forward
                    Log.d(TAG, "Forward command detected.");
                    outState[0] |= 0x01;
                    //mUsb.setStateBuffer((byte) 0x01, 0);
                    break;
                case 's':   //backward
                    outState[0] |= 0x02;
                    break;
                case 'a':   //left
                    outState[0] |= 0x04;
                    break;
                case 'd':   //right
                    outState[0] |= 0x08;
                    break;
                case 'f':   //up
                    outState[0] |= 0x10;
                    break;
                case 'e':   //down
                    outState[0] |= 0x20;
                    break;
                default:
                    outState[0] &= 0x00;
                    //mUsb.setStateBuffer((byte) 0x00, 0);
                    break;
            }

            return outState;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car_manual_control);

        mBtnStart = (Button) findViewById(R.id.startPorcess);
        mBtnStart.setOnClickListener(this);
        reportTxt = findViewById(R.id.reportTxt);

        mSensorString = new StringBuffer();

        mSensorManager = new MySensorManager(this);

        mUsb = new MyUSBManager(this, this, mSensorString);
        mUsb.updateDefaultDeviceAvailability();
        mUsb.tryOpenDefaultDevice();

        mWifiManager = new MyWifiManager(this, this);
        //Register receivers & start Bg threads
        mWifiManager.init();

        startSensorThread();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mIsStarted = MyStateManager.getBoolPref(this, KEY_STARTED_STATE, false);
        updateButtonState(mIsStarted);
        // Connect to a wifi network
        mWifiManager.setWifiState(true);
    }

    @Override
    protected void onStop() {
        if (mIsStarted) {
            stop();
        }
        mWifiManager.setWifiState(false);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mWifiManager.clean();
        stopSensorThread();
        super.onDestroy();
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

    private void start() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //mSensorManager.start();
        //mUsb.startPeriodicSensorPoll();
        mWifiManager.startServer();

        mController.postDelayed(controlTask, 0);

        mIsStarted = true;
        MyStateManager.setBoolPref(this, KEY_STARTED_STATE, mIsStarted);
        // UI
        updateButtonState(mIsStarted);
    }

    private void stop() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //mSensorManager.stop();
        //mUsb.stopPeriodicSensorPoll();
        mWifiManager.stopServer();

        mController.removeCallbacks(controlTask);

        mIsStarted = false;
        MyStateManager.setBoolPref(this, KEY_STARTED_STATE, mIsStarted);
        // UI
        updateButtonState(mIsStarted);
    }

    private void updateButtonState(boolean state) {
        if (state) {
            mBtnStart.setText("Stop");
            //mButtonVideo.setImageResource(R.drawable.ic_action_pause_over_video);
        } else {
            mBtnStart.setText("Start");
            //mButtonVideo.setImageResource(R.drawable.ic_action_play_over_video);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.startPorcess:
                if (mIsStarted) {
                    stop();
                } else {
                    start();
                }
                break;
            default:
                break;
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startSensorThread() {

        mControllerThread = new HandlerThread(TAG+"SensorBackground");
        mControllerThread.start();
        mControllerLooper = mControllerThread.getLooper();
        mController = new Handler(mControllerLooper);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopSensorThread() {

        mControllerThread.quitSafely();
        try {
            mControllerThread.join();
            mControllerThread = null;
            mController = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*============================ Static ActivityRequirements interface =========================*/
//    @Override
//    public boolean usesActivityRequirementsInterface() {
//        return true;
//    }
//
//    public static ArrayList<Requirement> getActivityRequirements() {
//        return REQUIREMENTS;
//    }
//
//    public static String[] getActivityPermissions() {
//        String[] allPermissions = null;
//        Log.i(TAG, Arrays.toString(allPermissions));
//        return allPermissions;
//    }
    /*============================ Static ActivityRequirements interface =========================*/

    public static SensorsContainer getSensorRequirements(Context mContext) {

        SensorsContainer sensors = new SensorsContainer();

//        MyUSBManager.getSensorRequirements(mContext, sensors);
//        MyWifiManager.getSensorRequirements(mContext, sensors);

        return sensors;
    }

    public void toastMsgShort(String msg) {
        Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUsbConnection(boolean connStat) {

    }

    @Override
    public void onWifiEnabled() {

    }

    @Override
    public void onClientConnected() {

    }

    @Override
    public void onInputReceived(String msg) {

    }
}
