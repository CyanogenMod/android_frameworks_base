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
            final QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mOnClick = new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                qsc.mBar.collapseAllPanels(true);
                AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
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
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        mDrawable = R.drawable.ic_qs_volume;
        mLabel = mContext.getString(R.string.quick_settings_volume);
    }
}
