package com.dayani.m.roboplatform.managers;

/**
 * TODO: Work more on the last methods.
 */

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyStorageManager /*implements MyPermissionManager.PermissionsInterface*/ {

    private static final String PACKAGE_NAME = "com.dayani.m.flightsimulator2019";
    private static final String TAG = "MyStorageManager";

    private static final int REQUEST_WRITE_PERMISSION_CODE = 7769;
    private static final int REQUEST_READ_PERMISSION_CODE = 7770;
    private static final String KEY_STORAGE_PERMISSION = PACKAGE_NAME+
            ".MyStorageManager_WRITE."+REQUEST_WRITE_PERMISSION_CODE;
    private static final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
           // Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    /**
     * This is used for URI getting in onActivityResult
     * (different from Request_read_permission);
     */
    private static final int READ_REQUEST_CODE = 35;

    /*private static final String filePath = "/DCIM/Files";
    private static final String fileName = "myfile.txt";
    private static final String fileContents = "Hello world!";*/

    private static Context appContext;

    private boolean isAvailable = false;

    //private MyPermissionManager mPermissionManager;

    private FileOutputStream outputStream;


    /**
     * Permission checking is now done implicitly.
     * IsAvailable flag in all util classes shows the state
     * of class permissions and ... after construction of these classes.
     * @param context
     */
    public MyStorageManager(Context context) {
        super();

        appContext = context;
        /*mPermissionManager = new MyPermissionManager(appContext,
                KEY_STORAGE_PERMISSION,REQUEST_WRITE_PERMISSION_CODE,STORAGE_PERMISSIONS);*/
        isAvailable = MyPermissionManager.hasAllPermissions(appContext,
                STORAGE_PERMISSIONS, KEY_STORAGE_PERMISSION);
    }

    /*===================================== Permissions ==========================================*/

    /**
     * checkPermissions also request permissions internally.
     * That's why hasPermissionsGranted is favored over this.
     * @return
     */
    /*public boolean checkPermissions() {
        isAvailable = this.mPermissionManager.checkPermissions();
        return isAvailable;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        this.mPermissionManager.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }

    public MyPermissionManager getPermissionManager() {
        return mPermissionManager;
    }*/

    public boolean isAvailable() {
        return isAvailable;
    }

    // Checks if external storage is available for read and write
    public boolean isExternalStorageWritable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    // Checks if external storage is available to at least read
    public boolean isExternalStorageReadable() {

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static int getRequestWritePermissionCode() {
        return REQUEST_WRITE_PERMISSION_CODE;
    }

    public static int getRequestReadPermissionCode() {
        return REQUEST_READ_PERMISSION_CODE;
    }

    public static int getRequestPermissionCode() {
        return getRequestWritePermissionCode();
    }

    public static String[] getPermissions() {
        return STORAGE_PERMISSIONS;
    }

    public static String getPermissionKey() {
        return KEY_STORAGE_PERMISSION;
    }


    /*======================================== Storage ===========================================*/


    public static String getNextFilePath(String basePath, String timePerfix, String typePerfix) {
        return (basePath == null ? "" : (basePath))
                + timePerfix + '.' + typePerfix;
    }

    public static String getTimePerfix() {
        return new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
    }

    /**
     * Works perfectly for internal storage (not sdCard).
     *      But requires runtime write permissions.
     *      The content is save in root internal
     *      directory and never deleted with uninstall.
     */
    public File getPublicInternalFile(String filePath, String fileName) {

        //check if external storage is writable
        //since the result is internal files, actually
        //there is no need to this here
        /*if (!isExternalStorageWritable()) {
            return null;
        }*/
        // get the path to internal public root dir.
        //or use Environment.getExternalStoragePublicDirectory(
        //          Environment.DIRECTORY_DOCUMENTS+[filePath])
        //although the result is the same.
        //File intPubRoot = Environment.getExternalStoragePublicDirectory(
        //       Environment.DIRECTORY_DOCUMENTS);
        File intPubRoot = Environment.getExternalStorageDirectory();
        // to this path add a new directory path
        File dir = new File(intPubRoot.getAbsolutePath() + filePath);
        // create this directory if not already created
        if (!dir.mkdirs()) {
            Log.e(TAG, "Directory not created.");
        }
        // create the file in which we will write the contents
        File file = new File(dir, fileName);
        Log.i(TAG, file.getParent());

        return file;
    }

    /**
     * saves file on ... location but readable from docs
     *      works for both internal and external storage
     *      its file content is deleted with uninstall.
     *      add readable writable check for external storage latter.
     *      Uses BufferWriter
     */
    public void writeBuffered(File file, String content) {

        try {
            // Adds a line to the file
            BufferedWriter writer = new BufferedWriter(new FileWriter(file, true /*append*/));
            writer.write(content);
            writer.close();
            // Refresh the data so it can seen when the device is plugged in a
            // computer. You may have to unplug and replug the device to see the
            // latest changes. This is not necessary if the user should not modify
            // the files.
            MediaScannerConnection.scanFile(appContext,
                    new String[]{file.toString()},
                    null,
                    null);
        } catch (IOException e) {
            Log.e(TAG, "Unable to write to the TestFile.txt file.");
        }
    }

    public File getPrivateInternalFile(Context context, String fileName) {

        // Creates a file in the primary external storage space of the
        // current application.
        // If the file does not exists, it is created.
        // to this path add a new directory path
        //this.getFilesDir for internal -> /data/user/0/com... or /data/data/com...?
        File dir = context.getFilesDir();
        Log.i(TAG, dir.getPath());
        File file = new File(dir, fileName);
        Log.i(TAG, file.getPath());
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        return file;
    }

    public File getPrivateExternalFile(Context context, String fileName) {

        // Get the directory for the app's private pictures directory.
//        File file = new File(context.getExternalFilesDir(
//                Environment.DIRECTORY_DOCUMENTS), fileName);
        File path = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        Log.i(TAG, path.getPath());
        File file = new File(path, fileName);
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
//        Uri mUri = MediaStore.Audio.Media.getContentUriForPath("audio/path");
//        Log.i(TAG, mUri.getPath());
        return file;

        /*try {
            // Creates a file in the primary external storage space of the
            // current application.
            // If the file does not exists, it is created.
            // to this path add a new directory path
            //or this.getExternalFilesDir for external -> /storage/emulated/0/Android/data/com...
            File dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            File testFile = new File(dir, fileName);
            Log.i(TAG, testFile.getPath());
            if (!testFile.exists()) {
                testFile.mkdirs();
                testFile.createNewFile();
            }
            return testFile;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
        return null;*/
    }

    /**
     Works perfectly for internal storage (not sdCard).
     But requires runtime write permissions.
     The content is save in root internal
     directory and never deleted with uninstall.
     Uses OutputStream
     */
    public void writeOutputStream(File file, String content) {

//        myFile.delete();
//        myContext.deleteFile(fileName);

        try {
            //file.createNewFile();
            outputStream = new FileOutputStream(file/*, append*/);
            //InputStream in = c.getInputStream();
            outputStream = appContext.openFileOutput(file.getName(), Context.MODE_PRIVATE);
            outputStream.write(content.getBytes());
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*String strMsg = "Sending a message!";
        byte[] buffer = new byte[1024];
        int len1 = 0;
        //while ((len1 = in.read(buffer)) > 0) {
        //f.write(strMsg.getBytes(), 0, strMsg.length());
        //}*/
    }

    public String readBuffered(File file) {

        String textFromFile = "";
        // Gets the file from the primary external storage space of the
        // current application.
        //File file = getPrivateExternalFile(appContext, fileName);
        if (file != null) {
            StringBuilder stringBuilder = new StringBuilder();
            // Reads the data from the file
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(file));
                String line;

                while ((line = reader.readLine()) != null) {
                    textFromFile += line.toString();
                    textFromFile += "\n";
                }
                reader.close();
            } catch (Exception e) {
                Log.e(TAG, "Unable to read the TestFile.txt file.");
            }
        }
        return textFromFile;
    }

    //Example
    /*public void savePubInternalFile() {

        File file = getPublicInternalFile(filePath, fileName);
        writeBuffered(file, fileContents);
    }*/

    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                //showImage(uri);
            }
        }
    }
}
