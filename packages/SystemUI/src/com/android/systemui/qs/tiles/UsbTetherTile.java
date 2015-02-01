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

import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import org.cyanogenmod.internal.logging.CMMetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

/**
 * USB Tether quick settings tile
 */
public class UsbTetherTile extends QSTile<QSTile.BooleanState> {
    private static final Intent WIRELESS_SETTINGS = new Intent(Settings.ACTION_WIRELESS_SETTINGS);

    private final ConnectivityManager mConnectivityManager;

    private boolean mListening;

    private boolean mUsbTethered = false;
    private boolean mUsbConnected = false;

    public UsbTetherTile(Host host) {
        super(host);
        mConnectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean isAvailable() {
        return mConnectivityManager.isTetheringSupported();
    }

    @Override
    public BooleanState newTileState() {
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
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
    }

    @Override
    protected void handleClick() {
        if (!mUsbConnected) {
            return;
        }
        mConnectivityManager.setUsbTethering(!mUsbTethered);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_usb_tether_label);
    }

    private void updateState() {
        String[] tetheredIfaces = mConnectivityManager.getTetheredIfaces();
        String[] usbRegexs = mConnectivityManager.getTetherableUsbRegexs();

        mUsbTethered = false;
        for (String s : tetheredIfaces) {
            for (String regex : usbRegexs) {
                if (s.matches(regex)) {
                    mUsbTethered = true;
                    return;
                }
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
            if (mUsbConnected && mConnectivityManager.isTetheringSupported()) {
                updateState();
            } else {
                mUsbTethered = false;
            }
            refreshState();
        }
    };

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = mUsbTethered;
        state.label = mContext.getString(R.string.quick_settings_usb_tether_label);
        state.icon = mUsbTethered ? ResourceIcon.get(R.drawable.ic_qs_usb_tether_on)
                : ResourceIcon.get(R.drawable.ic_qs_usb_tether_off);

        if (!mUsbConnected) {
            final int disabledColor = mHost.getContext().getColor(R.color.qs_tile_tint_unavailable);
            state.label = new SpannableStringBuilder().append(state.label,
                    new ForegroundColorSpan(disabledColor),
                    SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            state.icon = ResourceIcon.get(R.drawable.ic_qs_usb_tether_unavailable);
        }
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_USB_TETHER;
    }
}
