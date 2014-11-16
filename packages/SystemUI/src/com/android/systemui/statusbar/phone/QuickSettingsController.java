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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import com.android.internal.util.cm.QSUtils;
import com.android.systemui.R;
import com.android.systemui.quicksettings.*;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView.QSSize;
import com.android.systemui.statusbar.policy.NetworkController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static com.android.internal.util.cm.QSConstants.*;

public class QuickSettingsController {
    private static String TAG = "QuickSettingsController";

    // Stores the broadcast receivers and content observers
    // quick tiles register for.
    public HashMap<String, ArrayList<QuickSettingsTile>> mReceiverMap
            = new HashMap<String, ArrayList<QuickSettingsTile>>();
    public HashMap<Uri, ArrayList<QuickSettingsTile>> mObserverMap
            = new HashMap<Uri, ArrayList<QuickSettingsTile>>();

    // Uris that need to be monitored for updating tile status
    private HashSet<Uri> mTileStatusUris = new HashSet<Uri>();

    private final Context mContext;
    private ArrayList<QuickSettingsTile> mQuickSettingsTiles;
    public PanelBar mBar;
    private final QuickSettingsContainerView mContainerView;
    private final Handler mHandler;
    private BroadcastReceiver mReceiver;
    private ContentObserver mObserver;
    public PhoneStatusBar mStatusBarService;
    private final String mSettingsKey;
    private final boolean mRibbonMode;

    private InputMethodTile mIMETile;

    private static final int MSG_UPDATE_TILES = 1000;

