/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.util.cm.QSConstants;
import com.android.internal.util.cm.QSUtils;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.tiles.AdbOverNetworkTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.ApnTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.CompassTile;
import com.android.systemui.qs.tiles.DataTile;
import com.android.systemui.qs.tiles.DdsTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LiveDisplayTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.NfcTile;
import com.android.systemui.qs.tiles.LockscreenToggleTile;
import com.android.systemui.qs.tiles.LteTile;
import com.android.systemui.qs.tiles.NotificationsTile;
import com.android.systemui.qs.tiles.ProfilesTile;
import com.android.systemui.qs.tiles.PerfProfileTile;
import com.android.systemui.qs.tiles.RoamingTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.VisualizerTile;
import com.android.systemui.qs.tiles.ScreenTimeoutTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.volume.VolumeComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Platform implementation of the quick settings tile host **/
public class QSTileHost implements QSTile.Host {
    private static final String TAG = "QSTileHost";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final TelephonyManager mTelephonyManager;
    private final PhoneStatusBar mStatusBar;
    private final LinkedHashMap<String, QSTile<?>> mTiles = new LinkedHashMap<>();
    private final Observer mObserver = new Observer();
    private final BluetoothController mBluetooth;
    private final LocationController mLocation;
    private final RotationLockController mRotation;
    private final NetworkController mNetwork;
    private final ZenModeController mZen;
    private final HotspotController mHotspot;
    private final CastController mCast;
    private final Looper mLooper;
    private final CurrentUserTracker mUserTracker;
    private final VolumeComponent mVolume;
    private final UserSwitcherController mUserSwitcherController;
    private final KeyguardMonitor mKeyguard;
    private final SecurityController mSecurity;

    private Callback mCallback;

    public QSTileHost(Context context, PhoneStatusBar statusBar,
            BluetoothController bluetooth, LocationController location,
            RotationLockController rotation, NetworkController network,
            ZenModeController zen, VolumeComponent volume, HotspotController hotspot,
            CastController cast, UserSwitcherController userSwitcher, KeyguardMonitor keyguard,
            SecurityController security) {
        mContext = context;
        mStatusBar = statusBar;
        mBluetooth = bluetooth;
        mLocation = location;
        mRotation = rotation;
        mNetwork = network;
        mZen = zen;
        mVolume = volume;
        mHotspot = hotspot;
        mCast = cast;
        mUserSwitcherController = userSwitcher;
        mKeyguard = keyguard;
        mSecurity = security;
        mConnectivityManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);

