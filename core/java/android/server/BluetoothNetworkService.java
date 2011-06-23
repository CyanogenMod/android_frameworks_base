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

/**
 * TODO: Move this to services.jar
 * and make the contructor package private again.
 * @hide
 */

package android.server;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHid;
import android.bluetooth.BluetoothNetwork;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothNetwork;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.ParcelUuid;
import android.util.Log;

public class BluetoothNetworkService extends IBluetoothNetwork.Stub {
    private static final String TAG = "BluetoothNetworkService";
    private static final boolean DBG = true;

    public static final String BLUETOOTH_NETWORK_SERVICE = "bluetooth_network";
    
    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;
    private static final String BLUETOOTH_ENABLED = "bluetooth_enabled";
    
    private final Context mContext;
    private final BluetoothService mBluetoothService;
    private final BluetoothAdapter mAdapter;
    private final IntentFilter mIntentFilter;
    
    public BluetoothNetworkService(Context context, BluetoothService bluetoothService) {
        mContext = context;
        log("starting...");
        mBluetoothService = bluetoothService;
        if (mBluetoothService == null) {
            throw new RuntimeException("Platform does not support Bluetooth");
        }
        

        if (!initNative()) {
            throw new RuntimeException("Could not init BluetoothNetworkService");
        }
        
        log("passed init native.");
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, mIntentFilter);
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothService.isEnabled())
            onBluetoothEnable();
    }
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                               BluetoothAdapter.ERROR);
                switch (state) {
                case BluetoothAdapter.STATE_ON:
                    onBluetoothEnable();
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    onBluetoothDisable();
                    break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                                                   BluetoothDevice.ERROR);
                switch(bondState) {
                case BluetoothDevice.BOND_BONDED:
                    break;
                case BluetoothDevice.BOND_BONDING:
                case BluetoothDevice.BOND_NONE:
                    break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            	synchronized (this) {
                    
            	}
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                synchronized (this) {
                }
            }
        }
    };

    @Override
    protected void finalize() throws Throwable {
        try {
            cleanupNative();
        } finally {
            super.finalize();
        }
    }

    private int convertBluezSinkStringtoState(String value) {
        if (value.equalsIgnoreCase("disconnected"))
            return BluetoothNetwork.STATE_DISCONNECTED;
        if (value.equalsIgnoreCase("connecting"))
            return BluetoothNetwork.STATE_CONNECTING;
        if (value.equalsIgnoreCase("connected"))
            return BluetoothNetwork.STATE_CONNECTED;
        return -1;
    }

    private synchronized void onBluetoothEnable() {
        String devices = mBluetoothService.getProperty("Devices");

        if (devices != null) {
            String [] paths = devices.split(",");
            for (String path: paths) {
                log(path);
            }
        }
        onBluetoothEnableNative();
    }

    private synchronized void onBluetoothDisable() {
        onBluetoothDisableNative();
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private native boolean initNative();
    private native void cleanupNative();
    
    private native void onBluetoothEnableNative();
    private native void onBluetoothDisableNative();
}
