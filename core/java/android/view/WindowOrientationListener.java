/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.util.Config;
import android.util.Log;

/**
 * A special helper class used by the WindowManager
 *  for receiving notifications from the SensorManager when
 * the orientation of the device has changed.
 * @hide
 */
public abstract class WindowOrientationListener {
    private static final String TAG = "WindowOrientationListener";
    private static final boolean DEBUG = false;
    private static final boolean localLOGV = DEBUG ? Config.LOGD : Config.LOGV;
    private SensorManager mSensorManager;
    private boolean mEnabled = false;
    private int mRate;
    private Sensor mSensor;
    private SensorEventListener mSensorEventListener;
    private int mSensorRotation = -1;
    private Context mContext;
    
    /**
     * Creates a new WindowOrientationListener.
     * 
     * @param context for the WindowOrientationListener.
     */
    public WindowOrientationListener(Context context) {
        this(context, SensorManager.SENSOR_DELAY_NORMAL);
    }
    
    /**
     * Creates a new WindowOrientationListener.
     * 
     * @param context for the WindowOrientationListener.
     * @param rate at which sensor events are processed (see also
     * {@link android.hardware.SensorManager SensorManager}). Use the default
     * value of {@link android.hardware.SensorManager#SENSOR_DELAY_NORMAL 
     * SENSOR_DELAY_NORMAL} for simple screen orientation change detection.
     */
    public WindowOrientationListener(Context context, int rate) {
        mContext = context;
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mRate = rate;
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mSensor != null) {
            // Create listener only if sensors do exist
            mSensorEventListener = new SensorEventListenerImpl();
        }
    }

    /**
     * Enables the WindowOrientationListener so it will monitor the sensor and call
     * {@link #onOrientationChanged} when the device orientation changes.
     */
    public void enable() {
        if (mSensor == null) {
            Log.w(TAG, "Cannot detect sensors. Not enabled");
            return;
        }
        if (mEnabled == false) {
            if (localLOGV) Log.d(TAG, "WindowOrientationListener enabled");
            mSensorRotation = -1;
            mSensorManager.registerListener(mSensorEventListener, mSensor, mRate);
            mEnabled = true;
        }
    }

    /**
     * Disables the WindowOrientationListener.
     */
    public void disable() {
        if (mSensor == null) {
            Log.w(TAG, "Cannot detect sensors. Invalid disable");
            return;
        }
        if (mEnabled == true) {
            if (localLOGV) Log.d(TAG, "WindowOrientationListener disabled");
            mSensorRotation = -1;
            mSensorManager.unregisterListener(mSensorEventListener);
            mEnabled = false;
        }
    }

    public int getCurrentRotation() {
        return mSensorRotation;
    }
    
    class SensorEventListenerImpl implements SensorEventListener {
        private static final int _DATA_X = 0;

        private static final int _DATA_Y = 1;

        private static final int _DATA_Z = 2;

        // Angle around x-axis thats considered almost perfect vertical to hold
        // the device
        private static final int PIVOT = 20;

        // Angle around x-asis that's considered almost too vertical. Beyond
        // this angle will not result in any orientation changes. f phone faces
        // uses,
        // the device is leaning backward.
        private static final int PIVOT_UPPER = 65;

        // Angle about x-axis that's considered negative vertical. Beyond this
        // angle will not result in any orientation changes. If phone faces
        // uses,
        // the device is leaning forward.
        private static final int PIVOT_LOWER = -10;

        // Elanthis rotate code starts
        // Upper threshold limit for switching from portrait to landscape
        private static final int PL_UPPER = 65;

        // Lower threshold limit for switching from landscape to portrait
        private static final int LP_LOWER = 25;

        private static final float OneEightyOverPi = 57.29577957855f;

        public void onSensorChanged(SensorEvent event) {
            float[] values = event.values;
            float X = values[_DATA_X];
            float Y = values[_DATA_Y];
            float Z = values[_DATA_Z];
            float gravity = (float) Math.sqrt(X * X + Y * Y + Z * Z);
            float zyangle = (float) Math.asin(Z / gravity) * OneEightyOverPi;
            int rotation = -1;
            if ((zyangle <= PIVOT_UPPER) && (zyangle >= PIVOT_LOWER)) {
                float angle = (float) Math.atan2(Y, -X) * OneEightyOverPi;
                int orientation = 90 - (int) Math.round(angle);
                // normalize to 0 - 359 range
                orientation %= 360;
                if (orientation < 0)
                    orientation += 360;

                int quadrant = orientation / 90;
                orientation %= 90;

                // If you are in the 2nd or 4th quadrant we should reorient to
                // get the mirror of the angle
                if (quadrant == 1 || quadrant == 3)
                    orientation = 90 - orientation;

                boolean landscape = (mSensorRotation == Surface.ROTATION_90)
                        || (mSensorRotation == Surface.ROTATION_270);

                boolean newrot = false;
                // If the sensor doesn't help you, figure out which way it
                // should be
                if (mSensorRotation == -1) {
                    if (orientation <= LP_LOWER) {
                        newrot = true;
                        landscape = true;
                    } else if (orientation >= PL_UPPER) {
                        newrot = true;
                        landscape = false;
                    }
                } else if (landscape)
                    newrot = (orientation <= LP_LOWER);
                else
                    newrot = orientation >= PL_UPPER;

                if (landscape ^ newrot) {
                    if (quadrant == 0 || quadrant == 1)
                        rotation = Surface.ROTATION_270;
                    else
                        rotation = Surface.ROTATION_90;
                } else {
                    if (quadrant == 0 || quadrant == 3)
                        rotation = Surface.ROTATION_0;
                    else
                        rotation = Surface.ROTATION_180;
                }

            }
            if (mSensorRotation != rotation) {
                // End Elanthis rotate code
                int mode = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION_MODE, 0);
                if ((Surface.ROTATION_180 == rotation && mode == 2)
                        || (Surface.ROTATION_270 == rotation && mode > 0)
                        || Surface.ROTATION_90 == rotation || Surface.ROTATION_0 == rotation) {
                    mSensorRotation = rotation;
                    onOrientationChanged(mSensorRotation);
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    /*
     * Returns true if sensor is enabled and false otherwise
     */
    public boolean canDetectOrientation() {
        return mSensor != null;
    }
    
    /**
     * Called when the rotation view of the device has changed.
     * Can be either Surface.ROTATION_90 or Surface.ROTATION_0.
     * @param rotation The new orientation of the device.
     *
     *  @see #ORIENTATION_UNKNOWN
     */
    abstract public void onOrientationChanged(int rotation);
}
