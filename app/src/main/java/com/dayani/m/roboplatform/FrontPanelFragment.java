package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;
import com.dayani.m.roboplatform.utils.SensorsContainer;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link FrontPanelFragment.OnFrontPanelInteractionListener} interface
 * to handle interaction events.
 * Use the {@link FrontPanelFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FrontPanelFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = FrontPanelFragment.class.getSimpleName();

    //private ArrayList<Requirement> mRequirements;
    //private String[] mPermissions;
    SensorRequirementsViewModel mVM_Sensors;

    private OnFrontPanelInteractionListener mListener;

    public FrontPanelFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FrontPanelFragment.
     */
    public static FrontPanelFragment newInstance() {

        FrontPanelFragment fragment = new FrontPanelFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_front_panel, container, false);

        view.findViewById(R.id.startRecordAll).setOnClickListener(this);
        view.findViewById(R.id.startTest).setOnClickListener(this);
        view.findViewById(R.id.startCarManualCtrl).setOnClickListener(this);
        view.findViewById(R.id.startRecordSensors).setOnClickListener(this);

        return view;
    }

    @Override
    public void onAttach(@NonNull Context context) {

        super.onAttach(context);

        if (context instanceof OnFrontPanelInteractionListener) {
            mListener = (OnFrontPanelInteractionListener) context;
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

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);
        Class<?> targetActivity = MainActivity.class;
        SensorsContainer mSensors = new SensorsContainer();


        int id = view.getId();
        if (id == R.id.startRecordAll) {

            Log.d(TAG, "startRecordAllActivity");
            mSensors = this.getRecordAllInfo();
            targetActivity = RecordAllActivity.class;
        }
        else if (id == R.id.startCarManualCtrl) {

            Log.d(TAG, "startCarManualCtrl");
            mSensors = this.getCarManualControlInfo();
            targetActivity = CarManualControlActivity.class;
        }
        else if (id == R.id.startRecordSensors) {

            Log.d(TAG, "startRecordSensors");
            mSensors = this.getRecordSensorsInfo();
            targetActivity = RecordSensorsActivity.class;
        }
        else if (id == R.id.startTest) {

            Log.d(TAG, "startTest");
            mSensors = this.getTestActivityInfo();
            targetActivity = TestActivity.class;
        }

        mVM_Sensors.setSensorsContainer(mSensors);
        mListener.onFrontPanelInteraction(targetActivity);
    }

    /*--------------------------------------------------------------------------------------------*/

    private SensorsContainer getRecordAllInfo() {

        return RecordAllActivity.getSensorRequirements();
    }

    private SensorsContainer getCarManualControlInfo() {

        return CarManualControlActivity.getSensorRequirements();
    }

    private SensorsContainer getRecordSensorsInfo() {

        return RecordSensorsActivity.getSensorRequirements();
    }

    private SensorsContainer getTestActivityInfo() {

        return new SensorsContainer();
    }

    /*--------------------------------------------------------------------------------------------*/

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFrontPanelInteractionListener {

        void onFrontPanelInteraction(Class<?> targetActivity);
    }
}
