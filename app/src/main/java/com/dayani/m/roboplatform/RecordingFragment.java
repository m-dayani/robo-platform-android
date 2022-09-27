package com.dayani.m.roboplatform;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
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

import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.utils.cutom_views.AutoFitTextureView;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels.ChannelTransactions;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.List;


public class RecordingFragment extends Fragment
        implements View.OnClickListener, ChannelTransactions, DisplayManager.DisplayListener {

    private static final String TAG = RecordingFragment.class.getSimpleName();

    private DisplayManager mDisplayManager;

    private List<MyBaseManager> mlManagers;

    private CameraFlyVideo mCameraManager;

    private Button mButtonVideo;
    private Chronometer mChronometer;
    private TextView mReportTxt;
    private AutoFitTextureView mTextureView;

    private int mCurrScreenOrientation;

    boolean mbIsRecording = false;

    public RecordingFragment() {
        // Required empty public constructor
    }

    public static RecordingFragment newInstance() {

        RecordingFragment fragment = new RecordingFragment();
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

        // add recording fragment's logger
        for (MyBaseManager manager : mlManagers) {

            manager.registerChannel(this);

            if (manager instanceof CameraFlyVideo) {
                mCameraManager = (CameraFlyVideo) manager;
            }
        }

        mDisplayManager = (DisplayManager) requireActivity().getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_recording, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        mButtonVideo = view.findViewById(R.id.record);
        mButtonVideo.setOnClickListener(this);

        mChronometer = view.findViewById(R.id.chronometer);

        mReportTxt = view.findViewById(R.id.txtReport);
        mReportTxt.setText("");

        mTextureView = view.findViewById(R.id.texture);
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
    }

    @Override
    public void onPause() {

        FragmentActivity context = requireActivity();

        // stop if running
        if (mbIsRecording) {
            stop();
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

        int viewId = view.getId();
        if (viewId == R.id.record) {
            if (mbIsRecording) {
                stop();
            }
            else {
                start();
            }
        }
    }

    private void start() {

        mbIsRecording = true;
        updateRecordingUI(true);

        for (MyBaseManager manager : mlManagers) {
            manager.execute(requireActivity(), MyBaseManager.LifeCycleState.START_RECORDING);
        }

        MyScreenOperations.setScreenOn(requireActivity());

        Log.d(TAG, "Managers are started");
    }

    private void stop() {

        MyScreenOperations.unsetScreenOn(requireActivity());

        for (MyBaseManager manager : mlManagers) {
            manager.execute(requireActivity(), MyBaseManager.LifeCycleState.STOP_RECORDING);
        }

        mbIsRecording = false;
        updateRecordingUI(false);

        Log.d(TAG, "Managers are stopped");
    }

    private void updateRecordingUI(boolean state) {

        if (state) {
            mButtonVideo.setText(R.string.btn_txt_stop);
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.setVisibility(View.VISIBLE);
            mChronometer.start();
        }
        else {
            mButtonVideo.setText(R.string.btn_txt_start);
            mChronometer.stop();
            mChronometer.setVisibility(View.INVISIBLE);
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