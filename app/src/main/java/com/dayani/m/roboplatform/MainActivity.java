/**
 * There is no separate manual controllers or sensor recording in the newest version
 * 1. Record Sensors records all sensors -> if USB is not available, it's not recorded
 * 2. Controller client ->
 *      Represent a joystick above camera preview from the beginning
 *      Show "No Preview Available" if camera is not available
 *      Register to sensors over a (wireless) connection and show them
 *      Get commands from the joystick and send them to the controller server
 *      No connection? -> disable functionalities
 *      The complexity can be chosen in the settings (which sensors to record, camera res, ...)
 * 3. Controller server ->
 *      Send sensors (including images) to connected devices (wireless)
 *      Receive external commands
 *      Communicate with a microcontroller (USB or Wireless)
 *      Can show a panel similar to sensor record app
 *      If a connection is not available, disable related functionalities
 *      No requirements entry
 * 4. Tests: USB -> experiment with USB
 * 5. Tests: Native -> to be extended to a native autonomous app
 */

package com.dayani.m.roboplatform;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.dayani.m.roboplatform.controllers.RoboControllerActivity;
import com.dayani.m.roboplatform.recording.RecordSensorsActivity;
import com.dayani.m.roboplatform.tests.JoystickActivity;
import com.dayani.m.roboplatform.tests.NativeTestActivity;
import com.dayani.m.roboplatform.tests.TestActivity;


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
        findViewById(R.id.startControllerClient).setOnClickListener(this);
        findViewById(R.id.startControllerServer).setOnClickListener(this);
        findViewById(R.id.nativeTest).setOnClickListener(this);
//        findViewById(R.id.joystickTest).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {

        // Init variables
        Intent intent = null;

        int id = view.getId();
        if (id == R.id.startRecordAll) {

            intent = new Intent(this, RecordSensorsActivity.class);
            String extraKey = RecordSensorsActivity.EXTRA_KEY_RECORD_EXTERNAL;
            intent.putExtra(extraKey, true);
            Log.d(TAG, "startRecordAllActivity");
        }
        else if (id == R.id.startControllerClient) {

            intent = new Intent(this, RoboControllerActivity.class);
            Log.d(TAG, "startControllerClient");
            intent.putExtra(RoboControllerActivity.EXTRA_KEY_CONTROLLER_TYPE,
                    RoboControllerActivity.ControllerType.CTRL_CLIENT);
        }
        else if (id == R.id.startControllerServer) {

            intent = new Intent(this, RoboControllerActivity.class);
            Log.d(TAG, "start controller server");
            intent.putExtra(RoboControllerActivity.EXTRA_KEY_CONTROLLER_TYPE,
                    RoboControllerActivity.ControllerType.CTRL_SERVER);
        }
        else if (id == R.id.startTest) {

            Log.d(TAG, "usbTest");
            intent = new Intent(this, TestActivity.class);
        }
        else if (id == R.id.nativeTest) {

            Log.d(TAG, "nativeTest");
            intent = new Intent(this, NativeTestActivity.class);
        }
//        else if (id == R.id.joystickTest) {
//
//            Log.d(TAG, "Joystick Test");
//            intent = new Intent(this, JoystickActivity.class);
//        }

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