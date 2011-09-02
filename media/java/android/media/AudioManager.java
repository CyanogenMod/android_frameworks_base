/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.app.ProfileGroup;
import android.app.ProfileManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

import java.util.HashMap;

/**
 * AudioManager provides access to volume and ringer mode control.
 * <p>
 * Use <code>Context.getSystemService(Context.AUDIO_SERVICE)</code> to get
 * an instance of this class.
 */
public class AudioManager {

    private final Context mContext;
    private final Handler mHandler;

    private final ProfileManager mProfileManager;

    private static String TAG = "AudioManager";
    private static boolean DEBUG = false;
    private static boolean localLOGV = DEBUG || android.util.Config.LOGV;

    /**
     * Broadcast intent, a hint for applications that audio is about to become
     * 'noisy' due to a change in audio outputs. For example, this intent may
     * be sent when a wired headset is unplugged, or when an A2DP audio
     * sink is disconnected, and the audio system is about to automatically
     * switch audio route to the speaker. Applications that are controlling
     * audio streams may consider pausing, reducing volume or some other action
     * on receipt of this intent so as not to surprise the user with audio
     * from the speaker.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY";

    /**
     * Sticky broadcast intent action indicating that the ringer mode has
     * changed. Includes the new ringer mode.
     *
     * @see #EXTRA_RINGER_MODE
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String RINGER_MODE_CHANGED_ACTION = "android.media.RINGER_MODE_CHANGED";

    /**
     * The new ringer mode.
     *
     * @see #RINGER_MODE_CHANGED_ACTION
     * @see #RINGER_MODE_NORMAL
     * @see #RINGER_MODE_SILENT
     * @see #RINGER_MODE_VIBRATE
     */
    public static final String EXTRA_RINGER_MODE = "android.media.EXTRA_RINGER_MODE";

    /**
     * Broadcast intent action indicating that the vibrate setting has
     * changed. Includes the vibrate type and its new setting.
     *
     * @see #EXTRA_VIBRATE_TYPE
     * @see #EXTRA_VIBRATE_SETTING
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String VIBRATE_SETTING_CHANGED_ACTION = "android.media.VIBRATE_SETTING_CHANGED";

    /**
     * @hide Broadcast intent when the volume for a particular stream type changes.
     * Includes the stream, the new volume and previous volumes
     *
     * @see #EXTRA_VOLUME_STREAM_TYPE
     * @see #EXTRA_VOLUME_STREAM_VALUE
     * @see #EXTRA_PREV_VOLUME_STREAM_VALUE
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

    /**
     * The new vibrate setting for a particular type.
     *
     * @see #VIBRATE_SETTING_CHANGED_ACTION
     * @see #EXTRA_VIBRATE_TYPE
     * @see #VIBRATE_SETTING_ON
     * @see #VIBRATE_SETTING_OFF
     * @see #VIBRATE_SETTING_ONLY_SILENT
     */
    public static final String EXTRA_VIBRATE_SETTING = "android.media.EXTRA_VIBRATE_SETTING";

    /**
     * The vibrate type whose setting has changed.
     *
     * @see #VIBRATE_SETTING_CHANGED_ACTION
     * @see #VIBRATE_TYPE_NOTIFICATION
     * @see #VIBRATE_TYPE_RINGER
     */
    public static final String EXTRA_VIBRATE_TYPE = "android.media.EXTRA_VIBRATE_TYPE";

    /**
     * @hide The stream type for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    /**
     * @hide The volume associated with the stream for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_VALUE =
        "android.media.EXTRA_VOLUME_STREAM_VALUE";

    /**
     * @hide The previous volume associated with the stream for the volume changed intent.
     */
    public static final String EXTRA_PREV_VOLUME_STREAM_VALUE =
        "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    /** The audio stream for phone calls */
    public static final int STREAM_VOICE_CALL = AudioSystem.STREAM_VOICE_CALL;
    /** The audio stream for system sounds */
    public static final int STREAM_SYSTEM = AudioSystem.STREAM_SYSTEM;
    /** The audio stream for the phone ring */
    public static final int STREAM_RING = AudioSystem.STREAM_RING;
    /** The audio stream for music playback */
    public static final int STREAM_MUSIC = AudioSystem.STREAM_MUSIC;
    /** The audio stream for alarms */
    public static final int STREAM_ALARM = AudioSystem.STREAM_ALARM;
    /** The audio stream for notifications */
    public static final int STREAM_NOTIFICATION = AudioSystem.STREAM_NOTIFICATION;
    /** @hide The audio stream for phone calls when connected to bluetooth */
    public static final int STREAM_BLUETOOTH_SCO = AudioSystem.STREAM_BLUETOOTH_SCO;
    /** @hide The audio stream for enforced system sounds in certain countries (e.g camera in Japan) */
    public static final int STREAM_SYSTEM_ENFORCED = AudioSystem.STREAM_SYSTEM_ENFORCED;
    /** The audio stream for DTMF Tones */
    public static final int STREAM_DTMF = AudioSystem.STREAM_DTMF;
    /** @hide The audio stream for text to speech (TTS) */
    public static final int STREAM_TTS = AudioSystem.STREAM_TTS;
    /** Number of audio streams */
    /**
     * @deprecated Use AudioSystem.getNumStreamTypes() instead
     */
    @Deprecated public static final int NUM_STREAMS = AudioSystem.NUM_STREAMS;


    /**  @hide Default volume index values for audio streams */
    public static final int[] DEFAULT_STREAM_VOLUME = new int[] {
        4,  // STREAM_VOICE_CALL
        7,  // STREAM_SYSTEM
        5,  // STREAM_RING
        11, // STREAM_MUSIC
        6,  // STREAM_ALARM
        5,  // STREAM_NOTIFICATION
        11,  // STREAM_BLUETOOTH_SCO
        7,  // STREAM_SYSTEM_ENFORCED
        11, // STREAM_DTMF
        11,  // STREAM_TTS
        11 // STREAM_FM
    };

    /**
     * Increase the ringer volume.
     *
     * @see #adjustVolume(int, int)
     * @see #adjustStreamVolume(int, int, int)
     */
    public static final int ADJUST_RAISE = 1;

    /**
     * Decrease the ringer volume.
     *
     * @see #adjustVolume(int, int)
     * @see #adjustStreamVolume(int, int, int)
     */
    public static final int ADJUST_LOWER = -1;

