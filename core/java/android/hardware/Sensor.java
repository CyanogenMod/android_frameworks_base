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


package android.hardware;

import android.os.Build;

/**
 * Class representing a sensor. Use {@link SensorManager#getSensorList} to get
 * the list of available Sensors.
 *
 * @see SensorManager
 * @see SensorEventListener
 * @see SensorEvent
 *
 */
public final class Sensor {

    /**
     * A constant describing an accelerometer sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_ACCELEROMETER = 1;

    /**
     * A constant describing a magnetic field sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_MAGNETIC_FIELD = 2;

    /**
     * A constant describing an orientation sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     *
     * @deprecated use {@link android.hardware.SensorManager#getOrientation
     *             SensorManager.getOrientation()} instead.
     */
    @Deprecated
    public static final int TYPE_ORIENTATION = 3;

    /** A constant describing a gyroscope sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details. */
    public static final int TYPE_GYROSCOPE = 4;

    /**
     * A constant describing a light sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_LIGHT = 5;

    /** A constant describing a pressure sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details. */
    public static final int TYPE_PRESSURE = 6;

    /**
     * A constant describing a temperature sensor type
     *
     * @deprecated use
     *             {@link android.hardware.Sensor#TYPE_AMBIENT_TEMPERATURE
     *             Sensor.TYPE_AMBIENT_TEMPERATURE} instead.
     */
    @Deprecated
    public static final int TYPE_TEMPERATURE = 7;

    /**
     * A constant describing a proximity sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_PROXIMITY = 8;

    /**
     * A constant describing a gravity sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_GRAVITY = 9;

    /**
     * A constant describing a linear acceleration sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_LINEAR_ACCELERATION = 10;

    /**
     * A constant describing a rotation vector sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_ROTATION_VECTOR = 11;

    /**
     * A constant describing a relative humidity sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_RELATIVE_HUMIDITY = 12;

    /** A constant describing an ambient temperature sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details. */
    public static final int TYPE_AMBIENT_TEMPERATURE = 13;

    /**
     * A constant describing an uncalibrated magnetic field sensor type.
     * <p>
     * Similar to {@link #TYPE_MAGNETIC_FIELD} but the hard iron calibration (device calibration
     * due to distortions that arise from magnetized iron, steel or permanent magnets on the
     * device) is not considered in the given sensor values. However, such hard iron bias values
     * are returned to you separately in the result {@link android.hardware.SensorEvent#values}
     * so you may use them for custom calibrations.
     * <p>Also, no periodic calibration is performed
     * (i.e. there are no discontinuities in the data stream while using this sensor) and
     * assumptions that the magnetic field is due to the Earth's poles is avoided, but
     * factory calibration and temperature compensation have been performed.
     * </p>
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values} for more
     * details.
     */
    public static final int TYPE_MAGNETIC_FIELD_UNCALIBRATED = 14;

    /**
     * A constant describing an uncalibrated rotation vector sensor type.
     * <p>Identical to {@link #TYPE_ROTATION_VECTOR} except that it doesn't
     * use the geomagnetic field. Therefore the Y axis doesn't
     * point north, but instead to some other reference, that reference is
     * allowed to drift by the same order of magnitude as the gyroscope
     * drift around the Z axis.
     * <p>
     * In the ideal case, a phone rotated and returning to the same real-world
     * orientation should report the same game rotation vector
     * (without using the earth's geomagnetic field). However, the orientation
     * may drift somewhat over time.
     * </p>
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values} for more
     * details.
     */

    public static final int TYPE_GAME_ROTATION_VECTOR = 15;

    /**
     * A constant describing an uncalibrated gyroscope sensor type.
     * <p>Similar to {@link #TYPE_GYROSCOPE} but no gyro-drift compensation has been performed
     * to adjust the given sensor values. However, such gyro-drift bias values
     * are returned to you separately in the result {@link android.hardware.SensorEvent#values}
     * so you may use them for custom calibrations.
     * <p>Factory calibration and temperature compensation is still applied
     * to the rate of rotation (angular speeds).
     * </p>
     * <p> See {@link android.hardware.SensorEvent#values SensorEvent.values} for more
     * details.
     */
    public static final int TYPE_GYROSCOPE_UNCALIBRATED = 16;

    /**
     * A constant describing a significant motion trigger sensor.
     * <p>
     * It triggers when an event occurs and then automatically disables
     * itself. The sensor continues to operate while the device is asleep
     * and will automatically wake the device to notify when significant
     * motion is detected. The application does not need to hold any wake
     * locks for this sensor to trigger.
     * <p>See {@link TriggerEvent} for more details.
     */
    public static final int TYPE_SIGNIFICANT_MOTION = 17;

