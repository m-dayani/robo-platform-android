package com.dayani.m.roboplatform;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.utils.AutoFitTextureView;
import com.dayani.m.roboplatform.utils.MyScreenOperations;
import com.dayani.m.roboplatform.utils.SensorsViewModel;

import java.util.List;


public class RecordingFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = RecordingFragment.class.getSimpleName();

    private SensorsViewModel mVM_Sensors;

    private MyStorageManager mStorageManager;
    private MySensorManager mSensorManager;

    private Button mButtonVideo;
    private Chronometer mChronometer;
    private AutoFitTextureView mTextureView;
    private TextView mReportTxt;

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
        mVM_Sensors = new ViewModelProvider(context).get(SensorsViewModel.class);

        // setup managers
        mStorageManager = (MyStorageManager) RecordSensorsActivity.getOrCreateManager(
                context, mVM_Sensors, MyStorageManager.class.getSimpleName());
        mSensorManager = (MySensorManager) RecordSensorsActivity.getOrCreateManager(
                context, mVM_Sensors, MySensorManager.class.getSimpleName());

        // Update view model based on the latest managers' state
        //Log.d(TAG, mVM_Sensors.printState());
        mVM_Sensors.updateState(context);
        //Log.d(TAG, mVM_Sensors.printState());
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

        mTextureView = mView.findViewById(R.id.texture);

        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
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
    public void onStop() {
        super.onStop();
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

        updateRecordingUI(mbIsRecording);

        mSensorManager.start();
    }

    private void stop() {

        mbIsRecording = false;

        updateRecordingUI(mbIsRecording);

        mSensorManager.stop();
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
}