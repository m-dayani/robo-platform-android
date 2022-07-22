package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.view.View;

/*
    1) This Fragment does share data between every stage
    of transferring to target activity.
    TODO: Use ViewModel to hold necessary data.
    2) Implementing a list of requirements as a predefined
    stack of views is not advisable since it requires a lot of
    repetition with the slightest modifications
    TODO: Work with list views & ...
 */


import android.content.Context;

import androidx.fragment.app.Fragment;

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

    private View mView;

    private ArrayList<Requirement> requirements;
    private String[] permissions;
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
    public static RequirementsFragment newInstance(
            ArrayList<Requirement> requirements, String[] permissions) {
        RequirementsFragment fragment = new RequirementsFragment();
        Bundle args = new Bundle();
        args.putStringArray(ARG_PERMISSIONS, permissions);
        args.putParcelableArray(ARG_REQUIREMENTS, AppUtils.sRequirements2Parcelables(requirements));
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            //requirements = (Requirement[]) getArguments().getParcelableArray(ARG_REQUIREMENTS);
            requirements = AppUtils.sParcelables2Requirements(
                    getArguments().getParcelableArray(ARG_REQUIREMENTS));
            permissions = getArguments().getStringArray(ARG_PERMISSIONS);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mView = inflater.inflate(R.layout.fragment_requirements, container, false);

        mView.findViewById(R.id.permissions).setOnClickListener(this);
        mView.findViewById(R.id.usb_device).setOnClickListener(this);
        mView.findViewById(R.id.wireless_conn).setOnClickListener(this);
        mView.findViewById(R.id.enable_location).setOnClickListener(this);
        mView.findViewById(R.id.all_sensors).setOnClickListener(this);
        mView.findViewById(R.id.startActivity).setOnClickListener(this);

        processRequirements(mView, requirements, permissions);

        return mView;
    }

    @Override
    public void onAttach(Context context) {
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
        switch (view.getId()) {
            case R.id.permissions: {
                Log.d(TAG, "permissions requirement");
                PermissionReqFragment permPrefFrag = PermissionReqFragment.newInstance(permissions);
                //mListener.onRequirementsListInteraction(Requirement.PERMISSIONS, permPrefFrag);
                mPageListener.onRequestPageChange(permPrefFrag, "req");
                break;
            }
            case R.id.usb_device: {
                Log.d(TAG, "usb device requirement");
                UsbReqFragment usbPrefFragment = UsbReqFragment.newInstance();
                mPageListener.onRequestPageChange(usbPrefFragment, "req");
                break;
            }
            case R.id.wireless_conn: {
                Log.d(TAG, "wireless connection requirement");
                WirelessConnFrontPanelFragment wcfpFragment =
                        WirelessConnFrontPanelFragment.newInstance();
                mPageListener.onRequestPageChange(wcfpFragment, "req");
                break;
            }
            case R.id.enable_location: {
                Log.d(TAG, "enable location requirement");
                LocationReqFragment locPrefFragment = LocationReqFragment.newInstance();
                mPageListener.onRequestPageChange(locPrefFragment, "req");
                break;
            }
            case R.id.all_sensors: {
                Log.d(TAG, "all sensors requirement");
                //mListener.onRequirementInteraction(Requirement.ALL_SENSORS);
                break;
            }
            case R.id.startActivity: {
                Log.d(TAG, "permitted to start activity");
                mListener.startTargetActivity(connectionType);
                break;
            }
            default:
                Log.e(TAG, "Undefined requirement");
                break;
        }
    }

    private void processRequirements(View view,
                                     ArrayList<Requirement> requirements, String[] permissions) {
        if (view == null || requirements == null || requirements.size() <= 0) {
            //if requirements is null: there is no requirement!
            detachAll(view);
            enableStart(view);
            return;
        }
        if ((permissions == null || permissions.length <=0) &&
                (requirements).contains(Requirement.PERMISSIONS)) {
                requirements.remove(Requirement.PERMISSIONS);
        }
        if (!(requirements).contains(Requirement.PERMISSIONS)
            || permissions == null || permissions.length <= 0) {
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
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnRequirementsInteractionListener {
        //void onRequirementsListInteraction(Requirement req, Fragment targetFragment);
        void onRequirementInteraction(Requirement requirement,
                                      boolean isPassed, String connType, String backStackName);
        void startTargetActivity(String connType);
    }
}
