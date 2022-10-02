package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ServerWaitConnFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ServerWaitConnFragment extends Fragment implements View.OnClickListener,
        ActivityRequirements.OnRequirementResolved {

    private static final String TAG = ServerWaitConnFragment.class.getSimpleName();

    private static final String KEY_MANAGER_NAME = AppGlobals.PACKAGE_BASE_NAME+"key-manager-name";
    MyBaseManager mManager;

    public ServerWaitConnFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ServerWaitConnFragment.
     */
    public static ServerWaitConnFragment newInstance(String managerName) {

        ServerWaitConnFragment fragment = new ServerWaitConnFragment();
        Bundle args = new Bundle();
        args.putString(KEY_MANAGER_NAME, managerName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        SensorsViewModel mVmSensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        Bundle args = getArguments();
        if (args != null) {
            mManager = mVmSensors.getManager(args.getString(KEY_MANAGER_NAME));
            if (mManager != null) {
                mManager.setRequirementResponseListener(this);
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_server_wait_conn, container, false);

        view.findViewById(R.id.btnCancelServer).setOnClickListener(this);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        startServer();
    }

    @Override
    public void onStop() {
        stopServer();
        super.onStop();
    }

    @Override
    public void onClick(View view) {

        if (view.getId() == R.id.btnCancelServer) {

            // the server is automatically removed on detached event
            getParentFragmentManager().popBackStack();
        }
    }

    private void startServer() {

        if (mManager instanceof MyWifiManager) {
            ((MyWifiManager) mManager).startServer();
        }
        else if (mManager instanceof MyBluetoothManager) {
            ((MyBluetoothManager) mManager).startServer();
        }
    }

    private void stopServer() {

        if (mManager instanceof MyWifiManager) {
            ((MyWifiManager) mManager).stopServer();
        }
        if (mManager instanceof MyBluetoothManager) {
            ((MyBluetoothManager) mManager).stopServer();
        }
    }

    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

        boolean bIsConnected = false;
        if (mManager instanceof MyWifiManager) {
            bIsConnected = ((MyWifiManager) mManager).isConnected();
        }
        else if (mManager instanceof MyBluetoothManager) {
            bIsConnected = true; // todo: check connection
        }

        if (bIsConnected) {
            Log.i(TAG, "starting control panel fragment");
            Fragment frag = ControlPanelFragment.newInstance(mManager.getClass().getSimpleName());
            MainActivity.startNewFragment(getParentFragmentManager(),
                    R.id.fragment_container_view, frag, "control-panel");
        }
    }
}