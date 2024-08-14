package com.dayani.m.roboplatform.utils.helpers;

import android.os.SystemClock;
import android.util.Log;

import com.dayani.m.roboplatform.managers.MyBaseManager;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

public abstract class TestCommSpecs implements Runnable {

    protected static final String TAG = TestCommSpecs.class.getSimpleName();

    public enum TestMode {
        NONE,
        LATENCY,
        THROUGHPUT
    }

    protected TestMode mTestMode;

    protected final int BSZ_BASE = 64;

    // should change this both here and in micro board
    protected int buffLen = 64;
    protected byte[] mSendBuffer = new byte[buffLen];

    protected Map<Integer, Long> mTsMap = new HashMap<>();

    protected long mnBytes = 0;
    protected double mAvgLatency = 0.0;
    protected long cntLatency = 0;
    protected long cntThroughput = 0;
    protected long tsDiffThroughput = 0;

    private boolean mbResReceived = false;
//    private boolean mbTestFinished = false;

    private final int maxLatency = 100;
    private final int maxCycles = 10;
    private final int maxThroughput = 1000;

    //private final WeakReference<MyBaseManager> mManager;

    public TestCommSpecs(TestMode mode) {
        mTestMode = mode;
        //mManager = new WeakReference<>(commManager);
        Log.d(TAG, "New comm test started in mode: " + mode.toString());
    }

    public TestCommSpecs(TestMode mode, int buffLen_) {
        this(mode);
        buffLen = buffLen_;
    }

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

    private void testCommLatency() {

        // Send timestamps tags, receive, and compare
        for (int j = 0; j < maxCycles; j++) {
            for (int i = 0; i < maxLatency; i++) {
                mTsMap.put(i, SystemClock.elapsedRealtimeNanos());
                mSendBuffer[0] = (byte) i;
                setReceived(false);
                send(mSendBuffer);
                waitForRes(10, 100);
            }
        }
    }

    private void testCommThroughput() {

        fillSendBufferForThroughput();

        // Send a lot of data, time, and calc throughput
        long t0 = SystemClock.elapsedRealtimeNanos();

        for (int i = 0; i < maxThroughput; i++) {
            setReceived(false);
            send(mSendBuffer);
            waitForRes(10, 100);
        }

        long t1 = SystemClock.elapsedRealtimeNanos();

        tsDiffThroughput = t1 - t0;

        double tp = mnBytes / (tsDiffThroughput * 1e-9);
        String msg = "TP test done, all received, TP is: " + tp + " (Bps)";
        Log.d(TAG, msg);
//        if (mManager != null) {
//            mManager.get().publishMessage(new MyMessages.MsgLogging(msg, "logging"));
//        }
        reportResults(msg);
    }

    public void updateState(int state) {

        if (mTestMode == TestMode.LATENCY) {
            if (mTsMap.containsKey(state)) {
                Long lastTs = mTsMap.get(state);
                if (lastTs != null) {
                    long currTs = SystemClock.elapsedRealtimeNanos();
                    long tsDiff = Math.abs(currTs - lastTs);
                    mAvgLatency += tsDiff;
                    cntLatency++;
                }
            }
            if (cntLatency >= maxLatency * maxCycles) {
                double latency = (mAvgLatency / cntLatency) * 1e-6;
                String msg = "Latency test done, all received, avg latency (ms): " + latency;
                Log.d(TAG, msg);
                reportResults(msg);
            }
        }
        else if (mTestMode == TestMode.THROUGHPUT) {
            mnBytes += state;
            cntThroughput++;
        }
        setReceived(true);
    }

    public synchronized boolean isResReceived() {
        return mbResReceived;
    }

    public synchronized void setReceived(boolean state) { mbResReceived = state; }

    /*public synchronized boolean isTestFinished() {
        return mbTestFinished;
    }

    public synchronized void setTestFinished(boolean state) { mbTestFinished = state; }*/

    private void waitForRes(int maxCnt, int delay_ms) {
        int cnt = 0;
        while(!isResReceived()) {
            try {
                Thread.sleep(delay_ms);
            }
            catch (InterruptedException e) {
                Log.d(TAG, "waitForRes, runtime exception");
                throw new RuntimeException(e);
            }
            cnt++;
            if (cnt >= maxCnt) {
                break;
            }
        }
    }

    public abstract void send(byte[] buffer);
    public abstract void reportResults(String msg);
    protected abstract void fillSendBufferForThroughput();
}
