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
import android.net.wifi.WifiManager;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.NetworkController;

public class WiFiTile extends NetworkTile {
    private boolean mWifiConnected;
    private boolean mWifiNotConnected;
    private int mWifiSignalIconId;
    private String mDescription;

    public WiFiTile(Context context, QuickSettingsController qsc, NetworkController controller) {
        super(context, qsc, controller, R.layout.quick_settings_tile_wifi);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiManager wfm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                wfm.setWifiEnabled(!wfm.isWifiEnabled());
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_SETTINGS);
                return true;
            }
        };
    }

    @Override
    protected void updateTile() {
        if (mWifiConnected) {
            mDrawable = mWifiSignalIconId;
            mLabel = mDescription.substring(1, mDescription.length()-1);
        } else if (mWifiNotConnected) {
            mDrawable = R.drawable.ic_qs_wifi_0;
            mLabel = mContext.getString(R.string.quick_settings_wifi_label);
        } else {
            mDrawable = R.drawable.ic_qs_wifi_no_network;
            mLabel = mContext.getString(R.string.quick_settings_wifi_off_label);
        }
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            boolean activityIn, boolean activityOut,
            String wifiSignalContentDescriptionId, String description) {
        mWifiConnected = enabled && wifiSignalIconId > 0 && description != null;
        mWifiNotConnected = wifiSignalIconId > 0 && description == null;
        mWifiSignalIconId = wifiSignalIconId;
        mDescription = description;
        setActivity(activityIn, activityOut);
        updateResources();
    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled, int mobileSignalIconId,
            String mobileSignalContentDescriptionId, int dataTypeIconId,
            boolean activityIn, boolean activityOut,
            String dataTypeContentDescriptionId, String description) {
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
    }
}
