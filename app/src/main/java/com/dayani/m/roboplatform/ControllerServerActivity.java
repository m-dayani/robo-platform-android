package com.dayani.m.roboplatform;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.concurrent.Executor;

public class ControllerServerActivity extends AppCompatActivity
        implements MyBackgroundExecutor.JobListener, ActivityRequirements.RequirementResolution {

    private static final String TAG = ControllerServerActivity.class.getSimpleName();

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
                    .add(R.id.fragment_container_view, ConnectionListFragment.class, null, "car-front-panel")
                    .commit();
        }
    }

    private void initManagers() {

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

    /* ========================================================================================== */

    public static class ConnectionListFragment extends Fragment
            implements View.OnClickListener {

        private static final String TAG = ConnectionListFragment.class.getSimpleName();

        private MyWifiManager mWifiManager;

        MyBackgroundExecutor.JobListener mBackgroundHandler;


        public ConnectionListFragment() {}

        public static ConnectionListFragment newInstance() {
            return new ConnectionListFragment();
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            FragmentActivity context = requireActivity();

            SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

            mWifiManager = (MyWifiManager) SensorsViewModel.getOrCreateManager(
                    context, mVM_Sensors, MyWifiManager.class.getSimpleName());

            // set server mode
            mWifiManager.setServerMode(true);

            if (context instanceof MyBackgroundExecutor.JobListener) {
                mBackgroundHandler = (MyBackgroundExecutor.JobListener) context;
            }
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {

            View view = inflater.inflate(R.layout.activity_controller_server, container, false);

            view.findViewById(R.id.btnWifiServer).setOnClickListener(this);
            view.findViewById(R.id.btnP2pServer).setOnClickListener(this);
            view.findViewById(R.id.btnBluetoothServer).setOnClickListener(this);

            return view;
        }

        @Override
        public void onClick(View view) {

            FragmentActivity context = requireActivity();

            int id = view.getId();
            if (id == R.id.btnWifiServer) {

                if (!mWifiManager.isAvailable()) {
                    mWifiManager.resolveAvailability(context);
                }
            }
        }
    }
}