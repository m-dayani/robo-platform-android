package com.dayani.m.roboplatform.managers;

/*
 *
 * Note1: We have a file object here because we need to set it in MediaRecorder.
 *
 * ** Availability:
 *      1. Easy: Camera & Microphone permissions
 * ** Resources:
 *      1. Internal HandlerThread
 *      2. Camera & Microphone
 *      3. Related callbacks/objects
 * ** State Management:
 *      1. isAvailable (availability)
 *      2. isRecording
 *
 * ** Basic Operation:
 *      1. handle availability
 *      2. select sensors & configure (output format, ...)
 *      3. open camera with preview (if desired) -> Auto 3A, disable distortion correction
 *      4. when recording starts, lock most configurations (3A)
 *          and record images in the simplest form
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.utils.cutom_views.AutoFitTextureView;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


public class CameraFlyVideo extends MyBaseManager {

    /* ===================================== Variables ========================================== */

    private static final String TAG = CameraFlyVideo.class.getSimpleName();

    private static final int ANDROID_INTRINSIC_PARAMS_VERSION = Build.VERSION_CODES.M;
    private static final int ANDROID_DISTORTION_PARAMS_VERSION = Build.VERSION_CODES.P;
    private static final int ANDROID_ADVANCED_CAM_FEATURES_VERSION = Build.VERSION_CODES.P;
//    private static final int ANDROID_OUTPUT_CONFIG_VERSION = Build.VERSION_CODES.N;

    private static final String PATH_BASE_CAMERA = "cam";
    private static final String PATH_BASE_IMAGES = "image";
    private static final String IMAGES_FILE_NAME = "images.txt";
    private static final String DEF_IMAGE_FILE_EXTENSION = ".jpg";
    private static final String DEF_IMAGE_RAW_EXTENSION = ".dng";

    private static final Size SIZE_480P = new Size(640, 480);
    private static final Size SIZE_1080P = new Size(1920, 1080);
    // TODO: add these parameters to preferences
    private static final Size DEF_IM_READER_SIZE = SIZE_480P;
