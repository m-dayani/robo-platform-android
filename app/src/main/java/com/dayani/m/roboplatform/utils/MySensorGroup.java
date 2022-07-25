package com.dayani.m.roboplatform.utils;

import java.util.ArrayList;

public class MySensorGroup {

    public static enum SensorType {

        TYPE_UNKNOWN,
        TYPE_IMU,
        TYPE_GNSS,
        TYPE_CAMERA,
        TYPE_MAGNET
    }

    public MySensorGroup() {

        mTitle = "Unknown";
        mType = SensorType.TYPE_UNKNOWN;
        mSensors =  new ArrayList<>();
    }

    public MySensorGroup(SensorType type) {

        mType = type;
    }

    public MySensorGroup(SensorType type, ArrayList<MySensorInfo> sensors) {

        mType = type;
        mSensors = sensors;
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

    private String mTitle;
    private SensorType mType;
    private ArrayList<MySensorInfo> mSensors;
}
