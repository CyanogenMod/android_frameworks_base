/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.android.systemui.statusbar.policy.BatteryController;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback{

    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";

    private BatteryController mBatteryController;
    private boolean mShow;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange, Uri uri) {
            loadShowBatteryTextSetting();
            setVisibility(mShow ? View.VISIBLE : View.GONE);
        }
    };

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadShowBatteryTextSetting();
        setVisibility(mShow ? View.VISIBLE : View.GONE);
    }

    private void loadShowBatteryTextSetting() {
        //mShow = 2 == Settings.System.getInt(getContext().getContentResolver(),
        //        Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0);

        ContentResolver resolver = getContext().getContentResolver();
        int currentUserId = ActivityManager.getCurrentUser();

        boolean showInsidePercent = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, currentUserId) == 1;

        boolean showNextPercent = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, currentUserId) == 2;

        int batteryStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0, currentUserId);
        switch (batteryStyle) {
            case 2:
                //meterMode = BatteryMeterMode.BATTERY_METER_CIRCLE;
                showNextPercent = showNextPercent;
                break;

            case 4:
                //meterMode = BatteryMeterMode.BATTERY_METER_GONE;
                showNextPercent = false;
                break;

            case 5:
                //meterMode = BatteryMeterMode.BATTERY_METER_ICON_LANDSCAPE;
                showNextPercent = showNextPercent;
                break;

            case 6:
                //meterMode = BatteryMeterMode.BATTERY_METER_TEXT;
                showNextPercent = true;
                break;

            default:
                break;
        }

        setShowPercent(showNextPercent);

    }

    public void setShowPercent(boolean show) {
        mShow = show;
        setVisibility(mShow ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        setText(getResources().getString(R.string.battery_level_template, level));
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mBatteryController.addStateChangedCallback(this);
    }

    @Override
    public void onPowerSaveChanged() {
        // Not used
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                STATUS_BAR_BATTERY_STYLE), false, mObserver);
        getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                STATUS_BAR_SHOW_BATTERY_PERCENT), false, mObserver);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }
}
