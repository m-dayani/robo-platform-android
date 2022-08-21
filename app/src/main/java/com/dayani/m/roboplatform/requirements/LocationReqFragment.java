package com.dayani.m.roboplatform.requirements;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.RequirementsFragment;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnRequirementsInteractionListener} interface
 * to handle interaction events.
 * Use the {@link LocationReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LocationReqFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = LocationReqFragment.class.getSimpleName();
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

    SensorsViewModel mVM_Sensors;

    private MyLocationManager mLocation = null;

    public LocationReqFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment LocationPrefFragment.
     */
    public static LocationReqFragment newInstance() {

        LocationReqFragment fragment = new LocationReqFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        //if (getArguments() != null) { }
        mLocation = new MyLocationManager(requireActivity());
        //mLocation.updateLocationSettings(requireActivity());
        //this.checkLocationEnabled(); //useless, why?

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorsViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_location_req, container, false);
        mView.findViewById(R.id.enableLocation).setOnClickListener(this);
        mView.findViewById(R.id.checkLocation).setOnClickListener(this);
        return mView;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        // In fragment class callback
        Log.d(TAG, "onActivityResult");
        //mLocation.onActivityResult(requireActivity(), requestCode,resultCode,data);
        this.checkLocationEnabled();
    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.enableLocation) {
            Log.d(TAG, "Enabling Location");
            //mLocation.updateLocationSettings(requireActivity());
        }
        else if (id == R.id.checkLocation) {
            Log.d(TAG, "Checking Location");
            this.checkLocationEnabled();
        }
        else {
            Log.e(TAG, "Undefined Action");
        }
    }

    private boolean isLocationEnabled() {
        return false; //mLocation.checkAvailability(requireActivity());
    }

    private void checkLocationEnabled() {
        if (this.isLocationEnabled()) {
            this.permit();
        }
    }

    private void permit() {

        Bundle bundle = new Bundle();
        bundle.putBoolean(RequirementsFragment.KEY_REQUIREMENT_PASSED, true);
        getParentFragmentManager()
                .setFragmentResult(RequirementsFragment.KEY_REQUIREMENT_PASSED_REQUEST, bundle);

        // remove location requirement
        List<Requirement> reqs = mVM_Sensors.getRequirements().getValue();
        if (reqs != null && reqs.contains(Requirement.ENABLE_LOCATION)) {
            reqs.remove(Requirement.ENABLE_LOCATION);
            mVM_Sensors.getRequirements().setValue(reqs);
        }

        // remove current fragment and go back to last (requirements)
        getParentFragmentManager().popBackStack();
    }
}