        final HandlerThread ht = new HandlerThread(QSTileHost.class.getSimpleName());
        ht.start();
        mLooper = ht.getLooper();

        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onUserSwitched(int newUserId) {
                recreateTiles();
                for (QSTile<?> tile : mTiles.values()) {
                    tile.userSwitch(newUserId);
                }
                mSecurity.onUserSwitched(newUserId);
                mObserver.register();
            }
        };
        recreateTiles();

        mUserTracker.startTracking();
        mObserver.register();
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public Collection<QSTile<?>> getTiles() {
        return mTiles.values();
    }

    @Override
    public void startSettingsActivity(final Intent intent) {
        mStatusBar.postStartSettingsActivity(intent, 0);
    }

    @Override
    public void warn(String message, Throwable t) {
        // already logged
    }

    @Override
    public void collapsePanels() {
        mStatusBar.postAnimateCollapsePanels();
    }

    @Override
    public Looper getLooper() {
        return mLooper;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public BluetoothController getBluetoothController() {
        return mBluetooth;
    }

    @Override
    public LocationController getLocationController() {
        return mLocation;
    }

    @Override
    public RotationLockController getRotationLockController() {
        return mRotation;
    }

    @Override
    public NetworkController getNetworkController() {
        return mNetwork;
    }

    @Override
    public ZenModeController getZenModeController() {
        return mZen;
    }

    @Override
    public VolumeComponent getVolumeComponent() {
        return mVolume;
    }

    @Override
    public HotspotController getHotspotController() {
        return mHotspot;
    }

    @Override
    public CastController getCastController() {
        return mCast;
    }

    @Override
    public KeyguardMonitor getKeyguardMonitor() {
        return mKeyguard;
    }

    public UserSwitcherController getUserSwitcherController() {
        return mUserSwitcherController;
    }

    public SecurityController getSecurityController() {
        return mSecurity;
    }

    private void recreateTiles() {
        if (DEBUG) Log.d(TAG, "Recreating tiles");
        final List<String> tileSpecs = loadTileSpecs();
        for (QSTile oldTile : mTiles.values()) {
            oldTile.destroy();
        }
        final LinkedHashMap<String, QSTile<?>> newTiles = new LinkedHashMap<>();
        for (String tileSpec : tileSpecs) {
            QSTile<?> t = createTile(tileSpec);
            if (t != null) {
                newTiles.put(tileSpec, t);
            }
        }

        mTiles.clear();
        mTiles.putAll(newTiles);
        if (mCallback != null) {
            mCallback.onTilesChanged();
        }
    }

    private QSTile<?> createTile(String tileSpec) {
        if (tileSpec.startsWith(IntentTile.PREFIX)) {
            return IntentTile.create(this, tileSpec);
        }

        // Ensure tile is supported on this device
        if (!QSUtils.getAvailableTiles(mContext).contains(tileSpec)) {
            return null;
        }

        switch (tileSpec) {
            case QSConstants.TILE_WIFI:
                return new WifiTile(this);
            case QSConstants.TILE_BLUETOOTH:
                return new BluetoothTile(this);
            case QSConstants.TILE_INVERSION:
                return new ColorInversionTile(this);
            case QSConstants.TILE_CELLULAR:
                return new CellularTile(this);
            case QSConstants.TILE_AIRPLANE:
                return new AirplaneModeTile(this);
            case QSConstants.TILE_ROTATION:
                return new RotationLockTile(this);
            case QSConstants.TILE_FLASHLIGHT:
                return new FlashlightTile(this);
            case QSConstants.TILE_LOCATION:
                return new LocationTile(this);
            case QSConstants.TILE_CAST:
                return new CastTile(this);
            case QSConstants.TILE_HOTSPOT:
                return new HotspotTile(this);
            case QSConstants.TILE_NOTIFICATIONS:
                return new NotificationsTile(this);
            case QSConstants.TILE_DATA:
                return new DataTile(this);
            case QSConstants.TILE_ROAMING:
                return new RoamingTile(this);
            case QSConstants.TILE_DDS:
                return new DdsTile(this);
            case QSConstants.TILE_COMPASS:
                return new CompassTile(this);
            case QSConstants.TILE_APN:
                return new ApnTile(this);
            case QSConstants.TILE_PROFILES:
                return new ProfilesTile(this);
            case QSConstants.TILE_PERFORMANCE:
                return new PerfProfileTile(this);
            case QSConstants.TILE_ADB_NETWORK:
                return new AdbOverNetworkTile(this);
            case QSConstants.TILE_NFC:
                return new NfcTile(this);
            case QSConstants.TILE_LOCKSCREEN:
                return new LockscreenToggleTile(this);
            case QSConstants.TILE_LTE:
                return new LteTile(this);
            case QSConstants.TILE_VISUALIZER:
                return new VisualizerTile(this);
            case QSConstants.TILE_SCREEN_TIMEOUT:
                return new ScreenTimeoutTile(this);
            case QSConstants.TILE_LIVE_DISPLAY:
                return new LiveDisplayTile(this);
            default:
                throw new IllegalArgumentException("Bad tile spec: " + tileSpec);
        }
    }

    private List<String> loadTileSpecs() {
        final Resources res = mContext.getResources();
        final String defaultTileList = res.getString(R.string.quick_settings_tiles_default);
        String tileList = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.QS_TILES);
        if (DEBUG) Log.d(TAG, "Config string: "+tileList);
        if (tileList == null) {
            tileList = res.getString(R.string.quick_settings_tiles);
            if (DEBUG) Log.d(TAG, "Loaded tile specs from config: " + tileList);
        } else {
            if (DEBUG) Log.d(TAG, "Loaded tile specs from setting: " + tileList);
        }
        final ArrayList<String> tiles = new ArrayList<String>();
        boolean addedDefault = false;
        for (String tile : tileList.split(",")) {
            tile = tile.trim();
            if (tile.isEmpty()) continue;
            if (tile.equals("default")) {
                if (!addedDefault) {
                    tiles.addAll(Arrays.asList(defaultTileList.split(",")));
                    addedDefault = true;
                }
            } else {
                tiles.add(tile);
            }
        }
        return tiles;
    }

    private class Observer extends ContentObserver {
        private boolean mRegistered;

        public Observer() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register() {
            if (mRegistered) {
                mContext.getContentResolver().unregisterContentObserver(this);
            }
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.QS_TILES),
                    false, this, mUserTracker.getCurrentUserId());
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Settings.Secure.QS_USE_MAIN_TILES),
                    false, this, mUserTracker.getCurrentUserId());
            mRegistered = true;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            recreateTiles();
        }
    }
}
