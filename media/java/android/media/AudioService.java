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

import static android.Manifest.permission.REMOTE_AUDIO_PLAYBACK;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ThemeUtils;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.net.Uri;
import android.net.http.CertificateChainValidator;
import android.net.http.SslError;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.System;
import android.provider.Settings.SettingNotFoundException;
import android.speech.RecognizerIntent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.VolumePanel;
import android.view.WindowManager;

import com.android.internal.telephony.ITelephony;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

    /** Debug remote control client/display feature */
    protected static final boolean DEBUG_RC = false;
    /** Debug volumes */
    protected static final boolean DEBUG_VOL = false;
    /** Debug cert verification */
    private static final boolean DEBUG_CERTS = false;

    /** How long to delay before persisting a change in volume/ringer mode. */
    private static final int PERSIST_DELAY = 500;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final AppOpsManager mAppOps;
    private final boolean mVoiceCapable;

    /** The UI */
    private VolumePanel mVolumePanel;
    private Context mUiContext;
    private Handler mHandler;

    // sendMsg() flags
    /** If the msg is already queued, replace it with this one. */
    private static final int SENDMSG_REPLACE = 0;
    /** If the msg is already queued, ignore this one and leave the old. */
    private static final int SENDMSG_NOOP = 1;
    /** If the msg is already queued, queue this one and leave the old. */
    private static final int SENDMSG_QUEUE = 2;

    // AudioHandler messages
    private static final int MSG_SET_DEVICE_VOLUME = 0;
    private static final int MSG_PERSIST_VOLUME = 1;
    private static final int MSG_PERSIST_MASTER_VOLUME = 2;
    private static final int MSG_PERSIST_RINGER_MODE = 3;
    private static final int MSG_MEDIA_SERVER_DIED = 4;
    private static final int MSG_PLAY_SOUND_EFFECT = 5;
    private static final int MSG_BTA2DP_DOCK_TIMEOUT = 6;
    private static final int MSG_LOAD_SOUND_EFFECTS = 7;
    private static final int MSG_SET_FORCE_USE = 8;
    private static final int MSG_BT_HEADSET_CNCT_FAILED = 9;
    private static final int MSG_SET_ALL_VOLUMES = 10;
    private static final int MSG_PERSIST_MASTER_VOLUME_MUTE = 11;
    private static final int MSG_REPORT_NEW_ROUTES = 12;
    private static final int MSG_SET_FORCE_BT_A2DP_USE = 13;
    private static final int MSG_CHECK_MUSIC_ACTIVE = 14;
    private static final int MSG_BROADCAST_AUDIO_BECOMING_NOISY = 15;
    private static final int MSG_CONFIGURE_SAFE_MEDIA_VOLUME = 16;
    private static final int MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED = 17;
    private static final int MSG_PERSIST_SAFE_VOLUME_STATE = 18;
    private static final int MSG_BROADCAST_BT_CONNECTION_STATE = 19;
    private static final int MSG_UNLOAD_SOUND_EFFECTS = 20;
    // start of messages handled under wakelock
    //   these messages can only be queued, i.e. sent with queueMsgUnderWakeLock(),
    //   and not with sendMsg(..., ..., SENDMSG_QUEUE, ...)
    private static final int MSG_SET_WIRED_DEVICE_CONNECTION_STATE = 100;
    private static final int MSG_SET_A2DP_CONNECTION_STATE = 101;
    // end of messages handled under wakelock

    private static final int BTA2DP_DOCK_TIMEOUT_MILLIS = 8000;
    // Timeout for connection to bluetooth headset service
    private static final int BT_HEADSET_CNCT_TIMEOUT_MS = 3000;

    /** @see AudioSystemThread */
    private AudioSystemThread mAudioSystemThread;
    /** @see AudioHandler */
    private AudioHandler mAudioHandler;
    /** @see VolumeStreamState */
    private VolumeStreamState[] mStreamStates;
    private SettingsObserver mSettingsObserver;

    private int mMode = AudioSystem.MODE_NORMAL;
    // protects mRingerMode
    private final Object mSettingsLock = new Object();

    private SoundPool mSoundPool;
    private final Object mSoundEffectsLock = new Object();
    private static final int NUM_SOUNDPOOL_CHANNELS = 4;

    // Internally master volume is a float in the 0.0 - 1.0 range,
    // but to support integer based AudioManager API we translate it to 0 - 100
    private static final int MAX_MASTER_VOLUME = 100;

    // Maximum volume adjust steps allowed in a single batch call.
    private static final int MAX_BATCH_VOLUME_ADJUST_STEPS = 4;

    /* Sound effect file names  */
    private static final String SOUND_EFFECTS_PATH = "/media/audio/ui/";
    private static final List<String> SOUND_EFFECT_FILES = new ArrayList<String>();

    /* Sound effect file name mapping sound effect id (AudioManager.FX_xxx) to
     * file index in SOUND_EFFECT_FILES[] (first column) and indicating if effect
     * uses soundpool (second column) */
    private final int[][] SOUND_EFFECT_FILES_MAP = new int[AudioManager.NUM_SOUND_EFFECTS][2];

   /** @hide Maximum volume index values for audio streams */
    private static final int[] MAX_STREAM_VOLUME = new int[] {
        5,  // STREAM_VOICE_CALL
        7,  // STREAM_SYSTEM
        7,  // STREAM_RING
        15, // STREAM_MUSIC
        7,  // STREAM_ALARM
        7,  // STREAM_NOTIFICATION
        15, // STREAM_BLUETOOTH_SCO
        7,  // STREAM_SYSTEM_ENFORCED
        15, // STREAM_DTMF
        15  // STREAM_TTS
    };
    /* mStreamVolumeAlias[] indicates for each stream if it uses the volume settings
     * of another stream: This avoids multiplying the volume settings for hidden
     * stream types that follow other stream behavior for volume settings
     * NOTE: do not create loops in aliases!
     * Some streams alias to different streams according to device category (phone or tablet) or
     * use case (in call vs off call...). See updateStreamVolumeAlias() for more details.
     *  mStreamVolumeAlias contains the default aliases for a voice capable device (phone) and
     *  STREAM_VOLUME_ALIAS_NON_VOICE for a non voice capable device (tablet).*/
    private final int[] STREAM_VOLUME_ALIAS = new int[] {
        AudioSystem.STREAM_VOICE_CALL,      // STREAM_VOICE_CALL
        AudioSystem.STREAM_RING,            // STREAM_SYSTEM
        AudioSystem.STREAM_RING,            // STREAM_RING
        AudioSystem.STREAM_MUSIC,           // STREAM_MUSIC
        AudioSystem.STREAM_ALARM,           // STREAM_ALARM
        AudioSystem.STREAM_RING,            // STREAM_NOTIFICATION
        AudioSystem.STREAM_BLUETOOTH_SCO,   // STREAM_BLUETOOTH_SCO
        AudioSystem.STREAM_RING,            // STREAM_SYSTEM_ENFORCED
        AudioSystem.STREAM_RING,            // STREAM_DTMF
        AudioSystem.STREAM_MUSIC            // STREAM_TTS
    };
    private final int[] STREAM_VOLUME_ALIAS_NON_VOICE = new int[] {
        AudioSystem.STREAM_VOICE_CALL,      // STREAM_VOICE_CALL
        AudioSystem.STREAM_MUSIC,           // STREAM_SYSTEM
        AudioSystem.STREAM_RING,            // STREAM_RING
        AudioSystem.STREAM_MUSIC,           // STREAM_MUSIC
        AudioSystem.STREAM_ALARM,           // STREAM_ALARM
        AudioSystem.STREAM_RING,            // STREAM_NOTIFICATION
        AudioSystem.STREAM_BLUETOOTH_SCO,   // STREAM_BLUETOOTH_SCO
        AudioSystem.STREAM_MUSIC,           // STREAM_SYSTEM_ENFORCED
        AudioSystem.STREAM_MUSIC,           // STREAM_DTMF
        AudioSystem.STREAM_MUSIC            // STREAM_TTS
    };
    private int[] mStreamVolumeAlias;

    /**
     * Map AudioSystem.STREAM_* constants to app ops.  This should be used
     * after mapping through mStreamVolumeAlias.
     */
    private static final int[] STEAM_VOLUME_OPS = new int[] {
        AppOpsManager.OP_AUDIO_VOICE_VOLUME,            // STREAM_VOICE_CALL
        AppOpsManager.OP_AUDIO_MEDIA_VOLUME,            // STREAM_SYSTEM
        AppOpsManager.OP_AUDIO_RING_VOLUME,             // STREAM_RING
        AppOpsManager.OP_AUDIO_MEDIA_VOLUME,            // STREAM_MUSIC
        AppOpsManager.OP_AUDIO_ALARM_VOLUME,            // STREAM_ALARM
        AppOpsManager.OP_AUDIO_NOTIFICATION_VOLUME,     // STREAM_NOTIFICATION
        AppOpsManager.OP_AUDIO_BLUETOOTH_VOLUME,        // STREAM_BLUETOOTH_SCO
        AppOpsManager.OP_AUDIO_MEDIA_VOLUME,            // STREAM_SYSTEM_ENFORCED
        AppOpsManager.OP_AUDIO_MEDIA_VOLUME,            // STREAM_DTMF
        AppOpsManager.OP_AUDIO_MEDIA_VOLUME,            // STREAM_TTS
    };

    private final boolean mUseFixedVolume;

    // stream names used by dumpStreamStates()
    private final String[] STREAM_NAMES = new String[] {
            "STREAM_VOICE_CALL",
            "STREAM_SYSTEM",
            "STREAM_RING",
            "STREAM_MUSIC",
            "STREAM_ALARM",
            "STREAM_NOTIFICATION",
            "STREAM_BLUETOOTH_SCO",
            "STREAM_SYSTEM_ENFORCED",
            "STREAM_DTMF",
            "STREAM_TTS"
    };

    private boolean mLinkNotificationWithVolume;

    private final AudioSystem.ErrorCallback mAudioSystemCallback = new AudioSystem.ErrorCallback() {
        public void onError(int error) {
            switch (error) {
            case AudioSystem.AUDIO_STATUS_SERVER_DIED:
                sendMsg(mAudioHandler, MSG_MEDIA_SERVER_DIED,
                        SENDMSG_NOOP, 0, 0, null, 0);
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
    // protected by mSettingsLock
    private int mRingerMode;
    // last non-normal ringer mode
    private int mLastSilentRingerMode = -1;

    /** @see System#MODE_RINGER_STREAMS_AFFECTED */
    private int mRingerModeAffectedStreams = 0;

    // Streams currently muted by ringer mode
    private int mRingerModeMutedStreams;

    /** @see System#MUTE_STREAMS_AFFECTED */
    private int mMuteAffectedStreams;

    /**
     * NOTE: setVibrateSetting(), getVibrateSetting(), shouldVibrate() are deprecated.
     * mVibrateSetting is just maintained during deprecation period but vibration policy is
     * now only controlled by mHasVibrator and mRingerMode
     */
    private int mVibrateSetting;

    // Is there a vibrator
    private final boolean mHasVibrator;

    // Broadcast receiver for device connections intent broadcasts
    private final BroadcastReceiver mReceiver = new AudioServiceBroadcastReceiver();

    // Devices currently connected
    private final HashMap <Integer, String> mConnectedDevices = new HashMap <Integer, String>();

    // Forced device usage for communications
    private int mForcedUseForComm;

    // True if we have master volume support
    private final boolean mUseMasterVolume;

    private final int[] mMasterVolumeRamp;

    // List of binder death handlers for setMode() client processes.
    // The last process to have called setMode() is at the top of the list.
    private final ArrayList <SetModeDeathHandler> mSetModeDeathHandlers = new ArrayList <SetModeDeathHandler>();

    // List of clients having issued a SCO start request
    private final ArrayList <ScoClient> mScoClients = new ArrayList <ScoClient>();

    // BluetoothHeadset API to control SCO connection
    private BluetoothHeadset mBluetoothHeadset;

    // Bluetooth headset device
    private BluetoothDevice mBluetoothHeadsetDevice;

    // Indicate if SCO audio connection is currently active and if the initiator is
    // audio service (internal) or bluetooth headset (external)
    private int mScoAudioState;
    // SCO audio state is not active
    private static final int SCO_STATE_INACTIVE = 0;
    // SCO audio activation request waiting for headset service to connect
    private static final int SCO_STATE_ACTIVATE_REQ = 1;
    // SCO audio state is active or starting due to a request from AudioManager API
    private static final int SCO_STATE_ACTIVE_INTERNAL = 3;
    // SCO audio deactivation request waiting for headset service to connect
    private static final int SCO_STATE_DEACTIVATE_REQ = 5;

    // SCO audio state is active due to an action in BT handsfree (either voice recognition or
    // in call audio)
    private static final int SCO_STATE_ACTIVE_EXTERNAL = 2;
    // Deactivation request for all SCO connections (initiated by audio mode change)
    // waiting for headset service to connect
    private static final int SCO_STATE_DEACTIVATE_EXT_REQ = 4;

    // Indicates the mode used for SCO audio connection. The mode is virtual call if the request
    // originated from an app targeting an API version before JB MR2 and raw audio after that.
    private int mScoAudioMode;
    // SCO audio mode is virtual voice call (BluetoothHeadset.startScoUsingVirtualVoiceCall())
    private static final int SCO_MODE_VIRTUAL_CALL = 0;
    // SCO audio mode is raw audio (BluetoothHeadset.connectAudio())
    private static final int SCO_MODE_RAW = 1;

    // Current connection state indicated by bluetooth headset
    private int mScoConnectionState;

    // true if boot sequence has been completed
    private boolean mBootCompleted;
    // listener for SoundPool sample load completion indication
    private SoundPoolCallback mSoundPoolCallBack;
    // thread for SoundPool listener
    private SoundPoolListenerThread mSoundPoolListenerThread;
    // message looper for SoundPool listener
    private Looper mSoundPoolLooper = null;
    // volume applied to sound played with playSoundEffect()
    private static int sSoundEffectVolumeDb;
    // getActiveStreamType() will return:
    // - STREAM_NOTIFICATION on tablets during this period after a notification stopped
    // - STREAM_MUSIC on phones during this period after music or talkback/voice search prompt
    // stopped
    private static final int DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS = 5000;
    // previous volume adjustment direction received by checkForRingerModeChange()
    private int mPrevVolDirection = AudioManager.ADJUST_SAME;
    // Keyguard manager proxy
    private KeyguardManager mKeyguardManager;
    // mVolumeControlStream is set by VolumePanel to temporarily force the stream type which volume
    // is controlled by Vol keys.
    private int  mVolumeControlStream = -1;
    private final Object mForceControlStreamLock = new Object();
    // VolumePanel is currently the only client of forceVolumeControlStream() and runs in system
    // server process so in theory it is not necessary to monitor the client death.
    // However it is good to be ready for future evolutions.
    private ForceControlStreamClient mForceControlStreamClient = null;
    // Used to play ringtones outside system_server
    private volatile IRingtonePlayer mRingtonePlayer;

    private int mDeviceOrientation = Configuration.ORIENTATION_UNDEFINED;
    private int mDeviceRotation = Surface.ROTATION_0;

    // Request to override default use of A2DP for media.
    private boolean mBluetoothA2dpEnabled;
    private final Object mBluetoothA2dpEnabledLock = new Object();

    // Monitoring of audio routes.  Protected by mCurAudioRoutes.
    final AudioRoutesInfo mCurAudioRoutes = new AudioRoutesInfo();
    final RemoteCallbackList<IAudioRoutesObserver> mRoutesObservers
            = new RemoteCallbackList<IAudioRoutesObserver>();

    /**
     * A fake stream type to match the notion of remote media playback
     */
    public final static int STREAM_REMOTE_MUSIC = -200;

    // Devices for which the volume is fixed and VolumePanel slider should be disabled
    final int mFixedVolumeDevices = AudioSystem.DEVICE_OUT_AUX_DIGITAL |
            AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET |
            AudioSystem.DEVICE_OUT_ALL_USB |
            AudioSystem.DEVICE_OUT_PROXY; // use fixed volume on proxy device(WiFi display)

    // TODO merge orientation and rotation
    private final boolean mMonitorOrientation;
    private final boolean mMonitorRotation;

    private boolean mDockAudioMediaEnabled = true;

    private boolean mForceAnalogDeskDock;
    private boolean mForceAnalogCarDock;

    private int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;

    // Used when safe volume warning message display is requested by setStreamVolume(). In this
    // case, the new requested volume, stream type and device are stored in mPendingVolumeCommand
    // and used later when/if disableSafeMediaVolume() is called.
    private StreamVolumeCommand mPendingVolumeCommand;

    private PowerManager.WakeLock mAudioEventWakeLock;

    private TelephonyManager mTelephonyManager;

    private final MediaFocusControl mMediaFocusControl;

    // Reference to BluetoothA2dp to query for AbsoluteVolume.
    private BluetoothA2dp mA2dp;
    private final Object mA2dpAvrcpLock = new Object();
    // If absolute volume is supported in AVRCP device
    private boolean mAvrcpAbsVolSupported = false;

    private boolean mVolumeKeysControlRingStream;

    ///////////////////////////////////////////////////////////////////////////
    // Construction
    ///////////////////////////////////////////////////////////////////////////

    /** @hide */
    public AudioService(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mAppOps = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
        mHandler = new Handler();
        mVoiceCapable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mAudioEventWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "handleAudioEvent");
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator == null ? false : vibrator.hasVibrator();

       // Intialized volume
        MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL] = SystemProperties.getInt(
            "ro.config.vc_call_vol_steps",
           MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL]);

        sSoundEffectVolumeDb = context.getResources().getInteger(
                com.android.internal.R.integer.config_soundEffectVolumeDb);

        mVolumePanel = new VolumePanel(context, this);

        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        mForcedUseForComm = AudioSystem.FORCE_NONE;

        createAudioSystemThread();

        mMediaFocusControl = new MediaFocusControl(mAudioHandler.getLooper(),
                mContext, /*VolumeController*/ mVolumePanel, this);

        AudioSystem.setErrorCallback(mAudioSystemCallback);

        boolean cameraSoundForced = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_camera_sound_forced);
        mCameraSoundForced = new Boolean(cameraSoundForced);
        sendMsg(mAudioHandler,
                MSG_SET_FORCE_USE,
                SENDMSG_QUEUE,
                AudioSystem.FOR_SYSTEM,
                cameraSoundForced ?
                        AudioSystem.FORCE_SYSTEM_ENFORCED : AudioSystem.FORCE_NONE,
                null,
                0);

        mSafeMediaVolumeState = new Integer(Settings.Global.getInt(mContentResolver,
                                                        Settings.Global.AUDIO_SAFE_VOLUME_STATE,
                                                        SAFE_MEDIA_VOLUME_NOT_CONFIGURED));
        // The default safe volume index read here will be replaced by the actual value when
        // the mcc is read by onConfigureSafeVolume()
        mSafeMediaVolumeIndex = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_safe_media_volume_index) * 10;

        mUseFixedVolume = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_useFixedVolume);

        // must be called before readPersistedSettings() which needs a valid mStreamVolumeAlias[]
        // array initialized by updateStreamVolumeAlias()
        updateStreamVolumeAlias(false /*updateVolumes*/);
        readPersistedSettings();
        // must update link ring-notifications streams once the user preferences were read
        updateLinkNotificationStreamVolumeAlias();
        mSettingsObserver = new SettingsObserver();
        createStreamStates();

        readAndSetLowRamDevice();

        // Call setRingerModeInt() to apply correct mute
        // state on streams affected by ringer mode.
        mRingerModeMutedStreams = 0;
        setRingerModeInt(getRingerMode(), false);

        // Register for device connection intent broadcasts.
        IntentFilter intentFilter =
                new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        intentFilter.addAction(Intent.ACTION_USB_AUDIO_ACCESSORY_PLUG);
        intentFilter.addAction(Intent.ACTION_USB_AUDIO_DEVICE_PLUG);
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_USER_BACKGROUND);
        intentFilter.addAction(Intent.ACTION_USER_SWITCHED);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        // TODO merge orientation and rotation
        mMonitorOrientation = SystemProperties.getBoolean("ro.audio.monitorOrientation", false);
        if (mMonitorOrientation) {
            Log.v(TAG, "monitoring device orientation");
            // initialize orientation in AudioSystem
            setOrientationForAudioSystem();
        }
        mMonitorRotation = SystemProperties.getBoolean("ro.audio.monitorRotation", false);
        if (mMonitorRotation) {
            mDeviceRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay().getRotation();
            Log.v(TAG, "monitoring device rotation, initial=" + mDeviceRotation);
            // initialize rotation in AudioSystem
            setRotationForAudioSystem();
        }

        context.registerReceiver(mReceiver, intentFilter);

        ThemeUtils.registerThemeChangeReceiver(context, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mUiContext = null;
            }
        });

        mUseMasterVolume = context.getResources().getBoolean(
                com.android.internal.R.bool.config_useMasterVolume);
        restoreMasterVolume();

        mMasterVolumeRamp = context.getResources().getIntArray(
                com.android.internal.R.array.config_masterVolumeRamp);

        mForceAnalogDeskDock = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_forceAnalogDeskDock);

        mForceAnalogCarDock = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_forceAnalogCarDock);

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

    private void checkAllAliasStreamVolumes() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            if (streamType != mStreamVolumeAlias[streamType]) {
                mStreamStates[streamType].
                                    setAllIndexes(mStreamStates[mStreamVolumeAlias[streamType]]);
            }
            // apply stream volume
            if (!mStreamStates[streamType].isMuted()) {
                mStreamStates[streamType].applyAllVolumes();
            }
        }
    }

    private void createStreamStates() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        VolumeStreamState[] streams = mStreamStates = new VolumeStreamState[numStreamTypes];

        for (int i = 0; i < numStreamTypes; i++) {
            streams[i] = new VolumeStreamState(System.VOLUME_SETTINGS[mStreamVolumeAlias[i]], i);
        }

        checkAllAliasStreamVolumes();
    }

    private void dumpStreamStates(PrintWriter pw) {
        pw.println("\nStream volumes (device: index)");
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int i = 0; i < numStreamTypes; i++) {
            pw.println("- "+STREAM_NAMES[i]+":");
            mStreamStates[i].dump(pw);
            pw.println("");
        }
        pw.print("\n- mute affected streams = 0x");
        pw.println(Integer.toHexString(mMuteAffectedStreams));
    }


    private void updateStreamVolumeAlias(boolean updateVolumes) {
        int dtmfStreamAlias;
        if (mVoiceCapable) {
            mStreamVolumeAlias = STREAM_VOLUME_ALIAS;
            dtmfStreamAlias = AudioSystem.STREAM_RING;
        } else {
            mStreamVolumeAlias = STREAM_VOLUME_ALIAS_NON_VOICE;
            dtmfStreamAlias = AudioSystem.STREAM_MUSIC;
        }
        if (isInCommunication()) {
            dtmfStreamAlias = AudioSystem.STREAM_VOICE_CALL;
            mRingerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_DTMF);
        } else {
            mRingerModeAffectedStreams |= (1 << AudioSystem.STREAM_DTMF);
        }
        mStreamVolumeAlias[AudioSystem.STREAM_DTMF] = dtmfStreamAlias;

        updateLinkNotificationStreamVolumeAlias();

        if (updateVolumes) {
            mStreamStates[AudioSystem.STREAM_DTMF].setAllIndexes(mStreamStates[dtmfStreamAlias]);
            // apply stream mute states according to new value of mRingerModeAffectedStreams
            setRingerModeInt(getRingerMode(), false);
            sendMsg(mAudioHandler,
                    MSG_SET_ALL_VOLUMES,
                    SENDMSG_QUEUE,
                    0,
                    0,
                    mStreamStates[AudioSystem.STREAM_DTMF], 0);
        }
    }

    private void updateLinkNotificationStreamVolumeAlias() {
        if (mLinkNotificationWithVolume) {
            mStreamVolumeAlias[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_RING;
        } else {
            mStreamVolumeAlias[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_NOTIFICATION;
        }
    }

    private void readDockAudioSettings(ContentResolver cr)
    {
        mDockAudioMediaEnabled = Settings.Global.getInt(
                                        cr, Settings.Global.DOCK_AUDIO_MEDIA_ENABLED, 0) == 1;

        if (mDockAudioMediaEnabled) {
            mBecomingNoisyIntentDevices |= AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET;
        } else {
            mBecomingNoisyIntentDevices &= ~AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET;
        }

        sendMsg(mAudioHandler,
                MSG_SET_FORCE_USE,
                SENDMSG_QUEUE,
                AudioSystem.FOR_DOCK,
                mDockAudioMediaEnabled ?
                        AudioSystem.FORCE_ANALOG_DOCK : AudioSystem.FORCE_NONE,
                null,
                0);
    }

    private boolean safeVolumeEnabled(ContentResolver cr) {
        boolean safeMediaVolumeEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_safe_media_volume_enabled);
        boolean safeHeadsetVolumeEnabled = Settings.System.getIntForUser(cr,
                Settings.System.SAFE_HEADSET_VOLUME, safeMediaVolumeEnabled ? 1 : 0,
                UserHandle.USER_CURRENT_OR_SELF) != 0;
        return safeHeadsetVolumeEnabled;
    }

    private void readPersistedSettings() {
        final ContentResolver cr = mContentResolver;

        int ringerModeFromSettings =
                Settings.Global.getInt(
                        cr, Settings.Global.MODE_RINGER, AudioManager.RINGER_MODE_NORMAL);
        int ringerMode = ringerModeFromSettings;
        // sanity check in case the settings are restored from a device with incompatible
        // ringer modes
        if (!AudioManager.isValidRingerMode(ringerMode)) {
            ringerMode = AudioManager.RINGER_MODE_NORMAL;
        }
        if ((ringerMode == AudioManager.RINGER_MODE_VIBRATE) && !mHasVibrator) {
            ringerMode = AudioManager.RINGER_MODE_SILENT;
        }
        if (ringerMode != ringerModeFromSettings) {
            Settings.Global.putInt(cr, Settings.Global.MODE_RINGER, ringerMode);
        }
        if (mUseFixedVolume) {
            ringerMode = AudioManager.RINGER_MODE_NORMAL;
        }
        synchronized(mSettingsLock) {
            mRingerMode = ringerMode;

            // System.VIBRATE_ON is not used any more but defaults for mVibrateSetting
            // are still needed while setVibrateSetting() and getVibrateSetting() are being
            // deprecated.
            mVibrateSetting = getValueForVibrateSetting(0,
                                            AudioManager.VIBRATE_TYPE_NOTIFICATION,
                                            mHasVibrator ? AudioManager.VIBRATE_SETTING_ONLY_SILENT
                                                            : AudioManager.VIBRATE_SETTING_OFF);
            mVibrateSetting = getValueForVibrateSetting(mVibrateSetting,
                                            AudioManager.VIBRATE_TYPE_RINGER,
                                            mHasVibrator ? AudioManager.VIBRATE_SETTING_ONLY_SILENT
                                                            : AudioManager.VIBRATE_SETTING_OFF);

            updateRingerModeAffectedStreams();
            readDockAudioSettings(cr);

            mSafeVolumeEnabled = new Boolean(safeVolumeEnabled(cr));

            mVolumeKeysControlRingStream = Settings.System.getIntForUser(cr,
                    Settings.System.VOLUME_KEYS_CONTROL_RING_STREAM, 1, UserHandle.USER_CURRENT) == 1;
        }

        mLinkNotificationWithVolume = Settings.System.getIntForUser(cr,
                Settings.System.VOLUME_LINK_NOTIFICATION, 1, UserHandle.USER_CURRENT) == 1;

        mMuteAffectedStreams = System.getIntForUser(cr,
                System.MUTE_STREAMS_AFFECTED,
                ((1 << AudioSystem.STREAM_MUSIC)|
                 (1 << AudioSystem.STREAM_RING)|
                 (1 << AudioSystem.STREAM_SYSTEM)),
                 UserHandle.USER_CURRENT);

        boolean masterMute = System.getIntForUser(cr, System.VOLUME_MASTER_MUTE,
                                                  0, UserHandle.USER_CURRENT) == 1;
        if (mUseFixedVolume) {
            masterMute = false;
            AudioSystem.setMasterVolume(1.0f);
        }
        AudioSystem.setMasterMute(masterMute);
        broadcastMasterMuteStatus(masterMute);

        // Each stream will read its own persisted settings

        // Broadcast the sticky intent
        broadcastRingerMode(ringerMode);

        // Broadcast vibrate settings
        broadcastVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        broadcastVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION);

        // Restore the default media button receiver from the system settings
        mMediaFocusControl.restoreMediaButtonReceiver();
    }

    private int rescaleIndex(int index, int srcStream, int dstStream) {
        return (index * mStreamStates[dstStream].getMaxIndex() + mStreamStates[srcStream].getMaxIndex() / 2) / mStreamStates[srcStream].getMaxIndex();
    }

    ///////////////////////////////////////////////////////////////////////////
    // IPC methods
    ///////////////////////////////////////////////////////////////////////////
    /** @see AudioManager#isLocalOrRemoteMusicActive() */
    public boolean isLocalOrRemoteMusicActive() {
        if (AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0)) {
            // local / wired / BT playback active
            if (DEBUG_VOL) Log.d(TAG, "isLocalOrRemoteMusicActive(): local");
            return true;
        }
        if (mMediaFocusControl.checkUpdateRemoteStateIfActive(AudioSystem.STREAM_MUSIC)) {
            // remote "cast-like" playback active
            if (DEBUG_VOL) Log.d(TAG, "isLocalOrRemoteMusicActive(): has PLAYBACK_TYPE_REMOTE");
            return true;
        }
        if (AudioSystem.isStreamActiveRemotely(AudioSystem.STREAM_MUSIC, 0)) {
            // remote submix playback active
            if (DEBUG_VOL) Log.d(TAG, "isLocalOrRemoteMusicActive(): remote submix");
            return true;
        }
        if (DEBUG_VOL) Log.d(TAG, "isLocalOrRemoteMusicActive(): no");
        return false;
    }

    /** @see AudioManager#adjustVolume(int, int) */
    public void adjustVolume(int direction, int flags, String callingPackage) {
        adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, flags,
                callingPackage);
    }

    /** @see AudioManager#adjustLocalOrRemoteStreamVolume(int, int) with current assumption
     *  on streamType: fixed to STREAM_MUSIC */
    public void adjustLocalOrRemoteStreamVolume(int streamType, int direction,
            String callingPackage) {
        if (DEBUG_VOL) Log.d(TAG, "adjustLocalOrRemoteStreamVolume(dir="+direction+")");
        if (AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0)) {
            adjustStreamVolume(AudioSystem.STREAM_MUSIC, direction, 0, callingPackage);
        } else if (mMediaFocusControl.checkUpdateRemoteStateIfActive(AudioSystem.STREAM_MUSIC)) {
            mMediaFocusControl.adjustRemoteVolume(AudioSystem.STREAM_MUSIC, direction, 0);
        }
    }

    /** @see AudioManager#adjustVolume(int, int) */
    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags,
            String callingPackage) {
        if (DEBUG_VOL) Log.d(TAG, "adjustSuggestedStreamVolume() stream="+suggestedStreamType);
        int streamType;
        if (mVolumeControlStream != -1) {
            streamType = mVolumeControlStream;
        } else {
            streamType = getActiveStreamType(suggestedStreamType);
        }

        // Play sounds on STREAM_RING only and if lock screen is not on.
        if ((streamType != STREAM_REMOTE_MUSIC) &&
                (flags & AudioManager.FLAG_PLAY_SOUND) != 0 &&
                ((mStreamVolumeAlias[streamType] != AudioSystem.STREAM_RING)
                 || (mKeyguardManager != null && mKeyguardManager.isKeyguardLocked()))) {
            flags &= ~AudioManager.FLAG_PLAY_SOUND;
        }

        if (streamType == STREAM_REMOTE_MUSIC) {
            // don't play sounds for remote
            flags &= ~(AudioManager.FLAG_PLAY_SOUND|AudioManager.FLAG_FIXED_VOLUME);
            //if (DEBUG_VOL) Log.i(TAG, "Need to adjust remote volume: calling adjustRemoteVolume()");
            mMediaFocusControl.adjustRemoteVolume(AudioSystem.STREAM_MUSIC, direction, flags);
        } else {
            adjustStreamVolume(streamType, direction, flags, callingPackage);
        }
    }

    /** @see AudioManager#adjustStreamVolume(int, int, int) */
    public void adjustStreamVolume(int streamType, int direction, int flags,
            String callingPackage) {
        if (mUseFixedVolume) {
            return;
        }
        if (DEBUG_VOL) Log.d(TAG, "adjustStreamVolume() stream="+streamType+", dir="+direction);

        ensureValidDirection(direction);
        ensureValidStreamType(streamType);

        // use stream type alias here so that streams with same alias have the same behavior,
        // including with regard to silent mode control (e.g the use of STREAM_RING below and in
        // checkForRingerModeChange() in place of STREAM_RING or STREAM_NOTIFICATION)
        int streamTypeAlias = mStreamVolumeAlias[streamType];
        VolumeStreamState streamState = mStreamStates[streamTypeAlias];

        final int device = getDeviceForStream(streamTypeAlias);

        int aliasIndex = streamState.getIndex(device);
        boolean adjustVolume = true;
        int step;

        // skip a2dp absolute volume control request when the device
        // is not an a2dp device
        if ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) == 0 &&
            (flags & AudioManager.FLAG_BLUETOOTH_ABS_VOLUME) != 0) {
            return;
        }

        if (mAppOps.noteOp(STEAM_VOLUME_OPS[streamTypeAlias], Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        // reset any pending volume command
        synchronized (mSafeMediaVolumeState) {
            mPendingVolumeCommand = null;
        }

        flags &= ~AudioManager.FLAG_FIXED_VOLUME;
        if ((streamTypeAlias == AudioSystem.STREAM_MUSIC) &&
               ((device & mFixedVolumeDevices) != 0)) {
            flags |= AudioManager.FLAG_FIXED_VOLUME;

            // Always toggle between max safe volume and 0 for fixed volume devices where safe
            // volume is enforced, and max and 0 for the others.
            // This is simulated by stepping by the full allowed volume range
            if (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE &&
                    (device & mSafeMediaVolumeDevices) != 0) {
                step = mSafeMediaVolumeIndex;
            } else {
                step = streamState.getMaxIndex();
            }
            if (aliasIndex != 0) {
                aliasIndex = step;
            }
        } else {
            // convert one UI step (+/-1) into a number of internal units on the stream alias
            step = rescaleIndex(10, streamType, streamTypeAlias);
        }

        // If either the client forces allowing ringer modes for this adjustment,
        // or the stream type is one that is affected by ringer modes
        if (((flags & AudioManager.FLAG_ALLOW_RINGER_MODES) != 0) ||
                (streamTypeAlias == getMasterStreamType())) {
            int ringerMode = getRingerMode();
            // do not vibrate if already in vibrate mode
            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                flags &= ~AudioManager.FLAG_VIBRATE;
            }
            // Check if the ringer mode changes with this volume adjustment. If
            // it does, it will handle adjusting the volume, so we won't below
            adjustVolume = checkForRingerModeChange(aliasIndex, direction, step);
        }

        int oldIndex = mStreamStates[streamType].getIndex(device);

        if (adjustVolume && (direction != AudioManager.ADJUST_SAME)) {

            // Check if volume update should be send to AVRCP
            if (streamTypeAlias == AudioSystem.STREAM_MUSIC &&
                (device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 &&
                (flags & AudioManager.FLAG_BLUETOOTH_ABS_VOLUME) == 0) {
                synchronized (mA2dpAvrcpLock) {
                    if (mA2dp != null && mAvrcpAbsVolSupported) {
                        mA2dp.adjustAvrcpAbsoluteVolume(direction);
                    }
                }
            }

            if ((direction == AudioManager.ADJUST_RAISE) &&
                    !checkSafeMediaVolume(streamTypeAlias, aliasIndex + step, device)) {
                Log.e(TAG, "adjustStreamVolume() safe volume index = "+oldIndex);
                mVolumePanel.postDisplaySafeVolumeWarning(flags);
            } else if (streamState.adjustIndex(direction * step, device)) {
                // Post message to set system volume (it in turn will post a message
                // to persist). Do not change volume if stream is muted.
                sendMsg(mAudioHandler,
                        MSG_SET_DEVICE_VOLUME,
                        SENDMSG_QUEUE,
                        device,
                        0,
                        streamState,
                        0);
            }
        }
        int index = mStreamStates[streamType].getIndex(device);
        sendVolumeUpdate(streamType, oldIndex, index, flags);
    }

    /** @see AudioManager#adjustMasterVolume(int, int) */
    public void adjustMasterVolume(int steps, int flags, String callingPackage) {
        if (mUseFixedVolume) {
            return;
        }
        ensureValidSteps(steps);
        int volume = Math.round(AudioSystem.getMasterVolume() * MAX_MASTER_VOLUME);
        int delta = 0;
        int numSteps = Math.abs(steps);
        int direction = steps > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        for (int i = 0; i < numSteps; ++i) {
            delta = findVolumeDelta(direction, volume);
            volume += delta;
        }

        //Log.d(TAG, "adjustMasterVolume volume: " + volume + " steps: " + steps);
        setMasterVolume(volume, flags, callingPackage);
    }

    // StreamVolumeCommand contains the information needed to defer the process of
    // setStreamVolume() in case the user has to acknowledge the safe volume warning message.
    class StreamVolumeCommand {
        public final int mStreamType;
        public final int mIndex;
        public final int mFlags;
        public final int mDevice;

        StreamVolumeCommand(int streamType, int index, int flags, int device) {
            mStreamType = streamType;
            mIndex = index;
            mFlags = flags;
            mDevice = device;
        }
    };

    private void onSetStreamVolume(int streamType, int index, int flags, int device) {
        setStreamVolumeInt(mStreamVolumeAlias[streamType], index, device, false);
        // setting volume on master stream type also controls silent mode
        if (((flags & AudioManager.FLAG_ALLOW_RINGER_MODES) != 0) ||
                (mStreamVolumeAlias[streamType] == getMasterStreamType())) {
            int newRingerMode;
            if (index == 0) {
                synchronized (mSettingsLock) {
                    if (mLastSilentRingerMode != -1) {
                        newRingerMode = mLastSilentRingerMode;
                    } else {
                        newRingerMode = mHasVibrator ? AudioManager.RINGER_MODE_VIBRATE
                            : AudioManager.RINGER_MODE_SILENT;
                    }
                }
            } else {
                newRingerMode = AudioManager.RINGER_MODE_NORMAL;
            }
            setRingerMode(newRingerMode);
        }
    }

    /** @see AudioManager#setStreamVolume(int, int, int) */
    public void setStreamVolume(int streamType, int index, int flags, String callingPackage) {
        if (mUseFixedVolume) {
            return;
        }

        ensureValidStreamType(streamType);
        int streamTypeAlias = mStreamVolumeAlias[streamType];
        VolumeStreamState streamState = mStreamStates[streamTypeAlias];

        final int device = getDeviceForStream(streamType);
        int oldIndex;

        // skip a2dp absolute volume control request when the device
        // is not an a2dp device
        if ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) == 0 &&
            (flags & AudioManager.FLAG_BLUETOOTH_ABS_VOLUME) != 0) {
            return;
        }

        if (mAppOps.noteOp(STEAM_VOLUME_OPS[streamTypeAlias], Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        synchronized (mSafeMediaVolumeState) {
            // reset any pending volume command
            mPendingVolumeCommand = null;

            oldIndex = streamState.getIndex(device);

            index = rescaleIndex(index * 10, streamType, streamTypeAlias);

            if (streamTypeAlias == AudioSystem.STREAM_MUSIC &&
                (device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 &&
                (flags & AudioManager.FLAG_BLUETOOTH_ABS_VOLUME) == 0) {
                synchronized (mA2dpAvrcpLock) {
                    if (mA2dp != null && mAvrcpAbsVolSupported) {
                        mA2dp.setAvrcpAbsoluteVolume(index / 10);
                    }
                }
            }

            flags &= ~AudioManager.FLAG_FIXED_VOLUME;
            if ((streamTypeAlias == AudioSystem.STREAM_MUSIC) &&
                    ((device & mFixedVolumeDevices) != 0)) {
                flags |= AudioManager.FLAG_FIXED_VOLUME;

                // volume is either 0 or max allowed for fixed volume devices
                if (index != 0) {
                    if (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE &&
                            (device & mSafeMediaVolumeDevices) != 0) {
                        index = mSafeMediaVolumeIndex;
                    } else {
                        index = streamState.getMaxIndex();
                    }
                }
            }

            if (!checkSafeMediaVolume(streamTypeAlias, index, device)) {
                mVolumePanel.postDisplaySafeVolumeWarning(flags | AudioManager.FLAG_SHOW_UI);
                mPendingVolumeCommand = new StreamVolumeCommand(
                                                    streamType, index, flags, device);
            } else {
                onSetStreamVolume(streamType, index, flags, device);
                index = mStreamStates[streamType].getIndex(device);
            }
        }
        sendVolumeUpdate(streamType, oldIndex, index, flags);
    }

    /** @see AudioManager#forceVolumeControlStream(int) */
    public void forceVolumeControlStream(int streamType, IBinder cb) {
        synchronized(mForceControlStreamLock) {
            mVolumeControlStream = streamType;
            if (mVolumeControlStream == -1) {
                if (mForceControlStreamClient != null) {
                    mForceControlStreamClient.release();
                    mForceControlStreamClient = null;
                }
            } else {
                mForceControlStreamClient = new ForceControlStreamClient(cb);
            }
        }
    }

    private class ForceControlStreamClient implements IBinder.DeathRecipient {
        private IBinder mCb; // To be notified of client's death

        ForceControlStreamClient(IBinder cb) {
            if (cb != null) {
                try {
                    cb.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    // Client has died!
                    Log.w(TAG, "ForceControlStreamClient() could not link to "+cb+" binder death");
                    cb = null;
                }
            }
            mCb = cb;
        }

        public void binderDied() {
            synchronized(mForceControlStreamLock) {
                Log.w(TAG, "SCO client died");
                if (mForceControlStreamClient != this) {
                    Log.w(TAG, "unregistered control stream client died");
                } else {
                    mForceControlStreamClient = null;
                    mVolumeControlStream = -1;
                }
            }
        }

        public void release() {
            if (mCb != null) {
                mCb.unlinkToDeath(this, 0);
                mCb = null;
            }
        }
    }

    private int findVolumeDelta(int direction, int volume) {
        int delta = 0;
        if (direction == AudioManager.ADJUST_RAISE) {
            if (volume == MAX_MASTER_VOLUME) {
                return 0;
            }
            // This is the default value if we make it to the end
            delta = mMasterVolumeRamp[1];
            // If we're raising the volume move down the ramp array until we
            // find the volume we're above and use that groups delta.
            for (int i = mMasterVolumeRamp.length - 1; i > 1; i -= 2) {
                if (volume >= mMasterVolumeRamp[i - 1]) {
                    delta = mMasterVolumeRamp[i];
                    break;
                }
            }
        } else if (direction == AudioManager.ADJUST_LOWER){
            if (volume == 0) {
                return 0;
            }
            int length = mMasterVolumeRamp.length;
            // This is the default value if we make it to the end
            delta = -mMasterVolumeRamp[length - 1];
            // If we're lowering the volume move up the ramp array until we
            // find the volume we're below and use the group below it's delta
            for (int i = 2; i < length; i += 2) {
                if (volume <= mMasterVolumeRamp[i]) {
                    delta = -mMasterVolumeRamp[i - 1];
                    break;
                }
            }
        }
        return delta;
    }

    protected void sendBroadcastToAll(Intent intent) {
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void sendStickyBroadcastToAll(Intent intent) {
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    // UI update and Broadcast Intent
    private void sendVolumeUpdate(int streamType, int oldIndex, int index, int flags) {
        if (!mVoiceCapable && (streamType == AudioSystem.STREAM_RING)) {
            streamType = AudioSystem.STREAM_NOTIFICATION;
        }

        showVolumeChangeUi(streamType, flags);

        if ((flags & AudioManager.FLAG_FIXED_VOLUME) == 0) {
            oldIndex = (oldIndex + 5) / 10;
            index = (index + 5) / 10;
            Intent intent = new Intent(AudioManager.VOLUME_CHANGED_ACTION);
            intent.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, streamType);
            intent.putExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, index);
            intent.putExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, oldIndex);
            sendBroadcastToAll(intent);
        }
    }

    // UI update and Broadcast Intent
    private void sendMasterVolumeUpdate(int flags, int oldVolume, int newVolume) {
        masterVolumeChanged(flags);

        Intent intent = new Intent(AudioManager.MASTER_VOLUME_CHANGED_ACTION);
        intent.putExtra(AudioManager.EXTRA_PREV_MASTER_VOLUME_VALUE, oldVolume);
        intent.putExtra(AudioManager.EXTRA_MASTER_VOLUME_VALUE, newVolume);
        sendBroadcastToAll(intent);
    }

    // UI update and Broadcast Intent
    private void sendMasterMuteUpdate(boolean muted, int flags) {
        masterMuteChanged(flags);
        broadcastMasterMuteStatus(muted);
    }

    private void broadcastMasterMuteStatus(boolean muted) {
        Intent intent = new Intent(AudioManager.MASTER_MUTE_CHANGED_ACTION);
        intent.putExtra(AudioManager.EXTRA_MASTER_VOLUME_MUTED, muted);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        sendStickyBroadcastToAll(intent);
    }

    /**
     * Sets the stream state's index, and posts a message to set system volume.
     * This will not call out to the UI. Assumes a valid stream type.
     *
     * @param streamType Type of the stream
     * @param index Desired volume index of the stream
     * @param device the device whose volume must be changed
     * @param force If true, set the volume even if the desired volume is same
     * as the current volume.
     */
    private void setStreamVolumeInt(int streamType,
                                    int index,
                                    int device,
                                    boolean force) {
        VolumeStreamState streamState = mStreamStates[streamType];

        if (streamState.setIndex(index, device) || force) {
            // Post message to set system volume (it in turn will post a message
            // to persist).
            sendMsg(mAudioHandler,
                    MSG_SET_DEVICE_VOLUME,
                    SENDMSG_QUEUE,
                    device,
                    0,
                    streamState,
                    0);
        }
    }

    /** @see AudioManager#setStreamSolo(int, boolean) */
    public void setStreamSolo(int streamType, boolean state, IBinder cb) {
        if (mUseFixedVolume) {
            return;
        }

        for (int stream = 0; stream < mStreamStates.length; stream++) {
            if (!isStreamAffectedByMute(stream) || stream == streamType) continue;
            mStreamStates[stream].mute(cb, state);
         }
    }

    /** @see AudioManager#setStreamMute(int, boolean) */
    public void setStreamMute(int streamType, boolean state, IBinder cb) {
        if (mUseFixedVolume) {
            return;
        }

        if (isStreamAffectedByMute(streamType)) {
            mStreamStates[streamType].mute(cb, state);
        }
    }

    /** get stream mute state. */
    public boolean isStreamMute(int streamType) {
        return mStreamStates[streamType].isMuted();
    }

    /** @see AudioManager#setMasterMute(boolean, int) */
    public void setMasterMute(boolean state, int flags, IBinder cb) {
        if (mUseFixedVolume) {
            return;
        }

        if (state != AudioSystem.getMasterMute()) {
            AudioSystem.setMasterMute(state);
            // Post a persist master volume msg
            sendMsg(mAudioHandler, MSG_PERSIST_MASTER_VOLUME_MUTE, SENDMSG_REPLACE, state ? 1
                    : 0, 0, null, PERSIST_DELAY);
            sendMasterMuteUpdate(state, flags);
        }
    }

    /** get master mute state. */
    public boolean isMasterMute() {
        return AudioSystem.getMasterMute();
    }

    protected static int getMaxStreamVolume(int streamType) {
        return MAX_STREAM_VOLUME[streamType];
    }

    /** @see AudioManager#getStreamVolume(int) */
    public int getStreamVolume(int streamType) {
        ensureValidStreamType(streamType);
        int device = getDeviceForStream(streamType);
        int index = mStreamStates[streamType].getIndex(device);

        // by convention getStreamVolume() returns 0 when a stream is muted.
        if (mStreamStates[streamType].isMuted()) {
            index = 0;
        }
        if (index != 0 && (mStreamVolumeAlias[streamType] == AudioSystem.STREAM_MUSIC) &&
                (device & mFixedVolumeDevices) != 0) {
            index = mStreamStates[streamType].getMaxIndex();
        }
        return (index + 5) / 10;
    }

    public int getMasterVolume() {
        if (isMasterMute()) return 0;
        return getLastAudibleMasterVolume();
    }

    public void setMasterVolume(int volume, int flags, String callingPackage) {
        if (mUseFixedVolume) {
            return;
        }

        if (mAppOps.noteOp(AppOpsManager.OP_AUDIO_MASTER_VOLUME, Binder.getCallingUid(),
                callingPackage) != AppOpsManager.MODE_ALLOWED) {
            return;
        }

        if (volume < 0) {
            volume = 0;
        } else if (volume > MAX_MASTER_VOLUME) {
            volume = MAX_MASTER_VOLUME;
        }
        doSetMasterVolume((float)volume / MAX_MASTER_VOLUME, flags);
    }

    private void doSetMasterVolume(float volume, int flags) {
        // don't allow changing master volume when muted
        if (!AudioSystem.getMasterMute()) {
            int oldVolume = getMasterVolume();
            AudioSystem.setMasterVolume(volume);

            int newVolume = getMasterVolume();
            if (newVolume != oldVolume) {
                // Post a persist master volume msg
                sendMsg(mAudioHandler, MSG_PERSIST_MASTER_VOLUME, SENDMSG_REPLACE,
                        Math.round(volume * (float)1000.0), 0, null, PERSIST_DELAY);
            }
            // Send the volume update regardless whether there was a change.
            sendMasterVolumeUpdate(flags, oldVolume, newVolume);
        }
    }

    /** @see AudioManager#getStreamMaxVolume(int) */
    public int getStreamMaxVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (mStreamStates[streamType].getMaxIndex() + 5) / 10;
    }

    public int getMasterMaxVolume() {
        return MAX_MASTER_VOLUME;
    }

    /** Get last audible volume before stream was muted. */
    public int getLastAudibleStreamVolume(int streamType) {
        ensureValidStreamType(streamType);
        int device = getDeviceForStream(streamType);
        return (mStreamStates[streamType].getIndex(device) + 5) / 10;
    }

    /** Get last audible master volume before it was muted. */
    public int getLastAudibleMasterVolume() {
        return Math.round(AudioSystem.getMasterVolume() * MAX_MASTER_VOLUME);
    }

    /** @see AudioManager#getMasterStreamType()  */
    public int getMasterStreamType() {
        if (mVoiceCapable) {
            return AudioSystem.STREAM_RING;
        } else {
            return AudioSystem.STREAM_MUSIC;
        }
    }

    /** @see AudioManager#getRingerMode() */
    public int getRingerMode() {
        synchronized(mSettingsLock) {
            return mRingerMode;
        }
    }

    private void ensureValidRingerMode(int ringerMode) {
        if (!AudioManager.isValidRingerMode(ringerMode)) {
            throw new IllegalArgumentException("Bad ringer mode " + ringerMode);
        }
    }

    /** @see AudioManager#setRingerMode(int) */
    public void setRingerMode(int ringerMode) {
        if (mUseFixedVolume) {
            return;
        }

        if ((ringerMode == AudioManager.RINGER_MODE_VIBRATE) && !mHasVibrator) {
            ringerMode = AudioManager.RINGER_MODE_SILENT;
        }

        if ((ringerMode == AudioManager.RINGER_MODE_SILENT) ||
            (ringerMode == AudioManager.RINGER_MODE_VIBRATE))
            SystemProperties.set("persist.sys.silent", "1");
        else
            SystemProperties.set("persist.sys.silent", "0");

        if (ringerMode != getRingerMode()) {
            setRingerModeInt(ringerMode, true);
            // Send sticky broadcast
            broadcastRingerMode(ringerMode);
        }
    }

    private void setRingerModeInt(int ringerMode, boolean persist) {
        synchronized(mSettingsLock) {
            mRingerMode = ringerMode;
            if (ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                mLastSilentRingerMode = ringerMode;
            }
        }

        // Mute stream if not previously muted by ringer mode and ringer mode
        // is not RINGER_MODE_NORMAL and stream is affected by ringer mode.
        // Unmute stream if previously muted by ringer mode and ringer mode
        // is RINGER_MODE_NORMAL or stream is not affected by ringer mode.
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
            if (isStreamMutedByRingerMode(streamType)) {
                if (!isStreamAffectedByRingerMode(streamType) ||
                    ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    // ring and notifications volume should never be 0 when not silenced
                    // on voice capable devices
                    if (mVoiceCapable &&
                            mStreamVolumeAlias[streamType] == AudioSystem.STREAM_RING) {
                        synchronized (mStreamStates[streamType]) {
                            Set set = mStreamStates[streamType].mIndex.entrySet();
                            Iterator i = set.iterator();
                            while (i.hasNext()) {
                                Map.Entry entry = (Map.Entry)i.next();
                                if ((Integer)entry.getValue() == 0) {
                                    entry.setValue(10);
                                }
                            }
                        }
                    }
                    mStreamStates[streamType].mute(null, false);
                    mRingerModeMutedStreams &= ~(1 << streamType);
                }
            } else {
                if (isStreamAffectedByRingerMode(streamType) &&
                    ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                   mStreamStates[streamType].mute(null, true);
                   mRingerModeMutedStreams |= (1 << streamType);
               }
            }
        }

        // Post a persist ringer mode msg
        if (persist) {
            sendMsg(mAudioHandler, MSG_PERSIST_RINGER_MODE,
                    SENDMSG_REPLACE, 0, 0, null, PERSIST_DELAY);
        }
    }

    private void restoreMasterVolume() {
        if (mUseFixedVolume) {
            AudioSystem.setMasterVolume(1.0f);
            return;
        }
        if (mUseMasterVolume) {
            float volume = Settings.System.getFloatForUser(mContentResolver,
                    Settings.System.VOLUME_MASTER, -1.0f, UserHandle.USER_CURRENT);
            if (volume >= 0.0f) {
                AudioSystem.setMasterVolume(volume);
            }
        }
    }

    /** @see AudioManager#shouldVibrate(int) */
    public boolean shouldVibrate(int vibrateType) {
        if (!mHasVibrator) return false;

        switch (getVibrateSetting(vibrateType)) {

            case AudioManager.VIBRATE_SETTING_ON:
                return getRingerMode() != AudioManager.RINGER_MODE_SILENT;

            case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
                return getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;

            case AudioManager.VIBRATE_SETTING_OFF:
                // return false, even for incoming calls
                return false;

            default:
                return false;
        }
    }

    /** @see AudioManager#getVibrateSetting(int) */
    public int getVibrateSetting(int vibrateType) {
        if (!mHasVibrator) return AudioManager.VIBRATE_SETTING_OFF;
        return (mVibrateSetting >> (vibrateType * 2)) & 3;
    }

    /** @see AudioManager#setVibrateSetting(int, int) */
    public void setVibrateSetting(int vibrateType, int vibrateSetting) {

        if (!mHasVibrator) return;

        mVibrateSetting = getValueForVibrateSetting(mVibrateSetting, vibrateType, vibrateSetting);

        // Broadcast change
        broadcastVibrateSetting(vibrateType);

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
        private int mPid;
        private int mMode = AudioSystem.MODE_NORMAL; // Current mode set by this client

        SetModeDeathHandler(IBinder cb, int pid) {
            mCb = cb;
            mPid = pid;
        }

        public void binderDied() {
            int newModeOwnerPid = 0;
            synchronized(mSetModeDeathHandlers) {
                Log.w(TAG, "setMode() client died");
                int index = mSetModeDeathHandlers.indexOf(this);
                if (index < 0) {
                    Log.w(TAG, "unregistered setMode() client died");
                } else {
                    newModeOwnerPid = setModeInt(AudioSystem.MODE_NORMAL, mCb, mPid);
                }
            }
            // when entering RINGTONE, IN_CALL or IN_COMMUNICATION mode, clear all
            // SCO connections not started by the application changing the mode
            if (newModeOwnerPid != 0) {
                final long ident = Binder.clearCallingIdentity();
                disconnectBluetoothSco(newModeOwnerPid);
                Binder.restoreCallingIdentity(ident);
            }
        }

        public int getPid() {
            return mPid;
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

        int newModeOwnerPid = 0;
        synchronized(mSetModeDeathHandlers) {
            if (mode == AudioSystem.MODE_CURRENT) {
                mode = mMode;
            }

            if ((mode == AudioSystem.MODE_IN_CALL) &&
                (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
                 AudioSystem.setParameters("in_call=true");
            }
            newModeOwnerPid = setModeInt(mode, cb, Binder.getCallingPid());
        }
        // when entering RINGTONE, IN_CALL or IN_COMMUNICATION mode, clear all
        // SCO connections not started by the application changing the mode
        if (newModeOwnerPid != 0) {
             disconnectBluetoothSco(newModeOwnerPid);
        }
    }

    // must be called synchronized on mSetModeDeathHandlers
    // setModeInt() returns a valid PID if the audio mode was successfully set to
    // any mode other than NORMAL.
    int setModeInt(int mode, IBinder cb, int pid) {
        int newModeOwnerPid = 0;
        if (cb == null) {
            Log.e(TAG, "setModeInt() called with null binder");
            return newModeOwnerPid;
        }

        SetModeDeathHandler hdlr = null;
        Iterator iter = mSetModeDeathHandlers.iterator();
        while (iter.hasNext()) {
            SetModeDeathHandler h = (SetModeDeathHandler)iter.next();
            if (h.getPid() == pid) {
                hdlr = h;
                // Remove from client list so that it is re-inserted at top of list
                iter.remove();
                hdlr.getBinder().unlinkToDeath(hdlr, 0);
                break;
            }
        }
        int status = AudioSystem.AUDIO_STATUS_OK;
        do {
            if (mode == AudioSystem.MODE_NORMAL) {
                // get new mode from client at top the list if any
                if (!mSetModeDeathHandlers.isEmpty()) {
                    hdlr = mSetModeDeathHandlers.get(0);
                    cb = hdlr.getBinder();
                    mode = hdlr.getMode();
                }
            } else {
                if (hdlr == null) {
                    hdlr = new SetModeDeathHandler(cb, pid);
                }
                // Register for client death notification
                try {
                    cb.linkToDeath(hdlr, 0);
                } catch (RemoteException e) {
                    // Client has died!
                    Log.w(TAG, "setMode() could not link to "+cb+" binder death");
                }

                // Last client to call setMode() is always at top of client list
                // as required by SetModeDeathHandler.binderDied()
                mSetModeDeathHandlers.add(0, hdlr);
                hdlr.setMode(mode);
            }

            if (mode != mMode) {
                status = AudioSystem.setPhoneState(mode);
                if (status == AudioSystem.AUDIO_STATUS_OK) {
                    mMode = mode;
                } else {
                    if (hdlr != null) {
                        mSetModeDeathHandlers.remove(hdlr);
                        cb.unlinkToDeath(hdlr, 0);
                    }
                    // force reading new top of mSetModeDeathHandlers stack
                    mode = AudioSystem.MODE_NORMAL;
                }
            } else {
                status = AudioSystem.AUDIO_STATUS_OK;
            }
        } while (status != AudioSystem.AUDIO_STATUS_OK && !mSetModeDeathHandlers.isEmpty());

        if (status == AudioSystem.AUDIO_STATUS_OK) {
            if (mode != AudioSystem.MODE_NORMAL) {
                if (mSetModeDeathHandlers.isEmpty()) {
                    Log.e(TAG, "setMode() different from MODE_NORMAL with empty mode client stack");
                } else {
                    newModeOwnerPid = mSetModeDeathHandlers.get(0).getPid();
                }
            }
            int streamType = getActiveStreamType(AudioManager.USE_DEFAULT_STREAM_TYPE);
            if (streamType == STREAM_REMOTE_MUSIC) {
                // here handle remote media playback the same way as local playback
                streamType = AudioManager.STREAM_MUSIC;
            }
            int device = getDeviceForStream(streamType);
            int index = mStreamStates[mStreamVolumeAlias[streamType]].getIndex(device);
            setStreamVolumeInt(mStreamVolumeAlias[streamType], index, device, true);

            updateStreamVolumeAlias(true /*updateVolumes*/);
        }
        return newModeOwnerPid;
    }

    /** @see AudioManager#getMode() */
    public int getMode() {
        return mMode;
    }

    //==========================================================================================
    // Sound Effects
    //==========================================================================================

    private static final String TAG_AUDIO_ASSETS = "audio_assets";
    private static final String ATTR_VERSION = "version";
    private static final String TAG_GROUP = "group";
    private static final String ATTR_GROUP_NAME = "name";
    private static final String TAG_ASSET = "asset";
    private static final String ATTR_ASSET_ID = "id";
    private static final String ATTR_ASSET_FILE = "file";

    private static final String ASSET_FILE_VERSION = "1.0";
    private static final String GROUP_TOUCH_SOUNDS = "touch_sounds";

    private static final int SOUND_EFECTS_LOAD_TIMEOUT_MS = 5000;

    class LoadSoundEffectReply {
        public int mStatus = 1;
    };

    private void loadTouchSoundAssetDefaults() {
        SOUND_EFFECT_FILES.add("Effect_Tick.ogg");
        for (int i = 0; i < AudioManager.NUM_SOUND_EFFECTS; i++) {
            SOUND_EFFECT_FILES_MAP[i][0] = 0;
            SOUND_EFFECT_FILES_MAP[i][1] = -1;
        }
    }

    private void loadTouchSoundAssets() {
        XmlResourceParser parser = null;

        // only load assets once.
        if (!SOUND_EFFECT_FILES.isEmpty()) {
            return;
        }

        loadTouchSoundAssetDefaults();

        try {
            parser = mContext.getResources().getXml(com.android.internal.R.xml.audio_assets);

            XmlUtils.beginDocument(parser, TAG_AUDIO_ASSETS);
            String version = parser.getAttributeValue(null, ATTR_VERSION);
            boolean inTouchSoundsGroup = false;

            if (ASSET_FILE_VERSION.equals(version)) {
                while (true) {
                    XmlUtils.nextElement(parser);
                    String element = parser.getName();
                    if (element == null) {
                        break;
                    }
                    if (element.equals(TAG_GROUP)) {
                        String name = parser.getAttributeValue(null, ATTR_GROUP_NAME);
                        if (GROUP_TOUCH_SOUNDS.equals(name)) {
                            inTouchSoundsGroup = true;
                            break;
                        }
                    }
                }
                while (inTouchSoundsGroup) {
                    XmlUtils.nextElement(parser);
                    String element = parser.getName();
                    if (element == null) {
                        break;
                    }
                    if (element.equals(TAG_ASSET)) {
                        String id = parser.getAttributeValue(null, ATTR_ASSET_ID);
                        String file = parser.getAttributeValue(null, ATTR_ASSET_FILE);
                        int fx;

                        try {
                            Field field = AudioManager.class.getField(id);
                            fx = field.getInt(null);
                        } catch (Exception e) {
                            Log.w(TAG, "Invalid touch sound ID: "+id);
                            continue;
                        }

                        int i = SOUND_EFFECT_FILES.indexOf(file);
                        if (i == -1) {
                            i = SOUND_EFFECT_FILES.size();
                            SOUND_EFFECT_FILES.add(file);
                        }
                        SOUND_EFFECT_FILES_MAP[fx][0] = i;
                    } else {
                        break;
                    }
                }
            }
        } catch (Resources.NotFoundException e) {
            Log.w(TAG, "audio assets file not found", e);
        } catch (XmlPullParserException e) {
            Log.w(TAG, "XML parser exception reading touch sound assets", e);
        } catch (IOException e) {
            Log.w(TAG, "I/O exception reading touch sound assets", e);
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    /** @see AudioManager#playSoundEffect(int) */
    public void playSoundEffect(int effectType) {
        playSoundEffectVolume(effectType, -1.0f);
    }

    /** @see AudioManager#playSoundEffect(int, float) */
    public void playSoundEffectVolume(int effectType, float volume) {
        sendMsg(mAudioHandler, MSG_PLAY_SOUND_EFFECT, SENDMSG_QUEUE,
                effectType, (int) (volume * 1000), null, 0);
    }

    /**
     * Loads samples into the soundpool.
     * This method must be called at first when sound effects are enabled
     */
    public boolean loadSoundEffects() {
        int attempts = 3;
        LoadSoundEffectReply reply = new LoadSoundEffectReply();

        synchronized (reply) {
            sendMsg(mAudioHandler, MSG_LOAD_SOUND_EFFECTS, SENDMSG_QUEUE, 0, 0, reply, 0);
            while ((reply.mStatus == 1) && (attempts-- > 0)) {
                try {
                    reply.wait(SOUND_EFECTS_LOAD_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    Log.w(TAG, "loadSoundEffects Interrupted while waiting sound pool loaded.");
                }
            }
        }
        return (reply.mStatus == 0);
    }

    /**
     *  Unloads samples from the sound pool.
     *  This method can be called to free some memory when
     *  sound effects are disabled.
     */
    public void unloadSoundEffects() {
        sendMsg(mAudioHandler, MSG_UNLOAD_SOUND_EFFECTS, SENDMSG_QUEUE, 0, 0, null, 0);
    }

    class SoundPoolListenerThread extends Thread {
        public SoundPoolListenerThread() {
            super("SoundPoolListenerThread");
        }

        @Override
        public void run() {

            Looper.prepare();
            mSoundPoolLooper = Looper.myLooper();

            synchronized (mSoundEffectsLock) {
                if (mSoundPool != null) {
                    mSoundPoolCallBack = new SoundPoolCallback();
                    mSoundPool.setOnLoadCompleteListener(mSoundPoolCallBack);
                }
                mSoundEffectsLock.notify();
            }
            Looper.loop();
        }
    }

    private final class SoundPoolCallback implements
            android.media.SoundPool.OnLoadCompleteListener {

        int mStatus = 1; // 1 means neither error nor last sample loaded yet
        List<Integer> mSamples = new ArrayList<Integer>();

        public int status() {
            return mStatus;
        }

        public void setSamples(int[] samples) {
            for (int i = 0; i < samples.length; i++) {
                // do not wait ack for samples rejected upfront by SoundPool
                if (samples[i] > 0) {
                    mSamples.add(samples[i]);
                }
            }
        }

        public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
            synchronized (mSoundEffectsLock) {
                int i = mSamples.indexOf(sampleId);
                if (i >= 0) {
                    mSamples.remove(i);
                }
                if ((status != 0) || mSamples. isEmpty()) {
                    mStatus = status;
                    mSoundEffectsLock.notify();
                }
            }
        }
    }

    /** @see AudioManager#reloadAudioSettings() */
    public void reloadAudioSettings() {
        readAudioSettings(false /*userSwitch*/);
    }

    private void readAudioSettings(boolean userSwitch) {
        // restore ringer mode, ringer mode affected streams, mute affected streams and vibrate settings
        readPersistedSettings();

        // restore volume settings
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            VolumeStreamState streamState = mStreamStates[streamType];

            if (userSwitch && mStreamVolumeAlias[streamType] == AudioSystem.STREAM_MUSIC) {
                continue;
            }

            synchronized (streamState) {
                streamState.readSettings();

                // unmute stream that was muted but is not affect by mute anymore
                if (streamState.isMuted() && ((!isStreamAffectedByMute(streamType) &&
                        !isStreamMutedByRingerMode(streamType)) || mUseFixedVolume)) {
                    int size = streamState.mDeathHandlers.size();
                    for (int i = 0; i < size; i++) {
                        streamState.mDeathHandlers.get(i).mMuteCount = 1;
                        streamState.mDeathHandlers.get(i).mute(false);
                    }
                }
            }
        }

        // apply new ringer mode before checking volume for alias streams so that streams
        // muted by ringer mode have the correct volume
        setRingerModeInt(getRingerMode(), false);

        checkAllAliasStreamVolumes();

        synchronized (mSafeMediaVolumeState) {
            if (mSafeVolumeEnabled &&
                    mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE) {
                enforceSafeMediaVolume();
            }
        }
    }

    /** @see AudioManager#setSpeakerphoneOn(boolean) */
    public void setSpeakerphoneOn(boolean on){
        if (!checkAudioSettingsPermission("setSpeakerphoneOn()")) {
            return;
        }

        if (on) {
            if (mForcedUseForComm == AudioSystem.FORCE_BT_SCO) {
                    sendMsg(mAudioHandler, MSG_SET_FORCE_USE, SENDMSG_QUEUE,
                            AudioSystem.FOR_RECORD, AudioSystem.FORCE_NONE, null, 0);
            }
            mForcedUseForComm = AudioSystem.FORCE_SPEAKER;
        } else if (mForcedUseForComm == AudioSystem.FORCE_SPEAKER){
            mForcedUseForComm = AudioSystem.FORCE_NONE;
        }

        sendMsg(mAudioHandler, MSG_SET_FORCE_USE, SENDMSG_QUEUE,
                AudioSystem.FOR_COMMUNICATION, mForcedUseForComm, null, 0);
    }

    /** @see AudioManager#isSpeakerphoneOn() */
    public boolean isSpeakerphoneOn() {
        return (mForcedUseForComm == AudioSystem.FORCE_SPEAKER);
    }

    /** @see AudioManager#setBluetoothScoOn(boolean) */
    public void setBluetoothScoOn(boolean on){
        if (!checkAudioSettingsPermission("setBluetoothScoOn()")) {
            return;
        }

        if (on) {
            mForcedUseForComm = AudioSystem.FORCE_BT_SCO;
        } else if (mForcedUseForComm == AudioSystem.FORCE_BT_SCO) {
            mForcedUseForComm = AudioSystem.FORCE_NONE;
        }

        sendMsg(mAudioHandler, MSG_SET_FORCE_USE, SENDMSG_QUEUE,
                AudioSystem.FOR_COMMUNICATION, mForcedUseForComm, null, 0);
        sendMsg(mAudioHandler, MSG_SET_FORCE_USE, SENDMSG_QUEUE,
                AudioSystem.FOR_RECORD, mForcedUseForComm, null, 0);
    }

    /** @see AudioManager#isBluetoothScoOn() */
    public boolean isBluetoothScoOn() {
        return (mForcedUseForComm == AudioSystem.FORCE_BT_SCO);
    }

    /** @see AudioManager#setBluetoothA2dpOn(boolean) */
    public void setBluetoothA2dpOn(boolean on) {
        synchronized (mBluetoothA2dpEnabledLock) {
            mBluetoothA2dpEnabled = on;
            sendMsg(mAudioHandler, MSG_SET_FORCE_BT_A2DP_USE, SENDMSG_QUEUE,
                    AudioSystem.FOR_MEDIA,
                    mBluetoothA2dpEnabled ? AudioSystem.FORCE_NONE : AudioSystem.FORCE_NO_BT_A2DP,
                    null, 0);
        }
    }

    /** @see AudioManager#isBluetoothA2dpOn() */
    public boolean isBluetoothA2dpOn() {
        synchronized (mBluetoothA2dpEnabledLock) {
            return mBluetoothA2dpEnabled;
        }
    }

    /** @see AudioManager#startBluetoothSco() */
    public void startBluetoothSco(IBinder cb, int targetSdkVersion){
        if (!checkAudioSettingsPermission("startBluetoothSco()") ||
                !mBootCompleted) {
            return;
        }
        ScoClient client = getScoClient(cb, true);
        // The calling identity must be cleared before calling ScoClient.incCount().
        // inCount() calls requestScoState() which in turn can call BluetoothHeadset APIs
        // and this must be done on behalf of system server to make sure permissions are granted.
        // The caller identity must be cleared after getScoClient() because it is needed if a new
        // client is created.
        final long ident = Binder.clearCallingIdentity();
        client.incCount(targetSdkVersion);
        Binder.restoreCallingIdentity(ident);
    }

    /** @see AudioManager#stopBluetoothSco() */
    public void stopBluetoothSco(IBinder cb){
        if (!checkAudioSettingsPermission("stopBluetoothSco()") ||
                !mBootCompleted) {
            return;
        }
        ScoClient client = getScoClient(cb, false);
        // The calling identity must be cleared before calling ScoClient.decCount().
        // decCount() calls requestScoState() which in turn can call BluetoothHeadset APIs
        // and this must be done on behalf of system server to make sure permissions are granted.
        final long ident = Binder.clearCallingIdentity();
        if (client != null) {
            client.decCount();
        }
        Binder.restoreCallingIdentity(ident);
    }


    private class ScoClient implements IBinder.DeathRecipient {
        private IBinder mCb; // To be notified of client's death
        private int mCreatorPid;
        private int mStartcount; // number of SCO connections started by this client

        ScoClient(IBinder cb) {
            mCb = cb;
            mCreatorPid = Binder.getCallingPid();
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

        public void incCount(int targetSdkVersion) {
            synchronized(mScoClients) {
                requestScoState(BluetoothHeadset.STATE_AUDIO_CONNECTED, targetSdkVersion);
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
                    requestScoState(BluetoothHeadset.STATE_AUDIO_DISCONNECTED, 0);
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
                    requestScoState(BluetoothHeadset.STATE_AUDIO_DISCONNECTED, 0);
                }
            }
        }

        public int getCount() {
            return mStartcount;
        }

        public IBinder getBinder() {
            return mCb;
        }

        public int getPid() {
            return mCreatorPid;
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

        private void requestScoState(int state, int targetSdkVersion) {
            checkScoAudioState();
            if (totalCount() == 0) {
                if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
                    // Make sure that the state transitions to CONNECTING even if we cannot initiate
                    // the connection.
                    broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTING);
                    // Accept SCO audio activation only in NORMAL audio mode or if the mode is
                    // currently controlled by the same client process.
                    synchronized(mSetModeDeathHandlers) {
                        if ((mSetModeDeathHandlers.isEmpty() ||
                                mSetModeDeathHandlers.get(0).getPid() == mCreatorPid) &&
                                (mScoAudioState == SCO_STATE_INACTIVE ||
                                 mScoAudioState == SCO_STATE_DEACTIVATE_REQ)) {
                            if (mScoAudioState == SCO_STATE_INACTIVE) {
                                mScoAudioMode =
                                        (targetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) ?
                                                SCO_MODE_VIRTUAL_CALL : SCO_MODE_RAW;
                                /*
                                 * Raw mode causes some issues on HFP head units,
                                 * apparently when doing PBAP, so disregard it on
                                 * those for now and keep using VIRTUAL_CALL
                                 */
                                if (mBluetoothHeadsetDevice != null) {
                                    BluetoothClass btClass = mBluetoothHeadsetDevice.getBluetoothClass();
                                    if (btClass.getDeviceClass() ==
                                        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE  &&
                                        btClass.hasService(BluetoothClass.Service.OBJECT_TRANSFER)) {
                                            mScoAudioMode = SCO_MODE_VIRTUAL_CALL;
                                    }
                                }
                                if (mBluetoothHeadset != null && mBluetoothHeadsetDevice != null) {
                                    boolean status;
                                    status = mBluetoothHeadset.startScoUsingVirtualVoiceCall(
                                                                            mBluetoothHeadsetDevice);
                                    if (status) {
                                        mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                                    } else {
                                        broadcastScoConnectionState(
                                                AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                                    }
                                } else if (getBluetoothHeadset()) {
                                    mScoAudioState = SCO_STATE_ACTIVATE_REQ;
                                }
                            } else {
                                mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                                broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_CONNECTED);
                            }
                        } else {
                            broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                        }
                    }
                } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED &&
                              (mScoAudioState == SCO_STATE_ACTIVE_INTERNAL ||
                               mScoAudioState == SCO_STATE_ACTIVATE_REQ)) {
                    if (mScoAudioState == SCO_STATE_ACTIVE_INTERNAL) {
                        if (mBluetoothHeadset != null && mBluetoothHeadsetDevice != null) {
                            boolean status;
                                status = mBluetoothHeadset.stopScoUsingVirtualVoiceCall(
                                                                        mBluetoothHeadsetDevice);
                            if (!status) {
                                mScoAudioState = SCO_STATE_INACTIVE;
                                broadcastScoConnectionState(
                                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                            }
                        } else if (getBluetoothHeadset()) {
                            mScoAudioState = SCO_STATE_DEACTIVATE_REQ;
                        }
                    } else {
                        mScoAudioState = SCO_STATE_INACTIVE;
                        broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                    }
                }
            }
        }
    }

    private void checkScoAudioState() {
        if (mBluetoothHeadset != null && mBluetoothHeadsetDevice != null &&
                mScoAudioState == SCO_STATE_INACTIVE &&
                mBluetoothHeadset.getAudioState(mBluetoothHeadsetDevice)
                != BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
            mScoAudioState = SCO_STATE_ACTIVE_EXTERNAL;
        }
    }

    private ScoClient getScoClient(IBinder cb, boolean create) {
        synchronized(mScoClients) {
            ScoClient client = null;
            int size = mScoClients.size();
            for (int i = 0; i < size; i++) {
                client = mScoClients.get(i);
                if (client.getBinder() == cb)
                    return client;
            }
            if (create) {
                client = new ScoClient(cb);
                mScoClients.add(client);
            }
            return client;
        }
    }

    public void clearAllScoClients(int exceptPid, boolean stopSco) {
        synchronized(mScoClients) {
            ScoClient savedClient = null;
            int size = mScoClients.size();
            for (int i = 0; i < size; i++) {
                ScoClient cl = mScoClients.get(i);
                if (cl.getPid() != exceptPid) {
                    cl.clearCount(stopSco);
                } else {
                    savedClient = cl;
                }
            }
            mScoClients.clear();
            if (savedClient != null) {
                mScoClients.add(savedClient);
            }
        }
    }

    private boolean getBluetoothHeadset() {
        boolean result = false;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            result = adapter.getProfileProxy(mContext, mBluetoothProfileServiceListener,
                                    BluetoothProfile.HEADSET);
        }
        // If we could not get a bluetooth headset proxy, send a failure message
        // without delay to reset the SCO audio state and clear SCO clients.
        // If we could get a proxy, send a delayed failure message that will reset our state
        // in case we don't receive onServiceConnected().
        sendMsg(mAudioHandler, MSG_BT_HEADSET_CNCT_FAILED,
                SENDMSG_REPLACE, 0, 0, null, result ? BT_HEADSET_CNCT_TIMEOUT_MS : 0);
        return result;
    }

    private void disconnectBluetoothSco(int exceptPid) {
        synchronized(mScoClients) {
            checkScoAudioState();
            if (mScoAudioState == SCO_STATE_ACTIVE_EXTERNAL ||
                    mScoAudioState == SCO_STATE_DEACTIVATE_EXT_REQ) {
                if (mBluetoothHeadsetDevice != null) {
                    if (mBluetoothHeadset != null) {
                        if (!mBluetoothHeadset.stopVoiceRecognition(
                                mBluetoothHeadsetDevice)) {
                            sendMsg(mAudioHandler, MSG_BT_HEADSET_CNCT_FAILED,
                                    SENDMSG_REPLACE, 0, 0, null, 0);
                        }
                    } else if (mScoAudioState == SCO_STATE_ACTIVE_EXTERNAL &&
                            getBluetoothHeadset()) {
                        mScoAudioState = SCO_STATE_DEACTIVATE_EXT_REQ;
                    }
                }
            } else {
                clearAllScoClients(exceptPid, true);
            }
        }
    }

    private void resetBluetoothSco() {
        synchronized(mScoClients) {
            clearAllScoClients(0, false);
            mScoAudioState = SCO_STATE_INACTIVE;
            broadcastScoConnectionState(AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
        }
    }

    private void broadcastScoConnectionState(int state) {
        sendMsg(mAudioHandler, MSG_BROADCAST_BT_CONNECTION_STATE,
                SENDMSG_QUEUE, state, 0, null, 0);
    }

    private void onBroadcastScoConnectionState(int state) {
        if (state != mScoConnectionState) {
            Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
            newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, state);
            newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE,
                    mScoConnectionState);
            sendStickyBroadcastToAll(newIntent);
            mScoConnectionState = state;
        }
    }

    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            BluetoothDevice btDevice;
            List<BluetoothDevice> deviceList;
            switch(profile) {
            case BluetoothProfile.A2DP:
                synchronized (mA2dpAvrcpLock) {
                    mA2dp = (BluetoothA2dp) proxy;
                    deviceList = mA2dp.getConnectedDevices();
                    if (deviceList.size() > 0) {
                        btDevice = deviceList.get(0);
                        synchronized (mConnectedDevices) {
                            int state = mA2dp.getConnectionState(btDevice);
                            int delay = checkSendBecomingNoisyIntent(
                                                    AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                                                    (state == BluetoothA2dp.STATE_CONNECTED) ? 1 : 0);
                            queueMsgUnderWakeLock(mAudioHandler,
                                    MSG_SET_A2DP_CONNECTION_STATE,
                                    state,
                                    0,
                                    btDevice,
                                    delay);
                        }
                    }
                }
                break;

            case BluetoothProfile.HEADSET:
                synchronized (mScoClients) {
                    // Discard timeout message
                    mAudioHandler.removeMessages(MSG_BT_HEADSET_CNCT_FAILED);
                    mBluetoothHeadset = (BluetoothHeadset) proxy;
                    deviceList = mBluetoothHeadset.getConnectedDevices();
                    if (deviceList.size() > 0) {
                        mBluetoothHeadsetDevice = deviceList.get(0);
                    } else {
                        mBluetoothHeadsetDevice = null;
                    }
                    // Refresh SCO audio state
                    checkScoAudioState();
                    // Continue pending action if any
                    if (mScoAudioState == SCO_STATE_ACTIVATE_REQ ||
                            mScoAudioState == SCO_STATE_DEACTIVATE_REQ ||
                            mScoAudioState == SCO_STATE_DEACTIVATE_EXT_REQ) {
                        boolean status = false;
                        if (mBluetoothHeadsetDevice != null) {
                            switch (mScoAudioState) {
                            case SCO_STATE_ACTIVATE_REQ:
                                mScoAudioState = SCO_STATE_ACTIVE_INTERNAL;
                                status = mBluetoothHeadset.startScoUsingVirtualVoiceCall(
                                                                        mBluetoothHeadsetDevice);
                                break;
                            case SCO_STATE_DEACTIVATE_REQ:
                                 status = mBluetoothHeadset.stopScoUsingVirtualVoiceCall(
                                                                        mBluetoothHeadsetDevice);
                                break;
                            case SCO_STATE_DEACTIVATE_EXT_REQ:
                                status = mBluetoothHeadset.stopVoiceRecognition(
                                        mBluetoothHeadsetDevice);
                            }
                        }
                        if (!status) {
                            sendMsg(mAudioHandler, MSG_BT_HEADSET_CNCT_FAILED,
                                    SENDMSG_REPLACE, 0, 0, null, 0);
                        }
                    }
                }
                break;

            default:
                break;
            }
        }
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "onServiceDisconnected: Bluetooth profile: " + profile);
            switch(profile) {
            case BluetoothProfile.A2DP:
                synchronized (mA2dpAvrcpLock) {
                    mA2dp = null;
                    synchronized (mConnectedDevices) {
                        if (mConnectedDevices.containsKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP)) {
                            Log.d(TAG, "A2dp service disconnects, pause music player");
                            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                            BluetoothDevice btDevice = adapter.getRemoteDevice
                                    (mConnectedDevices.get(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP));
                            int delay = checkSendBecomingNoisyIntent(
                                                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, 0);
                            queueMsgUnderWakeLock(mAudioHandler,
                                                MSG_SET_A2DP_CONNECTION_STATE,
                                                BluetoothA2dp.STATE_DISCONNECTED,
                                                0,
                                                btDevice,
                                                delay);
                        }
                    }
                }
                break;

            case BluetoothProfile.HEADSET:
                synchronized (mScoClients) {
                    mBluetoothHeadset = null;
                }
                break;

            default:
                break;
            }
        }
    };

    private void onCheckMusicActive() {
        synchronized (mSafeMediaVolumeState) {
            if (mSafeVolumeEnabled &&
                    mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_INACTIVE) {
                int device = getDeviceForStream(AudioSystem.STREAM_MUSIC);

                if ((device & mSafeMediaVolumeDevices) != 0) {
                    sendMsg(mAudioHandler,
                            MSG_CHECK_MUSIC_ACTIVE,
                            SENDMSG_REPLACE,
                            0,
                            0,
                            null,
                            MUSIC_ACTIVE_POLL_PERIOD_MS);
                    int index = mStreamStates[AudioSystem.STREAM_MUSIC].getIndex(device);
                    if (AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0) &&
                            (index > mSafeMediaVolumeIndex)) {
                        // Approximate cumulative active music time
                        mMusicActiveMs += MUSIC_ACTIVE_POLL_PERIOD_MS;
                        if (mMusicActiveMs > UNSAFE_VOLUME_MUSIC_ACTIVE_MS_MAX) {
                            setSafeMediaVolumeEnabled(true);
                            mMusicActiveMs = 0;
                            displaySafeVolumeWarning();
                        }
                    }
                }
            }
        }
    }

    private void onConfigureSafeVolume(boolean force) {
        synchronized (mSafeMediaVolumeState) {
            int mcc = mContext.getResources().getConfiguration().mcc;
            if ((mMcc != mcc) || ((mMcc == 0) && force)) {
                mSafeMediaVolumeIndex = mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_safe_media_volume_index) * 10;
                boolean safeMediaVolumeEnabled = safeVolumeEnabled(mContentResolver);

                // The persisted state is either "disabled" or "active": this is the state applied
                // next time we boot and cannot be "inactive"
                int persistedState;
                if (safeMediaVolumeEnabled) {
                    persistedState = SAFE_MEDIA_VOLUME_ACTIVE;
                    // The state can already be "inactive" here if the user has forced it before
                    // the 30 seconds timeout for forced configuration. In this case we don't reset
                    // it to "active".
                    if (mSafeMediaVolumeState != SAFE_MEDIA_VOLUME_INACTIVE) {
                        mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_ACTIVE;
                        enforceSafeMediaVolume();
                    }
                } else {
                    persistedState = SAFE_MEDIA_VOLUME_DISABLED;
                    mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_DISABLED;
                }
                mMcc = mcc;
                sendMsg(mAudioHandler,
                        MSG_PERSIST_SAFE_VOLUME_STATE,
                        SENDMSG_QUEUE,
                        persistedState,
                        0,
                        null,
                        0);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Checks if the adjustment should change ringer mode instead of just
     * adjusting volume. If so, this will set the proper ringer mode and volume
     * indices on the stream states.
     */
    private boolean checkForRingerModeChange(int oldIndex, int direction,  int step) {
        boolean adjustVolumeIndex = true;
        int ringerMode = getRingerMode();

        switch (ringerMode) {
        case RINGER_MODE_NORMAL:
            if (direction == AudioManager.ADJUST_LOWER) {
                if (mHasVibrator) {
                    // "step" is the delta in internal index units corresponding to a
                    // change of 1 in UI index units.
                    // Because of rounding when rescaling from one stream index range to its alias
                    // index range, we cannot simply test oldIndex == step:
                    //   (step <= oldIndex < 2 * step) is equivalent to: (old UI index == 1)
                    if (step <= oldIndex && oldIndex < 2 * step) {
                        ringerMode = RINGER_MODE_VIBRATE;
                        if (mVoiceCapable)
                            adjustVolumeIndex = false;
                    }
                } else {
                    // (oldIndex < step) is equivalent to (old UI index == 0)
                    if ((oldIndex < step) && mPrevVolDirection != AudioManager.ADJUST_LOWER) {
                        ringerMode = RINGER_MODE_SILENT;
                        if (mVoiceCapable)
                            adjustVolumeIndex = false;
                    }
                }
            }
            break;
        case RINGER_MODE_VIBRATE:
            if (!mHasVibrator) {
                Log.e(TAG, "checkForRingerModeChange() current ringer mode is vibrate" +
                        "but no vibrator is present");
                break;
            }
            if ((direction == AudioManager.ADJUST_LOWER)) {
                if (mPrevVolDirection != AudioManager.ADJUST_LOWER) {
                    ringerMode = RINGER_MODE_SILENT;
                }
            } else if (direction == AudioManager.ADJUST_RAISE) {
                ringerMode = RINGER_MODE_NORMAL;
            }
            adjustVolumeIndex = false;
            break;
        case RINGER_MODE_SILENT:
            if (direction == AudioManager.ADJUST_RAISE) {
                if (mHasVibrator) {
                    ringerMode = RINGER_MODE_VIBRATE;
                } else {
                    ringerMode = RINGER_MODE_NORMAL;
                }
            }
            adjustVolumeIndex = false;
            break;
        default:
            Log.e(TAG, "checkForRingerModeChange() wrong ringer mode: "+ringerMode);
            break;
        }

        setRingerMode(ringerMode);

        mPrevVolDirection = direction;

        return adjustVolumeIndex;
    }

    public boolean isStreamAffectedByRingerMode(int streamType) {
        return (mRingerModeAffectedStreams & (1 << streamType)) != 0;
    }

    private boolean isStreamMutedByRingerMode(int streamType) {
        return (mRingerModeMutedStreams & (1 << streamType)) != 0;
    }

    boolean updateRingerModeAffectedStreams() {
        int ringerModeAffectedStreams;
        // make sure settings for ringer mode are consistent with device type: non voice capable
        // devices (tablets) include media stream in silent mode whereas phones don't.
        ringerModeAffectedStreams = Settings.System.getIntForUser(mContentResolver,
                Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                ((1 << AudioSystem.STREAM_RING)|(1 << AudioSystem.STREAM_NOTIFICATION)|
                 (1 << AudioSystem.STREAM_SYSTEM)|(1 << AudioSystem.STREAM_SYSTEM_ENFORCED)),
                 UserHandle.USER_CURRENT);

        // ringtone, system and dtmf streams are always affected by ringer mode
        ringerModeAffectedStreams |= (1 << AudioSystem.STREAM_RING)|
                                        (1 << AudioSystem.STREAM_SYSTEM)|
                                        (1 << AudioSystem.STREAM_DTMF);

        if (mVoiceCapable) {
            ringerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_MUSIC);
        } else {
            ringerModeAffectedStreams |= (1 << AudioSystem.STREAM_MUSIC);
        }
        synchronized (mCameraSoundForced) {
            if (mCameraSoundForced) {
                ringerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
            } else {
                ringerModeAffectedStreams |= (1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
            }
        }
        if (mStreamVolumeAlias[AudioSystem.STREAM_DTMF] == AudioSystem.STREAM_RING) {
            ringerModeAffectedStreams |= (1 << AudioSystem.STREAM_DTMF);
        } else {
            ringerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_DTMF);
        }

        if (ringerModeAffectedStreams != mRingerModeAffectedStreams) {
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                    ringerModeAffectedStreams,
                    UserHandle.USER_CURRENT);
            mRingerModeAffectedStreams = ringerModeAffectedStreams;
            return true;
        }
        return false;
    }

    public boolean isStreamAffectedByMute(int streamType) {
        return (mMuteAffectedStreams & (1 << streamType)) != 0;
    }

    private void ensureValidDirection(int direction) {
        if (direction < AudioManager.ADJUST_LOWER || direction > AudioManager.ADJUST_RAISE) {
            throw new IllegalArgumentException("Bad direction " + direction);
        }
    }

    private void ensureValidSteps(int steps) {
        if (Math.abs(steps) > MAX_BATCH_VOLUME_ADJUST_STEPS) {
            throw new IllegalArgumentException("Bad volume adjust steps " + steps);
        }
    }

    private void ensureValidStreamType(int streamType) {
        if (streamType < 0 || streamType >= mStreamStates.length) {
            throw new IllegalArgumentException("Bad stream type " + streamType);
        }
    }

    private boolean isInCommunication() {
        boolean isOffhook = false;

        if (mVoiceCapable) {
            try {
                ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
                if (phone != null) isOffhook = phone.isOffhook();
            } catch (RemoteException e) {
                Log.w(TAG, "Couldn't connect to phone service", e);
            }
        }
        return (isOffhook || getMode() == AudioManager.MODE_IN_COMMUNICATION);
    }

    /**
     * For code clarity for getActiveStreamType(int)
     * @param delay_ms max time since last STREAM_MUSIC activity to consider
     * @return true if STREAM_MUSIC is active in streams handled by AudioFlinger now or
     *     in the last "delay_ms" ms.
     */
    private boolean isAfMusicActiveRecently(int delay_ms) {
        return AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, delay_ms)
                || AudioSystem.isStreamActiveRemotely(AudioSystem.STREAM_MUSIC, delay_ms);
    }

    private int getActiveStreamType(int suggestedStreamType) {
        if (mVoiceCapable) {
            if (isInCommunication()) {
                if (AudioSystem.getForceUse(AudioSystem.FOR_COMMUNICATION)
                        == AudioSystem.FORCE_BT_SCO) {
                    // Log.v(TAG, "getActiveStreamType: Forcing STREAM_BLUETOOTH_SCO...");
                    return AudioSystem.STREAM_BLUETOOTH_SCO;
                } else {
                    // Log.v(TAG, "getActiveStreamType: Forcing STREAM_VOICE_CALL...");
                    return AudioSystem.STREAM_VOICE_CALL;
                }
            } else if (suggestedStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                if (isAfMusicActiveRecently(DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS)) {
                    if (DEBUG_VOL)
                        Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC stream active");
                    return AudioSystem.STREAM_MUSIC;
                } else
                    if (mMediaFocusControl.checkUpdateRemoteStateIfActive(AudioSystem.STREAM_MUSIC))
                    {
                        if (DEBUG_VOL)
                            Log.v(TAG, "getActiveStreamType: Forcing STREAM_REMOTE_MUSIC");
                        return STREAM_REMOTE_MUSIC;
                    } else {
                        if (mVolumeKeysControlRingStream) {
                            if (DEBUG_VOL)
                                Log.v(TAG, "getActiveStreamType: Forcing STREAM_RING b/c default");
                            return AudioSystem.STREAM_RING;
                        } else {
                            if (DEBUG_VOL)
                                Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC b/c default setting");
                            return AudioSystem.STREAM_MUSIC;
                        }
                }
            } else if (isAfMusicActiveRecently(0)) {
                if (DEBUG_VOL)
                    Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC stream active");
                return AudioSystem.STREAM_MUSIC;
            } else {
                if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: Returning suggested type "
                        + suggestedStreamType);
                return suggestedStreamType;
            }
        } else {
            if (isInCommunication()) {
                if (AudioSystem.getForceUse(AudioSystem.FOR_COMMUNICATION)
                        == AudioSystem.FORCE_BT_SCO) {
                    if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: Forcing STREAM_BLUETOOTH_SCO");
                    return AudioSystem.STREAM_BLUETOOTH_SCO;
                } else {
                    if (DEBUG_VOL)  Log.v(TAG, "getActiveStreamType: Forcing STREAM_VOICE_CALL");
                    return AudioSystem.STREAM_VOICE_CALL;
                }
            } else if (AudioSystem.isStreamActive(AudioSystem.STREAM_NOTIFICATION,
                    DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS) ||
                    AudioSystem.isStreamActive(AudioSystem.STREAM_RING,
                            DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS)) {
                if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: Forcing STREAM_NOTIFICATION");
                return AudioSystem.STREAM_NOTIFICATION;
            } else if (suggestedStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                if (isAfMusicActiveRecently(DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS)) {
                    if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: forcing STREAM_MUSIC");
                    return AudioSystem.STREAM_MUSIC;
                } else
                    if (mMediaFocusControl.checkUpdateRemoteStateIfActive(AudioSystem.STREAM_MUSIC))
                    {
                        if (DEBUG_VOL)
                            Log.v(TAG, "getActiveStreamType: Forcing STREAM_REMOTE_MUSIC");
                        return STREAM_REMOTE_MUSIC;
                } else {
                    if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: using STREAM_MUSIC as default");
                    return AudioSystem.STREAM_MUSIC;
                }
            } else {
                if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: Returning suggested type "
                        + suggestedStreamType);
                return suggestedStreamType;
            }
        }
    }

    private void broadcastRingerMode(int ringerMode) {
        // Send sticky broadcast
        Intent broadcast = new Intent(AudioManager.RINGER_MODE_CHANGED_ACTION);
        broadcast.putExtra(AudioManager.EXTRA_RINGER_MODE, ringerMode);
        broadcast.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);
        sendStickyBroadcastToAll(broadcast);
    }

    private void broadcastVibrateSetting(int vibrateType) {
        // Send broadcast
        if (ActivityManagerNative.isSystemReady()) {
            Intent broadcast = new Intent(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
            broadcast.putExtra(AudioManager.EXTRA_VIBRATE_TYPE, vibrateType);
            broadcast.putExtra(AudioManager.EXTRA_VIBRATE_SETTING, getVibrateSetting(vibrateType));
            sendBroadcastToAll(broadcast);
        }
    }

    // Message helper methods
    /**
     * Queue a message on the given handler's message queue, after acquiring the service wake lock.
     * Note that the wake lock needs to be released after the message has been handled.
     */
    private void queueMsgUnderWakeLock(Handler handler, int msg,
            int arg1, int arg2, Object obj, int delay) {
        final long ident = Binder.clearCallingIdentity();
        // Always acquire the wake lock as AudioService because it is released by the
        // message handler.
        mAudioEventWakeLock.acquire();
        Binder.restoreCallingIdentity(ident);
        sendMsg(handler, msg, SENDMSG_QUEUE, arg1, arg2, obj, delay);
    }

    private static void sendMsg(Handler handler, int msg,
            int existingMsgPolicy, int arg1, int arg2, Object obj, int delay) {

        if (existingMsgPolicy == SENDMSG_REPLACE) {
            handler.removeMessages(msg);
        } else if (existingMsgPolicy == SENDMSG_NOOP && handler.hasMessages(msg)) {
            return;
        }

        handler.sendMessageDelayed(handler.obtainMessage(msg, arg1, arg2, obj), delay);
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

    private int getDeviceForStream(int stream) {
        int device = AudioSystem.getDevicesForStream(stream);
        if ((device & (device - 1)) != 0) {
            // Multiple device selection is either:
            //  - speaker + one other device: give priority to speaker in this case.
            //  - one A2DP device + another device: happens with duplicated output. In this case
            // retain the device on the A2DP output as the other must not correspond to an active
            // selection if not the speaker.
            if ((device & AudioSystem.DEVICE_OUT_SPEAKER) != 0) {
                device = AudioSystem.DEVICE_OUT_SPEAKER;
            } else {
                device &= AudioSystem.DEVICE_OUT_ALL_A2DP;
            }
        }
        return device;
    }

    public void setWiredDeviceConnectionState(int device, int state, String name) {
        synchronized (mConnectedDevices) {
            int delay = checkSendBecomingNoisyIntent(device, state);
            queueMsgUnderWakeLock(mAudioHandler,
                    MSG_SET_WIRED_DEVICE_CONNECTION_STATE,
                    device,
                    state,
                    name,
                    delay);
        }
    }

    public int setBluetoothA2dpDeviceConnectionState(BluetoothDevice device, int state)
    {
        int delay;
        synchronized (mConnectedDevices) {
            delay = checkSendBecomingNoisyIntent(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                                            (state == BluetoothA2dp.STATE_CONNECTED) ? 1 : 0);
            queueMsgUnderWakeLock(mAudioHandler,
                    MSG_SET_A2DP_CONNECTION_STATE,
                    state,
                    0,
                    device,
                    delay);
        }
        return delay;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////

    public class VolumeStreamState {
        private final int mStreamType;

        private String mVolumeIndexSettingName;
        private int mIndexMax;
        private final ConcurrentHashMap<Integer, Integer> mIndex =
                                            new ConcurrentHashMap<Integer, Integer>(8, 0.75f, 4);
        private ArrayList<VolumeDeathHandler> mDeathHandlers; //handles mute/solo clients death

        private VolumeStreamState(String settingName, int streamType) {

            mVolumeIndexSettingName = settingName;

            mStreamType = streamType;
            mIndexMax = MAX_STREAM_VOLUME[streamType];
            AudioSystem.initStreamVolume(streamType, 0, mIndexMax);
            mIndexMax *= 10;

            // mDeathHandlers must be created before calling readSettings()
            mDeathHandlers = new ArrayList<VolumeDeathHandler>();

            readSettings();
        }

        public String getSettingNameForDevice(int device) {
            String name = mVolumeIndexSettingName;
            String suffix = AudioSystem.getDeviceName(device);
            if (suffix.isEmpty()) {
                return name;
            }
            return name + "_" + suffix;
        }

        public synchronized void readSettings() {
            // force maximum volume on all streams if fixed volume property is set
            if (mUseFixedVolume) {
                mIndex.put(AudioSystem.DEVICE_OUT_DEFAULT, mIndexMax);
                return;
            }
            // do not read system stream volume from settings: this stream is always aliased
            // to another stream type and its volume is never persisted. Values in settings can
            // only be stale values
            if ((mStreamType == AudioSystem.STREAM_SYSTEM) ||
                    (mStreamType == AudioSystem.STREAM_SYSTEM_ENFORCED)) {
                int index = 10 * AudioManager.DEFAULT_STREAM_VOLUME[mStreamType];
                synchronized (mCameraSoundForced) {
                    if (mCameraSoundForced) {
                        index = mIndexMax;
                    }
                }
                mIndex.put(AudioSystem.DEVICE_OUT_DEFAULT, index);
                return;
            }

            int remainingDevices = AudioSystem.DEVICE_OUT_ALL;

            for (int i = 0; remainingDevices != 0; i++) {
                int device = (1 << i);
                if ((device & remainingDevices) == 0) {
                    continue;
                }
                remainingDevices &= ~device;

                // retrieve current volume for device
                String name = getSettingNameForDevice(device);
                // if no volume stored for current stream and device, use default volume if default
                // device, continue otherwise
                int defaultIndex = (device == AudioSystem.DEVICE_OUT_DEFAULT) ?
                                        AudioManager.DEFAULT_STREAM_VOLUME[mStreamType] : -1;
                int index = Settings.System.getIntForUser(
                        mContentResolver, name, defaultIndex, UserHandle.USER_CURRENT);
                if (index == -1) {
                    continue;
                }

                // ignore settings for fixed volume devices: volume should always be at max or 0
                if ((mStreamVolumeAlias[mStreamType] == AudioSystem.STREAM_MUSIC) &&
                        ((device & mFixedVolumeDevices) != 0)) {
                    mIndex.put(device, (index != 0) ? mIndexMax : 0);
                } else {
                    mIndex.put(device, getValidIndex(10 * index));
                }
            }
        }

        public void applyDeviceVolume(int device) {
            int index;
            if (isMuted()) {
                index = 0;
            } else if ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 &&
                       mAvrcpAbsVolSupported) {
                index = (mIndexMax + 5)/10;
            } else {
                index = (getIndex(device) + 5)/10;
            }
            AudioSystem.setStreamVolumeIndex(mStreamType, index, device);
        }

        public synchronized void applyAllVolumes() {
            // apply default volume first: by convention this will reset all
            // devices volumes in audio policy manager to the supplied value
            int index;
            if (isMuted()) {
                index = 0;
            } else {
                index = (getIndex(AudioSystem.DEVICE_OUT_DEFAULT) + 5)/10;
            }
            AudioSystem.setStreamVolumeIndex(mStreamType, index, AudioSystem.DEVICE_OUT_DEFAULT);
            // then apply device specific volumes
            Set set = mIndex.entrySet();
            Iterator i = set.iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry)i.next();
                int device = ((Integer)entry.getKey()).intValue();
                if (device != AudioSystem.DEVICE_OUT_DEFAULT) {
                    if (isMuted()) {
                        index = 0;
                    } else if ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 &&
                            mAvrcpAbsVolSupported) {
                        index = (mIndexMax + 5)/10;
                    } else {
                        index = ((Integer)entry.getValue() + 5)/10;
                    }
                    AudioSystem.setStreamVolumeIndex(mStreamType, index, device);
                }
            }
        }

        public boolean adjustIndex(int deltaIndex, int device) {
            return setIndex(getIndex(device) + deltaIndex,
                            device);
        }

        public boolean setIndex(int index, int device) {
            int oldIndex = getIndex(device);
            index = getValidIndex(index);
            synchronized (mCameraSoundForced) {
                if ((mStreamType == AudioSystem.STREAM_SYSTEM_ENFORCED) && mCameraSoundForced) {
                    index = mIndexMax;
                }
            }
            synchronized (this) {
                mIndex.put(device, index);
            }
            if (oldIndex != index) {
                // Apply change to all streams using this one as alias
                // if changing volume of current device, also change volume of current
                // device on aliased stream
                boolean currentDevice = (device == getDeviceForStream(mStreamType));
                int numStreamTypes = AudioSystem.getNumStreamTypes();
                for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                    if (streamType != mStreamType &&
                            mStreamVolumeAlias[streamType] == mStreamType) {
                        int scaledIndex = rescaleIndex(index, mStreamType, streamType);
                        mStreamStates[streamType].setIndex(scaledIndex,
                                                           device);
                        if (currentDevice) {
                            mStreamStates[streamType].setIndex(scaledIndex,
                                                               getDeviceForStream(streamType));
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        public synchronized int getIndex(int device) {
            Integer index = mIndex.get(device);
            if (index == null) {
                // there is always an entry for AudioSystem.DEVICE_OUT_DEFAULT
                index = mIndex.get(AudioSystem.DEVICE_OUT_DEFAULT);
            }
            return index.intValue();
        }

        public int getMaxIndex() {
            return mIndexMax;
        }

        public synchronized void setAllIndexes(VolumeStreamState srcStream) {
            int srcStreamType = srcStream.getStreamType();
            // apply default device volume from source stream to all devices first in case
            // some devices are present in this stream state but not in source stream state
            int index = srcStream.getIndex(AudioSystem.DEVICE_OUT_DEFAULT);
            index = rescaleIndex(index, srcStreamType, mStreamType);
            Set set = mIndex.entrySet();
            Iterator i = set.iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry)i.next();
                entry.setValue(index);
            }
            // Now apply actual volume for devices in source stream state
            set = srcStream.mIndex.entrySet();
            i = set.iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry)i.next();
                int device = ((Integer)entry.getKey()).intValue();
                index = ((Integer)entry.getValue()).intValue();
                index = rescaleIndex(index, srcStreamType, mStreamType);

                setIndex(index, device);
            }
        }

        public synchronized void setAllIndexesToMax() {
            Set set = mIndex.entrySet();
            Iterator i = set.iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry)i.next();
                entry.setValue(mIndexMax);
            }
        }

        public synchronized void mute(IBinder cb, boolean state) {
            VolumeDeathHandler handler = getDeathHandler(cb, state);
            if (handler == null) {
                Log.e(TAG, "Could not get client death handler for stream: "+mStreamType);
                return;
            }
            handler.mute(state);
        }

        public int getStreamType() {
            return mStreamType;
        }

        private int getValidIndex(int index) {
            if (index < 0) {
                return 0;
            } else if (mUseFixedVolume || index > mIndexMax) {
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

            // must be called while synchronized on parent VolumeStreamState
            public void mute(boolean state) {
                boolean updateVolume = false;
                if (state) {
                    if (mMuteCount == 0) {
                        // Register for client death notification
                        try {
                            // mICallback can be 0 if muted by AudioService
                            if (mICallback != null) {
                                mICallback.linkToDeath(this, 0);
                            }
                            VolumeStreamState.this.mDeathHandlers.add(this);
                            // If the stream is not yet muted by any client, set level to 0
                            if (!VolumeStreamState.this.isMuted()) {
                                updateVolume = true;
                            }
                        } catch (RemoteException e) {
                            // Client has died!
                            binderDied();
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
                            // Unregister from client death notification
                            VolumeStreamState.this.mDeathHandlers.remove(this);
                            // mICallback can be 0 if muted by AudioService
                            if (mICallback != null) {
                                mICallback.unlinkToDeath(this, 0);
                            }
                            if (!VolumeStreamState.this.isMuted()) {
                                updateVolume = true;
                            }
                        }
                    }
                }
                if (updateVolume) {
                    sendMsg(mAudioHandler,
                            MSG_SET_ALL_VOLUMES,
                            SENDMSG_QUEUE,
                            0,
                            0,
                            VolumeStreamState.this, 0);
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

        private synchronized int muteCount() {
            int count = 0;
            int size = mDeathHandlers.size();
            for (int i = 0; i < size; i++) {
                count += mDeathHandlers.get(i).mMuteCount;
            }
            return count;
        }

        private synchronized boolean isMuted() {
            return muteCount() != 0;
        }

        // only called by mute() which is already synchronized
        private VolumeDeathHandler getDeathHandler(IBinder cb, boolean state) {
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

        private void dump(PrintWriter pw) {
            pw.print("   Mute count: ");
            pw.println(muteCount());
            pw.print("   Current: ");
            Set set = mIndex.entrySet();
            Iterator i = set.iterator();
            while (i.hasNext()) {
                Map.Entry entry = (Map.Entry)i.next();
                pw.print(Integer.toHexString(((Integer)entry.getKey()).intValue())
                             + ": " + ((((Integer)entry.getValue()).intValue() + 5) / 10)+", ");
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

        private void setDeviceVolume(VolumeStreamState streamState, int device) {

            // Apply volume
            streamState.applyDeviceVolume(device);

            // Apply change to all streams using this one as alias
            int numStreamTypes = AudioSystem.getNumStreamTypes();
            for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                if (streamType != streamState.mStreamType &&
                        mStreamVolumeAlias[streamType] == streamState.mStreamType) {
                    // Make sure volume is also maxed out on A2DP device for aliased stream
                    // that may have a different device selected
                    int streamDevice = getDeviceForStream(streamType);
                    if ((device != streamDevice) && mAvrcpAbsVolSupported &&
                            ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0)) {
                        mStreamStates[streamType].applyDeviceVolume(device);
                    }
                    mStreamStates[streamType].applyDeviceVolume(streamDevice);
                }
            }

            // Post a persist volume msg
            sendMsg(mAudioHandler,
                    MSG_PERSIST_VOLUME,
                    SENDMSG_QUEUE,
                    device,
                    0,
                    streamState,
                    PERSIST_DELAY);

        }

        private void setAllVolumes(VolumeStreamState streamState) {

            // Apply volume
            streamState.applyAllVolumes();

            // Apply change to all streams using this one as alias
            int numStreamTypes = AudioSystem.getNumStreamTypes();
            for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                if (streamType != streamState.mStreamType &&
                        mStreamVolumeAlias[streamType] == streamState.mStreamType) {
                    mStreamStates[streamType].applyAllVolumes();
                }
            }
        }

        private void persistVolume(VolumeStreamState streamState, int device) {
            if (mUseFixedVolume) {
                return;
            }
            System.putIntForUser(mContentResolver,
                      streamState.getSettingNameForDevice(device),
                      (streamState.getIndex(device) + 5)/ 10,
                      UserHandle.USER_CURRENT);
        }

        private void persistRingerMode(int ringerMode) {
            if (mUseFixedVolume) {
                return;
            }
            Settings.Global.putInt(mContentResolver, Settings.Global.MODE_RINGER, ringerMode);
        }

        private boolean onLoadSoundEffects() {
            int status;

            synchronized (mSoundEffectsLock) {
                if (!mBootCompleted) {
                    Log.w(TAG, "onLoadSoundEffects() called before boot complete");
                    return false;
                }

                if (mSoundPool != null) {
                    return true;
                }

                loadTouchSoundAssets();

                mSoundPool = new SoundPool(NUM_SOUNDPOOL_CHANNELS, AudioSystem.STREAM_SYSTEM, 0);
                mSoundPoolCallBack = null;
                mSoundPoolListenerThread = new SoundPoolListenerThread();
                mSoundPoolListenerThread.start();
                int attempts = 3;
                while ((mSoundPoolCallBack == null) && (attempts-- > 0)) {
                    try {
                        // Wait for mSoundPoolCallBack to be set by the other thread
                        mSoundEffectsLock.wait(SOUND_EFECTS_LOAD_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while waiting sound pool listener thread.");
                    }
                }

                if (mSoundPoolCallBack == null) {
                    Log.w(TAG, "onLoadSoundEffects() SoundPool listener or thread creation error");
                    if (mSoundPoolLooper != null) {
                        mSoundPoolLooper.quit();
                        mSoundPoolLooper = null;
                    }
                    mSoundPoolListenerThread = null;
                    mSoundPool.release();
                    mSoundPool = null;
                    return false;
                }
                /*
                 * poolId table: The value -1 in this table indicates that corresponding
                 * file (same index in SOUND_EFFECT_FILES[] has not been loaded.
                 * Once loaded, the value in poolId is the sample ID and the same
                 * sample can be reused for another effect using the same file.
                 */
                int[] poolId = new int[SOUND_EFFECT_FILES.size()];
                for (int fileIdx = 0; fileIdx < SOUND_EFFECT_FILES.size(); fileIdx++) {
                    poolId[fileIdx] = -1;
                }
                /*
                 * Effects whose value in SOUND_EFFECT_FILES_MAP[effect][1] is -1 must be loaded.
                 * If load succeeds, value in SOUND_EFFECT_FILES_MAP[effect][1] is > 0:
                 * this indicates we have a valid sample loaded for this effect.
                 */

                int numSamples = 0;
                for (int effect = 0; effect < AudioManager.NUM_SOUND_EFFECTS; effect++) {
                    // Do not load sample if this effect uses the MediaPlayer
                    if (SOUND_EFFECT_FILES_MAP[effect][1] == 0) {
                        continue;
                    }
                    if (poolId[SOUND_EFFECT_FILES_MAP[effect][0]] == -1) {
                        String filePath = Environment.getRootDirectory()
                                + SOUND_EFFECTS_PATH
                                + SOUND_EFFECT_FILES.get(SOUND_EFFECT_FILES_MAP[effect][0]);
                        int sampleId = mSoundPool.load(filePath, 0);
                        if (sampleId <= 0) {
                            Log.w(TAG, "Soundpool could not load file: "+filePath);
                        } else {
                            SOUND_EFFECT_FILES_MAP[effect][1] = sampleId;
                            poolId[SOUND_EFFECT_FILES_MAP[effect][0]] = sampleId;
                            numSamples++;
                        }
                    } else {
                        SOUND_EFFECT_FILES_MAP[effect][1] =
                                poolId[SOUND_EFFECT_FILES_MAP[effect][0]];
                    }
                }
                // wait for all samples to be loaded
                if (numSamples > 0) {
                    mSoundPoolCallBack.setSamples(poolId);

                    attempts = 3;
                    status = 1;
                    while ((status == 1) && (attempts-- > 0)) {
                        try {
                            mSoundEffectsLock.wait(SOUND_EFECTS_LOAD_TIMEOUT_MS);
                            status = mSoundPoolCallBack.status();
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Interrupted while waiting sound pool callback.");
                        }
                    }
                } else {
                    status = -1;
                }

                if (mSoundPoolLooper != null) {
                    mSoundPoolLooper.quit();
                    mSoundPoolLooper = null;
                }
                mSoundPoolListenerThread = null;
                if (status != 0) {
                    Log.w(TAG,
                            "onLoadSoundEffects(), Error "+status+ " while loading samples");
                    for (int effect = 0; effect < AudioManager.NUM_SOUND_EFFECTS; effect++) {
                        if (SOUND_EFFECT_FILES_MAP[effect][1] > 0) {
                            SOUND_EFFECT_FILES_MAP[effect][1] = -1;
                        }
                    }

                    mSoundPool.release();
                    mSoundPool = null;
                }
            }
            return (status == 0);
        }

        /**
         *  Unloads samples from the sound pool.
         *  This method can be called to free some memory when
         *  sound effects are disabled.
         */
        private void onUnloadSoundEffects() {
            synchronized (mSoundEffectsLock) {
                if (mSoundPool == null) {
                    return;
                }

                int[] poolId = new int[SOUND_EFFECT_FILES.size()];
                for (int fileIdx = 0; fileIdx < SOUND_EFFECT_FILES.size(); fileIdx++) {
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
                mSoundPool.release();
                mSoundPool = null;
            }
        }

        private void onPlaySoundEffect(int effectType, int volume) {
            synchronized (mSoundEffectsLock) {

                onLoadSoundEffects();

                if (mSoundPool == null) {
                    return;
                }
                float volFloat;
                // use default if volume is not specified by caller
                if (volume < 0) {
                    volFloat = (float)Math.pow(10, (float)sSoundEffectVolumeDb/20);
                } else {
                    volFloat = (float) volume / 1000.0f;
                }

                if (SOUND_EFFECT_FILES_MAP[effectType][1] > 0) {
                    mSoundPool.play(SOUND_EFFECT_FILES_MAP[effectType][1],
                                        volFloat, volFloat, 0, 0, 1.0f);
                } else {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    try {
                        String filePath = Environment.getRootDirectory() + SOUND_EFFECTS_PATH +
                                    SOUND_EFFECT_FILES.get(SOUND_EFFECT_FILES_MAP[effectType][0]);
                        mediaPlayer.setDataSource(filePath);
                        mediaPlayer.setAudioStreamType(AudioSystem.STREAM_SYSTEM);
                        mediaPlayer.prepare();
                        mediaPlayer.setVolume(volFloat);
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

        private void setForceUse(int usage, int config) {
            AudioSystem.setForceUse(usage, config);
        }

        private void onPersistSafeVolumeState(int state) {
            Settings.Global.putInt(mContentResolver,
                    Settings.Global.AUDIO_SAFE_VOLUME_STATE,
                    state);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case MSG_SET_DEVICE_VOLUME:
                    setDeviceVolume((VolumeStreamState) msg.obj, msg.arg1);
                    break;

                case MSG_SET_ALL_VOLUMES:
                    setAllVolumes((VolumeStreamState) msg.obj);
                    break;

                case MSG_PERSIST_VOLUME:
                    persistVolume((VolumeStreamState) msg.obj, msg.arg1);
                    break;

                case MSG_PERSIST_MASTER_VOLUME:
                    if (mUseFixedVolume) {
                        return;
                    }
                    Settings.System.putFloatForUser(mContentResolver,
                                                    Settings.System.VOLUME_MASTER,
                                                    (float)msg.arg1 / (float)1000.0,
                                                    UserHandle.USER_CURRENT);
                    break;

                case MSG_PERSIST_MASTER_VOLUME_MUTE:
                    if (mUseFixedVolume) {
                        return;
                    }
                    Settings.System.putIntForUser(mContentResolver,
                                                 Settings.System.VOLUME_MASTER_MUTE,
                                                 msg.arg1,
                                                 UserHandle.USER_CURRENT);
                    break;

                case MSG_PERSIST_RINGER_MODE:
                    // note that the value persisted is the current ringer mode, not the
                    // value of ringer mode as of the time the request was made to persist
                    persistRingerMode(getRingerMode());
                    break;

                case MSG_MEDIA_SERVER_DIED:
                    if (AudioSystem.checkAudioFlinger() != AudioSystem.AUDIO_STATUS_OK) {
                        Log.e(TAG, "Media server died.");
                        sendMsg(mAudioHandler, MSG_MEDIA_SERVER_DIED, SENDMSG_NOOP, 0, 0,
                                null, 500);
                        break;
                    }
                    Log.e(TAG, "Media server started.");

                    // indicate to audio HAL that we start the reconfiguration phase after a media
                    // server crash
                    // Note that we only execute this when the media server
                    // process restarts after a crash, not the first time it is started.
                    AudioSystem.setParameters("restarting=true");

                    readAndSetLowRamDevice();

                    // Restore device connection states
                    synchronized (mConnectedDevices) {
                        Set set = mConnectedDevices.entrySet();
                        Iterator i = set.iterator();
                        while (i.hasNext()) {
                            Map.Entry device = (Map.Entry)i.next();
                            AudioSystem.setDeviceConnectionState(
                                                            ((Integer)device.getKey()).intValue(),
                                                            AudioSystem.DEVICE_STATE_AVAILABLE,
                                                            (String)device.getValue());
                        }
                    }
                    // Restore call state
                    AudioSystem.setPhoneState(mMode);

                    // Restore forced usage for communcations and record
                    AudioSystem.setForceUse(AudioSystem.FOR_COMMUNICATION, mForcedUseForComm);
                    AudioSystem.setForceUse(AudioSystem.FOR_RECORD, mForcedUseForComm);
                    AudioSystem.setForceUse(AudioSystem.FOR_SYSTEM, mCameraSoundForced ?
                                    AudioSystem.FORCE_SYSTEM_ENFORCED : AudioSystem.FORCE_NONE);

                    // Restore stream volumes
                    int numStreamTypes = AudioSystem.getNumStreamTypes();
                    for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                        VolumeStreamState streamState = mStreamStates[streamType];
                        AudioSystem.initStreamVolume(streamType, 0, (streamState.mIndexMax + 5) / 10);

                        streamState.applyAllVolumes();
                    }

                    // Restore ringer mode
                    setRingerModeInt(getRingerMode(), false);

                    // Restore master volume
                    restoreMasterVolume();

                    // Reset device orientation (if monitored for this device)
                    if (mMonitorOrientation) {
                        setOrientationForAudioSystem();
                    }
                    if (mMonitorRotation) {
                        setRotationForAudioSystem();
                    }

                    synchronized (mBluetoothA2dpEnabledLock) {
                        AudioSystem.setForceUse(AudioSystem.FOR_MEDIA,
                                mBluetoothA2dpEnabled ?
                                        AudioSystem.FORCE_NONE : AudioSystem.FORCE_NO_BT_A2DP);
                    }

                    synchronized (mSettingsLock) {
                        AudioSystem.setForceUse(AudioSystem.FOR_DOCK,
                                mDockAudioMediaEnabled ?
                                        AudioSystem.FORCE_ANALOG_DOCK : AudioSystem.FORCE_NONE);
                    }

                    // indicate the end of reconfiguration phase to audio HAL
                    AudioSystem.setParameters("restarting=false");
                    break;

                case MSG_UNLOAD_SOUND_EFFECTS:
                    onUnloadSoundEffects();
                    break;

                case MSG_LOAD_SOUND_EFFECTS:
                    //FIXME: onLoadSoundEffects() should be executed in a separate thread as it
                    // can take several dozens of milliseconds to complete
                    boolean loaded = onLoadSoundEffects();
                    if (msg.obj != null) {
                        LoadSoundEffectReply reply = (LoadSoundEffectReply)msg.obj;
                        synchronized (reply) {
                            reply.mStatus = loaded ? 0 : -1;
                            reply.notify();
                        }
                    }
                    break;

                case MSG_PLAY_SOUND_EFFECT:
                    onPlaySoundEffect(msg.arg1, msg.arg2);
                    break;

                case MSG_BTA2DP_DOCK_TIMEOUT:
                    // msg.obj  == address of BTA2DP device
                    synchronized (mConnectedDevices) {
                        makeA2dpDeviceUnavailableNow( (String) msg.obj );
                    }
                    break;

                case MSG_SET_FORCE_USE:
                case MSG_SET_FORCE_BT_A2DP_USE:
                    setForceUse(msg.arg1, msg.arg2);
                    break;

                case MSG_BT_HEADSET_CNCT_FAILED:
                    resetBluetoothSco();
                    break;

                case MSG_SET_WIRED_DEVICE_CONNECTION_STATE:
                    onSetWiredDeviceConnectionState(msg.arg1, msg.arg2, (String)msg.obj);
                    mAudioEventWakeLock.release();
                    break;

                case MSG_SET_A2DP_CONNECTION_STATE:
                    onSetA2dpConnectionState((BluetoothDevice)msg.obj, msg.arg1);
                    mAudioEventWakeLock.release();
                    break;

                case MSG_REPORT_NEW_ROUTES: {
                    int N = mRoutesObservers.beginBroadcast();
                    if (N > 0) {
                        AudioRoutesInfo routes;
                        synchronized (mCurAudioRoutes) {
                            routes = new AudioRoutesInfo(mCurAudioRoutes);
                        }
                        while (N > 0) {
                            N--;
                            IAudioRoutesObserver obs = mRoutesObservers.getBroadcastItem(N);
                            try {
                                obs.dispatchAudioRoutesChanged(routes);
                            } catch (RemoteException e) {
                            }
                        }
                    }
                    mRoutesObservers.finishBroadcast();
                    break;
                }

                case MSG_CHECK_MUSIC_ACTIVE:
                    onCheckMusicActive();
                    break;

                case MSG_BROADCAST_AUDIO_BECOMING_NOISY:
                    onSendBecomingNoisyIntent();
                    break;

                case MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED:
                case MSG_CONFIGURE_SAFE_MEDIA_VOLUME:
                    onConfigureSafeVolume((msg.what == MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED));
                    break;
                case MSG_PERSIST_SAFE_VOLUME_STATE:
                    onPersistSafeVolumeState(msg.arg1);
                    break;

                case MSG_BROADCAST_BT_CONNECTION_STATE:
                    onBroadcastScoConnectionState(msg.arg1);
                    break;
            }
        }
    }

    private class SettingsObserver extends ContentObserver {

        SettingsObserver() {
            super(new Handler());
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.MODE_RINGER_STREAMS_AFFECTED), false, this);
            mContentResolver.registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.DOCK_AUDIO_MEDIA_ENABLED), false, this);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.VOLUME_LINK_NOTIFICATION), false, this);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.SAFE_HEADSET_VOLUME), false, this);
            mContentResolver.registerContentObserver(Settings.System.getUriFor(
                Settings.System.VOLUME_KEYS_CONTROL_RING_STREAM), false, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (uri == null) {
                return;
            }
            // FIXME This synchronized is not necessary if mSettingsLock only protects mRingerMode.
            //       However there appear to be some missing locks around mRingerModeMutedStreams
            //       and mRingerModeAffectedStreams, so will leave this synchronized for now.
            //       mRingerModeMutedStreams and mMuteAffectedStreams are safe (only accessed once).
            synchronized (mSettingsLock) {
                if (uri.equals(Settings.System.getUriFor(Settings.System.MODE_RINGER_STREAMS_AFFECTED))) {
                    int ringerModeAffectedStreams = Settings.System.getIntForUser(mContentResolver,
                           Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                           ((1 << AudioSystem.STREAM_RING)|(1 << AudioSystem.STREAM_NOTIFICATION)|
                           (1 << AudioSystem.STREAM_SYSTEM)|(1 << AudioSystem.STREAM_SYSTEM_ENFORCED)),
                           UserHandle.USER_CURRENT);
                    if (mVoiceCapable) {
                        ringerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_MUSIC);
                    } else {
                        ringerModeAffectedStreams |= (1 << AudioSystem.STREAM_MUSIC);
                    }
                    synchronized (mCameraSoundForced) {
                        if (mCameraSoundForced) {
                            ringerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
                        } else {
                            ringerModeAffectedStreams |= (1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
                        }
                    }
                    if (ringerModeAffectedStreams != mRingerModeAffectedStreams) {
                        /*
                         * Ensure all stream types that should be affected by ringer mode
                         * are in the proper state.
                         */
                        mRingerModeAffectedStreams = ringerModeAffectedStreams;
                        setRingerModeInt(getRingerMode(), false);
                    }

                } else if (uri.equals(Settings.Global.getUriFor(Settings.Global.DOCK_AUDIO_MEDIA_ENABLED))) {
                    readDockAudioSettings(mContentResolver);

                } else if (uri.equals(Settings.System.getUriFor(Settings.System.VOLUME_LINK_NOTIFICATION))) {
                    mLinkNotificationWithVolume = Settings.System.getInt(mContentResolver,
                            Settings.System.VOLUME_LINK_NOTIFICATION, 1) == 1;
                    if (mLinkNotificationWithVolume) {
                        mStreamVolumeAlias[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_RING;
                    } else {
                        mStreamVolumeAlias[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_NOTIFICATION;
                    }

                } else if (uri.equals(Settings.System.getUriFor(Settings.System.SAFE_HEADSET_VOLUME))) {
                    mSafeVolumeEnabled = safeVolumeEnabled(mContentResolver);
                } else if (uri.equals(Settings.System.getUriFor(Settings.System.VOLUME_KEYS_CONTROL_RING_STREAM))) {
                    mVolumeKeysControlRingStream = Settings.System.getIntForUser(mContentResolver,
                            Settings.System.VOLUME_KEYS_CONTROL_RING_STREAM, 1, UserHandle.USER_CURRENT) == 1;
                }
            }
        }
    }

    // must be called synchronized on mConnectedDevices
    private void makeA2dpDeviceAvailable(String address) {
        // enable A2DP before notifying A2DP connection to avoid unecessary processing in
        // audio policy manager
        VolumeStreamState streamState = mStreamStates[AudioSystem.STREAM_MUSIC];
        sendMsg(mAudioHandler, MSG_SET_DEVICE_VOLUME, SENDMSG_QUEUE,
                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, 0, streamState, 0);
        setBluetoothA2dpOnInt(true);
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_AVAILABLE,
                address);
        // Reset A2DP suspend state each time a new sink is connected
        AudioSystem.setParameters("A2dpSuspended=false");
        mConnectedDevices.put( new Integer(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP),
                address);
        sendA2dpRouteChangedIntent(address, BluetoothProfile.STATE_CONNECTED);
    }

    private void onSendBecomingNoisyIntent() {
        sendBroadcastToAll(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    // must be called synchronized on mConnectedDevices
    private void makeA2dpDeviceUnavailableNow(String address) {
        synchronized (mA2dpAvrcpLock) {
            mAvrcpAbsVolSupported = false;
        }
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_UNAVAILABLE,
                address);
        mConnectedDevices.remove(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP);
        sendA2dpRouteChangedIntent(address, BluetoothProfile.STATE_DISCONNECTED);
    }

    private void sendA2dpRouteChangedIntent(String address, int state) {
        Intent intent = new Intent(AudioManager.A2DP_ROUTE_CHANGED_ACTION);
        BluetoothDevice btDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, btDevice);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        mContext.sendBroadcast(intent);
    }

    // must be called synchronized on mConnectedDevices
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

    // must be called synchronized on mConnectedDevices
    private void cancelA2dpDeviceTimeout() {
        mAudioHandler.removeMessages(MSG_BTA2DP_DOCK_TIMEOUT);
    }

    // must be called synchronized on mConnectedDevices
    private boolean hasScheduledA2dpDockTimeout() {
        return mAudioHandler.hasMessages(MSG_BTA2DP_DOCK_TIMEOUT);
    }

    private void onSetA2dpConnectionState(BluetoothDevice btDevice, int state)
    {
        if (DEBUG_VOL) Log.d(TAG, "onSetA2dpConnectionState btDevice="+btDevice+" state="+state);
        if (btDevice == null) {
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }

        synchronized (mConnectedDevices) {
            boolean isConnected =
                (mConnectedDevices.containsKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP) &&
                 mConnectedDevices.get(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP).equals(address));

            if (isConnected && state != BluetoothProfile.STATE_CONNECTED) {
                if (btDevice.isBluetoothDock()) {
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        // introduction of a delay for transient disconnections of docks when
                        // power is rapidly turned off/on, this message will be canceled if
                        // we reconnect the dock under a preset delay
                        makeA2dpDeviceUnavailableLater(address);
                        // the next time isConnected is evaluated, it will be false for the dock
                    }
                } else {
                    makeA2dpDeviceUnavailableNow(address);
                }
                synchronized (mCurAudioRoutes) {
                    if (mCurAudioRoutes.mBluetoothName != null) {
                        mCurAudioRoutes.mBluetoothName = null;
                        sendMsg(mAudioHandler, MSG_REPORT_NEW_ROUTES,
                                SENDMSG_NOOP, 0, 0, null, 0);
                    }
                }
            } else if (!isConnected && state == BluetoothProfile.STATE_CONNECTED) {
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
                synchronized (mCurAudioRoutes) {
                    String name = btDevice.getAliasName();
                    if (!TextUtils.equals(mCurAudioRoutes.mBluetoothName, name)) {
                        mCurAudioRoutes.mBluetoothName = name;
                        sendMsg(mAudioHandler, MSG_REPORT_NEW_ROUTES,
                                SENDMSG_NOOP, 0, 0, null, 0);
                    }
                }
            }
        }
    }

    public void avrcpSupportsAbsoluteVolume(String address, boolean support) {
        // address is not used for now, but may be used when multiple a2dp devices are supported
        synchronized (mA2dpAvrcpLock) {
            mAvrcpAbsVolSupported = support;
            sendMsg(mAudioHandler, MSG_SET_DEVICE_VOLUME, SENDMSG_QUEUE,
                    AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, 0,
                    mStreamStates[AudioSystem.STREAM_MUSIC], 0);
            sendMsg(mAudioHandler, MSG_SET_DEVICE_VOLUME, SENDMSG_QUEUE,
                    AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, 0,
                    mStreamStates[AudioSystem.STREAM_RING], 0);
        }
    }

    private boolean handleDeviceConnection(boolean connected, int device, String params) {
        synchronized (mConnectedDevices) {
            boolean isConnected = (mConnectedDevices.containsKey(device) &&
                    (params.isEmpty() || mConnectedDevices.get(device).equals(params)));

            if (isConnected && !connected) {
                AudioSystem.setDeviceConnectionState(device,
                                              AudioSystem.DEVICE_STATE_UNAVAILABLE,
                                              mConnectedDevices.get(device));
                 mConnectedDevices.remove(device);
                 return true;
            } else if (!isConnected && connected) {
                 AudioSystem.setDeviceConnectionState(device,
                                                      AudioSystem.DEVICE_STATE_AVAILABLE,
                                                      params);
                 mConnectedDevices.put(new Integer(device), params);
                 return true;
            }
        }
        return false;
    }

    // Devices which removal triggers intent ACTION_AUDIO_BECOMING_NOISY. The intent is only
    // sent if none of these devices is connected.
    int mBecomingNoisyIntentDevices =
            AudioSystem.DEVICE_OUT_WIRED_HEADSET | AudioSystem.DEVICE_OUT_WIRED_HEADPHONE |
            AudioSystem.DEVICE_OUT_ALL_A2DP | AudioSystem.DEVICE_OUT_AUX_DIGITAL |
            AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET | AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET |
            AudioSystem.DEVICE_OUT_ALL_USB;

    // must be called before removing the device from mConnectedDevices
    private int checkSendBecomingNoisyIntent(int device, int state) {
        int delay = 0;
        if ((state == 0) && ((device & mBecomingNoisyIntentDevices) != 0)) {
            int devices = 0;
            for (int dev : mConnectedDevices.keySet()) {
                if ((dev & mBecomingNoisyIntentDevices) != 0) {
                   devices |= dev;
                }
            }
            if (devices == device) {
                sendMsg(mAudioHandler,
                        MSG_BROADCAST_AUDIO_BECOMING_NOISY,
                        SENDMSG_REPLACE,
                        0,
                        0,
                        null,
                        0);
                delay = 1000;
            }
        }

        if (mAudioHandler.hasMessages(MSG_SET_A2DP_CONNECTION_STATE) ||
                mAudioHandler.hasMessages(MSG_SET_WIRED_DEVICE_CONNECTION_STATE)) {
            delay = 1000;
        }
        return delay;
    }

    private void sendDeviceConnectionIntent(int device, int state, String name)
    {
        Intent intent = new Intent();

        intent.putExtra("state", state);
        intent.putExtra("name", name);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);

        int connType = 0;

        if (device == AudioSystem.DEVICE_OUT_WIRED_HEADSET) {
            connType = AudioRoutesInfo.MAIN_HEADSET;
            intent.setAction(Intent.ACTION_HEADSET_PLUG);
            intent.putExtra("microphone", 1);
        } else if (device == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE) {
            connType = AudioRoutesInfo.MAIN_HEADPHONES;
            intent.setAction(Intent.ACTION_HEADSET_PLUG);
            intent.putExtra("microphone", 0);
        } else if (device == AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET) {
            connType = AudioRoutesInfo.MAIN_DOCK_SPEAKERS;
            intent.setAction(Intent.ACTION_ANALOG_AUDIO_DOCK_PLUG);
        } else if (device == AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET) {
            connType = AudioRoutesInfo.MAIN_DOCK_SPEAKERS;
            intent.setAction(Intent.ACTION_DIGITAL_AUDIO_DOCK_PLUG);
        } else if (device == AudioSystem.DEVICE_OUT_AUX_DIGITAL) {
            connType = AudioRoutesInfo.MAIN_HDMI;
            intent.setAction(Intent.ACTION_HDMI_AUDIO_PLUG);
        }

        synchronized (mCurAudioRoutes) {
            if (connType != 0) {
                int newConn = mCurAudioRoutes.mMainType;
                if (state != 0) {
                    newConn |= connType;
                } else {
                    newConn &= ~connType;
                }
                if (newConn != mCurAudioRoutes.mMainType) {
                    mCurAudioRoutes.mMainType = newConn;
                    sendMsg(mAudioHandler, MSG_REPORT_NEW_ROUTES,
                            SENDMSG_NOOP, 0, 0, null, 0);
                }
            }
        }

        final long ident = Binder.clearCallingIdentity();
        try {
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void onSetWiredDeviceConnectionState(int device, int state, String name)
    {
        synchronized (mConnectedDevices) {
            if ((state == 0) && ((device == AudioSystem.DEVICE_OUT_WIRED_HEADSET) ||
                    (device == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE))) {
                setBluetoothA2dpOnInt(true);
            }
            boolean isUsb = ((device & AudioSystem.DEVICE_OUT_ALL_USB) != 0);
            handleDeviceConnection((state == 1), device, (isUsb ? name : ""));
            if (state != 0) {
                if ((device == AudioSystem.DEVICE_OUT_WIRED_HEADSET) ||
                    (device == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE)) {
                    setBluetoothA2dpOnInt(false);
                }
                if ((device & mSafeMediaVolumeDevices) != 0) {
                    sendMsg(mAudioHandler,
                            MSG_CHECK_MUSIC_ACTIVE,
                            SENDMSG_REPLACE,
                            0,
                            0,
                            null,
                            MUSIC_ACTIVE_POLL_PERIOD_MS);
                }
            }
            if (!isUsb) {
                sendDeviceConnectionIntent(device, state, name);
            }
        }
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
            int device;
            int state;

            if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                int config;
                switch (dockState) {
                    case Intent.EXTRA_DOCK_STATE_DESK:
                        config = mForceAnalogDeskDock ? AudioSystem.FORCE_ANALOG_DOCK : AudioSystem.FORCE_BT_DESK_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_CAR:
                        config = mForceAnalogCarDock ? AudioSystem.FORCE_ANALOG_DOCK : AudioSystem.FORCE_BT_CAR_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_LE_DESK:
                        config = AudioSystem.FORCE_ANALOG_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_HE_DESK:
                        config = AudioSystem.FORCE_DIGITAL_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_UNDOCKED:
                    default:
                        config = AudioSystem.FORCE_NONE;
                }
                // Low end docks have a menu to enable or disable audio
                // (see mDockAudioMediaEnabled)
                if (!((dockState == Intent.EXTRA_DOCK_STATE_LE_DESK) ||
                      ((dockState == Intent.EXTRA_DOCK_STATE_UNDOCKED) &&
                       (mDockState == Intent.EXTRA_DOCK_STATE_LE_DESK)))) {
                    AudioSystem.setForceUse(AudioSystem.FOR_DOCK, config);
                }
                mDockState = dockState;
            } else if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                                               BluetoothProfile.STATE_DISCONNECTED);
                device = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO;
                String address = null;

                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (btDevice == null) {
                    return;
                }

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

                if (!BluetoothAdapter.checkBluetoothAddress(address)) {
                    address = "";
                }

                boolean connected = (state == BluetoothProfile.STATE_CONNECTED);
                if (handleDeviceConnection(connected, device, address)) {
                    synchronized (mScoClients) {
                        if (connected) {
                            mBluetoothHeadsetDevice = btDevice;
                        } else {
                            mBluetoothHeadsetDevice = null;
                            resetBluetoothSco();
                        }
                    }
                }
            } else if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                state = intent.getIntExtra("state", 0);
                if (state == 1) {
                    // Headset plugged in
                    adjustCurrentStreamVolume();
                    // TODO: Cap volume at safe levels

                    boolean launchPlayer = Settings.System.getIntForUser(
                            context.getContentResolver(),
                            Settings.System.HEADSET_CONNECT_PLAYER,
                            0, UserHandle.USER_CURRENT) != 0;
                    if (launchPlayer) {
                        Intent playerIntent = new Intent(Intent.ACTION_MAIN);
                        playerIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
                        playerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        try {
                            context.startActivity(playerIntent);
                        } catch (ActivityNotFoundException e) {
                            Log.w(TAG, "No music player found to start after headset connection");
                        }
                    }
                } else {
                    // Headset disconnected
                    adjustCurrentStreamVolume();
                }
            } else if (action.equals(Intent.ACTION_USB_AUDIO_ACCESSORY_PLUG) ||
                           action.equals(Intent.ACTION_USB_AUDIO_DEVICE_PLUG)) {
                state = intent.getIntExtra("state", 0);
                int alsaCard = intent.getIntExtra("card", -1);
                int alsaDevice = intent.getIntExtra("device", -1);
                String params = (alsaCard == -1 && alsaDevice == -1 ? ""
                                    : "card=" + alsaCard + ";device=" + alsaDevice);
                device = action.equals(Intent.ACTION_USB_AUDIO_ACCESSORY_PLUG) ?
                        AudioSystem.DEVICE_OUT_USB_ACCESSORY : AudioSystem.DEVICE_OUT_USB_DEVICE;
                Log.v(TAG, "Broadcast Receiver: Got "
                        + (action.equals(Intent.ACTION_USB_AUDIO_ACCESSORY_PLUG) ?
                              "ACTION_USB_AUDIO_ACCESSORY_PLUG" : "ACTION_USB_AUDIO_DEVICE_PLUG")
                        + ", state = " + state + ", card: " + alsaCard + ", device: " + alsaDevice);
                setWiredDeviceConnectionState(device, state, params);
            } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
                boolean broadcast = false;
                int scoAudioState = AudioManager.SCO_AUDIO_STATE_ERROR;
                synchronized (mScoClients) {
                    int btState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                    // broadcast intent if the connection was initated by AudioService
                    if (!mScoClients.isEmpty() &&
                            (mScoAudioState == SCO_STATE_ACTIVE_INTERNAL ||
                             mScoAudioState == SCO_STATE_ACTIVATE_REQ ||
                             mScoAudioState == SCO_STATE_DEACTIVATE_REQ)) {
                        broadcast = true;
                    }
                    switch (btState) {
                    case BluetoothHeadset.STATE_AUDIO_CONNECTED:
                        scoAudioState = AudioManager.SCO_AUDIO_STATE_CONNECTED;
                        if (mScoAudioState != SCO_STATE_ACTIVE_INTERNAL &&
                            mScoAudioState != SCO_STATE_DEACTIVATE_REQ &&
                            mScoAudioState != SCO_STATE_DEACTIVATE_EXT_REQ) {
                            mScoAudioState = SCO_STATE_ACTIVE_EXTERNAL;
                        }
                        break;
                    case BluetoothHeadset.STATE_AUDIO_DISCONNECTED:
                        scoAudioState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED;
                        mScoAudioState = SCO_STATE_INACTIVE;
                        clearAllScoClients(0, false);
                        break;
                    case BluetoothHeadset.STATE_AUDIO_CONNECTING:
                        if (mScoAudioState != SCO_STATE_ACTIVE_INTERNAL &&
                            mScoAudioState != SCO_STATE_DEACTIVATE_REQ &&
                            mScoAudioState != SCO_STATE_DEACTIVATE_EXT_REQ) {
                            mScoAudioState = SCO_STATE_ACTIVE_EXTERNAL;
                        }
                    default:
                        // do not broadcast CONNECTING or invalid state
                        broadcast = false;
                        break;
                    }
                }
                if (broadcast) {
                    broadcastScoConnectionState(scoAudioState);
                    //FIXME: this is to maintain compatibility with deprecated intent
                    // AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED. Remove when appropriate.
                    Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
                    newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, scoAudioState);
                    sendStickyBroadcastToAll(newIntent);
                }
            } else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                mBootCompleted = true;
                sendMsg(mAudioHandler, MSG_LOAD_SOUND_EFFECTS, SENDMSG_QUEUE,
                        0, 0, null, 0);

                mKeyguardManager =
                        (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                mScoConnectionState = AudioManager.SCO_AUDIO_STATE_ERROR;
                resetBluetoothSco();
                getBluetoothHeadset();
                //FIXME: this is to maintain compatibility with deprecated intent
                // AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED. Remove when appropriate.
                Intent newIntent = new Intent(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED);
                newIntent.putExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED);
                sendStickyBroadcastToAll(newIntent);

                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    adapter.getProfileProxy(mContext, mBluetoothProfileServiceListener,
                                            BluetoothProfile.A2DP);
                }

                sendMsg(mAudioHandler,
                        MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED,
                        SENDMSG_REPLACE,
                        0,
                        0,
                        null,
                        SAFE_VOLUME_CONFIGURE_TIMEOUT_MS);
                adjustCurrentStreamVolume();
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                AudioSystem.setParameters("screen_state=on");
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                AudioSystem.setParameters("screen_state=off");
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                handleConfigurationChanged(context);
            } else if (action.equals(Intent.ACTION_USER_BACKGROUND)) {
                // attempt to stop music playback for background user
                sendMsg(mAudioHandler,
                        MSG_BROADCAST_AUDIO_BECOMING_NOISY,
                        SENDMSG_REPLACE,
                        0,
                        0,
                        null,
                        0);
            } else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                // the current audio focus owner is no longer valid
                mMediaFocusControl.discardAudioFocusOwner();

                // load volume settings for new user
                readAudioSettings(true /*userSwitch*/);
                // preserve STREAM_MUSIC volume from one user to the next.
                sendMsg(mAudioHandler,
                        MSG_SET_ALL_VOLUMES,
                        SENDMSG_QUEUE,
                        0,
                        0,
                        mStreamStates[AudioSystem.STREAM_MUSIC], 0);
            }
        }

        private void adjustCurrentStreamVolume() {
            VolumeStreamState streamState;
            int device;

            for (int stream = 0; stream < AudioSystem.getNumStreamTypes(); stream++) {
                if (stream == mStreamVolumeAlias[stream]) {
                    streamState = mStreamStates[mStreamVolumeAlias[stream]];
                    device = getDeviceForStream(stream);
                    // apply stored value for device
                    streamState.applyDeviceVolume(device);
                }
            }
        }
    }

    private void masterVolumeChanged(final int flags) {
        if (mUiContext != null && mVolumePanel != null) {
            mVolumePanel.postMasterVolumeChanged(flags);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mUiContext == null) {
                        mUiContext = ThemeUtils.createUiContext(mContext);
                    }

                    final Context context = mUiContext != null ? mUiContext : mContext;
                    mVolumePanel = new VolumePanel(context, AudioService.this);
                    mVolumePanel.postMasterVolumeChanged(flags);
                }
            });
        }
    }

    private void masterMuteChanged(final int flags) {
        if (mUiContext != null && mVolumePanel != null) {
            mVolumePanel.postMasterMuteChanged(flags);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mUiContext == null) {
                        mUiContext = ThemeUtils.createUiContext(mContext);
                    }

                    final Context context = mUiContext != null ? mUiContext : mContext;
                    mVolumePanel = new VolumePanel(context, AudioService.this);
                    mVolumePanel.postMasterMuteChanged(flags);
                }
            });
        }
    }

    private void showVolumeChangeUi(final int streamType, final int flags) {
        if (mUiContext != null && mVolumePanel != null) {
            mVolumePanel.postVolumeChanged(streamType, flags);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mUiContext == null) {
                        mUiContext = ThemeUtils.createUiContext(mContext);
                    }

                    final Context context = mUiContext != null ? mUiContext : mContext;
                    mVolumePanel = new VolumePanel(context, AudioService.this);
                    mVolumePanel.postVolumeChanged(streamType, flags);
                }
            });
        }
    }

    private void displaySafeVolumeWarning() {
        if (mUiContext != null && mVolumePanel != null) {
            mVolumePanel.postDisplaySafeVolumeWarning(0);
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mUiContext == null) {
                        mUiContext = ThemeUtils.createUiContext(mContext);
                    }

                    final Context context = mUiContext != null ? mUiContext : mContext;
                    mVolumePanel = new VolumePanel(context, AudioService.this);
                    mVolumePanel.postDisplaySafeVolumeWarning(0);
                }
            });
        }
    }

    //==========================================================================================
    // RemoteControlDisplay / RemoteControlClient / Remote info
    //==========================================================================================
    public boolean registerRemoteController(IRemoteControlDisplay rcd, int w, int h,
            ComponentName listenerComp) {
        return mMediaFocusControl.registerRemoteController(rcd, w, h, listenerComp);
    }

    public boolean registerRemoteControlDisplay(IRemoteControlDisplay rcd, int w, int h) {
        return mMediaFocusControl.registerRemoteControlDisplay(rcd, w, h);
    }

    public void unregisterRemoteControlDisplay(IRemoteControlDisplay rcd) {
        mMediaFocusControl.unregisterRemoteControlDisplay(rcd);
    }

    public void remoteControlDisplayUsesBitmapSize(IRemoteControlDisplay rcd, int w, int h) {
        mMediaFocusControl.remoteControlDisplayUsesBitmapSize(rcd, w, h);
    }

    public void remoteControlDisplayWantsPlaybackPositionSync(IRemoteControlDisplay rcd,
            boolean wantsSync) {
        mMediaFocusControl.remoteControlDisplayWantsPlaybackPositionSync(rcd, wantsSync);
    }

    public void registerMediaButtonEventReceiverForCalls(ComponentName c) {
        mMediaFocusControl.registerMediaButtonEventReceiverForCalls(c);
    }

    public void unregisterMediaButtonEventReceiverForCalls() {
        mMediaFocusControl.unregisterMediaButtonEventReceiverForCalls();
    }

    public void registerMediaButtonIntent(PendingIntent pi, ComponentName c, IBinder token) {
        mMediaFocusControl.registerMediaButtonIntent(pi, c, token);
    }

    public void unregisterMediaButtonIntent(PendingIntent pi) {
        mMediaFocusControl.unregisterMediaButtonIntent(pi);
    }

    public int registerRemoteControlClient(PendingIntent mediaIntent,
            IRemoteControlClient rcClient, String callingPckg) {
        return mMediaFocusControl.registerRemoteControlClient(mediaIntent, rcClient, callingPckg);
    }

    public void unregisterRemoteControlClient(PendingIntent mediaIntent,
            IRemoteControlClient rcClient) {
        mMediaFocusControl.unregisterRemoteControlClient(mediaIntent, rcClient);
    }

    public void setRemoteControlClientPlaybackPosition(int generationId, long timeMs) {
        mMediaFocusControl.setRemoteControlClientPlaybackPosition(generationId, timeMs);
    }

    public void updateRemoteControlClientMetadata(int generationId, int key, Rating value) {
        mMediaFocusControl.updateRemoteControlClientMetadata(generationId, key, value);
    }

    public void registerRemoteVolumeObserverForRcc(int rccId, IRemoteVolumeObserver rvo) {
        mMediaFocusControl.registerRemoteVolumeObserverForRcc(rccId, rvo);
    }

    public int getRemoteStreamVolume() {
        return mMediaFocusControl.getRemoteStreamVolume();
    }

    public int getRemoteStreamMaxVolume() {
        return mMediaFocusControl.getRemoteStreamMaxVolume();
    }

    public void setRemoteStreamVolume(int index) {
        mMediaFocusControl.setRemoteStreamVolume(index);
    }

    public void setPlaybackStateForRcc(int rccId, int state, long timeMs, float speed) {
        mMediaFocusControl.setPlaybackStateForRcc(rccId, state, timeMs, speed);
    }

    public void setPlaybackInfoForRcc(int rccId, int what, int value) {
        mMediaFocusControl.setPlaybackInfoForRcc(rccId, what, value);
    }

    public void dispatchMediaKeyEvent(KeyEvent keyEvent) {
        mMediaFocusControl.dispatchMediaKeyEvent(keyEvent);
    }

    public void dispatchMediaKeyEventUnderWakelock(KeyEvent keyEvent) {
        mMediaFocusControl.dispatchMediaKeyEventUnderWakelock(keyEvent);
    }

    //==========================================================================================
    // Audio Focus
    //==========================================================================================
    public int requestAudioFocus(int mainStreamType, int durationHint, IBinder cb,
            IAudioFocusDispatcher fd, String clientId, String callingPackageName) {
        return mMediaFocusControl.requestAudioFocus(mainStreamType, durationHint, cb, fd,
                clientId, callingPackageName);
    }

    public int abandonAudioFocus(IAudioFocusDispatcher fd, String clientId) {
        return mMediaFocusControl.abandonAudioFocus(fd, clientId);
    }

    public void unregisterAudioFocusClient(String clientId) {
        mMediaFocusControl.unregisterAudioFocusClient(clientId);
    }

    public int getCurrentAudioFocus() {
        return mMediaFocusControl.getCurrentAudioFocus();
    }

    //==========================================================================================
    // Device orientation
    //==========================================================================================
    /**
     * Handles device configuration changes that may map to a change in the orientation
     * or orientation.
     * Monitoring orientation and rotation is optional, and is defined by the definition and value
     * of the "ro.audio.monitorOrientation" and "ro.audio.monitorRotation" system properties.
     */
    private void handleConfigurationChanged(Context context) {
        try {
            // reading new orientation "safely" (i.e. under try catch) in case anything
            // goes wrong when obtaining resources and configuration
            Configuration config = context.getResources().getConfiguration();
            // TODO merge rotation and orientation
            if (mMonitorOrientation) {
                int newOrientation = config.orientation;
                if (newOrientation != mDeviceOrientation) {
                    mDeviceOrientation = newOrientation;
                    setOrientationForAudioSystem();
                }
            }
            if (mMonitorRotation) {
                int newRotation = ((WindowManager) context.getSystemService(
                        Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                if (newRotation != mDeviceRotation) {
                    mDeviceRotation = newRotation;
                    setRotationForAudioSystem();
                }
            }
            sendMsg(mAudioHandler,
                    MSG_CONFIGURE_SAFE_MEDIA_VOLUME,
                    SENDMSG_REPLACE,
                    0,
                    0,
                    null,
                    0);

            boolean cameraSoundForced = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_camera_sound_forced);
            synchronized (mSettingsLock) {
                synchronized (mCameraSoundForced) {
                    if (cameraSoundForced != mCameraSoundForced) {
                        mCameraSoundForced = cameraSoundForced;

                        VolumeStreamState s = mStreamStates[AudioSystem.STREAM_SYSTEM_ENFORCED];
                        if (cameraSoundForced) {
                            s.setAllIndexesToMax();
                            mRingerModeAffectedStreams &=
                                    ~(1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
                        } else {
                            s.setAllIndexes(mStreamStates[AudioSystem.STREAM_SYSTEM]);
                            mRingerModeAffectedStreams |=
                                    (1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
                        }
                        // take new state into account for streams muted by ringer mode
                        setRingerModeInt(getRingerMode(), false);

                        sendMsg(mAudioHandler,
                                MSG_SET_FORCE_USE,
                                SENDMSG_QUEUE,
                                AudioSystem.FOR_SYSTEM,
                                cameraSoundForced ?
                                        AudioSystem.FORCE_SYSTEM_ENFORCED : AudioSystem.FORCE_NONE,
                                null,
                                0);

                        sendMsg(mAudioHandler,
                                MSG_SET_ALL_VOLUMES,
                                SENDMSG_QUEUE,
                                0,
                                0,
                                mStreamStates[AudioSystem.STREAM_SYSTEM_ENFORCED], 0);
                    }
                }
            }
            mVolumePanel.setLayoutDirection(config.getLayoutDirection());
        } catch (Exception e) {
            Log.e(TAG, "Error handling configuration change: ", e);
        }
    }

    private void setOrientationForAudioSystem() {
        switch (mDeviceOrientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                //Log.i(TAG, "orientation is landscape");
                AudioSystem.setParameters("orientation=landscape");
                break;
            case Configuration.ORIENTATION_PORTRAIT:
                //Log.i(TAG, "orientation is portrait");
                AudioSystem.setParameters("orientation=portrait");
                break;
            case Configuration.ORIENTATION_SQUARE:
                //Log.i(TAG, "orientation is square");
                AudioSystem.setParameters("orientation=square");
                break;
            case Configuration.ORIENTATION_UNDEFINED:
                //Log.i(TAG, "orientation is undefined");
                AudioSystem.setParameters("orientation=undefined");
                break;
            default:
                Log.e(TAG, "Unknown orientation");
        }
    }

    private void setRotationForAudioSystem() {
        switch (mDeviceRotation) {
            case Surface.ROTATION_0:
                AudioSystem.setParameters("rotation=0");
                break;
            case Surface.ROTATION_90:
                AudioSystem.setParameters("rotation=90");
                break;
            case Surface.ROTATION_180:
                AudioSystem.setParameters("rotation=180");
                break;
            case Surface.ROTATION_270:
                AudioSystem.setParameters("rotation=270");
                break;
            default:
                Log.e(TAG, "Unknown device rotation");
        }
    }


    // Handles request to override default use of A2DP for media.
    public void setBluetoothA2dpOnInt(boolean on) {
        synchronized (mBluetoothA2dpEnabledLock) {
            mBluetoothA2dpEnabled = on;
            mAudioHandler.removeMessages(MSG_SET_FORCE_BT_A2DP_USE);
            AudioSystem.setForceUse(AudioSystem.FOR_MEDIA,
                    mBluetoothA2dpEnabled ? AudioSystem.FORCE_NONE : AudioSystem.FORCE_NO_BT_A2DP);
        }
    }

    @Override
    public void setRingtonePlayer(IRingtonePlayer player) {
        mContext.enforceCallingOrSelfPermission(REMOTE_AUDIO_PLAYBACK, null);
        mRingtonePlayer = player;
    }

    @Override
    public IRingtonePlayer getRingtonePlayer() {
        return mRingtonePlayer;
    }

    @Override
    public AudioRoutesInfo startWatchingRoutes(IAudioRoutesObserver observer) {
        synchronized (mCurAudioRoutes) {
            AudioRoutesInfo routes = new AudioRoutesInfo(mCurAudioRoutes);
            mRoutesObservers.register(observer);
            return routes;
        }
    }


    //==========================================================================================
    // Safe media volume management.
    // MUSIC stream volume level is limited when headphones are connected according to safety
    // regulation. When the user attempts to raise the volume above the limit, a warning is
    // displayed and the user has to acknowlegde before the volume is actually changed.
    // The volume index corresponding to the limit is stored in config_safe_media_volume_index
    // property. Platforms with a different limit must set this property accordingly in their
    // overlay.
    //==========================================================================================

    // mSafeMediaVolumeState indicates whether the media volume is limited over headphones.
    // It is SAFE_MEDIA_VOLUME_NOT_CONFIGURED at boot time until a network service is connected
    // or the configure time is elapsed. It is then set to SAFE_MEDIA_VOLUME_ACTIVE or
    // SAFE_MEDIA_VOLUME_DISABLED according to country option. If not SAFE_MEDIA_VOLUME_DISABLED, it
    // can be set to SAFE_MEDIA_VOLUME_INACTIVE by calling AudioService.disableSafeMediaVolume()
    // (when user opts out).
    private final int SAFE_MEDIA_VOLUME_NOT_CONFIGURED = 0;
    private final int SAFE_MEDIA_VOLUME_DISABLED = 1;
    private final int SAFE_MEDIA_VOLUME_INACTIVE = 2;
    private final int SAFE_MEDIA_VOLUME_ACTIVE = 3;
    private Integer mSafeMediaVolumeState;

    private int mMcc = 0;
    // mSafeVolumeEnabled indicates whether to check the volume of media play via headset
    private boolean mSafeVolumeEnabled;
    // mSafeMediaVolumeIndex is the cached value of config_safe_media_volume_index property
    private int mSafeMediaVolumeIndex;
    // mSafeMediaVolumeDevices lists the devices for which safe media volume is enforced,
    private final int mSafeMediaVolumeDevices = AudioSystem.DEVICE_OUT_WIRED_HEADSET |
                                                AudioSystem.DEVICE_OUT_WIRED_HEADPHONE;
    // mMusicActiveMs is the cumulative time of music activity since safe volume was disabled.
    // When this time reaches UNSAFE_VOLUME_MUSIC_ACTIVE_MS_MAX, the safe media volume is re-enabled
    // automatically. mMusicActiveMs is rounded to a multiple of MUSIC_ACTIVE_POLL_PERIOD_MS.
    private int mMusicActiveMs;
    private static final int UNSAFE_VOLUME_MUSIC_ACTIVE_MS_MAX = (20 * 3600 * 1000); // 20 hours
    private static final int MUSIC_ACTIVE_POLL_PERIOD_MS = 60000;  // 1 minute polling interval
    private static final int SAFE_VOLUME_CONFIGURE_TIMEOUT_MS = 30000;  // 30s after boot completed

    private void setSafeMediaVolumeEnabled(boolean on) {
        synchronized (mSafeMediaVolumeState) {
            if ((mSafeMediaVolumeState != SAFE_MEDIA_VOLUME_NOT_CONFIGURED) &&
                    (mSafeMediaVolumeState != SAFE_MEDIA_VOLUME_DISABLED)) {
                if (on && (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_INACTIVE)) {
                    mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_ACTIVE;
                    enforceSafeMediaVolume();
                } else if (!on && (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE)) {
                    mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_INACTIVE;
                    mMusicActiveMs = 0;
                    sendMsg(mAudioHandler,
                            MSG_CHECK_MUSIC_ACTIVE,
                            SENDMSG_REPLACE,
                            0,
                            0,
                            null,
                            MUSIC_ACTIVE_POLL_PERIOD_MS);
                }
            }
        }
    }

    private void enforceSafeMediaVolume() {
        VolumeStreamState streamState = mStreamStates[AudioSystem.STREAM_MUSIC];
        int devices = mSafeMediaVolumeDevices;
        int i = 0;

        while (devices != 0) {
            int device = 1 << i++;
            if ((device & devices) == 0) {
                continue;
            }
            int index = streamState.getIndex(device);
            if (index > mSafeMediaVolumeIndex) {
                streamState.setIndex(mSafeMediaVolumeIndex, device);
                sendMsg(mAudioHandler,
                        MSG_SET_DEVICE_VOLUME,
                        SENDMSG_QUEUE,
                        device,
                        0,
                        streamState,
                        0);
            }
            devices &= ~device;
        }
    }

    private boolean checkSafeMediaVolume(int streamType, int index, int device) {
        synchronized (mSafeMediaVolumeState) {
            if (mSafeVolumeEnabled &&
                    (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE) &&
                    (mStreamVolumeAlias[streamType] == AudioSystem.STREAM_MUSIC) &&
                    ((device & mSafeMediaVolumeDevices) != 0) &&
                    (index > mSafeMediaVolumeIndex)) {
                displaySafeVolumeWarning();
                return false;
            }
            return true;
        }
    }

    public void disableSafeMediaVolume() {
        synchronized (mSafeMediaVolumeState) {
            setSafeMediaVolumeEnabled(false);
            if (mPendingVolumeCommand != null) {
                onSetStreamVolume(mPendingVolumeCommand.mStreamType,
                                  mPendingVolumeCommand.mIndex,
                                  mPendingVolumeCommand.mFlags,
                                  mPendingVolumeCommand.mDevice);
                mPendingVolumeCommand = null;
            }
        }
    }

    public int verifyX509CertChain(int numcerts, byte [] chain, String domain, String authType) {

        if (DEBUG_CERTS) {
            Log.v(TAG, "java side verify for "
                    + numcerts + " certificates (" + chain.length + " bytes"
                            + ")for "+ domain + "/" + authType);
        }

        byte[][] certChain = new byte[numcerts][];

        ByteBuffer buf = ByteBuffer.wrap(chain);
        for (int i = 0; i < numcerts; i++) {
            int certlen = buf.getInt();
            if (DEBUG_CERTS) {
                Log.i(TAG, "cert " + i +": " + certlen);
            }
            certChain[i] = new byte[certlen];
            buf.get(certChain[i]);
        }

        try {
            SslError err = CertificateChainValidator.verifyServerCertificates(certChain,
                    domain, authType);
            if (DEBUG_CERTS) {
                Log.i(TAG, "verified: " + err);
            }
            if (err == null) {
                return -1;
            } else {
                return err.getPrimaryError();
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to verify chain: " + e);
        }
        return SslError.SSL_INVALID;
    }


    //==========================================================================================
    // Camera shutter sound policy.
    // config_camera_sound_forced configuration option in config.xml defines if the camera shutter
    // sound is forced (sound even if the device is in silent mode) or not. This option is false by
    // default and can be overridden by country specific overlay in values-mccXXX/config.xml.
    //==========================================================================================

    // cached value of com.android.internal.R.bool.config_camera_sound_forced
    private Boolean mCameraSoundForced;

    // called by android.hardware.Camera to populate CameraInfo.canDisableShutterSound
    public boolean isCameraSoundForced() {
        synchronized (mCameraSoundForced) {
            return mCameraSoundForced;
        }
    }

    private static final String[] RINGER_MODE_NAMES = new String[] {
            "SILENT",
            "VIBRATE",
            "NORMAL"
    };

    private void dumpRingerMode(PrintWriter pw) {
        pw.println("\nRinger mode: ");
        pw.println("- mode: "+RINGER_MODE_NAMES[mRingerMode]);
        pw.print("- ringer mode affected streams = 0x");
        pw.println(Integer.toHexString(mRingerModeAffectedStreams));
        pw.print("- ringer mode muted streams = 0x");
        pw.println(Integer.toHexString(mRingerModeMutedStreams));
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

        mMediaFocusControl.dump(pw);
        dumpStreamStates(pw);
        dumpRingerMode(pw);
        pw.println("\nAudio routes:");
        pw.print("  mMainType=0x"); pw.println(Integer.toHexString(mCurAudioRoutes.mMainType));
        pw.print("  mBluetoothName="); pw.println(mCurAudioRoutes.mBluetoothName);
    }

    // Inform AudioFlinger of our device's low RAM attribute
    private static void readAndSetLowRamDevice()
    {
        int status = AudioSystem.setLowRamDevice(ActivityManager.isLowRamDeviceStatic());
        if (status != 0) {
            Log.w(TAG, "AudioFlinger informed of device's low RAM attribute; status " + status);
        }
    }
}
