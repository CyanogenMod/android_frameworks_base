/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.provider.Settings;
import android.net.ConnectivityManager;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/**
 * USB Tether quick settings tile
 */
public class UsbTetherTile extends QSTile<QSTile.BooleanState> {
    private static final Intent WIRELESS_SETTINGS = new Intent(Settings.ACTION_WIRELESS_SETTINGS);

    private boolean mListening;

    private boolean mUsbTethered = false;
    private boolean mUsbConnected = false;

    public UsbTetherTile(Host host) {
        super(host);
    }

    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening)
            return;
        mListening = listening;
        if (listening) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_STATE);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }

    @Override
    protected void handleClick() {
        setUsbTethering(!mUsbTethered);
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(WIRELESS_SETTINGS);
    }

    private void updateState() {
        ConnectivityManager cm = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        String[] tethered = cm.getTetheredIfaces();
        String[] mUsbRegexs = cm.getTetherableUsbRegexs();

        mUsbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) {
                    mUsbTethered = true;
                    break;
                }
            }
        }
    }

    private void setUsbTethering(boolean enabled) {
        ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm.setUsbTethering(enabled) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
            return;
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
            updateState();
            refreshState();
        }
    };

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final ConnectivityManager cm =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        state.visible = mUsbConnected && cm.isTetheringSupported();
        state.value = mUsbTethered;
        state.label = mContext.getString(R.string.quick_settings_usb_tether_label);
        state.iconId = mUsbTethered ? R.drawable.ic_qs_usb_tether_on
                : R.drawable.ic_qs_usb_tether_off;
    }
}
