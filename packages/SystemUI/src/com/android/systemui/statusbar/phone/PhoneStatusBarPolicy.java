/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.app.StatusBarManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.PorterDuff.Mode;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.TtyIntent;
import com.android.systemui.R;

/**
 * This class contains all of the policy about which icons are installed in the status
 * bar at boot time.  It goes through the normal API for icons, even though it probably
 * strictly doesn't need to.
 */
public class PhoneStatusBarPolicy {
    private static final String TAG = "PhoneStatusBarPolicy";

    // message codes for the handler
    private static final int EVENT_BATTERY_CLOSE = 4;

    private static final int AM_PM_STYLE_NORMAL  = 0;
    private static final int AM_PM_STYLE_SMALL   = 1;
    private static final int AM_PM_STYLE_GONE    = 2;

    private static final int AM_PM_STYLE = AM_PM_STYLE_GONE;

    private static final int INET_CONDITION_THRESHOLD = 50;

    private static final boolean SHOW_SYNC_ICON = false;

    private final Context mContext;
    private final StatusBarManager mService;
    private final Handler mHandler = new Handler();

    // Assume it's all good unless we hear otherwise.  We don't always seem
    // to get broadcasts that it *is* there.
    IccCardConstants.State mSimState = IccCardConstants.State.READY;

    // ringer volume
    private boolean mVolumeVisible;

    // Alarm icon
    private boolean mAlarmIconDisabled;

    // bluetooth device status
    private boolean mBluetoothEnabled = false;

    private int mLastWifiSignalLevel = -1;
    private boolean mIsWifiConnected = false;

    // state of inet connection - 0 not connected, 100 connected
    private int mInetCondition = 0;

