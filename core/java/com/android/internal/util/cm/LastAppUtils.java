/*
 * Copyright (C) 2014 The CyanogenMod Project
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

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.List;

public class LastAppUtils {

    private static final boolean DEBUG = false;
    private static final String TAG = LastAppUtils.class.getSimpleName();
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    /**
     * Attempt to bring up the last activity in the stack before the current active one.
     *
     * @param context
     * @return whether an activity was found to switch to
     */
    public static boolean switchToLastApp(Context context) {
        final ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo lastTask = getLastTask(context, am);

        if (lastTask != null) {
            if (DEBUG) Log.d(TAG, "switching to " + lastTask.topActivity.getPackageName());

            final ActivityOptions opts = ActivityOptions.makeCustomAnimation(context,
                    com.android.internal.R.anim.last_app_in,
                    com.android.internal.R.anim.last_app_out);
            am.moveTaskToFront(lastTask.id, ActivityManager.MOVE_TASK_NO_USER_ACTION,
                    opts.toBundle());
            return true;
        }
        return false;
    }

    private static ActivityManager.RunningTaskInfo getLastTask(Context context,
                                                               final ActivityManager am) {
        final String defaultHomePackage = resolveCurrentLauncherPackage(context);
        List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(5);

        for (int i = 1; i < tasks.size(); i++) {
            String packageName = tasks.get(i).topActivity.getPackageName();
            if (!packageName.equals(defaultHomePackage)
                    && !packageName.equals(SYSTEMUI_PACKAGE)) {
                return tasks.get(i);
            }
        }

        return null;
    }

    private static String resolveCurrentLauncherPackage(Context context) {
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final PackageManager pm = context.getPackageManager();
        final ResolveInfo launcherInfo = pm.resolveActivity(launcherIntent, 0);
        return launcherInfo.activityInfo.packageName;
    }
}
