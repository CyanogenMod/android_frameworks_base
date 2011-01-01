
package com.android.systemui.statusbar.widget;

import com.android.systemui.R;

import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;

public class SoundButton extends PowerButton {

    static SoundButton ownButton = null;

    public static final int RINGER_MODE_UNKNOWN = 0;

    public static final int RINGER_MODE_SILENT = 1;

    public static final int RINGER_MODE_VIBRATE_ONLY = 2;

    public static final int RINGER_MODE_SOUND_ONLY = 3;

    public static final int RINGER_MODE_SOUND_AND_VIBRATE = 4;

    public static final int MODE_SOUNDVIB_VIB = 0;

    public static final int MODE_SOUND_VIB = 1;

    public static final int MODE_SOUND_SILENT = 2;

    public static final int MODE_SOUNDVIB_VIB_SILENT = 3;

    public static final int MODE_SOUND_VIB_SILENT = 4;

    private static final int DEFAULT_SETTING = 0;

    private static int currentMode;

    private static int getSoundState(Context context) {
        AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        int ringMode = mAudioManager.getRingerMode();
        int vibrateMode = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);

        if (ringMode == AudioManager.RINGER_MODE_NORMAL
                && vibrateMode == AudioManager.VIBRATE_SETTING_ON) {
            return RINGER_MODE_SOUND_AND_VIBRATE;
        } else if (ringMode == AudioManager.RINGER_MODE_NORMAL) {
            return RINGER_MODE_SOUND_ONLY;
        } else if (ringMode == AudioManager.RINGER_MODE_VIBRATE) {
            return RINGER_MODE_VIBRATE_ONLY;
        } else if (ringMode == AudioManager.RINGER_MODE_SILENT) {
            return RINGER_MODE_SILENT;
        }
        return RINGER_MODE_UNKNOWN;
    }

    /**
     * Toggles the state of 2G3G.
     * 
     * @param context
     */
    public void toggleState(Context context) {
        int currentMode = getSoundState(context);
        AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        switch (currentMode) {
            case RINGER_MODE_SOUND_AND_VIBRATE:
                if (supports(RINGER_MODE_SOUND_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                } else if (supports(RINGER_MODE_VIBRATE_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                } else if (supports(RINGER_MODE_SILENT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 0);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                } else { // Fall Back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                }
                break;
            case RINGER_MODE_SOUND_ONLY:
                if (supports(RINGER_MODE_VIBRATE_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                } else if (supports(RINGER_MODE_SILENT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 0);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                } else if (supports(RINGER_MODE_SOUND_AND_VIBRATE)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                } else { // Fall back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                }
                break;

            case RINGER_MODE_VIBRATE_ONLY:
                if (supports(RINGER_MODE_SILENT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 0);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_OFF);
                } else if (supports(RINGER_MODE_SOUND_AND_VIBRATE)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                } else if (supports(RINGER_MODE_SOUND_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                } else { // Fall Back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                }
                break;
            case RINGER_MODE_SILENT:
                if (supports(RINGER_MODE_SOUND_AND_VIBRATE)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                } else if (supports(RINGER_MODE_SOUND_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                } else if (supports(RINGER_MODE_VIBRATE_ONLY)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ONLY_SILENT);
                } else { // Fall Back
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.VIBRATE_IN_SILENT, 1);
                    mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                            AudioManager.VIBRATE_SETTING_ON);
                }
                break;
            default:
                Settings.System.putInt(context.getContentResolver(),
                        Settings.System.VIBRATE_IN_SILENT, 1);
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
                        AudioManager.VIBRATE_SETTING_ON);

        }
    }

    private boolean supports(int ringerMode) {
        switch (ringerMode) {
            case RINGER_MODE_SILENT:
                if (currentMode == MODE_SOUND_SILENT || currentMode == MODE_SOUNDVIB_VIB_SILENT
                        || currentMode == MODE_SOUND_VIB_SILENT)
                    return true;
                break;
            case RINGER_MODE_VIBRATE_ONLY:
                if (currentMode == MODE_SOUND_VIB || currentMode == MODE_SOUNDVIB_VIB
                        || currentMode == MODE_SOUNDVIB_VIB_SILENT
                        || currentMode == MODE_SOUND_VIB_SILENT)
                    return true;
                break;
            case RINGER_MODE_SOUND_ONLY:
                if (currentMode == MODE_SOUND_VIB || currentMode == MODE_SOUND_SILENT
                        || currentMode == MODE_SOUND_VIB_SILENT)
                    return true;
                break;
            case RINGER_MODE_SOUND_AND_VIBRATE:
                if (currentMode == MODE_SOUNDVIB_VIB || currentMode == MODE_SOUNDVIB_VIB_SILENT)
                    return true;
        }

        return false;
    }

    public static SoundButton getInstance() {
        if (ownButton == null)
            ownButton = new SoundButton();

        return ownButton;
    }

    @Override
    public void updateState(Context context) {
        int soundState = getSoundState(context);
        currentMode = Settings.System.getInt(context.getContentResolver(),
                Settings.System.EXPANDED_RING_MODE, DEFAULT_SETTING);

        switch (soundState) {
            case RINGER_MODE_SOUND_AND_VIBRATE:
                currentIcon = R.drawable.stat_ring_on;
                currentState = PowerButton.STATE_ENABLED;
                break;
            case RINGER_MODE_SOUND_ONLY:
                currentIcon = R.drawable.stat_ring_on;
                currentState = PowerButton.STATE_INTERMEDIATE;
                break;
            case RINGER_MODE_VIBRATE_ONLY:
                currentIcon = R.drawable.stat_vibrate_off;
                currentState = PowerButton.STATE_DISABLED;
                break;
            case RINGER_MODE_SILENT:
                currentIcon = R.drawable.stat_silent;
                currentState = PowerButton.STATE_DISABLED;
                break;

        }
    }
}
