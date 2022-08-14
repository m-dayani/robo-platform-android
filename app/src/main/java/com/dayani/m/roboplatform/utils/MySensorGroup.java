package com.dayani.m.roboplatform.utils;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;


public class MySensorGroup {

    public enum SensorType {

        TYPE_UNKNOWN,
        TYPE_IMU,
        TYPE_GNSS,
        TYPE_CAMERA,
        TYPE_MAGNET,
        TYPE_EXTERNAL,
        TYPE_STORAGE
    }

    private static int mIdGenerator = 0;

    private int mId;
    private final boolean mIsAvailable;

    private SensorType mType;
    private String mTitle;

    private final Map<Integer, MySensorInfo> mSensors;
    private List<Requirement> mRequirements;
    private List<String> mPermissions;

    /* ------------------------------------------------------------------------------------------ */

    public MySensorGroup() {

        mId = -1;
        mIsAvailable = true;
        mTitle = "Unknown";
        mType = SensorType.TYPE_UNKNOWN;
        mSensors =  new HashMap<>();
        mRequirements = new ArrayList<>();
        mPermissions = new ArrayList<>();
    }

    public MySensorGroup(int id, SensorType type, String title, List<MySensorInfo> sensors) {

        this();
        mId = id;
        mType = type;
        mTitle = title;

        this.setSensors(sensors);
    }

    public MySensorGroup(int id, SensorType type, String title, List<MySensorInfo> sensors,
                         List<Requirement> reqs, List<String> perms) {

        this(id, type, title, sensors);

        mRequirements = reqs;
        mPermissions = perms;
    }

    /* ------------------------------------------------------------------------------------------ */

    public void setId(int id) { this.mId = id; }
    public int getId() { return mId; }

    public SensorType getType() {
        return mType;
    }
    public void setType(SensorType mType) {
        this.mType = mType;
    }

    public List<MySensorInfo> getSensors() {

        if (mSensors == null) {
            return null;
        }
        return new ArrayList<>(mSensors.values());
    }
    public void setSensors(List<MySensorInfo> sensors) {

        for (MySensorInfo sensor : sensors) {
            mSensors.put(sensor.getId(), sensor);
        }
    }

    public String getTitle() {
        return mTitle;
    }

    public boolean isAvailable() {
        //this.updateIsAvailable();
        return mIsAvailable;
    }

    public List<Requirement> getRequirements() {
        return mRequirements;
    }
    public void setRequirements(List<Requirement> reqs) {
        mRequirements = reqs;
    }

    public List<String> getPermissions() {
        return mPermissions;
    }
    public void setPermissions(List<String> perms) {
        mPermissions = perms;
    }

    public MySensorInfo getSensorInfo(int sensorId) {

        MySensorInfo sensorInfo = null;

        if (mSensors != null && mSensors.containsKey(sensorId)) {

            sensorInfo = mSensors.get(sensorId);
        }

        return sensorInfo;
    }

    /* ------------------------------------------------------------------------------------------ */

    public static int getNextGlobalId() {
        return mIdGenerator++;
    }

    public static List<MySensorGroup> filterSensorGroups(List<MySensorGroup> inSensors,
                                                         SensorType sensorType) {

        List<MySensorGroup> outSensors = new ArrayList<>();

        for (MySensorGroup sensorGroup : inSensors) {

            if (!sensorGroup.getType().equals(sensorType)) {
                outSensors.add(sensorGroup);
            }
        }

        return outSensors;
    }

    public static List<Requirement> getUniqueRequirements(List<MySensorGroup> sensorGroups) {

        List<Requirement> requirements = new ArrayList<>();

        for (MySensorGroup sensorGroup : sensorGroups) {

            for (Requirement req : sensorGroup.getRequirements()) {

                if (!requirements.contains(req)) {
                    requirements.add(req);
                }
            }
        }

        return requirements;
    }

    public static List<String> getUniquePermissions(List<MySensorGroup> sensorGroups) {

        List<String> permissions = new ArrayList<>();

        for (MySensorGroup sensorGroup : sensorGroups) {

            for (String perm : sensorGroup.getPermissions()) {

                if (!permissions.contains(perm)) {
                    permissions.add(perm);
                }
            }
        }

        return permissions;
    }

    /**
     * Count available and checked sensors
     * TODO: Maybe check each criterion independently
     * @return Pair(int available count, int checked count)
     */
    public Pair<Integer, Integer> countAvailableAndCheckedSensors() {

        int countAvailable = 0;
        int countChecked = 0;

        for (MySensorInfo sensorInfo : mSensors.values()) {

            if (sensorInfo.isAvailable()) {
                countAvailable++;
                if (sensorInfo.isChecked()) {
                    countChecked++;
                }
            }
        }

        return new Pair<>(countAvailable, countChecked);
    }

    public static int countAvailableSensors(List<MySensorGroup> sensors,
                                            @Nullable List<SensorType> supportedSensors) {

        int count = 0;
        if (supportedSensors == null) {
            supportedSensors = new ArrayList<>();
        }

        for (MySensorGroup sensorGroup : sensors) {

            SensorType sensorType = sensorGroup.getType();
            if (supportedSensors.contains(sensorType)) {

                for (MySensorInfo sensor : sensorGroup.getSensors()) {

                    if (sensor.isAvailable() && sensor.isChecked()) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    public static MySensorGroup findSensorGroupById(List<MySensorGroup> lSensorGroup, int id) {

        for (MySensorGroup sGroup : lSensorGroup) {

            if (id == sGroup.getId()) {
                return sGroup;
            }
        }
        return null;
    }

    @NonNull
    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append("{Id: ").append(mId).append(", Type: ").append(mType).append(", Title: ")
                .append(mTitle).append(", Sensors: [");

        for (int skey : mSensors.keySet()) {
            MySensorInfo sensor = mSensors.get(skey);
            if (sensor != null) {
                sb.append(sensor).append(", ");
            }
        }

        sb.append("], Requirements: ").append(Arrays.toString(mRequirements.toArray()))
                .append(", Permissions: ").append(Arrays.toString(mPermissions.toArray())).append("}");

        return sb.toString();
    }
}
