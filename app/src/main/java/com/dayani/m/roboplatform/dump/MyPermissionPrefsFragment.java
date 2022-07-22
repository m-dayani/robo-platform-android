package com.dayani.m.roboplatform.dump;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.managers.MyStateManager;
import com.dayani.m.roboplatform.utils.AppGlobals;


public class MyPermissionPrefsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceClickListener {

    private static final String TAG = MyPermissionPrefsFragment.class.getSimpleName();



    /*@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_settings, container, false);
    }*/

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.permissions, rootKey);

        updateAllStates();

        Preference editPermissions = findPreference(AppGlobals.KEY_EDIT_PERMISSIONS_PREF);
        editPermissions.setOnPreferenceClickListener(this);
        //editPermissions.setIntent(getEditPermissionsIntent());

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult");
    }

    /*@Override
    public void onResume() {
        super.onResume();
        updatePermissionStates();
    }*/

    /**
     * @warning This function uses the MainActivity's UsbAvailabilityReceiver.
     *          If used independently, must register this receiver here if
     *          we need to update USB states here.
     * @param preference
     * @return
     */
    @Override
    public boolean onPreferenceClick(Preference preference) {
        Log.d(TAG, "onPermissionPreferencesClick");
        //without this there will be error when user deny a permission
        //and turns back to this activity -> TODO: why?
        if (preference.getKey().equals(AppGlobals.KEY_EDIT_PERMISSIONS_PREF)) {
            //getActivity().finish();
            getActivity().startActivity(getEditPermissionsIntent());
            return false;
        }
        return true;
    }

    private Intent getEditPermissionsIntent() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", AppGlobals.PACKAGE_BASE_NAME, null);
        intent.setData(uri);
        //intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    private void updatePermissionStates() {
        PreferenceCategory cat = findPreference("permissions_category");
        int catCount = cat.getPreferenceCount();

        for (int i = 0; i < catCount; i++) {
            Preference pref = cat.getPreference(i);
            String prefKey = pref.getKey();
            CharSequence prefTxt = pref.getTitle();
            boolean stat = MyStateManager.getBoolPref(getContext(),prefKey,false);
            if (stat) {
                pref.setTitle(prefTxt+": Granted");
                pref.setIcon(R.drawable.ic_action_accept);
            } else {
                pref.setTitle(prefTxt+": Denied");
                pref.setIcon(R.drawable.ic_action_cancel);
            }
        }
    }

    private void updateAllStates() {
        updatePermissionStates();
    }
}

