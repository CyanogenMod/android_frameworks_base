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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.IThemeProcessingListener;
import android.content.res.ThemeConfig;
import android.content.res.IThemeChangeListener;
import android.content.res.IThemeService;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.os.Binder;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.provider.ThemesContract;
import android.provider.ThemesContract.MixnMatchColumns;
import android.provider.ThemesContract.ThemesColumns;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.util.cm.ImageUtils;

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
import static android.content.res.ThemeConfig.SYSTEM_DEFAULT;

import java.util.List;

/**
 * {@hide}
 */
public class ThemeService extends IThemeService.Stub {
    private static final String TAG = ThemeService.class.getName();

    private static final boolean DEBUG = false;

    private static final String GOOGLE_SETUPWIZARD_PACKAGE = "com.google.android.setupwizard";
    private static final String CM_SETUPWIZARD_PACKAGE = "com.cyanogenmod.setupwizard";

    private static final long MAX_ICON_CACHE_SIZE = 33554432L; // 32MB
    private static final long PURGED_ICON_CACHE_SIZE = 25165824L; // 24 MB

    // Defines a min and max compatible api level for themes on this system.
    private static final int MIN_COMPATIBLE_VERSION = 21;

    private HandlerThread mWorker;
    private ThemeWorkerHandler mHandler;
    private ResourceProcessingHandler mResourceProcessingHandler;
    private Context mContext;
    private PackageManager mPM;
    private int mProgress;
    private boolean mWallpaperChangedByUs = false;
    private long mIconCacheSize = 0L;

    private boolean mIsThemeApplying = false;

    private final RemoteCallbackList<IThemeChangeListener> mClients =
            new RemoteCallbackList<IThemeChangeListener>();

    private final RemoteCallbackList<IThemeProcessingListener> mProcessingListeners =
            new RemoteCallbackList<IThemeProcessingListener>();

    final private ArrayList<String> mThemesToProcessQueue = new ArrayList<String>(0);

