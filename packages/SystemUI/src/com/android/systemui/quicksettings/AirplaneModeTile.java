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
import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public class AirplaneModeTile extends QuickSettingsTile implements NetworkSignalChangedCallback{
    private NetworkController mController;
    private boolean enabled = false;

    public AirplaneModeTile(Context context,
            QuickSettingsController qsc, NetworkController controller) {
        super(context, qsc);

        mController = controller;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Change the system setting
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON,
                        !enabled ? 1 : 0);

                // Post the intent
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.putExtra("state", !enabled);
                mContext.sendBroadcast(intent);
            }
        };
        mOnLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        mController.addNetworkSignalChangedCallback(this);
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeNetworkSignalChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mLabel = mContext.getString(R.string.quick_settings_airplane_mode_label);
        mDrawable = (enabled) ? R.drawable.ic_qs_airplane_on : R.drawable.ic_qs_airplane_off;
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description) {
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description) {
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        this.enabled = enabled;
        updateResources();
    }

}
