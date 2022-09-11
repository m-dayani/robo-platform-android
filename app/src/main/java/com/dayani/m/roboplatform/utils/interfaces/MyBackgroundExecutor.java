package com.dayani.m.roboplatform.utils.interfaces;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Pair;

import androidx.core.os.HandlerCompat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class MyBackgroundExecutor {

    // TODO: Later, move the multi-threading logic to a globally accessible class
    /*
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     */
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    // Instantiates the queue of Runnables as a LinkedBlockingQueue
    private final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();

    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

    // Creates a thread pool manager
//    ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
//            NUMBER_OF_CORES,       // Initial pool size
//            NUMBER_OF_CORES,       // Max pool size
//            KEEP_ALIVE_TIME,
//            KEEP_ALIVE_TIME_UNIT,
//            workQueue
//    );

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private final Handler mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper());

    private HandlerThread workerThread;
    private Handler workerHandler;

    public Executor getBackgroundExecutor() { return executorService; }
    public Handler getBackgroundHandler() { return workerHandler; }
    public Handler getUiHandler() { return mainThreadHandler; }

    public void execute(Runnable r) { executorService.execute(r); }
    public void handle(Runnable r) {
        if (workerHandler != null) {
            workerHandler.post(r);
        }
    }

    public void initWorkerThread(String tag) {

        workerThread = new HandlerThread(tag);
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    public void cleanWorkerThread() {

        if (workerThread == null) {
            return;
        }

        workerThread.quitSafely();
        try {
            workerThread.join();
            workerThread = null;
            workerHandler = null;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static Pair<HandlerThread, Handler> startThread(String tag) {

        HandlerThread mBackgroundThread = new HandlerThread(tag);
        mBackgroundThread.start();
        Handler mHandler = new Handler(mBackgroundThread.getLooper());

        return new Pair<>(mBackgroundThread, mHandler);
    }

    public static boolean stopThread(HandlerThread mBackgroundThread) {

        if (mBackgroundThread == null) {
            return false;
        }

        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            return true;
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public interface JobListener {

        public Executor getBackgroundExecutor();
        public Handler getBackgroundHandler();
        public Handler getUiHandler();

        public void execute(Runnable r);
        public void handle(Runnable r);
    }
}
