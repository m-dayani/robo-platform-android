package com.dayani.m.roboplatform.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorInfo;
import com.dayani.m.roboplatform.utils.SensorItemAdapter;

import java.util.ArrayList;

public class SensorsAdapter extends ArrayAdapter<MySensorGroup> {

    public SensorsAdapter(Context context, int resource, ArrayList<MySensorGroup> objects) {
        super(context, resource, objects);
    }

    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.sensor_group, parent, false);
        }

        MySensorGroup currSensorObj = getItem(position);
        ArrayList<MySensorInfo> currSensorItems = currSensorObj.getSensors();
        String sensorTitle = currSensorObj.getTitle();

        TextView tvSensorTitle = convertView.findViewById(R.id.sensor_grp_title);
        if (sensorTitle == null || sensorTitle.isEmpty()) {
            tvSensorTitle.setText(R.string.unknown_sensor);
        }
        else {
            tvSensorTitle.setText(sensorTitle);
        }

        ListView lvSensorItems = convertView.findViewById(R.id.sensor_items);
        if (currSensorItems == null || currSensorItems.size() <= 0) {
            ViewGroup par = (ViewGroup) lvSensorItems.getParent();
            int index = par.indexOfChild(lvSensorItems);
            par.removeView(lvSensorItems);
            ViewGroup sna_view = (ViewGroup) LayoutInflater.from(getContext()).inflate(R.layout.sensor_not_available, par, false);
            par.addView(sna_view, index);

        }
        else {
            lvSensorItems.setAdapter(new SensorItemAdapter(getContext(), R.layout.sensor_item, currSensorItems));
        }

        //return super.getView(position, convertView, parent);
        return convertView;
    }
}
