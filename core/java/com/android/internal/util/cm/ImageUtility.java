/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
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

package com.android.internal.util.cm;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.provider.ThemesContract;
import android.provider.ThemesContract.ThemesColumns;
import android.util.Log;
import android.webkit.URLUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import libcore.io.IoUtils;

public class ImageUtility {
    private static final String TAG = "ImageUtility";
    protected static final int DEFAULT_IMG_QUALITY = 90;

    public static Point getImageDemision(InputStream inputStream) {
        Point point = new Point(0, 0);
        if (inputStream == null) {
            Log.d(TAG, "inputStream is null");
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        point.x = options.outWidth;
        point.y = options.outHeight;
        return point;
    }

    public static InputStream cropImage(InputStream inputStream, int imageWidth,
                                        int imageHeight, int outWidth, int outHeight) {
        if (inputStream == null || imageWidth <= 0 || imageHeight <= 0 ||
                outWidth <= 0 || outHeight <= 0) {
            Log.e(TAG, "Invalid parameter");
            return null;
        }
        int scaleDownSampleSize = Math.min(imageWidth / outWidth, imageHeight / outHeight);
        if (scaleDownSampleSize > 0) {
            imageWidth /= scaleDownSampleSize;
            imageHeight /= scaleDownSampleSize;
        } else {
            float ratio = (float) outWidth / outHeight;
            if (imageWidth < imageHeight * ratio) {
                outWidth = imageWidth;
                outHeight = (int) (outWidth / ratio);
            } else {
                outHeight = imageHeight;
                outWidth = (int) (outHeight * ratio);
            }
        }
        int left = (imageWidth - outWidth) / 2;
        int top = (imageHeight - outHeight) / 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        if (scaleDownSampleSize > 1)
            options.inSampleSize = scaleDownSampleSize;
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
        if (bitmap == null)
            return null;
        Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, outWidth, outHeight);
        InputStream compressed = null;
        ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
        if (cropped.compress(Bitmap.CompressFormat.JPEG, DEFAULT_IMG_QUALITY, tmpOut)) {
            byte[] outByteArray = tmpOut.toByteArray();
            compressed = new ByteArrayInputStream(outByteArray);
        }
        return compressed;
    }

    public static InputStream getCroppedKeyguardStream(String pkgName, Context context) {
        InputStream stream = getOriginalKeyguardStream(pkgName, context);
        if (stream == null)
            return null;
        Point point = getImageDemision(stream);
        IoUtils.closeQuietly(stream);
        WallpaperManager wm = WallpaperManager.getInstance(context);
        int outWidth = wm.getDesiredMinimumWidth();
        int outHeight = wm.getDesiredMinimumHeight();
        stream = getOriginalKeyguardStream(pkgName, context);
        if (stream == null)
            return null;
        InputStream cropped = cropImage(stream, point.x, point.y, outWidth, outHeight);
        IoUtils.closeQuietly(stream);
        return cropped;
    }

    public static InputStream getCroppedWallpaperStream(String pkgName, Context context) {
        InputStream stream = getOrigianlWallpaperStream(pkgName, context);
        if (stream == null)
            return null;
        Point point = getImageDemision(stream);
        IoUtils.closeQuietly(stream);
        WallpaperManager wm = WallpaperManager.getInstance(context);
        int outWidth = wm.getDesiredMinimumWidth();
        int outHeight = wm.getDesiredMinimumHeight();
        stream = getOrigianlWallpaperStream(pkgName, context);
        if (stream == null)
            return null;
        InputStream cropped = cropImage(stream, point.x, point.y, outWidth, outHeight);
        IoUtils.closeQuietly(stream);
        return cropped;
    }

    private static InputStream getOriginalKeyguardStream(String pkgName, Context mContext) {
        InputStream inputStream = null;

        try {
            //Get input WP stream from the theme
            Context themeCtx = mContext.createPackageContext(pkgName,
                    Context.CONTEXT_IGNORE_SECURITY);
            AssetManager assetManager = themeCtx.getAssets();
            String wpPath = ThemeUtils.getLockscreenWallpaperPath(assetManager);
            if (wpPath == null)
                Log.w(TAG, "Not setting lockscreen wp because wallpaper file was not found.");
            else
                inputStream = ThemeUtils.getInputStreamFromAsset(themeCtx,
                        "file:///android_asset/" + wpPath);
        } catch (Exception e) {
            Log.e(TAG, "There was an error setting lockscreen wp for pkg " + pkgName, e);
        }
        return inputStream;
    }

    private static InputStream getOrigianlWallpaperStream(String pkgName, Context mContext) {
        InputStream inputStream = null;
        String selection = ThemesContract.ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = {pkgName};
        Cursor c = mContext.getContentResolver().query(ThemesColumns.CONTENT_URI,
                null, selection,
                selectionArgs, null);
        c.moveToFirst();

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
                        inputStream = ThemeUtils.getInputStreamFromAsset(themeContext, wallpaper);
                    } else {
                        inputStream = mContext.getContentResolver().openInputStream(
                                Uri.parse(wallpaper));
                    }
                } else {
                    // try and get the wallpaper directly from the apk if the URI was null
                    Context themeCtx = mContext.createPackageContext(pkgName,
                            Context.CONTEXT_IGNORE_SECURITY);
                    AssetManager assetManager = themeCtx.getAssets();
                    String wpPath = ThemeUtils.getWallpaperPath(assetManager);
                    if (wpPath == null)
                        Log.e(TAG, "Not setting wp because wallpaper file was not found.");
                    else
                        inputStream = ThemeUtils.getInputStreamFromAsset(themeCtx,
                                "file:///android_asset/" + wpPath);
                }
            } else {
                Resources resources = mContext.getResources();
                PackageInfo pi = mContext.getPackageManager().getPackageInfo(pkgName, 0);

                if (pi.legacyThemeInfos != null && pi.legacyThemeInfos.length > 0) {
                    inputStream =
                            resources.openRawResource(pi.legacyThemeInfos[0].wallpaperResourceId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getWallpaperStream: " + e);
        } finally {
            c.close();
        }

        return inputStream;

    }
}

