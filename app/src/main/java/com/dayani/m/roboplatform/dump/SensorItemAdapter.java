package com.dayani.m.roboplatform.dump;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;

import androidx.annotation.NonNull;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;

import java.util.ArrayList;

public class SensorItemAdapter extends ArrayAdapter<MySensorInfo> {


    public SensorItemAdapter(Context context, int resource, ArrayList<MySensorInfo> objects) {
        super(context, resource, objects);
    }

    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.sensor_item, parent, false);
        }

        MySensorInfo currSensorObj = getItem(position);

        CheckBox chbxSensorItem = convertView.findViewById(R.id.chbx_sensor_item);
        chbxSensorItem.setText(currSensorObj.getName());
        chbxSensorItem.setChecked(true);
        chbxSensorItem.setOnCheckedChangeListener((compoundButton, b) -> {
            Log.i("CheckBox::onChChanged", "Sensor: " + currSensorObj.getId() + " is clicked!");
            //((MainActivity) getContext()).notifyCheckboxChanged(currSensorObj.getId(), b);
        });

        ImageButton ibtnSensorInfo = convertView.findViewById(R.id.btn_sensor_details);
        ibtnSensorInfo.setOnClickListener(view -> {
            Log.i("ImageButton::onClick", "Sensor: " + currSensorObj.getId() + " is clicked!");
            //((MainActivity) getContext()).startSensorInfoActivity(currSensorObj.getId());
        });

        return convertView;
    }
}
