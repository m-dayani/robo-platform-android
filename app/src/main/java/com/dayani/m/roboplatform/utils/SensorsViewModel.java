package com.dayani.m.roboplatform.utils;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SensorsViewModel extends ViewModel {

    /* -------------------------------------- Variables ----------------------------------------- */

    private static final String TAG = SensorsViewModel.class.getSimpleName();

    private final List<MyBaseManager> mlManagers;

    private final MutableLiveData<Map<Integer, MySensorGroup>> mSensors;
    private final MutableLiveData<List<Requirement>> mRequirements;
    private final MutableLiveData<List<String>> mPermissions;

    private final MutableLiveData<String> mDsPath;

    /* ------------------------------------ Construction ---------------------------------------- */

    public SensorsViewModel() {

        mSensors = new MutableLiveData<>();
        mSensors.setValue(new HashMap<>());

        mRequirements = new MutableLiveData<>();
        mRequirements.setValue(new ArrayList<>());

        mPermissions = new MutableLiveData<>();
        mPermissions.setValue(new ArrayList<>());

        mDsPath = new MutableLiveData<>();

        mlManagers = new ArrayList<>();
    }

    /* ----------------------------------- Getters/Setters -------------------------------------- */

    public List<MySensorGroup> getSensorGroups() {

        Map<Integer, MySensorGroup> sensorsMap = mSensors.getValue();
        List<MySensorGroup> sensors = new ArrayList<>();

        if (sensorsMap != null) {
            sensors = new ArrayList<>(sensorsMap.values());
        }
        else {
            Log.w(TAG, "Sensors map is null, forgot to initialize it?");
        }

        return sensors;
    }

    public MutableLiveData<List<Requirement>> getRequirements() {
        return mRequirements;
    }

    public MutableLiveData<List<String>> getPermissions() {
        return mPermissions;
    }

    private void updateSensorGroups(List<MySensorGroup> sensorGrps) {

            Map<Integer, MySensorGroup> mapSensor = mSensors.getValue();
            if (mapSensor == null) {
                mapSensor = new HashMap<>();
            }

            for (MySensorGroup sensor : sensorGrps) {
                mapSensor.put(sensor.getId(), sensor);
            }

            mSensors.setValue(mapSensor);
    }

    private void updateRequirements(List<MySensorGroup> sensors) {

        List<Requirement> requirements = MySensorGroup.getUniqueRequirements(sensors);
        mRequirements.setValue(requirements);
    }

    private void updatePermissions(List<MySensorGroup> sensors) {

        List<String> perms = MySensorGroup.getUniquePermissions(sensors);
        mPermissions.setValue(perms);
    }

    public MutableLiveData<String> getDsPath() {
        return mDsPath;
    }

    public void setDsPath(String mDsPath) {
        this.mDsPath.setValue(mDsPath);
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

        Map<Integer, MySensorGroup> sensorGrps = mSensors.getValue();
        if (sensorGrps != null) {
            sensorGroup = sensorGrps.get(grpId);
        }

        return sensorGroup;
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
        List<MySensorGroup> sensorGroups = manager.getSensorRequirements(context);
        updateSensorGroups(sensorGroups);

        // update requirements
        updateRequirements(sensorGroups);

        // update permissions
        updatePermissions(sensorGroups);
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
            manager.updateState(context);

            List<MySensorGroup> sensorGroups = manager.getSensorRequirements(context);

            // update sensors
            updateSensorGroups(sensorGroups);

            // update requirements
            updateRequirements(sensorGroups);

            // update permissions
            updatePermissions(sensorGroups);
        }
    }

    private void deleteState() {

        // remove sensors
        Map<Integer, MySensorGroup> sensorsMap = mSensors.getValue();
        if (sensorsMap != null) {
            sensorsMap.clear();
            mSensors.setValue(sensorsMap);
        }

        // remove requirements
        List<Requirement> requirements = mRequirements.getValue();
        if (requirements != null) {
            requirements.clear();
            mRequirements.setValue(requirements);
        }

        // remove permissions
        List<String> perms = mPermissions.getValue();
        if (perms != null) {
            perms.clear();
            mPermissions.setValue(perms);
        }
    }

    /* ---------------------------------------- Helpers ----------------------------------------- */

    public String printState() {

        if (mSensors.getValue() != null & mRequirements.getValue() != null && mPermissions.getValue() != null) {
            return "Size of managers: " + mlManagers.size() + ", Size of sensors: " +
                    mSensors.getValue().size() + ", Size of requirements: " +
                    mRequirements.getValue().size() + ", Size of permissions: " +
                    mPermissions.getValue().size() + ", Ds path: " + mDsPath.getValue();
        }
        return "";
    }
}
