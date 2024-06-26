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



public class FlightControlFragment extends
        ManualControlFragment implements
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
}
