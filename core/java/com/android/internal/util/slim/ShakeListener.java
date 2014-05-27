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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeListener implements SensorEventListener {

    private static final int MAX_DURATION = 400;
    private static final int MIN_SHAKES = 4;

    private float mGravX = 0.0f;
    private float mGravY = 0.0f;
    private float mGravZ = 0.0f;
    private float mAccelX = 0.0f;
    private float mAccelY = 0.0f;
    private float mAccelZ = 0.0f;

    private long mInitialShake = 0;
    private long mLastShake = 0;
    private int mShakeCount = 0;

    private boolean mCoolOff;
    private int mMinAccel = 12;
    /* mAccelDirection grid-locks the shake listener
     * to listen to only one direction for each series of
     * shakes.  This allows the user to shake in any back
     * and forth direction while not defining which
     * direction they must choose
     */
    private int mAccelDirection = -1;
    private int mLastShakeDirection = -1;

    private Context mContext;

    private SensorManager mSensorManager;
    private OnShakeListener mShakeListener;
    private Handler mHandler = new Handler();

    public ShakeListener(Context context) {
        mContext = context;
    }

    public void setOnShakeListener(OnShakeListener shakeListener) {
        mShakeListener = shakeListener;
    }

    /*
     * Defaults assume a very intentional shake
     * Sensitivity may need adjusting in other situations
     */
    public void setSensitivity(boolean coolOff, int sensitivity) {
        switch (sensitivity) {
            case -4:
                // Ultra sensitive (reacts to nearly everything)
                mMinAccel = 6;
                break;
            case -3:
                // Super sensitive
                mMinAccel = 7;
                break;
            case -2:
                // Very sensitive
                mMinAccel = 8;
                break;
            case -1:
                // More sensitive
                mMinAccel = 10;
                break;
            case 1:
                // Less sensitive
                mMinAccel = 14;
                break;
            case 2:
                // Very insensitive (don't expect to love this one)
                mMinAccel = 15;
                break;
            default:
                // Default no change
                mMinAccel = 12;
                break;
        }
        mCoolOff = coolOff;
    }

    public void setDestroyEvents(final String[] events) {
        IntentFilter filter = new IntentFilter();
        for (int i = 0; i < events.length; i++) {
            filter.addAction(events[i]);
        }
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    public interface OnShakeListener {
        // The direction of the shake is passed
        public void onShake(int direction);
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

        mAccelX = event.values[SensorManager.DATA_X] - mGravX + 1.0f;
        mAccelY = event.values[SensorManager.DATA_Y] - mGravY + 1.5f;
        mAccelZ = event.values[SensorManager.DATA_Z] - mGravZ;

        float maxLinearAcceleration = 0.0f;

        switch (mAccelDirection) {
            case SensorManager.DATA_X:
                maxLinearAcceleration = mAccelX;
                break;
            case SensorManager.DATA_Y:
                maxLinearAcceleration = mAccelY;
                break;
            case SensorManager.DATA_Z:
                maxLinearAcceleration = mAccelZ;
                break;
            default:
                maxLinearAcceleration = mAccelX;
                mAccelDirection = SensorManager.DATA_X;
                if (mAccelY > maxLinearAcceleration) {
                    mAccelDirection = SensorManager.DATA_Y;
                    maxLinearAcceleration = mAccelY;
                }
                if (mAccelZ > maxLinearAcceleration) {
                    mAccelDirection = SensorManager.DATA_Z;
                    maxLinearAcceleration = mAccelZ;
                }
                if (maxLinearAcceleration < mMinAccel) {
                    mAccelDirection = -1;
                }
                break;
        }

        if (maxLinearAcceleration >= mMinAccel) {
            mHandler.removeCallbacks(resetDirection);
            mHandler.postDelayed(resetDirection, MAX_DURATION);
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
                        if (mLastShakeDirection != mAccelDirection) {
                            // Set directly to x,y,z - 0,1,2 - AOSP changes
                            // thus won't affect our shake listeners and we
                            // can keep the values for all future versions
                            switch (mAccelDirection) {
                                case SensorManager.DATA_X:
                                    mShakeListener.onShake(0);
                                    break;
                                case SensorManager.DATA_Y:
                                    mShakeListener.onShake(1);
                                    break;
                                case SensorManager.DATA_Z:
                                    mShakeListener.onShake(2);
                                    break;
                            }
                            if (mCoolOff) {
                                mLastShakeDirection = mAccelDirection;
                                mHandler.removeCallbacks(delayShakes);
                                mHandler.postDelayed(delayShakes, MAX_DURATION + 300);
                            }
                        }
                        resetShakes();
                    }
                }
            } else {
                // Gravity met but too slow
                resetShakes();
            }
        }
    }

    private void resetShakes() {
        mGravX = 0.0f;
        mGravY = 0.0f;
        mGravZ = 0.0f;
        mInitialShake = 0;
        mLastShake = 0;
        mShakeCount = 0;
        mAccelDirection = -1;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    final Runnable delayShakes = new Runnable () {
        public void run() {
            mLastShakeDirection = -1;
        }
    };

    final Runnable resetDirection = new Runnable () {
        public void run() {
            resetShakes();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            unregisterShakeListener();
            ShakeListener.this.mContext.unregisterReceiver(mBroadcastReceiver);
        }
    };

}