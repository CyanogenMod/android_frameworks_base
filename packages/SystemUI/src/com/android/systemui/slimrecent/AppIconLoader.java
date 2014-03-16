/*
 * Copyright (C) 2014 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.slimrecent;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.os.Process;

import com.android.systemui.R;

import java.lang.ref.WeakReference;

/**
 * This class handles async app icon load for the requested apps
 * and put them when sucessfull into the LRU cache.
 *
 * Compared to the task screenshots this class is laid out due
 * that the #link:CacheController can request an app icon as well
 * eg if the app was updated and may changed the icon.
 */
public class AppIconLoader {

    /**
     * Singleton.
     */
    private static AppIconLoader sInstance;

    private Context mContext;

    /**
     * Get the instance.
     */
    public static AppIconLoader getInstance(Context context) {
        if (sInstance != null) {
            return sInstance;
        } else {
            return sInstance = new AppIconLoader(context);
        }
    }

    /**
     * Constructor.
     */
    private AppIconLoader(Context context) {
        mContext = context;
    }

    /**
     * Load the app icon via async task.
     *
     * @params packageName
     * @params imageView
     */
    protected void loadAppIcon(ResolveInfo info, String identifier,
            RecentImageView imageView, float scaleFactor) {
        final BitmapDownloaderTask task =
                new BitmapDownloaderTask(imageView, mContext, scaleFactor, identifier);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, info);
    }

    /**
     * Loads the actual app icon.
     */
    private static Bitmap getAppIcon(ResolveInfo info, Context context, float scaleFactor) {
        if (context == null) {
            return null;
        }
        PackageManager pm = context.getPackageManager();
        return getResizedBitmap(getFullResIcon(context, info, pm), context, scaleFactor);

    }

    private static Drawable getFullResDefaultActivityIcon(Context context) {
        return getFullResIcon(context, Resources.getSystem(),
                com.android.internal.R.mipmap.sym_def_app_icon);
    }

    private static Drawable getFullResIcon(Context context, Resources resources, int iconId) {
        try {
            return resources.getDrawableForDensity(iconId,
                    context.getResources().getDisplayMetrics().densityDpi);
        } catch (Resources.NotFoundException e) {
            return getFullResDefaultActivityIcon(context);
        }
    }

    private static Drawable getFullResIcon(Context context,
            ResolveInfo info, PackageManager packageManager) {
        Resources resources;
        try {
            resources = packageManager.getResourcesForApplication(
                    info.activityInfo.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.activityInfo.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(context, resources, iconId);
            }
        }
        return getFullResDefaultActivityIcon(context);
    }

    /**
     * Resize the app icon to the size we need to save space in our LRU cache.
     * Normal we could assume that all app icons have the same default AOSP defined size.
     * The reality shows that a lot apps do not care about and add just one big icon for
     * all screen resolution.
     */
    private static Bitmap getResizedBitmap(Drawable source, Context context, float scaleFactor) {
        if (source == null) {
            return null;
        }

        final int iconSize = (int) (context.getResources()
                .getDimensionPixelSize(R.dimen.recent_app_icon_size) * scaleFactor);

        final Bitmap bitmap = ((BitmapDrawable) source).getBitmap();
        final Bitmap scaledBitmap = Bitmap.createBitmap(iconSize, iconSize, Config.ARGB_8888);

        final float ratioX = iconSize / (float) bitmap.getWidth();
        final float ratioY = iconSize / (float) bitmap.getHeight();
        final float middleX = iconSize / 2.0f;
        final float middleY = iconSize / 2.0f;

        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);

        final Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        final Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bitmap, middleX - bitmap.getWidth() / 2,
                middleY - bitmap.getHeight() / 2, paint);

        return scaledBitmap;
    }

    /**
     * AsyncTask loader for the app icon.
     */
    private static class BitmapDownloaderTask extends AsyncTask<ResolveInfo, Void, Bitmap> {

        private Bitmap mAppIcon;

        private final WeakReference<RecentImageView> rImageViewReference;
        private final WeakReference<Context> rContext;

        private int mOrigPri;
        private float mScaleFactor;

        private String mLRUCacheKey;

        public BitmapDownloaderTask(RecentImageView imageView,
                Context context, float scaleFactor, String identifier) {
            rImageViewReference = new WeakReference<RecentImageView>(imageView);
            rContext = new WeakReference<Context>(context);
            mScaleFactor = scaleFactor;
            mLRUCacheKey = identifier;
        }

        @Override
        protected Bitmap doInBackground(ResolveInfo... params) {
            // Save current thread priority and set it during the loading
            // to background priority.
            mOrigPri = Process.getThreadPriority(Process.myTid());
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (isCancelled() || rContext == null) {
                return null;
            }
            // Load and return bitmap
            return getAppIcon(params[0], rContext.get(), mScaleFactor);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }
            // Restore original thread priority.
            Process.setThreadPriority(mOrigPri);

            final Context context;
            if (rContext != null) {
                context = rContext.get();
            } else {
                context = null;
            }
            // Assign image to the view if the view was passed through.
            // #link:loadAppIcon
            if (rImageViewReference != null) {
                final RecentImageView imageView = rImageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                }
                if (bitmap != null && context != null) {
                    // Put our bitmap intu LRU cache for later use.
                    CacheController.getInstance(context)
                            .addBitmapToMemoryCache(mLRUCacheKey, bitmap);
                }
            }
        }
    }

}
