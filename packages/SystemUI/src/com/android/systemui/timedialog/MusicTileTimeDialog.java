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
import android.widget.TimePicker;

import com.android.systemui.R;

import java.util.Calendar;

public class MusicTileTimeDialog extends Activity  {

    private static final String TAG = "MusicTileTimeDialog";

    private static final String KEY_MUSIC_TIMER =
            "key_music_timer";
    private static final String SCHEDULE_MEDIA_SLEEP =
            "com.android.systemui.quicksettings.SCHEDULE_MEDIA_SLEEP";

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
        startTimerDialog();
    }

    private void startTimerDialog() {
        int hour;
        int minutes;
        mCalendar = Calendar.getInstance();
        final SharedPreferences shared = this.getSharedPreferences(
                KEY_MUSIC_TIMER, Context.MODE_PRIVATE);
        final boolean scheduled = shared.getBoolean("scheduled", false);
        hour = shared.getInt("hour", -1);
        minutes = shared.getInt("minutes", -1);

        if (hour == -1 || minutes == -1) {
            hour = mCalendar.get(Calendar.HOUR_OF_DAY);
            minutes = mCalendar.get(Calendar.MINUTE);
        }

        TimePickerDialog dlg = new TimePickerDialog(this,
        new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker t, int hours, int minutes) {
                mCalendar = Calendar.getInstance();
                int timePicked = hours * 60 + minutes;
                int timeNow = mCalendar.get(Calendar.HOUR_OF_DAY) * 60
                        + mCalendar.get(Calendar.MINUTE);

                if (timePicked == 0 && timePicked != timeNow) {
                    // Midnight
                    timePicked = FULL_DAY - timeNow;
                } else if (timePicked > timeNow) {
                    // Time after current time - same day
                    timePicked = timePicked - timeNow;
                } else if (timePicked < timeNow) {
                    // Time before current time - next day
                    timePicked = FULL_DAY - timeNow + timePicked;
                } else {
                    // Matches current time - will resolve now
                    timePicked = -1;
                }


                mCalendar.add(Calendar.MINUTE, timePicked);
                AlarmManager am = (AlarmManager)
                        MusicTileTimeDialog.this.getSystemService(Context.ALARM_SERVICE);
                PendingIntent stopMusic = null;
                Intent intent = new Intent();
                intent.setAction(SCHEDULE_MEDIA_SLEEP);
                if (mCanceled != -1) {
                    stopMusic = PendingIntent.getBroadcast(
                            MusicTileTimeDialog.this, 1, intent,
                            PendingIntent.FLAG_CANCEL_CURRENT);
                    am.cancel(stopMusic);
                }
                if (mCanceled == 0) {
                    shared.edit().putBoolean("scheduled", true).commit();
                    shared.edit().putInt("hour", hours).commit();
                    shared.edit().putInt("minutes", minutes).commit();
                    am.set(AlarmManager.RTC_WAKEUP,
                            mCalendar.getTimeInMillis(), stopMusic);
                }
                MusicTileTimeDialog.this.finish();
            };
        }, hour, minutes, DateFormat.is24HourFormat(this));
        dlg.setTitle(scheduled
                ? R.string.quick_settings_music_schedule_in_progress
                : R.string.quick_settings_music_schedule_title);
        if (scheduled) {
            dlg.setButton(DialogInterface.BUTTON_NEGATIVE,
                    this.getString(R.string.quick_settings_music_schedule_cancel),
                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    mCanceled = 1;
                    shared.edit().putBoolean("scheduled", false).commit();
                    shared.edit().putInt("hour", -1).commit();
                    shared.edit().putInt("minutes", -1).commit();
                    dialog.cancel();
                }
            });
        }
        dlg.setButton(DialogInterface.BUTTON_POSITIVE,
                this.getString(scheduled
                        ? R.string.quick_settings_music_schedule_update
                        : R.string.quick_settings_music_schedule_start),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mCanceled = 0;
                dialog.cancel();
            }
        });

        dlg.show();
    }

}
