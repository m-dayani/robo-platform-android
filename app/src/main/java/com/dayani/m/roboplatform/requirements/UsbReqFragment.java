package com.dayani.m.roboplatform.requirements;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.Locale;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnRequirementsInteractionListener} interface
 * to handle interaction events.
 * Use the {@link UsbReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class UsbReqFragment extends Fragment implements View.OnClickListener,
        ActivityRequirements.OnRequirementResolved {

    private static final String TAG = UsbReqFragment.class.getSimpleName();
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

    EditText mVidEdit;
    EditText mDidEdit;
    TextView reportTxt;
    //LinearLayout usbActionView;

    private MyUSBManager mUsb = null;

    SensorsViewModel mVM_Sensors;

    //private boolean usbLedState = false;

    public UsbReqFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment UsbPrefFragment.
     */
    public static UsbReqFragment newInstance() {

        UsbReqFragment fragment = new UsbReqFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorsViewModel.class);

        mUsb = (MyUSBManager) mVM_Sensors.getManager(MyUSBManager.class.getSimpleName());
        if (mUsb != null) {
            mUsb.setRequirementResponseListener(this);
        }
    }

    @Override
    public void onDestroy() {

        if (mUsb != null) {

            mUsb.setRequirementResponseListener(null);
            // maybe user has already opened the device
            //mUsb.close();
        }

        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_usb_req, container, false);

        mView.findViewById(R.id.enumDevs).setOnClickListener(this);
        mView.findViewById(R.id.openDevice).setOnClickListener(this);
        mView.findViewById(R.id.runTestBtn).setOnClickListener(this);
        mView.findViewById(R.id.saveDevice).setOnClickListener(this);
        //mView.findViewById(R.id.onBtn).setOnClickListener(this);

        mVidEdit = mView.findViewById(R.id.vendorID);
        mDidEdit = mView.findViewById(R.id.deviceID);
        if (mUsb != null) {
            mVidEdit.setText(String.format(Locale.US, "%d", mUsb.getVendorId()));
            mDidEdit.setText(String.format(Locale.US, "%d", mUsb.getDeviceId()));
        }

        reportTxt = mView.findViewById(R.id.foundPeers);

        //usbActionView = mView.findViewById(R.id.usbOpenActionsContainer);
        //this.setActionsEnableState(mUsb.isUsbDeviceAvailable(), usbActionView);

        return mView;
    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.enumDevs) {

            Log.d(TAG, "Enumerating devices");
            if (mUsb != null) {
                String devices = MyUSBManager.usbDeviceListToString(mUsb.enumerateDevices());
                reportTxt.setText(devices);
            }
            else {
                reportTxt.setText(R.string.w_usb_manager_null);
            }
        }
        else if (id == R.id.openDevice) {

            String vId = mVidEdit.getText().toString();
            String dId = mDidEdit.getText().toString();
            Log.d(TAG, "Open device: " + vId + ':' + dId);
            this.openDevice(Integer.parseInt(vId), Integer.parseInt(dId));
        }
        else if (id == R.id.saveDevice) {

            String vId = mVidEdit.getText().toString();
            String dId = mDidEdit.getText().toString();
            Log.d(TAG, "Save device: " + vId + ':' + dId);
            this.saveDeviceAsDefault(Integer.parseInt(vId), Integer.parseInt(dId));
        }
        else if (id == R.id.runTestBtn) {

            Log.d(TAG, "Running test");
            //mUsb.handleTestSynchronous(null);
            mUsb.updateUsbAvailabilityState();
            //reportTxt.setText("Run test twice for Arduino");
        }
        else {
            Log.e(TAG, "Undefined Action");
        }
    }

    private void openDevice(int vendorId, int deviceId) {

        if (mUsb != null) {
            mUsb.setVendorId(vendorId);
            mUsb.setDeviceId(deviceId);

            if (!mUsb.canFindTargetDevice()) {
                reportTxt.setText(String.format(Locale.US, "%s: \"%d:%d\"",
                        getString(R.string.w_cannot_find_dev_header), vendorId, deviceId));
                return;
            }

            if (!mUsb.isDevicePermitted()) {
                reportTxt.setText(R.string.msg_grant_usb_perm);
                mUsb.requestDevicePermission();
                return;
            }

            if (mUsb.tryOpenDeviceAndUpdateInfo()) {
                reportTxt.setText(R.string.usb_opened_success);
            }
        }
    }

    private void saveDeviceAsDefault(int vendorId, int deviceId) {

        if (mUsb != null) {
            mUsb.setVendorId(vendorId);
            mUsb.setDeviceId(deviceId);
            mUsb.saveVendorAndDeviceId(requireActivity());
        }
    }

    private void permit() {

        Bundle bundle = new Bundle();
        bundle.putBoolean(ActivityRequirements.KEY_REQUIREMENT_PASSED, true);
        getParentFragmentManager()
                .setFragmentResult(ActivityRequirements.KEY_REQUIREMENT_PASSED_REQUEST, bundle);

        // remove USB requirement -> not necessary
        // remove current fragment and go back to last (requirements)
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

        //maybe run some tests...
        if (manager != null) {
            //manager.updateAvailabilityAndCheckedSensors(requireActivity());
            if (manager.isAvailable()) {
//                if (reportTxt != null) {
//                    reportTxt.setText(R.string.test_successful);
//                }
                this.permit();
            }
        }
    }
}
