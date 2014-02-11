/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class BluetoothController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BluetoothController";

    public interface BluetoothDeviceConnectionStateChangeCallback {
        void onDeviceConnectionStateChange(BluetoothDevice device);
        void onDeviceNameChange(BluetoothDevice device);
    }

    private boolean mEnabled = false;

    private Set<BluetoothDevice> mBondedDevices = new HashSet<BluetoothDevice>();
    private Set<BluetoothDevice> mConnectedDevices = new HashSet<BluetoothDevice>();

    private ArrayList<BluetoothStateChangeCallback> mChangeCallbacks =
            new ArrayList<BluetoothStateChangeCallback>();
    private ArrayList<BluetoothDeviceConnectionStateChangeCallback> mConnectionChangeCallbacks =
            new ArrayList<BluetoothDeviceConnectionStateChangeCallback>();

    public BluetoothController(Context context) {

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ALIAS_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        context.registerReceiver(this, filter);

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            handleAdapterStateChange(adapter.getState());
        }
        fireCallbacks();
        updateBondedBluetoothDevices();
    }

    public void addStateChangedCallback(BluetoothStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void removeStateChangedCallback(BluetoothStateChangeCallback cb) {
        mChangeCallbacks.remove(cb);
    }

    public void addConnectionStateChangedCallback(
            BluetoothDeviceConnectionStateChangeCallback cb) {
        mConnectionChangeCallbacks.add(cb);
    }

    public void removeConnectionStateChangedCallback(
            BluetoothDeviceConnectionStateChangeCallback cb) {
        mConnectionChangeCallbacks.remove(cb);
    }

    public Set<BluetoothDevice> getBondedBluetoothDevices() {
        return mBondedDevices;
    }

    public Set<BluetoothDevice> getConnectedBluetoothDevices() {
        return mConnectedDevices;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            handleAdapterStateChange(
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR));
        }

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)
                || action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                || action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
            fireCallbacks();
            updateBondedBluetoothDevices();
        }

        if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
            mConnectedDevices.add(device);
            fireConnectionStateChanged(device);
        } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
            mConnectedDevices.remove(device);
            fireConnectionStateChanged(device);
        } else if (BluetoothDevice.ACTION_ALIAS_CHANGED.equals(action) ||
                BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
            BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
            fireDeviceNameChanged(device);
        }
    }

    private void updateBondedBluetoothDevices() {
        mBondedDevices.clear();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            if (devices != null) {
                for (BluetoothDevice device : devices) {
                    if (device.getBondState() != BluetoothDevice.BOND_NONE) {
                        mBondedDevices.add(device);
                    }
                }
            }
        }
    }

    private void handleAdapterStateChange(int adapterState) {
        mEnabled = (adapterState == BluetoothAdapter.STATE_ON);
    }

    private void fireCallbacks() {
        for (BluetoothStateChangeCallback cb : mChangeCallbacks) {
            cb.onBluetoothStateChange(mEnabled);
        }
    }

    private void fireConnectionStateChanged(BluetoothDevice device) {
        for (BluetoothDeviceConnectionStateChangeCallback cb : mConnectionChangeCallbacks) {
            cb.onDeviceConnectionStateChange(device);
        }
    }

    private void fireDeviceNameChanged(BluetoothDevice device) {
        for (BluetoothDeviceConnectionStateChangeCallback cb : mConnectionChangeCallbacks) {
            cb.onDeviceNameChange(device);
        }
    }
}