    /**
     * A constant describing a step detector sensor.
     * <p>
     * A sensor of this type triggers an event each time a step is taken by the user. The only
     * allowed value to return is 1.0 and an event is generated for each step. Like with any other
     * event, the timestamp indicates when the event (here the step) occurred, this corresponds to
     * when the foot hit the ground, generating a high variation in acceleration.
     * <p>
     * See {@link android.hardware.SensorEvent#values SensorEvent.values} for more details.
     */
    public static final int TYPE_STEP_DETECTOR = 18;

    /**
     * A constant describing a step counter sensor.
     * <p>
     * A sensor of this type returns the number of steps taken by the user since the last reboot
     * while activated. The value is returned as a float (with the fractional part set to zero) and
     * is reset to zero only on a system reboot. The timestamp of the event is set to the time when
     * the first step for that event was taken. This sensor is implemented in hardware and is
     * expected to be low power.
     * <p>
     * See {@link android.hardware.SensorEvent#values SensorEvent.values} for more details.
     */
    public static final int TYPE_STEP_COUNTER = 19;

    /**
     * A constant describing the geo-magnetic rotation vector.
     * <p>
     * Similar to {@link #TYPE_ROTATION_VECTOR}, but using a magnetometer instead of using a
     * gyroscope. This sensor uses lower power than the other rotation vectors, because it doesn't
     * use the gyroscope. However, it is more noisy and will work best outdoors.
     * <p>
     * See {@link android.hardware.SensorEvent#values SensorEvent.values} for more details.
     */
    public static final int TYPE_GEOMAGNETIC_ROTATION_VECTOR = 20;

    /**
     * A constant describing the motion accelerometer sensor.
     * <p>
     * This sensor is similar to the accelerometer sensor, however it only
     * streams data when the device is in motion, and stops streaming data
     * when the device is stationary.
     * Generally this kind of sensor would consume less power than the generic
     * accelerometer sensor, and hence can be used in applications that do not
     * require accelerometer streaming when device is stationary, such as the
     * screen auto-rotation.
     * {@hide}
     */
    public static final int TYPE_MOTION_ACCEL = 33171011;

    /**
     * A constant describing all sensor types.
     */
    public static final int TYPE_ALL = -1;

    /* Reporting mode constants for sensors. Each sensor will have exactly one
       reporting mode associated with it. */
    // Events are reported at a constant rate.
    static int REPORTING_MODE_CONTINUOUS = 1;

    // Events are reported only when the value changes.
    static int REPORTING_MODE_ON_CHANGE = 2;

    // Upon detection of an event, the sensor deactivates itself and then sends a single event.
    static int REPORTING_MODE_ONE_SHOT = 3;

    // TODO(): The following arrays are fragile and error-prone. This needs to be refactored.

    // Note: This needs to be updated, whenever a new sensor is added.
    // Holds the reporting mode and maximum length of the values array
    // associated with
    // {@link SensorEvent} or {@link TriggerEvent} for the Sensor
    private static final int[] sSensorReportingModes = {
            0, 0, // padding because sensor types start at 1
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_ACCELEROMETER
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_GEOMAGNETIC_FIELD
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_ORIENTATION
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_GYROSCOPE
            REPORTING_MODE_ON_CHANGE,  3, // SENSOR_TYPE_LIGHT
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_PRESSURE
            REPORTING_MODE_ON_CHANGE,  3, // SENSOR_TYPE_TEMPERATURE
            REPORTING_MODE_ON_CHANGE,  3, // SENSOR_TYPE_PROXIMITY
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_GRAVITY
            REPORTING_MODE_CONTINUOUS, 3, // SENSOR_TYPE_LINEAR_ACCELERATION
            REPORTING_MODE_CONTINUOUS, 5, // SENSOR_TYPE_ROTATION_VECTOR
            REPORTING_MODE_ON_CHANGE,  3, // SENSOR_TYPE_RELATIVE_HUMIDITY
            REPORTING_MODE_ON_CHANGE,  3, // SENSOR_TYPE_AMBIENT_TEMPERATURE
            REPORTING_MODE_CONTINUOUS, 6, // SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED
            REPORTING_MODE_CONTINUOUS, 4, // SENSOR_TYPE_GAME_ROTATION_VECTOR
            REPORTING_MODE_CONTINUOUS, 6, // SENSOR_TYPE_GYROSCOPE_UNCALIBRATED
            REPORTING_MODE_ONE_SHOT,   1, // SENSOR_TYPE_SIGNIFICANT_MOTION
            // added post 4.3
            REPORTING_MODE_ON_CHANGE,  1, // SENSOR_TYPE_STEP_DETECTOR
            REPORTING_MODE_ON_CHANGE,  1, // SENSOR_TYPE_STEP_COUNTER
            REPORTING_MODE_CONTINUOUS, 5  // SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR
    };

