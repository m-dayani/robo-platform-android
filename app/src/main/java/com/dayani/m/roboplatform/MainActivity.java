package com.dayani.m.roboplatform;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;


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
            // TODO: Also change to a fragment
            Toast.makeText(this, "Car manual control is not implemented",
                    Toast.LENGTH_SHORT).show();
        }
        else if (id == R.id.startTest) {

            Log.d(TAG, "startTest");
            // TODO: Also change to a fragment
            //targetActivity = TestActivity.class;
            Toast.makeText(this, "USB Device test is not implemented",
                    Toast.LENGTH_SHORT).show();
        }

        // Launch the desired activity
        if (intent != null) {
            startActivity(intent);
        }
    }
}