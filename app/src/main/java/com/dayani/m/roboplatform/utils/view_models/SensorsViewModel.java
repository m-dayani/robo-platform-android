package com.dayani.m.roboplatform.utils.view_models;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class SensorsViewModel extends ViewModel {

    public static class SensorManagerGroup extends HashMap<Integer, Pair<MyBaseManager, MySensorGroup>> {}

    /* -------------------------------------- Variables ----------------------------------------- */

    private static final String TAG = SensorsViewModel.class.getSimpleName();

    private final List<MyBaseManager> mlManagers;

    private final MutableLiveData<SensorManagerGroup> mSensors;

    /* ------------------------------------ Construction ---------------------------------------- */

    public SensorsViewModel() {

        mSensors = new MutableLiveData<>();
        mSensors.setValue(new SensorManagerGroup());

        mlManagers = new ArrayList<>();
    }

    /* ----------------------------------- Getters/Setters -------------------------------------- */

    public List<MySensorGroup> getSensorGroups() {

        SensorManagerGroup sensorsMap = mSensors.getValue();
        List<MySensorGroup> sensors = new ArrayList<>();

        if (sensorsMap != null) {

            for (Pair<MyBaseManager, MySensorGroup> managerSensorPair : sensorsMap.values()) {

                if (managerSensorPair != null) {
                    sensors.add(managerSensorPair.second);
                }
            }
        }
        else {
            Log.w(TAG, "Sensors map is null, forgot to initialize it?");
        }

        return sensors;
    }

    // deprecated
    public MutableLiveData<List<Requirement>> getRequirements() {

        List<Requirement> mRequirements = new ArrayList<>();

//        for (MyBaseManager manager : mlManagers) {
//            mRequirements.addAll(manager.getRequirements());
//        }

        return new MutableLiveData<>(mRequirements);
    }

    // deprecated
    public MutableLiveData<List<String>> getPermissions() {

        List<String> perms = new ArrayList<>();

//        for (MyBaseManager manager : mlManagers) {
//            perms.addAll(manager.getPermissions());
//        }

        return new MutableLiveData<>(perms);
    }

    private void updateSensorGroups(MyBaseManager manager, List<MySensorGroup> sensorGrps) {

            SensorManagerGroup mapSensor = mSensors.getValue();
            if (mapSensor == null) {
                mapSensor = new SensorManagerGroup();
            }

            for (MySensorGroup sensor : sensorGrps) {
                mapSensor.put(sensor.getId(), new Pair<>(manager, sensor));
            }

            mSensors.setValue(mapSensor);
    }

    public MySensorInfo getSensor(int grpId, int sensorId) {

        MySensorInfo sensor = null;

        MySensorGroup sensorGroup = getSensorGroup(grpId);
        if (sensorGroup != null) {
            sensor = sensorGroup.getSensorInfo(sensorId);
        }

        return sensor;
    }

    public MySensorGroup getSensorGroup(int grpId) {

        MySensorGroup sensorGroup = null;

        SensorManagerGroup sensorGrps = mSensors.getValue();
        if (sensorGrps != null && sensorGrps.containsKey(grpId)) {

            Pair<MyBaseManager, MySensorGroup> managerSensorPair = sensorGrps.get(grpId);
            if (managerSensorPair != null) {
                sensorGroup = managerSensorPair.second;
            }
        }

        return sensorGroup;
    }

    public MyBaseManager getManagerBySensorGroup(int grpId) {

        MyBaseManager manager = null;

        SensorManagerGroup sensorGrps = mSensors.getValue();
        if (sensorGrps != null && sensorGrps.containsKey(grpId)) {

            Pair<MyBaseManager, MySensorGroup> managerSensorPair = sensorGrps.get(grpId);
            if (managerSensorPair != null) {
                manager = managerSensorPair.first;
            }
        }

        return manager;
    }

    public MyBaseManager getManager(String className) {

        return MyBaseManager.getManager(mlManagers, className);
    }

    public List<MyBaseManager> getAllManagers() {
        return mlManagers;
    }

    public void addManagerAndSensors(Context context, MyBaseManager manager) {

        // add manager
        mlManagers.add(manager);

        // update sensors
        List<MySensorGroup> sensorGroups = manager.getSensorGroups(context);
        updateSensorGroups(manager, sensorGroups);
    }

    /* ----------------------------------- State Management ------------------------------------- */

    /**
     * Update all states when a task starts
     * Update only in one direction: from managers to this view model
     * @param context Context activity
     */
    public void updateState(Context context) {

        deleteState();

        for (MyBaseManager manager : mlManagers) {

            // update each manager
            //manager.updateState(context);

            List<MySensorGroup> sensorGroups = manager.getSensorGroups(context);

            // update sensors
            updateSensorGroups(manager, sensorGroups);
        }
    }

    private void deleteState() {

        // remove sensors
        SensorManagerGroup sensorsMap = mSensors.getValue();
        if (sensorsMap != null) {
            sensorsMap.clear();
            mSensors.setValue(sensorsMap);
        }
    }

    /* ---------------------------------------- Helpers ----------------------------------------- */

    public String printState() {

        if (mSensors.getValue() != null) {
            return "Size of managers: " + mlManagers.size() + ", Size of sensors: " + mSensors.getValue().size();
        }
        return "";
    }
}
