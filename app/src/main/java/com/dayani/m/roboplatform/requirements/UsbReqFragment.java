package com.dayani.m.roboplatform.requirements;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.RequirementsFragment;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.SensorRequirementsViewModel;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnRequirementsInteractionListener} interface
 * to handle interaction events.
 * Use the {@link UsbReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class UsbReqFragment extends Fragment implements View.OnClickListener,
        MyUSBManager.OnUsbConnectionListener {

    private static final String TAG = UsbReqFragment.class.getSimpleName();
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER

    EditText mVidEdit;
    EditText mDidEdit;
    TextView reportTxt;
    LinearLayout usbActionView;

    private MyUSBManager mUsb = null;

    SensorRequirementsViewModel mVM_Sensors;

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

        mUsb = new MyUSBManager(getActivity(),this, new StringBuffer());

        mVM_Sensors = new ViewModelProvider(requireActivity()).get(SensorRequirementsViewModel.class);
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
        mVidEdit.setText(Integer.toString(MyUSBManager.getDefaultVendorId()));
        mDidEdit = mView.findViewById(R.id.deviceID);
        mDidEdit.setText(Integer.toString(MyUSBManager.getDefaultDeviceId()));
        reportTxt = mView.findViewById(R.id.statView);

        usbActionView = mView.findViewById(R.id.usbOpenActionsContainer);
        this.setActionsEnableState(false, usbActionView);

        return mView;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        //since we didn't call init(), clean is not required.
        if (mUsb != null) {
            mUsb.clean();
        }
    }

//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent data) {
//        //super.onActivityResult(requestCode, resultCode, data);
//        // In fragment class callback
//        Log.d(TAG, "onActivityResult");
//
//    }

    @Override
    public void onClick(View view) {

        int id = view.getId();
        if (id == R.id.enumDevs) {
            Log.d(TAG, "Enumerating devices");
            reportTxt.setText(mUsb.enumerateDevices());
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
            this.runUsbTest();
//            case R.id.onBtn: {
//                Log.d(TAG, "On/Off LED");
//
//                break;
//            }
        }
        else {
            Log.e(TAG, "Undefined Action");
        }
    }

    private void setActionsEnableState(boolean state, LinearLayout view) {
        if (view == null) return;
        for (int i = 0; i < view.getChildCount(); i++) {
            Button b = (Button) view.getChildAt(i);
            b.setEnabled(state);
        }
    }

    private void openDevice(int vendorId, int deviceId) {
        mUsb.tryOpenDevice(vendorId, deviceId);
    }

    private void saveDeviceAsDefault(int vendorId, int deviceId) {
        MyUSBManager.setDefaultVendorId(vendorId);
        MyUSBManager.setDefaultDeviceId(deviceId);
    }

    private void runUsbTest() {
        boolean state = mUsb.testDevice();
        if (state) {
            this.permit();
        }
    }

    private void permit() {

        Bundle bundle = new Bundle();
        bundle.putBoolean(RequirementsFragment.KEY_REQUIREMENT_PASSED, true);
        getParentFragmentManager()
                .setFragmentResult(RequirementsFragment.KEY_REQUIREMENT_PASSED_REQUEST, bundle);

        // remove USB requirement
        ArrayList<Requirement> reqs = mVM_Sensors.getRequirements().getValue();
        if (reqs != null && reqs.contains(Requirement.USB_DEVICE)) {
            reqs.remove(Requirement.USB_DEVICE);
            mVM_Sensors.getRequirements().setValue(reqs);
        }

        // remove current fragment and go back to last (requirements)
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onUsbConnection(boolean connStat) {
        this.setActionsEnableState(true, usbActionView);
        //usbActionView.setEnabled(true);
        //maybe run some tests...
    }

}
