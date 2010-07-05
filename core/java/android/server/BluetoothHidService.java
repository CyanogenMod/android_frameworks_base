/*
 * Copyright (C) 2009 ISB Corporation
 * Copyright (C) 2010 0xlab
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

import android.bluetooth.BluetoothHid;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothHid;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class BluetoothHidService extends IBluetoothHid.Stub {
    private static final String TAG = "BluetoothHidService";
    private static final boolean DBG = false;

    public static final String BLUETOOTH_HID_SERVICE = "bluetooth_hid";

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;
    private static final String BLUETOOTH_ENABLED = "bluetooth_enabled";

    private final Context mContext;
    private final IntentFilter mIntentFilter;
    private HashMap<BluetoothDevice, Integer> mHidDevices;
    private final BluetoothService mBluetoothService;
    private final BluetoothAdapter mAdapter;


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
                    setHidDevicePriority(device, BluetoothHid.PRIORITY_ON);
                    break;
                case BluetoothDevice.BOND_BONDING:
                case BluetoothDevice.BOND_NONE:
                    setHidDevicePriority(device, BluetoothHid.PRIORITY_OFF);
                    break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
		synchronized (this) {
                    if (mHidDevices.containsKey(device)) {
			int state = mHidDevices.get(device);
			handleHIDStateChange(device, state, BluetoothHid.STATE_CONNECTED);
                    }
                }
            } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                synchronized (this) {
                    if (mHidDevices.containsKey(device)) {
			int state = mHidDevices.get(device);
			handleHIDStateChange(device, state, BluetoothHid.STATE_DISCONNECTED);
                    }
                }
            }
        }
    };


    public BluetoothHidService(Context context, BluetoothService bluetoothService) {
        mContext = context;

        mBluetoothService = bluetoothService;
        if (mBluetoothService == null) {
            throw new RuntimeException("Platform does not support Bluetooth");
        }

        if (!initNative()) {
            throw new RuntimeException("Could not init BluetoothHidService");
        }

	mAdapter = BluetoothAdapter.getDefaultAdapter();
        mIntentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        mIntentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        mContext.registerReceiver(mReceiver, mIntentFilter);

        mHidDevices = new HashMap<BluetoothDevice, Integer>();

        if (mBluetoothService.isEnabled())
            onBluetoothEnable();
    }


    @Override
    protected void finalize() throws Throwable {
        try {
            cleanupNative();
        } finally {
            super.finalize();
        }
    }

    private boolean isHidDevice(BluetoothDevice device) {
        ParcelUuid[] uuids = mBluetoothService.getRemoteUuids(device.getAddress());
        if (uuids != null && BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.HID)) {
            return true;
        }
        return false;
    }

    private synchronized boolean addHidDevice (BluetoothDevice device) {
        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        String propValues[] = (String []) getHidPropertiesNative(path);
        if (DBG) log("addHidDevice device = " + device);

        if (propValues == null) {
            Log.e(TAG, "Error while getting HID properties for device: " + device);
            return false;
        }
        Integer state = null;
        String name = propValues[0];
        if (name.equals("Connected")) {
	    state =  BluetoothHid.STATE_DISCONNECTED;
	    if (propValues[1].equals("true"))
		state = BluetoothHid.STATE_CONNECTED;
	}
        mHidDevices.put(device, state);
        handleHIDStateChange(device, BluetoothHid.STATE_DISCONNECTED, state);
        return true;
    }


    private synchronized void onBluetoothEnable() {
	String devices = mBluetoothService.getProperty("Devices");

        if (devices != null) {
            String [] paths = devices.split(",");
            for (String path: paths) {
                String address = mBluetoothService.getAddressFromObjectPath(path);
                BluetoothDevice device = mAdapter.getRemoteDevice(address);
                ParcelUuid[] remoteUuids = mBluetoothService.getRemoteUuids(address);
                if (remoteUuids != null)
                    if (BluetoothUuid.containsAnyUuid(remoteUuids,
                            new ParcelUuid[] {BluetoothUuid.HID})) {
                        addHidDevice(device);
                    }
                }
	}
    }

    private synchronized void onBluetoothDisable() {
        if (!mHidDevices.isEmpty()) {
            BluetoothDevice[] devices = new BluetoothDevice[mHidDevices.size()];
            devices = mHidDevices.keySet().toArray(devices);

            for (BluetoothDevice device : devices) {
                int state = mHidDevices.get(device);
                switch (state) {
                    case BluetoothHid.STATE_CONNECTING:
                    case BluetoothHid.STATE_CONNECTED:
                        disconnectHidDeviceNative(mBluetoothService.getObjectPathFromAddress(
                                device.getAddress()));
                        handleHIDStateChange(device, state, BluetoothHid.STATE_DISCONNECTED);
                        break;
                    case BluetoothHid.STATE_DISCONNECTING:
                        handleHIDStateChange(device, BluetoothHid.STATE_DISCONNECTING,
                                              BluetoothHid.STATE_DISCONNECTED);
                        break;
                }
            }
            mHidDevices.clear();
        }

    }

    public synchronized boolean connectHidDevice(BluetoothDevice device)  {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("connectHidDevice(" + device + ")");

        // ignore if there are any active sinks
        if (lookupSinksMatchingStates(new int[] {
                BluetoothHid.STATE_CONNECTING,
                BluetoothHid.STATE_CONNECTED,
                BluetoothHid.STATE_DISCONNECTING}).size() != 0) {
            return false;
        }
        if (mHidDevices.get(device) == null && !addHidDevice(device))
            return false;

        int state = mHidDevices.get(device);

        switch (state) {
        case BluetoothHid.STATE_CONNECTED:
        case BluetoothHid.STATE_DISCONNECTING:
            return false;
        case BluetoothHid.STATE_CONNECTING:
            return true;
        }

        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        if (path == null)
            return false;

        // State is DISCONNECTED
        if (!connectHidDeviceNative(path)) {
            return false;
        }
        return true;
    }

    public synchronized boolean  disconnectHidDevice(BluetoothDevice device)  {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        if (DBG) log("disconnectHidDevice(" + device + ")");

        if (mHidDevices.get(device) == null)
            return false;

        int state = mHidDevices.get(device);
        switch (state) {
        case BluetoothHid.STATE_DISCONNECTED:
            return false;
        case BluetoothHid.STATE_DISCONNECTING:
            return true;
        }

        String path = mBluetoothService.getObjectPathFromAddress(device.getAddress());
        if (path == null) {
            return false;
        }

        if (!disconnectHidDeviceNative(path)) {
            return false;
        }
        return true;
    }

    public synchronized BluetoothDevice[] getConnectedSinks() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Set<BluetoothDevice> sinks = lookupSinksMatchingStates(
                new int[] {BluetoothHid.STATE_CONNECTED});
        return sinks.toArray(new BluetoothDevice[sinks.size()]);
    }

    public synchronized BluetoothDevice[] getNonDisconnectedSinks() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Set<BluetoothDevice> sinks = lookupSinksMatchingStates(
                new int[] {BluetoothHid.STATE_CONNECTED,
                           BluetoothHid.STATE_CONNECTING,
                           BluetoothHid.STATE_DISCONNECTING});
        return sinks.toArray(new BluetoothDevice[sinks.size()]);
    }

    public synchronized int getHidDeviceState(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Integer state = mHidDevices.get(device);
        if (state == null){
	    return BluetoothHid.STATE_DISCONNECTED;
	}
        return state;
    }

    public synchronized int getHidDevicePriority(BluetoothDevice device) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.getBluetoothHidDevicePriorityKey(device.getAddress()),
                BluetoothHid.PRIORITY_UNDEFINED);
    }


    public synchronized boolean setHidDevicePriority(BluetoothDevice device, int priority) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH_ADMIN permission");
        if (!BluetoothAdapter.checkBluetoothAddress(device.getAddress())) {
            return false;
        }
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.getBluetoothHidDevicePriorityKey(device.getAddress()), priority);

    }


    public synchronized void onPropertyChanged(String path, String []propValues) {
        if (!mBluetoothService.isEnabled()) {
            return;
        }

        String name = propValues[0];
        String address = mBluetoothService.getAddressFromObjectPath(path);

        if (address == null) {
            Log.e(TAG, "onPropertyChanged: Address of the remote device in null");
            return;
        }

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        if (DBG) log("HID Device property changed: " + address + " property: " + name);

        if (name.equals("Connected")) {
	    int state =  BluetoothHid.STATE_DISCONNECTED;
	    if (propValues[1].equals("true")) {
		state = BluetoothHid.STATE_CONNECTED;
	    }
            if (mHidDevices.get(device) == null) {
                // This is for an incoming connection for a device not known to us.
                // We have authorized it and bluez state has changed.
                addHidDevice(device);
            } else {
                int prevState = mHidDevices.get(device);
                handleHIDStateChange(device, prevState, state);
            }
        }

    }

    private void handleHIDStateChange(BluetoothDevice device, int prevState, int state) {

	if(DBG) log("HID device: " + device + " State:" + prevState + "->" + state);

	if (state != prevState) {
	    mHidDevices.put(device, state);

	    if (getHidDevicePriority(device) > BluetoothHid.PRIORITY_OFF &&
                    state == BluetoothHid.STATE_CONNECTING ||
                    state == BluetoothHid.STATE_CONNECTED) {
                setHidDevicePriority(device, BluetoothHid.PRIORITY_AUTO_CONNECT);
            }
            Intent intent = new Intent(BluetoothHid.HID_DEVICE_STATE_CHANGED_ACTION);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
            intent.putExtra(BluetoothHid.HID_DEVICE_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothHid.HID_DEVICE_STATE, state);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    private synchronized Set<BluetoothDevice> lookupSinksMatchingStates(int[] states) {
        Set<BluetoothDevice> sinks = new HashSet<BluetoothDevice>();
        if (mHidDevices.isEmpty()) {
            return sinks;
        }
        for (BluetoothDevice device: mHidDevices.keySet()) {
            int sinkState = getHidDeviceState(device);
            for (int state : states) {
                if (state == sinkState) {
                    sinks.add(device);
                    break;
                }
            }
        }
        return sinks;
    }

    public synchronized void onHidDeviceConnected(String path) {
        if (mHidDevices == null) return;

        if (!mBluetoothService.isEnabled()) {
            return;
        }

        String address = mBluetoothService.getAddressFromObjectPath(path);
        if (address == null) {
            Log.e(TAG, "onHidDeviceConnected: Address of the remote device in null");
            return;
        }

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        if(DBG) log(" onHidDeviceConnected device = " + device);
	if (mHidDevices.containsKey(device)) {
	    int prevState = mHidDevices.get(device);
	    handleHIDStateChange(device, prevState, BluetoothHid.STATE_CONNECTED);
	}

    }

    public synchronized void onHidDeviceDisconnected(String path) {
        if (mHidDevices == null) return;

        if (!mBluetoothService.isEnabled()) {
            return;
        }

        String address = mBluetoothService.getAddressFromObjectPath(path);
        if (address == null) {
            Log.e(TAG, "onHidDeviceDisconnected: Address of the remote device in null");
            return;
        }

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        if(DBG) log(" onHidDeviceDisconnected device = " + device);
        if (mHidDevices.containsKey(device)) {
	    int prevState = mHidDevices.get(device);
	    handleHIDStateChange(device, prevState, BluetoothHid.STATE_DISCONNECTED);
	}
    }

    @Override
    protected synchronized void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mHidDevices.isEmpty()) return;
        pw.println("Cached hid devices:");
        for (BluetoothDevice device : mHidDevices.keySet()) {
            int state = mHidDevices.get(device);
            pw.println(device + " " + BluetoothHid.stateToString(state));
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    private native boolean initNative();
    private native void cleanupNative();

    private synchronized native boolean connectHidDeviceNative(String path);
    private synchronized native boolean disconnectHidDeviceNative(String path);
    private synchronized native Object []getHidPropertiesNative(String path);

}
