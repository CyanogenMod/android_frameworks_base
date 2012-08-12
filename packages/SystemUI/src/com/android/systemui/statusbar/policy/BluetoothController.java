/*
 * Copyright (C) 2008 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2012 ParanoidAndroid Project
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

import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.widget.ImageView;
import android.widget.CompoundButton;

import com.android.systemui.R;

public class BluetoothController extends BroadcastReceiver implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.BluetoothController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();

    private CompoundButton mCheckBox;
    private final BluetoothAdapter mAdapter;
    private int mIconId = R.drawable.stat_sys_data_bluetooth;
    private int mContentDescriptionId = 0;
    private int mState = BluetoothAdapter.ERROR;
    private boolean mEnabled = false;

    public BluetoothController(Context context, CompoundButton checkbox) {
        this(context);

        mCheckBox = checkbox;
        mCheckBox.setChecked(mEnabled);
        mCheckBox.setOnCheckedChangeListener(this);
    }

    public BluetoothController(Context context) {
        mContext = context;

        mAdapter =  BluetoothAdapter.getDefaultAdapter();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        context.registerReceiver(this, filter);

        if (mAdapter != null) {
            handleAdapterStateChange(mAdapter.getState());
            handleConnectionStateChange(mAdapter.getConnectionState());
        }
        refreshViews();
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            handleAdapterStateChange(
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR));
        } else if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
            handleConnectionStateChange(
                    intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED));
        }
        refreshViews();
    }

    public void handleAdapterStateChange(int adapterState) {
        mEnabled = (adapterState == BluetoothAdapter.STATE_ON);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
        if (checked != mEnabled) {
            mEnabled = checked;
            setBluetoothEnabled(mEnabled);
            setBluetoothStateInt(mAdapter.getState());
            syncBluetoothState();
        }
    }

    public void setBluetoothEnabled(boolean enabled) {
        boolean success = enabled
                ? mAdapter.enable()
                : mAdapter.disable();

        if (success)
            setBluetoothStateInt(enabled ? BluetoothAdapter.STATE_TURNING_ON : BluetoothAdapter.STATE_TURNING_OFF);
        else
            syncBluetoothState();
    }

    boolean syncBluetoothState() {
        int currentState = mAdapter.getState();
        if (currentState != mState) {
            setBluetoothStateInt(mState);
            return true;
        }
        return false;
    }

    synchronized void setBluetoothStateInt(int state) {
        mState = state;
        if (state == BluetoothAdapter.STATE_ON){
            if (mCheckBox != null)
                mCheckBox.setChecked(true);
        }
        else if (mCheckBox != null)
            mCheckBox.setChecked(false);
    }

    public void handleConnectionStateChange(int connectionState) {
        final boolean connected = (connectionState == BluetoothAdapter.STATE_CONNECTED);
        if (connected) {
            mIconId = R.drawable.stat_sys_data_bluetooth_connected;
            mContentDescriptionId = R.string.accessibility_bluetooth_connected;
        } else {
            mIconId = R.drawable.stat_sys_data_bluetooth;
            mContentDescriptionId = R.string.accessibility_bluetooth_disconnected;
        }
    }

    public void refreshViews() {
        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            v.setImageResource(mIconId);
            v.setVisibility(mEnabled ? View.VISIBLE : View.GONE);
            v.setContentDescription((mContentDescriptionId == 0)
                    ? null
                    : mContext.getString(mContentDescriptionId));
        }
    }
}
