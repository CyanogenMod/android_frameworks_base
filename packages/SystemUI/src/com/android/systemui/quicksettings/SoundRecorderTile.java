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
import android.util.Log;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;

public class SoundRecorderTile extends QuickSettingsTile {
    static final String TAG = "SoundRecorderTile";
    static final String DATE_FORMAT = "yyyyMMddHHmmss";
    static final String SAMPLE_PREFIX = "recording-";
    static final String FILE_EXTENSION = ".3gp";

    static final String STORAGE_PATH_LOCAL_PHONE =
            Environment.getExternalStorageDirectory().toString() + "/SoundRecorder/";

    private MediaRecorder mRecorder = null;

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
        if (isRecording()) {
            mDrawable = R.drawable.ic_qs_sound_recorder_off;
            mLabel = mContext.getString(R.string.quick_settings_sound_recorder);
        } else {
            mDrawable = R.drawable.ic_qs_sound_recorder_on;
        }
    }

    private boolean isRecording() {
        return mRecorder != null;
    }

    private void startRecording() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
        String time = simpleDateFormat.format(new Date(System.currentTimeMillis()));

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(STORAGE_PATH_LOCAL_PHONE + SAMPLE_PREFIX + time + FILE_EXTENSION);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "SoundRecorder failed");
        }

        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
    }
}
