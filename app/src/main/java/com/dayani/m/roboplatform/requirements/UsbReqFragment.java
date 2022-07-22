package com.dayani.m.roboplatform.requirements;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.RequirementsFragment.OnRequirementsInteractionListener;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;


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

    //private boolean usbLedState = false;

    private OnRequirementsInteractionListener mListener;

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
        switch (view.getId()) {
            case R.id.enumDevs: {
                Log.d(TAG, "Enumerating devices");
                reportTxt.setText(mUsb.enumerateDevices());
                break;
            }
            case R.id.openDevice: {
                String vId = mVidEdit.getText().toString();
                String dId = mDidEdit.getText().toString();
                Log.d(TAG, "Open device: "+vId+':'+dId);
                this.openDevice(Integer.parseInt(vId), Integer.parseInt(dId));
                break;
            }
            case R.id.saveDevice: {
                String vId = mVidEdit.getText().toString();
                String dId = mDidEdit.getText().toString();
                Log.d(TAG, "Save device: "+vId+':'+dId);
                this.saveDeviceAsDefault(Integer.parseInt(vId), Integer.parseInt(dId));
                break;
            }
            case R.id.runTestBtn: {
                Log.d(TAG, "Running test");
                this.runUsbTest();
                break;
            }
//            case R.id.onBtn: {
//                Log.d(TAG, "On/Off LED");
//
//                break;
//            }
            default:
                Log.e(TAG, "Undefined Action");
                break;
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

//    private void changeLedState() {
//        if (usbLedState) {
//            res = mUsb.sendControlMsg(UsbConstants.USB_DIR_IN,
//                    MyUSBManager.UsbCommands.USB_LED_ON, null, 0);
//            ledOn = true;
//        }
//    }

    private void permit() {
        mListener.onRequirementInteraction(Requirement.USB_DEVICE, true, null, "req");
    }

    @Override
    public void onUsbConnection(boolean connStat) {
        this.setActionsEnableState(true, usbActionView);
        //usbActionView.setEnabled(true);
        //maybe run some tests...
    }

//    private void updateUSBStates() {
//        PreferenceCategory cat = findPreference("usb_permissions_category");
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
