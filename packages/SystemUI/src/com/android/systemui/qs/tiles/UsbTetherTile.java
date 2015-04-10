/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.qs.UsageTracker;
import com.android.systemui.qs.QSTile;

public class UsbTetherTile extends QSTile<QSTile.BooleanState> {
    private static final Intent TETHER_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.TetherSettings"));
    private ConnectivityManager cm;
    private boolean enabled, mListening;

    public UsbTetherTile(Host host) {
        super(host);
        cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        enabled = false;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        enabled = !enabled;
        cm.setUsbTethering(enabled);
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(TETHER_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (cm == null) {
            cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            enabled = false;
        }
        state.visible = cm != null;
        state.value = cm != null && enabled;

        state.icon = state.value ? ResourceIcon.get(R.drawable.ic_qs_usb_tether_on)
                : ResourceIcon.get(R.drawable.ic_qs_usb_tether_off);
        state.label = mContext.getString(R.string.quick_settings_usb_tether_title);
    }

    @Override
    public void setListening(boolean listening) {
        mListening = listening;
    }
}
