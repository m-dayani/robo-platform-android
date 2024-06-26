package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;


public class ManualControlFragment extends Fragment
        implements View.OnClickListener, MyDrvUsb.UsbCmdInterpreter,
        MyChannels.ChannelTransactions {

    private static final String TAG = ManualControlFragment.class.getSimpleName();

    private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
            +'.'+TAG+".KEY_STARTED_STATE";

    protected Button mBtnStart;
    protected Button mBtnWifi;
    protected Button mBtnBt;
    protected Button mBtnUsb;

    protected boolean mIsStarted = false;

    protected MyUSBManager mUsb;
    protected MyWifiManager mWifiManager;
    protected MyBluetoothManager mBtManager;

    protected MyBackgroundExecutor.JobListener mBackgroundHandler;


    public ManualControlFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FrontPanelFragment.
     */
    public static ManualControlFragment newInstance() {

        ManualControlFragment fragment = new ManualControlFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        mUsb = (MyUSBManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyUSBManager.class.getSimpleName());

        mWifiManager = (MyWifiManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyWifiManager.class.getSimpleName());

        mBtManager = (MyBluetoothManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyBluetoothManager.class.getSimpleName());

        // establish connections
        mWifiManager.registerChannel(this);
        mBtManager.registerChannel(this);
        mUsb.registerChannel(this);

        if (context instanceof MyBackgroundExecutor.JobListener) {
            mBackgroundHandler = (MyBackgroundExecutor.JobListener) context;
        }
    }

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
    public void onResume() {
        super.onResume();
        updateAvailabilityUI();
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

    protected void start() {

        FragmentActivity context = requireActivity();

        MyScreenOperations.setScreenOn(context);

        if (mUsb != null) {
            mUsb.tryOpenDeviceAndUpdateInfo();
        }

        mIsStarted = true;
        MyStateManager.setBoolPref(context, KEY_STARTED_STATE, true);
        // UI
        updateProcessUI(mIsStarted);
    }

    protected void stop() {

        FragmentActivity context = requireActivity();

        MyScreenOperations.unsetScreenOn(context);

        if (mUsb != null) {
            mUsb.close();
        }

        mIsStarted = false;
        MyStateManager.setBoolPref(context, KEY_STARTED_STATE, false);
        // UI
        updateProcessUI(mIsStarted);
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

    @Override
    public byte[] interpret(String msg) {
        // This toy car has a single ON/OFF directional control
        // and a digital tri-state enable pin (ON/OFF/No Change)

        // set pins of an 8 pin port
        byte[] output = new byte[1];

        // 6-DoF command
        switch (msg) {
            case "w":
            case "up":
                // forward
                output[0] |= 0x04;
                break;
            case "q":
                // disable
                output[0] |= 0x02;
                break;
            case "e":
                // enable
                output[0] |= 0x01;
                break;
            case "0":
            default:
                break;
        }
        return output;
    }

    @Override
    public void registerChannel(MyChannels.ChannelTransactions channel) {

    }

    @Override
    public void unregisterChannel(MyChannels.ChannelTransactions channel) {

    }

    @Override
    public void publishMessage(MyMessages.MyMessage msg) {

    }

    @Override
    public void onMessageReceived(MyMessages.MyMessage msg) {

        if (msg instanceof MyMessages.MsgUsb) {
            Log.d(TAG, "USB message received: " + msg);
        }
        else if (msg instanceof MyMessages.MsgWireless) {
            //Log.v(TAG, "Wireless message received: " + msg);
            if (mUsb != null) {
                MyMessages.MsgWireless wMsg = (MyMessages.MsgWireless) msg;
                MyMessages.MsgWireless.WirelessCommand wCmd = wMsg.getCmd();

                if (wCmd.equals(MyMessages.MsgWireless.WirelessCommand.CMD_CHAR) ||
                        wCmd.equals(MyMessages.MsgWireless.WirelessCommand.CMD_DIR)) {

                    mUsb.onMessageReceived(MyDrvUsb.getCommandMessage(this, wMsg.toString()));
                }
            }
        }
        else {
            Log.d(TAG, "Unknown message received: " + msg);
        }
    }
}

