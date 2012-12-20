package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class RingerModeTile extends QuickSettingsTile {

    private AudioManager mAudioManager;

    public RingerModeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        mOnClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if(mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL){
                    if(mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER) == AudioManager.VIBRATE_SETTING_ON){
                        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    }else{
                        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }
                }else{
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
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
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(mAudioManager.isSilentMode()){
            mDrawable = R.drawable.ic_qs_ring_off;
            mLabel = mContext.getString(R.string.quick_settings_ringer_off);
        }else{
            mDrawable = R.drawable.ic_qs_ring_on;
            mLabel = mContext.getString(R.string.quick_settings_ringer_on);
        }
        updateQuickSettings();
    }

}
