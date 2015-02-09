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
package com.android.server;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.ICmHardwareService;
import android.hardware.CmHardwareManager;
import android.util.Log;

import java.io.File;

import org.cyanogenmod.hardware.AdaptiveBacklight;
import org.cyanogenmod.hardware.ColorEnhancement;
import org.cyanogenmod.hardware.DisplayColorCalibration;
import org.cyanogenmod.hardware.DisplayGammaCalibration;
import org.cyanogenmod.hardware.HighTouchSensitivity;
import org.cyanogenmod.hardware.KeyDisabler;
import org.cyanogenmod.hardware.LongTermOrbits;
import org.cyanogenmod.hardware.SerialNumber;
import org.cyanogenmod.hardware.SunlightEnhancement;
import org.cyanogenmod.hardware.TapToWake;
import org.cyanogenmod.hardware.TouchscreenHovering;
import org.cyanogenmod.hardware.VibratorHW;

public class CmHardwareService extends ICmHardwareService.Stub {
    private static final boolean DEBUG = true;
    private static final String TAG = CmHardwareService.class.getSimpleName();

    private final Context mContext;
    private final CmHardwareInterface mCmHwImpl;

    private interface CmHardwareInterface {
        public int getSupportedFeatures();
        public boolean get(int feature);
        public boolean set(int feature, boolean enable);

        public int[] getDisplayColorCalibration();
        public boolean setDisplayColorCalibration(int[] rgb);

        public int getNumGammaControls();
        public int[] getDisplayGammaCalibration(int idx);
        public boolean setDisplayGammaCalibration(int idx, int[] rgb);

        public int[] getVibratorIntensity();
        public boolean setVibratorIntensity(int intensity);

        public String getLtoSource();
        public String getLtoDestination();
        public long getLtoDownloadInterval();

        public String getSerialNumber();

        public boolean requireAdaptiveBacklightForSunlightEnhancement();
    }

    private class LegacyCmHardware implements CmHardwareInterface {

        private int mSupportedFeatures = 0;

