package com.dayani.m.roboplatform.requirements;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.MyPermissionManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.RequirementsFragment.OnRequirementsInteractionListener;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;

import java.util.Arrays;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link com.dayani.m.roboplatform.RequirementsFragment.OnRequirementsInteractionListener} interface
 * to handle interaction events.
 * Use the {@link PermissionReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class PermissionReqFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = PermissionReqFragment.class.getSimpleName();

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PERMISSIONS = "arg-permissions";

    View mView;

    SensorRequirementsViewModel mVM_Sensors;
    private String[] mPermissions;

    private OnRequirementsInteractionListener mListener;

    public PermissionReqFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment PermissionReqFragment.
     */
    public static PermissionReqFragment newInstance() {

        PermissionReqFragment fragment = new PermissionReqFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);
        mPermissions = mVM_Sensors.getSensorsContainer().getPermissions().toArray(new String[0]);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mView = inflater.inflate(R.layout.fragment_permission_req, container, false);

        mView.findViewById(R.id.permReqAction).setOnClickListener(this);
        mView.findViewById(R.id.permReqCheckAction).setOnClickListener(this);
        //reportTxt = (TextView) findViewById(R.id.reportText);

        processPermissions(mView, mPermissions);
        //updateStates(mView, mPermissions);

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
        Log.d(TAG, "onActivityResult");
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE) {
            //check for permissions after return of activity
            this.hasPermissions(this.mPermissions);
        }
    }

    //(knowing that checkPermissions will call this again implicitly!
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE) {
            MyPermissionManager.onRequestAllPermissionsResult(getActivity(),
                    AppGlobals.KEY_PARTIAL_PERMISSIONS, AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE,
                    requestCode, permissions, grantResults);
            this.hasPermissions(permissions);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.permReqCheckAction: {
                Log.d(TAG, "check permissions");
                this.checkPermissions(mPermissions);
                break;
            }
            case R.id.permReqAction: {
                Log.d(TAG, "modify permission");
                this.modifyPermissions();
                break;
            }
            default:
                Log.e(TAG, "Undefined permission action");
                break;
        }
    }

    private void processPermissions(View mView, String[] perms) {
        if (perms == null || perms.length <= 0) {
            //no permission is required
            this.permit();
        }
        if (!Arrays.asList(perms).contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            detachItem(mView, R.id.storagePermission);
        }
        if (!Arrays.asList(perms).contains(Manifest.permission.CAMERA)) {
            detachItem(mView, R.id.cameraPermission);
        }
        if (!Arrays.asList(perms).contains(Manifest.permission.RECORD_AUDIO)) {
            detachItem(mView, R.id.microphonePermission);
        }
        if (!Arrays.asList(perms).contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
            detachItem(mView, R.id.locationPermission);
        }
    }

    private void detachItem(View view, int rId) {
        LinearLayout parentView = (LinearLayout) view.findViewById(rId).getParent();
        ((LinearLayout)view.findViewById(R.id.permissions_container)).removeView(parentView);
    }

    private void detachAll(View view) {
        ((LinearLayout)view.findViewById(R.id.permissions_container)).removeAllViews();
    }

    private void updateStates(View view, String[] permissions) {
        if (view == null || permissions == null || permissions.length <= 0) {
            return;
        }
        for (String perm : permissions) {
            TextView text =
                    (TextView) view.findViewWithTag(perm);
            if (text == null) {
                Log.d(TAG, "can't find: "+perm);
                continue;
            }
            LinearLayout iconParent = (LinearLayout) text.getParent();
            ImageView icon = (ImageView) iconParent.getChildAt(0);
            String prefKey = MyPermissionManager.getPermissionKey(perm);
            boolean stat = MyStateManager.getBoolPref(getContext(),prefKey,false);
            if (stat) {
                icon.setImageResource(R.drawable.ic_action_accept);
            }
            else {
                icon.setImageResource(R.drawable.ic_action_cancel);
            }
        }
    }

    private void checkPermissions(String[] perms) {
        Log.d(TAG, "checkPermissions");
        boolean res = MyPermissionManager.checkAllPermissions(this.getActivity(),
                perms, AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE,
                AppGlobals.KEY_PARTIAL_PERMISSIONS);
        updateStates(mView, perms);
        if (res) {
            this.permit();
        }
    }

    //check permissions without request
    private void hasPermissions(String[] perms) {
        Log.d(TAG, "checkPermissions");
        boolean res = MyPermissionManager.hasAllPermissions(this.getActivity(),
                perms, AppGlobals.KEY_PARTIAL_PERMISSIONS);
        updateStates(mView, perms);
        if (res) {
            this.permit();
        }
    }

    private void modifyPermissions() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", AppGlobals.PACKAGE_BASE_NAME, TAG);
        intent.setData(uri);
        //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getActivity().startActivityForResult(intent, AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE);
    }

    private void permit() {
        mListener.onRequirementInteraction(Requirement.PERMISSIONS, true, null, "req");
    }
}
