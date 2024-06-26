package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBluetoothManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

public class ConnectionListFragment extends Fragment
        implements View.OnClickListener {

    private static final String TAG = ConnectionListFragment.class.getSimpleName();

    //private boolean mbWithWl = true;
    private MyWifiManager mWifiManager;
    private MyBluetoothManager mBtManager;

    MyBackgroundExecutor.JobListener mBackgroundHandler;


    public ConnectionListFragment() {}

    public static ConnectionListFragment newInstance() {
//            ConnectionListFragment myFragment = new ConnectionListFragment();

//            Bundle args = new Bundle();
//            args.putBoolean("withWl", withWl);
//            myFragment.setArguments(args);
//            Log.d(TAG, "ConnectionListFragment: Use Wireless? " + withWl);

        return new ConnectionListFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        mWifiManager = (MyWifiManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyWifiManager.class.getSimpleName());
        mWifiManager.setServerMode(true);

        mBtManager = (MyBluetoothManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyBluetoothManager.class.getSimpleName());
        mBtManager.setServerMode(true);

        if (context instanceof MyBackgroundExecutor.JobListener) {
            mBackgroundHandler = (MyBackgroundExecutor.JobListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.activity_controller_server, container, false);

        view.findViewById(R.id.btnWifiServer).setOnClickListener(this);
        view.findViewById(R.id.btnBluetoothServer).setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {

        FragmentActivity context = requireActivity();

        int id = view.getId();
        if (id == R.id.btnWifiServer) {

            if (!mWifiManager.isAvailable()) {
                mWifiManager.resolveAvailability(context);
            }
        }
        else if (id == R.id.btnBluetoothServer) {

            if (!mBtManager.isAvailable()) {
                mBtManager.resolveAvailability(context);
            }
        }
    }
}