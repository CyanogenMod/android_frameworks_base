package com.android.internal.util;

import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;

public class CmUtils {
    public static class QuickSettings {
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
        public static final String TILE_NFC = "toggleNfc";
        public static final String TILE_USBTETHER = "toggleUsbTether";

        public static final String TILE_DELIMITER = "|";
        public static ArrayList<String> TILES_DEFAULT = new ArrayList<String>();

        static {
            TILES_DEFAULT.add(TILE_USER);
            TILES_DEFAULT.add(TILE_BRIGHTNESS);
            TILES_DEFAULT.add(TILE_SETTINGS);
            TILES_DEFAULT.add(TILE_WIFI);
            TILES_DEFAULT.add(TILE_MOBILEDATA);
            TILES_DEFAULT.add(TILE_BATTERY);
            TILES_DEFAULT.add(TILE_AIRPLANE);
            TILES_DEFAULT.add(TILE_BLUETOOTH);
        }
        
        public static boolean deviceSupportsUsbTether(Context ctx) {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            return (cm.getTetherableUsbRegexs().length != 0);
        }

        public static boolean deviceSupportsWifiDisplay(Context ctx) {
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            return (dm.getWifiDisplayStatus().getFeatureState() != WifiDisplayStatus.FEATURE_STATE_UNAVAILABLE);
        }

        public static boolean deviceSupportsTelephony(Context ctx) {
            PackageManager pm = ctx.getPackageManager();
            return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        }

        public static boolean deviceSupportsBluetooth() {
            return (BluetoothAdapter.getDefaultAdapter() != null);
        }

        public static boolean systemProfilesEnabled(ContentResolver resolver) {
            return (Settings.System.getInt(resolver, Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1);
        }
        
        public static boolean deviceSupportsNfc(Context ctx) {
            return NfcAdapter.getDefaultAdapter(ctx) != null;
        }
    }
}
