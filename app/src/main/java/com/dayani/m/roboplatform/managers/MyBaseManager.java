package com.dayani.m.roboplatform.managers;
/*
    Deal with common manager's tasks:
        - Support: This is resolved in the construction and is never changed.
            If a module is not supported, it will never be supported.
        - Requirements: always change. Can be resolved in two ways:
            The result of an intent activity (GPS Setting)
            The result of another fragment or activity (USB Device)
        - Permissions: is a requirement, so always change.
        - Checked: A manager is checked if at least a sensor is checked or it has no sensors (storage).
            It is determined by user's interaction with the app -> can change only from a specific fragment
        - Availability: A manager is available when all above are satisfied.
        - State & Lifecycle aware methods

    Note: By keeping a final reference to sensor groups we make sure any change
          from anywhere is accessible from other classes
 */


import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.RequirementResolution;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.managers.MyStorageManager.StorageInfo;
import com.dayani.m.roboplatform.utils.interfaces.MessageChannel;
import com.dayani.m.roboplatform.utils.interfaces.MessageChannel.MyMessage;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public abstract class MyBaseManager {

    // Class Variables

    private static final String TAG = MyBaseManager.class.getSimpleName();

    public static final String KEY_INTENT_ACTIVITY_LAUNCHER =
            AppGlobals.PACKAGE_BASE_NAME + ".key_intent_activity_launcher";

    protected final boolean mbIsSupported;
    // requirements consist of other booleans
    protected boolean mbIsPermitted;
    protected boolean mbIsChecked;

    protected boolean mIsProcessing;

    protected HandlerThread mBackgroundThread;
    protected Handler mHandler;

    protected final List<MySensorGroup> mlSensorGroup;

    protected RequirementResolution mRequirementRequestListener;
    protected final List<MessageChannel<?>> mlChannelTransactions;

    protected final Map<Integer, StorageInfo> mmStorageChannels;

    // Construction

    public MyBaseManager(Context context) {

        mbIsSupported = resolveSupport(context);
        mIsProcessing = false;
        mlChannelTransactions = new ArrayList<>();

        if (context instanceof RequirementResolution) {
            setRequirementResolutionListener((RequirementResolution) context);
        }
        if (context instanceof MessageChannel) {
            addChannelTransaction((MessageChannel<?>) context);
        }

        mmStorageChannels = initStorageChannels();
        mlSensorGroup = getSensorGroups(context);

        //updateAvailabilityAndCheckedSensors(context);
    }

    // Support

    protected abstract boolean resolveSupport(Context context);
    protected boolean isSupported() { return mbIsSupported; }

    // Requirements & Permissions

    protected abstract List<Requirement> getRequirements();

    public boolean passedAllRequirements() { return hasAllPermissions(); }
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
    }

    // respond requirements
    public void onActivityResult(Context context, ActivityResult result) {}
    // request requirements
    protected void resolveRequirements(Context context) {

        List<Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to resolve");
            return;
        }

        // resolve permissions
        if (requirements.contains(Requirement.PERMISSIONS)) {
            resolvePermissions();
        }
    }

    protected abstract List<String> getPermissions();

    public boolean hasAllPermissions() { return mbIsPermitted; }
    public void updatePermissionsState(Context context) {

        List<String> lPerms = getPermissions();
        if (lPerms != null && !lPerms.isEmpty()) {
            String[] perms = lPerms.toArray(new String[0]);
            mbIsPermitted = MyPermissionManager.hasAllPermissions(context, perms);
        }
        else {
            mbIsPermitted = true;
        }
    }

    // respond permissions
    // Nothing needs to be done! (because if permitted hasAllPermissions yields true)
    public void onPermissionsResult(Context context, Map<String, Boolean> permissions) {

        updatePermissionsState(context);
    }
    // request permissions
    protected void resolvePermissions() {

        if (mRequirementRequestListener == null) {
            Log.w(TAG, "Permission launcher is null");
            return;
        }

        String[] perms = getPermissions().toArray(new String[0]);
        mRequirementRequestListener.requestResolution(perms);
    }

    public void setRequirementResolutionListener(RequirementResolution requestListener) {
        mRequirementRequestListener = requestListener;
    }

    public void onBroadcastReceived(Context context, Intent intent) {}

    // Availability

    public boolean hasCheckedSensors() {
        return mbIsChecked;
    }
    public void updateCheckedSensors() {
        mbIsChecked = MySensorGroup.countCheckedSensors(mlSensorGroup) > 0;
    }

    public boolean isAvailableAndChecked() { return isAvailable() && hasCheckedSensors(); }
    public boolean isAvailable() {
        return isSupported() && passedAllRequirements();
    }
    public void updateAvailabilityAndCheckedSensors(Context context) {

        updateRequirementsState(context);
        updateCheckedSensors();
    }
    public void resolveAvailability(Context context) {

        // if module is not supported, the resolution is not possible
        if (!isSupported()) {
            Log.w(TAG, "Module is not supported on the device");
            return;
        }

        // resolve requirements
        // note: permissions are a kind of requirement
        resolveRequirements(context);
    }

    // Lifecycle

    protected void init(Context context) {}
    public void clean(Context context) {}

    public void start(Context context) {
        if (!isProcessing()) {
            mIsProcessing = true;
        }
    }
    public void stop(Context context) {
        if (isProcessing()) {
            mIsProcessing = false;
        }
    }

    // State

    public boolean isProcessing() { return mIsProcessing; }

    //public void updateState(Context context) {}
    public void updateCheckedSensorsWithAvailability() {

        if (mlSensorGroup == null) {
            return;
        }

        final boolean isAvailable = isAvailable();

        for (MySensorGroup sensorGroup : mlSensorGroup) {
            if (sensorGroup != null) {
                for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {
                    if (sensorInfo != null) {
                        sensorInfo.setChecked(isAvailable);
                    }
                }
            }
        }
    }

    // Resources

    public abstract List<MySensorGroup> getSensorGroups(Context context);

    /**
     * Starts a background thread and its {@link Handler}.
     */
    protected void startBackgroundThread(String tag) {

        mBackgroundThread = new HandlerThread(tag);
        mBackgroundThread.start();
        mHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    protected void stopBackgroundThread() {

        if (mBackgroundThread == null) {
            return;
        }

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Message passing
    protected abstract Map<Integer, StorageInfo> initStorageChannels();
    protected abstract void openStorageChannels(Context context);

    protected int getChannelId(int sensorId) {

        if (mmStorageChannels == null) {
            return -1;
        }

        StorageInfo sInfo = mmStorageChannels.get(sensorId);

        if (sInfo != null) {
            return sInfo.getChannelId();
        }

        return -1;
    }
    protected void publish(int sensorId, MyMessage msg) {

        if (mlChannelTransactions == null) {
            return;
        }

        for (MessageChannel<?> channel : mlChannelTransactions) {
            channel.publishMessage(getChannelId(sensorId), msg);
        }
    }

    protected void closeStorageChannels() {

        if (mlChannelTransactions == null || mmStorageChannels == null) {
            return;
        }

        for (StorageInfo storageInfo : mmStorageChannels.values()) {

            int chId = storageInfo.getChannelId();
            if (chId >= 0) {
                for (MessageChannel<?> channel : mlChannelTransactions) {

                    channel.closeChannel(chId);
                    storageInfo.setChannelId(-1);
                }
            }
        }
    }

    public void addChannelTransaction(MessageChannel<?> channel) {

        if (mlChannelTransactions != null) {
            mlChannelTransactions.add(channel);
        }
    }

    // Helpers

    public static MyBaseManager getManager(List<MyBaseManager> lManagers, String className) {

        if (lManagers == null || className == null || className.isEmpty()) {
            return null;
        }

        for (MyBaseManager manager : lManagers) {

            String managerName = manager.getClass().getSimpleName();

            if (managerName.equals(className)) {
                return manager;
            }
        }

        return null;
    }

    private void logMessage(String logMessage) {

    }
}
