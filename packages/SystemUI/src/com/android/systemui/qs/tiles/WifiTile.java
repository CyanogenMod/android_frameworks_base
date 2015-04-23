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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ListView;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSDetailItemsList;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTileView;
import com.android.systemui.qs.SignalTileView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.AccessPointController;
import com.android.systemui.statusbar.policy.NetworkController.AccessPointController.AccessPoint;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import java.util.ArrayList;
import java.util.List;

/** Quick settings tile: Wifi **/
public class WifiTile extends QSTile<QSTile.SignalState> {
    private static final Intent WIFI_SETTINGS = new Intent(Settings.ACTION_WIFI_SETTINGS);

    private final NetworkController mController;
    private final AccessPointController mWifiController;
    private final WifiDetailAdapter mDetailAdapter;
    private final QSTile.SignalState mStateBeforeClick = newTileState();

    public WifiTile(Host host) {
        super(host);
        mController = host.getNetworkController();
        mWifiController = mController.getAccessPointController();
        mDetailAdapter = new WifiDetailAdapter();
    }

    @Override
    protected SignalState newTileState() {
        return new SignalState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addNetworkSignalChangedCallback(mCallback);
            mWifiController.addAccessPointCallback(mDetailAdapter);
        } else {
            mController.removeNetworkSignalChangedCallback(mCallback);
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
        if (!isRadioProhibited()) {
            mState.copyTo(mStateBeforeClick);
            mController.setWifiEnabled(!mState.enabled);
        }
    }

    @Override
    protected void handleSecondaryClick() {
        if (!mWifiController.canConfigWifi()) {
            mHost.startSettingsActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
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
        mHost.startSettingsActivity(WIFI_SETTINGS);
    }

    @Override
    protected void handleUpdateState(SignalState state, Object arg) {
        state.visible = true;
        if (DEBUG) Log.d(TAG, "handleUpdateState arg=" + arg);
        if (arg == null) return;
        CallbackInfo cb = (CallbackInfo) arg;

        boolean wifiConnected = cb.enabled && (cb.wifiSignalIconId > 0) && (cb.enabledDesc != null);
        boolean wifiNotConnected = (cb.wifiSignalIconId > 0) && (cb.enabledDesc == null);
        boolean enabledChanging = state.enabled != cb.enabled;
        if (enabledChanging) {
            mDetailAdapter.setItemsVisible(cb.enabled);
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
            state.icon = ResourceIcon.get(R.drawable.ic_qs_wifi_0);
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

    public boolean isRadioProhibited() {
        boolean airModeOn = (android.provider.Settings.System.getInt(
                mContext.getContentResolver(),
                android.provider.Settings.System.AIRPLANE_MODE_ON, 0) != 0);
        boolean disable = mContext.getResources().getBoolean(R.bool.config_disableWifiAndBluetooth);
        return disable && airModeOn;
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

    private final NetworkSignalChangedCallback mCallback = new NetworkSignalChangedCallback() {
        @Override
        public void onWifiSignalChanged(boolean enabled, boolean connected, int wifiSignalIconId,
                boolean activityIn, boolean activityOut,
                String wifiSignalContentDescriptionId, String description) {
            if (DEBUG) Log.d(TAG, "onWifiSignalChanged enabled=" + enabled);
            final CallbackInfo info = new CallbackInfo();
            info.enabled = enabled;
            info.connected = connected;
            info.wifiSignalIconId = wifiSignalIconId;
            info.enabledDesc = description;
            info.activityIn = activityIn;
            info.activityOut = activityOut;
            info.wifiSignalContentDescription = wifiSignalContentDescriptionId;
            refreshState(info);
        }

        @Override
        public void onMobileDataSignalChanged(boolean enabled,
                int mobileSignalIconId,
                String mobileSignalContentDescriptionId, int dataTypeIconId,
                boolean activityIn, boolean activityOut,
                String dataTypeContentDescriptionId, String description,
                boolean isDataTypeIconWide) {
            // noop
        }

        public void onNoSimVisibleChanged(boolean noSims) {
            // noop
        }

        @Override
        public void onAirplaneModeChanged(boolean enabled) {
            // noop
        }

        @Override
        public void onMobileDataEnabled(boolean enabled) {
            // noop
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
        public Boolean getToggleState() {
            return mState.enabled;
        }

        @Override
        public void setToggleState(boolean state) {
            if (DEBUG) Log.d(TAG, "setToggleState " + state);
            mController.setWifiEnabled(state);
            showDetail(false);
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
            setItemsVisible(mState.enabled);
            return mItemsList;
        }

        @Override
        public void onAccessPointsChanged(final List<AccessPoint> accessPoints) {
            mAccessPoints = accessPoints;
            setItemsVisible(mState.enabled);
            if (accessPoints != null && accessPoints.size() > 0) {
                fireScanStateChanged(false);
            }
        }

        @Override
        public void onSettingsActivityTriggered(Intent intent) {
            mHost.startSettingsActivity(intent);
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
                    item.icon = ap.iconId;
                    item.line1 = ap.ssid;
                    if (ap.isConnected) {
                        item.line2 = mContext.getString(ap.isConfigured ?
                                R.string.quick_settings_connected :
                                R.string.quick_settings_connected_via_wfa);
                    } else if (ap.networkId >= 0) {
                        // TODO: Set line 2 to wifi saved string here.
                    }
                    item.overlay = ap.hasSecurity
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
            if (!ap.isConnected) {
                if (mWifiController.connect(ap)) {
                    mHost.collapsePanels();
                }
            }
            showDetail(false);
        }
    };
}
