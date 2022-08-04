package com.dayani.m.roboplatform.utils;

import androidx.fragment.app.Fragment;

public interface MyFragmentInteraction {
    void onRequestPageChange(Fragment targetFragment, String backStackName);
    void onRequestPageRemove(String backStackName);
}
