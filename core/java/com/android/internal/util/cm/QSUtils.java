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

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.nfc.NfcAdapter;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.util.List;

public class QSUtils {

    private static boolean sAvailableTilesFiltered;

    private QSUtils() {}

    public static List<String> getAvailableTiles(Context context) {
        filterTiles(context);
        return QSConstants.TILES_AVAILABLE;
    }

    public static List<String> getDefaultTiles(Context context) {
        filterTiles(context);
        return QSConstants.TILES_DEFAULT;
    }

    public static String getDefaultTilesAsString(Context context) {
        List<String> list = getDefaultTiles(context);
        return TextUtils.join(",", list);
    }

    private static void filterTiles(Context context) {
        if (!sAvailableTilesFiltered) {
            boolean deviceSupportsMobile = deviceSupportsMobileData(context);

            // Tiles that need conditional filtering
            for (String tileKey : QSConstants.TILES_AVAILABLE) {
                boolean removeTile = false;
                switch (tileKey) {
                    case QSConstants.TILE_CELLULAR:
                    case QSConstants.TILE_HOTSPOT:
                    case QSConstants.TILE_DATA:
                    case QSConstants.TILE_ROAMING:
                    case QSConstants.TILE_APN:
                        removeTile = !deviceSupportsMobile;
                        break;
                    case QSConstants.TILE_DDS:
                        removeTile = !deviceSupportsDdsSupported(context);
                        break;
                    case QSConstants.TILE_FLASHLIGHT:
                        removeTile = !deviceSupportsFlashLight(context);
                        break;
                    case QSConstants.TILE_BLUETOOTH:
                        removeTile = !deviceSupportsBluetooth();
                        break;
                }
                if (removeTile) {
                    QSConstants.TILES_AVAILABLE.remove(tileKey);
                    QSConstants.TILES_DEFAULT.remove(tileKey);
                }
            }

            sAvailableTilesFiltered = true;
        }
    }

    private static boolean deviceSupportsDdsSupported(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.isMultiSimEnabled()
                && (telephonyManager.getMultiSimConfiguration()
                        == TelephonyManager.MultiSimVariants.DSDA);
    }

    public static boolean deviceSupportsMobileData(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE);
    }

    public static boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    public static boolean systemProfilesEnabled(ContentResolver resolver) {
        return (Settings.System.getInt(resolver, Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1);
    }

    public static boolean deviceSupportsPerformanceProfiles(Context ctx) {
        Resources res = ctx.getResources();
        String perfProfileProp = res.getString(
                com.android.internal.R.string.config_perf_profile_prop);
        return !TextUtils.isEmpty(perfProfileProp);
    }

//    public static boolean expandedDesktopEnabled(ContentResolver resolver) {
//        return Settings.System.getIntForUser(resolver, Settings.System.EXPANDED_DESKTOP_STYLE,
//                0, UserHandle.USER_CURRENT_OR_SELF) != 0;
//    }

    public static boolean deviceSupportsNfc(Context ctx) {
        return NfcAdapter.getDefaultAdapter(ctx) != null;
    }

    public static boolean deviceSupportsFlashLight(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null && flashAvailable
                        && lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            // Ignore
        }
        return false;
    }

    public static boolean deviceSupportsCamera() {
        return Camera.getNumberOfCameras() > 0;
    }

    public static boolean deviceSupportsGps(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    public static boolean adbEnabled(ContentResolver resolver) {
        return (Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0)) == 1;
    }

    public static boolean deviceSupportsCompass(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null);
    }

}