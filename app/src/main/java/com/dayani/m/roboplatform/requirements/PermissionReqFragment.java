package com.dayani.m.roboplatform.requirements;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.dayani.m.roboplatform.RequirementsFragment;
import com.dayani.m.roboplatform.managers.MyPermissionManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.SensorsViewModel;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


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

    View mView;

    SensorsViewModel mVM_Sensors;

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

    ActivityResultLauncher<String[]> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            permissions -> {

                // Note: none of PermissionManager's methods is used here

                List<String> perms = mVM_Sensors.getPermissions().getValue();

                if (perms != null) {
                    for (String perm : permissions.keySet()) {

                        // storage behaviour is different between various versions of Android,
                        // hence, it requires more care
                        if (MyStorageManager.isStoragePermission(perm)) {
                            if (MyStorageManager.checkManageAllFilesPermission(requireActivity())) {
                                Log.i(TAG, "Manage all files permission is granted");
                                perms.remove(perm);
                            }
                            else if (SDK_INT >= Build.VERSION_CODES.R && mView != null) {
                                TextView reportTxt = mView.findViewById(R.id.reportTxt);
                                if (reportTxt != null) {
                                    reportTxt.setText("1. Click 'Modify Permissions'\n2. Select 'Manage All Files' for Storage Permission");
                                }
                            }
                        }
                        else if (permissions.get(perm)) {
                            // no removal problem because removing from another list
                            perms.remove(perm);
                        }
                    }
                    mVM_Sensors.getPermissions().setValue(perms);
                }
            });

    ActivityResultLauncher<Intent> permissionResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.i(TAG, "Back from permission settings activity, result code: "+result.getResultCode());
                //if (result.getResultCode() == Activity.RESULT_OK) {

                // Here, no request code
                Intent data = result.getData();

                List<String> perms = mVM_Sensors.getPermissions().getValue();

                if (perms != null) {
                    Log.i(TAG, "get permission result OK, perms size: "+ perms);
                    for (Iterator<String> iter = perms.iterator(); iter.hasNext(); ) {
                        String perm = iter.next();
                        if (MyStorageManager.isStoragePermission(perm)) {
                            if (MyStorageManager.checkManageAllFilesPermission(requireActivity())) {
                                Log.i(TAG, "Manage all files permission is granted");
                                iter.remove();
                            }
                            else if (SDK_INT >= Build.VERSION_CODES.R && mView != null) {
                                TextView reportTxt = mView.findViewById(R.id.reportTxt);
                                if (reportTxt != null) {
                                    reportTxt.setText("1. Click 'Modify Permissions'\n2. Select 'Manage All Files' for Storage Permission");
                                }
                            }
                        }
                        else if (MyPermissionManager.hasAllPermissions(requireActivity(), new String[]{perm},
                                AppGlobals.KEY_PARTIAL_PERMISSIONS)) {
                            iter.remove();
                        }
                    }
                    Log.i(TAG, "perms new size: "+ perms.size());
                    mVM_Sensors.getPermissions().setValue(perms);
                }
                //}
            });

    ActivityResultLauncher<Intent> manageAllFilesPermLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.i(TAG, "Back from permission settings activity, result code: "+result.getResultCode());

                // Here, no request code
                Intent data = result.getData();
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorsViewModel.class);

        mVM_Sensors.getPermissions().observe(requireActivity(), perms -> {
            if (mView != null) {
                updatePermissionsUI(mView, perms.toArray(new String[0]));
            }
        });

        // Instigate an initial permission request
        List<String> perms = mVM_Sensors.getPermissions().getValue();
        if (perms != null) {
            String[] strArrPerms = perms.toArray(new String[0]);
            checkPermissions(strArrPerms);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        mView = inflater.inflate(R.layout.fragment_permission_req, container, false);

        mView.findViewById(R.id.permReqAction).setOnClickListener(this);
        mView.findViewById(R.id.permReqCheckAction).setOnClickListener(this);

        List<String> perms = mVM_Sensors.getPermissions().getValue();
        if (perms != null) {
            String[] mPermissions = perms.toArray(new String[0]);
            updatePermissionsUI(mView, mPermissions);
        }

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
    public void onClick(View view) {

        List<String> mPermissions = mVM_Sensors.getPermissions().getValue();

        int id = view.getId();
        if (id == R.id.permReqCheckAction) {
            Log.d(TAG, "check permissions");
            if (mPermissions != null) {
                String[] perms = mPermissions.toArray(new String[0]);
                checkPermissions(perms);
                if (mView != null) {
                    updatePermissionsUI(mView, perms);
                }
            }
        }
        else if (id == R.id.permReqAction) {
            Log.d(TAG, "modify permission");
            this.modifyPermissions();
        }
//        else if (id == R.id.btnManageAllPerm) {
//            Log.d(TAG, "modify manage all permissions (Android 11+)");
//            this.modifyManageAllFiles();
//        }
        else {
            Log.e(TAG, "Undefined permission action");
        }
    }

    /**
     * Remove checked permission items from the list
     * @param mView
     * @param perms
     */
    private void processPermissions(View mView, String[] perms) {

        if (perms == null || perms.length <= 0) {
            //no permission is required
            this.detachAll(mView);
            this.permit();
            return;
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

        View reqView = view.findViewById(rId);
        LinearLayout parentView = null;
        if (reqView != null) {
            parentView = (LinearLayout) reqView.getParent();
        }
        if (parentView != null) {
            ((LinearLayout) view.findViewById(R.id.permissions_container)).removeView(parentView);
        }
    }

    private void detachAll(View view) {
        ((LinearLayout)view.findViewById(R.id.permissions_container)).removeAllViews();
    }

    /**
     * Changes the cross icon to passed
     * @param view
     * @param permissions
     */
    private void updateStates(View view, String[] permissions) {

        if (view == null || permissions == null || permissions.length <= 0) {
            return;
        }

        for (String perm : permissions) {
            TextView text = view.findViewWithTag(perm);
            if (text == null) {
                Log.d(TAG, "can't find: "+perm);
                continue;
            }
            LinearLayout iconParent = (LinearLayout) text.getParent();
            if (iconParent == null) {
                continue;
            }
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

    /**
     * Updates UI icons from cross to tick if permission is granted
     * and omits it. -> Is updating icons redundant?
     * @param view
     * @param permissions
     */
    private void updatePermissionsUI(View view, String[] permissions) {

        updateStates(view, permissions);
        processPermissions(view, permissions);
    }

    /**
     * Initiates a permission request
     * @param perms
     * @return
     */
    private void checkPermissions(String[] perms) {

        Log.d(TAG, "checkPermissions");
        requestPermissionLauncher.launch(perms);
    }

    private void modifyPermissions() {

        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", AppGlobals.PACKAGE_BASE_NAME, TAG);
        intent.setData(uri);
        //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("REQUEST_CODE", AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE);

        //getActivity().startActivityForResult(intent, AppGlobals.REQUEST_PARTIAL_PERMISSIONS_CODE);
        permissionResultLauncher.launch(intent);
    }

    private void modifyManageAllFiles() {

        if (SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);

            manageAllFilesPermLauncher.launch(intent);
        }
    }

    /**
     * Removes the permissions requirement and current fragment to go back to the last one
     */
    private void permit() {

        // notify parent that this task is finished
        Bundle bundle = new Bundle();
        bundle.putBoolean(RequirementsFragment.KEY_REQUIREMENT_PASSED, true);
        //bundle.putInt("KEY", Requirement.PERMISSIONS);
        getParentFragmentManager()
                .setFragmentResult(RequirementsFragment.KEY_REQUIREMENT_PASSED_REQUEST, bundle);

        // remove permission requirement
        List<Requirement> reqs = mVM_Sensors.getRequirements().getValue();
        if (reqs != null && reqs.contains(Requirement.PERMISSIONS)) {
            Log.i(TAG, "old requirements: " + reqs);
            reqs.remove(Requirement.PERMISSIONS);
            mVM_Sensors.getRequirements().setValue(reqs);
            Log.i(TAG, "current requirements: " + reqs);
        }

        // remove current fragment and go back to last (requirements)
        getParentFragmentManager().popBackStack();
    }
}
