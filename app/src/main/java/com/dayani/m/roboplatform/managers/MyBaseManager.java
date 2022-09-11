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
import android.util.Log;
import android.util.Pair;

import androidx.activity.result.ActivityResult;

import com.dayani.m.roboplatform.RecordingFragment;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.RequirementResolution;
import com.dayani.m.roboplatform.utils.interfaces.MyBackgroundExecutor;
import com.dayani.m.roboplatform.utils.interfaces.MyChannels.ChannelTransactions;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgLogging;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;


public abstract class MyBaseManager implements ChannelTransactions {

    // Class Variables

    private static final String TAG = MyBaseManager.class.getSimpleName();

    public static final String KEY_INTENT_ACTIVITY_LAUNCHER =
            AppGlobals.PACKAGE_BASE_NAME + ".key_intent_activity_launcher";

    protected final boolean mbIsSupported;
    // requirements consist of other booleans
    protected boolean mbIsPermitted;
    protected boolean mbIsChecked;

    protected boolean mIsProcessing;

    // spreading multi-threading logic everywhere doesn't provide performance!
    protected MyBackgroundExecutor.JobListener mBackgroundJobListener;

    protected final List<MySensorGroup> mlSensorGroup;

    protected RequirementResolution mRequirementRequestListener;


    //protected final Map<MyChannels.ChannelType, Set<MessageChannel>> mlChannels;
    protected final Set<ChannelTransactions> mlChannels;
    // map<ChannelId, Map<taskId, targetId>>
    //protected final Map<String, Map<Integer, Integer>> mChannelTargetMap;

    // map<resId, ConfigMsg>: list of tagged configuration messages
    // used to interact with message channels
    protected final Map<String, MyMessages.MsgConfig> mmResources;

    // Construction

    public MyBaseManager(Context context) {

        mbIsSupported = resolveSupport(context);

        setIsProcessing(false);

        mlChannels = new HashSet<>();
        //mChannelTargetMap = new HashMap<>();

        mlSensorGroup = getSensorGroups(context);

        mmResources = new HashMap<>(); //initStorageChannels();
    }

    // Support

    protected abstract boolean resolveSupport(Context context);
    public boolean isSupported() { return mbIsSupported; }

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

    // Checked sensors

    public boolean hasCheckedSensors() {
        return mbIsChecked;
    }
    public void updateCheckedSensors() {
        mbIsChecked = MySensorGroup.countCheckedSensors(mlSensorGroup) > 0;
    }

    public void updateCheckedSensorsWithAvailability() {

        if (mlSensorGroup == null) {
            return;
        }

        final boolean isAvailable = isAvailable();

        if (!isAvailable) {
            for (MySensorGroup sensorGroup : mlSensorGroup) {
                if (sensorGroup != null) {
                    for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {
                        if (sensorInfo != null) {
                            sensorInfo.setChecked(false);
                        }
                    }
                }
            }
        }
    }
    public void onCheckedChanged(Context context, int grpId, int sensorId, boolean state) {

        if (!isAvailable()) {
            // resolve availability
            resolveAvailability(context);
        }
        else {
            MySensorInfo sensorInfo = getSensorInfo(grpId, sensorId);
            if (sensorInfo != null) {
                sensorInfo.setChecked(state);
            }
        }
    }

    // Availability

    public boolean isAvailableAndChecked() { return isAvailable() && hasCheckedSensors(); }
    public void updateAvailabilityAndCheckedSensors(Context context) {

        updateRequirementsState(context);
        updateCheckedSensors();
    }

