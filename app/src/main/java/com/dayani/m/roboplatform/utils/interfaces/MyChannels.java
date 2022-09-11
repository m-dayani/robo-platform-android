package com.dayani.m.roboplatform.utils.interfaces;


public interface MyChannels {

    enum ChannelType {
        UNKNOWN,
        CONFIGURATION,
        DATA,
        LOGGING,
        STORAGE
    }

    interface ChannelTransactions {

        void registerChannel(ChannelTransactions channel);
        void unregisterChannel(ChannelTransactions channel);

        void publishMessage(MyMessages.MyMessage msg);
        void onMessageReceived(MyMessages.MyMessage msg);
    }
}
