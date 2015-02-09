/*
 * Copyright (C) 2015 The CyanogenMod Project
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

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.IllegalArgumentException;
import java.util.Arrays;
import java.util.List;

/**
 * Manages access to CyanogenMod hardware extensions
 *
 * {@hide}
 */
public final class CmHardwareManager {
    private static final String TAG = "CmHardwareManager";

    private final ICmHardwareService mService;

    /**
     * Adaptive backlight support (this refers to technologies like NVIDIA SmartDimmer,
     * QCOM CABL or Samsung CABC)
     */
    public static final int FEATURE_ADAPTIVE_BACKLIGHT = 0x1;

    /**
     * Color enhancement support
     */
    public static final int FEATURE_COLOR_ENHANCEMENT = 0x2;

    /**
     * Display RGB color calibration
     */
    public static final int FEATURE_DISPLAY_COLOR_CALIBRATION = 0x4;

    /**
     * Display gamma calibration
     */
    public static final int FEATURE_DISPLAY_GAMMA_CALIBRATION = 0x8;

    /**
     * High touch sensitivity for touch panels
     */
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x10;

    /**
     * Hardware navigation key disablement
     */
    public static final int FEATURE_KEY_DISABLE = 0x20;

    /**
     * Long term orbits (LTO)
     */
    public static final int FEATURE_LONG_TERM_ORBITS = 0x40;

    /**
     * Serial number other than ro.serialno
     */
    public static final int FEATURE_SERIAL_NUMBER = 0x80;

    /**
     * Increased display readability in bright light
     */
    public static final int FEATURE_SUNLIGHT_ENHANCEMENT = 0x100;

    /**
     * Double-tap the touch panel to wake up the device
     */
    public static final int FEATURE_TAP_TO_WAKE = 0x200;

    /**
     * Variable vibrator intensity
     */
    public static final int FEATURE_VIBRATOR = 0x400;

