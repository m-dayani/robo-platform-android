package com.dayani.m.roboplatform.requirements;


import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.Handler;
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
import com.dayani.m.roboplatform.managers.MyWifiManager;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link WiNetReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class WiNetReqFragment extends Fragment
        implements View.OnClickListener, MyWifiManager.OnWifiNetworkInteractionListener {

    private static final String TAG = WiNetReqFragment.class.getSimpleName();

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    //private static final String ARG_CONN_TYPE = "connection-type";

    //private String mConnType;

    private TextView ipAddressTxt;
    private EditText portTxt;
    private TextView reportTxt;
    private LinearLayout actionsContainer;

    private MyWifiManager mWifi;
    private Handler mUiHandler = new Handler();


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
        mWifi = new MyWifiManager(getActivity(), this);
        mWifi.init();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_wi_net_req, container, false);

        mView.findViewById(R.id.enableWifi).setOnClickListener(this);
        mView.findViewById(R.id.startServer).setOnClickListener(this);
        mView.findViewById(R.id.saveDefaultPort).setOnClickListener(this);

        ipAddressTxt = mView.findViewById(R.id.ipTxtView);
        ipAddressTxt.setText(MyWifiManager.getDefaultIpAddress());
        portTxt = mView.findViewById(R.id.portTxtEdit);
        portTxt.setText(Integer.toString(MyWifiManager.getDefaultPort(getActivity())));
        reportTxt = mView.findViewById(R.id.statView);
        actionsContainer = mView.findViewById(R.id.wiNetActionsContainer);
        this.updateViewState(actionsContainer, false);

        return mView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        mWifi.clean();
        super.onDetach();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.enableWifi: {
                Log.d(TAG, "Enable Wifi");
                mWifi.setWifiState(true);
                break;
            }
            case R.id.startServer: {
                Log.d(TAG, "Start Server");
                mWifi.startServer();
                break;
            }
            case R.id.saveDefaultPort: {
                String port = portTxt.getText().toString();
                Log.d(TAG, "Save port as default: "+port);
                MyWifiManager.setDefaultPort(getActivity(), Integer.parseInt(port));
                break;
            }
            default: {
                Log.e(TAG, "Undefined Action");
                break;
            }
        }
    }

    private void updateViewState(LinearLayout view, boolean state) {
        if (view == null) return;
        for (int i = 0; i < view.getChildCount(); i++) {
            Button b = (Button) view.getChildAt(i);
            b.setEnabled(state);
        }
    }


    @Override
    public void onWifiEnabled() {
        //mWifi.init();
        String defaultIp = mWifi.getLocalIpAddress();
        ipAddressTxt.setText(defaultIp);
        this.updateViewState(actionsContainer, true);
        reportTxt.setText("1. Start Server.\n2. Run and connect client app."+'\n'+
                "3. Send 'test' command from client app to pass this requirement.");
    }

    @Override
    public void onClientConnected() {
        Log.i(TAG, "Client Connected");
    }

    @Override
    public void onInputReceived(String msg) {
        Log.d(TAG, "received: "+msg);
        if (msg.equals(MyWifiManager.getDefaultTestCommand())) {
            //mWifi.clean();
//            mUiHandler.post(new Runnable() {
//                @Override
//                public void run() {
                    permit();
//                }
//            });
            //this.onDetach();
        }
    }

    private void permit() {

        Bundle bundle = new Bundle();
        bundle.putBoolean(RequirementsFragment.KEY_REQUIREMENT_PASSED, true);
        getParentFragmentManager()
                .setFragmentResult(RequirementsFragment.KEY_REQUIREMENT_PASSED_REQUEST, bundle);
    }
}
