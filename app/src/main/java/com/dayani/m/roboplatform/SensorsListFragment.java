package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;
import com.dayani.m.roboplatform.utils.SensorsAdapter;

import java.util.ArrayList;
import java.util.Objects;


public class SensorsListFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = SensorsListFragment.class.getSimpleName();

    private static final String CALIB_FILE_NAME = "calib.txt";

    private SensorRequirementsViewModel mVM_Sensors;

    private String dsRoot;

    private MyStorageManager mStorageManager;

    private int mCalibStorageId;


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

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);

        String curDsPath = mVM_Sensors.getDsPath().getValue();

        if (curDsPath == null || curDsPath.isEmpty()) {

            dsRoot = MyStorageManager.DS_FOLDER_PREFIX+MyStorageManager.getTimePerfix();
            mVM_Sensors.setDsPath(dsRoot);
        }

        mStorageManager = new MyStorageManager(requireActivity());

        mCalibStorageId = -1;
    }

    @Override
    public void onDestroy() {

        mStorageManager.clean();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        ArrayList<MySensorGroup> mSensorGroups = new ArrayList<>(Objects.requireNonNull(mVM_Sensors.getSensorGroups().getValue()));

        ArrayList<MySensorGroup> sGroupsFiltered = new ArrayList<>();
        for (MySensorGroup sgrp : mSensorGroups) {
            if (!sgrp.getType().equals(MySensorGroup.SensorType.TYPE_STORAGE)) {
                sGroupsFiltered.add(sgrp);
            }
        }

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensors_list, container, false);

        view.findViewById(R.id.btn_start_req).setOnClickListener(this);
        view.findViewById(R.id.btn_save_info).setOnClickListener(this);

        ListView lvSensors = view.findViewById(R.id.list_sensors);
        lvSensors.setAdapter(new SensorsAdapter(getActivity(), R.layout.sensor_group, sGroupsFiltered));

        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {

        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
//        mListener = null;
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

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_view, RecordingFragment.class, null)
                .setReorderingAllowed(true)
                .addToBackStack("sensors")
                .commit();
    }

    private void saveSensorsInfo() {

        if (mStorageManager.isAvailable()) {
            if (mCalibStorageId < 0) {
                mCalibStorageId = mStorageManager.subscribeStorageChannel(
                        mStorageManager.resolveFilePath(new String[]{dsRoot}, CALIB_FILE_NAME));
            }

            mStorageManager.publishMessage(mCalibStorageId, getAllSensorsInfo());
        }
        else {
            mStorageManager.resolveAvailability();
        }
    }

    private String getAllSensorsInfo() {

        return "Overwritten at " + System.currentTimeMillis() + "\n" +
                "Sensors are very good!\n";
    }
}