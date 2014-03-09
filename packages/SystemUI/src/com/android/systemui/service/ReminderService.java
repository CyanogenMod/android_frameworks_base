/*
 * Copyright (C) 2014 SlimRoms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;

import com.android.internal.util.slim.SlimActions;
import com.android.systemui.R;

public class ReminderService extends Service {

    private static final String POST_REMINDER_NOTIFY =
            "com.android.systemui.POST_REMINDER_NOTIFY";

    private static final String KEY_REMINDER_ACTION =
            "key_reminder_action";

    private MediaPlayer mMediaPlayer;

    private boolean mPlaying = false;
    private boolean mStopSelf = false;

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mStopSelf = intent.getBooleanExtra("stopSelf", false);
        boolean startTime = intent.getBooleanExtra("time", false);
        boolean clearNoti = intent.getBooleanExtra("dismissNoti", false);
        if (startTime) {
            final SharedPreferences shared = this.getSharedPreferences(
                    KEY_REMINDER_ACTION, Context.MODE_PRIVATE);
            shared.edit().putBoolean("updated", true).commit();
            Intent time = new Intent(
                    "com.android.systemui.timedialog.ReminderTimeDialog");
            time.putExtra("type", "time");
            SlimActions.startIntent(this, time, true);
        }

        if (clearNoti) {
            Intent notify = new Intent();
            notify.setAction(POST_REMINDER_NOTIFY);
            notify.putExtra("clear", true);
            this.sendBroadcast(notify);
        }
        try {
            startAlarmSound();
        } catch (Exception e) {
            // Do nothing
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void startAlarmSound()
            throws java.io.IOException, IllegalArgumentException, IllegalStateException {


        String ringtoneString = Settings.System.getStringForUser(this.getContentResolver(),
                    Settings.System.REMINDER_ALERT_RINGER,
                    UserHandle.USER_CURRENT);

        if (mStopSelf) {
            stopSelf();
        } else {
            int alertMode = Settings.System.getIntForUser(
                        this.getContentResolver(),
                        Settings.System.REMINDER_ALERT_NOTIFY,
                        0, UserHandle.USER_CURRENT);
            Uri alertSoundUri;
            if (ringtoneString == null) {
                alertSoundUri = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_RINGTONE);
            } else {
                alertSoundUri = Uri.parse(ringtoneString);
            }

            if (mPlaying) {
                stopAlarm();
            }

            if (mMediaPlayer == null) {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setOnErrorListener(new OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        mp.stop();
                        mp.release();
                        mMediaPlayer = null;
                        return true;
                    }
                });
            }

            mMediaPlayer.setDataSource(this, alertSoundUri);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);

            if (alertMode == 2) {
                mMediaPlayer.setLooping(true);
            } else {
                mMediaPlayer.setLooping(false);
                mMediaPlayer.setOnSeekCompleteListener(stopSelf);
            }
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            mPlaying = true;
        }
    }

    public void stopAlarm() {
        if (mPlaying) {
            if (mMediaPlayer != null) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
            mPlaying = false;
        }
    }

    final OnSeekCompleteListener stopSelf = new OnSeekCompleteListener() {
        public void onSeekComplete(MediaPlayer mp) {
            ReminderService.this.stopSelf();
        }
    };
}