    // sync state
    // If sync is active the SyncActive icon is displayed. If sync is not active but
    // sync is failing the SyncFailing icon is displayed. Otherwise neither are displayed.

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_ALARM_CHANGED)) {
                updateAlarm(intent);
            }
            else if (action.equals(Intent.ACTION_SYNC_STATE_CHANGED)) {
                updateSyncState(intent);
            }
            else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) ||
                    action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
                updateBluetooth(intent);
            }
            else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                updateVolume();
            }
            else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                updateSimState(intent);
            }
            else if (action.equals(TtyIntent.TTY_ENABLED_CHANGE_ACTION)) {
                updateTTY(intent);
            }
        }
    };

    private class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.QUIET_HOURS_ENABLED),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ALARM_ICON_PREFERENCE),
                    false, this, UserHandle.USER_ALL);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            ContentResolver resolver = mContext.getContentResolver();
            if (uri.equals(Settings.System.getUriFor(Settings.System.ALARM_ICON_PREFERENCE))) {
                mAlarmIconDisabled = Settings.System.getIntForUser(resolver,
                        Settings.System.ALARM_ICON_PREFERENCE, 0, UserHandle.USER_CURRENT) == 1;
                final String timeString = Settings.System.getString(resolver,
                        Settings.System.NEXT_ALARM_FORMATTED);
                if (!mAlarmIconDisabled) {
                    if (timeString != null || !TextUtils.isEmpty(timeString)) {
                        mService.setIconVisibility("alarm_clock", true);
                    }
                } else {
                    mService.setIconVisibility("alarm_clock", false);
                }
            } else {
                updateSettings();
            }
        }

        private void updateSettings() {

            // Setup quiet hours icon.
            final int quietHoursMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT);

            final int drawableResource;
            switch (quietHoursMode) {
                case 4: // Quiet hours timer enabled and active - but waiting on requirements
                    drawableResource = R.drawable.stat_sys_quiet_hours_waiting;
                    break;
                case 3: // Quiet hours timer enabled and active
                    drawableResource = R.drawable.stat_sys_quiet_hours_timed_on;
                    break;
                case 2: // Quiet hours timer disabled and forced active
                default:
                    drawableResource = R.drawable.stat_sys_quiet_hours;
                    break;
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mService.setIcon("quiet_hours", drawableResource, 0, null);
                    mService.setIconVisibility("quiet_hours", quietHoursMode > 1);
                }
            });
        }

    }

    public PhoneStatusBarPolicy(Context context) {
        mContext = context;
        mService = (StatusBarManager)context.getSystemService(Context.STATUS_BAR_SERVICE);

        // listen for broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_ALARM_CHANGED);
        filter.addAction(Intent.ACTION_SYNC_STATE_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TtyIntent.TTY_ENABLED_CHANGE_ACTION);
        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);

        // TTY status
        mService.setIcon("tty",  R.drawable.stat_sys_tty_mode, 0, null);
        mService.setIconVisibility("tty", false);

        // Cdma Roaming Indicator, ERI
        mService.setIcon("cdma_eri", R.drawable.stat_sys_roaming_cdma_0, 0, null);
        mService.setIconVisibility("cdma_eri", false);

        // bluetooth status
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        int bluetoothIcon = R.drawable.stat_sys_data_bluetooth;
        if (adapter != null) {
            mBluetoothEnabled = (adapter.getState() == BluetoothAdapter.STATE_ON);
            if (adapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED) {
                bluetoothIcon = R.drawable.stat_sys_data_bluetooth_connected;
            }
        }
        mService.setIcon("bluetooth", bluetoothIcon, 0, null);
        mService.setIconVisibility("bluetooth", mBluetoothEnabled);

        // Alarm clock
        mService.setIcon("alarm_clock", R.drawable.stat_sys_alarm, 0, null);
        mService.setIconVisibility("alarm_clock", false);

        // Sync state
        mService.setIcon("sync_active", R.drawable.stat_sys_sync, 0, null);
        mService.setIconVisibility("sync_active", false);
        // "sync_failing" is obsolete: b/1297963

        // VoiceWakeup state
        mService.setIcon("voice_wakeup", R.drawable.stat_sys_voice_wakeup, 0, null);
        mService.setIconVisibility("voice_wakeup", false);

        // volume
        mService.setIcon("volume", R.drawable.stat_sys_ringer_silent, 0, null);
        mService.setIconVisibility("volume", false);
        updateVolume();

        // Listen to quiet hours changes and update accordingly the icon.
        // NOTE: This is not controled anymore over broadcasts.
        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observe();
    }

    private final void updateAlarm(Intent intent) {
        boolean alarmSet = intent.getBooleanExtra("alarmSet", false);
        if (!mAlarmIconDisabled) {
            alarmSet = false;
        }
        mService.setIconVisibility("alarm_clock", alarmSet);
    }

    private final void updateSyncState(Intent intent) {
        if (!SHOW_SYNC_ICON) return;
        boolean isActive = intent.getBooleanExtra("active", false);
        mService.setIconVisibility("sync_active", isActive);
    }

    private final void updateSimState(Intent intent) {
        String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
        if (IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            mSimState = IccCardConstants.State.ABSENT;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            mSimState = IccCardConstants.State.READY;
        }
        else if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason =
                    intent.getStringExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON);
            if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PIN_REQUIRED;
            }
            else if (IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                mSimState = IccCardConstants.State.PUK_REQUIRED;
            }
            else {
                mSimState = IccCardConstants.State.NETWORK_LOCKED;
            }
        } else {
            mSimState = IccCardConstants.State.UNKNOWN;
        }
    }

    private final void updateVolume() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        final int ringerMode = audioManager.getRingerMode();
        final int iconcolor = Settings.System.getInt(mContext.getContentResolver(), Settings.System.STATUS_BAR_VOLUME_COLOR, -1);
        final boolean visible = ringerMode == AudioManager.RINGER_MODE_SILENT ||
                ringerMode == AudioManager.RINGER_MODE_VIBRATE;
        int quietHoursAuto = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QUIET_HOURS_AUTOMATIC,
                0, UserHandle.USER_CURRENT);
        final int iconId;
        String contentDescription = null;
        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            if (quietHoursAuto == 2) {
                updateQuietHours(2);
            } else if (quietHoursAuto == 1) {
                updateQuietHours(1);
            }
            iconId = R.drawable.stat_sys_ringer_vibrate;
            mContext.getResources().getDrawable(R.drawable.stat_sys_ringer_vibrate).setColorFilter(iconcolor , Mode.MULTIPLY);
            contentDescription = mContext.getString(R.string.accessibility_ringer_vibrate);
        } else {
            if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                if (quietHoursAuto == 1 || quietHoursAuto == 2) {
                    updateQuietHours(2);
                }
            } else {
                if (quietHoursAuto != 0) {
                    updateQuietHours(1);
                }
            }
            iconId =  R.drawable.stat_sys_ringer_silent;
            mContext.getResources().getDrawable(R.drawable.stat_sys_ringer_silent).setColorFilter(iconcolor , Mode.MULTIPLY);
            contentDescription = mContext.getString(R.string.accessibility_ringer_silent);
        }

        if (visible) {
            mService.setIcon("volume", iconId, 0, contentDescription);
        }
        if (visible != mVolumeVisible) {
            mService.setIconVisibility("volume", visible);
            mVolumeVisible = visible;
        }
    }

    private final void updateBluetooth(Intent intent) {
        int iconId = R.drawable.stat_sys_data_bluetooth;
        String contentDescription = null;
        String action = intent.getAction();
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            mBluetoothEnabled = state == BluetoothAdapter.STATE_ON;
        } else if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                BluetoothAdapter.STATE_DISCONNECTED);
            if (state == BluetoothAdapter.STATE_CONNECTED) {
                iconId = R.drawable.stat_sys_data_bluetooth_connected;
                contentDescription = mContext.getString(R.string.accessibility_bluetooth_connected);
            } else {
                contentDescription = mContext.getString(
                        R.string.accessibility_bluetooth_disconnected);
            }
        } else {
            return;
        }

        mService.setIcon("bluetooth", iconId, 0, contentDescription);
        mService.setIconVisibility("bluetooth", mBluetoothEnabled);
    }

    private final void updateTTY(Intent intent) {
        final String action = intent.getAction();
        final boolean enabled = intent.getBooleanExtra(TtyIntent.TTY_ENABLED, false);

        if (false) Log.v(TAG, "updateTTY: enabled: " + enabled);

        if (enabled) {
            // TTY is on
            if (false) Log.v(TAG, "updateTTY: set TTY on");
            mService.setIcon("tty", R.drawable.stat_sys_tty_mode, 0,
                    mContext.getString(R.string.accessibility_tty_enabled));
            mService.setIconVisibility("tty", true);
        } else {
            // TTY is off
            if (false) Log.v(TAG, "updateTTY: set TTY off");
            mService.setIconVisibility("tty", false);
        }
    }

    private final void updateQuietHours(int enabled) {
        final int quietHours = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.QUIET_HOURS_ENABLED,
                0, UserHandle.USER_CURRENT);
        if (quietHours != 0 && quietHours != enabled) {
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.QUIET_HOURS_ENABLED,
                    enabled, UserHandle.USER_CURRENT);
        }
    }
}
