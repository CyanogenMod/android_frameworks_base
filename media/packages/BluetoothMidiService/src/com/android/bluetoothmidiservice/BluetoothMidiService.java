/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.bluetoothmidiservice;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.midi.IBluetoothMidiService;
import android.media.midi.MidiManager;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;

public class BluetoothMidiService extends Service {
    private static final String TAG = "BluetoothMidiService";

    // BluetoothMidiDevices keyed by BluetoothDevice
    private final HashMap<BluetoothDevice,BluetoothMidiDevice> mDeviceServerMap
            = new HashMap<BluetoothDevice,BluetoothMidiDevice>();

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }


    private final IBluetoothMidiService.Stub mBinder = new IBluetoothMidiService.Stub() {

        public IBinder addBluetoothDevice(BluetoothDevice bluetoothDevice) {
            BluetoothMidiDevice device;
            if (bluetoothDevice == null) {
                Log.e(TAG, "no BluetoothDevice in addBluetoothDevice()");
                return null;
            }
            synchronized (mDeviceServerMap) {
                device = mDeviceServerMap.get(bluetoothDevice);
                if (device == null) {
                    device = new BluetoothMidiDevice(BluetoothMidiService.this,
                            bluetoothDevice, BluetoothMidiService.this);
                    mDeviceServerMap.put(bluetoothDevice, device);
                }
            }
            return device.getBinder();
        }

    };

    void deviceClosed(BluetoothDevice device) {
        synchronized (mDeviceServerMap) {
            mDeviceServerMap.remove(device);
        }
    }
}
