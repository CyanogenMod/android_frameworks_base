/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.BatteryMeterView;
import com.android.systemui.BatteryMeterView.BatteryMeterMode;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryTile extends QuickSettingsTile implements BatteryStateChangeCallback {
    private BatteryController mController;

    private int mBatteryLevel = 0;
    private boolean mPluggedIn;
    private boolean mPresent;
    private BatteryMeterView mBatteryView;

    public BatteryTile(Context context, QuickSettingsController qsc, BatteryController controller) {
        this(context, qsc, controller, R.layout.quick_settings_tile_battery);
    }

    protected BatteryTile(Context context, QuickSettingsController qsc,
            BatteryController controller, int resourceId) {
        super(context, qsc, resourceId);

        mController = controller;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        mBatteryView = getBatteryMeterView();
        mBatteryView.setMode(BatteryMeterMode.BATTERY_METER_ICON_PORTRAIT);

        if (mQsc.isRibbonMode()) {
            boolean showPercent = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_BATTERY_SHOW_PERCENT,
                    0, UserHandle.USER_CURRENT) == 1;
            mBatteryView.setShowPercent(showPercent);
        } else {
            mBatteryView.setShowPercent(false);
        }

        mController.addStateChangedCallback(this);
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeStateChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn, int status) {
        mPresent = present;
        mBatteryLevel = level;
        mPluggedIn = pluggedIn;
        updateResources();
    }

    @Override
    public void onBatteryMeterModeChanged(BatteryMeterMode mode) {
        // All the battery tiles (qs and ribbon) use the NORMAL battery mode
    }

    @Override
    public void onBatteryMeterShowPercent(boolean showPercent) {
        // PowerWidget tile uses the same settings as status bar
        if (mQsc.isRibbonMode()) {
            mBatteryView.setShowPercent(showPercent);
        }
    }

    protected BatteryMeterView getBatteryMeterView() {
        return (BatteryMeterView) mTile.findViewById(R.id.battery);
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mTile.setVisibility(mPresent ? View.VISIBLE : View.GONE);
        if (mBatteryLevel == 100) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        } else {
            int resId = mPluggedIn
                ? R.string.quick_settings_battery_charging_label
                : R.string.status_bar_settings_battery_meter_format;
            mLabel = mContext.getString(resId, mBatteryLevel);
        }
    }
}
