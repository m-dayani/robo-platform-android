package com.dayani.m.roboplatform;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;


/**
 * Responsible for all fragment interactions from requirement handling to recording
 */
public class RecordSensorsActivity extends AppCompatActivity
        implements ActivityRequirements.RequirementResolution,
                MyBackgroundExecutor.JobListener {

    private static final String TAG = RecordSensorsActivity.class.getSimpleName();

    public static final String EXTRA_KEY_RECORD_EXTERNAL = TAG + "key_record_external";

    private SensorsViewModel mVM_Sensors;

    private int mRequestCode = 0;

    private MyBackgroundExecutor mBackgroundExecutor;


    /* -------------------------------------- Lifecycle ----------------------------------------- */

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_sensors);

        // instantiate sensors view model
        mVM_Sensors = new ViewModelProvider(this).get(SensorsViewModel.class);

        // resolve the record external sensor state (use USB device)
        Intent intent = getIntent();
        boolean mbRecordExternal = intent.getBooleanExtra(EXTRA_KEY_RECORD_EXTERNAL, false);

        initManagersAndSensors(mVM_Sensors, mbRecordExternal);

        mBackgroundExecutor = new MyBackgroundExecutor();
        mBackgroundExecutor.initWorkerThread(TAG);

        if (savedInstanceState == null) {

            getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, SensorsListFragment.class, null, "root-container")
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {

        if (mVM_Sensors != null) {
            // Only the context that creates managers (in onCreate) must call this
            this.cleanManagers(mVM_Sensors.getAllManagers());
        }
        mBackgroundExecutor.cleanWorkerThread();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // update permissions state (in case user changes in the settings)
        // TODO: find a broadcast or callback method to listen for these changes
        for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
            manager.updatePermissionsState(this);
        }
    }

    /* ------------------------------------ Init. & Clean --------------------------------------- */

    private void initManagersAndSensors(SensorsViewModel vm, boolean withUSB) {

        MyStorageManager storageManager = (MyStorageManager) SensorsViewModel.getOrCreateManager(
                this, vm, MyStorageManager.class.getSimpleName());

        MyBaseManager mManager = SensorsViewModel.getOrCreateManager(
                this, vm, MySensorManager.class.getSimpleName());
        mManager.registerChannel(storageManager);

        mManager = SensorsViewModel.getOrCreateManager(
                this, vm, MyLocationManager.class.getSimpleName());
        mManager.registerChannel(storageManager);

        mManager = SensorsViewModel.getOrCreateManager(
                this, vm, CameraFlyVideo.class.getSimpleName());
        mManager.registerChannel(storageManager);

        if (withUSB) {
            Log.d(TAG, "USB manager is enabled");
            mManager = SensorsViewModel.getOrCreateManager(
                    this, vm, MyUSBManager.class.getSimpleName());
            mManager.registerChannel(storageManager);
        }

        // Must always call init. here for symmetry (not in managers' constructor)
        for (MyBaseManager manager : vm.getAllManagers()) {
            manager.init(this);
        }
    }

    private void cleanManagers(List<MyBaseManager> lAllManagers) {

        for (MyBaseManager manager : lAllManagers) {

            manager.clean(this);
        }
    }

    /* ---------------------------------- Request Resolutions ----------------------------------- */

    private final ActivityResultLauncher<String[]> mPermsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::processPermissions);

    private final ActivityResultLauncher<Intent> mIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> this.processActivityResult(mRequestCode, result));

    private final ActivityResultLauncher<IntentSenderRequest> mIntentSender = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> this.processActivityResult(mRequestCode, result));

    private void processActivityResult(int requestCode, ActivityResult result) {

        Intent data = result.getData();
        if (data == null) {
            return;
        }
        data.putExtra(MyBaseManager.KEY_INTENT_ACTIVITY_LAUNCHER, requestCode);

        if (mVM_Sensors != null) {
            for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
                manager.onActivityResult(this, result);
            }
        }
    }

    private void processPermissions(Map<String, Boolean> permissions) {

        if (mVM_Sensors != null && permissions != null) {
            for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
                manager.onPermissionsResult(this, permissions);
            }
        }
    }

    @Override
    public void requestResolution(String[] perms) {

        mPermsLauncher.launch(perms);
    }

    @Override
    public void requestResolution(int requestCode, Intent activityIntent) {

        if (activityIntent == null) {
            return;
        }
        mRequestCode = requestCode;
        mIntentLauncher.launch(activityIntent);
    }

    @Override
    public void requestResolution(int requestCode, IntentSenderRequest resolutionIntent) {

        if (resolutionIntent == null) {
            return;
        }
        mRequestCode = requestCode;
        mIntentSender.launch(resolutionIntent);
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