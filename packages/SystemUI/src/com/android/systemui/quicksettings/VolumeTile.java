package com.android.systemui.quicksettings;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class VolumeTile extends QuickSettingsTile {

    public VolumeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, 1);
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                return true;
            }
        };
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        mDrawable = R.drawable.ic_qs_ring_on;
        mLabel = mContext.getString(R.string.quick_settings_volume);
    }
}
