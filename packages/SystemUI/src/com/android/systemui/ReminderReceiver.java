/*
 * Copyright (C) 2014 SlimRoms
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.android.systemui.service.ReminderService;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String KEY_REMINDER_ACTION =
            "key_reminder_action";

    private static final int NOTI_ID = 254;

    @Override
    public void onReceive(final Context context, Intent intent) {
        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        SharedPreferences shared = context.getSharedPreferences(
                    KEY_REMINDER_ACTION, Context.MODE_PRIVATE);

        if (intent.getBooleanExtra("clear", false)) {
            manager.cancel(NOTI_ID);
            // User has set a new reminder but didn't clear
            // This notification until now
            if (!shared.getBoolean("updated", false)) {
                shared.edit().putBoolean("scheduled", false).commit();
                shared.edit().putInt("hours", -1).commit();
                shared.edit().putInt("minutes", -1).commit();
                shared.edit().putInt("day", -1).commit();
                shared.edit().putString("title", null).commit();
                shared.edit().putString("message", null).commit();
            }
        } else {
            String title = shared.getString("title", null);
            String message = shared.getString("message", null);
            if (title != null && message != null) {
                Bitmap bm = BitmapFactory.decodeResource(
                        context.getResources(), R.drawable.ic_qs_alarm_on);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                        .setTicker(title)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSmallIcon(R.drawable.ic_qs_alarm_on)
                        .setLargeIcon(bm)
                        .setDefaults(Notification.DEFAULT_LIGHTS
                                | Notification.DEFAULT_VIBRATE)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(message));

                int alertMode = Settings.System.getIntForUser(
                            context.getContentResolver(),
                            Settings.System.REMINDER_ALERT_NOTIFY,
                            0, UserHandle.USER_CURRENT);
                PendingIntent result = null;
                Intent serviceIntent = new Intent(context, ReminderService.class);
                if (alertMode != 0
                        && Settings.System.getIntForUser(
                        context.getContentResolver(),
                        Settings.System.QUIET_HOURS_MUTE,
                        0, UserHandle.USER_CURRENT_OR_SELF) != 2) {
                        context.startService(serviceIntent);
                }

                // Stop sound on click
                serviceIntent.putExtra("stopSelf", true);
                result = PendingIntent.getService(
                        context, 1, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                builder.setContentIntent(result);

                // Add button for dismissal
                serviceIntent.putExtra("dismissNoti", true);
                result = PendingIntent.getService(
                        context, 0, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                builder.addAction(R.drawable.ic_sysbar_null,
                        context.getResources().getString(
                        R.string.quick_settings_reminder_noti_dismiss), result);

                // Add button for reminding later
                serviceIntent.putExtra("time", true);
                result = PendingIntent.getService(
                        context, 2, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                builder.addAction(R.drawable.ic_qs_alarm_on,
                        context.getResources().getString(
                        R.string.quick_settings_reminder_noti_later), result);

                shared.edit().putBoolean("scheduled", false).commit();
                shared.edit().putInt("hours", -1).commit();
                shared.edit().putInt("minutes", -1).commit();
                shared.edit().putInt("day", -1).commit();

                manager.notify(NOTI_ID, builder.build());
            }
        }
        shared.edit().putBoolean("updated", false).commit();
    }
}
