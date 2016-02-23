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
package android.content.pm;

import android.content.res.ThemeConfig;
import android.text.TextUtils;

import java.io.File;
import java.io.IOException;
import java.util.jar.StrictJarFile;
import java.util.zip.ZipEntry;

/**
 * @hide
 */
public class ThemeUtils {
    private static final String TAG = ThemeUtils.class.getSimpleName();

    /* Path inside a theme APK to the overlay folder */
    public static final String OVERLAY_PATH = "assets/overlays/";
    public static final String ICONS_PATH = "assets/icons/";
    public static final String COMMON_RES_PATH = "assets/overlays/common/";
    public static final String RESOURCE_CACHE_DIR = "/data/resource-cache/";
    public static final String COMMON_RES_TARGET = "common";

    // Package name for any app which does not have a specific theme applied
    private static final String DEFAULT_PKG = "default";

    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

    /**
     * IDMAP hash version code used to alter the resulting hash and force recreating
     * of the idmap.  This value should be changed whenever there is a need to force
     * an update to all idmaps.
     */
    private static final byte IDMAP_HASH_VERSION = 3;

    // Actions in manifests which identify legacy icon packs
    public static final String[] sSupportedActions = new String[] {
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME"
    };

    // Categories in manifests which identify legacy icon packs
    public static final String[] sSupportedCategories = new String[] {
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME"
    };


    /**
     * Get the root path of the resource cache for the given theme
     * @param themePkgName
     * @return Root resource cache path for the given theme
     */
    public static String getOverlayResourceCacheDir(String themePkgName) {
        return RESOURCE_CACHE_DIR + themePkgName;
    }

    /**
     * Get the path of the resource cache for the given target and theme
     * @param targetPkgName Target app package name
     * @param themePkgName Theme package name
     * @return Path to the resource cache for this target and theme
     */
    public static String getTargetCacheDir(String targetPkgName, String themePkgName) {
        return getOverlayResourceCacheDir(themePkgName) + File.separator + targetPkgName;
    }

    /**
     * Get the path to the icons for the given theme
     * @param pkgName
     * @return
     */
    public static String getIconPackDir(String pkgName) {
      return getOverlayResourceCacheDir(pkgName) + File.separator + "icons";
    }

    public static String getIconPackApkPath(String pkgName) {
        return getIconPackDir(pkgName) + "/resources.apk";
    }

    public static String getIdmapPath(String targetPkgName, String overlayPkgName) {
        return getTargetCacheDir(targetPkgName, overlayPkgName) + File.separator + "idmap";
    }

    public static String getOverlayPathToTarget(String targetPkgName) {
        StringBuilder sb = new StringBuilder();
        sb.append(OVERLAY_PATH);
        sb.append(targetPkgName);
        sb.append('/');
        return sb.toString();
    }

    public static String getCommonPackageName(String themePackageName) {
        if (TextUtils.isEmpty(themePackageName)) return null;

        return COMMON_RES_TARGET;
    }

    /**
     * Convenience method to determine if a theme component is a per app theme and not a standard
     * component.
     * @param component
     * @return
     */
    public static boolean isPerAppThemeComponent(String component) {
        return !(DEFAULT_PKG.equals(component)
                || ThemeConfig.SYSTEMUI_STATUS_BAR_PKG.equals(component)
                || ThemeConfig.SYSTEMUI_NAVBAR_PKG.equals(component));
    }

    /**
     * Get a 32 bit hashcode for the given package.
     * @param pkg
     * @return
     */
    public static int getPackageHashCode(PackageParser.Package pkg, StrictJarFile jarFile) {
        int hash = pkg.manifestDigest != null ? pkg.manifestDigest.hashCode() : 0;
        final ZipEntry je = jarFile.findEntry(MANIFEST_NAME);
        if (je != null) {
            try {
                try {
                    ManifestDigest digest = ManifestDigest.fromInputStream(
                        jarFile.getInputStream(je));
                    if (digest != null) {
                        hash += digest.hashCode();
                    }
                } finally {
                    jarFile.close();
                }
            } catch (IOException | RuntimeException e) {
                // Failed to generate digest from manifest.mf
            }
        }
        hash = 31 * hash + IDMAP_HASH_VERSION;
        return hash;
    }
}
