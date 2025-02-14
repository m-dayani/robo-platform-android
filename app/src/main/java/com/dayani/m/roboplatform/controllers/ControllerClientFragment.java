package com.dayani.m.roboplatform.controllers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.MainActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.ConnectionListFragment;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.cutom_views.AutoFitTextureView;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels.ChannelTransactions;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;
import com.dayani.m.thirdparty.virtual_joystick.JoystickView;

import java.util.List;


public class ControllerClientFragment extends Fragment
        implements View.OnClickListener, ChannelTransactions, DisplayManager.DisplayListener,
            View.OnTouchListener {

    private static final String TAG = ControllerClientFragment.class.getSimpleName();

    private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
            +'.'+TAG+".KEY_STARTED_STATE";

    private DisplayManager mDisplayManager;

//    private List<MyBaseManager> mlManagers;
//    private CameraFlyVideo mCameraManager;
    protected MyWifiManager mWifiManager;
    protected MyBluetoothManager mBtManager;

    private Button mBtnWifi;
    private Button mBtnBlt;
    private Button mBtnStart;

    protected boolean mIsStarted = false;

    private boolean[] mbStatesCmdPb = new boolean[4];
    private boolean[] mbStatesCmdSw = new boolean[4];
    private float[] mfJoyCmd = new float[4];

    private TextView mReportTxt;

    private AutoFitTextureView mTextureView;

    private int mCurrScreenOrientation;

    boolean mbIsConnected = false;

    protected MyBackgroundExecutor.JobListener mBackgroundHandler;

    public ControllerClientFragment() {
        // Required empty public constructor
    }

    public static ControllerClientFragment newInstance() {

        ControllerClientFragment fragment = new ControllerClientFragment();
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
        mWifiManager = (MyWifiManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyWifiManager.class.getSimpleName());

        mBtManager = (MyBluetoothManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyBluetoothManager.class.getSimpleName());

        // establish connections
        mWifiManager.registerChannel(this);
        mBtManager.registerChannel(this);

        if (context instanceof MyBackgroundExecutor.JobListener) {
            mBackgroundHandler = (MyBackgroundExecutor.JobListener) context;
        }

        mDisplayManager = (DisplayManager) requireActivity().getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    public void onDestroy() {

        mWifiManager.unregisterChannel(this);
        mBtManager.unregisterChannel(this);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_controller_client, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        mBtnWifi = view.findViewById(R.id.connWifi);
        mBtnWifi.setOnClickListener(this);
        mBtnBlt = view.findViewById(R.id.connBt);
        mBtnBlt.setOnClickListener(this);
        mBtnStart = view.findViewById(R.id.startCtrlClient);
        mBtnStart.setOnClickListener(this);

        view.findViewById(R.id.cmd_pb0).setOnTouchListener(this);
        view.findViewById(R.id.cmd_sw0).setOnClickListener(this);
        view.findViewById(R.id.cmd_sw1).setOnClickListener(this);
        view.findViewById(R.id.cmd_sw2).setOnClickListener(this);

        JoystickView joystickLeft = view.findViewById(R.id.joystickViewLeft);
        joystickLeft.setOnMoveListener((angle, strength) -> updateJoyCmd(angle, strength, true));
        JoystickView joystickRight = view.findViewById(R.id.joystickViewRight);
        joystickRight.setOnMoveListener((angle, strength) -> updateJoyCmd(angle, strength, false));

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
            stop();
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

        updateAvailabilityUI();
    }

    @Override
    public void onPause() {

        FragmentActivity context = requireActivity();

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
        if (id == R.id.connWifi) {

            if (!mWifiManager.isAvailable()) {
                mWifiManager.resolveAvailability(context);
            }
        }
        else if (id == R.id.connBt) {

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
        else if (id == R.id.cmd_sw0) {
            
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v.getId() == R.id.cmd_pb0) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mbStatesCmdPb[0] = true;
                Log.d(TAG, "Push button is pressed");
            }
            else if (event.getAction() == MotionEvent.ACTION_UP) {
                mbStatesCmdPb[0] = false;
            }
        }
        v.performClick();
        return true;
    }

    void updateJoyCmd(int angle, int strength, boolean left) {
        float ang_rad = (float) (angle * Math.PI / 180.0);
        float x = (float) (strength / 100.0 * Math.cos(ang_rad) * 255.0);
        float y = (float) (strength / 100.0 * Math.sin(ang_rad) * 255.0);
        if (left) {
            mfJoyCmd[2] = x;
            mfJoyCmd[3] = y;
        }
        else {
            mfJoyCmd[0] = x;
            mfJoyCmd[1] = y;
        }
    }

    void sendCtrlMsg() {
        // serialize the control
        // send to the active command socket
    }

    void onReceiveSensorData() {
        // get image data
        // update texture view
        // or
        // get IMU, GPS, ...
        // show/record/...
    }

    protected void start() {

        FragmentActivity context = requireActivity();

        MyScreenOperations.setScreenOn(context);

        mIsStarted = true;
        MyStateManager.setBoolPref(context, KEY_STARTED_STATE, true);
        // UI
        updateProcessUI(mIsStarted);
    }

    protected void stop() {

        FragmentActivity context = requireActivity();

        MyScreenOperations.unsetScreenOn(context);

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

    protected void updateAvailabilityUI() {

        boolean bWifi = false, bBt = false;


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
        mBtnBlt.setEnabled(!hasWirelessConn);
        mBtnStart.setEnabled(hasWirelessConn);
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
//                if (mCameraManager != null) {
//
//                    Size viewSize = new Size(width, height);
//                    mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
//                            CameraFlyVideo.PreviewConfigState.AVAILABLE);
//                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                    int width, int height) {

                Log.v(TAG, "Surface texture size changed");
//                if (mCameraManager != null) {
//
//                    Size viewSize = new Size(width, height);
//                    mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
//                            CameraFlyVideo.PreviewConfigState.CHANGED);
//                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {

                Log.v(TAG, "Surface texture destroyed");
//                if (mCameraManager != null) {
//                    mCameraManager.notifyPreviewChanged(mTextureView, null,
//                            CameraFlyVideo.PreviewConfigState.DESTROYED);
//                }
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

//                if (mCameraManager != null && mTextureView != null && mTextureView.isAvailable()) {
//
//                    Size viewSize = new Size(mTextureView.getWidth(), mTextureView.getHeight());
//                    mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
//                            CameraFlyVideo.PreviewConfigState.CHANGED);
//                }
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

//            if (mCameraManager != null && mTextureView != null && mTextureView.isAvailable()) {
//
//                Size viewSize = new Size(mTextureView.getWidth(), mTextureView.getHeight());
//                mCameraManager.notifyPreviewChanged(mTextureView, viewSize,
//                        CameraFlyVideo.PreviewConfigState.DETECT_180);
//            }
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

        // check the tag
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
}