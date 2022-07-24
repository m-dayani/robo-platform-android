package com.dayani.m.roboplatform.utils;

/*
    This is only used as an indicator that an Activity
    uses bunch of predefined static methods.
    In current version of program there is no way to
    define static interface.
    TODO: it might be possible to fix this later.
 */

import android.os.Parcel;
import android.os.Parcelable;

public interface ActivityRequirements {

    //Or go with enums but very hard for later use.
    enum Requirement implements Parcelable {

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

    enum RequirementState {
        PERMITTED,
        PENDING,
        DISABLED
    }

    class RequirementItem {
        Requirement requirement;
        RequirementState state; //permitted, pending, disabled, ...
        public int id;
        public String name;

        public RequirementItem(Requirement requirement, RequirementState state, int id, String name) {
            this.requirement = requirement;
            this.state = state;
            this.id = id;
            this.name = name;
        }
    }

    public SensorsContainer getSensorRequirements();
}
