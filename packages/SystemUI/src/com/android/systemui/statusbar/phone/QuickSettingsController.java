/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.systemui.quicksettings.AirplaneModeTile;
import com.android.systemui.quicksettings.AlarmTile;
import com.android.systemui.quicksettings.AutoRotateTile;
import com.android.systemui.quicksettings.BatteryTile;
import com.android.systemui.quicksettings.BluetoothTile;
import com.android.systemui.quicksettings.BrightnessTile;
import com.android.systemui.quicksettings.BugReportTile;
import com.android.systemui.quicksettings.FlashLightTile;
import com.android.systemui.quicksettings.GPSTile;
import com.android.systemui.quicksettings.InputMethodTile;
import com.android.systemui.quicksettings.MobileNetworkTile;
import com.android.systemui.quicksettings.MobileNetworkTypeTile;
import com.android.systemui.quicksettings.PreferencesTile;
import com.android.systemui.quicksettings.ProfileTile;
import com.android.systemui.quicksettings.QuickSettingsTile;
import com.android.systemui.quicksettings.RingerModeTile;
import com.android.systemui.quicksettings.RingerVibrationModeTile;
import com.android.systemui.quicksettings.SleepScreenTile;
import com.android.systemui.quicksettings.ToggleLockscreenTile;
import com.android.systemui.quicksettings.UserTile;
import com.android.systemui.quicksettings.VibrationModeTile;
import com.android.systemui.quicksettings.WiFiDisplayTile;
import com.android.systemui.quicksettings.WiFiTile;
import com.android.systemui.quicksettings.WifiAPTile;

public class QuickSettingsController {
    private static String TAG = "QuickSettingsController";

    /**
     * START OF DATA MATCHING BLOCK
     *
     * THE FOLLOWING DATA MUST BE KEPT UP-TO-DATE WITH THE DATA IN
     * com.android.settings.cyanogenmod.QuickSettingsUtil IN THE
     * Settings PACKAGE.
     */
    public static final String TILE_USER = "toggleUser";
    public static final String TILE_BATTERY = "toggleBattery";
    public static final String TILE_SETTINGS = "toggleSettings";
    public static final String TILE_WIFI = "toggleWifi";
    public static final String TILE_GPS = "toggleGPS";
    public static final String TILE_BLUETOOTH = "toggleBluetooth";
    public static final String TILE_BRIGHTNESS = "toggleBrightness";
    public static final String TILE_SOUND = "toggleSound";
    public static final String TILE_SYNC = "toggleSync";
    public static final String TILE_WIFIAP = "toggleWifiAp";
    public static final String TILE_SCREENTIMEOUT = "toggleScreenTimeout";
    public static final String TILE_MOBILEDATA = "toggleMobileData";
    public static final String TILE_LOCKSCREEN = "toggleLockScreen";
    public static final String TILE_NETWORKMODE = "toggleNetworkMode";
    public static final String TILE_AUTOROTATE = "toggleAutoRotate";
    public static final String TILE_AIRPLANE = "toggleAirplane";
    public static final String TILE_FLASHLIGHT = "toggleFlashlight";
    public static final String TILE_SLEEP = "toggleSleepMode";
    public static final String TILE_LTE = "toggleLte";
    public static final String TILE_WIMAX = "toggleWimax";
    public static final String TILE_PROFILE = "toggleProfile";

    private static final String TILE_DELIMITER = "|";
    private static final String TILES_DEFAULT = TILE_USER
            + TILE_DELIMITER + TILE_BRIGHTNESS
            + TILE_DELIMITER + TILE_SETTINGS
            + TILE_DELIMITER + TILE_WIFI
            + TILE_DELIMITER + TILE_MOBILEDATA
            + TILE_DELIMITER + TILE_BATTERY
            + TILE_DELIMITER + TILE_AIRPLANE
            + TILE_DELIMITER + TILE_BLUETOOTH;
    /**
     * END OF DATA MATCHING BLOCK
     */

