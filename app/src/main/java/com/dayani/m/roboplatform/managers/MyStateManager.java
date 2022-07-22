package com.dayani.m.roboplatform.managers;

/**
 * Master class for managing app states, both
 * short-time and permanent.
 * TODO: More strict settings and states
 *      management.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class MyStateManager {

    private static final String TAG = MyStateManager.class.getSimpleName();

    private static Context mAppContext;
    private SharedPreferences mDefaultSharedPrefs;

    public MyStateManager(Context context) {
        mAppContext = context;
        mDefaultSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean getBoolPref(final String key, boolean b) {
        return mDefaultSharedPrefs.getBoolean(key, b);
    }

    public static boolean getBoolPref(Context context, final String key, boolean b) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(key, b);
    }

    public void setBoolPref(final String key, boolean b) {
        SharedPreferences.Editor editor = mDefaultSharedPrefs.edit();
        editor.putBoolean(key, b);
        editor.commit();
    }

    public static void setBoolPref(Context context, final String key, boolean b) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(key, b)
                .apply();
    }

    static void setIntegerPref(Context context, String key, int val) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(key, val)
                .apply();
    }

    static int getIntegerPref(Context context, String key, int def) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(key, def);
    }

    static void setStringPref(Context context, String key, String val) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(key, val)
                .apply();
    }

    static String getStringPref(Context context, String key, String def) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(key, def);
    }

    static void setLongPref(Context context, String key, long val) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putLong(key, val)
                .apply();
    }

    static long getLongPref(Context context, String key, long def) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getLong(key, def);
    }

    public static void putBoolState(Bundle outState, final String key, boolean b) {
        outState.putBoolean(key, b);
    }

    public static boolean getBoolState(Bundle savedInstanceState, final String key, boolean b) {
        if (savedInstanceState != null) {
            return savedInstanceState.getBoolean(key);
        }
        return b;
    }

    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the MainActivity.
     */
    /*static void sendNotification(Context context, String notificationDetails) {
        // Create an explicit content Intent that starts the main Activity.
        Intent notificationIntent = new Intent(context, PendingIntentActivity.class);

        notificationIntent.putExtra("from_notification", true);

        // Construct a task stack.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);

        // Add the main Activity to the task stack as the parent.
        stackBuilder.addParentStack(PendingIntentActivity.class);

        // Push the content Intent onto the stack.
        stackBuilder.addNextIntent(notificationIntent);

        // Get a PendingIntent containing the entire back stack.
        PendingIntent notificationPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        // Get a notification builder that's compatible with platform versions >= 4
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);

        // Define the notification settings.
        builder.setSmallIcon(R.mipmap.ic_launcher)
                // In a real app, you may want to use a library like Volley
                // to decode the Bitmap.
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.mipmap.ic_launcher))
                .setColor(Color.RED)
                .setContentTitle("Location update")
                .setContentText(notificationDetails)
                .setContentIntent(notificationPendingIntent);

        // Dismiss notification once the user touches it.
        builder.setAutoCancel(true);

        // Get an instance of the Notification manager
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel);

            // Channel ID
            builder.setChannelId(CHANNEL_ID);
        }

        // Issue the notification
        mNotificationManager.notify(0, builder.build());
    }*/
}

