package com.dayani.m.roboplatform;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.managers.MyBaseManager.LifeCycleState;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgUsb;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.List;
import java.util.concurrent.Executor;


public class TestActivity extends AppCompatActivity implements
        ActivityRequirements.RequirementResolution,
        MyBackgroundExecutor.JobListener {

    private static final String TAG = TestActivity.class.getSimpleName();

    SensorsViewModel mVM_Sensors;

    MyBaseManager mUsb;

    private MyBackgroundExecutor mBackgroundExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);

        // instantiate sensors view model
        mVM_Sensors = new ViewModelProvider(this).get(SensorsViewModel.class);

        mUsb = SensorsViewModel.getOrCreateManager(
                this, mVM_Sensors, MyUSBManager.class.getSimpleName());
        mUsb.execute(this, LifeCycleState.ACT_CREATED);

        mBackgroundExecutor = new MyBackgroundExecutor();
        mBackgroundExecutor.initWorkerThread(TAG);

        if (savedInstanceState == null) {

            getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                    .add(R.id.fragment_container_view, TestFragment.class, null, "root-container")
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {

        mUsb.execute(this, LifeCycleState.ACT_DESTROYED);
        mBackgroundExecutor.cleanWorkerThread();
        super.onDestroy();
    }

    @Override
    public void requestResolution(String[] perms) {

    }

    @Override
    public void requestResolution(int requestCode, Intent activityIntent) {

    }

    @Override
    public void requestResolution(int requestCode, IntentSenderRequest resolutionIntent) {

    }

    @Override
    public void requestResolution(Fragment targetFragment) {

        MainActivity.startNewFragment(getSupportFragmentManager(),
                R.id.fragment_container_view, targetFragment, "test");
    }

    /* ------------------------------------ Multi-threading ------------------------------------- */

    @Override
    public Executor getBackgroundExecutor() {

        if (mBackgroundExecutor != null) {
            return mBackgroundExecutor.getBackgroundExecutor();
        }
        return null;
    }

    @Override
    public Handler getBackgroundHandler() {

        if (mBackgroundExecutor != null) {
            return mBackgroundExecutor.getBackgroundHandler();
        }
        return null;
    }

    @Override
    public Handler getUiHandler() {

        if (mBackgroundExecutor != null) {
            return mBackgroundExecutor.getUiHandler();
        }
        return null;
    }

    @Override
    public void execute(Runnable r) {

        if (mBackgroundExecutor != null) {
            mBackgroundExecutor.execute(r);
        }
    }

    @Override
    public void handle(Runnable r) {

        if (mBackgroundExecutor != null) {
            mBackgroundExecutor.handle(r);
        }
    }


    public static class TestFragment extends Fragment  implements View.OnClickListener,
            MyChannels.ChannelTransactions, ActivityRequirements.OnRequirementResolved {

        private static final String TAG = TestFragment.class.getSimpleName();

        private TextView rptTxt;

        SensorsViewModel mVM_Sensors;

        private MyUSBManager mUsb;
        private int usbGrpId;
        private int usbSensorId;

        public TestFragment() {
            // Required empty public constructor
        }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment FrontPanelFragment.
         */
        public static TestFragment newInstance() {

            TestFragment fragment = new TestFragment();
            Bundle args = new Bundle();
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            FragmentActivity context = requireActivity();

            // instantiate sensors view model
            mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

            // For a multi-fragment activity, use the SensorsViewModel
            mUsb = (MyUSBManager) SensorsViewModel.getOrCreateManager(
                    context, mVM_Sensors, MyUSBManager.class.getSimpleName());
            mUsb.setRequirementResponseListener(this);
            mUsb.registerChannel(this);

            List<MySensorGroup> lUsbGroups = mUsb.getSensorGroups(context);
            if (lUsbGroups != null) {
                for (MySensorGroup sensorGroup : lUsbGroups) {
                    if (sensorGroup != null) {
                        usbGrpId = sensorGroup.getId();
                        for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {
                            if (sensorInfo != null) {
                                usbSensorId = sensorInfo.getId();
                            }
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            mUsb.execute(requireActivity(), LifeCycleState.RESUMED);
        }

        @Override
        public void onPause() {

            mUsb.execute(requireActivity(), LifeCycleState.PAUSED);
            super.onPause();
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {

            return inflater.inflate(R.layout.activity_test, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            view.findViewById(R.id.enumDevs).setOnClickListener(this);
            view.findViewById(R.id.openDefaultDev).setOnClickListener(this);
            view.findViewById(R.id.sendState).setOnClickListener(this);
            view.findViewById(R.id.receiveSensor).setOnClickListener(this);
            view.findViewById(R.id.runTest).setOnClickListener(this);
            view.findViewById(R.id.serialTrans).setOnClickListener(this);
            view.findViewById(R.id.testUsbLatency).setOnClickListener(this);
            view.findViewById(R.id.testUsbThroughput).setOnClickListener(this);

            rptTxt = view.findViewById(R.id.fpReportText);
        }

        @Override
        public void onClick(View view) {

            if (mUsb == null) {
                return;
            }

            int id = view.getId();
            if (id == R.id.enumDevs) {

                String devices = MyUSBManager.usbDeviceListToString(mUsb.enumerateDevices());
                rptTxt.setText(devices);
            }
            else if (id == R.id.openDefaultDev) {

                FragmentActivity context = requireActivity();

                mUsb.onCheckedChanged(context, usbGrpId, usbSensorId, true);

                if (mUsb.isAvailable()) {
                    mUsb.tryOpenDeviceAndUpdateInfo();

                    String connReport = mUsb.getDeviceReport() + "\n\n" + mUsb.getConnectionReport();
                    rptTxt.setText(connReport);
                }
            }
            else if (id == R.id.sendState) {

                MsgUsb usbMsg = MyDrvUsb.getCommandMessage(MsgUsb.UsbCommand.CMD_UPDATE_OUTPUT, "w");
                // get the result in the callback
                mUsb.publishMessage(usbMsg);
            }
            else if (id == R.id.receiveSensor) {

                // send a sensor receive message
                // get the results in the callback
                mUsb.initiateAdcSingleRead();
            }
            else if (id == R.id.runTest) {

                mUsb.handleTest(null);
            }
            else if (id == R.id.serialTrans) {

                Fragment targetFragment = new SerialTransFragment();
                MainActivity.startNewFragment(requireActivity().getSupportFragmentManager(),
                        R.id.fragment_container_view, targetFragment, "serial-trans");
            }
            else if (id == R.id.testUsbLatency) {

                mUsb.runLatencyTest();
            }
            else if (id == R.id.testUsbThroughput) {

                mUsb.runThroughputTest();
            }
        }

        public void onUsbConnection(boolean connStat) {

            Log.i(TAG, "onUsbConnection called from test activity, state: "+connStat);
        }

        @Override
        public void registerChannel(MyChannels.ChannelTransactions channel) {

        }

        @Override
        public void unregisterChannel(MyChannels.ChannelTransactions channel) {

        }

        @Override
        public void publishMessage(MyMessages.MyMessage msg) {

        }

        @Override
        public void onMessageReceived(MyMessages.MyMessage msg) {

            if (msg instanceof MsgUsb) {

                MsgUsb usbMsg = (MsgUsb) msg;

                String res = usbMsg.toString();
                if (usbMsg.getAdcData() != null) {
                    res = usbMsg.getAdcSensorString();
                }

                // report message
                rptTxt.setText(res);
            }
        }

        @Override
        public void onAvailabilityStateChanged(MyBaseManager manager) {

            if (manager instanceof MyUSBManager) {
                onUsbConnection(manager.isAvailable());
            }
        }
    }
}
