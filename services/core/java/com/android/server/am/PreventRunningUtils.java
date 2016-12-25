package com.android.server.am;

import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ServiceManager;

import java.util.Set;

public class PreventRunningUtils {

    private static ActivityManagerService ams;

    private static PreventRunning mPreventRunning = new PreventRunning();

    private PreventRunningUtils() {
    }

    private static ActivityManagerService getAms() {
        if (ams == null) {
            ams = (ActivityManagerService) ServiceManager.getService(Context.ACTIVITY_SERVICE);
        }
        return ams;
    }

    public static boolean isExcludingStopped(Intent intent) {
        String action = intent.getAction();
        return intent.isExcludingStopped() && action != null && mPreventRunning.isExcludingStopped(action);
    }

    public static int match(IntentFilter filter, String action, String type, String scheme, Uri data, Set<String> categories, String tag) {
        int match = filter.match(action, type, scheme, data, categories, tag);
        if (match >= 0) {
            return mPreventRunning.match(match, filter, action, type, scheme, data, categories);
        } else {
            return match;
        }
    }

    public static boolean hookStartProcessLocked(String processName, ApplicationInfo info, boolean knownToBeDead, int intentFlags, String hostingType, ComponentName hostingName) {
        return mPreventRunning.hookStartProcessLocked(getAms().mContext, info, hostingType, hostingName);
    }

    public static int onStartActivity(int res, IApplicationThread caller, String callingPackage, Intent intent) {
        if (res >= 0 && intent != null && (intent.hasCategory(Intent.CATEGORY_HOME) || intent.hasCategory(Intent.CATEGORY_LAUNCHER))) {
            ProcessRecord callerApp = getAms().getRecordForAppLocked(caller);
            if (callerApp != null) {
                mPreventRunning.onStartHomeActivity(callerApp.info.packageName);
            }
        }
        return res;
    }

    public static void onAppDied(ProcessRecord app) {
        mPreventRunning.onAppDied(app);
    }

    public static boolean returnFalse(boolean res) {
        if (mPreventRunning.isActiviated()) {
            return false;
        } else {
            return res;
        }
    }

    public static void onCleanUpRemovedTask(Intent intent) {
        if (intent != null && intent.getComponent() != null) {
            mPreventRunning.onCleanUpRemovedTask(intent.getComponent().getPackageName());
        }
    }

    public static void onMoveActivityTaskToBack(IBinder token) {
        ActivityRecord ar = forToken(token);
        mPreventRunning.onMoveActivityTaskToBack(ar != null ? ar.packageName : null);
    }

    public static void setSender(IApplicationThread caller) {
        final ProcessRecord callerApp = getAms().getRecordForAppLocked(caller);
        mPreventRunning.setSender(callerApp != null ? callerApp.info.packageName : String.valueOf(Binder.getCallingUid()));
    }

    public static void clearSender() {
        mPreventRunning.setSender(null);
    }

    public static boolean hookStartService(IApplicationThread caller, Intent service) {
        return mPreventRunning.hookStartService(service);
    }

    public static boolean hookBindService(IApplicationThread caller, IBinder token, Intent service) {
        return mPreventRunning.hookBindService(service);
    }

    public static void onBroadcastIntent(Intent intent) {
        mPreventRunning.onBroadcastIntent(intent);
    }

    public static void onUserLeavingActivity(IBinder token, boolean finishing, boolean userLeaving) {
        if (userLeaving) {
            mPreventRunning.onUserLeavingActivity(forToken(token));
        }
    }

    public static void onResumeActivity(IBinder token) {
        mPreventRunning.onResumeActivity(forToken(token));
    }

    public static void onDestroyActivity(IBinder token) {
        mPreventRunning.onDestroyActivity(forToken(token));
    }

    public static void onLaunchActivity(IBinder token) {
        mPreventRunning.onLaunchActivity(forToken(token));
    }

    private static ActivityRecord forToken(IBinder token) {
        return ActivityRecord.forTokenLocked(token);
    }

}
