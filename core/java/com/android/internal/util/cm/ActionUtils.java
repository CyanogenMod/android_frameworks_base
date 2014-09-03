package com.android.internal.util.cm;

import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.IActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;

public class ActionUtils {
    private static final boolean DEBUG = false;
    private static final String TAG = ActionUtils.class.getSimpleName();
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    /**
     * Kills the top most / most recent user application, but leaves out the launcher.
     * This is function governed by {@link Settings.Secure.KILL_APP_LONGPRESS_BACK}.
     *
     * @param context the current context, used to retrieve the package manager.
     * @param userId the ID of the currently active user
     * @return {@code true} when a user application was found and closed.
     */
    public static boolean killForegroundApp(Context context, int userId) {
        try {
            return killForegroundAppInternal(context, userId);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not kill foreground app");
        }
        return false;
    }

    private static boolean killForegroundAppInternal(Context context, int userId)
            throws RemoteException {
        final IActivityManager am = ActivityManagerNative.getDefault();
        String defaultHomePackage = resolveCurrentLauncherPackage(context, userId);

        for (ActivityManager.RunningAppProcessInfo appInfo : am.getRunningAppProcesses()) {
            // Make sure it's a foreground user application (not system, root, phone, etc.)
            int uid = appInfo.uid, importance = appInfo.importance;
            if (uid < Process.FIRST_APPLICATION_UID
                    || uid > Process.LAST_APPLICATION_UID
                    || importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                continue;
            }

            if (appInfo.pkgList != null && appInfo.pkgList.length > 0) {
                for (String pkg : appInfo.pkgList) {
                    if (!pkg.equals(SYSTEMUI_PACKAGE) && !pkg.equals(defaultHomePackage)) {
                        am.forceStopPackage(pkg, userId);
                        return true;
                    }
                }
            } else {
                Process.killProcess(appInfo.pid);
                return true;
            }
        }

        return false;
    }

    /**
     * Attempt to bring up the last activity in the stack before the current active one.
     *
     * @param context
     * @return whether an activity was found to switch to
     */
    public static boolean switchToLastApp(Context context, int userId) {
        try {
            return switchToLastAppInternal(context, userId);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not switch to last app");
        }
        return false;
    }

    private static boolean switchToLastAppInternal(Context context, int userId)
            throws RemoteException {
        ActivityManager.RecentTaskInfo lastTask = getLastTask(context, userId);

        if (lastTask == null || lastTask.id < 0) {
            return false;
        }

        final String packageName = lastTask.baseIntent.getComponent().getPackageName();
        final IActivityManager am = ActivityManagerNative.getDefault();
        final ActivityOptions opts = ActivityOptions.makeCustomAnimation(context,
                com.android.internal.R.anim.last_app_in,
                com.android.internal.R.anim.last_app_out);

        if (DEBUG) Log.d(TAG, "switching to " + packageName);
        am.moveTaskToFront(lastTask.id, ActivityManager.MOVE_TASK_NO_USER_ACTION, opts.toBundle());

        return true;
    }

    private static ActivityManager.RecentTaskInfo getLastTask(Context context, int userId)
            throws RemoteException {
        final String defaultHomePackage = resolveCurrentLauncherPackage(context, userId);
        final IActivityManager am = ActivityManagerNative.getDefault();
        final List<ActivityManager.RecentTaskInfo> tasks = am.getRecentTasks(5,
                ActivityManager.RECENT_IGNORE_UNAVAILABLE, userId);

        for (int i = 1; i < tasks.size(); i++) {
            ActivityManager.RecentTaskInfo task = tasks.get(i);
            if (task.origActivity != null) {
                task.baseIntent.setComponent(task.origActivity);
            }
            String packageName = task.baseIntent.getComponent().getPackageName();
            if (!packageName.equals(defaultHomePackage)
                    && !packageName.equals(SYSTEMUI_PACKAGE)) {
                return tasks.get(i);
            }
        }

        return null;
    }

    private static String resolveCurrentLauncherPackage(Context context, int userId) {
        final Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final PackageManager pm = context.getPackageManager();
        final ResolveInfo launcherInfo = pm.resolveActivityAsUser(launcherIntent, 0, userId);
        return launcherInfo.activityInfo.packageName;
    }
}
