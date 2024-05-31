package com.dayani.m.roboplatform;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.Handler;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBaseManager.LifeCycleState;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.concurrent.Executor;


public class FlightControlActivity extends AppCompatActivity
        implements MyBackgroundExecutor.JobListener, ActivityRequirements.RequirementResolution {

    private static final String TAG = FlightControlActivity.class.getSimpleName();

    private SensorsViewModel mVM_Sensors;

    private MyBackgroundExecutor mBackgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);

        // instantiate sensors view model
        mVM_Sensors = new ViewModelProvider(this).get(SensorsViewModel.class);

        initManagers();

        mBackgroundExecutor = new MyBackgroundExecutor();
        mBackgroundExecutor.initWorkerThread(TAG);

        if (savedInstanceState == null) {

            getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, FlightControlFragment.class, null, "flight-front-panel")
                    .commit();
        }
    }

    private void initManagers() {

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyUSBManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyWifiManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyBluetoothManager.class.getSimpleName());

        //Register receivers
        for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
            manager.execute(this, LifeCycleState.ACT_CREATED);
        }
    }

    @Override
    protected void onDestroy() {

        for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
            manager.execute(this, LifeCycleState.ACT_DESTROYED);
        }

        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            MyScreenOperations.setFullScreen(this);
        }
    }

    /* ---------------------------------- Request Resolutions ----------------------------------- */

    @Override
    public void requestResolution(String[] perms) {

    }

    @Override
    public void requestResolution(int requestCode, Intent activityIntent) {

    }

    @Override
    public void requestResolution(int requestCode, IntentSenderRequest resolutionIntent) {

    }

    @Override
    public void requestResolution(Fragment targetFragment) {

        MainActivity.startNewFragment(getSupportFragmentManager(),
                R.id.fragment_container_view, targetFragment, "sensors");
    }

    /* ------------------------------------ Multi-threading ------------------------------------- */

    @Override
    public Executor getBackgroundExecutor() {

        if (mBackgroundExecutor != null) {
            return mBackgroundExecutor.getBackgroundExecutor();
        }
        return null;
    }

    @Override
    public Handler getBackgroundHandler() {

        if (mBackgroundExecutor != null) {
            return mBackgroundExecutor.getBackgroundHandler();
        }
        return null;
    }

    @Override
    public Handler getUiHandler() {

        if (mBackgroundExecutor != null) {
            return mBackgroundExecutor.getUiHandler();
        }
        return null;
    }

    @Override
    public void execute(Runnable r) {

        if (mBackgroundExecutor != null) {
            mBackgroundExecutor.execute(r);
        }
    }

    @Override
    public void handle(Runnable r) {

        if (mBackgroundExecutor != null) {
            mBackgroundExecutor.handle(r);
        }
    }

    /*============================ Static ActivityRequirements interface =========================*/

    public static class FlightControlFragment extends
            CarManualControlActivity.ManualControlFragment implements
            MyChannels.ChannelTransactions {

        //private static final String TAG = FlightControlFragment.class.getSimpleName();

//        private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
//                +'.'+TAG+".KEY_STARTED_STATE";

        private MySensorManager mSenManager;
        private boolean mbDisableSt = false;


        public FlightControlFragment() {
            // Required empty public constructor
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment FrontPanelFragment.
         */
        public static FlightControlFragment newInstance() {

            FlightControlFragment fragment = new FlightControlFragment();
            Bundle args = new Bundle();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            FragmentActivity context = requireActivity();

            SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

            mSenManager = (MySensorManager) SensorsViewModel.getOrCreateManager(
                    context, mVM_Sensors, MySensorManager.class.getSimpleName());
            // Setup the required sensors:
            mSenManager.uncheckAllSensors();
            mSenManager.updateCheckedByType(MySensorGroup.SensorType.TYPE_MOTION, Sensor.TYPE_GRAVITY, true);

            // establish connections
            // this module acts as the middle man between everything
            mUsb.registerChannel(this);
            mWifiManager.registerChannel(this);
            mBtManager.registerChannel(this);
            mSenManager.registerChannel(this);

            mSenManager.execute(context, LifeCycleState.ACT_CREATED);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();

            mSenManager.execute(requireActivity(), LifeCycleState.ACT_DESTROYED);
        }

        @Override
        protected void start() {
            super.start();
            if (mSenManager != null) {
                mSenManager.execute(requireActivity(), LifeCycleState.START_RECORDING);
            }
        }

        @Override
        protected void stop() {
            if (mSenManager != null) {
                mSenManager.execute(requireActivity(), LifeCycleState.STOP_RECORDING);
            }
            super.stop();
        }

        private byte[] processSensorMsg(MyMessages.MsgSensor msg) {

            //Log.d(TAG, "Sensor message received: " + msg);
            SensorEvent sensorEvent = msg.getSensorEvent();
            if (sensorEvent != null && sensorEvent.sensor.getType() == Sensor.TYPE_GRAVITY) {
                float[] gVec = sensorEvent.values;
                float gx = gVec[0];
                float gy = gVec[1];
                float gz = gVec[2];
                double g_abs = Math.sqrt(Math.pow(gx, 2) + Math.pow(gy, 2) + Math.pow(gz, 2));
                double gx_n = gx / g_abs;
                double gy_n = gy / g_abs;
                //private boolean mIsStarted = false;
                double gScale = 1.0;
                double gx_s = gx_n * gScale;
                double gy_s = gy_n * gScale;

                String wMsg_x = "0", wMsg_y = "0";

                if (gx_s > 0.01) {
                    wMsg_x = "w";
                }
                else if (gx_s < -0.01) {
                    wMsg_x = "s";
                }

                if (gy_s > 0.01) {
                    wMsg_y = "a";
                }
                else if (gy_s < -0.01) {
                    wMsg_y = "d";
                }

                byte[] out_x = this.interpret(wMsg_x);
                byte[] out_y = this.interpret(wMsg_y);
                byte[] out = new byte[Math.min(out_x.length, out_y.length)];

                for (int i = 0; i < out_x.length && i < out_y.length; i++) {
                    out[i] = (byte) (out_x[i] | out_y[i]);
                }

                return out;
            }
            return null;
        }

        @Override
        public byte[] interpret(String msg) {

            // This QC has 4 PWM channels (A, B, C, D, -> CW)
            // Channel A is between +x (head) and +y axis of phone
            // output: [0:A+, A-, B+, B-, C+, C-, D+, 7:D-]

            // set pins of an 8 pin port
            byte[] output = new byte[1];
            mbDisableSt = true;

            // 6-DoF command
            switch (msg) {
                case "w":
                case "up":
                    // forward
                    // increase channels A, B
                    // decrease channels C, D
                    output[0] |= 0xA5;
                    break;
                case "s":
                case "down":
                    // backward
                    output[0] |= 0x5A;
                    break;
                case "a":
                case "left":
                    // left
                    // increase A, D
                    // decrease B, C
                    output[0] |= 69;
                    break;
                case "d":
                case "right":
                    // right
                    output[0] |= 96;
                    break;
                case "e":
                case "fly":
                    // up
                    // increase all
                    output[0] |= 0x55;
                    mbDisableSt = false;
                    break;
                case "q":
                case "land":
                    // down
                    // decrease all
                    output[0] |= 0xAA;
                    mbDisableSt = false;
                    break;
                case "0":
                default:
                    mbDisableSt = false;
                    break;
            }
            return output;
        }


        /* ------------------------------- Channel Transactions --------------------------------- */

        @Override
        public void onMessageReceived(MyMessages.MyMessage msg) {

            super.onMessageReceived(msg);

            if (msg instanceof MyMessages.MsgSensor) {
                //Log.d(TAG, "Sensor message received: " + msg);
                if (!mbDisableSt) {
                    byte[] out = this.processSensorMsg((MyMessages.MsgSensor) msg);
                    if (mUsb != null) {
                        mUsb.onMessageReceived(MyDrvUsb.getCommandMessage(out));
                    }
                }
            }
        }
/*
        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {

            View view = inflater.inflate(R.layout.activity_car_manual_control, container, false);

            mBtnUsb = view.findViewById(R.id.btnCheckUsb);
            mBtnUsb.setOnClickListener(this);
            mBtnWifi = view.findViewById(R.id.btnCheckWifi);
            mBtnWifi.setOnClickListener(this);
            mBtnBt = view.findViewById(R.id.btnCheckBlth);
            mBtnBt.setOnClickListener(this);
            mBtnStart = view.findViewById(R.id.startProcess);
            mBtnStart.setOnClickListener(this);

            //TextView reportTxt = view.findViewById(R.id.reportTxt);

            return view;
        }

        @Override
        public void onResume() {
            super.onResume();

            Context context = requireActivity();
            if (mSenManager != null) mSenManager.execute(context, LifeCycleState.RESUMED);
        }

        @Override
        public void onPause() {

            Context context = requireActivity();
            if (mSenManager != null) mSenManager.execute(context, LifeCycleState.PAUSED);

            super.onPause();
        }

        @Override
        public void onStart() {
            super.onStart();

            mIsStarted = MyStateManager.getBoolPref(requireActivity(), KEY_STARTED_STATE, false);
            updateProcessUI(mIsStarted);
            // Connect to a wifi network
            //mWifiManager.setWifiState(true);
        }

        @Override
        public void onStop() {
            if (mIsStarted) {
                stop();
            }
            //mWifiManager.setWifiState(false);
            super.onStop();
        }

        @Override
        public void onClick(View view) {

            FragmentActivity context = requireActivity();

            int id = view.getId();
            if (id == R.id.btnCheckUsb) {

                if (!mUsb.isAvailable()) {
                    mUsb.resolveAvailability(context);
                }
            }
            else if (id == R.id.btnCheckWifi) {

                if (!mWifiManager.isAvailable()) {
                    mWifiManager.resolveAvailability(context);
                }
            }
            else if (id == R.id.btnCheckBlth) {

                if (mBtManager != null && !mBtManager.isAvailable()) {
                    mBtManager.resolveAvailability(context);
                }
            }
            else if (view.getId() == R.id.startProcess) {

                if (mIsStarted) {
                    stop();
                }
                else {
                    start();
                }
            }
        }



        private void updateProcessUI(boolean state) {
            if (state) {
                mBtnStart.setText(R.string.btn_txt_stop);
                //mButtonVideo.setImageResource(R.drawable.ic_action_pause_over_video);
            }
            else {
                mBtnStart.setText(R.string.btn_txt_start);
                //mButtonVideo.setImageResource(R.drawable.ic_action_play_over_video);
            }
        }

        private void updateAvailabilityUI() {

            boolean bUsb = false, bWifi = false, bBt = false;

            if (mUsb != null && mUsb.isAvailable()) {
                bUsb = true;
                mBtnUsb.setEnabled(false);
            }

            if (mWifiManager != null && mWifiManager.isAvailable()) {
                bWifi = true;
                //mBtnWifi.setEnabled(false);
            }

            if (mBtManager != null && mBtManager.isAvailable()) {
                bBt = true;
                //mBtnBt.setEnabled(false);
            }

            boolean hasWirelessConn = (bWifi || bBt);

            mBtnWifi.setEnabled(!hasWirelessConn);
            mBtnBt.setEnabled(!hasWirelessConn);

            mBtnStart.setEnabled(bUsb && hasWirelessConn);
        }
*/
    }
}