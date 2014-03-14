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

public class ShakeSensorManager {

    public interface ShakeListener {
        public void onShake();
    }

    private final ShakeSensorEventListener mShakeSensorListener;

    private boolean mManagerEnabled;

    private static float SENSITIVITY = 10;

    private static class ShakeSensorEventListener implements SensorEventListener {
        private static final int BUFFER = 5;
        private float[] gravity = new float[3];
        private float average = 0;
        private int fill = 0;

        private final SensorManager mSensorManager;
        private final Sensor mShakeSensor;
        private final ShakeListener mListener;

        public ShakeSensorEventListener(SensorManager sensorManager, Sensor shakeSensor,
                ShakeListener listener) {
            mSensorManager = sensorManager;
            mShakeSensor = shakeSensor;
            mListener = listener;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            final float alpha = 0.8F;
            for (int i = 0; i < 3; i++) {
                 gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i];
            }
            float x = event.values[0] - gravity[0];
            float y = event.values[1] - gravity[1];
            float z = event.values[2] - gravity[2];

            if (fill <= BUFFER) {
                average += Math.abs(x) + Math.abs(y) + Math.abs(z);
                fill++;
            } else {
                if (average / BUFFER >= SENSITIVITY) {
                    mListener.onShake();
                }
                average = 0;
                fill = 0;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public synchronized void unregisterWhenIdle() {
            unregisterWithoutNotification();
        }

        public synchronized void register() {
            mSensorManager.registerListener(this, mShakeSensor,
                   SensorManager.SENSOR_DELAY_GAME,
                   50 * 1000);
        }

        public void unregister() {
            synchronized (this) {
                unregisterWithoutNotification();
            }
        }

        private void unregisterWithoutNotification() {
            mSensorManager.unregisterListener(this);
        }
    }

    public ShakeSensorManager(Context context, ShakeListener listener) {
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor shakeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (shakeSensor == null) {
            mShakeSensorListener = null;
        } else {
            mShakeSensorListener =
                    new ShakeSensorEventListener(sensorManager, shakeSensor, listener);
        }
    }

    public void enable(float threshold) {
        SENSITIVITY = threshold;
        if (mShakeSensorListener != null && !mManagerEnabled) {
            mShakeSensorListener.register();
            mManagerEnabled = true;
        }
    }

    public void disable() {
        if (mShakeSensorListener != null && mManagerEnabled) {
            mShakeSensorListener.unregisterWhenIdle();
            mManagerEnabled = false;
        }
    }
}
