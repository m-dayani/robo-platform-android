package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.MySensorInfo;
import com.dayani.m.roboplatform.utils.SensorsAdapter;
import com.dayani.m.roboplatform.utils.SensorsViewModel;

import java.util.ArrayList;
import java.util.List;


public class SensorsListFragment extends Fragment implements View.OnClickListener,
        SensorsAdapter.SensorItemInteraction {

    private static final String TAG = SensorsListFragment.class.getSimpleName();

    private static final String CALIB_FILE_NAME = AppGlobals.DEF_FILE_NAME_CALIBRATION;

    private SensorsViewModel mVM_Sensors;

    private MyStorageManager mStorageManager;
    private MyStorageManager.StorageChannel mStorageChannel;
    private int mCalibStorageId = -1;

    //private List<MyBaseManager> mlSensorManagers;


    public SensorsListFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FrontPanelFragment.
     */
    public static SensorsListFragment newInstance() {

        SensorsListFragment fragment = new SensorsListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity activityContext = requireActivity();

        // get sensors view model object
        mVM_Sensors = new ViewModelProvider(activityContext).get(SensorsViewModel.class);

        if (activityContext instanceof MyStorageManager.StorageChannel) {
            mStorageChannel = (MyStorageManager.StorageChannel) activityContext;
        }

        // retrieve storage manager
        mStorageManager = (MyStorageManager) RecordSensorsActivity.getOrCreateManager(
                activityContext, mVM_Sensors, MyStorageManager.class.getSimpleName());

//        mLocationManager = (MyLocationManager) RecordSensorsActivity.getOrCreateManager(
//                activityContext, mVM_Sensors, MyLocationManager.class.getSimpleName());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensors_list, container, false);

        view.findViewById(R.id.btn_start_req).setOnClickListener(this);
        view.findViewById(R.id.btn_save_info).setOnClickListener(this);

        List<MySensorGroup> sGroupsFiltered = mVM_Sensors.getSensorGroups();
        sGroupsFiltered = MySensorGroup.filterSensorGroups(sGroupsFiltered, SensorType.TYPE_STORAGE);
        Log.v(TAG, sGroupsFiltered.toString());

        ListView lvSensors = view.findViewById(R.id.list_sensors);
        SensorsAdapter sensorsAdapter = new SensorsAdapter(getActivity(),
                R.layout.sensor_group, sGroupsFiltered, this);
        lvSensors.setAdapter(sensorsAdapter);

        return view;
    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.btn_start_req) {

            Log.d(TAG, "Launch Start Fragment");
            launchRecordingFrag();
        }
        else if (id == R.id.btn_save_info) {

            Log.d(TAG, "Launch save sensor info task.");
            saveSensorsInfo();
        }
    }

    private void launchRecordingFrag() {

        if (mStorageManager != null && mStorageManager.isAvailable(requireActivity())) {

            startNewFragment(RecordingFragment.newInstance());
        }
        else if (mStorageManager != null) {

            Log.d(TAG, "Storage manager is not available, request resolving the issues");
            mStorageManager.resolveAvailability(requireActivity());
        }
        else {
            Log.w(TAG, "Storage manager is not initialized");
        }
    }

    private void saveSensorsInfo() {

        FragmentActivity context = requireActivity();

        if (mStorageManager != null && mStorageManager.isAvailable(context) && mStorageChannel != null) {

            if (mCalibStorageId < 0) {

                Log.d(TAG, "Getting a storage channel for file: "+ CALIB_FILE_NAME);
                mCalibStorageId = mStorageChannel.getStorageChannel(new ArrayList<>(), CALIB_FILE_NAME, false);
            }

            if (mVM_Sensors != null) {

                // reset the contents before each write
                mStorageChannel.resetChannel(mCalibStorageId);

                List<MySensorGroup> sensors = mVM_Sensors.getSensorGroups();
                sensors = MySensorGroup.filterSensorGroups(sensors, SensorType.TYPE_STORAGE);

                mStorageChannel.publishMessage(mCalibStorageId, getAllSensorsInfo(sensors));

                String fullPath = mStorageChannel.getFullFilePath(mCalibStorageId);
                Toast.makeText(context, "Saved to: "+fullPath, Toast.LENGTH_SHORT).show();
            }
            else {
                Log.w(TAG, "Sensors view model is null");
            }
        }
        else if (mStorageManager != null) {

            Log.d(TAG, "Storage manager is not available, request resolving the issues");
            mStorageManager.resolveAvailability(context);
        }
    }

    /**
     * Only save available and checked sensors
     * @param lSensorGroups all sensor groups
     * @return empty string or yaml friendly serialized calibration info string
     */
    private static String getAllSensorsInfo(List<MySensorGroup> lSensorGroups) {

        StringBuilder sbSensorsInfo = new StringBuilder();

        for (MySensorGroup sensorGroup : lSensorGroups) {

            int counts = sensorGroup.countCheckedSensors();

            if (counts <= 0) {
                continue;
            }

            sbSensorsInfo.append(sensorGroup.getTitle()).append(":\n");

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo.isChecked()) {
                    sbSensorsInfo.append(sensorInfo.getCalibInfoString("\t")).append("\n");
                }
            }
        }

        return sbSensorsInfo.toString();
    }

    // TODO: Complete the implementation with other sensors
    @Override
    public void onSensorCheckedListener(View view, int grpId, int sensorId, boolean b) {

        boolean changeChecked = true;
        FragmentActivity context = requireActivity();

        MyBaseManager manager = mVM_Sensors.getManagerBySensorGroup(grpId);
        if (manager != null && !manager.isAvailable(context)) {

            // resolve availability
            manager.resolveAvailability(context);
            changeChecked = false;
        }

        if (changeChecked) {
            if (view instanceof CheckBox) {
                ((CheckBox) view).setChecked(b);
            }
            mVM_Sensors.getSensor(grpId, sensorId).setChecked(b);
            // debugging
//            Log.v(TAG, mVM_Sensors.getManagerSensor(requireActivity(),
//                    MySensorGroup.class.getSimpleName(), grpId, sensorId).toString());
        }
    }

    @Override
    public void onSensorInfoClickListener(View view, int grpId, int sensorId) {

        String info = "General Info:\n" + mVM_Sensors.getSensor(grpId, sensorId).getDescInfoString("");
        info += "\nCalibration Info:\n" + mVM_Sensors.getSensor(grpId, sensorId).getCalibInfoString("");
        Fragment frag = SensorInfoFragment.newInstance(info);
        startNewFragment(frag);
    }

    private void startNewFragment(Fragment frag) {

        MainActivity.startNewFragment(getParentFragmentManager(),
                R.id.fragment_container_view, frag, "sensors");
    }
}