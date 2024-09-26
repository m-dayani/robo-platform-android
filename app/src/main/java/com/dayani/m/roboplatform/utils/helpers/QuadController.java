package com.dayani.m.roboplatform.utils.helpers;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

public class QuadController {

    private static final String TAG = QuadController.class.getSimpleName();

    // State params
    // Max Strength, default: 255 (char)
    private final float mMaxStrength;
    private final char STATE_LEN = 4;
    private float[] mState = new float[STATE_LEN];
    private float[] mError = new float[STATE_LEN];
    private float mThrottle = 0.f;
    private float mThThrottle = 0.1f;

    private long mLastTs = -1;
    private final float mUpdateRate = 100;
    private final float mUpdatePer = 1.f / mUpdateRate;

    // Sensor params
    private final float s_gx = 0.25f, s_gy = 0.25f, s_gz = 0.25f, s_gn = 1e-6f;
    private float[] mSenState = new float[STATE_LEN];
    private float mSenWeight = 0.f;
    private char mMaxMa = 3;
    ArrayList<float[]> mGravityArr = new ArrayList<>();

    private final float s_rx = 0.25f, s_ry = 0.25f, s_rz = 0.25f;

    // Input params
    private final float s_t = 0.25f, s_r = 0.25f, s_p = 0.25f, s_y = 0.25f;
    private float[] mInputState = new float[STATE_LEN];
    private boolean mbFirstInputReceived = false;

    // PID params
    private final float pid_p = 5.f, pid_i = 0.f, pid_d = 0.f;

    public QuadController() {
        mMaxStrength = 255.f;
    }

    public QuadController(float maxStrength_) {
        mMaxStrength = maxStrength_;
    }

    public synchronized void updateState(byte[] state) {

        if (state == null || state.length < STATE_LEN) {
            return;
        }
        for (char i = 0; i < STATE_LEN; i++) {
            mState[i] = state[i];
        }
    }

    public synchronized void updateSensor(SensorEvent sensorEvent) {

        if (sensorEvent == null) {
            Log.w(TAG, "Detected null sensor event");
            return;
        }

        int sensorType = sensorEvent.sensor.getType();
        float[] sensorValues = sensorEvent.values;

        if (sensorType == Sensor.TYPE_GRAVITY || sensorType == Sensor.TYPE_ACCELEROMETER) {
            if (mGravityArr.size() < mMaxMa) {
                mGravityArr.add(sensorValues);
            }
            else {
                float[] g_vec = mean_float_arr(mGravityArr);
                mSenState = add_float_arr(mSenState, calc_gravity(g_vec));
                mGravityArr.clear();
            }
        }

        if (sensorType == Sensor.TYPE_GYROSCOPE) {
            mSenState = add_float_arr(mSenState, calc_gyro(sensorValues));
        }

        if (norm(mSenState) > 10) {
            Log.v(TAG, "last sensor state: " + Arrays.toString(mSenState));
        }
    }

    public synchronized void updateInput(float[] input) {

        if (!mbFirstInputReceived) {
            mbFirstInputReceived = true;
        }
        mInputState = calc_input(input);
        if (Math.abs(input[0]) > 1e-3) {
            updateThrottle(calc_throttle(mState, mInputState));
        }
    }

    public synchronized byte[] getLastState() {

        // update last state
        float senWeight = mSenWeight;
        if (getThrottle() < mThThrottle) {
            // sensor has no effect in low throttle
            senWeight = 0.f;
        }
        mError = add_float_arr(scale_float_arr(mSenState, senWeight), mInputState);
        mState = normalizeThrottle(calc_state(mState, mError));
        // reset sensor state to avoid accumulation of error
        mSenState = new float[STATE_LEN];

        // convert state to bytes array
        byte[] outBuff = new byte[STATE_LEN];
        for (char i = 0; i < STATE_LEN; i++) {
            outBuff[i] = (byte) mState[i];
        }
        return outBuff;
    }

    public synchronized void updateSensorWeight(float weight) {
        if (mbFirstInputReceived) {
            mSenWeight = (sigmoid(weight, 2) - 0.5f) * 2.f;
        }
    }

    public synchronized boolean isReady() {
        if (mLastTs < 0) {
            mLastTs = SystemClock.elapsedRealtimeNanos();
            return true;
        }
        long currTs = SystemClock.elapsedRealtimeNanos();
        boolean isReady = (currTs - mLastTs) * 1e-9 >= mUpdatePer;
        mLastTs = currTs;
        return isReady;
    }

    public synchronized float getThrottle() { return mThrottle; }
    private synchronized void updateThrottle(float throt) { mThrottle = throt; }

    private float calc_throttle(float[] lastState, float[] inputState) {

        float[] newState = calc_state(lastState, inputState);
        return mean_float_arr(newState);
    }

