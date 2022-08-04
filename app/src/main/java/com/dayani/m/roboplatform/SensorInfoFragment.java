package com.dayani.m.roboplatform;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorInfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorInfoFragment extends Fragment {

    private static final String TAG = SensorInfoFragment.class.getSimpleName();

    private static final String ARG_SENSOR_DESCRIPTION = "arg_sensor_description";

    private String sensorDesc;

    public SensorInfoFragment() {
        // Required empty public constructor
    }


    public static SensorInfoFragment newInstance(String strDesc) {

        SensorInfoFragment fragment = new SensorInfoFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SENSOR_DESCRIPTION, strDesc);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            sensorDesc = getArguments().getString(ARG_SENSOR_DESCRIPTION);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sensor_info, container, false);

        TextView txtView = view.findViewById(R.id.sensor_desc_tv);
        txtView.setText(sensorDesc);

        return view;
    }
}