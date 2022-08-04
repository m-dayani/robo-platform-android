package com.dayani.m.roboplatform.managers;

import static android.os.Build.VERSION.SDK_INT;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.utils.ActivityRequirements.Requirement;
import com.dayani.m.roboplatform.utils.AppGlobals;
import com.dayani.m.roboplatform.utils.MySensorGroup;
import com.dayani.m.roboplatform.utils.MySensorGroup.SensorType;
import com.dayani.m.roboplatform.utils.MySensorInfo;

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
import java.util.Locale;


public class MyStorageManager {

    private static final String PACKAGE_NAME = AppGlobals.PACKAGE_BASE_NAME;

    private static final String TAG = MyStorageManager.class.getSimpleName();

    private static final int REQUEST_WRITE_PERMISSION_CODE = 7769;
    //private static final int REQUEST_READ_PERMISSION_CODE = 7770;

    private static final String KEY_STORAGE_PERMISSION = PACKAGE_NAME+
            ".MyStorageManager_WRITE."+REQUEST_WRITE_PERMISSION_CODE;

    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
           // Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    public static final String TEST_PATH = "dummy";

    public static final String KEY_BASE_PATH = PACKAGE_NAME+"key-base-storage-path";
    public static final String BASE_STORAGE_PATH = "robo-platform";
    public static final String DS_FOLDER_PREFIX = "dataset-";

    private static final String ANDROID_REL_PATH_NAME = "/Android";

    private static final int ANDROID_SCOPED_STORAGE_VERSION = Build.VERSION_CODES.R;

    /**
     * This is used for URI getting in onActivityResult
     * (different from Request_read_permission);
     */
    //private static final int READ_REQUEST_CODE = 35;

    private static Context appContext;

    private ActivityResultLauncher<Intent> mDirSelLauncher;
    private ActivityResultLauncher<String[]> mPermsLauncher;

    private final HashMap<Integer, StorageHandle> mMapStorage;

    public enum StorageState {
        NOT_AVAILABLE,
        NEED_PATH_REQ,
        NEED_PERMS,
        AVAILABLE
    }

    StorageState mState;

    /*==================================== Initialization ========================================*/

    /**
     * Permission checking is now done implicitly.
     * IsAvailable flag in all util classes shows the state
     * of class permissions and ... after construction of these classes.
     * @param context app context (avoid memory leaks)
     */
    public MyStorageManager(Context context) {

        appContext = context;

        checkAvailability();

        mMapStorage = new HashMap<>();
    }

    private void initLaunchers() {

        mDirSelLauncher = getDirSelLauncher(appContext);
        mPermsLauncher = getStoragePermsLauncher(appContext);
    }

