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

package android.bluetooth;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.server.BluetoothHidService;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
/**
 * Public API for controlling the Bluetooth HID Profile Service.
 *
 * BluetoothHid is a proxy object for controlling the Bluetooth HID
 * Service via IPC.
 *
 *
 * Currently the BluetoothHid service runs in the system server and this
 * proxy object will be immediately bound to the service on construction.
 * However this may change in future releases, and error codes such as
 * BluetoothError.ERROR_IPC_NOT_READY will be returned from this API when the
 * proxy object is not yet attached.
 *
 * Currently this class provides methods to connect to HID devices.
 *
 * @hide
 */
public class BluetoothHid {
    private static final String TAG = "BluetoothHid";
    private static final boolean DBG = false;

    /** int extra for DEVICE_STATE_CHANGED_ACTION */
    public static final String HID_DEVICE_STATE =
        "android.bluetooth.hid.intent.HID_DEVICE_STATE";
    /** int extra for DEVICE_STATE_CHANGED_ACTION */
    public static final String HID_DEVICE_PREVIOUS_STATE =
        "android.bluetooth.hid.intent.HID_DEVICE_PREVIOUS_STATE";

    /** Indicates the state of an HID device has changed.
     *  This intent will always contain HID_DEVICE_STATE, and
     *  BluetoothIntent.ADDRESS extras.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String HID_DEVICE_STATE_CHANGED_ACTION =
        "android.bluetooth.hid.intent.action.HID_DEVICE_STATE_CHANGED";

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING   = 1;
    public static final int STATE_CONNECTED    = 2;
    public static final int STATE_DISCONNECTING = 3;

    /** Default priority for hid devices that we try to auto-connect
     * and allow incoming connections */
    public static final int PRIORITY_AUTO_CONNECT = 1000;
    /** Default priority for hid devices that should allow incoming
     * connections */
    public static final int PRIORITY_ON = 100;
    /** Default priority for hid devices that should not allow incoming
     * connections */
    public static final int PRIORITY_OFF = 0;
    /** Default priority when not set or when the device is unpaired */
    public static final int PRIORITY_UNDEFINED = -1;

    private final IBluetoothHid mService;
    private final Context mContext;


    /**
     * Create a BluetoothHid proxy object.
     */
    public BluetoothHid(Context c) {
        mContext = c;

        IBinder b = ServiceManager.getService(BluetoothHidService.BLUETOOTH_HID_SERVICE);
        if (b != null) {
            mService = IBluetoothHid.Stub.asInterface(b);
        } else {
            Log.w(TAG, "Bluetooth HID service not available!");
            // Instead of throwing an exception which prevents people from going
            // into Wireless settings in the emulator. Let it crash later when it is actually used.
            mService = null;
        }
    }


    /** Initiate a connection to an HID device.
     *  Listen for HID_DEVICE_STATE_CHANGED_ACTION to find out when the
     *  connection is completed.
     *  @param address Remote BT address.
     *  @return Result code, negative indicates an immediate error.
     *  @hide
     */
    public boolean connectHidDevice(BluetoothDevice device) {
        if (DBG) log("connectHidDevice(" + device + ")");
        try {
            return mService.connectHidDevice(device);
        } catch (RemoteException e) {
            Log.w(TAG, "", e);
            return false;
        }
    }

    /** Initiate disconnect from an HID device.
     *  Listen for HID_DEVICE_STATE_CHANGED_ACTION to find out when
     *  disconnect is completed.
     *  @param address Remote BT address.
     *  @return Result code, negative indicates an immediate error.
     *  @hide
     */
    public boolean disconnectHidDevice(BluetoothDevice device) {
        try {
            return mService.disconnectHidDevice(device);
        } catch (RemoteException e) {
            Log.w(TAG, "", e);
            return false;
        }
    }

