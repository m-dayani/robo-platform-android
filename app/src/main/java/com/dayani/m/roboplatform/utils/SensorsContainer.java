package com.dayani.m.roboplatform.utils;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

public class SensorsContainer {

    public SensorsContainer() {

        mRequirements = new ArrayList<>();
        mPermissions = new ArrayList<>();
        mArrSensorGroups = new ArrayList<>();
    }

    public SensorsContainer(ArrayList<ActivityRequirements.Requirement> reqs, ArrayList<String> perms,
                            ArrayList<MySensorGroup> sensorGroups) {

        mRequirements = reqs;
        mPermissions = perms;
        mArrSensorGroups = sensorGroups;
    }

    public void addRequirement(ActivityRequirements.Requirement req) {

        mRequirements.add(req);
    }

    public void addPermission(String perm) {

        mPermissions.add(perm);
    }

    public void addSensorGroup(MySensorGroup sensorGrp) {

        mArrSensorGroups.add(sensorGrp);
    }

    public ArrayList<ActivityRequirements.Requirement> getRequirements() {
        return mRequirements;
    }

    public ArrayList<String> getPermissions() {
        return mPermissions;
    }

    public ArrayList<MySensorGroup> getSensorGroups() {
        return mArrSensorGroups;
    }

    private ArrayList<Requirement> mRequirements;
    private ArrayList<String> mPermissions;
    private ArrayList<MySensorGroup> mArrSensorGroups;
}