    /**
     * Touchscreen hovering
     */
    public static final int FEATURE_TOUCH_HOVERING = 0x800;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_ADAPTIVE_BACKLIGHT,
        FEATURE_COLOR_ENHANCEMENT,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_SUNLIGHT_ENHANCEMENT,
        FEATURE_TAP_TO_WAKE,
        FEATURE_TOUCH_HOVERING
    );

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    public CmHardwareManager(Context context) {
        mService = ICmHardwareService.Stub.asInterface(
                ServiceManager.getService(Context.CMHW_SERVICE));
    }

    /**
     * @return the supported features bitmask
     */
    public int getSupportedFeatures() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return 0;
        }

        try {
            return mService.getSupportedFeatures();
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * Determine if a CM Hardware feature is supported on this device
     *
     * @param feature The CM Hardware feature to query
     *
     * @return true if the feature is supported, false otherwise.
     */
    public boolean isSupported(int feature) {
        return feature == (getSupportedFeatures() & feature);
    }

    /**
     * Determine if the given feature is enabled or disabled.
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the CM Hardware feature to query
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean get(int feature) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.get(feature);
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * Enable or disable the given feature
     *
     * Only used for features which have simple enable/disable controls.
     *
     * @param feature the CM Hardware feature to set
     * @param enable true to enable, false to disale
     *
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean set(int feature, boolean enable) {
        if (!BOOLEAN_FEATURES.contains(feature)) {
            throw new IllegalArgumentException(feature + " is not a boolean");
        }

        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.set(feature, enable);
        } catch (RemoteException e) {
        }
        return false;
    }

    private int getArrayValue(int[] arr, int idx, int defaultValue) {
        if (arr == null || arr.length <= idx) {
            return defaultValue;
        }

        return arr[idx];
    }

    /**
     * {@hide}
     */
    public static final int VIBRATOR_INTENSITY_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_DEFAULT_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MIN_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_MAX_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int VIBRATOR_WARNING_INDEX = 4;

    private int[] getVibratorIntensityArray() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return null;
        }

        try {
            return mService.getVibratorIntensity();
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return The current vibrator intensity.
     */
    public int getVibratorIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_INTENSITY_INDEX, 0);
    }

    /**
     * @return The default vibrator intensity.
     */
    public int getVibratorDefaultIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_DEFAULT_INDEX, 0);
    }

    /**
     * @return The minimum vibrator intensity.
     */
    public int getVibratorMinIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_MIN_INDEX, 0);
    }

    /**
     * @return The maximum vibrator intensity.
     */
    public int getVibratorMaxIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_MAX_INDEX, 0);
    }

    /**
     * @return The warning threshold vibrator intensity.
     */
    public int getVibratorWarningIntensity() {
        return getArrayValue(getVibratorIntensityArray(), VIBRATOR_WARNING_INDEX, 0);
    }

    /**
     * Set the current vibrator intensity
     *
     * @param intensity the intensity to set, between {@link #getVibratorMinIntensity()} and
     * {@link #getVibratorMaxIntensity()} inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setVibratorIntensity(int intensity) {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.setVibratorIntensity(intensity);
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_RED_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_GREEN_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_BLUE_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_DEFAULT_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_MIN_INDEX = 4;
    /**
     * {@hide}
     */
    public static final int COLOR_CALIBRATION_MAX_INDEX = 5;

    private int[] getDisplayColorCalibrationArray() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return null;
        }

        try {
            return mService.getDisplayColorCalibration();
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the current RGB calibration, where int[0] = R, int[1] = G, int[2] = B.
     */
    public int[] getDisplayColorCalibration() {
        int[] arr = getDisplayColorCalibrationArray();
        if (arr == null || arr.length < 3) {
            return null;
        }
        return Arrays.copyOf(arr, 3);
    }

    /**
     * @return the default value for all colors
     */
    public int getDisplayColorCalibrationDefault() {
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_DEFAULT_INDEX, 0);
    }

    /**
     * @return The minimum value for all colors
     */
    public int getDisplayColorCalibrationMin() {
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MIN_INDEX, 0);
    }

    /**
     * @return The minimum value for all colors
     */
    public int getDisplayColorCalibrationMax() {
        return getArrayValue(getDisplayColorCalibrationArray(), COLOR_CALIBRATION_MAX_INDEX, 0);
    }

    /**
     * Set the display color calibration to the given rgb triplet
     *
     * @param rgb RGB color calibration.  Each value must be between
     * {@link getDisplayColorCalibrationMin()} and {@link getDisplayColorCalibrationMax()},
     * inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setDisplayColorCalibration(int[] rgb) {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.setDisplayColorCalibration(rgb);
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_RED_INDEX = 0;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_GREEN_INDEX = 1;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_BLUE_INDEX = 2;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_MIN_INDEX = 3;
    /**
     * {@hide}
     */
    public static final int GAMMA_CALIBRATION_MAX_INDEX = 4;

    private int[] getDisplayGammaCalibrationArray(int idx) {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return null;
        }

        try {
            return mService.getDisplayGammaCalibration(idx);
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the number of RGB controls the device supports
     */
    public int getNumGammaControls() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return 0;
        }

        try {
            return mService.getNumGammaControls();
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * @param the control to query
     *
     * @return the current RGB gamma calibration for the given control
     */
    public int[] getDisplayGammaCalibration(int idx) {
        int[] arr = getDisplayGammaCalibrationArray(idx);
        if (arr == null || arr.length < 3) {
            return null;
        }
        return Arrays.copyOf(arr, 3);
    }

    /**
     * @return the minimum value for all colors
     */
    public int getDisplayGammaCalibrationMin() {
        return getArrayValue(getDisplayGammaCalibrationArray(0), GAMMA_CALIBRATION_MIN_INDEX, 0);
    }

    /**
     * @return the maximum value for all colors
     */
    public int getDisplayGammaCalibrationMax() {
        return getArrayValue(getDisplayGammaCalibrationArray(0), GAMMA_CALIBRATION_MAX_INDEX, 0);
    }

    /**
     * Set the display gamma calibration for a specific control
     *
     * @param idx the control to set
     * @param rgb RGB color calibration.  Each value must be between
     * {@link getDisplayGammaCalibrationMin()} and {@link getDisplayGammaCalibrationMax()},
     * inclusive.
     *
     * @return true on success, false otherwise.
     */
    public boolean setDisplayGammaCalibration(int idx, int[] rgb) {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.setDisplayGammaCalibration(idx, rgb);
        } catch (RemoteException e) {
        }
        return false;
    }

    /**
     * @return the source location of LTO data, or null on failure
     */
    public String getLtoSource() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return null;
        }

        try {
            return mService.getLtoSource();
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the destination location of LTO data, or null on failure
     */
    public String getLtoDestination() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return null;
        }

        try {
            return mService.getLtoDestination();
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return the interval, in milliseconds, to trigger LTO data download
     */
    public long getLtoDownloadInterval() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return 0;
        }

        try {
            return mService.getLtoDownloadInterval();
        } catch (RemoteException e) {
        }
        return 0;
    }

    /**
     * @return the serial number to display instead of ro.serialno, or null on failure
     */
    public String getSerialNumber() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return null;
        }

        try {
            return mService.getSerialNumber();
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * @return true if adaptive backlight should be enabled when sunlight enhancement
     * is enabled.
     */
    public boolean requireAdaptiveBacklightForSunlightEnhancement() {
        if (mService == null) {
            Log.w(TAG, "no cmhw service.");
            return false;
        }

        try {
            return mService.requireAdaptiveBacklightForSunlightEnhancement();
        } catch (RemoteException e) {
        }
        return false;
    }
}
