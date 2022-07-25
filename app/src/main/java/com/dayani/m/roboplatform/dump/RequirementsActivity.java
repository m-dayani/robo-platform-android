package com.dayani.m.roboplatform.dump;

/*
  Front Panel activity: this serves as the global
  management entry for the entire app.

  Note1: All permissions won't come up when asked
       individually. That's why all are bundled together
       and asked one time.

  TODO: find a way to register usb permission
       statically so we don't have to create the object
       here.

  TODO: Provide Settings (specially for permissions):
       1. Compatibility with system's Application Manager
       2. Unique keys (for permissions) across app components
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.CarManualControlActivity;
import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.RecordSensorsActivity;
import com.dayani.m.roboplatform.SettingsActivity;
import com.dayani.m.roboplatform.TestActivity;
import com.dayani.m.roboplatform.managers.CameraFlyVideo;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MyPermissionManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
import com.dayani.m.roboplatform.managers.MyUSBManager;
import com.dayani.m.roboplatform.utils.ActivityRequirements;
import com.google.android.gms.common.util.ArrayUtils;
import java.util.Arrays;


public class RequirementsActivity extends AppCompatActivity
        implements View.OnClickListener,
        RequirementListFragment.OnListFragmentInteractionListener {

    private static final String TAG = RequirementsActivity.class.getSimpleName();

    String[] activityRequirements;
    String[] activityPermissions;

    FragmentManager fragmentManager;
    FragmentTransaction fragmentTransaction;


    private String[] allPermissions;


    private MyUSBManager mUsb;

    private MyLocationManager mLocation;

    private LocalBroadcastManager mBrManager;
    private UsbAvailabilityReceiver mUsbAvailabilityReceiver;
    private IntentFilter mUsbAvailabilityIntent;
    private LocationAvailabilityReceiver mLocationAvailabilityReceiver;
    private IntentFilter mLocationAvailabilityIntent;

    boolean mUsbDeviceAvailable = false;
    boolean mLocationAvailable = false;

    /*private SharedPreferences.OnSharedPreferenceChangeListener listener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (key.equals("signature")) {
                        Log.i(TAG, "Preference value was updated to: " +
                                sharedPreferences.getString(key, ""));
                    }
                }
            };*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.z_activity_requirements);

        fragmentManager = getSupportFragmentManager();
        fragmentTransaction = fragmentManager.beginTransaction();

        Intent targetIntent = getIntent();
        activityRequirements =
                targetIntent.getStringArrayExtra(AppGlobals.KEY_TARGET_REQUIREMENTS);
        activityPermissions =
                targetIntent.getStringArrayExtra(AppGlobals.KEY_TARGET_PERMISSIONS);

        if (activityRequirements != null) {
            Log.d(TAG, "Activity Requirements: " + Arrays.toString(activityRequirements));
        }
        if (activityPermissions != null) {
            Log.d(TAG, "Activity Permissions: " + Arrays.toString(activityPermissions));
        }

//        ExampleFragment fragment = new ExampleFragment();
//        fragmentTransaction.add(R.id.fragment_container, fragment);
//        fragmentTransaction.commit();


        //reportTxt = (TextView) findViewById(R.id.reportText);

        //Manage permissions
//        createPermissions();
//        MyPermissionManager.checkAllPermissions(this,
//                allPermissions, AppGlobals.REQUEST_ALL_PERMISSIONS_CODE, AppGlobals.KEY_ALL_PERMISSIONS);
//
//        //Set a local broadcast receiver for USB permission
//        mBrManager = LocalBroadcastManager.getInstance(this);
//
//        mUsbAvailabilityIntent = new IntentFilter(MyUSBManager.ACTION_USB_AVAILABILITY);
//        mUsbAvailabilityReceiver = new UsbAvailabilityReceiver();
//        mBrManager.registerReceiver(mUsbAvailabilityReceiver,mUsbAvailabilityIntent);
//
//        mLocationAvailabilityIntent = new IntentFilter(
//                MyLocationManager.Constants.ACTION_LOCATION_SETTINGS_AVAILABILITY);
//        mLocationAvailabilityReceiver = new LocationAvailabilityReceiver();
//        mBrManager.registerReceiver(mLocationAvailabilityReceiver,mLocationAvailabilityIntent);
//
//
//        //detect USB device and ask required permissions
//        mUsb = new MyUSBManager(this, new StringBuffer());
//        mUsb.updateDefaultDeviceAvailability();
//
//        mLocation = new MyLocationManager(this, new StringBuffer());
//        mLocation.updateLocationSettings();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        //updateStates();
        //PreferenceManager.getDefaultSharedPreferences(this).
        //registerOnSharedPreferenceChangeListener(this);
        //mUsb.registerUsbPermission();
    }

    @Override
    public void onPause() {
        super.onPause();
        //PreferenceManager.getDefaultSharedPreferences(this).
        //unregisterOnSharedPreferenceChangeListener(this);
        //mUsb.unregisterUsbPermission();
    }

    /*@Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }*/

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {

//        mBrManager.unregisterReceiver(mUsbAvailabilityReceiver);
//        mBrManager.unregisterReceiver(mLocationAvailabilityReceiver);
//        mUsb.clean();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        int itemId = item.getItemId();
        if (itemId == R.id.settings) {
            changeSettings();
            return true;
        }
        else if (itemId == R.id.mainRefresh) {//updateStates();
            return true;
        }
        else if (itemId == R.id.help) {
            toastMsgShort("Duhh...! This is a flight simulator app!");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*@Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals("display_text")) {
            //setTextVisible(sharedPreferences.getBoolean("display_text",true));
        }
    }*/

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult");

        super.onActivityResult(requestCode,resultCode,data);

        //mLocation.onActivityResult(requestCode, resultCode, data);

        //updateStates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

