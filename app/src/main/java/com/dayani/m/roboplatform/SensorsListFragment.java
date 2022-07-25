package com.dayani.m.roboplatform;

import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.dayani.m.roboplatform.dump.RecordSensorsActivity_old;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorInfo;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;
import com.dayani.m.roboplatform.utils.SensorsAdapter;
import com.dayani.m.roboplatform.utils.SensorsContainer;

import java.io.File;
import java.util.ArrayList;

public class SensorsListFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = FrontPanelFragment.class.getSimpleName();

    SensorRequirementsViewModel mVM_Sensors;
    ArrayList<MySensorGroup> mSensorGroups;

    private FrontPanelFragment.OnFrontPanelInteractionListener mListener;

    public SensorsListFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FrontPanelFragment.
     */
    public static SensorsListFragment newInstance() {

        SensorsListFragment fragment = new SensorsListFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);
        mSensorGroups = mVM_Sensors.getSensorsContainer().getSensorGroups();

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.sensors_list_fragment, container, false);

        view.findViewById(R.id.btn_record).setOnClickListener(this);
        view.findViewById(R.id.btn_save_info).setOnClickListener(this);

        ListView lvSensors = view.findViewById(R.id.list_sensors);
        lvSensors.setAdapter(new SensorsAdapter(getActivity(), R.layout.sensor_group, mSensorGroups));

        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {

        super.onAttach(context);

        if (context instanceof FrontPanelFragment.OnFrontPanelInteractionListener) {
            mListener = (FrontPanelFragment.OnFrontPanelInteractionListener) context;
        }
        else {
            throw new RuntimeException(context + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.btn_record) {

            Log.d(TAG, "Launch Record Fragment");
        }
        else if (id == R.id.btn_save_info) {

            Log.d(TAG, "Launch save sensor info task.");
        }
    }

    /*--------------------------------------------------------------------------------------------*/

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFrontPanelInteractionListener {

        void onFrontPanelInteraction(Class<?> targetActivity);
    }
}