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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.ThemeConfig;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.FileUtils;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.ThemesContract;
import android.provider.ThemesContract.ThemesColumns;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static android.content.res.ThemeConfig.SYSTEM_DEFAULT;

/**
 * @hide
 */
public class ThemeUtils {
    private static final String TAG = "ThemeUtils";

    /* Path inside a theme APK to the overlay folder */
    public static final String OVERLAY_PATH = "assets/overlays/";
    public static final String ICONS_PATH = "assets/icons/";
    public static final String COMMON_RES_PATH = "assets/overlays/common/";
    public static final String FONT_XML = "fonts.xml";
    public static final String RESOURCE_CACHE_DIR = "/data/resource-cache/";
    public static final String IDMAP_SUFFIX = "@idmap";
    public static final String COMMON_RES_SUFFIX = ".common";
    public static final String COMMON_RES_TARGET = "common";
    public static final String ICON_HASH_FILENAME = "hash";

    // path to external theme resources, i.e. bootanimation.zip
    public static final String SYSTEM_THEME_PATH = "/data/system/theme";
    public static final String SYSTEM_THEME_FONT_PATH = SYSTEM_THEME_PATH + File.separator + "fonts";
    public static final String SYSTEM_THEME_RINGTONE_PATH = SYSTEM_THEME_PATH
            + File.separator + "ringtones";
    public static final String SYSTEM_THEME_NOTIFICATION_PATH = SYSTEM_THEME_PATH
            + File.separator + "notifications";
    public static final String SYSTEM_THEME_ALARM_PATH = SYSTEM_THEME_PATH
            + File.separator + "alarms";
    public static final String SYSTEM_THEME_ICON_CACHE_DIR = SYSTEM_THEME_PATH
            + File.separator + "icons";
    // internal path to bootanimation.zip inside theme apk
    public static final String THEME_BOOTANIMATION_PATH = "assets/bootanimation/bootanimation.zip";

    public static final String SYSTEM_MEDIA_PATH = "/system/media/audio";
    public static final String SYSTEM_ALARMS_PATH = SYSTEM_MEDIA_PATH + File.separator
            + "alarms";
    public static final String SYSTEM_RINGTONES_PATH = SYSTEM_MEDIA_PATH + File.separator
            + "ringtones";
    public static final String SYSTEM_NOTIFICATIONS_PATH = SYSTEM_MEDIA_PATH + File.separator
            + "notifications";

    private static final String MEDIA_CONTENT_URI = "content://media/internal/audio/media";

    // Constants for theme change broadcast
    public static final String ACTION_THEME_CHANGED = "org.cyanogenmod.intent.action.THEME_CHANGED";
    public static final String CATEGORY_THEME_COMPONENT_PREFIX = "org.cyanogenmod.intent.category.";
    public static final String EXTRA_COMPONENTS = "components";
    public static final String EXTRA_REQUEST_TYPE = "request_type";
    public static final String EXTRA_UPDATE_TIME = "update_time";

    public static final int SYSTEM_TARGET_API = 0;

    // Package name for any app which does not have a specific theme applied
    private static final String DEFAULT_PKG = "default";

    private static final String SETTINGS_DB =
            "/data/data/com.android.providers.settings/databases/settings.db";
    private static final String SETTINGS_SECURE_TABLE = "secure";

