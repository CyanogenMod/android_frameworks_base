/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * @hide
 */
public final class CmHardwareManager {
    private static final String TAG = "CmHardwareManager";

    private final String mPackageName;
    private final ICmHardwareService mService;

    public static final int FEATURE_ADAPTIVE_BACKLIGHT = 0x1;
    public static final int FEATURE_COLOR_ENHANCEMENT = 0x2;
    public static final int FEATURE_DISPLAY_COLOR_CALIBRATION = 0x4;
    public static final int FEATURE_DISPLAY_GAMMA_CALIBRATION = 0x8;
    public static final int FEATURE_HIGH_TOUCH_SENSITIVITY = 0x10;
    public static final int FEATURE_KEY_DISABLE = 0x20;
    public static final int FEATURE_LONG_TERM_ORBITS = 0x40;
    public static final int FEATURE_SERIAL_NUMBER = 0x80;
    public static final int FEATURE_SUNLIGHT_ENHANCEMENT = 0x100;
    public static final int FEATURE_TAP_TO_WAKE = 0x200;
    public static final int FEATURE_VIBRATOR = 0x400;

    private static final List<Integer> BOOLEAN_FEATURES = Arrays.asList(
        FEATURE_ADAPTIVE_BACKLIGHT,
        FEATURE_COLOR_ENHANCEMENT,
        FEATURE_HIGH_TOUCH_SENSITIVITY,
        FEATURE_KEY_DISABLE,
        FEATURE_SUNLIGHT_ENHANCEMENT,
        FEATURE_TAP_TO_WAKE
    );

    /**
     * @hide to prevent subclassing from outside of the framework
     */
    public CmHardwareManager(Context context) {
        mPackageName = context.getPackageName();
        mService = ICmHardwareService.Stub.asInterface(
                ServiceManager.getService(Context.CMHW_SERVICE));
    }

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

    public boolean isSupported(int feature) {
        return feature == (getSupportedFeatures() & feature);
    }

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

    public static final int VIBRATOR_INTENSITY_INDEX = 0;
    public static final int VIBRATOR_DEFAULT_INDEX = 1;
    public static final int VIBRATOR_MIN_INDEX = 2;
    public static final int VIBRATOR_MAX_INDEX = 3;
    public static final int VIBRATOR_WARNING_INDEX = 4;

    public int[] getVibratorIntensity() {
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

    public static final int COLOR_CALIBRATION_RED_INDEX = 0;
    public static final int COLOR_CALIBRATION_GREEN_INDEX = 1;
    public static final int COLOR_CALIBRATION_BLUE_INDEX = 2;
    public static final int COLOR_CALIBRATION_DEFAULT_INDEX = 3;
    public static final int COLOR_CALIBRATION_MIN_INDEX = 4;
    public static final int COLOR_CALIBRATION_MAX_INDEX = 5;

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

    public int[] getDisplayColorCalibration() {
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

    public static final int GAMMA_CALIBRATION_RED_INDEX = 0;
    public static final int GAMMA_CALIBRATION_GREEN_INDEX = 1;
    public static final int GAMMA_CALIBRATION_BLUE_INDEX = 2;
    public static final int GAMMA_CALIBRATION_MIN_INDEX = 3;
    public static final int GAMMA_CALIBRATION_MAX_INDEX = 4;

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

    public int[] getDisplayGammaCalibration(int idx) {
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
