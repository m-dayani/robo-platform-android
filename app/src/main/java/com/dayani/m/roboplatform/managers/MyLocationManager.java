package com.dayani.m.roboplatform.managers;

/**
 *      Google Play Service Location API
 *
 * Note1: To work with locations, we need to
 *      include location service dependency in the app
 *      and it might have collission problems with AppCompatActivity
 *      in android.support.v7 package.
 * Note2: call to runtime location check permission functions
 *      requires Min API level 23 or higher.
 * Note3: Consider using other request loc updates methods:
 *      service, intentService, broadcast reciever. Is there
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
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.text.SimpleDateFormat;
import java.util.Date;


public class MyLocationManager /*implements MyPermissionManager.PermissionsInterface*/ {

    private static final String PACKAGE_NAME = "com.dayani.m.flightsimulator2019";

    private static final String TAG = "MyLocationManager";

    public static final class Constants {

        private Constants() {}

        static final String KEY_ACTIVITY_UPDATES_REQUESTED = PACKAGE_NAME +
                ".ACTIVITY_UPDATES_REQUESTED";

        static final String KEY_DETECTED_ACTIVITIES = PACKAGE_NAME + ".DETECTED_ACTIVITIES";

        // Keys for storing activity state in the Bundle.
        final static String KEY_LOCATION_PERMISSION_GRANTED = "location-permission-granted";
        final static String KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates";
        final static String KEY_LOCATION = "location";
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

    private static final String[] LOCATION_PERMISSIONS = {
        Manifest.permission.ACCESS_FINE_LOCATION,
        //Manifest.permission.ACCESS_BACKGROUND_LOCATION
    };

    private static Context appContext;
    private static LocalBroadcastManager mLocalBrManager;

    private boolean isAvailable = false;
    private boolean mPermissionsGranted = false;
    private boolean isSettingsOk = false;
    private boolean mRequestingLocationUpdates = false;

    private String mLastUpdateTime = "";
    private Location mLastLocation = null;
    private Location mCurrentLocation = null;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;
    private LocationSettingsRequest mLocationSettingsRequest;
    private SettingsClient mSettingsClient;

    private StringBuffer mLocString;

    private HandlerThread mBackgroundThread;
    private Handler mLocationHandler;

    public MyLocationManager(Context ctxt, StringBuffer sb) {
        super();
        appContext = ctxt;
        mLocString = sb;
        mLocalBrManager = LocalBroadcastManager.getInstance(appContext);
        init();
        isAvailable = checkAvailability();
    }

    private void init() {

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext);
        mSettingsClient = LocationServices.getSettingsClient(appContext);

        // the process of building the LocationCallback, LocationRequest, and
        // LocationSettingsRequest objects.
        createLocationRequest();
        createLocationCallback();
        buildLocationSettingsRequest();
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(Constants.UPDATE_INTERVAL_IN_MILLISECONDS);
        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(Constants.FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Creates a callback for receiving location events.
     */
    private void createLocationCallback() {

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                if (locationResult == null) {
                    return;
                }
                mCurrentLocation = locationResult.getLastLocation();
                String sVal = getSensorString(mCurrentLocation);
                //just store the raw data without any change
                mLocString.append(sVal);
                Log.v(TAG, sVal);
                //mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            }
        };
    }

