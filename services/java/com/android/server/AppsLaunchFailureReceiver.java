package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.CustomTheme;
import android.util.Log;
import android.app.ActivityManager;
import android.os.SystemClock;

public class AppsLaunchFailureReceiver extends BroadcastReceiver {

    private static final int FAILURES_THRESHOLD = 5;
    private static final int EXPIRATION_TIME_IN_MILLISECONDS = 30000; // 30 seconds

    private int mFailuresCount = 0;
    private long mStartTime = 0;

    // This function implements the following logic.
    // If after a theme was applied the number of application launch failures
    // at any moment was equal to FAILURES_THRESHOLD
    // in less than EXPIRATION_TIME_IN_MILLISECONDS
    // the default theme is applied unconditionally.
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_APP_LAUNCH_FAILURE)) {
            long currentTime = SystemClock.uptimeMillis();
            if (currentTime - mStartTime > EXPIRATION_TIME_IN_MILLISECONDS) {
                // reset both the count and the timer
                mStartTime = currentTime;
                mFailuresCount = 0;
            }
            if (mFailuresCount <= FAILURES_THRESHOLD) {
                mFailuresCount++;
                if (mFailuresCount == FAILURES_THRESHOLD) {
                    CustomTheme defaultTheme = CustomTheme.getDefault();
                    ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                    Configuration currentConfig = am.getConfiguration();
                    currentConfig.customTheme = new CustomTheme(
                            defaultTheme.getThemeId(),
                            defaultTheme.getThemePackageName());
                    am.updateConfiguration(currentConfig);
                }
            }
        } else if (action.equals(Intent.ACTION_APP_LAUNCH_FAILURE_RESET)) {
            mFailuresCount = 0;
            mStartTime = SystemClock.uptimeMillis();
        } else if (action.equals(Intent.ACTION_PACKAGE_ADDED) ||
                   action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
            mFailuresCount = 0;
            mStartTime = SystemClock.uptimeMillis();
        }
    }

}
