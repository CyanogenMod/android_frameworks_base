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

import android.content.Context;
import android.provider.Settings;
import android.widget.Toast;
import android.util.Log;

import com.android.systemui.R;

public class CpuModeChanger extends ModeChanger {

    private boolean mIsTegra3 = false;
    private boolean mIsDynFreq = false;
    private String mMaxFreqSetting;
    private String mMaxFreqSaverSetting;

    public CpuModeChanger(Context context) {
        super(context);
        updateDefaultCpuValue();
    }

    @Override
    public void setModeEnabled(boolean enabled) {
        super.setModeEnabled(enabled);
        setWasEnabled(isStateEnabled());
    }

    private void updateDefaultCpuValue() {
        mIsTegra3 = Helpers.fileExists(Helpers.TEGRA_MAX_FREQ_PATH);
        mIsDynFreq = Helpers.fileExists(Helpers.DYN_MAX_FREQ_PATH) && Helpers.fileExists(Helpers.DYN_MIN_FREQ_PATH);

        if (Helpers.fileExists(Helpers.DYN_MAX_FREQ_PATH)) {
            mMaxFreqSetting = Helpers.readOneLine(Helpers.DYN_MAX_FREQ_PATH);
        } else {
            mMaxFreqSetting = Helpers.readOneLine(Helpers.MAX_FREQ_PATH);
        }

        if (mIsTegra3) {
            String curTegraMaxSpeed = Helpers.readOneLine(Helpers.TEGRA_MAX_FREQ_PATH);
            int curTegraMax;
            try {
                curTegraMax = Integer.parseInt(curTegraMaxSpeed);
                if (curTegraMax > 0) {
                    mMaxFreqSetting = Integer.toString(curTegraMax);
                }
            } catch (NumberFormatException ignored) {
                // Nothing to do
            }
        }
        if (mMaxFreqSetting == null) {
            mMaxFreqSetting = Settings.Global.getString(mContext.getContentResolver(),
                     Settings.Global.BATTERY_SAVER_CPU_FREQ_DEFAULT);
        }
        if (mMaxFreqSaverSetting == null) {
            mMaxFreqSaverSetting = mMaxFreqSetting;
        }
        if (BatterySaverService.DEBUG) {
            Log.i(BatterySaverService.TAG, " default maximum cpu freq = " + mMaxFreqSetting);
        }
    }

    private void restoreCpuState(boolean restore) {
        String setCpuFreq = restore ? mMaxFreqSetting : mMaxFreqSaverSetting;
        if (setCpuFreq != null) {
            for (int i = 0; i < Helpers.getNumOfCpus(); i++) {
                 Helpers.writeOneLine(Helpers.MAX_FREQ_PATH.replace("cpu0", "cpu" + i), setCpuFreq);
            }
            if (mIsTegra3) {
                Helpers.writeOneLine(Helpers.TEGRA_MAX_FREQ_PATH, setCpuFreq);
            }
            if (mIsDynFreq) {
                Helpers.writeOneLine(Helpers.DYN_MAX_FREQ_PATH, setCpuFreq);
            }
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " change maximum cpu freq = " + Helpers.toMHz(setCpuFreq));
            }
            if (isShowToast()) {
                Toast.makeText(mContext,
                  mContext.getString(R.string.battery_saver_cpu_change) + " "
                  + Helpers.toMHz(setCpuFreq), Toast.LENGTH_SHORT).show();
            }
        } else {
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " change maximum cpu freq = NULL");
            }
        }
    }

    public void setCpuValue(String value) {
        mMaxFreqSaverSetting = value;
        updateDefaultCpuValue();
        if (value != null) {
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " user setup maximum cpu freq = " + Helpers.toMHz(value));
            }
        }
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
        restoreCpuState(true);
    }

    @Override
    public void stateSaving() {
        restoreCpuState(false);
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
