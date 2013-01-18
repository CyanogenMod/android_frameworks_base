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
import java.util.HashMap;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;

import com.android.systemui.quicksettings.AirplaneModeTile;
import com.android.systemui.quicksettings.AlarmTile;
import com.android.systemui.quicksettings.AutoRotateTile;
import com.android.systemui.quicksettings.BatteryTile;
import com.android.systemui.quicksettings.BluetoothTile;
import com.android.systemui.quicksettings.BrightnessTile;
import com.android.systemui.quicksettings.BugReportTile;
import com.android.systemui.quicksettings.NfcTile;
import com.android.systemui.quicksettings.ScreenTimeoutTile;
import com.android.systemui.quicksettings.TorchTile;
import com.android.systemui.quicksettings.GPSTile;
import com.android.systemui.quicksettings.InputMethodTile;
import com.android.systemui.quicksettings.MobileNetworkTile;
import com.android.systemui.quicksettings.MobileNetworkTypeTile;
import com.android.systemui.quicksettings.PreferencesTile;
import com.android.systemui.quicksettings.ProfileTile;
import com.android.systemui.quicksettings.QuickSettingsTile;
import com.android.systemui.quicksettings.RingerModeTile;
import com.android.systemui.quicksettings.SleepScreenTile;
import com.android.systemui.quicksettings.SyncTile;
import com.android.systemui.quicksettings.ToggleLockscreenTile;
import com.android.systemui.quicksettings.UsbTetherTile;
import com.android.systemui.quicksettings.UserTile;
import com.android.systemui.quicksettings.WiFiDisplayTile;
import com.android.systemui.quicksettings.WiFiTile;
import com.android.systemui.quicksettings.WifiAPTile;
import static com.android.internal.util.CmUtils.QuickSettings.*;
public class QuickSettingsController {
    private static String TAG = "QuickSettingsController";

    // Stores the broadcast receivers and content observers
    // quick tiles register for.
    public HashMap<String, ArrayList<QuickSettingsTile>> mReceiverMap
        = new HashMap<String, ArrayList<QuickSettingsTile>>();
    public HashMap<Uri, ArrayList<QuickSettingsTile>> mObserverMap
        = new HashMap<Uri, ArrayList<QuickSettingsTile>>();

    private final Context mContext;
    public PanelBar mBar;
    private final QuickSettingsContainerView mContainerView;
    private final Handler mHandler;
    private BroadcastReceiver mReceiver;
    private ContentObserver mObserver;
    public PhoneStatusBar mStatusBarService;

    // Constants for use in switch statement
    public static final int WIFI_TILE = 0;
    public static final int MOBILE_NETWORK_TILE = 1;
    public static final int AIRPLANE_MODE_TILE = 2;
    public static final int BLUETOOTH_TILE = 3;
    public static final int RINGER_TILE = 4;
    public static final int SLEEP_TILE = 5;
    public static final int TOGGLE_LOCKSCREEN_TILE = 6;
    public static final int GPS_TILE = 7;
    public static final int AUTO_ROTATION_TILE = 8;
    public static final int BRIGHTNESS_TILE = 9;
    public static final int MOBILE_NETWORK_TYPE_TILE = 10;
    public static final int SETTINGS_TILE = 11;
    public static final int BATTERY_TILE = 12;
    public static final int IME_TILE = 13;
    public static final int ALARM_TILE = 14;
    public static final int BUG_REPORT_TILE = 15;
    public static final int WIFI_DISPLAY_TILE = 16;
    public static final int TORCH_TILE = 17;
    public static final int WIFIAP_TILE = 18;
    public static final int PROFILE_TILE = 19;
    public static final int SYNC_TILE = 20;
    public static final int NFC_TILE = 21;
    public static final int SCREENTIMEOUT_TILE = 22;
    public static final int USBTETHER_TILE = 23;
    public static final int USER_TILE = 99;
    private InputMethodTile IMETile;

    public QuickSettingsController(Context context, QuickSettingsContainerView container, PhoneStatusBar statusBarService) {
        mContext = context;
        mContainerView = container;
        mHandler = new Handler();
        mStatusBarService = statusBarService;
    }