    static int getReportingMode(Sensor sensor) {
        int offset = sensor.mType * 2;
        if (offset >= sSensorReportingModes.length) {
            // we don't know about this sensor, so this is probably a
            // vendor-defined sensor, in that case, we figure out the reporting
            // mode from the sensor meta-data.
            int minDelay = sensor.mMinDelay;
            if (minDelay == 0) {
                return REPORTING_MODE_ON_CHANGE;
            } else if (minDelay < 0) {
                return REPORTING_MODE_ONE_SHOT;
            } else {
                return REPORTING_MODE_CONTINUOUS;
            }
        }
        return sSensorReportingModes[offset];
    }

    static int getMaxLengthValuesArray(Sensor sensor, int sdkLevel) {
        int type = sensor.mType;
        // RotationVector length has changed to 3 to 5 for API level 18
        // Set it to 3 for backward compatibility.
        if (type == Sensor.TYPE_ROTATION_VECTOR &&
                sdkLevel <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return 3;
        }
        int offset = type * 2 + 1;
        if (offset >= sSensorReportingModes.length) {
            // we don't know about this sensor, so this is probably a
            // vendor-defined sensor, in that case, we don't know how many value
            // it has
            // so we return the maximum and assume the app will know.
            // FIXME: sensor HAL should advertise how much data is returned per
            // sensor
            return 16;
        }
        return sSensorReportingModes[offset];
    }

    /* Some of these fields are set only by the native bindings in
     * SensorManager.
     */
    private String  mName;
    private String  mVendor;
    private int     mVersion;
    private int     mHandle;
    private int     mType;
    private float   mMaxRange;
    private float   mResolution;
    private float   mPower;
    private int     mMinDelay;
    private int     mFifoReservedEventCount;
    private int     mFifoMaxEventCount;

    Sensor() {
    }

    /**
     * @return name string of the sensor.
     */
    public String getName() {
        return mName;
    }

    /**
     * @return vendor string of this sensor.
     */
    public String getVendor() {
        return mVendor;
    }

    /**
     * @return generic type of this sensor.
     */
    public int getType() {
        return mType;
    }

    /**
     * @return version of the sensor's module.
     */
    public int getVersion() {
        return mVersion;
    }

    /**
     * @return maximum range of the sensor in the sensor's unit.
     */
    public float getMaximumRange() {
        return mMaxRange;
    }

    /**
     * @return resolution of the sensor in the sensor's unit.
     */
    public float getResolution() {
        return mResolution;
    }

    /**
     * @return the power in mA used by this sensor while in use
     */
    public float getPower() {
        return mPower;
    }

    /**
     * @return the minimum delay allowed between two events in microsecond
     * or zero if this sensor only returns a value when the data it's measuring
     * changes.
     */
    public int getMinDelay() {
        return mMinDelay;
    }

    /**
     * @return Number of events reserved for this sensor in the batch mode FIFO. This gives a
     * guarantee on the minimum number of events that can be batched.
     */
    public int getFifoReservedEventCount() {
        return mFifoReservedEventCount;
    }

    /**
     * @return Maximum number of events of this sensor that could be batched. If this value is zero
     * it indicates that batch mode is not supported for this sensor. If other applications
     * registered to batched sensors, the actual number of events that can be batched might be
     * smaller because the hardware FiFo will be partially used to batch the other sensors.
     */
    public int getFifoMaxEventCount() {
        return mFifoMaxEventCount;
    }

    /** @hide */
    public int getHandle() {
        return mHandle;
    }

    void setRange(float max, float res) {
        mMaxRange = max;
        mResolution = res;
    }

    @Override
    public String toString() {
        return "{Sensor name=\"" + mName + "\", vendor=\"" + mVendor + "\", version=" + mVersion
                + ", type=" + mType + ", maxRange=" + mMaxRange + ", resolution=" + mResolution
                + ", power=" + mPower + ", minDelay=" + mMinDelay + "}";
    }
}
