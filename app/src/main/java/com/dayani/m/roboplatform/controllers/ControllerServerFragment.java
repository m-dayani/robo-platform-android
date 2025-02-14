package com.dayani.m.roboplatform.controllers;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.MainActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.drivers.MyDrvWireless;
import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.cutom_views.AutoFitTextureView;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels.ChannelTransactions;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless.WirelessCommand;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;


public class ControllerServerFragment extends Fragment
        implements View.OnClickListener, ChannelTransactions,
        DisplayManager.DisplayListener, ActivityRequirements.OnRequirementResolved {

    private static final String TAG = ControllerServerFragment.class.getSimpleName();

    private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
            +'.'+TAG+".KEY_STARTED_STATE";

    private DisplayManager mDisplayManager;

    private List<MyBaseManager> mlManagers;

    private CameraFlyVideo mCameraManager;

    protected Button mBtnStart;
    protected Button mBtnWifi;
    protected Button mBtnBt;
    protected Button mBtnUsb;

    private TextView mReportTxt;
    private AutoFitTextureView mTextureView;

    private int mCurrScreenOrientation;

    boolean mbIsRecording = false;
    protected boolean mIsStarted = false;

    protected MyBaseManager mManager;
    protected MyUSBManager mUsb;
    protected MyWifiManager mWifiManager;
    protected MyBluetoothManager mBtManager;

    protected MyBackgroundExecutor.JobListener mBackgroundHandler;

    public ControllerServerFragment() {
        // Required empty public constructor
    }

    public static ControllerServerFragment newInstance() {

        ControllerServerFragment fragment = new ControllerServerFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        // Retrieve sensors info to know which one should be operational and ...
        SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        // setup managers
        mlManagers = mVM_Sensors.getAllManagers();

        mUsb = (MyUSBManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyUSBManager.class.getSimpleName());

        mWifiManager = (MyWifiManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyWifiManager.class.getSimpleName());

        mBtManager = (MyBluetoothManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyBluetoothManager.class.getSimpleName());

        // todo: also provide Bluetooth connection
        mManager = mWifiManager;
        if (mManager != null) {
            mManager.setRequirementResponseListener(this);
        }

        // add recording fragment's logger
        for (MyBaseManager manager : mlManagers) {

            manager.registerChannel(this);

            if (manager instanceof CameraFlyVideo) {
                mCameraManager = (CameraFlyVideo) manager;
            }
        }

        if (context instanceof MyBackgroundExecutor.JobListener) {
            mBackgroundHandler = (MyBackgroundExecutor.JobListener) context;
        }

        mDisplayManager = (DisplayManager) requireActivity().getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    public void onDestroy() {

        for (MyBaseManager manager : mlManagers) {

            manager.unregisterChannel(this);
        }
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_controller_server, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        mBtnUsb = view.findViewById(R.id.connUsb);
        mBtnUsb.setOnClickListener(this);
        mBtnWifi = view.findViewById(R.id.connWifi);
        mBtnWifi.setOnClickListener(this);
        mBtnBt = view.findViewById(R.id.connBt);
        mBtnBt.setOnClickListener(this);
        mBtnStart = view.findViewById(R.id.startCtrlServer);
        mBtnStart.setOnClickListener(this);

        mReportTxt = view.findViewById(R.id.txtReport);
        mReportTxt.setText("");

        mTextureView = view.findViewById(R.id.texture);
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
            stopComm();
        }
        //mWifiManager.setWifiState(false);
        super.onStop();
    }

    @Override
    public void onResume() {

        super.onResume();
        FragmentActivity context = requireActivity();

        // configure the screen (Landscape, Fullscreen)
        mCurrScreenOrientation = getResources().getConfiguration().orientation;
        if (mCurrScreenOrientation == Configuration.ORIENTATION_PORTRAIT) {
            MyScreenOperations.setLandscape(context);
            return;
        }
        MyScreenOperations.setFullScreen(context);

        // configure texture view
        if (mTextureView != null && !mTextureView.isAvailable()) {
            mTextureView.setSurfaceTextureListener(getTextureListener());
        }

        // configure onOrientationListener
        if (mDisplayManager != null) {
            // WARNING: This is very dangerous (calls the callback continuously)
            mDisplayManager.registerDisplayListener(this, null);
        }

        if (mlManagers != null) {
            // preconfigure managers for faster start
            for (MyBaseManager manager : mlManagers) {
                manager.execute(context, MyBaseManager.LifeCycleState.RESUMED);
            }
        }

        updateAvailabilityUI();

//        startSensors();
    }

    @Override
    public void onPause() {

        FragmentActivity context = requireActivity();

        // stop if running
        if (mbIsRecording) {
            stopSensors();
        }

        // clean managers config.
        if (mlManagers != null) {
            for (MyBaseManager manager : mlManagers) {
                manager.execute(context, MyBaseManager.LifeCycleState.PAUSED);
            }
        }

        // stop listening on orientation change
        if (mDisplayManager != null) {
            mDisplayManager.unregisterDisplayListener(this);
        }

        // reset the screen to normal state
        if (mCurrScreenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            MyScreenOperations.unsetLandscape(context);
        }
        MyScreenOperations.unsetFullScreen(context);

        super.onPause();
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
        else if (view.getId() == R.id.startCtrlServer) {

//            if (mIsStarted) {
//                stopComm();
//            }
//            else {
//                startComm();
//            }
            if (mbIsRecording) {
                stopSensors();
            }
            else {
                startSensors();
            }
        }
    }

    protected void startComm() {

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

    protected void stopComm() {

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

    private void startSensors() {

        // todo: can also put these in onStart/onStop
        startServer();

        mbIsRecording = true;
        updateProcessUI(true);

        for (MyBaseManager manager : mlManagers) {
            manager.execute(requireActivity(), MyBaseManager.LifeCycleState.START_RECORDING);
        }

        MyScreenOperations.setScreenOn(requireActivity());

        Log.d(TAG, "Managers are started");
    }

    private void stopSensors() {

        MyScreenOperations.unsetScreenOn(requireActivity());

        for (MyBaseManager manager : mlManagers) {
            manager.execute(requireActivity(), MyBaseManager.LifeCycleState.STOP_RECORDING);
        }

        mbIsRecording = false;
        updateProcessUI(false);

        stopServer();

        Log.d(TAG, "Managers are stopped");
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

    protected void updateAvailabilityUI() {

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
//        mBtnStart.setEnabled(bUsb && hasWirelessConn);
        mBtnStart.setEnabled(true);
    }

    private void startServer() {

        if (mManager instanceof MyWifiManager) {
            ((MyWifiManager) mManager).startServer();
        }
        else if (mManager instanceof MyBluetoothManager) {
            ((MyBluetoothManager) mManager).startServer();
        }
    }

    private void stopServer() {

        if (mManager instanceof MyWifiManager) {
            ((MyWifiManager) mManager).stopServer();
        }
        if (mManager instanceof MyBluetoothManager) {
            ((MyBluetoothManager) mManager).stopServer();
        }
    }


    /**
     * Note: This callback automatically configures surfaces on surface available
     * @param context activity context
     * @return TextureView.SurfaceTextureListener
     */
    private TextureView.SurfaceTextureListener getTextureListener() {
        return new TextureView.SurfaceTextureListener() {

            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                                  int width, int height) {

                Log.v(TAG, "Surface texture is available");
                if (mCameraManager != null) {

                    Size viewSize = new Size(width, height);
                    mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
                            CameraFlyVideo.PreviewConfigState.AVAILABLE);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                    int width, int height) {

                Log.v(TAG, "Surface texture size changed");
                if (mCameraManager != null) {

                    Size viewSize = new Size(width, height);
                    mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
                            CameraFlyVideo.PreviewConfigState.CHANGED);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {

                Log.v(TAG, "Surface texture destroyed");
                if (mCameraManager != null) {
                    mCameraManager.notifyPreviewChanged(mTextureView, null,
                            CameraFlyVideo.PreviewConfigState.DESTROYED);
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            }
        };
    }

    /**
     * An {@link OrientationEventListener} used to determine when device rotation has occurred.
     * This is mainly necessary for when the device is rotated by 180 degrees, in which case
     * onCreate or onConfigurationChanged is not called as the view dimensions remain the same,
     * but the orientation of the has changed, and thus the preview rotation must be updated.
     */
    private OrientationEventListener getOrientationListener() {

        return new OrientationEventListener(getActivity(),
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {

                Log.v(TAG, "Orientation changed");

                if (mCameraManager != null && mTextureView != null && mTextureView.isAvailable()) {

                    Size viewSize = new Size(mTextureView.getWidth(), mTextureView.getHeight());
                    mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
                            CameraFlyVideo.PreviewConfigState.CHANGED);
                }
            }
        };
    }

    /* ------------------------------------ Display Changes ------------------------------------- */

    @Override
    public void onDisplayChanged(int i) {

        if (mDisplayManager != null) {

            Display display = mDisplayManager.getDisplay(i);
            int rot = display.getRotation();

            Log.v(TAG, "onDisplayChanged: Display rotation: "+rot);

            if (mCameraManager != null && mTextureView != null && mTextureView.isAvailable()) {

                Size viewSize = new Size(mTextureView.getWidth(), mTextureView.getHeight());
                mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
                        CameraFlyVideo.PreviewConfigState.DETECT_180);
            }
        }
    }

    @Override
    public void onDisplayAdded(int i) {

    }

    @Override
    public void onDisplayRemoved(int i) {

    }

    /* ------------------------------------ Logging channel ------------------------------------- */

    @Override
    public void onMessageReceived(MyMessages.MyMessage msg) {

        if (msg == null) {
            return;
        }

        MyMessages.MsgWireless msgSensorWl = null;
//        Log.i(TAG, msg.toString());
//        if (msg instanceof MyMessages.MsgSensor) {
//            MyMessages.MsgSensor msgSensor = (MyMessages.MsgSensor) msg;
//            SensorEvent sEvent = msgSensor.getSensorEvent();
////            if (sEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
////                Log.d(TAG, "Received Accl");
////            }
////            else if (sEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
////                Log.d(TAG, "Received Gyro");
////            }
////            if (sEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
////                Log.d(TAG, "Received Mag");
////            }
//        }
//        else if (msg instanceof MyMessages.MsgLocation) {
//            Log.d(TAG, "Received Location");
//        }
        if (msg instanceof MyMessages.MsgImage) {
//            Log.d(TAG, "Received image");
            MyMessages.MsgImage imgMsg = (MyMessages.MsgImage) msg;
//            String imgString = new String(imgMsg.getData(), StandardCharsets.UTF_8);
//            String imgStringAscii = new String(imgMsg.getData(), StandardCharsets.US_ASCII);
//            String imgStringIso = new String(imgMsg.getData(), StandardCharsets.ISO_8859_1);
//            String imgStr = new String(imgMsg.getData(), StandardCharsets.US_ASCII);
            byte[] imgBytes = imgMsg.getData();
            msgSensorWl = new MyMessages.MsgWireless(WirelessCommand.SENSOR, imgBytes);
//            mManager.onMessageReceived(msg);
        }

        if (msgSensorWl != null && mManager != null) {
            mManager.onMessageReceived(msgSensorWl);
        }

        // check the tag

        // todo: link sensor messages to servers (send messages to clients)
        String msgTag = msg.getChTag();
        if (msgTag != null && !msgTag.equals(TAG)) {
            return;
        }

        if (mReportTxt != null && msg instanceof MyMessages.MsgLogging) {

            // only respond to logging messages
            // this logger doesn't check targetId (logging level)
            mReportTxt.append(msg.toString());
        }
    }

    @Override
    public void registerChannel(ChannelTransactions channel) {

    }

    @Override
    public void unregisterChannel(ChannelTransactions channel) {

    }

    @Override
    public void publishMessage(MyMessages.MyMessage msg) {

    }



    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

        boolean bIsConnected = false;
        if (mManager instanceof MyWifiManager) {
            bIsConnected = ((MyWifiManager) mManager).isConnected();
        }
        else if (mManager instanceof MyBluetoothManager) {
            bIsConnected = true; // todo: check connection
        }

        if (bIsConnected) {
            Log.i(TAG, "starting control panel fragment");
//            Fragment frag = z_ControlPanelFragment.newInstance(mManager.getClass().getSimpleName());
//            MainActivity.startNewFragment(getParentFragmentManager(),
//                    R.id.fragment_container_view, frag, "control-panel");
        }
    }
}