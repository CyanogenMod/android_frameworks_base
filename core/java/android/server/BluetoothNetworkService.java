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

import java.util.ArrayList;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetoothNetwork;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.InterfaceConfiguration;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Slog;

public class BluetoothNetworkService extends IBluetoothNetwork.Stub {
    private static final String TAG = "BluetoothNetworkService";

    public static final String BLUETOOTH_NETWORK_SERVICE = "bluetooth_network";

    private final Context mContext;
    private final BluetoothService mBluetoothService;

    private INetworkManagementService mService;

    public BluetoothNetworkService(Context context, BluetoothService bluetoothService) {
        mContext = context;
        mBluetoothService = bluetoothService;
        if (mBluetoothService == null) {
            throw new RuntimeException("Platform does not support Bluetooth");
        }
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mService = INetworkManagementService.Stub.asInterface(b);

        mContext.registerReceiver(mReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {

                      ArrayList<String> available = intent.getStringArrayListExtra(
                              ConnectivityManager.EXTRA_AVAILABLE_TETHER);
                      ArrayList<String> active = intent.getStringArrayListExtra(
                              ConnectivityManager.EXTRA_ACTIVE_TETHER);
                      updateTetherState(available, active);

                    }
                },new IntentFilter(ConnectivityManager.ACTION_TETHER_STATE_CHANGED));

        if (mBluetoothService.isEnabled())
            onBluetoothEnable();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
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

    private synchronized void onBluetoothEnable() {
        if(mService == null){
            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            mService = INetworkManagementService.Stub.asInterface(b);
        }

        if(mService == null){
            log("cannot start NetworkManagementService");
            return;
        }

        try {
            mService.startPan();
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    private synchronized void onBluetoothDisable() {
        if(mService == null){
            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            mService = INetworkManagementService.Stub.asInterface(b);
        }

        if(mService == null){
            log("cannot start NetworkManagementService");
            return;
        }

        try {
            mService.stopPan();
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }
    
    private void updateTetherState(ArrayList<String> available, ArrayList<String> tethered) {
        log("updating tether state");
        boolean wifiTethered = false;
        boolean wifiAvailable = false;

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        INetworkManagementService service = INetworkManagementService.Stub.asInterface(b);

        ConnectivityManager mCm = (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        for (String intf : available) {
            log("interface " + intf);
            if (intf.equals("bnep0")) {
                log("configuring bnep0");
                InterfaceConfiguration ifcg = null;
                try {
                    ifcg = service.getInterfaceConfig(intf);
                    log("ifcg:" + ifcg.toString());
                    if (ifcg != null) {
                        /* IP/netmask: 192.168.43.1/255.255.255.0 */
                        ifcg.ipAddr = (192 << 24) + (168 << 16) + (43 << 8) + 1;
                        ifcg.netmask = (255 << 24) + (255 << 16) + (255 << 8) + 0;
                        ifcg.interfaceFlags = "[up broadcast multicast]";
                        service.setInterfaceConfig(intf, ifcg);
                    }
                } catch (Exception e) {
                    Slog.e(TAG, "Error configuring interface " + intf + ", :" + e);
                    return;
                }
                log("about to tether");
                if(mCm.tether(intf) != ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    Slog.e(TAG, "Error tethering "+intf);
                }
            }
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
