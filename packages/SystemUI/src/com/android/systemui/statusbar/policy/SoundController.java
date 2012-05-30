package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.media.AudioManager;
import android.content.IntentFilter;

import com.android.systemui.R;

public class SoundController extends BroadcastReceiver  implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.SoundController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mSoundMode;

    public SoundController(Context context, CompoundButton checkbox) {
	this(context);
        mContext = context;
        mSoundMode = getSoundMode();
        mCheckBox = checkbox;
        checkbox.setChecked(mSoundMode);
        checkbox.setOnCheckedChangeListener(this);
    }

    public SoundController(Context context) {
        mContext = context;

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        context.registerReceiver(this, filter);
    }

    public void release() {
        mContext.unregisterReceiver(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
	AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(checked ? AudioManager.RINGER_MODE_NORMAL : AudioManager.RINGER_MODE_SILENT);
    }

    public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                mCheckBox.setChecked(getSoundMode());
            }
    }

    private boolean getSoundMode() {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

	switch (am.getRingerMode()) {
	    case AudioManager.RINGER_MODE_SILENT:
		return false;
	    case AudioManager.RINGER_MODE_NORMAL:
		return true;
	}
        return false;
    }

}
