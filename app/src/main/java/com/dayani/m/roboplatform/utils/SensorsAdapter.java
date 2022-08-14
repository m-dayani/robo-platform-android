package com.dayani.m.roboplatform.utils;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.dayani.m.roboplatform.R;

import java.util.List;

public class SensorsAdapter extends ArrayAdapter<MySensorGroup> {

    public static final String TAG = SensorsAdapter.class.getSimpleName();

    public SensorsAdapter(Context context, int resource, List<MySensorGroup> objects) {
        super(context, resource, objects);
    }

    public SensorsAdapter(Context context, int resource, List<MySensorGroup> objects,
                          SensorItemInteraction sensorItemListener) {

        this(context, resource, objects);
        mSensorItemListener = sensorItemListener;
    }

    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.sensor_group, parent, false);
        }

        MySensorGroup currSensorObj = getItem(position);
        List<MySensorInfo> currSensorItems = currSensorObj.getSensors();
        String sensorTitle = currSensorObj.getTitle();

        TextView tvSensorTitle = convertView.findViewById(R.id.sensor_grp_title);
        if (sensorTitle == null || sensorTitle.isEmpty()) {
            tvSensorTitle.setText(R.string.unknown_sensor);
        }
        else {
            tvSensorTitle.setText(sensorTitle);
        }

        LinearLayout llSensorItems = convertView.findViewById(R.id.sensor_items);
        llSensorItems.removeAllViews();
        ViewGroup par = (ViewGroup) llSensorItems.getParent();

        if (currSensorItems == null || currSensorItems.size() <= 0) {

            View sna_view = LayoutInflater.from(getContext()).inflate(R.layout.sensor_not_available, par, false);
            llSensorItems.addView(sna_view);
        }
        else {
            Log.i(TAG, "size of sensor items: " + currSensorItems.size());
            //lvSensorItems.setAdapter(new SensorItemAdapter(getContext(), R.layout.sensor_item, currSensorItems));
            for (MySensorInfo sensor : currSensorItems) {

                View vSensor = LayoutInflater.from(getContext()).inflate(R.layout.sensor_item, par, false);

                CheckBox chbxSensorItem = vSensor.findViewById(R.id.chbx_sensor_item);
                chbxSensorItem.setText(sensor.getName());
                chbxSensorItem.setChecked(sensor.isChecked());
                chbxSensorItem.setOnCheckedChangeListener((compoundButton, b) -> {

                    if (mSensorItemListener != null) {
                        mSensorItemListener.onSensorCheckedListener(compoundButton, currSensorObj.getId(), sensor.getId(), b);
                    }
                    else {
                        Log.i(TAG, "Parent context doesn't implement SensorInfoInteraction");
                    }
                });

                ImageButton ibtnSensorInfo = vSensor.findViewById(R.id.btn_sensor_details);
                ibtnSensorInfo.setOnClickListener(view -> {

                    if (mSensorItemListener != null) {
                        mSensorItemListener.onSensorInfoClickListener(view, currSensorObj.getId(), sensor.getId());
                    }
                    else {
                        Log.i(TAG, "Parent context doesn't implement SensorInfoInteraction");
                    }
                });

                llSensorItems.addView(vSensor);
            }
        }

        return convertView;
    }

    public void setItemInteractionListener(SensorItemInteraction sensorItemInteraction) {

        mSensorItemListener = sensorItemInteraction;
    }

    private SensorItemInteraction mSensorItemListener;

    public interface SensorItemInteraction {

        void onSensorCheckedListener(View view, int grpId, int sensorId, boolean state);
        void onSensorInfoClickListener(View view, int grpId, int sensorId);
    }
}
