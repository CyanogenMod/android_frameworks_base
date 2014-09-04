/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.IActivityManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.ThemeConfig;
import android.content.res.IThemeChangeListener;
import android.content.res.IThemeService;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.ThemesContract;
import android.provider.ThemesContract.MixnMatchColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static android.content.pm.ThemeUtils.SYSTEM_THEME_PATH;
import static android.content.pm.ThemeUtils.THEME_BOOTANIMATION_PATH;
import static android.content.res.ThemeConfig.HOLO_DEFAULT;

import java.util.List;

/**
 * {@hide}
 */
public class ThemeService extends IThemeService.Stub {
    private static final String TAG = ThemeService.class.getName();

    private static final String GOOGLE_SETUPWIZARD_PACKAGE = "com.google.android.setupwizard";
    private static final String CM_SETUPWIZARD_PACKAGE = "com.cyanogenmod.account";

    private static final long MAX_ICON_CACHE_SIZE = 33554432L; // 32MB
    private static final long PURGED_ICON_CACHE_SIZE = 25165824L; // 24 MB

    private HandlerThread mWorker;
    private ThemeWorkerHandler mHandler;
    private Context mContext;
    private int mProgress;
    private boolean mWallpaperChangedByUs = false;
    private long mIconCacheSize = 0L;

    private boolean mIsThemeApplying = false;

    private final RemoteCallbackList<IThemeChangeListener> mClients =
            new RemoteCallbackList<IThemeChangeListener>();

    private class ThemeWorkerHandler extends Handler {
        private static final int MESSAGE_CHANGE_THEME = 1;
        private static final int MESSAGE_APPLY_DEFAULT_THEME = 2;
        private static final int MESSAGE_BUILD_ICON_CACHE = 3;

