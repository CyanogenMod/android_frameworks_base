/*
 * Copyright (C) 2015 The CyanogenMod Open Source Project
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
 * limitations under the License
 */

package com.android.internal.util.cm;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DynamicQSUtils {
    private static boolean sAvailableTilesFiltered;

    private static final SparseArray<Context> sSystemUiContextForUser = new SparseArray<>();

    public interface OnDynamicQSChanged {
        void onDynamicQSChanged();
    }

    private DynamicQSUtils() {}

    public static List<String> getAvailableTiles(Context context) {
        filterTiles(context);
        return QSConstants.DYNAMIC_TILES_AVAILABLE;
    }

    public static List<String> getDefaultTiles(Context context) {
        final List<String> tiles = new ArrayList<>();
        final String defaults = context.getString(
                com.android.internal.R.string.config_defaultDynamicQuickSettingsTiles);
        if (!TextUtils.isEmpty(defaults)) {
            final String[] array = TextUtils.split(defaults, Pattern.quote(","));
            for (String item : array) {
                if (TextUtils.isEmpty(item)) {
                    continue;
                }
                tiles.add(item);
            }
            filterTiles(context, tiles);
        }
        return tiles;
    }

    public static String getDefaultTilesAsString(Context context) {
        List<String> list = getDefaultTiles(context);
        return TextUtils.join(",", list);
    }

    private static void filterTiles(Context context, List<String> tiles) {
        // Add logic if a dynamic custom tile requires filtering
    }

    private static void filterTiles(Context context) {
        if (!sAvailableTilesFiltered) {
            filterTiles(context, QSConstants.DYNAMIC_TILES_AVAILABLE);
            sAvailableTilesFiltered = true;
        }
    }

    public static int getQSTileResIconId(Context context, int userId, String tileSpec) {
        Context ctx = getDynamicQSTileContext(context, userId);
        int index = translateTileSpecToIndex(ctx, tileSpec);
        if (index == -1) {
            return 0;
        }

        try {
            String resourceName = ctx.getResources().getStringArray(
                    ctx.getResources().getIdentifier("dynamic_qs_tiles_icons_resources_ids",
                            "array", ctx.getPackageName()))[index];
            return ctx.getResources().getIdentifier(
                    resourceName, "drawable", ctx.getPackageName());
        } catch (Exception ex) {
            // Ignore
        }
        return 0;
    }

    public static String getQSTileLabel(Context context, int userId, String tileSpec) {
        Context ctx = getDynamicQSTileContext(context, userId);
        int index = translateTileSpecToIndex(ctx, tileSpec);
        if (index == -1) {
            return null;
        }

        try {
            return ctx.getResources().getStringArray(
                    ctx.getResources().getIdentifier("dynamic_qs_tiles_labels",
                            "array", ctx.getPackageName()))[index];
        } catch (Exception ex) {
            // Ignore
        }
        return null;
    }

    private static int translateTileSpecToIndex(Context context, String tileSpec) {
        String[] keys = context.getResources().getStringArray(context.getResources().getIdentifier(
                "dynamic_qs_tiles_values", "array", context.getPackageName()));
        int count = keys.length -1;
        for (int i = 0; i < count; i++) {
            if (keys[i].equals(tileSpec)) {
                return i;
            }
        }
        return -1;
    }

    public static Context getDynamicQSTileContext(Context context, int userId) {
        Context ctx = sSystemUiContextForUser.get(userId);
        if (ctx == null) {
            try {
                ctx = context.createPackageContextAsUser(
                        "com.android.systemui", 0, new UserHandle(userId));
                sSystemUiContextForUser.put(userId, ctx);
            } catch (NameNotFoundException ex) {
                // We can safely ignore this
            }
        }
        return ctx;
    }

    public static boolean isDynamicQSTileEnabledForUser(
            Context context, String tileSpec, int userId) {
        final ContentResolver resolver = context.getContentResolver();
        String order = Settings.Secure.getStringForUser(resolver,
                Settings.Secure.QS_DYNAMIC_TILES, userId);
        if (order == null) {
            order = DynamicQSUtils.getDefaultTilesAsString(context);
            Settings.Secure.putStringForUser(resolver, Settings.Secure.QS_DYNAMIC_TILES,
                    order, userId);
        }
        return Arrays.asList(order.split(",")).contains(tileSpec);
    }

    public static ContentObserver registerForDynamicQSSettingsChanges(
            Context context, final OnDynamicQSChanged cb) {
        ContentObserver observer = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                cb.onDynamicQSChanged();
            }
        };

        context.getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.QS_DYNAMIC_TILES),
                false, observer, UserHandle.USER_ALL);
        return observer;
    }

    public static void unregisterForDynamicQSSettingsChanges(
            Context context, ContentObserver observer) {
        context.getContentResolver().unregisterContentObserver(observer);
    }
}
