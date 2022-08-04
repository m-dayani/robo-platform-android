package com.dayani.m.roboplatform.utils;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;


public class SensorRequirementsViewModel extends ViewModel {

    private static final String TAG = SensorRequirementsViewModel.class.getSimpleName();

    public SensorRequirementsViewModel() {

        mSensors = new MutableLiveData<>();
        mRequirements = new MutableLiveData<>();
        mPermissions = new MutableLiveData<>();

        mDsPath = new MutableLiveData<>();
    }

    public SensorRequirementsViewModel(ArrayList<MySensorGroup> sensorGrps) {

        this();
        this.setSensorsContainer(sensorGrps);
    }

    public MutableLiveData<ArrayList<MySensorGroup>> getSensorGroups() {
        return mSensors;
    }

    public MutableLiveData<ArrayList<Requirement>> getRequirements() {
        return mRequirements;
    }

    public MutableLiveData<ArrayList<String>> getPermissions() {
        return mPermissions;
    }

    public void setSensorsContainer(ArrayList<MySensorGroup> sensorGrps) {

        if (sensorGrps != null) {
            mSensors.setValue(sensorGrps);
            updateRequiremnts(sensorGrps);
        }
    }

    public void updateRequiremnts(ArrayList<MySensorGroup> sensors) {

        ArrayList<Requirement> requirements = new ArrayList<>();
        ArrayList<String> perms = new ArrayList<>();

        for (MySensorGroup sensorGroup : sensors) {

            if (sensorGroup.isAvailable()) {

                for (Requirement req : sensorGroup.getRequirements()) {
                    if (!requirements.contains(req)) {
                        requirements.add(req);
                    }
                }
                for (String perm : sensorGroup.getPermissions()) {
                    if (!perms.contains(perm)) {
                        perms.add(perm);
                    }
                }
            }
        }

        Log.i(TAG, "all requirements: "+requirements);
        Log.i(TAG, "all permissions: "+perms);

        mRequirements.setValue(requirements);
        mPermissions.setValue(perms);
    }

    public MutableLiveData<String> getDsPath() {
        return mDsPath;
    }

    public void setDsPath(String mDsPath) {
        this.mDsPath.setValue(mDsPath);
    }

    private MutableLiveData<ArrayList<MySensorGroup>> mSensors;
    private MutableLiveData<ArrayList<Requirement>> mRequirements;
    private MutableLiveData<ArrayList<String>> mPermissions;

    private MutableLiveData<String> mDsPath;
}
