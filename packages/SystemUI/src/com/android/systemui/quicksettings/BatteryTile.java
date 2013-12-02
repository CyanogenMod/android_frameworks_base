/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 CyanogenMod Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View.OnLongClickListener;

import com.android.systemui.BatteryMeterView;
import com.android.systemui.BatteryCircleMeterView;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryTile extends QuickSettingsTile implements BatteryStateChangeCallback {
    private BatteryController mController;

    private int mBatteryLevel = 0;
    private boolean mPluggedIn;

    public BatteryTile(Context context, QuickSettingsController qsc, BatteryController controller) {
        super(context, qsc, R.layout.quick_settings_tile_battery);

        mController = controller;

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_BATTERY), this);
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_BATTERY_COLOR), this);
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR), this);
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_BATTERY_TEXT_CHARGING_COLOR), this);
        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.STATUS_BAR_CIRCLE_BATTERY_ANIMATIONSPEED), this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        mController.addStateChangedCallback(this);
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeStateChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        BatteryMeterView battery =
                (BatteryMeterView) mTile.findViewById(R.id.image);
        BatteryCircleMeterView circleBattery =
                (BatteryCircleMeterView) mTile.findViewById(R.id.circle_battery);
        if (circleBattery != null) {
            circleBattery.updateSettings();
        }
        if (battery != null) {
            battery.updateSettings();
        }
        updateResources();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        mBatteryLevel = level;
        mPluggedIn = pluggedIn;
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        int batteryStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
            Settings.System.STATUS_BAR_BATTERY, 0, UserHandle.USER_CURRENT);
        boolean batteryHasPercent = batteryStyle == BatteryMeterView.BATTERY_STYLE_ICON_PERCENT
            || batteryStyle == BatteryMeterView.BATTERY_STYLE_PERCENT
            || batteryStyle == BatteryMeterView.BATTERY_STYLE_CIRCLE_PERCENT
            || batteryStyle == BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT;
        if (mBatteryLevel == 100) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        } else {
            if (!batteryHasPercent) {
                mLabel = mPluggedIn
                    ? mContext.getString(R.string.quick_settings_battery_charging_label,
                            mBatteryLevel)
                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                            mBatteryLevel);
            } else {
                mLabel = mPluggedIn
                    ? mContext.getString(R.string.quick_settings_battery_charging)
                    : mContext.getString(R.string.quick_settings_battery_discharging);
            }
        }
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            tv.setPadding(0, mTileTextPadding, 0, 0);
            if (mTileTextColor != -2) {
                tv.setTextColor(mTileTextColor);
            }
        }
    }

}
