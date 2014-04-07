/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import java.util.Set;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BluetoothController;

public class BluetoothModeChanger extends ModeChanger {

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothController mBluetoothController;
    private boolean mConnected = false;

    public BluetoothModeChanger(Context context) {
        super(context);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setConnected(boolean connect) {
        mConnected = connect;
    }

    public void setController(BluetoothController controller) {
        mBluetoothController = controller;
    }

    @Override
    public void setModeEnabled(boolean enabled) {
        super.setModeEnabled(enabled);
        setWasEnabled(isStateEnabled());
    }

    @Override
    public boolean isDelayChanges() {
        if (!isSupported()) return false;
        Set<BluetoothDevice> btDevices = mBluetoothController.getBondedBluetoothDevices();
        return (btDevices.size() == 1) && mConnected;
    }

    @Override
    public boolean isStateEnabled() {
        if (!isSupported()) return false;
        return mBluetoothAdapter.isEnabled();
    }

    @Override
    public boolean isSupported() {
        return (mBluetoothAdapter != null) && isModeEnabled();
    }

    @Override
    public int getMode() {
        return 0;
    }

    @Override
    public void stateNormal() {
        if (!isStateEnabled()) {
            mBluetoothAdapter.enable();
        }
    }

    @Override
    public void stateSaving() {
        if (isStateEnabled()) {
            mBluetoothAdapter.disable();
        }
    }

    @Override
    public boolean checkModes() {
        if (isDelayChanges()) {
            // bluetooth has paired devices and connected, delay changing mode
            changeMode(true, false);
            if (BatterySaverService.DEBUG) {
                Log.i(BatterySaverService.TAG, " delayed bluetooth changing because connected devices ");
            }
            return false;
        }
        return true;
    }

    @Override
    public void setModes() {
        super.setModes();
    }
}