    // Actions in manifests which identify legacy icon packs
    public static final String[] sSupportedActions = new String[] {
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme"
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
     * @param targetPkgName
     * @param themePkg
     * @return Path to the resource cache for this target and theme
     */
    public static String getTargetCacheDir(String targetPkgName, PackageInfo themePkg) {
        return getTargetCacheDir(targetPkgName, themePkg.packageName);
    }

    public static String getTargetCacheDir(String targetPkgName, PackageParser.Package themePkg) {
        return getTargetCacheDir(targetPkgName, themePkg.packageName);
    }

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

    public static String getIconHashFile(String pkgName) {
        return getIconPackDir(pkgName) + File.separator  +  ICON_HASH_FILENAME;
    }

    public static String getIconPackApkPath(String pkgName) {
        return getIconPackDir(pkgName) + "/resources.apk";
    }

    public static String getIconPackResPath(String pkgName) {
        return getIconPackDir(pkgName) + "/resources.arsc";
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

    public static void createCacheDirIfNotExists() throws IOException {
        File file = new File(RESOURCE_CACHE_DIR);
        if (!file.exists() && !file.mkdir()) {
            throw new IOException("Could not create dir: " + file.toString());
        }
        FileUtils.setPermissions(file, FileUtils.S_IRWXU
                | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
    }

    public static void createResourcesDirIfNotExists(String targetPkgName, String overlayPkgName)
            throws IOException {
        createDirIfNotExists(getOverlayResourceCacheDir(overlayPkgName));
        File file = new File(getTargetCacheDir(targetPkgName, overlayPkgName));
        if (!file.exists() && !file.mkdir()) {
            throw new IOException("Could not create dir: " + file.toString());
        }
        FileUtils.setPermissions(file, FileUtils.S_IRWXU
                | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
    }

    public static void createIconDirIfNotExists(String pkgName) throws IOException {
        createDirIfNotExists(getOverlayResourceCacheDir(pkgName));
        File file = new File(getIconPackDir(pkgName));
        if (!file.exists() && !file.mkdir()) {
            throw new IOException("Could not create dir: " + file.toString());
        }
        FileUtils.setPermissions(file, FileUtils.S_IRWXU
                | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
    }

    private static boolean dirExists(String dirPath) {
        final File dir = new File(dirPath);
        return dir.exists() && dir.isDirectory();
    }

    private static void createDirIfNotExists(String dirPath) {
        if (!dirExists(dirPath)) {
            File dir = new File(dirPath);
            if (dir.mkdir()) {
                FileUtils.setPermissions(dir, FileUtils.S_IRWXU |
                        FileUtils.S_IRWXG| FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
            }
        }
    }

    /**
     * Create SYSTEM_THEME_PATH directory if it does not exist
     */
    public static void createThemeDirIfNotExists() {
        createDirIfNotExists(SYSTEM_THEME_PATH);
    }

    /**
     * Create SYSTEM_FONT_PATH directory if it does not exist
     */
    public static void createFontDirIfNotExists() {
        createDirIfNotExists(SYSTEM_THEME_FONT_PATH);
    }

    /**
     * Create SYSTEM_THEME_RINGTONE_PATH directory if it does not exist
     */
    public static void createRingtoneDirIfNotExists() {
        createDirIfNotExists(SYSTEM_THEME_RINGTONE_PATH);
    }

    /**
     * Create SYSTEM_THEME_NOTIFICATION_PATH directory if it does not exist
     */
    public static void createNotificationDirIfNotExists() {
        createDirIfNotExists(SYSTEM_THEME_NOTIFICATION_PATH);
    }

    /**
     * Create SYSTEM_THEME_ALARM_PATH directory if it does not exist
     */
    public static void createAlarmDirIfNotExists() {
        createDirIfNotExists(SYSTEM_THEME_ALARM_PATH);
    }

    /**
     * Create SYSTEM_THEME_ICON_CACHE_DIR directory if it does not exist
     */
    public static void createIconCacheDirIfNotExists() {
        createDirIfNotExists(SYSTEM_THEME_ICON_CACHE_DIR);
    }

    public static void clearIconCache() {
        FileUtils.deleteContents(new File(SYSTEM_THEME_ICON_CACHE_DIR));
    }

    public static InputStream getInputStreamFromAsset(Context ctx, String path) throws IOException {
        if (ctx == null || path == null)
            return null;
        InputStream is = null;
        String ASSET_BASE = "file:///android_asset/";
        path = path.substring(ASSET_BASE.length());
        AssetManager assets = ctx.getAssets();
        is = assets.open(path);
        return is;
    }

    public static void closeQuietly(InputStream stream) {
        if (stream == null)
            return;
        try {
            stream.close();
        } catch (IOException e) {
        }
    }

    public static void closeQuietly(OutputStream stream) {
        if (stream == null)
            return;
        try {
            stream.close();
        } catch (IOException e) {
        }
    }

    /**
     * Scale the boot animation to better fit the device by editing the desc.txt found
     * in the bootanimation.zip
     * @param context Context to use for getting an instance of the WindowManager
     * @param input InputStream of the original bootanimation.zip
     * @param dst Path to store the newly created bootanimation.zip
     * @throws IOException
     */
    public static void copyAndScaleBootAnimation(Context context, InputStream input, String dst)
            throws IOException {
        final OutputStream os = new FileOutputStream(dst);
        final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os));
        final ZipInputStream bootAni = new ZipInputStream(new BufferedInputStream(input));
        ZipEntry ze;

        zos.setMethod(ZipOutputStream.STORED);
        final byte[] bytes = new byte[4096];
        int len;
        while ((ze = bootAni.getNextEntry()) != null) {
            ZipEntry entry = new ZipEntry(ze.getName());
            entry.setMethod(ZipEntry.STORED);
            entry.setCrc(ze.getCrc());
            entry.setSize(ze.getSize());
            entry.setCompressedSize(ze.getSize());
            if (!ze.getName().equals("desc.txt")) {
                // just copy this entry straight over into the output zip
                zos.putNextEntry(entry);
                while ((len = bootAni.read(bytes)) > 0) {
                    zos.write(bytes, 0, len);
                }
            } else {
                String line;
                BufferedReader reader = new BufferedReader(new InputStreamReader(bootAni));
                final String[] info = reader.readLine().split(" ");

                int scaledWidth;
                int scaledHeight;
                WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(dm);
                // just in case the device is in landscape orientation we will
                // swap the values since most (if not all) animations are portrait
                if (dm.widthPixels > dm.heightPixels) {
                    scaledWidth = dm.heightPixels;
                    scaledHeight = dm.widthPixels;
                } else {
                    scaledWidth = dm.widthPixels;
                    scaledHeight = dm.heightPixels;
                }

                int width = Integer.parseInt(info[0]);
                int height = Integer.parseInt(info[1]);

                if (width == height)
                    scaledHeight = scaledWidth;
                else {
                    // adjust scaledHeight to retain original aspect ratio
                    float scale = (float)scaledWidth / (float)width;
                    int newHeight = (int)((float)height * scale);
                    if (newHeight < scaledHeight)
                        scaledHeight = newHeight;
                }

                CRC32 crc32 = new CRC32();
                int size = 0;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                line = String.format("%d %d %s\n", scaledWidth, scaledHeight, info[2]);
                buffer.put(line.getBytes());
                size += line.getBytes().length;
                crc32.update(line.getBytes());
                while ((line = reader.readLine()) != null) {
                    line = String.format("%s\n", line);
                    buffer.put(line.getBytes());
                    size += line.getBytes().length;
                    crc32.update(line.getBytes());
                }
                entry.setCrc(crc32.getValue());
                entry.setSize(size);
                entry.setCompressedSize(size);
                zos.putNextEntry(entry);
                zos.write(buffer.array(), 0, size);
            }
            zos.closeEntry();
        }
        zos.close();
    }

    public static boolean isValidAudible(String fileName) {
        return (fileName != null &&
                (fileName.endsWith(".mp3") || fileName.endsWith(".ogg")));
    }

    public static boolean setAudible(Context context, File ringtone, int type, String name) {
        final String path = ringtone.getAbsolutePath();
        final String mimeType = name.endsWith(".ogg") ? "audio/ogg" : "audio/mp3";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, path);
        values.put(MediaStore.MediaColumns.TITLE, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        values.put(MediaStore.MediaColumns.SIZE, ringtone.length());
        values.put(MediaStore.Audio.Media.IS_RINGTONE, type == RingtoneManager.TYPE_RINGTONE);
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION,
                type == RingtoneManager.TYPE_NOTIFICATION);
        values.put(MediaStore.Audio.Media.IS_ALARM, type == RingtoneManager.TYPE_ALARM);
        values.put(MediaStore.Audio.Media.IS_MUSIC, false);

        Uri uri = MediaStore.Audio.Media.getContentUriForPath(path);
        Uri newUri = null;
        Cursor c = context.getContentResolver().query(uri,
                new String[] {MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DATA + "='" + path + "'",
                null, null);
        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            long id = c.getLong(0);
            c.close();
            newUri = Uri.withAppendedPath(Uri.parse(MEDIA_CONTENT_URI), "" + id);
            context.getContentResolver().update(uri, values,
                    MediaStore.MediaColumns._ID + "=" + id, null);
        }
        if (newUri == null)
            newUri = context.getContentResolver().insert(uri, values);
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, newUri);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static boolean setDefaultAudible(Context context, int type) {
        final String audiblePath = getDefaultAudiblePath(type);
        if (audiblePath != null) {
            Uri uri = MediaStore.Audio.Media.getContentUriForPath(audiblePath);
            Cursor c = context.getContentResolver().query(uri,
                    new String[] {MediaStore.MediaColumns._ID},
                    MediaStore.MediaColumns.DATA + "='" + audiblePath + "'",
                    null, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                long id = c.getLong(0);
                c.close();
                uri = Uri.withAppendedPath(
                        Uri.parse(MEDIA_CONTENT_URI), "" + id);
            }
            if (uri != null)
                RingtoneManager.setActualDefaultRingtoneUri(context, type, uri);
        } else {
            return false;
        }
        return true;
    }

    public static String getDefaultAudiblePath(int type) {
        final String name;
        final String path;
        switch (type) {
            case RingtoneManager.TYPE_ALARM:
                name = SystemProperties.get("ro.config.alarm_alert", null);
                path = name != null ? SYSTEM_ALARMS_PATH + File.separator + name : null;
                break;
            case RingtoneManager.TYPE_NOTIFICATION:
                name = SystemProperties.get("ro.config.notification_sound", null);
                path = name != null ? SYSTEM_NOTIFICATIONS_PATH + File.separator + name : null;
                break;
            case RingtoneManager.TYPE_RINGTONE:
                name = SystemProperties.get("ro.config.ringtone", null);
                path = name != null ? SYSTEM_RINGTONES_PATH + File.separator + name : null;
                break;
            default:
                path = null;
                break;
        }
        return path;
    }

    public static void clearAudibles(Context context, String audiblePath) {
        final File audibleDir = new File(audiblePath);
        if (audibleDir.exists()) {
            String[] files = audibleDir.list();
            final ContentResolver resolver = context.getContentResolver();
            for (String s : files) {
                final String filePath = audiblePath + File.separator + s;
                Uri uri = MediaStore.Audio.Media.getContentUriForPath(filePath);
                resolver.delete(uri, MediaStore.MediaColumns.DATA + "=\""
                        + filePath + "\"", null);
                (new File(filePath)).delete();
            }
        }
    }

    public static Context createUiContext(final Context context) {
        try {
            Context uiContext = context.createPackageContext("com.android.systemui",
                    Context.CONTEXT_RESTRICTED);
            return new ThemedUiContext(uiContext, context.getApplicationContext());
        } catch (PackageManager.NameNotFoundException e) {
        }

        return null;
    }

    public static void registerThemeChangeReceiver(final Context context,
            final BroadcastReceiver receiver) {
        IntentFilter filter = new IntentFilter(ACTION_THEME_CHANGED);

        context.registerReceiver(receiver, filter);
    }

    public static String getLockscreenWallpaperPath(AssetManager assetManager) throws IOException {
        String[] assets = assetManager.list("lockscreen");
        String asset = getFirstNonEmptyAsset(assets);
        if (asset == null) return null;
        return "lockscreen/" + asset;
    }

    public static String getWallpaperPath(AssetManager assetManager) throws IOException {
        String[] assets = assetManager.list("wallpapers");
        String asset = getFirstNonEmptyAsset(assets);
        if (asset == null) return null;
        return "wallpapers/" + asset;
    }

    // Returns the first non-empty asset name. Empty assets can occur if the APK is built
    // with folders included as zip entries in the APK. Searching for files inside "folderName" via
    // assetManager.list("folderName") can cause these entries to be included as empty strings.
    private static String getFirstNonEmptyAsset(String[] assets) {
        if (assets == null) return null;
        String filename = null;
        for(String asset : assets) {
            if (!asset.isEmpty()) {
                filename = asset;
                break;
            }
        }
        return filename;
    }

    public static String getDefaultThemePackageName(Context context) {
        final String defaultThemePkg = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.DEFAULT_THEME_PACKAGE);
        if (!TextUtils.isEmpty(defaultThemePkg)) {
            PackageManager pm = context.getPackageManager();
            try {
                if (pm.getPackageInfo(defaultThemePkg, 0) != null) {
                    return defaultThemePkg;
                }
            } catch (PackageManager.NameNotFoundException e) {
                // doesn't exist so system will be default
                Log.w(TAG, "Default theme " + defaultThemePkg + " not found", e);
            }
        }

        return SYSTEM_DEFAULT;
    }

    private static class ThemedUiContext extends ContextWrapper {
        private Context mAppContext;

        public ThemedUiContext(Context context, Context appContext) {
            super(context);
            mAppContext = appContext;
        }

        @Override
        public Context getApplicationContext() {
            return mAppContext;
        }

        @Override
        public String getPackageName() {
            return mAppContext.getPackageName();
        }
    }

    // Returns a mutable list of all theme components
    public static List<String> getAllComponents() {
        List<String> components = new ArrayList<String>(9);
        components.add(ThemesColumns.MODIFIES_FONTS);
        components.add(ThemesColumns.MODIFIES_LAUNCHER);
        components.add(ThemesColumns.MODIFIES_ALARMS);
        components.add(ThemesColumns.MODIFIES_BOOT_ANIM);
        components.add(ThemesColumns.MODIFIES_ICONS);
        components.add(ThemesColumns.MODIFIES_LOCKSCREEN);
        components.add(ThemesColumns.MODIFIES_NOTIFICATIONS);
        components.add(ThemesColumns.MODIFIES_OVERLAYS);
        components.add(ThemesColumns.MODIFIES_RINGTONES);
        components.add(ThemesColumns.MODIFIES_STATUS_BAR);
        components.add(ThemesColumns.MODIFIES_NAVIGATION_BAR);
        return components;
    }

    /**
     *  Returns a mutable list of all the theme components supported by a given package
     *  NOTE: This queries the themes content provider. If there isn't a provider installed
     *  or if it is too early in the boot process this method will not work.
     */
    public static List<String> getSupportedComponents(Context context, String pkgName) {
        List<String> supportedComponents = new ArrayList<String>();

        String selection = ThemesContract.ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = new String[]{ pkgName };
        Cursor c = context.getContentResolver().query(ThemesContract.ThemesColumns.CONTENT_URI,
                null, selection, selectionArgs, null);

        if (c != null && c.moveToFirst()) {
            List<String> allComponents = getAllComponents();
            for(String component : allComponents) {
                int index = c.getColumnIndex(component);
                if (c.getInt(index) == 1) {
                    supportedComponents.add(component);
                }
            }
        }
        return supportedComponents;
    }

    /**
     * Get the components from the default theme.  If the default theme is not SYSTEM then any
     * components that are not in the default theme will come from SYSTEM to create a complete
     * component map.
     * @param context
     * @return
     */
    public static Map<String, String> getDefaultComponents(Context context) {
        String defaultThemePkg = getDefaultThemePackageName(context);
        List<String> defaultComponents = null;
        List<String> systemComponents = getSupportedComponents(context, SYSTEM_DEFAULT);
        if (!SYSTEM_DEFAULT.equals(defaultThemePkg)) {
            defaultComponents = getSupportedComponents(context, defaultThemePkg);
        }

        Map<String, String> componentMap = new HashMap<String, String>(systemComponents.size());
        if (defaultComponents != null) {
            for (String component : defaultComponents) {
                componentMap.put(component, defaultThemePkg);
            }
        }
        for (String component : systemComponents) {
            if (!componentMap.containsKey(component)) {
                componentMap.put(component, SYSTEM_DEFAULT);
            }
        }

        return componentMap;
    }

    /**
     * Takes an existing component map and adds any missing components from the default
     * map of components.
     * @param context
     * @param componentMap An existing component map
     */
    public static void completeComponentMap(Context context,
            Map<String, String> componentMap) {
        if (componentMap == null) return;

        Map<String, String> defaultComponents = getDefaultComponents(context);
        for (String component : defaultComponents.keySet()) {
            if (!componentMap.containsKey(component)) {
                componentMap.put(component, defaultComponents.get(component));
            }
        }
    }

    /**
     * Get the boot theme by accessing the settings.db directly instead of using a content resolver.
     * Only use this when the system is starting up and the settings content provider is not ready.
     *
     * Note: This method will only succeed if the system is calling this since normal apps will not
     * be able to access the settings db path.
     *
     * @return The boot theme or null if unable to read the database or get the entry for theme
     *         config
     */
    public static ThemeConfig getBootThemeDirty() {
        ThemeConfig config = null;
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(SETTINGS_DB, null,
                    SQLiteDatabase.OPEN_READONLY);
            if (db != null) {
                String selection = "name=?";
                String[] selectionArgs =
                        { Configuration.THEME_PKG_CONFIGURATION_PERSISTENCE_PROPERTY };
                String[] columns = {"value"};
                Cursor c = db.query(SETTINGS_SECURE_TABLE, columns, selection, selectionArgs,
                        null, null, null);
                if (c != null) {
                    if (c.getCount() > 0) {
                        c.moveToFirst();
                        String json = c.getString(0);
                        if (json != null) {
                            config = ThemeConfig.fromJson(json);
                        }
                    }
                    c.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to open " + SETTINGS_DB, e);
        } finally {
            if (db != null) {
                db.close();
            }
        }

        return config;
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
}
