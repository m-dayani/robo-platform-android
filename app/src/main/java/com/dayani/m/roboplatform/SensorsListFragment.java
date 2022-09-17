package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.adapters.FastNestedListAdapter;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels.ChannelTransactions;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig.ConfigAction;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageInfo;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class SensorsListFragment extends Fragment implements View.OnClickListener,
        FastNestedListAdapter.SensorItemInteractions, ChannelTransactions {

    private static final String TAG = SensorsListFragment.class.getSimpleName();

    private static final String CALIB_FILE_NAME = AppGlobals.DEF_FILE_NAME_CALIBRATION;

    private SensorsViewModel mVM_Sensors;

    private MyStorageManager mStorageManager;
    private ChannelTransactions mStorageChannel;
    private int mCalibStorageId = -1;

    //SensorsAdapter mSensorsAdapter;
    private LinearLayout mSensorsListView;
    private FastNestedListAdapter mSensorsAdapter;


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

        // retrieve storage manager
        mStorageManager = (MyStorageManager) SensorsViewModel.getOrCreateManager(
                activityContext, mVM_Sensors, MyStorageManager.class.getSimpleName());
        mStorageChannel = mStorageManager;

        // register channel transactions
        Log.v(TAG, "registered sensors list frag channel");
        //mStorageChannel.registerChannel(this);

        // update checked sensors in case settings are changed
        for (MyBaseManager manager : mVM_Sensors.getAllManagers()) {
            manager.updateCheckedSensorsWithAvailability();
        }
    }

    @Override
    public void onDestroy() {

        Log.v(TAG, "unregistered sensors list frag channel");
        //mStorageChannel.unregisterChannel(this);
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensors_list, container, false);

        view.findViewById(R.id.btn_start_req).setOnClickListener(this);
        view.findViewById(R.id.btn_save_info).setOnClickListener(this);

        List<MySensorGroup> sGroupsFiltered = mVM_Sensors.getSensorGroups();
        sGroupsFiltered = MySensorGroup.filterSensorGroups(sGroupsFiltered, SensorType.TYPE_STORAGE);
        //Log.v(TAG, sGroupsFiltered.toString());

        FragmentActivity context = requireActivity();

        mSensorsListView = view.findViewById(R.id.list_sensors);
        mSensorsAdapter = new FastNestedListAdapter(sGroupsFiltered, this);
        mSensorsAdapter.createSensorsList(context, mSensorsListView);

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

        if (isStorageManagerBad()) {
            Log.w(TAG, "Cannot launch recording, abort");
            return;
        }

        startNewFragment(RecordingFragment.newInstance());
    }

    private void saveSensorsInfo() {

        if (isStorageManagerBad()) {
            Log.w(TAG, "Cannot save sensor info, abort");
            return;
        }

        StorageInfo.StreamType streamType = StorageInfo.StreamType.TRAIN_STRING;
        StorageInfo storageInfo = new StorageInfo(new ArrayList<>(), CALIB_FILE_NAME, streamType);
        String targetCh = MyStorageManager.class.getSimpleName();

        if (mCalibStorageId < 0) {

            // request the storage channel a new file handle
            Log.d(TAG, "Getting a storage channel for file: "+ CALIB_FILE_NAME);

            StorageConfig storageConfig = new StorageConfig(ConfigAction.OPEN, TAG, targetCh, storageInfo);

            publishMessage(storageConfig);

            mCalibStorageId = storageConfig.getTargetId();
        }

        // save the info
        List<MySensorGroup> sensors = mVM_Sensors.getSensorGroups();
        sensors = MySensorGroup.filterSensorGroups(sensors, SensorType.TYPE_STORAGE);
        String infoStr = getAllSensorsInfo(sensors);

        MyMessage sensorsInfo = new MyMessages.MsgStorage(infoStr, CALIB_FILE_NAME, mCalibStorageId);

        publishMessage(sensorsInfo);

        // inquire the full file path
        StorageConfig storageConfig = new StorageConfig(ConfigAction.GET_STATE, TAG, targetCh, storageInfo);
        storageConfig.setTargetId(mCalibStorageId);

        publishMessage(storageConfig);

        String fullPath = storageConfig.toString();
        Toast.makeText(requireActivity(), "Saved to: " + fullPath, Toast.LENGTH_SHORT).show();
    }

    private boolean isStorageManagerBad() {

        if (mStorageManager == null) {
            return true;
        }

        if (!mStorageManager.isAvailable()) {

            Log.d(TAG, "Storage manager is not available, request resolving the issues");
            mStorageManager.resolveAvailability(requireActivity());
            return true;
        }

        return false;
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

    @Override
    public void onSensorCheckedListener(int grpId, int sensorId, boolean b) {

        FragmentActivity context = requireActivity();
        MyBaseManager manager = mVM_Sensors.getManagerBySensorGroup(grpId);

        if (manager != null) {

            // update manager
            manager.onCheckedChanged(context, grpId, sensorId, b);

            // update view
            if (mSensorsAdapter != null && mSensorsListView != null) {

                List<MySensorGroup> lSensors = Collections.singletonList(mVM_Sensors.getSensorGroup(grpId));
//                mSensorsAdapter.updateSensorGroups(Collections.singletonList(grpId));
                mSensorsAdapter.updateSensorGroups(mSensorsListView, lSensors);
            }
        }
    }

    @Override
    public void onSensorInfoClickListener(int grpId, int sensorId) {

        String info = "General Info:\n" + mVM_Sensors.getSensor(grpId, sensorId).getDescInfoString("");
        info += "\nCalibration Info:\n" + mVM_Sensors.getSensor(grpId, sensorId).getCalibInfoString("");
        Fragment frag = SensorInfoFragment.newInstance(info);
        startNewFragment(frag);
    }

    private void startNewFragment(Fragment frag) {

        MainActivity.startNewFragment(getParentFragmentManager(),
                R.id.fragment_container_view, frag, "sensors");
    }


    @Override
    public void publishMessage(MyMessage msg) {

        mStorageChannel.onMessageReceived(msg);
    }

    @Override
    public void onMessageReceived(MyMessage msg) {
    }

    @Override
    public void registerChannel(ChannelTransactions channel) {

    }

    @Override
    public void unregisterChannel(ChannelTransactions channel) {

    }
}