/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.app;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * @hide
 */

public class ThemeUtils {
    private static final String TAG = "ThemeUtils";
    private static final String DATA_TYPE_TMOBILE_STYLE = "vnd.tmobile.cursor.item/style";
    private static final String DATA_TYPE_TMOBILE_THEME = "vnd.tmobile.cursor.item/theme";
    private static final String ACTION_TMOBILE_THEME_CHANGED = "com.tmobile.intent.action.THEME_CHANGED";

    public static Context createUiContext(final Context context) {
        try {
            return context.createPackageContext("com.android.systemui", Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException e) {
        }

        return null;
    }

    public static void registerThemeChangeReceiver(final Context context, final BroadcastReceiver receiver) {
        IntentFilter filter = new IntentFilter(ACTION_TMOBILE_THEME_CHANGED);
        try {
            filter.addDataType(DATA_TYPE_TMOBILE_THEME);
            filter.addDataType(DATA_TYPE_TMOBILE_STYLE);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.e(TAG, "Could not add MIME types to filter", e);
        }

        context.registerReceiver(receiver, filter);
    }
}

