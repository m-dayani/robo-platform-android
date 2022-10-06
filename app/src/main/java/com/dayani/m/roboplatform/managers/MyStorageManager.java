package com.dayani.m.roboplatform.managers;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

import androidx.activity.result.ActivityResult;
import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MsgConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.MyMessage;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageConfig;
import com.dayani.m.roboplatform.utils.interfaces.MyMessages.StorageInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;


public class MyStorageManager extends MyBaseManager {

    /* ===================================== Variables ========================================== */

    private static final String PACKAGE_NAME = AppGlobals.PACKAGE_BASE_NAME;

    private static final String TAG = MyStorageManager.class.getSimpleName();

    private static final int REQUEST_WRITE_PERMISSION_CODE = 7769;
    private static final int REQUEST_WRITABLE_STORAGE = 7770;
    private static final int REQUEST_MANAGE_ALL_FILES_PERM = 7771;

    private static final String KEY_STORAGE_PERMISSION = PACKAGE_NAME+
            ".MyStorageManager_WRITE."+REQUEST_WRITE_PERMISSION_CODE;

    public static final String TEST_PATH = "dummy";

    public static final String KEY_BASE_PATH = PACKAGE_NAME+"key-base-storage-path";
    public static final String BASE_STORAGE_PATH = "robo-platform";
    public static final String DS_FOLDER_PREFIX = "dataset-";

    private static final String ANDROID_REL_PATH_NAME = "/Android";

    private static final int ANDROID_SCOPED_STORAGE_VERSION = Build.VERSION_CODES.R;


    private boolean mbIsBasePathWritable = false;
    private String mBasePath;
    private final String mDsRoot;

    private static int mStorageId = 0;
    private final HashMap<Integer, StorageHandle> mmStorage = new HashMap<>();

    /* ==================================== Construction ======================================== */

    /**
     * Permission checking is now done implicitly.
     * IsAvailable flag in all util classes shows the state
     * of class permissions and ... after construction of these classes.
     * @param context app context (avoid memory leaks)
     */
    public MyStorageManager(Context context) {

        super(context);
        mDsRoot = initDsPath();
        setBasePath(getSavedBasePath(context));
    }

    /* ===================================== Core Tasks ========================================= */

    /* -------------------------------------- Support ------------------------------------------- */

    /**
     * Storage is supported if there is at least an available volume
     * TODO: Need to check for available space too??
     * @param context activity context
     * @return boolean, false: storage is permanently disabled in the device (possible?!)
     */
    @Override
    protected boolean resolveSupport(Context context) {

        List<String> volumes = getAvailableVolumes(context);
        return !volumes.isEmpty();
    }

    /* ----------------------------- Requirements & Permissions --------------------------------- */

    /**
     * Note: In scoped storage we still need manage all files permission
     *      It won't work if only asked for a directory
     * @return list of all requirements
     */
    @Override
    public List<Requirement> getRequirements() {

        return Arrays.asList(
                Requirement.PERMISSIONS,
                Requirement.BASE_PATH_WRITABLE
        );
    }

    @Override
    public boolean passedAllRequirements() {
        return hasAllPermissions() && canWriteBasePath();
    }

    @Override
    protected void updateRequirementsState(Context context) {

        List<Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to update");
            return;
        }

        // permissions
        if (requirements.contains(Requirement.PERMISSIONS)) {
            updatePermissionsState(context);
        }

