package com.dayani.m.roboplatform.utils.interfaces;

public interface LoggingChannel extends MessageChannel<Void> {

    // identify different logging channels using a unique identifier in logging messages
    int getChannelIdentifier();

    class MyLoggingMessage extends MyMessage {

        private int mLoggingIdentifier;

        public MyLoggingMessage(String msg, int identifier) {
            super(msg);
            mLoggingIdentifier = identifier;
        }

        public int getmLoggingIdentifier() { return mLoggingIdentifier; }
        public void setmLoggingIdentifier(int identifier) { mLoggingIdentifier = identifier; }
    }
}
