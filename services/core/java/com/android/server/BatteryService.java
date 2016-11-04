/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2016 The CyanogenMod Project
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

package com.android.server;

import android.database.ContentObserver;
import android.os.BatteryStats;

import com.android.internal.app.IBatteryStats;
import com.android.server.am.BatteryStatsService;
import com.android.server.lights.Light;
import com.android.server.lights.LightsManager;

import android.app.ActivityManagerNative;
import android.app.IBatteryService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.BatteryProperties;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBatteryPropertiesListener;
import android.os.IBatteryPropertiesRegistrar;
import android.os.IBinder;
import android.os.DropBoxManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.EventLog;
import android.util.Slog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import cyanogenmod.providers.CMSettings;

/**
 * <p>BatteryService monitors the charging status, and charge level of the device
 * battery.  When these values change this service broadcasts the new values
 * to all {@link android.content.BroadcastReceiver IntentReceivers} that are
 * watching the {@link android.content.Intent#ACTION_BATTERY_CHANGED
 * BATTERY_CHANGED} action.</p>
 * <p>The new values are stored in the Intent data and can be retrieved by
 * calling {@link android.content.Intent#getExtra Intent.getExtra} with the
 * following keys:</p>
 * <p>&quot;scale&quot; - int, the maximum value for the charge level</p>
 * <p>&quot;level&quot; - int, charge level, from 0 through &quot;scale&quot; inclusive</p>
 * <p>&quot;status&quot; - String, the current charging status.<br />
 * <p>&quot;health&quot; - String, the current battery health.<br />
 * <p>&quot;present&quot; - boolean, true if the battery is present<br />
 * <p>&quot;icon-small&quot; - int, suggested small icon to use for this state</p>
 * <p>&quot;plugged&quot; - int, 0 if the device is not plugged in; 1 if plugged
 * into an AC power adapter; 2 if plugged in via USB.</p>
 * <p>&quot;voltage&quot; - int, current battery voltage in millivolts</p>
 * <p>&quot;temperature&quot; - int, current battery temperature in tenths of
 * a degree Centigrade</p>
 * <p>&quot;technology&quot; - String, the type of battery installed, e.g. "Li-ion"</p>
 *
 * <p>If a dock battery is present, then this Intent data will be present too related
 * to dock battery information:</p>
 * <p>&quot;dock_scale&quot; - int, the maximum value for the charge level</p>
 * <p>&quot;dock_level&quot; - int, charge level, from 0 through &quot;scale&quot; inclusive</p>
 * <p>&quot;dock_status&quot; - String, the current charging status.<br />
 * <p>&quot;dock_health&quot; - String, the current battery health.<br />
 * <p>&quot;dock_present&quot; - boolean, true if the battery is present<br />
 * <p>&quot;dock_icon-small&quot; - int, suggested small icon to use for this state</p>
 * <p>&quot;dock_plugged&quot; - int, 0 if the device is not plugged in; 1 if plugged
 * into an AC power adapter; 2 if plugged in via USB.</p>
 * <p>&quot;dock_voltage&quot; - int, current battery voltage in millivolts</p>
 * <p>&quot;dock_temperature&quot; - int, current battery temperature in tenths of
 * a degree Centigrade</p>
 * <p>&quot;dock_technology&quot; - String, the type of battery installed, e.g. "Li-ion"</p>
 *
 * <p>
 * The battery service may be called by the power manager while holding its locks so
 * we take care to post all outcalls into the activity manager to a handler.
 *
 * FIXME: Ideally the power manager would perform all of its calls into the battery
 * service asynchronously itself.
 * </p>
 */
public final class BatteryService extends SystemService {
    private static final String TAG = BatteryService.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final int BATTERY_SCALE = 100;    // battery capacity is a percentage

    // notification light maximum brightness value to use
    private static final int LIGHT_BRIGHTNESS_MAXIMUM = 255;

    // Used locally for determining when to make a last ditch effort to log
    // discharge stats before the device dies.
    private int mCriticalBatteryLevel;

    private static final int DUMP_MAX_LENGTH = 24 * 1024;
    private static final String[] DUMPSYS_ARGS = new String[] { "--checkin", "--unplugged" };

    private static final String DUMPSYS_DATA_PATH = "/data/system/";

    // This should probably be exposed in the API, though it's not critical
    private static final int BATTERY_PLUGGED_NONE = 0;

    private final Context mContext;
    private final IBatteryStats mBatteryStats;
    private final Handler mHandler;

    private final Object mLock = new Object();

    private BatteryProperties mBatteryProps;
    private final BatteryProperties mLastBatteryProps = new BatteryProperties();
    private boolean mBatteryLevelCritical;
    private int mLastBatteryStatus;
    private int mLastBatteryHealth;
    private boolean mLastBatteryPresent;
    private int mLastBatteryLevel;
    private int mLastBatteryVoltage;
    private int mLastBatteryTemperature;
    private boolean mLastBatteryLevelCritical;
    private int mLastMaxChargingCurrent;

    private boolean mDockBatterySupported;
    private int mLastDockBatteryStatus;
    private int mLastDockBatteryHealth;
    private boolean mLastDockBatteryPresent;
    private int mLastDockBatteryLevel;
    private int mLastDockBatteryVoltage;
    private int mLastDockBatteryTemperature;

    private int mInvalidCharger;
    private int mLastInvalidCharger;

    private boolean mAdjustableNotificationLedBrightness;
    private int mNotificationLedBrightnessLevel = LIGHT_BRIGHTNESS_MAXIMUM;
    private boolean mUseSegmentedBatteryLed = false;

    private boolean mMultipleNotificationLeds;
    private boolean mMultipleLedsEnabled = false;

    private int mLowBatteryWarningLevel;
    private int mLowBatteryCloseWarningLevel;
    private int mShutdownBatteryTemperature;

    private int mPlugType;
    private int mLastPlugType = -1; // Extra state so we can detect first run
    private int mDockPlugType;
    private int mLastDockPlugType = -1; // Extra state so we can detect first run

    private boolean mBatteryLevelLow;
    private boolean mDockBatteryLevelLow;

    private long mDischargeStartTime;
    private int mDischargeStartLevel;

    private boolean mUpdatesStopped;

    private Led mLed;
    // Disable LED until SettingsObserver can be started
    private boolean mLightEnabled = false;
    private boolean mLedPulseEnabled;
    private int mBatteryLowARGB;
    private int mBatteryMediumARGB;
    private int mBatteryFullARGB;
    private boolean mMultiColorLed;

