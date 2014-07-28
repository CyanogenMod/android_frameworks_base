/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.wallpapercropper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class Utilities {
    private static final String TAG = "WallpaperCropper.Utilities";

    public static void scaleRect(Rect r, float scale) {
        if (scale != 1.0f) {
            r.left = (int) (r.left * scale + 0.5f);
            r.top = (int) (r.top * scale + 0.5f);
            r.right = (int) (r.right * scale + 0.5f);
            r.bottom = (int) (r.bottom * scale + 0.5f);
        }
    }

    public static void scaleRectAboutCenter(Rect r, float scale) {
        int cx = r.centerX();
        int cy = r.centerY();
        r.offset(-cx, -cy);
        Utilities.scaleRect(r, scale);
        r.offset(cx, cy);
    }

    public static void startActivityForResultSafely(
            Activity activity, Intent intent, int requestCode) {
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    public static Bitmap getThemeWallpaper(Context context, String path, String pkgName,
            boolean legacyTheme, boolean thumb) {
        if (legacyTheme) {
            return getLegacyThemeWallpaper(context, pkgName, thumb);
        }
        InputStream is = null;
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(pkgName);
            if (res == null) {
                return null;
            }

            AssetManager am = res.getAssets();
            String[] wallpapers = am.list(path);
            String wallpaper = getFirstNonEmptyString(wallpapers);
            if (wallpaper == null) {
                return null;
            }


            is = am.open(path + File.separator + wallpaper);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, bounds);
            if ((bounds.outWidth == -1) || (bounds.outHeight == -1))
                return null;

            int originalSize = (bounds.outHeight > bounds.outWidth) ? bounds.outHeight
                    : bounds.outWidth;
            Point outSize;

            if (thumb) {
                outSize = getDefaultThumbnailSize(context.getResources());
            } else {
                outSize = WallpaperCropActivity.getDefaultWallpaperSize(res,
                        ((Activity) context).getWindowManager());
            }
            int thumbSampleSize = (outSize.y > outSize.x) ? outSize.y : outSize.x;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = originalSize / thumbSampleSize;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (IOException e) {
            return null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static String getFirstNonEmptyString(String[] strings) {
        if (strings == null) return null;
        String firstNonEmptyString = null;
        for(String astring : strings) {
            if (!astring.isEmpty()) {
                firstNonEmptyString = astring;
                break;
            }
        }
        return firstNonEmptyString;
    }

    public static Bitmap getLegacyThemeWallpaper(Context context, String pkgName, boolean thumb) {
        try {
            final PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkgName, 0);
            Resources res = context.getPackageManager().getResourcesForApplication(pkgName);

            if (pi == null || res == null) {
                return null;
            }
            int resId = pi.legacyThemeInfos[0].wallpaperResourceId;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, opts);
            if ((opts.outWidth == -1) || (opts.outHeight == -1))
                return null;

            int originalSize = (opts.outHeight > opts.outWidth) ? opts.outHeight
                    : opts.outWidth;
            Point outSize;
            if (thumb) {
                outSize = getDefaultThumbnailSize(context.getResources());
            } else {
                outSize = WallpaperCropActivity.getDefaultWallpaperSize(res, (
                        (Activity) context).getWindowManager());
            }
            int thumbSampleSize = (outSize.y > outSize.x) ? outSize.y : outSize.x;

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = originalSize / thumbSampleSize;

            return BitmapFactory.decodeResource(res, resId, opts);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        } catch (OutOfMemoryError e1) {
            return null;
        }
    }

    public static Point getDefaultThumbnailSize(Resources res) {
        return new Point(res.getDimensionPixelSize(R.dimen.wallpaperThumbnailWidth),
                res.getDimensionPixelSize(R.dimen.wallpaperThumbnailHeight));

    }
}
