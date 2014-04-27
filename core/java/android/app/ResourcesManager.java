/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.app;

import static android.app.ActivityThread.DEBUG_CONFIGURATION;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.CustomTheme;
import android.content.res.Resources;
import android.content.res.ResourcesKey;
import android.hardware.display.DisplayManagerGlobal;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayAdjustments;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

/** @hide */
public class ResourcesManager {
    static final String TAG = "ResourcesManager";
    static final boolean DEBUG_CACHE = false;
    static final boolean DEBUG_STATS = true;

    private static ResourcesManager sResourcesManager;
    final ArrayMap<ResourcesKey, WeakReference<Resources> > mActiveResources
            = new ArrayMap<ResourcesKey, WeakReference<Resources> >();

    final ArrayMap<DisplayAdjustments, DisplayMetrics> mDefaultDisplayMetrics
            = new ArrayMap<DisplayAdjustments, DisplayMetrics>();

    CompatibilityInfo mResCompatibilityInfo;
    static IPackageManager sPackageManager;

    Configuration mResConfiguration;
    final Configuration mTmpConfig = new Configuration();

    public static ResourcesManager getInstance() {
        synchronized (ResourcesManager.class) {
            if (sResourcesManager == null) {
                sResourcesManager = new ResourcesManager();
            }
            return sResourcesManager;
        }
    }

    public Configuration getConfiguration() {
        return mResConfiguration;
    }

    public void flushDisplayMetricsLocked() {
        mDefaultDisplayMetrics.clear();
    }

    public DisplayMetrics getDisplayMetricsLocked(int displayId) {
        return getDisplayMetricsLocked(displayId, DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS);
    }

    public DisplayMetrics getDisplayMetricsLocked(int displayId, DisplayAdjustments daj) {
        boolean isDefaultDisplay = (displayId == Display.DEFAULT_DISPLAY);
        DisplayMetrics dm = isDefaultDisplay ? mDefaultDisplayMetrics.get(daj) : null;
        if (dm != null) {
            return dm;
        }
        dm = new DisplayMetrics();

        DisplayManagerGlobal displayManager = DisplayManagerGlobal.getInstance();
        if (displayManager == null) {
            // may be null early in system startup
            dm.setToDefaults();
            return dm;
        }

        if (isDefaultDisplay) {
            mDefaultDisplayMetrics.put(daj, dm);
        }

        Display d = displayManager.getCompatibleDisplay(displayId, daj);
        if (d != null) {
            d.getMetrics(dm);
        } else {
            // Display no longer exists
            // FIXME: This would not be a problem if we kept the Display object around
            // instead of using the raw display id everywhere.  The Display object caches
            // its information even after the display has been removed.
            dm.setToDefaults();
        }
        //Slog.i("foo", "New metrics: w=" + metrics.widthPixels + " h="
        //        + metrics.heightPixels + " den=" + metrics.density
        //        + " xdpi=" + metrics.xdpi + " ydpi=" + metrics.ydpi);
        return dm;
    }

    final void applyNonDefaultDisplayMetricsToConfigurationLocked(
            DisplayMetrics dm, Configuration config) {
        config.touchscreen = Configuration.TOUCHSCREEN_NOTOUCH;
        config.densityDpi = dm.densityDpi;
        config.screenWidthDp = (int)(dm.widthPixels / dm.density);
        config.screenHeightDp = (int)(dm.heightPixels / dm.density);
        int sl = Configuration.resetScreenLayout(config.screenLayout);
        if (dm.widthPixels > dm.heightPixels) {
            config.orientation = Configuration.ORIENTATION_LANDSCAPE;
            config.screenLayout = Configuration.reduceScreenLayout(sl,
                    config.screenWidthDp, config.screenHeightDp);
        } else {
            config.orientation = Configuration.ORIENTATION_PORTRAIT;
            config.screenLayout = Configuration.reduceScreenLayout(sl,
                    config.screenHeightDp, config.screenWidthDp);
        }
        config.smallestScreenWidthDp = config.screenWidthDp; // assume screen does not rotate
        config.compatScreenWidthDp = config.screenWidthDp;
        config.compatScreenHeightDp = config.screenHeightDp;
        config.compatSmallestScreenWidthDp = config.smallestScreenWidthDp;
    }

