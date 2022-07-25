package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.view.View;

/*
    1) This Fragment does share data between every stage
    of transferring to target activity.
    TODO: Consider using ViewModel to hold necessary data.
    2) Implementing a list of requirements as a predefined
    stack of views is not advisable since it requires a lot of
    repetition with the slightest modifications
    TODO: Work with list views & ...
 */


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.dayani.m.roboplatform.requirements.LocationReqFragment;
import com.dayani.m.roboplatform.requirements.PermissionReqFragment;
import com.dayani.m.roboplatform.requirements.UsbReqFragment;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.AppUtils;
import com.dayani.m.roboplatform.utils.OnRequestPageChange;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link RequirementsFragment.OnRequirementsInteractionListener} interface
 * to handle interaction events.
 * Use the {@link RequirementsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class RequirementsFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = RequirementsFragment.class.getSimpleName();

    private static final String ARG_PERMISSIONS = "arg_permissions";
    private static final String ARG_REQUIREMENTS = "arg_requirements";

    SensorRequirementsViewModel mVM_Sensors;
    private ArrayList<Requirement> requirements;
    //private String[] permissions;
    private String connectionType = null;

    private OnRequirementsInteractionListener mListener;
    private OnRequestPageChange mPageListener;

    public RequirementsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @param requirements Parameter 1.
     * @param permissions
     * @return A new instance of fragment RequirementsFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static RequirementsFragment newInstance() {

        RequirementsFragment fragment = new RequirementsFragment();

        Bundle args = new Bundle();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);
        requirements = mVM_Sensors.getSensorsContainer().getRequirements();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_requirements, container, false);

        mView.findViewById(R.id.permissions).setOnClickListener(this);
        mView.findViewById(R.id.usb_device).setOnClickListener(this);
        mView.findViewById(R.id.wireless_conn).setOnClickListener(this);
        mView.findViewById(R.id.enable_location).setOnClickListener(this);
        mView.findViewById(R.id.all_sensors).setOnClickListener(this);
        mView.findViewById(R.id.startActivity).setOnClickListener(this);

        processRequirements(mView, requirements);

        return mView;
    }

    @Override
    public void onAttach(@NonNull Context context) {

        super.onAttach(context);
        Log.i(TAG, "onAttach");

        if (context instanceof OnRequirementsInteractionListener) {
            mListener = (OnRequirementsInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnRequirementsInteractionListener");
        }

        if (context instanceof OnRequestPageChange) {
            mPageListener = (OnRequestPageChange) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnRequestPageChange");
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
        if (id == R.id.permissions) {
            Log.d(TAG, "permissions requirement");
            PermissionReqFragment permPrefFrag = PermissionReqFragment.newInstance();
            mPageListener.onRequestPageChange(permPrefFrag, "req");
        }
        else if (id == R.id.usb_device) {
            Log.d(TAG, "usb device requirement");
            UsbReqFragment usbPrefFragment = UsbReqFragment.newInstance();
            mPageListener.onRequestPageChange(usbPrefFragment, "req");
        }
        else if (id == R.id.wireless_conn) {
            Log.d(TAG, "wireless connection requirement");
            WirelessConnFrontPanelFragment wcfpFragment =
                    WirelessConnFrontPanelFragment.newInstance();
            mPageListener.onRequestPageChange(wcfpFragment, "req");
        }
        else if (id == R.id.enable_location) {
            Log.d(TAG, "enable location requirement");
            LocationReqFragment locPrefFragment = LocationReqFragment.newInstance();
            mPageListener.onRequestPageChange(locPrefFragment, "req");
        }
        else if (id == R.id.all_sensors) {
            Log.d(TAG, "all sensors requirement");
            //mListener.onRequirementInteraction(Requirement.ALL_SENSORS);
        }
        else if (id == R.id.startActivity) {
            Log.d(TAG, "permitted to start activity");
            mListener.startTargetActivity(connectionType);
        }
        else {
            Log.e(TAG, "Undefined requirement");
        }
    }

    private void processRequirements(View view,
                                     ArrayList<Requirement> requirements) {

        if (view == null || requirements == null || requirements.size() <= 0) {

            //if requirements is null: there is no requirement!
            detachAll(view);
            enableStart(view);
            return;
        }

        if (!(requirements).contains(Requirement.PERMISSIONS)) {
            detachItem(view,R.id.permissions);
        }
        if (!(requirements).contains(Requirement.USB_DEVICE)) {
            detachItem(view,R.id.usb_device);
        }
        if (!(requirements).contains(Requirement.WIRELESS_CONNECTION)) {
            detachItem(view,R.id.wireless_conn);
        }
        if (!(requirements).contains(Requirement.ENABLE_LOCATION)) {
            detachItem(view,R.id.enable_location);
        }
        if (!(requirements).contains(Requirement.ALL_SENSORS)) {
            detachItem(view,R.id.all_sensors);
        }
    }

    private void enableStart(View view) {

        Button startActBtn = view.findViewById(R.id.startActivity);
        startActBtn.setEnabled(true);
    }

    private void detachItem(View view, int rId) {

        LinearLayout btParent = (LinearLayout) view.findViewById(rId).getParent();
        ((LinearLayout)view.findViewById(R.id.requirements_container)).removeView(btParent);
    }

    private void detachAll(View view) {
        ((LinearLayout)view.findViewById(R.id.requirements_container)).removeAllViews();
    }

    public void requirementHandled(Requirement requirement, String connType) {

        Log.i(TAG, "requirementHandled");
        if (requirement.equals(Requirement.WIRELESS_CONNECTION) && connType == null) {
            return; //the requirement has not passed!
        }
        this.connectionType = connType;
        requirements = AppUtils.removeRequirement(requirements, requirement);
        Log.d(TAG, requirements.toString());
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnRequirementsInteractionListener {

        void onRequirementInteraction(Requirement requirement,
                                      boolean isPassed, String connType, String backStackName);
        void startTargetActivity(String connType);
    }
}
