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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.internal.logging.MetricsLogger;
import com.android.settingslib.wifi.AccessPoint;

import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.AccessPointController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.SignalCallbackAdapter;

import cyanogenmod.app.StatusBarPanelCustomTile;

import java.util.ArrayList;
import java.util.List;

/** Quick settings tile: Wifi **/
public class WifiTile extends QSTile<QSTile.SignalState> {
    private static final Intent WIFI_SETTINGS = new Intent(Settings.ACTION_WIFI_SETTINGS);

    private final NetworkController mController;
    private final AccessPointController mWifiController;
    private final WifiDetailAdapter mDetailAdapter;
    private final QSTile.SignalState mStateBeforeClick = newTileState();

    private final WifiSignalCallback mSignalCallback = new WifiSignalCallback();

    public WifiTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mWifiController = mController.getAccessPointController();
        mDetailAdapter = new WifiDetailAdapter();
    }

    @Override
    public boolean hasDualTargetsDetails() {
        return true;
    }

    @Override
    protected SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addSignalCallback(mSignalCallback);
        } else {
            mController.removeSignalCallback(mSignalCallback);
        }
    }

    @Override
    public void setDetailListening(boolean listening) {
        if (listening) {
            mWifiController.addAccessPointCallback(mDetailAdapter);
        } else {
            mWifiController.removeAccessPointCallback(mDetailAdapter);
        }
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    public QSTileView createTileView(Context context) {
        return new SignalTileView(context);
    }

    @Override
    protected void handleClick() {
        mState.copyTo(mStateBeforeClick);
        MetricsLogger.action(mContext, getMetricsCategory(), !mState.enabled);
        mController.setWifiEnabled(!mState.enabled);
    }

    @Override
    protected void handleSecondaryClick() {
        if (!mWifiController.canConfigWifi()) {
            mHost.startActivityDismissingKeyguard(new Intent(Settings.ACTION_WIFI_SETTINGS));
            return;
        }
        if (!mState.enabled) {
            mController.setWifiEnabled(true);
            mState.enabled = true;
        }
        showDetail(true);
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(WIFI_SETTINGS);
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = true;
        if (DEBUG) Log.d(TAG, "handleUpdateState arg=" + arg);
        final CallbackInfo cb;
        if (arg == null) {
            cb = mSignalCallback.mInfo;
        } else {
            cb = (CallbackInfo) arg;
        }

        boolean wifiConnected = cb.enabled && (cb.wifiSignalIconId > 0) && (cb.enabledDesc != null);
        boolean wifiNotConnected = (cb.wifiSignalIconId > 0) && (cb.enabledDesc == null);
        boolean enabledChanging = state.enabled != cb.enabled;
        if (enabledChanging) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                // on main thread, bypass the handler
                mDetailAdapter.setItemsVisible(cb.enabled);
            } else {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDetailAdapter.setItemsVisible(cb.enabled);
                    }
                });
            }
            fireToggleStateChanged(cb.enabled);
        }
        state.enabled = cb.enabled;
        state.connected = wifiConnected;
        state.activityIn = cb.enabled && cb.activityIn;
        state.activityOut = cb.enabled && cb.activityOut;
        state.filter = true;
        final String signalContentDescription;
        final Resources r = mContext.getResources();
        if (!state.enabled) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_wifi_disabled);
            state.label = r.getString(R.string.quick_settings_wifi_label);
            signalContentDescription = r.getString(R.string.accessibility_wifi_off);
        } else if (wifiConnected) {
            state.icon = ResourceIcon.get(cb.wifiSignalIconId);
            state.label = removeDoubleQuotes(cb.enabledDesc);
            signalContentDescription = cb.wifiSignalContentDescription;
        } else if (wifiNotConnected) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_wifi_full_0);
            state.label = r.getString(R.string.quick_settings_wifi_label);
            signalContentDescription = r.getString(R.string.accessibility_no_wifi);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_wifi_no_network);
            state.label = r.getString(R.string.quick_settings_wifi_label);
            signalContentDescription = r.getString(R.string.accessibility_wifi_off);
        }
        state.contentDescription = mContext.getString(
                R.string.accessibility_quick_settings_wifi,
                signalContentDescription);
        String wifiName = state.label;
        if (state.connected) {
            wifiName = r.getString(R.string.accessibility_wifi_name, state.label);
        }
        state.dualLabelContentDescription = wifiName;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_WIFI;
    }

    @Override
    protected boolean shouldAnnouncementBeDelayed() {
        return mStateBeforeClick.enabled == mState.enabled;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.enabled) {
            return mContext.getString(R.string.accessibility_quick_settings_wifi_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_wifi_changed_off);
        }
    }

    private static String removeDoubleQuotes(String string) {
        if (string == null) return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    private static final class CallbackInfo {
        boolean enabled;
        boolean connected;
        int wifiSignalIconId;
        String enabledDesc;
        boolean activityIn;
        boolean activityOut;
        String wifiSignalContentDescription;

        @Override
        public String toString() {
            return new StringBuilder("CallbackInfo[")
                .append("enabled=").append(enabled)
                .append(",connected=").append(connected)
                .append(",wifiSignalIconId=").append(wifiSignalIconId)
                .append(",enabledDesc=").append(enabledDesc)
                .append(",activityIn=").append(activityIn)
                .append(",activityOut=").append(activityOut)
                .append(",wifiSignalContentDescription=").append(wifiSignalContentDescription)
                .append(']').toString();
        }
    }

    private final class WifiSignalCallback extends SignalCallbackAdapter {
        final CallbackInfo mInfo = new CallbackInfo();

        @Override
        public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
                boolean activityIn, boolean activityOut, String description) {
            if (DEBUG) Log.d(TAG, "onWifiSignalChanged enabled=" + enabled);
            mInfo.enabled = enabled;
            mInfo.connected = qsIcon.visible;
            mInfo.wifiSignalIconId = qsIcon.icon;
            mInfo.enabledDesc = description;
            mInfo.activityIn = activityIn;
            mInfo.activityOut = activityOut;
            mInfo.wifiSignalContentDescription = qsIcon.contentDescription;
            refreshState(mInfo);
        }
    };

    private final class WifiDetailAdapter implements DetailAdapter,
            AccessPointController.AccessPointCallback, AdapterView.OnItemClickListener {

        private QSDetailItemsList mItemsList;
        private List<AccessPoint> mAccessPoints;
        private List<Item> mDisplayedAccessPoints = new ArrayList<>();
        private QSDetailItemsList.QSDetailListAdapter mAdapter;

        @Override
        public int getTitle() {
            return R.string.quick_settings_wifi_label;
        }

        public Intent getSettingsIntent() {
            return WIFI_SETTINGS;
        }

        @Override
        public StatusBarPanelCustomTile getCustomTile() {
            return null;
        }

        @Override
        public Boolean getToggleState() {
            return mState.enabled;
        }

        @Override
        public void setToggleState(boolean state) {
            if (DEBUG) Log.d(TAG, "setToggleState " + state);
            MetricsLogger.action(mContext, MetricsLogger.QS_WIFI_TOGGLE, state);
            mController.setWifiEnabled(state);
            showDetail(false);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsLogger.QS_WIFI_DETAILS;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            if (DEBUG) Log.d(TAG, "createDetailView convertView=" + (convertView != null));
            mAccessPoints = null;
            mWifiController.scanForAccessPoints();
            fireScanStateChanged(true);
            mItemsList = QSDetailItemsList.convertOrInflate(context, convertView, parent);
            ListView listView = mItemsList.getListView();
            listView.setDivider(null);
            listView.setOnItemClickListener(this);
            listView.setAdapter(mAdapter =
                    new QSDetailItemsList.QSDetailListAdapter(context, mDisplayedAccessPoints));
            mItemsList.setEmptyState(R.drawable.ic_qs_wifi_detail_empty,
                    R.string.quick_settings_wifi_detail_empty_text);
            updateItems();
            return mItemsList;
        }

        @Override
        public void onAccessPointsChanged(final List<AccessPoint> accessPoints) {
            mAccessPoints = accessPoints;
            updateItems();
            if (accessPoints != null && accessPoints.size() > 0) {
                fireScanStateChanged(false);
            }
        }

        @Override
        public void onSettingsActivityTriggered(Intent settingsIntent) {
            mHost.startActivityDismissingKeyguard(settingsIntent);
        }

        public void setItemsVisible(boolean visible) {
            if (mAdapter == null) return;
            if (visible) {
                updateItems();
            } else {
                mDisplayedAccessPoints.clear();
            }
            mAdapter.notifyDataSetChanged();
        }

        private void updateItems() {
            if (mAdapter == null) return;
            if (mAccessPoints != null) {
                mDisplayedAccessPoints.clear();
                for (int i = 0; i < mAccessPoints.size(); i++) {
                    final AccessPoint ap = mAccessPoints.get(i);
                    final Item item = new Item();
                    item.tag = ap;
                    item.icon = mWifiController.getIcon(ap);
                    item.line1 = ap.getSsid();
                    item.line2 = ap.isActive() ? ap.getSummary() : null;
                    item.overlay = ap.getSecurity() != AccessPoint.SECURITY_NONE
                            ? mContext.getDrawable(R.drawable.qs_ic_wifi_lock)
                            : null;
                    mDisplayedAccessPoints.add(item);
                }
            }
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Item item = (Item) parent.getItemAtPosition(position);
            if (item == null || item.tag == null) return;
            final AccessPoint ap = (AccessPoint) item.tag;
            if (!ap.isActive()) {
                if (mWifiController.connect(ap)) {
                    mHost.collapsePanels();
                }
            }
            showDetail(false);
        }
    };
}