    public boolean applyCompatConfiguration(int displayDensity,
            Configuration compatConfiguration) {
        if (mResCompatibilityInfo != null && !mResCompatibilityInfo.supportsScreen()) {
            mResCompatibilityInfo.applyToConfiguration(displayDensity, compatConfiguration);
            return true;
        }
        return false;
    }

    /**
     * Creates the top level Resources for applications with the given compatibility info.
     *
     * @param resDir the resource directory.
     * @param compatInfo the compability info. Must not be null.
     * @param token the application token for determining stack bounds.
     */
    public Resources getTopLevelResources(String resDir, String[] overlayDirs, int displayId, String packageName,
            Configuration overrideConfiguration, CompatibilityInfo compatInfo, IBinder token, Context context) {
        final float scale = compatInfo.applicationScale;
        final boolean isThemeable = compatInfo.isThemeable;
        ResourcesKey key = new ResourcesKey(resDir, displayId, overrideConfiguration, scale, isThemeable,
                token);
        Resources r;
        synchronized (this) {
            // Resources is app scale dependent.
            if (false) {
                Slog.w(TAG, "getTopLevelResources: " + resDir + " / " + scale);
            }
            WeakReference<Resources> wr = mActiveResources.get(key);
            r = wr != null ? wr.get() : null;
            //if (r != null) Slog.i(TAG, "isUpToDate " + resDir + ": " + r.getAssets().isUpToDate());
            if (r != null && r.getAssets().isUpToDate()) {
                if (false) {
                    Slog.w(TAG, "Returning cached resources " + r + " " + resDir
                            + ": appScale=" + r.getCompatibilityInfo().applicationScale);
                }
                return r;
            }
        }

        //if (r != null) {
        //    Slog.w(TAG, "Throwing away out-of-date resources!!!! "
        //            + r + " " + resDir);
        //}

        AssetManager assets = new AssetManager();
        assets.setAppName(packageName);
        assets.setThemeSupport(compatInfo.isThemeable);
        if (assets.addAssetPath(resDir) == 0) {
            return null;
        }

        //Slog.i(TAG, "Resource: key=" + key + ", display metrics=" + metrics);
        DisplayMetrics dm = getDisplayMetricsLocked(displayId);
        Configuration config;
        boolean isDefaultDisplay = (displayId == Display.DEFAULT_DISPLAY);
        final boolean hasOverrideConfig = key.hasOverrideConfiguration();
        if (!isDefaultDisplay || hasOverrideConfig) {
            config = new Configuration(getConfiguration());
            if (!isDefaultDisplay) {
                applyNonDefaultDisplayMetricsToConfigurationLocked(dm, config);
            }
            if (hasOverrideConfig) {
                config.updateFrom(key.mOverrideConfiguration);
            }
        } else {
            config = getConfiguration();
        }

        /* Attach theme information to the resulting AssetManager when appropriate. */
        if (compatInfo.isThemeable && config != null && !context.getPackageManager().isSafeMode()) {
            if (config.customTheme == null) {
                try {
                    config.customTheme = CustomTheme.getBootTheme(context.getContentResolver());
                } catch (Exception e) {
                    Slog.d(TAG, "CustomTheme.getBootTheme failed, falling back to system theme", e);
                    config.customTheme = CustomTheme.getSystemTheme();
                }
            }

            if (config.customTheme != null) {
                attachThemeAssets(assets, config.customTheme);
                attachIconAssets(assets, config.customTheme);
            }
        }

        r = new Resources(assets, dm, config, compatInfo, token);
        setActivityIcons(r);

        if (false) {
            Slog.i(TAG, "Created app resources " + resDir + " " + r + ": "
                    + r.getConfiguration() + " appScale="
                    + r.getCompatibilityInfo().applicationScale);
        }

        synchronized (this) {
            WeakReference<Resources> wr = mActiveResources.get(key);
            Resources existing = wr != null ? wr.get() : null;
            if (existing != null && existing.getAssets().isUpToDate()) {
                // Someone else already created the resources while we were
                // unlocked; go ahead and use theirs.
                r.getAssets().close();
                return existing;
            }

            // XXX need to remove entries when weak references go away
            mActiveResources.put(key, new WeakReference<Resources>(r));
            return r;
        }
    }

