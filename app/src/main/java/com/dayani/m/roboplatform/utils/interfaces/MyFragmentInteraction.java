package com.dayani.m.roboplatform.utils.interfaces;

import androidx.fragment.app.Fragment;

public interface MyFragmentInteraction {
    void onRequestPageChange(Fragment targetFragment, String backStackName);
    void onRequestPageRemove(String backStackName);
}
