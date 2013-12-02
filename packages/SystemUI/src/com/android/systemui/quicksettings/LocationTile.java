/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationSettingsChangeCallback;


public class LocationTile extends QuickSettingsTile implements LocationSettingsChangeCallback {

    ContentResolver mContentResolver;
    private LocationController mLocationController;
    private boolean mLocationEnabled;

    public LocationTile(Context context, final QuickSettingsController qsc) {
        super(context, qsc);

        mContentResolver = mContext.getContentResolver();
        mLocationController = new LocationController(mContext);
        mLocationController.addSettingsChangedCallback(this);
        mLocationEnabled = mLocationController.isLocationEnabled();

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLocationController.setLocationEnabled(!mLocationEnabled);
                if (!mLocationEnabled) {
                    qsc.mBar.collapseAllPanels(true);
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        int textResId = mLocationEnabled ? R.string.quick_settings_location_label
                : R.string.quick_settings_location_off_label;
        mLabel = mContext.getText(textResId).toString();
        mDrawable = mLocationEnabled
                ? R.drawable.ic_qs_location_on : R.drawable.ic_qs_location_off;
    }

    @Override
    public void onLocationSettingsChanged(boolean locationEnabled) {
        mLocationEnabled = locationEnabled;
        updateResources();
    }
}
