package com.dayani.m.roboplatform.utils;

import java.util.ArrayList;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

public class MySensorGroup {

    public static enum SensorType {

        TYPE_UNKNOWN,
        TYPE_IMU,
        TYPE_GNSS,
        TYPE_CAMERA,
        TYPE_MAGNET,
        TYPE_EXTERNAL,
        TYPE_STORAGE
    }

    public MySensorGroup() {

        mIsAvailable = true;
        mTitle = "Unknown";
        mType = SensorType.TYPE_UNKNOWN;
        mSensors =  new ArrayList<>();
        mRequirements = new ArrayList<>();
        mPermissions = new ArrayList<>();
    }

    public MySensorGroup(SensorType type, String title, ArrayList<MySensorInfo> sensors) {

        this();
        mType = type;
        mTitle = title;
        mSensors = sensors;
    }

    public MySensorGroup(SensorType type, String title, ArrayList<MySensorInfo> sensors,
                         ArrayList<Requirement> reqs, ArrayList<String> perms) {

        this();
        mType = type;
        mTitle = title;
        mSensors = sensors;
        mRequirements = reqs;
        mPermissions = perms;
    }

    public SensorType getType() {
        return mType;
    }

    public void setType(SensorType mType) {
        this.mType = mType;
    }

    public ArrayList<MySensorInfo> getSensors() {
        return mSensors;
    }

    public void setSensors(ArrayList<MySensorInfo> mSensors) {
        this.mSensors = mSensors;
    }

    public String getTitle() {
        return mTitle;
    }

    private void updateIsAvailable() {

        int cnt = 0;
        for (MySensorInfo sensor : mSensors) {

            if (sensor.isAvailable()) {
                cnt++;
            }
        }
        mIsAvailable = cnt > 0;
    }

    public boolean isAvailable() {
        //this.updateIsAvailable();
        return mIsAvailable;
    }

    public ArrayList<Requirement> getRequirements() {
        return mRequirements;
    }

    public ArrayList<String> getPermissions() {
        return mPermissions;
    }

    public void setRequirements(ArrayList<Requirement> reqs) {
        mRequirements = reqs;
    }

    public void setPermissions(ArrayList<String> perms) {
        mPermissions = perms;
    }

    public void setAvailability(boolean availability) {
        mIsAvailable = availability;
    }

    private boolean mIsAvailable;

    private SensorType mType;
    private String mTitle;
    private ArrayList<MySensorInfo> mSensors;

    private ArrayList<Requirement> mRequirements;
    private ArrayList<String> mPermissions;
}