    public QuickSettingsController(Context context, QuickSettingsContainerView container,
            PhoneStatusBar statusBarService, String settingsKey, boolean ribbonMode) {
        mContext = context;
        mContainerView = container;
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case MSG_UPDATE_TILES:
                        setupQuickSettings();
                        break;
                }
            }
        };
        mStatusBarService = statusBarService;
        mQuickSettingsTiles = new ArrayList<QuickSettingsTile>();
        mSettingsKey = settingsKey;
        mRibbonMode = ribbonMode;
    }

    public boolean isRibbonMode() {
        return mRibbonMode;
    }

    void loadTiles() {
        // Reset reference tiles
        mIMETile = null;

        // Filter items not compatible with device
        boolean cameraSupported = QSUtils.deviceSupportsCamera();
        boolean bluetoothSupported = QSUtils.deviceSupportsBluetooth();
        boolean mobileDataSupported = QSUtils.deviceSupportsMobileData(mContext);
        boolean lteSupported = QSUtils.deviceSupportsLte(mContext);
        boolean gpsSupported = QSUtils.deviceSupportsGps(mContext);
        boolean torchSupported = QSUtils.deviceSupportsTorch(mContext);

        if (!bluetoothSupported) {
            TILES_DEFAULT.remove(TILE_BLUETOOTH);
        }

        if (!mobileDataSupported) {
            TILES_DEFAULT.remove(TILE_WIFIAP);
            TILES_DEFAULT.remove(TILE_MOBILEDATA);
            TILES_DEFAULT.remove(TILE_NETWORKMODE);
        }

        if (!lteSupported) {
            TILES_DEFAULT.remove(TILE_LTE);
        }

        if (!gpsSupported) {
            TILES_DEFAULT.remove(TILE_GPS);
        }

        if (!torchSupported) {
            TILES_DEFAULT.remove(TILE_TORCH);
        }

        // Read the stored list of tiles
        ContentResolver resolver = mContext.getContentResolver();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        String tiles = Settings.System.getStringForUser(resolver,
                mSettingsKey, UserHandle.USER_CURRENT);
        if (tiles == null) {
            Log.i(TAG, "Default tiles being loaded");
            tiles = TextUtils.join(TILE_DELIMITER, TILES_DEFAULT);
        }

        Log.i(TAG, "Tiles list: " + tiles);

        // Split out the tile names and add to the list
        boolean dockBatteryLoaded = false;
        NetworkController networkController = MSimTelephonyManager.getDefault().isMultiSimEnabled()
                ? mStatusBarService.mMSimNetworkController
                : mStatusBarService.mNetworkController;

        for (String tile : tiles.split("\\|")) {
            QuickSettingsTile qs = null;
            if (tile.equals(TILE_USER)) {
                qs = new UserTile(mContext, this, mHandler);
            } else if (tile.equals(TILE_BATTERY)) {
                qs = new BatteryTile(mContext, this, mStatusBarService.mBatteryController);
            } else if (tile.equals(TILE_SETTINGS)) {
                qs = new PreferencesTile(mContext, this);
            } else if (tile.equals(TILE_WIFI)) {
                qs = new WiFiTile(mContext, this, networkController);
            } else if (tile.equals(TILE_GPS)) {
                qs = new GPSTile(mContext, this, mStatusBarService.mLocationController);
            } else if (tile.equals(TILE_BLUETOOTH) && bluetoothSupported) {
                qs = new BluetoothTile(mContext, this, mStatusBarService.mBluetoothController);
            } else if (tile.equals(TILE_BRIGHTNESS)) {
                qs = new BrightnessTile(mContext, this);
            } else if (tile.equals(TILE_CAMERA) && cameraSupported) {
                qs = new CameraTile(mContext, this, mHandler);
            } else if (tile.equals(TILE_RINGER)) {
                qs = new RingerModeTile(mContext, this);
            } else if (tile.equals(TILE_SYNC)) {
                qs = new SyncTile(mContext, this, mHandler);
            } else if (tile.equals(TILE_WIFIAP) && mobileDataSupported) {
                qs = new WifiAPTile(mContext, this);
            } else if (tile.equals(TILE_SCREENTIMEOUT)) {
                qs = new ScreenTimeoutTile(mContext, this);
            } else if (tile.equals(TILE_MOBILEDATA) && mobileDataSupported) {
                qs = new MobileNetworkTile(mContext, this, networkController);
            } else if (tile.equals(TILE_LOCKSCREEN)) {
                qs = new ToggleLockscreenTile(mContext, this);
            } else if (tile.equals(TILE_NETWORKMODE) && mobileDataSupported) {
                qs = new MobileNetworkTypeTile(mContext, this, networkController);
            } else if (tile.equals(TILE_AUTOROTATE)) {
                qs = new AutoRotateTile(mContext, this);
            } else if (tile.equals(TILE_AIRPLANE)) {
                qs = new AirplaneModeTile(mContext, this, networkController);
            } else if (tile.equals(TILE_TORCH)) {
                qs = new TorchTile(mContext, this);
            } else if (tile.equals(TILE_SLEEP)) {
                qs = new SleepScreenTile(mContext, this);
            } else if (tile.equals(TILE_PROFILE)) {
                mTileStatusUris.add(Settings.System.getUriFor(
                        Settings.System.SYSTEM_PROFILES_ENABLED));
                if (QSUtils.systemProfilesEnabled(resolver)) {
                    qs = new ProfileTile(mContext, this);
                }
            } else if (tile.equals(TILE_PERFORMANCE_PROFILE)) {
                if (QSUtils.deviceSupportsPerformanceProfiles(mContext)) {
                    qs = new PerformanceProfileTile(mContext, this);
                }
            } else if (tile.equals(TILE_NFC)) {
                // User cannot add the NFC tile if the device does not support it
                // No need to check again here
                qs = new NfcTile(mContext, this);
            } else if (tile.equals(TILE_WIMAX)) {
                // Not available yet
            } else if (tile.equals(TILE_LTE)) {
                qs = new LteTile(mContext, this);
            } else if (tile.equals(TILE_QUIETHOURS)) {
                qs = new QuietHoursTile(mContext, this);
            } else if (tile.equals(TILE_VOLUME)) {
                qs = new VolumeTile(mContext, this);
            } else if (tile.equals(TILE_EXPANDEDDESKTOP)) {
                mTileStatusUris.add(Settings.System.getUriFor(
                            Settings.System.EXPANDED_DESKTOP_STYLE));
                if (QSUtils.expandedDesktopEnabled(resolver)) {
                    qs = new ExpandedDesktopTile(mContext, this);
                }
            } else if (tile.equals(TILE_NETWORKADB)) {
                mTileStatusUris.add(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED));
                if (QSUtils.adbEnabled(resolver)) {
                    qs = new NetworkAdbTile(mContext, this);
                }
            } else if (tile.equals(TILE_COMPASS)) {
                qs = new CompassTile(mContext, this);
            } else if (tile.equals(TILE_HEADS_UP)) {
                qs = new HeadsUpTile(mContext, this);
            } else if (tile.equals(TILE_THEMES)) {
                qs = new ThemesTile(mContext, this);
            }

            if (qs != null) {
                qs.setupQuickSettingsTile(inflater, mContainerView);
                mQuickSettingsTiles.add(qs);

                // Add dock battery beside main battery when possible
                if (qs instanceof BatteryTile) {
                    loadDockBatteryTile(resolver, inflater);
                    dockBatteryLoaded = true;
                }
            }
        }

        if (mRibbonMode) {
            return;
        }

        // Load the dynamic tiles
        // These toggles must be the last ones added to the view, as they will show
        // only when they are needed
        if (Settings.System.getIntForUser(resolver,
                    Settings.System.QS_DYNAMIC_ALARM, 1, UserHandle.USER_CURRENT) == 1) {
            QuickSettingsTile qs = new AlarmTile(mContext, this);
            qs.setupQuickSettingsTile(inflater, mContainerView);
            mQuickSettingsTiles.add(qs);
        }
        if (Settings.System.getIntForUser(resolver,
                    Settings.System.QS_DYNAMIC_BUGREPORT, 1, UserHandle.USER_CURRENT) == 1) {
            QuickSettingsTile qs = new BugReportTile(mContext, this, mHandler);
            qs.setupQuickSettingsTile(inflater, mContainerView);
            mQuickSettingsTiles.add(qs);
        }
        if (!dockBatteryLoaded) {
            loadDockBatteryTile(resolver, inflater);
        }
        if (Settings.System.getIntForUser(resolver,
                    Settings.System.QS_DYNAMIC_WIFI, 1, UserHandle.USER_CURRENT) == 1) {
            QuickSettingsTile qs = new RemoteDisplayTile(mContext, this);
            qs.setupQuickSettingsTile(inflater, mContainerView);
            mQuickSettingsTiles.add(qs);
        }
        if (QSUtils.deviceSupportsImeSwitcher(mContext) && Settings.System.getIntForUser(resolver,
                    Settings.System.QS_DYNAMIC_IME, 1, UserHandle.USER_CURRENT) == 1) {
            mIMETile = new InputMethodTile(mContext, this);
            mIMETile.setupQuickSettingsTile(inflater, mContainerView);
            mQuickSettingsTiles.add(mIMETile);
        }
        if (QSUtils.deviceSupportsUsbTether(mContext) && Settings.System.getIntForUser(resolver,
                    Settings.System.QS_DYNAMIC_USBTETHER, 1, UserHandle.USER_CURRENT) == 1) {
            QuickSettingsTile qs = new UsbTetherTile(mContext, this);
            qs.setupQuickSettingsTile(inflater, mContainerView);
            mQuickSettingsTiles.add(qs);
        }
        if (Settings.System.getIntForUser(resolver,
                Settings.System.QS_DYNAMIC_EQUALIZER, 1, UserHandle.USER_CURRENT) == 1) {
            QuickSettingsTile qs = new EqualizerTile(mContext, this);
            qs.setupQuickSettingsTile(inflater, mContainerView);
            mQuickSettingsTiles.add(qs);
        }
    }

    private void loadDockBatteryTile(final ContentResolver resolver,
            final LayoutInflater inflater) {
        if (!QSUtils.deviceSupportsDockBattery(mContext)) {
            return;
        }
        if (Settings.System.getIntForUser(resolver,
                    Settings.System.QS_DYNAMIC_DOCK_BATTERY, 1, UserHandle.USER_CURRENT) == 0) {
            return;
        }

        QuickSettingsTile qs = new DockBatteryTile(mContext, this,
                mStatusBarService.mDockBatteryController);
        qs.setupQuickSettingsTile(inflater, mContainerView);
        mQuickSettingsTiles.add(qs);
    }

    public void shutdown() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
        if (mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
        }
        for (QuickSettingsTile qs : mQuickSettingsTiles) {
            qs.onDestroy();
        }
        mQuickSettingsTiles.clear();
        mContainerView.removeAllViews();
    }

    protected void setupQuickSettings() {
        shutdown();
        mReceiver = new QSBroadcastReceiver();
        mReceiverMap.clear();
        mObserver = new QuickSettingsObserver(mHandler);
        mObserverMap.clear();
        mTileStatusUris.clear();
        loadTiles();
        setupBroadcastReceiver();
        setupContentObserver();
        ContentResolver resolver = mContext.getContentResolver();
        boolean smallIcons = Settings.System.getIntForUser(resolver,
                Settings.System.QUICK_SETTINGS_SMALL_ICONS, 0, UserHandle.USER_CURRENT) == 1;
        if (mRibbonMode || smallIcons) {
            for (QuickSettingsTile t : mQuickSettingsTiles) {
                if (mRibbonMode) {
                    t.switchToRibbonMode();
                } else {
                    t.switchToSmallIcons();
                }
            }
        }
        updateResources();
    }

    void setupContentObserver() {
        ContentResolver resolver = mContext.getContentResolver();
        for (Uri uri : mObserverMap.keySet()) {
            resolver.registerContentObserver(uri, false, mObserver);
        }
        for (Uri uri : mTileStatusUris) {
            resolver.registerContentObserver(uri, false, mObserver);
        }
    }

    private class QuickSettingsObserver extends ContentObserver {
        public QuickSettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (mTileStatusUris.contains(uri)) {
                mHandler.removeMessages(MSG_UPDATE_TILES);
                mHandler.sendEmptyMessage(MSG_UPDATE_TILES);
            } else {
                ContentResolver resolver = mContext.getContentResolver();
                if (mObserverMap != null && mObserverMap.get(uri) != null) {
                    for (QuickSettingsTile tile : mObserverMap.get(uri)) {
                        tile.onChangeUri(resolver, uri);
                    }
                }
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
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

    public void registerAction(String action, QuickSettingsTile tile) {
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
        if (mIMETile != null) {
            mIMETile.toggleVisibility(visible);
        }
    }

    public void updateResources() {
        updateSize();
        mContainerView.updateResources();
        for (QuickSettingsTile t : mQuickSettingsTiles) {
            t.updateResources();
        }
    }

    private void updateSize() {
        if (mContainerView == null || !mRibbonMode)
            return;

        QSSize size = mContainerView.getRibbonSize();
        int height, margin;
        if (size == QSSize.AutoNarrow || size == QSSize.Narrow) {
            height = R.dimen.qs_ribbon_height_small;
            margin = R.dimen.qs_tile_ribbon_icon_margin_small;
        } else {
            height = R.dimen.qs_ribbon_height_big;
            margin = R.dimen.qs_tile_ribbon_icon_margin_big;
        }
        Resources res = mContext.getResources();
        height = res.getDimensionPixelSize(height);
        margin = res.getDimensionPixelSize(margin);

        View parent = (View) mContainerView.getParent();
        LayoutParams lp = parent.getLayoutParams();
        lp.height = height;
        parent.setLayoutParams(lp);
        for (QuickSettingsTile t : mQuickSettingsTiles) {
            t.setImageMargins(margin);
        }
    }
}