        // writable base path
        if (requirements.contains(Requirement.BASE_PATH_WRITABLE)) {
            updateWritableBasePathState();
        }
    }

    @Override
    protected void resolveRequirements(Context context) {

        List<Requirement> requirements = getRequirements();
        if (requirements == null || requirements.isEmpty()) {
            Log.d(TAG, "No requirements to resolve");
            return;
        }

        // permissions
        if (requirements.contains(Requirement.PERMISSIONS)) {
            if (!hasAllPermissions()) {
                resolvePermissions();
                return;
            }
        }

        // writable base path
        if (requirements.contains(Requirement.BASE_PATH_WRITABLE)) {
            if (!canWriteBasePath()) {
                resolveWriteBasePath(context);
            }
        }
    }

    @Override
    public void resolvePermissions() {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {

            if (mRequirementRequestListener != null) {

                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                mRequirementRequestListener.requestResolution(REQUEST_MANAGE_ALL_FILES_PERM, intent);
            }
        }
        else {
            super.resolvePermissions();
        }
    }

    @Override
    public void onActivityResult(Context context, ActivityResult result) {

        Intent resultData = result.getData();

        if (resultData == null) {
            Log.w(TAG, "Result intent is null");
            return;
        }

        int actionTag = resultData.getIntExtra(KEY_INTENT_ACTIVITY_LAUNCHER, 0);

        if (actionTag == REQUEST_WRITABLE_STORAGE) {

            if (result.getResultCode() == Activity.RESULT_OK) {

                // The result data contains a URI for the document or directory that
                // the user selected.
                Uri uri = resultData.getData();
                String uriAbsPath = convertUriToPath(uri);
                Log.d(TAG, "The permitted path: " + uriAbsPath);

                // save base path
                saveBasePath(context, uriAbsPath);

                // update the path variable
                setBasePath(uriAbsPath);

                // update requirement state
                updateWritableBasePathState();

                // persist permissions??
                persistWritablePathPermission(context, resultData);
            }
        }
        else if (actionTag == REQUEST_MANAGE_ALL_FILES_PERM) {

            if (result.getResultCode() == Activity.RESULT_OK) {

                Log.i(TAG, "Manage all files permission is granted");
                updatePermissionsState(context);
            }
            else {
                Log.i(TAG, "Manage all files permission is not granted");
            }
        }
    }

    @SuppressLint("WrongConstant")
    private void persistWritablePathPermission(Context context, Intent intent) {

        final int takeFlags = intent.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        // Check for the freshest data.
        context.getContentResolver().takePersistableUriPermission(intent.getData(), takeFlags);
    }

    @Override
    public List<String> getPermissions() {

        return Collections.singletonList(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
                // Manifest.permission.READ_EXTERNAL_STORAGE,
        );
    }

    @Override
    public void updatePermissionsState(Context context) {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {

            // Manage all files permission is granted
            mbIsPermitted = Environment.isExternalStorageManager();
        }
        else {
            // Regular write permission is granted (older android versions)
            super.updatePermissionsState(context);
        }
    }

    // deprecated
    public static boolean isStoragePermission(String qPerm) {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {
            return false;
        }

        return qPerm.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    // deprecated
    public static boolean checkManageAllFilesPermission(Context context) {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {
            return Environment.isExternalStorageManager();
        }
        else {
            String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
            return MyPermissionManager.hasAllPermissions(context, PERMISSIONS, KEY_STORAGE_PERMISSION);
        }
    }

    /* --------------------------------------- State -------------------------------------------- */

    @Override
    public void updateCheckedSensors() {
        mbIsChecked = true;
    }

    /* -------------------------------------- Lifecycle ----------------------------------------- */

    @Override
    public void execute(Context context, LifeCycleState state) {

        if (state == LifeCycleState.ACT_DESTROYED) {
            for (int keyStore : mmStorage.keySet()) {

                StorageHandle store = mmStorage.get(keyStore);
                if (store != null) {
                    store.close();
                }
            }
            super.execute(context, state);
        }
        else if (state == LifeCycleState.ACT_CREATED) {
            super.execute(context, state);
        }
        // do nothing
        //super.execute(context, state);
    }

    /* -------------------------------------- Resources ----------------------------------------- */

    @Override
    public List<MySensorGroup> getSensorGroups(Context context) {

        if (mlSensorGroup != null) {
            return mlSensorGroup;
        }

        ArrayList<MySensorGroup> sensorGroups = new ArrayList<>();
        ArrayList<MySensorInfo> sensors = new ArrayList<>();

        MySensorGroup storeGrp = new MySensorGroup(MySensorGroup.getNextGlobalId(),
                SensorType.TYPE_STORAGE, "Storage", sensors);
        sensorGroups.add(storeGrp);

        return sensorGroups;
    }

    @Override
    protected String getResourceId(MyResourceIdentifier resId) {
        // doesn't define resources
        return TAG;
    }

    @Override
    protected List<Pair<String, MsgConfig>> getStorageConfigMessages(MySensorInfo sensor) {
        // doesn't initialize storage config.
        return null;
    }

    /*======================================== Storage ===========================================*/

    private boolean canWriteBasePath() {
        return mbIsBasePathWritable;
    }

    private void updateWritableBasePathState() {

        // retrieve base path
        String basePath = getBasePath();
        // check if it's writable
        mbIsBasePathWritable = testTargetPathWritable(basePath);
    }

    private void resolveWriteBasePath(Context context) {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {

            if (mRequirementRequestListener == null) {
                Log.w(TAG, "Requirement listener is null, forgot to initialize?");
                return;
            }

            // Prompt user for a base path in higher versions of Android:
            // Choose a directory using the system's file picker.
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker when it loads.
            //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(suggestedPath));

            Log.d(TAG, "Requesting a base path from user");
            mRequirementRequestListener.requestResolution(REQUEST_WRITABLE_STORAGE, intent);
        }
        else {
            // In older versions, reconstruct the default path in the root
            String basePath = getSuggestedBasePath(context);

            String suggestedPath = resolveFilePath(Collections.singletonList(basePath));

            if (testTargetPathWritable(suggestedPath)) {

                saveBasePath(context, basePath);
                setBasePath(basePath);
                updateWritableBasePathState();
            }
        }
    }

    public String getBasePath() { return mBasePath; }
    private void setBasePath(String path) { mBasePath = path; }

    public String getDsRoot() { return mDsRoot; }

    /**
     * Retrieve a previously saved base path or return null
     * @param context context (Activity)
     * @return saved instance of root path
     */
    private static String getSavedBasePath(Context context) {

        return MyStateManager.getStringPref(context, KEY_BASE_PATH, "");
    }

    private static void saveBasePath(Context context, String path) {

        MyStateManager.setStringPref(context, KEY_BASE_PATH, path);
    }

    /**
     * Calculate and return base path based on available volumes and directories
     * @param context app context (Activity)
     * @return a list of root paths for each volume or empty list
     */
    private static List<String> getAvailableVolumes(Context context) {

        File[] files = context.getExternalFilesDirs(null);
        List<String> rootPaths = new ArrayList<>();

        for (File file : files) {

            String origPath = file.getAbsolutePath();
            int androidIdx = origPath.indexOf(ANDROID_REL_PATH_NAME);
            String path = origPath.substring(0, androidIdx);

            rootPaths.add(path);
        }

        return rootPaths;
    }

    private static String getSuggestedBasePath(Context context) {

        List<String> paths = getAvailableVolumes(context);
        if (paths.isEmpty()) {
            Log.w(TAG, "No volumes available, abort");
            return null;
        }

        File suggestedPath = new File(paths.get(0), BASE_STORAGE_PATH);

        return suggestedPath.getAbsolutePath();
    }

    public static String resolveFilePath(List<String> folders) {

        if (folders == null) {
            return null;
        }

        // create all intermediate folders
        File lastDir = null;

        for (String curFolder : folders) {

            if (curFolder == null) {
                continue;
            }

            if (lastDir == null) {
                lastDir = new File(curFolder);
            }
            else {
                lastDir = new File(lastDir, curFolder);
            }

            if (!(lastDir.exists() || lastDir.mkdir())) {
                return null;
            }
        }

        if (lastDir != null) {
            return lastDir.getAbsolutePath();
        }

        return null;
    }

    private static boolean testTargetPathWritable(String path) {

        if (path == null || path.isEmpty()) {
            return false;
        }

        File curDir = new File(path);
        if (!curDir.exists() || !curDir.isDirectory()) {
            return false;
        }

        File dummyFolder = new File(curDir, TEST_PATH);
        if (dummyFolder.exists() && dummyFolder.delete()) {
            return true;
        }

        return dummyFolder.mkdir() && dummyFolder.delete();
    }

    /* ----------------------------------- Message Passing -------------------------------------- */

    @Override
    public void onMessageReceived(MyMessage msg) {

        if (msg == null) {
            return;
        }

        // check the tag
        String msgTag = msg.getChTag();
        if (msgTag != null && !msgTag.equals(TAG)) {
            return;
        }

        // check the channel type
        // listens to different types of messages (except for logging messages??)

        // handle message depending on its type
        if (msg instanceof StorageConfig) {
            handleStorageConfigMessage((StorageConfig) msg);
        }
        else {
            handleStorageMessage(msg);
        }
    }

    private void handleStorageConfigMessage(StorageConfig msg) {

        // what kind of configuration?
        if (msg.isConfigurationAction(MsgConfig.ConfigAction.OPEN)) {
            openNewChannel(msg);
        }
        else if (msg.isConfigurationAction(MsgConfig.ConfigAction.CLOSE)) {
            closeChannel(msg);
        }
        else if (msg.isConfigurationAction(MsgConfig.ConfigAction.GET_STATE)) {
            getFullFilePath(msg);
        }
    }

    private void openNewChannel(StorageConfig config) {

        if (!this.isAvailable()) {
            Log.w(TAG, "Storage not available, abort");
            return;
        }

        // Make file path considering the base and ds root
        List<String> foldersWithRoot = new ArrayList<>();
        foldersWithRoot.add(getBasePath());

        StorageInfo storageInfo = config.getStorageInfo();

        if (storageInfo.isAppendDsRoot()) {
            foldersWithRoot.add(mDsRoot);
        }

        foldersWithRoot.addAll(storageInfo.getFolders());

        String filePath = resolveFilePath(foldersWithRoot);

        if (filePath == null) {
            Log.w(TAG, "Null path when initializing storage handle");
            return;
        }

        int newId = mStorageId++;
        config.setTargetId(newId);

        // create a new handle
        StorageHandle fileHandle = new StorageHandle(storageInfo, filePath);

        // TODO: Maybe check for existing channels
        mmStorage.put(newId, fileHandle);

        // write file header if it contains one
        //fileHandle.write(config);

        // WARNING: publishing a response message may result in an implicit infinite loop
    }

    public void closeChannel(StorageConfig config) {

        if (this.isAvailable()) {

            int targetId = config.getTargetId();
            StorageHandle store = mmStorage.get(targetId);

            if (store != null) {
                store.close();
                mmStorage.remove(targetId);
            }
        }
    }

    public void getFullFilePath(StorageConfig config) {

        if (this.isAvailable()) {

            StorageHandle store = mmStorage.get(config.getTargetId());
            if (store != null) {
                config.setStringMessage(store.getFullPath());
                // WARNING: publishing a response message may result in an implicit infinite loop
            }
        }
    }

    public void handleStorageMessage(MyMessage msg) {

        if (this.isAvailable()) {

            StorageHandle store = mmStorage.get(msg.getTargetId());
            if (store != null) {
                store.write(msg);
            }
        }
    }

    /*======================================== Helpers ===========================================*/

    public static String getTimePrefix() {
        // also 'SSS' ?
        return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(new Date());
    }

    public static String getStorageRootDir(String uriPath) {

        if (uriPath.contains("primary")) {
            return "/storage/self/";
        }
        return "/storage/";
    }

    public static String convertUriToPath(Uri uri) {

        return getStorageRootDir(uri.getPath())+uri.getLastPathSegment().replace(':', '/');
    }

    public static String initDsPath() {
        return DS_FOLDER_PREFIX + getTimePrefix();
    }

    /*=================================== Types & Interfaces =====================================*/

    private static class StorageStream {

        public StorageStream(String path, String fileName, boolean append) {

            mFile = new File(path, fileName);

            try {
                mOs = new FileOutputStream(mFile, append);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void write(String msg) {

            if (mOs == null) {
                return;
            }

            try {
                mOs.write(msg.getBytes());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void close() {

            if (mOs == null) {
                return;
            }

            try {
                mOs.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String getFullPath() {

            return mFile.getAbsolutePath();
        }

        private final File mFile;
        private FileOutputStream mOs;
    }

    private static class StorageHandle {

        private final StorageInfo mStorageInfo;

        private StorageStream mFileHandle;
        private final String mFilePath;

        public StorageHandle(StorageInfo channelInfo, String filePath) {

            mStorageInfo = channelInfo;
            mFilePath = filePath;
            String fileName = channelInfo.getFileName();

            if (channelInfo.isStream()) {

                boolean append = channelInfo.isStreamType(StorageInfo.StreamType.STREAM_STRING_APPEND);
                mFileHandle = new StorageStream(filePath, fileName, append);
            }
        }

        public void close() {

            if (mFileHandle != null) {
                mFileHandle.close();
            }
        }

        public void write(MyMessage msg) {

            if (mStorageInfo.isStream()) {

                // streaming operation
                if (mFileHandle != null) {
                    mFileHandle.write(msg.toString());
                }
            }
            else if (mStorageInfo.isTrain()) {

                // open/close operation
                if (msg instanceof MyMessages.MsgImage) {

                    // image
                    MyMessages.MsgImage imageMsg = (MyMessages.MsgImage) msg;
                    Image image = imageMsg.getImage();

                    if (image.getFormat() == ImageFormat.RAW_SENSOR) {
                        writeRawImage(mFilePath, imageMsg.getFileName(), imageMsg);
                    }
                    else {
                        writeFileAndClose(mFilePath, imageMsg.getFileName(), imageMsg.getData());
                    }
                }
                else if (msg instanceof MyMessages.MsgStorage) {

                    // storage message (file headers, images.txt, calib.txt, ...)
                    MyMessages.MsgStorage storageMsg = (MyMessages.MsgStorage) msg;
                    writeFileAndClose(mFilePath, storageMsg.getFileName(), msg.toString().getBytes());
                }
            }
        }

        private static void writeFileAndClose(String path, String fileName, byte[] data) {

            if (path == null || fileName == null) {
                return;
            }

            File file = new File(path, fileName);

            try {
                FileOutputStream fileOs = new FileOutputStream(file);
                fileOs.write(data);
                fileOs.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static void writeRawImage(String path, String fileName, MyMessages.MsgImage imageMsg) {

            if (path == null || fileName == null || imageMsg == null) {
                return;
            }

            CaptureResult captureResult = imageMsg.getCaptureResult();
            CameraCharacteristics characteristics = imageMsg.getCharacteristics();
            Image image = imageMsg.getImage();

            if (image == null || captureResult == null || characteristics == null) {
                return;
            }

            DngCreator dngCreator = new DngCreator(characteristics, captureResult);

            File file = new File(path, fileName);
            FileOutputStream fileOs = null;

            try {
                fileOs = new FileOutputStream(file);
                dngCreator.writeImage(fileOs, image);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                closeOutput(fileOs);
            }
        }

        private static void closeOutput(OutputStream outputStream) {
            if (null != outputStream) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public String getFullPath() {

            if (mFileHandle != null) {
                return mFileHandle.getFullPath();
            }
            if (mStorageInfo != null) {
                File file = new File(mFilePath, mStorageInfo.getFileName());
                return file.getAbsolutePath();
            }
            return mFilePath;
        }
    }
}