    /**
     * Maintain the previous ringer volume. This may be useful when needing to
     * show the volume toast without actually modifying the volume.
     *
     * @see #adjustVolume(int, int)
     * @see #adjustStreamVolume(int, int, int)
     */
    public static final int ADJUST_SAME = 0;

    // Flags should be powers of 2!

    /**
     * Show a toast containing the current volume.
     *
     * @see #adjustStreamVolume(int, int, int)
     * @see #adjustVolume(int, int)
     * @see #setStreamVolume(int, int, int)
     * @see #setRingerMode(int)
     */
    public static final int FLAG_SHOW_UI = 1 << 0;

    /**
     * Whether to include ringer modes as possible options when changing volume.
     * For example, if true and volume level is 0 and the volume is adjusted
     * with {@link #ADJUST_LOWER}, then the ringer mode may switch the silent or
     * vibrate mode.
     * <p>
     * By default this is on for the ring stream. If this flag is included,
     * this behavior will be present regardless of the stream type being
     * affected by the ringer mode.
     *
     * @see #adjustVolume(int, int)
     * @see #adjustStreamVolume(int, int, int)
     */
    public static final int FLAG_ALLOW_RINGER_MODES = 1 << 1;

    /**
     * Whether to play a sound when changing the volume.
     * <p>
     * If this is given to {@link #adjustVolume(int, int)} or
     * {@link #adjustSuggestedStreamVolume(int, int, int)}, it may be ignored
     * in some cases (for example, the decided stream type is not
     * {@link AudioManager#STREAM_RING}, or the volume is being adjusted
     * downward).
     *
     * @see #adjustStreamVolume(int, int, int)
     * @see #adjustVolume(int, int)
     * @see #setStreamVolume(int, int, int)
     */
    public static final int FLAG_PLAY_SOUND = 1 << 2;

    /**
     * Removes any sounds/vibrate that may be in the queue, or are playing (related to
     * changing volume).
     */
    public static final int FLAG_REMOVE_SOUND_AND_VIBRATE = 1 << 3;

    /**
     * Whether to vibrate if going into the vibrate ringer mode.
     */
    public static final int FLAG_VIBRATE = 1 << 4;

    /**
     * Ringer mode that will be silent and will not vibrate. (This overrides the
     * vibrate setting.)
     *
     * @see #setRingerMode(int)
     * @see #getRingerMode()
     */
    public static final int RINGER_MODE_SILENT = 0;

    /**
     * Ringer mode that will be silent and will vibrate. (This will cause the
     * phone ringer to always vibrate, but the notification vibrate to only
     * vibrate if set.)
     *
     * @see #setRingerMode(int)
     * @see #getRingerMode()
     */
    public static final int RINGER_MODE_VIBRATE = 1;

    /**
     * Ringer mode that may be audible and may vibrate. It will be audible if
     * the volume before changing out of this mode was audible. It will vibrate
     * if the vibrate setting is on.
     *
     * @see #setRingerMode(int)
     * @see #getRingerMode()
     */
    public static final int RINGER_MODE_NORMAL = 2;

    /**
     * Vibrate type that corresponds to the ringer.
     *
     * @see #setVibrateSetting(int, int)
     * @see #getVibrateSetting(int)
     * @see #shouldVibrate(int)
     */
    public static final int VIBRATE_TYPE_RINGER = 0;

    /**
     * Vibrate type that corresponds to notifications.
     *
     * @see #setVibrateSetting(int, int)
     * @see #getVibrateSetting(int)
     * @see #shouldVibrate(int)
     */
    public static final int VIBRATE_TYPE_NOTIFICATION = 1;

    /**
     * Vibrate setting that suggests to never vibrate.
     *
     * @see #setVibrateSetting(int, int)
     * @see #getVibrateSetting(int)
     */
    public static final int VIBRATE_SETTING_OFF = 0;

    /**
     * Vibrate setting that suggests to vibrate when possible.
     *
     * @see #setVibrateSetting(int, int)
     * @see #getVibrateSetting(int)
     */
    public static final int VIBRATE_SETTING_ON = 1;

    /**
     * Vibrate setting that suggests to only vibrate when in the vibrate ringer
     * mode.
     *
     * @see #setVibrateSetting(int, int)
     * @see #getVibrateSetting(int)
     */
    public static final int VIBRATE_SETTING_ONLY_SILENT = 2;

    /**
     * Suggests using the default stream type. This may not be used in all
     * places a stream type is needed.
     */
    public static final int USE_DEFAULT_STREAM_TYPE = Integer.MIN_VALUE;

    private static IAudioService sService;

    /**
     * @hide
     */
    public AudioManager(Context context) {
        mContext = context;
        mHandler = new Handler(context.getMainLooper());
        mProfileManager = (ProfileManager)context.getSystemService(Context.PROFILE_SERVICE);
    }

    private static IAudioService getService()
    {
        if (sService != null) {
            return sService;
        }
        IBinder b = ServiceManager.getService(Context.AUDIO_SERVICE);
        sService = IAudioService.Stub.asInterface(b);
        return sService;
    }

    /**
     * Adjusts the volume of a particular stream by one step in a direction.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param streamType The stream type to adjust. One of {@link #STREAM_VOICE_CALL},
     * {@link #STREAM_SYSTEM}, {@link #STREAM_RING}, {@link #STREAM_MUSIC} or
     * {@link #STREAM_ALARM}
     * @param direction The direction to adjust the volume. One of
     *            {@link #ADJUST_LOWER}, {@link #ADJUST_RAISE}, or
     *            {@link #ADJUST_SAME}.
     * @param flags One or more flags.
     * @see #adjustVolume(int, int)
     * @see #setStreamVolume(int, int, int)
     */
    public void adjustStreamVolume(int streamType, int direction, int flags) {
        IAudioService service = getService();
        try {
            service.adjustStreamVolume(streamType, direction, flags);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in adjustStreamVolume", e);
        }
    }

