package com.dayani.m.roboplatform.utils;

import androidx.fragment.app.Fragment;

public interface OnRequestPageChange {
    void onRequestPageChange(Fragment targetFragment, String backStackName);
}
