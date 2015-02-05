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
import android.content.res.Configuration;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.policy.BatteryController;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback{

    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";

    private BatteryController mBatteryController;
    private boolean mBatteryCharging;
    private boolean mShow;
    private boolean mForceShow;
    private boolean mAttached;
    private int mRequestedVisibility;

    private SettingsObserver mObserver = new SettingsObserver(new Handler());

    private class SettingsObserver extends UserContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();

            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    STATUS_BAR_BATTERY_STYLE), false, this, UserHandle.USER_ALL);
            getContext().getContentResolver().registerContentObserver(Settings.System.getUriFor(
                    STATUS_BAR_SHOW_BATTERY_PERCENT), false, this, UserHandle.USER_ALL);
        }

        @Override
        protected void unobserve() {
            super.unobserve();

            getContext().getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void update() {
            loadShowBatteryTextSetting();
        }
    };

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRequestedVisibility = getVisibility();
        loadShowBatteryTextSetting();
    }

    public void setForceShown(boolean forceShow) {
        mForceShow = forceShow;
        updateVisibility();
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        if (mAttached) {
            mBatteryController.addStateChangedCallback(this);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        mRequestedVisibility = visibility;
        updateVisibility();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Respect font size setting.
        setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.battery_level_text_size));
     }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        setText(getResources().getString(R.string.battery_level_template, level));
        boolean changed = mBatteryCharging != charging;
        mBatteryCharging = charging;
        if (changed) {
            loadShowBatteryTextSetting();
        }
    }

    @Override
    public void onPowerSaveChanged() {
        // Not used
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mBatteryController != null) {
            mBatteryController.addStateChangedCallback(this);
        }
        mObserver.observe();

        mAttached = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAttached = false;
        mObserver.unobserve();

        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }

    private void updateVisibility() {
        if (mShow || mForceShow) {
            super.setVisibility(mRequestedVisibility);
        } else {
            super.setVisibility(GONE);
        }
    }

    private void loadShowBatteryTextSetting() {
        ContentResolver resolver = getContext().getContentResolver();
        int currentUserId = ActivityManager.getCurrentUser();
        int mode = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, currentUserId);

        boolean showNextPercent = (mBatteryCharging && mode == 1) || mode == 2;
        int batteryStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0, currentUserId);

        switch (batteryStyle) {
            case 4: //BATTERY_METER_GONE
                showNextPercent = false;
                break;
            case 6: //BATTERY_METER_TEXT
                showNextPercent = true;
                break;
            default:
                break;
        }

        mShow = showNextPercent;
        updateVisibility();
    }
}
