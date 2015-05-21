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
import android.content.pm.ThemeUtils;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.provider.ThemesContract;
import android.provider.ThemesContract.ThemesColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.URLUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import libcore.io.IoUtils;

public class ImageUtils {
    private static final String TAG = ImageUtils.class.getSimpleName();

    private static final String ASSET_URI_PREFIX = "file:///android_asset/";
    private static final int DEFAULT_IMG_QUALITY = 100;

    /**
     * Gets the Width and Height of the image
     *
     * @param inputStream The input stream of the image
     *
     * @return A point structure that holds the Width and Height (x and y)/*"
     */
    public static Point getImageDimension(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("'inputStream' cannot be null!");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, options);
        Point point = new Point(options.outWidth,options.outHeight);
        return point;
    }

    /**
     * Crops the input image and returns a new InputStream of the cropped area
     *
     * @param inputStream The input stream of the image
     * @param imageWidth Width of the input image
     * @param imageHeight Height of the input image
     * @param inputStream Desired Width
     * @param inputStream Desired Width
     *
     * @return a new InputStream of the cropped area/*"
     */
    public static InputStream cropImage(InputStream inputStream, int imageWidth, int imageHeight,
            int outWidth, int outHeight) throws IllegalArgumentException {
        if (inputStream == null){
            throw new IllegalArgumentException("inputStream cannot be null");
        }

        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException(
                    String.format("imageWidth and imageHeight must be > 0: imageWidth=%d" +
                            " imageHeight=%d", imageWidth, imageHeight));
        }

        if (outWidth <= 0 || outHeight <= 0) {
            throw new IllegalArgumentException(
                    String.format("outWidth and outHeight must be > 0: outWidth=%d" +
                            " outHeight=%d", imageWidth, outHeight));
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
        InputStream compressed = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            if (scaleDownSampleSize > 1) {
                options.inSampleSize = scaleDownSampleSize;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (bitmap == null) {
                return null;
            }
            Bitmap cropped = Bitmap.createBitmap(bitmap, left, top, outWidth, outHeight);
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(2048);
            if (cropped.compress(Bitmap.CompressFormat.PNG, DEFAULT_IMG_QUALITY, tmpOut)) {
                byte[] outByteArray = tmpOut.toByteArray();
                compressed = new ByteArrayInputStream(outByteArray);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        }
        return compressed;
    }

    /**
     * Crops the lock screen image and returns a new InputStream of the cropped area
     *
     * @param pkgName Name of the theme package
     * @param context The context
     *
     * @return a new InputStream of the cropped image/*"
     */
    public static InputStream getCroppedKeyguardStream(String pkgName, Context context)
            throws IllegalArgumentException {
        if (TextUtils.isEmpty(pkgName)) {
            throw new IllegalArgumentException("'pkgName' cannot be null or empty!");
        }
        if (context == null) {
            throw new IllegalArgumentException("'context' cannot be null!");
        }

        InputStream cropped = null;
        InputStream stream = null;
        try {
            stream = getOriginalKeyguardStream(pkgName, context);
            if (stream == null) {
                return null;
            }
            Point point = getImageDimension(stream);
            IoUtils.closeQuietly(stream);
            if (point == null || point.x == 0 || point.y == 0) {
                return null;
            }
            WallpaperManager wm = WallpaperManager.getInstance(context);
            int outWidth = wm.getDesiredMinimumWidth();
            int outHeight = wm.getDesiredMinimumHeight();
            stream = getOriginalKeyguardStream(pkgName, context);
            if (stream == null) {
                return null;
            }
            cropped = cropImage(stream, point.x, point.y, outWidth, outHeight);
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        } finally {
            IoUtils.closeQuietly(stream);
        }
        return cropped;
    }

    /**
     * Crops the wallpaper image and returns a new InputStream of the cropped area
     *
     * @param pkgName Name of the theme package
     * @param context The context
     *
     * @return a new InputStream of the cropped image/*"
     */
    public static InputStream getCroppedWallpaperStream(String pkgName, long wallpaperId,
            Context context) {
        if (TextUtils.isEmpty(pkgName)) {
            throw new IllegalArgumentException("'pkgName' cannot be null or empty!");
        }
        if (context == null) {
            throw new IllegalArgumentException("'context' cannot be null!");
        }

        InputStream cropped = null;
        InputStream stream = null;
        try {
            stream = getOriginalWallpaperStream(pkgName, wallpaperId, context);
            if (stream == null) {
                return null;
            }
            Point point = getImageDimension(stream);
            IoUtils.closeQuietly(stream);
            if (point == null || point.x == 0 || point.y == 0) {
                return null;
            }
            WallpaperManager wm = WallpaperManager.getInstance(context);
            int outWidth = wm.getDesiredMinimumWidth();
            int outHeight = wm.getDesiredMinimumHeight();
            stream = getOriginalWallpaperStream(pkgName, wallpaperId, context);
            if (stream == null) {
                return null;
            }
            cropped = cropImage(stream, point.x, point.y, outWidth, outHeight);
        } catch (Exception e) {
            Log.e(TAG, "Exception " + e);
        } finally {
            IoUtils.closeQuietly(stream);
        }
        return cropped;
    }

    private static InputStream getOriginalKeyguardStream(String pkgName, Context context) {
        if (TextUtils.isEmpty(pkgName) || context == null) {
            return null;
        }

        InputStream inputStream = null;
        try {
            //Get input WP stream from the theme
            Context themeCtx = context.createPackageContext(pkgName,
                    Context.CONTEXT_IGNORE_SECURITY);
            AssetManager assetManager = themeCtx.getAssets();
            String wpPath = ThemeUtils.getLockscreenWallpaperPath(assetManager);
            if (wpPath == null) {
                Log.w(TAG, "Not setting lockscreen wp because wallpaper file was not found.");
            } else {
                inputStream = ThemeUtils.getInputStreamFromAsset(themeCtx,
                        ASSET_URI_PREFIX + wpPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "There was an error setting lockscreen wp for pkg " + pkgName, e);
        }
        return inputStream;
    }

    private static InputStream getOriginalWallpaperStream(String pkgName, long componentId,
            Context context) {
        String wpPath;
        if (TextUtils.isEmpty(pkgName) || context == null) {
            return null;
        }

        InputStream inputStream = null;
        String selection = ThemesContract.ThemesColumns.PKG_NAME + "= ?";
        String[] selectionArgs = {pkgName};
        Cursor c = context.getContentResolver().query(ThemesColumns.CONTENT_URI,
                null, selection,
                selectionArgs, null);
        if (c == null || c.getCount() < 1) {
            if (c != null) c.close();
            return null;
        } else {
            c.moveToFirst();
        }

        try {
            Context themeContext = context.createPackageContext(pkgName,
                    Context.CONTEXT_IGNORE_SECURITY);
            boolean isLegacyTheme = c.getInt(
                    c.getColumnIndex(ThemesColumns.IS_LEGACY_THEME)) == 1;
            String wallpaper = c.getString(
                    c.getColumnIndex(ThemesColumns.WALLPAPER_URI));
            if (wallpaper != null) {
                if (URLUtil.isAssetUrl(wallpaper)) {
                    inputStream = ThemeUtils.getInputStreamFromAsset(themeContext, wallpaper);
                } else {
                    inputStream = context.getContentResolver().openInputStream(
                            Uri.parse(wallpaper));
                }
            } else {
                // try and get the wallpaper directly from the apk if the URI was null
                Context themeCtx = context.createPackageContext(pkgName,
                        Context.CONTEXT_IGNORE_SECURITY);
                AssetManager assetManager = themeCtx.getAssets();
                wpPath = queryWpPathFromComponentId(context, pkgName, componentId);
                if (wpPath == null) wpPath = ThemeUtils.getWallpaperPath(assetManager);
                if (wpPath == null) {
                    Log.e(TAG, "Not setting wp because wallpaper file was not found.");
                } else {
                    inputStream = ThemeUtils.getInputStreamFromAsset(themeCtx,
                            ASSET_URI_PREFIX + wpPath);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getWallpaperStream: " + e);
        } finally {
            c.close();
        }

        return inputStream;
    }

    private static String queryWpPathFromComponentId(Context context, String pkgName,
            long componentId) {
        String wpPath = null;
        String[] projection = new String[] { ThemesContract.PreviewColumns.COL_VALUE };
        String selection = ThemesColumns.PKG_NAME + "=? AND " +
                ThemesContract.PreviewColumns.COMPONENT_ID + "=? AND " +
                ThemesContract.PreviewColumns.COL_KEY + "=?";
        String[] selectionArgs = new String[] {
                pkgName,
                Long.toString(componentId),
                ThemesContract.PreviewColumns.WALLPAPER_FULL
        };

        Cursor c = context.getContentResolver()
                .query(ThemesContract.PreviewColumns.COMPONENTS_URI,
                        projection, selection, selectionArgs, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    int valIdx = c.getColumnIndex(ThemesContract.PreviewColumns.COL_VALUE);
                    wpPath = c.getString(valIdx);
                }
            } catch(Exception e) {
                Log.e(TAG, "Could not get wallpaper path", e);
            } finally {
                c.close();
            }
        }
        return wpPath;
    }
}

