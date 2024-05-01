package com.dayani.m.roboplatform;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.managers.MyWifiManager;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgWireless.WirelessCommand;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ControlPanelFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ControlPanelFragment extends Fragment implements View.OnClickListener,
        MyChannels.ChannelTransactions, View.OnTouchListener {


    private static final String KEY_MANAGER_NAME = AppGlobals.PACKAGE_BASE_NAME+".key-manager-name";

    private String mLastBtnDir = "0";

    private MyBaseManager mManager;

    EditText keyInput;
    EditText wordInput;
    TextView chatBox;


    public ControlPanelFragment() {
        // Required empty public constructor
    }

    public static ControlPanelFragment newInstance(String managerName) {
        ControlPanelFragment fragment = new ControlPanelFragment();
        Bundle args = new Bundle();
        args.putString(KEY_MANAGER_NAME, managerName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SensorsViewModel mVmSensors = new ViewModelProvider(requireActivity()).get(SensorsViewModel.class);

        if (getArguments() != null) {
            mManager = mVmSensors.getManager(getArguments().getString(KEY_MANAGER_NAME));

            if (mManager != null) {
                mManager.registerChannel(this);
            }
        }
    }

    @Override
    public void onDestroy() {

        if (mManager instanceof MyWifiManager) {
            ((MyWifiManager) mManager).close();
        }
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_control_panel, container, false);

        view.findViewById(R.id.btnCtrlQ).setOnTouchListener(this);
        view.findViewById(R.id.btnCtrlW).setOnTouchListener(this);
        view.findViewById(R.id.btnCtrlE).setOnTouchListener(this);
        view.findViewById(R.id.btnCtrlA).setOnTouchListener(this);
        view.findViewById(R.id.btnCtrlS).setOnTouchListener(this);
        view.findViewById(R.id.btnCtrlD).setOnTouchListener(this);

        view.findViewById(R.id.btnSendWC).setOnClickListener(this);

        keyInput = view.findViewById(R.id.keyCmdInput);
        keyInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

                WirelessCommand cmdType = WirelessCommand.CMD_CHAR;

                for (int k = 0; k < charSequence.length(); k++) {

                    String cmd = Character.toString(charSequence.charAt(k));
                    publishMessage(new MsgWireless(cmdType, cmd));
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

                keyInput.setText("");
            }
        });

        wordInput = view.findViewById(R.id.wordCmdInput);

        chatBox = view.findViewById(R.id.txtChatReport);

        return view;
    }

    @Override
    public void onClick(View view) {

        String cmd;
        WirelessCommand cmdType;

        if (view.getId() == R.id.btnSendWC) {
            cmd = wordInput.getText().toString();
            chatBox.append("Server << "+cmd);
            cmdType = WirelessCommand.CMD_WORD;
        }
        else {
            cmd = "unknown";
            cmdType = WirelessCommand.CHAT;
        }

        MsgWireless msg = new MsgWireless(cmdType, cmd);
        this.publishMessage(msg);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {

        String cmd = mLastBtnDir;
        WirelessCommand cmdType = WirelessCommand.CMD_DIR;

        if(event.getAction() == MotionEvent.ACTION_DOWN) {

            int id = view.getId();
            if (id == R.id.btnCtrlQ) {
                cmd = "Q";
            }
            else if (id == R.id.btnCtrlE) {
                cmd = "E";
            }
            else if (id == R.id.btnCtrlW) {
                cmd = "W";
            }
            else if (id == R.id.btnCtrlS) {
                cmd = "S";
            }
            else if (id == R.id.btnCtrlA) {
                cmd = "A";
            }
            else if (id == R.id.btnCtrlD) {
                cmd = "D";
            }
            mLastBtnDir = cmd;
        }
        else if (event.getAction() == MotionEvent.ACTION_UP) {
            cmd = "0";
            mLastBtnDir = cmd;
        }

        MsgWireless msg = new MsgWireless(cmdType, cmd);
        this.publishMessage(msg);

        return true;
    }

    @Override
    public void registerChannel(MyChannels.ChannelTransactions channel) {

    }

    @Override
    public void unregisterChannel(MyChannels.ChannelTransactions channel) {

    }

    @Override
    public void publishMessage(MyMessages.MyMessage msg) {

        mManager.onMessageReceived(msg);
    }

    @Override
    public void onMessageReceived(MyMessages.MyMessage msg) {

        if (msg instanceof MsgWireless) {
            chatBox.append("Client >> "+ msg +"\n");
        }
    }
}