package com.dayani.m.roboplatform.managers;

/**
 * Use and extend this class instead of repeating permission
 * code in each utility class.
 * Each utility class needs to extend or return this.
 * If needed, extend & override for customizations.
 * Mored control for individual util class over permissions
 * Simple but might use more memory??
 *
 * Note1: We can use facilities to make permission handling faster:
 *      like using mPermissionGranted flag or save/retrieve from prefs.
 *      But note to 2 problems:
 *      1. Permissions are treated as groups by permission manager
 *          not indivitually.
 *      2. User can disable permissions from anywhere anytime!
 *      That's why we need to check for permissions every time
 *      and check it with package manager (instead of relying on
 *      last saved prefs).
 *
 * Note2: It might be a good idea to implement these for
 *      future work.
 *
 * Note3: There are 2 methods for checking all permissions:
 *      1. set/get key/val pairs
 *      2. add all permission codes and check the final addition
 */


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.dayani.m.roboplatform.utils.AppGlobals;

public class MyPermissionManager {

    private static final String TAG = MyPermissionManager.class.getSimpleName();

    private static final String KEY_PERMISSION_CODES_SUM = AppGlobals.PACKAGE_BASE_NAME +
            '.'+TAG+".PERMISSION_CODES_SUM";

    private static Context appContext;
    private static SharedPreferences mSharedPref;

    //private boolean mPermissionGranted = false;

    private String mPermissionKey;
    private int mPermissionCode;
    private String[] mPermissions;
    private String mPermissionRequest = null;
    private String mPermissionRational = null;

