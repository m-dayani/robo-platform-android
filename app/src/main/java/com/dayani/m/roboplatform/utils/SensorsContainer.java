package com.dayani.m.roboplatform.utils;

import java.util.ArrayList;

public class SensorsContainer {

    public SensorsContainer() {

        mRequirements = new ArrayList<>();
        mPermissions = new ArrayList<>();
        mArrSensorGroups = new ArrayList<>();
    }

    public SensorsContainer(ArrayList<String> reqs, ArrayList<String> perms,
                            ArrayList<MySensorGroup> sensorGroups) {

        mRequirements = reqs;
        mPermissions = perms;
        mArrSensorGroups = sensorGroups;
    }

    public void addRequirement(String req) {

        mRequirements.add(req);
    }

    public void addPermission(String perm) {

        mPermissions.add(perm);
    }

    public void addSensorGroup(MySensorGroup sensorGrp) {

        mArrSensorGroups.add(sensorGrp);
    }

    public ArrayList<String> getRequirements() {
        return mRequirements;
    }

    public ArrayList<String> getPermissions() {
        return mPermissions;
    }

    public ArrayList<MySensorGroup> getSensorGroups() {
        return mArrSensorGroups;
    }

    private ArrayList<String> mRequirements;
    private ArrayList<String> mPermissions;
    private ArrayList<MySensorGroup> mArrSensorGroups;
}
