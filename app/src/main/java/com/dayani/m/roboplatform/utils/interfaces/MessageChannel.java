package com.dayani.m.roboplatform.utils.interfaces;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public interface MessageChannel <T> {

    int openNewChannel(Context context, T channelInfo);
    void closeChannel(int id);
    void publishMessage(int id, MyMessage msg);
    void resetChannel(int id);

    class MyMessage implements Parcelable {

        private final String mStringMsg;

        //public MyMessage() { mStringMsg = ""; }

        private MyMessage(Parcel in) {
            mStringMsg = in.readString();
        }

        public MyMessage(String msg) {
            mStringMsg = msg;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeString(this.toString());
        }

        public static final Parcelable.Creator<MyMessage> CREATOR
                = new Parcelable.Creator<MyMessage>() {
            public MyMessage createFromParcel(Parcel in) {
                return new MyMessage(in);
            }

            public MyMessage[] newArray(int size) {
                return new MyMessage[size];
            }
        };

        @NonNull
        @Override
        public String toString() {
            return mStringMsg;
        }

    }
}
