/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.IOException;
import java.text.SimpleDateFormat;

public class SoundRecorderTile extends QuickSettingsTile {
    private static final String TAG = "SoundRecorderTile";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final String STORAGE_PATH_LOCAL_PHONE =
            Environment.getExternalStorageDirectory().toString() + "/SoundRecorder/";
    private static final String SAMPLE_PREFIX = "recording-";
    private static final String FILE_EXTENSION = ".3gp";

    private final static int RECORDING_TIMER_UPDATE_INTERVAL = 1000;

    private MediaRecorder mRecorder = null;
    private long mSampleStartTime = 0;
    private String mTimerFormat;

    private WakeLock mWakeLock;
    private Handler mHandler;

    private TextView mText;

    public SoundRecorderTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording()) {
                    stopRecording();
                } else {
                    startRecording();
                }

                updateResources();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage("com.android.soundrecorder");
                startSettingsActivity(intent);
                return true;
            }
        };

        // Set up PowerManager WakeLock
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK
                | PowerManager.ON_AFTER_RELEASE, TAG);

        mHandler = new Handler();
    }

    @Override
    void onPostCreate() {
        // Get text view and timer format
        mText = (TextView) mTile.findViewById(R.id.text);
        mTimerFormat = mContext.getString(R.string.quick_settings_sound_recorder_timer_format);

        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if (isRecording()) {
            mDrawable = R.drawable.ic_qs_sound_recorder_on;

            // Start wake lock and recording time updater
            mWakeLock.acquire();
            mRecordingTimeUpdater.run();
        } else {
            mDrawable = R.drawable.ic_qs_sound_recorder_off;
            mLabel = mContext.getString(R.string.quick_settings_sound_recorder);

            // Stop wake lock and recording time updater
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            if (mHandler.hasCallbacks(mRecordingTimeUpdater)) {
                mHandler.removeCallbacks(mRecordingTimeUpdater);
            }
        }
    }

    Runnable mRecordingTimeUpdater = new Runnable() {
        @Override
        public void run() {
            updateRecordingTime();
            mHandler.postDelayed(mRecordingTimeUpdater, RECORDING_TIMER_UPDATE_INTERVAL);
        }
    };

    private void updateRecordingTime() {
        // Exit if recording was already stopped while this
        // function got called
        if (!isRecording()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long time = (int) (currentTime - mSampleStartTime) / 1000;
        mText.setText(String.format(mTimerFormat, time / 60, time % 60));
    }

    private boolean isRecording() {
        return mRecorder != null;
    }

    private void startRecording() {
        String time = DATE_FORMAT.format(System.currentTimeMillis());

        // Set up recorder
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(STORAGE_PATH_LOCAL_PHONE + SAMPLE_PREFIX + time + FILE_EXTENSION);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        // Try to prepare recording
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "SoundRecorder failed");
            stopRecording();
            return;
        }

        // Try to start recording
        try {
            mRecorder.start();
        } catch (RuntimeException e) {
            Log.e(TAG, "SoundRecorder failed");
            stopRecording();
            return;
        }

        // Set start time of the recording
        mSampleStartTime = System.currentTimeMillis();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.reset();
        mRecorder.release();
        mRecorder = null;

        mSampleStartTime = 0;
    }
}