    void loadTiles() {

        // Filter items not compatible with device
        boolean bluetoothSupported = deviceSupportsBluetooth();
        boolean telephonySupported = deviceSupportsTelephony(mContext);

        if (!bluetoothSupported) {
            TILES_DEFAULT.remove(TILE_BLUETOOTH);
        }

        if (!telephonySupported) {
            TILES_DEFAULT.remove(TILE_WIFIAP);
            TILES_DEFAULT.remove(TILE_MOBILEDATA);
            TILES_DEFAULT.remove(TILE_NETWORKMODE);
        }

        // Read the stored list of tiles
        ContentResolver resolver = mContext.getContentResolver();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        String tiles = Settings.System.getString(resolver, Settings.System.QUICK_SETTINGS_TILES);
        if (tiles == null) {
            Log.i(TAG, "Default tiles being loaded");
            tiles = TextUtils.join(TILE_DELIMITER, TILES_DEFAULT);
        }

        Log.i(TAG, "Tiles list: " + tiles);

        // Split out the tile names and add to the list
        for (String tile : tiles.split("\\|")) {
            QuickSettingsTile qs = null;
            if (tile.equals(TILE_USER)) {
                qs = new UserTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_BATTERY)) {
                qs = new BatteryTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_SETTINGS)) {
                qs = new PreferencesTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_WIFI)) {
                qs = new WiFiTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_GPS)) {
                qs = new GPSTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_BLUETOOTH) && bluetoothSupported) {
                    qs = new BluetoothTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_BRIGHTNESS)) {
                qs = new BrightnessTile(mContext, inflater, mContainerView, this, mHandler);
            } else if (tile.equals(TILE_RINGER)) {
                qs = new RingerModeTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_SYNC)) {
                qs = new SyncTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_WIFIAP) && telephonySupported) {
                qs = new WifiAPTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_SCREENTIMEOUT)) {
                qs = new ScreenTimeoutTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_MOBILEDATA) && telephonySupported) {
                qs = new MobileNetworkTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_LOCKSCREEN)) {
                qs = new ToggleLockscreenTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_NETWORKMODE) && telephonySupported) {
                qs = new MobileNetworkTypeTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_AUTOROTATE)) {
                qs = new AutoRotateTile(mContext, inflater, mContainerView, this, mHandler);
            } else if (tile.equals(TILE_AIRPLANE)) {
                qs = new AirplaneModeTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_TORCH)) {
                qs = new TorchTile(mContext, inflater, mContainerView, this, mHandler);
            } else if (tile.equals(TILE_SLEEP)) {
                qs = new SleepScreenTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_PROFILE) && systemProfilesEnabled(resolver)) {
                qs = new ProfileTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_NFC) && deviceSupportsNfc(mContext)) {
                // User cannot add the NFC tile if the device does not support it
                // No need to check again here
                qs = new NfcTile(mContext, inflater, mContainerView, this);
            } else if (tile.equals(TILE_WIMAX)) {
                // Not available yet
            } else if (tile.equals(TILE_LTE)) {
                // Not available yet
            }
            if (qs != null) {
                qs.setupQuickSettingsTile();
            }
        }

        // Load the dynamic tiles
        // These toggles must be the last ones added to the view, as they will show
        // only when they are needed
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_ALARM, 1) == 1) {
            QuickSettingsTile qs = new AlarmTile(mContext, inflater, mContainerView, this, mHandler);
            qs.setupQuickSettingsTile();
        }
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_BUGREPORT, 1) == 1) {
            QuickSettingsTile qs = new BugReportTile(mContext, inflater, mContainerView, this, mHandler);
            qs.setupQuickSettingsTile();
        }
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_WIFI, 1) == 1) {
            QuickSettingsTile qs = new WiFiDisplayTile(mContext, inflater, mContainerView, this);
            qs.setupQuickSettingsTile();
        }
        if (Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_IME, 1) == 1) {
            QuickSettingsTile qs = new InputMethodTile(mContext, inflater, mContainerView, this);
            qs.setupQuickSettingsTile();
        }
        if (deviceSupportsUsbTether(mContext) && Settings.System.getInt(resolver, Settings.System.QS_DYNAMIC_USBTETHER, 1) == 1) {
            QuickSettingsTile qs = new UsbTetherTile(mContext, inflater, mContainerView, this);
            qs.setupQuickSettingsTile();
        }
    }

    private void setupQuickSettings() {
        // Clear out old receiver
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
        }
        mReceiver = new QSBroadcastReceiver();
        mReceiverMap.clear();
        ContentResolver resolver = mContext.getContentResolver();
        // Clear out old observer
        if (mObserver != null) {
            resolver.unregisterContentObserver(mObserver);
        }
        mObserver = new QuickSettingsObserver(mHandler);
        mObserverMap.clear();
        loadTiles();
        setupBroadcastReceiver();
        setupContentObserver();
    }

    void setupContentObserver() {
        ContentResolver resolver = mContext.getContentResolver();
        for (Uri uri : mObserverMap.keySet()) {
            resolver.registerContentObserver(uri, false, mObserver);
        }
    }

    private class QuickSettingsObserver extends ContentObserver {
        public QuickSettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            ContentResolver resolver = mContext.getContentResolver();
            for (QuickSettingsTile tile : mObserverMap.get(uri)) {
                tile.onChangeUri(resolver, uri);
            }
        }
    }

    void setupBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        for (String action : mReceiverMap.keySet()) {
            filter.addAction(action);
        }
        mContext.registerReceiver(mReceiver, filter);
    }

    private void registerInMap(Object item, QuickSettingsTile tile, HashMap map) {
        if (map.keySet().contains(item)) {
            ArrayList list = (ArrayList) map.get(item);
            if (!list.contains(tile)) {
                list.add(tile);
            }
        } else {
            ArrayList<QuickSettingsTile> list = new ArrayList<QuickSettingsTile>();
            list.add(tile);
            map.put(item, list);
        }
    }

    public void registerAction(Object action, QuickSettingsTile tile) {
        registerInMap(action, tile, mReceiverMap);
    }

    public void registerObservedContent(Uri uri, QuickSettingsTile tile) {
        registerInMap(uri, tile, mObserverMap);
    }

    private class QSBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                for (QuickSettingsTile t : mReceiverMap.get(action)) {
                    t.onReceive(context, intent);
                }
            }
        }
    };

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public void setImeWindowStatus(boolean visible) {
        if (IMETile != null) {
            IMETile.toggleVisibility(visible);
        }
    }

    public void updateResources() {
        mContainerView.updateResources();
        mContainerView.removeAllViews();
        setupQuickSettings();
        mContainerView.requestLayout();
    }
}
