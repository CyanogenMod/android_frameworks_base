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
import android.util.Log;
import android.widget.Switch;
import android.os.UserManager;

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
    private final GlobalSetting mAirplaneMode;
    private boolean mListening;
    private int mNumConnectedClients = 0;

    public HotspotTile(Host host) {
        super(host);
        mController = host.getHotspotController();
        mConnectivityManager = host.getContext().getSystemService(ConnectivityManager.class);
        mAirplaneMode = new GlobalSetting(mContext, mHandler, Global.AIRPLANE_MODE_ON) {
            @Override
            protected void handleValueChanged(int value) {
                refreshState();
            }
        };

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
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            refreshState();
        } else {
            mController.removeCallback(mCallback);
            mContext.unregisterReceiver(mTetherConnectStateChangedReceiver);
        }
        mListening = listening;
        mAirplaneMode.setListening(listening);
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
    }

    @Override
    protected void handleClick() {
        final boolean isEnabled = (Boolean) mState.value;
        if (!isEnabled && mAirplaneMode.getValue() != 0) {
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
    protected void handleUpdateState(AirplaneBooleanState state, Object arg) {
        boolean visible = mController.isHotspotEnabled();
        state.label = mContext.getString(R.string.quick_settings_hotspot_label);
        checkIfRestrictionEnforcedByAdminOnly(state, UserManager.DISALLOW_CONFIG_TETHERING);
        if (arg instanceof Boolean) {
            state.value = (boolean) arg;
        } else {
            state.value = mController.isHotspotEnabled();
        }
        state.icon = state.value ? mEnable : mDisable;
        boolean wasAirplane = state.isAirplaneMode;
        state.isAirplaneMode = mAirplaneMode.getValue() != 0;
        if (state.isAirplaneMode) {
            final int disabledColor = mHost.getContext().getColor(R.color.qs_tile_tint_unavailable);
            state.label = new SpannableStringBuilder().append(state.label,
                    new ForegroundColorSpan(disabledColor),
                    SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            state.icon = mUnavailable;
        } else if (wasAirplane) {
            state.icon = mDisableNoAnimation;
        }
        state.minimalAccessibilityClassName = state.expandedAccessibilityClassName
                = Switch.class.getName();
        state.contentDescription = state.label;
        if (visible && state.value) {
            state.label = mContext.getResources().getQuantityString(
                    R.plurals.wifi_hotspot_connected_clients_label, mNumConnectedClients,
                    mNumConnectedClients);
            mNumConnectedClients = 0;
        } else {
            state.label = mContext.getString(R.string.quick_settings_hotspot_label);
        }
        state.icon = visible && state.value ? mEnable : mDisable;

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
            final List<WifiDevice> clients = mConnectivityManager.getTetherConnectedSta();
            mNumConnectedClients = clients != null ? clients.size() : 0;
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
