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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.CustomTheme;
import android.content.res.IThemeChangeListener;
import android.content.res.IThemeService;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.provider.ThemesContract;
import android.util.Log;
import android.webkit.URLUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static android.content.pm.ThemeUtils.SYSTEM_THEME_PATH;
import static android.content.pm.ThemeUtils.THEME_BOOTANIMATION_PATH;

import java.util.List;

import com.android.internal.util.cm.LockscreenBackgroundUtil;

/**
 * {@hide}
 */
public class ThemeService extends IThemeService.Stub {
    private static final String TAG = ThemeService.class.getName();

    private HandlerThread mWorker;
    private ThemeWorkerHandler mHandler;
    private Context mContext;
    private String mPkgName;
    private int mProgress;

    private final RemoteCallbackList<IThemeChangeListener> mClients =
            new RemoteCallbackList<IThemeChangeListener>();

    private class ThemeWorkerHandler extends Handler {
        private static final int MESSAGE_CHANGE_THEME = 1;

        public ThemeWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_CHANGE_THEME:
                    final ThemeData themeData = (ThemeData) msg.obj;
                    doApplyTheme(themeData.pkgName, themeData.components);
                    break;
                default:
                    Log.w(TAG, "Unknown message " + msg.what);
                    break;
            }
        }
    }

    private class ThemeData {
        String pkgName;
        List<String> components;

        public ThemeData(String pkgName, List<String> components) {
            this.pkgName = pkgName;
            this.components = components;
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
    }

    private void doApplyTheme(String pkgName, List<String> components) {
        synchronized(this) {
            mPkgName = pkgName;
            mProgress = 0;
        }

        if (components == null || components.size() == 0) {
            postFinish(true, pkgName);
            return;
        }

        incrementProgress(5, pkgName);

        // TODO: provide progress updates that reflect the time needed for each component
        final int progressIncrement = 75 / components.size();

        updateProvider(components);

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_ICONS)) {
            updateIcons();
            incrementProgress(progressIncrement, pkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_LAUNCHER)) {
            updateWallpaper();
            incrementProgress(progressIncrement, pkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_LOCKSCREEN)) {
            updateLockscreen();
            incrementProgress(progressIncrement, pkgName);
        }

        PackageInfo pi = null;
        try {
            if (!"default".equals(pkgName))
                pi = mContext.getPackageManager().getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            // don't care
        }
        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_NOTIFICATIONS)) {
            updateNotifications(pi);
            incrementProgress(progressIncrement, pkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_ALARMS)) {
            updateAlarms(pi);
            incrementProgress(progressIncrement, pkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_RINGTONES)) {
            updateRingtones(pi);
            incrementProgress(progressIncrement, pkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_BOOT_ANIM)) {
            updateBootAnim();
            incrementProgress(progressIncrement, pkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_FONTS)) {
            updateFonts();
            incrementProgress(progressIncrement, pkgName);
        }

        updateConfiguration(components);

        killLaunchers();

        postFinish(true, pkgName);
    }

    private void updateProvider(List<String> components) {
        ContentValues values = new ContentValues();
        values.put(ThemesContract.MixnMatchColumns.COL_VALUE, mPkgName);

        for (String component : components) {
            String where = ThemesContract.MixnMatchColumns.COL_KEY + "=?";
            String[] selectionArgs = { ThemesContract.MixnMatchColumns.componentToMixNMatchKey(component) };
            if (selectionArgs[0] == null) {
                continue; // No equivalence between mixnmatch and theme
            }
            mContext.getContentResolver().update(ThemesContract.MixnMatchColumns.CONTENT_URI, values, where,
                    selectionArgs);
        }
    }

    private void updateIcons() {
        PackageManager pm = mContext.getPackageManager();
        if (mPkgName.equals("default")) {
            pm.updateIconMaps(null);
        } else {
            pm.updateIconMaps(mPkgName);
        }
    }

    private boolean updateFonts() {
        //Clear the font dir
        ThemeUtils.deleteFilesInDir(ThemeUtils.SYSTEM_THEME_FONT_PATH);

        if (!mPkgName.equals("default")) {
            //Get Font Assets
            Context themeCtx;
            String[] assetList;
            try {
                themeCtx = mContext.createPackageContext(mPkgName, Context.CONTEXT_IGNORE_SECURITY);
                AssetManager assetManager = themeCtx.getAssets();
                assetList = assetManager.list("fonts");
            } catch (Exception e) {
                Log.e(TAG, "There was an error getting assets  for pkg " + mPkgName, e);
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
                    is = ThemeUtils.getInputStreamFromAsset(themeCtx, "file:///android_asset/fonts/" + asset);
                    File outFile = new File(ThemeUtils.SYSTEM_THEME_FONT_PATH, asset);
                    FileUtils.copyToFile(is, outFile);
                    FileUtils.setPermissions(outFile, FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IRWXO, -1, -1);
                } catch (Exception e) {
                    Log.e(TAG, "There was an error installing the new fonts for pkg " + mPkgName, e);
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

    private void updateBootAnim() {
        clearBootAnimation();
        if ("default".equals(mPkgName)) return;

        PackageManager pm = mContext.getPackageManager();
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(mPkgName, 0);
            applyBootAnimation(ai.sourceDir);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Changing boot animation failed", e);
        }
    }

    private boolean updateAlarms(PackageInfo pi) {
        return updateAudible(ThemeUtils.SYSTEM_THEME_ALARM_PATH, "alarms",
                RingtoneManager.TYPE_ALARM, pi);
    }

    private boolean updateNotifications(PackageInfo pi) {
        return updateAudible(ThemeUtils.SYSTEM_THEME_NOTIFICATION_PATH, "notifications",
                RingtoneManager.TYPE_NOTIFICATION, pi);
    }

    private boolean updateRingtones(PackageInfo pi) {
        return updateAudible(ThemeUtils.SYSTEM_THEME_RINGTONE_PATH, "ringtones",
                RingtoneManager.TYPE_RINGTONE, pi);
    }

    private boolean updateAudible(String dirPath, String assetPath, int type, PackageInfo pi) {
        //Clear the dir
        ThemeUtils.clearAudibles(mContext, dirPath);
        if (mPkgName.equals("default")) {
            if (!ThemeUtils.setDefaultAudible(mContext, type)) {
                Log.e(TAG, "There was an error installing the default audio file");
                return false;
            }
            return true;
        }

        if (pi != null && pi.isLegacyThemeApk) {
            return updateLegacyAudible(dirPath, type, pi);
        }

        //Get theme Assets
        Context themeCtx;
        String[] assetList;
        try {
            themeCtx = mContext.createPackageContext(mPkgName, Context.CONTEXT_IGNORE_SECURITY);
            AssetManager assetManager = themeCtx.getAssets();
            assetList = assetManager.list(assetPath);
        } catch (Exception e) {
            Log.e(TAG, "There was an error getting assets for pkg " + mPkgName, e);
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
            FileUtils.setPermissions(outFile, FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IRWXO, -1, -1);
            ThemeUtils.setAudible(mContext, outFile, type);
        } catch (Exception e) {
            Log.e(TAG, "There was an error installing the new audio file for pkg " + mPkgName, e);
            return false;
        } finally {
            ThemeUtils.closeQuietly(is);
            ThemeUtils.closeQuietly(os);
        }
        return true;
    }

    private boolean updateLegacyAudible(String dirPath, int type, PackageInfo pi) {
        if (pi.legacyThemeInfos == null || pi.legacyThemeInfos.length == 0)
            return false;

        //Get theme Assets
        Context themeCtx;
        try {
            themeCtx = mContext.createPackageContext(mPkgName, Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            Log.e(TAG, "There was an error getting assets for pkg " + mPkgName, e);
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
            FileUtils.setPermissions(outFile, FileUtils.S_IRWXU|FileUtils.S_IRGRP|FileUtils.S_IRWXO, -1, -1);
            ThemeUtils.setAudible(mContext, outFile, type);
        } catch (Exception e) {
            Log.e(TAG, "There was an error installing the new audio file for pkg " + mPkgName, e);
            return false;
        } finally {
            ThemeUtils.closeQuietly(is);
            ThemeUtils.closeQuietly(os);
        }
        return true;
    }

    private boolean updateLockscreen() {
        boolean success = false;
        if ("default".equals(mPkgName)) {
            Settings.System.putInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_BACKGROUND_STYLE,
                    LockscreenBackgroundUtil.LOCKSCREEN_STYLE_DEFAULT);
            success = true;
        } else {
            success = setCustomLockScreenWallpaper();
        }

        if (success) {
            mContext.sendBroadcast(new Intent(Intent.ACTION_KEYGUARD_WALLPAPER_CHANGED));
        }
        return success;
    }

    private boolean setCustomLockScreenWallpaper() {
        try {
            //Get input WP stream from the theme
            Context themeCtx = mContext.createPackageContext(mPkgName, Context.CONTEXT_IGNORE_SECURITY);
            AssetManager assetManager = themeCtx.getAssets();
            String wpPath = ThemeUtils.getLockscreenWallpaperPath(assetManager);
            if (wpPath == null) {
                Log.w(TAG, "Not setting lockscreen wp because wallpaper file was not found.");
                return false;
            }
            InputStream is = ThemeUtils.getInputStreamFromAsset(themeCtx, "file:///android_asset/" + wpPath);

            //Get outgoing wp path from settings
            File wallpaperFile = LockscreenBackgroundUtil.getWallpaperFile(mContext);
            wallpaperFile.createNewFile();
            wallpaperFile.setReadable(true, false);
            FileOutputStream out = new FileOutputStream(wallpaperFile);

            //Decode bitmap to check it is ok and copy it over
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        } catch (Exception e) {
            Log.e(TAG, "There was an error setting lockscreen wp for pkg " + mPkgName, e);
            return false;
        }

        Settings.System.putInt(mContext.getContentResolver(), Settings.System.LOCKSCREEN_BACKGROUND_STYLE,
                LockscreenBackgroundUtil.LOCKSCREEN_STYLE_IMAGE);
        return true;
    }

    private boolean updateWallpaper() {
        String selection = ThemesContract.ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = { mPkgName };
        Cursor c = mContext.getContentResolver().query(ThemesContract.MixnMatchColumns.CONTENT_URI, null, selection,
                selectionArgs, null);
        c.moveToFirst();

        if ("default".equals(mPkgName)) {
            try {
                WallpaperManager.getInstance(mContext).clear();
            } catch (IOException e) {
                return false;
            }
        } else {
            InputStream in = null;
            try {
                Context themeContext = mContext.createPackageContext(mPkgName, Context.CONTEXT_IGNORE_SECURITY);
                boolean isLegacyTheme = c.getInt(
                        c.getColumnIndex(ThemesContract.ThemesColumns.IS_LEGACY_THEME)) == 1;
                if (!isLegacyTheme) {
                    String wallpaper = c.getString(c.getColumnIndex(ThemesContract.ThemesColumns.WALLPAPER_URI));
                    if (URLUtil.isAssetUrl(wallpaper)) {
                        in = ThemeUtils.getInputStreamFromAsset(themeContext, wallpaper);
                    } else {
                        in = mContext.getContentResolver().openInputStream(Uri.parse(wallpaper));
                    }
                    WallpaperManager.getInstance(mContext).setStream(in);
                } else {
                    PackageManager pm = mContext.getPackageManager();
                    PackageInfo pi = pm.getPackageInfo(mPkgName, 0);
                    if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0) {
                        WallpaperManager.getInstance(themeContext)
                                .setResource(pi.legacyThemeInfos[0].wallpaperResourceId);
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            } finally {
                ThemeUtils.closeQuietly(in);
            }
        }
        return true;
    }

    private boolean updateConfiguration(List<String> components) {
        final IActivityManager am = ActivityManagerNative.getDefault();
        if (am != null) {
            final long token = Binder.clearCallingIdentity();
            try {
                Configuration config = am.getConfiguration();
                CustomTheme.Builder themeBuilder = createBuilderFrom(config, components);
                config.customTheme = themeBuilder.build();
                am.updateConfiguration(config);
            } catch (RemoteException e) {
                return false;
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
        return true;
    }

    private CustomTheme.Builder createBuilderFrom(Configuration config, List<String> components) {
        CustomTheme.Builder builder = new CustomTheme.Builder(config.customTheme);

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_ICONS)) {
            builder.icons(mPkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_OVERLAYS)) {
            builder.overlay(mPkgName);
        }

        if (components.contains(ThemesContract.ThemesColumns.MODIFIES_FONTS)) {
            builder.fonts(mPkgName);
        }

        return builder;
    }

    // Kill the current Home process, they tend to be evil and cache
    // drawable references in all apps
    private void killLaunchers() {
        final ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final PackageManager pm = mContext.getPackageManager();

        Intent homeIntent = new Intent();
        homeIntent.setAction(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);

        List<ResolveInfo> infos = pm.queryIntentActivities(homeIntent, 0);
        for(ResolveInfo info : infos) {
            if (info.activityInfo != null && info.activityInfo.applicationInfo != null) {
                String pkgToStop = info.activityInfo.applicationInfo.packageName;
                Log.d(TAG, "Force stopping " +  pkgToStop + " for theme change");
                try {
                    am.forceStopPackage(pkgToStop);
                } catch(Exception e) {
                    Log.e(TAG, "Unable to force stop package, did you forget platform signature?" ,e);
                }
            }
        }
    }

    private void postProgress(String pkgName) {
        int N = mClients.beginBroadcast();
        for(int i=0; i < N; i++) {
            IThemeChangeListener listener = mClients.getBroadcastItem(0);
            try {
                listener.onProgress(mProgress, pkgName);
            } catch(RemoteException e) {
                Log.w(TAG, "Unable to post progress to client listener", e);
            }
        }
        mClients.finishBroadcast();
    }

    private void postFinish(boolean isSuccess, String pkgName) {
        synchronized(this) {
            mProgress = 0;
            mPkgName = null;
        }

        int N = mClients.beginBroadcast();
        for(int i=0; i < N; i++) {
            IThemeChangeListener listener = mClients.getBroadcastItem(0);
            try {
                listener.onFinish(isSuccess, pkgName);
            } catch(RemoteException e) {
                Log.w(TAG, "Unable to post progress to client listener", e);
            }
        }
        mClients.finishBroadcast();

        // if successful, broadcast that the theme changed
        if (isSuccess) {
            mContext.sendBroadcast(new Intent(ThemeUtils.ACTION_THEME_CHANGED));
        }
    }

    private void incrementProgress(int increment, String pkgName) {
        synchronized(this) {
            mProgress += increment;
            if (mProgress > 100) mProgress = 100;
        }
        postProgress(pkgName);
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
    public void requestThemeChange(String pkgName, List<String> components) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        Message msg = Message.obtain();
        msg.what = ThemeWorkerHandler.MESSAGE_CHANGE_THEME;
        msg.obj = new ThemeData(pkgName, components);
        mHandler.sendMessage(msg);
    }

    @Override
    public boolean isThemeApplying(String pkgName) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        if (pkgName == null) {
            throw new IllegalArgumentException("Package name is null");
        }
        return pkgName.equals(mPkgName);
    }

    @Override
    public int getProgress(String pkgName) throws RemoteException {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.ACCESS_THEME_MANAGER, null);
        synchronized(this) {
            return mProgress;
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
}
