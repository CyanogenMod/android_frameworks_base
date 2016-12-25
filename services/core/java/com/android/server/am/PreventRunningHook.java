package com.android.server.am;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;

import java.util.Set;

/**
 * Created by thom on 15/10/27.
 */
public interface PreventRunningHook {

    void setSender(String sender);

    void onBroadcastIntent(Intent intent);

    void onCleanUpRemovedTask(String packageName);

    void onStartHomeActivity(String sender);

    void onMoveActivityTaskToBack(String packageName);

    void onAppDied(Object processRecord);

    void onLaunchActivity(Object activityRecord);

    void onResumeActivity(Object activityRecord);

    void onUserLeavingActivity(Object activityRecord);

    void onDestroyActivity(Object activityRecord);

    boolean isExcludingStopped(String action);

    boolean hookStartProcessLocked(Context context, ApplicationInfo info, String hostingType, ComponentName hostingName);

    int match(int match, Object filter, String action, String type, String scheme, Uri data, Set<String> categories);

    void setVersion(int version);

    void setMethod(String method);

    boolean hookBindService(Intent service);

    boolean hookStartService(Intent service);

}
