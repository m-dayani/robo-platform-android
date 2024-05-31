package com.dayani.m.roboplatform.utils.adapters;
/*
 *  There are 3 ways to represent sensors nested list:
 *      1. create all views in for loops
 *      2. create the outer loop with a list view and the inner with for loop
 *      3. create the inner loop with a list view and the outer with for loop
 *
 *  the first method is the fastest and the second method is the slowest
 *
 */


import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class FastNestedListAdapter {

    public static final String TAG = FastNestedListAdapter.class.getSimpleName();

    private final SensorItemInteractions mSensorItemListener;
    private final List<MySensorGroup> mSensorGroups;
    private final Map<Integer, SensorItemsAdapter> mmSensorGroupsAdapter;
    private final Map<Integer, Map<Integer, Integer>> mmSensorsViewIds;

    public FastNestedListAdapter(List<MySensorGroup> sensors,
                                 SensorItemInteractions sensorItemListener) {

        mSensorGroups = sensors;
        mSensorItemListener = sensorItemListener;

        mmSensorGroupsAdapter = new HashMap<>();
        mmSensorsViewIds = new HashMap<>();
    }

    public void createSensorsList(Context context, ViewGroup sensorsListView) {

        if (mSensorGroups == null) {
            return;
        }

        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        sensorsListView.removeAllViews();
        ViewGroup parent = (ViewGroup) sensorsListView.getParent();

        for (MySensorGroup sensorGroup : mSensorGroups) {

            if (sensorGroup.isHidden()) {
                continue;
            }

            View sgView = getSensorsGroupView(context, sensorGroup, parent);

            sgView.setLayoutParams(lParams);
            sensorsListView.addView(sgView);
        }

        Log.v(TAG, "Added "+sensorsListView.getChildCount()+" sensor groups");
    }

    private View getSensorsGroupView(Context context, MySensorGroup sensorGroup, ViewGroup parent) {

        int groupId = sensorGroup.getId();

        View convertView = LayoutInflater.from(context).inflate(R.layout.sensor_group, parent, false);

        List<MySensorInfo> currSensorItems = sensorGroup.getSensors();
        String sensorTitle = sensorGroup.getTitle();

        TextView tvSensorTitle = convertView.findViewById(R.id.sensor_grp_title);
        if (sensorTitle == null || sensorTitle.isEmpty()) {
            tvSensorTitle.setText(R.string.unknown_sensor);
        }
        else {
            tvSensorTitle.setText(sensorTitle);
        }

        createSensorItemsViewLL(context, convertView, groupId, currSensorItems);
        //createSensorItemsViewLV(context, convertView, groupId, currSensorItems);

        return convertView;
    }

    private void createSensorItemsViewLL(Context context, View convertView, int groupId, List<MySensorInfo> sensors) {

        LinearLayout llSensorItems = convertView.findViewById(R.id.sensor_items);
        llSensorItems.removeAllViews();
        ViewGroup par = (ViewGroup) llSensorItems.getParent();

        if (sensors == null || sensors.size() <= 0) {

            View sna_view = LayoutInflater.from(context).inflate(R.layout.sensor_not_available, par, false);
            llSensorItems.addView(sna_view);
            //return null;
        }
        else {
            for (MySensorInfo sensor : sensors) {

                View vSensor = getSensorItemView(context, mSensorItemListener, groupId, sensor, par, mmSensorsViewIds);
                llSensorItems.addView(vSensor);
            }
        }

        convertView.setTag("Group."+groupId);
    }

    private void createSensorItemsViewLV(Context context, View convertView, int groupId, List<MySensorInfo> sensors) {

        ListView llSensorItems = convertView.findViewById(R.id.sensor_items);

        int lvSensorItemsId = View.generateViewId();
        llSensorItems.setId(lvSensorItemsId);

        SensorItemsAdapter sItemAdapter = new SensorItemsAdapter(context, lvSensorItemsId,
                sensors, mSensorItemListener, groupId);
        llSensorItems.setAdapter(sItemAdapter);

        if (!mmSensorGroupsAdapter.containsKey(groupId)) {
            mmSensorGroupsAdapter.put(groupId, sItemAdapter);
        }
    }

    public void updateSensorGroups(ViewGroup sensorsListView, List<MySensorGroup> sensorGroups) {

        int sensor_cnt = 0;
        for (MySensorGroup sensorGroup : sensorGroups) {

            int grpId = sensorGroup.getId();

            //ViewGroup vSensorGroup = sensorsListView.findViewWithTag("Group."+grpId).findViewById(R.id.sensor_items);
            Map<Integer, Integer> mSensorViewId = mmSensorsViewIds.get(grpId);

            if (mSensorViewId == null) {
                continue;
            }

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                int sensorId = sensorInfo.getId();
                //String tag = grpId+"."+sensorId;

                //ViewGroup vSensor = sensorsListView.findViewWithTag("Sensor."+tag);
                Integer chBxId = mSensorViewId.get(sensorId);

                if (chBxId == null) {
                    continue;
                }

                //CheckBox chBxSensor = vSensor.findViewWithTag("CheckBox."+tag);
                CheckBox chBxSensor = sensorsListView.findViewById(chBxId);

                if (chBxSensor == null) {
                    continue;
                }

                // disable onCheckedChangeListener
                chBxSensor.setOnCheckedChangeListener(null);

                chBxSensor.setChecked(sensorInfo.isChecked());

                // enable the listener again
                chBxSensor.setOnCheckedChangeListener(getCheckedChangeListener(mSensorItemListener, grpId, sensorId));

                sensor_cnt++;
            }
        }

        Log.v(TAG, "N_processed_grp: "+sensorGroups.size()+", N_processed_sensors: "+sensor_cnt);
    }

    public void updateSensorGroups(List<Integer> lSensorGroups) {

        for (int grpId : lSensorGroups) {
            SensorItemsAdapter adapter = mmSensorGroupsAdapter.get(grpId);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }


    private static View getSensorItemView(Context context,
                                          SensorItemInteractions sensorItemListener,
                                          int grpId, MySensorInfo sensor, ViewGroup par,
                                          Map<Integer, Map<Integer, Integer>> sensorsViewIds) {

        int sensorId = sensor.getId();
        String tag = grpId+"."+sensorId;
        Log.v(TAG, "sensor view tag is: "+tag);

        View vSensor = LayoutInflater.from(context).inflate(R.layout.sensor_item, par, false);

        int chBxGeneratedId = View.generateViewId();

        CheckBox chBxSensorItem = vSensor.findViewById(R.id.chbx_sensor_item);
        chBxSensorItem.setText(sensor.getName());
        chBxSensorItem.setChecked(sensor.isChecked());
        chBxSensorItem.setTag("CheckBox."+tag);
        chBxSensorItem.setId(chBxGeneratedId);
        chBxSensorItem.setOnCheckedChangeListener(getCheckedChangeListener(sensorItemListener, grpId, sensorId));

        ImageButton iBtnSensorInfo = vSensor.findViewById(R.id.btn_sensor_details);
        iBtnSensorInfo.setOnClickListener(getImageBtnClickListener(sensorItemListener, grpId, sensorId));

        vSensor.setTag("Sensor."+tag);

        if (sensorsViewIds != null) {

            Map<Integer, Integer> sensorViewIdMap = sensorsViewIds.get(grpId);
            if (sensorViewIdMap == null) {
                sensorViewIdMap = new HashMap<>();
                sensorsViewIds.put(grpId, sensorViewIdMap);
            }

            sensorViewIdMap.put(sensorId, chBxGeneratedId);
        }

        return vSensor;
    }

    private static CompoundButton.OnCheckedChangeListener getCheckedChangeListener(
            SensorItemInteractions sensorItemListener, int grpId, int sensorId) {

        return (compoundButton, b) -> {

            if (sensorItemListener != null) {
                sensorItemListener.onSensorCheckedListener(grpId, sensorId, b);
            }
            else {
                Log.i(TAG, "Parent context doesn't implement SensorInfoInteraction");
            }
        };
    }

    private static View.OnClickListener getImageBtnClickListener(
            SensorItemInteractions sensorItemListener, int grpId, int sensorId) {

        return view -> {

            if (sensorItemListener != null) {
                sensorItemListener.onSensorInfoClickListener(grpId, sensorId);
            }
            else {
                Log.i(TAG, "Parent context doesn't implement SensorInfoInteraction");
            }
        };
    }


    public static class SensorItemsAdapter extends ArrayAdapter<MySensorInfo> {

        private final SensorItemInteractions mSensorItemListener;
        private final int mGroupId;

        public SensorItemsAdapter(@NonNull Context context, int resource,
                                  @NonNull List<MySensorInfo> objects,
                                  SensorItemInteractions sensorItemListener, int grpId) {

            super(context, resource, objects);
            mSensorItemListener = sensorItemListener;
            mGroupId = grpId;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

            MySensorInfo sensorInfo = getItem(position);

            return getSensorItemView(getContext(), mSensorItemListener, mGroupId, sensorInfo, parent, null);
        }
    }

    public interface SensorItemInteractions {

        void onSensorCheckedListener(int grpId, int sensorId, boolean state);
        void onSensorInfoClickListener(int grpId, int sensorId);
    }
}