//    private static final Size DEF_PREVIEW_SIZE = SIZE_480P;
    private static final Size MAX_PREVIEW_SIZE = SIZE_1080P;

    private static final int FRAME_BUFF_COUNT = 3;

    /**
     * Timeout for the pre-capture sequence.
     */
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;

    private static final boolean supportsMultiCamApi =
            Build.VERSION.SDK_INT >= ANDROID_ADVANCED_CAM_FEATURES_VERSION;

    /* ------------------------------------ Multithreading -------------------------------------- */

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private final Object mCameraStateLock = new Object();

    /* --------------------------------------- Preview ------------------------------------------ */

    public enum PreviewConfigState {
        NONE,
        AVAILABLE,
        CHANGED,
        DESTROYED,
        UPDATED,
        DETECT_180
    }

    public enum PreviewState {
        NONE,               // no preview
        SIMPLE,             // automatic control
        PRECAPTURE_TRAINING // continuously trigger & lock 3A until capture begins
    }

    /**
     * The {@link android.util.Size} of camera preview.
     */
    //private Size mPreviewSize;

    // preview configuration state
    private PreviewConfigState mPreviewConfigState = PreviewConfigState.NONE;

    // different modes of preview
    private PreviewState mPreviewState = PreviewState.PRECAPTURE_TRAINING;

    /* --------------------------------------- Camera ------------------------------------------- */

    private enum CameraState {
        CLOSED,
        OPENED,
        PREVIEW,
        WAITING_FOR_3A_CONVERGENCE,
        LOCKED_RUNNING
    }

    private final CameraManager mCamManager;

    private CameraState mCamState = CameraState.CLOSED;

    // should be thread safe -----------------------------------------------------------------------
    /**
     * A reference to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mCaptureSession;

    private CaptureRequest.Builder mPreviewRequest;
    private CaptureRequest.Builder mCaptureRequest;
    // should be thread safe -----------------------------------------------------------------------

    private CameraGroup mSelectedCamGroup;

    /**
     * Whether or not the currently configured camera device is fixed-focus.
     */
    private boolean mNoAFRun = false;

    /**
     * Timer to use with pre-capture sequence to ensure a timely capture if 3A convergence is
     * taking too long.
     */
    private long mCaptureTimer;

    // control image output format
    private int mCaptureFormat = ImageFormat.JPEG;

    // for debugging
    AtomicInteger mCounter = new AtomicInteger();

    private static int mCamIdGenerator = 0;

    private boolean mbIsFirstCapture = true;

    private CaptureResult mLastCaptureResult;

    /* -------------------------------------- Callbacks ----------------------------------------- */

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private final CameraDevice.StateCallback mStateCallback;

    private final CameraCaptureSession.StateCallback mCaptureSessionCallback;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback;

    private final CameraCaptureSession.CaptureCallback mPreCaptureCallback;

    /* ==================================== Construction ======================================== */

    public CameraFlyVideo(Context context) {

        super(context);

        mCamManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        mStateCallback = getCameraStateCallback();
        mCaptureSessionCallback = getCaptureSessionCallback();
        mPreCaptureCallback = getPreCaptureCallback();
        mCaptureCallback = getCaptureCallback();
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    @Override
    protected boolean resolveSupport(Context context) {

        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    @Override
    protected List<Requirement> getRequirements() {
        return Collections.singletonList(Requirement.PERMISSIONS);
    }

    @Override
    protected List<String> getPermissions() {
        return Collections.singletonList(Manifest.permission.CAMERA);
    }

    /* ------------------------------------ Checked State --------------------------------------- */

    @Override
    public void onCheckedChanged(Context context, int grpId, int sensorId, boolean state) {

        if (!isAvailable()) {
            resolveAvailability(context);
            return;
        }

        MySensorInfo selectedSensor = getSensorInfo(grpId, sensorId);
        if (!(selectedSensor instanceof CameraSensor)) {
            return;
        }

        CameraSensor selectedCamera = (CameraSensor) selectedSensor;
        String logicalCamera = selectedCamera.getLogicalCamera();

        // cameras can be selected at the same time only from one logical group
        for (MySensorGroup sensorGroup : mlSensorGroup) {
            for (MySensorInfo camera : sensorGroup.getSensors()) {

                if (camera instanceof CameraSensor) {

                    CameraSensor currCamera = (CameraSensor) camera;

                    if (!logicalCamera.equals(currCamera.getLogicalCamera())) {
                        // deselect all cameras outside this group
                        currCamera.setChecked(false);
                    }
                }
            }
        }

        selectedCamera.setChecked(state);
    }

    /* --------------------------------- Lifecycle Management ----------------------------------- */

    @Override
    public void init(Context context) {

        super.init(context);
    }

    @Override
    public void clean(Context context) {

        super.clean(context);

        // Also close camera if somewhere opened
        closeCamera();
    }

    @Override
    public void initConfigurations(Context context) {

        // ignore if not available
        if (!this.isAvailableAndChecked()) {
            Log.w(TAG, "Cameras are not available, abort");
            return;
        }

        // detect selected sensors & config. ImageReaders
        configureCameraSensorsAndOutputStreams();

        // open camera (can come first because doesn't depend on anything)
        // it internally creates a capture session, with or without a preview
        openCamera(context);
    }

    @Override
    public void cleanConfigurations(Context context) {

        // ignore if not available
        if (!this.isAvailableAndChecked()) {
            Log.w(TAG, "Cameras are not available, abort");
            return;
        }

        // close camera session
        // close camera
        // close surfaces (ImageReader)
        closeCamera();
    }

    @Override
    public void start(Context context) {

        if (!this.isAvailableAndChecked()) {
            Log.w(TAG, "Cameras are not available, abort");
            return;
        }

        super.start(context);
        openStorageChannels();
        startPreviewAndCaptureLoop();
    }

    @Override
    public void stop(Context context) {

        if (!this.isAvailableAndChecked() || !this.isProcessing()) {
            Log.d(TAG, "Camera Sensors are not running");
            return;
        }

        // call first to stop the process (isProcessing)
        super.stop(context);
        stopPreviewAndCaptureLoop();
        closeStorageChannels();
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    protected String getResourceId(MyResourceIdentifier resId) {

        String prefixId = "Cam";
        int sensorId = resId.getId();
        int resState = resId.getState();

        if (resState == 0) {
            // text file containing <timestamp, image name> pairs
            return prefixId + sensorId + "_Image_File";
        }
        else if (resState == 1) {
            // image directory
            return prefixId + sensorId + "_Image_Dir";
        }
        else {
            return null;
        }
    }

    @Override
    protected List<Pair<String, MsgConfig>> getStorageConfigMessages(MySensorInfo sensor) {

        // for each camera sensor we have two resources:
        // image info text file and image directory

        List<Pair<String, MsgConfig>> lConfigMsgPairs = new ArrayList<>();

        int sensorId = sensor.getId();

        List<String> camFolders = Collections.singletonList(PATH_BASE_CAMERA + sensorId);

        MsgConfig.ConfigAction configAction = MsgConfig.ConfigAction.OPEN;

        // add images.txt file
        MyResourceIdentifier resId = new MyResourceIdentifier(sensorId, 0);
        StorageInfo.StreamType ss = StorageInfo.StreamType.STREAM_STRING;
        StorageInfo storageInfo = new StorageInfo(camFolders, IMAGES_FILE_NAME, ss);
        StorageConfig storageConfig = new StorageConfig(configAction, TAG, storageInfo);
        storageConfig.setStringMessage(MyMessages.MsgImage.getHeaderMessage());

        lConfigMsgPairs.add(new Pair<>(getResourceId(resId), storageConfig));

        // add images directory
        resId.setState(1);
        ss = StorageInfo.StreamType.TRAIN_BYTE;
        List<String> extCamFolders = Arrays.asList(camFolders.get(0), PATH_BASE_IMAGES);
        storageInfo = new StorageInfo(extCamFolders, "", ss);
        storageConfig = new StorageConfig(configAction, TAG, storageInfo);

        lConfigMsgPairs.add(new Pair<>(getResourceId(resId), storageConfig));

        return lConfigMsgPairs;
    }

    /* ====================================== Camera2 =========================================== */

    private CameraManager getCameraManager() {
        return mCamManager;
    }

    synchronized public void setImageOutputFormat(int format) { mCaptureFormat = format; }
    synchronized private int getImageOutputFormat() { return mCaptureFormat; }

    synchronized public void setPreviewCaptureState(PreviewState state) { mPreviewState = state; }
    synchronized private boolean isPreviewCaptureState(PreviewState state) {
        return state == mPreviewState;
    }

    synchronized private void setPreviewConfigState(PreviewConfigState state) { mPreviewConfigState = state; }
    synchronized private boolean isPreviewConfigState(PreviewConfigState state) {
        return state == mPreviewConfigState;
    }
    private boolean isPreviewAvailable() {
        return !(isPreviewConfigState(PreviewConfigState.NONE) ||
                isPreviewConfigState(PreviewConfigState.DESTROYED));
    }

    synchronized private void setCameraState(CameraState state) { mCamState = state; }
    synchronized private boolean isCameraState(CameraState state) {
        return mCamState == state;
    }

    synchronized private void setNoAFRun(boolean state) { mNoAFRun = state; }
    synchronized private boolean isAFRun() { return !mNoAFRun; }

    /* ----------------------------------- Init. Callbacks -------------------------------------- */

    private CameraCaptureSession.StateCallback getCaptureSessionCallback() {
        return new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {

                synchronized (mCameraStateLock) {
                    mCaptureSession = session;
                    //Log.i(TAG, "Camera session configured successfully, "+mCounter.get());
                }
                startPreview();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.w(TAG, "Capture session configuration failed");
            }
        };
    }

    private CameraDevice.StateCallback getCameraStateCallback() {

        return new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {

                synchronized (mCameraStateLock) {
                    mCameraOpenCloseLock.release();
                    mCameraDevice = cameraDevice;
                    setCameraState(CameraState.OPENED);
                    Log.v(TAG, "Calling initCameraSession from CameraDevice.StateCallback::onOpened");
                    //mCounter.set(34);
                }
                initCameraSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {

                synchronized (mCameraStateLock) {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                    setCameraState(CameraState.CLOSED);
                }
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {

                synchronized (mCameraStateLock) {
                    mCameraOpenCloseLock.release();
                    cameraDevice.close();
                    mCameraDevice = null;
                    setCameraState(CameraState.CLOSED);
                }
            }
        };
    }

    private ImageReader.OnImageAvailableListener getOnImAvailableCallback(int sensorId) {

        return imageReader -> doInBackground(() -> {
            Image image = imageReader.acquireNextImage();
            processCapturedImage(sensorId, image);
        });
    }

    private CameraCaptureSession.CaptureCallback getPreCaptureCallback() {

        return new CameraCaptureSession.CaptureCallback() {

            private void process(CaptureResult result) {

                if (isCameraState(CameraState.WAITING_FOR_3A_CONVERGENCE)) {

                    boolean readyToCapture = true;
                    if (isAFRun()) {
                        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (afState == null) {
                            return;
                        }

                        // If auto-focus has reached locked state, we are ready to capture
                        readyToCapture =
                                (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                    }

                    // If we are running on an non-legacy device, we should also wait until
                    // auto-exposure and auto-white-balance have converged as well before
                    // taking a picture.
                    if (!isLegacyLocked()) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
                        if (aeState == null || awbState == null) {
                            return;
                        }

                        readyToCapture = readyToCapture &&
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                                awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED;
                    }

                    // If we haven't finished the pre-capture sequence but have hit our maximum
                    // wait timeout, too bad! Begin capture anyway.
                    if (!readyToCapture && hitTimeoutLocked()) {
                        Log.w(TAG, "Timed out waiting for pre-capture sequence to complete.");
                        readyToCapture = true;
                    }

                    if (readyToCapture) {
                        doInBackground(() -> runCaptureLoop());
                    }
                }
            }

            @Override
            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureResult partialResult) {
                process(partialResult);
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                           TotalCaptureResult result) {
                process(result);
            }
        };
    }

    private CameraCaptureSession.CaptureCallback getCaptureCallback() {

        return new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                         long timestamp, long frameNumber) {

                // configure?
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                           TotalCaptureResult result) {

                // setup messages (e.g. with capture result and ...)
                mLastCaptureResult = result;

                if (mbIsFirstCapture) {
                    lockPrecaptureRequest();
                    mbIsFirstCapture = false;
                }
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureFailure failure) {

                // mLastCaptureResult = null;
            }
        };
    }

    /* --------------------------------- Init. Camera Sensors ----------------------------------- */

    public List<String> filterCompatibleCameras(CameraManager cameraManager, String[] cameraIds) {

        final List<String> compatibleCameras = new ArrayList<>();

        try {
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                for (int capability : capabilities) {
                    if (capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {
                        compatibleCameras.add(id);
                    }
                    else {
                        Log.d(TAG, "found an incompatible camera: " + id);
                    }
                }
            }
        }
        catch (CameraAccessException e) {
            Log.e(TAG, "filterCompatibleCameras: " + e.getMessage());
        }

        return compatibleCameras;
    }

    public List<CameraGroup> getCameraGroups(CameraManager cameraManager, List<String> cameraIdList) {

        List<CameraGroup> cameraGroups = new ArrayList<>();

        if (cameraManager == null || cameraIdList == null) {
            return cameraGroups;
        }

        Set<String> processedCameras = new HashSet<>();

        for (String cameraId : cameraIdList) {

            if (processedCameras.contains(cameraId)) {
                continue;
            }
            processedCameras.add(cameraId);

            CameraCharacteristics characteristics;
            try {
                characteristics = cameraManager.getCameraCharacteristics(cameraId);
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
                continue;
            }

            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            CameraCharacteristics logicalCamCharacteristics = characteristics;
            Set<CameraSensor> physicalCameras;

            if (supportsMultiCamApi && contains(capabilities,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)) {

                // multi-camera
                Set<String> physicalCams = characteristics.getPhysicalCameraIds();
                processedCameras.addAll(physicalCams);
                physicalCameras = getCameras(cameraManager, physicalCams, cameraId);
            }
            else {
                // regular cameras
                physicalCameras = getCameras(cameraManager,
                        new HashSet<>(Collections.singletonList(cameraId)), null);
            }

            CameraGroup cameraGroup =
                    new CameraGroup(cameraId, logicalCamCharacteristics, physicalCameras);
            cameraGroups.add(cameraGroup);
        }

        return cameraGroups;
    }

    private Set<CameraSensor> getCameras(CameraManager cameraManager, Set<String> cameraIds, String logicalCamId) {

        Set<CameraSensor> cameras = new HashSet<>();

        if (cameraManager == null) {
            return cameras;
        }

        // iterate over available camera devices
        for (String cameraId : cameraIds) {

            CameraCharacteristics characteristics = getCameraCharacteristics(cameraManager, cameraId);
            if (characteristics == null) {
                continue;
            }

            int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            int sensorId = mCamIdGenerator++;
            String sensorName = "Camera " + sensorId;
            String strSensorId = String.format(Locale.US, "%d", sensorId);

            String version = "NA";
            if (Build.VERSION.SDK_INT >= ANDROID_ADVANCED_CAM_FEATURES_VERSION) {
                version = characteristics.get(CameraCharacteristics.INFO_VERSION);
            }

            String lensFacing;
            switch (characteristics.get(CameraCharacteristics.LENS_FACING)) {
                case CameraMetadata.LENS_FACING_BACK:
                    lensFacing = "Back";
                    break;
                case CameraMetadata.LENS_FACING_FRONT:
                    lensFacing = "Front";
                    break;
                case CameraMetadata.LENS_FACING_EXTERNAL:
                    lensFacing = "External";
                    break;
                default:
                    lensFacing = "NA";
                    break;
            }

            String hwLevel;
            switch (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    hwLevel = "Legacy";
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    hwLevel = "Limited";
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                    hwLevel = "Full";
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                    hwLevel = "Level-3";
                    break;
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL:
                    hwLevel = "External";
                    break;
                default:
                    hwLevel = "NA";
                    break;
            }

            // supports raw format?
            boolean supportsRaw = false;
            for (int capability : capabilities) {
                if (capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) {
                    supportsRaw = true;
                    break;
                }
            }

            // timestamp source
            boolean realtimeTsSource = characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
                    CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;

            // sensor & pixel info
            Rect activeArrSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            Size pixelArrSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);

            // output frame duration
            long maxFrDuration = 0;
            try {
                maxFrDuration = characteristics.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
            }
            catch (NullPointerException e) {
                e.printStackTrace();
            }

            // focal length
            float[] focalLengthRanges = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

            // intrinsics
            float[] intrinsics = {};
            if (Build.VERSION.SDK_INT >= ANDROID_INTRINSIC_PARAMS_VERSION) {
                intrinsics = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
            }

            // distortion
            float[] distortion = {};
            if (Build.VERSION.SDK_INT >= ANDROID_DISTORTION_PARAMS_VERSION) {
                distortion = characteristics.get(CameraCharacteristics.LENS_DISTORTION);
            }

            // TODO: also add the transformation between multiple cameras

            // description info
            Map<String, String> descInfo = new HashMap<>();
            descInfo.put("Name", sensorName);
            descInfo.put("Android_Id", cameraId);
            descInfo.put("App_Id", strSensorId);
            descInfo.put("Version", (version == null) ? "NA" : version);
            descInfo.put("Hardware_Level", hwLevel);
            descInfo.put("Lens_Facing", lensFacing);
            descInfo.put("Supports_Raw", (supportsRaw) ? "Yes" : "No");
            descInfo.put("Supports_MultiCam", (logicalCamId != null) ? "Yes" : "No");
            descInfo.put("Timestamp_Source", (realtimeTsSource) ? "Real-time" : "NA");

            // calibration info
            Map<String, String> calibInfo = new HashMap<>();
            calibInfo.put("App_Id", strSensorId);
            String strPhysicalSz = "[" + physicalSize.getWidth() + ", " + physicalSize.getHeight() + "]";
            calibInfo.put("Physical_Size_mm", strPhysicalSz);
            String strActArrSz = "[" + activeArrSize.top + ", " + activeArrSize.left +
                    ", " + activeArrSize.width() + ", " + activeArrSize.height() + "]";
            calibInfo.put("Active_Array_Rect_px", strActArrSz);
            String strPxSz = "[" + pixelArrSize.getWidth() + ", " + pixelArrSize.getHeight() + "]";
            calibInfo.put("Pixel_Array_Size_px", strPxSz);
            calibInfo.put("Max_Frame_Duration_ns", String.format(Locale.US, "%d", maxFrDuration));
            long minFps = (maxFrDuration == 0) ? 0 : Math.round(1e9 / (double) maxFrDuration);
            calibInfo.put("Min_FPS_Hz", String.format(Locale.US, "%d", minFps));
            calibInfo.put("Focal_Lengths_mm", Arrays.toString(focalLengthRanges));
            calibInfo.put("Intrinsics", (intrinsics == null) ? "[]" : Arrays.toString(intrinsics));
            calibInfo.put("Distortion", (distortion == null) ? "[]" : Arrays.toString(distortion));

            String logicalCam = (logicalCamId == null) ? cameraId : logicalCamId;
            CameraSensor sensorInfo = new CameraSensor(sensorId, sensorName, logicalCam, characteristics);
            sensorInfo.setDescInfo(descInfo);
            sensorInfo.setCalibInfo(calibInfo);
            sensorInfo.setAndroidId(cameraId);
            sensorInfo.setChecked(false);

            cameras.add(sensorInfo);
        }

        return cameras;
    }

    @Override
    public List<MySensorGroup> getSensorGroups(Context context) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        List<MySensorGroup> sensorGroups = new ArrayList<>();

        // add sensors:
        List<String> compatCameras;
        try {
            String[] iniCameraIds = cameraManager.getCameraIdList();
            Log.v(TAG, "Has " + iniCameraIds.length + " total cameras");
            compatCameras = filterCompatibleCameras(cameraManager, iniCameraIds);
            Log.v(TAG, "Has " + compatCameras.size() + " compatible cameras");
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
            return sensorGroups;
        }

        List<CameraGroup> cameraGroups = getCameraGroups(cameraManager, compatCameras);
        Log.v(TAG, "Has " + cameraGroups.size() + " logical cameras");

        List<MySensorInfo> sensors = new ArrayList<>();
        for (CameraGroup cameraGroup : cameraGroups) {
            sensors.addAll(cameraGroup.getPhysicalCameras());
        }
        Log.v(TAG, "Has " + sensors.size() + " camera sensors");

        // select the first physical sensor
        if (sensors.size() > 0 && sensors.get(0) != null) {
            sensors.get(0).setChecked(true);
        }

        int currGrpId = MySensorGroup.getNextGlobalId();
        sensorGroups.add(new MySensorGroup(currGrpId, MySensorGroup.SensorType.TYPE_CAMERA, "Camera", sensors));

        return sensorGroups;
    }

    private CameraGroup getSelectedCameraGroup() {

        if (mlSensorGroup == null || mlSensorGroup.isEmpty()) {
            return null;
        }

        String logicalCam = null;
        Set<CameraSensor> physicalCams = new HashSet<>();

        for (MySensorGroup sensorGroup : mlSensorGroup) {

            if (sensorGroup == null) {
                continue;
            }

            for (MySensorInfo sensorInfo : sensorGroup.getSensors()) {

                if (!(sensorInfo instanceof CameraSensor) || !sensorInfo.isChecked()) {
                    continue;
                }

                CameraSensor camera = (CameraSensor) sensorInfo;

                String currLogicalId = camera.getLogicalCamera();

                if (logicalCam == null) {
                    logicalCam = currLogicalId;
                }
                else if (!logicalCam.equals(currLogicalId)) {
                    Log.w(TAG, "Detected physical cameras with different logical IDs");
                }

                physicalCams.add(camera);
            }
        }

        CameraCharacteristics logicalCamChars = getCameraCharacteristics(getCameraManager(), logicalCam);

        return new CameraGroup(logicalCam, logicalCamChars, physicalCams);
    }

    private boolean initSelectedCameras() {

        mSelectedCamGroup = getSelectedCameraGroup();
        return mSelectedCamGroup != null;
    }

    /* --------------------------------- Configure Resources ------------------------------------ */

    private ImageReader getImReaderSurface(CameraSensor camera) {

        int outputFormat = mCaptureFormat;

        CameraCharacteristics characteristics = camera.getCharacteristics();
        Size outputSize = SizeSelector.getSurfaceSize(characteristics, outputFormat, DEF_IM_READER_SIZE);

        if (outputSize == null) {
            return null;
        }

        // for image reader
        ImageReader imageReader = ImageReader.newInstance(outputSize.getWidth(), outputSize.getHeight(),
                outputFormat, FRAME_BUFF_COUNT);
        imageReader.setOnImageAvailableListener(getOnImAvailableCallback(camera.getId()), null);

        return imageReader;
    }

    /**
     * Configure the selected cameras and output streams (ImageReaders)
     * @param context activity context (for camera manager)
     */
    @SuppressLint("NewApi")
    public void configureCameraSensorsAndOutputStreams() {

        if (mSelectedCamGroup == null) {
            if (!initSelectedCameras()) {
                Log.w(TAG, "ImageCaptureConfig: cannot get selected cameras");
                return;
            }
        }

        // Prepare surfaces & setup configurations
        // Physical cameras
        List<OutputConfiguration> lAllConfigurations = new ArrayList<>();
        List<Surface> lPhysicalSurfaces = new ArrayList<>();
        List<ImageReader> lImageReaders = new ArrayList<>();

        for (CameraSensor camSensor : mSelectedCamGroup.getPhysicalCameras()) {

            String phCamId = camSensor.getAndroidId();

            ImageReader imageReader = getImReaderSurface(camSensor);

            if (imageReader == null) {
                Log.w(TAG, "Physical camera returns null surface: " + phCamId);
                continue;
            }

            Surface phSurface = imageReader.getSurface();

            lImageReaders.add(imageReader);
            lPhysicalSurfaces.add(phSurface);

            // depends on API level
            if (Build.VERSION.SDK_INT >= ANDROID_ADVANCED_CAM_FEATURES_VERSION) {

                OutputConfiguration outputConfig = new OutputConfiguration(phSurface);
                outputConfig.setPhysicalCameraId(phCamId);
                lAllConfigurations.add(outputConfig);
            }
        }

        mSelectedCamGroup.setPhysicalOutConfig(lAllConfigurations);
        mSelectedCamGroup.setPhysicalImageReaders(lImageReaders);
        mSelectedCamGroup.setPhysicalSurfaces(lPhysicalSurfaces);
    }


    public void notifyPreviewChanged(AutoFitTextureView textureView, Size viewSize,
                                     PreviewConfigState previewConfigState) {

        setPreviewConfigState(previewConfigState);

        // configure the preview surface
        if (textureView == null || viewSize == null) {
            Log.w(TAG, "notifyPreviewChanged: bad input, abort");
            return;
        }

        if (!isPreviewAvailable()) { // destroyed or no preview

            // initiate a camera session without preview
            if (mSelectedCamGroup != null) {
                mSelectedCamGroup.releasePreviewSurface();
            }
            Log.w(TAG, "configurePreview: preview not available");
            return;
        }

        boolean hasSelectedCams = true;
        if (mSelectedCamGroup == null) {
            hasSelectedCams = initSelectedCameras();
        }

        if (!hasSelectedCams) {
            Log.w(TAG, "configurePreview: cannot find any selected camera");
            return;
        }

        Context context = textureView.getContext();
        CameraCharacteristics characteristics = mSelectedCamGroup.getLogicalCamCharacteristics();


        Matrix trans = PreviewTransforms.computeTransformationMatrix(textureView, characteristics,
                viewSize, PreviewTransforms.getDeviceRotation(context));
        textureView.setTransform(trans);

        mSelectedCamGroup.setPreviewSurface(new Surface(textureView.getSurfaceTexture()));

        // create a session (internally detects whether a preview should be used or not)
        Log.v(TAG, "Calling initCameraSession from notifyPreviewChanged");
        // todo: don't call this without checking
        //mCounter.set(35);
        initCameraSession();
    }


    private CaptureRequest.Builder getPreviewRequest() {

        Surface previewSurface = null;
        if (mSelectedCamGroup != null) {
            previewSurface = mSelectedCamGroup.getPreviewSurface();
        }

        synchronized (mCameraStateLock) { // protect camera device

            if (mCameraDevice == null || previewSurface == null) {
                return null;
            }

            try {
                CaptureRequest.Builder repeatingRequest = mCameraDevice
                        .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                repeatingRequest.addTarget(previewSurface);

                return repeatingRequest;
            } catch (CameraAccessException e) {
                //setRunningImageCaptureFlag(false);
                e.printStackTrace();
            }
        }
        return null;
    }

    private CaptureRequest.Builder getMultiCamRequest() {

        List<Surface> targetSurfaces = new ArrayList<>();
        if (mSelectedCamGroup != null) {
            targetSurfaces = mSelectedCamGroup.getAllSurfaces();
        }

        synchronized (mCameraStateLock) {

            if (mCameraDevice == null || targetSurfaces.isEmpty()) {
                return null;
            }

            try {
                // Create the single request and dispatch it
                // NOTE: This may disrupt the ongoing repeating request momentarily
                CaptureRequest.Builder singleRequest = mCameraDevice
                        .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

                for (Surface surface : targetSurfaces) {
                    singleRequest.addTarget(surface);
                }

                return singleRequest;
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /* --------------------------------- Lifecycle Management ----------------------------------- */

    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressLint("MissingPermission")
    private void openCamera(Context context) {

        if (!this.isAvailable() || context == null) {
            Log.w(TAG, "Camera manager is not available, cannot open");
            return;
        }

        CameraManager cameraManager = getCameraManager();

        String logicalCam = mSelectedCamGroup.getLogicalCamera();

        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            if (Build.VERSION.SDK_INT < ANDROID_ADVANCED_CAM_FEATURES_VERSION) {
                // old API
                cameraManager.openCamera(logicalCam, mStateCallback, getBgHandler());
            }
            else {
                // new API
                cameraManager.openCamera(logicalCam, getBgExecutor(), mStateCallback);
            }
        }
        catch (CameraAccessException | NullPointerException e) {
            // Currently a NPE is thrown when the Camera2 API is used but not supported on the
            // device this code runs.
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (null != mSelectedCamGroup) {

                    List<ImageReader> lImReaders = mSelectedCamGroup.getPhysicalImageReaders();

                    if (lImReaders != null) {
                        for (ImageReader mImageReader : lImReaders) {
                            mImageReader.close();
                        }
                        mSelectedCamGroup.setPhysicalImageReaders(new ArrayList<>());
                    }

                    mSelectedCamGroup.releasePreviewSurface();
                }
                setCameraState(CameraState.CLOSED);
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        }
        finally {
            mCameraOpenCloseLock.release();
        }
    }

    // create multi-stream, multi-camera session
    private synchronized void initCameraSession() {

        if (mSelectedCamGroup == null) {
            Log.w(TAG, "initCameraSession: no selected camera");
            return;
        }

        List<OutputConfiguration> outputConfigsAll = mSelectedCamGroup.getAllOutConfigs();
        List<Surface> allOutSurfaces = mSelectedCamGroup.getAllSurfaces();
        boolean isMultiCam = mSelectedCamGroup.isMultiCam();

        synchronized (mCameraStateLock) { // protect cameraDevice

            if (mCameraDevice == null) {
                return;
            }

            try {
                if (Build.VERSION.SDK_INT >= ANDROID_ADVANCED_CAM_FEATURES_VERSION && isMultiCam) {

                    // Instantiate a session configuration that can be used to create a session
                    SessionConfiguration sessionConfiguration = new SessionConfiguration(
                            SessionConfiguration.SESSION_REGULAR,
                            outputConfigsAll, getBgExecutor(), mCaptureSessionCallback);

                    mCameraDevice.createCaptureSession(sessionConfiguration);
                }
                else {

                    mCameraDevice.createCaptureSession(allOutSurfaces, mCaptureSessionCallback, getBgHandler());
                }
            }
            catch (CameraAccessException e) {// | InterruptedException e) {
                e.printStackTrace();
            }
        }

        // initialize requests
        // todo: this is very sensitive to configuration changes
        // solutions:
        // 1. handle the exception
        // 2. better fragment's lifecycle management
        // 3. unchain initialization steps (call this method startPreviewAndCaptureLoop)
        mPreviewRequest = getPreviewRequest();
        mCaptureRequest = getMultiCamRequest();

        synchronized (mCameraStateLock) {
            // disable distortion correction
            CameraCharacteristics characteristics = mSelectedCamGroup.getLogicalCamCharacteristics();
            disableDistortionCorrection(mPreviewRequest, characteristics);
            disableDistortionCorrection(mCaptureRequest, characteristics);
        }
    }

    // when start is called: create a preview request for preview and initiate still capture loop
    private void startPreviewAndCaptureLoop() {

        startPrecaptureTraining();
    }

    private void stopPreviewAndCaptureLoop() {

    }

    private void startPreview() {

        if (!isPreviewAvailable()) {
            Log.d(TAG, "Preview is not available, abort");
            return;
        }

        synchronized (mCameraStateLock) {

            if (mCaptureSession == null || mPreviewRequest == null || mSelectedCamGroup == null) {
                Log.d(TAG, "startPreview: bad state, abort");
                return;
            }

            // configure the right settings (auto)
            setup3AControlsLocked(mPreviewRequest, mSelectedCamGroup.getLogicalCamCharacteristics());

            setCameraState(CameraState.PREVIEW);

            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequest.build(), mPreCaptureCallback, getBgHandler());
            }
            catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void startPrecaptureTraining() {

        synchronized (mCameraStateLock) {
            if (mCaptureSession == null || mPreviewRequest == null) {
                return;
            }

            // Trigger an auto-focus run if camera is capable. If the camera is already focused,
            // this should do nothing.
            if (isAFRun()) {
                mPreviewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_START);
            }

            // If this is not a legacy device, we can also trigger an auto-exposure metering
            // run.
            if (!isLegacyLocked()) {
                // Tell the camera to lock focus.
                mPreviewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                        CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            }

            // Update state machine to wait for auto-focus, auto-exposure, and
            // auto-white-balance (aka. "3A") to converge.
            setCameraState(CameraState.WAITING_FOR_3A_CONVERGENCE);

            // Start a timer for the pre-capture sequence.
            startTimerLocked();

            try {
                // Replace the existing repeating request with one with updated 3A triggers.
                mCaptureSession.capture(mPreviewRequest.build(), mPreCaptureCallback, getBgHandler());
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    // ?? Is it possible to get locked focal length (optical zoom)
    private void logCameraCharacteristics3A(CaptureResult result) {

        Float focalDistance = result.getRequest().get(CaptureRequest.LENS_FOCUS_DISTANCE);
        Log.v(TAG, "Ready to capture, fd: "+focalDistance);
    }

    private void runCaptureLoop() {

        synchronized (mCameraStateLock) {

            if (mCaptureSession == null || mCaptureRequest == null) {
                return;
            }

            if (mbIsFirstCapture) {
                // Use the same AE and AF modes as the preview.
                setup3AControlsLocked(mCaptureRequest, mSelectedCamGroup.getLogicalCamCharacteristics());

                setCameraState(CameraState.LOCKED_RUNNING);

                //mbIsFirstCapture = false;
            }

            // Set orientation.

            // Set request tag to easily track results in callbacks.
            mCaptureRequest.setTag(mCounter.getAndIncrement());

            try {
                mCaptureSession.capture(mCaptureRequest.build(), mCaptureCallback, getBgHandler());
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void processCapturedImage(int sensorId, Image image) {

        synchronized (mCameraStateLock) {

            if (image == null) {
                return;
            }

            int imageFormat = image.getFormat();

            String imageExtension = DEF_IMAGE_FILE_EXTENSION;
            if (imageFormat == ImageFormat.RAW_SENSOR) {
                imageExtension = DEF_IMAGE_RAW_EXTENSION;
            }

            // create image message
            long imageTs = image.getTimestamp();
            String fileName = imageTs + imageExtension;
            String filePath = PATH_BASE_IMAGES + "/" + fileName;
            //Log.v(TAG, "New image: " + fileName);

            MyResourceIdentifier resId = new MyResourceIdentifier(sensorId, 0);

            // image message
            resId.setState(1);
            int imageTarget = getTargetId(resId);
            MyMessages.MsgImage imageMsg = new MyMessages.MsgImage(image, filePath, imageTarget);
            // show full path in image message but use only the file name to save image
            imageMsg.setFileName(fileName);

            //byte[] bytes = null;

            if (imageFormat == ImageFormat.JPEG) {

                // create JPEG message
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                imageMsg.setData(bytes);
            }
            else if (imageFormat == ImageFormat.RAW_SENSOR) {

                // create RAW image message
                imageMsg.setCaptureResult(mLastCaptureResult);
                if (mSelectedCamGroup != null) {
                    imageMsg.setCharacteristics(mSelectedCamGroup.getPhysicalCamCharacteristics(sensorId));
                }
            }

            publishMessage(imageMsg);

            // text message (images.txt -> file names)
            resId.setState(0);
            int imgTxtTarget = getTargetId(resId);

            // register the file name only if image is jpg or raw
            if (imgTxtTarget >= 0 && imageFormat != ImageFormat.YUV_420_888) {

                MyMessages.MsgStorage txtMsg = new MyMessages.MsgStorage(imageMsg.toString(), fileName, imgTxtTarget);
                publishMessage(txtMsg);
            }
        }

        if (isProcessing()) { // recursive loop

            image.close();
            runCaptureLoop();
        }
        else {
            image.close();
        }
    }

    /* ------------------------------- Capture Request Configs ---------------------------------- */

    /**
     * Configure the given {@link CaptureRequest.Builder} to use auto-focus, auto-exposure, and
     * auto-white-balance controls if available.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param builder the builder to configure.
     */
    private void setup3AControlsLocked(CaptureRequest.Builder builder, CameraCharacteristics mCharacteristics) {
        // Enable auto-magical 3A run by camera device
        builder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);

        Float minFocusDist =
                mCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);

        // If MINIMUM_FOCUS_DISTANCE is 0, lens is fixed-focus and we need to skip the AF run.
        setNoAFRun(minFocusDist == null || minFocusDist == 0);

        if (isAFRun()) {
            // If there is a "continuous picture" mode available, use it, otherwise default to AUTO.
            if (contains(mCharacteristics.get(
                            CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            } else {
                builder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_AUTO);
            }
        }

        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        if (contains(mCharacteristics.get(
                        CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON);
        }

        // If there is an auto-magical white balance control mode available, use it.
        if (contains(mCharacteristics.get(
                        CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            // Allow AWB to run auto-magically if this device supports this
            builder.set(CaptureRequest.CONTROL_AWB_MODE,
                    CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }
    }

    private void disableDistortionCorrection(CaptureRequest.Builder builder, CameraCharacteristics mCharacteristics) {

        if (Build.VERSION.SDK_INT >= ANDROID_ADVANCED_CAM_FEATURES_VERSION) {

            // Determine if this device supports distortion correction
            if (contains(
                    mCharacteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES),
                    CameraMetadata.DISTORTION_CORRECTION_MODE_OFF
            )) {
                builder.set(
                        CaptureRequest.DISTORTION_CORRECTION_MODE,
                        CameraMetadata.DISTORTION_CORRECTION_MODE_OFF
                );
                Log.v(TAG, "Distortion correction disabled");
            }
        }
    }

    /**
     * Called after a RAW/JPEG capture has completed; resets the AF trigger state for the
     * pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void finishedCaptureLocked() {

        synchronized (mCameraStateLock) {
            try {
                // Reset the auto-focus trigger in case AF didn't run quickly enough.
                if (isAFRun()) {
                    mPreviewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

                    mCaptureSession.capture(mPreviewRequest.build(), mPreCaptureCallback,
                            null);

                    mPreviewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                }
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void lockPrecaptureRequest() {

        synchronized (mCameraStateLock) {
            try {
                // Reset the auto-focus trigger in case AF didn't run quickly enough.
                if (isAFRun()) {
                    mPreviewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE);

                    mCaptureSession.capture(mPreviewRequest.build(), mPreCaptureCallback, getBgHandler());
                }
            }
            catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /* ======================================= Helpers ========================================== */

    private static List<Integer> arrayToList(int[] inputArr) {

        List<Integer> outList = new ArrayList<>();

        for (int item : inputArr) {
            outList.add(item);
        }

        return outList;
    }

    /**
     * Return true if the given array contains the given integer.
     *
     * @param modes array to check.
     * @param mode  integer to get for.
     * @return true if the array contains the given integer, otherwise false.
     */
    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    public static CameraCharacteristics getCameraCharacteristics(CameraManager cameraManager, String cameraId) {

        if (cameraManager == null) {
            return null;
        }

        try {
            return cameraManager.getCameraCharacteristics(cameraId);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if we are using a device that only supports the LEGACY hardware level.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if this is a legacy device.
     */
    private boolean isLegacyLocked() {

        if (mSelectedCamGroup == null) {
            return false;
        }
        CameraCharacteristics mCharacteristics = mSelectedCamGroup.getLogicalCamCharacteristics();
        return mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    /**
     * Start the timer for the pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    synchronized private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the timer for the pre-capture sequence has been hit.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if the timeout occurred.
     */
    synchronized private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
    }

    /* ====================================== Data Types ======================================== */

    // this container should be thread-safe
    static class CameraGroup {

        private final boolean isMultiCam;

        private final String mLogicalCamera;
        private final CameraCharacteristics mLogicalCamCharacteristics;

        private final Set<CameraSensor> mPhysicalCameras;

        // can we hold a preview surface in a non-activity class??
        private Surface mPreviewSurface;

        private List<ImageReader> mPhysicalImageReaders;
        private List<OutputConfiguration> mPhysicalOutConfig;
        private List<Surface> mPhysicalSurfaces;


        public CameraGroup(String logicalId, CameraCharacteristics logicalCamCharacteristics,
                           Set<CameraSensor> physicalCams) {

            mLogicalCamera = logicalId;
            mLogicalCamCharacteristics = logicalCamCharacteristics;
            mPhysicalCameras = physicalCams;
            isMultiCam = physicalCams.size() > 1;
        }


        public boolean isMultiCam() {
            return isMultiCam;
        }
        public String getLogicalCamera() {
            return mLogicalCamera;
        }
        public CameraCharacteristics getLogicalCamCharacteristics() {
            return mLogicalCamCharacteristics;
        }
        public Set<CameraSensor> getPhysicalCameras() {
            return mPhysicalCameras;
        }


        synchronized public List<ImageReader> getPhysicalImageReaders() {
            return mPhysicalImageReaders;
        }
        synchronized public void setPhysicalImageReaders(List<ImageReader> mPhysicalImageReaders) {
            this.mPhysicalImageReaders = mPhysicalImageReaders;
        }

        synchronized public List<OutputConfiguration> getPhysicalOutConfig() {
            return mPhysicalOutConfig;
        }
        synchronized public void setPhysicalOutConfig(List<OutputConfiguration> physicalOutConfig) {
            this.mPhysicalOutConfig = physicalOutConfig;
        }

        synchronized public List<Surface> getPhysicalSurfaces() {
            return mPhysicalSurfaces;
        }
        synchronized public void setPhysicalSurfaces(List<Surface> physicalSurfaces) {
            this.mPhysicalSurfaces = physicalSurfaces;
        }

        synchronized public Surface getPreviewSurface() {
            return mPreviewSurface;
        }
        synchronized public void setPreviewSurface(Surface mPreviewSurface) {
            this.mPreviewSurface = mPreviewSurface;
        }
        synchronized public void releasePreviewSurface() {

            if (mPreviewSurface != null) {
                mPreviewSurface.release();
                mPreviewSurface = null;
            }
        }

        synchronized public List<Surface> getAllSurfaces() {

            List<Surface> targetSurfaces = new ArrayList<>();

            if (mPreviewSurface != null) {
                targetSurfaces.add(mPreviewSurface);
            }
            if (mPhysicalSurfaces != null && !mPhysicalSurfaces.isEmpty()) {
                targetSurfaces.addAll(mPhysicalSurfaces);
            }

            return targetSurfaces;
        }

        synchronized public List<OutputConfiguration> getAllOutConfigs() {

            List<OutputConfiguration> lAllOutConfigs = new ArrayList<>();

            if (Build.VERSION.SDK_INT >= ANDROID_ADVANCED_CAM_FEATURES_VERSION) {
                if (mPreviewSurface != null) {
                    lAllOutConfigs.add(new OutputConfiguration(mPreviewSurface));
                }
            }
            if (mPhysicalOutConfig != null && !mPhysicalOutConfig.isEmpty()) {
                lAllOutConfigs.addAll(mPhysicalOutConfig);
            }

            return lAllOutConfigs;
        }

        synchronized public CameraSensor getPhysicalCamera(int sensorId) {

            if (mPhysicalCameras != null) {

                for (CameraSensor camera : mPhysicalCameras) {

                    if (camera != null && camera.getId() == sensorId) {
                        return camera;
                    }
                }
            }
            return null;
        }

        synchronized public CameraCharacteristics getPhysicalCamCharacteristics(int sensorId) {

            CameraSensor camera = getPhysicalCamera(sensorId);
            if (camera != null) {
                return camera.getCharacteristics();
            }
            return null;
        }
    }

    static class CameraSensor extends MySensorInfo {

        private String mAndroidId;
        private final String mLogicalCamera;
        private final CameraCharacteristics mCharacteristics;


        public CameraSensor(int id, String name, String logicalCamera,
                            CameraCharacteristics characteristics) {

            super(id, name);
            mLogicalCamera = logicalCamera;
            mCharacteristics = characteristics;
        }

        public String getLogicalCamera() {
            return mLogicalCamera;
        }

        public String getAndroidId() {
            return mAndroidId;
        }

        public void setAndroidId(String androidId) {
            this.mAndroidId = androidId;
        }

        public CameraCharacteristics getCharacteristics() {
            return mCharacteristics;
        }
    }

    static class SizeSelector {

        private static final double ASPECT_RATIO_TOLERANCE = 0.005;

        /**
         * Compares two {@code Size}s based on their areas.
         */
        public static class CompareSizesByArea implements Comparator<Size> {

            @Override
            public int compare(Size lhs, Size rhs) {
                // We cast here to ensure the multiplications won't overflow
                return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                        (long) rhs.getWidth() * rhs.getHeight());
            }
        }

        public static Size swapDimensions(Size input) {
            return new Size(input.getHeight(), input.getWidth());
        }

        public static Size clampDimensions(Size target, Size maxSize) {

            int outWidth = target.getWidth();
            int outHeight = target.getHeight();

            int maxWidth = maxSize.getWidth();
            int maxHeight = maxSize.getHeight();

            if (outWidth > maxWidth) {
                outWidth = maxWidth;
            }

            if (outHeight > maxHeight) {
                outHeight = maxHeight;
            }

            return new Size(outWidth, outHeight);
        }

        public static Size getDisplaySize(Context activity) {

            // todo: according to Android website this is faulty, should work with window metrics
            Point displaySize = new Point();
            ((AppCompatActivity) activity).getWindowManager().getDefaultDisplay().getSize(displaySize);

            return new Size(displaySize.x, displaySize.y);
        }


        public static Size getSurfaceSize(CameraCharacteristics characteristics,
                                          int outputFormat, Size targetSize) {

            if (characteristics == null) {
                return targetSize;
            }

            // setup output formats
            int[] outputFormats = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputFormats();

            if (!contains(outputFormats, outputFormat)) {
                Log.w(TAG, "Selected camera doesn't support requested image format");
                return targetSize;
            }

            // setup sizes
            Size[] choices = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(outputFormat);

            return SizeSelector.getNearestSize(Arrays.asList(choices), targetSize);
        }

        /**
         * Return true if the two given {@link Size}s have the same aspect ratio.
         *
         * @param a first {@link Size} to compare.
         * @param b second {@link Size} to compare.
         * @return true if the sizes have the same aspect ratio, otherwise false.
         */
        public static boolean checkAspectsEqual(Size a, Size b) {

            double aAspect = a.getWidth() / (double) a.getHeight();
            double bAspect = b.getWidth() / (double) b.getHeight();
            return Math.abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE;
        }

        /**
         * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
         * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
         *
         * @param choices The list of available sizes
         * @return The video size
         */
        public static Size chooseVideoSize(Size[] choices) {

            for (Size size : choices) {
                if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                    return size;
                }
            }
            Log.e(TAG, "Couldn't find any suitable video size");
            return choices[choices.length - 1];
        }

        /**
         * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as the
         * respective max size, and whose aspect ratio matches with the specified value. If such size
         * doesn't exist, choose the largest one that is at most as large as the respective max size,
         * and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended output
         *                          class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal {@code Size}, or an arbitrary one if none were big enough
         */
        public static Size chooseOptimalSize(Size[] choices, Size viewSize, Size maxSize, Size aspectRatio) {

            int textureViewWidth = viewSize.getWidth();
            int textureViewHeight = viewSize.getHeight();
            int maxWidth = maxSize.getWidth();
            int maxHeight = maxSize.getHeight();

            // Collect the supported resolutions that are at least as big as the preview Surface
            List<Size> bigEnough = new ArrayList<>();
            // Collect the supported resolutions that are smaller than the preview Surface
            List<Size> notBigEnough = new ArrayList<>();
            int w = aspectRatio.getWidth();
            int h = aspectRatio.getHeight();
            for (Size option : choices) {
                if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                        option.getHeight() == option.getWidth() * h / w) {

                    if (option.getWidth() >= textureViewWidth &&
                            option.getHeight() >= textureViewHeight) {
                        bigEnough.add(option);
                    }
                    else {
                        notBigEnough.add(option);
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            if (bigEnough.size() > 0) {
                return Collections.min(bigEnough, new CompareSizesByArea());
            }
            else if (notBigEnough.size() > 0) {
                return Collections.max(notBigEnough, new CompareSizesByArea());
            }
            else {
                Log.e(TAG, "Couldn't find any suitable preview size");
                return choices[0];
            }
        }

        public static Size getNearestSize(List<Size> availableSizes, Size targetSize) {

            if (availableSizes == null || availableSizes.isEmpty() || targetSize == null) {
                Log.w(TAG, "getNearestSize: Bad input");
                return targetSize;
            }

            long minScore = Long.MAX_VALUE;
            Size minDistance = targetSize;

            int width = targetSize.getWidth();
            int height = targetSize.getHeight();

            for (Size sz : availableSizes) {

                int distW = Math.abs(sz.getWidth() - width);
                if (distW == 0) {
                    distW = 1;
                }
                int distH = Math.abs(sz.getHeight() - height);
                if (distH == 0) {
                    distH = 1;
                }

                int currScore = distH * distW;

                if (currScore < minScore) {
                    minScore = currScore;
                    minDistance = sz;
                }
            }

            return minDistance;
        }
    }

    public static class PreviewTransforms {

        private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
        private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

        static {
            ORIENTATIONS.append(Surface.ROTATION_0, 0);
            ORIENTATIONS.append(Surface.ROTATION_90, 90);
            ORIENTATIONS.append(Surface.ROTATION_180, 180);
            ORIENTATIONS.append(Surface.ROTATION_270, 270);
        }

        static {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
        }

        static {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
        }

        /**
         * Computes rotation required to transform the camera sensor output orientation to the
         * device's current orientation in degrees.
         *
         * @param characteristics        The CameraCharacteristics to query for the sensor orientation.
         * @param surfaceRotationDegrees The current device orientation as a Surface constant.
         * @return Relative rotation of the camera sensor output.
         */
        public static int computeRelativeRotation(CameraCharacteristics characteristics,
                                                  int surfaceRotationDegrees) {

            Integer sensorOrientationDegrees =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            // Reverse device orientation for back-facing cameras.
            int sign = characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT ? 1 : -1;

            // Calculate desired orientation relative to camera orientation to make
            // the image upright relative to the device orientation.
            return (sensorOrientationDegrees - surfaceRotationDegrees * sign + 360) % 360;
        }

        /**
         * This method calculates the transformation matrix that we need to apply to the
         * TextureView to avoid a distorted preview.
         */
        public static Matrix computeTransformationMatrix(TextureView textureView,
                                                         CameraCharacteristics characteristics,
                                                         Size previewSize, int surfaceRotation) {

            Matrix matrix = new Matrix();

            int surfaceRotationDegrees = ORIENTATIONS.get(surfaceRotation);

            // Rotation required to transform from the camera sensor orientation to the
            // device's current orientation in degrees.
            int relativeRotation = computeRelativeRotation(characteristics, surfaceRotationDegrees);

            // Scale factor required to scale the preview to its original size on the x-axis.
            float scaleX = (relativeRotation % 180 == 0)
                    ? (float) textureView.getWidth() / previewSize.getWidth()
                    : (float) textureView.getWidth() / previewSize.getHeight();

            // Scale factor required to scale the preview to its original size on the y-axis.
            float scaleY = (relativeRotation % 180 == 0)
                    ? (float) textureView.getHeight() / previewSize.getHeight()
                    : (float) textureView.getHeight() / previewSize.getWidth();

            // Scale factor required to fit the preview to the TextureView size.
            float finalScale = Math.min(scaleX, scaleY);

            // The scale will be different if the buffer has been rotated.
            if (relativeRotation % 180 == 0) {
                matrix.setScale(
                        textureView.getHeight() / (float) textureView.getWidth() / scaleY * finalScale,
                        textureView.getWidth() / (float) textureView.getHeight() / scaleX * finalScale,
                        textureView.getWidth() / 2f,
                        textureView.getHeight() / 2f
                );
            }
            else {
                matrix.setScale(
                        1 / scaleX * finalScale,
                        1 / scaleY * finalScale,
                        textureView.getWidth() / 2f,
                        textureView.getHeight() / 2f
                );
            }

            // Rotate the TextureView to compensate for the Surface's rotation.
            matrix.postRotate(
                    (float) -surfaceRotationDegrees,
                    textureView.getWidth() / 2f,
                    textureView.getHeight() / 2f
            );

            return matrix;
        }

        /**
         * Configure the necessary {@link android.graphics.Matrix} transformation to `mTextureView`,
         * and start/restart the preview capture session if necessary.
         * <p/>
         * This method should be called after the camera state has been initialized in
         * setUpCameraOutputs.
         *
         * @param viewWidth  The width of `mTextureView`
         * @param viewHeight The height of `mTextureView`
         */
        private static Size configureTransform(Context context, CameraCharacteristics mCharacteristics,
                                               AutoFitTextureView mTextureView, Size viewSize) {

            AppCompatActivity activity = (AppCompatActivity) context;

            if (null == mTextureView || null == activity) {
                return viewSize;
            }

            int viewWidth = viewSize.getWidth();
            int viewHeight = viewSize.getHeight();

            StreamConfigurationMap map = mCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // For still image captures, we always use the largest available size.
            Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new SizeSelector.CompareSizesByArea());

            // Find the rotation of the device relative to the native device orientation.
            int deviceRotation = getDeviceRotation(activity);
            Size displaySize = SizeSelector.getDisplaySize(activity);

            // Find the rotation of the device relative to the camera sensor's orientation.
            int totalRotation = computeRelativeRotation(mCharacteristics, deviceRotation);

            // Swap the view dimensions for calculation as needed if they are rotated relative to
            // the sensor.
            boolean swappedDimensions = PreviewTransforms.isSwappedDimensions(totalRotation);

            Size rotatedViewSize = viewSize;
            Size maxPreviewSize = displaySize;
            if (swappedDimensions) {
                rotatedViewSize = SizeSelector.swapDimensions(viewSize);
                maxPreviewSize = SizeSelector.swapDimensions(displaySize);
            }

            // Preview should not be larger than display size and 1080p.
            maxPreviewSize = SizeSelector.clampDimensions(maxPreviewSize, MAX_PREVIEW_SIZE);

            // Find the best preview size for these view dimensions and configured JPEG size.
            Size previewSize = SizeSelector.chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture.class),
                    rotatedViewSize, maxPreviewSize, largestJpeg);

            if (swappedDimensions) {
                mTextureView.setAspectRatio(
                        previewSize.getHeight(), previewSize.getWidth());
            }
            else {
                mTextureView.setAspectRatio(
                        previewSize.getWidth(), previewSize.getHeight());
            }

            // Find rotation of device in degrees (reverse device orientation for front-facing
            // cameras).
            int rotation = (mCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT) ?
                    (360 + ORIENTATIONS.get(deviceRotation)) % 360 :
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360;

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            // Initially, output stream images from the Camera2 API will be rotated to the native
            // device orientation from the sensor's orientation, and the TextureView will default to
            // scaling these buffers to fill it's view bounds.  If the aspect ratios and relative
            // orientations are correct, this is fine.
            //
            // However, if the device orientation has been rotated relative to its native
            // orientation so that the TextureView's dimensions are swapped relative to the
            // native device orientation, we must do the following to ensure the output stream
            // images are not incorrectly scaled by the TextureView:
            //   - Undo the scale-to-fill from the output buffer's dimensions (i.e. its dimensions
            //     in the native device orientation) to the TextureView's dimension.
            //   - Apply a scale-to-fill from the output buffer's rotated dimensions
            //     (i.e. its dimensions in the current device orientation) to the TextureView's
            //     dimensions.
            //   - Apply the rotation from the native device orientation to the current device
            //     rotation.
            if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.getHeight(),
                        (float) viewWidth / previewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);

            }
            matrix.postRotate(rotation, centerX, centerY);

            mTextureView.setTransform(matrix);

            return previewSize;
        }

        public static int getDeviceRotation(Context activity) {

            return ((AppCompatActivity) activity).getWindowManager().getDefaultDisplay().getRotation();
        }

        static boolean isSwappedDimensions(int totalRotation) {
            return totalRotation == 90 || totalRotation == 270;
        }
    }
}

