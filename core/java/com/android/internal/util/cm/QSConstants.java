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
    public static final String TILE_VOLUME = "volume_panel";
    public static final String TILE_SCREEN_TIMEOUT = "screen_timeout";
    public static final String TILE_LIVE_DISPLAY = "live_display";
    public static final String TILE_USB_TETHER = "usb_tether";
    public static final String TILE_HEADS_UP = "heads_up";
    public static final String TILE_AMBIENT_DISPLAY = "ambient_display";
    public static final String TILE_SYNC = "sync";
    public static final String TILE_BATTERY_SAVER = "battery_saver";

    public static final String DYNAMIC_TILE_NEXT_ALARM = "next_alarm";
    public static final String DYNAMIC_TILE_IME_SELECTOR = "ime_selector";
    public static final String DYNAMIC_TILE_SU = "su";
    public static final String DYNAMIC_TILE_ADB = "adb";

    protected static final ArrayList<String> STATIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> DYNAMIC_TILES_AVAILABLE = new ArrayList<String>();
    protected static final ArrayList<String> TILES_AVAILABLE = new ArrayList<String>();

    static {
        STATIC_TILES_AVAILABLE.add(TILE_WIFI);
        STATIC_TILES_AVAILABLE.add(TILE_BLUETOOTH);
        STATIC_TILES_AVAILABLE.add(TILE_CELLULAR);
        STATIC_TILES_AVAILABLE.add(TILE_AIRPLANE);
        STATIC_TILES_AVAILABLE.add(TILE_ROTATION);
        STATIC_TILES_AVAILABLE.add(TILE_FLASHLIGHT);
        STATIC_TILES_AVAILABLE.add(TILE_LOCATION);
        STATIC_TILES_AVAILABLE.add(TILE_CAST);
        STATIC_TILES_AVAILABLE.add(TILE_INVERSION);
        STATIC_TILES_AVAILABLE.add(TILE_HOTSPOT);
        STATIC_TILES_AVAILABLE.add(TILE_NOTIFICATIONS);
        STATIC_TILES_AVAILABLE.add(TILE_DATA);
        STATIC_TILES_AVAILABLE.add(TILE_ROAMING);
        STATIC_TILES_AVAILABLE.add(TILE_DDS);
        STATIC_TILES_AVAILABLE.add(TILE_APN);
        STATIC_TILES_AVAILABLE.add(TILE_PROFILES);
        STATIC_TILES_AVAILABLE.add(TILE_PERFORMANCE);
        STATIC_TILES_AVAILABLE.add(TILE_ADB_NETWORK);
        STATIC_TILES_AVAILABLE.add(TILE_NFC);
        STATIC_TILES_AVAILABLE.add(TILE_COMPASS);
        STATIC_TILES_AVAILABLE.add(TILE_LOCKSCREEN);
        STATIC_TILES_AVAILABLE.add(TILE_LTE);
        STATIC_TILES_AVAILABLE.add(TILE_VISUALIZER);
        STATIC_TILES_AVAILABLE.add(TILE_VOLUME);
        STATIC_TILES_AVAILABLE.add(TILE_SCREEN_TIMEOUT);
        STATIC_TILES_AVAILABLE.add(TILE_LIVE_DISPLAY);
        STATIC_TILES_AVAILABLE.add(TILE_USB_TETHER);
        STATIC_TILES_AVAILABLE.add(TILE_HEADS_UP);
        STATIC_TILES_AVAILABLE.add(TILE_AMBIENT_DISPLAY);
        STATIC_TILES_AVAILABLE.add(TILE_SYNC);
        STATIC_TILES_AVAILABLE.add(TILE_BATTERY_SAVER);

        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_NEXT_ALARM);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_IME_SELECTOR);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_SU);
        DYNAMIC_TILES_AVAILABLE.add(DYNAMIC_TILE_ADB);

        TILES_AVAILABLE.addAll(STATIC_TILES_AVAILABLE);
        TILES_AVAILABLE.addAll(DYNAMIC_TILES_AVAILABLE);
    }
}
