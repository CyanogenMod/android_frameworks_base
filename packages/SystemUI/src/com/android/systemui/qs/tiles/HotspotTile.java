/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.net.ConnectivityManager;
import android.net.wifi.WifiDevice;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.widget.Switch;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.MetricsProto.MetricsEvent;
import com.android.systemui.R;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.HotspotController;

import java.util.List;

/** Quick settings tile: Hotspot **/
public class HotspotTile extends QSTile<QSTile.AirplaneBooleanState> {
    private final AnimationIcon mEnable =
            new AnimationIcon(R.drawable.ic_hotspot_enable_animation,
                    R.drawable.ic_hotspot_disable);
    private final AnimationIcon mDisable =
            new AnimationIcon(R.drawable.ic_hotspot_disable_animation,
                    R.drawable.ic_hotspot_enable);
    private final Icon mDisableNoAnimation = ResourceIcon.get(R.drawable.ic_hotspot_enable);
    private final Icon mUnavailable = ResourceIcon.get(R.drawable.ic_hotspot_unavailable);

    private final HotspotController mController;
    private final Callback mCallback = new Callback();
    private final ConnectivityManager mConnectivityManager;
    private boolean mListening;

    public HotspotTile(Host host) {
        super(host);
        mController = host.getHotspotController();
        mConnectivityManager = host.getContext().getSystemService(ConnectivityManager.class);

    }

    @Override
    public boolean isAvailable() {
        return mController.isHotspotSupported();
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
    }

    @Override
    public AirplaneBooleanState newTileState() {
        return new AirplaneBooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        if (listening) {
            mController.addCallback(mCallback);
            mContext.registerReceiver(mTetherConnectStateChangedReceiver,
                    new IntentFilter(ConnectivityManager.TETHER_CONNECT_STATE_CHANGED));
        } else {
            mController.removeCallback(mCallback);
            mContext.unregisterReceiver(mTetherConnectStateChangedReceiver);
        }
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
    }

    @Override
    protected void handleClick() {
        final boolean isEnabled = (Boolean) mState.value;
        if (!isEnabled ) {
            return;
        }
        MetricsLogger.action(mContext, getMetricsCategory(), !isEnabled);
        mController.setHotspotEnabled(!isEnabled);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_hotspot_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.disabledByPolicy = mController.isHotspotSupported();

        if (arg instanceof Boolean) {
            state.value = (boolean) arg;
        } else {
            state.value = mController.isHotspotEnabled();
        }
        if (state.disabledByPolicy && state.value) {
            final List<WifiDevice> clients = mConnectivityManager.getTetherConnectedSta();
            final int count = clients != null ? clients.size() : 0;
            state.label = mContext.getResources().getQuantityString(
                    R.plurals.wifi_hotspot_connected_clients_label, count, count);
        } else {
            state.label = mContext.getString(R.string.quick_settings_hotspot_label);
        }
        state.icon = state.disabledByPolicy && state.value ? mEnable : mDisable;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_HOTSPOT;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_hotspot_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_hotspot_changed_off);
        }
    }

    private BroadcastReceiver mTetherConnectStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };

    private final class Callback implements HotspotController.Callback {
        @Override
        public void onHotspotChanged(boolean enabled) {
            refreshState(enabled);
        }
    };
}
