package com.dayani.m.roboplatform;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.utils.helpers.MyScreenOperations;
import com.dayani.m.roboplatform.utils.interfaces.LoggingChannel;
import com.dayani.m.roboplatform.utils.view_models.SensorsViewModel;

import java.util.List;


public class RecordingFragment extends Fragment implements View.OnClickListener, LoggingChannel {

    private static final String TAG = RecordingFragment.class.getSimpleName();

    //private MyStorageManager mStorageManager;
    private List<MyBaseManager> mlManagers;

    private Button mButtonVideo;
    private Chronometer mChronometer;
    //private AutoFitTextureView mTextureView;
    private TextView mReportTxt;

    public static final int FRAG_RECORDING_LOGGING_IDENTIFIER = 6453;

    boolean mbIsRecording = false;

    public RecordingFragment() {
        // Required empty public constructor
    }

    public static RecordingFragment newInstance() {
        RecordingFragment fragment = new RecordingFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FragmentActivity context = requireActivity();

        // Retrieve sensors info to know which one should be operational and ...
        SensorsViewModel mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        // setup managers
        mlManagers = mVM_Sensors.getAllManagers();

        // add recording fragment's logger
        for (MyBaseManager manager : mlManagers) {
            manager.addChannelTransaction(this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View mView = inflater.inflate(R.layout.fragment_recording, container, false);

        mButtonVideo = mView.findViewById(R.id.record);
        mButtonVideo.setOnClickListener(this);

        mChronometer = mView.findViewById(R.id.chronometer);

        mReportTxt = mView.findViewById(R.id.txtReport);

        //mTextureView = mView.findViewById(R.id.texture);

        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onResume() {

        super.onResume();

        MyScreenOperations.setFullScreen(requireActivity());
        MyScreenOperations.setLandscape(requireActivity());
    }

    @Override
    public void onPause() {

        MyScreenOperations.unsetLandscape(requireActivity());
        MyScreenOperations.unsetFullScreen(requireActivity());

        if (mbIsRecording) {
            stop();
        }

        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {

        int viewId = view.getId();
        if (viewId == R.id.record) {
            if (mbIsRecording) {
                stop();
            }
            else {
                start();
            }
        }
    }

    private void start() {

        mbIsRecording = true;
        updateRecordingUI(true);

        for (MyBaseManager manager : mlManagers) {
            manager.start(requireActivity());
        }

        MyScreenOperations.setScreenOn(requireActivity());

        Log.d(TAG, "Managers are started");
    }

    private void stop() {

        MyScreenOperations.unsetScreenOn(requireActivity());

        for (MyBaseManager manager : mlManagers) {
            manager.stop(requireActivity());
        }

        mbIsRecording = false;
        updateRecordingUI(false);

        Log.d(TAG, "Managers are stopped");
    }

    private void updateRecordingUI(boolean state) {

        if (state) {
            mButtonVideo.setText(R.string.btn_txt_stop);
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.setVisibility(View.VISIBLE);
            mChronometer.start();
        }
        else {
            mButtonVideo.setText(R.string.btn_txt_start);
            mChronometer.stop();
            mChronometer.setVisibility(View.INVISIBLE);
        }
    }

    /* ------------------------------------ Logging channel ------------------------------------- */

    @Override
    public int openNewChannel(Context context, Void nothing) {
        return -1;
    }

    @Override
    public void closeChannel(int id) {

    }

    @Override
    public void publishMessage(int id, MyMessage msg) {

        if (mReportTxt != null && msg instanceof MyLoggingMessage) {
            // only respond to logging messages
            mReportTxt.append(msg.toString());
        }
    }

    @Override
    public void resetChannel(int id) {

    }

    @Override
    public int getChannelIdentifier() {
        return FRAG_RECORDING_LOGGING_IDENTIFIER;
    }
}