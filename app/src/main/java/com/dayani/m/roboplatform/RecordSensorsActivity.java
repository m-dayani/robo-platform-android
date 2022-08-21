package com.dayani.m.roboplatform;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.List;
import java.util.Map;


/**
 * Responsible for all fragment interactions from requirement handling to recording
 */
public class RecordSensorsActivity extends AppCompatActivity
        implements ActivityRequirements.RequirementResolution {

    private static final String TAG = RecordSensorsActivity.class.getSimpleName();

    public static final String EXTRA_KEY_RECORD_EXTERNAL = TAG + "key_record_external";

    private SensorsViewModel mVM_Sensors;

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

        // launch requirements fragment
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

        MyStorageManager storageManager = (MyStorageManager) getOrCreateManager(
                this, vm, MyStorageManager.class.getSimpleName());

        MyBaseManager manager = getOrCreateManager(this, vm, MySensorManager.class.getSimpleName());
        manager.addChannelTransaction(storageManager);

        manager = getOrCreateManager(this, vm, MyLocationManager.class.getSimpleName());
        manager.addChannelTransaction(storageManager);

        // TODO: Add the rest [also change getOrCreateManager(...)]
        //manager = getOrCreateManager(this, vm, CameraFlyVideo.class.getSimpleName());
        //manager.addChannelTransaction(storageManager);

        if (withUSB) {
            Log.d(TAG, "USB manager is enabled");
            //manager = getOrCreateManager(this, vm, MyUSBManager.class.getSimpleName());
            //manager.addChannelTransaction(storageManager);
        }
    }

    private void cleanManagers(List<MyBaseManager> lAllManagers) {

        for (MyBaseManager manager : lAllManagers) {
            manager.clean(this);
        }
    }

    // don't repeat the same check and create in all fragments and activities
    public static MyBaseManager getOrCreateManager(Context context, SensorsViewModel vm,
                                                   String managerClassName) {

        MyBaseManager manager = null;

        if (managerClassName.equals(MyStorageManager.class.getSimpleName())) {
            manager = vm.getManager(MyStorageManager.class.getSimpleName());
            if (manager == null) {
                manager = new MyStorageManager(context);
                vm.addManagerAndSensors(context, manager);
            }
        }
        else if (managerClassName.equals(MySensorManager.class.getSimpleName())) {
            manager = vm.getManager(MySensorManager.class.getSimpleName());
            if (manager == null) {
                manager = new MySensorManager(context);
                vm.addManagerAndSensors(context, manager);
            }
        }
        else if (managerClassName.equals(MyLocationManager.class.getSimpleName())) {
            manager = vm.getManager(MyLocationManager.class.getSimpleName());
            if (manager == null) {
                manager = new MyLocationManager(context);
                vm.addManagerAndSensors(context, manager);
            }
        }
        //TODO: Add the rest

        return manager;
    }

    /* ---------------------------------- Request Resolutions ----------------------------------- */

    private final ActivityResultLauncher<String[]> mPermsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            this::processPermissions);

    private int mRequestCode = 0;

    private final ActivityResultLauncher<Intent> mIntentLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                this.processActivityResult(mRequestCode, result);
            });

    private final ActivityResultLauncher<IntentSenderRequest> mIntentSender = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                this.processActivityResult(mRequestCode, result);
            });

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
}