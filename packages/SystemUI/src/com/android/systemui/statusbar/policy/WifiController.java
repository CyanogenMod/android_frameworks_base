/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Slog;
import android.widget.CompoundButton;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.util.concurrent.atomic.AtomicBoolean;

public class WifiController extends BroadcastReceiver
        implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.WifiController";

    private Context mContext;
    private CompoundButton mCheckBox;
    private boolean mStateMachineEvent;
    private final WifiManager mWifiManager;
    private AtomicBoolean mConnected = new AtomicBoolean(false);

    private boolean mWifi;

    public WifiController(Context context, CompoundButton checkbox) {
        mContext = context;
        mWifi = getWifi();
        mCheckBox = checkbox;
        checkbox.setChecked(mWifi);
        checkbox.setOnCheckedChangeListener(this);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        context.registerReceiver(this, filter);

    }

    public void release() {
        mContext.unregisterReceiver(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        //Do nothing if called as a result of a state machine event
        if (mStateMachineEvent) {
            return;
        }
        // Show toast message if Wi-Fi is not allowed in airplane mode

        // Disable tethering if enabling Wifi
        int wifiApState = mWifiManager.getWifiApState();
        if (mCheckBox.isChecked() && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
            mWifiManager.setWifiApEnabled(null, false);
        }

        if (mWifiManager.setWifiEnabled(mCheckBox.isChecked())) {
            // Intent has been taken into account, disable until new state is active
            mCheckBox.setChecked(false);
        }
    }

    public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                handleWifiStateChanged(intent.getIntExtra(
                        WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
            } else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
                if (!mConnected.get()) {
                    handleStateChanged(WifiInfo.getDetailedStateOf((SupplicantState)
                            intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE)));
                }
            } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                NetworkInfo info = (NetworkInfo) intent.getParcelableExtra(
                        WifiManager.EXTRA_NETWORK_INFO);
                mConnected.set(info.isConnected());
                handleStateChanged(info.getDetailedState());
            }
        }
    private void handleStateChanged(@SuppressWarnings("unused") NetworkInfo.DetailedState state) {
    }

    private void handleWifiStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_STATE_ENABLED:
                setSwitchChecked(true);
                mCheckBox.setEnabled(true);
                break;
            case WifiManager.WIFI_STATE_DISABLING:
                mCheckBox.setEnabled(false);
                break;
            case WifiManager.WIFI_STATE_DISABLED:
                setSwitchChecked(false);
                mCheckBox.setEnabled(true);
                break;
            default:
                setSwitchChecked(false);
                mCheckBox.setEnabled(true);
                break;
        }
    }

    private void setSwitchChecked(boolean checked) {
        if (checked != mCheckBox.isChecked()) {
            mStateMachineEvent = true;
            mCheckBox.setChecked(checked);
            mStateMachineEvent = false;
        }
    }

    private boolean getWifi() {
        ContentResolver cr = mContext.getContentResolver();
        return 0 != Settings.System.getInt(cr, Settings.System.AIRPLANE_MODE_ON, 0);
    }

    // TODO: Fix this racy API by adding something better to TelephonyManager or
    // ConnectivityService.
    private void unsafe(final boolean enabled) {
        AsyncTask.execute(new Runnable() {
                public void run() {
                    Settings.System.putInt(
                            mContext.getContentResolver(),
                            Settings.System.AIRPLANE_MODE_ON,
                            enabled ? 1 : 0);
                    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                    intent.putExtra("state", enabled);
                    mContext.sendBroadcast(intent);
                }
            });
    }
}