    /**
     * Interface for organizing required methods in
     * implementer classes.
     */
    public interface PermissionsInterface {
        public boolean checkPermissions();
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] grantResults);
        public MyPermissionManager getPermissionManager();
    }

    /*public interface PermissionsInfo {
        public int getRequestPermissionCode();
        public String[] getPermissions();
        public String getPermissionKey();
    }*/

    public MyPermissionManager(Context context,
                               String permKey, int permCode, String[] perms) {
        this.appContext = context;
        this.mSharedPref = ((Activity) appContext).getPreferences(Context.MODE_PRIVATE);
        this.mPermissionKey = permKey;
        this.mPermissionCode = permCode;
        this.mPermissions = perms;
    }

    public String getPermissionKey() {
        return mPermissionKey;
    }

    public int getPermissionCode() {
        return mPermissionCode;
    }

    public String[] getPermissions() {
        return mPermissions;
    }

    public void setPermissionCode(int permissionCode) {
        this.mPermissionCode = permissionCode;
    }

    public void setPermissions(String[] permissions) {
        this.mPermissions = permissions;
    }

    public void setPermissionRequest(String permissionRequest) {
        this.mPermissionRequest = permissionRequest;
    }

    public void setRational(String rational) {
        this.mPermissionRational = rational;
    }

    private void savePermissionPreferences(String key, boolean val) {
        SharedPreferences.Editor editor = mSharedPref.edit();
        editor.putBoolean(key, val);
        editor.apply();
    }

    private boolean getPermissionPreferenes(String key) {
        return mSharedPref.getBoolean(key, false);
    }

    /*============================= Enhanced Dynamic permission checks ===========================*/

    public boolean checkPermissions() {
        boolean res = hasPermissionsGranted(getPermissions(), getPermissionCode());
        if (!res) {
            requestPermissions(getPermissions(), getPermissionCode());
        }
        return res;
    }

    /**
     * little problem in requesting specific permissions...
     * @param permissions
     * @return
     */
    public boolean hasPermissionsGranted(String[] permissions, int permCode) {
        boolean stat = true;
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(appContext, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                MyStateManager.setBoolPref(appContext,getPermissionKey(permission),false);
                stat = false;
            } else {
                MyStateManager.setBoolPref(appContext,getPermissionKey(permission),true);
            }
        }
        return stat;
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((AppCompatActivity) appContext,
                    permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private void requestPermissions(String[] permissions, int permCode) {

        Log.i(TAG, "requestPermissions");

        if (shouldShowRequestPermissionRationale(permissions)) {
            //new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            //Only for android 6.0 and higher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions((AppCompatActivity) appContext,
                        permissions, permCode);
            }
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");

        //check if this permission group is related to this class
        if (requestCode == getPermissionCode()) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");

            } else if (grantResults.length == getPermissions().length) {
                for (int i = 0; i < grantResults.length; i++) {
                    int result = grantResults[i];
                    String permission = permissions[i];
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        //individual permission denied
                        MyStateManager.setBoolPref(appContext,getPermissionKey(permission),false);
                        showLongToast("Without all permissions, this app can't work properly.");
                        break;
                    } else {
                        //permission granted
                        MyStateManager.setBoolPref(appContext,getPermissionKey(permission),true);
                        Log.d(TAG, "Permission granted (dynamic call): "+permission);
                    }
                }
            } else {
                Log.d(TAG, "User interaction was interrupted (dynamic call)");
                showLongToast("All permissions required for this app to work.");
            }
        }
    }

    /*=============================== Static Methods (Useless!) ==================================*/

    /*public static boolean checkPermissions(Context context, String[] permissions, int permCode) {
        return hasPermissionsGranted(context, permissions, permCode);
    }*/

    /**
     * little problem in requesting specific permissions...
     * @param permissions
     * @return
     */
    /*private static boolean hasPermissionsGranted(Context context, String[] permissions, int permCode) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(context, permissions, permCode);
                return false;
            }
        }
        return true;
    }*/

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private static boolean shouldShowRequestPermissionRationale(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((AppCompatActivity) context,
                    permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Requests permissions needed for recording video.
     */
    /*private static void requestPermissions(Context context, String[] permissions, int permCode) {

        Log.i(TAG, "requestPermissions");

        if (shouldShowRequestPermissionRationale(context, permissions)) {
            //new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            //Only for android 6.0 and higher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions((AppCompatActivity) context,
                        permissions, permCode);
            }
        }
    }

    public static void onRequestPermissionsResult(int requestCode, int resultCode, @NonNull String[] permissions,
                                                  @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");

        //check if this permission group is related to this class
        if (requestCode == resultCode) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
                //or add a String[] requestPermissions argument
            } else if (grantResults.length == permissions.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        //individual permission denied
                        //ErrorDialog.newInstance(getString(R.string.permission_request))
                            //.show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        Log.i(TAG, "Without all permissions, this app can't work properly.");
                        break;
                    } else {
                        //permission granted
                        //mPermissionGranted = true;
                        //savePreferences
                    }
                }
            } else {
                //ErrorDialog.newInstance(getString(R.string.permission_request))
                    //.show(getChildFragmentManager(), FRAGMENT_DIALOG);
                Log.i(TAG, "All permissions required for this app to work.");
            }
        }
    }

    public static void onRequestPermissionsResult(Context context, int requestCode, int resultCode,
                              @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");

        //check if this permission group is related to this class
        if (requestCode == resultCode) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
                //or add a String[] requestPermissions argument
            } else if (grantResults.length == permissions.length) {
                boolean res = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        //individual permission denied
                        //ErrorDialog.newInstance(getString(R.string.permission_request))
                            //.show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        res = false;
                        Log.i(TAG, "Without all permissions, this app can't work properly.");
                        break;
                    } else {
                        //permission granted
                        //mPermissionGranted = true;
                        //savePreferences
                    }
                }
                if (res) {
                    long permissionsSum = MyStateManager.getLongPref(context,
                            KEY_PERMISSION_CODES_SUM,0);
                    permissionsSum += requestCode;
                    MyStateManager.setLongPref(context,KEY_PERMISSION_CODES_SUM,permissionsSum);
                }
            } else {
                //ErrorDialog.newInstance(getString(R.string.permission_request))
                    //.show(getChildFragmentManager(), FRAGMENT_DIALOG);
                Log.i(TAG, "All permissions required for this app to work.");
            }
        }
    }

    public static void onRequestPermissionsResult(Context context, String permissionKey,
                              int requestCode, int resultCode, @NonNull String[] permissions,
                              @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");

        //check if this permission group is related to this class
        if (requestCode == resultCode) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
                //or add a String[] requestPermissions argument
            } else if (grantResults.length == permissions.length) {
                boolean res = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        //individual permission denied
                        // ErrorDialog.newInstance(getString(R.string.permission_request))
                            //.show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        res = false;
                        Log.i(TAG, "Without all permissions, this app can't work properly.");
                        break;
                    } else {
                        //permission granted
                        //savePreferences
                    }
                }
                if (res) {
                    long permissionsSum = MyStateManager.getLongPref(context,
                            KEY_PERMISSION_CODES_SUM, 0);
                    permissionsSum += requestCode;
                    MyStateManager.setLongPref(context, KEY_PERMISSION_CODES_SUM, permissionsSum);
                    MyStateManager.setBoolPref(context, permissionKey, true);
                }
            } else {
                //ErrorDialog.newInstance(getString(R.string.permission_request))
                    //.show(getChildFragmentManager(), FRAGMENT_DIALOG);
                Log.i(TAG, "All permissions required for this app to work.");
            }
        }
    }

    private static long getPermissionRequestCodesSum(Context context) {
        return MyStateManager.getLongPref(context, KEY_PERMISSION_CODES_SUM,0);
    }

    public static boolean checkAllPermissionsByCode(Context context, int[] codeArray) {
        long sum = 0;
        for (int code : codeArray) {
            sum += code;
        }
        if (sum == getPermissionRequestCodesSum(context)) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean checkAllPermissionsByKey(Context context, String[] keys) {
        for (String key : keys) {
            if(!MyStateManager.getBoolPref(context,key,false)) {
                return false;
            }
        }
        return true;
    }*/


    /*------------------------------------ Enhandced Methods -------------------------------------*/

    /*
      These functions also update the state of permissions
      There is 2 category of universal keys for each permission group:
           1. The key identifying the group
           2. The keys for each permission
     */

    /**
     * This is only a check on all permissions (no request)
     * And it also updates the saved permission states
     * @param context
     * @param permissions
     * @return
     */
    public static boolean hasAllPermissions(Context context, String[] permissions,
                                            String permissionKey) {
        Log.i(TAG, "Check and update permission states.");
        boolean flag = true;
        for (String permission : permissions) {
            //for each check, also update saved prefs
            if (ActivityCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                //requestPermissions(context, permissions, permCode);
                MyStateManager.setBoolPref(context,getPermissionKey(permission),false);
                flag = false;
            } else {
                MyStateManager.setBoolPref(context,getPermissionKey(permission),true);
            }
        }
        //update the universal key for the group
        MyStateManager.setBoolPref(context,permissionKey,flag);
        return flag;
    }

    /**
     * Check and ask for permissions if not permitted
     * @param context
     * @param permissions
     * @param permCode
     * @return
     */
    public static boolean checkAllPermissions(Context context, String[] permissions, int permCode,
                                              String mPermissionKey) {
        Log.i(TAG, "Check and request permissions if necessary.");
        boolean res = hasAllPermissions(context,permissions,mPermissionKey);
        if (!res) {
            requestAllPermissions(context, permissions, permCode);
        }
        return res;
    }

    /**
     * Requests permissions needed for recording video.
     */
    private static void requestAllPermissions(Context context, String[] permissions, int permCode) {

        Log.i(TAG, "requestPermissions");

        if (shouldShowRequestPermissionRationale(context, permissions)) {
            //new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            //Only for android 6.0 and higher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions((AppCompatActivity) context,
                        permissions, permCode);
            }
        }
    }

    public static void onRequestAllPermissionsResult(Context context, String permissionKey,
                             int requestCode, int resultCode, @NonNull String[] permissions,
                             @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestAllPermissionsResult");

        //check if this permission group is related to this class
        if (requestCode == resultCode) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                // set the state of general key to false
                MyStateManager.setBoolPref(context,permissionKey,false);
                Log.i(TAG, "User interaction was cancelled.");
                //or add a String[] requestPermissions argument
            } else if (grantResults.length == permissions.length) {
                boolean flag = true;
                for (int i = 0; i < grantResults.length; i++) {
                    int result = grantResults[i];
                    //hopping that permissions' length is equal to ...
                    String permission = permissions[i];
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        //individual permission denied
                        MyStateManager.setBoolPref(context,getPermissionKey(permission),false);
                        Log.i(TAG, "Individual permission denied: "+permission);
                        flag = false;
                        //break;
                    } else {
                        //permission granted
                        MyStateManager.setBoolPref(context,getPermissionKey(permission),false);
                        Log.i(TAG, "Individual permission granted: "+permission);
                    }
                }
                MyStateManager.setBoolPref(context,permissionKey,flag);
            } else {
                MyStateManager.setBoolPref(context,permissionKey,false);
                Log.i(TAG, "There was a problem in user interaction (permissions).");
            }
        }
    }

    public static String getPermissionKey(String indentifier) {
        return AppGlobals.PACKAGE_BASE_NAME+'.'+indentifier;
    }

    public static void showLongToast(String msg) {
        Toast.makeText(appContext,msg,Toast.LENGTH_LONG).show();
    }

    /*public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }*/
}

