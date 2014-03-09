/*
 * Copyright 2014 SlimRom
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

package com.android.systemui.timedialog;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.EditText;
import android.widget.TimePicker;

import com.android.systemui.R;

import java.util.Calendar;

public class ReminderTimeDialog extends Activity  {

    private static final String TAG = "ReminderTimeDialog";

    private static final String KEY_REMINDER_ACTION =
            "key_reminder_action";

    private static final String SCHEDULE_REMINDER_NOTIFY =
            "com.android.systemui.SCHEDULE_REMINDER_NOTIFY";

    private static final int FULL_DAY = 1440; // 1440 minutes in a day

    private int mCanceled = -1;

    private Calendar mCalendar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        String type = getIntent().getStringExtra("type");

        if (type == null) {
            startTextDialog();
        } else if (type.equals("time")) {
            startTimerDialog();
        } else {
            startClearDialog();
        }
    }

    private void startTextDialog() {
        final int dpi = this.getResources().getDisplayMetrics().densityDpi;
        final int maxChar = (int) (-1 * dpi) / 15 + (113 / 3);
        View view = View.inflate(this, R.layout.reminder_dialog, null);
        final SharedPreferences shared = this.getSharedPreferences(
                KEY_REMINDER_ACTION, Context.MODE_PRIVATE);
        final EditText title = (EditText) view.findViewById(R.id.title);
        final EditText message = (EditText) view.findViewById(R.id.message);
        String titleText = shared.getString("title", null);
        String messageText = shared.getString("message", null);
        title.append(titleText == null ? "" : titleText);
        message.append(messageText == null ? "" : messageText);

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.quick_settings_reminder)
        .setCancelable(false)
        .setNegativeButton(R.string.cancel,
            new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                ReminderTimeDialog.this.finish();
            }
        })
        .setPositiveButton(R.string.dlg_ok,
            new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                shared.edit().putBoolean("updated", true).commit();
                shared.edit().putString("title", title.getText().toString()).commit();
                shared.edit().putString("message", message.getText().toString()).commit();
                startTimerDialog();
            }
        });
        AlertDialog a = alert.create();
        a.setCanceledOnTouchOutside(false);
        a.setView(view);
        a.show();
    }

    private void startTimerDialog() {
        int hour;
        int minutes;
        boolean newTime = false;
        mCalendar = Calendar.getInstance();
        final SharedPreferences shared = this.getSharedPreferences(
                KEY_REMINDER_ACTION, Context.MODE_PRIVATE);
        hour = shared.getInt("hours", -1);
        minutes = shared.getInt("minutes", -1);

        if (hour == -1 || minutes == -1) {
            newTime = true;
            hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            minutes = mCalendar.get(Calendar.MINUTE);
        }

        TimePickerDialog dlg = new TimePickerDialog(this,
        new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker t, int hours, int minutes) {
                mCalendar = Calendar.getInstance();
                shared.edit().putInt("day", mCalendar.get(Calendar.DAY_OF_YEAR)).commit();
                int timePicked = hours * 60 + minutes;
                int timeNow = mCalendar.get(Calendar.HOUR_OF_DAY) * 60
                        + mCalendar.get(Calendar.MINUTE);

                if (timePicked == 0 && timePicked != timeNow) {
                    // Midnight
                    shared.edit().putBoolean("tomorrow", true).commit();
                    timePicked = FULL_DAY - timeNow;
                } else if (timePicked > timeNow) {
                    // Time after current time - same day
                    shared.edit().putBoolean("tomorrow", false).commit();
                    timePicked = timePicked - timeNow;
                } else if (timePicked < timeNow) {
                    // Time before current time - next day
                    shared.edit().putBoolean("tomorrow", true).commit();
                    timePicked = FULL_DAY - timeNow + timePicked;
                } else {
                    // Matches current time - 24 hours
                    shared.edit().putBoolean("tomorrow", true).commit();
                    timePicked = FULL_DAY;
                }


                mCalendar.add(Calendar.MINUTE, timePicked);
                mCalendar.add(Calendar.SECOND, -mCalendar.get(Calendar.SECOND));
                mCalendar.add(Calendar.MILLISECOND, -mCalendar.get(Calendar.MILLISECOND));
                AlarmManager am = (AlarmManager)
                        ReminderTimeDialog.this.getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent();
                intent.setAction(SCHEDULE_REMINDER_NOTIFY);
                PendingIntent reminder = PendingIntent.getBroadcast(
                            ReminderTimeDialog.this, 1, intent,
                            PendingIntent.FLAG_CANCEL_CURRENT);
                am.cancel(reminder);
                shared.edit().putBoolean("scheduled", false).commit();
                if (mCanceled == 0) {
                    shared.edit().putInt("hours", hours).commit();
                    shared.edit().putInt("minutes", minutes).commit();
                    am.set(AlarmManager.RTC_WAKEUP,
                            mCalendar.getTimeInMillis(), reminder);
                } else if (mCanceled == 1) {
                    shared.edit().putInt("hours", -1).commit();
                    shared.edit().putInt("minutes", -1).commit();
                    shared.edit().putInt("day", -1).commit();
                }
                updateView();
                ReminderTimeDialog.this.finish();
            };
        }, hour, minutes, DateFormat.is24HourFormat(this));
        dlg.setTitle(R.string.quick_settings_reminder_time);
        dlg.setButton(DialogInterface.BUTTON_NEGATIVE,
                this.getString(R.string.quick_settings_reminder_no_expire),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCanceled = 1;
                dialog.cancel();
            }
        });
        dlg.setButton(DialogInterface.BUTTON_POSITIVE,
                this.getString(newTime ? R.string.quick_settings_reminder_set
                        : R.string.quick_settings_reminder_update),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCanceled = 0;
                dialog.cancel();
            }
        });
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        dlg.show();
    }

    private void startClearDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.quick_settings_reminder_dismiss)
        .setCancelable(false)
        .setMessage(R.string.quick_settings_reminder_dismiss_message)
        .setNegativeButton(R.string.cancel,
            new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                ReminderTimeDialog.this.finish();
            }
        })
        .setPositiveButton(R.string.yes,
            new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                final SharedPreferences shared =
                        ReminderTimeDialog.this.getSharedPreferences(
                KEY_REMINDER_ACTION, Context.MODE_PRIVATE);
                shared.edit().putBoolean("scheduled", false).commit();
                shared.edit().putInt("hours", -1).commit();
                shared.edit().putInt("minutes", -1).commit();
                shared.edit().putInt("day", -1).commit();
                shared.edit().putString("title", null).commit();
                shared.edit().putString("message", null).commit();
                AlarmManager am = (AlarmManager)
                        ReminderTimeDialog.this.getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent();
                intent.setAction(SCHEDULE_REMINDER_NOTIFY);
                PendingIntent reminder = PendingIntent.getBroadcast(
                            ReminderTimeDialog.this, 1, intent,
                            PendingIntent.FLAG_CANCEL_CURRENT);
                am.cancel(reminder);
                updateView();
                ReminderTimeDialog.this.finish();
            }
        });
        AlertDialog a = alert.create();
        a.setCanceledOnTouchOutside(false);
        a.show();
    }

    private void updateView() {
        Intent intent = new Intent();
        intent.setAction(SCHEDULE_REMINDER_NOTIFY);
        this.sendBroadcast(intent);
    }
}
