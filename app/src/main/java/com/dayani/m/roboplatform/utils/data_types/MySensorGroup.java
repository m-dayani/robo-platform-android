package com.dayani.m.roboplatform.utils.data_types;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MySensorGroup {

    public enum SensorType {

        TYPE_UNKNOWN,
        TYPE_IMU,
        TYPE_GNSS,
        TYPE_CAMERA,
        TYPE_MAGNET,
        TYPE_EXTERNAL,
        TYPE_STORAGE,
        TYPE_WIRELESS_NETWORK
    }

    private static int mIdGenerator = 0;

    private int mId;

    private SensorType mType;
    private String mTitle;

    private final Map<Integer, MySensorInfo> mSensors;

    /* ------------------------------------------------------------------------------------------ */

    public MySensorGroup() {

        mId = -1;
        mTitle = "Unknown";
        mType = SensorType.TYPE_UNKNOWN;
        mSensors =  new HashMap<>();
    }

    public MySensorGroup(int id, SensorType type, String title, List<MySensorInfo> sensors) {

        this();
        mId = id;
        mType = type;
        mTitle = title;

        this.setSensors(sensors);
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

    /**
     * Count available and checked sensors
     * TODO: Maybe check each criterion independently
     * @return int number of checked sensors in the group
     */
    public int countCheckedSensors() {

        int countChecked = 0;

        for (MySensorInfo sensorInfo : mSensors.values()) {

            if (sensorInfo.isChecked()) {
                countChecked++;
            }
        }

        return countChecked;
    }

    public static int countCheckedSensors(List<MySensorGroup> sensorGroups) {

        int totalCount = 0;

        if (sensorGroups == null) {
            return totalCount;
        }

        for (MySensorGroup sensorGroup : sensorGroups) {

            totalCount += sensorGroup.countCheckedSensors();
        }

        return totalCount;
    }

    public static MySensorGroup findSensorGroupById(List<MySensorGroup> lSensorGroup, int id) {

        for (MySensorGroup sGroup : lSensorGroup) {

            if (sGroup == null) {
                continue;
            }
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

        for (int sKey : mSensors.keySet()) {
            MySensorInfo sensor = mSensors.get(sKey);
            if (sensor != null) {
                sb.append(sensor).append(", ");
            }
        }

        sb.append("]}");

        return sb.toString();
    }
}
