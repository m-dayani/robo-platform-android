package com.dayani.m.roboplatform;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.SensorsViewModel;

import java.util.ArrayList;
import java.util.List;


/**
 * Responsible for all fragment interactions from requirement handling to recording
 */
public class RecordSensorsActivity extends AppCompatActivity implements MyStorageManager.StorageChannel {

    private static final String TAG = RecordSensorsActivity.class.getSimpleName();

    public static final String EXTRA_KEY_RECORD_EXTERNAL = TAG+"key_record_external";

    boolean mbRecordExternal = false;

    SensorsViewModel mVM_Sensors;

    MyStorageManager mStorageManager;

    private String mDsRoot;

    /* -------------------------------------- Lifecycle ----------------------------------------- */

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_sensors);

        // instantiate sensors view model
        mVM_Sensors = new ViewModelProvider(this).get(SensorsViewModel.class);

        // resolve the record external sensor state (use USB device)
        Intent intent = getIntent();
        mbRecordExternal = intent.getBooleanExtra(EXTRA_KEY_RECORD_EXTERNAL, false);

        mDsRoot = initDsPath(mVM_Sensors);
        initManagersAndSensors(mVM_Sensors);

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

    /* ------------------------------------ Init. & Clean --------------------------------------- */

    private void initManagersAndSensors(SensorsViewModel vm) {

        mStorageManager = (MyStorageManager) getOrCreateManager(this, vm, MyStorageManager.class.getSimpleName());

        getOrCreateManager(this, vm, MySensorManager.class.getSimpleName());

        // TODO: Add the rest
    }

    private void cleanManagers(List<MyBaseManager> lAllManagers) {

        for (MyBaseManager manager : lAllManagers) {
            manager.clean();
        }
    }

    public static String initDsPath(SensorsViewModel vm) {

        String curDsPath = vm.getDsPath().getValue();
        if (curDsPath == null || curDsPath.isEmpty()) {

            curDsPath = MyStorageManager.DS_FOLDER_PREFIX+MyStorageManager.getTimePrefix();
            vm.setDsPath(curDsPath);
            Log.v(TAG, "Created new ds root: "+curDsPath);
        }
        return curDsPath;
    }

    /* --------------------------------------- Helpers ------------------------------------------ */

    // don't repeat the same check and create in all fragments and activities
    public static MyBaseManager getOrCreateManager(Context context, SensorsViewModel vm, String managerClassName) {

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

        return manager;
    }

    /* --------------------------------- Storage Transactions ----------------------------------- */

    // Better to define this interface in the storage creator context
    @Override
    public int getStorageChannel(List<String> folders, String fileName, boolean append) {

        if (mStorageManager != null) {

            List<String> foldersWithRoot = new ArrayList<>();

            if (mDsRoot == null) {
                mDsRoot = initDsPath(mVM_Sensors);
            }
            foldersWithRoot.add(mDsRoot);
            foldersWithRoot.addAll(folders);

            String[] foldersArr = foldersWithRoot.toArray(new String[0]);

            String fullPath = mStorageManager.resolveFilePath(this, foldersArr, fileName);
            return mStorageManager.subscribeStorageChannel(fullPath, append);
        }
        else {
            Log.w(TAG, "Storage manager is empty");
        }
        return -1;
    }

    @Override
    public void publishMessage(int id, String msg) {

        if (mStorageManager != null) {
            mStorageManager.publishMessage(id, msg);
        }
        else {
            Log.w(TAG, "Storage manager is empty");
        }
    }

    @Override
    public void resetChannel(int id) {

        if (mStorageManager != null) {
            mStorageManager.resetStorageChannel(id);
        }
        else {
            Log.w(TAG, "Storage manager is empty");
        }
    }

    @Override
    public String getFullFilePath(int id) {

        if (mStorageManager != null) {
            return mStorageManager.getFullFilePath(id);
        }
        else {
            Log.w(TAG, "Storage manager is empty");
            return "";
        }
    }

    @Override
    public void removeChannel(int id) {

        if (mStorageManager != null) {
            mStorageManager.removeChannel(id);
        }
        else {
            Log.w(TAG, "Storage manager is empty");
        }
    }
}