/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.util.concurrent.atomic.AtomicReference;

public class BluetoothTetheringTile extends QuickSettingsTile {
    private final String TAG = "BluetoothTetheringTile";
    private final int on_drawable = R.drawable.ic_qs_bluetooth_tethering_on;
    private final int off_drawable = R.drawable.ic_qs_bluetooth_tethering_off;
    
    private AtomicReference<BluetoothPan> mBluetoothPan = new AtomicReference<BluetoothPan>();
    private boolean mBluetoothEnableForTether; // True while we wait for BT adapter to switch on before starting tether
    
    private BluetoothProfile.ServiceListener mProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                mBluetoothPan.set((BluetoothPan) proxy);
            }
            public void onServiceDisconnected(int profile) {
                mBluetoothPan.set(null);
            }
    };
    
    public BluetoothTetheringTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);
        
        // click listeners
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBluetoothTethering(!getEnabled());
                updateResources();
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                startSettingsActivity(intent);
                return true;
            }
        };
        qsc.registerAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED, this);
        qsc.registerAction(BluetoothAdapter.ACTION_STATE_CHANGED, this);
        updateResources();
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            if (mBluetoothEnableForTether) {
                switch (intent
                        .getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    case BluetoothAdapter.STATE_ON:
                        BluetoothPan bluetoothPan = mBluetoothPan.get();
                        if (bluetoothPan != null) {
                            bluetoothPan.setBluetoothTethering(true);
                            mBluetoothEnableForTether = false;
                        }
                        break;

                    case BluetoothAdapter.STATE_OFF:
                    case BluetoothAdapter.ERROR:
                        mBluetoothEnableForTether = false;
                        break;

                    default:
                        // ignore transition states
                }
            }
        }
        if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)
                || intent.getAction().equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED) )
            updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private synchronized void updateTile() {
        if (getEnabled()) {
            mDrawable = on_drawable;
            mLabel = mContext.getString(R.string.quick_settings_bluetooth_tethering_on);
        } else {
            // TODO: create on and off icons
            mDrawable = off_drawable;
            mLabel = mContext.getString(R.string.quick_settings_bluetooth_tethering_off);
        }
    }
    
    private void setBluetoothTethering(boolean enabled) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            if (enabled) {    
                if (adapter.getState() == BluetoothAdapter.STATE_OFF) {
                    //Get the Pan Profile proxy object while turning on BT
                    adapter.getProfileProxy(mContext,
                            mProfileServiceListener, BluetoothProfile.PAN);
                    mBluetoothEnableForTether = true;
                    adapter.enable();
                    //mBluetoothTether.setSummary(R.string.bluetooth_turning_on); // TODO: feedback on status
                    //mBluetoothTether.setEnabled(false);
                } else {
                    BluetoothPan bluetoothPan = mBluetoothPan.get();
                    if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(true);
                    //mBluetoothTether.setSummary(R.string.bluetooth_tethering_available_subtext);
                }
            } else {
                BluetoothPan bluetoothPan = mBluetoothPan.get();
                if (bluetoothPan != null) bluetoothPan.setBluetoothTethering(false);
            }
        }
    }

    private boolean getEnabled() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothPan pan = mBluetoothPan.get();
        return (adapter != null && adapter.getState() == BluetoothAdapter.STATE_ON 
                && pan != null && pan.isTetheringOn());
    }
}
