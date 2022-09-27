package com.dayani.m.roboplatform;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBaseManager.LifeCycleState;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.concurrent.Executor;


public class CarManualControlActivity extends AppCompatActivity
        implements MyBackgroundExecutor.JobListener, ActivityRequirements.RequirementResolution {

    private static final String TAG = CarManualControlActivity.class.getSimpleName();

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
                    .add(R.id.fragment_container_view, ManualControlFragment.class, null, "car-front-panel")
                    .commit();
        }
    }

    private void initManagers() {

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MySensorManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyUSBManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyWifiManager.class.getSimpleName());

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

    public static class ManualControlFragment extends Fragment
            implements View.OnClickListener {

        private static final String TAG = ManualControlFragment.class.getSimpleName();

        private static final String KEY_STARTED_STATE = AppGlobals.PACKAGE_BASE_NAME
                +'.'+TAG+".KEY_STARTED_STATE";

        private Button mBtnStart;
        //private Chronometer mChronometer;
        //private AutoFitTextureView mTextureView;
        private TextView reportTxt;

        private boolean mIsStarted = false;

        private MyUSBManager mUsb;

        private MyWifiManager mWifiManager;

        MyBackgroundExecutor.JobListener mBackgroundHandler;


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

            // establish connections
            mUsb.registerChannel(mWifiManager);
            mWifiManager.registerChannel(mUsb);

            if (context instanceof MyBackgroundExecutor.JobListener) {
                mBackgroundHandler = (MyBackgroundExecutor.JobListener) context;
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {

            View view = inflater.inflate(R.layout.activity_car_manual_control, container, false);

            mBtnStart = view.findViewById(R.id.startPorcess);
            mBtnStart.setOnClickListener(this);
            reportTxt = view.findViewById(R.id.reportTxt);

            return view;
        }

        @Override
        public void onStart() {
            super.onStart();

            mIsStarted = MyStateManager.getBoolPref(requireActivity(), KEY_STARTED_STATE, false);
            updateButtonState(mIsStarted);
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

            if (view.getId() == R.id.startPorcess) {

                FragmentActivity context = requireActivity();

                // check and resolve availability
                if (!mUsb.isAvailable()) {
                    mUsb.resolveAvailability(context);
                    return;
                }
                if (!mWifiManager.isAvailable()) {
                    mWifiManager.resolveAvailability(context);
                    return;
                }

                if (mIsStarted) {
                    stop();
                }
                else {
                    start();
                }
            }
        }

        private void start() {

            FragmentActivity context = requireActivity();

            MyScreenOperations.setScreenOn(context);

            if (mUsb != null) {
                mUsb.tryOpenDeviceAndUpdateInfo();
            }

            mIsStarted = true;
            MyStateManager.setBoolPref(context, KEY_STARTED_STATE, true);
            // UI
            updateButtonState(mIsStarted);
        }

        private void stop() {

            FragmentActivity context = requireActivity();

            MyScreenOperations.unsetScreenOn(context);

            if (mUsb != null) {
                mUsb.close();
            }

            mIsStarted = false;
            MyStateManager.setBoolPref(context, KEY_STARTED_STATE, false);
            // UI
            updateButtonState(mIsStarted);
        }

        private void updateButtonState(boolean state) {
            if (state) {
                mBtnStart.setText(R.string.btn_txt_stop);
                //mButtonVideo.setImageResource(R.drawable.ic_action_pause_over_video);
            }
            else {
                mBtnStart.setText(R.string.btn_txt_start);
                //mButtonVideo.setImageResource(R.drawable.ic_action_play_over_video);
            }
        }
    }
}