    private ActivityResultLauncher<Intent> getDirSelLauncher(Context context) {

        return ((AppCompatActivity) context).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.i(TAG, "Back from select dir activity, result code: "+result.getResultCode());

                    if (result.getResultCode() == Activity.RESULT_OK) {

                        // The result data contains a URI for the document or directory that
                        // the user selected.
                        Intent resultData = result.getData();
                        if (resultData != null) {
                            Uri uri = resultData.getData();
                            String uriAbsPath = convertUriToPath(uri);
                            Log.i(TAG, "The permitted path: " + uriAbsPath);

                            // save base path
                            saveBasePath(context, uriAbsPath);

                            // update storage state
                            //mState = StorageState.AVAILABLE;
                            checkAvailability();
                        }

                    }
                });
    }

    private ActivityResultLauncher<String[]> getStoragePermsLauncher(Context context) {

        return ((AppCompatActivity) context).registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {

                    boolean res = true;
                    for (String perm : permissions.keySet()) {

                        if (!permissions.get(perm)) {
                            // no removal problem because removing from another list
                            res = false;
                            break;
                        }
                    }
                    if (res) {
                        //mState = StorageState.AVAILABLE;
                        checkAvailability();
                    }
                });
    }

    /*============================== Permissions & Availability ==================================*/

    private void checkAvailability() {

        mState = StorageState.NOT_AVAILABLE;

        // Android 10+ uses scoped storage -> no permission required
        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {

            // if already asked for base path, storage is available
            String basePath = getSavedBasePath(appContext);
            if (testTargetPathWritable(basePath)) {
                mState = StorageState.AVAILABLE;
            }
            else {
                mState = StorageState.NEED_PATH_REQ;
            }
        }
        else {

            // in older versions, check the permissions
            if (checkManageAllFilesPermission(appContext)) {

                ArrayList<String> listWritablePaths = getAvailableVolumes(appContext);
                if (listWritablePaths.size() > 0) {
                    mState = StorageState.AVAILABLE;
                }
            }
            else {
                mState = StorageState.NEED_PERMS;
            }
        }
    }

    public boolean isAvailable() {
        return mState == StorageState.AVAILABLE;
    }

    /*// Checks if external storage is available for read and write
    public boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    // Checks if external storage is available to at least read
    public boolean isExternalStorageReadable() {

        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }*/

    public static String[] getPermissions() {

//        if (SDK_INT >= Build.VERSION_CODES.R) {
//
//            return new String[] {Manifest.permission.MANAGE_EXTERNAL_STORAGE};
//        }
//        else {
            return STORAGE_PERMISSIONS;
//        }
    }

    public static boolean isStoragePermission(String qperm) {

        boolean res = false;

        for (String perm : getPermissions()) {

            if (perm.equals(qperm)) {
                res = true;
                break;
            }
        }

        return res;
    }

    public static boolean checkManageAllFilesPermission(Context context) {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {
            return Environment.isExternalStorageManager();
        } else {
            return MyPermissionManager.hasAllPermissions(context, STORAGE_PERMISSIONS, KEY_STORAGE_PERMISSION);
        }
    }

    public static ArrayList<MySensorGroup> getSensorRequirements(Context mContext) {

        ArrayList<MySensorGroup> sensorGroups = new ArrayList<>();
        ArrayList<MySensorInfo> sensors = new ArrayList<>();
        ArrayList<Requirement> reqs = new ArrayList<>();
        ArrayList<String> perms = new ArrayList<>();

        String[] strArrPerms = getPermissions();

        if (SDK_INT < ANDROID_SCOPED_STORAGE_VERSION) {

            reqs = new ArrayList<>(Collections.singletonList(Requirement.PERMISSIONS));
            perms = new ArrayList<>(Arrays.asList(strArrPerms));
        }

        sensorGroups.add(new MySensorGroup(SensorType.TYPE_STORAGE, "Storage", sensors, reqs, perms));

        return sensorGroups;
    }

    public void resolveAvailability() {

        if (mPermsLauncher == null || mDirSelLauncher == null) {
            this.initLaunchers();
        }

        if (mState.equals(StorageState.NEED_PATH_REQ)) {
            this.requestBasePath();
        }
        else if (mState.equals(StorageState.NEED_PERMS)) {
            this.requestPermissions();
        }
    }

    public void requestBasePath() {

        if (SDK_INT >= ANDROID_SCOPED_STORAGE_VERSION) {

            ArrayList<String> paths = getAvailableVolumes(appContext);
            File suggestedPath = new File(paths.get(0), BASE_STORAGE_PATH);

            // Prompt user for a base path in higher versions of Android:
            // Choose a directory using the system's file picker.
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker when it loads.
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(suggestedPath));

            Log.d(TAG, "Requesting a base path from user");
            mDirSelLauncher.launch(intent);
        }
    }

    public void requestPermissions() {

        mPermsLauncher.launch(STORAGE_PERMISSIONS);
    }

    /*======================================== Storage ===========================================*/

    public static String getNextFilePath(String basePath, String timePerfix, String typePerfix) {
        return (basePath == null ? "" : (basePath))
                + timePerfix + '.' + typePerfix;
    }

    public static String getTimePerfix() {
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


    /**
     * Retrieve a previously saved base path or return null
     * @param mContext context (Activity)
     * @return saved instance of root path
     */
    public static String getSavedBasePath(Context mContext) {

        SharedPreferences sharedPref = ((AppCompatActivity) mContext).getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getString(KEY_BASE_PATH, null);
    }

    public static void saveBasePath(Context context, String path) {

        SharedPreferences sharedPref = ((AppCompatActivity) context).getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(MyStorageManager.KEY_BASE_PATH, path);
        editor.apply();
    }

    /**
     * Calculate and return base path based on available volumes and directories
     * @param mContext app context (Activity)
     * @return a list of root paths for each volume or empty list
     */
    public static ArrayList<String> getAvailableVolumes(Context mContext) {

        File[] files = mContext.getExternalFilesDirs(null);
        ArrayList<String> rootPaths = new ArrayList<>();

        for (File file : files) {

            String origPath = file.getAbsolutePath();
            int androidIdx = origPath.indexOf(ANDROID_REL_PATH_NAME);
            String path = origPath.substring(0, androidIdx);

            if (testTargetPathWritable(path)) {
                rootPaths.add(path);
            }
        }

        return rootPaths;
    }

    public String resolveBaseWorkingPath() {

        // retrieve the root path, first look in saved prefs
        String basePath = getSavedBasePath(appContext);

        if (basePath == null || basePath.isEmpty()) {

            // calculate and request a new base path
            ArrayList<String> paths = getAvailableVolumes(appContext);

            if (paths.size() > 0) {

                File suggestedPath = new File(paths.get(0), BASE_STORAGE_PATH);

                if (suggestedPath.exists() || suggestedPath.mkdir()) {

                    return suggestedPath.getAbsolutePath();
                }
            }
            else {
                Log.w(TAG, "Cannot get any writable root path!");
            }
        }
        else {
            return basePath;
        }
        return null;
    }

    public String resolveFilePath(String[] folders, String fileName) {

        // Resolve base working path
        String basePath = resolveBaseWorkingPath();
        String fullPath = null;

        if (this.isAvailable() && basePath != null) {

            File lastDir = new File(basePath);
            if (lastDir.exists() || lastDir.mkdir()) {

                // create all intermediate folders
                int cnt = 0;
                do {
                    String curFolder = folders[cnt];
                    lastDir = new File(lastDir, curFolder);

                    if (!(lastDir.exists() || lastDir.mkdir())) {
                        return fullPath;
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


    public int subscribeStorageChannel(String path) {

        //TODO: More checks on path
        if (!this.isAvailable()) {
            return -1;
        }

        mMapStorage.put(mMapStorage.size(), new StorageHandle(path));

        return mMapStorage.size() - 1;
    }

    public void publishMessage(int id, String msg) {

        if (this.isAvailable() && id >= 0 && id < mMapStorage.size()) {
            StorageHandle store = mMapStorage.get(id);
            if (store != null) {
                store.write(msg);
            }
        }
    }


    public void clean() {

        for (int keyStore : mMapStorage.keySet()) {

            StorageHandle store = mMapStorage.get(keyStore);
            if (store != null) {
                store.close();
            }
        }
    }

    /*======================================== Classes ===========================================*/

    public static class StorageHandle {

        public StorageHandle(String path) {

            File mFile = new File(path);
            try {
                mOs = new FileOutputStream(mFile);
            }
            catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void write(String msg) {

            try {
                mOs.write(msg.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void close() {

            try {
                mOs.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private FileOutputStream mOs;
    }
}
