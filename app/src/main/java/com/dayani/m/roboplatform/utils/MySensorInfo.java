package com.dayani.m.roboplatform.utils;

public class MySensorInfo {

    public MySensorInfo() {

        mId = -1;
        mName = "unknown";
        mDescInfo = "description";
        mCalibInfo = "calibration";
        mIsAvailable = true;
    }

    public MySensorInfo(int id, String name, String info) {

        this();
        mId = id;
        mName = name;
        mDescInfo = info;
    }

    public int getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getCalibInfo() {
        return mCalibInfo;
    }

    public String getDescInfo() {
        return mDescInfo;
    }

    public boolean isAvailable() {
        return mIsAvailable;
    }

    public void setAvailability(boolean state) {
        mIsAvailable = state;
    }

    private int mId;
    private String mName;
    private String mCalibInfo;
    private String mDescInfo;

    private boolean mIsAvailable;
}