    private boolean mSentLowBatteryBroadcast = false;

    private boolean mShowBatteryFullyChargedNotification;
    private boolean mIsShowingBatteryFullyChargedNotification;

    public BatteryService(Context context) {
        super(context);

        mContext = context;
        mHandler = new Handler(true /*async*/);
        mLed = new Led(context, getLocalService(LightsManager.class));
        mBatteryStats = BatteryStatsService.getService();

        // By default dock battery are not supported. The first events will refresh
        // this status from the battery property bag
        mDockBatterySupported = false;

        mCriticalBatteryLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        mLowBatteryWarningLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        mLowBatteryCloseWarningLevel = mLowBatteryWarningLevel + mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryCloseWarningBump);
        mShutdownBatteryTemperature = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_shutdownBatteryTemperature);
        mShowBatteryFullyChargedNotification = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_showBatteryFullyChargedNotification);

        // watch for invalid charger messages if the invalid_charger switch exists
        if (new File("/sys/devices/virtual/switch/invalid_charger/state").exists()) {
            mInvalidChargerObserver.startObserving(
                    "DEVPATH=/devices/virtual/switch/invalid_charger");
        }
    }

    @Override
    public void onStart() {
        IBinder b = ServiceManager.getService("batteryproperties");
        final IBatteryPropertiesRegistrar batteryPropertiesRegistrar =
                IBatteryPropertiesRegistrar.Stub.asInterface(b);
        try {
            batteryPropertiesRegistrar.registerListener(new BatteryListener());
        } catch (RemoteException e) {
            // Should never happen.
        }

        publishBinderService(Context.BATTERY_SERVICE, new BinderService());
        publishLocalService(BatteryManagerInternal.class, new LocalService());
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_ACTIVITY_MANAGER_READY) {
            // check our power situation now that it is safe to display the shutdown dialog.
            synchronized (mLock) {
                ContentObserver obs = new ContentObserver(mHandler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        synchronized (mLock) {
                            updateBatteryWarningLevelLocked();
                        }
                    }
                };
                final ContentResolver resolver = mContext.getContentResolver();
                resolver.registerContentObserver(Settings.Global.getUriFor(
                        Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL),
                        false, obs, UserHandle.USER_ALL);
                updateBatteryWarningLevelLocked();
            }
        } else if (phase == PHASE_BOOT_COMPLETED) {
            SettingsObserver observer = new SettingsObserver(new Handler());
            observer.observe();
        }
    }

    private void updateBatteryWarningLevelLocked() {
        final ContentResolver resolver = mContext.getContentResolver();
        int defWarnLevel = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        mLowBatteryWarningLevel = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, defWarnLevel);
        if (mLowBatteryWarningLevel == 0) {
            mLowBatteryWarningLevel = defWarnLevel;
        }
        if (mLowBatteryWarningLevel < mCriticalBatteryLevel) {
            mLowBatteryWarningLevel = mCriticalBatteryLevel;
        }
        mLowBatteryCloseWarningLevel = mLowBatteryWarningLevel + mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryCloseWarningBump);
        processValuesLocked(true);
    }

    private boolean isPoweredLocked(int plugTypeSet) {
        // assume we are powered if battery state is unknown so
        // the "stay on while plugged in" option will work.
        if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_UNKNOWN) {
            return true;
        }
        if ((plugTypeSet & BatteryManager.BATTERY_PLUGGED_AC) != 0 && mBatteryProps.chargerAcOnline) {
            return true;
        }
        if ((plugTypeSet & BatteryManager.BATTERY_PLUGGED_USB) != 0 && mBatteryProps.chargerUsbOnline) {
            return true;
        }
        if ((plugTypeSet & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 && mBatteryProps.chargerWirelessOnline) {
            return true;
        }
        return false;
    }

    private boolean shouldSendBatteryLowLocked() {
        final boolean plugged = mPlugType != BATTERY_PLUGGED_NONE;
        final boolean oldPlugged = mLastPlugType != BATTERY_PLUGGED_NONE;

        /* The ACTION_BATTERY_LOW broadcast is sent in these situations:
         * - is just un-plugged (previously was plugged) and battery level is
         *   less than or equal to WARNING, or
         * - is not plugged and battery level falls to WARNING boundary
         *   (becomes <= mLowBatteryWarningLevel).
         */
        return !plugged
                && mBatteryProps.batteryStatus != BatteryManager.BATTERY_STATUS_UNKNOWN
                && mBatteryProps.batteryLevel <= mLowBatteryWarningLevel
                && (oldPlugged || mLastBatteryLevel > mLowBatteryWarningLevel);
    }

    private void shutdownIfNoPowerLocked() {
        // shut down gracefully if our battery is critically low and we are not powered.
        // or the battery voltage is decreasing (consumption rate higher than charging rate)
        // wait until the system has booted before attempting to display the shutdown dialog.
        if (mBatteryProps.batteryLevel == 0 && (!isPoweredLocked(BatteryManager.BATTERY_PLUGGED_ANY) ||
                                                 mBatteryProps.batteryVoltage < mLastBatteryVoltage) ) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (ActivityManagerNative.isSystemReady()) {
                        Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                        intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                    }
                }
            });
        }
    }

    private void shutdownIfOverTempLocked() {
        // shut down gracefully if temperature is too high (> 68.0C by default)
        // wait until the system has booted before attempting to display the
        // shutdown dialog.
        if (mBatteryProps.batteryTemperature > mShutdownBatteryTemperature) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (ActivityManagerNative.isSystemReady()) {
                        Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
                        intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
                    }
                }
            });
        }
    }

    private void update(BatteryProperties props) {
        synchronized (mLock) {
            if (!mUpdatesStopped) {
                mBatteryProps = props;
                // Process the new values.
                processValuesLocked(false);
            } else {
                mLastBatteryProps.set(props);
            }
        }
    }

    private void processValuesLocked(boolean force) {
        boolean logOutlier = false;
        long dischargeDuration = 0;

        mDockBatterySupported = mBatteryProps.dockBatterySupported;

        mBatteryLevelCritical = (mBatteryProps.batteryLevel <= mCriticalBatteryLevel);
        mPlugType = BATTERY_PLUGGED_NONE;
        if (mBatteryProps.chargerAcOnline) {
            mPlugType = BatteryManager.BATTERY_PLUGGED_AC;
        } else if (mBatteryProps.chargerUsbOnline) {
            mPlugType = BatteryManager.BATTERY_PLUGGED_USB;
        } else if (mBatteryProps.chargerWirelessOnline) {
            mPlugType = BatteryManager.BATTERY_PLUGGED_WIRELESS;
        }
        mDockPlugType = BATTERY_PLUGGED_NONE;
        if (mBatteryProps.chargerDockAcOnline && mBatteryProps.chargerAcOnline) {
            mDockPlugType = BatteryManager.BATTERY_DOCK_PLUGGED_AC;
        } else if (mBatteryProps.chargerDockAcOnline && mBatteryProps.chargerUsbOnline) {
            mDockPlugType = BatteryManager.BATTERY_DOCK_PLUGGED_USB;
        }

        if (DEBUG) {
            String msg = "Processing new values: "
                    + "chargerAcOnline=" + mBatteryProps.chargerAcOnline;
            if (mDockBatterySupported) {
                msg +=  ", chargerDockAcOnline=" + mBatteryProps.chargerDockAcOnline;
            }
            msg +=  ", chargerUsbOnline=" + mBatteryProps.chargerUsbOnline
                    + ", chargerWirelessOnline=" + mBatteryProps.chargerWirelessOnline
                    + ", maxChargingCurrent" + mBatteryProps.maxChargingCurrent
                    + ", batteryStatus=" + mBatteryProps.batteryStatus
                    + ", batteryHealth=" + mBatteryProps.batteryHealth
                    + ", batteryPresent=" + mBatteryProps.batteryPresent
                    + ", batteryLevel=" + mBatteryProps.batteryLevel
                    + ", batteryTechnology=" + mBatteryProps.batteryTechnology
                    + ", batteryVoltage=" + mBatteryProps.batteryVoltage
                    + ", batteryTemperature=" + mBatteryProps.batteryTemperature
                    + ", mBatteryLevelCritical=" + mBatteryLevelCritical;
            if (mDockBatterySupported) {
                msg += ", dockBatteryStatus=" + mBatteryProps.dockBatteryStatus
                        + ", dockBatteryHealth=" + mBatteryProps.dockBatteryHealth
                        + ", dockBatteryPresent=" + mBatteryProps.dockBatteryPresent
                        + ", dockBatteryLevel=" + mBatteryProps.dockBatteryLevel
                        + ", dockBatteryTechnology=" + mBatteryProps.dockBatteryTechnology
                        + ", dockBatteryVoltage=" + mBatteryProps.dockBatteryVoltage
                        + ", dockBatteryTemperature=" + mBatteryProps.dockBatteryTemperature;
            }
            msg += ", mPlugType=" + mPlugType;
            if (mDockBatterySupported) {
                msg +=  ", mDockPlugType=" + mDockPlugType;
            }

            Slog.d(TAG, msg);
        }

        // Let the battery stats keep track of the current level.
        try {
            mBatteryStats.setBatteryState(mBatteryProps.batteryStatus, mBatteryProps.batteryHealth,
                    mPlugType, mBatteryProps.batteryLevel, mBatteryProps.batteryTemperature,
                    mBatteryProps.batteryVoltage);
        } catch (RemoteException e) {
            // Should never happen.
        }
        if (mDockBatterySupported) {
            try {
                mBatteryStats.setDockBatteryState(mBatteryProps.dockBatteryStatus,
                        mBatteryProps.dockBatteryHealth, mDockPlugType,
                        mBatteryProps.dockBatteryLevel, mBatteryProps.dockBatteryTemperature,
                        mBatteryProps.dockBatteryVoltage);
            } catch (RemoteException e) {
                // Should never happen.
            }
        }

        shutdownIfNoPowerLocked();
        shutdownIfOverTempLocked();

        final boolean batteryChanged = mBatteryProps.batteryStatus != mLastBatteryStatus ||
                mBatteryProps.batteryHealth != mLastBatteryHealth ||
                mBatteryProps.batteryPresent != mLastBatteryPresent ||
                mBatteryProps.batteryLevel != mLastBatteryLevel ||
                mPlugType != mLastPlugType ||
                mBatteryProps.batteryVoltage != mLastBatteryVoltage ||
                mBatteryProps.batteryTemperature != mLastBatteryTemperature ||
                mBatteryProps.maxChargingCurrent != mLastMaxChargingCurrent;

        final boolean dockBatteryChanged = mDockBatterySupported &&
                (mBatteryProps.dockBatteryStatus != mLastDockBatteryStatus ||
                mBatteryProps.dockBatteryHealth != mLastDockBatteryHealth ||
                mBatteryProps.dockBatteryPresent != mLastDockBatteryPresent ||
                mBatteryProps.dockBatteryLevel != mLastDockBatteryLevel ||
                mDockPlugType != mLastDockPlugType ||
                mBatteryProps.dockBatteryVoltage != mLastDockBatteryVoltage ||
                mBatteryProps.dockBatteryTemperature != mLastDockBatteryTemperature);

        if (force || batteryChanged || dockBatteryChanged ||
                mInvalidCharger != mLastInvalidCharger) {

            if (mPlugType != mLastPlugType) {
                if (mLastPlugType == BATTERY_PLUGGED_NONE) {
                    // discharging -> charging

                    // There's no value in this data unless we've discharged at least once and the
                    // battery level has changed; so don't log until it does.
                    if (mDischargeStartTime != 0 && mDischargeStartLevel != mBatteryProps.batteryLevel) {
                        dischargeDuration = SystemClock.elapsedRealtime() - mDischargeStartTime;
                        logOutlier = true;
                        EventLog.writeEvent(EventLogTags.BATTERY_DISCHARGE, dischargeDuration,
                                mDischargeStartLevel, mBatteryProps.batteryLevel);
                        // make sure we see a discharge event before logging again
                        mDischargeStartTime = 0;
                    }
                } else if (mPlugType == BATTERY_PLUGGED_NONE) {
                    // charging -> discharging or we just powered up
                    mDischargeStartTime = SystemClock.elapsedRealtime();
                    mDischargeStartLevel = mBatteryProps.batteryLevel;
                }
            }
            if (mBatteryProps.batteryStatus != mLastBatteryStatus ||
                    mBatteryProps.batteryHealth != mLastBatteryHealth ||
                    mBatteryProps.batteryPresent != mLastBatteryPresent ||
                    mPlugType != mLastPlugType) {
                EventLog.writeEvent(EventLogTags.BATTERY_STATUS,
                        mBatteryProps.batteryStatus, mBatteryProps.batteryHealth, mBatteryProps.batteryPresent ? 1 : 0,
                        mPlugType, mBatteryProps.batteryTechnology);
            }
            if (mDockBatterySupported &&
                    (mBatteryProps.dockBatteryStatus != mLastDockBatteryStatus ||
                    mBatteryProps.dockBatteryHealth != mLastDockBatteryHealth ||
                    mBatteryProps.dockBatteryPresent != mLastDockBatteryPresent ||
                    mDockPlugType != mLastDockPlugType)) {
                EventLog.writeEvent(EventLogTags.DOCK_BATTERY_STATUS,
                        mBatteryProps.dockBatteryStatus, mBatteryProps.dockBatteryHealth,
                        mBatteryProps.dockBatteryPresent ? 1 : 0,
                        mDockPlugType, mBatteryProps.dockBatteryTechnology);
            }
            if (mBatteryProps.batteryLevel != mLastBatteryLevel) {
                // Don't do this just from voltage or temperature changes, that is
                // too noisy.
                EventLog.writeEvent(EventLogTags.BATTERY_LEVEL,
                        mBatteryProps.batteryLevel, mBatteryProps.batteryVoltage, mBatteryProps.batteryTemperature);
            }
            if (mDockBatterySupported &&
                    (mBatteryProps.dockBatteryLevel != mLastDockBatteryLevel)) {
                // Don't do this just from voltage or temperature changes, that is
                // too noisy.
                EventLog.writeEvent(EventLogTags.DOCK_BATTERY_LEVEL,
                        mBatteryProps.dockBatteryLevel, mBatteryProps.dockBatteryVoltage,
                        mBatteryProps.dockBatteryTemperature);
            }
            if (mBatteryLevelCritical && !mLastBatteryLevelCritical &&
                    mPlugType == BATTERY_PLUGGED_NONE) {
                // We want to make sure we log discharge cycle outliers
                // if the battery is about to die.
                dischargeDuration = SystemClock.elapsedRealtime() - mDischargeStartTime;
                logOutlier = true;
            }

            if (!mBatteryLevelLow) {
                // Should we now switch in to low battery mode?
                if (mPlugType == BATTERY_PLUGGED_NONE
                        && mBatteryProps.batteryLevel <= mLowBatteryWarningLevel) {
                    mBatteryLevelLow = true;
                }
            } else {
                // Should we now switch out of low battery mode?
                if (mPlugType != BATTERY_PLUGGED_NONE) {
                    mBatteryLevelLow = false;
                } else if (mBatteryProps.batteryLevel >= mLowBatteryCloseWarningLevel)  {
                    mBatteryLevelLow = false;
                } else if (force && mBatteryProps.batteryLevel >= mLowBatteryWarningLevel) {
                    // If being forced, the previous state doesn't matter, we will just
                    // absolutely check to see if we are now above the warning level.
                    mBatteryLevelLow = false;
                }
            }
            if (mDockBatterySupported) {
                if (!mDockBatteryLevelLow) {
                    // Should we now switch in to low battery mode?
                    if (mDockPlugType == BATTERY_PLUGGED_NONE
                            && mBatteryProps.dockBatteryLevel <= mLowBatteryWarningLevel) {
                        mDockBatteryLevelLow = true;
                    }
                } else {
                    // Should we now switch out of low battery mode?
                    if (mDockPlugType != BATTERY_PLUGGED_NONE) {
                        mDockBatteryLevelLow = false;
                    } else if (mBatteryProps.dockBatteryLevel >= mLowBatteryCloseWarningLevel)  {
                        mDockBatteryLevelLow = false;
                    } else if (force && mBatteryProps.batteryLevel >= mLowBatteryWarningLevel) {
                        // If being forced, the previous state doesn't matter, we will just
                        // absolutely check to see if we are now above the warning level.
                        mDockBatteryLevelLow = false;
                    }
                }
            }

            sendIntentLocked();

            // Separate broadcast is sent for power connected / not connected
            // since the standard intent will not wake any applications and some
            // applications may want to have smart behavior based on this.
            if (mPlugType != 0 && mLastPlugType == 0 ||
                    (mLastPlugType == 0 && mDockPlugType != 0 && mLastDockPlugType == 0)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_POWER_CONNECTED);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            }
            else if (mPlugType == 0 && mLastPlugType != 0 ||
                    (mLastPlugType != 0 && mDockPlugType == 0 && mLastDockPlugType != 0)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_POWER_DISCONNECTED);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            }

            if (shouldSendBatteryLowLocked()) {
                mSentLowBatteryBroadcast = true;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_BATTERY_LOW);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            } else if (mSentLowBatteryBroadcast && mLastBatteryLevel >= mLowBatteryCloseWarningLevel) {
                mSentLowBatteryBroadcast = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Intent statusIntent = new Intent(Intent.ACTION_BATTERY_OKAY);
                        statusIntent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                        mContext.sendBroadcastAsUser(statusIntent, UserHandle.ALL);
                    }
                });
            }

            // Update the battery LED
            mLed.updateLightsLocked();

            if (shouldShowBatteryFullyChargedNotificationLocked()) {
                showBatteryFullyChargedNotificationLocked();
            } else if (shouldClearBatteryFullyChargedNotificationLocked()) {
                clearBatteryFullyChargedNotificationLocked();
            }

            // This needs to be done after sendIntent() so that we get the lastest battery stats.
            if (logOutlier && dischargeDuration != 0) {
                logOutlierLocked(dischargeDuration);
            }

            mLastBatteryStatus = mBatteryProps.batteryStatus;
            mLastBatteryHealth = mBatteryProps.batteryHealth;
            mLastBatteryPresent = mBatteryProps.batteryPresent;
            mLastBatteryLevel = mBatteryProps.batteryLevel;
            mLastPlugType = mPlugType;
            mLastBatteryVoltage = mBatteryProps.batteryVoltage;
            mLastBatteryTemperature = mBatteryProps.batteryTemperature;
            mLastMaxChargingCurrent = mBatteryProps.maxChargingCurrent;
            mLastBatteryLevelCritical = mBatteryLevelCritical;
            mLastDockBatteryStatus = mBatteryProps.dockBatteryStatus;
            mLastDockBatteryHealth = mBatteryProps.dockBatteryHealth;
            mLastDockBatteryPresent = mBatteryProps.dockBatteryPresent;
            mLastDockBatteryLevel = mBatteryProps.dockBatteryLevel;
            mLastDockPlugType = mDockPlugType;
            mLastDockBatteryVoltage = mBatteryProps.dockBatteryVoltage;
            mLastDockBatteryTemperature = mBatteryProps.dockBatteryTemperature;

            mLastInvalidCharger = mInvalidCharger;
        }
    }

    private void sendIntentLocked() {
        //  Pack up the values and broadcast them to everyone
        final Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                | Intent.FLAG_RECEIVER_REPLACE_PENDING);

        int icon = getIconLocked(mBatteryProps.batteryLevel);
        int dockIcon = 0;

        intent.putExtra(BatteryManager.EXTRA_STATUS, mBatteryProps.batteryStatus);
        intent.putExtra(BatteryManager.EXTRA_HEALTH, mBatteryProps.batteryHealth);
        intent.putExtra(BatteryManager.EXTRA_PRESENT, mBatteryProps.batteryPresent);
        intent.putExtra(BatteryManager.EXTRA_LEVEL, mBatteryProps.batteryLevel);
        intent.putExtra(BatteryManager.EXTRA_SCALE, BATTERY_SCALE);
        intent.putExtra(BatteryManager.EXTRA_ICON_SMALL, icon);
        intent.putExtra(BatteryManager.EXTRA_PLUGGED, mPlugType);
        intent.putExtra(BatteryManager.EXTRA_VOLTAGE, mBatteryProps.batteryVoltage);
        intent.putExtra(BatteryManager.EXTRA_TEMPERATURE, mBatteryProps.batteryTemperature);
        intent.putExtra(BatteryManager.EXTRA_TECHNOLOGY, mBatteryProps.batteryTechnology);
        intent.putExtra(BatteryManager.EXTRA_INVALID_CHARGER, mInvalidCharger);
        intent.putExtra(BatteryManager.EXTRA_MAX_CHARGING_CURRENT, mBatteryProps.maxChargingCurrent);

        if (mDockBatterySupported) {
            dockIcon = getDockIconLocked(mBatteryProps.dockBatteryLevel);

            intent.putExtra(BatteryManager.EXTRA_DOCK_STATUS, mBatteryProps.dockBatteryStatus);
            intent.putExtra(BatteryManager.EXTRA_DOCK_HEALTH, mBatteryProps.dockBatteryHealth);
            intent.putExtra(BatteryManager.EXTRA_DOCK_PRESENT, mBatteryProps.dockBatteryPresent);
            intent.putExtra(BatteryManager.EXTRA_DOCK_LEVEL, mBatteryProps.dockBatteryLevel);
            intent.putExtra(BatteryManager.EXTRA_DOCK_SCALE, BATTERY_SCALE);
            intent.putExtra(BatteryManager.EXTRA_DOCK_ICON_SMALL, dockIcon);
            intent.putExtra(BatteryManager.EXTRA_DOCK_PLUGGED, mDockPlugType);
            intent.putExtra(BatteryManager.EXTRA_DOCK_VOLTAGE, mBatteryProps.dockBatteryVoltage);
            intent.putExtra(BatteryManager.EXTRA_DOCK_TEMPERATURE,
                    mBatteryProps.dockBatteryTemperature);
            intent.putExtra(BatteryManager.EXTRA_DOCK_TECHNOLOGY,
                    mBatteryProps.dockBatteryTechnology);

            // EEPAD legacy data
            intent.putExtra("usb_wakeup", mBatteryProps.chargerUsbOnline);
            intent.putExtra("ac_online", mBatteryProps.chargerAcOnline);
            intent.putExtra("dock_ac_online", mBatteryProps.chargerDockAcOnline);
        }


        if (DEBUG) {
            String msg = "Sending ACTION_BATTERY_CHANGED. level:" + mBatteryProps.batteryLevel +
                    ", scale:" + BATTERY_SCALE +
                    ", status:" + mBatteryProps.batteryStatus +
                    ", health:" + mBatteryProps.batteryHealth +
                    ", present:" + mBatteryProps.batteryPresent +
                    ", voltage: " + mBatteryProps.batteryVoltage +
                    ", temperature: " + mBatteryProps.batteryTemperature +
                    ", technology: " + mBatteryProps.batteryTechnology +
                    ", maxChargingCurrent:" + mBatteryProps.maxChargingCurrent;

            if (mDockBatterySupported) {
                msg += ", dock_level:" + mBatteryProps.dockBatteryLevel +
                    ", dock_status:" + mBatteryProps.dockBatteryStatus +
                    ", dock_health:" + mBatteryProps.dockBatteryHealth +
                    ", dock_present:" + mBatteryProps.dockBatteryPresent +
                    ", dock_voltage: " + mBatteryProps.dockBatteryVoltage +
                    ", dock_temperature: " + mBatteryProps.dockBatteryTemperature +
                    ", dock_technology: " + mBatteryProps.dockBatteryTechnology;
            }
            msg += ", AC powered:" + mBatteryProps.chargerAcOnline;
            if (mDockBatterySupported) {
                msg += ", Dock AC powered:" + mBatteryProps.chargerDockAcOnline;
            }
            msg += ", USB powered:" + mBatteryProps.chargerUsbOnline +
                    ", Wireless powered:" + mBatteryProps.chargerWirelessOnline;
            msg += ", icon:" + icon;
            if (mDockBatterySupported) {
                msg += ", dock_icon:" + dockIcon;
            }
            msg += ", invalid charger:" + mInvalidCharger;

            Slog.d(TAG, msg);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
            }
        });
    }

    private void logBatteryStatsLocked() {
        IBinder batteryInfoService = ServiceManager.getService(BatteryStats.SERVICE_NAME);
        if (batteryInfoService == null) return;

        DropBoxManager db = (DropBoxManager) mContext.getSystemService(Context.DROPBOX_SERVICE);
        if (db == null || !db.isTagEnabled("BATTERY_DISCHARGE_INFO")) return;

        File dumpFile = null;
        FileOutputStream dumpStream = null;
        try {
            // dump the service to a file
            dumpFile = new File(DUMPSYS_DATA_PATH + BatteryStats.SERVICE_NAME + ".dump");
            dumpStream = new FileOutputStream(dumpFile);
            batteryInfoService.dump(dumpStream.getFD(), DUMPSYS_ARGS);
            FileUtils.sync(dumpStream);

            // add dump file to drop box
            db.addFile("BATTERY_DISCHARGE_INFO", dumpFile, DropBoxManager.IS_TEXT);
        } catch (RemoteException e) {
            Slog.e(TAG, "failed to dump battery service", e);
        } catch (IOException e) {
            Slog.e(TAG, "failed to write dumpsys file", e);
        } finally {
            // make sure we clean up
            if (dumpStream != null) {
                try {
                    dumpStream.close();
                } catch (IOException e) {
                    Slog.e(TAG, "failed to close dumpsys output stream");
                }
            }
            if (dumpFile != null && !dumpFile.delete()) {
                Slog.e(TAG, "failed to delete temporary dumpsys file: "
                        + dumpFile.getAbsolutePath());
            }
        }
    }

    private void logOutlierLocked(long duration) {
        ContentResolver cr = mContext.getContentResolver();
        String dischargeThresholdString = Settings.Global.getString(cr,
                Settings.Global.BATTERY_DISCHARGE_THRESHOLD);
        String durationThresholdString = Settings.Global.getString(cr,
                Settings.Global.BATTERY_DISCHARGE_DURATION_THRESHOLD);

        if (dischargeThresholdString != null && durationThresholdString != null) {
            try {
                long durationThreshold = Long.parseLong(durationThresholdString);
                int dischargeThreshold = Integer.parseInt(dischargeThresholdString);
                if (duration <= durationThreshold &&
                        mDischargeStartLevel - mBatteryProps.batteryLevel >= dischargeThreshold) {
                    // If the discharge cycle is bad enough we want to know about it.
                    logBatteryStatsLocked();
                }
                if (DEBUG) Slog.v(TAG, "duration threshold: " + durationThreshold +
                        " discharge threshold: " + dischargeThreshold);
                if (DEBUG) Slog.v(TAG, "duration: " + duration + " discharge: " +
                        (mDischargeStartLevel - mBatteryProps.batteryLevel));
            } catch (NumberFormatException e) {
                Slog.e(TAG, "Invalid DischargeThresholds GService string: " +
                        durationThresholdString + " or " + dischargeThresholdString);
                return;
            }
        }
    }

    private int getIconLocked(int level) {
        if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            return com.android.internal.R.drawable.stat_sys_battery_charge;
        } else if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return com.android.internal.R.drawable.stat_sys_battery;
        } else if (mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || mBatteryProps.batteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
            if (isPoweredLocked(BatteryManager.BATTERY_PLUGGED_ANY)
                    && mBatteryProps.batteryLevel >= 100) {
                return com.android.internal.R.drawable.stat_sys_battery_charge;
            } else {
                return com.android.internal.R.drawable.stat_sys_battery;
            }
        } else {
            return com.android.internal.R.drawable.stat_sys_battery_unknown;
        }
    }

    private int getDockIconLocked(int level) {
        if (mBatteryProps.dockBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            return com.android.internal.R.drawable.stat_sys_battery_charge;
        } else if (mBatteryProps.dockBatteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            return com.android.internal.R.drawable.stat_sys_battery;
        } else if (mBatteryProps.dockBatteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || mBatteryProps.dockBatteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
            if (isPoweredLocked(BatteryManager.BATTERY_PLUGGED_ANY)
                    && mBatteryProps.dockBatteryLevel >= 100) {
                return com.android.internal.R.drawable.stat_sys_battery_charge;
            }
            return com.android.internal.R.drawable.stat_sys_battery;
        }
        return com.android.internal.R.drawable.stat_sys_battery_unknown;
    }

    private void dumpInternal(PrintWriter pw, String[] args) {
        synchronized (mLock) {
            if (args == null || args.length == 0 || "-a".equals(args[0])) {
                pw.println("Current Battery Service state:");
                if (mUpdatesStopped) {
                    pw.println("  (UPDATES STOPPED -- use 'reset' to restart)");
                }
                pw.println("  AC powered: " + mBatteryProps.chargerAcOnline);
                if (mDockBatterySupported) {
                    pw.println("  Dock AC powered: " + mBatteryProps.chargerDockAcOnline);
                }
                pw.println("  USB powered: " + mBatteryProps.chargerUsbOnline);
                pw.println("  Wireless powered: " + mBatteryProps.chargerWirelessOnline);
                pw.println("  Max charging current: " + mBatteryProps.maxChargingCurrent);
                pw.println("  status: " + mBatteryProps.batteryStatus);
                pw.println("  health: " + mBatteryProps.batteryHealth);
                pw.println("  present: " + mBatteryProps.batteryPresent);
                pw.println("  level: " + mBatteryProps.batteryLevel);
                pw.println("  scale: " + BATTERY_SCALE);
                pw.println("  voltage: " + mBatteryProps.batteryVoltage);
                pw.println("  temperature: " + mBatteryProps.batteryTemperature);
                pw.println("  technology: " + mBatteryProps.batteryTechnology);
                if (mDockBatterySupported) {
                    pw.println("  dock_status: " + mBatteryProps.dockBatteryStatus);
                    pw.println("  dock_health: " + mBatteryProps.dockBatteryHealth);
                    pw.println("  dock_present: " + mBatteryProps.dockBatteryPresent);
                    pw.println("  dock_level: " + mBatteryProps.dockBatteryLevel);
                    pw.println("  dock_voltage: " + mBatteryProps.dockBatteryVoltage);
                    pw.println("  dock_temperature: " + mBatteryProps.dockBatteryTemperature);
                    pw.println("  dock_technology: " + mBatteryProps.dockBatteryTechnology);
                }
            } else if ("unplug".equals(args[0])) {
                if (!mUpdatesStopped) {
                    mLastBatteryProps.set(mBatteryProps);
                }
                mBatteryProps.chargerAcOnline = false;
                mBatteryProps.chargerUsbOnline = false;
                mBatteryProps.chargerWirelessOnline = false;
                long ident = Binder.clearCallingIdentity();
                try {
                    mUpdatesStopped = true;
                    processValuesLocked(false);
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }

            } else if (args.length == 3 && "set".equals(args[0])) {
                String key = args[1];
                String value = args[2];
                try {
                    if (!mUpdatesStopped) {
                        mLastBatteryProps.set(mBatteryProps);
                    }
                    boolean update = true;
                    if ("ac".equals(key)) {
                        mBatteryProps.chargerAcOnline = Integer.parseInt(value) != 0;
                    } else if (mDockBatterySupported && "dockac".equals(key)) {
                        mBatteryProps.chargerDockAcOnline = Integer.parseInt(value) != 0;
                    } else if ("usb".equals(key)) {
                        mBatteryProps.chargerUsbOnline = Integer.parseInt(value) != 0;
                    } else if ("wireless".equals(key)) {
                        mBatteryProps.chargerWirelessOnline = Integer.parseInt(value) != 0;
                    } else if ("status".equals(key)) {
                        mBatteryProps.batteryStatus = Integer.parseInt(value);
                    } else if ("level".equals(key)) {
                        mBatteryProps.batteryLevel = Integer.parseInt(value);
                    } else if (mDockBatterySupported && "dockstatus".equals(key)) {
                        mBatteryProps.dockBatteryStatus = Integer.parseInt(value);
                    } else if (mDockBatterySupported && "docklevel".equals(key)) {
                        mBatteryProps.dockBatteryLevel = Integer.parseInt(value);
                    } else if ("invalid".equals(key)) {
                        mInvalidCharger = Integer.parseInt(value);
                    } else {
                        pw.println("Unknown set option: " + key);
                        update = false;
                    }
                    if (update) {
                        long ident = Binder.clearCallingIdentity();
                        try {
                            mUpdatesStopped = true;
                            processValuesLocked(false);
                        } finally {
                            Binder.restoreCallingIdentity(ident);
                        }
                    }
                } catch (NumberFormatException ex) {
                    pw.println("Bad value: " + value);
                }

            } else if (args.length == 1 && "reset".equals(args[0])) {
                long ident = Binder.clearCallingIdentity();
                try {
                    if (mUpdatesStopped) {
                        mUpdatesStopped = false;
                        mBatteryProps.set(mLastBatteryProps);
                        processValuesLocked(false);
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            } else {
                pw.println("Dump current battery state, or:");
                if (mDockBatterySupported) {
                    pw.println("  set [ac|dockac|usb|wireless|status|level|dockstatus" +
                            "|docklevel|invalid] <value>");
                } else {
                    pw.println("  set [ac|usb|wireless|status|level|invalid] <value>");
                }
                pw.println("  unplug");
                pw.println("  reset");
            }
        }
    }

    private final UEventObserver mInvalidChargerObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            final int invalidCharger = "1".equals(event.get("SWITCH_STATE")) ? 1 : 0;
            synchronized (mLock) {
                if (mInvalidCharger != invalidCharger) {
                    mInvalidCharger = invalidCharger;
                }
            }
        }
    };

    private synchronized void updateLedPulse() {
        mLed.updateLightsLocked();
    }

    private boolean shouldShowBatteryFullyChargedNotificationLocked() {
        return mShowBatteryFullyChargedNotification && mPlugType != 0
                && mBatteryProps.batteryLevel == BATTERY_SCALE
                && !mIsShowingBatteryFullyChargedNotification;
    }

    private void showBatteryFullyChargedNotificationLocked() {
        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                intent, 0, null, UserHandle.CURRENT);

        CharSequence title = mContext.getText(
                com.android.internal.R.string.notify_battery_fully_charged_title);
        CharSequence message = mContext.getText(
                com.android.internal.R.string.notify_battery_fully_charged_text);

        Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(com.android.internal.R.drawable.stat_sys_battery_charge)
                .setWhen(0)
                .setOngoing(false)
                .setAutoCancel(true)
                .setTicker(title)
                .setDefaults(0)  // please be quiet
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setColor(mContext.getColor(
                        com.android.internal.R.color.system_notification_accent_color))
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pi)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        nm.notifyAsUser(null, com.android.internal.R.string.notify_battery_fully_charged_title,
                notification, UserHandle.ALL);
        mIsShowingBatteryFullyChargedNotification = true;
    }

    private boolean shouldClearBatteryFullyChargedNotificationLocked() {
        return mIsShowingBatteryFullyChargedNotification &&
                (mPlugType == 0 || mBatteryProps.batteryLevel < BATTERY_SCALE);
    }

    private void clearBatteryFullyChargedNotificationLocked() {
        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        nm.cancel(com.android.internal.R.string.notify_battery_fully_charged_title);
        mIsShowingBatteryFullyChargedNotification = false;
    }

    private final class Led {
        private final Light mBatteryLight;

        private final int mBatteryLedOn;
        private final int mBatteryLedOff;

        public Led(Context context, LightsManager lights) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            mBatteryLight = lights.getLight(LightsManager.LIGHT_ID_BATTERY);

            // Does the Device support changing battery LED colors?
            mMultiColorLed = nm.deviceLightsCan(NotificationManager.LIGHTS_RGB_BATTERY);

            // Is the notification LED brightness changeable ?
            mAdjustableNotificationLedBrightness = nm.deviceLightsCan(
                                            NotificationManager.LIGHTS_ADJUSTABLE_NOTIFICATION_BRIGHTNESS);

            // Does the Device have multiple LEDs ?
            mMultipleNotificationLeds = nm.deviceLightsCan(
                                            NotificationManager.LIGHTS_MULTIPLE_LED);

            mBatteryLedOn = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLedOn);
            mBatteryLedOff = context.getResources().getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLedOff);

            // Does the Device have segmented battery LED support? In this case, we send the level
            // in the alpha channel of the color and let the HAL sort it out.
            mUseSegmentedBatteryLed = nm.deviceLightsCan(
                                            NotificationManager.LIGHTS_SEGMENTED_BATTERY_LIGHTS);
        }

        /**
         * Synchronize on BatteryService.
         */
        public void updateLightsLocked() {
            // mBatteryProps could be null on startup (called by SettingsObserver)
            if (mBatteryProps == null) {
                Slog.w(TAG, "updateLightsLocked: mBatteryProps is null; skipping");
                return;
            }

            final int level = mBatteryProps.batteryLevel;
            final int status = mBatteryProps.batteryStatus;
            mNotificationLedBrightnessLevel = mUseSegmentedBatteryLed ? level :
                    LIGHT_BRIGHTNESS_MAXIMUM;

            if (!mLightEnabled) {
                // No lights if explicitly disabled
                mBatteryLight.turnOff();
            } else if (level < mLowBatteryWarningLevel) {
                mBatteryLight.setModes(mNotificationLedBrightnessLevel,
                        mMultipleLedsEnabled);
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    // Battery is charging and low
                    mBatteryLight.setColor(mBatteryLowARGB);
                } else if (mLedPulseEnabled) {
                    // Battery is low and not charging
                    mBatteryLight.setFlashing(mBatteryLowARGB, Light.LIGHT_FLASH_TIMED,
                            mBatteryLedOn, mBatteryLedOff);
                } else {
                    // "Pulse low battery light" is disabled, no lights.
                    mBatteryLight.turnOff();
                }
            } else if (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL) {
                mBatteryLight.setModes(mNotificationLedBrightnessLevel,
                        mMultipleLedsEnabled);
                if (status == BatteryManager.BATTERY_STATUS_FULL || level >= 90) {
                    // Battery is full or charging and nearly full
                    mBatteryLight.setColor(mBatteryFullARGB);
                } else {
                    // Battery is charging and halfway full
                    mBatteryLight.setColor(mBatteryMediumARGB);
                }
            } else {
                // No lights if not charging and not low
                mBatteryLight.turnOff();
            }
        }
    }

    private final class BatteryListener extends IBatteryPropertiesListener.Stub {
        @Override
        public void batteryPropertiesChanged(BatteryProperties props) {
            final long identity = Binder.clearCallingIdentity();
            try {
                BatteryService.this.update(props);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
       }
    }

    private final class BinderService extends IBatteryService.Stub {
        @Override
        public boolean isDockBatterySupported() throws RemoteException {
            return getLocalService(BatteryManagerInternal.class).isDockBatterySupported();
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {

                pw.println("Permission Denial: can't dump Battery service from from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid());
                return;
            }

            dumpInternal(pw, args);
        }
    }

    private final class LocalService extends BatteryManagerInternal {
        @Override
        public boolean isPowered(int plugTypeSet) {
            synchronized (mLock) {
                return isPoweredLocked(plugTypeSet);
            }
        }

        @Override
        public int getPlugType() {
            synchronized (mLock) {
                return mPlugType;
            }
        }

        @Override
        public int getBatteryLevel() {
            synchronized (mLock) {
                return mBatteryProps.batteryLevel;
            }
        }

        @Override
        public boolean getBatteryLevelLow() {
            synchronized (mLock) {
                return mBatteryLevelLow;
            }
        }

        @Override
        public int getDockPlugType() {
            synchronized (mLock) {
                return mDockPlugType;
            }
        }

        @Override
        public int getDockBatteryLevel() {
            synchronized (mLock) {
                return mBatteryProps.dockBatteryLevel;
            }
        }

        @Override
        public boolean getDockBatteryLevelLow() {
            synchronized (mLock) {
                return mDockBatteryLevelLow;
            }
        }

        @Override
        public int getInvalidCharger() {
            synchronized (mLock) {
                return mInvalidCharger;
            }
        }

        @Override
        public boolean isDockBatterySupported() {
            synchronized (mLock) {
                return mDockBatterySupported;
            }
        }
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            // Battery light enabled
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.BATTERY_LIGHT_ENABLED), false, this, UserHandle.USER_ALL);

            // Low battery pulse
            resolver.registerContentObserver(CMSettings.System.getUriFor(
                    CMSettings.System.BATTERY_LIGHT_PULSE), false, this, UserHandle.USER_ALL);

            // Notification LED brightness
            if (mAdjustableNotificationLedBrightness) {
                resolver.registerContentObserver(CMSettings.System.getUriFor(
                        CMSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL),
                        false, this, UserHandle.USER_ALL);
            }

            // Multiple LEDs enabled
            if (mMultipleNotificationLeds) {
                resolver.registerContentObserver(CMSettings.System.getUriFor(
                        CMSettings.System.NOTIFICATION_LIGHT_MULTIPLE_LEDS_ENABLE),
                        false, this, UserHandle.USER_ALL);
            }

            // Light colors
            if (mMultiColorLed) {
                // Register observer if we have a multi color led
                resolver.registerContentObserver(
                        CMSettings.System.getUriFor(CMSettings.System.BATTERY_LIGHT_LOW_COLOR),
                        false, this, UserHandle.USER_ALL);
                resolver.registerContentObserver(
                        CMSettings.System.getUriFor(CMSettings.System.BATTERY_LIGHT_MEDIUM_COLOR),
                        false, this, UserHandle.USER_ALL);
                resolver.registerContentObserver(
                        CMSettings.System.getUriFor(CMSettings.System.BATTERY_LIGHT_FULL_COLOR),
                        false, this, UserHandle.USER_ALL);
            }

            update();
        }

        @Override public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            // Battery light enabled
            mLightEnabled = CMSettings.System.getInt(resolver,
                    CMSettings.System.BATTERY_LIGHT_ENABLED, 1) != 0;

            // Low battery pulse
            mLedPulseEnabled = CMSettings.System.getInt(resolver,
                        CMSettings.System.BATTERY_LIGHT_PULSE, 1) != 0;

            // Light colors
            mBatteryLowARGB = CMSettings.System.getInt(resolver,
                    CMSettings.System.BATTERY_LIGHT_LOW_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryLowARGB));
            mBatteryMediumARGB = CMSettings.System.getInt(resolver,
                    CMSettings.System.BATTERY_LIGHT_MEDIUM_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryMediumARGB));
            mBatteryFullARGB = CMSettings.System.getInt(resolver,
                    CMSettings.System.BATTERY_LIGHT_FULL_COLOR, res.getInteger(
                    com.android.internal.R.integer.config_notificationsBatteryFullARGB));

            // Notification LED brightness
            if (mAdjustableNotificationLedBrightness) {
                mNotificationLedBrightnessLevel = CMSettings.System.getInt(resolver,
                        CMSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                        LIGHT_BRIGHTNESS_MAXIMUM);
            }

            // Multiple LEDs enabled
            if (mMultipleNotificationLeds) {
                mMultipleLedsEnabled = CMSettings.System.getInt(resolver,
                        CMSettings.System.NOTIFICATION_LIGHT_MULTIPLE_LEDS_ENABLE,
                        mMultipleNotificationLeds ? 1 : 0) != 0;
            }

            updateLedPulse();
        }
    }
}
