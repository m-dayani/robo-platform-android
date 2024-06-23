package com.dayani.m.roboplatform;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;


/**
 * Entry point activity responsible for launching other tasks
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.startRecordAll).setOnClickListener(this);
        findViewById(R.id.startTest).setOnClickListener(this);
        findViewById(R.id.startCarManualCtrl).setOnClickListener(this);
        findViewById(R.id.startRecordSensors).setOnClickListener(this);
        findViewById(R.id.startController).setOnClickListener(this);
        findViewById(R.id.nativeTest).setOnClickListener(this);
        findViewById(R.id.startFlightManualCtrl).setOnClickListener(this);
        findViewById(R.id.cpTest).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        // Init variables
        Intent intent = null;

        int id = view.getId();
        if (id == R.id.startRecordAll || id == R.id.startRecordSensors) {

            intent = new Intent(this, RecordSensorsActivity.class);
            String extraKey = RecordSensorsActivity.EXTRA_KEY_RECORD_EXTERNAL;

            if (id == R.id.startRecordAll) {
                intent.putExtra(extraKey, true);
                Log.d(TAG, "startRecordAllActivity");
            }
            else {
                intent.putExtra(extraKey, false);
                Log.d(TAG, "startRecordSensors");
            }
        }
        else if (id == R.id.startCarManualCtrl) {

            Log.d(TAG, "startCarManualCtrl");
            intent = new Intent(this, CarManualControlActivity.class);
        }
        else if (id == R.id.startFlightManualCtrl) {

            Log.d(TAG, "startFlightManualCtrl");
            intent = new Intent(this, FlightControlActivity.class);
        }
        else if (id == R.id.startController) {

            Log.d(TAG, "start controller server");
            intent = new Intent(this, ControllerServerActivity.class);
        }
        else if (id == R.id.startTest) {

            Log.d(TAG, "usbTest");
            intent = new Intent(this, TestActivity.class);
        }
        else if (id == R.id.nativeTest) {

            Log.d(TAG, "nativeTest");
            intent = new Intent(this, NativeTestActivity.class);
        }
        else if (id == R.id.cpTest) {

            Log.d(TAG, "cpTest");
            intent = new Intent(this, ControllerServerActivity.class);
            intent.putExtra(ControllerServerActivity.EXTRA_KEY_WITH_WL, true);
        }

        // Launch the desired activity
        if (intent != null) {
            startActivity(intent);
        }
    }

    public static void startNewFragment(FragmentManager fragmentManager, int viewId, Fragment frag, String tag) {

        fragmentManager.beginTransaction()
                .replace(viewId, frag, null)
                .setReorderingAllowed(true)
                .addToBackStack(tag)
                .commit();
    }

}