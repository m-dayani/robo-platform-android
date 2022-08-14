package com.dayani.m.roboplatform.managers;
/*
    Deal with common manager's tasks:
        - Requirements
        - Availability
        - State & Lifecycle aware methods

    Note: By keeping a final reference to sensor groups we make sure any change
          from anywhere is accessible from other classes
 */


import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;

import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;

import java.util.List;
import java.util.Map;

public abstract class MyBaseManager {

    // Class Variables
    protected boolean mIsAvailable;

    protected ActivityResultLauncher<Intent> mActivityResultLauncher;
    protected ActivityResultLauncher<String[]> mPermsLauncher;

    protected final List<MySensorGroup> mlSensorGroup;
    protected final List<Requirement> mlRequirements;
    protected final List<String> mPermissions;

    // Construction
    public MyBaseManager(Context context) {

        mlSensorGroup = getSensorRequirements(context);
        mlRequirements = MySensorGroup.getUniqueRequirements(mlSensorGroup);
        mPermissions = MySensorGroup.getUniquePermissions(mlSensorGroup);

        //init(context);
        //checkAvailability(context);
    }

    // Availability
    public boolean isAvailable() { return mIsAvailable; }
    protected abstract void checkAvailability(Context context);
    public abstract void resolveAvailability(Context context);

    public abstract void updateState(Context context);

    // Requirements & Permissions
    public abstract List<MySensorGroup> getSensorRequirements(Context context);

    public void setIntentActivityLauncher(ActivityResultLauncher<Intent> actResLauncher) {
        mActivityResultLauncher = actResLauncher;
    }
    public void setPermissionsLauncher(ActivityResultLauncher<String[]> permsLauncher) {
        mPermsLauncher = permsLauncher;
    }

    public abstract void onActivityResult(Context context, ActivityResult result);
    public abstract void onPermissionsResult(Context context, Map<String, Boolean> permissions);

    public String[] getPermissions() { return mPermissions.toArray(new String[0]); }

    // Lifecycle & State Management
    public abstract void start();
    public abstract void stop();

    // Resources
    protected abstract void init(Context context);
    public abstract void clean();

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
