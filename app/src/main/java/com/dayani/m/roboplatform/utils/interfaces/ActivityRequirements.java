package com.dayani.m.roboplatform.utils.interfaces;

/*
    This is only used as an indicator that an Activity
    uses bunch of predefined static methods.
    In current version of program there is no way to
    define static interface.
    TODO: it might be possible to fix this later.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.activity.result.IntentSenderRequest;
import androidx.fragment.app.Fragment;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.utils.AppGlobals;

import java.util.List;
import java.util.Map;

public class ActivityRequirements {

    public static final String KEY_REQUIREMENT_PASSED = AppGlobals.PACKAGE_BASE_NAME +
            "key-requirement-passed";
    public static final String KEY_REQUIREMENT_PASSED_REQUEST = AppGlobals.PACKAGE_BASE_NAME +
            "key-requirement-passed-request";

    //Or go with enums but very hard for later use.
    public enum Requirement implements Parcelable {

        BASE_PATH_WRITABLE,
        PERMISSIONS,
        USB_DEVICE,
        WIRELESS_CONNECTION,
        ENABLE_LOCATION,
        ALL_SENSORS;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(ordinal());
        }

        public static final Parcelable.Creator<Requirement> CREATOR
                = new Parcelable.Creator<Requirement>() {
            public Requirement createFromParcel(Parcel in) {
                return Requirement.values()[in.readInt()];
            }

            public Requirement[] newArray(int size) {
                return new Requirement[size];
            }
        };
    }

    public enum RequirementState {
        PERMITTED,
        PENDING,
        //DISABLED
    }

    public static class RequirementItem {
        Requirement requirement;
        public RequirementState state; //permitted, pending, disabled, ...
        public int id;
        public String name;

        public RequirementItem(Requirement requirement, RequirementState state, int id, String name) {
            this.requirement = requirement;
            this.state = state;
            this.id = id;
            this.name = name;
        }
    }

    //boolean usesActivityRequirementsInterface();

    public interface RequirementResolution {

        void requestResolution(String[] perms);
        void requestResolution(int requestCode, Intent activityIntent);
        void requestResolution(int requestCode, IntentSenderRequest resolutionIntent);
        void requestResolution(Fragment targetFragment);
    }

    public interface OnRequirementResolved {

        void onAvailabilityStateChanged(MyBaseManager manager);
    }

    public interface HandlePermissionRequirement {

        List<String> getPermissions();

        boolean hasAllPermissions();
        void updatePermissionsState(Context context);

        // respond permissions
        // Nothing needs to be done! (because if permitted hasAllPermissions yields true)
        void onPermissionsResult(Context context, Map<String, Boolean> permissions);
        // request permissions
        void resolvePermissions();
    }

    public interface HandleEnableSettingsRequirement {

        boolean isSettingsEnabled();
        // listen for changes in the state of resources (enabled state)
        void onSettingsChanged(Context context, Intent intent);
        void enableSettingsRequirement(Context context); // resolve
        void updateSettingsEnabled();
    }

    // to listen for requirement changes
    public interface HandleBroadcastReceivers {

        void registerBrReceivers(Context context, MyBaseManager.LifeCycleState state);
    }

    public interface ConnectivityTest {
        void handleTestSynchronous(MyMessages.MyMessage msg);
        void handleTestAsynchronous(MyMessages.MyMessage msg);
        boolean passedConnectivityTest();
    }

    public static class ManagerRequirementBroadcastReceiver extends BroadcastReceiver {

        private final MyBaseManager mManager;

        public ManagerRequirementBroadcastReceiver(MyBaseManager manager) {

            mManager = manager;
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            if (mManager instanceof HandleEnableSettingsRequirement) {
                ((HandleEnableSettingsRequirement) mManager).onSettingsChanged(context, intent);
            }
        }
    }
}
