package com.dayani.m.roboplatform.dump;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.CarManualControlActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.SensorsListFragment;
import com.dayani.m.roboplatform.TestActivity;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;
import com.dayani.m.roboplatform.utils.SensorsContainer;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link FrontPanelFragment_old.OnFrontPanelInteractionListener} interface
 * to handle interaction events.
 * Use the {@link FrontPanelFragment_old#newInstance} factory method to
 * create an instance of this fragment.
 */
public class FrontPanelFragment_old extends Fragment implements View.OnClickListener {

    private static final String TAG = FrontPanelFragment_old.class.getSimpleName();

    //private ArrayList<Requirement> mRequirements;
    //private String[] mPermissions;
    SensorRequirementsViewModel mVM_Sensors;

    private OnFrontPanelInteractionListener mListener;

    public FrontPanelFragment_old() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment FrontPanelFragment.
     */
    public static FrontPanelFragment_old newInstance() {

        FrontPanelFragment_old fragment = new FrontPanelFragment_old();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.z_fragment_front_panel, container, false);

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

        Class<?> targetActivity = FrontPanelFragment_old.class;
        SensorsContainer mSensors = new SensorsContainer();


        int id = view.getId();
        if (id == R.id.startRecordAll) {

            Log.d(TAG, "startRecordAllActivity");
            mSensors = RecordSensorsActivity_old2.getSensorRequirements(getActivity(), true);
            targetActivity = SensorsListFragment.class;
        }
        else if (id == R.id.startCarManualCtrl) {

            Log.d(TAG, "startCarManualCtrl");
            mSensors = CarManualControlActivity.getSensorRequirements(getActivity());
            // TODO: Also change to a fragment
            targetActivity = CarManualControlActivity.class;
        }
        else if (id == R.id.startRecordSensors) {

            Log.d(TAG, "startRecordSensors");
            mSensors = RecordSensorsActivity_old2.getSensorRequirements(getActivity(), false);
            targetActivity = SensorsListFragment.class;
        }
        else if (id == R.id.startTest) {

            Log.d(TAG, "startTest");
            // TODO: Also change to a fragment
            targetActivity = TestActivity.class;
        }

        //mVM_Sensors.setSensorsContainer(mSensors);
        mListener.onFrontPanelInteraction(targetActivity);
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
