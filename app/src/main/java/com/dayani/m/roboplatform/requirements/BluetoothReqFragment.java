package com.dayani.m.roboplatform.requirements;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dayani.m.roboplatform.MainActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.RequirementsFragment;
import com.dayani.m.roboplatform.ServerWaitConnFragment;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.utils.adapters.BtPeersAdapter;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link BluetoothReqFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BluetoothReqFragment extends Fragment
        implements View.OnClickListener, ActivityRequirements.OnRequirementResolved,
        BtPeersAdapter.PeersListInteraction {

    private static final String TAG = BluetoothReqFragment.class.getSimpleName();

    private Button btnStartServer;
    private Button btnFindRemote;
    private Button btnTestConn;

    private BtPeersAdapter btPeersAdapter;

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
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        SensorsViewModel mVmSensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        mBlth = (MyBluetoothManager) SensorsViewModel.getOrCreateManager(context,
                mVmSensors, MyBluetoothManager.class.getSimpleName());

        if (mBlth != null) {
            mBlth.setRequirementResponseListener(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_bluetooth_req, container, false);

        mView.findViewById(R.id.enableBluetooth).setOnClickListener(this);

        btnTestConn = mView.findViewById(R.id.btnInitTest);
        btnTestConn.setOnClickListener(this);

        btnStartServer = mView.findViewById(R.id.startServer);
        btnStartServer.setOnClickListener(this);

        btnFindRemote = mView.findViewById(R.id.btnFindRemote);
        btnFindRemote.setOnClickListener(this);

        TextView devNameEditTxt = mView.findViewById(R.id.devNameTxtEdit);
        String msg = "Device Name:\t"+MyBluetoothManager.getDefaultDeviceName()+"\n"+
                "Service Name:\t"+MyBluetoothManager.getDefaultServiceName();
        devNameEditTxt.setText(msg);

        // init. list of peers
        btPeersAdapter = new BtPeersAdapter(mBlth.queryPairedDevices(), this);
        RecyclerView recyclerView = mView.findViewById(R.id.foundPeers);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        recyclerView.setAdapter(btPeersAdapter);

        return mView;
    }

    @Override
    public void onResume() {

        super.onResume();
        if (mBlth != null) {
            mBlth.execute(requireActivity(), MyBaseManager.LifeCycleState.RESUMED);
        }
        updateUI();
    }

    @Override
    public void onPause() {

        if (mBlth != null) {
            mBlth.execute(requireActivity(), MyBaseManager.LifeCycleState.PAUSED);
        }
        super.onPause();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onClick(View view) {

        if (mBlth == null) {
            return;
        }

        FragmentActivity context = requireActivity();

        int id = view.getId();
        if (id == R.id.enableBluetooth) {

            Log.d(TAG, "Enabling Bluetooth");
            mBlth.enableSettingsRequirement(context);
        }
        else if (id == R.id.startServer) {

            if (!mBlth.isSettingsEnabled()) {
                Log.d(TAG, "Bluetooth is not enabled");
                return;
            }
            Log.d(TAG, "Start Server");
            Fragment frag = ServerWaitConnFragment.newInstance(MyBluetoothManager.class.getSimpleName());
            MainActivity.startNewFragment(getParentFragmentManager(),
                    R.id.fragment_container_view, frag, "bt-server");
           // mBlth.startServer(context);
        }
        else if (id == R.id.btnFindRemote) {

            Log.d(TAG, "Find remote server");
            btPeersAdapter.setPeerNames(mBlth.queryPairedDevices());
            btPeersAdapter.notifyDataSetChanged();
        }
        else if (id == R.id.btnInitTest) {

            Log.d(TAG, "Run device test");
            mBlth.handleTestAsynchronous(null);
        }
        else {
            Log.e(TAG, "Undefined Action");
        }
    }

    private void updateUI() {

        if (mBlth == null) {
            return;
        }

        boolean enableServer = mBlth.isServerMode();
        btnStartServer.setEnabled(enableServer);
        btnFindRemote.setEnabled(!enableServer);
        btnTestConn.setEnabled(!enableServer);
    }

    private void permit() {

        Log.d(TAG, "Connection requirement passed.");

        Bundle bundle = new Bundle();
        bundle.putBoolean(RequirementsFragment.KEY_REQUIREMENT_PASSED, true);
        getParentFragmentManager()
                .setFragmentResult(RequirementsFragment.KEY_REQUIREMENT_PASSED_REQUEST, bundle);

        getParentFragmentManager().popBackStack();
    }

    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

        //this.updateUI();

        if (manager != null) {
            manager.updateAvailabilityAndCheckedSensors(requireActivity());
            if (manager.isAvailable()) {
                this.permit();
            }
        }
    }

    @Override
    public void onItemClicked(String item) {

        if (mBlth != null) {
            // set bt manager's selected device
            mBlth.setDefaultDevice(item);
            Log.d(TAG, "Set default device: "+item);
            mBlth.connectToRemote();
        }
    }
}
