/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2012 Android Open Kang Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

import java.io.File;
import java.io.IOException;

public class QuickRecordTile extends QuickSettingsTile {

    private static final String TAG = "QuickRecordTile";

    public static final int STATE_IDLE = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_RECORDING = 2;
    public static final int STATE_JUST_RECORDED = 3;
    public static final int STATE_NO_RECORDING = 4;
    public static final int MAX_RECORD_TIME = 120000;

    private File mFile;
    private Handler mHandler;
    private MediaPlayer mPlayer = null;
    private MediaRecorder mRecorder = null;

    private static String mQuickAudio = null;

    private int mRecordingState = 0;

    public QuickRecordTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mFile = new File(mContext.getFilesDir() + File.separator
                + "quickrecord.3gp");
        mQuickAudio = mFile.getAbsolutePath();
        mHandler = new Handler();

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mFile.exists()) {
                    mRecordingState = STATE_NO_RECORDING;
                }
                switch (mRecordingState) {
                    case STATE_RECORDING:
                        stopRecording();
                        break;
                    case STATE_NO_RECORDING:
                        return;
                    case STATE_IDLE:
                    case STATE_JUST_RECORDED:
                        startPlaying();
                        break;
                    case STATE_PLAYING:
                        stopPlaying();
                        break;
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                switch (mRecordingState) {
                    case STATE_NO_RECORDING:
                    case STATE_IDLE:
                    case STATE_JUST_RECORDED:
                        startRecording();
                        break;
                }
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        int playStateName = 0;
        int playStateIcon = 0;
        if (!mFile.exists()) {
            mRecordingState = STATE_NO_RECORDING;
        }
        switch (mRecordingState) {
            case STATE_IDLE:
                playStateName = R.string.quick_settings_quick_record_def;
                playStateIcon = R.drawable.ic_qs_quickrecord;
                break;
            case STATE_PLAYING:
                playStateName = R.string.quick_settings_quick_record_play;
                playStateIcon = R.drawable.ic_qs_playing;
                break;
            case STATE_RECORDING:
                playStateName = R.string.quick_settings_quick_record_rec;
                playStateIcon = R.drawable.ic_qs_recording;
                break;
            case STATE_JUST_RECORDED:
                playStateName = R.string.quick_settings_quick_record_save;
                playStateIcon = R.drawable.ic_qs_saved;
                break;
            case STATE_NO_RECORDING:
                playStateName = R.string.quick_settings_quick_record_nofile;
                playStateIcon = R.drawable.ic_qs_quickrecord;
                break;
        }
        mDrawable = playStateIcon;
        mLabel = mContext.getString(playStateName);
    }

    final Runnable delayTileRevert = new Runnable () {
        public void run() {
            if (mRecordingState == STATE_JUST_RECORDED) {
                mRecordingState = STATE_IDLE;
                updateResources();
            }
        }
    };

    final Runnable autoStopRecord = new Runnable() {
        public void run() {
            if (mRecordingState == STATE_RECORDING) {
                stopRecording();
            }
        }
    };

    final OnCompletionListener stoppedPlaying = new OnCompletionListener(){
        public void onCompletion(MediaPlayer mp) {
            mRecordingState = STATE_IDLE;
            updateResources();
        }
    };

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mQuickAudio);
            mPlayer.prepare();
            mPlayer.start();
            mRecordingState = STATE_PLAYING;
            updateResources();
            mPlayer.setOnCompletionListener(stoppedPlaying);
        } catch (IOException e) {
            Log.e(TAG, "QuickRecord prepare() failed on play: ", e);
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
        mRecordingState = STATE_IDLE;
        updateResources();
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mQuickAudio);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
            mRecorder.start();
            mRecordingState = STATE_RECORDING;
            updateResources();
            mHandler.postDelayed(autoStopRecord, MAX_RECORD_TIME);
        } catch (IOException e) {
            Log.e(TAG, "QuickRecord prepare() failed on record: ", e);
        }
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        mRecordingState = STATE_JUST_RECORDED;
        updateResources();
        mHandler.postDelayed(delayTileRevert, 2000);
    }
}
