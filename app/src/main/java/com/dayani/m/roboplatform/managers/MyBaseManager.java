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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.ActivityRequirements.RequirementResolution;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.managers.MyStorageManager.StorageInfo;
import com.dayani.m.roboplatform.managers.MyStorageManager.StorageChannel;
import com.dayani.m.roboplatform.utils.MySensorInfo;

import java.util.List;
import java.util.Map;


public abstract class MyBaseManager {

    // Class Variables

    private static final String TAG = MyBaseManager.class.getSimpleName();

    protected static final String KEY_INTENT_ACTIVITY_LAUNCHER =
            AppGlobals.PACKAGE_BASE_NAME + "key_intent_activity_launcher";

    protected final boolean mIsSupported;

    protected boolean mIsProcessing;

    protected HandlerThread mBackgroundThread;
    protected Handler mHandler;

    protected final List<MySensorGroup> mlSensorGroup;

    protected RequirementResolution mRequirementRequestListener;
    protected StorageChannel mStorageListener;

    protected final Map<Integer, StorageInfo> mmStorageChannels;

    // Construction

    public MyBaseManager(Context context) {

        mIsSupported = resolveSupport(context);
        mIsProcessing = false;

        if (context instanceof RequirementResolution) {
            setRequirementResolutionListener((RequirementResolution) context);
        }
        if (context instanceof StorageChannel) {
            setStorageListener((StorageChannel) context);
        }

        mmStorageChannels = initStorageChannels();
        mlSensorGroup = getSensorGroups(context);
    }

    // Support

    protected abstract boolean resolveSupport(Context context);
    protected boolean isSupported() { return mIsSupported; }

    // Requirements & Permissions

    public abstract List<Requirement> getRequirements();
    protected boolean hasAllRequirements(Context context) {

        List<Requirement> requirements = getRequirements();

        // permissions
        boolean resPerms = true;
        if (requirements.contains(Requirement.PERMISSIONS)) {
            resPerms = hasAllPermissions(context);
        }

        return resPerms;
    }

    // respond requirements
    public void onActivityResult(Context context, ActivityResult result) {}
    // request requirements
    protected void resolveRequirements(Context context) {

        // resolve permissions
        resolvePermissions();
    }

    public abstract List<String> getPermissions();
    protected boolean hasAllPermissions(Context context) {

        String[] perms = getPermissions().toArray(new String[0]);
        return MyPermissionManager.hasAllPermissions(context, perms);
    }

    // respond permissions
    // Nothing needs to be done! (because if permitted hasAllPermissions yields true)
    public void onPermissionsResult(Context context, Map<String, Boolean> permissions) {}
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

    // Availability

    public boolean isAvailable(Context context) {

        boolean resSupport = isSupported();
        boolean resRequirements = hasAllRequirements(context);
        boolean resPerms = hasAllPermissions(context);
        boolean resChecked = hasCheckedSensors();

        return resSupport && resRequirements && resPerms && resChecked;
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
    public void clean() {}

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

    public void updateState(Context context) {}
    protected void updateCheckedSensors(Context context) {

        if (mlSensorGroup == null) {
            return;
        }

        final boolean isAvailable = isAvailable(context);

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

    protected boolean hasCheckedSensors() {

        return MySensorGroup.countCheckedSensors(mlSensorGroup) > 0;
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
    protected abstract void openStorageChannels();

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
    protected void writeStorageChannel(int sensorId, String msg) {

        if (mStorageListener != null) {
            mStorageListener.publishMessage(getChannelId(sensorId), msg);
        }
    }

    protected void closeStorageChannels() {

        if (mStorageListener == null || mmStorageChannels == null) {
            return;
        }

        for (StorageInfo storageInfo : mmStorageChannels.values()) {

            int chId = storageInfo.getChannelId();
            if (chId >= 0) {
                mStorageListener.removeChannel(chId);
                storageInfo.setChannelId(-1);
            }
        }
    }

    public void setStorageListener(StorageChannel storageListener) {
        mStorageListener = storageListener;
    }

    // Helpers

    public static MyBaseManager getManager(List<MyBaseManager> lManagers, String className) {

        for (MyBaseManager manager : lManagers) {

            String managerName = manager.getClass().getSimpleName();

            if (managerName.equals(className)) {
                return manager;
            }
        }
        return null;
    }
}
