package com.dayani.m.roboplatform.requirements;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.RequirementsFragment.OnRequirementsInteractionListener;


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

    private MyLocationManager mLocation = null;

    private OnRequirementsInteractionListener mListener;

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
        mLocation = new MyLocationManager(getActivity(), new StringBuffer());
        mLocation.updateLocationSettings();
        //this.checkLocationEnabled(); //useless, why?
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
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnRequirementsInteractionListener) {
            mListener = (OnRequirementsInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        // In fragment class callback
        Log.d(TAG, "onActivityResult");
        mLocation.onActivityResult(requestCode,resultCode,data);
        this.checkLocationEnabled();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.enableLocation: {
                Log.d(TAG, "Enabling Location");
                mLocation.updateLocationSettings();
                break;
            }
            case R.id.checkLocation: {
                Log.d(TAG, "Checking Location");
                this.checkLocationEnabled();
                break;
            }
            default:
                Log.e(TAG, "Undefined Action");
                break;
        }
    }

    private boolean isLocationEnabled() {
        if (mLocation.checkAvailability()) {
            return true;
        }
        return false;
    }

    private void checkLocationEnabled() {
        if (this.isLocationEnabled()) {
            this.permit();
        }
    }

    private void permit() {
        mListener.onRequirementInteraction(Requirement.ENABLE_LOCATION,
                true, null, "req");
    }

//    private void updateLocationStates() {
//        PreferenceCategory cat = findPreference("location_settings_category");
//        int catCount = cat.getPreferenceCount();
//
//        for (int i = 0; i < catCount; i++) {
//            Preference pref = cat.getPreference(i);
//            String prefKey = pref.getKey();
//            CharSequence prefTxt = pref.getTitle();
//            boolean stat = MyStateManager.getBoolPref(getContext(),prefKey,false);
//            if (stat) {
//                pref.setTitle(prefTxt+": Available");
//                pref.setIcon(R.drawable.ic_action_accept);
//            } else {
//                pref.setTitle(prefTxt+": Missing");
//                pref.setIcon(R.drawable.ic_action_cancel);
//            }
//        }
//    }
}