        public ThemeWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CHANGE_THEME:
                    final Map<String, String> componentMap = (Map<String, String>) msg.obj;
                    doApplyTheme(componentMap);
                    break;
                case MESSAGE_APPLY_DEFAULT_THEME:
                    doApplyDefaultTheme();
                    break;
                case MESSAGE_BUILD_ICON_CACHE:
                    doBuildIconCache();
                    break;
                default:
                    Log.w(TAG, "Unknown message " + msg.what);
                    break;
            }
        }
    }

    public ThemeService(Context context) {
        super();
        mContext = context;
        mWorker = new HandlerThread("ThemeServiceWorker");
        mWorker.start();
        mHandler = new ThemeWorkerHandler(mWorker.getLooper());
        Log.i(TAG, "Spawned worker thread");

        // create the theme directory if it does not exist
        ThemeUtils.createThemeDirIfNotExists();
        ThemeUtils.createFontDirIfNotExists();
        ThemeUtils.createAlarmDirIfNotExists();
        ThemeUtils.createNotificationDirIfNotExists();
        ThemeUtils.createRingtoneDirIfNotExists();
        ThemeUtils.createIconCacheDirIfNotExists();
    }

    public void systemRunning() {
        // listen for wallpaper changes
        IntentFilter filter = new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED);
        mContext.registerReceiver(mWallpaperChangeReceiver, filter);
    }

    private void doApplyTheme(Map<String, String> componentMap) {
        synchronized(this) {
            mProgress = 0;
        }

        if (componentMap == null || componentMap.size() == 0) {
            postFinish(true, componentMap);
            return;
        }
        mIsThemeApplying = true;

        incrementProgress(5);

        // TODO: provide progress updates that reflect the time needed for each component
        final int progressIncrement = 75 / componentMap.size();

        if (componentMap.containsKey(ThemesColumns.MODIFIES_ICONS)) {
            if (!updateIcons(componentMap.get(ThemesColumns.MODIFIES_ICONS))) {
                componentMap.remove(ThemesColumns.MODIFIES_ICONS);
            }
            incrementProgress(progressIncrement);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_LAUNCHER)) {
            if (updateWallpaper(componentMap.get(ThemesColumns.MODIFIES_LAUNCHER))) {
                mWallpaperChangedByUs = true;
            } else {
                componentMap.remove(ThemesColumns.MODIFIES_LAUNCHER);
            }
            incrementProgress(progressIncrement);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_LOCKSCREEN)) {
            if (!updateLockscreen(componentMap.get(ThemesColumns.MODIFIES_LOCKSCREEN))) {
                componentMap.remove(ThemesColumns.MODIFIES_LOCKSCREEN);
            }
            incrementProgress(progressIncrement);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_NOTIFICATIONS)) {
            if (!updateNotifications(componentMap.get(ThemesColumns.MODIFIES_NOTIFICATIONS))) {
                componentMap.remove(ThemesColumns.MODIFIES_NOTIFICATIONS);
            }
            incrementProgress(progressIncrement);
        }

        Environment.setUserRequired(false);
        if (componentMap.containsKey(ThemesColumns.MODIFIES_ALARMS)) {
            if (!updateAlarms(componentMap.get(ThemesColumns.MODIFIES_ALARMS))) {
                componentMap.remove(ThemesColumns.MODIFIES_ALARMS);
            }
            incrementProgress(progressIncrement);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_RINGTONES)) {
            if (!updateRingtones(componentMap.get(ThemesColumns.MODIFIES_RINGTONES))) {
                componentMap.remove(ThemesColumns.MODIFIES_RINGTONES);
            }
            incrementProgress(progressIncrement);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_BOOT_ANIM)) {
            if (!updateBootAnim(componentMap.get(ThemesColumns.MODIFIES_BOOT_ANIM))) {
                componentMap.remove(ThemesColumns.MODIFIES_BOOT_ANIM);
            }
            incrementProgress(progressIncrement);
        }
        Environment.setUserRequired(true);

        if (componentMap.containsKey(ThemesColumns.MODIFIES_FONTS)) {
            if (!updateFonts(componentMap.get(ThemesColumns.MODIFIES_FONTS))) {
                componentMap.remove(ThemesColumns.MODIFIES_FONTS);
            }
            incrementProgress(progressIncrement);
        }

        updateProvider(componentMap);

        updateConfiguration(componentMap);

        killLaunchers(componentMap);

        postFinish(true, componentMap);
        mIsThemeApplying = false;
    }

    private void doApplyDefaultTheme() {
        final ContentResolver resolver = mContext.getContentResolver();
        final String defaultThemePkg = Settings.Secure.getString(resolver,
                Settings.Secure.DEFAULT_THEME_PACKAGE);
        if (!TextUtils.isEmpty(defaultThemePkg)) {
            String defaultThemeComponents = Settings.Secure.getString(resolver,
                    Settings.Secure.DEFAULT_THEME_COMPONENTS);
            List<String> components;
            if (TextUtils.isEmpty(defaultThemeComponents)) {
                components = ThemeUtils.getAllComponents();
            } else {
                components = new ArrayList<String>(
                        Arrays.asList(defaultThemeComponents.split("\\|")));
            }
            Map<String, String> componentMap = new HashMap<String, String>(components.size());
            for (String component : components) {
                componentMap.put(component, defaultThemePkg);
            }
            try {
                requestThemeChange(componentMap);
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to set default theme", e);
            }
        }
    }

    private void updateProvider(Map<String, String> componentMap) {
        ContentValues values = new ContentValues();

        for (String component : componentMap.keySet()) {
            values.put(ThemesContract.MixnMatchColumns.COL_VALUE, componentMap.get(component));
            String where = ThemesContract.MixnMatchColumns.COL_KEY + "=?";
            String[] selectionArgs = { MixnMatchColumns.componentToMixNMatchKey(component) };
            if (selectionArgs[0] == null) {
                continue; // No equivalence between mixnmatch and theme
            }
            mContext.getContentResolver().update(MixnMatchColumns.CONTENT_URI, values, where,
                    selectionArgs);
        }
    }

    private boolean updateIcons(String pkgName) {
        try {
            PackageManager pm = mContext.getPackageManager();
            if (pkgName.equals(HOLO_DEFAULT)) {
                pm.updateIconMaps(null);
            } else {
                pm.updateIconMaps(pkgName);
                mHandler.sendEmptyMessage(ThemeWorkerHandler.MESSAGE_BUILD_ICON_CACHE);
            }
        } catch (Exception e) {
            Log.w(TAG, "Changing icons failed", e);
            return false;
        }
        return true;
    }

    private boolean updateFonts(String pkgName) {
        //Clear the font dir
        ThemeUtils.deleteFilesInDir(ThemeUtils.SYSTEM_THEME_FONT_PATH);

        if (!pkgName.equals(HOLO_DEFAULT)) {
            //Get Font Assets
            Context themeCtx;
            String[] assetList;
            try {
                themeCtx = mContext.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY);
                AssetManager assetManager = themeCtx.getAssets();
                assetList = assetManager.list("fonts");
            } catch (Exception e) {
                Log.e(TAG, "There was an error getting assets  for pkg " + pkgName, e);
                return false;
            }
            if (assetList == null || assetList.length == 0) {
                Log.e(TAG, "Could not find any font assets");
                return false;
            }

            //Copy font assets to font dir
            for(String asset : assetList) {
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = ThemeUtils.getInputStreamFromAsset(themeCtx,
                            "file:///android_asset/fonts/" + asset);
                    File outFile = new File(ThemeUtils.SYSTEM_THEME_FONT_PATH, asset);
                    FileUtils.copyToFile(is, outFile);
                    FileUtils.setPermissions(outFile,
                            FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IRWXO, -1, -1);
                } catch (Exception e) {
                    Log.e(TAG, "There was an error installing the new fonts for pkg " + pkgName, e);
                    return false;
                } finally {
                    ThemeUtils.closeQuietly(is);
                    ThemeUtils.closeQuietly(os);
                }
            }
        }

        //Notify zygote that themes need a refresh
        SystemProperties.set("sys.refresh_theme", "1");
        return true;
    }

    private boolean updateBootAnim(String pkgName) {
        if (HOLO_DEFAULT.equals(pkgName)) {
            clearBootAnimation();
            return true;
        }

        PackageManager pm = mContext.getPackageManager();
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
            applyBootAnimation(ai.sourceDir);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Changing boot animation failed", e);
            return false;
        }
        return true;
    }

    private boolean updateAlarms(String pkgName) {
        return updateAudible(ThemeUtils.SYSTEM_THEME_ALARM_PATH, "alarms",
                RingtoneManager.TYPE_ALARM, pkgName);
    }

    private boolean updateNotifications(String pkgName) {
        return updateAudible(ThemeUtils.SYSTEM_THEME_NOTIFICATION_PATH, "notifications",
                RingtoneManager.TYPE_NOTIFICATION, pkgName);
    }

    private boolean updateRingtones(String pkgName) {
        return updateAudible(ThemeUtils.SYSTEM_THEME_RINGTONE_PATH, "ringtones",
                RingtoneManager.TYPE_RINGTONE, pkgName);
    }

    private boolean updateAudible(String dirPath, String assetPath, int type, String pkgName) {
        //Clear the dir
        ThemeUtils.clearAudibles(mContext, dirPath);
        if (pkgName.equals(HOLO_DEFAULT)) {
            if (!ThemeUtils.setDefaultAudible(mContext, type)) {
                Log.e(TAG, "There was an error installing the default audio file");
                return false;
            }
            return true;
        }

        PackageInfo pi = null;
        try {
            pi = mContext.getPackageManager().getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to update audible " + dirPath, e);
            return false;
        }
        if (pi != null && pi.isLegacyThemeApk) {
            return updateLegacyAudible(dirPath, type, pi);
        }

        //Get theme Assets
        Context themeCtx;
        String[] assetList;
        try {
            themeCtx = mContext.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY);
            AssetManager assetManager = themeCtx.getAssets();
            assetList = assetManager.list(assetPath);
        } catch (Exception e) {
            Log.e(TAG, "There was an error getting assets for pkg " + pkgName, e);
            return false;
        }
        if (assetList == null || assetList.length == 0) {
            Log.e(TAG, "Could not find any audio assets");
            return false;
        }

        // TODO: right now we just load the first file but this will need to be changed
        // in the future if multiple audio files are supported.
        final String asset = assetList[0];
        if (!ThemeUtils.isValidAudible(asset)) return false;

        InputStream is = null;
        OutputStream os = null;
        try {
            is = ThemeUtils.getInputStreamFromAsset(themeCtx, "file:///android_asset/"
                    + assetPath + File.separator + asset);
            File outFile = new File(dirPath, asset);
            FileUtils.copyToFile(is, outFile);
            FileUtils.setPermissions(outFile,
                    FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IRWXO,-1, -1);
            ThemeUtils.setAudible(mContext, outFile, type, pi.themeInfos[0].name);
        } catch (Exception e) {
            Log.e(TAG, "There was an error installing the new audio file for pkg " + pkgName, e);
            return false;
        } finally {
            ThemeUtils.closeQuietly(is);
            ThemeUtils.closeQuietly(os);
        }
        return true;
    }

    private boolean updateLegacyAudible(String dirPath, int type, PackageInfo pi) {
        final String pkgName = pi.packageName;
        if (pi.legacyThemeInfos == null || pi.legacyThemeInfos.length == 0)
            return false;

        //Get theme Assets
        Context themeCtx;
        try {
            themeCtx = mContext.createPackageContext(pkgName, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            Log.e(TAG, "There was an error getting assets for pkg " + pkgName, e);
            return false;
        }

        // TODO: right now we just load the first file but this will need to be changed
        // in the future if multiple audio files are supported.
        final String asset;
        switch (type) {
            case RingtoneManager.TYPE_NOTIFICATION:
                asset = pi.legacyThemeInfos[0].notificationFileName;
                break;
            case RingtoneManager.TYPE_RINGTONE:
                asset = pi.legacyThemeInfos[0].ringtoneFileName;
                break;
            default:
                return false;
        }
        if (!ThemeUtils.isValidAudible(asset)) return false;

        InputStream is = null;
        OutputStream os = null;
        try {
            is = ThemeUtils.getInputStreamFromAsset(themeCtx, "file:///android_asset/" + asset);
            File outFile = new File(dirPath, asset.substring(asset.lastIndexOf('/') + 1));
            FileUtils.copyToFile(is, outFile);
            FileUtils.setPermissions(outFile,
                    FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IRWXO, -1, -1);
            ThemeUtils.setAudible(mContext, outFile, type, pi.legacyThemeInfos[0].name);
        } catch (Exception e) {
            Log.e(TAG, "There was an error installing the new audio file for pkg " + pkgName, e);
            return false;
        } finally {
            ThemeUtils.closeQuietly(is);
            ThemeUtils.closeQuietly(os);
        }
        return true;
    }

    private boolean updateLockscreen(String pkgName) {
        boolean success;
        success = setCustomLockScreenWallpaper(pkgName);

        if (success) {
            mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_KEYGUARD_WALLPAPER_CHANGED),
                    UserHandle.ALL);
        }
        return success;
    }

    private boolean setCustomLockScreenWallpaper(String pkgName) {
        WallpaperManager wm = WallpaperManager.getInstance(mContext);
        try {
            if (HOLO_DEFAULT.equals(pkgName)) {
                final Bitmap bmp = BitmapFactory.decodeResource(mContext.getResources(),
                        com.android.internal.R.drawable.default_wallpaper);
                wm.setKeyguardBitmap(bmp);
            } else if (TextUtils.isEmpty(pkgName)) {
                wm.clearKeyguardWallpaper();
            } else {
                //Get input WP stream from the theme
                Context themeCtx = mContext.createPackageContext(pkgName,
                        Context.CONTEXT_IGNORE_SECURITY);
                AssetManager assetManager = themeCtx.getAssets();
                String wpPath = ThemeUtils.getLockscreenWallpaperPath(assetManager);
                if (wpPath == null) {
                    Log.w(TAG, "Not setting lockscreen wp because wallpaper file was not found.");
                    return false;
                }
                InputStream is = ThemeUtils.getInputStreamFromAsset(themeCtx,
                        "file:///android_asset/" + wpPath);

                wm.setKeyguardStream(is);
            }
        } catch (Exception e) {
            Log.e(TAG, "There was an error setting lockscreen wp for pkg " + pkgName, e);
            return false;
        }
        return true;
    }

    private boolean updateWallpaper(String pkgName) {
        String selection = ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = { pkgName };
        Cursor c = mContext.getContentResolver().query(ThemesColumns.CONTENT_URI,
                null, selection,
                selectionArgs, null);
        c.moveToFirst();
        WallpaperManager wm = WallpaperManager.getInstance(mContext);
        if (HOLO_DEFAULT.equals(pkgName)) {
            try {
                wm.clear();
            } catch (IOException e) {
                return false;
            } finally {
                c.close();
            }
        } else if (TextUtils.isEmpty(pkgName)) {
            try {
                wm.clear(false);
            } catch (IOException e) {
                return false;
            } finally {
                c.close();
            }
        } else {
            InputStream in = null;
            try {
                Context themeContext = mContext.createPackageContext(pkgName,
                        Context.CONTEXT_IGNORE_SECURITY);
                boolean isLegacyTheme = c.getInt(
                        c.getColumnIndex(ThemesColumns.IS_LEGACY_THEME)) == 1;
                if (!isLegacyTheme) {
                    String wallpaper = c.getString(
                                c.getColumnIndex(ThemesColumns.WALLPAPER_URI));
                    if (wallpaper != null) {
                        if (URLUtil.isAssetUrl(wallpaper)) {
                            in = ThemeUtils.getInputStreamFromAsset(themeContext, wallpaper);
                        } else {
                            in = mContext.getContentResolver().openInputStream(
                                    Uri.parse(wallpaper));
                        }
                    } else {
                        // try and get the wallpaper directly from the apk if the URI was null
                        Context themeCtx = mContext.createPackageContext(pkgName,
                                Context.CONTEXT_IGNORE_SECURITY);
                        AssetManager assetManager = themeCtx.getAssets();
                        String wpPath = ThemeUtils.getWallpaperPath(assetManager);
                        if (wpPath == null) {
                            Log.w(TAG, "Not setting wp because wallpaper file was not found.");
                            return false;
                        }
                        in = ThemeUtils.getInputStreamFromAsset(themeCtx, "file:///android_asset/"
                                + wpPath);
                    }
                    wm.setStream(in);
                } else {
                    PackageManager pm = mContext.getPackageManager();
                    PackageInfo pi = pm.getPackageInfo(pkgName, 0);
                    if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0) {
                        // we need to get an instance of the WallpaperManager using the theme's
                        // context so it can retrieve the resource
                        wm = WallpaperManager.getInstance(themeContext);
                        wm.setResource(pi.legacyThemeInfos[0].wallpaperResourceId);
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            } finally {
                ThemeUtils.closeQuietly(in);
                c.close();
            }
        }
        return true;
    }

    private boolean updateConfiguration(Map<String, String> components) {
        final IActivityManager am = ActivityManagerNative.getDefault();
        if (am != null) {
            final long token = Binder.clearCallingIdentity();
            try {
                Configuration config = am.getConfiguration();
                ThemeConfig.Builder themeBuilder = createBuilderFrom(config, components, null);
                ThemeConfig newConfig = themeBuilder.build();

                // If this is a theme upgrade then new config equals existing config. The result
                // is that the config is not considered changed and therefore not propagated,
                // which can be problem if the APK path changes (ex theme-1.apk -> theme-2.apk)
                if (newConfig.equals(config.themeConfig)) {
                    // We can't just use null for the themeConfig, it won't be registered as
                    // a changed config value because of the way equals in config had to be written.
                    final String defaultThemePkg =
                            Settings.Secure.getString(mContext.getContentResolver(),
                            Settings.Secure.DEFAULT_THEME_PACKAGE);
                    ThemeConfig.Builder defaultBuilder =
                            createBuilderFrom(config, components, defaultThemePkg);
                    config.themeConfig = defaultBuilder.build();
                    am.updateConfiguration(config);
                }

                config.themeConfig = newConfig;
                am.updateConfiguration(config);
            } catch (RemoteException e) {
                return false;
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
        return true;
    }

    private static ThemeConfig.Builder createBuilderFrom(Configuration config,
            Map<String, String> componentMap, String pkgName) {
        ThemeConfig.Builder builder = new ThemeConfig.Builder(config.themeConfig);

        if (componentMap.containsKey(ThemesColumns.MODIFIES_ICONS)) {
            builder.defaultIcon(pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_ICONS) : pkgName);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_OVERLAYS)) {
            builder.defaultOverlay(pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_OVERLAYS) : pkgName);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_FONTS)) {
            builder.defaultFont(pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_FONTS) : pkgName);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_STATUS_BAR)) {
            builder.overlay("com.android.systemui", pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_STATUS_BAR) : pkgName);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_NAVIGATION_BAR)) {
            builder.overlay(ThemeConfig.SYSTEMUI_NAVBAR_PKG, pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_NAVIGATION_BAR) : pkgName);
        }

        return builder;
    }

    // Kill the current Home process, they tend to be evil and cache
    // drawable references in all apps
    private void killLaunchers(Map<String, String> componentMap) {
        if (!(componentMap.containsKey(ThemesColumns.MODIFIES_ICONS)
                || componentMap.containsKey(ThemesColumns.MODIFIES_OVERLAYS))) {
            return;
        }

        final ActivityManager am =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final PackageManager pm = mContext.getPackageManager();

        Intent homeIntent = new Intent();
        homeIntent.setAction(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        List<ResolveInfo> infos = pm.queryIntentActivities(homeIntent, 0);
        List<ResolveInfo> themeChangeInfos = pm.queryBroadcastReceivers(
                new Intent(ThemeUtils.ACTION_THEME_CHANGED), 0);
        for(ResolveInfo info : infos) {
            if (info.activityInfo != null && info.activityInfo.applicationInfo != null &&
                    !isSetupActivity(info) && !handlesThemeChanges(
                    info.activityInfo.applicationInfo.packageName, themeChangeInfos)) {
                String pkgToStop = info.activityInfo.applicationInfo.packageName;
                Log.d(TAG, "Force stopping " +  pkgToStop + " for theme change");
                try {
                    am.forceStopPackage(pkgToStop);
                } catch(Exception e) {
                    Log.e(TAG, "Unable to force stop package, did you forget platform signature?",
                            e);
                }
            }
        }
    }

    private boolean isSetupActivity(ResolveInfo info) {
        return GOOGLE_SETUPWIZARD_PACKAGE.equals(info.activityInfo.packageName) ||
               CM_SETUPWIZARD_PACKAGE.equals(info.activityInfo.packageName);
    }

    private boolean handlesThemeChanges(String pkgName, List<ResolveInfo> infos) {
        if (infos != null && infos.size() > 0) {
            for (ResolveInfo info : infos) {
                if (info.activityInfo.applicationInfo.packageName.equals(pkgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void postProgress() {
        int N = mClients.beginBroadcast();
        for(int i=0; i < N; i++) {
            IThemeChangeListener listener = mClients.getBroadcastItem(0);
            try {
                listener.onProgress(mProgress);
            } catch(RemoteException e) {
                Log.w(TAG, "Unable to post progress to client listener", e);
            }
        }
        mClients.finishBroadcast();
    }

    private void postFinish(boolean isSuccess, Map<String, String> componentMap) {
        synchronized(this) {
            mProgress = 0;
        }

        int N = mClients.beginBroadcast();
        for(int i=0; i < N; i++) {
            IThemeChangeListener listener = mClients.getBroadcastItem(0);
            try {
                listener.onFinish(isSuccess);
            } catch(RemoteException e) {
                Log.w(TAG, "Unable to post progress to client listener", e);
            }
        }
        mClients.finishBroadcast();

        // if successful, broadcast that the theme changed
        if (isSuccess) {
            broadcastThemeChange(componentMap);
        }
    }

    private void broadcastThemeChange(Map<String, String> components) {
        final Intent intent = new Intent(ThemeUtils.ACTION_THEME_CHANGED);
        ArrayList componentsArrayList = new ArrayList(components.keySet());
        intent.putStringArrayListExtra("components", componentsArrayList);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void incrementProgress(int increment) {
        synchronized(this) {
            mProgress += increment;
            if (mProgress > 100) mProgress = 100;
        }
        postProgress();
    }

    @Override
    public void requestThemeChangeUpdates(IThemeChangeListener listener) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        mClients.register(listener);
    }

    @Override
    public void removeUpdates(IThemeChangeListener listener) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        mClients.unregister(listener);
    }

    @Override
    public void requestThemeChange(Map componentMap) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        Message msg = Message.obtain();
        msg.what = ThemeWorkerHandler.MESSAGE_CHANGE_THEME;
        msg.obj = componentMap;
        mHandler.sendMessage(msg);
    }

    @Override
    public void applyDefaultTheme() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        Message msg = Message.obtain();
        msg.what = ThemeWorkerHandler.MESSAGE_APPLY_DEFAULT_THEME;
        mHandler.sendMessage(msg);
    }

    @Override
    public boolean isThemeApplying() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        return mIsThemeApplying;
    }

    @Override
    public int getProgress() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        synchronized(this) {
            return mProgress;
        }
    }

    @Override
    public boolean cacheComposedIcon(Bitmap icon, String fileName) throws RemoteException {
        final long token = Binder.clearCallingIdentity();
        boolean success;
        FileOutputStream os;
        final File cacheDir = new File(ThemeUtils.SYSTEM_THEME_ICON_CACHE_DIR);
        if (cacheDir.listFiles().length == 0) {
            mIconCacheSize = 0;
        }
        try {
            File outFile = new File(cacheDir, fileName);
            os = new FileOutputStream(outFile);
            icon.compress(Bitmap.CompressFormat.PNG, 90, os);
            os.close();
            FileUtils.setPermissions(outFile,
                    FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH,
                    -1, -1);
            mIconCacheSize += outFile.length();
            if (mIconCacheSize > MAX_ICON_CACHE_SIZE) {
                purgeIconCache();
            }
            success = true;
        } catch (Exception e) {
            success = false;
            Log.w(TAG, "Unable to cache icon " + fileName, e);
        }
        Binder.restoreCallingIdentity(token);
        return success;
    }

    private void purgeIconCache() {
        Log.d(TAG, "Purging icon cahe of size " + mIconCacheSize);
        File cacheDir = new File(ThemeUtils.SYSTEM_THEME_ICON_CACHE_DIR);
        File[] files = cacheDir.listFiles();
        Arrays.sort(files, mOldestFilesFirstComparator);
        for (File f : files) {
            if (!f.isDirectory()) {
                final long size = f.length();
                if(f.delete()) mIconCacheSize -= size;
            }
            if (mIconCacheSize <= PURGED_ICON_CACHE_SIZE) break;
        }
    }

    private boolean applyBootAnimation(String themePath) {
        boolean success = false;
        try {
            ZipFile zip = new ZipFile(new File(themePath));
            ZipEntry ze = zip.getEntry(THEME_BOOTANIMATION_PATH);
            if (ze != null) {
                clearBootAnimation();
                BufferedInputStream is = new BufferedInputStream(zip.getInputStream(ze));
                final String bootAnimationPath = SYSTEM_THEME_PATH + File.separator
                        + "bootanimation.zip";
                ThemeUtils.copyAndScaleBootAnimation(mContext, is, bootAnimationPath);
                FileUtils.setPermissions(bootAnimationPath,
                        FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IROTH, -1, -1);
            }
            zip.close();
            success = true;
        } catch (Exception e) {
            Log.w(TAG, "Unable to load boot animation for " + themePath, e);
        }

        return success;
    }

    private void clearBootAnimation() {
        File anim = new File(SYSTEM_THEME_PATH + File.separator + "bootanimation.zip");
        if (anim.exists())
            anim.delete();
    }

    private BroadcastReceiver mWallpaperChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mWallpaperChangedByUs) {
                // In case the mixnmatch table has a mods_launcher entry, we'll clear it
                Map<String, String> components = new HashMap<String, String>(1);
                components.put(ThemesColumns.MODIFIES_LAUNCHER, "");
                updateProvider(components);
            } else {
                mWallpaperChangedByUs = false;
            }
        }
    };

    private Comparator<File> mOldestFilesFirstComparator = new Comparator<File>() {
        @Override
        public int compare(File lhs, File rhs) {
            return (int) (lhs.lastModified() - rhs.lastModified());
        }
    };

    private void doBuildIconCache() {
        PackageManager pm = mContext.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> infos = pm.queryIntentActivities(mainIntent, 0);
        for(ResolveInfo info : infos) {
            try {
                pm.getActivityIcon(new ComponentName(info.activityInfo.packageName,
                        info.activityInfo.name));
            } catch (Exception e) {
                Log.w(TAG, "Unable to fetch icon for " + info, e);
            }
        }
    }
}
