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

import android.content.ContentResolver;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.Settings;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;

import com.android.systemui.R;

import java.util.ArrayList;

/**
 * This class is our LRU cache controller. It holds
 * the app icons and the task screenshots.
 *
 * BroadcastReceiver takes care of the situation if the user updated
 * or removed and installed again the app and the icon may have changed.
 */
public class CacheController {

    private final static String TAG = "RecentCacheController";

    /**
     * Singleton.
     */
    private static CacheController sInstance;

    /**
     * Memory Cache.
     */
    protected LruCache<String, Bitmap> mMemoryCache;

    private Context mContext;

    private static String sKeyExcludeRecycle;
    private static boolean sRecentScreenShowing;

    // Array list of all current keys.
    private final ArrayList<String> mKeys = new ArrayList<String>();

    /**
     * Get the instance.
     */
    public static CacheController getInstance(Context context) {
        if (sInstance != null) {
            return sInstance;
        } else {
            return sInstance = new CacheController(context);
        }
    }

    /**
     * Listen for package change or added broadcast.
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                    || Intent.ACTION_PACKAGE_ADDED.equals(action)
                    || Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                // Get the package name from the intent.
                Uri uri = intent.getData();
                final String packageName = uri != null ? uri.getSchemeSpecificPart() : null;
                if (packageName == null) {
                    return;
                }

                // Check if icons from the searched package are present.
                // If yes remove them.
                final ArrayList<String> keysToRemove = new ArrayList<String>();
                for (String key : mKeys) {
                    if (key.toLowerCase().contains(packageName.toLowerCase())) {
                        keysToRemove.add(key);
                    }
                }
                for (String key : keysToRemove) {
                    Log.d(TAG, "application icon removed for uri= " + key);
                    setKeyExcludeRecycle(key);
                    removeBitmapFromMemCache(key);
                }
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    mayBeRemoveFavoriteEntry(packageName);
                }
            }
        }
    };

    /**
     * Remove favorite if current app was uninstalled.
     */
    private void mayBeRemoveFavoriteEntry(String packageName) {
        ContentResolver resolver = mContext.getContentResolver();
        final String favorites = Settings.System.getStringForUser(
                    resolver, Settings.System.RECENT_PANEL_FAVORITES,
                    UserHandle.USER_CURRENT);
        String entryToSave = "";

        if (favorites == null || favorites != null && favorites.isEmpty()) {
            return;
        }
        for (String favorite : favorites.split("\\|")) {
            if (favorite.toLowerCase().contains(packageName.toLowerCase())) {
                continue;
            }
            entryToSave += favorite + "|";
        }
        if (!entryToSave.isEmpty()) {
            entryToSave = entryToSave.substring(0, entryToSave.length() - 1);
        }

        Settings.System.putStringForUser(
                resolver, Settings.System.RECENT_PANEL_FAVORITES,
                entryToSave,
                UserHandle.USER_CURRENT);
    }

