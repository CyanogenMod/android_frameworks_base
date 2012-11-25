package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class RingerVibrationModeTile extends QuickSettingsTile {

    private AudioManager mAudioManager;

    public RingerVibrationModeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        onClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                boolean vibrate = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON;
                if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && vibrate){
                    // Switch to Silent
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
                    // Switch to Sound + Vibration
                    vibrator.vibrate(300);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    Intent i = new Intent(VibrationModeTile.VIBRATION_STATE_CHANGED);
                    mContext.sendBroadcast(i);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT){
                    // Switch to Vibration
                    vibrator.vibrate(300);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    Intent i = new Intent(VibrationModeTile.VIBRATION_STATE_CHANGED);
                    mContext.sendBroadcast(i);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
                    // Switch to Sound
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                }
                applyVibrationChanges();
            }
        };

        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                return true;
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                applyVibrationChanges();
            }
        };

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mIntentFilter.addAction(VibrationModeTile.VIBRATION_STATE_CHANGED);
    }

    private void applyVibrationChanges(){
        boolean vibrate = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON;
        mLabel = mContext.getString(R.string.quick_settings_ringer_normal);
        if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && vibrate){
            //Sound + Vibrate
            mDrawable = R.drawable.ic_qs_ring_vibrate_on;
        }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
            //Sound
            mDrawable = R.drawable.ic_qs_ring_on;
        }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT){
            mDrawable = R.drawable.ic_qs_ring_off;
        }else{
            mDrawable = R.drawable.ic_qs_vibrate_on;
        }
        updateQuickSettings();
    }

}