    private class ThemeWorkerHandler extends Handler {
        private static final int MESSAGE_CHANGE_THEME = 1;
        private static final int MESSAGE_APPLY_DEFAULT_THEME = 2;
        private static final int MESSAGE_REBUILD_RESOURCE_CACHE = 3;

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
                case MESSAGE_REBUILD_RESOURCE_CACHE:
                    doRebuildResourceCache();
                    break;
                default:
                    Log.w(TAG, "Unknown message " + msg.what);
                    break;
            }
        }
    }

    private class ResourceProcessingHandler extends Handler {
        private static final int MESSAGE_QUEUE_THEME_FOR_PROCESSING = 3;
        private static final int MESSAGE_DEQUEUE_AND_PROCESS_THEME = 4;

        public ResourceProcessingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_QUEUE_THEME_FOR_PROCESSING:
                    String pkgName = (String) msg.obj;
                    synchronized (mThemesToProcessQueue) {
                        if (!mThemesToProcessQueue.contains(pkgName)) {
                            if (DEBUG) Log.d(TAG, "Adding " + pkgName + " for processing");
                            mThemesToProcessQueue.add(pkgName);
                            if (mThemesToProcessQueue.size() == 1) {
                                this.sendEmptyMessage(MESSAGE_DEQUEUE_AND_PROCESS_THEME);
                            }
                        }
                    }
                    break;
                case MESSAGE_DEQUEUE_AND_PROCESS_THEME:
                    synchronized (mThemesToProcessQueue) {
                        pkgName = mThemesToProcessQueue.get(0);
                    }
                    if (pkgName != null) {
                        if (DEBUG) Log.d(TAG, "Processing " + pkgName);
                        String name;
                        try {
                            PackageInfo pi = mPM.getPackageInfo(pkgName, 0);
                            name = getThemeName(pi);
                        } catch (PackageManager.NameNotFoundException e) {
                            name = null;
                        }
                        if (name != null) {
                            int result = mPM.processThemeResources(pkgName);
                            if (result < 0) {
                                postFailedThemeInstallNotification(name);
                            }
                            sendThemeResourcesCachedBroadcast(pkgName, result);
                        }
                        synchronized (mThemesToProcessQueue) {
                            mThemesToProcessQueue.remove(0);
                            if (mThemesToProcessQueue.size() > 0 &&
                                    !hasMessages(MESSAGE_DEQUEUE_AND_PROCESS_THEME)) {
                                this.sendEmptyMessage(MESSAGE_DEQUEUE_AND_PROCESS_THEME);
                            }
                        }
                        postFinishedProcessing(pkgName);
                    }
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
        mWorker = new HandlerThread("ThemeServiceWorker", Process.THREAD_PRIORITY_BACKGROUND);
        mWorker.start();
        mHandler = new ThemeWorkerHandler(mWorker.getLooper());
        Log.i(TAG, "Spawned worker thread");

        HandlerThread processingThread = new HandlerThread("ResourceProcessingThread",
                Process.THREAD_PRIORITY_BACKGROUND);
        processingThread.start();
        mResourceProcessingHandler =
                new ResourceProcessingHandler(processingThread.getLooper());

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

        mPM = mContext.getPackageManager();

        processInstalledThemes();

        if (!isThemeApiUpToDate()) {
            Log.d(TAG, "The system has been upgraded to a theme new api, " +
                    "checking if currently set theme is compatible");
            removeObsoleteThemeOverlayIfExists();
            updateThemeApi();
        }
    }

    private void removeObsoleteThemeOverlayIfExists() {
        // Get the current overlay theme so we can see it it's overlay should be unapplied
        final IActivityManager am = ActivityManagerNative.getDefault();
        ThemeConfig config = null;
        try {
            if (am != null) {
                config = am.getConfiguration().themeConfig;
            } else {
                Log.e(TAG, "ActivityManager getDefault() " +
                        "returned null, cannot remove obsolete theme");
            }
        } catch(RemoteException e) {
            Log.e(TAG, "Failed to get the theme config ", e);
        }
        if (config == null) return; // No need to unapply a theme if one isn't set

        // Populate the currentTheme map for the components we care about, we'll look
        // at the compatibility of each pkg below.
        HashMap<String, String> currentThemeMap = new HashMap<String, String>();
        currentThemeMap.put(ThemesColumns.MODIFIES_STATUS_BAR, config.getOverlayForStatusBar());
        currentThemeMap.put(ThemesColumns.MODIFIES_NAVIGATION_BAR,
                config.getOverlayForNavBar());
        currentThemeMap.put(ThemesColumns.MODIFIES_OVERLAYS, config.getOverlayPkgName());

        // Look at each component's theme (that we care about at least) and check compatibility
        // of the pkg with the system. If it is not compatible then we will add it to a theme
        // change request.
        Map<String, String> defaults = ThemeUtils.getDefaultComponents(mContext);
        HashMap<String, String> changeThemeRequestMap = new HashMap<String, String>();
        for(Map.Entry<String, String> entry : currentThemeMap.entrySet()) {
            String component = entry.getKey();
            String pkgName = entry.getValue();
            String defaultPkg = defaults.get(component);

            // Check that the default overlay theme is not currently set
            if (defaultPkg.equals(pkgName)) {
                Log.d(TAG, "Current overlay theme is same as default. " +
                        "Not doing anything for " + component);
                continue;
            }

            // No need to unapply a system theme since it is always compatible
            if (ThemeConfig.SYSTEM_DEFAULT.equals(pkgName)) {
                Log.d(TAG, "Current overlay theme for "
                        + component + " was system. no need to unapply");
                continue;
            }

            if (!isThemeCompatibleWithUpgradedApi(pkgName)) {
                Log.d(TAG, pkgName + "is incompatible with latest theme api for component " +
                        component + ", Applying " + defaultPkg);
                changeThemeRequestMap.put(component, pkgName);
            }
        }

        // Now actually unapply the incompatible themes
        if (!changeThemeRequestMap.isEmpty()) {
            try {
                requestThemeChange(changeThemeRequestMap);
            } catch(RemoteException e) {
                // This cannot happen
            }
        } else {
            Log.d(TAG, "Current theme is compatible with the system. Not unapplying anything");
        }
    }

    private boolean isThemeCompatibleWithUpgradedApi(String pkgName) {
        // Note this function does not cover the case of a downgrade. That case is out of scope and
        // would require predicting whether the future API levels will be compatible or not.
        boolean compatible = false;
        try {
            PackageInfo pi = mPM.getPackageInfo(pkgName, 0);
            Log.d(TAG, "Comparing theme target: " + pi.applicationInfo.targetSdkVersion +
                    "to " + android.os.Build.VERSION.SDK_INT);
            compatible = pi.applicationInfo.targetSdkVersion >= MIN_COMPATIBLE_VERSION;
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Unable to get package info for " + pkgName, e);
        }
        return compatible;
    }

    private boolean isThemeApiUpToDate() {
        // We can't be 100% sure its an upgrade. If the field is undefined it
        // could have been a factory reset.
        final ContentResolver resolver = mContext.getContentResolver();
        int recordedApiLevel = android.os.Build.VERSION.SDK_INT;
        try {
            recordedApiLevel = Settings.Secure.getInt(resolver,
                    Settings.Secure.THEME_PREV_BOOT_API_LEVEL);
        } catch (SettingNotFoundException e) {
            recordedApiLevel = -1;
            Log.d(TAG, "Previous api level not found. First time booting?");
        }
        Log.d(TAG, "Prev api level was: " + recordedApiLevel
                + ", api is now: " + android.os.Build.VERSION.SDK_INT);

        return recordedApiLevel == android.os.Build.VERSION.SDK_INT;
    }

    private void updateThemeApi() {
        final ContentResolver resolver = mContext.getContentResolver();
        boolean success = Settings.Secure.putInt(resolver,
                Settings.Secure.THEME_PREV_BOOT_API_LEVEL, android.os.Build.VERSION.SDK_INT);
        if (!success) {
            Log.e(TAG, "Unable to store latest API level to secure settings");
        }
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

    private void doRebuildResourceCache() {
        FileUtils.deleteContents(new File(ThemeUtils.RESOURCE_CACHE_DIR));
        processInstalledThemes();
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
            if (pkgName.equals(SYSTEM_DEFAULT)) {
                mPM.updateIconMaps(null);
            } else {
                mPM.updateIconMaps(pkgName);
            }
        } catch (Exception e) {
            Log.w(TAG, "Changing icons failed", e);
            return false;
        }
        return true;
    }

    private boolean updateFonts(String pkgName) {
        //Clear the font dir
        FileUtils.deleteContents(new File(ThemeUtils.SYSTEM_THEME_FONT_PATH));

        if (!pkgName.equals(SYSTEM_DEFAULT)) {
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
        if (SYSTEM_DEFAULT.equals(pkgName)) {
            clearBootAnimation();
            return true;
        }

        try {
            final ApplicationInfo ai = mPM.getApplicationInfo(pkgName, 0);
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
        if (pkgName.equals(SYSTEM_DEFAULT)) {
            if (!ThemeUtils.setDefaultAudible(mContext, type)) {
                Log.e(TAG, "There was an error installing the default audio file");
                return false;
            }
            return true;
        }

        PackageInfo pi = null;
        try {
            pi = mPM.getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unable to update audible " + dirPath, e);
            return false;
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
            ThemeUtils.setAudible(mContext, outFile, type, pi.themeInfo.name);
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
            if (SYSTEM_DEFAULT.equals(pkgName) || TextUtils.isEmpty(pkgName)) {
                wm.clearKeyguardWallpaper();
            } else {
                InputStream in = ImageUtils.getCroppedKeyguardStream(pkgName, mContext);
                if (in != null) {
                    wm.setKeyguardStream(in);
                    ThemeUtils.closeQuietly(in);
                }
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
        if (SYSTEM_DEFAULT.equals(pkgName)) {
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
                in = ImageUtils.getCroppedWallpaperStream(pkgName, mContext);
                if (in != null)
                    wm.setStream(in);
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
            builder.overlay(ThemeConfig.SYSTEMUI_STATUS_BAR_PKG, pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_STATUS_BAR) : pkgName);
        }

        if (componentMap.containsKey(ThemesColumns.MODIFIES_NAVIGATION_BAR)) {
            builder.overlay(ThemeConfig.SYSTEMUI_NAVBAR_PKG, pkgName == null ?
                    componentMap.get(ThemesColumns.MODIFIES_NAVIGATION_BAR) : pkgName);
        }

        builder.setThemeChangeTimestamp(System.currentTimeMillis());

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

        Intent homeIntent = new Intent();
        homeIntent.setAction(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        List<ResolveInfo> infos = mPM.queryIntentActivities(homeIntent, 0);
        List<ResolveInfo> themeChangeInfos = mPM.queryBroadcastReceivers(
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
            IThemeChangeListener listener = mClients.getBroadcastItem(i);
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
            IThemeChangeListener listener = mClients.getBroadcastItem(i);
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

    private void postFinishedProcessing(String pkgName) {
        int N = mProcessingListeners.beginBroadcast();
        for(int i=0; i < N; i++) {
            IThemeProcessingListener listener = mProcessingListeners.getBroadcastItem(i);
            try {
                listener.onFinishedProcessing(pkgName);
            } catch(RemoteException e) {
                Log.w(TAG, "Unable to post progress to listener", e);
            }
        }
        mProcessingListeners.finishBroadcast();
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
        Message msg;

        /**
         * Since the ThemeService handles compiling theme resource we need to make sure that any
         * of the components we are trying to apply are either already processed or put to the
         * front of the queue and handled before the theme change takes place.
         *
         * TODO: create a callback that can be sent to any ThemeChangeListeners to notify them that
         * the theme will be applied once the processing is done.
         */
        synchronized (mThemesToProcessQueue) {
            for (Object key : componentMap.keySet()) {
                if (ThemesColumns.MODIFIES_OVERLAYS.equals(key) ||
                        ThemesColumns.MODIFIES_NAVIGATION_BAR.equals(key) ||
                        ThemesColumns.MODIFIES_STATUS_BAR.equals(key) ||
                        ThemesColumns.MODIFIES_ICONS.equals(key)) {
                    String pkgName = (String) componentMap.get(key);
                    if (mThemesToProcessQueue.indexOf(pkgName) > 0) {
                        mThemesToProcessQueue.remove(pkgName);
                        mThemesToProcessQueue.add(0, pkgName);
                        // We want to make sure these resources are taken care of first so
                        // send the dequeue message and place it in the front of the queue
                        msg = mHandler.obtainMessage(
                                ResourceProcessingHandler.MESSAGE_DEQUEUE_AND_PROCESS_THEME);
                        mHandler.sendMessageAtFrontOfQueue(msg);
                    }
                }
            }
        }
        msg = Message.obtain();
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
        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_THEME_MANAGER, null);
        return mIsThemeApplying;
    }

    @Override
    public int getProgress() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_THEME_MANAGER, null);
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

    @Override
    public boolean processThemeResources(String themePkgName) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        try {
            mPM.getPackageInfo(themePkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            // Package doesn't exist so nothing to process
            return false;
        }
        // Obtain a message and send it to the handler to process this theme
        Message msg = mResourceProcessingHandler.obtainMessage(
                ResourceProcessingHandler.MESSAGE_QUEUE_THEME_FOR_PROCESSING, 0, 0, themePkgName);
        mResourceProcessingHandler.sendMessage(msg);
        return true;
    }

    @Override
    public boolean isThemeBeingProcessed(String themePkgName) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        synchronized (mThemesToProcessQueue) {
            return mThemesToProcessQueue.contains(themePkgName);
        }
    }

    @Override
    public void registerThemeProcessingListener(IThemeProcessingListener listener)
            throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        mProcessingListeners.register(listener);
    }

    @Override
    public void unregisterThemeProcessingListener(IThemeProcessingListener listener)
            throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        mProcessingListeners.unregister(listener);
    }

    @Override
    public void rebuildResourceCache() throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        mHandler.sendEmptyMessage(ThemeWorkerHandler.MESSAGE_REBUILD_RESOURCE_CACHE);
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

    private void processInstalledThemes() {
        final String defaultTheme = ThemeUtils.getDefaultThemePackageName(mContext);
        Message msg;
        // Make sure the default theme is the first to get processed!
        if (!ThemeConfig.SYSTEM_DEFAULT.equals(defaultTheme)) {
            msg = mHandler.obtainMessage(
                    ResourceProcessingHandler.MESSAGE_QUEUE_THEME_FOR_PROCESSING,
                    0, 0, defaultTheme);
            mResourceProcessingHandler.sendMessage(msg);
        }
        // Iterate over all installed packages and queue up the ones that are themes or icon packs
        List<PackageInfo> packages = mPM.getInstalledPackages(0);
        for (PackageInfo info : packages) {
            if (!defaultTheme.equals(info.packageName) &&
                    (info.isThemeApk || info.isLegacyIconPackApk)) {
                msg = mHandler.obtainMessage(
                        ResourceProcessingHandler.MESSAGE_QUEUE_THEME_FOR_PROCESSING,
                        0, 0, info.packageName);
                mResourceProcessingHandler.sendMessage(msg);
            }
        }
    }

    private void sendThemeResourcesCachedBroadcast(String themePkgName, int resultCode) {
        final Intent intent = new Intent(Intent.ACTION_THEME_RESOURCES_CACHED);
        intent.putExtra(Intent.EXTRA_THEME_PACKAGE_NAME, themePkgName);
        intent.putExtra(Intent.EXTRA_THEME_RESULT, resultCode);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    /**
     * Posts a notification to let the user know the theme was not installed.
     * @param name
     */
    private void postFailedThemeInstallNotification(String name) {
        NotificationManager nm =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notice = new Notification.Builder(mContext)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentTitle(
                        mContext.getString(R.string.theme_install_error_title))
                .setContentText(String.format(mContext.getString(
                                R.string.theme_install_error_message),
                                name))
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setWhen(System.currentTimeMillis())
                .build();
        nm.notify(name.hashCode(), notice);
    }

    private String getThemeName(PackageInfo pi) {
        if (pi.themeInfo != null) {
            return pi.themeInfo.name;
        } else if (pi.isLegacyIconPackApk) {
            return pi.applicationInfo.name;
        }

        return null;
    }
}
