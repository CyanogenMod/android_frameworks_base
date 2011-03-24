/*
 * Copyright (C) 2006 The Android Open Source Project
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

import java.util.NoSuchElementException;
import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.VolumePanel;
import android.os.SystemProperties;

import com.android.internal.telephony.ITelephony;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * The implementation of the volume manager service.
 * <p>
 * This implementation focuses on delivering a responsive UI. Most methods are
 * asynchronous to external calls. For example, the task of setting a volume
 * will update our internal state, but in a separate thread will set the system
 * volume and later persist to the database. Similarly, setting the ringer mode
 * will update the state and broadcast a change and in a separate thread later
 * persist the ringer mode.
 *
 * @hide
 */
public class AudioService extends IAudioService.Stub {

    private static final String TAG = "AudioService";

    /** How long to delay before persisting a change in volume/ringer mode. */
    private static final int PERSIST_DELAY = 3000;

    private Context mContext;
    private ContentResolver mContentResolver;


    /** The UI */
    private VolumePanel mVolumePanel;

    // sendMsg() flags
    /** Used when a message should be shared across all stream types. */
    private static final int SHARED_MSG = -1;
    /** If the msg is already queued, replace it with this one. */
    private static final int SENDMSG_REPLACE = 0;
    /** If the msg is already queued, ignore this one and leave the old. */
    private static final int SENDMSG_NOOP = 1;
    /** If the msg is already queued, queue this one and leave the old. */
    private static final int SENDMSG_QUEUE = 2;

    // AudioHandler message.whats
    private static final int MSG_SET_SYSTEM_VOLUME = 0;
    private static final int MSG_PERSIST_VOLUME = 1;
    private static final int MSG_PERSIST_RINGER_MODE = 3;
    private static final int MSG_PERSIST_VIBRATE_SETTING = 4;
    private static final int MSG_MEDIA_SERVER_DIED = 5;
    private static final int MSG_MEDIA_SERVER_STARTED = 6;
    private static final int MSG_PLAY_SOUND_EFFECT = 7;
    private static final int MSG_BTA2DP_DOCK_TIMEOUT = 8;

    private static final int BTA2DP_DOCK_TIMEOUT_MILLIS = 8000;

    /** @see AudioSystemThread */
    private AudioSystemThread mAudioSystemThread;
    /** @see AudioHandler */
    private AudioHandler mAudioHandler;
    /** @see VolumeStreamState */
    private VolumeStreamState[] mStreamStates;
    private SettingsObserver mSettingsObserver;

    private int mMode;
    private Object mSettingsLock = new Object();
    private boolean mMediaServerOk;

    private SoundPool mSoundPool;
    private Object mSoundEffectsLock = new Object();
    private static final int NUM_SOUNDPOOL_CHANNELS = 4;
    private static final int SOUND_EFFECT_VOLUME = 1000;

    /* Sound effect file names  */
    private static final String SOUND_EFFECTS_PATH = "/media/audio/ui/";
    private static final String[] SOUND_EFFECT_FILES = new String[] {
        "Effect_Tick.ogg",
        "KeypressStandard.ogg",
        "KeypressSpacebar.ogg",
        "KeypressDelete.ogg",
        "KeypressReturn.ogg"
    };

    /* Sound effect file name mapping sound effect id (AudioManager.FX_xxx) to
     * file index in SOUND_EFFECT_FILES[] (first column) and indicating if effect
     * uses soundpool (second column) */
    private int[][] SOUND_EFFECT_FILES_MAP = new int[][] {
        {0, -1},  // FX_KEY_CLICK
        {0, -1},  // FX_FOCUS_NAVIGATION_UP
        {0, -1},  // FX_FOCUS_NAVIGATION_DOWN
        {0, -1},  // FX_FOCUS_NAVIGATION_LEFT
        {0, -1},  // FX_FOCUS_NAVIGATION_RIGHT
        {1, -1},  // FX_KEYPRESS_STANDARD
        {2, -1},  // FX_KEYPRESS_SPACEBAR
        {3, -1},  // FX_FOCUS_DELETE
        {4, -1}   // FX_FOCUS_RETURN
    };

   /** @hide Maximum volume index values for audio streams */
    private int[] MAX_STREAM_VOLUME = new int[] {
        5,  // STREAM_VOICE_CALL
        7,  // STREAM_SYSTEM
        7,  // STREAM_RING
        15, // STREAM_MUSIC
        7,  // STREAM_ALARM
        7,  // STREAM_NOTIFICATION
        15, // STREAM_BLUETOOTH_SCO
        7,  // STREAM_SYSTEM_ENFORCED
        15, // STREAM_DTMF
        15,  // STREAM_TTS
        15 // STREAM_FM
    };
    /* STREAM_VOLUME_ALIAS[] indicates for each stream if it uses the volume settings
     * of another stream: This avoids multiplying the volume settings for hidden
     * stream types that follow other stream behavior for volume settings
     * NOTE: do not create loops in aliases! */
    private int[] STREAM_VOLUME_ALIAS = new int[] {
        AudioSystem.STREAM_VOICE_CALL,  // STREAM_VOICE_CALL
        AudioSystem.STREAM_SYSTEM,  // STREAM_SYSTEM
        AudioSystem.STREAM_RING,  // STREAM_RING
        AudioSystem.STREAM_MUSIC, // STREAM_MUSIC
        AudioSystem.STREAM_ALARM,  // STREAM_ALARM
        AudioSystem.STREAM_NOTIFICATION,  // STREAM_NOTIFICATION
        AudioSystem.STREAM_BLUETOOTH_SCO, // STREAM_BLUETOOTH_SCO
        AudioSystem.STREAM_SYSTEM,  // STREAM_SYSTEM_ENFORCED
        AudioSystem.STREAM_VOICE_CALL, // STREAM_DTMF
        AudioSystem.STREAM_MUSIC,  // STREAM_TTS
        AudioSystem.STREAM_MUSIC  // STREAM_FM
    };

    private AudioSystem.ErrorCallback mAudioSystemCallback = new AudioSystem.ErrorCallback() {
        public void onError(int error) {
            switch (error) {
            case AudioSystem.AUDIO_STATUS_SERVER_DIED:
                if (mMediaServerOk) {
                    sendMsg(mAudioHandler, MSG_MEDIA_SERVER_DIED, SHARED_MSG, SENDMSG_NOOP, 0, 0,
                            null, 1500);
                    mMediaServerOk = false;
                }
                break;
            case AudioSystem.AUDIO_STATUS_OK:
                if (!mMediaServerOk) {
                    sendMsg(mAudioHandler, MSG_MEDIA_SERVER_STARTED, SHARED_MSG, SENDMSG_NOOP, 0, 0,
                            null, 0);
                    mMediaServerOk = true;
                }
                break;
            default:
                break;
            }
       }
    };

    /**
     * Current ringer mode from one of {@link AudioManager#RINGER_MODE_NORMAL},
     * {@link AudioManager#RINGER_MODE_SILENT}, or
     * {@link AudioManager#RINGER_MODE_VIBRATE}.
     */
    private int mRingerMode;

    /** @see System#MODE_RINGER_STREAMS_AFFECTED */
    private int mRingerModeAffectedStreams;

    // Streams currently muted by ringer mode
    private int mRingerModeMutedStreams;

    /** @see System#MUTE_STREAMS_AFFECTED */
    private int mMuteAffectedStreams;

    /**
     * Has multiple bits per vibrate type to indicate the type's vibrate
     * setting. See {@link #setVibrateSetting(int, int)}.
     * <p>
     * NOTE: This is not the final decision of whether vibrate is on/off for the
     * type since it depends on the ringer mode. See {@link #shouldVibrate(int)}.
     */
    private int mVibrateSetting;

    /** @see System#NOTIFICATIONS_USE_RING_VOLUME */
    private int mNotificationsUseRingVolume;

    // Broadcast receiver for device connections intent broadcasts
    private final BroadcastReceiver mReceiver = new AudioServiceBroadcastReceiver();

    //  Broadcast receiver for media button broadcasts (separate from mReceiver to
    //  independently change its priority)
    private final BroadcastReceiver mMediaButtonReceiver = new MediaButtonBroadcastReceiver();

    // Used to alter media button redirection when the phone is ringing.
    private boolean mIsRinging = false;

    // Devices currently connected
    private HashMap <Integer, String> mConnectedDevices = new HashMap <Integer, String>();

    // Forced device usage for communications
    private int mForcedUseForComm;

    // List of binder death handlers for setMode() client processes.
    // The last process to have called setMode() is at the top of the list.
    private ArrayList <SetModeDeathHandler> mSetModeDeathHandlers = new ArrayList <SetModeDeathHandler>();

    // List of clients having issued a SCO start request
    private ArrayList <ScoClient> mScoClients = new ArrayList <ScoClient>();

    private ArrayList <AudioFocusDeathHandler> mAudioFocusDeathHandlers = new ArrayList <AudioFocusDeathHandler>();

    // BluetoothHeadset API to control SCO connection
    private BluetoothHeadset mBluetoothHeadset;

    // Bluetooth headset connection state
    private boolean mBluetoothHeadsetConnected;

    ///////////////////////////////////////////////////////////////////////////
    // Construction
    ///////////////////////////////////////////////////////////////////////////

