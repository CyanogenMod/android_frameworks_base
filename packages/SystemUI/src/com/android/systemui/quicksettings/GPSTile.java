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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;

import java.util.ArrayList;


public class GPSTile extends QuickSettingsTile implements LocationSettingsChangeCallback {
    private LocationController mLocationController;
    private int mCurrentMode;

    private static final String SEPARATOR = "OV=I=XseparatorX=I=VO";

    private static final int[] LOCATORS = new int[]{
            Settings.Secure.LOCATION_MODE_OFF,
            Settings.Secure.LOCATION_MODE_BATTERY_SAVING,
            Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
            Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
    };

    private ArrayList<Integer> mLocators;
    private int mLocatorIndex;

    public GPSTile(Context context, QuickSettingsController qsc, LocationController lc) {
        super(context, qsc);

        mLocators = new ArrayList<Integer>();

        mLocationController = lc;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                updateResources();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.EXPANDED_LOCATION_MODE), this);
    }

    @Override
    void onPostCreate() {
        onLocationSettingsChanged(false);
        updateSettings();
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

    protected void toggleState() {
        int r;
        do {
            mLocatorIndex++;
            if (mLocatorIndex >= LOCATORS.length) {
                mLocatorIndex = 0;
            }
            r = LOCATORS[mLocatorIndex];
        } while (!mLocators.contains(r));

        // Set the desired state
        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE, r, UserHandle.USER_CURRENT);
    }

    private void findCurrentState() {
        mLocatorIndex = 0;
        for (int i = 0; i < LOCATORS.length; i++) {
            int r = LOCATORS[i];
            if (mCurrentMode == r) {
                mLocatorIndex = i;
                break;
            }
        }
    }

    public String[] parseStoredValue(CharSequence val) {
        if (TextUtils.isEmpty(val)) {
            return null;
        }
        return val.toString().split(SEPARATOR);
    }

    private void updateSettings() {
        String setting = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.EXPANDED_LOCATION_MODE, UserHandle.USER_CURRENT);
        String[] modes = parseStoredValue(setting);

        mLocators.clear();

        if (modes == null || modes.length == 0) {
            for (int r : LOCATORS) {
                mLocators.add(r);
            }
        } else {
            for (String mode : modes) {
                int index = Integer.valueOf(mode);
                if (index < LOCATORS.length) {
                    mLocators.add(LOCATORS[index]);
                }
            }
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateSettings();
        updateResources();
    }

    private synchronized void updateTile() {
        int textResId;
        findCurrentState();
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
