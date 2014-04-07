/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.provider.Settings;
import android.util.Log;

import java.util.Calendar;
/**
 * Performs a number of miscellaneous, non-system-critical actions
 * after the system has finished booting.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "SystemUIBootReceiver";

    private static final String KEY_MUSIC_TIMER =
            "key_music_timer";
    private static final String KEY_REMINDER_ACTION =
            "key_reminder_action";
    private static final String SCHEDULE_REMINDER_NOTIFY =
            "com.android.systemui.SCHEDULE_REMINDER_NOTIFY";

    private static final int FULL_DAY = 1440; // 1440 minutes in a day

    @Override
    public void onReceive(final Context context, Intent intent) {
        SharedPreferences shared = context.getSharedPreferences(
                KEY_MUSIC_TIMER, Context.MODE_PRIVATE);
        shared.edit().putBoolean("scheduled", false).commit();
        shared.edit().putInt("hour", -1).commit();
        shared.edit().putInt("minutes", -1).commit();

        shared = context.getSharedPreferences(
                KEY_REMINDER_ACTION, Context.MODE_PRIVATE);
        int hours = shared.getInt("hours", -1);
        if (shared.getBoolean("scheduled", false)
                && hours != -1) {
            Calendar calendar = Calendar.getInstance();
            int today = calendar.get(Calendar.DAY_OF_YEAR);
            int day = shared.getInt("day", -1);
            int minutes = shared.getInt("minutes", -1);
            int timePicked = hours * 60 + minutes;
            int slop = today - day;

            if (slop == 0 || slop == 1 || slop >= 363) {

                int timeNow = calendar.get(Calendar.HOUR_OF_DAY) * 60
                        + calendar.get(Calendar.MINUTE);
                // same day
                if (slop == 0) {
                    if (timePicked == 0 && timePicked != timeNow) {
                        // Midnight
                        timePicked = FULL_DAY - timeNow;
                    } else if (timePicked > timeNow) {
                        // Time after current time - same day
                        timePicked = timePicked - timeNow;
                    } else if (timePicked < timeNow) {
                        boolean tomorrow = shared.getBoolean("tomorrow", false);
                        if (tomorrow) {
                            // Time before current time - next day
                            timePicked = FULL_DAY - timeNow + timePicked;
                        } else {
                            // Already happened - notify once system is warmed up
                            timePicked = 2;
                        }
                    } else {
                        // Matches current time - 24 hours
                        timePicked = FULL_DAY;
                    }
                } else {
                    if (timePicked > timeNow) {
                        // Today is the next day - resolve reminder today
                        timePicked = timePicked - timeNow;
                    } else {
                        // Already happened - notify once system is warmed up
                        timePicked = 2;
                    }
                }
            } else {
                // Way in the past, notify once system is warmed up.
                timePicked = 2;
            }

            calendar.add(Calendar.MINUTE, timePicked);
            calendar.add(Calendar.SECOND, -calendar.get(Calendar.SECOND));
            calendar.add(Calendar.MILLISECOND, -calendar.get(Calendar.MILLISECOND));
            AlarmManager am = (AlarmManager)
                    context.getSystemService(Context.ALARM_SERVICE);
            PendingIntent notify = null;
            Intent pend = new Intent();
            pend.setAction(SCHEDULE_REMINDER_NOTIFY);
            notify = PendingIntent.getBroadcast(
                    context, 1, pend,
                    PendingIntent.FLAG_CANCEL_CURRENT);
            am.cancel(notify);
            am.set(AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(), notify);
        }

        try {
            // Start the load average overlay, if activated
            ContentResolver res = context.getContentResolver();
            if (Settings.Global.getInt(res, Settings.Global.SHOW_PROCESSES, 0) != 0) {
                Intent loadavg = new Intent(context, com.android.systemui.LoadAverageService.class);
                context.startService(loadavg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Can't start load average service", e);
        }
    }
}