    /*
     private void createLocationCallbackThreadSafe() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                if (locationResult == null) {
                    return;
                }
                mCurrentLocation = locationResult.getLastLocation();
                String sVal = getSensorString(mCurrentLocation);
                synchronized (this) {
                    //just store the raw data without any change
                    mLocString.append(sVal);
                }
                Log.v(TAG, sVal);
                //mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            }
        };
     }
     */

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

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread(TAG);
        mBackgroundThread.start();
        mLocationHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mLocationHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    protected void updateValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(Constants.KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates =
                        savedInstanceState.getBoolean(Constants.KEY_REQUESTING_LOCATION_UPDATES);
            }
            if (savedInstanceState.keySet().contains(Constants.KEY_LOCATION_PERMISSION_GRANTED)) {
                isAvailable =
                        savedInstanceState.getBoolean(Constants.KEY_LOCATION_PERMISSION_GRANTED);
            }
            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            /*if (savedInstanceState.keySet().contains(Constants.KEY_LOCATION)) {
                // Since KEY_LOCATION was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(Constants.KEY_LOCATION);
            }
            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(Constants.KEY_LAST_UPDATED_TIME_STRING)) {
                mLastUpdateTime =
                        savedInstanceState.getString(Constants.KEY_LAST_UPDATED_TIME_STRING);
            }*/
        }
    }

    public void setBundleData(Bundle outState) {

        outState.putBoolean(Constants.KEY_LOCATION_PERMISSION_GRANTED, isAvailable);
        outState.putBoolean(Constants.KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates);
        //outState.putParcelable(Constants.KEY_LOCATION, mCurrentLocation);
        //outState.putString(Constants.KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime);
    }

    /*-------------------------------- Sertters and Getters --------------------------------------*/

    private boolean getRequestingLocationUpdatesFlag() {
        return mRequestingLocationUpdates;
    }
    private void setRequestingLocationUpdatesFlag(boolean stat) {
        mRequestingLocationUpdates = stat;
    }

    private void setSettingsOkFlag(boolean state) {
        isSettingsOk = state;
    }
    private boolean getSettingsOkFlag() {
        return isSettingsOk;
    }

    private boolean getAvailableFlag() {
        //Log.d(TAG, "available(): "+isAvailable);
        return isAvailable;
    }
    private void setAvailableFlag(boolean state) {
        isAvailable = state;
    }

    private void setPermissionsFlag(boolean state) {
        mPermissionsGranted = state;
    }

    public static int getRequestPermissionCode() {
        return Constants.REQUEST_LOCATION_PERMISSION_CODE;
    }

    public static String[] getPermissions(Context mContext) {

        if (hasGNSS_Sensor(mContext)) {
            Log.d(TAG, "Device supports the GNSS sensor.");
            return LOCATION_PERMISSIONS;
        }
        else {
            Log.d(TAG, "Device doesn't support the GNSS sensor.");
            return new String[]{};
        }
    }

    public static boolean hasGNSS_Sensor(Context mContext) {

        PackageManager packageManager = mContext.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    public static String getPermissionKey() {
        return Constants.KEY_LOCATION_PERMISSION;
    }


    /*======================================= Permissions ========================================*/

    private boolean checkPermissions() {
        boolean permission = MyPermissionManager.hasAllPermissions(appContext,
                LOCATION_PERMISSIONS, Constants.KEY_LOCATION_PERMISSION);
        return permission;
    }

    public boolean checkAvailability() {
        boolean permission = checkPermissions();
        setPermissionsFlag(permission);
        //checks only loc settings asynchronously.
        //and sets the settingsOk flag.
        checkLocationSettings();
        boolean available = permission && getSettingsOkFlag();
        setAvailableFlag(available);
        return available;
    }

    /*private void updateAvailability() {
        isAvailable = checkAvailability();
        Log.i(TAG, "Availability updated with: "+isAvailable);
    }*/

    /*======================================== Location ==========================================*/

    /*---------------------------------- Settings (for LocUpdates) -------------------------------*/

    /**
     * Only check, no request.
     * Since this is asynchronous, we need a way to update
     * total availability, without calling this forever!
     * Nothing cheesy is done here because this method is the
     * main global handle (and we don't want it to be time consuming).
     */
    private void checkLocationSettings() {

        if (appContext == null || mSettingsClient == null) {
            setSettingsOkFlag(false);
            return;
        }
        if (mLocationSettingsRequest == null) {
            buildLocationSettingsRequest();
        }
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener((AppCompatActivity) appContext,
                new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        // All location settings are satisfied. The client can initialize
                        // location requests here.
                        setSettingsOkFlag(true);
                        //updateAvailability();
                        Log.i(TAG, "All location settings are satisfied.");
                    }
                }
            )
            .addOnFailureListener((AppCompatActivity) appContext, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    setSettingsOkFlag(false);

                    int statusCode = ((ApiException) e).getStatusCode();
                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied, but this can be fixed
                            // by showing the user a dialog.
                            Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                    "location settings ");
                            //requestChangeSettings(e);
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String errorMessage = "Location settings are inadequate, and cannot be " +
                                    "fixed here. Fix in Settings.";
                            Log.e(TAG, errorMessage);
                            toastMessageLong(appContext, errorMessage);
                            break;
                    }
                }
            })
            .addOnCompleteListener((AppCompatActivity) appContext,
                new OnCompleteListener<LocationSettingsResponse>() {
                    @Override
                    public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                        boolean available = checkPermissions() && getSettingsOkFlag();
                        setAvailableFlag(available);
                    }
                }
            );
    }

    /**
     * Check & request change settings if necessary.
     */
    public void updateLocationSettings() {

        if (mLocationSettingsRequest == null) {
            buildLocationSettingsRequest();
        }
        // Begin by checking if the device has the necessary location settings.
        Task<LocationSettingsResponse> task =
                mSettingsClient.checkLocationSettings(mLocationSettingsRequest);
        task.addOnSuccessListener((AppCompatActivity) appContext,
            new OnSuccessListener<LocationSettingsResponse>() {
                @Override
                public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                    // All location settings are satisfied. The client can initialize
                    // location requests here.
                    //getLastLocation();
                    setSettingsOkFlag(true);
                    mLocalBrManager.
                        sendBroadcast(new Intent(Constants.ACTION_LOCATION_SETTINGS_AVAILABILITY));
                    Log.i(TAG, "All location settings are satisfied.");
                }
            }
        );
        task.addOnFailureListener((AppCompatActivity) appContext, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                /*if (e instanceof ResolvableApiException) {
                    requestChangeSettings(e);
                }*/
                setSettingsOkFlag(false);
                int statusCode = ((ApiException) e).getStatusCode();
                switch (statusCode) {
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                "location settings ");
                        requestChangeSettings(e);
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        String errorMessage = "Location settings are inadequate, and cannot be " +
                                "fixed here. Fix in Settings.";
                        Log.e(TAG, errorMessage);
                        toastMessageLong(appContext, errorMessage);
                        break;
                }
            }
        });
        task.addOnCompleteListener((AppCompatActivity) appContext,
            new OnCompleteListener<LocationSettingsResponse>() {
                @Override
                public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                    MyStateManager.setBoolPref(appContext,
                            Constants.KEY_LOCATION_SETTINGS, getSettingsOkFlag());
                    //updateAvailability();
                }
            }
        );
    }

    private void requestChangeSettings(Exception e) {
        try {
            // Show the dialog by calling startResolutionForResult(), and check the
            // result in onActivityResult().
            ResolvableApiException rae = (ResolvableApiException) e;
            rae.startResolutionForResult((AppCompatActivity) appContext,
                    Constants.REQUEST_CHECK_SETTINGS);
        } catch (IntentSender.SendIntentException sie) {
            Log.i(TAG, "PendingIntent unable to execute request.");
        }
    }

    /**
     *  This is not an override, it's a simple method that can handle
     *  Perhaps need to consider new mLocPermissionGranted feature here.
     *  location based part of onActivityResult in mainActivity.
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case Constants.REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        setSettingsOkFlag(true);
                        mLocalBrManager.
                            sendBroadcast(new Intent(Constants.ACTION_LOCATION_SETTINGS_AVAILABILITY));
                        Log.i(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        setSettingsOkFlag(false);
                        Log.i(TAG, "User chose not to make required location settings changes.");
                        break;
                    default:
                        setSettingsOkFlag(false);
                        Log.i(TAG, "Location settings result: default.");
                }
                MyStateManager.setBoolPref(appContext,
                        Constants.KEY_LOCATION_SETTINGS, getSettingsOkFlag());
                //updateAvailability();
                break;
        }
    }

    /*---------------------------------------- Last Location -------------------------------------*/

    /**
     * ?????????????????? appcontext: ?
     */
    @SuppressWarnings("MissingPermission")
    public void getLastLocation() {
        if (!this.checkAvailability())
            return;
        try {
            mFusedLocationClient.getLastLocation()
                .addOnSuccessListener((AppCompatActivity) appContext,
                    new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                // Logic to handle location object
                                mLastLocation = location;
                            } else {
                                Log.i(TAG, "getLastLocation: null location.");
                                toastMessageLong(appContext, "Location is null");
                            }
                        }
                    })
                .addOnFailureListener((AppCompatActivity) appContext,
                    new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            toastMessageLong(appContext,"Failure reading location");
                        }
                    })
                .addOnCompleteListener((AppCompatActivity) appContext,
                    new OnCompleteListener<Location>() {
                        @Override
                        public void onComplete(@NonNull Task<Location> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLastLocation = task.getResult();
                            }
                            else {
                                Log.w(TAG, "getLastLocation:exception", task.getException());
                                //showSnackbar(getString(R.string.no_location_detected));
                            }
                        }
                    });
        }
        catch (SecurityException e) {
            toastMessageLong(appContext, e.getMessage());
            return;
        }
    }

    /*------------------------------------------ Updates -----------------------------------------*/

    /**
     * should we worry about context?
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    @SuppressWarnings("MissingPermission")
    public void startLocationUpdatesWithSettingsCheck() {
        if (!this.checkAvailability()) {
            return;
        }
        if (mLocationSettingsRequest == null) {
            buildLocationSettingsRequest();
        }
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener((AppCompatActivity) appContext,
                        new OnSuccessListener<LocationSettingsResponse>() {
                            @Override
                            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                                Log.i(TAG, "All location settings are satisfied.");
                                try {
                                    //noinspection MissingPermission
                                    //getLastLocation();
                                    mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                            mLocationCallback, Looper.getMainLooper());
                                    setRequestingLocationUpdatesFlag(true);
                                }
                                catch (SecurityException e) {

                                }
                                //setRequestingLocationUpdates(true);
                            }
                        })
                .addOnFailureListener((AppCompatActivity) appContext, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult((AppCompatActivity) appContext,
                                            Constants.REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                toastMessageLong(appContext, errorMessage);
                                break;
                        }
                        setRequestingLocationUpdatesFlag(false);
                    }
                });
    }

    public void startLocationUpdatesWithSettingsCheck(final Looper locLooper) {
        if (!this.checkAvailability()) {
            return;
        }
        if (mLocationSettingsRequest == null) {
            buildLocationSettingsRequest();
        }
        //createLocationCallbackThreadSafe();
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener((AppCompatActivity) appContext,
                        new OnSuccessListener<LocationSettingsResponse>() {
                            @Override
                            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                                Log.i(TAG, "All location settings are satisfied.");
                                try {
                                    //noinspection MissingPermission
                                    //getLastLocation();
                                    mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                            mLocationCallback, locLooper);
                                    setRequestingLocationUpdatesFlag(true);
                                }
                                catch (SecurityException e) {

                                }
                                //setRequestingLocationUpdates(true);
                            }
                        })
                .addOnFailureListener((AppCompatActivity) appContext, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult((AppCompatActivity) appContext,
                                            Constants.REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                toastMessageLong(appContext, errorMessage);
                                break;
                        }
                        setRequestingLocationUpdatesFlag(false);
                    }
                });
    }

    public void startLocationUpdates(final Looper locLooper) {
        if (!this.checkAvailability()) {
            return;
        }
        if (mLocationSettingsRequest == null) {
            buildLocationSettingsRequest();
        }
        //createLocationCallbackThreadSafe();
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
            .addOnSuccessListener((AppCompatActivity) appContext,
                new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");
                        try {
                            //noinspection MissingPermission
                            getLastLocation();
                            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                    mLocationCallback, locLooper);
                            setRequestingLocationUpdatesFlag(true);
                        }
                        catch (SecurityException e) {

                        }
                    }
                })
            .addOnFailureListener((AppCompatActivity) appContext, new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    setRequestingLocationUpdatesFlag(false);
                    int statusCode = ((ApiException) e).getStatusCode();
                    switch (statusCode) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                    "location settings ");
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            String errorMessage = "Location settings are inadequate, and cannot be " +
                                    "fixed here. Fix in Settings.";
                            Log.e(TAG, errorMessage);
                            break;
                    }
                }
            });
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    public void stopLocationUpdates() {

        if (!getRequestingLocationUpdatesFlag()) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.");
            return;
        }
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            .addOnCompleteListener((AppCompatActivity) appContext,
                    new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            //Will this work in this context??????
                            Log.i(TAG, "stopLocationUpdates");
                            setRequestingLocationUpdatesFlag(false);
                            //setButtonsEnabledState();
                        }
                    }
            );
    }


    /*========================================= Helpers ==========================================*/

    protected void toastMessageLong(Context ctxt, String msg) {
        Toast.makeText(ctxt, msg, Toast.LENGTH_LONG).show();
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

    public String getLocationUpdate() {

        if (mCurrentLocation == null) {
            Log.w(TAG, "no location available.");
            return "No Location available!";
        }
        return "Last Updated at: " + mLastUpdateTime + ",\n" +
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

    /*
        API level 29 or higher
     */
    /*public void checkLocBackgroundPermission() {

        boolean permissionAccessCoarseLocationApproved =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        if (permissionAccessCoarseLocationApproved) {
            boolean backgroundLocationPermissionApproved =
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;

            if (backgroundLocationPermissionApproved) {
                // App can access location both in the foreground and in the background.
                // Start your service that doesn't have a foreground service type
                // defined.
            } else {
                // App can only access location in the foreground. Display a dialog
                // warning the user that your app must have all-the-time access to
                // location in order to function properly. Then, request background
                // location.
                ActivityCompat.requestPermissions(this, new String[] {
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                        MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION);
            }
        } else {
            // App doesn't have access to the device's location at all. Make full request
            // for permission.
            ActivityCompat.requestPermissions(this, new String[] {
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    },
                    MY_PERMISSIONS_REQUEST_BACKGROUND_LOCATION);
        }
    }*/
}
