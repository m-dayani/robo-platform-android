package com.dayani.m.roboplatform.utils.android_gnss;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.RequiresApi;

import com.dayani.m.roboplatform.utils.loggers.UiLogger;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A container for GPS related API calls, it binds the {@link LocationManager} with {@link UiLogger}
 */
public class GnssContainer {

    public static final String TAG = "GnssContainer";

    private static final long LOCATION_RATE_GPS_MS = TimeUnit.SECONDS.toMillis(1L);
    private static final long LOCATION_RATE_NETWORK_MS = TimeUnit.SECONDS.toMillis(60L);

    private boolean mLogLocations = true;
    private boolean mLogNavigationMessages = true;
    private boolean mLogMeasurements = true;
    private boolean mLogStatuses = true;
    private boolean mLogNmeas = true;
    private long registrationTimeNanos = 0L;
    private long firstLocatinTimeNanos = 0L;
    private long ttff = 0L;
    private boolean firstTime = true;

    private final List<GnssListener> mLoggers;

    private static Context mContext;
    private final LocationManager mLocationManager;
    private final LocationListener mLocationListener =
            new LocationListener() {

                @Override
                public void onProviderEnabled(String provider) {
                    if (mLogLocations) {
                        for (GnssListener logger : mLoggers) {
//                            if (logger instanceof AgnssUiLogger && !firstTime) {
//                                continue;
//                            }
                            logger.onProviderEnabled(provider);
                        }
                    }
                }

                @Override
                public void onProviderDisabled(String provider) {
                    if (mLogLocations) {
                        for (GnssListener logger : mLoggers) {
//                            if (logger instanceof AgnssUiLogger && !firstTime) {
//                                continue;
//                            }
                            logger.onProviderDisabled(provider);
                        }
                    }
                }

                @Override
                public void onLocationChanged(Location location) {
                    if (firstTime && location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                        if (mLogLocations) {
                            for (GnssListener logger : mLoggers) {
                                firstLocatinTimeNanos = SystemClock.elapsedRealtimeNanos();
                                ttff = firstLocatinTimeNanos - registrationTimeNanos;
                                logger.onTTFFReceived(ttff);
                            }
                        }
                        firstTime = false;
                    }
                    if (mLogLocations) {
                        for (GnssListener logger : mLoggers) {
//                            if (logger instanceof AgnssUiLogger && !firstTime) {
//                                continue;
//                            }
                            logger.onLocationChanged(location);
                        }
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    if (mLogLocations) {
                        for (GnssListener logger : mLoggers) {
                            logger.onLocationStatusChanged(provider, status, extras);
                        }
                    }
                }
            };

    @RequiresApi(api = Build.VERSION_CODES.N)
    private final GnssMeasurementsEvent.Callback gnssMeasurementsEventListener =
            new GnssMeasurementsEvent.Callback() {
                @Override
                public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
                    if (mLogMeasurements) {
                        for (GnssListener logger : mLoggers) {
                            logger.onGnssMeasurementsReceived(event);
                        }
                    }
                }

                @Override
                public void onStatusChanged(int status) {
                    if (mLogMeasurements) {
                        for (GnssListener logger : mLoggers) {
                            logger.onGnssMeasurementsStatusChanged(status);
                        }
                    }
                }
            };

    @RequiresApi(api = Build.VERSION_CODES.N)
    private final GnssNavigationMessage.Callback gnssNavigationMessageListener =
            new GnssNavigationMessage.Callback() {
                @Override
                public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
                    if (mLogNavigationMessages) {
                        for (GnssListener logger : mLoggers) {
                            logger.onGnssNavigationMessageReceived(event);
                        }
                    }
                }

