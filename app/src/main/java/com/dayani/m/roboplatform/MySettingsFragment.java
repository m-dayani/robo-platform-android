package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class MySettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    /*@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_settings, container, false);
    }*/

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        //select the pref
        //set this class as onPrefChangeListener
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.e("preference", "Pending Preference value is: " + newValue);
        return true;
    }
}
