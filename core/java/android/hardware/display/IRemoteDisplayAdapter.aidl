/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package android.hardware.display;

import android.hardware.display.WifiDisplayStatus;
import android.hardware.display.IDisplayDevice;

interface IRemoteDisplayAdapter {
    // No permissions required.
    void scanRemoteDisplays();

    // Requires CONFIGURE_WIFI_DISPLAY permission to connect to an unknown device.
    // No permissions required to connect to a known device.
    void connectRemoteDisplay(String address);

    // No permissions required.
    void disconnectRemoteDisplay();

    // Requires CONFIGURE_WIFI_DISPLAY permission.
    void forgetRemoteDisplay(String address);

    // Requires CONFIGURE_WIFI_DISPLAY permission.
    void renameRemoteDisplay(String address, String alias);

    // No permissions required.
    /**
    /* @hide
    */
    WifiDisplayStatus getRemoteDisplayStatus();

    oneway void registerDisplayDevice(
                IDisplayDevice device,
                String name,
                int width,
                int height,
                float refreshRate,
                int flags,
                String address);

    oneway void unregisterDisplayDevice(IDisplayDevice device);
}
