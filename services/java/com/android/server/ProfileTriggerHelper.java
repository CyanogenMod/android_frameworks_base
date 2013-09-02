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
import android.app.Profile.ProfileTrigger;
import android.app.Profile.TriggerState;
import android.app.Profile.TriggerType;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Stack;
import java.util.UUID;

public class ProfileTriggerHelper extends BroadcastReceiver {
    private static final String TAG = "ProfileTriggerHelper";
    private static final boolean DEBUG = false;

    private Context mContext;
    private ProfileManagerService mService;

    private WifiManager mWifiManager;
    private String mConnectedSSID;
    private Stack<TriggerStackEntry> mTriggerStack;
    private UUID mActiveProfileBeforeTrigger;
    private HashSet<String> mConnectedBluetoothDevices;
    private HashSet<String> mConnectedA2dpDevices;

    private IntentFilter mIntentFilter;
    private boolean mFilterRegistered = false;

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateEnabled();
        }
    };

    public ProfileTriggerHelper(Context context, ProfileManagerService service) {
        mContext = context;
        mService = service;

        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mConnectedSSID = getActiveSSID();
        mTriggerStack = new Stack<TriggerStackEntry>();
        mConnectedBluetoothDevices = new HashSet<String>();
        mConnectedA2dpDevices = new HashSet<String>();

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mIntentFilter.addAction(AudioManager.A2DP_ROUTE_CHANGED_ACTION);
        updateEnabled();

        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SYSTEM_PROFILES_ENABLED), false,
                mSettingsObserver);
    }

    public void updateEnabled() {
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;
        if (enabled && !mFilterRegistered) {
            Log.v(TAG, "Enabling");
            mContext.registerReceiver(this, mIntentFilter);
            mFilterRegistered = true;
        } else if (!enabled && mFilterRegistered) {
            Log.v(TAG, "Disabling");
            mContext.unregisterReceiver(this);
            mFilterRegistered = false;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            String activeSSID = getActiveSSID();

            if (!TextUtils.equals(activeSSID, mConnectedSSID)) {
                if (DEBUG) {
                    Log.d(TAG, "Wifi state changed: old SSID "
                            + mConnectedSSID + ", new SSID " + activeSSID);
                }
                if (mConnectedSSID != null) {
                    checkTriggers(TriggerType.WIFI, mConnectedSSID, TriggerState.ON_DISCONNECT);
                }
                if (activeSSID != null) {
                    checkTriggers(TriggerType.WIFI, activeSSID, TriggerState.ON_CONNECT);
                }
                mConnectedSSID = activeSSID;
            }
        } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)
                || action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            boolean connected = action.equals(BluetoothDevice.ACTION_ACL_CONNECTED);
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            String address = device.getAddress();

            if (DEBUG) {
                Log.d(TAG, "Bluetooth device " + address + " now "
                        + (connected ? "" : "dis") + "connected");
            }
            checkTriggers(TriggerType.BLUETOOTH, address,
                    connected ? TriggerState.ON_CONNECT : TriggerState.ON_DISCONNECT);
            if (connected) {
                mConnectedBluetoothDevices.add(address);
            } else {
                mConnectedBluetoothDevices.remove(address);
            }
        } else if (action.equals(AudioManager.A2DP_ROUTE_CHANGED_ACTION)) {
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
            boolean connected = state == BluetoothProfile.STATE_CONNECTED;
            String address = device.getAddress();

            if (DEBUG) {
                Log.d(TAG, "A2DP profile for device " + address + " now "
                        + (connected ? "" : "dis") + "connected");
            }
            checkTriggers(TriggerType.BLUETOOTH, address,
                    connected ? TriggerState.ON_A2DP_CONNECT : TriggerState.ON_A2DP_DISCONNECT);
            if (connected) {
                mConnectedA2dpDevices.add(address);
            } else {
                mConnectedA2dpDevices.remove(address);
            }
        }
    }

    /* package */ void clearTriggerStack() {
        if (DEBUG) Log.d(TAG, "Clearing trigger stack");
        mTriggerStack.clear();
        mActiveProfileBeforeTrigger = null;
    }

    /* package */ void removeFromTriggerStack(UUID uuid) {
        if (DEBUG) Log.d(TAG, "Removing profile " + uuid + " from trigger stack");
        if (uuid.equals(mActiveProfileBeforeTrigger)) {
            mActiveProfileBeforeTrigger = null;
        }
        for (TriggerStackEntry entry : mTriggerStack) {
            if (entry.profile.equals(uuid)) {
                mTriggerStack.remove(entry);
                break;
            }
        }
        restoreLastTriggeredProfile();
    }

    /* package */ void updateTriggerStateForProfile(UUID uuid) {
        Profile p = mService.getProfile(uuid);
        if (p == null) {
            return;
        }

        Collection<ProfileTrigger> triggers = p.getTriggers();

        // pass 1: untrigger removed triggers
        Iterator<TriggerStackEntry> iterator = mTriggerStack.iterator();
        while (iterator.hasNext()) {
            TriggerStackEntry entry = iterator.next();
            if (!entry.profile.equals(uuid)) {
                continue;
            }

            boolean triggerRemoved = true;
            for (ProfileTrigger trigger : triggers) {
                if (trigger.getId().equals(entry.id)) {
                    triggerRemoved = false;
                    break;
                }
            }
            if (triggerRemoved) {
                untriggerForId(entry.id);
            }
        }

        // pass 2: trigger newly added triggers
        for (ProfileTrigger trigger : triggers) {
            if (mTriggerStack.search(trigger.getId()) > 0) {
                continue;
            }
            switch (trigger.getType()) {
                case TriggerType.WIFI:
                    if (mConnectedSSID != null) {
                        checkTriggersForProfile(p, TriggerType.WIFI, mConnectedSSID,
                                TriggerState.ON_CONNECT);
                    }
                    break;
                case TriggerType.BLUETOOTH:
                    for (String address : mConnectedBluetoothDevices) {
                        checkTriggersForProfile(p, TriggerType.BLUETOOTH, address,
                                TriggerState.ON_CONNECT);
                        if (mConnectedA2dpDevices.contains(address)) {
                            checkTriggersForProfile(p, TriggerType.BLUETOOTH, address,
                                    TriggerState.ON_A2DP_CONNECT);
                        }
                    }
                    break;
            }
        }
    }

    private void checkTriggers(int type, String id, int triggerState) {
        for (Profile p : mService.getProfileList()) {
            if (checkTriggersForProfile(p, type, id, triggerState)) {
                break;
            }
        }
        if (triggerState == TriggerState.ON_DISCONNECT
                || triggerState == TriggerState.ON_A2DP_DISCONNECT) {
            int index = mTriggerStack.search(id);
            if (index > 0) {
                TriggerStackEntry entry = mTriggerStack.get(mTriggerStack.size() - index);
                if (triggerState == entry.untriggerState) {
                    untriggerForId(id);
                }
            }
        }
    }

    private boolean checkTriggersForProfile(Profile p, int type, String id, int triggerState) {
        if (triggerState != p.getTrigger(type, id)) {
            return false;
        }

        UUID currentProfileUuid = mService.getActiveProfile().getUuid();
        if (!currentProfileUuid.equals(p.getUuid())) {
            triggerProfileForId(p.getUuid(), type, triggerState, id);
            return true;
        }

        return false;
    }

    private void triggerProfileForId(UUID uuid, int type, int triggerState, String id) {
        if (DEBUG) {
            Log.d(TAG, "Profile change to profile " + uuid + " triggered by "
                    + id + " (type " + type + ", state " + triggerState + ")");
            Log.d(TAG, "Current stack: " + mTriggerStack.size()
                    + " items, active before " + mActiveProfileBeforeTrigger);
        }
        if (mActiveProfileBeforeTrigger == null) {
            mActiveProfileBeforeTrigger = mService.getActiveProfile().getUuid();
        }

        int stackIndex = mTriggerStack.search(id);
        TriggerStackEntry entry;
        if (stackIndex > 0) {
            entry = mTriggerStack.remove(mTriggerStack.size() - stackIndex);
        } else {
            entry = new TriggerStackEntry(type, id, uuid);
        }
        entry.untriggerState = triggerState == TriggerState.ON_A2DP_CONNECT
                ? TriggerState.ON_A2DP_DISCONNECT : TriggerState.ON_DISCONNECT;
        mTriggerStack.push(entry);

        mService.setActiveProfile(uuid, true);
    }

    private void untriggerForId(String id) {
        int index = mTriggerStack.search(id);

        if (DEBUG) Log.d(TAG, "Untrigger profile for " + id + " (position " + index + ")");

        if (index > 0) {
            mTriggerStack.remove(mTriggerStack.size() - index);
        }
        restoreLastTriggeredProfile();
    }

    private void restoreLastTriggeredProfile() {
        UUID nextUuid;
        if (mTriggerStack.empty()) {
            nextUuid = mActiveProfileBeforeTrigger;
            mActiveProfileBeforeTrigger = null;
        } else {
            nextUuid = mTriggerStack.peek().profile;
        }

        if (nextUuid == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Restoring last triggered profile " + nextUuid);
        }
        if (!mService.getActiveProfile().getUuid().equals(nextUuid)) {
            mService.setActiveProfile(nextUuid, true);
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

    private static class TriggerStackEntry {
        int type;
        int untriggerState = -1;
        String id;
        UUID profile;

        public TriggerStackEntry(int type, String id, UUID profile) {
            this.type = type;
            this.id = id;
            this.profile = profile;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof TriggerStackEntry) {
                TriggerStackEntry o = (TriggerStackEntry) other;
                return TextUtils.equals(o.id, id) && profile.equals(o.profile);
            } else if (other instanceof String) {
                return TextUtils.equals((String) other, id);
            }
            return false;
        }
    }
}
