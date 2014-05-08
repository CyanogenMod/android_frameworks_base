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
/* Copied from Launcher3 */
package com.android.wallpapercropper;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Various utilities shared amongst the WallpaperCropper's classes.
 */
public final class Utilities {
    private static final String TAG = "WallpaperCropper.Utilities";

    /*
     * Finds all system apks which had a broadcast receiver listening to a particular action.
     * @param action intent action used to find the apk
     * @return a list of pairs of apk package name and the resources.
     */
    static List<Pair<String, Resources>> findSystemApks(String action, PackageManager pm) {
        final Intent intent = new Intent(action);
        List<Pair<String, Resources>> systemApks = new ArrayList<Pair<String, Resources>>();
        for (ResolveInfo info : pm.queryBroadcastReceivers(intent, 0)) {
            if (info.activityInfo != null &&
                    (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                final String packageName = info.activityInfo.packageName;
                try {
                    final Resources res = pm.getResourcesForApplication(packageName);
                    systemApks.add(Pair.create(packageName, res));
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "Failed to find resources for " + packageName);
                }
            }
        }
        return systemApks;
    }
}