    /**
     * Creates a map between an activity & app's icon ids to its component info. This map
     * is then stored in the resource object.
     * When resource.getDrawable(id) is called it will check this mapping and replace
     * the id with the themed resource id if one is available
     * @param context
     * @param pkgName
     * @param r
     */
    private void setActivityIcons(Resources r) {
        SparseArray<PackageItemInfo> iconResources = new SparseArray<PackageItemInfo>();
        String pkgName = r.getAssets().getAppName();
        PackageInfo pkgInfo = null;
        ApplicationInfo appInfo = null;

        try {
            pkgInfo = getPackageManager().getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES, UserHandle.myUserId());
        } catch (RemoteException e1) {
            Log.e(TAG, "Unable to get pkg " + pkgName, e1);
            return;
        }

        //Map application icon
        if (pkgInfo != null && pkgInfo.applicationInfo != null) {
            appInfo = pkgInfo.applicationInfo;
            if (appInfo.themedIcon != 0) iconResources.put(appInfo.icon, appInfo);
        }

        //Map activity icons.
        if (pkgInfo != null && pkgInfo.activities != null) {
            for (ActivityInfo ai : pkgInfo.activities) {
                if (ai.themedIcon != 0 && ai.icon != 0) {
                    iconResources.put(ai.icon, ai);
                } else if (ai.themedIcon != 0 && appInfo != null && appInfo.icon != 0) {
                    iconResources.put(appInfo.icon, ai);
                }
            }
        }
        r.setIconResources(iconResources);
    }

    public final int applyConfigurationToResourcesLocked(Configuration config,
            CompatibilityInfo compat) {
        if (mResConfiguration == null) {
            mResConfiguration = new Configuration();
        }
        if (!mResConfiguration.isOtherSeqNewer(config) && compat == null) {
            if (DEBUG_CONFIGURATION) Slog.v(TAG, "Skipping new config: curSeq="
                    + mResConfiguration.seq + ", newSeq=" + config.seq);
            return 0;
        }
        int changes = mResConfiguration.updateFrom(config);
        flushDisplayMetricsLocked();
        DisplayMetrics defaultDisplayMetrics = getDisplayMetricsLocked(Display.DEFAULT_DISPLAY);

        if (compat != null && (mResCompatibilityInfo == null ||
                !mResCompatibilityInfo.equals(compat))) {
            mResCompatibilityInfo = compat;
            changes |= ActivityInfo.CONFIG_SCREEN_LAYOUT
                    | ActivityInfo.CONFIG_SCREEN_SIZE
                    | ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE;
        }

        // set it for java, this also affects newly created Resources
        if (config.locale != null) {
            Locale.setDefault(config.locale);
        }

        Resources.updateSystemConfiguration(config, defaultDisplayMetrics, compat);

        ApplicationPackageManager.configurationChanged();
        //Slog.i(TAG, "Configuration changed in " + currentPackageName());

        Configuration tmpConfig = null;

        for (int i=mActiveResources.size()-1; i>=0; i--) {
            ResourcesKey key = mActiveResources.keyAt(i);
            Resources r = mActiveResources.valueAt(i).get();
            if (r != null) {
                if (DEBUG_CONFIGURATION) Slog.v(TAG, "Changing resources "
                        + r + " config to: " + config);
                int displayId = key.mDisplayId;
                boolean isDefaultDisplay = (displayId == Display.DEFAULT_DISPLAY);
                DisplayMetrics dm = defaultDisplayMetrics;
                final boolean hasOverrideConfiguration = key.hasOverrideConfiguration();
                boolean themeChanged = (changes & ActivityInfo.CONFIG_THEME_RESOURCE) != 0;
                if (themeChanged) {
                    AssetManager am = r.getAssets();
                    if (am.hasThemeSupport()) {
                        r.setIconResources(null);
                        detachThemeAssets(am);
                        if (config.customTheme != null) {
                            attachThemeAssets(am, config.customTheme);
                            attachIconAssets(am, config.customTheme);
                            setActivityIcons(r);
                        }
                    }
                }
                if (!isDefaultDisplay || hasOverrideConfiguration) {
                    if (tmpConfig == null) {
                        tmpConfig = new Configuration();
                    }
                    tmpConfig.setTo(config);
                    if (!isDefaultDisplay) {
                        dm = getDisplayMetricsLocked(displayId);
                        applyNonDefaultDisplayMetricsToConfigurationLocked(dm, tmpConfig);
                    }
                    if (hasOverrideConfiguration) {
                        tmpConfig.updateFrom(key.mOverrideConfiguration);
                    }
                    r.updateConfiguration(tmpConfig, dm, compat);
                } else {
                    r.updateConfiguration(config, dm, compat);
                }
                if (themeChanged) {
                    r.updateStringCache();
                }
                //Slog.i(TAG, "Updated app resources " + v.getKey()
                //        + " " + r + ": " + r.getConfiguration());
            } else {
                //Slog.i(TAG, "Removing old resources " + v.getKey());
                mActiveResources.removeAt(i);
            }
        }

        return changes;
    }

    public static IPackageManager getPackageManager() {
        if (sPackageManager != null) {
            return sPackageManager;
        }
        IBinder b = ServiceManager.getService("package");
        sPackageManager = IPackageManager.Stub.asInterface(b);
        return sPackageManager;
    }


    /**
     * Attach the necessary theme asset paths and meta information to convert an
     * AssetManager to being globally "theme-aware".
     *
     * @param assets
     * @param theme
     * @return true if the AssetManager is now theme-aware; false otherwise.
     *         This can fail, for example, if the theme package has been been
     *         removed and the theme manager has yet to revert formally back to
     *         the framework default.
     */
    private boolean attachThemeAssets(AssetManager assets, CustomTheme theme) {
        PackageInfo piTheme = null;
        PackageInfo piTarget = null;
        PackageInfo piAndroid = null;

        // Some apps run in process of another app (eg keyguard/systemUI) so we must get the package name
        // from the res tables. The 0th base package name will be the android group. The
        // 1st base package name will be the app group if one is attached. Check if it is there
        // first or else the system will crash!
        String packageName = null;
        int count = assets.getBasePackageCount();
        if (count > 1) {
            packageName  = assets.getBasePackageName(1);
        } else if (count <= 1) {
            return false;
        }

        try {
            piTheme = getPackageManager().getPackageInfo(theme.getThemePackageNameForApp(packageName), 0, UserHandle.myUserId());
            piTarget = getPackageManager().getPackageInfo(packageName, 0, UserHandle.myUserId());
            piAndroid = getPackageManager().getPackageInfo("android", 0, UserHandle.myUserId());
        } catch (RemoteException e) {
        }

        if (piTheme == null || piTheme.applicationInfo == null ||
                    piTarget == null || piTarget.applicationInfo == null ||
                    piAndroid == null || piAndroid.applicationInfo == null) {
            return false;
        }

        String themePackageName = piTheme.applicationInfo.packageName;
        String themePath = piTheme.applicationInfo.publicSourceDir;

        if (!piTarget.isThemeApk && piTheme.mOverlayTargets.contains(packageName)) {
            String targetPackagePath = piTarget.applicationInfo.sourceDir;
            String prefixPath = piTheme.isLegacyThemeApk ? "" : ThemeUtils.getOverlayPathToTarget(piTarget.packageName);
            String resCachePath = ThemeUtils.getResDir(piTarget.packageName, piTheme);
            String resTablePath = piTheme.isLegacyThemeApk ? "" : resCachePath + "/resources.arsc";
            String resApkPath = piTheme.isLegacyThemeApk ? "" : resCachePath + "/resources.apk";
            int cookie = assets.addOverlayPath(themePath, resTablePath, resApkPath, targetPackagePath, prefixPath);

            if (cookie != 0) {
                assets.setThemePackageName(themePackageName);
                assets.addThemeCookie(cookie);
            }
        }

        if (!piTarget.isThemeApk && piTheme.mOverlayTargets.contains("android")) {
            String resCachePath= ThemeUtils.getResDir(piAndroid.packageName, piTheme);
            String prefixPath = piTheme.isLegacyThemeApk ? "" : ThemeUtils.getOverlayPathToTarget(piAndroid.packageName);
            String targetPackagePath = piAndroid.applicationInfo.publicSourceDir;
            String resTablePath = piTheme.isLegacyThemeApk ? "" : resCachePath + "/resources.arsc";
            String resApkPath = piTheme.isLegacyThemeApk ? "" : resCachePath + "/resources.apk";
            int cookie = assets.addOverlayPath(themePath, resTablePath, resApkPath, targetPackagePath, prefixPath);
            if (cookie != 0) {
                assets.setThemePackageName(themePackageName);
                assets.addThemeCookie(cookie);
            }
        }

        return true;
    }

    /**
     * Attach the necessary icon asset paths. Icon assets should be in a different
     * namespace than the standard 0x7F.
     *
     * @param assets
     * @param theme
     * @return true if succes, false otherwise
     */
    private boolean attachIconAssets(AssetManager assets, CustomTheme theme) {
        PackageInfo piIcon = null;
        try {
            piIcon = getPackageManager().getPackageInfo(theme.getIconPackPkgName(), 0, UserHandle.myUserId());
        } catch (RemoteException e) {
        }

        if (piIcon == null || piIcon.applicationInfo == null) {
            return false;
        }

        String iconPkg = theme.getIconPackPkgName();
        if (iconPkg != null && !iconPkg.isEmpty()) {
            String themeIconPath =  piIcon.applicationInfo.publicSourceDir;
            String prefixPath = ThemeUtils.ICONS_PATH;
            String iconDir = ThemeUtils.getIconPackDir(iconPkg); //ThemeUtils.getResDir(piTarget.packageName, piTheme);
            String resTablePath = iconDir + "/resources.arsc";
            String resApkPath = iconDir + "/resources.apk";
            int cookie = assets.addIconPath(themeIconPath, resTablePath, resApkPath, prefixPath);
            if (cookie != 0) {
                assets.setIconPackCookie(cookie);
                assets.setIconPackageName(iconPkg);
            }
        }

        return true;
    }

    private void detachThemeAssets(AssetManager assets) {
        String themePackageName = assets.getThemePackageName();
        String iconPackageName = assets.getIconPackageName();

        //Remove Icon pack if it exists
        if (!TextUtils.isEmpty(iconPackageName) && assets.getIconPackCookie() > 0) {
            assets.removeOverlayPath(iconPackageName, assets.getIconPackCookie());
            assets.setIconPackageName(null);
            assets.setIconPackCookie(0);
        }
        final List<Integer> themeCookies = assets.getThemeCookies();
        if (!TextUtils.isEmpty(themePackageName) && !themeCookies.isEmpty()) {
            // remove overlays in reverse order
            for (int i = themeCookies.size() - 1; i >= 0; i--) {
                assets.removeOverlayPath(themePackageName, themeCookies.get(i));
            }
        }
        assets.getThemeCookies().clear();
        assets.setThemePackageName(null);
    }
}
