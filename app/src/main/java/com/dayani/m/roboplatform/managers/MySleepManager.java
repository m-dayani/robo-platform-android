package com.dayani.m.roboplatform.managers;

/*
 * ADB Commands for testing doze mode:
 *      adb shell dumpsys deviceidle force-idle
 *      adb shell dumpsys deviceidle unforce
 *      adb shell dumpsys battery reset
 * ADB Commands for testing standby:
 *      adb shell dumpsys battery unplug
 *      adb shell am set-inactive <packageName> true
 *      adb shell am set-inactive <packageName> false
 *      adb shell am get-inactive <packageName>
 * Ignoring Battery Optimization permission is tested and
 *      works perfectly.
 * Important Note:
 *      WackLock mechanism for doing jobs even in sleep is
 *      deprecated (because it's not good to start a service
 *      from a broadcast receiver). Use WorkManager or Jobscheduler
 *      instead.
 */

import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.legacy.content.WakefulBroadcastReceiver;

import static android.content.Context.POWER_SERVICE;

public class MySleepManager {

    private static final String TAG = "MySleepManager";

    private static final int REQUEST_IGNORE_BATTERY_OPTIMIZATION = 1037;

    private Context mAppContext;

    private PackageManager mPackageManager;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    public MySleepManager(Context context) {
        mAppContext = context;
        mPackageManager = mAppContext.getPackageManager();
        mPowerManager = (PowerManager) mAppContext.getSystemService(Context.POWER_SERVICE);
    }

    /*
     * Start an alarm when the device restarts
     * This works even when reboots and goes to sleep mode.
     */
    /*public static void enableBootReciever(Context mAppContext) {
        ComponentName receiver = new ComponentName(mAppContext, AlarmBootReceiver.class);
        PackageManager pm = mAppContext.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static void disableBootReciever(Context mAppContext) {
        ComponentName receiver = new ComponentName(mAppContext, AlarmBootReceiver.class);
        PackageManager pm = mAppContext.getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }*/

    /**
     * requires this permission (manifest):
     *      android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
     */
    public static void askDozeWakePermission(Context mAppContext) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = mAppContext.getPackageName();
            PowerManager pm = (PowerManager) mAppContext.getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                //mAppContext.startActivity(intent);
                ((Activity) mAppContext).
                        startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATION);
                /*or this:
                if (pm.isIgnoringBatteryOptimizations(packageName))
                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                else {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                }
                */
            }
        }
    }

    /*public static void anotherAskDozeWakePerm(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);

        boolean isIgnoringBatteryOptimizations = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            isIgnoringBatteryOptimizations =
                    pm.isIgnoringBatteryOptimizations(context.getPackageName());
            if(!isIgnoringBatteryOptimizations){
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                ((Activity) context).
                        startActivityForResult(intent, REQUEST_IGNORE_BATTERY_OPTIMIZATION);
            }
        }

    }*/

    public static void onActivityResult(Context mAppContext,
                                        int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IGNORE_BATTERY_OPTIMIZATION) {
            PowerManager pm = (PowerManager) mAppContext.getSystemService(Context.POWER_SERVICE);
            boolean isIgnoringBatteryOptimizations = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                isIgnoringBatteryOptimizations =
                        pm.isIgnoringBatteryOptimizations(mAppContext.getPackageName());
                if(isIgnoringBatteryOptimizations){
                    // Ignoring battery optimization
                    Log.d(TAG, "Now Ignoring battery optimizations for this app.");
                }else{
                    // Not ignoring battery optimization
                    Log.d(TAG, "Use not agreed to ignore battery opt for this app.");
                }
            }

        }
    }

    //TODO: Explore and test these:
    //!!!!! Nasty code! !!!!!

    /**
     * requires this permission: android.permission.WAKE_LOCK
     */
    private void acquireWakeLock() {
        //PowerManager powerManager = (PowerManager) mAppContext.getSystemService(POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        mWakeLock.acquire();
    }

    private void releaseWakeLock() {
        mWakeLock.release();
    }

    public class MyWakefulReceiver extends WakefulBroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            // Start the service, keeping the device awake while the service is
            // launching. This is the Intent to deliver to the service.
            Intent service = new Intent(context, MyIntentService.class);
            startWakefulService(context, service);
        }
    }

    public class MyIntentService extends IntentService {
        public static final int NOTIFICATION_ID = 1;
        private NotificationManager notificationManager;
        NotificationCompat.Builder builder;
        public MyIntentService() {
            super("MyIntentService");
        }
        @Override
        protected void onHandleIntent(Intent intent) {
            Bundle extras = intent.getExtras();
            // Do the work that requires your app to keep the CPU running.
            // ...
            // Release the wake lock provided by the WakefulBroadcastReceiver.
            MyWakefulReceiver.completeWakefulIntent(intent);
        }
    }
}

