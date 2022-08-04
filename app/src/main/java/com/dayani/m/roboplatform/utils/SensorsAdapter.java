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
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.dayani.m.roboplatform.MainActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.SensorInfoFragment;

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

        LinearLayout llSensorItems = convertView.findViewById(R.id.sensor_items);
        ViewGroup par = (ViewGroup) llSensorItems.getParent();

        if (currSensorItems == null || currSensorItems.size() <= 0) {

            View sna_view = LayoutInflater.from(getContext()).inflate(R.layout.sensor_not_available, par, false);
            llSensorItems.addView(sna_view);
        }
        else {
            Log.i("SensorsAdapter", "size of sensor items: " + Integer.toString(currSensorItems.size()));
            //lvSensorItems.setAdapter(new SensorItemAdapter(getContext(), R.layout.sensor_item, currSensorItems));
            for (MySensorInfo sensor : currSensorItems) {

                View vSensor = LayoutInflater.from(getContext()).inflate(R.layout.sensor_item, par, false);

                CheckBox chbxSensorItem = vSensor.findViewById(R.id.chbx_sensor_item);
                chbxSensorItem.setText(sensor.getName());
                chbxSensorItem.setChecked(true);
                chbxSensorItem.setOnCheckedChangeListener((compoundButton, b) -> {
                    Log.i("CheckBox::onChChanged", "Sensor: " + sensor.getId() + " is clicked!");
                    //((MainActivity) getContext()).notifyCheckboxChanged(currSensorObj.getId(), b);
                });

                ImageButton ibtnSensorInfo = vSensor.findViewById(R.id.btn_sensor_details);
                ibtnSensorInfo.setOnClickListener(view -> {
                    Log.i("ImageButton::onClick", "Sensor info: " + sensor.getDescInfo());
                    //((MainActivity) getContext()).startSensorInfoActivity(currSensorObj.getId());
                    Fragment frag = SensorInfoFragment.newInstance(sensor.getDescInfo());
                    // TODO: Is there a better way than a hardwired context???
                    ((AppCompatActivity) getContext()).getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container_view, frag, null)
                            .setReorderingAllowed(true)
                            .addToBackStack("sensors")
                            .commit();
                });

                llSensorItems.addView(vSensor);
            }
        }

        return convertView;
    }
}