    /** @hide */
    public AudioService(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();

       // Intialized volume
        MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL] = SystemProperties.getInt(
            "ro.config.vc_call_vol_steps",
           MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL]);

        mVolumePanel = new VolumePanel(context, this);
        mSettingsObserver = new SettingsObserver();
        mForcedUseForComm = AudioSystem.FORCE_NONE;
        createAudioSystemThread();
        readPersistedSettings();
        createStreamStates();
        // Call setMode() to initialize mSetModeDeathHandlers
        mMode = AudioSystem.MODE_INVALID;
        setMode(AudioSystem.MODE_NORMAL, null);
        mMediaServerOk = true;

        // Call setRingerModeInt() to apply correct mute
        // state on streams affected by ringer mode.
        mRingerModeMutedStreams = 0;
        setRingerModeInt(getRingerMode(), false);

        AudioSystem.setErrorCallback(mAudioSystemCallback);
        loadSoundEffects();

        mBluetoothHeadsetConnected = false;
        mBluetoothHeadset = new BluetoothHeadset(context,
                                                 mBluetoothHeadsetServiceListener);

        // Register for device connection intent broadcasts.
        IntentFilter intentFilter =
                new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        intentFilter.addAction(BluetoothA2dp.ACTION_SINK_STATE_CHANGED);
        intentFilter.addAction(BluetoothHeadset.ACTION_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        intentFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);

        // Register for media button intent broadcasts.
        intentFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        context.registerReceiver(mMediaButtonReceiver, intentFilter);

        // Register for phone state monitoring
        TelephonyManager tmgr = (TelephonyManager)
                context.getSystemService(Context.TELEPHONY_SERVICE);
        tmgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void createAudioSystemThread() {
        mAudioSystemThread = new AudioSystemThread();
        mAudioSystemThread.start();
        waitForAudioHandlerCreation();
    }

    /** Waits for the volume handler to be created by the other thread. */
    private void waitForAudioHandlerCreation() {
        synchronized(this) {
            while (mAudioHandler == null) {
                try {
                    // Wait for mAudioHandler to be set by the other thread
                    wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting on volume handler.");
                }
            }
        }
    }

    private void createStreamStates() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        VolumeStreamState[] streams = mStreamStates = new VolumeStreamState[numStreamTypes];

        for (int i = 0; i < numStreamTypes; i++) {
            streams[i] = new VolumeStreamState(System.VOLUME_SETTINGS[STREAM_VOLUME_ALIAS[i]], i);
        }

        // Correct stream index values for streams with aliases
        for (int i = 0; i < numStreamTypes; i++) {
            if (STREAM_VOLUME_ALIAS[i] != i) {
                int index = rescaleIndex(streams[i].mIndex, STREAM_VOLUME_ALIAS[i], i);
                streams[i].mIndex = streams[i].getValidIndex(index);
                setStreamVolumeIndex(i, index);
                index = rescaleIndex(streams[i].mLastAudibleIndex, STREAM_VOLUME_ALIAS[i], i);
                streams[i].mLastAudibleIndex = streams[i].getValidIndex(index);
            }
        }
    }

    private void readPersistedSettings() {
        final ContentResolver cr = mContentResolver;

        mRingerMode = System.getInt(cr, System.MODE_RINGER, AudioManager.RINGER_MODE_NORMAL);

        mVibrateSetting = System.getInt(cr, System.VIBRATE_ON, 0);

        mRingerModeAffectedStreams = Settings.System.getInt(cr,
                Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                ((1 << AudioSystem.STREAM_RING)|(1 << AudioSystem.STREAM_NOTIFICATION)|
                 (1 << AudioSystem.STREAM_SYSTEM)|(1 << AudioSystem.STREAM_SYSTEM_ENFORCED)));

        mMuteAffectedStreams = System.getInt(cr,
                System.MUTE_STREAMS_AFFECTED,
                ((1 << AudioSystem.STREAM_MUSIC)|(1 << AudioSystem.STREAM_RING)|(1 << AudioSystem.STREAM_SYSTEM)));

        mNotificationsUseRingVolume = System.getInt(cr,
                Settings.System.NOTIFICATIONS_USE_RING_VOLUME, 1);

        if (mNotificationsUseRingVolume == 1) {
            STREAM_VOLUME_ALIAS[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_RING;
        }
        // Each stream will read its own persisted settings

        // Broadcast the sticky intent
        broadcastRingerMode();

        // Broadcast vibrate settings
        broadcastVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        broadcastVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION);
    }

    private void setStreamVolumeIndex(int stream, int index) {
        AudioSystem.setStreamVolumeIndex(stream, (index + 5)/10);
    }

    private int rescaleIndex(int index, int srcStream, int dstStream) {
        return (index * mStreamStates[dstStream].getMaxIndex() + mStreamStates[srcStream].getMaxIndex() / 2) / mStreamStates[srcStream].getMaxIndex();
    }

    ///////////////////////////////////////////////////////////////////////////
    // IPC methods
    ///////////////////////////////////////////////////////////////////////////

    /** @see AudioManager#adjustVolume(int, int) */
    public void adjustVolume(int direction, int flags) {
        adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, flags);
    }

    /** @see AudioManager#adjustVolume(int, int, int) */
    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags) {

        int streamType = getActiveStreamType(suggestedStreamType);

        // Don't play sound on other streams
        if (streamType != AudioSystem.STREAM_RING && (flags & AudioManager.FLAG_PLAY_SOUND) != 0) {
            flags &= ~AudioManager.FLAG_PLAY_SOUND;
        }

        adjustStreamVolume(streamType, direction, flags);
    }

    /** @see AudioManager#adjustStreamVolume(int, int, int) */
    public void adjustStreamVolume(int streamType, int direction, int flags) {
        ensureValidDirection(direction);
        ensureValidStreamType(streamType);


        VolumeStreamState streamState = mStreamStates[STREAM_VOLUME_ALIAS[streamType]];
        final int oldIndex = (streamState.muteCount() != 0) ? streamState.mLastAudibleIndex : streamState.mIndex;
        boolean adjustVolume = true;

        // If either the client forces allowing ringer modes for this adjustment,
        // or the stream type is one that is affected by ringer modes
        if ((flags & AudioManager.FLAG_ALLOW_RINGER_MODES) != 0
                || streamType == AudioSystem.STREAM_RING) {
            // Check if the ringer mode changes with this volume adjustment. If
            // it does, it will handle adjusting the volume, so we won't below
            adjustVolume = checkForRingerModeChange(oldIndex, direction);
        }

        // If stream is muted, adjust last audible index only
        int index;
        if (streamState.muteCount() != 0) {
            if (adjustVolume) {
                streamState.adjustLastAudibleIndex(direction);
                // Post a persist volume msg
                sendMsg(mAudioHandler, MSG_PERSIST_VOLUME, streamType,
                        SENDMSG_REPLACE, 0, 1, streamState, PERSIST_DELAY);
            }
            index = streamState.mLastAudibleIndex;
        } else {
            if (adjustVolume && streamState.adjustIndex(direction)) {
                // Post message to set system volume (it in turn will post a message
                // to persist). Do not change volume if stream is muted.
                sendMsg(mAudioHandler, MSG_SET_SYSTEM_VOLUME, STREAM_VOLUME_ALIAS[streamType], SENDMSG_NOOP, 0, 0,
                        streamState, 0);
            }
            index = streamState.mIndex;
        }
        // UI
        mVolumePanel.postVolumeChanged(streamType, flags);
        // Broadcast Intent
        sendVolumeUpdate(streamType, oldIndex, index);
    }

    /** @see AudioManager#setStreamVolume(int, int, int) */
    public void setStreamVolume(int streamType, int index, int flags) {
        ensureValidStreamType(streamType);
        VolumeStreamState streamState = mStreamStates[STREAM_VOLUME_ALIAS[streamType]];

        final int oldIndex = (streamState.muteCount() != 0) ? streamState.mLastAudibleIndex : streamState.mIndex;

        index = rescaleIndex(index * 10, streamType, STREAM_VOLUME_ALIAS[streamType]);
        setStreamVolumeInt(STREAM_VOLUME_ALIAS[streamType], index, false, true);

        index = (streamState.muteCount() != 0) ? streamState.mLastAudibleIndex : streamState.mIndex;

        // UI, etc.
        mVolumePanel.postVolumeChanged(streamType, flags);
        // Broadcast Intent
        sendVolumeUpdate(streamType, oldIndex, index);
    }

    private void sendVolumeUpdate(int streamType, int oldIndex, int index) {
        oldIndex = (oldIndex + 5) / 10;
        index = (index + 5) / 10;

        Intent intent = new Intent(AudioManager.VOLUME_CHANGED_ACTION);
        intent.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, streamType);
        intent.putExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, index);
        intent.putExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, oldIndex);

        mContext.sendBroadcast(intent);
    }

    /**
     * Sets the stream state's index, and posts a message to set system volume.
     * This will not call out to the UI. Assumes a valid stream type.
     *
     * @param streamType Type of the stream
     * @param index Desired volume index of the stream
     * @param force If true, set the volume even if the desired volume is same
     * as the current volume.
     * @param lastAudible If true, stores new index as last audible one
     */
    private void setStreamVolumeInt(int streamType, int index, boolean force, boolean lastAudible) {
        VolumeStreamState streamState = mStreamStates[streamType];

        // If stream is muted, set last audible index only
        if (streamState.muteCount() != 0) {
            // Do not allow last audible index to be 0
            if (index != 0) {
                streamState.setLastAudibleIndex(index);
                // Post a persist volume msg
                sendMsg(mAudioHandler, MSG_PERSIST_VOLUME, streamType,
                        SENDMSG_REPLACE, 0, 1, streamState, PERSIST_DELAY);
            }
        } else {
            if (streamState.setIndex(index, lastAudible) || force) {
                // Post message to set system volume (it in turn will post a message
                // to persist).
                sendMsg(mAudioHandler, MSG_SET_SYSTEM_VOLUME, streamType, SENDMSG_NOOP, 0, 0,
                        streamState, 0);
            }
        }
    }

    /** @see AudioManager#setStreamSolo(int, boolean) */
    public void setStreamSolo(int streamType, boolean state, IBinder cb) {
        for (int stream = 0; stream < mStreamStates.length; stream++) {
            if (!isStreamAffectedByMute(stream) || stream == streamType) continue;
            // Bring back last audible volume
            mStreamStates[stream].mute(cb, state);
         }
    }

    /** @see AudioManager#setStreamMute(int, boolean) */
    public void setStreamMute(int streamType, boolean state, IBinder cb) {
        if (isStreamAffectedByMute(streamType)) {
            mStreamStates[streamType].mute(cb, state);
        }
    }

    /** @see AudioManager#getStreamVolume(int) */
    public int getStreamVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (mStreamStates[streamType].mIndex + 5) / 10;
    }

    /** @see AudioManager#getStreamMaxVolume(int) */
    public int getStreamMaxVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (mStreamStates[streamType].getMaxIndex() + 5) / 10;
    }

    /** @see AudioManager#getRingerMode() */
    public int getRingerMode() {
        return mRingerMode;
    }

    /** @see AudioManager#setRingerMode(int) */
    public void setRingerMode(int ringerMode) {
        synchronized (mSettingsLock) {
            if (ringerMode != mRingerMode) {
                setRingerModeInt(ringerMode, true);
                // Send sticky broadcast
                broadcastRingerMode();
            }
        }
    }

    private void setRingerModeInt(int ringerMode, boolean persist) {
        mRingerMode = ringerMode;

        // Mute stream if not previously muted by ringer mode and ringer mode
        // is not RINGER_MODE_NORMAL and stream is affected by ringer mode.
        // Unmute stream if previously muted by ringer mode and ringer mode
        // is RINGER_MODE_NORMAL or stream is not affected by ringer mode.
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
            if (isStreamMutedByRingerMode(streamType)) {
                if (!isStreamAffectedByRingerMode(streamType) ||
                    mRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                    mStreamStates[streamType].mute(null, false);
                    mRingerModeMutedStreams &= ~(1 << streamType);
                }
            } else {
                if (isStreamAffectedByRingerMode(streamType) &&
                    mRingerMode != AudioManager.RINGER_MODE_NORMAL) {
                   mStreamStates[streamType].mute(null, true);
                   mRingerModeMutedStreams |= (1 << streamType);
               }
            }
        }

        // Post a persist ringer mode msg
        if (persist) {
            sendMsg(mAudioHandler, MSG_PERSIST_RINGER_MODE, SHARED_MSG,
                    SENDMSG_REPLACE, 0, 0, null, PERSIST_DELAY);
        }
    }

    /** @see AudioManager#shouldVibrate(int) */
    public boolean shouldVibrate(int vibrateType) {

        switch (getVibrateSetting(vibrateType)) {

            case AudioManager.VIBRATE_SETTING_ON:
                return mRingerMode != AudioManager.RINGER_MODE_SILENT;

            case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
                return mRingerMode == AudioManager.RINGER_MODE_VIBRATE;

            case AudioManager.VIBRATE_SETTING_OFF:
                // return false, even for incoming calls
                return false;

            default:
                return false;
        }
    }

    /** @see AudioManager#getVibrateSetting(int) */
    public int getVibrateSetting(int vibrateType) {
        return (mVibrateSetting >> (vibrateType * 2)) & 3;
    }

    /** @see AudioManager#setVibrateSetting(int, int) */
    public void setVibrateSetting(int vibrateType, int vibrateSetting) {

        mVibrateSetting = getValueForVibrateSetting(mVibrateSetting, vibrateType, vibrateSetting);

        // Broadcast change
        broadcastVibrateSetting(vibrateType);

        // Post message to set ringer mode (it in turn will post a message
        // to persist)
        sendMsg(mAudioHandler, MSG_PERSIST_VIBRATE_SETTING, SHARED_MSG, SENDMSG_NOOP, 0, 0,
                null, 0);
    }

    /**
     * @see #setVibrateSetting(int, int)
     */
    public static int getValueForVibrateSetting(int existingValue, int vibrateType,
            int vibrateSetting) {

        // First clear the existing setting. Each vibrate type has two bits in
        // the value. Note '3' is '11' in binary.
        existingValue &= ~(3 << (vibrateType * 2));

        // Set into the old value
        existingValue |= (vibrateSetting & 3) << (vibrateType * 2);

        return existingValue;
    }

    private class SetModeDeathHandler implements IBinder.DeathRecipient {
        private IBinder mCb; // To be notified of client's death
        private int mMode = AudioSystem.MODE_NORMAL; // Current mode set by this client

        SetModeDeathHandler(IBinder cb) {
            mCb = cb;
        }

        public void binderDied() {
            synchronized(mSetModeDeathHandlers) {
                Log.w(TAG, "setMode() client died");
                int index = mSetModeDeathHandlers.indexOf(this);
                if (index < 0) {
                    Log.w(TAG, "unregistered setMode() client died");
                } else {
                    mSetModeDeathHandlers.remove(this);
                    // If dead client was a the top of client list,
                    // apply next mode in the stack
                    if (index == 0) {
                        // mSetModeDeathHandlers is never empty as the initial entry
                        // created when AudioService starts is never removed
                        SetModeDeathHandler hdlr = mSetModeDeathHandlers.get(0);
                        int mode = hdlr.getMode();
                        if (AudioService.this.mMode != mode) {
                            if (AudioSystem.setPhoneState(mode) == AudioSystem.AUDIO_STATUS_OK) {
                                AudioService.this.mMode = mode;
                            }
                        }
                    }
                }
            }
        }

        public void setMode(int mode) {
            mMode = mode;
        }

        public int getMode() {
            return mMode;
        }

        public IBinder getBinder() {
            return mCb;
        }
    }

    /** @see AudioManager#setMode(int) */
    public void setMode(int mode, IBinder cb) {
        if (!checkAudioSettingsPermission("setMode()")) {
            return;
        }

        if (mode < AudioSystem.MODE_CURRENT || mode >= AudioSystem.NUM_MODES) {
            return;
        }

        synchronized (mSettingsLock) {
            if (mode == AudioSystem.MODE_CURRENT) {
                mode = mMode;
            }
            if (mode != mMode) {
                if (AudioSystem.setPhoneState(mode) == AudioSystem.AUDIO_STATUS_OK) {
                    mMode = mode;

                    synchronized(mSetModeDeathHandlers) {
                        SetModeDeathHandler hdlr = null;
                        Iterator iter = mSetModeDeathHandlers.iterator();
                        while (iter.hasNext()) {
                            SetModeDeathHandler h = (SetModeDeathHandler)iter.next();
                            if (h.getBinder() == cb) {
                                hdlr = h;
                                // Remove from client list so that it is re-inserted at top of list
                                iter.remove();
                                break;
                            }
                        }
                        if (hdlr == null) {
                            hdlr = new SetModeDeathHandler(cb);
                            // cb is null when setMode() is called by AudioService constructor
                            if (cb != null) {
                                // Register for client death notification
                                try {
                                    cb.linkToDeath(hdlr, 0);
                                } catch (RemoteException e) {
                                    // Client has died!
                                    Log.w(TAG, "setMode() could not link to "+cb+" binder death");
                                }
                            }
                        }
                        // Last client to call setMode() is always at top of client list
                        // as required by SetModeDeathHandler.binderDied()
                        mSetModeDeathHandlers.add(0, hdlr);
                        hdlr.setMode(mode);
                    }

                    if (mode != AudioSystem.MODE_NORMAL) {
                        clearAllScoClients();
                    }
                }
            }
            int streamType = getActiveStreamType(AudioManager.USE_DEFAULT_STREAM_TYPE);
            int index = mStreamStates[STREAM_VOLUME_ALIAS[streamType]].mIndex;
            setStreamVolumeInt(STREAM_VOLUME_ALIAS[streamType], index, true, false);
        }
    }

    /** @see AudioManager#getMode() */
    public int getMode() {
        return mMode;
    }

    /** @see AudioManager#playSoundEffect(int) */
    public void playSoundEffect(int effectType) {
        sendMsg(mAudioHandler, MSG_PLAY_SOUND_EFFECT, SHARED_MSG, SENDMSG_NOOP,
                effectType, -1, null, 0);
    }

    /** @see AudioManager#playSoundEffect(int, float) */
    public void playSoundEffectVolume(int effectType, float volume) {
        loadSoundEffects();
        sendMsg(mAudioHandler, MSG_PLAY_SOUND_EFFECT, SHARED_MSG, SENDMSG_NOOP,
                effectType, (int) (volume * 1000), null, 0);
    }

    /**
     * Loads samples into the soundpool.
     * This method must be called at when sound effects are enabled
     */
    public boolean loadSoundEffects() {
        synchronized (mSoundEffectsLock) {
            if (mSoundPool != null) {
                return true;
            }
            mSoundPool = new SoundPool(NUM_SOUNDPOOL_CHANNELS, AudioSystem.STREAM_SYSTEM, 0);
            if (mSoundPool == null) {
                return false;
            }
            /*
             * poolId table: The value -1 in this table indicates that corresponding
             * file (same index in SOUND_EFFECT_FILES[] has not been loaded.
             * Once loaded, the value in poolId is the sample ID and the same
             * sample can be reused for another effect using the same file.
             */
            int[] poolId = new int[SOUND_EFFECT_FILES.length];
            for (int fileIdx = 0; fileIdx < SOUND_EFFECT_FILES.length; fileIdx++) {
                poolId[fileIdx] = -1;
            }
            /*
             * Effects whose value in SOUND_EFFECT_FILES_MAP[effect][1] is -1 must be loaded.
             * If load succeeds, value in SOUND_EFFECT_FILES_MAP[effect][1] is > 0:
             * this indicates we have a valid sample loaded for this effect.
             */
            for (int effect = 0; effect < AudioManager.NUM_SOUND_EFFECTS; effect++) {
                // Do not load sample if this effect uses the MediaPlayer
                if (SOUND_EFFECT_FILES_MAP[effect][1] == 0) {
                    continue;
                }
                if (poolId[SOUND_EFFECT_FILES_MAP[effect][0]] == -1) {
                    String filePath = Environment.getRootDirectory() + SOUND_EFFECTS_PATH + SOUND_EFFECT_FILES[SOUND_EFFECT_FILES_MAP[effect][0]];
                    int sampleId = mSoundPool.load(filePath, 0);
                    SOUND_EFFECT_FILES_MAP[effect][1] = sampleId;
                    poolId[SOUND_EFFECT_FILES_MAP[effect][0]] = sampleId;
                    if (sampleId <= 0) {
                        Log.w(TAG, "Soundpool could not load file: "+filePath);
                    }
                } else {
                    SOUND_EFFECT_FILES_MAP[effect][1] = poolId[SOUND_EFFECT_FILES_MAP[effect][0]];
                }
            }
        }

        return true;
    }

    /**
     *  Unloads samples from the sound pool.
     *  This method can be called to free some memory when
     *  sound effects are disabled.
     */
    public void unloadSoundEffects() {
        synchronized (mSoundEffectsLock) {
            if (mSoundPool == null) {
                return;
            }
            int[] poolId = new int[SOUND_EFFECT_FILES.length];
            for (int fileIdx = 0; fileIdx < SOUND_EFFECT_FILES.length; fileIdx++) {
                poolId[fileIdx] = 0;
            }

            for (int effect = 0; effect < AudioManager.NUM_SOUND_EFFECTS; effect++) {
                if (SOUND_EFFECT_FILES_MAP[effect][1] <= 0) {
                    continue;
                }
                if (poolId[SOUND_EFFECT_FILES_MAP[effect][0]] == 0) {
                    mSoundPool.unload(SOUND_EFFECT_FILES_MAP[effect][1]);
                    SOUND_EFFECT_FILES_MAP[effect][1] = -1;
                    poolId[SOUND_EFFECT_FILES_MAP[effect][0]] = -1;
                }
            }
            mSoundPool = null;
        }
    }

    /** @see AudioManager#reloadAudioSettings() */
    public void reloadAudioSettings() {
        // restore ringer mode, ringer mode affected streams, mute affected streams and vibrate settings
        readPersistedSettings();

        // restore volume settings
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            VolumeStreamState streamState = mStreamStates[streamType];

            String settingName = System.VOLUME_SETTINGS[STREAM_VOLUME_ALIAS[streamType]];
            String lastAudibleSettingName = settingName + System.APPEND_FOR_LAST_AUDIBLE;
            int index = Settings.System.getInt(mContentResolver,
                                           settingName,
                                           AudioManager.DEFAULT_STREAM_VOLUME[streamType]);
            if (STREAM_VOLUME_ALIAS[streamType] != streamType) {
                index = rescaleIndex(index * 10, STREAM_VOLUME_ALIAS[streamType], streamType);
            } else {
                index *= 10;
            }
            streamState.mIndex = streamState.getValidIndex(index);

            index = (index + 5) / 10;
            index = Settings.System.getInt(mContentResolver,
                                            lastAudibleSettingName,
                                            (index > 0) ? index : AudioManager.DEFAULT_STREAM_VOLUME[streamType]);
            if (STREAM_VOLUME_ALIAS[streamType] != streamType) {
                index = rescaleIndex(index * 10, STREAM_VOLUME_ALIAS[streamType], streamType);
            } else {
                index *= 10;
            }
            streamState.mLastAudibleIndex = streamState.getValidIndex(index);

            // unmute stream that was muted but is not affect by mute anymore
            if (streamState.muteCount() != 0 && !isStreamAffectedByMute(streamType)) {
                int size = streamState.mDeathHandlers.size();
                for (int i = 0; i < size; i++) {
                    streamState.mDeathHandlers.get(i).mMuteCount = 1;
                    streamState.mDeathHandlers.get(i).mute(false);
                }
            }
            // apply stream volume
            if (streamState.muteCount() == 0) {
                setStreamVolumeIndex(streamType, streamState.mIndex);
            }
        }

        // apply new ringer mode
        setRingerModeInt(getRingerMode(), false);
    }

    /** @see AudioManager#setSpeakerphoneOn() */
    public void setSpeakerphoneOn(boolean on){
        if (!checkAudioSettingsPermission("setSpeakerphoneOn()")) {
            return;
        }
        if (on) {
            AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_SPEAKER);
            mForcedUseForComm = AudioSystem.FORCE_SPEAKER;
        } else {
            AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_NONE);
            mForcedUseForComm = AudioSystem.FORCE_NONE;
        }
    }

    /** @see AudioManager#isSpeakerphoneOn() */
    public boolean isSpeakerphoneOn() {
        if (mForcedUseForComm == AudioSystem.FORCE_SPEAKER) {
            return true;
        } else {
            return false;
        }
    }

    /** @see AudioManager#setBluetoothScoOn() */
    public void setBluetoothScoOn(boolean on){
        if (!checkAudioSettingsPermission("setBluetoothScoOn()")) {
            return;
        }
        if (on) {
            AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_BT_SCO);
            AudioSystem.setForceUse(AudioSystem.FOR_RECORD, AudioSystem.FORCE_BT_SCO);
            mForcedUseForComm = AudioSystem.FORCE_BT_SCO;
        } else {
            AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, AudioSystem.FORCE_NONE);
            AudioSystem.setForceUse(AudioSystem.FOR_RECORD, AudioSystem.FORCE_NONE);
            mForcedUseForComm = AudioSystem.FORCE_NONE;
        }
    }

    /** @see AudioManager#isBluetoothScoOn() */
    public boolean isBluetoothScoOn() {
        if (mForcedUseForComm == AudioSystem.FORCE_BT_SCO) {
            return true;
        } else {
            return false;
        }
    }

    /** @see AudioManager#startBluetoothSco() */
    public void startBluetoothSco(IBinder cb){
        if (!checkAudioSettingsPermission("startBluetoothSco()")) {
            return;
        }
        ScoClient client = getScoClient(cb);
        client.incCount();
    }

    /** @see AudioManager#stopBluetoothSco() */
    public void stopBluetoothSco(IBinder cb){
        if (!checkAudioSettingsPermission("stopBluetoothSco()")) {
            return;
        }
        ScoClient client = getScoClient(cb);
        client.decCount();
    }

    private class ScoClient implements IBinder.DeathRecipient {
        private IBinder mCb; // To be notified of client's death
        private int mStartcount; // number of SCO connections started by this client

        ScoClient(IBinder cb) {
            mCb = cb;
            mStartcount = 0;
        }

        public void binderDied() {
            synchronized(mScoClients) {
                Log.w(TAG, "SCO client died");
                int index = mScoClients.indexOf(this);
                if (index < 0) {
                    Log.w(TAG, "unregistered SCO client died");
                } else {
                    clearCount(true);
                    mScoClients.remove(this);
                }
            }
        }

        public void incCount() {
            synchronized(mScoClients) {
                requestScoState(BluetoothHeadset.AUDIO_STATE_CONNECTED);
                if (mStartcount == 0) {
                    try {
                        mCb.linkToDeath(this, 0);
                    } catch (RemoteException e) {
                        // client has already died!
                        Log.w(TAG, "ScoClient  incCount() could not link to "+mCb+" binder death");
                    }
                }
                mStartcount++;
            }
        }

        public void decCount() {
            synchronized(mScoClients) {
                if (mStartcount == 0) {
                    Log.w(TAG, "ScoClient.decCount() already 0");
                } else {
                    mStartcount--;
                    if (mStartcount == 0) {
                        try {
                            mCb.unlinkToDeath(this, 0);
                        } catch (NoSuchElementException e) {
                            Log.w(TAG, "decCount() going to 0 but not registered to binder");
                        }
                    }
                    requestScoState(BluetoothHeadset.AUDIO_STATE_DISCONNECTED);
                }
            }
        }

        public void clearCount(boolean stopSco) {
            synchronized(mScoClients) {
                if (mStartcount != 0) {
                    try {
                        mCb.unlinkToDeath(this, 0);
                    } catch (NoSuchElementException e) {
                        Log.w(TAG, "clearCount() mStartcount: "+mStartcount+" != 0 but not registered to binder");
                    }
                }
                mStartcount = 0;
                if (stopSco) {
                    requestScoState(BluetoothHeadset.AUDIO_STATE_DISCONNECTED);
                }
            }
        }

        public int getCount() {
            return mStartcount;
        }

        public IBinder getBinder() {
            return mCb;
        }

        public int totalCount() {
            synchronized(mScoClients) {
                int count = 0;
                int size = mScoClients.size();
                for (int i = 0; i < size; i++) {
                    count += mScoClients.get(i).getCount();
                }
                return count;
            }
        }

        private void requestScoState(int state) {
            if (totalCount() == 0 &&
                mBluetoothHeadsetConnected &&
                AudioService.this.mMode == AudioSystem.MODE_NORMAL) {
                if (state == BluetoothHeadset.AUDIO_STATE_CONNECTED) {
                    mBluetoothHeadset.startVoiceRecognition();
                } else {
                    mBluetoothHeadset.stopVoiceRecognition();
                }
            }
        }
    }

    public ScoClient getScoClient(IBinder cb) {
        synchronized(mScoClients) {
            ScoClient client;
            int size = mScoClients.size();
            for (int i = 0; i < size; i++) {
                client = mScoClients.get(i);
                if (client.getBinder() == cb)
                    return client;
            }
            client = new ScoClient(cb);
            mScoClients.add(client);
            return client;
        }
    }

    public void clearAllScoClients() {
        synchronized(mScoClients) {
            int size = mScoClients.size();
            for (int i = 0; i < size; i++) {
                mScoClients.get(i).clearCount(false);
            }
        }
    }

    private BluetoothHeadset.ServiceListener mBluetoothHeadsetServiceListener =
        new BluetoothHeadset.ServiceListener() {
        public void onServiceConnected() {
            if (mBluetoothHeadset != null) {
                BluetoothDevice device = mBluetoothHeadset.getCurrentHeadset();
                if (mBluetoothHeadset.getState(device) == BluetoothHeadset.STATE_CONNECTED) {
                    mBluetoothHeadsetConnected = true;
                }
            }
        }
        public void onServiceDisconnected() {
            if (mBluetoothHeadset != null) {
                BluetoothDevice device = mBluetoothHeadset.getCurrentHeadset();
                if (mBluetoothHeadset.getState(device) == BluetoothHeadset.STATE_DISCONNECTED) {
                    mBluetoothHeadsetConnected = false;
                    clearAllScoClients();
                }
            }
        }
    };

    ///////////////////////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Checks if the adjustment should change ringer mode instead of just
     * adjusting volume. If so, this will set the proper ringer mode and volume
     * indices on the stream states.
     */
    private boolean checkForRingerModeChange(int oldIndex, int direction) {
        boolean mVolumeControlSilent = Settings.System.getInt(mContentResolver,
                Settings.System.VOLUME_CONTROL_SILENT, 0) != 0;
        boolean vibrateInSilent = System.getInt(mContentResolver,
                System.VIBRATE_IN_SILENT, 1) == 1;
        boolean adjustVolumeIndex = true;
        int newRingerMode = mRingerMode;

        if (mRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            // audible mode, at the bottom of the scale
            if (direction == AudioManager.ADJUST_LOWER
                    && (oldIndex + 5) / 10 == 1) {
                if (vibrateInSilent) {
                    newRingerMode = AudioManager.RINGER_MODE_VIBRATE;
                } else {
                    newRingerMode = AudioManager.RINGER_MODE_SILENT;
                }
            }
        } else if (mRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
            if (direction == AudioManager.ADJUST_RAISE) {
                newRingerMode = AudioManager.RINGER_MODE_NORMAL;
            } else if (direction == AudioManager.ADJUST_LOWER
                    && mVolumeControlSilent) {
                newRingerMode = AudioManager.RINGER_MODE_SILENT;
            } else {
                // prevent last audible index to reach 0
                adjustVolumeIndex = false;
            }
        } else if (mRingerMode == AudioManager.RINGER_MODE_SILENT) {
            if (direction == AudioManager.ADJUST_RAISE) {
                if (vibrateInSilent) {
                    newRingerMode = AudioManager.RINGER_MODE_VIBRATE;
                } else {
                    newRingerMode = AudioManager.RINGER_MODE_NORMAL;
                }
            } else {
                adjustVolumeIndex = false;
            }
        } else {
            // is this fallback needed?
            newRingerMode = AudioManager.RINGER_MODE_NORMAL;
        }

        if (newRingerMode != mRingerMode) {
            setRingerMode(newRingerMode);

            /*
             * If we are changing ringer modes, do not increment/decrement the
             * volume index. Instead, the handler for the message above will
             * take care of changing the index.
             */
            adjustVolumeIndex = false;
        }

        return adjustVolumeIndex;
    }

    public boolean isStreamAffectedByRingerMode(int streamType) {
        return (mRingerModeAffectedStreams & (1 << streamType)) != 0;
    }

    private boolean isStreamMutedByRingerMode(int streamType) {
        return (mRingerModeMutedStreams & (1 << streamType)) != 0;
    }

    public boolean isStreamAffectedByMute(int streamType) {
        return (mMuteAffectedStreams & (1 << streamType)) != 0;
    }

    private void ensureValidDirection(int direction) {
        if (direction < AudioManager.ADJUST_LOWER || direction > AudioManager.ADJUST_RAISE) {
            throw new IllegalArgumentException("Bad direction " + direction);
        }
    }

    private void ensureValidStreamType(int streamType) {
        if (streamType < 0 || streamType >= mStreamStates.length) {
            throw new IllegalArgumentException("Bad stream type " + streamType);
        }
    }

    private int getActiveStreamType(int suggestedStreamType) {
        boolean isOffhook = false;
        try {
            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
            if (phone != null) isOffhook = phone.isOffhook();
        } catch (RemoteException e) {
            Log.w(TAG, "Couldn't connect to phone service", e);
        }

        if (AudioSystem.getForceUse(AudioSystem.FOR_COMMUNICATION) == AudioSystem.FORCE_BT_SCO) {
            // Log.v(TAG, "getActiveStreamType: Forcing STREAM_BLUETOOTH_SCO...");
            return AudioSystem.STREAM_BLUETOOTH_SCO;
        } else if (isOffhook || AudioSystem.isStreamActive(AudioSystem.STREAM_VOICE_CALL)) {
            // Log.v(TAG, "getActiveStreamType: Forcing STREAM_VOICE_CALL...");
            return AudioSystem.STREAM_VOICE_CALL;
        } else if (AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC)) {
            // Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC...");
            return AudioSystem.STREAM_MUSIC;
        } else if (suggestedStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
            // Log.v(TAG, "getActiveStreamType: Forcing STREAM_RING...");
            return AudioSystem.STREAM_RING;
        } else {
            // Log.v(TAG, "getActiveStreamType: Returning suggested type " + suggestedStreamType);
            return suggestedStreamType;
        }
    }

    private void broadcastRingerMode() {
        // Send sticky broadcast
        Intent broadcast = new Intent(AudioManager.RINGER_MODE_CHANGED_ACTION);
        broadcast.putExtra(AudioManager.EXTRA_RINGER_MODE, mRingerMode);
        broadcast.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        long origCallerIdentityToken = Binder.clearCallingIdentity();
        mContext.sendStickyBroadcast(broadcast);
        Binder.restoreCallingIdentity(origCallerIdentityToken);
    }

    private void broadcastVibrateSetting(int vibrateType) {
        // Send broadcast
        if (ActivityManagerNative.isSystemReady()) {
            Intent broadcast = new Intent(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
            broadcast.putExtra(AudioManager.EXTRA_VIBRATE_TYPE, vibrateType);
            broadcast.putExtra(AudioManager.EXTRA_VIBRATE_SETTING, getVibrateSetting(vibrateType));
            mContext.sendBroadcast(broadcast);
        }
    }

    // Message helper methods
    private static int getMsg(int baseMsg, int streamType) {
        return (baseMsg & 0xffff) | streamType << 16;
    }

    private static int getMsgBase(int msg) {
        return msg & 0xffff;
    }

    private static void sendMsg(Handler handler, int baseMsg, int streamType,
            int existingMsgPolicy, int arg1, int arg2, Object obj, int delay) {
        int msg = (streamType == SHARED_MSG) ? baseMsg : getMsg(baseMsg, streamType);

        if (existingMsgPolicy == SENDMSG_REPLACE) {
            handler.removeMessages(msg);
        } else if (existingMsgPolicy == SENDMSG_NOOP && handler.hasMessages(msg)) {
            return;
        }

        handler
                .sendMessageDelayed(handler.obtainMessage(msg, arg1, arg2, obj), delay);
    }

    boolean checkAudioSettingsPermission(String method) {
        if (mContext.checkCallingOrSelfPermission("android.permission.MODIFY_AUDIO_SETTINGS")
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        String msg = "Audio Settings Permission Denial: " + method + " from pid="
                + Binder.getCallingPid()
                + ", uid=" + Binder.getCallingUid();
        Log.w(TAG, msg);
        return false;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////

    public class VolumeStreamState {
        private final int mStreamType;

        private String mVolumeIndexSettingName;
        private String mLastAudibleVolumeIndexSettingName;
        private int mIndexMax;
        private int mIndex;
        private int mLastAudibleIndex;
        private ArrayList<VolumeDeathHandler> mDeathHandlers; //handles mute/solo requests client death

        private VolumeStreamState(String settingName, int streamType) {

            setVolumeIndexSettingName(settingName);

            mStreamType = streamType;

            final ContentResolver cr = mContentResolver;
            mIndexMax = MAX_STREAM_VOLUME[streamType];
            mIndex = Settings.System.getInt(cr,
                                            mVolumeIndexSettingName,
                                            AudioManager.DEFAULT_STREAM_VOLUME[streamType]);
            mLastAudibleIndex = Settings.System.getInt(cr,
                                                       mLastAudibleVolumeIndexSettingName,
                                                       (mIndex > 0) ? mIndex : AudioManager.DEFAULT_STREAM_VOLUME[streamType]);
            AudioSystem.initStreamVolume(streamType, 0, mIndexMax);
            mIndexMax *= 10;
            mIndex = getValidIndex(10 * mIndex);
            mLastAudibleIndex = getValidIndex(10 * mLastAudibleIndex);
            setStreamVolumeIndex(streamType, mIndex);
            mDeathHandlers = new ArrayList<VolumeDeathHandler>();
        }

        public void setVolumeIndexSettingName(String settingName) {
            mVolumeIndexSettingName = settingName;
            mLastAudibleVolumeIndexSettingName = settingName + System.APPEND_FOR_LAST_AUDIBLE;
        }

        public boolean adjustIndex(int deltaIndex) {
            return setIndex(mIndex + deltaIndex * 10, true);
        }

        public boolean setIndex(int index, boolean lastAudible) {
            int oldIndex = mIndex;
            mIndex = getValidIndex(index);

            if (oldIndex != mIndex) {
                if (lastAudible) {
                    mLastAudibleIndex = mIndex;
                }
                // Apply change to all streams using this one as alias
                int numStreamTypes = AudioSystem.getNumStreamTypes();
                for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                    if (streamType != mStreamType && STREAM_VOLUME_ALIAS[streamType] == mStreamType) {
                        mStreamStates[streamType].setIndex(rescaleIndex(mIndex, mStreamType, streamType), lastAudible);
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        public void setLastAudibleIndex(int index) {
            mLastAudibleIndex = getValidIndex(index);
        }

        public void adjustLastAudibleIndex(int deltaIndex) {
            setLastAudibleIndex(mLastAudibleIndex + deltaIndex * 10);
        }

        public int getMaxIndex() {
            return mIndexMax;
        }

        public void mute(IBinder cb, boolean state) {
            VolumeDeathHandler handler = getDeathHandler(cb, state);
            if (handler == null) {
                Log.e(TAG, "Could not get client death handler for stream: "+mStreamType);
                return;
            }
            handler.mute(state);
        }

        private int getValidIndex(int index) {
            if (index < 0) {
                return 0;
            } else if (index > mIndexMax) {
                return mIndexMax;
            }

            return index;
        }

        private class VolumeDeathHandler implements IBinder.DeathRecipient {
            private IBinder mICallback; // To be notified of client's death
            private int mMuteCount; // Number of active mutes for this client

            VolumeDeathHandler(IBinder cb) {
                mICallback = cb;
            }

            public void mute(boolean state) {
                synchronized(mDeathHandlers) {
                    if (state) {
                        if (mMuteCount == 0) {
                            // Register for client death notification
                            try {
                                // mICallback can be 0 if muted by AudioService
                                if (mICallback != null) {
                                    mICallback.linkToDeath(this, 0);
                                }
                                mDeathHandlers.add(this);
                                // If the stream is not yet muted by any client, set lvel to 0
                                if (muteCount() == 0) {
                                    setIndex(0, false);
                                    sendMsg(mAudioHandler, MSG_SET_SYSTEM_VOLUME, mStreamType, SENDMSG_NOOP, 0, 0,
                                            VolumeStreamState.this, 0);
                                }
                            } catch (RemoteException e) {
                                // Client has died!
                                binderDied();
                                mDeathHandlers.notify();
                                return;
                            }
                        } else {
                            Log.w(TAG, "stream: "+mStreamType+" was already muted by this client");
                        }
                        mMuteCount++;
                    } else {
                        if (mMuteCount == 0) {
                            Log.e(TAG, "unexpected unmute for stream: "+mStreamType);
                        } else {
                            mMuteCount--;
                            if (mMuteCount == 0) {
                                // Unregistr from client death notification
                                mDeathHandlers.remove(this);
                                // mICallback can be 0 if muted by AudioService
                                if (mICallback != null) {
                                    mICallback.unlinkToDeath(this, 0);
                                }
                                if (muteCount() == 0) {
                                    // If the stream is not muted any more, restore it's volume if
                                    // ringer mode allows it
                                    if (!isStreamAffectedByRingerMode(mStreamType) || mRingerMode == AudioManager.RINGER_MODE_NORMAL) {
                                        setIndex(mLastAudibleIndex, false);
                                        sendMsg(mAudioHandler, MSG_SET_SYSTEM_VOLUME, mStreamType, SENDMSG_NOOP, 0, 0,
                                                VolumeStreamState.this, 0);
                                    }
                                }
                            }
                        }
                    }
                    mDeathHandlers.notify();
                }
            }

            public void binderDied() {
                Log.w(TAG, "Volume service client died for stream: "+mStreamType);
                if (mMuteCount != 0) {
                    // Reset all active mute requests from this client.
                    mMuteCount = 1;
                    mute(false);
                }
            }
        }

        private int muteCount() {
            int count = 0;
            int size = mDeathHandlers.size();
            for (int i = 0; i < size; i++) {
                count += mDeathHandlers.get(i).mMuteCount;
            }
            return count;
        }

        private VolumeDeathHandler getDeathHandler(IBinder cb, boolean state) {
            synchronized(mDeathHandlers) {
                VolumeDeathHandler handler;
                int size = mDeathHandlers.size();
                for (int i = 0; i < size; i++) {
                    handler = mDeathHandlers.get(i);
                    if (cb == handler.mICallback) {
                        return handler;
                    }
                }
                // If this is the first mute request for this client, create a new
                // client death handler. Otherwise, it is an out of sequence unmute request.
                if (state) {
                    handler = new VolumeDeathHandler(cb);
                } else {
                    Log.w(TAG, "stream was not muted by this client");
                    handler = null;
                }
                return handler;
            }
        }
    }

    /** Thread that handles native AudioSystem control. */
    private class AudioSystemThread extends Thread {
        AudioSystemThread() {
            super("AudioService");
        }

        @Override
        public void run() {
            // Set this thread up so the handler will work on it
            Looper.prepare();

            synchronized(AudioService.this) {
                mAudioHandler = new AudioHandler();

                // Notify that the handler has been created
                AudioService.this.notify();
            }

            // Listen for volume change requests that are set by VolumePanel
            Looper.loop();
        }
    }

    /** Handles internal volume messages in separate volume thread. */
    private class AudioHandler extends Handler {

        private void setSystemVolume(VolumeStreamState streamState) {

            // Adjust volume
            setStreamVolumeIndex(streamState.mStreamType, streamState.mIndex);

            // Apply change to all streams using this one as alias
            int numStreamTypes = AudioSystem.getNumStreamTypes();
            for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                if (streamType != streamState.mStreamType &&
                    STREAM_VOLUME_ALIAS[streamType] == streamState.mStreamType) {
                    setStreamVolumeIndex(streamType, mStreamStates[streamType].mIndex);
                }
            }

            // Post a persist volume msg
            sendMsg(mAudioHandler, MSG_PERSIST_VOLUME, streamState.mStreamType,
                    SENDMSG_REPLACE, 1, 1, streamState, PERSIST_DELAY);
        }

        private void persistVolume(VolumeStreamState streamState, boolean current, boolean lastAudible) {
            if (current) {
                System.putInt(mContentResolver, streamState.mVolumeIndexSettingName,
                              (streamState.mIndex + 5)/ 10);
            }
            if (lastAudible) {
                System.putInt(mContentResolver, streamState.mLastAudibleVolumeIndexSettingName,
                    (streamState.mLastAudibleIndex + 5) / 10);
            }
        }

        private void persistRingerMode() {
            System.putInt(mContentResolver, System.MODE_RINGER, mRingerMode);
        }

        private void persistVibrateSetting() {
            System.putInt(mContentResolver, System.VIBRATE_ON, mVibrateSetting);
        }

        private void playSoundEffect(int effectType, int volume) {
            synchronized (mSoundEffectsLock) {
                if (mSoundPool == null) {
                    return;
                }
                float volFloat;
                // use STREAM_MUSIC volume attenuated by 3 dB if volume is not specified by caller
                if (volume < 0) {
                    // Same linear to log conversion as in native AudioSystem::linearToLog() (AudioSystem.cpp)
                    float dBPerStep = (float)((0.5 * 100) / MAX_STREAM_VOLUME[AudioSystem.STREAM_MUSIC]);
                    int musicVolIndex = (mStreamStates[AudioSystem.STREAM_MUSIC].mIndex + 5) / 10;
                    float musicVoldB = dBPerStep * (musicVolIndex - MAX_STREAM_VOLUME[AudioSystem.STREAM_MUSIC]);
                    volFloat = (float)Math.pow(10, (musicVoldB - 3)/20);
                } else {
                    volFloat = (float) volume / 1000.0f;
                }

                if (SOUND_EFFECT_FILES_MAP[effectType][1] > 0) {
                    mSoundPool.play(SOUND_EFFECT_FILES_MAP[effectType][1], volFloat, volFloat, 0, 0, 1.0f);
                } else {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    if (mediaPlayer != null) {
                        try {
                            String filePath = Environment.getRootDirectory() + SOUND_EFFECTS_PATH + SOUND_EFFECT_FILES[SOUND_EFFECT_FILES_MAP[effectType][0]];
                            mediaPlayer.setDataSource(filePath);
                            mediaPlayer.setAudioStreamType(AudioSystem.STREAM_SYSTEM);
                            mediaPlayer.prepare();
                            mediaPlayer.setVolume(volFloat, volFloat);
                            mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
                                public void onCompletion(MediaPlayer mp) {
                                    cleanupPlayer(mp);
                                }
                            });
                            mediaPlayer.setOnErrorListener(new OnErrorListener() {
                                public boolean onError(MediaPlayer mp, int what, int extra) {
                                    cleanupPlayer(mp);
                                    return true;
                                }
                            });
                            mediaPlayer.start();
                        } catch (IOException ex) {
                            Log.w(TAG, "MediaPlayer IOException: "+ex);
                        } catch (IllegalArgumentException ex) {
                            Log.w(TAG, "MediaPlayer IllegalArgumentException: "+ex);
                        } catch (IllegalStateException ex) {
                            Log.w(TAG, "MediaPlayer IllegalStateException: "+ex);
                        }
                    }
                }
            }
        }

        private void cleanupPlayer(MediaPlayer mp) {
            if (mp != null) {
                try {
                    mp.stop();
                    mp.release();
                } catch (IllegalStateException ex) {
                    Log.w(TAG, "MediaPlayer IllegalStateException: "+ex);
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            int baseMsgWhat = getMsgBase(msg.what);

            switch (baseMsgWhat) {

                case MSG_SET_SYSTEM_VOLUME:
                    setSystemVolume((VolumeStreamState) msg.obj);
                    break;

                case MSG_PERSIST_VOLUME:
                    persistVolume((VolumeStreamState) msg.obj, (msg.arg1 != 0), (msg.arg2 != 0));
                    break;

                case MSG_PERSIST_RINGER_MODE:
                    persistRingerMode();
                    break;

                case MSG_PERSIST_VIBRATE_SETTING:
                    persistVibrateSetting();
                    break;

                case MSG_MEDIA_SERVER_DIED:
                    // Force creation of new IAudioflinger interface
                    if (!mMediaServerOk) {
                        Log.e(TAG, "Media server died.");
                        AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC);
                        sendMsg(mAudioHandler, MSG_MEDIA_SERVER_DIED, SHARED_MSG, SENDMSG_NOOP, 0, 0,
                                null, 500);
                    }
                    break;

                case MSG_MEDIA_SERVER_STARTED:
                    Log.e(TAG, "Media server started.");
                    // indicate to audio HAL that we start the reconfiguration phase after a media
                    // server crash
                    // Note that MSG_MEDIA_SERVER_STARTED message is only received when the media server
                    // process restarts after a crash, not the first time it is started.
                    AudioSystem.setParameters("restarting=true");

                    // Restore device connection states
                    Set set = mConnectedDevices.entrySet();
                    Iterator i = set.iterator();
                    while(i.hasNext()){
                        Map.Entry device = (Map.Entry)i.next();
                        AudioSystem.setDeviceConnectionState(((Integer)device.getKey()).intValue(),
                                                             AudioSystem.DEVICE_STATE_AVAILABLE,
                                                             (String)device.getValue());
                    }

                    // Restore call state
                    AudioSystem.setPhoneState(mMode);

                    // Restore forced usage for communcations and record
                    AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, mForcedUseForComm);
                    AudioSystem.setForceUse(AudioSystem.FOR_RECORD, mForcedUseForComm);

                    // Restore stream volumes
                    int numStreamTypes = AudioSystem.getNumStreamTypes();
                    for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                        int index;
                        VolumeStreamState streamState = mStreamStates[streamType];
                        AudioSystem.initStreamVolume(streamType, 0, (streamState.mIndexMax + 5) / 10);
                        if (streamState.muteCount() == 0) {
                            index = streamState.mIndex;
                        } else {
                            index = 0;
                        }
                        setStreamVolumeIndex(streamType, index);
                    }

                    // Restore ringer mode
                    setRingerModeInt(getRingerMode(), false);

                    // indicate the end of reconfiguration phase to audio HAL
                    AudioSystem.setParameters("restarting=false");
                    break;

                case MSG_PLAY_SOUND_EFFECT:
                    playSoundEffect(msg.arg1, msg.arg2);
                    break;

                case MSG_BTA2DP_DOCK_TIMEOUT:
                    // msg.obj  == address of BTA2DP device
                    makeA2dpDeviceUnavailableNow( (String) msg.obj );
                    break;
            }
        }
    }

    private class SettingsObserver extends ContentObserver {

        SettingsObserver() {
            super(new Handler());
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED), false, this);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.NOTIFICATIONS_USE_RING_VOLUME), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            synchronized (mSettingsLock) {
                int ringerModeAffectedStreams = Settings.System.getInt(mContentResolver,
                        Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                        0);
                if (ringerModeAffectedStreams != mRingerModeAffectedStreams) {
                    /*
                     * Ensure all stream types that should be affected by ringer mode
                     * are in the proper state.
                     */
                    mRingerModeAffectedStreams = ringerModeAffectedStreams;
                    setRingerModeInt(getRingerMode(), false);
                }

                int notificationsUseRingVolume = Settings.System.getInt(mContentResolver,
                        Settings.System.NOTIFICATIONS_USE_RING_VOLUME,
                        1);
                if (notificationsUseRingVolume != mNotificationsUseRingVolume) {
                    mNotificationsUseRingVolume = notificationsUseRingVolume;
                    if (mNotificationsUseRingVolume == 1) {
                        STREAM_VOLUME_ALIAS[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_RING;
                        mStreamStates[AudioSystem.STREAM_NOTIFICATION].setVolumeIndexSettingName(
                                System.VOLUME_SETTINGS[AudioSystem.STREAM_RING]);
                    } else {
                        STREAM_VOLUME_ALIAS[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_NOTIFICATION;
                        mStreamStates[AudioSystem.STREAM_NOTIFICATION].setVolumeIndexSettingName(
                                System.VOLUME_SETTINGS[AudioSystem.STREAM_NOTIFICATION]);
                        // Persist notification volume volume as it was not persisted while aliased to ring volume
                        //  and persist with no delay as there might be registered observers of the persisted
                        //  notification volume.
                        sendMsg(mAudioHandler, MSG_PERSIST_VOLUME, AudioSystem.STREAM_NOTIFICATION,
                                SENDMSG_REPLACE, 1, 1, mStreamStates[AudioSystem.STREAM_NOTIFICATION], 0);
                    }
                }
            }
        }
    }

    private void makeA2dpDeviceAvailable(String address) {
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_AVAILABLE,
                address);
        // Reset A2DP suspend state each time a new sink is connected
        AudioSystem.setParameters("A2dpSuspended=false");
        mConnectedDevices.put( new Integer(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP),
                address);
    }

    private void makeA2dpDeviceUnavailableNow(String address) {
        Intent noisyIntent = new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        mContext.sendBroadcast(noisyIntent);
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_UNAVAILABLE,
                address);
        mConnectedDevices.remove(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP);
    }

    private void makeA2dpDeviceUnavailableLater(String address) {
        // prevent any activity on the A2DP audio output to avoid unwanted
        // reconnection of the sink.
        AudioSystem.setParameters("A2dpSuspended=true");
        // the device will be made unavailable later, so consider it disconnected right away
        mConnectedDevices.remove(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP);
        // send the delayed message to make the device unavailable later
        Message msg = mAudioHandler.obtainMessage(MSG_BTA2DP_DOCK_TIMEOUT, address);
        mAudioHandler.sendMessageDelayed(msg, BTA2DP_DOCK_TIMEOUT_MILLIS);

    }

    private void cancelA2dpDeviceTimeout() {
        mAudioHandler.removeMessages(MSG_BTA2DP_DOCK_TIMEOUT);
    }

    private boolean hasScheduledA2dpDockTimeout() {
        return mAudioHandler.hasMessages(MSG_BTA2DP_DOCK_TIMEOUT);
    }

    /* cache of the address of the last dock the device was connected to */
    private String mDockAddress;

    /**
     * Receiver for misc intent broadcasts the Phone app cares about.
     */
    private class AudioServiceBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                int config;
                switch (dockState) {
                    case Intent.EXTRA_DOCK_STATE_DESK:
                        config = AudioSystem.FORCE_BT_DESK_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_CAR:
                        config = AudioSystem.FORCE_BT_CAR_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_UNDOCKED:
                    default:
                        config = AudioSystem.FORCE_NONE;
                }
                AudioSystem.setForceUse(AudioSystem.FOR_DOCK, config);
            } else if (action.equals(BluetoothA2dp.ACTION_SINK_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothA2dp.EXTRA_SINK_STATE,
                                               BluetoothA2dp.STATE_DISCONNECTED);
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String address = btDevice.getAddress();
                boolean isConnected = (mConnectedDevices.containsKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP) &&
                                       ((String)mConnectedDevices.get(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP)).equals(address));

                if (isConnected &&
                    state != BluetoothA2dp.STATE_CONNECTED && state != BluetoothA2dp.STATE_PLAYING) {
                    if (btDevice.isBluetoothDock()) {
                        if (state == BluetoothA2dp.STATE_DISCONNECTED) {
                            // introduction of a delay for transient disconnections of docks when
                            // power is rapidly turned off/on, this message will be canceled if
                            // we reconnect the dock under a preset delay
                            makeA2dpDeviceUnavailableLater(address);
                            // the next time isConnected is evaluated, it will be false for the dock
                        }
                    } else {
                        makeA2dpDeviceUnavailableNow(address);
                    }
                } else if (!isConnected &&
                             (state == BluetoothA2dp.STATE_CONNECTED ||
                              state == BluetoothA2dp.STATE_PLAYING)) {
                    if (btDevice.isBluetoothDock()) {
                        // this could be a reconnection after a transient disconnection
                        cancelA2dpDeviceTimeout();
                        mDockAddress = address;
                    } else {
                        // this could be a connection of another A2DP device before the timeout of
                        // a dock: cancel the dock timeout, and make the dock unavailable now
                        if(hasScheduledA2dpDockTimeout()) {
                            cancelA2dpDeviceTimeout();
                            makeA2dpDeviceUnavailableNow(mDockAddress);
                        }
                    }
                    makeA2dpDeviceAvailable(address);
                }
            } else if (action.equals(BluetoothHeadset.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                                               BluetoothHeadset.STATE_ERROR);
                int device = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO;
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String address = null;
                if (btDevice != null) {
                    address = btDevice.getAddress();
                    BluetoothClass btClass = btDevice.getBluetoothClass();
                    if (btClass != null) {
                        switch (btClass.getDeviceClass()) {
                        case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                        case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                            device = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET;
                            break;
                        case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                            device = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_CARKIT;
                            break;
                        }
                    }
                }

                boolean isConnected = (mConnectedDevices.containsKey(device) &&
                                       ((String)mConnectedDevices.get(device)).equals(address));

                if (isConnected && state != BluetoothHeadset.STATE_CONNECTED) {
                    AudioSystem.setDeviceConnectionState(device,
                                                         AudioSystem.DEVICE_STATE_UNAVAILABLE,
                                                         address);
                    mConnectedDevices.remove(device);
                    mBluetoothHeadsetConnected = false;
                    clearAllScoClients();
                } else if (!isConnected && state == BluetoothHeadset.STATE_CONNECTED) {
                    AudioSystem.setDeviceConnectionState(device,
                                                         AudioSystem.DEVICE_STATE_AVAILABLE,
                                                         address);
                    mConnectedDevices.put(new Integer(device), address);
                    mBluetoothHeadsetConnected = true;
                }
            } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", 0);
                int microphone = intent.getIntExtra("microphone", 0);
                String name = intent.getStringExtra("name");
                
                if (name != null && !name.equalsIgnoreCase("1")) {
                    if (microphone != 0) {
                        boolean isConnected = mConnectedDevices.containsKey(AudioSystem.DEVICE_OUT_WIRED_HEADSET);
                        if (state == 0 && isConnected) {
                            AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_WIRED_HEADSET,
                                    AudioSystem.DEVICE_STATE_UNAVAILABLE,
                                    "");
                            mConnectedDevices.remove(AudioSystem.DEVICE_OUT_WIRED_HEADSET);
                        } else if (state == 1 && !isConnected)  {
                            AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_WIRED_HEADSET,
                                    AudioSystem.DEVICE_STATE_AVAILABLE,
                                    "");
                            mConnectedDevices.put( new Integer(AudioSystem.DEVICE_OUT_WIRED_HEADSET), "");
                        }
                    } else {
                        boolean isConnected = mConnectedDevices.containsKey(AudioSystem.DEVICE_OUT_WIRED_HEADPHONE);
                        if (state == 0 && isConnected) {
                            AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_WIRED_HEADPHONE,
                                    AudioSystem.DEVICE_STATE_UNAVAILABLE,
                                    "");
                            mConnectedDevices.remove(AudioSystem.DEVICE_OUT_WIRED_HEADPHONE);
                        } else if (state == 1 && !isConnected)  {
                            AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_WIRED_HEADPHONE,
                                    AudioSystem.DEVICE_STATE_AVAILABLE,
                                    "");
                            mConnectedDevices.put( new Integer(AudioSystem.DEVICE_OUT_WIRED_HEADPHONE), "");
                        }
                    }
                } else {
                    if (state == 0) {
                        AudioSystem.setDeviceConnectionState(0x20000,
                                AudioSystem.DEVICE_STATE_UNAVAILABLE,
                                "");
                    } else if (state == 1)  {
                        AudioSystem.setDeviceConnectionState(0x20000,
                                AudioSystem.DEVICE_STATE_AVAILABLE,
                                "");
                    }
                }
            } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothHeadset.EXTRA_AUDIO_STATE,
                                               BluetoothHeadset.STATE_ERROR);
                synchronized (mScoClients) {
                    if (!mScoClients.isEmpty()) {
                        switch (state) {
                        case BluetoothHeadset.AUDIO_STATE_CONNECTED:
                            state = AudioManager.SCO_AUDIO_STATE_CONNECTED;
                            break;
                        case BluetoothHeadset.AUDIO_STATE_DISCONNECTED:
                            state = AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
                            break;
                        default:
                            state = AudioManager.SCO_AUDIO_STATE_ERROR;
                            break;
                        }
                        if (state != AudioManager.SCO_AUDIO_STATE_ERROR) {
                            Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
                            newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state);
                            mContext.sendStickyBroadcast(newIntent);
                        }
                    }
                }
            }
        }
    }

    //==========================================================================================
    // AudioFocus
    //==========================================================================================

    /* constant to identify focus stack entry that is used to hold the focus while the phone
     * is ringing or during a call
     */
    private final static String IN_VOICE_COMM_FOCUS_ID = "AudioFocus_For_Phone_Ring_And_Calls";

    private final static Object mAudioFocusLock = new Object();

    private final static Object mRingingLock = new Object();

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                //Log.v(TAG, " CALL_STATE_RINGING");
                synchronized(mRingingLock) {
                    mIsRinging = true;
                }
                int ringVolume = AudioService.this.getStreamVolume(AudioManager.STREAM_RING);
                if (ringVolume > 0) {
                    requestAudioFocus(AudioManager.STREAM_RING,
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                                null, null /* both allowed to be null only for this clientId */,
                                IN_VOICE_COMM_FOCUS_ID /*clientId*/);
                }
            } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                //Log.v(TAG, " CALL_STATE_OFFHOOK");
                synchronized(mRingingLock) {
                    mIsRinging = false;
                }
                requestAudioFocus(AudioManager.STREAM_RING,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                        null, null /* both allowed to be null only for this clientId */,
                        IN_VOICE_COMM_FOCUS_ID /*clientId*/);
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                //Log.v(TAG, " CALL_STATE_IDLE");
                synchronized(mRingingLock) {
                    mIsRinging = false;
                }
                abandonAudioFocus(null, IN_VOICE_COMM_FOCUS_ID, null);
            }
        }
    };

    private void notifyTopOfAudioFocusStack() {
        // notify the top of the stack it gained focus
        if (!mFocusStack.empty() && (mFocusStack.peek().mFocusDispatcher != null)) {
            if (canReassignAudioFocus()) {
                try {
                    mFocusStack.peek().mFocusDispatcher.dispatchAudioFocusChange(
                            AudioManager.AUDIOFOCUS_GAIN, mFocusStack.peek().mClientId);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failure to signal gain of audio control focus due to "+ e);
                    e.printStackTrace();
                }
            }
        }
    }

    private static class FocusStackEntry {
        public int mStreamType = -1;// no stream type
        public boolean mIsTransportControlReceiver = false;
        public IAudioFocusDispatcher mFocusDispatcher = null;
        public IBinder mSourceRef = null;
        public String mClientId;
        public int mFocusChangeType;

        public FocusStackEntry() {
        }

        public FocusStackEntry(int streamType, int duration, boolean isTransportControlReceiver,
                IAudioFocusDispatcher afl, IBinder source, String id) {
            mStreamType = streamType;
            mIsTransportControlReceiver = isTransportControlReceiver;
            mFocusDispatcher = afl;
            mSourceRef = source;
            mClientId = id;
            mFocusChangeType = duration;
        }
    }

    private Stack<FocusStackEntry> mFocusStack = new Stack<FocusStackEntry>();

    /**
     * Helper function:
     * Display in the log the current entries in the audio focus stack
     */
    private void dumpFocusStack(PrintWriter pw) {
        pw.println("\nAudio Focus stack entries:");
        synchronized(mAudioFocusLock) {
            Iterator<FocusStackEntry> stackIterator = mFocusStack.iterator();
            while(stackIterator.hasNext()) {
                FocusStackEntry fse = stackIterator.next();
                pw.println("     source:" + fse.mSourceRef + " -- client: " + fse.mClientId
                        + " -- duration: " +fse.mFocusChangeType);
            }
        }
    }

    /**
     * Helper function:
     * Remove a focus listener from the focus stack.
     * @param focusListenerToRemove the focus listener
     * @param signal if true and the listener was at the top of the focus stack, i.e. it was holding
     *   focus, notify the next item in the stack it gained focus.
     */
    private void removeFocusStackEntry(String clientToRemove, boolean signal) {
        // is the current top of the focus stack abandoning focus? (because of death or request)
        if (!mFocusStack.empty() && mFocusStack.peek().mClientId.equals(clientToRemove))
        {
            //Log.i(TAG, "   removeFocusStackEntry() removing top of stack");
            mFocusStack.pop();
            if (signal) {
                // notify the new top of the stack it gained focus
                notifyTopOfAudioFocusStack();
            }
        } else {
            // focus is abandoned by a client that's not at the top of the stack,
            // no need to update focus.
            Iterator<FocusStackEntry> stackIterator = mFocusStack.iterator();
            while(stackIterator.hasNext()) {
                FocusStackEntry fse = (FocusStackEntry)stackIterator.next();
                if(fse.mClientId.equals(clientToRemove)) {
                    Log.i(TAG, " AudioFocus  abandonAudioFocus(): removing entry for "
                            + fse.mClientId);
                    mFocusStack.remove(fse);
                }
            }
        }
    }

    /**
     * Helper function:
     * Remove focus listeners from the focus stack for a particular client.
     */
    private void removeFocusStackEntryForClient(IBinder cb) {
        // is the owner of the audio focus part of the client to remove?
        boolean isTopOfStackForClientToRemove = !mFocusStack.isEmpty() &&
                mFocusStack.peek().mSourceRef.equals(cb);
        Iterator<FocusStackEntry> stackIterator = mFocusStack.iterator();
        while(stackIterator.hasNext()) {
            FocusStackEntry fse = (FocusStackEntry)stackIterator.next();
            if(fse.mSourceRef.equals(cb)) {
                Log.i(TAG, " AudioFocus  abandonAudioFocus(): removing entry for "
                        + fse.mClientId);
                mFocusStack.remove(fse);
            }
        }
        if (isTopOfStackForClientToRemove) {
            // we removed an entry at the top of the stack:
            //  notify the new top of the stack it gained focus.
            notifyTopOfAudioFocusStack();
        }
    }

    /**
     * Helper function:
     * Returns true if the system is in a state where the focus can be reevaluated, false otherwise.
     */
    private boolean canReassignAudioFocus() {
        // focus requests are rejected during a phone call or when the phone is ringing
        // this is equivalent to IN_VOICE_COMM_FOCUS_ID having the focus
        if (!mFocusStack.isEmpty() && IN_VOICE_COMM_FOCUS_ID.equals(mFocusStack.peek().mClientId)) {
            return false;
        }
        return true;
    }

    /**
     * Inner class to monitor audio focus client deaths, and remove them from the audio focus
     * stack if necessary.
     */
    private class AudioFocusDeathHandler implements IBinder.DeathRecipient {
        private final IBinder mCb; // To be notified of client's death

        AudioFocusDeathHandler(IBinder cb) {
            mCb = cb;
        }

        public void binderDied() {
            synchronized(mAudioFocusLock) {
                Log.w(TAG, "  AudioFocus   audio focus client died");
                removeFocusStackEntryForClient(mCb);
                mAudioFocusDeathHandlers.remove(this);
            }
        }

        public IBinder getBinder() {
            return mCb;
        }
    }


    /** @see AudioManager#requestAudioFocus(IAudioFocusDispatcher, int, int) */
    public int requestAudioFocus(int mainStreamType, int focusChangeHint, IBinder cb,
            IAudioFocusDispatcher fd, String clientId) {
        Log.i(TAG, " AudioFocus  requestAudioFocus() from " + clientId);
        // the main stream type for the audio focus request is currently not used. It may
        // potentially be used to handle multiple stream type-dependent audio focuses.

        // we need a valid binder callback for clients other than the AudioService's phone
        // state listener
        if (!IN_VOICE_COMM_FOCUS_ID.equals(clientId) && ((cb == null) || !cb.pingBinder())) {
            Log.i(TAG, " AudioFocus  DOA client for requestAudioFocus(), exiting");
            return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }

        synchronized(mAudioFocusLock) {
            if (!canReassignAudioFocus()) {
                return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
            }

            if (!mFocusStack.empty() && mFocusStack.peek().mClientId.equals(clientId)) {
                // if focus is already owned by this client and the reason for acquiring the focus
                // hasn't changed, don't do anything
                if (mFocusStack.peek().mFocusChangeType == focusChangeHint) {
                    return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                }
                // the reason for the audio focus request has changed: remove the current top of
                // stack and respond as if we had a new focus owner
                mFocusStack.pop();
            }

            // notify current top of stack it is losing focus
            if (!mFocusStack.empty() && (mFocusStack.peek().mFocusDispatcher != null)) {
                try {
                    mFocusStack.peek().mFocusDispatcher.dispatchAudioFocusChange(
                            -1 * focusChangeHint, // loss and gain codes are inverse of each other
                            mFocusStack.peek().mClientId);
                } catch (RemoteException e) {
                    Log.e(TAG, " Failure to signal loss of focus due to "+ e);
                    e.printStackTrace();
                }
            }

            // focus requester might already be somewhere below in the stack, remove it
            removeFocusStackEntry(clientId, false);

            // push focus requester at the top of the audio focus stack
            mFocusStack.push(new FocusStackEntry(mainStreamType, focusChangeHint, false, fd, cb,
                    clientId));
        }//synchronized(mAudioFocusLock)

        // handle the potential premature death of the new holder of the focus
        // (premature death == death before abandoning focus) for a client which is not the
        // AudioService's phone state listener
        if (!IN_VOICE_COMM_FOCUS_ID.equals(clientId)) {
            // Register for client death notification
            int size = 0;
            int i = 0;
            synchronized(mAudioFocusLock) {
              size = mAudioFocusDeathHandlers.size();
              for (i = 0; i < size; i++) {
                   final AudioFocusDeathHandler afdhandler = mAudioFocusDeathHandlers.get(i);

                   if(afdhandler.getBinder() == cb) {
                      break;
                   }
              }
            }
            // Register once per client
            if (i == size) {
                AudioFocusDeathHandler afdh = new AudioFocusDeathHandler(cb);

                try {
                    cb.linkToDeath(afdh, 0);
                    synchronized(mAudioFocusLock) {
                       mAudioFocusDeathHandlers.add(afdh);
                    }
                } catch (RemoteException e) {
                    // client has already died!
                    Log.w(TAG, "AudioFocus  requestAudioFocus() could not link to "+cb+" binder death");
                }
            }
        }

        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    /** @see AudioManager#abandonAudioFocus(IAudioFocusDispatcher) */
    public int abandonAudioFocus(IAudioFocusDispatcher fl, String clientId, IBinder cb) {
        Log.i(TAG, " AudioFocus  abandonAudioFocus() from " + clientId);
        try {
            // this will take care of notifying the new focus owner if needed
            synchronized(mAudioFocusLock) {
                removeFocusStackEntry(clientId, true);

                if (!IN_VOICE_COMM_FOCUS_ID.equals(clientId)) {

                    int size = mAudioFocusDeathHandlers.size();

                    for (int i = 0; i < size; i++) {

                         final AudioFocusDeathHandler afdh = mAudioFocusDeathHandlers.get(i);

                         if (cb == afdh.getBinder()) {
                             cb.unlinkToDeath(afdh ,0);
                             mAudioFocusDeathHandlers.remove(i);
                             break;
                         }
                    }
                }
            }
        } catch (java.util.ConcurrentModificationException cme) {
            // Catching this exception here is temporary. It is here just to prevent
            // a crash seen when the "Silent" notification is played. This is believed to be fixed
            // but this try catch block is left just to be safe.
            Log.e(TAG, "FATAL EXCEPTION AudioFocus  abandonAudioFocus() caused " + cme);
            cme.printStackTrace();
        }

        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }


    public void unregisterAudioFocusClient(String clientId, IBinder cb) {
        synchronized(mAudioFocusLock) {
            removeFocusStackEntry(clientId, false);

            if (!IN_VOICE_COMM_FOCUS_ID.equals(clientId)) {

                int size = mAudioFocusDeathHandlers.size();

                for (int i = 0; i < size; i++) {

                     final AudioFocusDeathHandler afdh = mAudioFocusDeathHandlers.get(i);

                     if (cb == afdh.getBinder()) {
                         cb.unlinkToDeath(afdh ,0);
                         mAudioFocusDeathHandlers.remove(i);
                         break;
                     }
                }
            }
        }
    }

    //==========================================================================================
    // RemoteControl
    //==========================================================================================
    /**
     * Receiver for media button intents. Handles the dispatching of the media button event
     * to one of the registered listeners, or if there was none, resumes the intent broadcast
     * to the rest of the system.
     */
    private class MediaButtonBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!Intent.ACTION_MEDIA_BUTTON.equals(action)) {
                return;
            }
            KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event != null) {
                // if in a call or ringing, do not break the current phone app behavior
                // TODO modify this to let the phone app specifically get the RC focus
                //      add modify the phone app to take advantage of the new API
                synchronized(mRingingLock) {
                    if (mIsRinging || (getMode() == AudioSystem.MODE_IN_CALL) ||
                            (getMode() == AudioSystem.MODE_IN_COMMUNICATION) ||
                            (getMode() == AudioSystem.MODE_RINGTONE) ) {
                        return;
                    }
                }
                synchronized(mRCStack) {
                    if (!mRCStack.empty()) {
                        // create a new intent specifically aimed at the current registered listener
                        Intent targetedIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        targetedIntent.putExtras(intent.getExtras());
                        targetedIntent.setComponent(mRCStack.peek().mReceiverComponent);
                        // trap the current broadcast
                        abortBroadcast();
                        //Log.v(TAG, " Sending intent" + targetedIntent);
                        context.sendBroadcast(targetedIntent, null);
                    }
                }
            }
        }
    }

    private static class RemoteControlStackEntry {
        public ComponentName mReceiverComponent;// always non null
        // TODO implement registration expiration?
        //public int mRegistrationTime;

        public RemoteControlStackEntry() {
        }

        public RemoteControlStackEntry(ComponentName r) {
            mReceiverComponent = r;
        }
    }

    private Stack<RemoteControlStackEntry> mRCStack = new Stack<RemoteControlStackEntry>();

    /**
     * Helper function:
     * Display in the log the current entries in the remote control focus stack
     */
    private void dumpRCStack(PrintWriter pw) {
        pw.println("\nRemote Control stack entries:");
        synchronized(mRCStack) {
            Iterator<RemoteControlStackEntry> stackIterator = mRCStack.iterator();
            while(stackIterator.hasNext()) {
                RemoteControlStackEntry fse = stackIterator.next();
                pw.println("     receiver:" + fse.mReceiverComponent);
            }
        }
    }

    /**
     * Helper function:
     * Set the new remote control receiver at the top of the RC focus stack
     */
    private void pushMediaButtonReceiver(ComponentName newReceiver) {
        // already at top of stack?
        if (!mRCStack.empty() && mRCStack.peek().mReceiverComponent.equals(newReceiver)) {
            return;
        }
        Iterator<RemoteControlStackEntry> stackIterator = mRCStack.iterator();
        while(stackIterator.hasNext()) {
            RemoteControlStackEntry rcse = (RemoteControlStackEntry)stackIterator.next();
            if(rcse.mReceiverComponent.equals(newReceiver)) {
                mRCStack.remove(rcse);
                break;
            }
        }
        mRCStack.push(new RemoteControlStackEntry(newReceiver));
    }

    /**
     * Helper function:
     * Remove the remote control receiver from the RC focus stack
     */
    private void removeMediaButtonReceiver(ComponentName newReceiver) {
        Iterator<RemoteControlStackEntry> stackIterator = mRCStack.iterator();
        while(stackIterator.hasNext()) {
            RemoteControlStackEntry rcse = (RemoteControlStackEntry)stackIterator.next();
            if(rcse.mReceiverComponent.equals(newReceiver)) {
                mRCStack.remove(rcse);
                break;
            }
        }
    }


    /** see AudioManager.registerMediaButtonEventReceiver(ComponentName eventReceiver) */
    public void registerMediaButtonEventReceiver(ComponentName eventReceiver) {
        Log.i(TAG, "  Remote Control   registerMediaButtonEventReceiver() for " + eventReceiver);

        synchronized(mRCStack) {
            pushMediaButtonReceiver(eventReceiver);
        }
    }

    /** see AudioManager.unregisterMediaButtonEventReceiver(ComponentName eventReceiver) */
    public void unregisterMediaButtonEventReceiver(ComponentName eventReceiver) {
        Log.i(TAG, "  Remote Control   unregisterMediaButtonEventReceiver() for " + eventReceiver);

        synchronized(mRCStack) {
            removeMediaButtonReceiver(eventReceiver);
        }
    }


    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        // TODO probably a lot more to do here than just the audio focus and remote control stacks
        dumpFocusStack(pw);
        dumpRCStack(pw);
    }


}
