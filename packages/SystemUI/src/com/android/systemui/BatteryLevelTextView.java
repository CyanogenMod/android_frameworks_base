/*
 * Copyright (C) 2014-2016 The CyanogenMod Project
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

package com.android.systemui;

import android.content.Context;
import android.database.ContentObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.tuner.TunerService;

import cyanogenmod.providers.CMSettings;

import java.text.NumberFormat;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback, TunerService.Tunable {

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
            "cmsystem:" + CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT;

    private static final int BATTERY_ICON_GONE      = 4;
    private static final int BATTERY_ICON_TEXT      = 6;

    private BatteryController mBatteryController;
    private boolean mShow;

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateBatteryText();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        setText(NumberFormat.getPercentInstance().format((double) level / 100.0));
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mBatteryController.addStateChangedCallback(this);
        TunerService.get(getContext()).addTunable(this, STATUS_BAR_SHOW_BATTERY_PERCENT);
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        // Not used
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        TunerService.get(getContext()).removeTunable(this);
        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (key.equals(STATUS_BAR_SHOW_BATTERY_PERCENT)) {
            updateBatteryText();
        }
    }

    private void updateBatteryText() {
        final ContentResolver resolver = getContext().getContentResolver();
        final int batteryStyle = CMSettings.System.getIntForUser(resolver,
                CMSettings.System.STATUS_BAR_BATTERY_STYLE, 0, UserHandle.USER_CURRENT);
        final boolean showSidePercent;
        switch (batteryStyle) {
            case BATTERY_ICON_GONE:
                showSidePercent = false;
                break;
            case BATTERY_ICON_TEXT:
                showSidePercent = true;
                break;
            default:
                showSidePercent = CMSettings.System.getIntForUser(resolver,
                        CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT,
                        0, UserHandle.USER_CURRENT) == 2;
                break;
        }
        setShowPercent(showSidePercent);
    }

    public void setShowPercent(boolean show) {
        mShow = show;
        setVisibility(mShow ? View.VISIBLE : View.GONE);
    }
}
