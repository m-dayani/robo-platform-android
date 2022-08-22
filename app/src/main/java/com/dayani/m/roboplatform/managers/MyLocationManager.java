package com.dayani.m.roboplatform.managers;

/*
 *      Google Play Service Location API
 *
 * Note1: To work with locations, we need to
 *      include location service dependency in the app
 *      and it might have collision problems with AppCompatActivity
 *      in android.support.v7 package.
 * Note2: call to runtime location check permission functions
 *      requires Min API level 23 or higher.
 * Note3: Consider using other request loc updates methods:
 *      service, intentService, broadcast receiver. Is there
 *      any performance difference?
 *
 * TODO: We can receive and process sensor values from
 *      another thread.
 * (We don't do this here because it's a very light job).
 *
 * Note4: Deadly behavior: When trying to get update location
 *      in conjunction with recording all other things, we have to
 *      avoid async operation: like changing location settings.
 *
 * Note5: There must be only one entry for checking availability
 *      and that entry checks availability from the root.
 *
 * ** Availability:
 *      0. Device supports location? (e.g. emulators don't support GNSS)
 *      1. Location permissions
 *      2. Location settings is on (for updates)
 * ** Resources:
 *      1. Internal HandlerThread
 *      2. GPS
 * ** State Management:
 *      1. isAvailable (availability)
 *      2. permissionsGranted
 *      3. isSettingsOk
 *      4. Loc update state
 *
 */

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dayani.m.roboplatform.RecordingFragment;
import com.dayani.m.roboplatform.managers.MyStorageManager.StorageInfo;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.LoggingChannel;
import com.dayani.m.roboplatform.utils.interfaces.MessageChannel;
import com.dayani.m.roboplatform.utils.interfaces.MessageChannel.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.StorageChannel;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MyLocationManager extends MyBaseManager {

    /* ===================================== Variables ========================================== */

    private static final String PACKAGE_NAME = AppGlobals.PACKAGE_BASE_NAME;

    private static final String TAG = MyLocationManager.class.getSimpleName();

    private enum LocationSettingAction {
        CHECK_ENABLED,
        REQUEST_ENABLE,
        REQUEST_UPDATES,
        BROADCAST_ENABLED
    }

    public static final String ACTION_LOCATION_SETTINGS_AVAILABILITY = PACKAGE_NAME+
            ".MyLocationManager_LOCATION.ACTION_LOCATION_SETTINGS_AVAILABILITY";

    /**
     * Constant used in the location settings dialog.
     */
    static final int REQUEST_CHECK_SETTINGS = 0x83;

    /*
     * Code used in requesting runtime permissions.
     */
    //static final int REQUEST_LOCATION_PERMISSION_CODE = 3402;

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    static final long UPDATE_INTERVAL_IN_MILLISECONDS = 4000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;


    private static LocalBroadcastManager mLocalBrManager;
    private final BroadcastReceiver mLocationProviderChangedBR;

    private Location mLastLocation = null;

    private final SettingsClient mSettingsClient;

    private LocationRequest mLocationRequest;

    private LocationSettingsRequest mLocationSettingsRequest;

    private final LocationManager mLocationManager;

    private final FusedLocationProviderClient mFusedLocationClient;

    private final LocationCallback mLocationCallback = initLocationCallback();


    private static final int GPS_ID = 0;
    private static final int GNSS_ID = 1;

    private boolean mIsLocationEnabled = false;


    /* ==================================== Construction ======================================== */

    public MyLocationManager(Context context) {

        super(context);

        mSettingsClient = LocationServices.getSettingsClient(context);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        mLocalBrManager = LocalBroadcastManager.getInstance(context);
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        mLocationProviderChangedBR = new ManagerRequirementBroadcastReceiver(this);

        //init(context);
        createLocationRequest();
        buildLocationSettingsRequest();
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    /**
     * Device has to support at least GPS sensor (Raw GNSS is not mandatory)
     * Although devices location can coarsely be estimated using WiFi or GSM,
     * GPS must be available.
     * @param context activity's context
     * @return boolean, false: has no GPS
     */
    @Override
    protected boolean resolveSupport(Context context) {

        return hasGpsSensor(context);
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    @Override
    public List<Requirement> getRequirements() {
        return Arrays.asList(
                Requirement.PERMISSIONS,
                Requirement.ENABLE_LOCATION
        );
    }

    public boolean passedAllRequirements() {
        return hasAllPermissions() && isLocationEnabled();
    }

    @Override
    protected void updateRequirementsState(Context context) {

        List<Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to update");
            return;
        }

        // permissions
        if (requirements.contains(Requirement.PERMISSIONS)) {
            updatePermissionsState(context);
        }

        // location settings is enabled
        if (requirements.contains(Requirement.ENABLE_LOCATION)) {
            // this is an async request, so we can't retrieve its result immediately
            changeLocationSettings(context, LocationSettingAction.CHECK_ENABLED, null);
        }
    }

    @Override
    protected void resolveRequirements(Context context) {

        List<Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to resolve");
            return;
        }

        // permissions
        if (requirements.contains(Requirement.PERMISSIONS)) {
            if (!hasAllPermissions()) {
                resolvePermissions();
                return;
            }
        }

        // location setting enabled
        if (requirements.contains(Requirement.ENABLE_LOCATION)) {
            if (!isLocationEnabled()) {
                changeLocationSettings(context, LocationSettingAction.REQUEST_ENABLE, null);
            }
        }
    }

    @Override
    public void onActivityResult(Context context, ActivityResult result) {

        Intent resultData = result.getData();

        if (resultData == null) {
            Log.w(TAG, "Result intent is null");
            return;
        }

        int activityResultTag = resultData.getIntExtra(KEY_INTENT_ACTIVITY_LAUNCHER, 0);

        if (activityResultTag == REQUEST_CHECK_SETTINGS) {
            if (result.getResultCode() == Activity.RESULT_OK) {

                Log.i(TAG, "User agreed to make required location settings changes.");
                updateLocationEnabledState(true);

                // TODO: broadcast location settings enabled??
                //mLocalBrManager.sendBroadcast(new Intent(Constants.ACTION_LOCATION_SETTINGS_AVAILABILITY));
            }
        }
    }

    @Override
    public List<String> getPermissions() {
        return Arrays.asList(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
                //Manifest.permission.ACCESS_BACKGROUND_LOCATION
        );
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {

        super.onBroadcastReceived(context, intent);

        if (intent.getAction().matches("android.location.PROVIDERS_CHANGED"))  {

            Log.i(TAG, "Location Providers changed");

            boolean isGpsEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            updateLocationEnabledState(isGpsEnabled || isNetworkEnabled);
        }
    }

    private boolean isLocationEnabled() { return mIsLocationEnabled; }

    private void updateLocationEnabledState(boolean state) {
        mIsLocationEnabled = state;
    }

    /*--------------------------------- Lifecycle Management -------------------------------------*/

    @Override
    public void init(Context context) {

        super.init(context);
        registerLocationChangeBrReceiver(context);
    }

    @Override
    public void clean(Context context) {

        unregisterLocationChangeBrReceiver(context);
        super.clean(context);
    }

    @Override
    public void start(Context context) {

        if (!this.isAvailableAndChecked()) {
            Log.w(TAG, "Location Sensors are not available, abort");
            return;
        }

        super.start(context);
        openStorageChannels(context);
        //startBackgroundThread(TAG);
        changeLocationSettings(context, LocationSettingAction.REQUEST_UPDATES, null);
    }

    @Override
    public void stop(Context context) {

        if (!this.isAvailableAndChecked() || !this.isProcessing()) {
            Log.d(TAG, "Location Sensors are not running");
            return;
        }

        stopLocationUpdates(context);
        //stopBackgroundThread();
        closeStorageChannels();
        super.stop(context);
    }

    private void registerLocationChangeBrReceiver(Context context) {

        if (mLocationProviderChangedBR != null) {
            IntentFilter locationFilter = new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION);
            context.getApplicationContext().registerReceiver(mLocationProviderChangedBR, locationFilter);
            Log.d(TAG, "Registered onProviderChangedBrReceiver");
        }
    }

    private void unregisterLocationChangeBrReceiver(Context context) {

        if (mLocationProviderChangedBR != null) {
            try {
                context.getApplicationContext().unregisterReceiver(mLocationProviderChangedBR);
                Log.d(TAG, "Unregistered onProviderChangedBrReceiver");
            }
            catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    protected Map<Integer, StorageInfo> initStorageChannels() {

        Map<Integer, StorageInfo> storageInfoMap = new HashMap<>();
        List<String> gnssFolders = Collections.singletonList("gnss");

        storageInfoMap.put(GPS_ID, new StorageInfo(gnssFolders, "gps.txt"));
        storageInfoMap.put(GNSS_ID, new StorageInfo(gnssFolders, "gnss_raw.txt"));

        return storageInfoMap;
    }

    @Override
    protected void openStorageChannels(Context context) {

        if (mlChannelTransactions == null || mmStorageChannels == null || mlSensorGroup == null) {
            Log.w(TAG, "Either sensors are not available or no storage listener found");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo != null) {

                    int sensorId = sensorInfo.getId();
                    StorageInfo storageInfo = mmStorageChannels.get(sensorId);

                    if (storageInfo != null) {

                        for (MessageChannel<?> channel : mlChannelTransactions) {

                            if (channel instanceof StorageChannel) {

                                int chId = ((StorageChannel) channel).openNewChannel(context, storageInfo);
                                storageInfo.setChannelId(chId);
                                writeFileHeader(sensorId);
                            }
                        }
                    }
                }
            }
        }
    }

    /* ======================================= Location ========================================= */

    private void createLocationRequest() {

        mLocationRequest = LocationRequest.create();
        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(Priority.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Uses a {@link com.google.android.gms.location.LocationSettingsRequest.Builder} to build
     * a {@link com.google.android.gms.location.LocationSettingsRequest} that is used for checking
     * if a device has the needed location settings.
     */
    private void buildLocationSettingsRequest() {

        if (mLocationRequest == null) {
            createLocationRequest();
        }
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private LocationCallback initLocationCallback() {

        return new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {

                //super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    publish(GPS_ID, new LocationMessage(location));
                }
            }
        };
    }


    private static boolean hasGpsSensor(Context context) {

        PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    private static boolean hasGnssRawSensor(Context context) {

        // TODO: Implement
        return false;
    }

    private MySensorInfo getGpsSensor(Context context) {

        MySensorInfo sensorInfo = null;

        if (hasGpsSensor(context)) {

            Map<String, String> descInfo = new HashMap<>();
            descInfo.put("Usage", "\n\t1. Grant location permissions.\n\t2. Enable location settings");

            Map<String, String> calibInfo = new HashMap<>();
            calibInfo.put("Resolution_m", "10");

            sensorInfo = new MySensorInfo(GPS_ID, "GPS");
            sensorInfo.setDescInfo(descInfo);
            sensorInfo.setCalibInfo(calibInfo);
            //sensorInfo.setChecked(false);
        }

        return sensorInfo;
    }

    private MySensorInfo getGnssRawSensor(Context context) {

        MySensorInfo sensorInfo = null;

        if (hasGnssRawSensor(context)) {

            Map<String, String> descInfo = new HashMap<>();
            descInfo.put("Usage", "\n\t1. Grant location permissions.\n\t2. Enable location settings");

            Map<String, String> calibInfo = new HashMap<>();
            calibInfo.put("Resolution_m", "10");

            sensorInfo = new MySensorInfo(GNSS_ID, "GNSS Raw Measurements");
            sensorInfo.setDescInfo(descInfo);
            sensorInfo.setCalibInfo(calibInfo);
            //sensorInfo.setChecked(false);
        }

        return sensorInfo;
    }

    @Override
    public List<MySensorGroup> getSensorGroups(Context context) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        List<MySensorGroup> sensorGroups = new ArrayList<>();
        List<MySensorInfo> sensors = new ArrayList<>();

        // add sensors:
        MySensorInfo gps = getGpsSensor(context);
        if (gps != null) {
            sensors.add(gps);
        }

        MySensorInfo gnssRaw = getGnssRawSensor(context);
        if (gnssRaw != null) {
            sensors.add(gnssRaw);
        }

        sensorGroups.add(new MySensorGroup(MySensorGroup.getNextGlobalId(),
                MySensorGroup.SensorType.TYPE_GNSS, "GNSS", sensors));

        return sensorGroups;
    }

    /* ------------------------------- Settings (for LocUpdates) -------------------------------- */

    private void changeLocationSettings(Context context, LocationSettingAction action, final Looper locLooper) {

        if (context == null || mSettingsClient == null) {

            Log.w(TAG, "Either context or settings client is null");
            return;
        }

        if (mLocationSettingsRequest == null) {
            buildLocationSettingsRequest();
        }

        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener((AppCompatActivity) context,
                        locationSettingsResponse -> {

                            // All location settings are satisfied. The client can initialize
                            // location requests here.
                            Log.d(TAG, "All location settings are satisfied.");

                            if (action.equals(LocationSettingAction.CHECK_ENABLED)) {

                                updateLocationEnabledState(true);
                            }
                            else if (action.equals(LocationSettingAction.REQUEST_UPDATES)) {

                                try {
                                    getLastLocation(context);

                                    if (locLooper == null) {
                                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                                mLocationCallback, Looper.getMainLooper());
                                    }
                                    else {
                                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                                mLocationCallback, locLooper);
                                    }
                                }
                                catch (SecurityException e) {
                                    e.printStackTrace();
                                }
                            }
                            else if (action.equals(LocationSettingAction.BROADCAST_ENABLED)) {
                                mLocalBrManager.
                                        sendBroadcast(new Intent(ACTION_LOCATION_SETTINGS_AVAILABILITY));
                            }
                        }
                )
                .addOnFailureListener((AppCompatActivity) context,
                        e -> {
                            int statusCode = ((ApiException) e).getStatusCode();
                            switch (statusCode) {

                                case LocationSettingsStatusCodes.RESOLUTION_REQUIRED: {

                                    // Location settings are not satisfied, but this can be fixed
                                    // by showing the user a dialog.
                                    Log.i(TAG, "Location settings are not satisfied");
                                    if (action.equals(LocationSettingAction.REQUEST_ENABLE)) {

                                        Log.i(TAG, "Attempting to send enable request");
                                        requestChangeSettings(e);
                                    }
                                    break;
                                }
                                case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE: {

                                    String errorMessage = "Location settings are inadequate and cannot be " +
                                            "fixed here. Fix in Settings.";
                                    Log.e(TAG, errorMessage);
                                    break;
                                }
                            }
                        })
                .addOnCompleteListener((AppCompatActivity) context,
                        task -> {
                            Log.v(TAG, "Change location settings task completed");
                        }
                );
    }

    private void requestChangeSettings(Exception e) {

        IntentSenderRequest intentSenderRequest = new IntentSenderRequest
                .Builder(((ResolvableApiException) e).getResolution()).build();
        mRequirementRequestListener.requestResolution(REQUEST_CHECK_SETTINGS, intentSenderRequest);
    }

    /* ------------------------------------ Last Location --------------------------------------- */

    @SuppressWarnings("MissingPermission")
    public void getLastLocation(Context context) {

        if (!isAvailableAndChecked())
            return;

        try {
            mFusedLocationClient.getLastLocation()
                .addOnSuccessListener((AppCompatActivity) context,
                        location -> {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                // Logic to handle location object
                                mLastLocation = location;
                                // Log last location
                                MyMessage msg = new LoggingChannel.MyLoggingMessage(
                                        "Last location: " + LocationMessage.toString(mLastLocation) + "\n",
                                        RecordingFragment.FRAG_RECORDING_LOGGING_IDENTIFIER);
                                Log.v(TAG, msg.toString());
                                publish(-1, msg);
                            }
                            else {
                                Log.d(TAG, "getLastLocation: null last location.");
                            }
                        })
                .addOnFailureListener((AppCompatActivity) context,
                        Throwable::printStackTrace)
                .addOnCompleteListener((AppCompatActivity) context,
                        task -> {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLastLocation = task.getResult();
                            }
                            else {
                                Log.w(TAG, "getLastLocation:exception", task.getException());
                            }
                        });
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    /* --------------------------------------- Updates ------------------------------------------ */

    /**
     * Removes location updates from the FusedLocationApi.
     */
    public void stopLocationUpdates(Context context) {

        if (mFusedLocationClient == null || mLocationCallback == null) {
            Log.d(TAG, "Location processing has not started");
            return;
        }

        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener((AppCompatActivity) context,
                        task -> Log.i(TAG, "stopLocationUpdates")
                );
    }

    /* ======================================== Helpers ========================================= */

    private void writeFileHeader(int sensorId) {

        if (mlChannelTransactions == null) {
            return;
        }

        MyMessage header = new MyMessage("# unknown sensor type\n");

        if (sensorId == GPS_ID) {

            header = new MyMessage("# timestamp_ns, latitude_deg, longitude_deg, altitude_deg, velocity_mps, bearing");
        }
        else if (sensorId == GNSS_ID) {

            header = new MyMessage("# timestamp_ns, latitude_deg, longitude_deg, altitude_deg, velocity_mps, bearing, satellite");
        }

        publish(sensorId, header);
    }

    /* ======================================= Data Types ======================================= */

    public static class ManagerRequirementBroadcastReceiver extends BroadcastReceiver {

        private final MyBaseManager mManager;

        public ManagerRequirementBroadcastReceiver(MyBaseManager manager) {

            mManager = manager;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            if (mManager != null) {
                mManager.onBroadcastReceived(context, intent);
            }
        }
    }

    private static class LocationMessage extends MyMessage {

        private Location mLocEvent;

        public LocationMessage(Location locEvent) {

            super(toString(locEvent));
            mLocEvent = locEvent;
        }

        /**
         * @param loc new location event
         * @return String("timestamp, longitude, latitude, altitude, velocity, bearing")
         */
        public static String toString(Location loc) {
            return loc.getTime() + ", " + loc.getLongitude() + ", " + loc.getLatitude() +
                    ", " + loc.getAltitude() + ", " + loc.getSpeed() + ", " + loc.getBearing() + '\n';
        }

        public Location getLocEvent() {
            return mLocEvent;
        }

        public void setLocEvent(Location locEvent) {
            this.mLocEvent = locEvent;
        }
    }
}
