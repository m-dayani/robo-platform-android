package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.drivers.MyDrvUsb;
import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link SerialTransFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SerialTransFragment extends Fragment implements View.OnClickListener,
        MyChannels.ChannelTransactions, ActivityRequirements.OnRequirementResolved {

    private static final String TAG = SerialTransFragment.class.getSimpleName();

    private EditText mInputMsg;
    private TextView rptTxt;

    private MyUSBManager mUsb;

    public SerialTransFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment SerialTransFragment.
     */
    public static SerialTransFragment newInstance() {
        SerialTransFragment fragment = new SerialTransFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        // instantiate sensors view model
        SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        // For a multi-fragment activity, use the SensorsViewModel
        mUsb = (MyUSBManager) SensorsViewModel.getOrCreateManager(
                context, mVM_Sensors, MyUSBManager.class.getSimpleName());
        mUsb.setRequirementResponseListener(this);
        mUsb.registerChannel(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_serial_trans, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mInputMsg = view.findViewById(R.id.serialMsg);

        view.findViewById(R.id.sendControlMsg).setOnClickListener(this);
        view.findViewById(R.id.sendAsync).setOnClickListener(this);
        view.findViewById(R.id.sendSync).setOnClickListener(this);

        rptTxt = view.findViewById(R.id.reportTxt);
    }

    @Override
    public void onResume() {
        super.onResume();
        mUsb.execute(requireActivity(), MyBaseManager.LifeCycleState.RESUMED);
    }

    @Override
    public void onPause() {

        mUsb.execute(requireActivity(), MyBaseManager.LifeCycleState.PAUSED);
        super.onPause();
    }

    @Override
    public void onClick(View view) {

        if (mUsb == null) {
            return;
        }

        String msg = mInputMsg.getText().toString();

        int id = view.getId();
        if (id == R.id.sendControlMsg) {
            Log.d(TAG, "Send Control Message");
            int res = mUsb.sendControlMsg(MyDrvUsb.getCommandMessage(msg));
            Log.d(TAG, "Send status: " + res);
        }
        else if (id == R.id.sendAsync) {
            Log.d(TAG, "Send Message Asynchronously");
            String res = mUsb.sendMsgAsync(msg);
            Log.d(TAG, "USB manager: " + res);
        }
        else if (id == R.id.sendSync) {
            Log.d(TAG, "Send Message Synchronously");
            String res = mUsb.sendMsgSync(msg);
            Log.d(TAG, "USB manager: " + res);
        }
    }

    @Override
    public void onAvailabilityStateChanged(MyBaseManager manager) {

        Log.i(TAG, "onUsbConnection called from test activity, state: " + manager.isAvailable());
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

        if (msg instanceof MyMessages.MsgUsb) {

            MyMessages.MsgUsb usbMsg = (MyMessages.MsgUsb) msg;

            String res = usbMsg.toString();
            if (usbMsg.getAdcData() != null) {
                res = usbMsg.getAdcSensorString();
            }

            // report message
            rptTxt.setText(res);
        }
    }
}