    /**
     * Adjusts the volume of the most relevant stream. For example, if a call is
     * active, it will have the highest priority regardless of if the in-call
     * screen is showing. Another example, if music is playing in the background
     * and a call is not active, the music stream will be adjusted.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param direction The direction to adjust the volume. One of
     *            {@link #ADJUST_LOWER}, {@link #ADJUST_RAISE}, or
     *            {@link #ADJUST_SAME}.
     * @param flags One or more flags.
     * @see #adjustSuggestedStreamVolume(int, int, int)
     * @see #adjustStreamVolume(int, int, int)
     * @see #setStreamVolume(int, int, int)
     */
    public void adjustVolume(int direction, int flags) {
        IAudioService service = getService();
        try {
            service.adjustVolume(direction, flags);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in adjustVolume", e);
        }
    }

    /**
     * Adjusts the volume of the most relevant stream, or the given fallback
     * stream.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param direction The direction to adjust the volume. One of
     *            {@link #ADJUST_LOWER}, {@link #ADJUST_RAISE}, or
     *            {@link #ADJUST_SAME}.
     * @param suggestedStreamType The stream type that will be used if there
     *            isn't a relevant stream. {@link #USE_DEFAULT_STREAM_TYPE} is valid here.
     * @param flags One or more flags.
     * @see #adjustVolume(int, int)
     * @see #adjustStreamVolume(int, int, int)
     * @see #setStreamVolume(int, int, int)
     */
    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags) {
        IAudioService service = getService();
        try {
            service.adjustSuggestedStreamVolume(direction, suggestedStreamType, flags);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in adjustVolume", e);
        }
    }

    /**
     * Returns the current ringtone mode.
     *
     * @return The current ringtone mode, one of {@link #RINGER_MODE_NORMAL},
     *         {@link #RINGER_MODE_SILENT}, or {@link #RINGER_MODE_VIBRATE}.
     * @see #setRingerMode(int)
     */
    public int getRingerMode() {
        IAudioService service = getService();
        try {
            return service.getRingerMode();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in getRingerMode", e);
            return RINGER_MODE_NORMAL;
        }
    }

    /**
     * Returns the maximum volume index for a particular stream.
     *
     * @param streamType The stream type whose maximum volume index is returned.
     * @return The maximum valid volume index for the stream.
     * @see #getStreamVolume(int)
     */
    public int getStreamMaxVolume(int streamType) {
        IAudioService service = getService();
        try {
            return service.getStreamMaxVolume(streamType);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in getStreamMaxVolume", e);
            return 0;
        }
    }

    /**
     * Returns the current volume index for a particular stream.
     *
     * @param streamType The stream type whose volume index is returned.
     * @return The current volume index for the stream.
     * @see #getStreamMaxVolume(int)
     * @see #setStreamVolume(int, int, int)
     */
    public int getStreamVolume(int streamType) {
        IAudioService service = getService();
        try {
            return service.getStreamVolume(streamType);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in getStreamVolume", e);
            return 0;
        }
    }

    /**
     * Sets the ringer mode.
     * <p>
     * Silent mode will mute the volume and will not vibrate. Vibrate mode will
     * mute the volume and vibrate. Normal mode will be audible and may vibrate
     * according to user settings.
     *
     * @param ringerMode The ringer mode, one of {@link #RINGER_MODE_NORMAL},
     *            {@link #RINGER_MODE_SILENT}, or {@link #RINGER_MODE_VIBRATE}.
     * @see #getRingerMode()
     */
    public void setRingerMode(int ringerMode) {
        IAudioService service = getService();
        try {
            service.setRingerMode(ringerMode);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setRingerMode", e);
        }
    }

    /**
     * Sets the volume index for a particular stream.
     *
     * @param streamType The stream whose volume index should be set.
     * @param index The volume index to set. See
     *            {@link #getStreamMaxVolume(int)} for the largest valid value.
     * @param flags One or more flags.
     * @see #getStreamMaxVolume(int)
     * @see #getStreamVolume(int)
     */
    public void setStreamVolume(int streamType, int index, int flags) {
        IAudioService service = getService();
        try {
            service.setStreamVolume(streamType, index, flags);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setStreamVolume", e);
        }
    }

    /**
     * Solo or unsolo a particular stream. All other streams are muted.
     * <p>
     * The solo command is protected against client process death: if a process
     * with an active solo request on a stream dies, all streams that were muted
     * because of this request will be unmuted automatically.
     * <p>
     * The solo requests for a given stream are cumulative: the AudioManager
     * can receive several solo requests from one or more clients and the stream
     * will be unsoloed only when the same number of unsolo requests are received.
     * <p>
     * For a better user experience, applications MUST unsolo a soloed stream
     * in onPause() and solo is again in onResume() if appropriate.
     *
     * @param streamType The stream to be soloed/unsoloed.
     * @param state The required solo state: true for solo ON, false for solo OFF
     */
    public void setStreamSolo(int streamType, boolean state) {
        IAudioService service = getService();
        try {
            service.setStreamSolo(streamType, state, mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setStreamSolo", e);
        }
    }

    /**
     * Mute or unmute an audio stream.
     * <p>
     * The mute command is protected against client process death: if a process
     * with an active mute request on a stream dies, this stream will be unmuted
     * automatically.
     * <p>
     * The mute requests for a given stream are cumulative: the AudioManager
     * can receive several mute requests from one or more clients and the stream
     * will be unmuted only when the same number of unmute requests are received.
     * <p>
     * For a better user experience, applications MUST unmute a muted stream
     * in onPause() and mute is again in onResume() if appropriate.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param streamType The stream to be muted/unmuted.
     * @param state The required mute state: true for mute ON, false for mute OFF
     */
    public void setStreamMute(int streamType, boolean state) {
        IAudioService service = getService();
        try {
            service.setStreamMute(streamType, state, mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setStreamMute", e);
        }
    }

    /**
     * Returns whether a particular type should vibrate according to user
     * settings and the current ringer mode.
     * <p>
     * This shouldn't be needed by most clients that use notifications to
     * vibrate. The notification manager will not vibrate if the policy doesn't
     * allow it, so the client should always set a vibrate pattern and let the
     * notification manager control whether or not to actually vibrate.
     *
     * @param vibrateType The type of vibrate. One of
     *            {@link #VIBRATE_TYPE_NOTIFICATION} or
     *            {@link #VIBRATE_TYPE_RINGER}.
     * @return Whether the type should vibrate at the instant this method is
     *         called.
     * @see #setVibrateSetting(int, int)
     * @see #getVibrateSetting(int)
     */
    public boolean shouldVibrate(int vibrateType) {
        String packageName = mContext.getPackageName();
        // Don't apply profiles for "android" context, as these could
        // come from the NotificationManager, and originate from a real package.
        if(!packageName.equals("android")){
            ProfileGroup profileGroup = mProfileManager.getActiveProfileGroup(packageName);
            if(profileGroup != null){
                Log.v(TAG, "shouldVibrate, group: " + profileGroup.getUuid()
                        + " mode: " + profileGroup.getVibrateMode());
                switch(profileGroup.getVibrateMode()){
                    case OVERRIDE :
                        return true;
                    case SUPPRESS :
                        return false;
                    case DEFAULT :
                        // Drop through
                }
            }
        }else{
            Log.v(TAG, "Not applying override for 'android' package");
        }
        IAudioService service = getService();
        try {
            return service.shouldVibrate(vibrateType);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in shouldVibrate", e);
            return false;
        }
    }

    /**
     * Returns whether the user's vibrate setting for a vibrate type.
     * <p>
     * This shouldn't be needed by most clients that want to vibrate, instead
     * see {@link #shouldVibrate(int)}.
     *
     * @param vibrateType The type of vibrate. One of
     *            {@link #VIBRATE_TYPE_NOTIFICATION} or
     *            {@link #VIBRATE_TYPE_RINGER}.
     * @return The vibrate setting, one of {@link #VIBRATE_SETTING_ON},
     *         {@link #VIBRATE_SETTING_OFF}, or
     *         {@link #VIBRATE_SETTING_ONLY_SILENT}.
     * @see #setVibrateSetting(int, int)
     * @see #shouldVibrate(int)
     */
    public int getVibrateSetting(int vibrateType) {
        IAudioService service = getService();
        try {
            return service.getVibrateSetting(vibrateType);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in getVibrateSetting", e);
            return VIBRATE_SETTING_OFF;
        }
    }

    /**
     * Sets the setting for when the vibrate type should vibrate.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param vibrateType The type of vibrate. One of
     *            {@link #VIBRATE_TYPE_NOTIFICATION} or
     *            {@link #VIBRATE_TYPE_RINGER}.
     * @param vibrateSetting The vibrate setting, one of
     *            {@link #VIBRATE_SETTING_ON},
     *            {@link #VIBRATE_SETTING_OFF}, or
     *            {@link #VIBRATE_SETTING_ONLY_SILENT}.
     * @see #getVibrateSetting(int)
     * @see #shouldVibrate(int)
     */
    public void setVibrateSetting(int vibrateType, int vibrateSetting) {
        IAudioService service = getService();
        try {
            service.setVibrateSetting(vibrateType, vibrateSetting);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setVibrateSetting", e);
        }
    }

    /**
     * Sets the speakerphone on or off.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param on set <var>true</var> to turn on speakerphone;
     *           <var>false</var> to turn it off
     */
    public void setSpeakerphoneOn(boolean on){
        IAudioService service = getService();
        try {
            service.setSpeakerphoneOn(on);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setSpeakerphoneOn", e);
        }
    }

    /**
     * Checks whether the speakerphone is on or off.
     *
     * @return true if speakerphone is on, false if it's off
     */
    public boolean isSpeakerphoneOn() {
        IAudioService service = getService();
        try {
            return service.isSpeakerphoneOn();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in isSpeakerphoneOn", e);
            return false;
        }
     }

    //====================================================================
    // Bluetooth SCO control
    /**
     * Sticky broadcast intent action indicating that the bluetoooth SCO audio
     * connection state has changed. The intent contains on extra {@link #EXTRA_SCO_AUDIO_STATE}
     * indicating the new state which is either {@link #SCO_AUDIO_STATE_DISCONNECTED}
     * or {@link #SCO_AUDIO_STATE_CONNECTED}
     *
     * @see #startBluetoothSco()
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_SCO_AUDIO_STATE_CHANGED =
            "android.media.SCO_AUDIO_STATE_CHANGED";
    /**
     * Extra for intent {@link #ACTION_SCO_AUDIO_STATE_CHANGED} containing the new
     * bluetooth SCO connection state.
     */
    public static final String EXTRA_SCO_AUDIO_STATE =
            "android.media.extra.SCO_AUDIO_STATE";

    /**
     * Value for extra {@link #EXTRA_SCO_AUDIO_STATE} indicating that the
     * SCO audio channel is not established
     */
    public static final int SCO_AUDIO_STATE_DISCONNECTED = 0;
    /**
     * Value for extra {@link #EXTRA_SCO_AUDIO_STATE} indicating that the
     * SCO audio channel is established
     */
    public static final int SCO_AUDIO_STATE_CONNECTED = 1;
    /**
     * Value for extra {@link #EXTRA_SCO_AUDIO_STATE} indicating that
     * there was an error trying to obtain the state
     */
    public static final int SCO_AUDIO_STATE_ERROR = -1;


    /**
     * Indicates if current platform supports use of SCO for off call use cases.
     * Application wanted to use bluetooth SCO audio when the phone is not in call
     * must first call thsi method to make sure that the platform supports this
     * feature.
     * @return true if bluetooth SCO can be used for audio when not in call
     *         false otherwise
     * @see #startBluetoothSco()
    */
    public boolean isBluetoothScoAvailableOffCall() {
        return mContext.getResources().getBoolean(
               com.android.internal.R.bool.config_bluetooth_sco_off_call);
    }

    /**
     * Start bluetooth SCO audio connection.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#MODIFY_AUDIO_SETTINGS}.
     * <p>This method can be used by applications wanting to send and received audio
     * to/from a bluetooth SCO headset while the phone is not in call.
     * <p>As the SCO connection establishment can take several seconds,
     * applications should not rely on the connection to be available when the method
     * returns but instead register to receive the intent {@link #ACTION_SCO_AUDIO_STATE_CHANGED}
     * and wait for the state to be {@link #SCO_AUDIO_STATE_CONNECTED}.
     * <p>As the connection is not guaranteed to succeed, applications must wait for this intent with
     * a timeout.
     * <p>When finished with the SCO connection or if the establishment times out,
     * the application must call {@link #stopBluetoothSco()} to clear the request and turn
     * down the bluetooth connection.
     * <p>Even if a SCO connection is established, the following restrictions apply on audio
     * output streams so that they can be routed to SCO headset:
     * - the stream type must be {@link #STREAM_VOICE_CALL}
     * - the format must be mono
     * - the sampling must be 16kHz or 8kHz
     * <p>The following restrictions apply on input streams:
     * - the format must be mono
     * - the sampling must be 8kHz
     *
     * <p>Note that the phone application always has the priority on the usage of the SCO
     * connection for telephony. If this method is called while the phone is in call
     * it will be ignored. Similarly, if a call is received or sent while an application
     * is using the SCO connection, the connection will be lost for the application and NOT
     * returned automatically when the call ends.
     * @see #stopBluetoothSco()
     * @see #ACTION_SCO_AUDIO_STATE_CHANGED
     */
    public void startBluetoothSco(){
        IAudioService service = getService();
        try {
            service.startBluetoothSco(mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in startBluetoothSco", e);
        }
    }

    /**
     * Stop bluetooth SCO audio connection.
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#MODIFY_AUDIO_SETTINGS}.
     * <p>This method must be called by applications having requested the use of
     * bluetooth SCO audio with {@link #startBluetoothSco()}
     * when finished with the SCO connection or if the establishment times out.
     * @see #startBluetoothSco()
     */
    public void stopBluetoothSco(){
        IAudioService service = getService();
        try {
            service.stopBluetoothSco(mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in stopBluetoothSco", e);
        }
    }

    /**
     * Request use of Bluetooth SCO headset for communications.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param on set <var>true</var> to use bluetooth SCO for communications;
     *               <var>false</var> to not use bluetooth SCO for communications
     */
    public void setBluetoothScoOn(boolean on){
        IAudioService service = getService();
        try {
            service.setBluetoothScoOn(on);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setBluetoothScoOn", e);
        }
    }

    /**
     * Checks whether communications use Bluetooth SCO.
     *
     * @return true if SCO is used for communications;
     *         false if otherwise
     */
    public boolean isBluetoothScoOn() {
        IAudioService service = getService();
        try {
            return service.isBluetoothScoOn();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in isBluetoothScoOn", e);
            return false;
        }
    }

    /**
     * @param on set <var>true</var> to route A2DP audio to/from Bluetooth
     *           headset; <var>false</var> disable A2DP audio
     * @deprecated Do not use.
     */
    @Deprecated public void setBluetoothA2dpOn(boolean on){
    }

    /**
     * Checks whether A2DP audio routing to the Bluetooth headset is on or off.
     *
     * @return true if A2DP audio is being routed to/from Bluetooth headset;
     *         false if otherwise
     */
    public boolean isBluetoothA2dpOn() {
        if (AudioSystem.getDeviceConnectionState(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,"")
            == AudioSystem.DEVICE_STATE_UNAVAILABLE) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Sets audio routing to the wired headset on or off.
     *
     * @param on set <var>true</var> to route audio to/from wired
     *           headset; <var>false</var> disable wired headset audio
     * @deprecated Do not use.
     */
    @Deprecated public void setWiredHeadsetOn(boolean on){
    }

    /**
     * Checks whether audio routing to the wired headset is on or off.
     *
     * @return true if audio is being routed to/from wired headset;
     *         false if otherwise
     */
    public boolean isWiredHeadsetOn() {
        if (AudioSystem.getDeviceConnectionState(AudioSystem.DEVICE_OUT_WIRED_HEADSET,"")
                == AudioSystem.DEVICE_STATE_UNAVAILABLE &&
            AudioSystem.getDeviceConnectionState(AudioSystem.DEVICE_OUT_WIRED_HEADPHONE,"")
                == AudioSystem.DEVICE_STATE_UNAVAILABLE) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Sets the microphone mute on or off.
     * <p>
     * This method should only be used by applications that replace the platform-wide
     * management of audio settings or the main telephony application.
     *
     * @param on set <var>true</var> to mute the microphone;
     *           <var>false</var> to turn mute off
     */
    public void setMicrophoneMute(boolean on){
        AudioSystem.muteMicrophone(on);
    }

    /**
     * Checks whether the microphone mute is on or off.
     *
     * @return true if microphone is muted, false if it's not
     */
    public boolean isMicrophoneMute() {
        return AudioSystem.isMicrophoneMuted();
    }

    /**
     * Sets the audio mode.
     * <p>
     * The audio mode encompasses audio routing AND the behavior of
     * the telephony layer. Therefore this method should only be used by applications that
     * replace the platform-wide management of audio settings or the main telephony application.
     * In particular, the {@link #MODE_IN_CALL} mode should only be used by the telephony
     * application when it places a phone call, as it will cause signals from the radio layer
     * to feed the platform mixer.
     *
     * @param mode  the requested audio mode (NORMAL, RINGTONE, or IN_CALL).
     *              Informs the HAL about the current audio state so that
     *              it can route the audio appropriately.
     */
    public void setMode(int mode) {
        IAudioService service = getService();
        try {
            service.setMode(mode, mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in setMode", e);
        }
    }

    /**
     * Returns the current audio mode.
     *
     * @return      the current audio mode (NORMAL, RINGTONE, or IN_CALL).
     *              Returns the current current audio state from the HAL.
     */
    public int getMode() {
        IAudioService service = getService();
        try {
            return service.getMode();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in getMode", e);
            return MODE_INVALID;
        }
    }

    /* modes for setMode/getMode/setRoute/getRoute */
    /**
     * Audio harware modes.
     */
    /**
     * Invalid audio mode.
     */
    public static final int MODE_INVALID            = AudioSystem.MODE_INVALID;
    /**
     * Current audio mode. Used to apply audio routing to current mode.
     */
    public static final int MODE_CURRENT            = AudioSystem.MODE_CURRENT;
    /**
     * Normal audio mode: not ringing and no call established.
     */
    public static final int MODE_NORMAL             = AudioSystem.MODE_NORMAL;
    /**
     * Ringing audio mode. An incoming is being signaled.
     */
    public static final int MODE_RINGTONE           = AudioSystem.MODE_RINGTONE;
    /**
     * In call audio mode. A telephony call is established.
     */
    public static final int MODE_IN_CALL            = AudioSystem.MODE_IN_CALL;
    /**
     * @hide
     * In communication audio mode. An audio/video chat or VoIP call is established.
     */
    public static final int MODE_IN_COMMUNICATION   = AudioSystem.MODE_IN_COMMUNICATION;

    /* Routing bits for setRouting/getRouting API */
    /**
     * Routing audio output to earpiece
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_EARPIECE          = AudioSystem.ROUTE_EARPIECE;
    /**
     * Routing audio output to speaker
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_SPEAKER           = AudioSystem.ROUTE_SPEAKER;
    /**
     * @deprecated use {@link #ROUTE_BLUETOOTH_SCO}
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_BLUETOOTH = AudioSystem.ROUTE_BLUETOOTH_SCO;
    /**
     * Routing audio output to bluetooth SCO
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_BLUETOOTH_SCO     = AudioSystem.ROUTE_BLUETOOTH_SCO;
    /**
     * Routing audio output to headset
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_HEADSET           = AudioSystem.ROUTE_HEADSET;
    /**
     * Routing audio output to bluetooth A2DP
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_BLUETOOTH_A2DP    = AudioSystem.ROUTE_BLUETOOTH_A2DP;
    /**
     * Used for mask parameter of {@link #setRouting(int,int,int)}.
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated public static final int ROUTE_ALL               = AudioSystem.ROUTE_ALL;

    /**
     * Sets the audio routing for a specified mode
     *
     * @param mode   audio mode to change route. E.g., MODE_RINGTONE.
     * @param routes bit vector of routes requested, created from one or
     *               more of ROUTE_xxx types. Set bits indicate that route should be on
     * @param mask   bit vector of routes to change, created from one or more of
     * ROUTE_xxx types. Unset bits indicate the route should be left unchanged
     *
     * @deprecated   Do not set audio routing directly, use setSpeakerphoneOn(),
     * setBluetoothScoOn() methods instead.
     */
    @Deprecated
    public void setRouting(int mode, int routes, int mask) {
    }

    /**
     * Returns the current audio routing bit vector for a specified mode.
     *
     * @param mode audio mode to get route (e.g., MODE_RINGTONE)
     * @return an audio route bit vector that can be compared with ROUTE_xxx
     * bits
     * @deprecated   Do not query audio routing directly, use isSpeakerphoneOn(),
     * isBluetoothScoOn(), isBluetoothA2dpOn() and isWiredHeadsetOn() methods instead.
     */
    @Deprecated
    public int getRouting(int mode) {
        return -1;
    }

    /**
     * Checks whether any music is active.
     *
     * @return true if any music tracks are active.
     */
    public boolean isMusicActive() {
        return AudioSystem.isStreamActive(STREAM_MUSIC);
    }

    /*
     * Sets a generic audio configuration parameter. The use of these parameters
     * are platform dependant, see libaudio
     *
     * ** Temporary interface - DO NOT USE
     *
     * TODO: Replace with a more generic key:value get/set mechanism
     *
     * param key   name of parameter to set. Must not be null.
     * param value value of parameter. Must not be null.
     */
    /**
     * @hide
     * @deprecated Use {@link #setPrameters(String)} instead
     */
    @Deprecated public void setParameter(String key, String value) {
        setParameters(key+"="+value);
    }

    /**
     * Sets a variable number of parameter values to audio hardware.
     *
     * @param keyValuePairs list of parameters key value pairs in the form:
     *    key1=value1;key2=value2;...
     *
     */
    public void setParameters(String keyValuePairs) {
        AudioSystem.setParameters(keyValuePairs);
    }

    /**
     * Sets a varaible number of parameter values to audio hardware.
     *
     * @param keys list of parameters
     * @return list of parameters key value pairs in the form:
     *    key1=value1;key2=value2;...
     */
    public String getParameters(String keys) {
        return AudioSystem.getParameters(keys);
    }

    /* Sound effect identifiers */
    /**
     * Keyboard and direction pad click sound
     * @see #playSoundEffect(int)
     */
    public static final int FX_KEY_CLICK = 0;
    /**
     * Focus has moved up
     * @see #playSoundEffect(int)
     */
    public static final int FX_FOCUS_NAVIGATION_UP = 1;
    /**
     * Focus has moved down
     * @see #playSoundEffect(int)
     */
    public static final int FX_FOCUS_NAVIGATION_DOWN = 2;
    /**
     * Focus has moved left
     * @see #playSoundEffect(int)
     */
    public static final int FX_FOCUS_NAVIGATION_LEFT = 3;
    /**
     * Focus has moved right
     * @see #playSoundEffect(int)
     */
    public static final int FX_FOCUS_NAVIGATION_RIGHT = 4;
    /**
     * IME standard keypress sound
     * @see #playSoundEffect(int)
     */
    public static final int FX_KEYPRESS_STANDARD = 5;
    /**
     * IME spacebar keypress sound
     * @see #playSoundEffect(int)
     */
    public static final int FX_KEYPRESS_SPACEBAR = 6;
    /**
     * IME delete keypress sound
     * @see #playSoundEffect(int)
     */
    public static final int FX_KEYPRESS_DELETE = 7;
    /**
     * IME return_keypress sound
     * @see #playSoundEffect(int)
     */
    public static final int FX_KEYPRESS_RETURN = 8;
    /**
     * @hide Number of sound effects
     */
    public static final int NUM_SOUND_EFFECTS = 9;

    /**
     * Plays a sound effect (Key clicks, lid open/close...)
     * @param effectType The type of sound effect. One of
     *            {@link #FX_KEY_CLICK},
     *            {@link #FX_FOCUS_NAVIGATION_UP},
     *            {@link #FX_FOCUS_NAVIGATION_DOWN},
     *            {@link #FX_FOCUS_NAVIGATION_LEFT},
     *            {@link #FX_FOCUS_NAVIGATION_RIGHT},
     *            {@link #FX_KEYPRESS_STANDARD},
     *            {@link #FX_KEYPRESS_SPACEBAR},
     *            {@link #FX_KEYPRESS_DELETE},
     *            {@link #FX_KEYPRESS_RETURN},
     * NOTE: This version uses the UI settings to determine
     * whether sounds are heard or not.
     */
    public void  playSoundEffect(int effectType) {
        if (effectType < 0 || effectType >= NUM_SOUND_EFFECTS) {
            return;
        }

        if (!querySoundEffectsEnabled()) {
            return;
        }

        IAudioService service = getService();
        try {
            service.playSoundEffect(effectType);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in playSoundEffect"+e);
        }
    }

    /**
     * Plays a sound effect (Key clicks, lid open/close...)
     * @param effectType The type of sound effect. One of
     *            {@link #FX_KEY_CLICK},
     *            {@link #FX_FOCUS_NAVIGATION_UP},
     *            {@link #FX_FOCUS_NAVIGATION_DOWN},
     *            {@link #FX_FOCUS_NAVIGATION_LEFT},
     *            {@link #FX_FOCUS_NAVIGATION_RIGHT},
     *            {@link #FX_KEYPRESS_STANDARD},
     *            {@link #FX_KEYPRESS_SPACEBAR},
     *            {@link #FX_KEYPRESS_DELETE},
     *            {@link #FX_KEYPRESS_RETURN},
     * @param volume Sound effect volume.
     * The volume value is a raw scalar so UI controls should be scaled logarithmically.
     * If a volume of -1 is specified, the AudioManager.STREAM_MUSIC stream volume minus 3dB will be used.
     * NOTE: This version is for applications that have their own
     * settings panel for enabling and controlling volume.
     */
    public void  playSoundEffect(int effectType, float volume) {
        if (effectType < 0 || effectType >= NUM_SOUND_EFFECTS) {
            return;
        }

        IAudioService service = getService();
        try {
            service.playSoundEffectVolume(effectType, volume);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in playSoundEffect"+e);
        }
    }

    /**
     * Settings has an in memory cache, so this is fast.
     */
    private boolean querySoundEffectsEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 0) != 0;
    }

    /**
     * See if haptic feedback is enabled for screen touches of objects (called by ViewRoot)
     * @hide
     */
    public boolean queryHapticsAllEnabled() {
        return Settings.System.getInt(mContext.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ALL_ENABLED, 0) != 0;
    }

    /**
     *  Load Sound effects.
     *  This method must be called when sound effects are enabled.
     */
    public void loadSoundEffects() {
        IAudioService service = getService();
        try {
            service.loadSoundEffects();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in loadSoundEffects"+e);
        }
    }

    /**
     *  Unload Sound effects.
     *  This method can be called to free some memory when
     *  sound effects are disabled.
     */
    public void unloadSoundEffects() {
        IAudioService service = getService();
        try {
            service.unloadSoundEffects();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in unloadSoundEffects"+e);
        }
    }

    /**
     * Used to indicate a gain of audio focus, or a request of audio focus, of unknown duration.
     * @see OnAudioFocusChangeListener#onAudioFocusChange(int)
     * @see #requestAudioFocus(OnAudioFocusChangeListener, int, int)
     */
    public static final int AUDIOFOCUS_GAIN = 1;
    /**
     * Used to indicate a temporary gain or request of audio focus, anticipated to last a short
     * amount of time. Examples of temporary changes are the playback of driving directions, or an
     * event notification.
     * @see OnAudioFocusChangeListener#onAudioFocusChange(int)
     * @see #requestAudioFocus(OnAudioFocusChangeListener, int, int)
     */
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;
    /**
     * Used to indicate a temporary request of audio focus, anticipated to last a short
     * amount of time, and where it is acceptable for other audio applications to keep playing
     * after having lowered their output level (also referred to as "ducking").
     * Examples of temporary changes are the playback of driving directions where playback of music
     * in the background is acceptable.
     * @see OnAudioFocusChangeListener#onAudioFocusChange(int)
     * @see #requestAudioFocus(OnAudioFocusChangeListener, int, int)
     */
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3;
    /**
     * Used to indicate a loss of audio focus of unknown duration.
     * @see OnAudioFocusChangeListener#onAudioFocusChange(int)
     */
    public static final int AUDIOFOCUS_LOSS = -1 * AUDIOFOCUS_GAIN;
    /**
     * Used to indicate a transient loss of audio focus.
     * @see OnAudioFocusChangeListener#onAudioFocusChange(int)
     */
    public static final int AUDIOFOCUS_LOSS_TRANSIENT = -1 * AUDIOFOCUS_GAIN_TRANSIENT;
    /**
     * Used to indicate a transient loss of audio focus where the loser of the audio focus can
     * lower its output volume if it wants to continue playing (also referred to as "ducking"), as
     * the new focus owner doesn't require others to be silent.
     * @see OnAudioFocusChangeListener#onAudioFocusChange(int)
     */
    public static final int AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK =
            -1 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;

    /**
     * Interface definition for a callback to be invoked when the audio focus of the system is
     * updated.
     */
    public interface OnAudioFocusChangeListener {
        /**
         * Called on the listener to notify it the audio focus for this listener has been changed.
         * The focusChange value indicates whether the focus was gained,
         * whether the focus was lost, and whether that loss is transient, or whether the new focus
         * holder will hold it for an unknown amount of time.
         * When losing focus, listeners can use the focus change information to decide what
         * behavior to adopt when losing focus. A music player could for instance elect to lower
         * the volume of its music stream (duck) for transient focus losses, and pause otherwise.
         * @param focusChange the type of focus change, one of {@link AudioManager#AUDIOFOCUS_GAIN},
         *   {@link AudioManager#AUDIOFOCUS_LOSS}, {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT}
         *   and {@link AudioManager#AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}.
         */
        public void onAudioFocusChange(int focusChange);
    }

    /**
     * Map to convert focus event listener IDs, as used in the AudioService audio focus stack,
     * to actual listener objects.
     */
    private HashMap<String, OnAudioFocusChangeListener> mAudioFocusIdListenerMap =
            new HashMap<String, OnAudioFocusChangeListener>();
    /**
     * Lock to prevent concurrent changes to the list of focus listeners for this AudioManager
     * instance.
     */
    private final Object mFocusListenerLock = new Object();

    private OnAudioFocusChangeListener findFocusListener(String id) {
        return mAudioFocusIdListenerMap.get(id);
    }

    /**
     * Handler for audio focus events coming from the audio service.
     */
    private FocusEventHandlerDelegate mAudioFocusEventHandlerDelegate =
            new FocusEventHandlerDelegate();

    /**
     * Helper class to handle the forwarding of audio focus events to the appropriate listener
     */
    private class FocusEventHandlerDelegate {
        private final Handler mHandler;

        FocusEventHandlerDelegate() {
            Looper looper;
            if ((looper = Looper.myLooper()) == null) {
                looper = Looper.getMainLooper();
            }

            if (looper != null) {
                // implement the event handler delegate to receive audio focus events
                mHandler = new Handler(looper) {
                    @Override
                    public void handleMessage(Message msg) {
                        OnAudioFocusChangeListener listener = null;
                        synchronized(mFocusListenerLock) {
                            listener = findFocusListener((String)msg.obj);
                        }
                        if (listener != null) {
                            listener.onAudioFocusChange(msg.what);
                        }
                    }
                };
            } else {
                mHandler = null;
            }
        }

        Handler getHandler() {
            return mHandler;
        }
    }

    private IAudioFocusDispatcher mAudioFocusDispatcher = new IAudioFocusDispatcher.Stub() {

        public void dispatchAudioFocusChange(int focusChange, String id) {
            Message m = mAudioFocusEventHandlerDelegate.getHandler().obtainMessage(focusChange, id);
            mAudioFocusEventHandlerDelegate.getHandler().sendMessage(m);
        }

    };

    private String getIdForAudioFocusListener(OnAudioFocusChangeListener l) {
        if (l == null) {
            return new String(this.toString());
        } else {
            return new String(this.toString() + l.toString());
        }
    }

    /**
     * @hide
     * Registers a listener to be called when audio focus changes. Calling this method is optional
     * before calling {@link #requestAudioFocus(OnAudioFocusChangeListener, int, int)}, as it
     * will register the listener as well if it wasn't registered already.
     * @param l the listener to be notified of audio focus changes.
     */
    public void registerAudioFocusListener(OnAudioFocusChangeListener l) {
        synchronized(mFocusListenerLock) {
            if (mAudioFocusIdListenerMap.containsKey(getIdForAudioFocusListener(l))) {
                return;
            }
            mAudioFocusIdListenerMap.put(getIdForAudioFocusListener(l), l);
        }
    }

    /**
     * @hide
     * Causes the specified listener to not be called anymore when focus is gained or lost.
     * @param l the listener to unregister.
     */
    public void unregisterAudioFocusListener(OnAudioFocusChangeListener l) {

        // remove locally
        synchronized(mFocusListenerLock) {
            mAudioFocusIdListenerMap.remove(getIdForAudioFocusListener(l));
        }
    }


    /**
     * A failed focus change request.
     */
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    /**
     * A successful focus change request.
     */
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;


    /**
     *  Request audio focus.
     *  Send a request to obtain the audio focus
     *  @param l the listener to be notified of audio focus changes
     *  @param streamType the main audio stream type affected by the focus request
     *  @param durationHint use {@link #AUDIOFOCUS_GAIN_TRANSIENT} to indicate this focus request
     *      is temporary, and focus will be abandonned shortly. Examples of transient requests are
     *      for the playback of driving directions, or notifications sounds.
     *      Use {@link #AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK} to indicate also that it's ok for
     *      the previous focus owner to keep playing if it ducks its audio output.
     *      Use {@link #AUDIOFOCUS_GAIN} for a focus request of unknown duration such
     *      as the playback of a song or a video.
     *  @return {@link #AUDIOFOCUS_REQUEST_FAILED} or {@link #AUDIOFOCUS_REQUEST_GRANTED}
     */
    public int requestAudioFocus(OnAudioFocusChangeListener l, int streamType, int durationHint) {
        int status = AUDIOFOCUS_REQUEST_FAILED;
        if ((durationHint < AUDIOFOCUS_GAIN) || (durationHint > AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK))
        {
            Log.e(TAG, "Invalid duration hint, audio focus request denied");
            return status;
        }
        registerAudioFocusListener(l);
        //TODO protect request by permission check?
        IAudioService service = getService();
        try {
            status = service.requestAudioFocus(streamType, durationHint, mICallBack,
                    mAudioFocusDispatcher, getIdForAudioFocusListener(l));
        } catch (RemoteException e) {
            Log.e(TAG, "Can't call requestAudioFocus() from AudioService due to "+e);
        }
        return status;
    }


    /**
     *  Abandon audio focus. Causes the previous focus owner, if any, to receive focus.
     *  @param l the listener with which focus was requested.
     *  @return {@link #AUDIOFOCUS_REQUEST_FAILED} or {@link #AUDIOFOCUS_REQUEST_GRANTED}
     */
    public int abandonAudioFocus(OnAudioFocusChangeListener l) {
        int status = AUDIOFOCUS_REQUEST_FAILED;
        unregisterAudioFocusListener(l);
        IAudioService service = getService();
        try {
            status = service.abandonAudioFocus(mAudioFocusDispatcher,
                    getIdForAudioFocusListener(l), mICallBack);
        } catch (RemoteException e) {
            Log.e(TAG, "Can't call abandonAudioFocus() from AudioService due to "+e);
        }
        return status;
    }


    //====================================================================
    // Remote Control
    /**
     * Register a component to be the sole receiver of MEDIA_BUTTON intents.
     * @param eventReceiver identifier of a {@link android.content.BroadcastReceiver}
     *      that will receive the media button intent. This broadcast receiver must be declared
     *      in the application manifest.
     */
    public void registerMediaButtonEventReceiver(ComponentName eventReceiver) {
        //TODO enforce the rule about the receiver being declared in the manifest
        IAudioService service = getService();
        try {
            service.registerMediaButtonEventReceiver(eventReceiver);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in registerMediaButtonEventReceiver"+e);
        }
    }

    /**
     * Unregister the receiver of MEDIA_BUTTON intents.
     * @param eventReceiver identifier of a {@link android.content.BroadcastReceiver}
     *      that was registered with {@link #registerMediaButtonEventReceiver(ComponentName)}.
     */
    public void unregisterMediaButtonEventReceiver(ComponentName eventReceiver) {
        IAudioService service = getService();
        try {
            service.unregisterMediaButtonEventReceiver(eventReceiver);
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in unregisterMediaButtonEventReceiver"+e);
        }
    }

    /**
     *  @hide
     *  Reload audio settings. This method is called by Settings backup
     *  agent when audio settings are restored and causes the AudioService
     *  to read and apply restored settings.
     */
    public void reloadAudioSettings() {
        IAudioService service = getService();
        try {
            service.reloadAudioSettings();
        } catch (RemoteException e) {
            Log.e(TAG, "Dead object in reloadAudioSettings"+e);
        }
    }

     /**
      * {@hide}
      */
     private IBinder mICallBack = new Binder();
}
