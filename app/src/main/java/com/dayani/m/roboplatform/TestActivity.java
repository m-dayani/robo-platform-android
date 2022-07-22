package com.dayani.m.roboplatform;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.dayani.m.roboplatform.managers.MyUSBManager;

public class TestActivity extends AppCompatActivity implements View.OnClickListener,
        MyUSBManager.OnUsbConnectionListener {

    private static final String TAG = TestActivity.class.getSimpleName();


    private MyUSBManager mUsb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        findViewById(R.id.enumDevs).setOnClickListener(this);
        findViewById(R.id.openDefaultDev).setOnClickListener(this);
        findViewById(R.id.sendState).setOnClickListener(this);
        findViewById(R.id.recieveSensor).setOnClickListener(this);
        findViewById(R.id.runTest).setOnClickListener(this);

        mUsb = new MyUSBManager(this, this, new StringBuffer());
        mUsb.updateDefaultDeviceAvailability();
    }

    @Override
    protected void onDestroy() {
        mUsb.clean();
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.enumDevs: {
                TextView bili = (TextView) findViewById(R.id.reportText);
                bili.setText(mUsb.enumerateDevices());
                break;
            }
            case R.id.openDefaultDev: {
                mUsb.tryOpenDefaultDevice();
                break;
            }
            case R.id.sendState: {
                mUsb.sendStateUpdates();
                break;
            }
            case R.id.recieveSensor: {
                TextView bili = (TextView) findViewById(R.id.reportText);
                bili.setText(mUsb.receiveSensor());
                break;
            }
            case R.id.runTest: {
                mUsb.testDevice();
                break;
            }
        }
    }

    @Override
    public void onUsbConnection(boolean connStat) {

    }
}
