package com.dayani.m.roboplatform.utils;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;


public class MySensorInfo {

    private int mId;
    private String mName;
    private Map<String, String> mCalibInfo;
    private Map<String, String> mDescInfo;

    private boolean mIsAvailable;
    private boolean mIsChecked;

    /* ------------------------------------------------------------------------------------------ */

    public MySensorInfo() {

        mId = -1;
        mName = "unknown";
        mDescInfo = new HashMap<>();
        mCalibInfo = new HashMap<>();
        mIsAvailable = false;
        mIsChecked = true;
    }

    public MySensorInfo(int id, String name) {

        this();
        mId = id;
        mName = name;
    }

    public MySensorInfo(int id, String name, boolean isAvailable) {

        this(id, name);
        mIsAvailable = isAvailable;
    }

    /* ------------------------------------------------------------------------------------------ */

    public int getId() {
        return mId;
    }
    public void setId(int id) { mId = id; }

    public String getName() {
        return mName;
    }

    public String getCalibInfoString(String prefix) {

        return serializeInfoMap(prefix, mCalibInfo);
    }
    public void setCalibInfo(Map<String, String> info) { mCalibInfo = info; }

    public String getDescInfoString(String prefix) {

        return serializeInfoMap(prefix, mDescInfo);
    }
    public void setDescInfo(Map<String, String> info) { mDescInfo = info; }

    public boolean isAvailable() {
        return mIsAvailable;
    }
    public void setAvailability(boolean state) {
        mIsAvailable = state;
    }

    public boolean isChecked() { return mIsChecked; }
    public void setChecked(boolean checked) { mIsChecked = checked; }

    /* ------------------------------------------------------------------------------------------ */

    private static String serializeInfoMap(String prefix, Map<String, String> infoMap) {

        StringBuilder info = new StringBuilder();

        for (String infoKey : infoMap.keySet()) {

            String infoVal = infoMap.get(infoKey);

            String quotes = "";
            if (infoKey.equalsIgnoreCase("name")) {
                quotes = "\"";
            }

            info.append(prefix).append(infoKey).append(": ")
                    .append(quotes).append(infoVal).append(quotes).append("\n");
        }

        return info.toString();
    }

    @NonNull
    public String toString() {

        return "{Id: " + mId + ", Name: " + mName + ", CalibInfo: " +
                mCalibInfo + ", DescInfo: " + mDescInfo + ", Available: " +
                mIsAvailable + ", isChecked: " + mIsChecked + "}";
    }
}

