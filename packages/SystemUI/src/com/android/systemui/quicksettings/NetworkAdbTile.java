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
import android.net.NetworkUtils;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.net.InetAddress;

public class NetworkAdbTile extends QuickSettingsTile {
    public NetworkAdbTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.Secure.putIntForUser(mContext.getContentResolver(),
                        Settings.Secure.ADB_PORT, isEnabled() ? -1 : 5555,
                        UserHandle.USER_CURRENT);
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.Secure.getUriFor(Settings.Secure.ADB_PORT), this);
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private void updateTile() {
        if (isEnabled()) {
            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();

            if (wifiInfo != null) {
                // if wifiInfo is not null, set the label to "hostAddress"
                InetAddress address = NetworkUtils.intToInetAddress(wifiInfo.getIpAddress());
                mLabel = address.getHostAddress();
            } else {
                //if wifiInfo is null, set the enabled label without host address
                mLabel = mContext.getString(R.string.quick_settings_network_adb_enabled_label);
            }
            mDrawable = R.drawable.ic_qs_network_adb_on;
        } else {
            // Otherwise set the disabled label and icon
            mLabel = mContext.getString(R.string.quick_settings_network_adb_disabled_label);
            mDrawable = R.drawable.ic_qs_network_adb_off;
        }
    }

    private boolean isEnabled() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.ADB_PORT, 0, UserHandle.USER_CURRENT) > 0;
    }
}
