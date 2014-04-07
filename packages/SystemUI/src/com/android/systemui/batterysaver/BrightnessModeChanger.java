/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.content.ContentResolver;
import android.content.Context;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;

import com.android.systemui.R;

public class BrightnessModeChanger extends ModeChanger {

    private IPowerManager mPM;

    private int mBrightnessMode = -1;
    private int mUserBrightnessLevel = -1;
    private int mInitialBrightness = 0;
    private int mMinimumBacklight;
    private int mMaximumBacklight;
    private boolean mIsBrightnessRestored = false;

    public BrightnessModeChanger(Context context) {
        super(context);
        mPM = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mMinimumBacklight = pm.getMinimumScreenBrightnessSetting();
        mMaximumBacklight = pm.getMaximumScreenBrightnessSetting();
    }

    @Override
    public void setModeEnabled(boolean enabled) {
        super.setModeEnabled(enabled);
        setWasEnabled(isStateEnabled());
    }

    public void updateBrightnessValue(int initial) {
        if (initial == -1) {
            mInitialBrightness = mMinimumBacklight;
        } else {
            mInitialBrightness = initial;
        }
    }

    public void updateBrightnessMode(int mode) {
        if (mBrightnessMode != mode) {
            mBrightnessMode = mode;
            mUserBrightnessLevel = -1;
        }
    }

    private void setBrightness(int brightness) {
        final ContentResolver resolver = mContext.getContentResolver();
        mBrightnessMode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        if (mBrightnessMode != Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            mUserBrightnessLevel = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS,
                    mMaximumBacklight);
            final int level = brightness;
            Settings.System.putInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            try {
                mPM.setTemporaryScreenBrightnessSettingOverride(level);
            } catch (RemoteException e) {
            }
        }
    }

    private void restoreBrightness() {
        if (mUserBrightnessLevel < 0 || mBrightnessMode < 0
                || mBrightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            return;
        }
        final ContentResolver resolver = mContext.getContentResolver();
        try {
            mPM.setTemporaryScreenBrightnessSettingOverride(mUserBrightnessLevel);
        } catch (RemoteException e) {
        }
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                mBrightnessMode);
    }

    @Override
    public boolean isStateEnabled() {
        return isModeEnabled();
    }

    @Override
    public boolean isSupported() {
        return isModeEnabled();
    }

    @Override
    public int getMode() {
        return 0;
    }

    @Override
    public void stateNormal() {
        restoreBrightness();
    }

    @Override
    public void stateSaving() {
        setBrightness(mInitialBrightness);
    }

    @Override
    public boolean checkModes() {
        return isModeEnabled();
    }

    @Override
    public void setModes() {
        super.setModes();
    }
}
