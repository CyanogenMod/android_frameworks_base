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

import android.bluetooth.BluetoothDevice;

/**
 * API for Bluetooth Headset service
 *
 * {@hide}
 */
interface IBluetoothHeadset {
    // Public API
    boolean connect(in BluetoothDevice device);
    boolean disconnect(in BluetoothDevice device);
    List<BluetoothDevice> getConnectedDevices();
    List<BluetoothDevice> getDevicesMatchingConnectionStates(in int[] states);
    int getConnectionState(in BluetoothDevice device);
    boolean setPriority(in BluetoothDevice device, int priority);
    int getPriority(in BluetoothDevice device);
    boolean startVoiceRecognition(in BluetoothDevice device);
    boolean stopVoiceRecognition(in BluetoothDevice device);
    boolean isAudioConnected(in BluetoothDevice device);
    boolean sendVendorSpecificResultCode(in BluetoothDevice device,
                                         in String command,
                                         in String arg);

    // APIs that can be made public in future
    int getBatteryUsageHint(in BluetoothDevice device);

    // Internal functions, not be made public
    boolean acceptIncomingConnect(in BluetoothDevice device);
    boolean rejectIncomingConnect(in BluetoothDevice device);
    int getAudioState(in BluetoothDevice device);

    boolean isAudioOn();
    boolean connectAudio();
    boolean disconnectAudio();
    void setAudioRouteAllowed(boolean allowed);
    boolean getAudioRouteAllowed();
    boolean startScoUsingVirtualVoiceCall(in BluetoothDevice device);
    boolean stopScoUsingVirtualVoiceCall(in BluetoothDevice device);
    void phoneStateChanged(int numActive, int numHeld, int callState, String number, int type);
    void clccResponse(int index, int direction, int status, int mode, boolean mpty,
                      String number, int type);
    boolean enableWBS();
    boolean disableWBS();
}
