/*
 *  Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.internal.util.omni;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.ActivityNotFoundException;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.util.Log;

import java.util.List;

public class TaskUtils {

    public static boolean killActiveTask(final Context context){
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        boolean targetKilled = false;
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Activity.ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> apps = am.getRunningAppProcesses();
        for (RunningAppProcessInfo appInfo : apps) {
            int uid = appInfo.uid;
            // Make sure it's a foreground user application (not system,
            // root, phone, etc.)
            if (uid >= Process.FIRST_APPLICATION_UID && uid <= Process.LAST_APPLICATION_UID
                    && appInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (appInfo.pkgList != null && (appInfo.pkgList.length > 0)) {
                    for (String pkg : appInfo.pkgList) {
                        if (!pkg.equals("com.android.systemui")
                                && !pkg.equals(defaultHomePackage)) {
                            am.forceStopPackage(pkg);
                            targetKilled = true;
                            break;
                        }
                    }
                } else {
                     Process.killProcess(appInfo.pid);
                     targetKilled = true;
                }
            }
            if (targetKilled) {
                return true;
            }
        }
        return false;
    }

    public static void toggleLastApp(final Context context){
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Activity.ACTIVITY_SERVICE);
        String defaultHomePackage = "com.android.launcher";
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
            defaultHomePackage = res.activityInfo.packageName;
        }
        final List<ActivityManager.RecentTaskInfo> tasks =
                am.getRecentTasks(5, ActivityManager.RECENT_IGNORE_UNAVAILABLE);
        // lets get enough tasks to find something to switch to
        // Note, we'll only get as many as the system currently has - up to 5
        int lastAppId = 0;
        Intent lastAppIntent = null;
        for (int i = 1; i < tasks.size() && lastAppIntent == null; i++) {
            final String packageName = tasks.get(i).baseIntent.getComponent().getPackageName();
            if (!packageName.equals(defaultHomePackage) && !packageName.equals("com.android.systemui")) {
                final ActivityManager.RecentTaskInfo info = tasks.get(i);
                lastAppId = info.id;
                lastAppIntent = info.baseIntent;
            }
        }
        if (lastAppId > 0) {
            am.moveTaskToFront(lastAppId, am.MOVE_TASK_NO_USER_ACTION);
        } else if (lastAppIntent != null) {
            // last task is dead, restart it.
            lastAppIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
            try {
                context.startActivityAsUser(lastAppIntent, UserHandle.CURRENT);
            } catch (ActivityNotFoundException e) {
                Log.w("Recent", "Unable to launch recent task", e);
            }
        }
    }
}
