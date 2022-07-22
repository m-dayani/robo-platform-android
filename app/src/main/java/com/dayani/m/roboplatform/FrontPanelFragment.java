package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

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


    private ArrayList<Requirement> mRequirements;
    private String[] mPermissions;

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

//    @Override
//    public void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        //if (getArguments() != null) {
//            //mParam1 = getArguments().getString(ARG_PARAM1);
//        //}
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

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

        int id = view.getId();
        if (id == R.id.startRecordAll) {

            Log.d(TAG, "startRecordAllActivity");
            this.getRecordAllInfo();
            mListener.onFrontPanelInteraction(RecordAllActivity.class, mRequirements, mPermissions);
        }
        else if (id == R.id.startCarManualCtrl) {

            Log.d(TAG, "startCarManualCtrl");
            this.getCarManualControlInfo();
            mListener.onFrontPanelInteraction(CarManualControlActivity.class, mRequirements, mPermissions);
        }
        else if (id == R.id.startRecordSensors) {

            Log.d(TAG, "startRecordSensors");
            this.getRecordSensorsInfo();
            mListener.onFrontPanelInteraction(RecordSensorsActivity.class, mRequirements, mPermissions);
        }
        else if (id == R.id.startTest) {

            Log.d(TAG, "startTest");
            this.getTestActivityInfo();
            mListener.onFrontPanelInteraction(TestActivity.class, mRequirements, mPermissions);
        }
    }

    /*--------------------------------------------------------------------------------------------*/

    private void getRecordAllInfo() {

        mRequirements = RecordAllActivity.getActivityRequirements();
        mPermissions = RecordAllActivity.getActivityPermissions();
        //RecordAllActivity.class.getSimpleName();
    }

    private void getCarManualControlInfo() {

        mRequirements = CarManualControlActivity.getActivityRequirements();
        mPermissions = CarManualControlActivity.getActivityPermissions();
    }

    private void getRecordSensorsInfo() {

        mRequirements = RecordSensorsActivity.getActivityRequirements();
        mPermissions = RecordSensorsActivity.getActivityPermissions();
    }

    private void getTestActivityInfo() {

        mRequirements = null;
        mPermissions = null;
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

        void onFrontPanelInteraction(Class<?> targetActivity,
                ArrayList<Requirement> requirements, String[] perms);
    }
}
