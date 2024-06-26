package com.dayani.m.roboplatform;

import androidx.activity.result.IntentSenderRequest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.concurrent.Executor;

public class RoboControllerActivity extends AppCompatActivity
        implements MyBackgroundExecutor.JobListener, ActivityRequirements.RequirementResolution {

    public enum ControllerType {
        SERVER_WL,
        SERVER_CP,
        CLIENT_CAR,
        CLIENT_FC,
    }

    private static final String TAG = RoboControllerActivity.class.getSimpleName();

    public static final String EXTRA_KEY_CONTROLLER_TYPE = TAG + ".extraKeyWithWireless";

    private SensorsViewModel mVM_Sensors;

    private MyBackgroundExecutor mBackgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);

        Intent intent = getIntent();
        ControllerType controllerType = (ControllerType) intent.getSerializableExtra(
                EXTRA_KEY_CONTROLLER_TYPE
        );
        Log.d(TAG, "Controller Type: " + controllerType);

        // instantiate sensors view model
        mVM_Sensors = new ViewModelProvider(this).get(SensorsViewModel.class);

        initManagers();

        mBackgroundExecutor = new MyBackgroundExecutor();
        mBackgroundExecutor.initWorkerThread(TAG);

        Class<? extends Fragment> controllerFragment = ConnectionListFragment.class;
        if (ControllerType.CLIENT_CAR == controllerType) {
            controllerFragment = ManualControlFragment.class;
        }
        else if (ControllerType.CLIENT_FC == controllerType) {
            controllerFragment = FlightControlFragment.class;
        }
        else if (ControllerType.SERVER_CP == controllerType) {
            controllerFragment = UsbControllerFragment.class;
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, controllerFragment, null, "car-front-panel")
                    .commit();
        }
    }

    private void initManagers() {

        //SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MySensorManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyUSBManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyWifiManager.class.getSimpleName());

        SensorsViewModel.getOrCreateManager(this, mVM_Sensors, MyBluetoothManager.class.getSimpleName());

        //Register receivers
        for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
            manager.execute(this, MyBaseManager.LifeCycleState.ACT_CREATED);
        }
    }

    @Override
    protected void onDestroy() {

        for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
            manager.execute(this, MyBaseManager.LifeCycleState.ACT_DESTROYED);
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
}