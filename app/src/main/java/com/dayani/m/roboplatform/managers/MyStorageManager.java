package com.dayani.m.roboplatform.managers;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.utils.interfaces.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup;
import com.dayani.m.roboplatform.utils.data_types.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.data_types.MySensorInfo;
import com.dayani.m.roboplatform.utils.interfaces.StorageChannel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class MyStorageManager extends MyBaseManager implements StorageChannel {

    /* ===================================== Variables ========================================== */

    private static final String PACKAGE_NAME = AppGlobals.PACKAGE_BASE_NAME;

    private static final String TAG = MyStorageManager.class.getSimpleName();

    private static final int REQUEST_WRITE_PERMISSION_CODE = 7769;
    private static final int REQUEST_WRITABLE_STORAGE = 7770;
    private static final int REQUEST_MANAGE_ALL_FILES_PERM = 7771;
    //private static final int REQUEST_READ_PERMISSION_CODE = 7772;

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
    private final HashMap<Integer, StorageHandle> mMapStorage  = new HashMap<>();

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

        init(context);
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
            updateWritableBasePathState(context);
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
    protected void resolvePermissions() {

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

                // update requirement state
                updateWritableBasePathState(context);

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

    /* ------------------------------------ Availability ---------------------------------------- */

    /* --------------------------------------- State -------------------------------------------- */

    @Override
    public void updateCheckedSensors() {
        mbIsChecked = true;
    }

    /* -------------------------------------- Lifecycle ----------------------------------------- */

    @Override
    protected void init(Context context) {

        mBasePath = getBasePath(context);
        updateAvailabilityAndCheckedSensors(context);
    }

    @Override
    public void clean(Context context) {

        for (int keyStore : mMapStorage.keySet()) {

            StorageHandle store = mMapStorage.get(keyStore);
            if (store != null) {
                store.close();
            }
        }
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
    protected Map<Integer, StorageInfo> initStorageChannels() { return new HashMap<>(); }

    @Override
    protected void openStorageChannels(Context context) {}

    /*======================================== Storage ===========================================*/

    private boolean canWriteBasePath() {
        return mbIsBasePathWritable;
    }

    private void updateWritableBasePathState(Context context) {

        // retrieve base path
        String basePath = getBasePath(context);
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

            if (testTargetPathWritable(basePath)) {
                saveBasePath(context, basePath);
            }
        }
    }


    private String getBasePath(Context context) {

        if (mBasePath == null) {
            return getSavedBasePath(context);
        }
        return mBasePath;
    }

    /**
     * Retrieve a previously saved base path or return null
     * @param context context (Activity)
     * @return saved instance of root path
     */
    private static String getSavedBasePath(Context context) {

        SharedPreferences sharedPref = ((AppCompatActivity) context).getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(KEY_BASE_PATH, null);
    }

    private static void saveBasePath(Context context, String path) {

        SharedPreferences sharedPref = ((AppCompatActivity) context).getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(MyStorageManager.KEY_BASE_PATH, path);
        editor.apply();
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

    private String getSuggestedBasePath(Context context) {

        List<String> paths = getAvailableVolumes(context);
        if (paths.isEmpty()) {
            Log.w(TAG, "No volumes available, abort");
            return null;
        }

        File suggestedPath = new File(paths.get(0), BASE_STORAGE_PATH);

        return suggestedPath.getAbsolutePath();
    }

    public String resolveFilePath(Context context, String[] folders, String fileName) {

        // Resolve base working path
        String basePath = getBasePath(context);
        String fullPath = null;

        if (this.isAvailable() && basePath != null) {

            File lastDir = new File(basePath);
            if (lastDir.exists() || lastDir.mkdir()) {

                // create all intermediate folders
                int cnt = 0;
                do {
                    String curFolder = folders[cnt];
                    if (curFolder == null) {
                        continue;
                    }

                    lastDir = new File(lastDir, curFolder);

                    if (!(lastDir.exists() || lastDir.mkdir())) {
                        return null;
                    }
                    cnt++;
                }
                while (cnt < folders.length);

                File fullFile = new File(lastDir, fileName);
                fullPath = fullFile.getAbsolutePath();
            }
        }

        return fullPath;
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
    public int openNewChannel(Context context, StorageInfo channelInfo) {

        if (!this.isAvailable()) {
            Log.d(TAG, "Storage not available, abort");
            return -1;
        }

        List<String> foldersWithRoot = new ArrayList<>();
        if (channelInfo.isAppendDsRoot()) {
            foldersWithRoot.add(mDsRoot);
        }
        foldersWithRoot.addAll(channelInfo.getFolders());

        String[] foldersArr = foldersWithRoot.toArray(new String[0]);

        String fullPath = resolveFilePath(context, foldersArr, channelInfo.getFileName());

        if (fullPath == null) {
            Log.w(TAG, "Null path when initializing storage handle");
            return -1;
        }

        int newId = mStorageId++;
        mMapStorage.put(newId, new StorageHandle(fullPath, channelInfo.isAppend()));

        return newId;
    }

    @Override
    public void closeChannel(int id) {
        if (this.isAvailable()) {
            StorageHandle store = mMapStorage.get(id);
            if (store != null) {
                store.close();
                mMapStorage.remove(id);
            }
        }
    }

    @Override
    public void publishMessage(int id, MyMessage msg) {

        if (this.isAvailable()) {
            StorageHandle store = mMapStorage.get(id);
            if (store != null) {
                store.write(msg.toString());
            }
        }
    }

    @Override
    public void resetChannel(int id) {

        if (this.isAvailable()) {
            StorageHandle store = mMapStorage.get(id);
            if (store != null) {
                store.resetContent();
            }
        }
    }

    @Override
    public String getFullFilePath(int id) {

        if (this.isAvailable()) {
            StorageHandle store = mMapStorage.get(id);
            if (store != null) {
                return store.getFullPath();
            }
        }

        return "";
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

    private static class StorageHandle {

        public StorageHandle(String path, boolean append) {

            mFile = new File(path);
            mbAppend = append;

            try {
                mOs = new FileOutputStream(mFile, mbAppend);
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

        public void resetContent() {

            if (mOs == null) {
                return;
            }

            try {
                mOs.close();
                mOs = new FileOutputStream(mFile, mbAppend);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        private final File mFile;
        private final boolean mbAppend;
        private FileOutputStream mOs;
    }

    public static class StorageInfo {

        private final List<String> mlFolders;
        private final String mFileName;

        private boolean mbAppend;
        private boolean mbAppendDsRoot;

        private int channelId;

        public StorageInfo(List<String> folders, String fileName) {

            mlFolders = folders;
            mFileName = fileName;
            channelId = -1;
            mbAppend = false;
            mbAppendDsRoot = true;
        }

        public List<String> getFolders() {
            return mlFolders;
        }

        public String getFileName() {
            return mFileName;
        }

        public int getChannelId() {
            return channelId;
        }

        public void setChannelId(int channelId) {
            this.channelId = channelId;
        }

        public boolean isAppend() {
            return mbAppend;
        }

        public void setAppend(boolean mbAppend) {
            this.mbAppend = mbAppend;
        }

        public boolean isAppendDsRoot() {
            return mbAppendDsRoot;
        }

        public void setAppendDsRoot(boolean mbAppendDsRoot) {
            this.mbAppendDsRoot = mbAppendDsRoot;
        }
    }
}