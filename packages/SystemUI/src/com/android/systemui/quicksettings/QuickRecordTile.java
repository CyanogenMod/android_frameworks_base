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
import android.view.ViewConfiguration;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.text.DateFormat;

public class QuickRecordTile extends QuickSettingsTile {

    private static final String TAG = "QuickRecordTile";

    private static final String PLAY_RECORDING =
            "com.android.systemui.quicksettings.PLAY_RECORDING";
    private static final String RECORDING_NAME = "QuickRecord ";
    private static final String DELIMITER = "|";
    private static final String RECORDING_TYPE = ".3gp";

    public static final int STATE_IDLE = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_RECORDING = 2;
    public static final int STATE_JUST_RECORDED = 3;
    public static final int STATE_NO_RECORDING = 4;
    // One hour maximum recording time
    public static final int MAX_RECORD_TIME = 3600000;

    private Handler mHandler;
    private MediaPlayer mPlayer = null;
    private MediaRecorder mRecorder = null;

    private static String mQuickAudio = null;

    private boolean mExists;

    private int mRecordingState = 0;
    private int mTaps = 0;

    public QuickRecordTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mGenericCollapse = false;
        mQuickAudio = manipulateCurrentFiles(false);
        mHandler = new Handler();

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mExists) {
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
                        checkDoubleClick();
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

        qsc.registerAction(PLAY_RECORDING, this);
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

    @Override
    public void onReceive(Context context, Intent intent) {
        final String file = intent.getStringExtra("file");
        if (file != null) {
            if (!mExists) {
                mRecordingState = STATE_NO_RECORDING;
            }
            switch (mRecordingState) {
                case STATE_IDLE:
                case STATE_JUST_RECORDED:
                    startPlaying(file);
                    break;
                case STATE_PLAYING:
                    stopPlaying();
                    startPlaying(file);
                    break;
                case STATE_RECORDING:
                    stopRecording();
                    startPlaying(file);
                    break;
            }
        } else {
            mQuickAudio = manipulateCurrentFiles(false);
            updateResources();
        }
    }

    private synchronized void updateTile() {
        int playStateName = 0;
        int playStateIcon = 0;
        if (!mExists) {
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

    private String manipulateCurrentFiles(final boolean newFileIncoming) {
        File[] currentRecordings = currentRecordings();
        if (newFileIncoming) {
            boolean deleted = false;
            // Allow up to 10 recordings
            if (currentRecordings.length == 10
                    && currentRecordings[0] != null
                    && currentRecordings[0].exists()) {
                currentRecordings[0].delete();
                deleted = true;
            }

            // Return the name of the new recording
            File file = new File(mContext.getFilesDir() + File.separator
                    + RECORDING_NAME + DELIMITER
                    + DateFormat.getDateTimeInstance().format(System.currentTimeMillis())
                    + DELIMITER + RECORDING_TYPE);
            mQuickAudio = file.getAbsolutePath();
            mExists = true;
            return mQuickAudio;
        } else {
            // Return the name of the latest recording
            if (currentRecordings.length > 0) {
                return currentRecordings[currentRecordings.length - 1].getAbsolutePath();
            } else {
                return null;
            }
        }
    }

    private File[] currentRecordings() {
        File location = new File(mContext.getFilesDir().toString());
        File[] files = location.listFiles(new FilenameFilter() {
            public boolean accept(File directory, String file) {
                return file.startsWith(RECORDING_NAME)
                        && file.endsWith(RECORDING_TYPE);
            }
        });

        Arrays.sort(files, new Comparator<File>(){
            public int compare(File f1, File f2) {
                return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
            }
        });

        if (files.length > 0 && files[0] != null) {
            mExists = files[0].exists();
        } else {
            mExists = false;
        }
        return files;
    }

    private void checkDoubleClick() {
        mHandler.removeCallbacks(checkDouble);
        if (mTaps > 0) {
            mTaps = 0;
            Intent intent = new Intent(
                    "com.android.systemui.quicksettings.QuickRecordingsDialog");
            startSettingsActivity(intent);
        } else {
            mTaps += 1;
            mHandler.postDelayed(checkDouble,
                    ViewConfiguration.getDoubleTapTimeout());
        }
    }

    private void tryMediaEvent() {
        if (!mExists) {
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
                startPlaying(mQuickAudio);
                break;
            case STATE_PLAYING:
                stopPlaying();
                break;
        }
    }

    final Runnable checkDouble = new Runnable () {
        public void run() {
            mTaps = 0;
            tryMediaEvent();
        }
    };

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

    private void startPlaying(final String file) {
        if (file != null) {
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setDataSource(file);
                mPlayer.prepare();
                mPlayer.start();
                mRecordingState = STATE_PLAYING;
                updateResources();
                mPlayer.setOnCompletionListener(stoppedPlaying);
            } catch (IOException e) {
                Log.e(TAG, "QuickRecord prepare() failed on play: ", e);
            }
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
        mRecorder.setOutputFile(manipulateCurrentFiles(true));
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
            mRecorder.start();
            mRecordingState = STATE_RECORDING;
            updateResources();
            mHandler.postDelayed(autoStopRecord, MAX_RECORD_TIME);
        } catch (IOException e) {
            Log.e(TAG, "QuickRecord prepare() failed on record: ", e);
            mQuickAudio = manipulateCurrentFiles(false);
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
