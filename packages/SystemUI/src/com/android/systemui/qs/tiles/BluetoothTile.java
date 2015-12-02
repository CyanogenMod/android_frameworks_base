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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.logging.MetricsLogger;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSDetailItems.Item;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.BluetoothController;
import cyanogenmod.app.StatusBarPanelCustomTile;

import java.util.Collection;
import java.util.Set;

/** Quick settings tile: Bluetooth **/
public class BluetoothTile extends QSTile<QSTile.BooleanState>  {
    private static final Intent BLUETOOTH_SETTINGS = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);

    private final BluetoothController mController;
    private final BluetoothDetailAdapter mDetailAdapter;

    public BluetoothTile(Host host) {
        super(host);
        mController = host.getBluetoothController();
        mDetailAdapter = new BluetoothDetailAdapter();
    }

    @Override
    public boolean hasDualTargetsDetails() {
        return true;
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mController.addStateChangedCallback(mCallback);
        } else {
            mController.removeStateChangedCallback(mCallback);
        }
    }

    @Override
    protected void handleClick() {
        final boolean isEnabled = (Boolean)mState.value;
        MetricsLogger.action(mContext, getMetricsCategory(), !isEnabled);
        mController.setBluetoothEnabled(!isEnabled);
    }

    @Override
    protected void handleSecondaryClick() {
        if (!mState.value) {
            mState.value = true;
            mController.setBluetoothEnabled(true);
        }
        showDetail(true);
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(BLUETOOTH_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean supported = mController.isBluetoothSupported();
        final boolean enabled = mController.isBluetoothEnabled();
        final boolean connected = mController.isBluetoothConnected();
        final boolean connecting = mController.isBluetoothConnecting();
        state.visible = supported;
        state.value = enabled;
        state.autoMirrorDrawable = false;
        if (enabled) {
            state.label = null;
            if (connected) {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_connected);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth_connected);
                state.label = mController.getLastDeviceName();
            } else if (connecting) {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_connecting);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth_connecting);
                state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
            } else {
                state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_on);
                state.contentDescription = mContext.getString(
                        R.string.accessibility_quick_settings_bluetooth_on);
            }
            if (TextUtils.isEmpty(state.label)) {
                state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
            }
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_bluetooth_off);
            state.label = mContext.getString(R.string.quick_settings_bluetooth_label);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_bluetooth_off);
        }

        String bluetoothName = state.label;
        if (connected) {
            bluetoothName = state.dualLabelContentDescription = mContext.getString(
                    R.string.accessibility_bluetooth_name, state.label);
        }
        state.dualLabelContentDescription = bluetoothName;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_BLUETOOTH;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(R.string.accessibility_quick_settings_bluetooth_changed_on);
        } else {
            return mContext.getString(R.string.accessibility_quick_settings_bluetooth_changed_off);
        }
    }

    private final BluetoothController.Callback mCallback = new BluetoothController.Callback() {
        @Override
        public void onBluetoothStateChange(boolean enabled) {
            refreshState();
        }

        @Override
        public void onBluetoothDevicesChanged() {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDetailAdapter.updateItems();
                }
            });
            refreshState();
        }
    };

    private final class BluetoothDetailAdapter implements DetailAdapter, QSDetailItems.Callback {
        private QSDetailItems mItems;

        @Override
        public int getTitle() {
            return R.string.quick_settings_bluetooth_label;
        }

        @Override
        public Boolean getToggleState() {
            return mState.value;
        }

        @Override
        public Intent getSettingsIntent() {
            return BLUETOOTH_SETTINGS;
        }

        @Override
        public StatusBarPanelCustomTile getCustomTile() {
            return null;
        }

        @Override
        public void setToggleState(boolean state) {
            MetricsLogger.action(mContext, MetricsLogger.QS_BLUETOOTH_TOGGLE, state);
            mController.setBluetoothEnabled(state);
            showDetail(false);
        }

        @Override
        public int getMetricsCategory() {
            return MetricsLogger.QS_BLUETOOTH_DETAILS;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            mItems = QSDetailItems.convertOrInflate(context, convertView, parent);
            mItems.setTagSuffix("Bluetooth");
            mItems.setEmptyState(R.drawable.ic_qs_bluetooth_detail_empty,
                    R.string.quick_settings_bluetooth_detail_empty_text);
            mItems.setCallback(this);
            mItems.setMinHeightInItems(0);
            updateItems();
            setItemsVisible(mState.value);
            return mItems;
        }

        public void setItemsVisible(boolean visible) {
            if (mItems == null) return;
            mItems.setItemsVisible(visible);
        }

        private void updateItems() {
            if (mItems == null) return;
            Item[] items = null;
            final Collection<CachedBluetoothDevice> devices = mController.getDevices();
            if (devices != null) {
                items = new Item[getBondedCount(devices)];
                int i = 0;
                for (CachedBluetoothDevice device : devices) {
                    if (device.getBondState() == BluetoothDevice.BOND_NONE) continue;
                    final Item item = new Item();
                    item.icon = R.drawable.ic_qs_bluetooth_on;
                    item.line1 = device.getName();
                    int state = device.getMaxConnectionState();
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        item.icon = R.drawable.ic_qs_bluetooth_connected;
                        item.line2 = mContext.getString(R.string.quick_settings_connected);
                        item.canDisconnect = true;
                    } else if (state == BluetoothProfile.STATE_CONNECTING) {
                        item.icon = R.drawable.ic_qs_bluetooth_connecting;
                        item.line2 = mContext.getString(R.string.quick_settings_connecting);
                    }
                    item.tag = device;
                    items[i++] = item;
                }
            }
            mItems.setItems(items);
        }

        private int getBondedCount(Collection<CachedBluetoothDevice> devices) {
            int ct = 0;
            for (CachedBluetoothDevice device : devices) {
                if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                    ct++;
                }
            }
            return ct;
        }

        @Override
        public void onDetailItemClick(Item item) {
            if (item == null || item.tag == null) return;
            final CachedBluetoothDevice device = (CachedBluetoothDevice) item.tag;
            if (device != null && device.getMaxConnectionState()
                    == BluetoothProfile.STATE_DISCONNECTED) {
                mController.connect(device);
            }
        }

        @Override
        public void onDetailItemDisconnect(Item item) {
            if (item == null || item.tag == null) return;
            final CachedBluetoothDevice device = (CachedBluetoothDevice) item.tag;
            if (device != null) {
                mController.disconnect(device);
            }
        }
    }
}
