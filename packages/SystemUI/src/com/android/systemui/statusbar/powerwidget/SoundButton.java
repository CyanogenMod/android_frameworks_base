
package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SoundButton extends PowerButton {

    private static final String TAG = "SoundButton";

    private static final int VIBRATE_DURATION = 250; // 0.25s

    private static final IntentFilter INTENT_FILTER = new IntentFilter();
    static {
        INTENT_FILTER.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        INTENT_FILTER.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
    }

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.EXPANDED_RING_MODE));
    }

    private final Ringer mSilentRinger = new Ringer(false, AudioManager.VIBRATE_SETTING_OFF,
            AudioManager.RINGER_MODE_SILENT, false);
    private final Ringer mVibrateRinger = new Ringer(true, AudioManager.VIBRATE_SETTING_ONLY_SILENT,
            AudioManager.RINGER_MODE_VIBRATE, true);
    private final Ringer mSoundRinger = new Ringer(true, AudioManager.VIBRATE_SETTING_ONLY_SILENT,
            AudioManager.RINGER_MODE_NORMAL, false);
    private final Ringer mSoundVibrateRinger = new Ringer(true, AudioManager.VIBRATE_SETTING_ON,
            AudioManager.RINGER_MODE_NORMAL, true);
    private final Ringer[] mRingers = new Ringer[] {
            mSilentRinger, mVibrateRinger, mSoundRinger, mSoundVibrateRinger
    };
    private int mRingersIndex = 2;

    private int[] mRingerValues = new int[] {
            0, 1, 2, 3
    };
    private int mRingerValuesIndex = 2;

    private AudioManager mAudioManager;

    public SoundButton() {
        mType = BUTTON_SOUND;
    }

    @Override
    protected void setupButton(View view) {
        super.setupButton(view);
        if (mView != null) {
            Context context = mView.getContext();
            updateSettings(context.getContentResolver());
        }
    }

    @Override
    protected void updateState(Context context) {
        findCurrentState(context);
        switch (mRingersIndex) {
            case 0:
                mIcon = R.drawable.stat_silent;
                mState = STATE_DISABLED;
                break;
            case 1:
                mIcon = R.drawable.stat_vibrate_off;
                mState = STATE_DISABLED;
                break;
            case 2:
                mIcon = R.drawable.stat_ring_on;
                mState = STATE_ENABLED;
                break;
            case 3:
                mIcon = R.drawable.stat_ring_vibrate_on;
                mState = STATE_ENABLED;
                break;
        }
        for (int i = 0; i < mRingerValues.length; i++) {
            if (mRingersIndex == mRingerValues[i]) {
                mRingerValuesIndex = i;
                break;
            }
        }
    }

    @Override
    protected void toggleState(Context context) {
        mRingerValuesIndex++;
        if (mRingerValuesIndex > mRingerValues.length - 1) {
            mRingerValuesIndex = 0;
        }
        mRingersIndex = mRingerValues[mRingerValuesIndex];
        if (mRingersIndex > mRingers.length - 1) {
            mRingersIndex = 0;
        }
        Ringer ringer = mRingers[mRingersIndex];
        ringer.execute(context);
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.SOUND_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    protected void onChangeUri(ContentResolver cr, Uri uri) {
        updateSettings(cr);
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        return INTENT_FILTER;
    }

    private void updateSettings(ContentResolver resolver) {
        String[] modes = parseStoredValue(Settings.System.getString(
                resolver, Settings.System.EXPANDED_RING_MODE));
        if (modes == null || modes.length == 0) {
            mRingerValues = new int[] {
                    0, 1, 2, 3
            };
        } else {
            mRingerValues = new int[modes.length];
            for (int i = 0; i < modes.length; i++) {
                mRingerValues[i] = Integer.valueOf(modes[i]);
            }
        }
    }

    private void findCurrentState(Context context) {
        ensureAudioManager(context);

        boolean vibrateInSilent = Settings.System.getInt(context.getContentResolver(),
                Settings.System.VIBRATE_IN_SILENT, 0) == 1;
        int vibrateSetting = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        int ringerMode = mAudioManager.getRingerMode();
        // Sometimes the setting don't quite match up to the states we've defined.
        // In that case, override the reported settings to get us "close" to the
        // defined settings. This bit is a little ugly but oh well.
        if (!vibrateInSilent && ringerMode == AudioManager.RINGER_MODE_SILENT) {
            vibrateSetting = AudioManager.VIBRATE_SETTING_OFF; // match Silent ringer
        } else if (!vibrateInSilent && ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            vibrateInSilent = true; // match either Sound or SoundVibrate ringer
            if (vibrateSetting == AudioManager.VIBRATE_SETTING_OFF) {
                vibrateSetting = AudioManager.VIBRATE_SETTING_ONLY_SILENT; // match Sound ringer
            }
        } else if (vibrateInSilent && ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            vibrateSetting = AudioManager.VIBRATE_SETTING_ONLY_SILENT; // match Vibrate ringer 
        }

        Ringer ringer = new Ringer(vibrateInSilent, vibrateSetting, ringerMode, false);
        for (int i = 0; i < mRingers.length; i++) {
            if (mRingers[i].equals(ringer)) {
                mRingersIndex = i;
                break;
            }
        }
    }

    private void ensureAudioManager(Context context) {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
    }

    private class Ringer {
        final boolean mVibrateInSilent;
        final int mVibrateSetting;
        final int mRingerMode;
        final boolean mDoHapticFeedback;

        Ringer(boolean vibrateInSilent, int vibrateSetting, int ringerMode, boolean doHapticFeedback) {
            mVibrateInSilent = vibrateInSilent;
            mVibrateSetting = vibrateSetting;
            mRingerMode = ringerMode;
            mDoHapticFeedback = doHapticFeedback;
        }

        void execute(Context context) {
            ContentResolver resolver = context.getContentResolver();
            Settings.System.putInt(resolver, Settings.System.VIBRATE_IN_SILENT,
                    (mVibrateInSilent ? 1 : 0));

            ensureAudioManager(context);
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, mVibrateSetting);
            mAudioManager.setRingerMode(mRingerMode);
            if (mDoHapticFeedback && mHapticFeedback) {
                mVibrator.vibrate(VIBRATE_DURATION);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o.getClass() != getClass()) {
                return false;
            }

            Ringer r = (Ringer) o;
            // Silent mode docs: "Ringer mode that will be silent and will not
            // vibrate. (This overrides the vibrate setting.)" If silent mode is
            // set, don't bother checking vibrate since silent overrides. This
            // fixes cases where silent mode is not detected because of "wrong"
            // vibrate state.
            if (mRingerMode == AudioManager.RINGER_MODE_SILENT && (r.mRingerMode == mRingerMode))
                return true;
            return r.mVibrateInSilent == mVibrateInSilent && r.mVibrateSetting == mVibrateSetting
                    && r.mRingerMode == mRingerMode;
        }

    }

}
