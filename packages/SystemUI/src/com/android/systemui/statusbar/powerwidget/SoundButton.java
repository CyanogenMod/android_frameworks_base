package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Vibrator;
import android.provider.Settings;

public class SoundButton extends PowerButton {

    public static final int RINGER_MODE_UNKNOWN = 0;
    public static final int RINGER_MODE_SILENT = 1;
    public static final int RINGER_MODE_VIBRATE_ONLY = 2;
    public static final int RINGER_MODE_SOUND_ONLY = 3;
    public static final int RINGER_MODE_SOUND_AND_VIBRATE = 4;

    public static final int CM_MODE_SOUNDVIB_VIB = 0;
    public static final int CM_MODE_SOUND_VIB = 1;
    public static final int CM_MODE_SOUND_SILENT = 2;
    public static final int CM_MODE_SOUNDVIB_VIB_SILENT = 3;
    public static final int CM_MODE_SOUND_VIB_SILENT = 4;
    public static final int CM_MODE_SOUNDVIB_SOUND_VIB_SILENT = 5;

    public static final int VIBRATE_DURATION = 500; // 0.5s

    public static AudioManager AUDIO_MANAGER = null;
    public static Vibrator VIBRATOR = null;

    public SoundButton() { mType = BUTTON_SOUND; }

    @Override
    protected void updateState() {
        switch (getSoundState(mView.getContext())) {
        case RINGER_MODE_SOUND_AND_VIBRATE:
                mIcon = R.drawable.stat_ring_vibrate_on;
                mState = STATE_ENABLED;
            break;
        case RINGER_MODE_SOUND_ONLY:
                mIcon = R.drawable.stat_ring_on;
                mState = STATE_ENABLED;
            break;
        case RINGER_MODE_VIBRATE_ONLY:
                mIcon = R.drawable.stat_vibrate_off;
                mState = STATE_DISABLED;
            break;
        case RINGER_MODE_SILENT:
                mIcon = R.drawable.stat_silent;
                mState = STATE_DISABLED;
            break;

        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        int currentState = getSoundState(context);

        // services should be initialized in the last call, but we do this for completeness anyway
        initServices(context);

        switch (currentState) {
            // order of check: soundvib sound vib silent
            case RINGER_MODE_SOUND_AND_VIBRATE:
                if (supports(RINGER_MODE_SOUND_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                } else if (supports(RINGER_MODE_VIBRATE_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                } else if (supports(RINGER_MODE_SILENT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 0);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                } else { // Fall Back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                }
                break;
            case RINGER_MODE_SOUND_ONLY:
                if (supports(RINGER_MODE_VIBRATE_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                } else if (supports(RINGER_MODE_SILENT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 0);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                } else if (supports(RINGER_MODE_SOUND_AND_VIBRATE)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                } else { // Fall back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                }
                break;

            case RINGER_MODE_VIBRATE_ONLY:
                if (supports(RINGER_MODE_SILENT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 0);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                } else if (supports(RINGER_MODE_SOUND_AND_VIBRATE)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                } else if (supports(RINGER_MODE_SOUND_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                } else { // Fall Back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                }
                break;
            case RINGER_MODE_SILENT:
                if (supports(RINGER_MODE_SOUND_AND_VIBRATE)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                } else if (supports(RINGER_MODE_SOUND_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                } else if (supports(RINGER_MODE_VIBRATE_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                } else { // Fall Back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    VIBRATOR.vibrate(VIBRATE_DURATION);
                }
                break;
            default:
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.VIBRATE_IN_SILENT, 1);
                AUDIO_MANAGER.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                AUDIO_MANAGER.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                        AudioManager.VIBRATE_SETTING_ON);
                VIBRATOR.vibrate(VIBRATE_DURATION);
        }
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        // note, we don't actually have an "onReceive", so the caught intent will be ignored, but we want
        // to catch it anyway so the ringer status is updated if changed externally :D
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
        return filter;
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.SOUND_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    private boolean supports(int ringerMode) {
        // returns true if ringerMode is one of the modes in the selected sound option for the power widget
        int currentMode = getCurrentCMMode(mView.getContext());

        switch (ringerMode) {
            case RINGER_MODE_SILENT:
                if (currentMode == CM_MODE_SOUND_SILENT || currentMode == CM_MODE_SOUNDVIB_VIB_SILENT
                        || currentMode == CM_MODE_SOUND_VIB_SILENT
                        || currentMode == CM_MODE_SOUNDVIB_SOUND_VIB_SILENT)
                    return true;
                break;
            case RINGER_MODE_VIBRATE_ONLY:
                if (currentMode == CM_MODE_SOUND_VIB || currentMode == CM_MODE_SOUNDVIB_VIB
                        || currentMode == CM_MODE_SOUNDVIB_VIB_SILENT
                        || currentMode == CM_MODE_SOUND_VIB_SILENT
                        || currentMode == CM_MODE_SOUNDVIB_SOUND_VIB_SILENT)
                    return true;
                break;
            case RINGER_MODE_SOUND_ONLY:
                if (currentMode == CM_MODE_SOUND_VIB || currentMode == CM_MODE_SOUND_SILENT
                        || currentMode == CM_MODE_SOUND_VIB_SILENT
                        || currentMode == CM_MODE_SOUNDVIB_SOUND_VIB_SILENT)
                    return true;
                break;
            case RINGER_MODE_SOUND_AND_VIBRATE:
                if (currentMode == CM_MODE_SOUNDVIB_VIB || currentMode == CM_MODE_SOUNDVIB_VIB_SILENT
                        || currentMode == CM_MODE_SOUNDVIB_SOUND_VIB_SILENT)
                    return true;
        }

        return false;
    }

    private static int getSoundState(Context context) {
        // ensure our services are initialized
        initServices(context);

        int ringMode = AUDIO_MANAGER.getRingerMode();
        int vibrateMode = AUDIO_MANAGER.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);

        if (ringMode == AudioManager.RINGER_MODE_NORMAL && vibrateMode == AudioManager.VIBRATE_SETTING_ON) {
            return RINGER_MODE_SOUND_AND_VIBRATE;
        } else if (ringMode == AudioManager.RINGER_MODE_NORMAL) {
            return RINGER_MODE_SOUND_ONLY;
        } else if (ringMode == AudioManager.RINGER_MODE_VIBRATE) {
            return RINGER_MODE_VIBRATE_ONLY;
        } else if (ringMode == AudioManager.RINGER_MODE_SILENT) {
            return RINGER_MODE_SILENT;
        } else {
            return RINGER_MODE_UNKNOWN;
        }
    }

    private static void initServices(Context context) {
        if(AUDIO_MANAGER == null) {
            AUDIO_MANAGER = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        if(VIBRATOR == null) {
            VIBRATOR = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private static int getCurrentCMMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.EXPANDED_RING_MODE,
                CM_MODE_SOUNDVIB_SOUND_VIB_SILENT);
    }
}
