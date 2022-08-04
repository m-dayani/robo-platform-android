package com.dayani.m.roboplatform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MyPermissionManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;
import com.dayani.m.roboplatform.utils.SensorsContainer;

import java.util.ArrayList;

/**
 * Responsible for all fragment interactions from requirement handling to recording
 */
public class RecordSensorsActivity extends AppCompatActivity {

    private static final String TAG = RecordSensorsActivity.class.getSimpleName();

    public static final String EXTRA_KEY_RECORD_EXTERNAL = TAG+"key_record_external";

    boolean recordExternal = false;

    SensorRequirementsViewModel mVM_Sensors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_sensors);

        // resolve the record external sensor state (use USB device)
        Intent intent = getIntent();
        recordExternal = intent.getBooleanExtra(EXTRA_KEY_RECORD_EXTERNAL, false);

        // resolve and populate all requirements and sensors
        mVM_Sensors = new ViewModelProvider(this).get(SensorRequirementsViewModel.class);
        mVM_Sensors.setSensorsContainer(getSensorRequirements(this, recordExternal));
        Log.i(TAG, "Requirements: " + mVM_Sensors.getRequirements().getValue());
        Log.i(TAG, "Permissions: " + mVM_Sensors.getPermissions().getValue());

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

        // launch requirements fragment
        if (savedInstanceState == null) {
            fragmentTransaction
                    .setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, RequirementsFragment.class, null, "root-container")
                    .commit();
        }
    }

    public static ArrayList<MySensorGroup> getSensorRequirements(Context mContext, boolean useUSB) {

        ArrayList<MySensorGroup> sensors = new ArrayList<>();

        sensors.addAll(MyStorageManager.getSensorRequirements(mContext));
        sensors.addAll(MySensorManager.getSensorRequirements(mContext));
        sensors.addAll(MyLocationManager.getSensorRequirements(mContext));
        sensors.addAll(CameraFlyVideo.getSensorRequirements(mContext));

        if (useUSB) {
            sensors.addAll(MyUSBManager.getSensorRequirements(mContext));
        }

        return sensors;
    }
}