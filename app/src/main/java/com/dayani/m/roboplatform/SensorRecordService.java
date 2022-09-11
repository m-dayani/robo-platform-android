package com.dayani.m.roboplatform;

/*
    TODO: Don't use a stringBuffer for
    sensor logging and a fileLogger for gnss!
    Fix this.
 */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.dayani.m.roboplatform.dump.MainActivity_old;
import com.dayani.m.roboplatform.managers.MyLocationManager;
import com.dayani.m.roboplatform.managers.MySensorManager;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.managers.MyStorageManager;
//import com.dayani.m.roboplatform.utils.android_gnss.GnssContainer;

import java.io.File;

import static com.dayani.m.roboplatform.dump.RecordSensorsActivity_old.KEY_IS_RECORDING_STATE;

public class SensorRecordService extends Service {

    private static final String TAG = SensorRecordService.class.getSimpleName();

    public static final String CHANNEL_ID = "SensorRecordServiceChannelId";

    private static final String SENSOR_FILE_BASE_PATH = "/DCIM/Files";
    private static final String SENSOR_FILE_BASE_NAME = "sensor_";

    private boolean mIsRecording = false;

    private MyStorageManager mStore;
    private File mSensorFile;
    private String mTimePrefix;
    private StringBuffer mSensorString;

    private MyLocationManager mLocation = null;

//    private UiLogger mUiLogger;
//    private FileLogger mFileLogger;
//    private GnssContainer mGnss = null;

    private MySensorManager mSensorManager;

    private HandlerThread mSensorThread;
    private Looper mSensorLooper;
    private Handler mSensorWriteHandler;

    private Runnable sensorWriteTask = new Runnable() {

        @Override
        public void run() {
            Log.d(TAG, "Writing sensor string file in background...");

            //Store sensor values
            //devise a unified optimum method for setting and
            //updating compatible video & sensor file names
            synchronized (this) {
//                mStore.writeBuffered(
//                        new File(MyStorageManager.getNextFilePath(
//                                mSensorFile.getAbsolutePath(),mTimePrefix,"txt")),
//                        mSensorString.toString());
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        this.init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String input = intent.getStringExtra("inputExtra");
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity_old.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Record Service")
                .setContentText(input)
                .setSmallIcon(R.drawable.ic_action_info)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        startRecording();

        //stopSelf();

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopRecording();
        this.clean();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void init() {
        mStore = new MyStorageManager(this);

        //mSensorFile = mStore.getPublicInternalFile(SENSOR_FILE_BASE_PATH, SENSOR_FILE_BASE_NAME);
        mTimePrefix = MyStorageManager.getTimePrefix();
        mSensorString = new StringBuffer();

        //mUiLogger = new UiLogger();
        //mUiLogger.setUiFragmentComponent(new UiLogger.UIFragmentComponent(this, reportTxt, frag));
        //mFileLogger = new FileLogger(getApplicationContext());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //function calls in this class requires API 24 or above
            //mGnss = new GnssContainer(this, mFileLogger, mUiLogger);
        }
        else {
            //mLocation = new MyLocationManager(this, mSensorString);
        }

        mSensorManager = new MySensorManager(this);
        //null, SensorManager.SENSOR_DELAY_FASTEST);

        startSensorThread();
    }

    private void clean() {
        stopSensorThread();
        this.cleanState();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startSensorThread() {

        mSensorThread = new HandlerThread(TAG+"SensorBackground");
        mSensorThread.start();
        mSensorLooper = mSensorThread.getLooper();
        mSensorWriteHandler = new Handler(mSensorLooper);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopSensorThread() {

        mSensorThread.quitSafely();
        try {
            mSensorThread.join();
            mSensorThread = null;
            mSensorWriteHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        //startSensorThread();
        //Keep the device screen on while recording
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //Registers sensor callbacks
        mSensorManager.start(this);
        //mUsb.startPeriodicSensorPoll();
        //while we're not saving files, use our handlerThread for location updates
        if (mLocation != null) {
            //mLocation.startLocationUpdates(mSensorLooper);
        }
//        if (mGnss != null) {
//            mFileLogger.startNewLog();
//            //mGnss.registerLocation();
//        }
        //mCam.startRecordingVideo();
    }

    private void stopRecording() {
        //stopSensorThread();
        //Allow device screen to be turned off
        //getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager.stop(this);
        //mUsb.stopPeriodicSensorPoll();
        if (mLocation != null) {
            //mLocation.stopLocationUpdates(context);
        }
//        if (mGnss != null) {
//            ///mGnss.unregisterLocation();
//            mFileLogger.stopLog();
//        }
        //mCam.stopRecordingVideo();
        //Store sensor values
        //devise a unified optimum method for setting and
        //updating compatible video & sensor file names
        //mStore.writeBuffered(new File(getNextSensorFilePath(mTimePerfix)),
        //mSensorString.toString());
        //TODO: or use an asyncTask instead
        mSensorWriteHandler.post(sensorWriteTask);
        //preparing the next file
        mTimePrefix = MyStorageManager.getTimePrefix();
        // UI
        //updateButtonState(mIsRecording);
    }

    //In case the user stops this service from notification
    //remember to clean mIsRecording state in main activity
    private void cleanState() {
        MyStateManager.setBoolPref(this, KEY_IS_RECORDING_STATE, false);
    }
}