    /** Check if a specified HID device is connected.
     *  @param address Remote BT address.
     *  @return True if connected (or playing), false otherwise and on error.
     *  @hide
     */
    public boolean isHidDeviceConnected(BluetoothDevice device) {
        int state = getHidDeviceState(device);
        return state == STATE_CONNECTED;
    }

    /** Check if any HID device is connected.
     * @return a unmodifiable set of connected HID devices, or null on error.
     * @hide
     */
    public  Set<BluetoothDevice> getConnectedSinks() {
        if (DBG) log("getConnectedSinks()");
        try {
            return Collections.unmodifiableSet(
                    new HashSet<BluetoothDevice>(Arrays.asList(mService.getConnectedSinks())));
        } catch (RemoteException e) {
            Log.w(TAG, "", e);
            return null;
        }
    }

    /** Check if any HID device is in Non Disconnected state
     * i.e connected, connecting, disconnecting.
     * @return a unmodifiable set of connected HID devices, or null on error.
     * @hide
     */
    public Set<BluetoothDevice> getNonDisconnectedSinks() {
        if (DBG) log("getNonDisconnectedSinks()");
        try {
            return Collections.unmodifiableSet(
                    new HashSet<BluetoothDevice>(Arrays.asList(mService.getNonDisconnectedSinks())));
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
            return null;
        }
    }

    /** Get the state of an HID device
     *  @param address Remote BT address.
     *  @return State code, or negative on error
     *  @hide
     */
    public int getHidDeviceState(BluetoothDevice device) {
        if (DBG) log("getHidDeviceState(" + device + ")");
        try {
            return mService.getHidDeviceState(device);
        } catch (RemoteException e) {
            Log.w(TAG, "", e);
            return BluetoothHid.STATE_DISCONNECTED;
        }
    }


    /**
     * Set priority of HID device.
     * Priority is a non-negative integer. By default paired devices will have
     * a priority of PRIORITY_AUTO, and unpaired device PRIORITY_NONE (0).
     * Sinks with priority greater than zero will accept incoming connections
     * (if no sink is currently connected).
     * Priority for unpaired sink must be PRIORITY_NONE.
     * @param device Paired sink
     * @param priority Integer priority, for example PRIORITY_AUTO or
     *                 PRIORITY_NONE
     * @return true if priority is set, false on error
     */
    public boolean setHidDevicePriority(BluetoothDevice device, int priority) {
        if (DBG) log("setHidDevicePriority(" + device + ", " + priority + ")");
        try {
            return mService.setHidDevicePriority(device, priority);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
            return false;
        }
    }

    /**
     * Get priority of HID device.
     * @param device Sink
     * @return non-negative priority, or negative error code on error.
     */
    public int getHidDevicePriority(BluetoothDevice device) {
        if (DBG) log("getHidDevicePriority(" + device + ")");
        try {
            return mService.getHidDevicePriority(device);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
            return PRIORITY_OFF;
        }
    }

    /** Helper for converting a state to a string.
     * For debug use only - strings are not internationalized.
     * @hide
     */
    public static String stateToString(int state) {
        switch (state) {
        case STATE_DISCONNECTED:
            return "disconnected";
        case STATE_CONNECTING:
            return "connecting";
        case STATE_CONNECTED:
            return "connected";
        case STATE_DISCONNECTING:
            return "disconnecting";
        default:
            return "<unknown state " + state + ">";
        }
    }


    /**
     * Check class bits for possible HID device support.
     * This is a simple heuristic that tries to guess if a device with the
     * given class bits might be a HID device. It is not accurate for all
     * devices. It tries to err on the side of false positives.
     * @return True if this device might be a HID device
     */
    public static boolean doesClassMatch(BluetoothClass btClass) {
       switch (btClass.getDeviceClass()) {
        case BluetoothClass.Device.PERIPHERAL_KEYBORD:
        case BluetoothClass.Device.PERIPHERAL_POINTING_DEVICE:
        case BluetoothClass.Device.PERIPHERAL_COMBO_KEYBORD_POINTING:
	    return true;
        default:
            return false;
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
