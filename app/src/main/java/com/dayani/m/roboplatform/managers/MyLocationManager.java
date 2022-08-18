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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorInfo;
import com.dayani.m.roboplatform.managers.MyStorageManager.StorageInfo;

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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
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

    public static final class Constants {

        private Constants() {}

        // Keys for storing activity state in the Bundle.
        final static String KEY_LOCATION_PERMISSION_GRANTED = "location-permission-granted";
        final static String KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates";
        final static String KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string";

        static final String KEY_LOCATION_PERMISSION = PACKAGE_NAME+
                ".MyLocationManager_LOCATION.KEY_LOCATION_PERMISSION";

        static final String KEY_LOCATION_SETTINGS = PACKAGE_NAME+
                ".MyLocationManager_LOCATION.KEY_LOCATION_SETTINGS";

        public static final String ACTION_LOCATION_SETTINGS_AVAILABILITY = PACKAGE_NAME+
                ".MyLocationManager_LOCATION.ACTION_LOCATION_SETTINGS_AVAILABILITY";

        /**
         * Constant used in the location settings dialog.
         */
        static final int REQUEST_CHECK_SETTINGS = 0x83;

        /**
         * Code used in requesting runtime permissions.
         */
        static final int REQUEST_LOCATION_PERMISSION_CODE = 3402;

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
    }

    private static LocalBroadcastManager mLocalBrManager;
    //private final LocationManager mLocationManager;

    private Location mLastLocation = null;
    private Location mCurrentLocation = null;

    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;

    private LocationRequest mLocationRequest;

    private final LocationCallback mLocationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);

            mCurrentLocation = locationResult.getLastLocation();
            String sVal = null;
            if (mCurrentLocation != null) {
                sVal = getSensorString(mCurrentLocation);
            }
            //just store the raw data without any change -> Need synchronization??
            //mLocString.append(sVal);
            Log.v(TAG, sVal);
            //mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }
    };

    private LocationSettingsRequest mLocationSettingsRequest;

    //private static int currSensorId = 0;
    private static final int GPS_ID = 0;
    private static final int GNSS_ID = 1;

    private boolean mIsLocationEnabled = false;


    /* ==================================== Construction ======================================== */

    public MyLocationManager(Context context) {

        super(context);

        mLocalBrManager = LocalBroadcastManager.getInstance(context);
        //mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        init(context);
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

    @Override
    protected boolean hasAllRequirements(Context context) {

        List<Requirement> requirements = getRequirements();

        // permissions
        boolean resPerms = true;
        if (requirements.contains(Requirement.PERMISSIONS)) {
            resPerms = hasAllPermissions(context);
        }

        // location settings is enabled
        boolean resEnabled = true;
        if (requirements.contains(Requirement.ENABLE_LOCATION)) {
            // this is an asynch request, so we can't retrieve its result immediately
            changeLocationSettings(context, LocationSettingAction.CHECK_ENABLED, null);
            // get the last state
            resEnabled = getLastLocationSettingEnabledState();
        }

        return resPerms && resEnabled;
    }

    @Override
    protected void resolveRequirements(Context context) {

        List<Requirement> requirements = getRequirements();

        // permissions
        if (requirements.contains(Requirement.PERMISSIONS)) {
            if (!hasAllPermissions(context)) {
                resolvePermissions();
                return;
            }
        }

        // location setting enabled
        if (requirements.contains(Requirement.ENABLE_LOCATION)) {
            if (!getLastLocationSettingEnabledState()) {
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

        if (//resultData.getAction().equals(Constants.REQUEST_CHECK_SETTINGS) ||
                resultData.getStringExtra(KEY_INTENT_ACTIVITY_LAUNCHER).equals(TAG)) {
            if (result.getResultCode() == Activity.RESULT_OK) {

                Log.i(TAG, "User agreed to make required location settings changes.");
                mIsLocationEnabled = true;

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

    /* ------------------------------------ Availability ---------------------------------------- */

    /*--------------------------------- Lifecycle Management -------------------------------------*/

    @Override
    protected void init(Context context) {

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        mSettingsClient = LocationServices.getSettingsClient(context);

        // the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationRequest();
        buildLocationSettingsRequest();

        updateCheckedSensors(context);
    }

    @Override
    public void start(Context context) {

        super.start(context);
        openStorageChannels();
        startBackgroundThread(TAG);
        changeLocationSettings(context, LocationSettingAction.REQUEST_UPDATES, null);
    }

    @Override
    public void stop(Context context) {

        stopLocationUpdates(context);
        stopBackgroundThread();
        closeStorageChannels();
        super.stop(context);
    }

    @Override
    public void clean() {

    }

    @Override
    public void updateState(Context context) {

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
    protected void openStorageChannels() {

        if (mStorageListener == null || mmStorageChannels == null || mlSensorGroup == null) {
            Log.w(TAG, "Either sensors are not available or no storage listener found");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo != null) {

                    int sensorId = sensorInfo.getId();
                    StorageInfo storageInfo = mmStorageChannels.get(sensorId);

                    if (storageInfo != null) {

                        int chId = mStorageListener.getStorageChannel(
                                storageInfo.getFolders(), storageInfo.getFileName(), false);
                        storageInfo.setChannelId(chId);
                        writeFileHeader(sensorId, chId);
                    }
                }
            }
        }
    }

    /* ======================================= Location ========================================= */

    private static boolean hasGpsSensor(Context mContext) {

        PackageManager packageManager = mContext.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    private static boolean hasGnssRawSensor(Context context) {

        // TODO: Implement
        return false;
    }

    private boolean getLastLocationSettingEnabledState() {

        return mIsLocationEnabled;
    }

    private void createLocationRequest() {
        mLocationRequest = LocationRequest.create();
        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL_IN_MILLISECONDS);
        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(Constants.FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
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
        }

        return sensorInfo;
    }

    @Override
    public ArrayList<MySensorGroup> getSensorGroups(Context mContext) {

        ArrayList<MySensorGroup> sensorGroups = new ArrayList<>();
        ArrayList<MySensorInfo> sensors = new ArrayList<>();

        // add sensors:
        MySensorInfo gps = getGpsSensor(mContext);
        if (gps != null) {
            sensors.add(gps);
        }

        MySensorInfo gnssRaw = getGnssRawSensor(mContext);
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
                            Log.i(TAG, "All location settings are satisfied.");

                            if (action.equals(LocationSettingAction.CHECK_ENABLED)) {

                                mIsLocationEnabled = true;
                            }
                            else if (action.equals(LocationSettingAction.REQUEST_UPDATES)) {

                                try {
                                    //noinspection MissingPermission
                                    //getLastLocation();
                                    if (locLooper == null) {
                                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                                mLocationCallback, Looper.getMainLooper());
                                    }
                                    else {
                                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                                mLocationCallback, locLooper);
                                    }
                                    //setRequestingLocationUpdatesFlag(true);
                                }
                                catch (SecurityException e) {
                                    e.printStackTrace();
                                }
                            }
                            else if (action.equals(LocationSettingAction.BROADCAST_ENABLED)) {
                                mLocalBrManager.
                                        sendBroadcast(new Intent(Constants.ACTION_LOCATION_SETTINGS_AVAILABILITY));
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
                                    toastMessageLong(context, errorMessage);
                                    break;
                                }
                            }
                        })
                .addOnCompleteListener((AppCompatActivity) context,
                        task -> {
                            //boolean available = checkPermissions(context);
                            // && getSettingsOkFlag();
                            //setAvailableFlag(available);
                        }
                );
    }

    private void requestChangeSettings(Exception e) {

        // Show the dialog by calling startResolutionForResult(), and check the
            // result in onActivityResult().
//            ResolvableApiException rae = (ResolvableApiException) e;
//            rae.startResolutionForResult((AppCompatActivity) context,
//                    Constants.REQUEST_CHECK_SETTINGS);

        IntentSenderRequest intentSenderRequest = new IntentSenderRequest
                .Builder(((ResolvableApiException) e).getResolution()).build();

        Intent intent = intentSenderRequest.getFillInIntent();
        if (intent != null) {
            intent.putExtra(KEY_INTENT_ACTIVITY_LAUNCHER, TAG);
        }
        //intentSenderRequest.getFillInIntent().setAction(Constants.REQUEST_CHECK_SETTINGS);

        mRequirementRequestListener.requestResolution(intentSenderRequest);
    }

    /* ------------------------------------ Last Location --------------------------------------- */

    @SuppressWarnings("MissingPermission")
    public void getLastLocation(Context context) {

        if (!isAvailable(context))
            return;

        try {
            mFusedLocationClient.getLastLocation()
                .addOnSuccessListener((AppCompatActivity) context,
                        location -> {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                // Logic to handle location object
                                mLastLocation = location;
                            } else {
                                Log.i(TAG, "getLastLocation: null location.");
                                toastMessageLong(context, "Location is null");
                            }
                        })
                .addOnFailureListener((AppCompatActivity) context,
                        e -> toastMessageLong(context,"Failure reading location"))
                .addOnCompleteListener((AppCompatActivity) context,
                        task -> {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLastLocation = task.getResult();
                            }
                            else {
                                Log.w(TAG, "getLastLocation:exception", task.getException());
                                //showSnackbar(getString(R.string.no_location_detected));
                            }
                        });
        }
        catch (SecurityException e) {
            toastMessageLong(context, e.getMessage());
        }
    }

    /* --------------------------------------- Updates ------------------------------------------ */

    /**
     * Removes location updates from the FusedLocationApi.
     */
    public void stopLocationUpdates(Context context) {

//        if (!getRequestingLocationUpdatesFlag()) {
//            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
//            return;
//        }
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener((AppCompatActivity) context,
                        task -> {
                            //Will this work in this context??????
                            Log.i(TAG, "stopLocationUpdates");
                            //setRequestingLocationUpdatesFlag(false);
                            //setButtonsEnabledState();
                        }
                );
    }

    /*========================================= Helpers ==========================================*/

    protected void toastMessageLong(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     *
     * @param loc
     * @return String("data-time-format: longitude, latitude")
     */
    public String getSensorString(Location loc) {
        return "Location, " +
                new SimpleDateFormat("HH:mm:ss.SSSSSS").format(new Date()) +
                ", " + loc.getLongitude() + ", " + loc.getLatitude() + '\n';
    }

    public String getLocationUpdate(double lastUpdateTime) {

        if (mCurrentLocation == null) {
            Log.w(TAG, "no location available.");
            return "No Location available!";
        }
        return "Last Updated at: " + lastUpdateTime + ",\n" +
                "Latitude: " + mCurrentLocation.getLatitude() + ",\n" +
                "Longitude: " + mCurrentLocation.getLongitude();
    }

    public String getLastLocString() {

        if (mLastLocation == null) {
            Log.w(TAG, "no last location available.");
            return "No last location available!";
        }
        return "Accuracy: " + mLastLocation.getAccuracy() + ",\n" +
                "Latitude: " + mLastLocation.getLatitude() + ",\n" +
                "Longitude: " + mLastLocation.getLongitude();
    }

    private void writeFileHeader(int sensorId, int channelId) {

        if (mStorageListener == null) {
            return;
        }

        if (sensorId == GPS_ID) {

            mStorageListener.publishMessage(channelId,
                    "# timestamp_ns, latitude_deg, longitude_deg, altitude_deg, velocity_mps");
        }
        else if (sensorId == GNSS_ID) {

            mStorageListener.publishMessage(channelId,
                    "# timestamp_ns, latitude_deg, longitude_deg, altitude_deg, velocity_mps, bearing, satellite");
        }
    }


}