    private final Context mContext;
    public PanelBar mBar;
    private final ViewGroup mContainerView;
    private final Handler mHandler;
    private final ArrayList<Integer> mQuickSettings;
    public PhoneStatusBar mStatusBarService;

    // Constants for use in switch statement
    public static final int WIFI_TILE = 0;
    public static final int MOBILE_NETWORK_TILE = 1;
    public static final int AIRPLANE_MODE_TILE = 2;
    public static final int BLUETOOTH_TILE = 3;
    public static final int SOUND_TILE = 4;
    public static final int VIBRATION_TILE = 5;
    public static final int SOUND_VIBRATION_TILE = 6;
    public static final int SLEEP_TILE = 7;
    public static final int TOGGLE_LOCKSCREEN_TILE = 8;
    public static final int GPS_TILE = 9;
    public static final int AUTO_ROTATION_TILE = 10;
    public static final int BRIGHTNESS_TILE = 11;
    public static final int MOBILE_NETWORK_TYPE_TILE = 12;
    public static final int SETTINGS_TILE = 13;
    public static final int BATTERY_TILE = 14;
    public static final int IME_TILE = 15;
    public static final int ALARM_TILE = 16;
    public static final int BUG_REPORT_TILE = 17;
    public static final int WIFI_DISPLAY_TILE = 18;
    public static final int FLASHLIGHT_TILE = 19;
    public static final int WIFIAP_TILE = 20;
    public static final int PROFILE_TILE = 21;
    public static final int USER_TILE = 99;
    private InputMethodTile IMETile;

    public QuickSettingsController(Context context, QuickSettingsContainerView container, PhoneStatusBar statusBarService) {
        mContext = context;
        mContainerView = container;
        mHandler = new Handler();
        mStatusBarService = statusBarService;
        mQuickSettings = new ArrayList<Integer>();
    }