    public boolean isAvailable() {
        return isSupported() && passedAllRequirements();
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

    // these two are called with every activity's onCreate() and onDestroy()
    public void init(Context context) {

        if (context instanceof RequirementResolution) {
            setRequirementResolutionListener((RequirementResolution) context);
        }
        if (context instanceof ChannelTransactions) {
            registerChannel((ChannelTransactions) context);
        }
        if (context instanceof MyBackgroundExecutor.JobListener) {
            mBackgroundJobListener = (MyBackgroundExecutor.JobListener) context;
        }

        updateAvailabilityAndCheckedSensors(context);
    }
    public void clean(Context context) {
        // no need to clean message channels because it's a set
    }

    // use these in a fragment's onResume/onPause or onStart/onStop
    // preconfigure resources for faster start/stop response
    public void initConfigurations(Context context) {}
    public void cleanConfigurations(Context context) {}

    // resource intensive methods, only call based on user interaction
    public void start(Context context) {

        if (this.isProcessing()) {
            stop(context);
        }
        setIsProcessing(true);

        String mClassName = getClass().getSimpleName();
        Log.d(TAG, mClassName + " started successfully");
        // publish a logging message (doesn't care about task Id)
        logMessage("Started " + mClassName + "\n", RecordingFragment.class.getSimpleName());
    }
    public void stop(Context context) {

        setIsProcessing(false);

        String mClassName = getClass().getSimpleName();
        Log.d(TAG, mClassName + " stopped successfully");
        // publish a logging message
        logMessage("Stopped " + mClassName + "\n", RecordingFragment.class.getSimpleName());
    }

    // State

    public synchronized boolean isProcessing() { return mIsProcessing; }
    protected synchronized void setIsProcessing(boolean state) { mIsProcessing = state; }

    // Resources

    protected Executor getBgExecutor() {
        if (mBackgroundJobListener != null) {
            return mBackgroundJobListener.getBackgroundExecutor();
        }
        return null;
    }

    protected Handler getBgHandler() {
        if (mBackgroundJobListener != null) {
            return mBackgroundJobListener.getBackgroundHandler();
        }
        return null;
    }

    protected void doInBackground(Runnable r) {
        if (mBackgroundJobListener != null) {
            mBackgroundJobListener.execute(r);
        }
    }

    protected MySensorInfo getSensorInfo(int grpId, int sensorId) {

        MySensorGroup sensorGroup = MySensorGroup.findSensorGroupById(mlSensorGroup, grpId);

        if (sensorGroup != null) {
            return sensorGroup.getSensorInfo(sensorId);
        }
        return null;
    }

    public abstract List<MySensorGroup> getSensorGroups(Context context);

    // Message passing (Storage)

    // determines the mapping between sensors and unique identifiers
    protected abstract String getResourceId(MyResourceIdentifier resId);
    protected MsgConfig getResourceMsg(MyResourceIdentifier resId) {

        String strResId = getResourceId(resId);

        return mmResources.get(strResId);
    }
    protected int getTargetId(MyResourceIdentifier resId) {

        MyMessages.MsgConfig configMsg = getResourceMsg(resId);

        if (configMsg != null) {
            return configMsg.getTargetId();
        }
        return -1;
    }

    //protected abstract Map<Integer, StorageInfo> initStorageChannels();
    // returns a list of tag, config pairs, used to open storage channels
    protected abstract List<Pair<String, MsgConfig>> getStorageConfigMessages(MySensorInfo sensor);

    protected void openStorageChannels() {

        if (mmResources == null || mlSensorGroup == null) {
            Log.w(TAG, "Either sensors are not available or no storage listener found");
            return;
        }

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            if (sensorGroup == null) {
                continue;
            }

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (sensorInfo != null && sensorInfo.isChecked()) {

                    List<Pair<String, MsgConfig>> configInfo = getStorageConfigMessages(sensorInfo);

                    for (Pair<String, MsgConfig> configPair : configInfo) {

                        MsgConfig config = configPair.second;
                        // open channel
                        publishMessage(config);
                        // save info
                        mmResources.put(configPair.first, config);
                        // write header
                        MyMessages.MsgStorage writeHeader = new MyMessages.MsgStorage(
                                config.toString(), null, config.getTargetId());
                        publishMessage(writeHeader);
                    }
                }
            }
        }
    }

    protected void closeStorageChannels() {

        if (mmResources == null) {
            return;
        }

        for (MsgConfig configMsg : mmResources.values()) {

            if (configMsg instanceof StorageConfig) {

                StorageConfig storageConfig = (StorageConfig) configMsg;
                StorageConfig closeMsg = new StorageConfig(MsgConfig.ConfigAction.CLOSE,
                        storageConfig.getSender(), storageConfig.getStorageInfo());

                publishMessage(closeMsg);
            }

        }
    }

    /*protected abstract Set<Integer> getSelectedStorageTaskIds();
    protected void openStorageChannels(Context context, Set<Integer> selectedTaskIds) {

        MyChannels.ChannelType chType = MyChannels.ChannelType.STORAGE;
        Set<MessageChannel<?>> channels = mlChannels.get(chType);

        if (channels == null) {
            return;
        }

        for (MessageChannel<?> channel : channels) {

            if (channel == null || channel.getChannelType() != chType) {
                continue;
            }

            MyChannels.StorageChannel storageChannel = (MyChannels.StorageChannel) channel;
            String chTag = storageChannel.getChannelId();

            for (Integer taskId : selectedTaskIds) {

                if (taskId == null) {
                    continue;
                }

                StorageInfo storageInfo = mmStorageChannels.get(taskId);

                if (storageInfo == null) {
                    continue;
                }

                int targetId = storageChannel.openNewChannel(chType, context, storageInfo);
                storageInfo.setChannelId(targetId);

                Map<Integer, Integer> taskTargetMap = mChannelTargetMap.get(chTag);

                if (taskTargetMap == null) {
                    taskTargetMap = new HashMap<>();
                    mChannelTargetMap.put(chTag, taskTargetMap);
                }

                taskTargetMap.put(taskId, targetId);
            }
        }
    }

    protected void publish(MyChannels.ChannelType chType, int taskId, MyMessage msg) {

        Set<MessageChannel<?>> channels = mlChannels.get(chType);

        if (channels == null) {
            return;
        }

        for (MessageChannel<?> channel : channels) {

            if (channel == null) {
                continue;
            }

            Map<Integer, Integer> taskTargetMap = mChannelTargetMap.get(channel.getChannelId());

            if (taskTargetMap == null) {
                continue;
            }

            Integer targetId = taskTargetMap.get(taskId);

            if (targetId == null) {
                continue;
            }

            channel.publishMessage(chType, targetId, msg);
        }
    }

    protected void closeChannels(MyChannels.ChannelType chType) {

        Set<MessageChannel<?>> channels = mlChannels.get(chType);

        if (channels == null) {
            return;
        }

        for (MessageChannel<?> channel : channels) {

            if (channel == null) {
                continue;
            }

            Map<Integer, Integer> taskTargetMap = mChannelTargetMap.get(channel.getChannelId());

            if (taskTargetMap == null) {
                continue;
            }

            for (Integer targetId : taskTargetMap.values()) {

                if (targetId == null) {
                    continue;
                }

                channel.closeChannel(chType, targetId);
            }
        }
    }
    protected void closeChannels(MyChannels.ChannelType chType, int taskId) {

        Set<MessageChannel<?>> channels = mlChannels.get(chType);

        if (channels == null) {
            return;
        }

        for (MessageChannel<?> channel : channels) {

            if (channel == null) {
                continue;
            }

            Map<Integer, Integer> taskTargetMap = mChannelTargetMap.get(channel.getChannelId());

            if (taskTargetMap == null) {
                continue;
            }

            Integer targetId = taskTargetMap.get(taskId);

            if (targetId == null) {
                continue;
            }

            channel.closeChannel(chType, targetId);
        }
    }

    public void addChannelTransaction(MessageChannel channel) {

        if (channel == null) {
            return;
        }

        if (mlChannels != null) {

            MyChannels.ChannelType chType = channel.getChannelType();
            String chTag = channel.getChannelId();

            Set<MessageChannel<?>> channels = mlChannels.get(chType);

            if (channels == null) {
                channels = new HashSet<>();
                mlChannels.put(chType, channels);
            }

            channels.add(channel);

            Map<Integer, Integer> taskTargetMap = mChannelTargetMap.get(chTag);

            if (taskTargetMap == null) {
                taskTargetMap = new HashMap<>();
                mChannelTargetMap.put(chTag, taskTargetMap);
            }
        }
    }*/

    @Override
    public void registerChannel(ChannelTransactions channel) {

        if (mlChannels != null) {
            mlChannels.add(channel);
        }
    }

    @Override
    public void unregisterChannel(ChannelTransactions channel) {

        if (mlChannels != null) {
            mlChannels.remove(channel);
        }
    }

    @Override
    public void publishMessage(MyMessage msg) {

        for (ChannelTransactions channel : mlChannels) {
            channel.onMessageReceived(msg);
        }
    }

    @Override
    public void onMessageReceived(MyMessage msg) {
    }

    protected void logMessage(String msg, String loggerTag) {

        MsgLogging loggingMsg = new MsgLogging(msg, RecordingFragment.class.getSimpleName());
        loggingMsg.setChTag(loggerTag);
        publishMessage(loggingMsg);
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

    // Types

    protected static class MyResourceIdentifier {

        private int mId;
        private int mState;

        private String mRI;

        public MyResourceIdentifier(int id, int state) {

            mId = id;
            mState = state;
        }

        public int getId() {
            return mId;
        }

        public void setId(int mId) {
            this.mId = mId;
        }

        public int getState() {
            return mState;
        }

        public void setState(int mState) {
            this.mState = mState;
        }

        public String getResourceId() {
            return mRI;
        }

        public void setResourceId(String mRI) {
            this.mRI = mRI;
        }
    }
}
