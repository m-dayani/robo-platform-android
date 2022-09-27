package com.dayani.m.roboplatform.requirements;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.RequirementsFragment;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBaseManager.LifeCycleState;
import com.dayani.m.roboplatform.managers.MyPermissionManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link OnRequirementsInteractionListener} interface
 * to handle interaction events.
 * Use the {@link HotspotReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HotspotReqFragment extends Fragment
        implements View.OnClickListener, ActivityRequirements.OnRequirementResolved {

    private static final String TAG = HotspotReqFragment.class.getSimpleName();

    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    //private static final String ARG_CONN_TYPE = "connection-type";

    //private String mConnType;

    private EditText ipAddressTxt;
    private EditText portTxt;
    private TextView reportTxt;
    private LinearLayout actionsContainer;

    private MyWifiManager mWifi;


    public HotspotReqFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment WiNetReqFragment.
     */
    public static HotspotReqFragment newInstance() {
        HotspotReqFragment fragment = new HotspotReqFragment();
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
        mWifi = new MyWifiManager(getActivity());
        mWifi.execute(requireActivity(), LifeCycleState.ACT_CREATED);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.z_fragment_hotspot_req, container, false);

        mView.findViewById(R.id.hotspotPermission).setOnClickListener(this);
        mView.findViewById(R.id.enableHotspot).setOnClickListener(this);
        mView.findViewById(R.id.startServer).setOnClickListener(this);
        mView.findViewById(R.id.saveDefaultSocket).setOnClickListener(this);

        ipAddressTxt = mView.findViewById(R.id.ipTxtView);
        ipAddressTxt.setText(mWifi.getDefaultIp());
        portTxt = mView.findViewById(R.id.portTxtEdit);
        portTxt.setText(Integer.toString(mWifi.getDefaultPort()));
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
        mWifi.execute(requireActivity(), LifeCycleState.ACT_DESTROYED);
        super.onDetach();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //if (requestCode == MyWifiManager.getRequestPermissionCode()) {
        MyPermissionManager.onRequestAllPermissionsResult(getActivity(),
                MyWifiManager.getPermissionKey(),MyWifiManager.getRequestPermissionCode(),
                requestCode, permissions, grantResults);
        if (requestCode == MyWifiManager.getRequestPermissionCode() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mWifi.setWifiApState(requireActivity(), true);
        }
        //}
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MyWifiManager.getRequestPermissionCode())
                //&& mWifi.hasWriteSettingsPermission(getActivity()))
        {
            Log.d(TAG, "Write Settings permission granted.");
            mWifi.setWifiApState(requireActivity(), true);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.hotspotPermission: {
                Log.d(TAG, "Asking Hotspot change state permission");
                this.requestHotspotPermission();
                break;
            }
            case R.id.enableHotspot: {
                Log.d(TAG, "Enable Hotspot");
                mWifi.setWifiApState(requireActivity(), true);
                break;
            }
            case R.id.startServer: {
                Log.d(TAG, "Start Server");
                mWifi.startServer();
                break;
            }
            case R.id.saveDefaultSocket: {
                String sIp = ipAddressTxt.getText().toString();
                String sPort = portTxt.getText().toString();
                Log.d(TAG, "Saving new address: "+sIp+':'+sPort);
                mWifi.setDefaultIp(sIp);
                mWifi.setDefaultPort(Integer.parseInt(sPort));
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

    private void requestHotspotPermission() {
        Log.d(TAG, "requestHotspotPermission");
//        if (!mWifi.hasWriteSettingsPermission(getActivity())) {
//            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
//            startActivityForResult(intent, MyWifiManager.getRequestPermissionCode());
//        }
//        MyPermissionManager.checkAllPermissions(getActivity(), MyWifiManager.getPermissions(),
//                MyWifiManager.getRequestPermissionCode(), MyWifiManager.getPermissionKey());
    }

    public void onWifiEnabled() {
        //mWifi.init();
//        String defaultIp = mWifi.getLocalIpAddress();
//        ipAddressTxt.setText(defaultIp);
        this.updateViewState(actionsContainer, true);
        reportTxt.setText("1. Start Server.\n2. Run and connect client app."+'\n'+
                "3. Send 'test' command from client app to pass this requirement.");
    }

    public void onClientConnected() {
        Log.i(TAG, "Client Connected");
    }

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

    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

    }
}
