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

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;


public class GPSTile extends QuickSettingsTile implements LocationSettingsChangeCallback {
    private LocationController mLocationController;
    private int mCurrentMode;

    public GPSTile(Context context, QuickSettingsController qsc, LocationController lc) {
        super(context, qsc);

        mLocationController = lc;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeLocationMode();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        onLocationSettingsChanged(false);
        updateTile();
        super.onPostCreate();
        mLocationController.addSettingsChangedCallback(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLocationController.removeSettingsChangedCallback(this);
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        mCurrentMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF,
                UserHandle.USER_CURRENT);
        updateResources();
    }

    private void changeLocationMode() {
        int newMode;

        switch (mCurrentMode) {
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                newMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                newMode = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
                break;
            case Settings.Secure.LOCATION_MODE_OFF:
                newMode = Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
                break;
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                newMode = Settings.Secure.LOCATION_MODE_OFF;
                break;
            default:
                newMode = Settings.Secure.LOCATION_MODE_OFF;
                break;
        }

        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, newMode, UserHandle.USER_CURRENT);
    }

    private synchronized void updateTile() {
        int textResId;
        switch (mCurrentMode) {
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                textResId = R.string.location_mode_sensors_only_title;
                mDrawable = R.drawable.ic_qs_location_on;
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                textResId = R.string.location_mode_battery_saving_title;
                mDrawable = R.drawable.ic_qs_location_lowpower;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                textResId = R.string.location_mode_high_accuracy_title;
                mDrawable = R.drawable.ic_qs_location_on;
                break;
            default:
                textResId = R.string.quick_settings_location_off_label;
                mDrawable = R.drawable.ic_qs_location_off;
                break;
        }
        mLabel = mContext.getString(textResId);
    }
}
