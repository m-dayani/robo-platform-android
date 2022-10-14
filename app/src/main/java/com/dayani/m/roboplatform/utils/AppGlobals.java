package com.dayani.m.roboplatform.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;

public interface AppGlobals {

    String PACKAGE_BASE_NAME = "com.dayani.m.roboplatform";
    String APPLICATION_NAME = "Robo-Platform";

    String KEY_TARGET_ACTIVITY = PACKAGE_BASE_NAME+".TARGET_ACTIVITY";

    String KEY_TARGET_REQUIREMENTS = PACKAGE_BASE_NAME+".TARGET_REQUIREMENTS";
    String KEY_TARGET_PERMISSIONS = PACKAGE_BASE_NAME+".TARGET_PERMISSIONS";

    String KEY_ALL_PERMISSIONS = PACKAGE_BASE_NAME+".ALL_PERMISSIONS";
    String KEY_PARTIAL_PERMISSIONS = PACKAGE_BASE_NAME+".KEY_PARTIAL_PERMISSIONS";
    String KEY_LOCATION_PERMISSION = PACKAGE_BASE_NAME+".android.permission.ACCESS_FINE_LOCATION";
    String KEY_CAMERA_PERMISSION = PACKAGE_BASE_NAME+".android.permission.CAMERA";
    String KEY_MIC_PERMISSION = PACKAGE_BASE_NAME+".android.permission.RECORD_AUDIO";
    String KEY_STORAGE_PERMISSION = PACKAGE_BASE_NAME+".android.permission.WRITE_EXTERNAL_STORAGE";

    String KEY_EDIT_PERMISSIONS_PREF = "edit_permission_prefs";
    String KEY_USB_PERMISSIONS = PACKAGE_BASE_NAME+".MyUSBManager_DefaultUSBDevice.5824:2002";
    String KEY_LOCATION_SETTINGS = PACKAGE_BASE_NAME+".MyLocationManager_LOCATION.KEY_LOCATION_SETTINGS";

    String KEY_CONNECTION_TYPE = PACKAGE_BASE_NAME+".KEY_CONNECTION_TYPE";

    enum ConnectionType {
        WIRELESS_NETWORK,
        MOBILE_HOTSPOT,
        BLUETOOTH
    }

    String DEF_FILE_NAME_CALIBRATION = "calib.txt";

    int REQUEST_ALL_PERMISSIONS_CODE = 9432;
    int REQUEST_PARTIAL_PERMISSIONS_CODE = 2345;



    static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }
}
