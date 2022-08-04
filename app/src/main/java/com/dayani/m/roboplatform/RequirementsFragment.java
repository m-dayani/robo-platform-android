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
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
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
import com.dayani.m.roboplatform.utils.MyFragmentInteraction;
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
public class RequirementsFragment extends Fragment implements View.OnClickListener, FragmentResultListener {

    private static final String TAG = RequirementsFragment.class.getSimpleName();

    public static final String KEY_REQUIREMENT_PASSED_REQUEST = TAG + "requirement-passed-request";
    public static final String KEY_REQUIREMENT_PASSED = TAG + "requirement-passed";

    SensorRequirementsViewModel mVM_Sensors;

    View mView;

    public RequirementsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     * @param requirements Parameter 1.
     * @return A new instance of fragment RequirementsFragment.
     */
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
        mVM_Sensors.getRequirements().observe(this, reqs -> {
            // update UI
            if (mView != null) {
                processRequirements(mView, reqs);
            }
        });

        getParentFragmentManager()
                .setFragmentResultListener(KEY_REQUIREMENT_PASSED_REQUEST, this, this);
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

        ArrayList<Requirement> requirements = mVM_Sensors.getRequirements().getValue();
        processRequirements(mView, requirements);

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
    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {

        if (requestKey.equals(KEY_REQUIREMENT_PASSED_REQUEST)) {

            if (bundle.getBoolean(KEY_REQUIREMENT_PASSED)) {

                Log.i(TAG, "requirement granted");

                // here and in observer the view is updated based on changes in requirements
//                ArrayList<Requirement> reqs = mVM_Sensors.getRequirements().getValue();
//                processRequirements(mView, reqs);
            }
        }
    }

    @Override
    public void onClick(View view) {

        Fragment childFragment = null;

        int id = view.getId();
        if (id == R.id.permissions) {
            Log.d(TAG, "permissions requirement");
            childFragment = PermissionReqFragment.newInstance();
        }
        else if (id == R.id.usb_device) {
            Log.d(TAG, "usb device requirement");
            childFragment = UsbReqFragment.newInstance();
        }
        else if (id == R.id.wireless_conn) {
            Log.d(TAG, "wireless connection requirement");
            childFragment = WirelessConnFrontPanelFragment.newInstance();
        }
        else if (id == R.id.enable_location) {
            Log.d(TAG, "enable location requirement");
            childFragment = LocationReqFragment.newInstance();
        }
        else if (id == R.id.all_sensors) {
            Log.d(TAG, "all sensors requirement");
        }
        else if (id == R.id.startActivity) {
            Log.d(TAG, "permitted to start activity");
            childFragment = SensorsListFragment.newInstance();
        }
        else {
            Log.e(TAG, "Undefined requirement");
        }

        if (childFragment != null) {

            Log.i(TAG, "Starting: " + childFragment.getTag());
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container_view, childFragment, null)
                    .setReorderingAllowed(true)
                    .addToBackStack("requirement")
                    .commit();
        }
    }

    private void processRequirements(View view, ArrayList<Requirement> requirements) {

        if (view == null || requirements == null || requirements.size() <= 0) {

            //if requirements is null: there is no requirement!
            if (view != null) {
                detachAll(view);
                enableStart(view);
            }
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
        if (startActBtn != null) {
            startActBtn.setEnabled(true);
        }
    }

    private void detachItem(View view, int rId) {

        View reqView = view.findViewById(rId);
        LinearLayout btParent = null;
        if (reqView != null) {
            btParent = (LinearLayout) reqView.getParent();
        }
        if (btParent != null) {
            ((LinearLayout) view.findViewById(R.id.requirements_container)).removeView(btParent);
        }
    }

    private void detachAll(View view) {
        ((LinearLayout)view.findViewById(R.id.requirements_container)).removeAllViews();
    }
}
