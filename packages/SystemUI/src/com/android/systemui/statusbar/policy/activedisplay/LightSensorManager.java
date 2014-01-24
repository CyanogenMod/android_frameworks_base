/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.statusbar.policy.activedisplay;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Manages the light sensor and notifies a listener when enabled.
 */
public class LightSensorManager {
    /**
     * Listener of the state of the light sensor.
     * <p>
     * This interface abstracts possible states for the light sensor.
     * <p>
     * The actual meaning of these states depends on the actual sensor.
     */
    public interface LightListener {
        /** Called when the light sensor transitions from the brighter to the darker state. */
        public void onDarker();
        /** Called when the light sensor transitions from the darker to the brighter state. */
        public void onBrighter();
    }

    public static enum State {
        BRIGHTER, DARKER
    }

    private final LightSensorEventListener mLightSensorListener;

    /**
     * The current state of the manager, i.e., whether it is currently tracking the state of the
     * sensor.
     */
    private boolean mManagerEnabled;

    /**
     * The listener to the state of the sensor.
     * <p>
     * Contains most of the logic concerning tracking of the sensor.
     * <p>
     * After creating an instance of this object, one should call {@link #register()} and
     * {@link #unregister()} to enable and disable the notifications.
     */
    private static class LightSensorEventListener implements SensorEventListener {
        private final SensorManager mSensorManager;
        private final Sensor mLightSensor;
        private final float mMaxValue;
        private final LightListener mListener;

        /**
         * The last state of the sensor.
         * <p>
         * Before registering and after unregistering we are always in the {@link State#DARKER} state.
         */
        private State mLastState;
        /**
         * If this flag is set to true, we are waiting to reach the {@link State#DARKER} state and
         * should notify the listener and unregister when that happens.
         */
        private boolean mWaitingForDarkerState;

        public LightSensorEventListener(SensorManager sensorManager, Sensor lightSensor,
                LightListener listener) {
            mSensorManager = sensorManager;
            mLightSensor = lightSensor;
            mMaxValue = lightSensor.getMaximumRange();
            mListener = listener;
            // Initialize at darkerer state.
            mLastState = State.DARKER;
            mWaitingForDarkerState = false;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Make sure we have a valid value.
            if (event.values == null) return;
            if (event.values.length == 0) return;
            float value = event.values[0];
            // Convert the sensor into a DARKER/BRIGHTER state.
            State state = getStateFromValue(value);
            synchronized (this) {
                // No change in state, do nothing.
                if (state == mLastState) return;
                // Keep track of the current state.
                mLastState = state;
                // If we are waiting to reach the darker state and we are now in it, unregister.
                if (mWaitingForDarkerState && mLastState == State.DARKER) {
                    unregisterWithoutNotification();
                }
            }
            // Notify the listener of the state change.
            switch (state) {
                case DARKER:
                    mListener.onDarker();
                    break;

                case BRIGHTER:
                    mListener.onBrighter();
                    break;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Nothing to do here.
        }

        /** Returns the state of the sensor given its current value. */
        private State getStateFromValue(float value) {
            // Determine if the current value corresponds to the DARKER or BRIGHTER state.
            // Take case of the case where the proximity sensor is binary: if the current value is
            // equal to the maximum, we are always in the DARKER state.
            return (value > (mMaxValue * 0.8f)) ? State.BRIGHTER : State.DARKER;
        }

        /**
         * Unregister the next time the sensor reaches the {@link State#DARKER} state.
         */
        public synchronized void unregisterWhenDarker() {
            if (mLastState == State.DARKER) {
                // We are already in the darker state, just unregister now.
                unregisterWithoutNotification();
            } else {
                mWaitingForDarkerState = true;
            }
        }

        /** Register the listener and call the listener as necessary. */
        public synchronized void register() {
            // It is okay to register multiple times.
            mSensorManager.registerListener(this, mLightSensor, SensorManager.SENSOR_DELAY_UI);
            // We should no longer be waiting for the darker state if we are registering again.
            mWaitingForDarkerState = false;
        }

        public void unregister() {
            State lastState;
            synchronized (this) {
                unregisterWithoutNotification();
                lastState = mLastState;
                // Always go back to the DARKER state. That way, when we register again we will get a
                // transition when the sensor gets into the DARKER state.
                mLastState = State.DARKER;
            }
            // Notify the listener if we changed the state to DARKER while unregistering.
            if (lastState != State.DARKER) {
                mListener.onDarker();
            }
        }

        private void unregisterWithoutNotification() {
            mSensorManager.unregisterListener(this);
            mWaitingForDarkerState = false;
        }
    }

    public LightSensorManager(Context context, LightListener listener) {
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor == null) {
            // If there is no sensor, we should not do anything.
            mLightSensorListener = null;
        } else {
            mLightSensorListener =
                    new LightSensorEventListener(sensorManager, lightSensor, listener);
        }
    }

    /**
     * Enables the light manager.
     * <p>
     * The listener will start getting notifications of events.
     * <p>
     * This method is idempotent.
     */
    public void enable() {
        if (mLightSensorListener != null && !mManagerEnabled) {
            mLightSensorListener.register();
            mManagerEnabled = true;
        }
    }

    /**
     * Disables the light manager.
     * <p>
     * The listener will stop receiving notifications of events, possibly after receiving a last
     * {@link Listener#onDarker()} callback.
     * <p>
     * If {@code waitForDarkState} is true, if the sensor is not currently in the {@link State#DARKER}
     * state, the listener will receive a {@link Listener#onDarker()} callback the next time the sensor
     * actually reaches the {@link State#DARKER} state.
     * <p>
     * If {@code waitForDarkerState} is false, the listener will receive a {@link Listener#onDarker()}
     * callback immediately if the sensor is currently not in the {@link State#DARKER} state.
     * <p>
     * This method is idempotent.
     */
    public void disable(boolean waitForDarkerState) {
        if (mLightSensorListener != null && mManagerEnabled) {
            if (waitForDarkerState) {
                mLightSensorListener.unregisterWhenDarker();
            } else {
                mLightSensorListener.unregister();
            }
            mManagerEnabled = false;
        }
    }
}