        public LegacyCmHardware() {
            if (AdaptiveBacklight.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT;
            if (ColorEnhancement.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_COLOR_ENHANCEMENT;
            if (DisplayColorCalibration.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION;
            if (DisplayGammaCalibration.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION;
            if (HighTouchSensitivity.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY;
            if (KeyDisabler.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_KEY_DISABLE;
            if (LongTermOrbits.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_LONG_TERM_ORBITS;
            if (SerialNumber.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_SERIAL_NUMBER;
            if (SunlightEnhancement.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT;
            if (TapToWake.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_TAP_TO_WAKE;
            if (VibratorHW.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_VIBRATOR;
            if (TouchscreenHovering.isSupported())
                mSupportedFeatures |= CmHardwareManager.FEATURE_TOUCH_HOVERING;
        }

        public int getSupportedFeatures() {
            return mSupportedFeatures;
        }

        public boolean get(int feature) {
            switch(feature) {
                case CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT:
                    return AdaptiveBacklight.isEnabled();
                case CmHardwareManager.FEATURE_COLOR_ENHANCEMENT:
                    return ColorEnhancement.isEnabled();
                case CmHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY:
                    return HighTouchSensitivity.isEnabled();
                case CmHardwareManager.FEATURE_KEY_DISABLE:
                    return KeyDisabler.isActive();
                case CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT:
                    return SunlightEnhancement.isEnabled();
                case CmHardwareManager.FEATURE_TAP_TO_WAKE:
                    return TapToWake.isEnabled();
                case CmHardwareManager.FEATURE_TOUCH_HOVERING:
                    return TouchscreenHovering.isEnabled();
                default:
                    Log.e(TAG, "feature " + feature + " is not a boolean feature");
                    return false;
            }
        }

        public boolean set(int feature, boolean enable) {
            switch(feature) {
                case CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT:
                    return AdaptiveBacklight.setEnabled(enable);
                case CmHardwareManager.FEATURE_COLOR_ENHANCEMENT:
                    return ColorEnhancement.setEnabled(enable);
                case CmHardwareManager.FEATURE_HIGH_TOUCH_SENSITIVITY:
                    return HighTouchSensitivity.setEnabled(enable);
                case CmHardwareManager.FEATURE_KEY_DISABLE:
                    return KeyDisabler.setActive(enable);
                case CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT:
                    return SunlightEnhancement.setEnabled(enable);
                case CmHardwareManager.FEATURE_TAP_TO_WAKE:
                    return TapToWake.setEnabled(enable);
                case CmHardwareManager.FEATURE_TOUCH_HOVERING:
                    return TouchscreenHovering.setEnabled(enable);
                default:
                    Log.e(TAG, "feature " + feature + " is not a boolean feature");
                    return false;
            }
        }

        private int[] splitStringToInt(String input, String delimiter) {
            if (input == null || delimiter == null) {
                return null;
            }
            String strArray[] = input.split(delimiter);
            try {
                int intArray[] = new int[strArray.length];
                for(int i = 0; i < strArray.length; i++) {
                    intArray[i] = Integer.parseInt(strArray[i]);
                }
                return intArray;
            } catch (NumberFormatException e) {
                /* ignore */
            }
            return null;
        }

        private String rgbToString(int[] rgb) {
            StringBuilder builder = new StringBuilder();
            builder.append(rgb[CmHardwareManager.COLOR_CALIBRATION_RED_INDEX]);
            builder.append(" ");
            builder.append(rgb[CmHardwareManager.COLOR_CALIBRATION_GREEN_INDEX]);
            builder.append(" ");
            builder.append(rgb[CmHardwareManager.COLOR_CALIBRATION_BLUE_INDEX]);
            return builder.toString();
        }

        public int[] getDisplayColorCalibration() {
            int[] rgb = splitStringToInt(DisplayColorCalibration.getCurColors(), " ");
            if (rgb == null || rgb.length != 3) {
                Log.e(TAG, "Invalid color calibration string");
                return null;
            }
            int[] currentCalibration = new int[6];
            currentCalibration[CmHardwareManager.COLOR_CALIBRATION_RED_INDEX] = rgb[0];
            currentCalibration[CmHardwareManager.COLOR_CALIBRATION_GREEN_INDEX] = rgb[1];
            currentCalibration[CmHardwareManager.COLOR_CALIBRATION_BLUE_INDEX] = rgb[2];
            currentCalibration[CmHardwareManager.COLOR_CALIBRATION_DEFAULT_INDEX] =
                DisplayColorCalibration.getDefValue();
            currentCalibration[CmHardwareManager.COLOR_CALIBRATION_MIN_INDEX] =
                DisplayColorCalibration.getMinValue();
            currentCalibration[CmHardwareManager.COLOR_CALIBRATION_MAX_INDEX] =
                DisplayColorCalibration.getMaxValue();
            return currentCalibration;
        }

        public boolean setDisplayColorCalibration(int[] rgb) {
            return DisplayColorCalibration.setColors(rgbToString(rgb));
        }

        public int getNumGammaControls() {
            return DisplayGammaCalibration.getNumberOfControls();
        }

        public int[] getDisplayGammaCalibration(int idx) {
            int[] rgb = splitStringToInt(DisplayGammaCalibration.getCurGamma(idx), " ");
            if (rgb == null || rgb.length != 3) {
                Log.e(TAG, "Invalid gamma calibration string");
                return null;
            }
            int[] currentCalibration = new int[5];
            currentCalibration[CmHardwareManager.GAMMA_CALIBRATION_RED_INDEX] = rgb[0];
            currentCalibration[CmHardwareManager.GAMMA_CALIBRATION_GREEN_INDEX] = rgb[1];
            currentCalibration[CmHardwareManager.GAMMA_CALIBRATION_BLUE_INDEX] = rgb[2];
            currentCalibration[CmHardwareManager.GAMMA_CALIBRATION_MIN_INDEX] =
                DisplayGammaCalibration.getMinValue(idx);
            currentCalibration[CmHardwareManager.GAMMA_CALIBRATION_MAX_INDEX] =
                DisplayGammaCalibration.getMaxValue(idx);
            return currentCalibration;
        }

        public boolean setDisplayGammaCalibration(int idx, int[] rgb) {
            return DisplayGammaCalibration.setGamma(idx, rgbToString(rgb));
        }

        public int[] getVibratorIntensity() {
            int[] vibrator = new int[5];
            vibrator[CmHardwareManager.VIBRATOR_INTENSITY_INDEX] = VibratorHW.getCurIntensity();
            vibrator[CmHardwareManager.VIBRATOR_DEFAULT_INDEX] = VibratorHW.getDefaultIntensity();
            vibrator[CmHardwareManager.VIBRATOR_MIN_INDEX] = VibratorHW.getMinIntensity();
            vibrator[CmHardwareManager.VIBRATOR_MAX_INDEX] = VibratorHW.getMaxIntensity();
            vibrator[CmHardwareManager.VIBRATOR_WARNING_INDEX] = VibratorHW.getWarningThreshold();
            return vibrator;
        }

        public boolean setVibratorIntensity(int intensity) {
            return VibratorHW.setIntensity(intensity);
        }

        public String getLtoSource() {
            return LongTermOrbits.getSourceLocation();
        }

        public String getLtoDestination() {
            File file = LongTermOrbits.getDestinationLocation();
            return file.getAbsolutePath();
        }

        public long getLtoDownloadInterval() {
            return LongTermOrbits.getDownloadInterval();
        }

        public String getSerialNumber() {
            return SerialNumber.getSerialNumber();
        }

        public boolean requireAdaptiveBacklightForSunlightEnhancement() {
            return SunlightEnhancement.isAdaptiveBacklightRequired();
        }
    }

    private CmHardwareInterface getImpl(Context context) {
        return new LegacyCmHardware();
    }

    public CmHardwareService(Context context) {
        mContext = context;
        mCmHwImpl = getImpl(context);
    }

    private boolean isSupported(int feature) {
        return (getSupportedFeatures() & feature) == feature;
    }

    @Override
    public int getSupportedFeatures() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        return mCmHwImpl.getSupportedFeatures();
    }

    @Override
    public boolean get(int feature) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(feature)) {
            Log.e(TAG, "feature " + feature + " is not supported");
            return false;
        }
        return mCmHwImpl.get(feature);
    }

    @Override
    public boolean set(int feature, boolean enable) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(feature)) {
            Log.e(TAG, "feature " + feature + " is not supported");
            return false;
        }
        return mCmHwImpl.set(feature, enable);
    }

    @Override
    public int[] getDisplayColorCalibration() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            Log.e(TAG, "Display color calibration is not supported");
            return null;
        }
        return mCmHwImpl.getDisplayColorCalibration();
    }

    @Override
    public boolean setDisplayColorCalibration(int[] rgb) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            Log.e(TAG, "Display color calibration is not supported");
            return false;
        }
        if (rgb.length < 3) {
            Log.e(TAG, "Invalid color calibration");
            return false;
        }
        return mCmHwImpl.setDisplayColorCalibration(rgb);
    }

    @Override
    public int getNumGammaControls() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION)) {
            Log.e(TAG, "Display gamma calibration is not supported");
            return 0;
        }
        return mCmHwImpl.getNumGammaControls();
    }

    @Override
    public int[] getDisplayGammaCalibration(int idx) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION)) {
            Log.e(TAG, "Display gamma calibration is not supported");
            return null;
        }
        return mCmHwImpl.getDisplayGammaCalibration(idx);
    }

    @Override
    public boolean setDisplayGammaCalibration(int idx, int[] rgb) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_DISPLAY_GAMMA_CALIBRATION)) {
            Log.e(TAG, "Display gamma calibration is not supported");
            return false;
        }
        return mCmHwImpl.setDisplayGammaCalibration(idx, rgb);
    }

    @Override
    public int[] getVibratorIntensity() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_VIBRATOR)) {
            Log.e(TAG, "Vibrator is not supported");
            return null;
        }
        return mCmHwImpl.getVibratorIntensity();
    }

    @Override
    public boolean setVibratorIntensity(int intensity) {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_VIBRATOR)) {
            Log.e(TAG, "Vibrator is not supported");
            return false;
        }
        return mCmHwImpl.setVibratorIntensity(intensity);
    }

    @Override
    public String getLtoSource() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_LONG_TERM_ORBITS)) {
            Log.e(TAG, "Long term orbits is not supported");
            return null;
        }
        return mCmHwImpl.getLtoSource();
    }

    @Override
    public String getLtoDestination() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_LONG_TERM_ORBITS)) {
            Log.e(TAG, "Long term orbits is not supported");
            return null;
        }
        return mCmHwImpl.getLtoDestination();
    }

    @Override
    public long getLtoDownloadInterval() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_LONG_TERM_ORBITS)) {
            Log.e(TAG, "Long term orbits is not supported");
            return 0;
        }
        return mCmHwImpl.getLtoDownloadInterval();
    }

    @Override
    public String getSerialNumber() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_SERIAL_NUMBER)) {
            Log.e(TAG, "Serial number is not supported");
            return null;
        }
        return mCmHwImpl.getSerialNumber();
    }

    @Override
    public boolean requireAdaptiveBacklightForSunlightEnhancement() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.HARDWARE_ABSTRACTION_ACCESS, null);
        if (!isSupported(CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT)) {
            Log.e(TAG, "Sunlight enhancement is not supported");
            return false;
        }
        return mCmHwImpl.requireAdaptiveBacklightForSunlightEnhancement();
    }
}
