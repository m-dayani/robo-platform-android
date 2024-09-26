package com.dayani.m.roboplatform;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.managers.MyBaseManager.LifeCycleState;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.helpers.QuadController;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb.UsbCommand;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.Arrays;


public class FlightControlFragment extends
        ManualControlFragment implements
        MyChannels.ChannelTransactions {

    private static final String TAG = FlightControlFragment.class.getSimpleName();

//        private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
//                +'.'+TAG+".KEY_STARTED_STATE";

    private MySensorManager mSenManager;
//    private boolean mbDisableSt = true;

    private final QuadController mQcController;
    private final boolean mTestMode = true;


    public FlightControlFragment() {
        // Required empty public constructor
        mQcController = new QuadController(180);
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
        boolean res = mSenManager.updateCheckedByType(SensorType.TYPE_MOTION, Sensor.TYPE_GRAVITY, true);
        if (!res) {
            mSenManager.updateCheckedByType(SensorType.TYPE_IMU, Sensor.TYPE_ACCELEROMETER, true);
        }
        mSenManager.updateCheckedByType(SensorType.TYPE_IMU, Sensor.TYPE_GYROSCOPE, true);

        // establish connections
        // this module acts as the middle man between everything
        mUsb.registerChannel(this);
        mWifiManager.registerChannel(this);
        mBtManager.registerChannel(this);
        mSenManager.registerChannel(this);

        mSenManager.execute(context, LifeCycleState.ACT_CREATED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (mTestMode) {
            if (mBtnUsb != null) {
                mBtnUsb.setEnabled(false);
            }
            if (mBtnWifi != null) {
                mBtnWifi.setEnabled(false);
            }
            if (mBtnBt != null) {
                mBtnBt.setEnabled(false);
            }
            if (mBtnStart != null) {
                mBtnStart.setEnabled(true);
            }
        }
    }

    @Override
    protected void updateAvailabilityUI() {
        if (!mTestMode) {
            super.updateAvailabilityUI();
        }
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

    @Override
    public byte[] interpret(String msg) {

        // This QC has 4 PWM channels (A, B, C, D, -> CW)
        // Channel A is between +x (head) and +y axis of phone
        // output: [0:A+, A-, B+, B-, C+, C-, D+, 7:D-]

        // set pins of an 8 pin port
        byte[] output = new byte[1];
//       mbDisableSt = true;

        // 6-DoF command
        switch (msg) {
            case "w":
            case "up":
                // forward
                // increase channels A, B
                // decrease channels C, D
                output[0] |= (byte) 0xA5;
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
                output[0] |= 0x69;
                break;
            case "d":
            case "right":
                // right
                output[0] |= (byte) 0x96;
                break;
            case "e":
            case "fly":
                // up
                // increase all
                output[0] |= 0x55;
//                mbDisableSt = false;
                break;
            case "q":
            case "land":
                // down
                // decrease all
                output[0] |= (byte) 0xAA;
//                mbDisableSt = false;
                break;
            case "0":
            default:
//                mbDisableSt = false;
                break;
        }
        Log.v(TAG, "interpret: send command: " + Arrays.toString(output));
        return output;
    }

    private float[] cmd2input(String msg) {

        // Input: [throttle, roll, pitch, yaw]
        float[] input = new float[4];
        float scale = 1.f;

        switch (msg) {
            case "w":
            case "up":
                // forward
                input[2] = scale;
                break;
            case "s":
            case "down":
                // backward
                input[2] = -scale;
                break;
            case "a":
            case "left":
                // left
                input[1] = scale;
                break;
            case "d":
            case "right":
                // right
                input[1] = -scale;
                break;
            case "e":
            case "fly":
                // up
                input[0] = scale;
                break;
            case "q":
            case "land":
                // down
                input[0] = -scale;
                break;
            case "r":
            case "turn_cw":
                // clockwise yaw
                input[3] = scale;
                break;
            case "f":
            case "turn_ccw":
                // ccw yaw
                input[3] = -scale;
                break;
            case "0":
            default:
                break;
        }

        return input;
    }

    /* ------------------------------- Channel Transactions --------------------------------- */

    @Override
    public void onMessageReceived(MyMessages.MyMessage msg) {

        if (msg instanceof MyMessages.MsgWireless) {
            //Log.v(TAG, "Wireless message received: " + msg);
            if (mUsb != null) {

                MyMessages.MsgWireless wMsg = (MyMessages.MsgWireless) msg;
                MyMessages.MsgWireless.WirelessCommand wCmd = wMsg.getCmd();

                if (wCmd.equals(MyMessages.MsgWireless.WirelessCommand.CMD_CHAR) ||
                        wCmd.equals(MyMessages.MsgWireless.WirelessCommand.CMD_DIR)) {

                    mQcController.updateInput(cmd2input(msg.toString()));
                }
            }
        }
        else if (msg instanceof MyMessages.MsgSensor) {
//            Log.v(TAG, "Sensor message received: " + msg);
            MyMessages.MsgSensor msgSen = (MyMessages.MsgSensor) msg;
            SensorEvent sensorEvent = msgSen.getSensorEvent();
            mQcController.updateSensor(sensorEvent);
        }
        else if (msg instanceof MsgUsb) {
//            Log.d(TAG, "USB message received: " + msg);
            MsgUsb msgUsb = (MsgUsb) msg;
            if (msgUsb.getCmd().equals(UsbCommand.CMD_GET_CMD_RES)) {
                byte[] resBuff = msgUsb.getRawBuffer();
                mQcController.updateState(resBuff);
            }
        }
//        else if (msg.getChTag() != null && msg.getChTag().equals("usb-response")) {
//            byte[] resBuff = msg.toString().getBytes(StandardCharsets.US_ASCII);
//            mQcController.updateState(resBuff);
//        }
//        else {
//            Log.d(TAG, "Unknown message received: " + msg);
//        }

        if (mUsb != null && mQcController.isReady()) {
            byte[] transBuff = mQcController.getLastState();
            MsgUsb outMsg = MyDrvUsb.getCommandMessage(UsbCommand.CMD_UPDATE_OUTPUT, "0");
            byte[] buffer = MyDrvUsb.encodeUsbCommand(transBuff);
            outMsg.setRawBuffer(buffer);
            Log.d(TAG, Arrays.toString(buffer));
            mUsb.onMessageReceived(outMsg);
        }
    }
}
