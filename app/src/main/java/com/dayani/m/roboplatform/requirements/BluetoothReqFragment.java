package com.dayani.m.roboplatform.requirements;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
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
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.RequirementsFragment.OnRequirementsInteractionListener;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link BluetoothReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BluetoothReqFragment extends Fragment
        implements View.OnClickListener, MyBluetoothManager.OnBluetoothInteractionListener {

    private static final String TAG = BluetoothReqFragment.class.getSimpleName();

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    //private static final String ARG_CONN_TYPE = "connection-type";

    //private String mConnType;

    private TextView devNameEditTxt;
    private EditText serviceUuidEditTxt;
    private TextView reportTxt;
    private LinearLayout actionsContainer;

    private OnRequirementsInteractionListener mListener;

    private MyBluetoothManager mBlth;


    public BluetoothReqFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment WiNetReqFragment.
     */
    public static BluetoothReqFragment newInstance() {
        BluetoothReqFragment fragment = new BluetoothReqFragment();
        Bundle args = new Bundle();
        //args.putString(ARG_CONN_TYPE, param1);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        if (getArguments() != null) {
//            mConnType = getArguments().getString(ARG_CONN_TYPE);
//        }
        mBlth = new MyBluetoothManager(getActivity(), this);
        //mBlth.init();
        //mBlth.registerBrRec;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_bluetooth_req, container, false);

        mView.findViewById(R.id.enableBluetooth).setOnClickListener(this);
        mView.findViewById(R.id.startServer).setOnClickListener(this);
        mView.findViewById(R.id.saveServiceUuid).setOnClickListener(this);

        devNameEditTxt = mView.findViewById(R.id.devNameTxtEdit);
        devNameEditTxt.setText(MyBluetoothManager.getDefaultDeviceName());
        serviceUuidEditTxt = mView.findViewById(R.id.serviceUuidTxtEdit);
        serviceUuidEditTxt.setText(MyBluetoothManager.getDefaultServiceUuid(getActivity()));
        reportTxt = mView.findViewById(R.id.statView);
        actionsContainer = mView.findViewById(R.id.wiNetActionsContainer);
        this.updateViewState(actionsContainer, false);

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
        //mBlth.clean();
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.enableBluetooth: {
                Log.d(TAG, "Enabling Bluetooth");
                this.updateBlthEnabled();
                break;
            }
            case R.id.startServer: {
                Log.d(TAG, "Start Server");
                mBlth.startServer();
                break;
            }
            case R.id.saveServiceUuid: {
                String uuid = serviceUuidEditTxt.getText().toString();
                Log.d(TAG, "Save service UUID as default: "+uuid);
                MyBluetoothManager.setDefaultServiceUuid(getActivity(), uuid);
                break;
            }
            default: {
                Log.e(TAG, "Undefined Action");
                break;
            }
        }
    }

    /**
     * Attention: there are 2 ways to check if blth enabled:
     * 1. Directly in this function
     * 2. Using interface methods.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mBlth.onActivityResult(requestCode,resultCode,data);
//        if (requestCode == MyBluetoothManager.getRequestEnableBt() && mBlth.updateAvailability()) {
//            updateViewState(actionsContainer, true);
//        }
    }

    private void updateViewState(LinearLayout view, boolean state) {
        if (view == null) return;
        for (int i = 0; i < view.getChildCount(); i++) {
            Button b = (Button) view.getChildAt(i);
            b.setEnabled(state);
        }
    }

    private void updateBlthEnabled() {
        if (mBlth.updateAvailability()) {
            this.onBluetoothEnabled();
        } else {
            mBlth.requestBluetoothEnabled();
        }
    }

    @Override
    public void onBluetoothEnabled() {
        this.updateViewState(actionsContainer, true);
        reportTxt.setText("1. Start Server.\n2. Run and connect client app to the same service."
                +'\n'+"3. Send 'test' command from client app to pass this requirement.");
    }

    @Override
    public void onClientConnection() {
        Log.i(TAG, "Client Connected");
    }

    @Override
    public void onMessageReceived(String msg) {
        Log.d(TAG, "received: "+msg);
        if (msg.equals(MyWifiManager.getDefaultTestCommand())) {
            Log.d(TAG, "Connection requirement passed.");
            permit();
        }
    }

    private void permit() {
        mListener.onRequirementInteraction(Requirement.WIRELESS_CONNECTION,
                true, "bluetooth", "req");
    }
}