                @Override
                public void onStatusChanged(int status) {
                    if (mLogNavigationMessages) {
                        for (GnssListener logger : mLoggers) {
                            logger.onGnssNavigationMessageStatusChanged(status);
                        }
                    }
                }
            };

    @RequiresApi(api = Build.VERSION_CODES.N)
    private final GnssStatus.Callback gnssStatusListener =
            new GnssStatus.Callback() {
                @Override
                public void onStarted() {
                }

                @Override
                public void onStopped() {
                }

                @Override
                public void onFirstFix(int ttff) {
                }

                @Override
                public void onSatelliteStatusChanged(GnssStatus status) {
                    for (GnssListener logger : mLoggers) {
                        logger.onGnssStatusChanged(status);
                    }
                }
            };

    @RequiresApi(api = Build.VERSION_CODES.N)
    private final OnNmeaMessageListener nmeaListener =
            new OnNmeaMessageListener() {
                @Override
                public void onNmeaMessage(String s, long l) {
                    if (mLogNmeas) {
                        for (GnssListener logger : mLoggers) {
                            logger.onNmeaReceived(l, s);
                        }
                    }
                }
            };

    public GnssContainer(Context context, GnssListener... loggers) {
        this.mContext = context;
        this.mLoggers = Arrays.asList(loggers);
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public LocationManager getLocationManager() {
        return mLocationManager;
    }

    public void setLogLocations(boolean value) {
        mLogLocations = value;
    }

    public boolean canLogLocations() {
        return mLogLocations;
    }

    public void setLogNavigationMessages(boolean value) {
        mLogNavigationMessages = value;
    }

    public boolean canLogNavigationMessages() {
        return mLogNavigationMessages;
    }

    public void setLogMeasurements(boolean value) {
        mLogMeasurements = value;
    }

    public boolean canLogMeasurements() {
        return mLogMeasurements;
    }

    public void setLogStatuses(boolean value) {
        mLogStatuses = value;
    }

    public boolean canLogStatuses() {
        return mLogStatuses;
    }

    public void setLogNmeas(boolean value) {
        mLogNmeas = value;
    }

    public boolean canLogNmeas() {
        return mLogNmeas;
    }



    public void registerLocation() {
        boolean isGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isGpsProviderEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED &&
                    mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return;
            }
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_RATE_NETWORK_MS,
                    0.0f /* minDistance */,
                    mLocationListener);
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_RATE_GPS_MS,
                    0.0f /* minDistance */,
                    mLocationListener);
        }
        logRegistration("LocationUpdates", isGpsProviderEnabled);
    }

    public void registerSingleNetworkLocation() {
        boolean isNetworkProviderEnabled =
                mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (isNetworkProviderEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED &&
                    mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return;
            }
            mLocationManager.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER, mLocationListener, null);
        }
        logRegistration("LocationUpdates", isNetworkProviderEnabled);
    }

    public void registerSingleGpsLocation() {
        boolean isGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (isGpsProviderEnabled) {
            this.firstTime = true;
            registrationTimeNanos = SystemClock.elapsedRealtimeNanos();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED &&
                    mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return;
            }
            mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, mLocationListener, null);
        }
        logRegistration("LocationUpdates", isGpsProviderEnabled);
    }

    public void unregisterLocation() {
        mLocationManager.removeUpdates(mLocationListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void registerMeasurements() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED &&
                mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            return;
        }
        logRegistration(
                "GnssMeasurements",
                mLocationManager.registerGnssMeasurementsCallback(gnssMeasurementsEventListener));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void unregisterMeasurements() {
        mLocationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsEventListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void registerNavigation() {
        logRegistration(
                "GpsNavigationMessage",
                mLocationManager.registerGnssNavigationMessageCallback(gnssNavigationMessageListener));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void unregisterNavigation() {
        mLocationManager.unregisterGnssNavigationMessageCallback(gnssNavigationMessageListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void registerGnssStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED &&
                mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            return;
        }
        logRegistration("GnssStatus", mLocationManager.registerGnssStatusCallback(gnssStatusListener));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void unregisterGpsStatus() {
        mLocationManager.unregisterGnssStatusCallback(gnssStatusListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void registerNmea() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                mContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED &&
                mContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    Activity#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for Activity#requestPermissions for more details.
            return;
        }
        logRegistration("Nmea", mLocationManager.addNmeaListener(nmeaListener));
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void unregisterNmea() {
        mLocationManager.removeNmeaListener(nmeaListener);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void registerAll() {
        registerLocation();
        registerMeasurements();
        registerNavigation();
        registerGnssStatus();
        registerNmea();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void unregisterAll() {
        unregisterLocation();
        unregisterMeasurements();
        unregisterNavigation();
        unregisterGpsStatus();
        unregisterNmea();
    }

    private void logRegistration(String listener, boolean result) {
        for (GnssListener logger : mLoggers) {
//            if (logger instanceof AgnssUiLogger && !firstTime) {
//                continue;
//            }
            logger.onListenerRegistration(listener, result);
        }
    }
}