    private float[] calc_state(float[] lastState, float[] error) {

        if (lastState == null || error == null || lastState.length != error.length ||
                error.length < STATE_LEN) {
            return mState;
        }

        float[] state = new float[STATE_LEN];
        for (char i = 0; i < STATE_LEN; i++) {
            float state_i = pid_p * error[i] + lastState[i];
            state_i = clip_float(state_i, 0, mMaxStrength);
            state[i] = state_i;
        }

        return state;
    }

    private float[] normalizeThrottle(float[] state) {

        float newThrottle = mean_float_arr(state);
        float thDiff = getThrottle() - newThrottle;
        float addedTh = thDiff / 4.f;
        for (char i = 0; i < STATE_LEN; i++) {
            state[i] = clip_float(state[i] + addedTh, 0, mMaxStrength);
        }
        return state;
    }

    private float[] calc_input(float[] input) {

        if (input == null || input.length < STATE_LEN) {
            return mInputState;
        }

        float[] out = new float[STATE_LEN];

        out[0] = s_t * input[0] - s_r * input[1] - s_p * input[2] + s_y * input[3];
        out[1] = s_t * input[0] + s_r * input[1] - s_p * input[2] - s_y * input[3];
        out[2] = s_t * input[0] + s_r * input[1] + s_p * input[2] + s_y * input[3];
        out[3] = s_t * input[0] - s_r * input[1] + s_p * input[2] - s_y * input[3];

        return out;
    }

    private float[] calc_gravity(float[] g_vec) {

        if (g_vec == null || g_vec.length < 3) {
            return new float[STATE_LEN];
        }

        float[] out = new float[STATE_LEN];

        float gx = g_vec[0];
        float gy = g_vec[1];
        float gz = g_vec[2];
        float g_n = Math.round(Math.sqrt(gx*gx + gy*gy + gz*gz));
        float gx_n = gx / g_n;
        float gy_n = gy / g_n;
        float gz_n = gz / g_n;
        float gxy_n = Math.round(Math.sqrt(gx_n*gx_n + gy_n*gy_n));
        updateSensorWeight(gxy_n);

        if (gz_n > 0) {
            // only consider gz when it's flipped, when you should suddenly drop the throttle
            gz_n = 0;
        }

        out[0] = - s_gx * gx_n - s_gy * gy_n + s_gz * gz_n + s_gn * g_n;
        out[1] = - s_gx * gx_n + s_gy * gy_n + s_gz * gz_n + s_gn * g_n;
        out[2] =   s_gx * gx_n + s_gy * gy_n + s_gz * gz_n + s_gn * g_n;
        out[3] =   s_gx * gx_n - s_gy * gy_n + s_gz * gz_n + s_gn * g_n;

        return out;
    }

    private float[] calc_gyro(float[] gyro_mea) {

        if (gyro_mea == null || gyro_mea.length < 3) {
            return new float[STATE_LEN];
        }

        float[] out = new float[STATE_LEN];

        out[0] = - s_rx * gyro_mea[0] + s_ry * gyro_mea[1] - s_rz * gyro_mea[2];
        out[1] =   s_rx * gyro_mea[0] + s_ry * gyro_mea[1] + s_rz * gyro_mea[2];
        out[2] =   s_rx * gyro_mea[0] - s_ry * gyro_mea[1] - s_rz * gyro_mea[2];
        out[3] = - s_rx * gyro_mea[0] - s_ry * gyro_mea[1] + s_rz * gyro_mea[2];

        return out;
    }

    public static float[] add_float_arr(float[] a, float[] b) {

        if (a == null || b == null || a.length != b.length) {
            return null;
        }

        float[] c = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }

        return c;
    }

    public static float[] scale_float_arr(float[] a, float scale) {
        if (a == null) {
            return null;
        }

        float[] out = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = a[i] * scale;
        }
        return out;
    }

    public static float norm(float[] a) {
        if (a == null) {
            return 0.f;
        }

        int len = a.length;
        float n = 0.f;
        for (float ai : a) {
            n += (ai * ai);
        }

        return Math.round(Math.sqrt(n)/len);
    }

    public static float mean_float_arr(float[] a) {
        if (a == null) {
            return 0.f;
        }
        float mu = 0.f;
        for (float v : a) {
            mu += v;
        }
        return mu / (a.length);
    }

    public static float[] mean_float_arr(ArrayList<float[]> arrFloats) {

        float[] out = null;
        char cnt = 0;
        for (float[] f_arr : arrFloats) {
            if (out == null) {
                out = f_arr;
            }
            else {
                out = add_float_arr(out, f_arr);
            }
            cnt++;
        }
        if (cnt != 0) {
            return scale_float_arr(out, 1.f / cnt);
        }
        return out;
    }

    public static float clip_float(float v, float min_v, float max_v) {
        float r = v;
        if (v > max_v) {
            r = max_v;
        }
        else if (v < min_v) {
            r = min_v;
        }
        return r;
    }

    public static float sigmoid(float x, float c0) {

        return (float) Math.round(1.f / (Math.exp(-x * c0) + 1.f));
    }
}