    /**
     * Constructor.
     * Defines the LRU cache size and setup the broadcast receiver.
     */
    private CacheController(Context context) {
        mContext = context;

        final Resources res = context.getResources();

        // Gets the dimensions of the device's screen
        DisplayMetrics dm = res.getDisplayMetrics();
        final int screenWidth = dm.widthPixels;
        final int screenHeight = dm.heightPixels;

        // We have ARGB_8888 pixel format, 4 bytes per pixel
        final int size = screenWidth * screenHeight * 4;

        // Calculate how much thumbnails we can put per screen page
        final int thumbnailWidth = res.getDimensionPixelSize(R.dimen.recent_thumbnail_width);
        final int thumbnailHeight = res.getDimensionPixelSize(R.dimen.recent_thumbnail_height);
        final float thumbnailsPerPage =
                (screenWidth / thumbnailWidth) * (screenHeight / thumbnailHeight);

        // Needed screen pages for max thumbnails we can get.
        float neededPages = RecentPanelView.MAX_TASKS / thumbnailsPerPage;

        // Calculate how much app icons we can put per screen page
        final int iconSize = res.getDimensionPixelSize(R.dimen.recent_app_icon_size);
        final float iconsPerPage = (screenWidth / iconSize) * (screenHeight / iconSize);

        // Needed screen pages for max thumbnails and max app icons we can get.
        neededPages += RecentPanelView.MAX_TASKS / iconsPerPage;

        // Calculate final cache size, stored in kilobytes.
        int cacheSize = (int) (size * neededPages / 1024);

        // Get max available VM memory, exceeding this amount will throw an
        // OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Do not allow more then 1/8 from max available memory.
        if (cacheSize > maxMemory / 8) {
            cacheSize = maxMemory / 8;
        }

        if (mMemoryCache == null) {
            mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    return bitmap.getByteCount() / 1024;
                }

                @Override
                protected void entryRemoved(boolean evicted, String key,
                        Bitmap oldBitmap, Bitmap newBitmap) {
                    /**
                     * For normal LRU cache designs this is not a valid approach.
                     * Well in our case it is another case. The LRU cache control
                     * was designed in this way that we can be 100% sure if a put(K, V)
                     * or a remove(V) was called that we do not have any reference to
                     * this bitmap anymore. So it can be savely recycled.
                     * Cases are:
                     * 1. New bitmap was put into the LRU cache. Shortly before the loaders
                     *    assign the new bitmap to the imageview. So old one has no reference.
                     * 2. Task entry was removed which removes as well any reference to the bitmap.
                     *    So we are save here as well.
                     * 3. The CacheController broadcastreceiver removes the bitmap from
                     *    the LRU cache. When this happens we recycle only if the recent screen
                     *    is not shown due that we may have a valid reference. This scenario
                     *    is realy realy rare. So we are save and can recycle in most of the cases
                     *    if an app was updated by the user. So we do not need to worry to produce
                     *    a memory leak here at all.
                     * 4. The case that the entry was evicted. Here we do not recycle the old
                     *    image. Well this case should never happen due that our LRU cache is
                     *    exactly meassured to keep all tasks in memory.
                     *    Just in case we still check for it.
                     */
                    if (!evicted) {
                        if (key != null && key.equals(getKeyExcludeRecycle())) {
                            setKeyExcludeRecycle(null);
                            if (isRecentScreenShowing()) {
                                return;
                            }
                        }
                        oldBitmap.recycle();
                        oldBitmap = null;
                    }
                }
            };
        }

        // Receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Add the bitmap to the LRU cache.
     */
    protected void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (key != null && bitmap != null) {
            if (key.startsWith(RecentPanelView.TASK_PACKAGE_IDENTIFIER)) {
                mKeys.add(key);
            }
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * Get the bitmap from the LRU cache.
     */
    protected Bitmap getBitmapFromMemCache(String key) {
        if (key == null) {
            return null;
        }
        return mMemoryCache.get(key);
    }

    /**
     * Remove a bitmap from the LRU cache.
     */
    protected Bitmap removeBitmapFromMemCache(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith(RecentPanelView.TASK_PACKAGE_IDENTIFIER)) {
            mKeys.remove(key);
        }
        return mMemoryCache.remove(key);
    }

    /**
     * Set key which should be excluded from recycle if recent panel is showing.
     * Call by BroadCastReceiver.
     */
    protected void setKeyExcludeRecycle(String key) {
        sKeyExcludeRecycle = key;
    }

    /**
     * Get key which should be excluded from recycle.
     */
    private static String getKeyExcludeRecycle() {
        return sKeyExcludeRecycle;
    }

    /**
     * Set wether recent screen is showing. Call from RecentController.
     */
    protected void setRecentScreenShowing(boolean showing) {
        sRecentScreenShowing = showing;
    }

    /**
     * Wether recent screen is showing.
     */
    private static boolean isRecentScreenShowing() {
        return sRecentScreenShowing;
    }

}
