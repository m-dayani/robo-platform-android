package com.dayani.m.roboplatform.utils.view_models;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class SensorsViewModel extends ViewModel {

    public static class ManagerSensorGroup extends HashMap<Integer, Pair<MyBaseManager, MySensorGroup>> {}

    /* -------------------------------------- Variables ----------------------------------------- */

    private static final String TAG = SensorsViewModel.class.getSimpleName();

    private final Set<MyBaseManager> mlManagers;
    private final MutableLiveData<ManagerSensorGroup> mmManagerSensors;
    private final MutableLiveData<Set<MySensorGroup>> mSensorGroups;

    /* ------------------------------------ Construction ---------------------------------------- */

    public SensorsViewModel() {

        mmManagerSensors = new MutableLiveData<>();
        mSensorGroups = new MutableLiveData<>();
        mmManagerSensors.setValue(new ManagerSensorGroup());

        mlManagers = new HashSet<>();
    }

    /* ----------------------------------- Getters/Setters -------------------------------------- */

    /*public List<MySensorGroup> getSensorGroups() {

        ManagerSensorGroup sensorsMap = mmManagerSensors.getValue();
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
    }*/

    public MutableLiveData<Set<MySensorGroup>> getSensorGroupsLiveData() {
        return mSensorGroups;
    }

    public List<MySensorGroup> getSensorGroups() {

        Set<MySensorGroup> sSensors = mSensorGroups.getValue();
        List<MySensorGroup> lSensors = new ArrayList<>();
        if (sSensors != null) {
            lSensors.addAll(sSensors);
        }
        return lSensors;
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

        if (sensorGrps == null) {
            return;
        }

        ManagerSensorGroup mapSensor = mmManagerSensors.getValue();
        if (mapSensor == null) {
            mapSensor = new ManagerSensorGroup();
        }
        Set<MySensorGroup> mSensorGrps = mSensorGroups.getValue();
        if (mSensorGrps == null) {
            mSensorGrps = new LinkedHashSet<>();
        }

        for (MySensorGroup sensor : sensorGrps) {
            mapSensor.put(sensor.getId(), new Pair<>(manager, sensor));
            mSensorGrps.add(sensor);
        }

        Log.v(TAG, "Added " + sensorGrps.size() + " sensor groups");

        mmManagerSensors.setValue(mapSensor);
        mSensorGroups.setValue(mSensorGrps);
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

        ManagerSensorGroup sensorGrps = mmManagerSensors.getValue();
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

        ManagerSensorGroup sensorGrps = mmManagerSensors.getValue();
        if (sensorGrps != null && sensorGrps.containsKey(grpId)) {

            Pair<MyBaseManager, MySensorGroup> managerSensorPair = sensorGrps.get(grpId);
            if (managerSensorPair != null) {
                manager = managerSensorPair.first;
            }
        }

        return manager;
    }

    public MyBaseManager getManager(String className) {

        return MyBaseManager.getManager(getAllManagers(), className);
    }

    public List<MyBaseManager> getAllManagers() {

        return new ArrayList<>(mlManagers);
    }

    public void addManagerAndSensors(Context context, MyBaseManager manager) {

        if (context == null || manager == null || !manager.isSupported()) {
            return;
        }

        // add manager
        mlManagers.add(manager);

        // update sensors
        List<MySensorGroup> sensorGroups = manager.getSensorGroups(context);
        updateSensorGroups(manager, sensorGroups);
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
        else if (managerClassName.equals(CameraFlyVideo.class.getSimpleName())) {
            manager = vm.getManager(CameraFlyVideo.class.getSimpleName());
            if (manager == null) {
                manager = new CameraFlyVideo(context);
                vm.addManagerAndSensors(context, manager);
            }
        }
        else if (managerClassName.equals(MyUSBManager.class.getSimpleName())) {
            manager = vm.getManager(MyUSBManager.class.getSimpleName());
            if (manager == null) {
                manager = new MyUSBManager(context);
                vm.addManagerAndSensors(context, manager);
            }
        }
        else if (managerClassName.equals(MyWifiManager.class.getSimpleName())) {
            manager = vm.getManager(MyWifiManager.class.getSimpleName());
            if (manager == null) {
                manager = new MyWifiManager(context);
                vm.addManagerAndSensors(context, manager);
            }
        }
        else if (managerClassName.equals(MyBluetoothManager.class.getSimpleName())) {
            manager = vm.getManager(MyBluetoothManager.class.getSimpleName());
            if (manager == null) {
                manager = new MyBluetoothManager(context);
                vm.addManagerAndSensors(context, manager);
            }
        }

        return manager;
    }

    /* ---------------------------------------- Helpers ----------------------------------------- */

    public String printState() {

        if (mmManagerSensors.getValue() != null) {
            return "Size of managers: " + mlManagers.size() + ", Size of sensors: " + mmManagerSensors.getValue().size();
        }
        return "";
    }
}