//        MyPermissionManager.onRequestAllPermissionsResult(this,
//                AppGlobals.KEY_ALL_PERMISSIONS, AppGlobals.REQUEST_ALL_PERMISSIONS_CODE,
//                requestCode, permissions, grantResults);

        //updateStates();
    }

    @Override
    public void onListFragmentInteraction(ActivityRequirements.RequirementItem item) {

    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.startRecordAll: {
                startRecordAllTest();
                break;
            }
            case R.id.startCarManualCtrl: {
                startCarManualControl();
                break;
            }
            case R.id.startTest: {
                startTestActivity();
                break;
            }
        }
    }

    /*--------------------------------------------------------------------------------------------*/

    private void createPermissions() {
        allPermissions = ArrayUtils.concat(MyStorageManager.getPermissions(),
                CameraFlyVideo.getPermissions(), MyLocationManager.getPermissions(getApplicationContext()));
        Log.i(TAG, Arrays.toString(allPermissions));
    }

    /**
     * Either use the total key to see if permissions granted
     * or we can use the MyPermissionManager.hasAllPermissions check.
     * Difference is that with the second one we get a free update of stats!
     */
    private boolean allPermissionsGranted() {
        return MyPermissionManager.hasAllPermissions(this,
                allPermissions,AppGlobals.KEY_ALL_PERMISSIONS);
    }

    private boolean isLocationSettingsGood() {
        return mLocation.checkAvailability();
    }

    /*private void testUsbAvailability() {
        mUsb.checkDefaultDeviceAvailability();
    }*/

    private boolean allGood() {
        return allPermissionsGranted() &&
                isLocationSettingsGood() &&
                mUsbDeviceAvailable;
    }

    /*private void updateStates() {

        if (allGood()) {
            mBtnStartRecordAll.setEnabled(true);
            //mBtnStartRealSim.setEnabled(true);
            reportTxt.setText("Access Granted,\nUSB device available,\ngood to go.");
        } else {
            mBtnStartRecordAll.setEnabled(false);
            //mBtnStartRealSim.setEnabled(false);
            reportTxt.setText("1. Grant all permissions (4)."+
                    "\n2. Connect the target USB device and permit."+
                    "\n3. Change location setting if it's off.");
        }
    }*/

    /*--------------------------------------------------------------------------------------------*/

    private void startRecordAllTest() {
        Intent intent = new Intent(this, RecordSensorsActivity.class);
        //intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        //finish();
        startActivity(intent);
    }

    private void startCarManualControl() {
        Intent intent = new Intent(this, CarManualControlActivity.class);
        //intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        //finish();
        startActivity(intent);
    }

    private void startTestActivity() {
        Intent intent = new Intent(this, TestActivity.class);
        //intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        //finish();
        startActivity(intent);
    }

    public void changeSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        //finish();
        startActivity(intent);
    }

    /*--------------------------------------------------------------------------------------------*/

    public void toastMsgShort(String msg) {
        Toast.makeText(this.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    /*--------------------------------------------------------------------------------------------*/

    public class UsbAvailabilityReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MyUSBManager.ACTION_USB_AVAILABILITY.equals(action)) {
                //closing the connection is now implicit
                //If we need the connection, try open explicitly.
                mUsbDeviceAvailable = true;
                //updateStates();
                Log.i(TAG, "UsbAvailabilityReceiver");
            }
        }
    }

    public static class LocationAvailabilityReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (MyLocationManager.Constants.ACTION_LOCATION_SETTINGS_AVAILABILITY.equals(action)) {
                //closing the connection is now implicit
                //If we need the connection, try open explicitly.
                //updateStates();
                Log.i(TAG, "UsbAvailabilityReceiver");
            }
        }
    }
}
