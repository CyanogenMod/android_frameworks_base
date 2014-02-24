package com.android.systemui.quicksettings;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.media.MediaRecorder;
import android.util.Log;
import android.os.Environment;
import android.provider.Settings;

import com.android.systemui.R; 
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class SoundRecorderTile extends QuickSettingsTile {

    private static final String TAG = "SoundRecorderTile";
    private MediaRecorder mRecorder = null;

    public SoundRecorderTile(Context context, QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isRecording()){
                    stopRecording();
                } else {
                    startRecording();
                }
                updateTile();
                updateQuickSettings();
            }
        };
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if(isRecording()){
            mDrawable = R.drawable.ic_qs_soundrecorder_off;
            mLabel = mContext.getString(R.string.quick_settings_soundrecord_stop);
        } else {
            mDrawable = R.drawable.ic_qs_soundrecorder_on;
            mLabel = mContext.getString(R.string.quick_settings_soundrecord_start);
        }
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    private boolean isRecording() {
        return mRecorder != null;
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(getRecordFileLocate());
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

    private String getRecordFileLocate() {
        SimpleDateFormat simpleformat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String recordtime = simpleformat.format(new Date());
        return Environment.getExternalStorageDirectory().getAbsolutePath()+"/sound-"+recordtime+".3gp";
    }
}