    void loadTiles() {
        // Read the stored list of tiles
        ContentResolver resolver = mContext.getContentResolver();
        String tiles = Settings.System.getString(resolver, Settings.System.QUICK_SETTINGS_TILES);
        if (tiles == null) {
            Log.i(TAG, "Default tiles being loaded");
            tiles = TILES_DEFAULT;
        }

        Log.i(TAG, "Tiles list: " + tiles);

        // Clear the list
        mQuickSettings.clear();

        // Split out the tile names and add to the list
        for (String tile : tiles.split("\\|")) {
            if (tile.equals(TILE_USER)) {
                mQuickSettings.add(USER_TILE);
            } else if (tile.equals(TILE_BATTERY)) {
                mQuickSettings.add(BATTERY_TILE);
            } else if (tile.equals(TILE_SETTINGS)) {
                mQuickSettings.add(SETTINGS_TILE);
            } else if (tile.equals(TILE_WIFI)) {
                mQuickSettings.add(WIFI_TILE);
            } else if (tile.equals(TILE_GPS)) {
                mQuickSettings.add(GPS_TILE);
            } else if (tile.equals(TILE_BLUETOOTH)) {
                if(deviceSupportsBluetooth()) {
                    mQuickSettings.add(BLUETOOTH_TILE);
                }
            } else if (tile.equals(TILE_BRIGHTNESS)) {
                mQuickSettings.add(BRIGHTNESS_TILE);
            } else if (tile.equals(TILE_SOUND)) {
                mQuickSettings.add(SOUND_VIBRATION_TILE);
            } else if (tile.equals(TILE_SYNC)) {
                // Not available yet
            } else if (tile.equals(TILE_WIFIAP)) {
                if(deviceSupportsTelephony()) {
                    mQuickSettings.add(WIFIAP_TILE);
                }
            } else if (tile.equals(TILE_SCREENTIMEOUT)) {
                // Not available yet
            } else if (tile.equals(TILE_MOBILEDATA)) {
                if(deviceSupportsTelephony()) {
                    mQuickSettings.add(MOBILE_NETWORK_TILE);
                }
            } else if (tile.equals(TILE_LOCKSCREEN)) {
                mQuickSettings.add(TOGGLE_LOCKSCREEN_TILE);
            } else if (tile.equals(TILE_NETWORKMODE)) {
                if(deviceSupportsTelephony()) {
                    mQuickSettings.add(MOBILE_NETWORK_TYPE_TILE);
                }
            } else if (tile.equals(TILE_AUTOROTATE)) {
                mQuickSettings.add(AUTO_ROTATION_TILE);
            } else if (tile.equals(TILE_AIRPLANE)) {
                mQuickSettings.add(AIRPLANE_MODE_TILE);
            } else if (tile.equals(TILE_FLASHLIGHT)) {
                mQuickSettings.add(FLASHLIGHT_TILE);
            } else if (tile.equals(TILE_SLEEP)) {
                mQuickSettings.add(SLEEP_TILE);
            } else if (tile.equals(TILE_PROFILE)) {
                if (systemProfilesEnabled(resolver)) {
                    mQuickSettings.add(PROFILE_TILE);
                }
            } else if (tile.equals(TILE_WIMAX)) {
                // Not available yet
            } else if (tile.equals(TILE_LTE)) {
                // Not available yet
            }
        }

        // Load the dynamic tiles
        // These toggles must be the last ones added to the view, as they will show
        // only when they are needed
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_ALARM, 1) == 1) {
            mQuickSettings.add(ALARM_TILE);
        }
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_BUGREPORT, 1) == 1) {
            mQuickSettings.add(BUG_REPORT_TILE);
        }
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_WIFI, 1) == 1) {
            mQuickSettings.add(WIFI_DISPLAY_TILE);
        }
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_IME, 1) == 1) {
            mQuickSettings.add(IME_TILE);
        }
    }

    void setupQuickSettings() {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        addQuickSettings(inflater);
    }

    boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    boolean systemProfilesEnabled(ContentResolver resolver) {
        return (Settings.System.getInt(resolver, Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1);
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    void addQuickSettings(LayoutInflater inflater){
        // Load the user configured tiles
        loadTiles();

        // Now add the actual tiles from the loaded list
        for (Integer entry: mQuickSettings) {
            QuickSettingsTile qs = null;
            switch (entry) {
            case WIFI_TILE:
                qs = new WiFiTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case MOBILE_NETWORK_TILE:
                qs = new MobileNetworkTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case AIRPLANE_MODE_TILE:
                qs = new AirplaneModeTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case BLUETOOTH_TILE:
                qs = new BluetoothTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case SOUND_TILE:
                qs = new RingerModeTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case VIBRATION_TILE:
                qs = new VibrationModeTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case SOUND_VIBRATION_TILE:
                qs = new RingerVibrationModeTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case SLEEP_TILE:
                qs = new SleepScreenTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case TOGGLE_LOCKSCREEN_TILE:
                qs = new ToggleLockscreenTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case GPS_TILE:
                qs = new GPSTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case AUTO_ROTATION_TILE:
                qs = new AutoRotateTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case BRIGHTNESS_TILE:
                qs = new BrightnessTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case MOBILE_NETWORK_TYPE_TILE:
                qs = new MobileNetworkTypeTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case ALARM_TILE:
                qs = new AlarmTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case BUG_REPORT_TILE:
                qs = new BugReportTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case WIFI_DISPLAY_TILE:
                qs = new WiFiDisplayTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case SETTINGS_TILE:
                qs = new PreferencesTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case BATTERY_TILE:
                qs = new BatteryTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case IME_TILE:
                IMETile = new InputMethodTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                qs = IMETile;
                break;
            case USER_TILE:
                qs = new UserTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case FLASHLIGHT_TILE:
                qs = new FlashLightTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case WIFIAP_TILE:
                qs = new WifiAPTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            case PROFILE_TILE:
                qs = new ProfileTile(mContext, inflater,
                        (QuickSettingsContainerView) mContainerView, this);
                break;
            }
            if (qs != null) {
                qs.setupQuickSettingsTile();
            }
        }
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public void setImeWindowStatus(boolean visible) {
        if (IMETile != null) {
            IMETile.toggleVisibility(visible);
        }
    }

    public void updateResources() {}

}
