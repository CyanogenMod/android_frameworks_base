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

package com.android.server.audio;

import static android.Manifest.permission.REMOTE_AUDIO_PLAYBACK;
import static android.media.AudioManager.RINGER_MODE_NORMAL;
import static android.media.AudioManager.RINGER_MODE_SILENT;
import static android.media.AudioManager.RINGER_MODE_VIBRATE;
import static android.os.Process.FIRST_APPLICATION_UID;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerNative;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiTvClient;
import android.hardware.usb.UsbManager;
import android.media.AudioAttributes;
import android.media.AudioDevicePort;
import android.media.AudioSystem;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManagerInternal;
import android.media.AudioPort;
import android.media.AudioRecord;
import android.media.AudioRoutesInfo;
import android.media.IAudioFocusDispatcher;
import android.media.IAudioRoutesObserver;
import android.media.IAudioService;
import android.media.IRemoteControlDisplay;
import android.media.IRingtonePlayer;
import android.media.IVolumeController;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.VolumePolicy;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioPolicy;
import android.media.audiopolicy.AudioPolicyConfig;
import android.media.audiopolicy.IAudioPolicyCallback;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.System;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.MathUtils;
import android.util.Slog;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.util.XmlUtils;
import com.android.server.EventLogTags;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerService;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import cyanogenmod.providers.CMSettings;

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

    /** Debug audio mode */
    protected static final boolean DEBUG_MODE = Log.isLoggable(TAG + ".MOD", Log.DEBUG);

    /** Debug audio policy feature */
    protected static final boolean DEBUG_AP = Log.isLoggable(TAG + ".AP", Log.DEBUG);

    /** Debug volumes */
    protected static final boolean DEBUG_VOL = Log.isLoggable(TAG + ".VOL", Log.DEBUG);

    /** debug calls to devices APIs */
    protected static final boolean DEBUG_DEVICES = Log.isLoggable(TAG + ".DEVICES", Log.DEBUG);

    /** How long to delay before persisting a change in volume/ringer mode. */
    private static final int PERSIST_DELAY = 500;

    /** How long to delay after a volume down event before unmuting a stream */
    private static final int UNMUTE_STREAM_DELAY = 350;

    /**
     * Only used in the result from {@link #checkForRingerModeChange(int, int, int)}
     */
    private static final int FLAG_ADJUST_VOLUME = 1;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final AppOpsManager mAppOps;

    // the platform type affects volume and silent mode behavior
    private final int mPlatformType;

    private static final ArrayList<MediaPlayerInfo> mMediaPlayers =
                                        new ArrayList<MediaPlayerInfo>();

    private class MediaPlayerInfo {
        private String mPackageName;
        private boolean mIsfocussed;
        public MediaPlayerInfo(String packageName, boolean isfocussed) {
            mPackageName = packageName;
            mIsfocussed = isfocussed;
        }
        public boolean isFocussed() {
            return mIsfocussed;
        }
        public void setFocus(boolean focus) {
            mIsfocussed = focus;
        }
        public String getPackageName() {
            return mPackageName;
        }
    }

    private boolean isPlatformVoice() {
        return mPlatformType == AudioSystem.PLATFORM_VOICE;
    }

    private boolean isPlatformTelevision() {
        return mPlatformType == AudioSystem.PLATFORM_TELEVISION;
    }

    /** The controller for the volume UI. */
    private final VolumeController mVolumeController = new VolumeController();
    private final ControllerService mControllerService = new ControllerService();

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
    private static final int MSG_SYSTEM_READY = 21;
    private static final int MSG_PERSIST_MUSIC_ACTIVE_MS = 22;
    private static final int MSG_PERSIST_MICROPHONE_MUTE = 23;
    private static final int MSG_UNMUTE_STREAM = 24;
    private static final int MSG_DYN_POLICY_MIX_STATE_UPDATE = 25;
    private static final int MSG_INDICATE_SYSTEM_READY = 26;
    // start of messages handled under wakelock
    //   these messages can only be queued, i.e. sent with queueMsgUnderWakeLock(),
    //   and not with sendMsg(..., ..., SENDMSG_QUEUE, ...)
    private static final int MSG_SET_WIRED_DEVICE_CONNECTION_STATE = 100;
    private static final int MSG_SET_A2DP_SRC_CONNECTION_STATE = 101;
    private static final int MSG_SET_A2DP_SINK_CONNECTION_STATE = 102;
    // end of messages handled under wakelock

    private static final int BTA2DP_DOCK_TIMEOUT_MILLIS = 8000;
    // Timeout for connection to bluetooth headset service
    private static final int BT_HEADSET_CNCT_TIMEOUT_MS = 3000;

    // retry delay in case of failure to indicate system ready to AudioFlinger
    private static final int INDICATE_SYSTEM_READY_RETRY_DELAY_MS = 1000;

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

    /* Sound effect file names  */
    private static final String SOUND_EFFECTS_PATH = "/media/audio/ui/";
    private static final List<String> SOUND_EFFECT_FILES = new ArrayList<String>();

    /* Sound effect file name mapping sound effect id (AudioManager.FX_xxx) to
     * file index in SOUND_EFFECT_FILES[] (first column) and indicating if effect
     * uses soundpool (second column) */
    private final int[][] SOUND_EFFECT_FILES_MAP = new int[AudioManager.NUM_SOUND_EFFECTS][2];

   /** Maximum volume index values for audio streams */
    private static int[] MAX_STREAM_VOLUME = new int[] {
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

    /** Minimum volume index values for audio streams */
    private static int[] MIN_STREAM_VOLUME = new int[] {
        1,  // STREAM_VOICE_CALL
        0,  // STREAM_SYSTEM
        0,  // STREAM_RING
        0,  // STREAM_MUSIC
        0,  // STREAM_ALARM
        0,  // STREAM_NOTIFICATION
        1,  // STREAM_BLUETOOTH_SCO
        0,  // STREAM_SYSTEM_ENFORCED
        0,  // STREAM_DTMF
        0   // STREAM_TTS
    };

    /* mStreamVolumeAlias[] indicates for each stream if it uses the volume settings
     * of another stream: This avoids multiplying the volume settings for hidden
     * stream types that follow other stream behavior for volume settings
     * NOTE: do not create loops in aliases!
     * Some streams alias to different streams according to device category (phone or tablet) or
     * use case (in call vs off call...). See updateStreamVolumeAlias() for more details.
     *  mStreamVolumeAlias contains STREAM_VOLUME_ALIAS_VOICE aliases for a voice capable device
     *  (phone), STREAM_VOLUME_ALIAS_TELEVISION for a television or set-top box and
     *  STREAM_VOLUME_ALIAS_DEFAULT for other devices (e.g. tablets).*/
    private final int[] STREAM_VOLUME_ALIAS_VOICE = new int[] {
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
    private final int[] STREAM_VOLUME_ALIAS_TELEVISION = new int[] {
        AudioSystem.STREAM_MUSIC,       // STREAM_VOICE_CALL
        AudioSystem.STREAM_MUSIC,       // STREAM_SYSTEM
        AudioSystem.STREAM_MUSIC,       // STREAM_RING
        AudioSystem.STREAM_MUSIC,       // STREAM_MUSIC
        AudioSystem.STREAM_MUSIC,       // STREAM_ALARM
        AudioSystem.STREAM_MUSIC,       // STREAM_NOTIFICATION
        AudioSystem.STREAM_MUSIC,       // STREAM_BLUETOOTH_SCO
        AudioSystem.STREAM_MUSIC,       // STREAM_SYSTEM_ENFORCED
        AudioSystem.STREAM_MUSIC,       // STREAM_DTMF
        AudioSystem.STREAM_MUSIC        // STREAM_TTS
    };
    private final int[] STREAM_VOLUME_ALIAS_DEFAULT = new int[] {
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
    private int[] mStreamVolumeAlias;

    /**
     * Map AudioSystem.STREAM_* constants to app ops.  This should be used
     * after mapping through mStreamVolumeAlias.
     */
    private static final int[] STREAM_VOLUME_OPS = new int[] {
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
    private int mRingerMode;  // internal ringer mode, affects muting of underlying streams
    private int mRingerModeExternal = -1;  // reported ringer mode to outside clients (AudioManager)

    /** @see System#MODE_RINGER_STREAMS_AFFECTED */
    private int mRingerModeAffectedStreams = 0;

    // Streams currently muted by ringer mode
    private int mRingerModeMutedStreams;

    /** Streams that can be muted. Do not resolve to aliases when checking.
     * @see System#MUTE_STREAMS_AFFECTED */
    private int mMuteAffectedStreams;

    /** @see #handleHotwordInput **/
    private Object mHotwordInputLock = new Object();

    /** The package name of the application that is
     * currently using the HOTWORD input.
     */
    // protected by mHotwordInputLock
    private String mHotwordAudioInputPackage;

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
    // Use makeDeviceListKey() to make a unique key for this list.
    private class DeviceListSpec {
        int mDeviceType;
        String mDeviceName;
        String mDeviceAddress;

        public DeviceListSpec(int deviceType, String deviceName, String deviceAddress) {
            mDeviceType = deviceType;
            mDeviceName = deviceName;
            mDeviceAddress = deviceAddress;
        }

        public String toString() {
            return "[type:0x" + Integer.toHexString(mDeviceType) + " name:" + mDeviceName
                    + " address:" + mDeviceAddress + "]";
        }
    }

    // Generate a unique key for the mConnectedDevices List by composing the device "type"
    // and the "address" associated with a specific instance of that device type
    private String makeDeviceListKey(int device, String deviceAddress) {
        return "0x" + Integer.toHexString(device) + ":" + deviceAddress;
    }

    private final ArrayMap<String, DeviceListSpec> mConnectedDevices = new ArrayMap<>();

    private String mA2dpConnectedDevice = ""; //Used for BT a2dp connection
    //Add connected A2dp devices in this list
    private ArrayList<BluetoothDevice> mConnectedBTDevicesList =
            new ArrayList<BluetoothDevice>();

    // Forced device usage for communications
    private int mForcedUseForComm;

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
    // SCO audio mode is undefined
    private static final int SCO_MODE_UNDEFINED = -1;
    // SCO audio mode is virtual voice call (BluetoothHeadset.startScoUsingVirtualVoiceCall())
    private static final int SCO_MODE_VIRTUAL_CALL = 0;
    // SCO audio mode is raw audio (BluetoothHeadset.connectAudio())
    private static final int SCO_MODE_RAW = 1;
    // SCO audio mode is Voice Recognition (BluetoothHeadset.startVoiceRecognition())
    private static final int SCO_MODE_VR = 2;

    private static final int SCO_MODE_MAX = 2;

    // Current connection state indicated by bluetooth headset
    private int mScoConnectionState;

    // true if boot sequence has been completed
    private boolean mSystemReady;
    // listener for SoundPool sample load completion indication
    private SoundPoolCallback mSoundPoolCallBack;
    // thread for SoundPool listener
    private SoundPoolListenerThread mSoundPoolListenerThread;
    // message looper for SoundPool listener
    private Looper mSoundPoolLooper = null;
    // volume applied to sound played with playSoundEffect()
    private static int sSoundEffectVolumeDb;
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

    // Request to override default use of A2DP for media.
    private boolean mBluetoothA2dpEnabled;
    private final Object mBluetoothA2dpEnabledLock = new Object();

    // Monitoring of audio routes.  Protected by mCurAudioRoutes.
    final AudioRoutesInfo mCurAudioRoutes = new AudioRoutesInfo();
    final RemoteCallbackList<IAudioRoutesObserver> mRoutesObservers
            = new RemoteCallbackList<IAudioRoutesObserver>();

    // Devices for which the volume is fixed and VolumePanel slider should be disabled
    int mFixedVolumeDevices = AudioSystem.DEVICE_OUT_HDMI |
            AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET |
            AudioSystem.DEVICE_OUT_HDMI_ARC |
            AudioSystem.DEVICE_OUT_SPDIF |
            AudioSystem.DEVICE_OUT_AUX_LINE;
    int mFullVolumeDevices = 0;

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

    private final MediaFocusControl mMediaFocusControl;

    // Reference to BluetoothA2dp to query for AbsoluteVolume.
    private BluetoothA2dp mA2dp;
    // lock always taken synchronized on mConnectedDevices
    private final Object mA2dpAvrcpLock = new Object();
    // If absolute volume is supported in AVRCP device
    private boolean mAvrcpAbsVolSupported = false;

    private boolean mLinkNotificationWithVolume;
    private final boolean mVoiceCapable;
    private static Long mLastDeviceConnectMsgTime = new Long(0);

    private AudioManagerInternal.RingerModeDelegate mRingerModeDelegate;
    private VolumePolicy mVolumePolicy = VolumePolicy.DEFAULT;
    private long mLoweredFromNormalToVibrateTime;
    private boolean mVolumeKeysControlRingStream;

    // Intent "extra" data keys.
    public static final String CONNECT_INTENT_KEY_PORT_NAME = "portName";
    public static final String CONNECT_INTENT_KEY_STATE = "state";
    public static final String CONNECT_INTENT_KEY_ADDRESS = "address";
    public static final String CONNECT_INTENT_KEY_HAS_PLAYBACK = "hasPlayback";
    public static final String CONNECT_INTENT_KEY_HAS_CAPTURE = "hasCapture";
    public static final String CONNECT_INTENT_KEY_HAS_MIDI = "hasMIDI";
    public static final String CONNECT_INTENT_KEY_DEVICE_CLASS = "class";

    // Defines the format for the connection "address" for ALSA devices
    public static String makeAlsaAddressString(int card, int device) {
        return "card=" + card + ";device=" + device + ";";
    }

    ///////////////////////////////////////////////////////////////////////////
    // Construction
    ///////////////////////////////////////////////////////////////////////////

    /** @hide */
    public AudioService(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mAppOps = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);

        mPlatformType = AudioSystem.getPlatformType(context);

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mAudioEventWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "handleAudioEvent");

        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator == null ? false : vibrator.hasVibrator();

        // Initialize volume
        int maxVolume = SystemProperties.getInt("ro.config.vc_call_vol_steps",
                MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL]);
        if (maxVolume != MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL]) {
            MAX_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL] = maxVolume;
            AudioSystem.DEFAULT_STREAM_VOLUME[AudioSystem.STREAM_VOICE_CALL] = (maxVolume * 3) / 4;
        }
        maxVolume = SystemProperties.getInt("ro.config.media_vol_steps",
                MAX_STREAM_VOLUME[AudioSystem.STREAM_MUSIC]);
        if (maxVolume != MAX_STREAM_VOLUME[AudioSystem.STREAM_MUSIC]) {
            MAX_STREAM_VOLUME[AudioSystem.STREAM_MUSIC] = maxVolume;
            AudioSystem.DEFAULT_STREAM_VOLUME[AudioSystem.STREAM_MUSIC] = (maxVolume * 3) / 4;
        }

        sSoundEffectVolumeDb = context.getResources().getInteger(
                com.android.internal.R.integer.config_soundEffectVolumeDb);

        mForcedUseForComm = AudioSystem.FORCE_NONE;

        mVoiceCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);

        createAudioSystemThread();

        AudioSystem.setErrorCallback(mAudioSystemCallback);

        boolean cameraSoundForced = readCameraSoundForced();
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

        mForceAnalogDeskDock = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_forceAnalogDeskDock);
        mForceAnalogCarDock = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_forceAnalogCarDock);

        // read this in before readPersistedSettings() because updateStreamVolumeAlias needs it
        mLinkNotificationWithVolume = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.VOLUME_LINK_NOTIFICATION, 1) == 1;

        // must be called before readPersistedSettings() which needs a valid mStreamVolumeAlias[]
        // array initialized by updateStreamVolumeAlias()
        updateStreamVolumeAlias(false /*updateVolumes*/, TAG);
        readPersistedSettings();
        mSettingsObserver = new SettingsObserver();
        createStreamStates();

        mMediaFocusControl = new MediaFocusControl(mAudioHandler.getLooper(),
                mContext, mVolumeController, this);

        readAndSetLowRamDevice();

        // Call setRingerModeInt() to apply correct mute
        // state on streams affected by ringer mode.
        mRingerModeMutedStreams = 0;
        setRingerModeInt(getRingerModeInternal(), false);

        // Register for device connection intent broadcasts.
        IntentFilter intentFilter =
                new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_DOCK_EVENT);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_USER_SWITCHED);
        intentFilter.addAction(Intent.ACTION_USER_BACKGROUND);
        intentFilter.addAction(Intent.ACTION_USER_FOREGROUND);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SHUTDOWN);
        // TODO merge orientation and rotation
        mMonitorOrientation = SystemProperties.getBoolean("ro.audio.monitorOrientation", false);
        if (mMonitorOrientation) {
            Log.v(TAG, "monitoring device orientation");
            // initialize orientation in AudioSystem
            setOrientationForAudioSystem();
        }
        mMonitorRotation = SystemProperties.getBoolean("ro.audio.monitorRotation", false);
        if (mMonitorRotation) {
            RotationHelper.init(mContext, mAudioHandler);
        }

        context.registerReceiverAsUser(mReceiver, UserHandle.ALL, intentFilter, null, null);

        LocalServices.addService(AudioManagerInternal.class, new AudioServiceInternal());
    }

    public void systemReady() {
        sendMsg(mAudioHandler, MSG_SYSTEM_READY, SENDMSG_QUEUE,
                0, 0, null, 0);
    }

    public void onSystemReady() {
        mSystemReady = true;
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

        mHdmiManager =
                (HdmiControlManager) mContext.getSystemService(Context.HDMI_CONTROL_SERVICE);
        if (mHdmiManager != null) {
            synchronized (mHdmiManager) {
                mHdmiTvClient = mHdmiManager.getTvClient();
                if (mHdmiTvClient != null) {
                    mFixedVolumeDevices &= ~AudioSystem.DEVICE_ALL_HDMI_SYSTEM_AUDIO_AND_SPEAKER;
                }
                mHdmiPlaybackClient = mHdmiManager.getPlaybackClient();
                mHdmiCecSink = false;
            }
        }

        sendMsg(mAudioHandler,
                MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED,
                SENDMSG_REPLACE,
                0,
                0,
                TAG,
                SAFE_VOLUME_CONFIGURE_TIMEOUT_MS);

        StreamOverride.init(mContext);
        mControllerService.init();
        onIndicateSystemReady();
    }

    void onIndicateSystemReady() {
        if (AudioSystem.systemReady() == AudioSystem.SUCCESS) {
            return;
        }
        sendMsg(mAudioHandler,
                MSG_INDICATE_SYSTEM_READY,
                SENDMSG_REPLACE,
                0,
                0,
                null,
                INDICATE_SYSTEM_READY_RETRY_DELAY_MS);
    }

    public void onMediaServerDied() {
        if (!mSystemReady ||
                (AudioSystem.checkAudioFlinger() != AudioSystem.AUDIO_STATUS_OK)) {
            Log.e(TAG, "Media server died.");
            sendMsg(mAudioHandler, MSG_MEDIA_SERVER_DIED, SENDMSG_NOOP, 0, 0,
                    null, 500);
            return;
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
            for (int i = 0; i < mConnectedDevices.size(); i++) {
                DeviceListSpec spec = mConnectedDevices.valueAt(i);
                AudioSystem.setDeviceConnectionState(
                                                spec.mDeviceType,
                                                AudioSystem.DEVICE_STATE_AVAILABLE,
                                                spec.mDeviceAddress,
                                                spec.mDeviceName);
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
        setRingerModeInt(getRingerModeInternal(), false);

        // Reset device orientation (if monitored for this device)
        if (mMonitorOrientation) {
            setOrientationForAudioSystem();
        }
        if (mMonitorRotation) {
            RotationHelper.updateOrientation();
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
        if (mHdmiManager != null) {
            synchronized (mHdmiManager) {
                if (mHdmiTvClient != null) {
                    setHdmiSystemAudioSupported(mHdmiSystemAudioSupported);
                }
            }
        }

        synchronized (mAudioPolicies) {
            for (AudioPolicyProxy policy : mAudioPolicies.values()) {
                policy.connectMixes();
            }
        }

        onIndicateSystemReady();
        // indicate the end of reconfiguration phase to audio HAL
        AudioSystem.setParameters("restarting=false");
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

    /**
     * @hide
     */
    public void addMediaPlayerAndUpdateRemoteController (String packageName) {
        synchronized(mMediaPlayers) {
            Log.v(TAG, "addMediaPlayerAndUpdateRemoteController: size of existing list: " +
                    mMediaPlayers.size());
            boolean playerToAdd = true;
            if (mMediaPlayers.size() > 0) {
                final Iterator<MediaPlayerInfo> rccIterator = mMediaPlayers.iterator();
                while (rccIterator.hasNext()) {
                    final MediaPlayerInfo player = rccIterator.next();
                    if (packageName.equals(player.getPackageName())) {
                        Log.e(TAG, "Player entry present, no need to add");
                        playerToAdd = false;
                        player.setFocus(true);
                    } else {
                        Log.e(TAG, "Player: " + player.getPackageName()+ "Lost Focus");
                        player.setFocus(false);
                    }
                }
            }
            if (playerToAdd) {
                Log.e(TAG, "Adding Player: " + packageName + " to available player list");
                mMediaPlayers.add(new MediaPlayerInfo(packageName, true));
            }
            Intent intent = new Intent(AudioManager.RCC_CHANGED_ACTION);
            intent.putExtra(AudioManager.EXTRA_CALLING_PACKAGE_NAME, packageName);
            intent.putExtra(AudioManager.EXTRA_FOCUS_CHANGED_VALUE, true);
            intent.putExtra(AudioManager.EXTRA_AVAILABLITY_CHANGED_VALUE, true);
            sendBroadcastToAll(intent);
            Log.v(TAG, "updating focussed RCC change to RCD: CallingPackageName:"
                    + packageName);
        }
    }

    /**
     * @hide
     */
    public void updateRemoteControllerOnExistingMediaPlayers() {
        synchronized(mMediaPlayers) {
            Log.v(TAG, "updateRemoteControllerOnExistingMediaPlayers: size of Player list: " +
                                                                mMediaPlayers.size());
            if (mMediaPlayers.size() > 0) {
                Log.v(TAG, "Inform RemoteController regarding existing RCC entry");
                final Iterator<MediaPlayerInfo> rccIterator = mMediaPlayers.iterator();
                while (rccIterator.hasNext()) {
                    final MediaPlayerInfo player = rccIterator.next();
                    Intent intent = new Intent(AudioManager.RCC_CHANGED_ACTION);
                    intent.putExtra(AudioManager.EXTRA_CALLING_PACKAGE_NAME,
                                                        player.getPackageName());
                    intent.putExtra(AudioManager.EXTRA_FOCUS_CHANGED_VALUE,
                                                        player.isFocussed());
                    intent.putExtra(AudioManager.EXTRA_AVAILABLITY_CHANGED_VALUE, true);
                    sendBroadcastToAll(intent);
                    Log.v(TAG, "updating RCC change: CallingPackageName:" +
                                                        player.getPackageName());
                }
            } else {
                Log.e(TAG, "No RCC entry present to update");
            }
        }
    }

    /**
     * @hide
     */
    public void removeMediaPlayerAndUpdateRemoteController (String packageName) {
        synchronized(mMediaPlayers) {
            Log.v(TAG, "removeMediaPlayerAndUpdateRemoteController: size of existing list: " +
                                                                    mMediaPlayers.size());
            boolean playerToRemove = false;
            int index = -1;
            if (mMediaPlayers.size() > 0) {
                final Iterator<MediaPlayerInfo> rccIterator = mMediaPlayers.iterator();
                while (rccIterator.hasNext()) {
                    index++;
                    final MediaPlayerInfo player = rccIterator.next();
                    if (packageName.equals(player.getPackageName())) {
                        Log.v(TAG, "Player entry present remove and update RemoteController");
                        playerToRemove = true;
                        break;
                    } else {
                        Log.v(TAG, "Player entry for " + player.getPackageName()+ " is not present");
                    }
                }
            }
            if (playerToRemove) {
                Log.e(TAG, "Removing Player: " + packageName + " from index" + index);
                mMediaPlayers.remove(index);
            }
            Intent intent = new Intent(AudioManager.RCC_CHANGED_ACTION);
            intent.putExtra(AudioManager.EXTRA_CALLING_PACKAGE_NAME, packageName);
            intent.putExtra(AudioManager.EXTRA_FOCUS_CHANGED_VALUE, false);
            intent.putExtra(AudioManager.EXTRA_AVAILABLITY_CHANGED_VALUE, false);
            sendBroadcastToAll(intent);
            Log.v(TAG, "Updated List size: " + mMediaPlayers.size());
        }
    }

    private void checkAllAliasStreamVolumes() {
        synchronized (VolumeStreamState.class) {
            int numStreamTypes = AudioSystem.getNumStreamTypes();
            for (int streamType = 0; streamType < numStreamTypes; streamType++) {
                if (streamType != mStreamVolumeAlias[streamType]) {
                    mStreamStates[streamType].
                                    setAllIndexes(mStreamStates[mStreamVolumeAlias[streamType]],
                                            TAG);
                }
                // apply stream volume
                if (!mStreamStates[streamType].mIsMuted) {
                    mStreamStates[streamType].applyAllVolumes();
                }
            }
        }
    }

    private void checkAllFixedVolumeDevices()
    {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int streamType = 0; streamType < numStreamTypes; streamType++) {
            mStreamStates[streamType].checkFixedVolumeDevices();
        }
    }

    private void checkAllFixedVolumeDevices(int streamType) {
        mStreamStates[streamType].checkFixedVolumeDevices();
    }

    private void checkMuteAffectedStreams() {
        // any stream with a min level > 0 is not muteable by definition
        for (int i = 0; i < mStreamStates.length; i++) {
            final VolumeStreamState vss = mStreamStates[i];
            if (vss.mIndexMin > 0) {
                mMuteAffectedStreams &= ~(1 << vss.mStreamType);
            }
        }
    }

    private void createStreamStates() {
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        VolumeStreamState[] streams = mStreamStates = new VolumeStreamState[numStreamTypes];

        for (int i = 0; i < numStreamTypes; i++) {
            streams[i] = new VolumeStreamState(System.VOLUME_SETTINGS[mStreamVolumeAlias[i]], i);
        }

        checkAllFixedVolumeDevices();
        checkAllAliasStreamVolumes();
        checkMuteAffectedStreams();
    }

    private void dumpStreamStates(PrintWriter pw) {
        pw.println("\nStream volumes (device: index)");
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        for (int i = 0; i < numStreamTypes; i++) {
            pw.println("- " + AudioSystem.STREAM_NAMES[i] + ":");
            mStreamStates[i].dump(pw);
            pw.println("");
        }
        pw.print("\n- mute affected streams = 0x");
        pw.println(Integer.toHexString(mMuteAffectedStreams));
    }

    private void updateStreamVolumeAlias(boolean updateVolumes, String caller) {
        int dtmfStreamAlias;

        switch (mPlatformType) {
        case AudioSystem.PLATFORM_VOICE:
            mStreamVolumeAlias = STREAM_VOLUME_ALIAS_VOICE;
            dtmfStreamAlias = AudioSystem.STREAM_RING;
            break;
        case AudioSystem.PLATFORM_TELEVISION:
            mStreamVolumeAlias = STREAM_VOLUME_ALIAS_TELEVISION;
            dtmfStreamAlias = AudioSystem.STREAM_MUSIC;
            break;
        default:
            mStreamVolumeAlias = STREAM_VOLUME_ALIAS_DEFAULT;
            dtmfStreamAlias = AudioSystem.STREAM_MUSIC;
        }

        if (isPlatformTelevision()) {
            mRingerModeAffectedStreams = 0;
        } else {
            if (isInCommunication()) {
                dtmfStreamAlias = AudioSystem.STREAM_VOICE_CALL;
                mRingerModeAffectedStreams &= ~(1 << AudioSystem.STREAM_DTMF);
            } else {
                mRingerModeAffectedStreams |= (1 << AudioSystem.STREAM_DTMF);
            }
        }

        mStreamVolumeAlias[AudioSystem.STREAM_DTMF] = dtmfStreamAlias;

        if (mLinkNotificationWithVolume && mVoiceCapable) {
            mStreamVolumeAlias[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_RING;
        } else {
            mStreamVolumeAlias[AudioSystem.STREAM_NOTIFICATION] = AudioSystem.STREAM_NOTIFICATION;
        }

        if (updateVolumes) {
            mStreamStates[AudioSystem.STREAM_DTMF].setAllIndexes(mStreamStates[dtmfStreamAlias],
                    caller);
            // apply stream mute states according to new value of mRingerModeAffectedStreams
            setRingerModeInt(getRingerModeInternal(), false);
            sendMsg(mAudioHandler,
                    MSG_SET_ALL_VOLUMES,
                    SENDMSG_QUEUE,
                    0,
                    0,
                    mStreamStates[AudioSystem.STREAM_DTMF], 0);
        }
    }

    private void readDockAudioSettings(ContentResolver cr)
    {
        mDockAudioMediaEnabled = Settings.Global.getInt(
                                        cr, Settings.Global.DOCK_AUDIO_MEDIA_ENABLED, 0) == 1;

        sendMsg(mAudioHandler,
                MSG_SET_FORCE_USE,
                SENDMSG_QUEUE,
                AudioSystem.FOR_DOCK,
                mDockAudioMediaEnabled ?
                        AudioSystem.FORCE_ANALOG_DOCK : AudioSystem.FORCE_NONE,
                null,
                0);
    }

    private void readPersistedSettings() {
        final ContentResolver cr = mContentResolver;

        int ringerModeFromSettings =
                Settings.Global.getInt(
                        cr, Settings.Global.MODE_RINGER, AudioManager.RINGER_MODE_NORMAL);
        int ringerMode = ringerModeFromSettings;
        // sanity check in case the settings are restored from a device with incompatible
        // ringer modes
        if (!isValidRingerMode(ringerMode)) {
            ringerMode = AudioManager.RINGER_MODE_NORMAL;
        }
        if ((ringerMode == AudioManager.RINGER_MODE_VIBRATE) && !mHasVibrator) {
            ringerMode = AudioManager.RINGER_MODE_SILENT;
        }
        if (ringerMode != ringerModeFromSettings) {
            Settings.Global.putInt(cr, Settings.Global.MODE_RINGER, ringerMode);
        }
        if (mUseFixedVolume || isPlatformTelevision()) {
            ringerMode = AudioManager.RINGER_MODE_NORMAL;
        }
        synchronized(mSettingsLock) {
            mRingerMode = ringerMode;
            if (mRingerModeExternal == -1) {
                mRingerModeExternal = mRingerMode;
            }

            // System.VIBRATE_ON is not used any more but defaults for mVibrateSetting
            // are still needed while setVibrateSetting() and getVibrateSetting() are being
            // deprecated.
            mVibrateSetting = AudioSystem.getValueForVibrateSetting(0,
                                            AudioManager.VIBRATE_TYPE_NOTIFICATION,
                                            mHasVibrator ? AudioManager.VIBRATE_SETTING_ONLY_SILENT
                                                            : AudioManager.VIBRATE_SETTING_OFF);
            mVibrateSetting = AudioSystem.getValueForVibrateSetting(mVibrateSetting,
                                            AudioManager.VIBRATE_TYPE_RINGER,
                                            mHasVibrator ? AudioManager.VIBRATE_SETTING_ONLY_SILENT
                                                            : AudioManager.VIBRATE_SETTING_OFF);

            updateRingerModeAffectedStreams();
            readDockAudioSettings(cr);

            mVolumeKeysControlRingStream = CMSettings.System.getIntForUser(cr,
                    CMSettings.System.VOLUME_KEYS_CONTROL_RING_STREAM, 1,
                    UserHandle.USER_CURRENT) == 1;
        }

        mLinkNotificationWithVolume = Settings.Secure.getInt(cr,
                Settings.Secure.VOLUME_LINK_NOTIFICATION, 1) == 1;

        mMuteAffectedStreams = System.getIntForUser(cr,
                System.MUTE_STREAMS_AFFECTED, AudioSystem.DEFAULT_MUTE_STREAMS_AFFECTED,
                UserHandle.USER_CURRENT);

        boolean masterMute = System.getIntForUser(cr, System.VOLUME_MASTER_MUTE,
                0, UserHandle.USER_CURRENT) == 1;
        if (mUseFixedVolume) {
            masterMute = false;
            AudioSystem.setMasterVolume(1.0f);
        }
        AudioSystem.setMasterMute(masterMute);
        broadcastMasterMuteStatus(masterMute);

        boolean microphoneMute =
                System.getIntForUser(cr, System.MICROPHONE_MUTE, 0, UserHandle.USER_CURRENT) == 1;
        AudioSystem.muteMicrophone(microphoneMute);

        // Each stream will read its own persisted settings

        // Broadcast the sticky intents
        broadcastRingerMode(AudioManager.RINGER_MODE_CHANGED_ACTION, mRingerModeExternal);
        broadcastRingerMode(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION, mRingerMode);

        // Broadcast vibrate settings
        broadcastVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
        broadcastVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION);

        // Load settings for the volume controller
        mVolumeController.loadSettings(cr);
    }

    private int rescaleIndex(int index, int srcStream, int dstStream) {
        return (index * mStreamStates[dstStream].getMaxIndex() + mStreamStates[srcStream].getMaxIndex() / 2) / mStreamStates[srcStream].getMaxIndex();
    }

    ///////////////////////////////////////////////////////////////////////////
    // IPC methods
    ///////////////////////////////////////////////////////////////////////////
    /** @see AudioManager#adjustVolume(int, int) */
    public void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags,
            String callingPackage, String caller) {
        adjustSuggestedStreamVolume(direction, suggestedStreamType, flags, callingPackage,
                caller, Binder.getCallingUid());
    }

    private void adjustSuggestedStreamVolume(int direction, int suggestedStreamType, int flags,
            String callingPackage, String caller, int uid) {
        if (DEBUG_VOL) Log.d(TAG, "adjustSuggestedStreamVolume() stream=" + suggestedStreamType
                + ", flags=" + flags + ", caller=" + caller);
        int streamType;
        boolean isMute = isMuteAdjust(direction);
        if (mVolumeControlStream != -1) {
            streamType = mVolumeControlStream;
        } else {
            streamType = getActiveStreamType(suggestedStreamType);
        }
        ensureValidStreamType(streamType);
        final int resolvedStream = mStreamVolumeAlias[streamType];

        // Play sounds on STREAM_RING only.
        if ((flags & AudioManager.FLAG_PLAY_SOUND) != 0 &&
                resolvedStream != AudioSystem.STREAM_RING) {
            flags &= ~AudioManager.FLAG_PLAY_SOUND;
        }

        // For notifications/ring, show the ui before making any adjustments
        // Don't suppress mute/unmute requests
        if (mVolumeController.suppressAdjustment(resolvedStream, flags, isMute)) {
            direction = 0;
            flags &= ~AudioManager.FLAG_PLAY_SOUND;
            flags &= ~AudioManager.FLAG_VIBRATE;
            if (DEBUG_VOL) Log.d(TAG, "Volume controller suppressed adjustment");
        }

        adjustStreamVolume(streamType, direction, flags, callingPackage, caller, uid);
    }

    /** @see AudioManager#adjustStreamVolume(int, int, int) */
    public void adjustStreamVolume(int streamType, int direction, int flags,
            String callingPackage) {
        adjustStreamVolume(streamType, direction, flags, callingPackage, callingPackage,
                Binder.getCallingUid());
    }

    private void adjustStreamVolume(int streamType, int direction, int flags,
            String callingPackage, String caller, int uid) {
        if (mUseFixedVolume) {
            return;
        }
        if (DEBUG_VOL) Log.d(TAG, "adjustStreamVolume() stream=" + streamType + ", dir=" + direction
                + ", flags=" + flags + ", caller=" + caller);

        ensureValidDirection(direction);
        ensureValidStreamType(streamType);

        boolean isMuteAdjust = isMuteAdjust(direction);

        if (isMuteAdjust && !isStreamAffectedByMute(streamType)) {
            return;
        }

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

        // If we are being called by the system (e.g. hardware keys) check for current user
        // so we handle user restrictions correctly.
        if (uid == android.os.Process.SYSTEM_UID) {
            uid = UserHandle.getUid(getCurrentUserId(), UserHandle.getAppId(uid));
        }
        if (mAppOps.noteOp(STREAM_VOLUME_OPS[streamTypeAlias], uid, callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
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
                (streamTypeAlias == getUiSoundsStreamType())) {
            int ringerMode = getRingerModeInternal();
            // do not vibrate if already in vibrate mode
            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                flags &= ~AudioManager.FLAG_VIBRATE;
            }
            // Check if the ringer mode handles this adjustment. If it does we don't
            // need to adjust the volume further.
            final int result = checkForRingerModeChange(aliasIndex, direction, step,
                    streamState.mIsMuted);
            adjustVolume = (result & FLAG_ADJUST_VOLUME) != 0;
            // If suppressing a volume adjustment in silent mode, display the UI hint
            if ((result & AudioManager.FLAG_SHOW_SILENT_HINT) != 0) {
                flags |= AudioManager.FLAG_SHOW_SILENT_HINT;
            }
            // If suppressing a volume down adjustment in vibrate mode, display the UI hint
            if ((result & AudioManager.FLAG_SHOW_VIBRATE_HINT) != 0) {
                flags |= AudioManager.FLAG_SHOW_VIBRATE_HINT;
            }
        }
        // If the ringermode is suppressing media, prevent changes
        if (streamTypeAlias == AudioSystem.STREAM_MUSIC
                && (mRingerModeMutedStreams & (1 << AudioSystem.STREAM_MUSIC)) != 0) {
            adjustVolume = false;
        }

        int oldIndex = mStreamStates[streamType].getIndex(device);

        if (adjustVolume && (direction != AudioManager.ADJUST_SAME)) {
            mAudioHandler.removeMessages(MSG_UNMUTE_STREAM);

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

            if (isMuteAdjust) {
                boolean state;
                if (direction == AudioManager.ADJUST_TOGGLE_MUTE) {
                    state = !streamState.mIsMuted;
                } else {
                    state = direction == AudioManager.ADJUST_MUTE;
                }
                if (streamTypeAlias == AudioSystem.STREAM_MUSIC) {
                    setSystemAudioMute(state);
                }
                for (int stream = 0; stream < mStreamStates.length; stream++) {
                    if (streamTypeAlias == mStreamVolumeAlias[stream]) {
                        if (!(readCameraSoundForced()
                                    && (mStreamStates[stream].getStreamType()
                                        == AudioSystem.STREAM_SYSTEM_ENFORCED))) {
                            mStreamStates[stream].mute(state);
                        }
                    }
                }
            } else if ((direction == AudioManager.ADJUST_RAISE) &&
                    !checkSafeMediaVolume(streamTypeAlias, aliasIndex + step, device)) {
                Log.e(TAG, "adjustStreamVolume() safe volume index = " + oldIndex);
                mVolumeController.postDisplaySafeVolumeWarning(flags);
            } else if (streamState.adjustIndex(direction * step, device, caller)
                    || streamState.mIsMuted) {
                // Post message to set system volume (it in turn will post a
                // message to persist).
                if (streamState.mIsMuted) {
                    // Unmute the stream if it was previously muted
                    if (direction == AudioManager.ADJUST_RAISE) {
                        // unmute immediately for volume up
                        streamState.mute(false);
                    } else if (direction == AudioManager.ADJUST_LOWER) {
                        if (mPlatformType == AudioSystem.PLATFORM_TELEVISION) {
                            sendMsg(mAudioHandler, MSG_UNMUTE_STREAM, SENDMSG_QUEUE,
                                    streamTypeAlias, flags, null, UNMUTE_STREAM_DELAY);
                        }
                    }
                }
                sendMsg(mAudioHandler,
                        MSG_SET_DEVICE_VOLUME,
                        SENDMSG_QUEUE,
                        device,
                        0,
                        streamState,
                        0);
            }

            // Check if volume update should be sent to Hdmi system audio.
            int newIndex = mStreamStates[streamType].getIndex(device);
            if (streamTypeAlias == AudioSystem.STREAM_MUSIC) {
                setSystemAudioVolume(oldIndex, newIndex, getStreamMaxVolume(streamType), flags);
            }
            if (mHdmiManager != null) {
                synchronized (mHdmiManager) {
                    // mHdmiCecSink true => mHdmiPlaybackClient != null
                    if (mHdmiCecSink &&
                            streamTypeAlias == AudioSystem.STREAM_MUSIC &&
                            oldIndex != newIndex) {
                        synchronized (mHdmiPlaybackClient) {
                            int keyCode = (direction == -1) ? KeyEvent.KEYCODE_VOLUME_DOWN :
                                    KeyEvent.KEYCODE_VOLUME_UP;
                            mHdmiPlaybackClient.sendKeyEvent(keyCode, true);
                            mHdmiPlaybackClient.sendKeyEvent(keyCode, false);
                        }
                    }
                }
            }
        }
        int index = mStreamStates[streamType].getIndex(device);
        sendVolumeUpdate(streamType, oldIndex, index, flags);
    }

    // Called after a delay when volume down is pressed while muted
    private void onUnmuteStream(int stream, int flags) {
        VolumeStreamState streamState = mStreamStates[stream];
        streamState.mute(false);

        final int device = getDeviceForStream(stream);
        final int index = mStreamStates[stream].getIndex(device);
        sendVolumeUpdate(stream, index, index, flags);
    }

    private void setSystemAudioVolume(int oldVolume, int newVolume, int maxVolume, int flags) {
        if (mHdmiManager == null
                || mHdmiTvClient == null
                || oldVolume == newVolume
                || (flags & AudioManager.FLAG_HDMI_SYSTEM_AUDIO_VOLUME) != 0) return;

        // Sets the audio volume of AVR when we are in system audio mode. The new volume info
        // is tranformed to HDMI-CEC commands and passed through CEC bus.
        synchronized (mHdmiManager) {
            if (!mHdmiSystemAudioSupported) return;
            synchronized (mHdmiTvClient) {
                final long token = Binder.clearCallingIdentity();
                try {
                    mHdmiTvClient.setSystemAudioVolume(oldVolume, newVolume, maxVolume);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            }
        }
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

        @Override
        public String toString() {
            return new StringBuilder().append("{streamType=").append(mStreamType).append(",index=")
                    .append(mIndex).append(",flags=").append(mFlags).append(",device=")
                    .append(mDevice).append('}').toString();
        }
    };

    private void onSetStreamVolume(int streamType, int index, int flags, int device,
            String caller) {
        final int stream = mStreamVolumeAlias[streamType];
        setStreamVolumeInt(stream, index, device, false, caller);
        // setting volume on ui sounds stream type also controls silent mode
        if (((flags & AudioManager.FLAG_ALLOW_RINGER_MODES) != 0) ||
                (stream == getUiSoundsStreamType())) {
            int newRingerMode;
            if (index == 0) {
                newRingerMode = mHasVibrator ? AudioManager.RINGER_MODE_VIBRATE
                        : mVolumePolicy.volumeDownToEnterSilent ? AudioManager.RINGER_MODE_SILENT
                        : AudioManager.RINGER_MODE_NORMAL;
            } else {
                newRingerMode = AudioManager.RINGER_MODE_NORMAL;
            }
            setRingerMode(newRingerMode, TAG + ".onSetStreamVolume", false /*external*/);
        }
        // setting non-zero volume for a muted stream unmutes the stream and vice versa
        mStreamStates[stream].mute(index == 0);
    }

    /** @see AudioManager#setStreamVolume(int, int, int) */
    public void setStreamVolume(int streamType, int index, int flags, String callingPackage) {
        setStreamVolume(streamType, index, flags, callingPackage, callingPackage,
                Binder.getCallingUid());
    }

    private void setStreamVolume(int streamType, int index, int flags, String callingPackage,
            String caller, int uid) {
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
        // If we are being called by the system (e.g. hardware keys) check for current user
        // so we handle user restrictions correctly.
        if (uid == android.os.Process.SYSTEM_UID) {
            uid = UserHandle.getUid(getCurrentUserId(), UserHandle.getAppId(uid));
        }
        if (mAppOps.noteOp(STREAM_VOLUME_OPS[streamTypeAlias], uid, callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
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

            if (streamTypeAlias == AudioSystem.STREAM_MUSIC) {
                setSystemAudioVolume(oldIndex, index, getStreamMaxVolume(streamType), flags);
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
                mVolumeController.postDisplaySafeVolumeWarning(flags);
                mPendingVolumeCommand = new StreamVolumeCommand(
                                                    streamType, index, flags, device);
            } else {
                onSetStreamVolume(streamType, index, flags, device, caller);
                index = mStreamStates[streamType].getIndex(device);
            }
        }
        sendVolumeUpdate(streamType, oldIndex, index, flags);
    }

    /**
     * Retrieve the package name of the application that currently controls
     * the {@link android.media.MediaRecorder.AudioSource#HOTWORD} input.
     * @return The package name of the application that controls the input
     * or null if no package currently controls it.
     */
    public String getCurrentHotwordInputPackageName() {
        return mHotwordAudioInputPackage;
    }

    /**
     * Handle the change of state of the HOTWORD input.
     *
     * When the {@link android.media.MediaRecorder.AudioSource#HOTWORD} input is
     * in use, send a broadcast to alert the new state and store the name of the current
     * package that controls the input in mHotwordAudioInputPackage.
     * @param listening Whether the input is being listened to.
     */
    public void handleHotwordInput(boolean listening) {
        synchronized (mHotwordInputLock) {
            Intent broadcastIntent = new Intent(Intent.ACTION_HOTWORD_INPUT_CHANGED);
            String[] packages = mContext.getPackageManager().getPackagesForUid(
                    Binder.getCallingUid());
            if (packages.length > 0) {
                if (listening) {
                    mHotwordAudioInputPackage = packages[0];
                }
                broadcastIntent.putExtra(Intent.EXTRA_CURRENT_PACKAGE_NAME, packages[0]);
            }
            broadcastIntent.putExtra(Intent.EXTRA_HOTWORD_INPUT_STATE,
                                     listening ? AudioRecord.RECORDSTATE_RECORDING :
                                     AudioRecord.RECORDSTATE_STOPPED);
            // Set the currently listening package to null if listening has stopped.
            if (!listening) {
                mHotwordAudioInputPackage = null;
            }
            sendBroadcastToAll(broadcastIntent, Manifest.permission.CAPTURE_AUDIO_HOTWORD);
        }
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

    private void sendBroadcastToAll(Intent intent) {
        sendBroadcastToAll(intent, null);
    }

    private void sendBroadcastToAll(Intent intent, String receiverPermission) {
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL, receiverPermission);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void sendStickyBroadcastToAll(Intent intent) {
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private int getCurrentUserId() {
        final long ident = Binder.clearCallingIdentity();
        try {
            UserInfo currentUser = ActivityManagerNative.getDefault().getCurrentUser();
            return currentUser.id;
        } catch (RemoteException e) {
            // Activity manager not running, nothing we can do assume user 0.
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return UserHandle.USER_OWNER;
    }

    // UI update and Broadcast Intent
    private void sendVolumeUpdate(int streamType, int oldIndex, int index, int flags) {
        streamType = mStreamVolumeAlias[streamType];

        if (streamType == AudioSystem.STREAM_MUSIC) {
            flags = updateFlagsForSystemAudio(flags);
        }
        mVolumeController.postVolumeChanged(streamType, flags);
    }

    // If Hdmi-CEC system audio mode is on, we show volume bar only when TV
    // receives volume notification from Audio Receiver.
    private int updateFlagsForSystemAudio(int flags) {
        if (mHdmiTvClient != null) {
            synchronized (mHdmiTvClient) {
                if (mHdmiSystemAudioSupported &&
                        ((flags & AudioManager.FLAG_HDMI_SYSTEM_AUDIO_VOLUME) == 0)) {
                    flags &= ~AudioManager.FLAG_SHOW_UI;
                }
            }
        }
        return flags;
    }

    // UI update and Broadcast Intent
    private void sendMasterMuteUpdate(boolean muted, int flags) {
        mVolumeController.postMasterMuteChanged(updateFlagsForSystemAudio(flags));
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
                                    boolean force,
                                    String caller) {
        VolumeStreamState streamState = mStreamStates[streamType];

        if (streamState.setIndex(index, device, caller) || force) {
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

    private void setSystemAudioMute(boolean state) {
        if (mHdmiManager == null || mHdmiTvClient == null) return;
        synchronized (mHdmiManager) {
            if (!mHdmiSystemAudioSupported) return;
            synchronized (mHdmiTvClient) {
                final long token = Binder.clearCallingIdentity();
                try {
                    mHdmiTvClient.setSystemAudioMute(state);
                } finally {
                    Binder.restoreCallingIdentity(token);
                }
            }
        }
    }

    /** get stream mute state. */
    public boolean isStreamMute(int streamType) {
        if (streamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
            streamType = getActiveStreamType(streamType);
        }
        synchronized (VolumeStreamState.class) {
            return mStreamStates[streamType].mIsMuted;
        }
    }

    private class RmtSbmxFullVolDeathHandler implements IBinder.DeathRecipient {
        private IBinder mICallback; // To be notified of client's death

        RmtSbmxFullVolDeathHandler(IBinder cb) {
            mICallback = cb;
            try {
                cb.linkToDeath(this, 0/*flags*/);
            } catch (RemoteException e) {
                Log.e(TAG, "can't link to death", e);
            }
        }

        boolean isHandlerFor(IBinder cb) {
            return mICallback.equals(cb);
        }

        void forget() {
            try {
                mICallback.unlinkToDeath(this, 0/*flags*/);
            } catch (NoSuchElementException e) {
                Log.e(TAG, "error unlinking to death", e);
            }
        }

        public void binderDied() {
            Log.w(TAG, "Recorder with remote submix at full volume died " + mICallback);
            forceRemoteSubmixFullVolume(false, mICallback);
        }
    }

    /**
     * call must be synchronized on mRmtSbmxFullVolDeathHandlers
     * @return true if there is a registered death handler, false otherwise */
    private boolean discardRmtSbmxFullVolDeathHandlerFor(IBinder cb) {
        Iterator<RmtSbmxFullVolDeathHandler> it = mRmtSbmxFullVolDeathHandlers.iterator();
        while (it.hasNext()) {
            final RmtSbmxFullVolDeathHandler handler = it.next();
            if (handler.isHandlerFor(cb)) {
                handler.forget();
                mRmtSbmxFullVolDeathHandlers.remove(handler);
                return true;
            }
        }
        return false;
    }

    /** call synchronized on mRmtSbmxFullVolDeathHandlers */
    private boolean hasRmtSbmxFullVolDeathHandlerFor(IBinder cb) {
        Iterator<RmtSbmxFullVolDeathHandler> it = mRmtSbmxFullVolDeathHandlers.iterator();
        while (it.hasNext()) {
            if (it.next().isHandlerFor(cb)) {
                return true;
            }
        }
        return false;
    }

    private int mRmtSbmxFullVolRefCount = 0;
    private ArrayList<RmtSbmxFullVolDeathHandler> mRmtSbmxFullVolDeathHandlers =
            new ArrayList<RmtSbmxFullVolDeathHandler>();

    public void forceRemoteSubmixFullVolume(boolean startForcing, IBinder cb) {
        if (cb == null) {
            return;
        }
        if ((PackageManager.PERMISSION_GRANTED != mContext.checkCallingOrSelfPermission(
                        android.Manifest.permission.CAPTURE_AUDIO_OUTPUT))) {
            Log.w(TAG, "Trying to call forceRemoteSubmixFullVolume() without CAPTURE_AUDIO_OUTPUT");
            return;
        }
        synchronized(mRmtSbmxFullVolDeathHandlers) {
            boolean applyRequired = false;
            if (startForcing) {
                if (!hasRmtSbmxFullVolDeathHandlerFor(cb)) {
                    mRmtSbmxFullVolDeathHandlers.add(new RmtSbmxFullVolDeathHandler(cb));
                    if (mRmtSbmxFullVolRefCount == 0) {
                        mFullVolumeDevices |= AudioSystem.DEVICE_OUT_REMOTE_SUBMIX;
                        mFixedVolumeDevices |= AudioSystem.DEVICE_OUT_REMOTE_SUBMIX;
                        applyRequired = true;
                    }
                    mRmtSbmxFullVolRefCount++;
                }
            } else {
                if (discardRmtSbmxFullVolDeathHandlerFor(cb) && (mRmtSbmxFullVolRefCount > 0)) {
                    mRmtSbmxFullVolRefCount--;
                    if (mRmtSbmxFullVolRefCount == 0) {
                        mFullVolumeDevices &= ~AudioSystem.DEVICE_OUT_REMOTE_SUBMIX;
                        mFixedVolumeDevices &= ~AudioSystem.DEVICE_OUT_REMOTE_SUBMIX;
                        applyRequired = true;
                    }
                }
            }
            if (applyRequired) {
                // Assumes only STREAM_MUSIC going through DEVICE_OUT_REMOTE_SUBMIX
                checkAllFixedVolumeDevices(AudioSystem.STREAM_MUSIC);
                mStreamStates[AudioSystem.STREAM_MUSIC].applyAllVolumes();
            }
        }
    }

    private void setMasterMuteInternal(boolean mute, int flags, String callingPackage, int uid,
            int userId) {
        // If we are being called by the system check for user we are going to change
        // so we handle user restrictions correctly.
        if (uid == android.os.Process.SYSTEM_UID) {
            uid = UserHandle.getUid(userId, UserHandle.getAppId(uid));
        }
        if (mAppOps.noteOp(AppOpsManager.OP_AUDIO_MASTER_VOLUME, uid, callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
            return;
        }
        if (userId != UserHandle.getCallingUserId() &&
                mContext.checkCallingOrSelfPermission(
                        android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (getCurrentUserId() == userId) {
            if (mute != AudioSystem.getMasterMute()) {
                setSystemAudioMute(mute);
                AudioSystem.setMasterMute(mute);
                // Post a persist master volume msg
                sendMsg(mAudioHandler, MSG_PERSIST_MASTER_VOLUME_MUTE, SENDMSG_REPLACE, mute ? 1
                        : 0, userId, null, PERSIST_DELAY);
                sendMasterMuteUpdate(mute, flags);

                Intent intent = new Intent(AudioManager.MASTER_MUTE_CHANGED_ACTION);
                intent.putExtra(AudioManager.EXTRA_MASTER_VOLUME_MUTED, mute);
                sendBroadcastToAll(intent);
            }
        } else {
            // If not the current user just persist the setting which will be loaded
            // on user switch.
            sendMsg(mAudioHandler, MSG_PERSIST_MASTER_VOLUME_MUTE, SENDMSG_REPLACE, mute ? 1
                    : 0, userId, null, PERSIST_DELAY);
        }
    }

    /** get master mute state. */
    public boolean isMasterMute() {
        return AudioSystem.getMasterMute();
    }

    public void setMasterMute(boolean mute, int flags, String callingPackage, int userId) {
        setMasterMuteInternal(mute, flags, callingPackage, Binder.getCallingUid(),
                userId);
    }

    /** @see AudioManager#getStreamVolume(int) */
    public int getStreamVolume(int streamType) {
        ensureValidStreamType(streamType);
        int device = getDeviceForStream(streamType);
        synchronized (VolumeStreamState.class) {
            int index = mStreamStates[streamType].getIndex(device);

            // by convention getStreamVolume() returns 0 when a stream is muted.
            if (mStreamStates[streamType].mIsMuted) {
                index = 0;
            }
            if (index != 0 && (mStreamVolumeAlias[streamType] == AudioSystem.STREAM_MUSIC) &&
                    (device & mFixedVolumeDevices) != 0) {
                index = mStreamStates[streamType].getMaxIndex();
            }
            return (index + 5) / 10;
        }
    }

    /** @see AudioManager#getStreamMaxVolume(int) */
    public int getStreamMaxVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (mStreamStates[streamType].getMaxIndex() + 5) / 10;
    }

    /** @see AudioManager#getStreamMinVolume(int) */
    public int getStreamMinVolume(int streamType) {
        ensureValidStreamType(streamType);
        return (mStreamStates[streamType].getMinIndex() + 5) / 10;
    }

    /** Get last audible volume before stream was muted. */
    public int getLastAudibleStreamVolume(int streamType) {
        ensureValidStreamType(streamType);
        int device = getDeviceForStream(streamType);
        return (mStreamStates[streamType].getIndex(device) + 5) / 10;
    }

    /** @see AudioManager#getUiSoundsStreamType()  */
    public int getUiSoundsStreamType() {
        return mStreamVolumeAlias[AudioSystem.STREAM_SYSTEM];
    }

    /** @see AudioManager#setMicrophoneMute(boolean, int) */
    public void setMicrophoneMute(boolean on, String callingPackage, int userId) {
        // If we are being called by the system check for user we are going to change
        // so we handle user restrictions correctly.
        int uid = Binder.getCallingUid();
        if (uid == android.os.Process.SYSTEM_UID) {
            uid = UserHandle.getUid(userId, UserHandle.getAppId(uid));
        }
        if (mAppOps.noteOp(AppOpsManager.OP_MUTE_MICROPHONE, uid, callingPackage)
                != AppOpsManager.MODE_ALLOWED) {
            return;
        }
        if (!checkAudioSettingsPermission("setMicrophoneMute()")) {
            return;
        }
        if (userId != UserHandle.getCallingUserId() &&
                mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // If mute is for current user actually mute, else just persist the setting
        // which will be loaded on user switch.
        if (getCurrentUserId() == userId) {
            AudioSystem.muteMicrophone(on);
        }
        // Post a persist microphone msg.
        sendMsg(mAudioHandler, MSG_PERSIST_MICROPHONE_MUTE, SENDMSG_REPLACE, on ? 1
                : 0, userId, null, PERSIST_DELAY);
    }

    @Override
    public int getRingerModeExternal() {
        synchronized(mSettingsLock) {
            return mRingerModeExternal;
        }
    }

    @Override
    public int getRingerModeInternal() {
        synchronized(mSettingsLock) {
            return mRingerMode;
        }
    }

    private void ensureValidRingerMode(int ringerMode) {
        if (!isValidRingerMode(ringerMode)) {
            throw new IllegalArgumentException("Bad ringer mode " + ringerMode);
        }
    }

    /** @see AudioManager#isValidRingerMode(int) */
    public boolean isValidRingerMode(int ringerMode) {
        return ringerMode >= 0 && ringerMode <= AudioManager.RINGER_MODE_MAX;
    }

    public void setRingerModeExternal(int ringerMode, String caller) {
        setRingerMode(ringerMode, caller, true /*external*/);
    }

    public void setRingerModeInternal(int ringerMode, String caller) {
        enforceVolumeController("setRingerModeInternal");
        setRingerMode(ringerMode, caller, false /*external*/);
    }

    private void setRingerMode(int ringerMode, String caller, boolean external) {
        if (mUseFixedVolume || isPlatformTelevision()) {
            return;
        }
        if (caller == null || caller.length() == 0) {
            throw new IllegalArgumentException("Bad caller: " + caller);
        }
        ensureValidRingerMode(ringerMode);
        if ((ringerMode == AudioManager.RINGER_MODE_VIBRATE) && !mHasVibrator) {
            ringerMode = AudioManager.RINGER_MODE_SILENT;
        }
        final long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mSettingsLock) {
                final int ringerModeInternal = getRingerModeInternal();
                final int ringerModeExternal = getRingerModeExternal();
                if (external) {
                    setRingerModeExt(ringerMode);
                    if (mRingerModeDelegate != null) {
                        ringerMode = mRingerModeDelegate.onSetRingerModeExternal(ringerModeExternal,
                                ringerMode, caller, ringerModeInternal, mVolumePolicy);
                    }
                    if (ringerMode != ringerModeInternal) {
                        setRingerModeInt(ringerMode, true /*persist*/);
                    }
                } else /*internal*/ {
                    if (ringerMode != ringerModeInternal) {
                        setRingerModeInt(ringerMode, true /*persist*/);
                    }
                    if (mRingerModeDelegate != null) {
                        ringerMode = mRingerModeDelegate.onSetRingerModeInternal(ringerModeInternal,
                                ringerMode, caller, ringerModeExternal, mVolumePolicy);
                    }
                    setRingerModeExt(ringerMode);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void setRingerModeExt(int ringerMode) {
        synchronized(mSettingsLock) {
            if (ringerMode == mRingerModeExternal) return;
            mRingerModeExternal = ringerMode;
        }
        // Send sticky broadcast
        broadcastRingerMode(AudioManager.RINGER_MODE_CHANGED_ACTION, ringerMode);
    }

    private void muteRingerModeStreams() {
        // Mute stream if not previously muted by ringer mode and ringer mode
        // is not RINGER_MODE_NORMAL and stream is affected by ringer mode.
        // Unmute stream if previously muted by ringer mode and ringer mode
        // is RINGER_MODE_NORMAL or stream is not affected by ringer mode.
        int numStreamTypes = AudioSystem.getNumStreamTypes();
        final boolean ringerModeMute = mRingerMode == AudioManager.RINGER_MODE_VIBRATE
                || mRingerMode == AudioManager.RINGER_MODE_SILENT;
        for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
            final boolean isMuted = isStreamMutedByRingerMode(streamType);
            final boolean shouldMute = ringerModeMute && isStreamAffectedByRingerMode(streamType);
            if (isMuted == shouldMute) continue;
            if (!shouldMute) {
                // unmute
                // ring and notifications volume should never be 0 when not silenced
                if (mStreamVolumeAlias[streamType] == AudioSystem.STREAM_RING) {
                    synchronized (VolumeStreamState.class) {
                        final VolumeStreamState vss = mStreamStates[streamType];
                        for (int i = 0; i < vss.mIndexMap.size(); i++) {
                            int device = vss.mIndexMap.keyAt(i);
                            int value = vss.mIndexMap.valueAt(i);
                            if (value == 0) {
                                vss.setIndex(10, device, TAG);
                            }
                        }
                        // Persist volume for stream ring when it is changed here
                      final int device = getDeviceForStream(streamType);
                      sendMsg(mAudioHandler,
                              MSG_PERSIST_VOLUME,
                              SENDMSG_QUEUE,
                              device,
                              0,
                              mStreamStates[streamType],
                              PERSIST_DELAY);
                    }
                }
                mStreamStates[streamType].mute(false);
                mRingerModeMutedStreams &= ~(1 << streamType);
            } else {
                // mute
                mStreamStates[streamType].mute(true);
                mRingerModeMutedStreams |= (1 << streamType);
            }
        }
    }

    private void setRingerModeInt(int ringerMode, boolean persist) {
        final boolean change;
        synchronized(mSettingsLock) {
            change = mRingerMode != ringerMode;
            mRingerMode = ringerMode;
        }

        muteRingerModeStreams();

        // Post a persist ringer mode msg
        if (persist) {
            sendMsg(mAudioHandler, MSG_PERSIST_RINGER_MODE,
                    SENDMSG_REPLACE, 0, 0, null, PERSIST_DELAY);
        }
        if (change) {
            // Send sticky broadcast
            broadcastRingerMode(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION, ringerMode);
        }
    }

    /** @see AudioManager#shouldVibrate(int) */
    public boolean shouldVibrate(int vibrateType) {
        if (!mHasVibrator) return false;

        switch (getVibrateSetting(vibrateType)) {

            case AudioManager.VIBRATE_SETTING_ON:
                return getRingerModeExternal() != AudioManager.RINGER_MODE_SILENT;

            case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
                return getRingerModeExternal() == AudioManager.RINGER_MODE_VIBRATE;

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

        mVibrateSetting = AudioSystem.getValueForVibrateSetting(mVibrateSetting, vibrateType,
                vibrateSetting);

        // Broadcast change
        broadcastVibrateSetting(vibrateType);

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
                    newModeOwnerPid = setModeInt(AudioSystem.MODE_NORMAL, mCb, mPid, TAG);
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
    public void setMode(int mode, IBinder cb, String callingPackage) {
        if (DEBUG_MODE) { Log.v(TAG, "setMode(mode=" + mode + ", callingPackage=" + callingPackage + ")"); }
        if (!checkAudioSettingsPermission("setMode()")) {
            return;
        }

        if ( (mode == AudioSystem.MODE_IN_CALL) &&
                (mContext.checkCallingOrSelfPermission(
                        android.Manifest.permission.MODIFY_PHONE_STATE)
                            != PackageManager.PERMISSION_GRANTED)) {
            Log.w(TAG, "MODIFY_PHONE_STATE Permission Denial: setMode(MODE_IN_CALL) from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
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
            newModeOwnerPid = setModeInt(mode, cb, Binder.getCallingPid(), callingPackage);
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
    private int setModeInt(int mode, IBinder cb, int pid, String caller) {
        if (DEBUG_MODE) { Log.v(TAG, "setModeInt(mode=" + mode + ", pid=" + pid + ", caller="
                + caller + ")"); }
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
                    if (DEBUG_MODE) {
                        Log.w(TAG, " using mode=" + mode + " instead due to death hdlr at pid="
                                + hdlr.mPid);
                    }
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
                    if (DEBUG_MODE) { Log.v(TAG, " mode successfully set to " + mode); }
                    mMode = mode;
                } else {
                    if (hdlr != null) {
                        mSetModeDeathHandlers.remove(hdlr);
                        cb.unlinkToDeath(hdlr, 0);
                    }
                    // force reading new top of mSetModeDeathHandlers stack
                    if (DEBUG_MODE) { Log.w(TAG, " mode set to MODE_NORMAL after phoneState pb"); }
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
            int device = getDeviceForStream(streamType);
            int index = mStreamStates[mStreamVolumeAlias[streamType]].getIndex(device);
            setStreamVolumeInt(mStreamVolumeAlias[streamType], index, device, true, caller);

            updateStreamVolumeAlias(true /*updateVolumes*/, caller);
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

    private static final int SOUND_EFFECTS_LOAD_TIMEOUT_MS = 5000;

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
        if (effectType >= AudioManager.NUM_SOUND_EFFECTS || effectType < 0) {
            Log.w(TAG, "AudioService effectType value " + effectType + " out of range");
            return;
        }

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
                    reply.wait(SOUND_EFFECTS_LOAD_TIMEOUT_MS);
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

            streamState.readSettings();
            synchronized (VolumeStreamState.class) {
                // unmute stream that was muted but is not affect by mute anymore
                if (streamState.mIsMuted && ((!isStreamAffectedByMute(streamType) &&
                        !isStreamMutedByRingerMode(streamType)) || mUseFixedVolume)) {
                    streamState.mIsMuted = false;
                }
            }
        }

        // apply new ringer mode before checking volume for alias streams so that streams
        // muted by ringer mode have the correct volume
        setRingerModeInt(getRingerModeInternal(), false);

        checkAllFixedVolumeDevices();
        checkAllAliasStreamVolumes();
        checkMuteAffectedStreams();

        synchronized (mSafeMediaVolumeState) {
            mMusicActiveMs = MathUtils.constrain(Settings.Secure.getIntForUser(mContentResolver,
                    Settings.Secure.UNSAFE_VOLUME_MUSIC_ACTIVE_MS, 0, UserHandle.USER_CURRENT),
                    0, UNSAFE_VOLUME_MUSIC_ACTIVE_MS_MAX);
            if (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE) {
                enforceSafeMediaVolume(TAG);
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
    public void setBluetoothScoOn(boolean on) {
        if (!checkAudioSettingsPermission("setBluetoothScoOn()")) {
            return;
        }
        setBluetoothScoOnInt(on);
    }

    public void setBluetoothScoOnInt(boolean on) {
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
    public void startBluetoothSco(IBinder cb, int targetSdkVersion) {
        int scoAudioMode =
                (targetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2) ?
                        SCO_MODE_VIRTUAL_CALL : SCO_MODE_UNDEFINED;
        startBluetoothScoInt(cb, scoAudioMode);
    }

    /** @see AudioManager#startBluetoothScoVirtualCall() */
    public void startBluetoothScoVirtualCall(IBinder cb) {
        startBluetoothScoInt(cb, SCO_MODE_VIRTUAL_CALL);
    }

    void startBluetoothScoInt(IBinder cb, int scoAudioMode){
        if (!checkAudioSettingsPermission("startBluetoothSco()") ||
                !mSystemReady) {
            return;
        }
        ScoClient client = getScoClient(cb, true);
        // The calling identity must be cleared before calling ScoClient.incCount().
        // inCount() calls requestScoState() which in turn can call BluetoothHeadset APIs
        // and this must be done on behalf of system server to make sure permissions are granted.
        // The caller identity must be cleared after getScoClient() because it is needed if a new
        // client is created.
        final long ident = Binder.clearCallingIdentity();
        client.incCount(scoAudioMode);
        Binder.restoreCallingIdentity(ident);
    }

    /** @see AudioManager#stopBluetoothSco() */
    public void stopBluetoothSco(IBinder cb){
        if (!checkAudioSettingsPermission("stopBluetoothSco()") ||
                !mSystemReady) {
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

        public void incCount(int scoAudioMode) {
            synchronized(mScoClients) {
                requestScoState(BluetoothHeadset.STATE_AUDIO_CONNECTED, scoAudioMode);
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

        private void requestScoState(int state, int scoAudioMode) {
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
                                mScoAudioMode = scoAudioMode;
                                if (scoAudioMode == SCO_MODE_UNDEFINED) {
                                    if (mBluetoothHeadsetDevice != null) {
                                        mScoAudioMode = new Integer(Settings.Global.getInt(
                                                                mContentResolver,
                                                                "bluetooth_sco_channel_"+
                                                                mBluetoothHeadsetDevice.getAddress(),
                                                                SCO_MODE_VIRTUAL_CALL));
                                        if (mScoAudioMode > SCO_MODE_MAX || mScoAudioMode < 0) {
                                            mScoAudioMode = SCO_MODE_VIRTUAL_CALL;
                                        }
                                    } else {
                                        mScoAudioMode = SCO_MODE_RAW;
                                    }
                                }
                                if (mBluetoothHeadset != null && mBluetoothHeadsetDevice != null) {
                                    boolean status = false;
                                    if (mScoAudioMode == SCO_MODE_RAW) {
                                        status = mBluetoothHeadset.connectAudio();
                                    } else if (mScoAudioMode == SCO_MODE_VIRTUAL_CALL) {
                                        status = mBluetoothHeadset.startScoUsingVirtualVoiceCall(
                                                                            mBluetoothHeadsetDevice);
                                    } else if (mScoAudioMode == SCO_MODE_VR) {
                                        status = mBluetoothHeadset.startVoiceRecognition(
                                                                           mBluetoothHeadsetDevice);
                                    }

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
                            boolean status = false;
                            if (mScoAudioMode == SCO_MODE_RAW) {
                                status = mBluetoothHeadset.disconnectAudio();
                            } else if (mScoAudioMode == SCO_MODE_VIRTUAL_CALL) {
                                status = mBluetoothHeadset.stopScoUsingVirtualVoiceCall(
                                                                        mBluetoothHeadsetDevice);
                            } else if (mScoAudioMode == SCO_MODE_VR) {
                                        status = mBluetoothHeadset.stopVoiceRecognition(
                                                                      mBluetoothHeadsetDevice);
                            }

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
        AudioSystem.setParameters("A2dpSuspended=false");
        setBluetoothScoOnInt(false);
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

    void setBtScoDeviceConnectionState(BluetoothDevice btDevice, int state) {
        if (btDevice == null) {
            return;
        }

        String address = btDevice.getAddress();
        BluetoothClass btClass = btDevice.getBluetoothClass();
        int outDevice = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO;
        int inDevice = AudioSystem.DEVICE_IN_BLUETOOTH_SCO_HEADSET;
        if (btClass != null) {
            switch (btClass.getDeviceClass()) {
            case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                outDevice = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_HEADSET;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                outDevice = AudioSystem.DEVICE_OUT_BLUETOOTH_SCO_CARKIT;
                break;
            }
        }

        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }

        boolean connected = (state == BluetoothProfile.STATE_CONNECTED);

        String btDeviceName =  btDevice.getName();
        boolean success =
            handleDeviceConnection(connected, outDevice, address, btDeviceName) &&
            handleDeviceConnection(connected, inDevice, address, btDeviceName);
        if (success) {
            synchronized (mScoClients) {
                if (connected) {
                    mBluetoothHeadsetDevice = btDevice;
                } else {
                    mBluetoothHeadsetDevice = null;
                    resetBluetoothSco();
                }
            }
        }
    }

    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
        new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            BluetoothDevice btDevice;
            List<BluetoothDevice> deviceList;
            switch(profile) {
            case BluetoothProfile.A2DP:
                synchronized (mConnectedDevices) {
                    synchronized (mA2dpAvrcpLock) {
                        mA2dp = (BluetoothA2dp) proxy;
                        if (mConnectedBTDevicesList.size() > 0) {
                            Log.d(TAG,"A2dp connection list not empty, purge it, size " +
                                    mConnectedBTDevicesList.size());
                            mConnectedBTDevicesList.clear();
                        }
                        //In Dual A2dp, we can have two devices connected
                        deviceList = mA2dp.getConnectedDevices();
                        Log.d(TAG, "onServiceConnected: A2dp Service connected: " +
                                deviceList.size());
                        for (int i = 0; i < deviceList.size(); i++) {
                            //Add the device in Connected list
                            btDevice = deviceList.get(i);
                            int state = mA2dp.getConnectionState(btDevice);
                            int delay = checkSendBecomingNoisyIntent(
                                                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                                                (state == BluetoothA2dp.STATE_CONNECTED) ? 1 : 0);
                            queueMsgUnderWakeLock(mAudioHandler,
                                    MSG_SET_A2DP_SINK_CONNECTION_STATE,
                                    state,
                                    0,
                                    btDevice,
                                    delay);
                        }
                    }
                }
                break;

            case BluetoothProfile.A2DP_SINK:
                deviceList = proxy.getConnectedDevices();
                if (deviceList.size() > 0) {
                    btDevice = deviceList.get(0);
                    synchronized (mConnectedDevices) {
                        int state = proxy.getConnectionState(btDevice);
                        queueMsgUnderWakeLock(mAudioHandler,
                                MSG_SET_A2DP_SRC_CONNECTION_STATE,
                                state,
                                0,
                                btDevice,
                                0 /* delay */);
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
                                if (mScoAudioMode == SCO_MODE_RAW) {
                                    status = mBluetoothHeadset.connectAudio();
                                } else if (mScoAudioMode == SCO_MODE_VIRTUAL_CALL) {
                                    status = mBluetoothHeadset.startScoUsingVirtualVoiceCall(
                                                                        mBluetoothHeadsetDevice);
                                } else if (mScoAudioMode == SCO_MODE_VR) {
                                    status = mBluetoothHeadset.startVoiceRecognition(
                                                                      mBluetoothHeadsetDevice);
                                }
                                break;
                            case SCO_STATE_DEACTIVATE_REQ:
                                if (mScoAudioMode == SCO_MODE_RAW) {
                                    status = mBluetoothHeadset.disconnectAudio();
                                } else if (mScoAudioMode == SCO_MODE_VIRTUAL_CALL) {
                                    status = mBluetoothHeadset.stopScoUsingVirtualVoiceCall(
                                                                        mBluetoothHeadsetDevice);
                                } else if (mScoAudioMode == SCO_MODE_VR) {
                                    status = mBluetoothHeadset.stopVoiceRecognition(
                                                                      mBluetoothHeadsetDevice);
                                }
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
            ArraySet<String> toRemove = null;
            switch (profile) {
            case BluetoothProfile.A2DP:
                synchronized (mConnectedDevices) {
                    synchronized (mA2dpAvrcpLock) {
                        Log.d(TAG,"mConnectedBTDevicesList size " + mConnectedBTDevicesList.size());
                        if (mConnectedBTDevicesList.size() > 0) {
                            mConnectedBTDevicesList.clear();
                        }
                        // Disconnect ALL DEVICE_OUT_BLUETOOTH_A2DP devices
                        for (int i = 0; i < mConnectedDevices.size(); i++) {
                            DeviceListSpec deviceSpec = mConnectedDevices.valueAt(i);
                            if (deviceSpec.mDeviceType == AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP) {
                                toRemove = toRemove != null ? toRemove : new ArraySet<String>();
                                toRemove.add(deviceSpec.mDeviceAddress);
                            }
                        }
                        if (toRemove != null) {
                            int delay = checkSendBecomingNoisyIntent(
                                                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                                                0);
                            for (int i = 0; i < toRemove.size(); i++) {
                                makeA2dpDeviceUnavailableLater(toRemove.valueAt(i), delay);
                            }
                        }
                    }
                }
                break;

            case BluetoothProfile.A2DP_SINK:
                synchronized (mConnectedDevices) {
                    // Disconnect ALL DEVICE_IN_BLUETOOTH_A2DP devices
                    for(int i = 0; i < mConnectedDevices.size(); i++) {
                        DeviceListSpec deviceSpec = mConnectedDevices.valueAt(i);
                        if (deviceSpec.mDeviceType == AudioSystem.DEVICE_IN_BLUETOOTH_A2DP) {
                            toRemove = toRemove != null ? toRemove : new ArraySet<String>();
                            toRemove.add(deviceSpec.mDeviceAddress);
                        }
                    }
                    if (toRemove != null) {
                        for (int i = 0; i < toRemove.size(); i++) {
                            makeA2dpSrcUnavailable(toRemove.valueAt(i));
                        }
                    }
                }
                break;

            case BluetoothProfile.HEADSET:
                synchronized (mScoClients) {
                    if (mBluetoothHeadsetDevice != null) {
                        setBtScoDeviceConnectionState(mBluetoothHeadsetDevice,
                                BluetoothProfile.STATE_DISCONNECTED);
                    }
                    mBluetoothHeadset = null;
                }
                break;

            default:
                break;
            }
        }
    };

    private void onCheckMusicActive(String caller) {
        synchronized (mSafeMediaVolumeState) {
            if (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_INACTIVE) {
                int device = getDeviceForStream(AudioSystem.STREAM_MUSIC);

                if ((device & mSafeMediaVolumeDevices) != 0) {
                    sendMsg(mAudioHandler,
                            MSG_CHECK_MUSIC_ACTIVE,
                            SENDMSG_REPLACE,
                            0,
                            0,
                            caller,
                            MUSIC_ACTIVE_POLL_PERIOD_MS);
                    int index = mStreamStates[AudioSystem.STREAM_MUSIC].getIndex(device);
                    if (AudioSystem.isStreamActive(AudioSystem.STREAM_MUSIC, 0) &&
                            (index > mSafeMediaVolumeIndex)) {
                        // Approximate cumulative active music time
                        mMusicActiveMs += MUSIC_ACTIVE_POLL_PERIOD_MS;
                        if (mMusicActiveMs > UNSAFE_VOLUME_MUSIC_ACTIVE_MS_MAX) {
                            setSafeMediaVolumeEnabled(true, caller);
                            mMusicActiveMs = 0;
                        }
                        saveMusicActiveMs();
                    }
                }
            }
        }
    }

    private void saveMusicActiveMs() {
        mAudioHandler.obtainMessage(MSG_PERSIST_MUSIC_ACTIVE_MS, mMusicActiveMs, 0).sendToTarget();
    }

    private void onConfigureSafeVolume(boolean force, String caller) {
        synchronized (mSafeMediaVolumeState) {
            int mcc = mContext.getResources().getConfiguration().mcc;
            if ((mMcc != mcc) || ((mMcc == 0) && force)) {
                mSafeMediaVolumeIndex = mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_safe_media_volume_index) * 10;
                boolean safeMediaVolumeEnabled =
                        SystemProperties.getBoolean("audio.safemedia.force", false)
                        || mContext.getResources().getBoolean(
                                com.android.internal.R.bool.config_safe_media_volume_enabled);

                boolean safeMediaVolumeBypass =
                        SystemProperties.getBoolean("audio.safemedia.bypass", false);

                // The persisted state is either "disabled" or "active": this is the state applied
                // next time we boot and cannot be "inactive"
                int persistedState;
                if (safeMediaVolumeEnabled && !safeMediaVolumeBypass) {
                    persistedState = SAFE_MEDIA_VOLUME_ACTIVE;
                    // The state can already be "inactive" here if the user has forced it before
                    // the 30 seconds timeout for forced configuration. In this case we don't reset
                    // it to "active".
                    if (mSafeMediaVolumeState != SAFE_MEDIA_VOLUME_INACTIVE) {
                        if (mMusicActiveMs == 0) {
                            mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_ACTIVE;
                            enforceSafeMediaVolume(caller);
                        } else {
                            // We have existing playback time recorded, already confirmed.
                            mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_INACTIVE;
                        }
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
    private int checkForRingerModeChange(int oldIndex, int direction, int step, boolean isMuted) {
        final boolean isTv = mPlatformType == AudioSystem.PLATFORM_TELEVISION;
        int result = FLAG_ADJUST_VOLUME;
        int ringerMode = getRingerModeInternal();

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
                        mLoweredFromNormalToVibrateTime = SystemClock.uptimeMillis();
                    }
                } else {
                    if (oldIndex == step && mVolumePolicy.volumeDownToEnterSilent) {
                        ringerMode = RINGER_MODE_SILENT;
                    }
                }
            } else if (isTv && (direction == AudioManager.ADJUST_TOGGLE_MUTE
                    || direction == AudioManager.ADJUST_MUTE)) {
                if (mHasVibrator) {
                    ringerMode = RINGER_MODE_VIBRATE;
                } else {
                    ringerMode = RINGER_MODE_SILENT;
                }
                // Setting the ringer mode will toggle mute
                result &= ~FLAG_ADJUST_VOLUME;
            }
            break;
        case RINGER_MODE_VIBRATE:
            if (!mHasVibrator) {
                Log.e(TAG, "checkForRingerModeChange() current ringer mode is vibrate" +
                        "but no vibrator is present");
                break;
            }
            if ((direction == AudioManager.ADJUST_LOWER)) {
                // This is the case we were muted with the volume turned up
                if (isTv && oldIndex >= 2 * step && isMuted) {
                    ringerMode = RINGER_MODE_NORMAL;
                } else if (mPrevVolDirection != AudioManager.ADJUST_LOWER) {
                    if (mVolumePolicy.volumeDownToEnterSilent) {
                        final long diff = SystemClock.uptimeMillis()
                                - mLoweredFromNormalToVibrateTime;
                        if (diff > mVolumePolicy.vibrateToSilentDebounce
                                && mRingerModeDelegate.canVolumeDownEnterSilent()) {
                            ringerMode = RINGER_MODE_SILENT;
                        }
                    } else {
                        result |= AudioManager.FLAG_SHOW_VIBRATE_HINT;
                    }
                }
            } else if (direction == AudioManager.ADJUST_RAISE
                    || direction == AudioManager.ADJUST_TOGGLE_MUTE
                    || direction == AudioManager.ADJUST_UNMUTE) {
                ringerMode = RINGER_MODE_NORMAL;
            }
            result &= ~FLAG_ADJUST_VOLUME;
            break;
        case RINGER_MODE_SILENT:
            if (isTv && direction == AudioManager.ADJUST_LOWER && oldIndex >= 2 * step && isMuted) {
                // This is the case we were muted with the volume turned up
                ringerMode = RINGER_MODE_NORMAL;
            } else if (direction == AudioManager.ADJUST_RAISE
                    || direction == AudioManager.ADJUST_TOGGLE_MUTE
                    || direction == AudioManager.ADJUST_UNMUTE) {
                if (!mVolumePolicy.volumeUpToExitSilent) {
                    result |= AudioManager.FLAG_SHOW_SILENT_HINT;
                } else {
                  if (mHasVibrator && direction == AudioManager.ADJUST_RAISE) {
                      ringerMode = RINGER_MODE_VIBRATE;
                  } else {
                      // If we don't have a vibrator or they were toggling mute
                      // go straight back to normal.
                      ringerMode = RINGER_MODE_NORMAL;
                  }
                }
            }
            result &= ~FLAG_ADJUST_VOLUME;
            break;
        default:
            Log.e(TAG, "checkForRingerModeChange() wrong ringer mode: "+ringerMode);
            break;
        }

        setRingerMode(ringerMode, TAG + ".checkForRingerModeChange", false /*external*/);

        mPrevVolDirection = direction;

        return result;
    }

    @Override
    public boolean isStreamAffectedByRingerMode(int streamType) {
        return (mRingerModeAffectedStreams & (1 << streamType)) != 0;
    }

    private boolean isStreamMutedByRingerMode(int streamType) {
        return (mRingerModeMutedStreams & (1 << streamType)) != 0;
    }

    private boolean updateRingerModeAffectedStreams() {
        int ringerModeAffectedStreams = Settings.System.getIntForUser(mContentResolver,
                Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                ((1 << AudioSystem.STREAM_RING)|(1 << AudioSystem.STREAM_NOTIFICATION)|
                 (1 << AudioSystem.STREAM_SYSTEM)|(1 << AudioSystem.STREAM_SYSTEM_ENFORCED)),
                 UserHandle.USER_CURRENT);

        if (mPlatformType == AudioSystem.PLATFORM_TELEVISION) {
            ringerModeAffectedStreams = 0;
        } else if (mRingerModeDelegate != null) {
            ringerModeAffectedStreams = mRingerModeDelegate
                    .getRingerModeAffectedStreams(ringerModeAffectedStreams);
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

    @Override
    public boolean isStreamAffectedByMute(int streamType) {
        return (mMuteAffectedStreams & (1 << streamType)) != 0;
    }

    private void ensureValidDirection(int direction) {
        switch (direction) {
            case AudioManager.ADJUST_LOWER:
            case AudioManager.ADJUST_RAISE:
            case AudioManager.ADJUST_SAME:
            case AudioManager.ADJUST_MUTE:
            case AudioManager.ADJUST_UNMUTE:
            case AudioManager.ADJUST_TOGGLE_MUTE:
                break;
            default:
                throw new IllegalArgumentException("Bad direction " + direction);
        }
    }

    private void ensureValidStreamType(int streamType) {
        if (streamType < 0 || streamType >= mStreamStates.length) {
            throw new IllegalArgumentException("Bad stream type " + streamType);
        }
    }

    private boolean isMuteAdjust(int adjust) {
        return adjust == AudioManager.ADJUST_MUTE || adjust == AudioManager.ADJUST_UNMUTE
                || adjust == AudioManager.ADJUST_TOGGLE_MUTE;
    }

    private boolean isInCommunication() {
        boolean IsInCall = false;

        TelecomManager telecomManager =
                (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        final long ident = Binder.clearCallingIdentity();
        IsInCall = telecomManager.isInCall();
        Binder.restoreCallingIdentity(ident);

        return (IsInCall || getMode() == AudioManager.MODE_IN_COMMUNICATION);
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
        switch (mPlatformType) {
        case AudioSystem.PLATFORM_VOICE:
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
                if (isAfMusicActiveRecently(StreamOverride.sDelayMs)) {
                    if (DEBUG_VOL)
                        Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC stream active");
                    return AudioSystem.STREAM_MUSIC;
                } else {
                    if (mVolumeKeysControlRingStream) {
                        if (DEBUG_VOL)
                            Log.v(TAG, "getActiveStreamType: Forcing STREAM_RING b/c default");
                        return AudioSystem.STREAM_RING;
                    } else {
                        if (DEBUG_VOL)
                            Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC b/c default");
                        return AudioSystem.STREAM_MUSIC;
                    }
                }
            } else if (isAfMusicActiveRecently(0)) {
                if (DEBUG_VOL)
                    Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC stream active");
                return AudioSystem.STREAM_MUSIC;
            }
            break;
        case AudioSystem.PLATFORM_TELEVISION:
            if (suggestedStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                    // TV always defaults to STREAM_MUSIC
                    return AudioSystem.STREAM_MUSIC;
            }
            break;
        default:
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
                    StreamOverride.sDelayMs) ||
                    AudioSystem.isStreamActive(AudioSystem.STREAM_RING,
                            StreamOverride.sDelayMs)) {
                if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: Forcing STREAM_NOTIFICATION");
                return AudioSystem.STREAM_NOTIFICATION;
            } else if (suggestedStreamType == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                if (isAfMusicActiveRecently(StreamOverride.sDelayMs)) {
                    if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: forcing STREAM_MUSIC");
                    return AudioSystem.STREAM_MUSIC;
                } else {
                    if (mVolumeKeysControlRingStream) {
                        if (DEBUG_VOL)
                            Log.v(TAG,
                                    "getActiveStreamType: using STREAM_NOTIFICATION as default");
                        return AudioSystem.STREAM_NOTIFICATION;
                    } else {
                        if (DEBUG_VOL)
                            Log.v(TAG, "getActiveStreamType: Forcing STREAM_MUSIC b/c default");
                        return AudioSystem.STREAM_MUSIC;
                    }
                }
            }
            break;
        }
        if (DEBUG_VOL) Log.v(TAG, "getActiveStreamType: Returning suggested type "
                + suggestedStreamType);
        return suggestedStreamType;
    }

    private void broadcastRingerMode(String action, int ringerMode) {
        // Send sticky broadcast
        Intent broadcast = new Intent(action);
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
        synchronized (mLastDeviceConnectMsgTime) {
            long time = SystemClock.uptimeMillis() + delay;
            handler.sendMessageAtTime(handler.obtainMessage(msg, arg1, arg2, obj), time);
            if (msg == MSG_SET_WIRED_DEVICE_CONNECTION_STATE ||
                    msg == MSG_SET_A2DP_SRC_CONNECTION_STATE ||
                    msg == MSG_SET_A2DP_SINK_CONNECTION_STATE) {
                mLastDeviceConnectMsgTime = time;
            }
        }
    }

    boolean checkAudioSettingsPermission(String method) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
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
        int device = getDevicesForStream(stream);
        if ((device & (device - 1)) != 0) {
            // Multiple device selection is either:
            //  - speaker + one other device: give priority to speaker in this case.
            //  - one A2DP device + another device: happens with duplicated output. In this case
            // retain the device on the A2DP output as the other must not correspond to an active
            // selection if not the speaker.
            //  - HDMI-CEC system audio mode only output: give priority to available item in order.
            if ((device & AudioSystem.DEVICE_OUT_SPEAKER) != 0) {
                device = AudioSystem.DEVICE_OUT_SPEAKER;
            } else if ((device & AudioSystem.DEVICE_OUT_HDMI_ARC) != 0) {
                device = AudioSystem.DEVICE_OUT_HDMI_ARC;
            } else if ((device & AudioSystem.DEVICE_OUT_SPDIF) != 0) {
                device = AudioSystem.DEVICE_OUT_SPDIF;
            } else if ((device & AudioSystem.DEVICE_OUT_AUX_LINE) != 0) {
                device = AudioSystem.DEVICE_OUT_AUX_LINE;
            } else {
                device &= AudioSystem.DEVICE_OUT_ALL_A2DP;
            }
        }
        return device;
    }

    private int getDevicesForStream(int stream) {
        return getDevicesForStream(stream, true /*checkOthers*/);
    }

    private int getDevicesForStream(int stream, boolean checkOthers) {
        ensureValidStreamType(stream);
        synchronized (VolumeStreamState.class) {
            return mStreamStates[stream].observeDevicesForStream_syncVSS(checkOthers);
        }
    }

    private void observeDevicesForStreams(int skipStream) {
        synchronized (VolumeStreamState.class) {
            for (int stream = 0; stream < mStreamStates.length; stream++) {
                if (stream != skipStream) {
                    mStreamStates[stream].observeDevicesForStream_syncVSS(false /*checkOthers*/);
                }
            }
        }
    }

    /*
     * A class just for packaging up a set of connection parameters.
     */
    private class WiredDeviceConnectionState {
        public final int mType;
        public final int mState;
        public final String mAddress;
        public final String mName;
        public final String mCaller;

        public WiredDeviceConnectionState(int type, int state, String address, String name,
                String caller) {
            mType = type;
            mState = state;
            mAddress = address;
            mName = name;
            mCaller = caller;
        }
    }

    public void setWiredDeviceConnectionState(int type, int state, String address, String name,
            String caller) {
        synchronized (mConnectedDevices) {
            if (DEBUG_DEVICES) {
                Slog.i(TAG, "setWiredDeviceConnectionState(" + state + " nm: " + name + " addr:"
                        + address + ")");
            }
            int delay = checkSendBecomingNoisyIntent(type, state);
            queueMsgUnderWakeLock(mAudioHandler,
                    MSG_SET_WIRED_DEVICE_CONNECTION_STATE,
                    0,
                    0,
                    new WiredDeviceConnectionState(type, state, address, name, caller),
                    delay);
        }
    }

    public int setBluetoothA2dpDeviceConnectionState(BluetoothDevice device, int state, int profile)
    {
        int delay = 0;
        if (profile != BluetoothProfile.A2DP && profile != BluetoothProfile.A2DP_SINK) {
            throw new IllegalArgumentException("invalid profile " + profile);
        }
        /*check the state of the currnt device*/
        if (state == BluetoothA2dp.STATE_CONNECTING) {
            Log.d(TAG, "Device is still connecting ");
            return delay;
        }
        if ((mConnectedBTDevicesList.contains(device) &&
            (state == BluetoothA2dp.STATE_CONNECTED))) {
            Log.d(TAG, "Device conn is updated again, ignore ");
            return delay;
        }
        if (!mConnectedBTDevicesList.contains(device) &&
            (state == BluetoothA2dp.STATE_CONNECTED)) {
            /*add the device in the list*/
            Log.d(TAG, "Add new connected device in the list: " + device);
            mConnectedBTDevicesList.add(device);
            if (mConnectedBTDevicesList.size() > 1) {
                Log.d(TAG, "Second device connected, add new device ");
                return delay;
            }
        } else if ((state == BluetoothA2dp.STATE_DISCONNECTED) ||
            (state == BluetoothA2dp.STATE_DISCONNECTING)) {
            Log.d(TAG, "Device is getting disconnected: " + device);
            if (mConnectedBTDevicesList.contains(device)) {
                Log.d(TAG, "Remove the BT device ");
                mConnectedBTDevicesList.remove(device);
            }
            if (mConnectedBTDevicesList.size() > 0) {
                Log.d(TAG, "Not all are disconnected ");
                return delay;
            }
        }
        synchronized (mConnectedDevices) {
            if (profile == BluetoothProfile.A2DP) {
                delay = checkSendBecomingNoisyIntent(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                                                (state == BluetoothA2dp.STATE_CONNECTED) ? 1 : 0);
            } else {
                delay = 0;
            }
            queueMsgUnderWakeLock(mAudioHandler,
                    (profile == BluetoothProfile.A2DP ?
                        MSG_SET_A2DP_SINK_CONNECTION_STATE : MSG_SET_A2DP_SRC_CONNECTION_STATE),
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

    // NOTE: Locking order for synchronized objects related to volume or ringer mode management:
    //  1 mScoclient OR mSafeMediaVolumeState
    //  2   mSetModeDeathHandlers
    //  3     mSettingsLock
    //  4       VolumeStreamState.class
    //  5         mCameraSoundForced
    public class VolumeStreamState {
        private final int mStreamType;
        private final int mIndexMin;
        private final int mIndexMax;

        private boolean mIsMuted;
        private String mVolumeIndexSettingName;
        private int mObservedDevices;

        private final SparseIntArray mIndexMap = new SparseIntArray(8);
        private final Intent mVolumeChanged;
        private final Intent mStreamDevicesChanged;

        private VolumeStreamState(String settingName, int streamType) {

            mVolumeIndexSettingName = settingName;

            mStreamType = streamType;
            mIndexMin = MIN_STREAM_VOLUME[streamType] * 10;
            mIndexMax = MAX_STREAM_VOLUME[streamType] * 10;
            AudioSystem.initStreamVolume(streamType, mIndexMin / 10, mIndexMax / 10);

            readSettings();
            mVolumeChanged = new Intent(AudioManager.VOLUME_CHANGED_ACTION);
            mVolumeChanged.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, mStreamType);
            mStreamDevicesChanged = new Intent(AudioManager.STREAM_DEVICES_CHANGED_ACTION);
            mStreamDevicesChanged.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, mStreamType);
        }

        public int observeDevicesForStream_syncVSS(boolean checkOthers) {
            final int devices = AudioSystem.getDevicesForStream(mStreamType);
            if (devices == mObservedDevices) {
                return devices;
            }
            final int prevDevices = mObservedDevices;
            mObservedDevices = devices;
            if (checkOthers) {
                // one stream's devices have changed, check the others
                observeDevicesForStreams(mStreamType);
            }
            // log base stream changes to the event log
            if (mStreamVolumeAlias[mStreamType] == mStreamType) {
                EventLogTags.writeStreamDevicesChanged(mStreamType, prevDevices, devices);
            }
            sendBroadcastToAll(mStreamDevicesChanged
                    .putExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_DEVICES, prevDevices)
                    .putExtra(AudioManager.EXTRA_VOLUME_STREAM_DEVICES, devices));
            return devices;
        }

        public String getSettingNameForDevice(int device) {
            String name = mVolumeIndexSettingName;
            String suffix = AudioSystem.getOutputDeviceName(device);
            if (suffix.isEmpty()) {
                return name;
            }
            return name + "_" + suffix;
        }

        public void readSettings() {
            synchronized (VolumeStreamState.class) {
                // force maximum volume on all streams if fixed volume property is set
                if (mUseFixedVolume) {
                    mIndexMap.put(AudioSystem.DEVICE_OUT_DEFAULT, mIndexMax);
                    return;
                }
                // do not read system stream volume from settings: this stream is always aliased
                // to another stream type and its volume is never persisted. Values in settings can
                // only be stale values
                if ((mStreamType == AudioSystem.STREAM_SYSTEM) ||
                        (mStreamType == AudioSystem.STREAM_SYSTEM_ENFORCED)) {
                    int index = 10 * AudioSystem.DEFAULT_STREAM_VOLUME[mStreamType];
                    synchronized (mCameraSoundForced) {
                        if (mCameraSoundForced) {
                            index = mIndexMax;
                        }
                    }
                    mIndexMap.put(AudioSystem.DEVICE_OUT_DEFAULT, index);
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
                            AudioSystem.DEFAULT_STREAM_VOLUME[mStreamType] : -1;
                    int index = Settings.System.getIntForUser(
                            mContentResolver, name, defaultIndex, UserHandle.USER_CURRENT);
                    if (index == -1) {
                        continue;
                    }

                    mIndexMap.put(device, getValidIndex(10 * index));
                }
            }
        }

        // must be called while synchronized VolumeStreamState.class
        public void applyDeviceVolume_syncVSS(int device) {
            int index;
            if (mIsMuted) {
                index = 0;
            } else if ((device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 && mAvrcpAbsVolSupported) {
                /* Special handling for Bluetooth Absolute Volume scenario
                 * If we send full audio gain, some accessories are too loud even at its lowest
                 * volume. We are not able to enumerate all such accessories, so here is the
                 * workaround from phone side.
                 * For the lowest volume steps 1 and 2, restrict audio gain to 50% and 75%.
                 * For volume step 0, set audio gain to 0 as some accessories won't mute on their end.
                 */
                int i = (getIndex(device) + 5)/10;
                if (i == 0) {
                    // 0% for volume 0
                    index = 0;
                } else if (i == 1) {
                    // 50% for volume 1
                    index = (int)(mIndexMax * 0.5) /10;
                } else if (i == 2) {
                    // 75% for volume 2
                    index = (int)(mIndexMax * 0.75) /10;
                } else {
                    // otherwise, full gain
                    index = (mIndexMax + 5)/10;
                }
            } else if ((device & mFullVolumeDevices) != 0) {
                index = (mIndexMax + 5)/10;
            } else {
                index = (getIndex(device) + 5)/10;
            }
            AudioSystem.setStreamVolumeIndex(mStreamType, index, device);
        }

        public void applyAllVolumes() {
            synchronized (VolumeStreamState.class) {
                // apply default volume first: by convention this will reset all
                // devices volumes in audio policy manager to the supplied value
                int index;
                if (mIsMuted) {
                    index = 0;
                } else {
                    index = (getIndex(AudioSystem.DEVICE_OUT_DEFAULT) + 5)/10;
                }
                AudioSystem.setStreamVolumeIndex(mStreamType, index, AudioSystem.DEVICE_OUT_DEFAULT);
                // then apply device specific volumes
                for (int i = 0; i < mIndexMap.size(); i++) {
                    int device = mIndexMap.keyAt(i);
                    if (device != AudioSystem.DEVICE_OUT_DEFAULT) {
                        if (mIsMuted) {
                            index = 0;
                        } else if (((device & AudioSystem.DEVICE_OUT_ALL_A2DP) != 0 &&
                                mAvrcpAbsVolSupported)
                                    || ((device & mFullVolumeDevices) != 0))
                        {
                            index = (mIndexMax + 5)/10;
                        } else {
                            index = (mIndexMap.valueAt(i) + 5)/10;
                        }
                        AudioSystem.setStreamVolumeIndex(mStreamType, index, device);
                    }
                }
            }
        }

        public boolean adjustIndex(int deltaIndex, int device, String caller) {
            return setIndex(getIndex(device) + deltaIndex, device, caller);
        }

        public boolean setIndex(int index, int device, String caller) {
            boolean changed = false;
            int oldIndex;
            synchronized (VolumeStreamState.class) {
                oldIndex = getIndex(device);
                index = getValidIndex(index);
                synchronized (mCameraSoundForced) {
                    if ((mStreamType == AudioSystem.STREAM_SYSTEM_ENFORCED) && mCameraSoundForced) {
                        index = mIndexMax;
                    }
                }
                mIndexMap.put(device, index);

                changed = oldIndex != index;
                if (changed) {
                    // Apply change to all streams using this one as alias
                    // if changing volume of current device, also change volume of current
                    // device on aliased stream
                    boolean currentDevice = (device == getDeviceForStream(mStreamType));
                    int numStreamTypes = AudioSystem.getNumStreamTypes();
                    for (int streamType = numStreamTypes - 1; streamType >= 0; streamType--) {
                        if (streamType != mStreamType &&
                                mStreamVolumeAlias[streamType] == mStreamType) {
                            int scaledIndex = rescaleIndex(index, mStreamType, streamType);
                            mStreamStates[streamType].setIndex(scaledIndex, device, caller);
                            if (currentDevice) {
                                mStreamStates[streamType].setIndex(scaledIndex,
                                        getDeviceForStream(streamType), caller);
                            }
                        }
                    }
                }
            }
            if (changed) {
                oldIndex = (oldIndex + 5) / 10;
                index = (index + 5) / 10;
                // log base stream changes to the event log
                if (mStreamVolumeAlias[mStreamType] == mStreamType) {
                    if (caller == null) {
                        Log.w(TAG, "No caller for volume_changed event", new Throwable());
                    }
                    EventLogTags.writeVolumeChanged(mStreamType, oldIndex, index, mIndexMax / 10,
                            caller);
                }
                // fire changed intents for all streams
                mVolumeChanged.putExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, index);
                mVolumeChanged.putExtra(AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, oldIndex);
                mVolumeChanged.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE_ALIAS,
                        mStreamVolumeAlias[mStreamType]);
                sendBroadcastToAll(mVolumeChanged);
            }
            return changed;
        }

        public int getIndex(int device) {
            synchronized (VolumeStreamState.class) {
                int index = mIndexMap.get(device, -1);
                if (index == -1) {
                    // there is always an entry for AudioSystem.DEVICE_OUT_DEFAULT
                    index = mIndexMap.get(AudioSystem.DEVICE_OUT_DEFAULT);
                }
                return index;
            }
        }

        public int getMaxIndex() {
            return mIndexMax;
        }

        public int getMinIndex() {
            return mIndexMin;
        }

        public void setAllIndexes(VolumeStreamState srcStream, String caller) {
            synchronized (VolumeStreamState.class) {
                int srcStreamType = srcStream.getStreamType();
                // apply default device volume from source stream to all devices first in case
                // some devices are present in this stream state but not in source stream state
                int index = srcStream.getIndex(AudioSystem.DEVICE_OUT_DEFAULT);
                index = rescaleIndex(index, srcStreamType, mStreamType);
                for (int i = 0; i < mIndexMap.size(); i++) {
                    mIndexMap.put(mIndexMap.keyAt(i), index);
                }
                // Now apply actual volume for devices in source stream state
                SparseIntArray srcMap = srcStream.mIndexMap;
                for (int i = 0; i < srcMap.size(); i++) {
                    int device = srcMap.keyAt(i);
                    index = srcMap.valueAt(i);
                    index = rescaleIndex(index, srcStreamType, mStreamType);

                    setIndex(index, device, caller);
                }
            }
        }

        public void setAllIndexesToMax() {
            synchronized (VolumeStreamState.class) {
                for (int i = 0; i < mIndexMap.size(); i++) {
                    mIndexMap.put(mIndexMap.keyAt(i), mIndexMax);
                }
            }
        }

        public void mute(boolean state) {
            boolean changed = false;
            synchronized (VolumeStreamState.class) {
                if (state != mIsMuted) {
                    changed = true;
                    mIsMuted = state;

                    // Set the new mute volume. This propagates the values to
                    // the audio system, otherwise the volume won't be changed
                    // at the lower level.
                    sendMsg(mAudioHandler,
                            MSG_SET_ALL_VOLUMES,
                            SENDMSG_QUEUE,
                            0,
                            0,
                            this, 0);
                }
            }
            if (changed) {
                // Stream mute changed, fire the intent.
                Intent intent = new Intent(AudioManager.STREAM_MUTE_CHANGED_ACTION);
                intent.putExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, mStreamType);
                intent.putExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, state);
                sendBroadcastToAll(intent);
            }
        }

        public int getStreamType() {
            return mStreamType;
        }

        public void checkFixedVolumeDevices() {
            synchronized (VolumeStreamState.class) {
                // ignore settings for fixed volume devices: volume should always be at max or 0
                if (mStreamVolumeAlias[mStreamType] == AudioSystem.STREAM_MUSIC) {
                    for (int i = 0; i < mIndexMap.size(); i++) {
                        int device = mIndexMap.keyAt(i);
                        int index = mIndexMap.valueAt(i);
                        if (((device & mFullVolumeDevices) != 0)
                                || (((device & mFixedVolumeDevices) != 0) && index != 0)) {
                            mIndexMap.put(device, mIndexMax);
                        }
                        applyDeviceVolume_syncVSS(device);
                    }
                }
            }
        }

        private int getValidIndex(int index) {
            if (index < mIndexMin) {
                return mIndexMin;
            } else if (mUseFixedVolume || index > mIndexMax) {
                return mIndexMax;
            }

            return index;
        }

        private void dump(PrintWriter pw) {
            pw.print("   Muted: ");
            pw.println(mIsMuted);
            pw.print("   Min: ");
            pw.println((mIndexMin + 5) / 10);
            pw.print("   Max: ");
            pw.println((mIndexMax + 5) / 10);
            pw.print("   Current: ");
            for (int i = 0; i < mIndexMap.size(); i++) {
                if (i > 0) {
                    pw.print(", ");
                }
                final int device = mIndexMap.keyAt(i);
                pw.print(Integer.toHexString(device));
                final String deviceName = device == AudioSystem.DEVICE_OUT_DEFAULT ? "default"
                        : AudioSystem.getOutputDeviceName(device);
                if (!deviceName.isEmpty()) {
                    pw.print(" (");
                    pw.print(deviceName);
                    pw.print(")");
                }
                pw.print(": ");
                final int index = (mIndexMap.valueAt(i) + 5) / 10;
                pw.print(index);
            }
            pw.println();
            pw.print("   Devices: ");
            final int devices = getDevicesForStream(mStreamType);
            int device, i = 0, n = 0;
            // iterate all devices from 1 to DEVICE_OUT_DEFAULT exclusive
            // (the default device is not returned by getDevicesForStream)
            while ((device = 1 << i) != AudioSystem.DEVICE_OUT_DEFAULT) {
                if ((devices & device) != 0) {
                    if (n++ > 0) {
                        pw.print(", ");
                    }
                    pw.print(AudioSystem.getOutputDeviceName(device));
                }
                i++;
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

            synchronized (VolumeStreamState.class) {
                // Apply volume
                streamState.applyDeviceVolume_syncVSS(device);

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
                            mStreamStates[streamType].applyDeviceVolume_syncVSS(device);
                        }
                        mStreamStates[streamType].applyDeviceVolume_syncVSS(streamDevice);
                    }
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
            if (isPlatformTelevision() && (streamState.mStreamType != AudioSystem.STREAM_MUSIC)) {
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
                if (!mSystemReady) {
                    Log.w(TAG, "onLoadSoundEffects() called before boot complete");
                    return false;
                }

                if (mSoundPool != null) {
                    return true;
                }

                loadTouchSoundAssets();

                mSoundPool = new SoundPool.Builder()
                        .setMaxStreams(NUM_SOUNDPOOL_CHANNELS)
                        .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                        .build();
                mSoundPoolCallBack = null;
                mSoundPoolListenerThread = new SoundPoolListenerThread();
                mSoundPoolListenerThread.start();
                int attempts = 3;
                while ((mSoundPoolCallBack == null) && (attempts-- > 0)) {
                    try {
                        // Wait for mSoundPoolCallBack to be set by the other thread
                        mSoundEffectsLock.wait(SOUND_EFFECTS_LOAD_TIMEOUT_MS);
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
                            mSoundEffectsLock.wait(SOUND_EFFECTS_LOAD_TIMEOUT_MS);
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
                    volFloat = volume / 1000.0f;
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
            synchronized (mConnectedDevices) {
                setForceUseInt_SyncDevices(usage, config);
            }
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

                case MSG_PERSIST_MASTER_VOLUME_MUTE:
                    if (mUseFixedVolume) {
                        return;
                    }
                    Settings.System.putIntForUser(mContentResolver,
                                                 Settings.System.VOLUME_MASTER_MUTE,
                                                 msg.arg1,
                                                 msg.arg2);
                    break;

                case MSG_PERSIST_RINGER_MODE:
                    // note that the value persisted is the current ringer mode, not the
                    // value of ringer mode as of the time the request was made to persist
                    persistRingerMode(getRingerModeInternal());
                    break;

                case MSG_MEDIA_SERVER_DIED:
                    onMediaServerDied();
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
                    if (isStreamMute(AudioSystem.STREAM_SYSTEM)) {
                        Log.d(TAG, "Stream muted, skip playback");
                    } else {
                        onPlaySoundEffect(msg.arg1, msg.arg2);
                    }
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
                    {   WiredDeviceConnectionState connectState =
                            (WiredDeviceConnectionState)msg.obj;
                        onSetWiredDeviceConnectionState(connectState.mType, connectState.mState,
                                connectState.mAddress, connectState.mName, connectState.mCaller);
                        mAudioEventWakeLock.release();
                    }
                    break;

                case MSG_SET_A2DP_SRC_CONNECTION_STATE:
                    onSetA2dpSourceConnectionState((BluetoothDevice)msg.obj, msg.arg1);
                    mAudioEventWakeLock.release();
                    break;

                case MSG_SET_A2DP_SINK_CONNECTION_STATE:
                    onSetA2dpSinkConnectionState((BluetoothDevice)msg.obj, msg.arg1);
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
                    observeDevicesForStreams(-1);
                    break;
                }

                case MSG_CHECK_MUSIC_ACTIVE:
                    onCheckMusicActive((String) msg.obj);
                    break;

                case MSG_BROADCAST_AUDIO_BECOMING_NOISY:
                    onSendBecomingNoisyIntent();
                    break;

                case MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED:
                case MSG_CONFIGURE_SAFE_MEDIA_VOLUME:
                    onConfigureSafeVolume((msg.what == MSG_CONFIGURE_SAFE_MEDIA_VOLUME_FORCED),
                            (String) msg.obj);
                    break;
                case MSG_PERSIST_SAFE_VOLUME_STATE:
                    onPersistSafeVolumeState(msg.arg1);
                    break;

                case MSG_BROADCAST_BT_CONNECTION_STATE:
                    onBroadcastScoConnectionState(msg.arg1);
                    break;

                case MSG_SYSTEM_READY:
                    onSystemReady();
                    break;

                case MSG_INDICATE_SYSTEM_READY:
                    onIndicateSystemReady();
                    break;

                case MSG_PERSIST_MUSIC_ACTIVE_MS:
                    final int musicActiveMs = msg.arg1;
                    Settings.Secure.putIntForUser(mContentResolver,
                            Settings.Secure.UNSAFE_VOLUME_MUSIC_ACTIVE_MS, musicActiveMs,
                            UserHandle.USER_CURRENT);
                    break;
                case MSG_PERSIST_MICROPHONE_MUTE:
                    Settings.System.putIntForUser(mContentResolver,
                                                 Settings.System.MICROPHONE_MUTE,
                                                 msg.arg1,
                                                 msg.arg2);
                    break;
                case MSG_UNMUTE_STREAM:
                    onUnmuteStream(msg.arg1, msg.arg2);
                    break;
                case MSG_DYN_POLICY_MIX_STATE_UPDATE:
                    onDynPolicyMixStateUpdate((String) msg.obj, msg.arg1);
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
            mContentResolver.registerContentObserver(Settings.Secure.getUriFor(
                Settings.Secure.VOLUME_LINK_NOTIFICATION), false, this);
            mContentResolver.registerContentObserver(CMSettings.System.getUriFor(
                CMSettings.System.VOLUME_KEYS_CONTROL_RING_STREAM), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // FIXME This synchronized is not necessary if mSettingsLock only protects mRingerMode.
            //       However there appear to be some missing locks around mRingerModeMutedStreams
            //       and mRingerModeAffectedStreams, so will leave this synchronized for now.
            //       mRingerModeMutedStreams and mMuteAffectedStreams are safe (only accessed once).
            synchronized (mSettingsLock) {
                if (updateRingerModeAffectedStreams()) {
                    /*
                     * Ensure all stream types that should be affected by ringer mode
                     * are in the proper state.
                     */
                    setRingerModeInt(getRingerModeInternal(), false);
                }
                readDockAudioSettings(mContentResolver);

                boolean linkNotificationWithVolume = Settings.Secure.getInt(mContentResolver,
                        Settings.Secure.VOLUME_LINK_NOTIFICATION, 1) == 1;
                if (linkNotificationWithVolume != mLinkNotificationWithVolume) {
                    mLinkNotificationWithVolume = linkNotificationWithVolume;
                    createStreamStates();
                    updateStreamVolumeAlias(true, TAG);
                }
                mVolumeKeysControlRingStream = CMSettings.System.getIntForUser(mContentResolver,
                        CMSettings.System.VOLUME_KEYS_CONTROL_RING_STREAM, 1,
                        UserHandle.USER_CURRENT) == 1;
            }
        }
    }

    // must be called synchronized on mConnectedDevices
    private void makeA2dpDeviceAvailable(String address, String name) {
        // enable A2DP before notifying A2DP connection to avoid unecessary processing in
        // audio policy manager
        VolumeStreamState streamState = mStreamStates[AudioSystem.STREAM_MUSIC];
        sendMsg(mAudioHandler, MSG_SET_DEVICE_VOLUME, SENDMSG_QUEUE,
                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, 0, streamState, 0);
        setBluetoothA2dpOnInt(true);
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_AVAILABLE, address, name);
        // Reset A2DP suspend state each time a new sink is connected
        AudioSystem.setParameters("A2dpSuspended=false");
        mConnectedDevices.put(
                makeDeviceListKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, address),
                new DeviceListSpec(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, name,
                                   address));
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
                AudioSystem.DEVICE_STATE_UNAVAILABLE, address, "");
        mConnectedDevices.remove(
                makeDeviceListKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, address));
        synchronized (mCurAudioRoutes) {
            // Remove A2DP routes as well
            if (mCurAudioRoutes.bluetoothName != null) {
                mCurAudioRoutes.bluetoothName = null;
                sendMsg(mAudioHandler, MSG_REPORT_NEW_ROUTES,
                        SENDMSG_NOOP, 0, 0, null, 0);
            }
        }
    }

    // must be called synchronized on mConnectedDevices
    private void makeA2dpDeviceUnavailableLater(String address, int delayMs) {
        // prevent any activity on the A2DP audio output to avoid unwanted
        // reconnection of the sink.
        AudioSystem.setParameters("A2dpSuspended=true");
        // the device will be made unavailable later, so consider it disconnected right away
        mConnectedDevices.remove(
                makeDeviceListKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, address));
        // send the delayed message to make the device unavailable later
        Message msg = mAudioHandler.obtainMessage(MSG_BTA2DP_DOCK_TIMEOUT, address);
        mAudioHandler.sendMessageDelayed(msg, delayMs);

    }

    // must be called synchronized on mConnectedDevices
    private void makeA2dpSrcAvailable(String address) {
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_IN_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_AVAILABLE, address, "");
        mConnectedDevices.put(
                makeDeviceListKey(AudioSystem.DEVICE_IN_BLUETOOTH_A2DP, address),
                new DeviceListSpec(AudioSystem.DEVICE_IN_BLUETOOTH_A2DP, "",
                                   address));
    }

    // must be called synchronized on mConnectedDevices
    private void makeA2dpSrcUnavailable(String address) {
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_IN_BLUETOOTH_A2DP,
                AudioSystem.DEVICE_STATE_UNAVAILABLE, address, "");
        mConnectedDevices.remove(
                makeDeviceListKey(AudioSystem.DEVICE_IN_BLUETOOTH_A2DP, address));
    }

    // must be called synchronized on mConnectedDevices
    private void cancelA2dpDeviceTimeout() {
        mAudioHandler.removeMessages(MSG_BTA2DP_DOCK_TIMEOUT);
    }

    // must be called synchronized on mConnectedDevices
    private boolean hasScheduledA2dpDockTimeout() {
        return mAudioHandler.hasMessages(MSG_BTA2DP_DOCK_TIMEOUT);
    }

    private void onSetA2dpSinkConnectionState(BluetoothDevice btDevice, int state)
    {
        if (DEBUG_VOL) {
            Log.d(TAG, "onSetA2dpSinkConnectionState btDevice="+btDevice+"state="+state);
        }
        if (btDevice == null) {
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }

        synchronized (mConnectedDevices) {
            String key = makeDeviceListKey(AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP,
                                           btDevice.getAddress());
            DeviceListSpec deviceSpec = mConnectedDevices.get(key);
            boolean isConnected = deviceSpec != null;

            if (isConnected && state != BluetoothProfile.STATE_CONNECTED) {
                if (btDevice.isBluetoothDock()) {
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        // introduction of a delay for transient disconnections of docks when
                        // power is rapidly turned off/on, this message will be canceled if
                        // we reconnect the dock under a preset delay
                        makeA2dpDeviceUnavailableLater(btDevice.getAddress(), BTA2DP_DOCK_TIMEOUT_MILLIS);
                        // the next time isConnected is evaluated, it will be false for the dock
                    }
                } else {
                    Log.d(TAG, "All devices are disconneted, update Policymanager ");
                    makeA2dpDeviceUnavailableNow(btDevice.getAddress());
                }
                synchronized (mCurAudioRoutes) {
                    if (mCurAudioRoutes.bluetoothName != null) {
                        mCurAudioRoutes.bluetoothName = null;
                        sendMsg(mAudioHandler, MSG_REPORT_NEW_ROUTES,
                                SENDMSG_NOOP, 0, 0, null, 0);
                    }
                }
            } else if (!isConnected && state == BluetoothProfile.STATE_CONNECTED) {
                //This function is not implemented
                mA2dpConnectedDevice = "BluetoothA2dp"; // Add this String
                if (btDevice.isBluetoothDock()) {
                    // this could be a reconnection after a transient disconnection
                    cancelA2dpDeviceTimeout();
                    mDockAddress =  mA2dpConnectedDevice;
                } else {
                    // this could be a connection of another A2DP device before the timeout of
                    // a dock: cancel the dock timeout, and make the dock unavailable now
                    if(hasScheduledA2dpDockTimeout()) {
                        cancelA2dpDeviceTimeout();
                        makeA2dpDeviceUnavailableNow(btDevice.getAddress());
                    }
                }
                makeA2dpDeviceAvailable(btDevice.getAddress(), btDevice.getName());
                //Updated the Router for a2dp device
                synchronized (mCurAudioRoutes) {
                    String name = mA2dpConnectedDevice;
                    if (!TextUtils.equals(mCurAudioRoutes.bluetoothName, name)) {
                        mCurAudioRoutes.bluetoothName = name;
                        sendMsg(mAudioHandler, MSG_REPORT_NEW_ROUTES,
                                SENDMSG_NOOP, 0, 0, null, 0);
                    }
                }
            }
        }
    }

    private void onSetA2dpSourceConnectionState(BluetoothDevice btDevice, int state)
    {
        if (DEBUG_VOL) {
            Log.d(TAG, "onSetA2dpSourceConnectionState btDevice="+btDevice+" state="+state);
        }
        if (btDevice == null) {
            Log.d(TAG, "onSetA2dpSourceConnectionState device is null"); //gasati
            return;
        }
        String address = btDevice.getAddress();
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            address = "";
        }

        synchronized (mConnectedDevices) {
            String key = makeDeviceListKey(AudioSystem.DEVICE_IN_BLUETOOTH_A2DP, address);
            DeviceListSpec deviceSpec = mConnectedDevices.get(key);
            boolean isConnected = deviceSpec != null;

            if (isConnected && state != BluetoothProfile.STATE_CONNECTED) {
                makeA2dpSrcUnavailable(address);
            } else if (!isConnected && state == BluetoothProfile.STATE_CONNECTED) {
                makeA2dpSrcAvailable(address);
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

    private boolean handleDeviceConnection(boolean connect, int device, String address,
            String deviceName) {
        if (DEBUG_DEVICES) {
            Slog.i(TAG, "handleDeviceConnection(" + connect + " dev:" + Integer.toHexString(device)
                    + " address:" + address + " name:" + deviceName + ")");
        }
        synchronized (mConnectedDevices) {
            String deviceKey = makeDeviceListKey(device, address);
            if (DEBUG_DEVICES) {
                Slog.i(TAG, "deviceKey:" + deviceKey);
            }
            DeviceListSpec deviceSpec = mConnectedDevices.get(deviceKey);
            boolean isConnected = deviceSpec != null;
            if (DEBUG_DEVICES) {
                Slog.i(TAG, "deviceSpec:" + deviceSpec + " is(already)Connected:" + isConnected);
            }
            if (connect && !isConnected) {
                final int res = AudioSystem.setDeviceConnectionState(device,
                        AudioSystem.DEVICE_STATE_AVAILABLE, address, deviceName);
                if (res != AudioSystem.AUDIO_STATUS_OK) {
                    Slog.e(TAG, "not connecting device 0x" + Integer.toHexString(device) +
                            " due to command error " + res );
                    return false;
                }
                mConnectedDevices.put(deviceKey, new DeviceListSpec(device, deviceName, address));
                return true;
            } else if (!connect && isConnected) {
                AudioSystem.setDeviceConnectionState(device,
                        AudioSystem.DEVICE_STATE_UNAVAILABLE, address, deviceName);
                // always remove even if disconnection failed
                mConnectedDevices.remove(deviceKey);
                return true;
            }
        }
        return false;
    }

    // Devices which removal triggers intent ACTION_AUDIO_BECOMING_NOISY. The intent is only
    // sent if none of these devices is connected.
    // Access synchronized on mConnectedDevices
    int mBecomingNoisyIntentDevices =
            AudioSystem.DEVICE_OUT_WIRED_HEADSET | AudioSystem.DEVICE_OUT_WIRED_HEADPHONE |
            AudioSystem.DEVICE_OUT_ALL_A2DP | AudioSystem.DEVICE_OUT_HDMI |
            AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET | AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET |
            AudioSystem.DEVICE_OUT_ALL_USB | AudioSystem.DEVICE_OUT_LINE;

    // must be called before removing the device from mConnectedDevices
    // Called synchronized on mConnectedDevices
    private int checkSendBecomingNoisyIntent(int device, int state) {
        int delay = 0;
        if (mConnectedBTDevicesList.size() > 1) {
            Log.d(TAG, "checkSendBecomingNoisyIntent on state: " + state);
            return delay;
        }

        if ((state == 0) && ((device & mBecomingNoisyIntentDevices) != 0)) {
            int devices = 0;
            Log.d(TAG, "checkSendBecomingNoisyIntent update the noise");
            for (int i = 0; i < mConnectedDevices.size(); i++) {
                int dev = mConnectedDevices.valueAt(i).mDeviceType;
                if (((dev & AudioSystem.DEVICE_BIT_IN) == 0)
                        && ((dev & mBecomingNoisyIntentDevices) != 0)) {
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

        if (mAudioHandler.hasMessages(MSG_SET_A2DP_SRC_CONNECTION_STATE) ||
                mAudioHandler.hasMessages(MSG_SET_A2DP_SINK_CONNECTION_STATE) ||
                mAudioHandler.hasMessages(MSG_SET_WIRED_DEVICE_CONNECTION_STATE)) {
            synchronized (mLastDeviceConnectMsgTime) {
                long time = SystemClock.uptimeMillis();
                if (mLastDeviceConnectMsgTime > time) {
                    delay = (int)(mLastDeviceConnectMsgTime - time) + 30;
                }
            }
        }
        return delay;
    }

    private void sendDeviceConnectionIntent(int device, int state, String address,
            String deviceName) {
        if (DEBUG_DEVICES) {
            Slog.i(TAG, "sendDeviceConnectionIntent(dev:0x" + Integer.toHexString(device) +
                    " state:0x" + Integer.toHexString(state) + " address:" + address +
                    " name:" + deviceName + ");");
        }
        Intent intent = new Intent();

        intent.putExtra(CONNECT_INTENT_KEY_STATE, state);
        intent.putExtra(CONNECT_INTENT_KEY_ADDRESS, address);
        intent.putExtra(CONNECT_INTENT_KEY_PORT_NAME, deviceName);

        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);

        int connType = 0;

        if (device == AudioSystem.DEVICE_OUT_WIRED_HEADSET) {
            connType = AudioRoutesInfo.MAIN_HEADSET;
            intent.setAction(Intent.ACTION_HEADSET_PLUG);
            intent.putExtra("microphone", 1);
            if (state == 1) {
                startMusicPlayer();
            }
        } else if (device == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE ||
                   device == AudioSystem.DEVICE_OUT_LINE) {
            /*do apps care about line-out vs headphones?*/
            connType = AudioRoutesInfo.MAIN_HEADPHONES;
            intent.setAction(Intent.ACTION_HEADSET_PLUG);
            intent.putExtra("microphone", 0);
            if (state == 1) {
                startMusicPlayer();
            }
        } else if (device == AudioSystem.DEVICE_OUT_HDMI ||
                device == AudioSystem.DEVICE_OUT_HDMI_ARC) {
            connType = AudioRoutesInfo.MAIN_HDMI;
            configureHdmiPlugIntent(intent, state);
        } else if (device == AudioSystem.DEVICE_OUT_USB_DEVICE) {
            connType = AudioRoutesInfo.MAIN_USB;
        }

        synchronized (mCurAudioRoutes) {
            if (connType != 0) {
                int newConn = mCurAudioRoutes.mainType;
                if (state != 0) {
                    newConn |= connType;
                } else {
                    newConn &= ~connType;
                }
                if (newConn != mCurAudioRoutes.mainType) {
                    mCurAudioRoutes.mainType = newConn;
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

    private void startMusicPlayer() {
        boolean launchPlayer = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.HEADSET_CONNECT_PLAYER, 0, UserHandle.USER_CURRENT) != 0;
        TelecomManager tm = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);

        if (launchPlayer && !tm.isInCall()) {
            try {
                Intent playerIntent = new Intent(Intent.ACTION_MAIN);
                playerIntent.addCategory(Intent.CATEGORY_APP_MUSIC);
                playerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(playerIntent);
            } catch (ActivityNotFoundException | IllegalArgumentException e) {
                Log.w(TAG, "No music player Activity could be found");
            }
        }
    }

    private void onSetWiredDeviceConnectionState(int device, int state, String address,
            String deviceName, String caller) {
        if (DEBUG_DEVICES) {
            Slog.i(TAG, "onSetWiredDeviceConnectionState(dev:" + Integer.toHexString(device)
                    + " state:" + Integer.toHexString(state)
                    + " address:" + address
                    + " deviceName:" + deviceName
                    + " caller: " + caller + ");");
        }

        synchronized (mConnectedDevices) {
            if ((state == 0) && ((device == AudioSystem.DEVICE_OUT_WIRED_HEADSET) ||
                    (device == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE) ||
                    (device == AudioSystem.DEVICE_OUT_LINE))) {
                setBluetoothA2dpOnInt(true);
            }
            boolean isUsb = ((device & ~AudioSystem.DEVICE_OUT_ALL_USB) == 0) ||
                            (((device & AudioSystem.DEVICE_BIT_IN) != 0) &&
                             ((device & ~AudioSystem.DEVICE_IN_ALL_USB) == 0));
            if (!handleDeviceConnection(state == 1, device, address, deviceName)) {
                // change of connection state failed, bailout
                return;
            }
            if (state != 0) {
                if ((device == AudioSystem.DEVICE_OUT_WIRED_HEADSET) ||
                    (device == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE) ||
                    (device == AudioSystem.DEVICE_OUT_LINE)) {
                    setBluetoothA2dpOnInt(false);
                }
                if ((device & mSafeMediaVolumeDevices) != 0) {
                    sendMsg(mAudioHandler,
                            MSG_CHECK_MUSIC_ACTIVE,
                            SENDMSG_REPLACE,
                            0,
                            0,
                            caller,
                            MUSIC_ACTIVE_POLL_PERIOD_MS);
                }
                // Television devices without CEC service apply software volume on HDMI output
                if (isPlatformTelevision() && ((device & AudioSystem.DEVICE_OUT_HDMI) != 0)) {
                    mFixedVolumeDevices |= AudioSystem.DEVICE_OUT_HDMI;
                    checkAllFixedVolumeDevices();
                    if (mHdmiManager != null) {
                        synchronized (mHdmiManager) {
                            if (mHdmiPlaybackClient != null) {
                                mHdmiCecSink = false;
                                mHdmiPlaybackClient.queryDisplayStatus(mHdmiDisplayStatusCallback);
                            }
                        }
                    }
                }
            } else {
                if (isPlatformTelevision() && ((device & AudioSystem.DEVICE_OUT_HDMI) != 0)) {
                    if (mHdmiManager != null) {
                        synchronized (mHdmiManager) {
                            mHdmiCecSink = false;
                        }
                    }
                }
            }
            if (!isUsb && device != AudioSystem.DEVICE_IN_WIRED_HEADSET) {
                sendDeviceConnectionIntent(device, state, address, deviceName);
            }
        }
    }

    private void configureHdmiPlugIntent(Intent intent, int state) {
        intent.setAction(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        intent.putExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, state);
        if (state == 1) {
            ArrayList<AudioPort> ports = new ArrayList<AudioPort>();
            int[] portGeneration = new int[1];
            int status = AudioSystem.listAudioPorts(ports, portGeneration);
            if (status == AudioManager.SUCCESS) {
                for (AudioPort port : ports) {
                    if (port instanceof AudioDevicePort) {
                        final AudioDevicePort devicePort = (AudioDevicePort) port;
                        if (devicePort.type() == AudioManager.DEVICE_OUT_HDMI ||
                                devicePort.type() == AudioManager.DEVICE_OUT_HDMI_ARC) {
                            // format the list of supported encodings
                            int[] formats = AudioFormat.filterPublicFormats(devicePort.formats());
                            if (formats.length > 0) {
                                ArrayList<Integer> encodingList = new ArrayList(1);
                                for (int format : formats) {
                                    // a format in the list can be 0, skip it
                                    if (format != AudioFormat.ENCODING_INVALID) {
                                        encodingList.add(format);
                                    }
                                }
                                int[] encodingArray = new int[encodingList.size()];
                                for (int i = 0 ; i < encodingArray.length ; i++) {
                                    encodingArray[i] = encodingList.get(i);
                                }
                                intent.putExtra(AudioManager.EXTRA_ENCODINGS, encodingArray);
                            }
                            // find the maximum supported number of channels
                            int maxChannels = 0;
                            for (int mask : devicePort.channelMasks()) {
                                int channelCount = AudioFormat.channelCountFromOutChannelMask(mask);
                                if (channelCount > maxChannels) {
                                    maxChannels = channelCount;
                                }
                            }
                            intent.putExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, maxChannels);
                        }
                    }
                }
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
            int outDevice;
            int inDevice;
            int state;

            if (action.equals(Intent.ACTION_DOCK_EVENT)) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                int config;
                switch (dockState) {
                    case Intent.EXTRA_DOCK_STATE_DESK:
                        config = mForceAnalogDeskDock ? AudioSystem.FORCE_ANALOG_DOCK :
                                                        AudioSystem.FORCE_BT_DESK_DOCK;
                        break;
                    case Intent.EXTRA_DOCK_STATE_CAR:
                        config = mForceAnalogCarDock ? AudioSystem.FORCE_ANALOG_DOCK :
                                                       AudioSystem.FORCE_BT_CAR_DOCK;
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
                BluetoothDevice btDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                setBtScoDeviceConnectionState(btDevice, state);
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
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if (mMonitorRotation) {
                    RotationHelper.enable();
                }
                AudioSystem.setParameters("screen_state=on");
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (mMonitorRotation) {
                    //reduce wakeups (save current) by only listening when display is on
                    RotationHelper.disable();
                }
                AudioSystem.setParameters("screen_state=off");
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                handleConfigurationChanged(context);
            } else if (action.equals(Intent.ACTION_USER_SWITCHED)) {
                // attempt to stop music playback for background user
                sendMsg(mAudioHandler,
                        MSG_BROADCAST_AUDIO_BECOMING_NOISY,
                        SENDMSG_REPLACE,
                        0,
                        0,
                        null,
                        0);
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
            } else if (action.equals(Intent.ACTION_USER_BACKGROUND)) {
                // Disable audio recording for the background user/profile
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                if (userId >= 0) {
                    // TODO Kill recording streams instead of killing processes holding permission
                    UserInfo userInfo = UserManagerService.getInstance().getUserInfo(userId);
                    killBackgroundUserProcessesWithRecordAudioPermission(userInfo);
                }
                UserManagerService.getInstance().setSystemControlledUserRestriction(
                        UserManager.DISALLOW_RECORD_AUDIO, true, userId);
            } else if (action.equals(Intent.ACTION_USER_FOREGROUND)) {
                // Enable audio recording for foreground user/profile
                int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                UserManagerService.getInstance().setSystemControlledUserRestriction(
                        UserManager.DISALLOW_RECORD_AUDIO, false, userId);
            } else if (action.equals(Intent.ACTION_SHUTDOWN)) {
                AudioSystem.setParameters("dev_shutdown=true");
            }
        }
    } // end class AudioServiceBroadcastReceiver

    private void killBackgroundUserProcessesWithRecordAudioPermission(UserInfo oldUser) {
        PackageManager pm = mContext.getPackageManager();
        // Find the home activity of the user. It should not be killed to avoid expensive restart,
        // when the user switches back. For managed profiles, we should kill all recording apps
        ComponentName homeActivityName = null;
        if (!oldUser.isManagedProfile()) {
            homeActivityName = LocalServices.getService(ActivityManagerInternal.class)
                    .getHomeActivityForUser(oldUser.id);
        }
        final String[] permissions = { Manifest.permission.RECORD_AUDIO };
        List<PackageInfo> packages;
        try {
            packages = AppGlobals.getPackageManager()
                    .getPackagesHoldingPermissions(permissions, 0, oldUser.id).getList();
        } catch (RemoteException e) {
            throw new AndroidRuntimeException(e);
        }
        for (int j = packages.size() - 1; j >= 0; j--) {
            PackageInfo pkg = packages.get(j);
            // Skip system processes
            if (UserHandle.getAppId(pkg.applicationInfo.uid) < FIRST_APPLICATION_UID) {
                continue;
            }
            // Skip packages that have permission to interact across users
            if (pm.checkPermission(Manifest.permission.INTERACT_ACROSS_USERS, pkg.packageName)
                    == PackageManager.PERMISSION_GRANTED) {
                continue;
            }
            if (homeActivityName != null
                    && pkg.packageName.equals(homeActivityName.getPackageName())
                    && pkg.applicationInfo.isSystemApp()) {
                continue;
            }
            try {
                final int uid = pkg.applicationInfo.uid;
                ActivityManagerNative.getDefault().killUid(UserHandle.getAppId(uid),
                        UserHandle.getUserId(uid),
                        "killBackgroundUserProcessesWithAudioRecordPermission");
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling killUid", e);
            }
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

    public void setRemoteControlClientPlayItem(long uid, int scope) {
        mMediaFocusControl.setRemoteControlClientPlayItem(uid, scope);
    }

    public void getRemoteControlClientNowPlayingEntries() {
        mMediaFocusControl.getRemoteControlClientNowPlayingEntries();
    }

    public void setRemoteControlClientBrowsedPlayer() {
        mMediaFocusControl.setRemoteControlClientBrowsedPlayer();
    }

    @Override
    public void setRemoteStreamVolume(int index) {
        enforceVolumeController("set the remote stream volume");
        mMediaFocusControl.setRemoteStreamVolume(index);
    }

    //==========================================================================================
    // Audio Focus
    //==========================================================================================
    public int requestAudioFocus(AudioAttributes aa, int durationHint, IBinder cb,
            IAudioFocusDispatcher fd, String clientId, String callingPackageName, int flags,
            IAudioPolicyCallback pcb) {
        // permission checks
        if ((flags & AudioManager.AUDIOFOCUS_FLAG_LOCK) == AudioManager.AUDIOFOCUS_FLAG_LOCK) {
            if (AudioSystem.IN_VOICE_COMM_FOCUS_ID.equals(clientId)) {
                if (PackageManager.PERMISSION_GRANTED != mContext.checkCallingOrSelfPermission(
                            android.Manifest.permission.MODIFY_PHONE_STATE)) {
                    Log.e(TAG, "Invalid permission to (un)lock audio focus", new Exception());
                    return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                }
            } else {
                // only a registered audio policy can be used to lock focus
                synchronized (mAudioPolicies) {
                    if (!mAudioPolicies.containsKey(pcb.asBinder())) {
                        Log.e(TAG, "Invalid unregistered AudioPolicy to (un)lock audio focus");
                        return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
                    }
                }
            }
        }

        return mMediaFocusControl.requestAudioFocus(aa, durationHint, cb, fd,
                clientId, callingPackageName, flags);
    }

    public int abandonAudioFocus(IAudioFocusDispatcher fd, String clientId, AudioAttributes aa) {
        return mMediaFocusControl.abandonAudioFocus(fd, clientId, aa);
    }

    public void unregisterAudioFocusClient(String clientId) {
        mMediaFocusControl.unregisterAudioFocusClient(clientId);
    }

    public int getCurrentAudioFocus() {
        return mMediaFocusControl.getCurrentAudioFocus();
    }

    private boolean readCameraSoundForced() {
        return SystemProperties.getBoolean("audio.camerasound.force", false) ||
                mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_camera_sound_forced);
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
            sendMsg(mAudioHandler,
                    MSG_CONFIGURE_SAFE_MEDIA_VOLUME,
                    SENDMSG_REPLACE,
                    0,
                    0,
                    TAG,
                    0);

            boolean cameraSoundForced = readCameraSoundForced();
            synchronized (mSettingsLock) {
                boolean cameraSoundForcedChanged = false;
                synchronized (mCameraSoundForced) {
                    if (cameraSoundForced != mCameraSoundForced) {
                        mCameraSoundForced = cameraSoundForced;
                        cameraSoundForcedChanged = true;
                    }
                }
                if (cameraSoundForcedChanged) {
                    if (!isPlatformTelevision()) {
                        VolumeStreamState s = mStreamStates[AudioSystem.STREAM_SYSTEM_ENFORCED];
                        if (cameraSoundForced) {
                            s.setAllIndexesToMax();
                            mRingerModeAffectedStreams &=
                                    ~(1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
                        } else {
                            s.setAllIndexes(mStreamStates[AudioSystem.STREAM_SYSTEM], TAG);
                            mRingerModeAffectedStreams |=
                                    (1 << AudioSystem.STREAM_SYSTEM_ENFORCED);
                        }
                        // take new state into account for streams muted by ringer mode
                        setRingerModeInt(getRingerModeInternal(), false);
                    }

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
            mVolumeController.setLayoutDirection(config.getLayoutDirection());
        } catch (Exception e) {
            Log.e(TAG, "Error handling configuration change: ", e);
        }
    }

    //TODO move to an external "orientation helper" class
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

    // Handles request to override default use of A2DP for media.
    // Must be called synchronized on mConnectedDevices
    public void setBluetoothA2dpOnInt(boolean on) {
        synchronized (mBluetoothA2dpEnabledLock) {
            mBluetoothA2dpEnabled = on;
            mAudioHandler.removeMessages(MSG_SET_FORCE_BT_A2DP_USE);
            setForceUseInt_SyncDevices(AudioSystem.FOR_MEDIA,
                    mBluetoothA2dpEnabled ? AudioSystem.FORCE_NONE : AudioSystem.FORCE_NO_BT_A2DP);
        }
    }

    // Must be called synchronized on mConnectedDevices
    private void setForceUseInt_SyncDevices(int usage, int config) {
        switch (usage) {
            case AudioSystem.FOR_MEDIA:
                if (config == AudioSystem.FORCE_NO_BT_A2DP) {
                    mBecomingNoisyIntentDevices &= ~AudioSystem.DEVICE_OUT_ALL_A2DP;
                } else { // config == AudioSystem.FORCE_NONE
                    mBecomingNoisyIntentDevices |= AudioSystem.DEVICE_OUT_ALL_A2DP;
                }
                break;
            case AudioSystem.FOR_DOCK:
                if (config == AudioSystem.FORCE_ANALOG_DOCK) {
                    mBecomingNoisyIntentDevices |= AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET;
                } else { // config == AudioSystem.FORCE_NONE
                    mBecomingNoisyIntentDevices &= ~AudioSystem.DEVICE_OUT_ANLG_DOCK_HEADSET;
                }
                break;
            default:
                // usage doesn't affect the broadcast of ACTION_AUDIO_BECOMING_NOISY
        }
        AudioSystem.setForceUse(usage, config);
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
    private static final int SAFE_MEDIA_VOLUME_NOT_CONFIGURED = 0;
    private static final int SAFE_MEDIA_VOLUME_DISABLED = 1;
    private static final int SAFE_MEDIA_VOLUME_INACTIVE = 2;  // confirmed
    private static final int SAFE_MEDIA_VOLUME_ACTIVE = 3;  // unconfirmed
    private Integer mSafeMediaVolumeState;

    private int mMcc = 0;
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

    private void setSafeMediaVolumeEnabled(boolean on, String caller) {
        synchronized (mSafeMediaVolumeState) {
            if ((mSafeMediaVolumeState != SAFE_MEDIA_VOLUME_NOT_CONFIGURED) &&
                    (mSafeMediaVolumeState != SAFE_MEDIA_VOLUME_DISABLED)) {
                if (on && (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_INACTIVE)) {
                    mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_ACTIVE;
                    enforceSafeMediaVolume(caller);
                } else if (!on && (mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE)) {
                    mSafeMediaVolumeState = SAFE_MEDIA_VOLUME_INACTIVE;
                    mMusicActiveMs = 1;  // nonzero = confirmed
                    saveMusicActiveMs();
                    sendMsg(mAudioHandler,
                            MSG_CHECK_MUSIC_ACTIVE,
                            SENDMSG_REPLACE,
                            0,
                            0,
                            caller,
                            MUSIC_ACTIVE_POLL_PERIOD_MS);
                }
            }
        }
    }

    private void enforceSafeMediaVolume(String caller) {
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
                streamState.setIndex(mSafeMediaVolumeIndex, device, caller);
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
            if ((mSafeMediaVolumeState == SAFE_MEDIA_VOLUME_ACTIVE) &&
                    (mStreamVolumeAlias[streamType] == AudioSystem.STREAM_MUSIC) &&
                    ((device & mSafeMediaVolumeDevices) != 0) &&
                    (index > mSafeMediaVolumeIndex)) {
                return false;
            }
            return true;
        }
    }

    @Override
    public void disableSafeMediaVolume(String callingPackage) {
        enforceVolumeController("disable the safe media volume");
        synchronized (mSafeMediaVolumeState) {
            setSafeMediaVolumeEnabled(false, callingPackage);
            if (mPendingVolumeCommand != null) {
                onSetStreamVolume(mPendingVolumeCommand.mStreamType,
                                  mPendingVolumeCommand.mIndex,
                                  mPendingVolumeCommand.mFlags,
                                  mPendingVolumeCommand.mDevice,
                                  callingPackage);
                mPendingVolumeCommand = null;
            }
        }
    }

    //==========================================================================================
    // Hdmi Cec system audio mode.
    // If Hdmi Cec's system audio mode is on, audio service should send the volume change
    // to HdmiControlService so that the audio receiver can handle it.
    //==========================================================================================

    private class MyDisplayStatusCallback implements HdmiPlaybackClient.DisplayStatusCallback {
        public void onComplete(int status) {
            if (mHdmiManager != null) {
                synchronized (mHdmiManager) {
                    mHdmiCecSink = (status != HdmiControlManager.POWER_STATUS_UNKNOWN);
                    // Television devices without CEC service apply software volume on HDMI output
                    if (isPlatformTelevision() && !mHdmiCecSink) {
                        mFixedVolumeDevices &= ~AudioSystem.DEVICE_OUT_HDMI;
                    }
                    checkAllFixedVolumeDevices();
                }
            }
        }
    };

    // If HDMI-CEC system audio is supported
    private boolean mHdmiSystemAudioSupported = false;
    // Set only when device is tv.
    private HdmiTvClient mHdmiTvClient;
    // true if the device has system feature PackageManager.FEATURE_LEANBACK.
    // cached HdmiControlManager interface
    private HdmiControlManager mHdmiManager;
    // Set only when device is a set-top box.
    private HdmiPlaybackClient mHdmiPlaybackClient;
    // true if we are a set-top box, an HDMI sink is connected and it supports CEC.
    private boolean mHdmiCecSink;

    private MyDisplayStatusCallback mHdmiDisplayStatusCallback = new MyDisplayStatusCallback();

    @Override
    public int setHdmiSystemAudioSupported(boolean on) {
        int device = AudioSystem.DEVICE_NONE;
        if (mHdmiManager != null) {
            synchronized (mHdmiManager) {
                if (mHdmiTvClient == null) {
                    Log.w(TAG, "Only Hdmi-Cec enabled TV device supports system audio mode.");
                    return device;
                }

                synchronized (mHdmiTvClient) {
                    if (mHdmiSystemAudioSupported != on) {
                        mHdmiSystemAudioSupported = on;
                        AudioSystem.setForceUse(AudioSystem.FOR_HDMI_SYSTEM_AUDIO,
                                on ? AudioSystem.FORCE_HDMI_SYSTEM_AUDIO_ENFORCED :
                                     AudioSystem.FORCE_NONE);
                    }
                    device = getDevicesForStream(AudioSystem.STREAM_MUSIC);
                }
            }
        }
        return device;
    }

    @Override
    public boolean isHdmiSystemAudioSupported() {
        return mHdmiSystemAudioSupported;
    }

    //==========================================================================================
    // Accessibility: taking touch exploration into account for selecting the default
    //   stream override timeout when adjusting volume
    //==========================================================================================
    private static class StreamOverride
            implements AccessibilityManager.TouchExplorationStateChangeListener {

        // AudioService.getActiveStreamType() will return:
        // - STREAM_NOTIFICATION on tablets during this period after a notification stopped
        // - STREAM_MUSIC on phones during this period after music or talkback/voice search prompt
        // stopped
        private static final int DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS = 0;
        private static final int TOUCH_EXPLORE_STREAM_TYPE_OVERRIDE_DELAY_MS = 1000;

        static int sDelayMs;

        static void init(Context ctxt) {
            AccessibilityManager accessibilityManager =
                    (AccessibilityManager) ctxt.getSystemService(Context.ACCESSIBILITY_SERVICE);
            updateDefaultStreamOverrideDelay(
                    accessibilityManager.isTouchExplorationEnabled());
            accessibilityManager.addTouchExplorationStateChangeListener(
                    new StreamOverride());
        }

        @Override
        public void onTouchExplorationStateChanged(boolean enabled) {
            updateDefaultStreamOverrideDelay(enabled);
        }

        private static void updateDefaultStreamOverrideDelay(boolean touchExploreEnabled) {
            if (touchExploreEnabled) {
                sDelayMs = TOUCH_EXPLORE_STREAM_TYPE_OVERRIDE_DELAY_MS;
            } else {
                sDelayMs = DEFAULT_STREAM_TYPE_OVERRIDE_DELAY_MS;
            }
            if (DEBUG_VOL) Log.d(TAG, "Touch exploration enabled=" + touchExploreEnabled
                    + " stream override delay is now " + sDelayMs + " ms");
        }
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
        pw.println("- mode (internal) = " + RINGER_MODE_NAMES[mRingerMode]);
        pw.println("- mode (external) = " + RINGER_MODE_NAMES[mRingerModeExternal]);
        dumpRingerModeStreams(pw, "affected", mRingerModeAffectedStreams);
        dumpRingerModeStreams(pw, "muted", mRingerModeMutedStreams);
        pw.print("- delegate = "); pw.println(mRingerModeDelegate);
    }

    private void dumpRingerModeStreams(PrintWriter pw, String type, int streams) {
        pw.print("- ringer mode "); pw.print(type); pw.print(" streams = 0x");
        pw.print(Integer.toHexString(streams));
        if (streams != 0) {
            pw.print(" (");
            boolean first = true;
            for (int i = 0; i < AudioSystem.STREAM_NAMES.length; i++) {
                final int stream = (1 << i);
                if ((streams & stream) != 0) {
                    if (!first) pw.print(',');
                    pw.print(AudioSystem.STREAM_NAMES[i]);
                    streams &= ~stream;
                    first = false;
                }
            }
            if (streams != 0) {
                if (!first) pw.print(',');
                pw.print(streams);
            }
            pw.print(')');
        }
        pw.println();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

        mMediaFocusControl.dump(pw);
        dumpStreamStates(pw);
        dumpRingerMode(pw);
        pw.println("\nAudio routes:");
        pw.print("  mMainType=0x"); pw.println(Integer.toHexString(mCurAudioRoutes.mainType));
        pw.print("  mBluetoothName="); pw.println(mCurAudioRoutes.bluetoothName);

        pw.println("\nOther state:");
        pw.print("  mVolumeController="); pw.println(mVolumeController);
        pw.print("  mSafeMediaVolumeState=");
        pw.println(safeMediaVolumeStateToString(mSafeMediaVolumeState));
        pw.print("  mSafeMediaVolumeIndex="); pw.println(mSafeMediaVolumeIndex);
        pw.print("  mPendingVolumeCommand="); pw.println(mPendingVolumeCommand);
        pw.print("  mMusicActiveMs="); pw.println(mMusicActiveMs);
        pw.print("  mMcc="); pw.println(mMcc);
        pw.print("  mCameraSoundForced="); pw.println(mCameraSoundForced);
        pw.print("  mHasVibrator="); pw.println(mHasVibrator);
        pw.print("  mControllerService="); pw.println(mControllerService);
        pw.print("  mVolumePolicy="); pw.println(mVolumePolicy);

        dumpAudioPolicies(pw);
    }

    private static String safeMediaVolumeStateToString(Integer state) {
        switch(state) {
            case SAFE_MEDIA_VOLUME_NOT_CONFIGURED: return "SAFE_MEDIA_VOLUME_NOT_CONFIGURED";
            case SAFE_MEDIA_VOLUME_DISABLED: return "SAFE_MEDIA_VOLUME_DISABLED";
            case SAFE_MEDIA_VOLUME_INACTIVE: return "SAFE_MEDIA_VOLUME_INACTIVE";
            case SAFE_MEDIA_VOLUME_ACTIVE: return "SAFE_MEDIA_VOLUME_ACTIVE";
        }
        return null;
    }

    // Inform AudioFlinger of our device's low RAM attribute
    private static void readAndSetLowRamDevice()
    {
        int status = AudioSystem.setLowRamDevice(ActivityManager.isLowRamDeviceStatic());
        if (status != 0) {
            Log.w(TAG, "AudioFlinger informed of device's low RAM attribute; status " + status);
        }
    }

    private void enforceVolumeController(String action) {
        if (mControllerService.mUid != 0 && Binder.getCallingUid() == mControllerService.mUid) {
            return;
        }
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.STATUS_BAR_SERVICE,
                "Only SystemUI can " + action);
    }

    @Override
    public void setVolumeController(final IVolumeController controller) {
        enforceVolumeController("set the volume controller");

        // return early if things are not actually changing
        if (mVolumeController.isSameBinder(controller)) {
            return;
        }

        // dismiss the old volume controller
        mVolumeController.postDismiss();
        if (controller != null) {
            // we are about to register a new controller, listen for its death
            try {
                controller.asBinder().linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        if (mVolumeController.isSameBinder(controller)) {
                            Log.w(TAG, "Current remote volume controller died, unregistering");
                            setVolumeController(null);
                        }
                    }
                }, 0);
            } catch (RemoteException e) {
                // noop
            }
        }
        mVolumeController.setController(controller);
        if (DEBUG_VOL) Log.d(TAG, "Volume controller: " + mVolumeController);
    }

    @Override
    public void notifyVolumeControllerVisible(final IVolumeController controller, boolean visible) {
        enforceVolumeController("notify about volume controller visibility");

        // return early if the controller is not current
        if (!mVolumeController.isSameBinder(controller)) {
            return;
        }

        mVolumeController.setVisible(visible);
        if (DEBUG_VOL) Log.d(TAG, "Volume controller visible: " + visible);
    }

    @Override
    public void setVolumePolicy(VolumePolicy policy) {
        enforceVolumeController("set volume policy");
        if (policy != null && !policy.equals(mVolumePolicy)) {
            mVolumePolicy = policy;
            if (DEBUG_VOL) Log.d(TAG, "Volume policy changed: " + mVolumePolicy);
        }
    }

    public static class VolumeController {
        private static final String TAG = "VolumeController";

        private IVolumeController mController;
        private boolean mVisible;
        private long mNextLongPress;
        private int mLongPressTimeout;

        public void setController(IVolumeController controller) {
            mController = controller;
            mVisible = false;
        }

        public void loadSettings(ContentResolver cr) {
            mLongPressTimeout = Settings.Secure.getIntForUser(cr,
                    Settings.Secure.LONG_PRESS_TIMEOUT, 500, UserHandle.USER_CURRENT);
        }

        public boolean suppressAdjustment(int resolvedStream, int flags, boolean isMute) {
            if (isMute) {
                return false;
            }
            boolean suppress = false;
            if (resolvedStream == AudioSystem.STREAM_RING && mController != null) {
                final long now = SystemClock.uptimeMillis();
                if ((flags & AudioManager.FLAG_SHOW_UI) != 0 && !mVisible) {
                    // ui will become visible
                    if (mNextLongPress < now) {
                        mNextLongPress = now + mLongPressTimeout;
                    }
                    suppress = true;
                } else if (mNextLongPress > 0) {  // in a long-press
                    if (now > mNextLongPress) {
                        // long press triggered, no more suppression
                        mNextLongPress = 0;
                    } else {
                        // keep suppressing until the long press triggers
                        suppress = true;
                    }
                }
            }
            return suppress;
        }

        public void setVisible(boolean visible) {
            mVisible = visible;
        }

        public boolean isSameBinder(IVolumeController controller) {
            return Objects.equals(asBinder(), binder(controller));
        }

        public IBinder asBinder() {
            return binder(mController);
        }

        private static IBinder binder(IVolumeController controller) {
            return controller == null ? null : controller.asBinder();
        }

        @Override
        public String toString() {
            return "VolumeController(" + asBinder() + ",mVisible=" + mVisible + ")";
        }

        public void postDisplaySafeVolumeWarning(int flags) {
            if (mController == null)
                return;
            try {
                mController.displaySafeVolumeWarning(flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling displaySafeVolumeWarning", e);
            }
        }

        public void postVolumeChanged(int streamType, int flags) {
            if (mController == null)
                return;
            try {
                mController.volumeChanged(streamType, flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling volumeChanged", e);
            }
        }

        public void postMasterMuteChanged(int flags) {
            if (mController == null)
                return;
            try {
                mController.masterMuteChanged(flags);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling masterMuteChanged", e);
            }
        }

        public void setLayoutDirection(int layoutDirection) {
            if (mController == null)
                return;
            try {
                mController.setLayoutDirection(layoutDirection);
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling setLayoutDirection", e);
            }
        }

        public void postDismiss() {
            if (mController == null)
                return;
            try {
                mController.dismiss();
            } catch (RemoteException e) {
                Log.w(TAG, "Error calling dismiss", e);
            }
        }
    }

    /**
     * Interface for system components to get some extra functionality through
     * LocalServices.
     */
    final class AudioServiceInternal extends AudioManagerInternal {
        @Override
        public void setRingerModeDelegate(RingerModeDelegate delegate) {
            mRingerModeDelegate = delegate;
            if (mRingerModeDelegate != null) {
                updateRingerModeAffectedStreams();
                setRingerModeInternal(getRingerModeInternal(), TAG + ".setRingerModeDelegate");
            }
        }

        @Override
        public void adjustSuggestedStreamVolumeForUid(int streamType, int direction, int flags,
                String callingPackage, int uid) {
            // direction and stream type swap here because the public
            // adjustSuggested has a different order than the other methods.
            adjustSuggestedStreamVolume(direction, streamType, flags, callingPackage,
                    callingPackage, uid);
        }

        @Override
        public void adjustStreamVolumeForUid(int streamType, int direction, int flags,
                String callingPackage, int uid) {
            adjustStreamVolume(streamType, direction, flags, callingPackage,
                    callingPackage, uid);
        }

        @Override
        public void setStreamVolumeForUid(int streamType, int direction, int flags,
                String callingPackage, int uid) {
            setStreamVolume(streamType, direction, flags, callingPackage, callingPackage, uid);
        }

        @Override
        public int getRingerModeInternal() {
            return AudioService.this.getRingerModeInternal();
        }

        @Override
        public void setRingerModeInternal(int ringerMode, String caller) {
            AudioService.this.setRingerModeInternal(ringerMode, caller);
        }

        @Override
        public int getVolumeControllerUid() {
            return mControllerService.mUid;
        }

        @Override
        public void updateRingerModeAffectedStreamsInternal() {
            synchronized (mSettingsLock) {
                if (updateRingerModeAffectedStreams()) {
                    setRingerModeInt(getRingerModeInternal(), false);
                }
            }
        }
    }

    //==========================================================================================
    // Audio policy management
    //==========================================================================================
    public String registerAudioPolicy(AudioPolicyConfig policyConfig, IAudioPolicyCallback pcb,
            boolean hasFocusListener) {
        AudioSystem.setDynamicPolicyCallback(mDynPolicyCallback);

        if (DEBUG_AP) Log.d(TAG, "registerAudioPolicy for " + pcb.asBinder()
                + " with config:" + policyConfig);
        String regId = null;
        // error handling
        boolean hasPermissionForPolicy =
                (PackageManager.PERMISSION_GRANTED == mContext.checkCallingPermission(
                        android.Manifest.permission.MODIFY_AUDIO_ROUTING));
        if (!hasPermissionForPolicy) {
            Slog.w(TAG, "Can't register audio policy for pid " + Binder.getCallingPid() + " / uid "
                    + Binder.getCallingUid() + ", need MODIFY_AUDIO_ROUTING");
            return null;
        }

        synchronized (mAudioPolicies) {
            try {
                if (mAudioPolicies.containsKey(pcb.asBinder())) {
                    Slog.e(TAG, "Cannot re-register policy");
                    return null;
                }
                AudioPolicyProxy app = new AudioPolicyProxy(policyConfig, pcb, hasFocusListener);
                pcb.asBinder().linkToDeath(app, 0/*flags*/);
                regId = app.getRegistrationId();
                mAudioPolicies.put(pcb.asBinder(), app);
            } catch (RemoteException e) {
                // audio policy owner has already died!
                Slog.w(TAG, "Audio policy registration failed, could not link to " + pcb +
                        " binder death", e);
                return null;
            }
        }
        return regId;
    }

    public void unregisterAudioPolicyAsync(IAudioPolicyCallback pcb) {
        if (DEBUG_AP) Log.d(TAG, "unregisterAudioPolicyAsync for " + pcb.asBinder());
        synchronized (mAudioPolicies) {
            AudioPolicyProxy app = mAudioPolicies.remove(pcb.asBinder());
            if (app == null) {
                Slog.w(TAG, "Trying to unregister unknown audio policy for pid "
                        + Binder.getCallingPid() + " / uid " + Binder.getCallingUid());
                return;
            } else {
                pcb.asBinder().unlinkToDeath(app, 0/*flags*/);
            }
            app.release();
        }
        // TODO implement clearing mix attribute matching info in native audio policy
    }

    public int setFocusPropertiesForPolicy(int duckingBehavior, IAudioPolicyCallback pcb) {
        if (DEBUG_AP) Log.d(TAG, "setFocusPropertiesForPolicy() duck behavior=" + duckingBehavior
                + " policy " +  pcb.asBinder());
        // error handling
        boolean hasPermissionForPolicy =
                (PackageManager.PERMISSION_GRANTED == mContext.checkCallingPermission(
                        android.Manifest.permission.MODIFY_AUDIO_ROUTING));
        if (!hasPermissionForPolicy) {
            Slog.w(TAG, "Cannot change audio policy ducking handling for pid " +
                    + Binder.getCallingPid() + " / uid "
                    + Binder.getCallingUid() + ", need MODIFY_AUDIO_ROUTING");
            return AudioManager.ERROR;
        }

        synchronized (mAudioPolicies) {
            if (!mAudioPolicies.containsKey(pcb.asBinder())) {
                Slog.e(TAG, "Cannot change audio policy focus properties, unregistered policy");
                return AudioManager.ERROR;
            }
            final AudioPolicyProxy app = mAudioPolicies.get(pcb.asBinder());
            if (duckingBehavior == AudioPolicy.FOCUS_POLICY_DUCKING_IN_POLICY) {
                // is there already one policy managing ducking?
                for (AudioPolicyProxy policy : mAudioPolicies.values()) {
                    if (policy.mFocusDuckBehavior == AudioPolicy.FOCUS_POLICY_DUCKING_IN_POLICY) {
                        Slog.e(TAG, "Cannot change audio policy ducking behavior, already handled");
                        return AudioManager.ERROR;
                    }
                }
            }
            app.mFocusDuckBehavior = duckingBehavior;
            mMediaFocusControl.setDuckingInExtPolicyAvailable(
                    duckingBehavior == AudioPolicy.FOCUS_POLICY_DUCKING_IN_POLICY);
        }
        return AudioManager.SUCCESS;
    }

    private void dumpAudioPolicies(PrintWriter pw) {
        pw.println("\nAudio policies:");
        synchronized (mAudioPolicies) {
            for (AudioPolicyProxy policy : mAudioPolicies.values()) {
                pw.println(policy.toLogFriendlyString());
            }
        }
    }

    //======================
    // Audio policy callback from AudioSystem
    //======================
    private final AudioSystem.DynamicPolicyCallback mDynPolicyCallback =
            new AudioSystem.DynamicPolicyCallback() {
        public void onDynamicPolicyMixStateUpdate(String regId, int state) {
            if (!TextUtils.isEmpty(regId)) {
                sendMsg(mAudioHandler, MSG_DYN_POLICY_MIX_STATE_UPDATE, SENDMSG_QUEUE,
                        state /*arg1*/, 0 /*arg2 ignored*/, regId /*obj*/, 0 /*delay*/);
            }
        }
    };

    private void onDynPolicyMixStateUpdate(String regId, int state) {
        if (DEBUG_AP) Log.d(TAG, "onDynamicPolicyMixStateUpdate("+ regId + ", " + state +")");
        synchronized (mAudioPolicies) {
            for (AudioPolicyProxy policy : mAudioPolicies.values()) {
                for (AudioMix mix : policy.getMixes()) {
                    if (mix.getRegistration().equals(regId)) {
                        try {
                            policy.mPolicyCallback.notifyMixStateUpdate(regId, state);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Can't call notifyMixStateUpdate() on IAudioPolicyCallback "
                                    + policy.mPolicyCallback.asBinder(), e);
                        }
                        return;
                    }
                }
            }
        }

    }

    //======================
    // Audio policy proxy
    //======================
    /**
     * This internal class inherits from AudioPolicyConfig, each instance contains all the
     * mixes of an AudioPolicy and their configurations.
     */
    public class AudioPolicyProxy extends AudioPolicyConfig implements IBinder.DeathRecipient {
        private static final String TAG = "AudioPolicyProxy";
        IAudioPolicyCallback mPolicyCallback;
        boolean mHasFocusListener;
        /**
         * Audio focus ducking behavior for an audio policy.
         * This variable reflects the value that was successfully set in
         * {@link AudioService#setFocusPropertiesForPolicy(int, IAudioPolicyCallback)}. This
         * implies that a value of FOCUS_POLICY_DUCKING_IN_POLICY means the corresponding policy
         * is handling ducking for audio focus.
         */
        int mFocusDuckBehavior = AudioPolicy.FOCUS_POLICY_DUCKING_DEFAULT;

        AudioPolicyProxy(AudioPolicyConfig config, IAudioPolicyCallback token,
                boolean hasFocusListener) {
            super(config);
            setRegistration(new String(config.hashCode() + ":ap:" + mAudioPolicyCounter++));
            mPolicyCallback = token;
            mHasFocusListener = hasFocusListener;
            if (mHasFocusListener) {
                mMediaFocusControl.addFocusFollower(mPolicyCallback);
            }
            connectMixes();
        }

        public void binderDied() {
            synchronized (mAudioPolicies) {
                Log.i(TAG, "audio policy " + mPolicyCallback + " died");
                release();
                mAudioPolicies.remove(mPolicyCallback.asBinder());
            }
        }

        String getRegistrationId() {
            return getRegistration();
        }

        void release() {
            if (mFocusDuckBehavior == AudioPolicy.FOCUS_POLICY_DUCKING_IN_POLICY) {
                mMediaFocusControl.setDuckingInExtPolicyAvailable(false);
            }
            if (mHasFocusListener) {
                mMediaFocusControl.removeFocusFollower(mPolicyCallback);
            }
            AudioSystem.registerPolicyMixes(mMixes, false);
        }

        void connectMixes() {
            AudioSystem.registerPolicyMixes(mMixes, true);
        }
    };

    private HashMap<IBinder, AudioPolicyProxy> mAudioPolicies =
            new HashMap<IBinder, AudioPolicyProxy>();
    private int mAudioPolicyCounter = 0; // always accessed synchronized on mAudioPolicies

    private class ControllerService extends ContentObserver {
        private int mUid;
        private ComponentName mComponent;

        public ControllerService() {
            super(null);
        }

        @Override
        public String toString() {
            return String.format("{mUid=%d,mComponent=%s}", mUid, mComponent);
        }

        public void init() {
            onChange(true);
            mContentResolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.VOLUME_CONTROLLER_SERVICE_COMPONENT), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mUid = 0;
            mComponent = null;
            final String setting = Settings.Secure.getString(mContentResolver,
                    Settings.Secure.VOLUME_CONTROLLER_SERVICE_COMPONENT);
            if (setting == null) return;
            try {
                mComponent = ComponentName.unflattenFromString(setting);
                if (mComponent == null) return;
                mUid = mContext.getPackageManager()
                        .getApplicationInfo(mComponent.getPackageName(), 0).uid;
            } catch (Exception e) {
                Log.w(TAG, "Error loading controller service", e);
            }
            if (DEBUG_VOL) Log.d(TAG, "Reloaded controller service: " + this);
        }
    }
}
