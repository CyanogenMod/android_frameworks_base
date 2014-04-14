/*
 * Copyright (C) 2014 SlimRom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.slim;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeListener implements SensorEventListener {

    private static final int MIN_ACCEL = 12;
    private static final int MIN_SHAKES = 5;
    private static final int MAX_DURATION = 400;

    private static final int X_ACCEL = 0;
    private static final int Y_ACCEL = 1;
    private static final int Z_ACCEL = 2;

    private float mGravX = 0.0f;
    private float mGravY = 0.0f;
    private float mGravZ = 0.0f;
    private float mAccelX = 0.0f;
    private float mAccelY = 0.0f;
    private float mAccelZ = 0.0f;

    private long mInitialShake = 0;
    private long mLastShake = 0;
    private int mShakeCount = 0;

    /* mAccelDirection grid-locks the shake listener
     * to listen to only one direction for each series of
     * shakes.  This allows the user to shake in any back
     * and forth direction while not defining which
     * direction they must choose
     */
    private int mAccelDirection = -1;

    private Context mContext;

    private SensorManager mSensorManager;
    private OnShakeListener mShakeListener;

    public ShakeListener(Context context) {
        mContext = context;
    }

    public void setOnShakeListener(OnShakeListener shakeListener) {
        mShakeListener = shakeListener;
    }

    public interface OnShakeListener {
        public void onShake();
    }

    private SensorManager getSensorManager() {
        if (mSensorManager == null) {
            mSensorManager = (SensorManager)
                    mContext.getSystemService(Context.SENSOR_SERVICE);
        }
        return mSensorManager;
    }

    public void registerShakeListener() {
        getSensorManager().registerListener(this,
                getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_GAME);
    }

    public void unregisterShakeListener() {
        getSensorManager().unregisterListener(this);
        mSensorManager = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float alpha = 0.8f;

        mGravX = alpha * mGravX + (1 - alpha) * event.values[SensorManager.DATA_X];
        mGravY = alpha * mGravY + (1 - alpha) * event.values[SensorManager.DATA_Y];
        mGravZ = alpha * mGravZ + (1 - alpha) * event.values[SensorManager.DATA_Z];

        mAccelX = event.values[SensorManager.DATA_X] - mGravX;
        mAccelY = event.values[SensorManager.DATA_Y] - mGravY;
        mAccelZ = event.values[SensorManager.DATA_Z] - mGravZ;

        float maxLinearAcceleration = 0.0f;

        switch (mAccelDirection) {
            case X_ACCEL:
                maxLinearAcceleration = mAccelX;
                break;
            case Y_ACCEL:
                maxLinearAcceleration = mAccelY;
                break;
            case Z_ACCEL:
                maxLinearAcceleration = mAccelZ;
                break;
            default:
                maxLinearAcceleration = mAccelX;
                mAccelDirection = X_ACCEL;
                if (mAccelY > maxLinearAcceleration) {
                    mAccelDirection = Y_ACCEL;
                    maxLinearAcceleration = mAccelY;
                }
                if (mAccelZ > maxLinearAcceleration) {
                    mAccelDirection = Z_ACCEL;
                    maxLinearAcceleration = mAccelZ;
                }
                if (maxLinearAcceleration < MIN_ACCEL) {
                    mAccelDirection = -1;
                }
                break;
        }

        if (maxLinearAcceleration >= MIN_ACCEL) {
            long now = System.currentTimeMillis();
            boolean firstShake = false;

            if (mInitialShake == 0) {
                firstShake = true;
                mInitialShake = now;
                mLastShake = now;
            }

            long elapsedTime = now - mInitialShake;

            if (elapsedTime <= MAX_DURATION) {
                // Grid-lock to ensure we aren't reading the same shake
                // multiple times and bloating our counter
                if (firstShake || ((now - mLastShake) >= 15)) {
                    mLastShake = now;
                    mShakeCount++;
                    if (mShakeCount >= MIN_SHAKES) {
                        mShakeListener.onShake();
                        mInitialShake = 0;
                        mLastShake = 0;
                        mShakeCount = 0;
                        mAccelDirection = -1;
                    }
                }
            } else {
                // Gravity met but too slow
                mInitialShake = now;
                mLastShake = now;
                mShakeCount = 1;
                mAccelDirection = -1;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

}