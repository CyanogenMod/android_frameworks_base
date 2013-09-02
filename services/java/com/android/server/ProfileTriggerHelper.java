/*
 * Copyright (c) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.app.Profile;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiInfo;
import android.util.Log;

import java.util.UUID;

public class ProfileTriggerHelper extends BroadcastReceiver {
    private static final String TAG = "ProfileTriggerService";

    private Context mContext;
    private ProfileManagerService mService;

    private WifiManager mWifiManager;
    private String mLastConnectedSSID;

    public ProfileTriggerHelper(Context context, ProfileManagerService service) {
        mContext = context;
        mService = service;

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mLastConnectedSSID = getActiveSSID();

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mContext.registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            String activeSSID = getActiveSSID();
            int triggerState;

            if (activeSSID != null) {
                triggerState = Profile.TriggerState.ON_CONNECT;
                mLastConnectedSSID = activeSSID;
            } else {
                triggerState = Profile.TriggerState.ON_DISCONNECT;
            }
            checkTriggers(Profile.TriggerType.WIFI, mLastConnectedSSID, triggerState);
        } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            int triggerState = action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                    ? Profile.TriggerState.ON_CONNECT : Profile.TriggerState.ON_DISCONNECT;
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            checkTriggers(Profile.TriggerType.BLUETOOTH, device.getAddress(), triggerState);
        }
    }

    private void checkTriggers(int type, String id, int newState) {
        for (Profile p : mService.getProfileList()) {
            if (newState != p.getTrigger(type, id)) {
                continue;
            }

            UUID currentProfileUuid = mService.getActiveProfile().getUuid();
            if (!currentProfileUuid.equals(p.getUuid())) {
                mService.setActiveProfile(p, true);
            }
        }
    }

    private String getActiveSSID() {
        WifiInfo wifiinfo = mWifiManager.getConnectionInfo();
        if (wifiinfo == null) {
            return null;
        }
        WifiSsid ssid = wifiinfo.getWifiSsid();
        if (ssid == null) {
            return null;
        }
        return ssid.toString();
    }
}
