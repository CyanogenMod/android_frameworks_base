/*
 * Copyright (C) 2014 ParanoidAndroid Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class SensorActivityHandler {

    private final static String TAG = "Peek.SensorActivityHandler";

    private final static int INCREMENTS_TO_DISABLE = 5;
    private final static float NOISE_THRESHOLD = 0.5f;

    private SensorManager mSensorManager;
    private SensorEventListener mProximityEventListener;
    private SensorEventListener mGyroscopeEventListener;
    private Sensor mProximitySensor;
    private Sensor mGyroscopeSensor;

    private ScreenReceiver mScreenReceiver;

    private SensorChangedCallback mCallback;
    private Context mContext;

    private float mLastX = 0, mLastY = 0, mLastZ = 0;
    private int mSensorIncrement = 0;

    private boolean mWaitingForMovement;
    private boolean mHasInitialValues;
    private boolean mScreenReceiverRegistered;
    private boolean mProximityRegistered;
    private boolean mGyroscopeRegistered;

    private boolean mInPocket;
    private boolean mOnTable = true;

    public SensorActivityHandler(Context context, SensorChangedCallback callback) {
        mContext = context;
        mCallback = callback;

        mScreenReceiver = new ScreenReceiver();

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // get proximity sensor for in-pocket detection
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (mProximitySensor != null) {
            mProximityEventListener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }

                @Override
                public void onSensorChanged(SensorEvent event) {
                    boolean inPocket = event.values[0] == 0;
                    if (inPocket) {
                        mOnTable = false; // we can't have phone on table and pocket at the same time
                        unregisterGyroscopeEvent();
                    } else {
                        if (!mGyroscopeRegistered) {
                            registerEventListeners();
                            mSensorManager.registerListener(mGyroscopeEventListener,
                                    mGyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL);
                        }
                    }
                    if (Peek.DEBUG) {
                        Log.d(TAG, "In pocket: "+inPocket + ", old: " + mInPocket);
                    }
                    boolean oldInPocket = mInPocket;
                    mInPocket = inPocket;
                    if (oldInPocket != inPocket) mCallback.onPocketModeChanged(mInPocket);
                }
            };
        } else {
            // ugh, that's bad, run now that you can
        }

        // get gyroscope sensor for on-table detection
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (mGyroscopeSensor != null) {
            mGyroscopeEventListener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                }

                @Override
                public void onSensorChanged(SensorEvent event) {
                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[1];
                    if (Peek.DEBUG) Log.d(TAG, "Received values: x: " + x + " y: " + y + " z: " + z);
                    boolean storeValues = false;
                    if (mHasInitialValues) {
                        float dX = Math.abs(mLastX - x);
                        float dY = Math.abs(mLastY - y);
                        float dZ = Math.abs(mLastY - z);
                        if (dX >= NOISE_THRESHOLD ||
                                dY >= NOISE_THRESHOLD || dZ >= NOISE_THRESHOLD) {
                            if (mWaitingForMovement) {
                                if (Peek.DEBUG) Log.d(TAG, "On table: false");
                                mOnTable = false;
                                mCallback.onTableModeChanged(mOnTable);
                                registerEventListeners();
                                mWaitingForMovement = false;
                                mSensorIncrement = 0;
                            }
                            storeValues = true;
                        } else {
                            if (mOnTable) { // we are assuming that device is on table for now.
                                unregisterProximityEvent();
                                mWaitingForMovement = true;
                                return;
                            }
                            if (mSensorIncrement < INCREMENTS_TO_DISABLE) {
                                mSensorIncrement++;
                                if (mSensorIncrement == INCREMENTS_TO_DISABLE) {
                                    unregisterProximityEvent();
                                    if (Peek.DEBUG) Log.d(TAG, "On table: true");
                                    mOnTable = true;
                                    mCallback.onTableModeChanged(mOnTable);
                                    mWaitingForMovement = true;
                                }
                            }
                        }
                    }

                    if (!mHasInitialValues || storeValues) {
                        mHasInitialValues = true;
                        mLastX = x;
                        mLastY = y;
                        mLastZ = z;
                    }
                }
            };
        } else {
            // no gyroscope? time to buy a nexus
        }
    }

    public boolean isInPocket() {
        return mInPocket;
    }

    public boolean isOnTable() {
        return mOnTable;
    }

    public void registerScreenReceiver() {
        if (!mScreenReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mScreenReceiver, intentFilter);
            mScreenReceiverRegistered = true;
        }
    }

    public void unregisterScreenReceiver() {
        if (mScreenReceiverRegistered) {
            mContext.unregisterReceiver(mScreenReceiver);
            mScreenReceiverRegistered = false;
        }
    }

    public void registerEventListeners() {
        registerProximityEvent();
        registerGyroscopeEvent();
    }

    public void registerProximityEvent() {
        if (!mProximityRegistered) {
            if (Peek.DEBUG) Log.d(TAG, "Registering proximity polling");
            mSensorManager.registerListener(mProximityEventListener, mProximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mProximityRegistered = true;
        }
    }

    public void registerGyroscopeEvent() {
        if (!mGyroscopeRegistered) {
            if (Peek.DEBUG) Log.d(TAG, "Registering gyroscope polling");
            mSensorManager.registerListener(mGyroscopeEventListener, mGyroscopeSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            mGyroscopeRegistered = true;
        }
    }

    public void unregisterEventListeners() {
        unregisterProximityEvent();
        unregisterGyroscopeEvent();
    }

    private void unregisterProximityEvent() {
        if (mProximityRegistered) {
            if (Peek.DEBUG) Log.d(TAG, "Unregistering proximity polling");
            mSensorManager.unregisterListener(mProximityEventListener);
            mProximityRegistered = false;
        }
    }

    private void unregisterGyroscopeEvent() {
        if (mGyroscopeRegistered) {
            if (Peek.DEBUG) Log.d(TAG, "Unregistering gyroscope polling");
            mSensorManager.unregisterListener(mGyroscopeEventListener);
            mLastX = mLastY = mLastZ = 0;
            mSensorIncrement = 0;
            mGyroscopeRegistered = false;
            mHasInitialValues = false;
        }
    }

    public interface SensorChangedCallback {
        public abstract void onPocketModeChanged(boolean inPocket);
        public abstract void onTableModeChanged(boolean onTable);
        public abstract void onScreenStateChaged(boolean screenOn);
    }

    public class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (Peek.DEBUG) Log.d(TAG, "Screen is off");
                mCallback.onScreenStateChaged(false);
                registerEventListeners();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (Peek.DEBUG) Log.d(TAG, "Screen is on");
                mCallback.onScreenStateChaged(true);
                unregisterEventListeners();
                mInPocket = false;
                mOnTable = true; // let's assume phone is back on table
            }
        }
    }
}
