package com.dayani.m.roboplatform.requirements;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.MainActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.ServerWaitConnFragment;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.Locale;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link WiNetReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WiNetReqFragment extends Fragment
        implements View.OnClickListener, ActivityRequirements.OnRequirementResolved {

    private static final String TAG = WiNetReqFragment.class.getSimpleName();


    private TextView ipAddressTxt;
    private EditText portTxt;
    private TextView reportTxt;
    private LinearLayout actionsContainer;

    private MyWifiManager mWifi;


    public WiNetReqFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment WiNetReqFragment.
     */
    public static WiNetReqFragment newInstance() {
        WiNetReqFragment fragment = new WiNetReqFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        SensorsViewModel mVmSensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        mWifi = (MyWifiManager) SensorsViewModel.getOrCreateManager(context,
                mVmSensors, MyWifiManager.class.getSimpleName());

        mWifi.setRequirementResponseListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_wi_net_req, container, false);

        mView.findViewById(R.id.enableWifiOrHotspot).setOnClickListener(this);
        Button btnConnect = mView.findViewById(R.id.connectToRemote);
        btnConnect.setOnClickListener(this);
        mView.findViewById(R.id.saveDefaultSocket).setOnClickListener(this);
        Button btnTestConn = mView.findViewById(R.id.testWirelessConn);
        btnTestConn.setOnClickListener(this);

        ipAddressTxt = mView.findViewById(R.id.ipTxtEdit);
        ipAddressTxt.setText(mWifi.getDefaultIp());
        portTxt = mView.findViewById(R.id.portTxtEdit);
        portTxt.setText(String.format(Locale.US, "%d", mWifi.getDefaultPort()));

        reportTxt = mView.findViewById(R.id.foundPeers);
        //reportTxt.setText(getString(R.string.establish_wifi_conn_proc));

        //actionsContainer = mView.findViewById(R.id.wiNetActionsContainer);
        //this.updateViewState(actionsContainer, false);

        // if wifi manager is in server mode:
        // disable the ip input, change connection text, and show available interfaces
        if (mWifi.isServerMode()) {
            ipAddressTxt.setText("0.0.0.0");
            ipAddressTxt.setEnabled(false);
            btnTestConn.setEnabled(false);
            btnConnect.setText(R.string.start_server);
            String msg = "Suggested Interfaces:\n" + MyWifiManager.getSuggestedInterfaces(true, "\t");
            reportTxt.setText(msg);
        }

        return mView;
    }

    @Override
    public void onClick(View view) {

        if (mWifi == null) {
            return;
        }

        FragmentActivity context = requireActivity();

        int id = view.getId();
        if (id == R.id.enableWifiOrHotspot) {

            //mWifi.updateSettingsEnabled();
            Log.d(TAG, "Enable Wifi");
            mWifi.enableSettingsRequirement(context);
        }
        else if (id == R.id.connectToRemote) {

            if (!mWifi.isWifiEnabled()) {
                Toast.makeText(context, "Enable Wifi Settings", Toast.LENGTH_SHORT).show();
                return;
            }

            String ip = ipAddressTxt.getText().toString();
            int port = Integer.parseInt(portTxt.getText().toString());
            mWifi.setDefaultIp(ip);
            mWifi.setDefaultPort(port);

            if (mWifi.isServerMode()) {
                Log.d(TAG, "String wifi server");
                Fragment frag = ServerWaitConnFragment.newInstance(MyWifiManager.class.getSimpleName());
                MainActivity.startNewFragment(getParentFragmentManager(),
                        R.id.fragment_container_view, frag, "wifi-server");
            }
            else {
                Log.d(TAG, "Trying to connect to remote server on " + ip + ":" + port);
                mWifi.connectClient();
            }
        }
        else if (id == R.id.saveDefaultSocket) {

            String sIp = ipAddressTxt.getText().toString();
            String sPort = portTxt.getText().toString();
            mWifi.setDefaultIp(sIp);
            mWifi.setDefaultPort(Integer.parseInt(sPort));

            Log.d(TAG, "Saving new address: "+sIp+':'+sPort);
            mWifi.saveRemoteIpAndPort(context);
        }
        else if (id == R.id.testWirelessConn) {

            mWifi.handleTestAsynchronous(null);
        }
        else {
            Log.e(TAG, "Undefined Action");
        }
    }

    private void updateViewState(LinearLayout view, boolean state) {

        if (view == null) return;

        for (int i = 0; i < view.getChildCount(); i++) {
            Button b = (Button) view.getChildAt(i);
            b.setEnabled(state);
        }
    }

    private void permit() {

        Bundle bundle = new Bundle();
        bundle.putBoolean(ActivityRequirements.KEY_REQUIREMENT_PASSED, true);
        getParentFragmentManager()
                .setFragmentResult(ActivityRequirements.KEY_REQUIREMENT_PASSED_REQUEST, bundle);

        // remove current fragment and go back to last
        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

        Log.i(TAG, "Wifi availability is changed");
        if (manager != null) {
            manager.updateAvailabilityAndCheckedSensors(requireActivity());
            if (manager.isAvailable()) {
                this.permit();
            }
        }
    }
}
