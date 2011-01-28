/*
 * Copyright (C) 2009 ISB Corporation
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
 * System private API for Bluetooth HID service
 *
 * {@hide}
 */
interface IBluetoothHid {
	boolean connectHidDevice(in BluetoothDevice device);
	boolean disconnectHidDevice(in BluetoothDevice device);
	BluetoothDevice[] getConnectedSinks();  // change to Set<> once AIDL supports
	BluetoothDevice[] getNonDisconnectedSinks();  // change to Set<> once AIDL supports
	int getHidDeviceState(in BluetoothDevice device);
	boolean setHidDevicePriority(in BluetoothDevice device, int priority);
	int getHidDevicePriority(in BluetoothDevice device);
}
