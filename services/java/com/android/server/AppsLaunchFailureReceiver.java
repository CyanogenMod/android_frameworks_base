/*
 * Copyright (C) 2010, T-Mobile USA, Inc.
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

package com.android.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ThemeUtils;
import android.content.res.ThemeConfig;
import android.content.res.ThemeManager;
import android.os.SystemClock;
import android.provider.ThemesContract;

import java.util.ArrayList;
import java.util.List;

public class AppsLaunchFailureReceiver extends BroadcastReceiver {

    private static final int FAILURES_THRESHOLD = 3;
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
                    // let the theme manager take care of getting us back on the default theme
                    ThemeManager tm = (ThemeManager) context.getSystemService(Context.THEME_SERVICE);
                    List<String> components = new ArrayList<String>();
                    components.add(ThemesContract.ThemesColumns.MODIFIES_FONTS);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_LAUNCHER);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_ALARMS);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_BOOT_ANIM);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_ICONS);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_LOCKSCREEN);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_NOTIFICATIONS);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_OVERLAYS);
                    components.add(ThemesContract.ThemesColumns.MODIFIES_RINGTONES);
                    tm.requestThemeChange(ThemeConfig.HOLO_DEFAULT, components);
                }
            }
        } else if (action.equals(Intent.ACTION_APP_LAUNCH_FAILURE_RESET)
                || action.equals(ThemeUtils.ACTION_THEME_CHANGED)) {
            mFailuresCount = 0;
            mStartTime = SystemClock.uptimeMillis();
        } else if (action.equals(Intent.ACTION_PACKAGE_ADDED) ||
                action.equals(Intent.ACTION_PACKAGE_REMOVED)) {
            mFailuresCount = 0;
            mStartTime = SystemClock.uptimeMillis();
        }
    }

}
