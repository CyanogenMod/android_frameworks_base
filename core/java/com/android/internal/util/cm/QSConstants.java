package com.android.internal.util.cm;

import java.util.ArrayList;

public class QSConstants {
        public static final String TILE_USER = "toggleUser";
        public static final String TILE_BATTERY = "toggleBattery";
        public static final String TILE_SETTINGS = "toggleSettings";
        public static final String TILE_WIFI = "toggleWifi";
        public static final String TILE_GPS = "toggleGPS";
        public static final String TILE_BLUETOOTH = "toggleBluetooth";
        public static final String TILE_BRIGHTNESS = "toggleBrightness";
        public static final String TILE_RINGER = "toggleSound";
        public static final String TILE_SYNC = "toggleSync";
        public static final String TILE_WIFIAP = "toggleWifiAp";
        public static final String TILE_SCREENTIMEOUT = "toggleScreenTimeout";
        public static final String TILE_MOBILEDATA = "toggleMobileData";
        public static final String TILE_LOCKSCREEN = "toggleLockScreen";
        public static final String TILE_NETWORKMODE = "toggleNetworkMode";
        public static final String TILE_AUTOROTATE = "toggleAutoRotate";
        public static final String TILE_AIRPLANE = "toggleAirplane";
        public static final String TILE_TORCH = "toggleFlashlight";  // Keep old string for compatibility
        public static final String TILE_SLEEP = "toggleSleepMode";
        public static final String TILE_LTE = "toggleLte";
        public static final String TILE_WIMAX = "toggleWimax";
        public static final String TILE_PROFILE = "toggleProfile";
        public static final String TILE_PERFORMANCE_PROFILE = "togglePerformanceProfile";
        public static final String TILE_NFC = "toggleNfc";
        public static final String TILE_USBTETHER = "toggleUsbTether";
        public static final String TILE_QUIETHOURS = "toggleQuietHours";
        public static final String TILE_VOLUME = "toggleVolume";
        public static final String TILE_EXPANDEDDESKTOP = "toggleExpandedDesktop";
        public static final String TILE_CAMERA = "toggleCamera";
        public static final String TILE_NETWORKADB = "toggleNetworkAdb";
        public static final String TILE_COMPASS = "toggleCompass";
        public static final String TILE_HEADS_UP = "toggleHeadsUp";

        public static final String TILE_DELIMITER = "|";
        public static ArrayList<String> TILES_DEFAULT = new ArrayList<String>();

        static {
            TILES_DEFAULT.add(TILE_USER);
            TILES_DEFAULT.add(TILE_BRIGHTNESS);
            TILES_DEFAULT.add(TILE_SETTINGS);
            TILES_DEFAULT.add(TILE_WIFI);
            TILES_DEFAULT.add(TILE_MOBILEDATA);
            TILES_DEFAULT.add(TILE_GPS);
            TILES_DEFAULT.add(TILE_TORCH);
            TILES_DEFAULT.add(TILE_BATTERY);
            TILES_DEFAULT.add(TILE_AIRPLANE);
            TILES_DEFAULT.add(TILE_BLUETOOTH);
            TILES_DEFAULT.add(TILE_AUTOROTATE);
        }
}
