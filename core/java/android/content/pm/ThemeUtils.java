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
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.FileUtils;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.provider.Settings;
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
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static android.content.res.CustomTheme.HOLO_DEFAULT;

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
    public static final String RESTABLE_EXTENSION = ".arsc";
    public static final String IDMAP_PREFIX = "/data/resource-cache/";
    public static final String IDMAP_SUFFIX = "@idmap";
    public static final String COMMON_RES_SUFFIX = ".common";
    public static final String COMMON_RES_TARGET = "common";

    // path to external theme resources, i.e. bootanimation.zip
    public static final String SYSTEM_THEME_PATH = "/data/system/theme";
    public static final String SYSTEM_THEME_FONT_PATH = SYSTEM_THEME_PATH + File.separator + "fonts";
    public static final String SYSTEM_THEME_RINGTONE_PATH = SYSTEM_THEME_PATH
            + File.separator + "ringtones";
    public static final String SYSTEM_THEME_NOTIFICATION_PATH = SYSTEM_THEME_PATH
            + File.separator + "notifications";
    public static final String SYSTEM_THEME_ALARM_PATH = SYSTEM_THEME_PATH
            + File.separator + "alarms";
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

    public static final String ACTION_THEME_CHANGED = "org.cyanogenmod.intent.action.THEME_CHANGED";

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


    /*
     * Retrieve the path to a resource table (ie resource.arsc)
     * Themes have a resources.arsc for every overlay package targeted. These are compiled
     * at install time and stored in the data partition.
     *
     */
    public static String getResTablePath(String targetPkgName, PackageInfo overlayPkg) {
        return getResTablePath(targetPkgName, overlayPkg.applicationInfo.publicSourceDir);
    }

    public static String getResTablePath(String targetPkgName, PackageParser.Package overlayPkg) {
        return getResTablePath(targetPkgName, overlayPkg.applicationInfo.publicSourceDir);
    }

    public static String getResTablePath(String targetPkgName, String overlayApkPath) {
        String restablePath = getResDir(targetPkgName, overlayApkPath) + "/resources.arsc";
        return restablePath;
    }

    /*
     * Retrieve the path to the directory where resource table (ie resource.arsc) resides
     * Themes have a resources.arsc for every overlay package targeted. These are compiled
     * at install time and stored in the data partition.
     *
     */
    public static String getResDir(String targetPkgName, PackageInfo overlayPkg) {
        return getResDir(targetPkgName, overlayPkg.applicationInfo.publicSourceDir);
    }

    public static String getResDir(String targetPkgName, PackageParser.Package overlayPkg) {
        return getResDir(targetPkgName, overlayPkg.applicationInfo.publicSourceDir);
    }

    public static String getResDir(String targetPkgName, String overlayApkPath) {
        String restableName = overlayApkPath.replaceAll("/", "@") + "@" + targetPkgName;
        if (restableName.startsWith("@")) restableName = restableName.substring(1);
        return IDMAP_PREFIX + restableName;
    }

    public static String getIconPackDir(String pkgName) {
      return IDMAP_PREFIX + pkgName;
    }

    public static String getIconPackApkPath(String pkgName) {
        return getIconPackDir(pkgName) + "/resources.apk";
    }

    public static String getIconPackResPath(String pkgName) {
        return getIconPackDir(pkgName) + "/resources.arsc";
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
        File file = new File(IDMAP_PREFIX);
        if (!file.exists() && !file.mkdir()) {
            throw new IOException("Could not create dir: " + file.toString());
        }
        FileUtils.setPermissions(file, FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
    }

    public static void createResourcesDirIfNotExists(String targetPkgName, String overlayApkPath)
            throws IOException {
        File file = new File(getResDir(targetPkgName, overlayApkPath));
        if (!file.exists() && !file.mkdir()) {
            throw new IOException("Could not create dir: " + file.toString());
        }
        FileUtils.setPermissions(file, FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
    }

    public static void createIconDirIfNotExists(String pkgName) throws IOException {
        File file = new File(getIconPackDir(pkgName));
        if (!file.exists() && !file.mkdir()) {
            throw new IOException("Could not create dir: " + file.toString());
        }
        FileUtils.setPermissions(file, FileUtils.S_IRWXU | FileUtils.S_IRWXG | FileUtils.S_IROTH | FileUtils.S_IXOTH, -1, -1);
    }

    private static boolean dirExists(String dirPath) {
        final File dir = new File(dirPath);
        return dir.exists() && dir.isDirectory();
    }

    private static void createDirIfNotExists(String dirPath) {
        if (!dirExists(dirPath)) {
            File dir = new File(dirPath);
            if (dir.mkdir()) {
                FileUtils.setPermissions(dir,
                        FileUtils.S_IRWXU|FileUtils.S_IRWXG|FileUtils.S_IROTH|FileUtils.S_IXOTH, -1, -1);
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

    //Note: will not delete populated subdirs
    public static void deleteFilesInDir(String dirPath) {
        File fontDir = new File(dirPath);
        File[] files = fontDir.listFiles();
        if (files != null) {
            for(File file : fontDir.listFiles()) {
                file.delete();
            }
        }
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
        values.put(MediaStore.Audio.Media.IS_NOTIFICATION, type == RingtoneManager.TYPE_NOTIFICATION);
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
            context.getContentResolver().update(uri, values, MediaStore.MediaColumns._ID + "=" + id, null);
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
            return new ThemedUiContext(uiContext, context.getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
        }

        return null;
    }

    public static void registerThemeChangeReceiver(final Context context, final BroadcastReceiver receiver) {
        IntentFilter filter = new IntentFilter(ACTION_THEME_CHANGED);

        context.registerReceiver(receiver, filter);
    }

    public static String getLockscreenWallpaperPath(AssetManager assetManager) throws IOException {
        final String WALLPAPER_JPG = "wallpaper.jpg";
        final String WALLPAPER_PNG = "wallpaper.png";

        String[] assets = assetManager.list("lockscreen");
        if (assets == null || assets.length == 0) return null;
        for (String asset : assets) {
            if (WALLPAPER_JPG.equals(asset)) {
                return "lockscreen/" + WALLPAPER_JPG;
            } else if (WALLPAPER_PNG.equals(asset)) {
                return "lockscreen/" + WALLPAPER_PNG;
            }
        }
        return null;
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
                // doesn't exist so holo will be default
                Log.w(TAG, "Default theme " + defaultThemePkg + " not found", e);
            }
        }

        return HOLO_DEFAULT;
    }

    private static class ThemedUiContext extends ContextWrapper {
        private String mPackageName;

        public ThemedUiContext(Context context, String packageName) {
            super(context);
            mPackageName = packageName;
        }

        @Override
        public String getPackageName() {
            return mPackageName;
        }
    }
}
