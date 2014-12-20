/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

package android.app;

import android.annotation.SystemApi;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.media.AudioAttributes.AttributeUsage;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserManager;
import android.util.ArrayMap;

import com.android.internal.app.IAppOpsCallback;
import com.android.internal.app.IAppOpsService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * API for interacting with "application operation" tracking.
 *
 * <p>This API is not generally intended for third party application developers; most
 * features are only available to system applications.  Obtain an instance of it through
 * {@link Context#getSystemService(String) Context.getSystemService} with
 * {@link Context#APP_OPS_SERVICE Context.APP_OPS_SERVICE}.</p>
 */
public class AppOpsManager {
    /**
     * <p>App ops allows callers to:</p>
     *
     * <ul>
     * <li> Note when operations are happening, and find out if they are allowed for the current
     * caller.</li>
     * <li> Disallow specific apps from doing specific operations.</li>
     * <li> Collect all of the current information about operations that have been executed or
     * are not being allowed.</li>
     * <li> Monitor for changes in whether an operation is allowed.</li>
     * </ul>
     *
     * <p>Each operation is identified by a single integer; these integers are a fixed set of
     * operations, enumerated by the OP_* constants.
     *
     * <p></p>When checking operations, the result is a "mode" integer indicating the current
     * setting for the operation under that caller: MODE_ALLOWED, MODE_IGNORED (don't execute
     * the operation but fake its behavior enough so that the caller doesn't crash),
     * MODE_ERRORED (throw a SecurityException back to the caller; the normal operation calls
     * will do this for you).
     */

    /** {@hide */
    public static final String ACTION_SU_SESSION_CHANGED =
            "android.intent.action.SU_SESSION_CHANGED";

    final Context mContext;
    final IAppOpsService mService;
    final ArrayMap<OnOpChangedListener, IAppOpsCallback> mModeWatchers
            = new ArrayMap<OnOpChangedListener, IAppOpsCallback>();

    static IBinder sToken;

    /**
     * Result from {@link #checkOp}, {@link #noteOp}, {@link #startOp}: the given caller is
     * allowed to perform the given operation.
     */
    public static final int MODE_ALLOWED = 0;

    /**
     * Result from {@link #checkOp}, {@link #noteOp}, {@link #startOp}: the given caller is
     * not allowed to perform the given operation, and this attempt should
     * <em>silently fail</em> (it should not cause the app to crash).
     */
    public static final int MODE_IGNORED = 1;

    /**
     * Result from {@link #checkOpNoThrow}, {@link #noteOpNoThrow}, {@link #startOpNoThrow}: the
     * given caller is not allowed to perform the given operation, and this attempt should
     * cause it to have a fatal error, typically a {@link SecurityException}.
     */
    public static final int MODE_ERRORED = 2;

    /**
     * Result from {@link #checkOp}, {@link #noteOp}, {@link #startOp}: the given caller should
     * use its default security check.  This mode is not normally used; it should only be used
     * with appop permissions, and callers must explicitly check for it and deal with it.
     */
    public static final int MODE_DEFAULT = 3;

    /**
     * @hide Result from {@link #checkOp}, {@link #noteOp}, {@link #startOp}:
     * AppOps Service should show a dialog box on screen to get user
     * permission.
     */
    public static final int MODE_ASK = 4;

    // when adding one of these:
    //  - increment _NUM_OP
    //  - add rows to sOpToSwitch, sOpToString, sOpNames, sOpPerms, sOpDefaultMode, sOpDefaultStrictMode,
    //    sOpToOpString, sOpStrictMode.
    //  - add descriptive strings to frameworks/base/core/res/res/values/config.xml
    //  - add descriptive strings to Settings/res/values/arrays.xml
    //  - add the op to the appropriate template in AppOpsState.OpsTemplate (settings app)

    /** @hide No operation specified. */
    public static final int OP_NONE = -1;
    /** @hide Access to coarse location information. */
    public static final int OP_COARSE_LOCATION = 0;
    /** @hide Access to fine location information. */
    public static final int OP_FINE_LOCATION = 1;
    /** @hide Causing GPS to run. */
    public static final int OP_GPS = 2;
    /** @hide */
    public static final int OP_VIBRATE = 3;
    /** @hide */
    public static final int OP_READ_CONTACTS = 4;
    /** @hide */
    public static final int OP_WRITE_CONTACTS = 5;
    /** @hide */
    public static final int OP_READ_CALL_LOG = 6;
    /** @hide */
    public static final int OP_WRITE_CALL_LOG = 7;
    /** @hide */
    public static final int OP_READ_CALENDAR = 8;
    /** @hide */
    public static final int OP_WRITE_CALENDAR = 9;
    /** @hide */
    public static final int OP_WIFI_SCAN = 10;
    /** @hide */
    public static final int OP_POST_NOTIFICATION = 11;
    /** @hide */
    public static final int OP_NEIGHBORING_CELLS = 12;
    /** @hide */
    public static final int OP_CALL_PHONE = 13;
    /** @hide */
    public static final int OP_READ_SMS = 14;
    /** @hide */
    public static final int OP_WRITE_SMS = 15;
    /** @hide */
    public static final int OP_RECEIVE_SMS = 16;
    /** @hide */
    public static final int OP_RECEIVE_EMERGECY_SMS = 17;
    /** @hide */
    public static final int OP_RECEIVE_MMS = 18;
    /** @hide */
    public static final int OP_RECEIVE_WAP_PUSH = 19;
    /** @hide */
    public static final int OP_SEND_SMS = 20;
    /** @hide */
    public static final int OP_READ_ICC_SMS = 21;
    /** @hide */
    public static final int OP_WRITE_ICC_SMS = 22;
    /** @hide */
    public static final int OP_WRITE_SETTINGS = 23;
    /** @hide */
    public static final int OP_SYSTEM_ALERT_WINDOW = 24;
    /** @hide */
    public static final int OP_ACCESS_NOTIFICATIONS = 25;
    /** @hide */
    public static final int OP_CAMERA = 26;
    /** @hide */
    public static final int OP_RECORD_AUDIO = 27;
    /** @hide */
    public static final int OP_PLAY_AUDIO = 28;
    /** @hide */
    public static final int OP_READ_CLIPBOARD = 29;
    /** @hide */
    public static final int OP_WRITE_CLIPBOARD = 30;
    /** @hide */
    public static final int OP_TAKE_MEDIA_BUTTONS = 31;
    /** @hide */
    public static final int OP_TAKE_AUDIO_FOCUS = 32;
    /** @hide */
    public static final int OP_AUDIO_MASTER_VOLUME = 33;
    /** @hide */
    public static final int OP_AUDIO_VOICE_VOLUME = 34;
    /** @hide */
    public static final int OP_AUDIO_RING_VOLUME = 35;
    /** @hide */
    public static final int OP_AUDIO_MEDIA_VOLUME = 36;
    /** @hide */
    public static final int OP_AUDIO_ALARM_VOLUME = 37;
    /** @hide */
    public static final int OP_AUDIO_NOTIFICATION_VOLUME = 38;
    /** @hide */
    public static final int OP_AUDIO_BLUETOOTH_VOLUME = 39;
    /** @hide */
    public static final int OP_WAKE_LOCK = 40;
    /** @hide Continually monitoring location data. */
    public static final int OP_MONITOR_LOCATION = 41;
    /** @hide Continually monitoring location data with a relatively high power request. */
    public static final int OP_MONITOR_HIGH_POWER_LOCATION = 42;
    /** @hide Retrieve current usage stats via {@link UsageStatsManager}. */
    public static final int OP_GET_USAGE_STATS = 43;
    /** @hide */
    public static final int OP_MUTE_MICROPHONE = 44;
    /** @hide */
    public static final int OP_TOAST_WINDOW = 45;
    /** @hide Capture the device's display contents and/or audio */
    public static final int OP_PROJECT_MEDIA = 46;
    /** @hide Activate a VPN connection without user intervention. */
    public static final int OP_ACTIVATE_VPN = 47;
    /** @hide */
    public static final int OP_WIFI_CHANGE = 48;
    /** @hide */
    public static final int OP_BLUETOOTH_CHANGE = 49;
    /** @hide */
    public static final int OP_SEND_MMS = 50;
    /** @hide */
    public static final int OP_READ_MMS = 51;
    /** @hide */
    public static final int OP_WRITE_MMS = 52;
    /** @hide */
    public static final int OP_BOOT_COMPLETED = 53;
    /** @hide */
    public static final int OP_NFC_CHANGE = 54;
    /** @hide */
    public static final int OP_DELETE_SMS = 55;
    /** @hide */
    public static final int OP_DELETE_MMS = 56;
    /** @hide */
    public static final int OP_DELETE_CONTACTS = 57;
    /** @hide */
    public static final int OP_DELETE_CALL_LOG = 58;
    /** @hide */
    public static final int OP_DATA_CONNECT_CHANGE = 59;
    /** @hide */
    public static final int OP_ALARM_WAKEUP = 60;
    /** @hide */
    public static final int OP_SU = 61;
    /** @hide */
    public static final int _NUM_OP = 62;

    /** Access to coarse location information. */
    public static final String OPSTR_COARSE_LOCATION =
            "android:coarse_location";
    /** Access to fine location information. */
    public static final String OPSTR_FINE_LOCATION =
            "android:fine_location";
    /** Continually monitoring location data. */
    public static final String OPSTR_MONITOR_LOCATION
            = "android:monitor_location";
    /** Continually monitoring location data with a relatively high power request. */
    public static final String OPSTR_MONITOR_HIGH_POWER_LOCATION
            = "android:monitor_location_high_power";
    /** Access to {@link android.app.usage.UsageStatsManager}. */
    public static final String OPSTR_GET_USAGE_STATS
            = "android:get_usage_stats";
    /** Activate a VPN connection without user intervention. @hide */
    @SystemApi
    public static final String OPSTR_ACTIVATE_VPN = "android:activate_vpn";

    private static final String OPSTR_GPS =
            "android:gps";
    private static final String OPSTR_VIBRATE =
            "android:vibrate";
    private static final String OPSTR_READ_CONTACTS =
            "android:read_contacts";
    private static final String OPSTR_WRITE_CONTACTS =
            "android:write_contacts";
    private static final String OPSTR_READ_CALL_LOG =
            "android:read_call_log";
    private static final String OPSTR_WRITE_CALL_LOG =
            "android:write_call_log";
    private static final String OPSTR_READ_CALENDAR =
            "android:read_calendar";
    private static final String OPSTR_WRITE_CALENDAR =
            "android:write_calendar";
    private static final String OPSTR_WIFI_SCAN =
            "android:wifi_scan";
    private static final String OPSTR_POST_NOTIFICATION =
            "android:post_notification";
    private static final String OPSTR_NEIGHBORING_CELLS =
            "android:neighboring_cells";
    private static final String OPSTR_CALL_PHONE =
            "android:call_phone";
    private static final String OPSTR_READ_SMS =
            "android:read_sms";
    private static final String OPSTR_WRITE_SMS =
            "android:write_sms";
    private static final String OPSTR_RECEIVE_SMS =
            "android:receive_sms";
    private static final String OPSTR_RECEIVE_EMERGECY_SMS =
            "android:receive_emergecy_sms";
    private static final String OPSTR_RECEIVE_MMS =
            "android:receive_mms";
    private static final String OPSTR_RECEIVE_WAP_PUSH =
            "android:receive_wap_push";
    private static final String OPSTR_SEND_SMS =
            "android:send_sms";
    private static final String OPSTR_READ_ICC_SMS =
            "android:read_icc_sms";
    private static final String OPSTR_WRITE_ICC_SMS =
            "android:write_icc_sms";
    private static final String OPSTR_WRITE_SETTINGS =
            "android:write_settings";
    private static final String OPSTR_SYSTEM_ALERT_WINDOW =
            "android:system_alert_window";
    private static final String OPSTR_ACCESS_NOTIFICATIONS =
            "android:access_notifications";
    private static final String OPSTR_CAMERA =
            "android:camera";
    private static final String OPSTR_RECORD_AUDIO =
            "android:record_audio";
    private static final String OPSTR_PLAY_AUDIO =
            "android:play_audio";
    private static final String OPSTR_READ_CLIPBOARD =
            "android:read_clipboard";
    private static final String OPSTR_WRITE_CLIPBOARD =
            "android:write_clipboard";
    private static final String OPSTR_TAKE_MEDIA_BUTTONS =
            "android:take_media_buttons";
    private static final String OPSTR_TAKE_AUDIO_FOCUS =
            "android:take_audio_focus";
    private static final String OPSTR_AUDIO_MASTER_VOLUME =
            "android:audio_master_volume";
    private static final String OPSTR_AUDIO_VOICE_VOLUME =
            "android:audio_voice_volume";
    private static final String OPSTR_AUDIO_RING_VOLUME =
            "android:audio_ring_volume";
    private static final String OPSTR_AUDIO_MEDIA_VOLUME =
            "android:audio_media_volume";
    private static final String OPSTR_AUDIO_ALARM_VOLUME =
            "android:audio_alarm_volume";
    private static final String OPSTR_AUDIO_NOTIFICATION_VOLUME =
            "android:audio_notification_volume";
    private static final String OPSTR_AUDIO_BLUETOOTH_VOLUME =
            "android:audio_bluetooth_volume";
    private static final String OPSTR_WAKE_LOCK =
            "android:wake_lock";
    private static final String OPSTR_MUTE_MICROPHONE =
            "android:mute_microphone";
    private static final String OPSTR_TOAST_WINDOW =
            "android:toast_window";
    private static final String OPSTR_PROJECT_MEDIA =
            "android:project_media";
    private static final String OPSTR_WIFI_CHANGE =
            "android:wifi_change";
    private static final String OPSTR_BLUETOOTH_CHANGE =
            "android:bluetooth_change";
    private static final String OPSTR_SEND_MMS =
            "android:send_mms";
    private static final String OPSTR_READ_MMS =
            "android:read_mms";
    private static final String OPSTR_WRITE_MMS =
            "android:write_mms";
    private static final String OPSTR_BOOT_COMPLETED =
            "android:boot_completed";
    private static final String OPSTR_NFC_CHANGE =
            "android:nfc_change";
    private static final String OPSTR_DELETE_SMS =
            "android:delete_sms";
    private static final String OPSTR_DELETE_MMS =
            "android:delete_mms";
    private static final String OPSTR_DELETE_CONTACTS =
            "android:delete_contacts";
    private static final String OPSTR_DELETE_CALL_LOG =
            "android:delete_call_log";
    private static final String OPSTR_DATA_CONNECT_CHANGE =
            "android:data_connect_change";
    private static final String OPSTR_ALARM_WAKEUP =
            "android:alarm_wakeup";
    private static final String OPSTR_SU =
            "android:su";

    /**
     * This maps each operation to the operation that serves as the
     * switch to determine whether it is allowed.  Generally this is
     * a 1:1 mapping, but for some things (like location) that have
     * multiple low-level operations being tracked that should be
     * presented to the user as one switch then this can be used to
     * make them all controlled by the same single operation.
     */
    private static int[] sOpToSwitch = new int[] {
            OP_COARSE_LOCATION,
            OP_COARSE_LOCATION,
            OP_COARSE_LOCATION,
            OP_VIBRATE,
            OP_READ_CONTACTS,
            OP_WRITE_CONTACTS,
            OP_READ_CALL_LOG,
            OP_WRITE_CALL_LOG,
            OP_READ_CALENDAR,
            OP_WRITE_CALENDAR,
            OP_COARSE_LOCATION,
            OP_POST_NOTIFICATION,
            OP_COARSE_LOCATION,
            OP_CALL_PHONE,
            OP_READ_SMS,
            OP_WRITE_SMS,
            OP_RECEIVE_SMS,
            OP_RECEIVE_SMS,
            OP_RECEIVE_SMS,
            OP_RECEIVE_SMS,
            OP_SEND_SMS,
            OP_READ_SMS,
            OP_WRITE_SMS,
            OP_WRITE_SETTINGS,
            OP_SYSTEM_ALERT_WINDOW,
            OP_ACCESS_NOTIFICATIONS,
            OP_CAMERA,
            OP_RECORD_AUDIO,
            OP_PLAY_AUDIO,
            OP_READ_CLIPBOARD,
            OP_WRITE_CLIPBOARD,
            OP_TAKE_MEDIA_BUTTONS,
            OP_TAKE_AUDIO_FOCUS,
            OP_AUDIO_MASTER_VOLUME,
            OP_AUDIO_VOICE_VOLUME,
            OP_AUDIO_RING_VOLUME,
            OP_AUDIO_MEDIA_VOLUME,
            OP_AUDIO_ALARM_VOLUME,
            OP_AUDIO_NOTIFICATION_VOLUME,
            OP_AUDIO_BLUETOOTH_VOLUME,
            OP_WAKE_LOCK,
            OP_COARSE_LOCATION,
            OP_COARSE_LOCATION,
            OP_GET_USAGE_STATS,
            OP_MUTE_MICROPHONE,
            OP_TOAST_WINDOW,
            OP_PROJECT_MEDIA,
            OP_ACTIVATE_VPN,
            OP_WIFI_CHANGE,
            OP_BLUETOOTH_CHANGE,
            OP_SEND_MMS,
            OP_READ_MMS,
            OP_WRITE_MMS,
            OP_BOOT_COMPLETED,
            OP_NFC_CHANGE,
            OP_DELETE_SMS,
            OP_DELETE_MMS,
            OP_DELETE_CONTACTS,
            OP_DELETE_CALL_LOG,
            OP_DATA_CONNECT_CHANGE,
            OP_ALARM_WAKEUP,
            OP_SU
    };

    /**
     * This maps each operation to the public string constant for it.
     * If it doesn't have a public string constant, it maps to null.
     */
    private static String[] sOpToString = new String[] {
            OPSTR_COARSE_LOCATION,
            OPSTR_FINE_LOCATION,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            OPSTR_MONITOR_LOCATION,
            OPSTR_MONITOR_HIGH_POWER_LOCATION,
            OPSTR_GET_USAGE_STATS,
            null,
            null,
            null,
            OPSTR_ACTIVATE_VPN,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            OPSTR_SU,
    };

    /**
     * This maps each operation to the public string constant for it.
     * If it doesn't have a public string constant, it maps to null.
     */
    private static String[] sOpToOpString = new String[] {
        OPSTR_COARSE_LOCATION,
        OPSTR_FINE_LOCATION,
        OPSTR_GPS,
        OPSTR_VIBRATE,
        OPSTR_READ_CONTACTS,
        OPSTR_WRITE_CONTACTS,
        OPSTR_READ_CALL_LOG,
        OPSTR_WRITE_CALL_LOG,
        OPSTR_READ_CALENDAR,
        OPSTR_WRITE_CALENDAR,
        OPSTR_WIFI_SCAN,
        OPSTR_POST_NOTIFICATION,
        OPSTR_NEIGHBORING_CELLS,
        OPSTR_CALL_PHONE,
        OPSTR_READ_SMS,
        OPSTR_WRITE_SMS,
        OPSTR_RECEIVE_SMS,
        OPSTR_RECEIVE_EMERGECY_SMS,
        OPSTR_RECEIVE_MMS,
        OPSTR_RECEIVE_WAP_PUSH,
        OPSTR_SEND_SMS,
        OPSTR_READ_ICC_SMS,
        OPSTR_WRITE_ICC_SMS,
        OPSTR_WRITE_SETTINGS,
        OPSTR_SYSTEM_ALERT_WINDOW,
        OPSTR_ACCESS_NOTIFICATIONS,
        OPSTR_CAMERA,
        OPSTR_RECORD_AUDIO,
        OPSTR_PLAY_AUDIO,
        OPSTR_READ_CLIPBOARD,
        OPSTR_WRITE_CLIPBOARD,
        OPSTR_TAKE_MEDIA_BUTTONS,
        OPSTR_TAKE_AUDIO_FOCUS,
        OPSTR_AUDIO_MASTER_VOLUME,
        OPSTR_AUDIO_VOICE_VOLUME,
        OPSTR_AUDIO_RING_VOLUME,
        OPSTR_AUDIO_MEDIA_VOLUME,
        OPSTR_AUDIO_ALARM_VOLUME,
        OPSTR_AUDIO_NOTIFICATION_VOLUME,
        OPSTR_AUDIO_BLUETOOTH_VOLUME,
        OPSTR_WAKE_LOCK,
        OPSTR_MONITOR_LOCATION,
        OPSTR_MONITOR_HIGH_POWER_LOCATION,
        OPSTR_GET_USAGE_STATS,
        OPSTR_MUTE_MICROPHONE,
        OPSTR_TOAST_WINDOW,
        OPSTR_PROJECT_MEDIA,
        OPSTR_ACTIVATE_VPN,
        OPSTR_WIFI_CHANGE,
        OPSTR_BLUETOOTH_CHANGE,
        OPSTR_SEND_MMS,
        OPSTR_READ_MMS,
        OPSTR_WRITE_MMS,
        OPSTR_BOOT_COMPLETED,
        OPSTR_NFC_CHANGE,
        OPSTR_DELETE_SMS,
        OPSTR_DELETE_MMS,
        OPSTR_DELETE_CONTACTS,
        OPSTR_DELETE_CALL_LOG,
        OPSTR_DATA_CONNECT_CHANGE,
        OPSTR_ALARM_WAKEUP,
        OPSTR_SU,
    };

    /**
     * This provides a simple name for each operation to be used
     * in debug output.
     */
    private static String[] sOpNames = new String[] {
            "COARSE_LOCATION",
            "FINE_LOCATION",
            "GPS",
            "VIBRATE",
            "READ_CONTACTS",
            "WRITE_CONTACTS",
            "READ_CALL_LOG",
            "WRITE_CALL_LOG",
            "READ_CALENDAR",
            "WRITE_CALENDAR",
            "WIFI_SCAN",
            "POST_NOTIFICATION",
            "NEIGHBORING_CELLS",
            "CALL_PHONE",
            "READ_SMS",
            "WRITE_SMS",
            "RECEIVE_SMS",
            "RECEIVE_EMERGECY_SMS",
            "RECEIVE_MMS",
            "RECEIVE_WAP_PUSH",
            "SEND_SMS",
            "READ_ICC_SMS",
            "WRITE_ICC_SMS",
            "WRITE_SETTINGS",
            "SYSTEM_ALERT_WINDOW",
            "ACCESS_NOTIFICATIONS",
            "CAMERA",
            "RECORD_AUDIO",
            "PLAY_AUDIO",
            "READ_CLIPBOARD",
            "WRITE_CLIPBOARD",
            "TAKE_MEDIA_BUTTONS",
            "TAKE_AUDIO_FOCUS",
            "AUDIO_MASTER_VOLUME",
            "AUDIO_VOICE_VOLUME",
            "AUDIO_RING_VOLUME",
            "AUDIO_MEDIA_VOLUME",
            "AUDIO_ALARM_VOLUME",
            "AUDIO_NOTIFICATION_VOLUME",
            "AUDIO_BLUETOOTH_VOLUME",
            "WAKE_LOCK",
            "MONITOR_LOCATION",
            "MONITOR_HIGH_POWER_LOCATION",
            "GET_USAGE_STATS",
            "MUTE_MICROPHONE",
            "TOAST_WINDOW",
            "PROJECT_MEDIA",
            "ACTIVATE_VPN",
            "WIFI_CHANGE",
            "BLUETOOTH_CHANGE",
            "SEND_MMS",
            "READ_MMS",
            "WRITE_MMS",
            "BOOT_COMPLETED",
            "NFC_CHANGE",
            "DELETE_SMS",
            "DELETE_MMS",
            "DELETE_CONTACTS",
            "DELETE_CALL_LOG",
            "DATA_CONNECT_CHANGE",
            "ALARM_WAKEUP",
            "SU",
    };

    /**
     * This optionally maps a permission to an operation.  If there
     * is no permission associated with an operation, it is null.
     */
    private static String[] sOpPerms = new String[] {
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            null,
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            null, // no permission required for notifications
            null, // neighboring cells shares the coarse location perm
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.WRITE_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST,
            android.Manifest.permission.RECEIVE_MMS,
            android.Manifest.permission.RECEIVE_WAP_PUSH,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.WRITE_SMS,
            android.Manifest.permission.WRITE_SETTINGS,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW,
            android.Manifest.permission.ACCESS_NOTIFICATIONS,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            null, // no permission for playing audio
            null, // no permission for reading clipboard
            null, // no permission for writing clipboard
            null, // no permission for taking media buttons
            null, // no permission for taking audio focus
            null, // no permission for changing master volume
            null, // no permission for changing voice volume
            null, // no permission for changing ring volume
            null, // no permission for changing media volume
            null, // no permission for changing alarm volume
            null, // no permission for changing notification volume
            null, // no permission for changing bluetooth volume
            android.Manifest.permission.WAKE_LOCK,
            null, // no permission for generic location monitoring
            null, // no permission for high power location monitoring
            android.Manifest.permission.PACKAGE_USAGE_STATS,
            null, // no permission for muting/unmuting microphone
            null, // no permission for displaying toasts
            null, // no permission for projecting media
            null, // no permission for activating vpn
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.WRITE_SMS,
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            android.Manifest.permission.NFC,
            android.Manifest.permission.WRITE_SMS,
            android.Manifest.permission.WRITE_SMS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.MODIFY_PHONE_STATE,
            null, // OP_ALARM_WAKEUP
            null,
    };

    /**
     * Specifies whether an Op should be restricted by a user restriction.
     * Each Op should be filled with a restriction string from UserManager or
     * null to specify it is not affected by any user restriction.
     */
    private static String[] sOpRestrictions = new String[] {
            UserManager.DISALLOW_SHARE_LOCATION, //COARSE_LOCATION
            UserManager.DISALLOW_SHARE_LOCATION, //FINE_LOCATION
            UserManager.DISALLOW_SHARE_LOCATION, //GPS
            null, //VIBRATE
            null, //READ_CONTACTS
            null, //WRITE_CONTACTS
            UserManager.DISALLOW_OUTGOING_CALLS, //READ_CALL_LOG
            UserManager.DISALLOW_OUTGOING_CALLS, //WRITE_CALL_LOG
            null, //READ_CALENDAR
            null, //WRITE_CALENDAR
            UserManager.DISALLOW_SHARE_LOCATION, //WIFI_SCAN
            null, //POST_NOTIFICATION
            null, //NEIGHBORING_CELLS
            null, //CALL_PHONE
            UserManager.DISALLOW_SMS, //READ_SMS
            UserManager.DISALLOW_SMS, //WRITE_SMS
            UserManager.DISALLOW_SMS, //RECEIVE_SMS
            null, //RECEIVE_EMERGENCY_SMS
            UserManager.DISALLOW_SMS, //RECEIVE_MMS
            null, //RECEIVE_WAP_PUSH
            UserManager.DISALLOW_SMS, //SEND_SMS
            UserManager.DISALLOW_SMS, //READ_ICC_SMS
            UserManager.DISALLOW_SMS, //WRITE_ICC_SMS
            null, //WRITE_SETTINGS
            UserManager.DISALLOW_CREATE_WINDOWS, //SYSTEM_ALERT_WINDOW
            null, //ACCESS_NOTIFICATIONS
            null, //CAMERA
            null, //RECORD_AUDIO
            null, //PLAY_AUDIO
            null, //READ_CLIPBOARD
            null, //WRITE_CLIPBOARD
            null, //TAKE_MEDIA_BUTTONS
            null, //TAKE_AUDIO_FOCUS
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_MASTER_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_VOICE_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_RING_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_MEDIA_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_ALARM_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_NOTIFICATION_VOLUME
            UserManager.DISALLOW_ADJUST_VOLUME, //AUDIO_BLUETOOTH_VOLUME
            null, //WAKE_LOCK
            UserManager.DISALLOW_SHARE_LOCATION, //MONITOR_LOCATION
            UserManager.DISALLOW_SHARE_LOCATION, //MONITOR_HIGH_POWER_LOCATION
            null, //GET_USAGE_STATS
            UserManager.DISALLOW_UNMUTE_MICROPHONE, // MUTE_MICROPHONE
            UserManager.DISALLOW_CREATE_WINDOWS, // TOAST_WINDOW
            null, //PROJECT_MEDIA
            UserManager.DISALLOW_CONFIG_VPN, // ACTIVATE_VPN
            null, //WIFI_CHANGE
            null, //BLUETOOTH_CHANGE
            null, //SEND_MMS
            null, //READ_MMS
            null, //WRITE_MMS
            null, //BOOT_COMPLETED
            null, //NFC_CHANGE
            null, //DELETE_SMS
            null, //DELETE_MMS
            null, //DELETE_CONTACTS
            null, //DELETE_CALL_LOG
            null, //DATA_CONNECT_CHANGE
            null, //ALARM_WAKEUP
            UserManager.DISALLOW_SU, //SU TODO: this should really be investigated.
    };

    /**
     * This specifies whether each option should allow the system
     * (and system ui) to bypass the user restriction when active.
     */
    private static boolean[] sOpAllowSystemRestrictionBypass = new boolean[] {
            false, //COARSE_LOCATION
            false, //FINE_LOCATION
            false, //GPS
            false, //VIBRATE
            false, //READ_CONTACTS
            false, //WRITE_CONTACTS
            false, //READ_CALL_LOG
            false, //WRITE_CALL_LOG
            false, //READ_CALENDAR
            false, //WRITE_CALENDAR
            true, //WIFI_SCAN
            false, //POST_NOTIFICATION
            false, //NEIGHBORING_CELLS
            false, //CALL_PHONE
            false, //READ_SMS
            false, //WRITE_SMS
            false, //RECEIVE_SMS
            false, //RECEIVE_EMERGECY_SMS
            false, //RECEIVE_MMS
            false, //RECEIVE_WAP_PUSH
            false, //SEND_SMS
            false, //READ_ICC_SMS
            false, //WRITE_ICC_SMS
            false, //WRITE_SETTINGS
            true, //SYSTEM_ALERT_WINDOW
            false, //ACCESS_NOTIFICATIONS
            false, //CAMERA
            false, //RECORD_AUDIO
            false, //PLAY_AUDIO
            false, //READ_CLIPBOARD
            false, //WRITE_CLIPBOARD
            false, //TAKE_MEDIA_BUTTONS
            false, //TAKE_AUDIO_FOCUS
            false, //AUDIO_MASTER_VOLUME
            false, //AUDIO_VOICE_VOLUME
            false, //AUDIO_RING_VOLUME
            false, //AUDIO_MEDIA_VOLUME
            false, //AUDIO_ALARM_VOLUME
            false, //AUDIO_NOTIFICATION_VOLUME
            false, //AUDIO_BLUETOOTH_VOLUME
            false, //WAKE_LOCK
            false, //MONITOR_LOCATION
            false, //MONITOR_HIGH_POWER_LOCATION
            false, //GET_USAGE_STATS
            false, //MUTE_MICROPHONE
            true, //TOAST_WINDOW
            false, //PROJECT_MEDIA
            false, //ACTIVATE_VPN
            false, // WIFI_CHANGE
            false, // BLUETOOTH_CHANGE
            false, // SEND_MMS
            false, // READ_MMS
            false, // WRITE_MMS
            false, // BOOT_COMPLETED
            false, // NFC_CHANGE
            false, //DELETE_SMS
            false, //DELETE_MMS
            false, //DELETE_CONTACTS
            false, //DELETE_CALL_LOG
            false, //DATA_CONNECT_CHANGE
            true, //ALARM_WAKEUP
            false, //SU
    };

    /**
     * This specifies the default mode for each operation.
     */
    private static int[] sOpDefaultMode = new int[] {
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_IGNORED, // OP_WRITE_SMS
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_DEFAULT, // OP_GET_USAGE_STATS
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_IGNORED, // OP_PROJECT_MEDIA
            AppOpsManager.MODE_IGNORED, // OP_ACTIVATE_VPN
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED,
            AppOpsManager.MODE_ALLOWED, // OP_ALARM_WAKEUP
            AppOpsManager.MODE_ASK, // OP_SU
    };

    /**
     * This specifies the default mode for each strict operation.
     */

    private static int[] sOpDefaultStrictMode = new int[] {
            AppOpsManager.MODE_ASK,     // OP_COARSE_LOCATION
            AppOpsManager.MODE_ASK,     // OP_FINE_LOCATION
            AppOpsManager.MODE_ASK,     // OP_GPS
            AppOpsManager.MODE_ALLOWED, // OP_VIBRATE
            AppOpsManager.MODE_ASK,     // OP_READ_CONTACTS
            AppOpsManager.MODE_ASK,     // OP_WRITE_CONTACTS
            AppOpsManager.MODE_ASK,     // OP_READ_CALL_LOG
            AppOpsManager.MODE_ASK,     // OP_WRITE_CALL_LOG
            AppOpsManager.MODE_ALLOWED, // OP_READ_CALENDAR
            AppOpsManager.MODE_ALLOWED, // OP_WRITE_CALENDAR
            AppOpsManager.MODE_ASK,     // OP_WIFI_SCAN
            AppOpsManager.MODE_ALLOWED, // OP_POST_NOTIFICATION
            AppOpsManager.MODE_ALLOWED, // OP_NEIGHBORING_CELLS
            AppOpsManager.MODE_ASK,     // OP_CALL_PHONE
            AppOpsManager.MODE_ASK,     // OP_READ_SMS
            AppOpsManager.MODE_ASK,     // OP_WRITE_SMS
            AppOpsManager.MODE_ALLOWED, // OP_RECEIVE_SMS
            AppOpsManager.MODE_ALLOWED, // OP_RECEIVE_EMERGECY_SMS
            AppOpsManager.MODE_ALLOWED, // OP_RECEIVE_MMS
            AppOpsManager.MODE_ALLOWED, // OP_RECEIVE_WAP_PUSH
            AppOpsManager.MODE_ASK,     // OP_SEND_SMS
            AppOpsManager.MODE_ALLOWED, // OP_READ_ICC_SMS
            AppOpsManager.MODE_ALLOWED, // OP_WRITE_ICC_SMS
            AppOpsManager.MODE_ALLOWED, // OP_WRITE_SETTINGS
            AppOpsManager.MODE_ALLOWED, // OP_SYSTEM_ALERT_WINDOW
            AppOpsManager.MODE_ALLOWED, // OP_ACCESS_NOTIFICATIONS
            AppOpsManager.MODE_ASK,     // OP_CAMERA
            AppOpsManager.MODE_ASK,     // OP_RECORD_AUDIO
            AppOpsManager.MODE_ALLOWED, // OP_PLAY_AUDIO
            AppOpsManager.MODE_ALLOWED, // OP_READ_CLIPBOARD
            AppOpsManager.MODE_ALLOWED, // OP_WRITE_CLIPBOARD
            AppOpsManager.MODE_ALLOWED, // OP_TAKE_MEDIA_BUTTONS
            AppOpsManager.MODE_ALLOWED, // OP_TAKE_AUDIO_FOCUS
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_MASTER_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_VOICE_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_RING_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_MEDIA_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_ALARM_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_NOTIFICATION_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_AUDIO_BLUETOOTH_VOLUME
            AppOpsManager.MODE_ALLOWED, // OP_WAKE_LOCK
            AppOpsManager.MODE_ALLOWED, // OP_MONITOR_LOCATION
            AppOpsManager.MODE_ASK,     // OP_MONITOR_HIGH_POWER_LOCATION
            AppOpsManager.MODE_DEFAULT, // OP_GET_USAGE_STATS
            AppOpsManager.MODE_ALLOWED, // OP_MUTE_MICROPHONE
            AppOpsManager.MODE_ALLOWED, // OP_TOAST_WINDOW
            AppOpsManager.MODE_IGNORED, // OP_PROJECT_MEDIA
            AppOpsManager.MODE_IGNORED, // OP_ACTIVATE_VPN
            AppOpsManager.MODE_ASK,     // OP_WIFI_CHANGE
            AppOpsManager.MODE_ASK,     // OP_BLUETOOTH_CHANGE
            AppOpsManager.MODE_ASK,     // OP_SEND_MMS
            AppOpsManager.MODE_ASK,     // OP_READ_MMS
            AppOpsManager.MODE_ASK,     // OP_WRITE_MMS
            AppOpsManager.MODE_ALLOWED, // OP_BOOT_COMPLETED
            AppOpsManager.MODE_ASK,     // OP_NFC_CHANGE
            AppOpsManager.MODE_ASK,     // OP_DELETE_SMS
            AppOpsManager.MODE_ASK,     // OP_DELETE_MMS
            AppOpsManager.MODE_ASK,     // OP_DELETE_CONTACTS
            AppOpsManager.MODE_ASK,     // OP_DELETE_CALL_LOG
            AppOpsManager.MODE_ASK,     // OP_DATA_CONNECT_CHANGE
            AppOpsManager.MODE_ALLOWED, // OP_ALARM_WAKEUP
            AppOpsManager.MODE_ASK,     // OP_SU
    };

    /**
     * This specifies if operation is in strict mode.
     */
    private final static boolean[] sOpStrictMode = new boolean[] {
        true,     // OP_COARSE_LOCATION
        true,     // OP_FINE_LOCATION
        true,     // OP_GPS
        false,    // OP_VIBRATE
        true,     // OP_READ_CONTACTS
        true,     // OP_WRITE_CONTACTS
        true,     // OP_READ_CALL_LOG
        true,     // OP_WRITE_CALL_LOG
        false,    // OP_READ_CALENDAR
        false,    // OP_WRITE_CALENDAR
        true,     // OP_WIFI_SCAN
        false,    // OP_POST_NOTIFICATION
        false,    // OP_NEIGHBORING_CELLS
        true,     // OP_CALL_PHONE
        true,     // OP_READ_SMS
        true,     // OP_WRITE_SMS
        false,    // OP_RECEIVE_SMS
        false,    // OP_RECEIVE_EMERGECY_SMS
        false,    // OP_RECEIVE_MMS
        false,    // OP_RECEIVE_WAP_PUSH
        true,     // OP_SEND_SMS
        false,    // OP_READ_ICC_SMS
        false,    // OP_WRITE_ICC_SMS
        false,    // OP_WRITE_SETTINGS
        false,    // OP_SYSTEM_ALERT_WINDOW
        false,    // OP_ACCESS_NOTIFICATIONS
        true,     // OP_CAMERA
        true,     // OP_RECORD_AUDIO
        false,    // OP_PLAY_AUDIO
        false,    // OP_READ_CLIPBOARD
        false,    // OP_WRITE_CLIPBOARD
        false,    // OP_TAKE_MEDIA_BUTTONS
        false,    // OP_TAKE_AUDIO_FOCUS
        false,    // OP_AUDIO_MASTER_VOLUME
        false,    // OP_AUDIO_VOICE_VOLUME
        false,    // OP_AUDIO_RING_VOLUME
        false,    // OP_AUDIO_MEDIA_VOLUME
        false,    // OP_AUDIO_ALARM_VOLUME
        false,    // OP_AUDIO_NOTIFICATION_VOLUME
        false,    // OP_AUDIO_BLUETOOTH_VOLUME
        false,    // OP_WAKE_LOCK
        false,    // OP_MONITOR_LOCATION
        true,     // OP_MONITOR_HIGH_POWER_LOCATION
        false,    // OP_GET_USAGE_STATS
        false,    // OP_MUTE_MICROPHONE
        false,    // OP_TOAST_WINDOW
        false,    // OP_PROJECT_MEDIA
        false,    // OP_ACTIVATE_VPN
        true,     // OP_WIFI_CHANGE
        true,     // OP_BLUETOOTH_CHANGE
        true,     // OP_SEND_MMS
        true,     // OP_READ_MMS
        true,     // OP_WRITE_MMS
        false,    // OP_BOOT_COMPLETED
        true,     // OP_NFC_CHANGE
        true,     // OP_DELETE_SMS
        true,     // OP_DELETE_MMS
        true,     // OP_DELETE_CONTACTS
        true,     // OP_DELETE_CALL_LOG
        true,     // OP_DATA_CONNECT_CHANGE
        false,    // OP_ALARM_WAKEUP
        true,     // OP_SU
    };

    /**
     * This specifies whether each option is allowed to be reset
     * when resetting all app preferences.  Disable reset for
     * app ops that are under strong control of some part of the
     * system (such as OP_WRITE_SMS, which should be allowed only
     * for whichever app is selected as the current SMS app).
     */
    private static boolean[] sOpDisableReset = new boolean[] {
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            true,      // OP_WRITE_SMS
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,     // OP_WIFI_CHANGE
            false,     // OP_BLUETOOTH_CHANGE
            false,     // OP_SEND_MMS
            false,     // OP_READ_MMS
            false,     // OP_WRITE_MMS
            false,     // OP_BOOT_COMPLETED
            false,     // OP_NFC_CHANGE
            false,     // OP_DELETE_SMS
            false,     // OP_DELETE_MMS
            false,     // OP_DELETE_CONTACTS
            false,     // OP_DELETE_CALL_LOG
            false,     // OP_DATA_CONNECT_CHANGE
            false,     // OP_ALARM_WAKEUP
            false,     // OP_SU
    };

    private static HashMap<String, Integer> sOpStrToOp = new HashMap<String, Integer>();
    private static HashMap<String, Integer> sOpStringToOp = new HashMap<String, Integer>();

    private static HashMap<String, Integer> sNameToOp = new HashMap<String, Integer>();

    static {
        if (sOpToSwitch.length != _NUM_OP) {
            throw new IllegalStateException("sOpToSwitch length " + sOpToSwitch.length
                    + " should be " + _NUM_OP);
        }
        if (sOpToString.length != _NUM_OP) {
            throw new IllegalStateException("sOpToString length " + sOpToString.length
                    + " should be " + _NUM_OP);
        }
        if (sOpToOpString.length != _NUM_OP) {
            throw new IllegalStateException("sOpToOpString length " + sOpToOpString.length
                    + " should be " + _NUM_OP);
        }
        if (sOpNames.length != _NUM_OP) {
            throw new IllegalStateException("sOpNames length " + sOpNames.length
                    + " should be " + _NUM_OP);
        }
        if (sOpPerms.length != _NUM_OP) {
            throw new IllegalStateException("sOpPerms length " + sOpPerms.length
                    + " should be " + _NUM_OP);
        }
        if (sOpDefaultMode.length != _NUM_OP) {
            throw new IllegalStateException("sOpDefaultMode length " + sOpDefaultMode.length
                    + " should be " + _NUM_OP);
        }
        if (sOpDefaultStrictMode.length != _NUM_OP) {
            throw new IllegalStateException("sOpDefaultStrictMode length " + sOpDefaultStrictMode.length
                    + " should be " + _NUM_OP);
        }
        if (sOpDisableReset.length != _NUM_OP) {
            throw new IllegalStateException("sOpDisableReset length " + sOpDisableReset.length
                    + " should be " + _NUM_OP);
        }
        if (sOpRestrictions.length != _NUM_OP) {
            throw new IllegalStateException("sOpRestrictions length " + sOpRestrictions.length
                    + " should be " + _NUM_OP);
        }
        if (sOpAllowSystemRestrictionBypass.length != _NUM_OP) {
            throw new IllegalStateException("sOpAllowSYstemRestrictionsBypass length "
                    + sOpRestrictions.length + " should be " + _NUM_OP);
        }
        if (sOpStrictMode.length != _NUM_OP) {
            throw new IllegalStateException("sOpStrictMode length "
                    + sOpStrictMode.length + " should be " + _NUM_OP);
        }
        for (int i=0; i<_NUM_OP; i++) {
            if (sOpToString[i] != null) {
                sOpStrToOp.put(sOpToString[i], i);
            }
            if (sOpToOpString[i] != null) {
                sOpStringToOp.put(sOpToOpString[i], i);
            }
        }
        for (int i=0; i<_NUM_OP; i++) {
            sNameToOp.put(sOpNames[i], i);
        }
    }

    /**
     * Retrieve the op switch that controls the given operation.
     * @hide
     */
    public static int opToSwitch(int op) {
        return sOpToSwitch[op];
    }

    /**
     * Retrieve a non-localized name for the operation, for debugging output.
     * @hide
     */
    public static String opToName(int op) {
        if (op == OP_NONE) return "NONE";
        return op < sOpNames.length ? sOpNames[op] : ("Unknown(" + op + ")");
    }

    /**
     * Map a non-localized name for the operation back to the Op number
     * @hide
     */
    public static int nameToOp(String name) {
        Integer val = sNameToOp.get(name);
        return val != null ? val : OP_NONE;
    }

    /**
     * Retrieve the permission associated with an operation, or null if there is not one.
     * @hide
     */
    public static String opToPermission(int op) {
        return sOpPerms[op];
    }

    /**
     * Retrieve the user restriction associated with an operation, or null if there is not one.
     * @hide
     */
    public static String opToRestriction(int op) {
        return sOpRestrictions[op];
    }

    /**
     * Retrieve whether the op allows the system (and system ui) to
     * bypass the user restriction.
     * @hide
     */
    public static boolean opAllowSystemBypassRestriction(int op) {
        return sOpAllowSystemRestrictionBypass[op];
    }

    /**
     * Retrieve the default mode for the operation.
     * @hide
     */
    public static int opToDefaultMode(int op, boolean isStrict) {
        if (isStrict)
            return sOpDefaultStrictMode[op];
        return sOpDefaultMode[op];
    }

    /**
     * Retrieve whether the op allows itself to be reset.
     * @hide
     */
    public static boolean opAllowsReset(int op) {
        return !sOpDisableReset[op];
    }

    /**
     * Class holding all of the operation information associated with an app.
     * @hide
     */
    public static class PackageOps implements Parcelable {
        private final String mPackageName;
        private final int mUid;
        private final List<OpEntry> mEntries;

        public PackageOps(String packageName, int uid, List<OpEntry> entries) {
            mPackageName = packageName;
            mUid = uid;
            mEntries = entries;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public int getUid() {
            return mUid;
        }

        public List<OpEntry> getOps() {
            return mEntries;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mPackageName);
            dest.writeInt(mUid);
            dest.writeInt(mEntries.size());
            for (int i=0; i<mEntries.size(); i++) {
                mEntries.get(i).writeToParcel(dest, flags);
            }
        }

        PackageOps(Parcel source) {
            mPackageName = source.readString();
            mUid = source.readInt();
            mEntries = new ArrayList<OpEntry>();
            final int N = source.readInt();
            for (int i=0; i<N; i++) {
                mEntries.add(OpEntry.CREATOR.createFromParcel(source));
            }
        }

        public static final Creator<PackageOps> CREATOR = new Creator<PackageOps>() {
            @Override public PackageOps createFromParcel(Parcel source) {
                return new PackageOps(source);
            }

            @Override public PackageOps[] newArray(int size) {
                return new PackageOps[size];
            }
        };
    }

    /**
     * Class holding the information about one unique operation of an application.
     * @hide
     */
    public static class OpEntry implements Parcelable {
        private final int mOp;
        private final int mMode;
        private final long mTime;
        private final long mRejectTime;
        private final int mDuration;
        private final int mAllowedCount;
        private final int mIgnoredCount;

        public OpEntry(int op, int mode, long time, long rejectTime, int duration,
                int allowedCount, int ignoredCount) {
            mOp = op;
            mMode = mode;
            mTime = time;
            mRejectTime = rejectTime;
            mDuration = duration;
            mAllowedCount = allowedCount;
            mIgnoredCount = ignoredCount;
        }

        public int getOp() {
            return mOp;
        }

        public int getMode() {
            return mMode;
        }

        public long getTime() {
            return mTime;
        }

        public long getRejectTime() {
            return mRejectTime;
        }

        public boolean isRunning() {
            return mDuration == -1;
        }

        public int getDuration() {
            return mDuration == -1 ? (int)(System.currentTimeMillis()-mTime) : mDuration;
        }

        public int getAllowedCount() {
            return mAllowedCount;
        }

        public int getIgnoredCount() {
            return mIgnoredCount;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mOp);
            dest.writeInt(mMode);
            dest.writeLong(mTime);
            dest.writeLong(mRejectTime);
            dest.writeInt(mDuration);
            dest.writeInt(mAllowedCount);
            dest.writeInt(mIgnoredCount);
        }

        OpEntry(Parcel source) {
            mOp = source.readInt();
            mMode = source.readInt();
            mTime = source.readLong();
            mRejectTime = source.readLong();
            mDuration = source.readInt();
            mAllowedCount = source.readInt();
            mIgnoredCount = source.readInt();
        }

        public static final Creator<OpEntry> CREATOR = new Creator<OpEntry>() {
            @Override public OpEntry createFromParcel(Parcel source) {
                return new OpEntry(source);
            }

            @Override public OpEntry[] newArray(int size) {
                return new OpEntry[size];
            }
        };
    }

    /**
     * Callback for notification of changes to operation state.
     */
    public interface OnOpChangedListener {
        public void onOpChanged(String op, String packageName);
    }

    /**
     * Callback for notification of changes to operation state.
     * This allows you to see the raw op codes instead of strings.
     * @hide
     */
    public static class OnOpChangedInternalListener implements OnOpChangedListener {
        public void onOpChanged(String op, String packageName) { }
        public void onOpChanged(int op, String packageName) { }
    }

    AppOpsManager(Context context, IAppOpsService service) {
        mContext = context;
        mService = service;
    }

    /**
     * Retrieve current operation state for all applications.
     *
     * @param ops The set of operations you are interested in, or null if you want all of them.
     * @hide
     */
    public List<AppOpsManager.PackageOps> getPackagesForOps(int[] ops) {
        try {
            return mService.getPackagesForOps(ops);
        } catch (RemoteException e) {
        }
        return null;
    }

    /**
     * Retrieve current operation state for one application.
     *
     * @param uid The uid of the application of interest.
     * @param packageName The name of the application of interest.
     * @param ops The set of operations you are interested in, or null if you want all of them.
     * @hide
     */
    public List<AppOpsManager.PackageOps> getOpsForPackage(int uid, String packageName, int[] ops) {
        try {
            return mService.getOpsForPackage(uid, packageName, ops);
        } catch (RemoteException e) {
        }
        return null;
    }

    /** @hide */
    public void setMode(int code, int uid, String packageName, int mode) {
        try {
            mService.setMode(code, uid, packageName, mode);
        } catch (RemoteException e) {
        }
    }

    /**
     * Set a non-persisted restriction on an audio operation at a stream-level.
     * Restrictions are temporary additional constraints imposed on top of the persisted rules
     * defined by {@link #setMode}.
     *
     * @param code The operation to restrict.
     * @param usage The {@link android.media.AudioAttributes} usage value.
     * @param mode The restriction mode (MODE_IGNORED,MODE_ERRORED) or MODE_ALLOWED to unrestrict.
     * @param exceptionPackages Optional list of packages to exclude from the restriction.
     * @hide
     */
    public void setRestriction(int code, @AttributeUsage int usage, int mode,
            String[] exceptionPackages) {
        try {
            final int uid = Binder.getCallingUid();
            mService.setAudioRestriction(code, usage, uid, mode, exceptionPackages);
        } catch (RemoteException e) {
        }
    }

    /** @hide */
    public void resetAllModes() {
        try {
            mService.resetAllModes();
        } catch (RemoteException e) {
        }
    }

    /**
     * Monitor for changes to the operating mode for the given op in the given app package.
     * @param op The operation to monitor, one of OPSTR_*.
     * @param packageName The name of the application to monitor.
     * @param callback Where to report changes.
     */
    public void startWatchingMode(String op, String packageName,
            final OnOpChangedListener callback) {
        startWatchingMode(strOpToOp(op), packageName, callback);
    }

    /**
     * Monitor for changes to the operating mode for the given op in the given app package.
     * @param op The operation to monitor, one of OP_*.
     * @param packageName The name of the application to monitor.
     * @param callback Where to report changes.
     * @hide
     */
    public void startWatchingMode(int op, String packageName, final OnOpChangedListener callback) {
        synchronized (mModeWatchers) {
            IAppOpsCallback cb = mModeWatchers.get(callback);
            if (cb == null) {
                cb = new IAppOpsCallback.Stub() {
                    public void opChanged(int op, String packageName) {
                        if (callback instanceof OnOpChangedInternalListener) {
                            ((OnOpChangedInternalListener)callback).onOpChanged(op, packageName);
                        }
                        if (sOpToString[op] != null) {
                            callback.onOpChanged(sOpToString[op], packageName);
                        }
                    }
                };
                mModeWatchers.put(callback, cb);
            }
            try {
                mService.startWatchingMode(op, packageName, cb);
            } catch (RemoteException e) {
            }
        }
    }

    /**
     * Stop monitoring that was previously started with {@link #startWatchingMode}.  All
     * monitoring associated with this callback will be removed.
     */
    public void stopWatchingMode(OnOpChangedListener callback) {
        synchronized (mModeWatchers) {
            IAppOpsCallback cb = mModeWatchers.get(callback);
            if (cb != null) {
                try {
                    mService.stopWatchingMode(cb);
                } catch (RemoteException e) {
                }
            }
        }
    }

    private String buildSecurityExceptionMsg(int op, int uid, String packageName) {
        return packageName + " from uid " + uid + " not allowed to perform " + sOpNames[op];
    }

    /**
     * {@hide}
     */
    public static int strOpToOp(String op) {
        Integer val = sOpStrToOp.get(op);
        if (val == null) {
            throw new IllegalArgumentException("Unknown operation string: " + op);
        }
        return val;
    }

    /**
     * Do a quick check for whether an application might be able to perform an operation.
     * This is <em>not</em> a security check; you must use {@link #noteOp(String, int, String)}
     * or {@link #startOp(String, int, String)} for your actual security checks, which also
     * ensure that the given uid and package name are consistent.  This function can just be
     * used for a quick check to see if an operation has been disabled for the application,
     * as an early reject of some work.  This does not modify the time stamp or other data
     * about the operation.
     * @param op The operation to check.  One of the OPSTR_* constants.
     * @param uid The user id of the application attempting to perform the operation.
     * @param packageName The name of the application attempting to perform the operation.
     * @return Returns {@link #MODE_ALLOWED} if the operation is allowed, or
     * {@link #MODE_IGNORED} if it is not allowed and should be silently ignored (without
     * causing the app to crash).
     * @throws SecurityException If the app has been configured to crash on this op.
     */
    public int checkOp(String op, int uid, String packageName) {
        return checkOp(strOpToOp(op), uid, packageName);
    }

    /**
     * Like {@link #checkOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     */
    public int checkOpNoThrow(String op, int uid, String packageName) {
        return checkOpNoThrow(strOpToOp(op), uid, packageName);
    }

    /**
     * Make note of an application performing an operation.  Note that you must pass
     * in both the uid and name of the application to be checked; this function will verify
     * that these two match, and if not, return {@link #MODE_IGNORED}.  If this call
     * succeeds, the last execution time of the operation for this app will be updated to
     * the current time.
     * @param op The operation to note.  One of the OPSTR_* constants.
     * @param uid The user id of the application attempting to perform the operation.
     * @param packageName The name of the application attempting to perform the operation.
     * @return Returns {@link #MODE_ALLOWED} if the operation is allowed, or
     * {@link #MODE_IGNORED} if it is not allowed and should be silently ignored (without
     * causing the app to crash).
     * @throws SecurityException If the app has been configured to crash on this op.
     */
    public int noteOp(String op, int uid, String packageName) {
        return noteOp(strOpToOp(op), uid, packageName);
    }

    /**
     * Like {@link #noteOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     */
    public int noteOpNoThrow(String op, int uid, String packageName) {
        return noteOpNoThrow(strOpToOp(op), uid, packageName);
    }

    /**
     * Report that an application has started executing a long-running operation.  Note that you
     * must pass in both the uid and name of the application to be checked; this function will
     * verify that these two match, and if not, return {@link #MODE_IGNORED}.  If this call
     * succeeds, the last execution time of the operation for this app will be updated to
     * the current time and the operation will be marked as "running".  In this case you must
     * later call {@link #finishOp(String, int, String)} to report when the application is no
     * longer performing the operation.
     * @param op The operation to start.  One of the OPSTR_* constants.
     * @param uid The user id of the application attempting to perform the operation.
     * @param packageName The name of the application attempting to perform the operation.
     * @return Returns {@link #MODE_ALLOWED} if the operation is allowed, or
     * {@link #MODE_IGNORED} if it is not allowed and should be silently ignored (without
     * causing the app to crash).
     * @throws SecurityException If the app has been configured to crash on this op.
     */
    public int startOp(String op, int uid, String packageName) {
        return startOp(strOpToOp(op), uid, packageName);
    }

    /**
     * Like {@link #startOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     */
    public int startOpNoThrow(String op, int uid, String packageName) {
        return startOpNoThrow(strOpToOp(op), uid, packageName);
    }

    /**
     * Report that an application is no longer performing an operation that had previously
     * been started with {@link #startOp(String, int, String)}.  There is no validation of input
     * or result; the parameters supplied here must be the exact same ones previously passed
     * in when starting the operation.
     */
    public void finishOp(String op, int uid, String packageName) {
        finishOp(strOpToOp(op), uid, packageName);
    }

    /**
     * Do a quick check for whether an application might be able to perform an operation.
     * This is <em>not</em> a security check; you must use {@link #noteOp(int, int, String)}
     * or {@link #startOp(int, int, String)} for your actual security checks, which also
     * ensure that the given uid and package name are consistent.  This function can just be
     * used for a quick check to see if an operation has been disabled for the application,
     * as an early reject of some work.  This does not modify the time stamp or other data
     * about the operation.
     * @param op The operation to check.  One of the OP_* constants.
     * @param uid The user id of the application attempting to perform the operation.
     * @param packageName The name of the application attempting to perform the operation.
     * @return Returns {@link #MODE_ALLOWED} if the operation is allowed, or
     * {@link #MODE_IGNORED} if it is not allowed and should be silently ignored (without
     * causing the app to crash).
     * @throws SecurityException If the app has been configured to crash on this op.
     * @hide
     */
    public int checkOp(int op, int uid, String packageName) {
        try {
            int mode = mService.checkOperation(op, uid, packageName);
            if (mode == MODE_ERRORED) {
                throw new SecurityException(buildSecurityExceptionMsg(op, uid, packageName));
            }
            return mode;
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /**
     * Like {@link #checkOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     * @hide
     */
    public int checkOpNoThrow(int op, int uid, String packageName) {
        try {
            return mService.checkOperation(op, uid, packageName);
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /**
     * Do a quick check to validate if a package name belongs to a UID.
     *
     * @throws SecurityException if the package name doesn't belong to the given
     *             UID, or if ownership cannot be verified.
     */
    public void checkPackage(int uid, String packageName) {
        try {
            if (mService.checkPackage(uid, packageName) != MODE_ALLOWED) {
                throw new SecurityException(
                        "Package " + packageName + " does not belong to " + uid);
            }
        } catch (RemoteException e) {
            throw new SecurityException("Unable to verify package ownership", e);
        }
    }

    /**
     * Like {@link #checkOp} but at a stream-level for audio operations.
     * @hide
     */
    public int checkAudioOp(int op, int stream, int uid, String packageName) {
        try {
            final int mode = mService.checkAudioOperation(op, stream, uid, packageName);
            if (mode == MODE_ERRORED) {
                throw new SecurityException(buildSecurityExceptionMsg(op, uid, packageName));
            }
            return mode;
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /**
     * Like {@link #checkAudioOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     * @hide
     */
    public int checkAudioOpNoThrow(int op, int stream, int uid, String packageName) {
        try {
            return mService.checkAudioOperation(op, stream, uid, packageName);
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /**
     * Make note of an application performing an operation.  Note that you must pass
     * in both the uid and name of the application to be checked; this function will verify
     * that these two match, and if not, return {@link #MODE_IGNORED}.  If this call
     * succeeds, the last execution time of the operation for this app will be updated to
     * the current time.
     * @param op The operation to note.  One of the OP_* constants.
     * @param uid The user id of the application attempting to perform the operation.
     * @param packageName The name of the application attempting to perform the operation.
     * @return Returns {@link #MODE_ALLOWED} if the operation is allowed, or
     * {@link #MODE_IGNORED} if it is not allowed and should be silently ignored (without
     * causing the app to crash).
     * @throws SecurityException If the app has been configured to crash on this op.
     * @hide
     */
    public int noteOp(int op, int uid, String packageName) {
        try {
            int mode = mService.noteOperation(op, uid, packageName);
            if (mode == MODE_ERRORED) {
                throw new SecurityException(buildSecurityExceptionMsg(op, uid, packageName));
            }
            return mode;
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /**
     * Like {@link #noteOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     * @hide
     */
    public int noteOpNoThrow(int op, int uid, String packageName) {
        try {
            return mService.noteOperation(op, uid, packageName);
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /** @hide */
    public int noteOp(int op) {
        return noteOp(op, Process.myUid(), mContext.getOpPackageName());
    }

    /** @hide */
    public static IBinder getToken(IAppOpsService service) {
        synchronized (AppOpsManager.class) {
            if (sToken != null) {
                return sToken;
            }
            try {
                sToken = service.getToken(new Binder());
            } catch (RemoteException e) {
                // System is dead, whatevs.
            }
            return sToken;
        }
    }

    /**
     * Report that an application has started executing a long-running operation.  Note that you
     * must pass in both the uid and name of the application to be checked; this function will
     * verify that these two match, and if not, return {@link #MODE_IGNORED}.  If this call
     * succeeds, the last execution time of the operation for this app will be updated to
     * the current time and the operation will be marked as "running".  In this case you must
     * later call {@link #finishOp(int, int, String)} to report when the application is no
     * longer performing the operation.
     * @param op The operation to start.  One of the OP_* constants.
     * @param uid The user id of the application attempting to perform the operation.
     * @param packageName The name of the application attempting to perform the operation.
     * @return Returns {@link #MODE_ALLOWED} if the operation is allowed, or
     * {@link #MODE_IGNORED} if it is not allowed and should be silently ignored (without
     * causing the app to crash).
     * @throws SecurityException If the app has been configured to crash on this op.
     * @hide
     */
    public int startOp(int op, int uid, String packageName) {
        try {
            int mode = mService.startOperation(getToken(mService), op, uid, packageName);
            if (mode == MODE_ERRORED) {
                throw new SecurityException(buildSecurityExceptionMsg(op, uid, packageName));
            }
            return mode;
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /**
     * Like {@link #startOp} but instead of throwing a {@link SecurityException} it
     * returns {@link #MODE_ERRORED}.
     * @hide
     */
    public int startOpNoThrow(int op, int uid, String packageName) {
        try {
            return mService.startOperation(getToken(mService), op, uid, packageName);
        } catch (RemoteException e) {
        }
        return MODE_IGNORED;
    }

    /** @hide */
    public int startOp(int op) {
        return startOp(op, Process.myUid(), mContext.getOpPackageName());
    }

    /**
     * Report that an application is no longer performing an operation that had previously
     * been started with {@link #startOp(int, int, String)}.  There is no validation of input
     * or result; the parameters supplied here must be the exact same ones previously passed
     * in when starting the operation.
     * @hide
     */
    public void finishOp(int op, int uid, String packageName) {
        try {
            mService.finishOperation(getToken(mService), op, uid, packageName);
        } catch (RemoteException e) {
        }
    }

    /** @hide */
    public void finishOp(int op) {
        finishOp(op, Process.myUid(), mContext.getOpPackageName());
    }

    /** @hide */
    public static boolean isStrictEnable() {
        return SystemProperties.getBoolean("persist.sys.strict_op_enable", false);
    }

    /**
     * Check if op in strict mode
     * @hide
     */
    public static boolean isStrictOp(int code) {
        return sOpStrictMode[code];
    }


    /** @hide */
    public static int stringToMode(String permission) {
        if ("allowed".equalsIgnoreCase(permission)) {
            return AppOpsManager.MODE_ALLOWED;
        } else if ("ignored".equalsIgnoreCase(permission)) {
            return AppOpsManager.MODE_IGNORED;
        } else if ("ask".equalsIgnoreCase(permission)) {
            return AppOpsManager.MODE_ASK;
        }
        return AppOpsManager.MODE_ERRORED;
    }

    /** @hide */
    public static int stringOpToOp (String op) {
        Integer val = sOpStringToOp.get(op);
        if (val == null) {
            val = OP_NONE;
        }
        return val;
    }

    /** @hide */
    public boolean isControlAllowed(int op, String packageName) {
        boolean isShow = true;
        try {
            isShow = mService.isControlAllowed(op, packageName);
        } catch (RemoteException e) {
        }
        return isShow;
    }

    /** @hide */
    public boolean getPrivacyGuardSettingForPackage(int uid, String packageName) {
        try {
            return mService.getPrivacyGuardSettingForPackage(uid, packageName);
        } catch (RemoteException e) {
        }
        return false;
    }

    /** @hide */
    public void setPrivacyGuardSettingForPackage(int uid, String packageName,
            boolean state) {
        try {
            mService.setPrivacyGuardSettingForPackage(uid, packageName, state);
        } catch (RemoteException e) {
        }
    }

    /** @hide */
    public void resetCounters() {
        try {
            mService.resetCounters();
        } catch (RemoteException e) {
        }
    }
}
