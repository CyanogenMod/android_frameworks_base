/*
 * Copyright (C) 2015 The CyanogenMod Open Source Project
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
 * limitations under the License
 */

package com.android.internal.util.cm;

import java.util.ArrayList;

public class QSConstants {
    private QSConstants() {}

    public static final String TILE_WIFI = "wifi";
    public static final String TILE_BLUETOOTH = "bt";
    public static final String TILE_INVERSION = "inversion";
    public static final String TILE_CELLULAR = "cell";
    public static final String TILE_AIRPLANE = "airplane";
    public static final String TILE_ROTATION = "rotation";
    public static final String TILE_FLASHLIGHT = "flashlight";
    public static final String TILE_LOCATION = "location";
    public static final String TILE_CAST = "cast";
    public static final String TILE_HOTSPOT = "hotspot";
    public static final String TILE_NOTIFICATIONS = "notifications";
    public static final String TILE_DATA = "data";
    public static final String TILE_ROAMING = "roaming";
    public static final String TILE_DDS = "dds";
    public static final String TILE_APN = "apn";
    public static final String TILE_PROFILES = "profiles";
    public static final String TILE_PERFORMANCE = "performance";
    public static final String TILE_ADB_NETWORK = "adb_network";
    public static final String TILE_NFC = "nfc";
    public static final String TILE_COMPASS = "compass";
    public static final String TILE_LOCKSCREEN = "lockscreen";
    public static final String TILE_LTE = "lte";
    public static final String TILE_VISUALIZER = "visualizer";
    public static final String TILE_SCREEN_TIMEOUT = "screen_timeout";
    public static final String TILE_LIVE_DISPLAY = "live_display";
    public static final String TILE_USB_TETHER = "usb_tether";

    // Order matters
    protected static final ArrayList<String> TILES_DEFAULT = new ArrayList<String>();

    static {
        TILES_DEFAULT.add(TILE_WIFI);
        TILES_DEFAULT.add(TILE_BLUETOOTH);
        TILES_DEFAULT.add(TILE_CELLULAR);
        TILES_DEFAULT.add(TILE_AIRPLANE);
        TILES_DEFAULT.add(TILE_ROTATION);
        TILES_DEFAULT.add(TILE_FLASHLIGHT);
        TILES_DEFAULT.add(TILE_LOCATION);
        TILES_DEFAULT.add(TILE_CAST);
    }

    protected static final ArrayList<String> TILES_AVAILABLE = new ArrayList<String>();

    static {
        TILES_AVAILABLE.addAll(TILES_DEFAULT);
        TILES_AVAILABLE.add(TILE_INVERSION);
        TILES_AVAILABLE.add(TILE_HOTSPOT);
        TILES_AVAILABLE.add(TILE_NOTIFICATIONS);
        TILES_AVAILABLE.add(TILE_DATA);
        TILES_AVAILABLE.add(TILE_ROAMING);
        TILES_AVAILABLE.add(TILE_DDS);
        TILES_AVAILABLE.add(TILE_APN);
        TILES_AVAILABLE.add(TILE_PROFILES);
        TILES_AVAILABLE.add(TILE_PERFORMANCE);
        TILES_AVAILABLE.add(TILE_ADB_NETWORK);
        TILES_AVAILABLE.add(TILE_NFC);
        TILES_AVAILABLE.add(TILE_COMPASS);
        TILES_AVAILABLE.add(TILE_LOCKSCREEN);
        TILES_AVAILABLE.add(TILE_LTE);
        TILES_AVAILABLE.add(TILE_VISUALIZER);
        TILES_AVAILABLE.add(TILE_SCREEN_TIMEOUT);
        TILES_AVAILABLE.add(TILE_LIVE_DISPLAY);
        TILES_AVAILABLE.add(TILE_USB_TETHER);
    }
}
