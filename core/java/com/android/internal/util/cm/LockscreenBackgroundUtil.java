package com.android.internal.util.cm;

import java.io.File;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.Settings;

/**
 * @hide
 */
public class LockscreenBackgroundUtil {

    public static final int LOCKSCREEN_STYLE_IMAGE = 1;
    public static final int LOCKSCREEN_STYLE_DEFAULT = 0;

    private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    private static final String LOCKSCREEN_WALLPAPER_FILE_NAME = "lockwallpaper";

    public static File getWallpaperFile(Context ctx) {
        Context settingsContext = null;
        if (ctx.getPackageName().equals(SETTINGS_PACKAGE_NAME)) {
            settingsContext = ctx;
        } else {
            try {
                settingsContext = ctx.createPackageContext(SETTINGS_PACKAGE_NAME, 0);
            } catch (NameNotFoundException e) {
                // Settings package doesn't exist.
                return null;
            }
        }
        return new File(settingsContext.getFilesDir(), LOCKSCREEN_WALLPAPER_FILE_NAME);
    }

    public static int getLockscreenStyle(Context ctx) {
        return Settings.System.getInt(ctx.getContentResolver(),
                Settings.System.LOCKSCREEN_BACKGROUND_STYLE, LOCKSCREEN_STYLE_DEFAULT);
    }
}
