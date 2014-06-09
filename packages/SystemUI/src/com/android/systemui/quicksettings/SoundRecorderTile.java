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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;

public class SoundRecorderTile extends QuickSettingsTile {
    private static final String TAG = "SoundRecorderTile";

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final String SOUND_RECORDER_DIR_PATH =
            Environment.getExternalStorageDirectory().toString() + "/SoundRecorder/";
    private static final String SAMPLE_PREFIX = "recording";
    private static final String FILE_EXTENSION = ".3gp";

    private final static int SOUND_RECORDER_TILE_UPDATE_INTERVAL = 200;
    private final static long MAX_AMPLITUDE = 32768;

    private MediaRecorder mRecorder = null;
    private long mSampleStartTime = 0;
    private String mTimerFormat;

    private WakeLock mWakeLock;
    private Handler mHandler;

    private TextView mText;
    private ImageView mAmplitudeIndicator;

    public SoundRecorderTile(Context context, QuickSettingsController qsc) {
        super(context, qsc, R.layout.quick_settings_tile_sound_recorder);

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
        // Get views and timer format
        mText = (TextView) mTile.findViewById(R.id.text);
        mAmplitudeIndicator = (ImageView) mTile.findViewById(R.id.amplitudeIndicator);
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
            mSoundRecorderTileUpdater.run();
        } else {
            mDrawable = R.drawable.ic_qs_sound_recorder_off;
            mLabel = mContext.getString(R.string.quick_settings_sound_recorder);

            // Remove the amplitude indicator image
            mAmplitudeIndicator.setImageDrawable(null);

            // Stop wake lock and recording time updater
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
            if (mHandler.hasCallbacks(mSoundRecorderTileUpdater)) {
                mHandler.removeCallbacks(mSoundRecorderTileUpdater);
            }
        }
    }

    Runnable mSoundRecorderTileUpdater = new Runnable() {
        @Override
        public void run() {
            updateSoundRecorderTile();
            mHandler.postDelayed(mSoundRecorderTileUpdater, SOUND_RECORDER_TILE_UPDATE_INTERVAL);
        }
    };

    private void updateSoundRecorderTile() {
        // Exit if recording was already stopped while this
        // function got called
        if (!isRecording()) {
            return;
        }

        // Calculate the current loudness of the microphone input
        float loudness = mRecorder.getMaxAmplitude() / MAX_AMPLITUDE;
        if (loudness >= 0.2) {
            // Get the size of the image view
            int size = (int) mContext.getResources().getDimension(R.dimen.qs_tile_icon_size);

            // Set up the paint
            Paint paint = new Paint();
            paint.setColor(mContext.getResources().getColor(R.color.amplitude_indicator));

            // Draw the circle
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawCircle(size / 2, size / 2, size * loudness, paint);

            // Set the drawn circle as amplitude indicator
            mAmplitudeIndicator.setImageBitmap(bitmap);
        } else {
            mAmplitudeIndicator.setImageDrawable(null);
        }

        // Set recording time as tile label
        long currentTime = System.currentTimeMillis();
        long time = (int) (currentTime - mSampleStartTime) / 1000;
        mText.setText(String.format(mTimerFormat, time / 60, time % 60));
    }

    private boolean isRecording() {
        return mRecorder != null;
    }

    private void startRecording() {
        String time = DATE_FORMAT.format(System.currentTimeMillis());

        // Make sure the SoundRecorder directory exists
        File soundRecorderDir = new File(SOUND_RECORDER_DIR_PATH);
        if (!soundRecorderDir.exists()) {
            soundRecorderDir.mkdir();
        }

        // Set up recorder
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile(SOUND_RECORDER_DIR_PATH + SAMPLE_PREFIX + time + FILE_EXTENSION);

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
