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

package android.bluetooth;

import android.server.BluetoothNetworkService;
import android.content.Context;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.os.IBinder;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

/**
 * Public API for controlling the Bluetooth Network Profile Service.
 *
 * BluetoothNetwork is a proxy object for controlling the Bluetooth Network
 * Service via IPC.
 *
 * @hide
 */
public final class BluetoothNetwork {
    private static final String TAG = "BluetoothNetwork";

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING   = 1;
    public static final int STATE_CONNECTED    = 2;
    public static final int STATE_DISCONNECTING = 3;
        
    private final IBluetoothNetwork mService;

    /**
     * Create a BluetoothA2dp proxy object for interacting with the local
     * Bluetooth Network service.
     * @param c Context
     */
    public BluetoothNetwork(Context c) {
        IBinder b = ServiceManager.getService(BluetoothNetworkService.BLUETOOTH_NETWORK_SERVICE);
        if (b != null) {
            mService = IBluetoothNetwork.Stub.asInterface(b);
        } else {
            Log.w(TAG, "Bluetooth Network service not available!");
            
            // Instead of throwing an exception which prevents people from going
            // into Wireless settings in the emulator. Let it crash later when it is actually used.
            mService = null;
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
}
