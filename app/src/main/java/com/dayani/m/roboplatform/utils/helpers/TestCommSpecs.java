package com.dayani.m.roboplatform.utils.helpers;

import android.os.SystemClock;
import android.util.Log;

import com.dayani.m.roboplatform.utils.interfaces.MyChannels;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class TestCommSpecs implements Runnable {

    protected static final String TAG = TestCommSpecs.class.getSimpleName();

    public enum TestMode {
        NONE,
        LATENCY,
        THROUGHPUT
    }

    protected TestMode mTestMode;

    protected final int buffLen = 64;
    protected byte[] mSendBuffer = new byte[buffLen];
    protected byte[] mReceiveBuffer = new byte[buffLen];

    protected Map<Integer, Long> mTsMap;

    protected long mnBytes = 0;
    protected double mAvgLatency = 0.0;

    @Override
    public void run() {
        if (mTestMode == TestMode.LATENCY) {
            testCommLatency();
        }
        else if (mTestMode == TestMode.THROUGHPUT) {
            testCommThroughput();
        }
        else {
            Log.d(TAG, "Test mode not supported, nothing to do");
        }
    }

    void testCommLatency() {

        // Send timestamps tags, receive, and compare
        int maxLatency = 255;
        int maxCycles = 100;
        for (int j = 0; j < maxCycles; j++) {
            for (int i = 0; i < maxLatency; i++) {

                mTsMap.put(i, SystemClock.elapsedRealtimeNanos());
                MyMessages.MyMessage msg = new MyMessages.MyMessage(MyChannels.ChannelType.DATA,
                        "", Integer.toString(i));
                send(msg);
                receiveSync();
            }
        }
    }

    void testCommThroughput() {

        fillSendBufferForThroughput();
        MyMessages.MyMessage sendMsg = new MyMessages.MyMessage(MyChannels.ChannelType.DATA,
                "", new String(mSendBuffer, StandardCharsets.US_ASCII));
        int maxThroughput = 1000;

        // Send a lot of data, time, and calc throughput
        long t0 = SystemClock.elapsedRealtimeNanos();

        for (int i = 0; i < maxThroughput; i++) {
            send(sendMsg);
            receiveSync();
            // add received bytes
        }

        long t1 = SystemClock.elapsedRealtimeNanos();
    }

    void fillSendBufferForThroughput() {

        mSendBuffer[0] = 64;
        mSendBuffer[1] = 3; // code
        for (int i = 2; i < buffLen; i++) {
            if (i % 2 == 0) {
                mSendBuffer[i] = (byte) 0x55;
            }
            else {
                mSendBuffer[i] = (byte) 0xAA;
            }
        }
    }

    public abstract void send(MyMessages.MyMessage mgs);
    public abstract void receiveSync();
    public abstract void receiveAsync();
}
