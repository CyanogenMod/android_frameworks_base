
package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Vibrator;
import android.preference.MultiSelectListPreference;
import android.provider.Settings;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SoundButton extends PowerButton {

    private static final String TAG = "SoundButton";

    private static final int VIBRATE_DURATION = 500; // 0.5s

    private static final IntentFilter INTENT_FILTER = new IntentFilter();
    static {
        INTENT_FILTER.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        INTENT_FILTER.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
    }

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.EXPANDED_RING_MODE));
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.EXPANDED_HAPTIC_FEEDBACK));
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED));
    }

    private final Ringer mSilentRinger = new Ringer(false, AudioManager.VIBRATE_SETTING_OFF,
            AudioManager.RINGER_MODE_SILENT, false);
    private final Ringer mVibrateRinger = new Ringer(true, AudioManager.VIBRATE_SETTING_ON,
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

    private boolean mHapticFeedbackEnabled = false;

    private AudioManager mAudioManager;
    private Vibrator mVibrator;

    public SoundButton() {
        mType = BUTTON_SOUND;
    }

    @Override
    protected void setupButton(View view) {
        super.setupButton(view);
        if (mView != null) {
            Context context = mView.getContext();
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            updateSettings();
        }
    }

    @Override
    protected void updateState() {
        findCurrentState();
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
    }

    @Override
    protected void toggleState() {
        mRingerValuesIndex++;
        if (mRingerValuesIndex > mRingerValues.length - 1) {
            mRingerValuesIndex = 0;
        }
        mRingersIndex = mRingerValues[mRingerValuesIndex];
        Ringer ringer = mRingers[mRingersIndex];
        ringer.execute();
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.SOUND_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    protected void onChangeUri(Uri uri) {
        updateSettings();
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        return INTENT_FILTER;
    }

    private void updateSettings() {
        ContentResolver resolver = mView.getContext().getContentResolver();

        int expandedHapticFeedback = Settings.System.getInt(resolver,
                Settings.System.EXPANDED_HAPTIC_FEEDBACK, 2);
        if (expandedHapticFeedback == 2) {
            mHapticFeedbackEnabled = (Settings.System.getInt(resolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1);
        } else {
            mHapticFeedbackEnabled = (expandedHapticFeedback == 1);
        }

        String[] modes = MultiSelectListPreference.parseStoredValue(Settings.System.getString(
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

        updateState();
        for (int i = 0; i < mRingerValues.length; i++) {
            if (mRingersIndex == mRingerValues[i]) {
                mRingerValuesIndex = i;
                break;
            }
        }
    }

    private void findCurrentState() {
        ContentResolver resolver = mView.getContext().getContentResolver();
        boolean vibrateInSilent = Settings.System.getInt(resolver,
                Settings.System.VIBRATE_IN_SILENT, 0) == 1;
        int vibrateSetting = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        int ringerMode = mAudioManager.getRingerMode();
        Ringer ringer = new Ringer(vibrateInSilent, vibrateSetting, ringerMode, false);
        for (int i = 0; i < mRingers.length; i++) {
            if (mRingers[i].equals(ringer)) {
                mRingersIndex = i;
                break;
            }
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

        void execute() {
            ContentResolver resolver = mView.getContext().getContentResolver();
            Settings.System.putInt(resolver, Settings.System.VIBRATE_IN_SILENT,
                    (mVibrateInSilent ? 1 : 0));
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, mVibrateSetting);
            mAudioManager.setRingerMode(mRingerMode);
            if (mDoHapticFeedback && mHapticFeedbackEnabled) {
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
            return r.mVibrateInSilent == mVibrateInSilent && r.mVibrateSetting == mVibrateSetting
                    && r.mRingerMode == mRingerMode;
        }

    }

}
