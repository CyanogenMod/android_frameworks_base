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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.DataUsageInfo;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

/** Quick settings tile: Cellular **/
public class CellularTile extends QSTile<QSTile.SignalState> {
    private static final Intent DATA_USAGE_SETTINGS = new Intent().setComponent(new ComponentName(
            "com.android.settings", "com.android.settings.Settings$DataUsageSummaryActivity"));
    private static final Intent MOBILE_NETWORK_SETTINGS = new Intent(Intent.ACTION_MAIN)
            .setComponent(new ComponentName("com.android.phone",
                    "com.android.phone.MobileNetworkSettings"));
    private static final Intent MOBILE_NETWORK_SETTINGS_MSIM = new Intent(Intent.ACTION_MAIN)
            .setClassName("com.android.phone", "com.android.phone.msim.SelectSubscription")
            .putExtra("PACKAGE", "com.android.phone")
            .putExtra("TARGET_CLASS", "com.android.phone.MobileNetworkSettings")
            .putExtra("TARGET_THEME", "Theme.Material.Settings");

    private final NetworkController mController;
    private final CellularDetailAdapter mDetailAdapter;
    TelephonyManager mTelephonyManager;

    public CellularTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mDetailAdapter = new CellularDetailAdapter();
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    protected SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addNetworkSignalChangedCallback(mCallback);
        } else {
            mController.removeNetworkSignalChangedCallback(mCallback);
        }
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new SignalTileView(context);
    }

    @Override
    protected void handleClick() {
        if (mController.isMobileDataSupported()) {
            showDetail(true);
        } else {
            mHost.startSettingsActivity(DATA_USAGE_SETTINGS);
        }
    }

    @Override
    protected void handleLongClick() {
        if (mTelephonyManager.getDefault().getPhoneCount() > 1) {
            mHost.startSettingsActivity(MOBILE_NETWORK_SETTINGS_MSIM);
        } else {
            mHost.startSettingsActivity(MOBILE_NETWORK_SETTINGS);
        }
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = mController.hasMobileDataFeature();
        if (!state.visible) return;
        final CallbackInfo cb = (CallbackInfo) arg;
        if (cb == null) return;

        final Resources r = mContext.getResources();
        final int iconId = cb.noSim ? R.drawable.ic_qs_no_sim
                : !cb.enabled || cb.airplaneModeEnabled ? R.drawable.ic_qs_signal_disabled
                : cb.mobileSignalIconId > 0 ? cb.mobileSignalIconId
                : R.drawable.ic_qs_signal_no_signal;
        state.icon = ResourceIcon.get(iconId);
        state.isOverlayIconWide = cb.isDataTypeIconWide;
        state.autoMirrorDrawable = !cb.noSim;
        state.overlayIconId = cb.enabled && (cb.dataTypeIconId > 0) ? cb.dataTypeIconId : 0;
        state.filter = iconId != R.drawable.ic_qs_no_sim;
        state.activityIn = cb.enabled && cb.activityIn;
        state.activityOut = cb.enabled && cb.activityOut;

        state.label = cb.enabled
                ? removeTrailingPeriod(cb.enabledDesc)
                : r.getString(R.string.quick_settings_rssi_emergency_only);

        final String signalContentDesc = cb.enabled && (cb.mobileSignalIconId > 0)
                ? cb.signalContentDescription
                : r.getString(R.string.accessibility_no_signal);
        final String dataContentDesc = cb.enabled && (cb.dataTypeIconId > 0) && !cb.wifiEnabled
                ? cb.dataContentDescription
                : r.getString(R.string.accessibility_no_data);
        state.contentDescription = r.getString(
                R.string.accessibility_quick_settings_mobile,
                signalContentDesc, dataContentDesc,
                state.label);
    }

    // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            return string.substring(0, length - 1);
        }
        return string;
    }

    private static final class CallbackInfo {
        boolean enabled;
        boolean wifiEnabled;
        boolean wifiConnected;
        boolean airplaneModeEnabled;
        int mobileSignalIconId;
        String signalContentDescription;
        int dataTypeIconId;
        String dataContentDescription;
        boolean activityIn;
        boolean activityOut;
        String enabledDesc;
        boolean noSim;
        boolean isDataTypeIconWide;
    }

    private final NetworkSignalChangedCallback mCallback = new NetworkSignalChangedCallback() {
        private final CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void onWifiSignalChanged(boolean enabled, boolean connected, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description) {
            mInfo.wifiEnabled = enabled;
            mInfo.wifiConnected = connected;
            refreshState(mInfo);
        }

        @Override
        public void onMobileDataSignalChanged(boolean enabled,
                int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description, boolean noSim,
                boolean isDataTypeIconWide) {
            mInfo.enabled = enabled;
            mInfo.mobileSignalIconId = mobileSignalIconId;
            mInfo.signalContentDescription = mobileSignalContentDescriptionId;
            mInfo.dataTypeIconId = dataTypeIconId;
            mInfo.dataContentDescription = dataTypeContentDescriptionId;
            mInfo.activityIn = activityIn;
            mInfo.activityOut = activityOut;
            mInfo.enabledDesc = description;
            mInfo.noSim = noSim;
            mInfo.isDataTypeIconWide = isDataTypeIconWide;
            refreshState(mInfo);
        }

        @Override
        public void onNoSimVisibleChanged(boolean visible) {
            mInfo.noSim = visible;
            if (mInfo.noSim) {
                // Make sure signal gets cleared out when no sims.
                mInfo.mobileSignalIconId = 0;
                mInfo.dataTypeIconId = 0;
                // Show a No SIMs description to avoid emergency calls message.
                mInfo.enabled = true;
                mInfo.enabledDesc = mContext.getString(
                        R.string.keyguard_missing_sim_message_short);
                mInfo.signalContentDescription = mInfo.enabledDesc;
            }
            refreshState(mInfo);
        }

        @Override
        public void onAirplaneModeChanged(boolean enabled) {
            mInfo.airplaneModeEnabled = enabled;
            refreshState(mInfo);
        }

        public void onMobileDataEnabled(boolean enabled) {
            mDetailAdapter.setMobileDataEnabled(enabled);
        }
    };

    private final class CellularDetailAdapter implements DetailAdapter {

        @Override
        public int getTitle() {
            return R.string.quick_settings_cellular_detail_title;
        }

        @Override
        public Boolean getToggleState() {
            return mController.isMobileDataSupported()
                    ? mController.isMobileDataEnabled()
                    : null;
        }

        @Override
        public Intent getSettingsIntent() {
            return DATA_USAGE_SETTINGS;
        }

        @Override
        public void setToggleState(boolean state) {
            mController.setMobileDataEnabled(state);
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            final DataUsageDetailView v = (DataUsageDetailView) (convertView != null
                    ? convertView
                    : LayoutInflater.from(mContext).inflate(R.layout.data_usage, parent, false));
            final DataUsageInfo info = mController.getDataUsageInfo();
            if (info == null) return v;
            v.bind(info);
            return v;
        }

        public void setMobileDataEnabled(boolean enabled) {
            fireToggleStateChanged(enabled);
        }
    }
}
