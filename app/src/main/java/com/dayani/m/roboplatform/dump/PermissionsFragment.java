package com.dayani.m.roboplatform.dump;

import androidx.lifecycle.ViewModelProviders;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dayani.m.roboplatform.R;
import com.dayani.m.roboplatform.utils.PermissionsViewModel;

public class PermissionsFragment extends Fragment {

    private PermissionsViewModel mViewModel;

    public static PermissionsFragment newInstance() {
        return new PermissionsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.z_permissions_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(PermissionsViewModel.class);
        // TODO: Use the ViewModel
    }

}
