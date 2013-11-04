package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class RingerVibrationModeTile extends QuickSettingsTile {

    private static final String SEPARATOR = "OV=I=XseparatorX=I=VO";

    // Define the available ringer modes
    private final Ringer mSilentRinger = new Ringer(false,
            AudioManager.VIBRATE_SETTING_OFF,
            AudioManager.RINGER_MODE_SILENT, false);
    private final Ringer mVibrateRinger = new Ringer(true,
            AudioManager.VIBRATE_SETTING_ONLY_SILENT,
            AudioManager.RINGER_MODE_VIBRATE, true);
    private final Ringer mSoundRinger = new Ringer(true,
            AudioManager.VIBRATE_SETTING_ONLY_SILENT,
            AudioManager.RINGER_MODE_NORMAL, false);
    private final Ringer mSoundVibrateRinger = new Ringer(true,
            AudioManager.VIBRATE_SETTING_ON,
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
    private Handler mHandler;
    private SoundModesChangedObserver mSoundModesChangedObserver;

    public RingerVibrationModeTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();

        // Load the available ringer modes
        updateSettings(mContext.getContentResolver());

        // Start observing for changes
        mSoundModesChangedObserver = new SoundModesChangedObserver(mHandler);
        mSoundModesChangedObserver.startObserving();

        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
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
        updateState();
        updateQuickSettings();
    }

    protected void updateState() {
        // The title does not change
        mLabel = mContext.getString(R.string.quick_settings_ringer_normal);

        // The icon will change depending on index
        findCurrentState();
        switch (mRingersIndex) {
            case 0:
                mDrawable = R.drawable.ic_qs_ring_off;
                break;
            case 1:
                mDrawable = R.drawable.ic_qs_vibrate_on;
                break;
            case 2:
                mDrawable = R.drawable.ic_qs_ring_on;
                break;
            case 3:
                mDrawable = R.drawable.ic_qs_ring_vibrate_on;
                break;
        }

        for (int i = 0; i < mRingerValues.length; i++) {
            if (mRingersIndex == mRingerValues[i]) {
                mRingerValuesIndex = i;
                break;
            }
        }
    }

    protected void toggleState() {
        mRingerValuesIndex++;
        if (mRingerValuesIndex > mRingerValues.length - 1) {
            mRingerValuesIndex = 0;
        }

        mRingersIndex = mRingerValues[mRingerValuesIndex];
        if (mRingersIndex > mRingers.length - 1) {
            mRingersIndex = 0;
        }

        Ringer ringer = mRingers[mRingersIndex];
        ringer.execute(mContext);
    }

    public String[] parseStoredValue(CharSequence val) {
        if (TextUtils.isEmpty(val)) {
          return null;
        } else {
          return val.toString().split(SEPARATOR);
        }
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

    private void findCurrentState() {
        boolean vibrateInSilent = Settings.System.getInt(mContext.getContentResolver(),
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

    private class Ringer {
        final boolean mVibrateInSilent;
        final int mVibrateSetting;
        final int mRingerMode;

        Ringer(boolean vibrateInSilent, int vibrateSetting, int ringerMode, boolean doHapticFeedback) {
            mVibrateInSilent = vibrateInSilent;
            mVibrateSetting = vibrateSetting;
            mRingerMode = ringerMode;
        }

        void execute(Context context) {
            ContentResolver resolver = context.getContentResolver();
            Settings.System.putInt(resolver, Settings.System.VIBRATE_IN_SILENT,
                    (mVibrateInSilent ? 1 : 0));

            // If we are setting a vibrating state, vibrate to indicate it
            if (mVibrateSetting == AudioManager.VIBRATE_SETTING_ON
                    || mVibrateSetting == AudioManager.VIBRATE_SETTING_ONLY_SILENT) {
                Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                vibrator.vibrate(250);
            }

            // Set the desired state
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER, mVibrateSetting);
            mAudioManager.setRingerMode(mRingerMode);
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
            if (mRingerMode == AudioManager.RINGER_MODE_SILENT
                    && (r.mRingerMode == mRingerMode)) {
                return true;
            }

            return r.mVibrateInSilent == mVibrateInSilent
                    && r.mVibrateSetting == mVibrateSetting
                    && r.mRingerMode == mRingerMode;
        }
    }

    /**
     *  ContentObserver to watch for Quick Settings tiles changes
     * @author dvtonder
     *
     */
    private class SoundModesChangedObserver extends ContentObserver {
        public SoundModesChangedObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings(mContext.getContentResolver());
            applyVibrationChanges();
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.EXPANDED_RING_MODE),
                    false, this);
        }
    }
}
