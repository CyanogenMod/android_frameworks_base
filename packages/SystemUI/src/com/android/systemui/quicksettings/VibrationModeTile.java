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

public class VibrationModeTile extends QuickSettingsTile {

    private AudioManager mAudioManager;
    public final static String VIBRATION_STATE_CHANGED = "com.android.systemui.quicksettings.VIBRATION_STATE_CHANGED";

    public VibrationModeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mOnClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
                    //Vibrate -> Silent
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT){
                    //Silent -> Vibrate
                    vibrator.vibrate(300);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    Intent i = new Intent(VIBRATION_STATE_CHANGED);
                    mContext.sendBroadcast(i);
                }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
                    if(mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON){
                        //Sound + Vibrate -> Sound
                        mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_OFF);
                    }else{
                        //Sound -> Sound + Vibrate
                        vibrator.vibrate(300);
                        Intent i = new Intent(VIBRATION_STATE_CHANGED);
                        mContext.sendBroadcast(i);
                        mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, AudioManager.VIBRATE_SETTING_ON);
                    }
                    applyVibrationChanges();
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                return true;
            }
        };
        qsc.registerAction(AudioManager.RINGER_MODE_CHANGED_ACTION, this);
        qsc.registerAction(VIBRATION_STATE_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        applyVibrationChanges();
    }

    private void applyVibrationChanges(){
        int vibrateSetting = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL && vibrateSetting == AudioManager.VIBRATE_SETTING_ON){
            //Sound + vibrate
            mDrawable = R.drawable.ic_qs_vibrate_on;
            mLabel = mContext.getString(R.string.quick_settings_vibration_on);
        }else if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE){
            //Vibrate
            mDrawable = R.drawable.ic_qs_vibrate_on;
            mLabel = mContext.getString(R.string.quick_settings_vibration_on);
        }else{
            //No vibration
            mDrawable = R.drawable.ic_qs_vibrate_off;
            mLabel = mContext.getString(R.string.quick_settings_vibration_off);
        }
        updateQuickSettings();
    }

}
