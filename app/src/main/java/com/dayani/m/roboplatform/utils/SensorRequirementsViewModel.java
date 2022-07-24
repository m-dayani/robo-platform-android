package com.dayani.m.roboplatform.utils;

import androidx.lifecycle.ViewModel;


public class SensorRequirementsViewModel extends ViewModel {

    public SensorRequirementsViewModel(SensorsContainer sensors) {

        mSensorsContainer = sensors;
    }

    public SensorsContainer getSensorsContainer() {
        return mSensorsContainer;
    }

    public void setSensorsContainer(SensorsContainer mSensorsContainer) {
        this.mSensorsContainer = mSensorsContainer;
    }

    private SensorsContainer mSensorsContainer;
}
