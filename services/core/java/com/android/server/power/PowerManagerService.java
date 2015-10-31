/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

package com.android.server.power;

import com.android.internal.app.IAppOpsService;
import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BackgroundThread;
import com.android.server.EventLogTags;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.am.BatteryStatsService;
import com.android.server.lights.Light;
import com.android.server.lights.LightsManager;
import com.android.server.Watchdog;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SystemSensorManager;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.DisplayManagerInternal.DisplayPowerRequest;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.service.dreams.DreamManagerInternal;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Slog;
import android.util.TimeUtils;
import android.view.Display;
import android.view.WindowManagerPolicy;

import cyanogenmod.providers.CMSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import libcore.util.Objects;

import static android.os.PowerManagerInternal.WAKEFULNESS_ASLEEP;
import static android.os.PowerManagerInternal.WAKEFULNESS_AWAKE;
import static android.os.PowerManagerInternal.WAKEFULNESS_DREAMING;
import static android.os.PowerManagerInternal.WAKEFULNESS_DOZING;

/**
 * The power manager service is responsible for coordinating power management
 * functions on the device.
 */
public final class PowerManagerService extends SystemService
        implements Watchdog.Monitor {
    private static final String TAG = "PowerManagerService";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SPEW = DEBUG && true;

    // Message: Sent when a user activity timeout occurs to update the power state.
    private static final int MSG_USER_ACTIVITY_TIMEOUT = 1;
    // Message: Sent when the device enters or exits a dreaming or dozing state.
    private static final int MSG_SANDMAN = 2;
    // Message: Sent when the screen brightness boost expires.
    private static final int MSG_SCREEN_BRIGHTNESS_BOOST_TIMEOUT = 3;

    private static final int MSG_WAKE_UP = 5;

    // Dirty bit: mWakeLocks changed
    private static final int DIRTY_WAKE_LOCKS = 1 << 0;
    // Dirty bit: mWakefulness changed
    private static final int DIRTY_WAKEFULNESS = 1 << 1;
    // Dirty bit: user activity was poked or may have timed out
    private static final int DIRTY_USER_ACTIVITY = 1 << 2;
    // Dirty bit: actual display power state was updated asynchronously
    private static final int DIRTY_ACTUAL_DISPLAY_POWER_STATE_UPDATED = 1 << 3;
    // Dirty bit: mBootCompleted changed
    private static final int DIRTY_BOOT_COMPLETED = 1 << 4;
    // Dirty bit: settings changed
    private static final int DIRTY_SETTINGS = 1 << 5;
    // Dirty bit: mIsPowered changed
    private static final int DIRTY_IS_POWERED = 1 << 6;
    // Dirty bit: mStayOn changed
    private static final int DIRTY_STAY_ON = 1 << 7;
    // Dirty bit: battery state changed
    private static final int DIRTY_BATTERY_STATE = 1 << 8;
    // Dirty bit: proximity state changed
    private static final int DIRTY_PROXIMITY_POSITIVE = 1 << 9;
    // Dirty bit: dock state changed
    private static final int DIRTY_DOCK_STATE = 1 << 10;
    // Dirty bit: brightness boost changed
    private static final int DIRTY_SCREEN_BRIGHTNESS_BOOST = 1 << 11;

    // Summarizes the state of all active wakelocks.
    private static final int WAKE_LOCK_CPU = 1 << 0;
    private static final int WAKE_LOCK_SCREEN_BRIGHT = 1 << 1;
    private static final int WAKE_LOCK_SCREEN_DIM = 1 << 2;
    private static final int WAKE_LOCK_BUTTON_BRIGHT = 1 << 3;
    private static final int WAKE_LOCK_PROXIMITY_SCREEN_OFF = 1 << 4;
    private static final int WAKE_LOCK_STAY_AWAKE = 1 << 5; // only set if already awake
    private static final int WAKE_LOCK_DOZE = 1 << 6;

    // Summarizes the user activity state.
    private static final int USER_ACTIVITY_SCREEN_BRIGHT = 1 << 0;
    private static final int USER_ACTIVITY_SCREEN_DIM = 1 << 1;
    private static final int USER_ACTIVITY_SCREEN_DREAM = 1 << 2;

    // Default timeout in milliseconds.  This is only used until the settings
    // provider populates the actual default value (R.integer.def_screen_off_timeout).
    private static final int DEFAULT_SCREEN_OFF_TIMEOUT = 15 * 1000;
    private static final int DEFAULT_SLEEP_TIMEOUT = -1;

    // Screen brightness boost timeout.
    // Hardcoded for now until we decide what the right policy should be.
    // This should perhaps be a setting.
    private static final int SCREEN_BRIGHTNESS_BOOST_TIMEOUT = 5 * 1000;

    // Power hints defined in hardware/libhardware/include/hardware/power.h.
    private static final int POWER_HINT_INTERACTION = 2;
    private static final int POWER_HINT_LOW_POWER = 5;

    private static final int DEFAULT_BUTTON_ON_DURATION = 5 * 1000;

    // Config value for NSRM
    private static final int DPM_CONFIG_FEATURE_MASK_NSRM = 0x00000004;

    private static final int BUTTON_ON_DURATION = 5 * 1000;

    // Max time (microseconds) to allow a CPU boost for
    private static final int MAX_CPU_BOOST_TIME = 5000000;

    private static final float PROXIMITY_NEAR_THRESHOLD = 5.0f;

    private final Context mContext;
    private final ServiceThread mHandlerThread;
    private final PowerManagerHandler mHandler;

    private LightsManager mLightsManager;
    private BatteryManagerInternal mBatteryManagerInternal;
    private DisplayManagerInternal mDisplayManagerInternal;
    private IBatteryStats mBatteryStats;
    private IAppOpsService mAppOps;
    private WindowManagerPolicy mPolicy;
    private Notifier mNotifier;
    private WirelessChargerDetector mWirelessChargerDetector;
    private SettingsObserver mSettingsObserver;
    private DreamManagerInternal mDreamManager;
    private Light mAttentionLight;
    private Light mButtonsLight;
    private Light mKeyboardLight;
    private Light mCapsLight;
    private Light mFnLight;

    private int mButtonTimeout;
    private int mButtonBrightness;
    private int mButtonBrightnessSettingDefault;
    private int mKeyboardBrightness;
    private int mKeyboardBrightnessSettingDefault;

    private final Object mLock = new Object();

    // A bitfield that indicates what parts of the power state have
    // changed and need to be recalculated.
    private int mDirty;

    // Indicates whether the device is awake or asleep or somewhere in between.
    // This is distinct from the screen power state, which is managed separately.
    private int mWakefulness;
    private boolean mWakefulnessChanging;

    // True if the sandman has just been summoned for the first time since entering the
    // dreaming or dozing state.  Indicates whether a new dream should begin.
    private boolean mSandmanSummoned;

    // True if MSG_SANDMAN has been scheduled.
    private boolean mSandmanScheduled;

    // Table of all suspend blockers.
    // There should only be a few of these.
    private final ArrayList<SuspendBlocker> mSuspendBlockers = new ArrayList<SuspendBlocker>();

    // Table of all wake locks acquired by applications.
    private final ArrayList<WakeLock> mWakeLocks = new ArrayList<WakeLock>();

    // A bitfield that summarizes the state of all active wakelocks.
    private int mWakeLockSummary;

    // If true, instructs the display controller to wait for the proximity sensor to
    // go negative before turning the screen on.
    private boolean mRequestWaitForNegativeProximity;

    // Timestamp of the last time the device was awoken or put to sleep.
    private long mLastWakeTime;
    private long mLastSleepTime;

    // Timestamp of the last call to user activity.
    private long mLastUserActivityTime;
    private long mLastUserActivityTimeNoChangeLights;

    // Timestamp of last interactive power hint.
    private long mLastInteractivePowerHintTime;

    // Timestamp of the last screen brightness boost.
    private long mLastScreenBrightnessBoostTime;
    private boolean mScreenBrightnessBoostInProgress;

    // A bitfield that summarizes the effect of the user activity timer.
    private int mUserActivitySummary;

    // The desired display power state.  The actual state may lag behind the
    // requested because it is updated asynchronously by the display power controller.
    private final DisplayPowerRequest mDisplayPowerRequest = new DisplayPowerRequest();

    // True if the display power state has been fully applied, which means the display
    // is actually on or actually off or whatever was requested.
    private boolean mDisplayReady;

    // The suspend blocker used to keep the CPU alive when an application has acquired
    // a wake lock.
    private final SuspendBlocker mWakeLockSuspendBlocker;

    // True if the wake lock suspend blocker has been acquired.
    private boolean mHoldingWakeLockSuspendBlocker;

    // The suspend blocker used to keep the CPU alive when the display is on, the
    // display is getting ready or there is user activity (in which case the display
    // must be on).
    private final SuspendBlocker mDisplaySuspendBlocker;

    // True if the display suspend blocker has been acquired.
    private boolean mHoldingDisplaySuspendBlocker;

    // True if systemReady() has been called.
    private boolean mSystemReady;

    // True if boot completed occurred.  We keep the screen on until this happens.
    private boolean mBootCompleted;

    // True if auto-suspend mode is enabled.
    // Refer to autosuspend.h.
    private boolean mHalAutoSuspendModeEnabled;

    // True if interactive mode is enabled.
    // Refer to power.h.
    private boolean mHalInteractiveModeEnabled;

    // True if the device is plugged into a power source.
    private boolean mIsPowered;

    // The current plug type, such as BatteryManager.BATTERY_PLUGGED_WIRELESS.
    private int mPlugType;

    // The current battery level percentage.
    private int mBatteryLevel;

    // The battery level percentage at the time the dream started.
    // This is used to terminate a dream and go to sleep if the battery is
    // draining faster than it is charging and the user activity timeout has expired.
    private int mBatteryLevelWhenDreamStarted;

    // The current dock state.
    private int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;

    // True to decouple auto-suspend mode from the display state.
    private boolean mDecoupleHalAutoSuspendModeFromDisplayConfig;

    // True to decouple interactive mode from the display state.
    private boolean mDecoupleHalInteractiveModeFromDisplayConfig;

    // True if the device should wake up when plugged or unplugged.
    private boolean mWakeUpWhenPluggedOrUnpluggedConfig;

    // True if the device should wake up when plugged or unplugged in theater mode.
    private boolean mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig;

    // True if the device should suspend when the screen is off due to proximity.
    private boolean mSuspendWhenScreenOffDueToProximityConfig;

    // True if dreams are supported on this device.
    private boolean mDreamsSupportedConfig;

    // Default value for dreams enabled
    private boolean mDreamsEnabledByDefaultConfig;

    // Default value for dreams activate-on-sleep
    private boolean mDreamsActivatedOnSleepByDefaultConfig;

    // Default value for dreams activate-on-dock
    private boolean mDreamsActivatedOnDockByDefaultConfig;

    // True if dreams can run while not plugged in.
    private boolean mDreamsEnabledOnBatteryConfig;

    // Minimum battery level to allow dreaming when powered.
    // Use -1 to disable this safety feature.
    private int mDreamsBatteryLevelMinimumWhenPoweredConfig;

    // Minimum battery level to allow dreaming when not powered.
    // Use -1 to disable this safety feature.
    private int mDreamsBatteryLevelMinimumWhenNotPoweredConfig;

    // If the battery level drops by this percentage and the user activity timeout
    // has expired, then assume the device is receiving insufficient current to charge
    // effectively and terminate the dream.  Use -1 to disable this safety feature.
    private int mDreamsBatteryLevelDrainCutoffConfig;

    // True if dreams are enabled by the user.
    private boolean mDreamsEnabledSetting;

    // True if dreams should be activated on sleep.
    private boolean mDreamsActivateOnSleepSetting;

    // True if dreams should be activated on dock.
    private boolean mDreamsActivateOnDockSetting;

    // True if doze should not be started until after the screen off transition.
    private boolean mDozeAfterScreenOffConfig;

    // The minimum screen off timeout, in milliseconds.
    private int mMinimumScreenOffTimeoutConfig;

    // The screen dim duration, in milliseconds.
    // This is subtracted from the end of the screen off timeout so the
    // minimum screen off timeout should be longer than this.
    private int mMaximumScreenDimDurationConfig;

    // The maximum screen dim time expressed as a ratio relative to the screen
    // off timeout.  If the screen off timeout is very short then we want the
    // dim timeout to also be quite short so that most of the time is spent on.
    // Otherwise the user won't get much screen on time before dimming occurs.
    private float mMaximumScreenDimRatioConfig;

    // Default value for proximity prevent accidental wakeups
    private boolean mProximityWakeEnabledByDefaultConfig;

    // The screen off timeout setting value in milliseconds.
    private int mScreenOffTimeoutSetting;

    // The sleep timeout setting value in milliseconds.
    private int mSleepTimeoutSetting;

    // The maximum allowable screen off timeout according to the device
    // administration policy.  Overrides other settings.
    private int mMaximumScreenOffTimeoutFromDeviceAdmin = Integer.MAX_VALUE;

    // The stay on while plugged in setting.
    // 0: Not enabled; 1: debugging over usb; >2: charging.
    private int mStayOnWhilePluggedInSetting;

    // True if the device should wake up when plugged or unplugged
    private int mWakeUpWhenPluggedOrUnpluggedSetting;

    // True if the device should stay on.
    private boolean mStayOn;

    // True if the proximity sensor reads a positive result.
    private boolean mProximityPositive;

    // Screen brightness setting limits.
    private int mScreenBrightnessSettingMinimum;
    private int mScreenBrightnessSettingMaximum;
    private int mScreenBrightnessSettingDefault;

    // The screen brightness setting, from 0 to 255.
    // Use -1 if no value has been set.
    private int mScreenBrightnessSetting;

    // The screen auto-brightness adjustment setting, from -1 to 1.
    // Use 0 if there is no adjustment.
    private float mScreenAutoBrightnessAdjustmentSetting;

    // The screen brightness mode.
    // One of the Settings.System.SCREEN_BRIGHTNESS_MODE_* constants.
    private int mScreenBrightnessModeSetting;

    // The screen brightness setting override from the window manager
    // to allow the current foreground activity to override the brightness.
    // Use -1 to disable.
    private int mScreenBrightnessOverrideFromWindowManager = -1;

    // The button brightness setting override from the window manager
    // to allow the current foreground activity to override the button brightness.
    // Use -1 to disable.
    private int mButtonBrightnessOverrideFromWindowManager = -1;

    // The user activity timeout override from the window manager
    // to allow the current foreground activity to override the user activity timeout.
    // Use -1 to disable.
    private long mUserActivityTimeoutOverrideFromWindowManager = -1;

    // The screen brightness setting override from the settings application
    // to temporarily adjust the brightness until next updated,
    // Use -1 to disable.
    private int mTemporaryScreenBrightnessSettingOverride = -1;

    // The screen brightness adjustment setting override from the settings
    // application to temporarily adjust the auto-brightness adjustment factor
    // until next updated, in the range -1..1.
    // Use NaN to disable.
    private float mTemporaryScreenAutoBrightnessAdjustmentSettingOverride = Float.NaN;

    // The screen state to use while dozing.
    private int mDozeScreenStateOverrideFromDreamManager = Display.STATE_UNKNOWN;

    // The screen brightness to use while dozing.
    private int mDozeScreenBrightnessOverrideFromDreamManager = PowerManager.BRIGHTNESS_DEFAULT;

    // Time when we last logged a warning about calling userActivity() without permission.
    private long mLastWarningAboutUserActivityPermission = Long.MIN_VALUE;

    // If true, the device is in low power mode.
    private boolean mLowPowerModeEnabled;

    // Current state of the low power mode setting.
    private boolean mLowPowerModeSetting;

    // Current state of whether the settings are allowing auto low power mode.
    private boolean mAutoLowPowerModeConfigured;

    // The user turned off low power mode below the trigger level
    private boolean mAutoLowPowerModeSnoozing;

    // True if the battery level is currently considered low.
    private boolean mBatteryLevelLow;

    // True if theater mode is enabled
    private boolean mTheaterModeEnabled;

    private final ArrayList<PowerManagerInternal.LowPowerModeListener> mLowPowerModeListeners
            = new ArrayList<PowerManagerInternal.LowPowerModeListener>();

    //track the blocked uids.
    private final ArrayList<Integer> mBlockedUids = new ArrayList<Integer>();

    private native void nativeInit();

    private static native void nativeAcquireSuspendBlocker(String name);
    private static native void nativeReleaseSuspendBlocker(String name);
    private static native void nativeSetInteractive(boolean enable);
    private static native void nativeSetAutoSuspend(boolean enable);
    private static native void nativeSendPowerHint(int hintId, int data);
    private static native void nativeCpuBoost(int duration);
    private static native void nativeLaunchBoost();
    static native void nativeSetPowerProfile(int profile);
    private boolean mKeyboardVisible = false;

    private SensorManager mSensorManager;
    private Sensor mProximitySensor;
    private boolean mProximityWakeEnabled;
    private int mProximityTimeOut;
    private boolean mProximityWakeSupported;
    android.os.PowerManager.WakeLock mProximityWakeLock;
    SensorEventListener mProximityListener;

    private PerformanceManager mPerformanceManager;

    public PowerManagerService(Context context) {
        super(context);
        mContext = context;
        mHandlerThread = new ServiceThread(TAG,
                Process.THREAD_PRIORITY_DISPLAY, false /*allowIo*/);
        mHandlerThread.start();
        mHandler = new PowerManagerHandler(mHandlerThread.getLooper());
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mPerformanceManager = new PerformanceManager(context);

        synchronized (mLock) {
            mWakeLockSuspendBlocker = createSuspendBlockerLocked("PowerManagerService.WakeLocks");
            mDisplaySuspendBlocker = createSuspendBlockerLocked("PowerManagerService.Display");
            mDisplaySuspendBlocker.acquire();
            mHoldingDisplaySuspendBlocker = true;
            mHalAutoSuspendModeEnabled = false;
            mHalInteractiveModeEnabled = true;

            mWakefulness = WAKEFULNESS_AWAKE;

            nativeInit();
            nativeSetAutoSuspend(false);
            nativeSetInteractive(true);
        }
    }

    @Override
    public void onStart() {
        publishBinderService(Context.POWER_SERVICE, new BinderService());
        publishLocalService(PowerManagerInternal.class, new LocalService());

        Watchdog.getInstance().addMonitor(this);
        Watchdog.getInstance().addThread(mHandler);
    }

    @Override
    public void onBootPhase(int phase) {
        synchronized (mLock) {
            if (phase == PHASE_BOOT_COMPLETED) {
                final long now = SystemClock.uptimeMillis();
                mBootCompleted = true;
                mDirty |= DIRTY_BOOT_COMPLETED;
                userActivityNoUpdateLocked(
                        now, PowerManager.USER_ACTIVITY_EVENT_OTHER, 0, Process.SYSTEM_UID);
                updatePowerStateLocked();
            }
        }
    }

    public void systemReady(IAppOpsService appOps) {
        synchronized (mLock) {
            mSystemReady = true;
            mAppOps = appOps;
            mDreamManager = getLocalService(DreamManagerInternal.class);
            mDisplayManagerInternal = getLocalService(DisplayManagerInternal.class);
            mPolicy = getLocalService(WindowManagerPolicy.class);
            mBatteryManagerInternal = getLocalService(BatteryManagerInternal.class);

            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mScreenBrightnessSettingMinimum = pm.getMinimumScreenBrightnessSetting();
            mScreenBrightnessSettingMaximum = pm.getMaximumScreenBrightnessSetting();
            mScreenBrightnessSettingDefault = pm.getDefaultScreenBrightnessSetting();
            mButtonBrightnessSettingDefault = pm.getDefaultButtonBrightness();
            mKeyboardBrightnessSettingDefault = pm.getDefaultKeyboardBrightness();

            SensorManager sensorManager = new SystemSensorManager(mContext, mHandler.getLooper());

            // The notifier runs on the system server's main looper so as not to interfere
            // with the animations and other critical functions of the power manager.
            mBatteryStats = BatteryStatsService.getService();
            mNotifier = new Notifier(Looper.getMainLooper(), mContext, mBatteryStats,
                    mAppOps, createSuspendBlockerLocked("PowerManagerService.Broadcasts"),
                    mPolicy);

            mWirelessChargerDetector = new WirelessChargerDetector(sensorManager,
                    createSuspendBlockerLocked("PowerManagerService.WirelessChargerDetector"),
                    mHandler);
            mSettingsObserver = new SettingsObserver(mHandler);

            mLightsManager = getLocalService(LightsManager.class);
            mAttentionLight = mLightsManager.getLight(LightsManager.LIGHT_ID_ATTENTION);
            mButtonsLight = mLightsManager.getLight(LightsManager.LIGHT_ID_BUTTONS);
            mKeyboardLight = mLightsManager.getLight(LightsManager.LIGHT_ID_KEYBOARD);
            mCapsLight = mLightsManager.getLight(LightsManager.LIGHT_ID_CAPS);
            mFnLight = mLightsManager.getLight(LightsManager.LIGHT_ID_FUNC);

            // Initialize display power management.
            mDisplayManagerInternal.initPowerManagement(
                    mDisplayPowerCallbacks, mHandler, sensorManager);

            // Register for broadcasts from other components of the system.
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            mContext.registerReceiver(new BatteryReceiver(), filter, null, mHandler);

            filter = new IntentFilter();
            filter.addAction(Intent.ACTION_DREAMING_STARTED);
            filter.addAction(Intent.ACTION_DREAMING_STOPPED);
            mContext.registerReceiver(new DreamReceiver(), filter, null, mHandler);

            filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            mContext.registerReceiver(new UserSwitchedReceiver(), filter, null, mHandler);

            filter = new IntentFilter();
            filter.addAction(Intent.ACTION_DOCK_EVENT);
            mContext.registerReceiver(new DockReceiver(), filter, null, mHandler);

            // Register for settings changes.
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SCREENSAVER_ENABLED),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_OFF_TIMEOUT),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                    Settings.Secure.SLEEP_TIMEOUT),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_BRIGHTNESS),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_BRIGHTNESS_MODE),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.LOW_POWER_MODE),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.THEATER_MODE_ON),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.PROXIMITY_ON_WAKE),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(CMSettings.Secure.getUriFor(
                    CMSettings.Secure.BUTTON_BRIGHTNESS),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(CMSettings.Secure.getUriFor(
                    CMSettings.Secure.KEYBOARD_BRIGHTNESS),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(CMSettings.Secure.getUriFor(
                    CMSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT),
                    false, mSettingsObserver, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED),
                    false, mSettingsObserver, UserHandle.USER_ALL);

            mPerformanceManager.reset();

            // Go.
            readConfigurationLocked();
            updateSettingsLocked();
            mDirty |= DIRTY_BATTERY_STATE;
            updatePowerStateLocked();
        }
    }

    private void readConfigurationLocked() {
        final Resources resources = mContext.getResources();

        mDecoupleHalAutoSuspendModeFromDisplayConfig = resources.getBoolean(
                com.android.internal.R.bool.config_powerDecoupleAutoSuspendModeFromDisplay);
        mDecoupleHalInteractiveModeFromDisplayConfig = resources.getBoolean(
                com.android.internal.R.bool.config_powerDecoupleInteractiveModeFromDisplay);
        mWakeUpWhenPluggedOrUnpluggedConfig = resources.getBoolean(
                com.android.internal.R.bool.config_unplugTurnsOnScreen);
        mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig = resources.getBoolean(
                com.android.internal.R.bool.config_allowTheaterModeWakeFromUnplug);
        mSuspendWhenScreenOffDueToProximityConfig = resources.getBoolean(
                com.android.internal.R.bool.config_suspendWhenScreenOffDueToProximity);
        mDreamsSupportedConfig = resources.getBoolean(
                com.android.internal.R.bool.config_dreamsSupported);
        mDreamsEnabledByDefaultConfig = resources.getBoolean(
                com.android.internal.R.bool.config_dreamsEnabledByDefault);
        mDreamsActivatedOnSleepByDefaultConfig = resources.getBoolean(
                com.android.internal.R.bool.config_dreamsActivatedOnSleepByDefault);
        mDreamsActivatedOnDockByDefaultConfig = resources.getBoolean(
                com.android.internal.R.bool.config_dreamsActivatedOnDockByDefault);
        mDreamsEnabledOnBatteryConfig = resources.getBoolean(
                com.android.internal.R.bool.config_dreamsEnabledOnBattery);
        mDreamsBatteryLevelMinimumWhenPoweredConfig = resources.getInteger(
                com.android.internal.R.integer.config_dreamsBatteryLevelMinimumWhenPowered);
        mDreamsBatteryLevelMinimumWhenNotPoweredConfig = resources.getInteger(
                com.android.internal.R.integer.config_dreamsBatteryLevelMinimumWhenNotPowered);
        mDreamsBatteryLevelDrainCutoffConfig = resources.getInteger(
                com.android.internal.R.integer.config_dreamsBatteryLevelDrainCutoff);
        mDozeAfterScreenOffConfig = resources.getBoolean(
                com.android.internal.R.bool.config_dozeAfterScreenOff);
        mMinimumScreenOffTimeoutConfig = resources.getInteger(
                com.android.internal.R.integer.config_minimumScreenOffTimeout);
        mMaximumScreenDimDurationConfig = resources.getInteger(
                com.android.internal.R.integer.config_maximumScreenDimDuration);
        mMaximumScreenDimRatioConfig = resources.getFraction(
                com.android.internal.R.fraction.config_maximumScreenDimRatio, 1, 1);
        mProximityTimeOut = resources.getInteger(
                com.android.internal.R.integer.config_proximityCheckTimeout);
        mProximityWakeSupported = resources.getBoolean(
                com.android.internal.R.bool.config_proximityCheckOnWake);
        mProximityWakeEnabledByDefaultConfig = resources.getBoolean(
                com.android.internal.R.bool.config_proximityCheckOnWakeEnabledByDefault);
        if (mProximityWakeSupported) {
            PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mProximityWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "ProximityWakeLock");
        }

    }

    private void updateSettingsLocked() {
        final ContentResolver resolver = mContext.getContentResolver();

        mDreamsEnabledSetting = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.SCREENSAVER_ENABLED,
                mDreamsEnabledByDefaultConfig ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        mDreamsActivateOnSleepSetting = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP,
                mDreamsActivatedOnSleepByDefaultConfig ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        mDreamsActivateOnDockSetting = (Settings.Secure.getIntForUser(resolver,
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                mDreamsActivatedOnDockByDefaultConfig ? 1 : 0,
                UserHandle.USER_CURRENT) != 0);
        mScreenOffTimeoutSetting = Settings.System.getIntForUser(resolver,
                Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_TIMEOUT,
                UserHandle.USER_CURRENT);
        mSleepTimeoutSetting = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.SLEEP_TIMEOUT, DEFAULT_SLEEP_TIMEOUT,
                UserHandle.USER_CURRENT);
        mStayOnWhilePluggedInSetting = Settings.Global.getInt(resolver,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
        mTheaterModeEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.THEATER_MODE_ON, 0) == 1;
        mWakeUpWhenPluggedOrUnpluggedSetting = Settings.Global.getInt(resolver,
                Settings.Global.WAKE_WHEN_PLUGGED_OR_UNPLUGGED,
                (mWakeUpWhenPluggedOrUnpluggedConfig ? 1 : 0));
        mProximityWakeEnabled = Settings.System.getInt(resolver,
                Settings.System.PROXIMITY_ON_WAKE, mProximityWakeEnabledByDefaultConfig ? 1 : 0) == 1;

        final int oldScreenBrightnessSetting = mScreenBrightnessSetting;
        mScreenBrightnessSetting = Settings.System.getIntForUser(resolver,
                Settings.System.SCREEN_BRIGHTNESS, mScreenBrightnessSettingDefault,
                UserHandle.USER_CURRENT);
        if (oldScreenBrightnessSetting != mScreenBrightnessSetting) {
            mTemporaryScreenBrightnessSettingOverride = -1;
        }

        final float oldScreenAutoBrightnessAdjustmentSetting =
                mScreenAutoBrightnessAdjustmentSetting;
        mScreenAutoBrightnessAdjustmentSetting = Settings.System.getFloatForUser(resolver,
                Settings.System.SCREEN_AUTO_BRIGHTNESS_ADJ, 0.0f,
                UserHandle.USER_CURRENT);
        if (oldScreenAutoBrightnessAdjustmentSetting != mScreenAutoBrightnessAdjustmentSetting) {
            mTemporaryScreenAutoBrightnessAdjustmentSettingOverride = Float.NaN;
        }

        mScreenBrightnessModeSetting = Settings.System.getIntForUser(resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL, UserHandle.USER_CURRENT);

        final boolean lowPowerModeEnabled = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE, 0) != 0;
        final boolean autoLowPowerModeConfigured = Settings.Global.getInt(resolver,
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0) != 0;
        if (lowPowerModeEnabled != mLowPowerModeSetting
                || autoLowPowerModeConfigured != mAutoLowPowerModeConfigured) {
            mLowPowerModeSetting = lowPowerModeEnabled;
            mAutoLowPowerModeConfigured = autoLowPowerModeConfigured;
            updateLowPowerModeLocked();
        }

        mButtonTimeout = CMSettings.Secure.getIntForUser(resolver,
                CMSettings.Secure.BUTTON_BACKLIGHT_TIMEOUT,
                DEFAULT_BUTTON_ON_DURATION, UserHandle.USER_CURRENT);

        mButtonBrightness = CMSettings.Secure.getIntForUser(resolver,
                CMSettings.Secure.BUTTON_BRIGHTNESS, mButtonBrightnessSettingDefault,
                UserHandle.USER_CURRENT);
        mKeyboardBrightness = CMSettings.Secure.getIntForUser(resolver,
                CMSettings.Secure.KEYBOARD_BRIGHTNESS, mKeyboardBrightnessSettingDefault,
                UserHandle.USER_CURRENT);

        mDirty |= DIRTY_SETTINGS;
    }

    void updateLowPowerModeLocked() {
        if (mIsPowered && mLowPowerModeSetting) {
            if (DEBUG_SPEW) {
                Slog.d(TAG, "updateLowPowerModeLocked: powered, turning setting off");
            }
            // Turn setting off if powered
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.LOW_POWER_MODE, 0);
            // update performance profile
            mPerformanceManager.setPowerProfile(PowerManager.PROFILE_BALANCED);
            mLowPowerModeSetting = false;
        }
        final boolean autoLowPowerModeEnabled = !mIsPowered && mAutoLowPowerModeConfigured
                && !mAutoLowPowerModeSnoozing && mBatteryLevelLow;
        final boolean lowPowerModeEnabled = mLowPowerModeSetting || autoLowPowerModeEnabled;

        if (mLowPowerModeEnabled != lowPowerModeEnabled) {
            mLowPowerModeEnabled = lowPowerModeEnabled;
            powerHintInternal(POWER_HINT_LOW_POWER, lowPowerModeEnabled ? 1 : 0);
            BackgroundThread.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGING)
                            .putExtra(PowerManager.EXTRA_POWER_SAVE_MODE, mLowPowerModeEnabled)
                            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                    mContext.sendBroadcast(intent);
                    ArrayList<PowerManagerInternal.LowPowerModeListener> listeners;
                    synchronized (mLock) {
                        listeners = new ArrayList<PowerManagerInternal.LowPowerModeListener>(
                                mLowPowerModeListeners);
                    }
                    for (int i=0; i<listeners.size(); i++) {
                        listeners.get(i).onLowPowerModeChanged(lowPowerModeEnabled);
                    }
                    intent = new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                    intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                    mContext.sendBroadcast(intent);
                }
            });
        }
    }

    private void handleSettingsChangedLocked() {
        updateSettingsLocked();
        updatePowerStateLocked();
    }

    private void acquireWakeLockInternal(IBinder lock, int flags, String tag, String packageName,
            WorkSource ws, String historyTag, int uid, int pid) {
        synchronized (mLock) {
            if(mBlockedUids.contains(new Integer(uid)) && uid != Process.myUid()) {
                //wakelock acquisition for blocked uid, do not acquire.
                if (DEBUG_SPEW) {
                    Slog.d(TAG, "uid is blocked not acquiring wakeLock flags=0x" +
                            Integer.toHexString(flags) + " tag=" + tag + " uid=" + uid +
                            " pid =" + pid);
                }
                return;
            }
            if (DEBUG_SPEW) {
                Slog.d(TAG, "acquireWakeLockInternal: lock=" + Objects.hashCode(lock)
                        + ", flags=0x" + Integer.toHexString(flags)
                        + ", tag=\"" + tag + "\", ws=" + ws + ", uid=" + uid + ", pid=" + pid);
            }

            WakeLock wakeLock;
            int index = findWakeLockIndexLocked(lock);
            boolean notifyAcquire;
            if (index >= 0) {
                wakeLock = mWakeLocks.get(index);
                if (!wakeLock.hasSameProperties(flags, tag, ws, uid, pid)) {
                    // Update existing wake lock.  This shouldn't happen but is harmless.
                    notifyWakeLockChangingLocked(wakeLock, flags, tag, packageName,
                            uid, pid, ws, historyTag);
                    wakeLock.updateProperties(flags, tag, packageName, ws, historyTag, uid, pid);
                }
                notifyAcquire = false;
            } else {
                wakeLock = new WakeLock(lock, flags, tag, packageName, ws, historyTag, uid, pid);
                try {
                    lock.linkToDeath(wakeLock, 0);
                } catch (RemoteException ex) {
                    throw new IllegalArgumentException("Wake lock is already dead.");
                }
                mWakeLocks.add(wakeLock);
                notifyAcquire = true;
            }

            applyWakeLockFlagsOnAcquireLocked(wakeLock, uid);
            mDirty |= DIRTY_WAKE_LOCKS;
            updatePowerStateLocked();
            if (notifyAcquire) {
                // This needs to be done last so we are sure we have acquired the
                // kernel wake lock.  Otherwise we have a race where the system may
                // go to sleep between the time we start the accounting in battery
                // stats and when we actually get around to telling the kernel to
                // stay awake.
                notifyWakeLockAcquiredLocked(wakeLock);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isScreenLock(final WakeLock wakeLock) {
        switch (wakeLock.mFlags & PowerManager.WAKE_LOCK_LEVEL_MASK) {
            case PowerManager.FULL_WAKE_LOCK:
            case PowerManager.SCREEN_BRIGHT_WAKE_LOCK:
            case PowerManager.SCREEN_DIM_WAKE_LOCK:
                return true;
        }
        return false;
    }

    private void applyWakeLockFlagsOnAcquireLocked(WakeLock wakeLock, int uid) {
        if ((wakeLock.mFlags & PowerManager.ACQUIRE_CAUSES_WAKEUP) != 0
                && isScreenLock(wakeLock)) {
            wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), uid);
        }
    }

    private void releaseWakeLockInternal(IBinder lock, int flags) {
        synchronized (mLock) {
            int index = findWakeLockIndexLocked(lock);
            if (index < 0) {
                if (DEBUG_SPEW) {
                    Slog.d(TAG, "releaseWakeLockInternal: lock=" + Objects.hashCode(lock)
                            + " [not found], flags=0x" + Integer.toHexString(flags));
                }
                return;
            }

            WakeLock wakeLock = mWakeLocks.get(index);
            if (DEBUG_SPEW) {
                Slog.d(TAG, "releaseWakeLockInternal: lock=" + Objects.hashCode(lock)
                        + " [" + wakeLock.mTag + "], flags=0x" + Integer.toHexString(flags));
            }

            if ((flags & PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) != 0) {
                mRequestWaitForNegativeProximity = true;
            }

            wakeLock.mLock.unlinkToDeath(wakeLock, 0);
            removeWakeLockLocked(wakeLock, index);
        }
    }

    private void handleWakeLockDeath(WakeLock wakeLock) {
        synchronized (mLock) {
            if (DEBUG_SPEW) {
                Slog.d(TAG, "handleWakeLockDeath: lock=" + Objects.hashCode(wakeLock.mLock)
                        + " [" + wakeLock.mTag + "]");
            }

            int index = mWakeLocks.indexOf(wakeLock);
            if (index < 0) {
                return;
            }

            removeWakeLockLocked(wakeLock, index);
        }
    }

    private void removeWakeLockLocked(WakeLock wakeLock, int index) {
        mWakeLocks.remove(index);
        notifyWakeLockReleasedLocked(wakeLock);

        applyWakeLockFlagsOnReleaseLocked(wakeLock);
        mDirty |= DIRTY_WAKE_LOCKS;
        updatePowerStateLocked();
    }

    private void applyWakeLockFlagsOnReleaseLocked(WakeLock wakeLock) {
        if ((wakeLock.mFlags & PowerManager.ON_AFTER_RELEASE) != 0
                && isScreenLock(wakeLock)) {
            userActivityNoUpdateLocked(SystemClock.uptimeMillis(),
                    PowerManager.USER_ACTIVITY_EVENT_OTHER,
                    PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS,
                    wakeLock.mOwnerUid);
        }
    }

    private void updateWakeLockWorkSourceInternal(IBinder lock, WorkSource ws, String historyTag,
            int callingUid) {
        synchronized (mLock) {
            int index = findWakeLockIndexLocked(lock);
            int value = SystemProperties.getInt("persist.dpm.feature", 0);
            boolean isNsrmEnabled = false;

            if ((value & DPM_CONFIG_FEATURE_MASK_NSRM) == DPM_CONFIG_FEATURE_MASK_NSRM)
                isNsrmEnabled = true;

            if (index < 0) {
                if (DEBUG_SPEW) {
                    Slog.d(TAG, "updateWakeLockWorkSourceInternal: lock=" + Objects.hashCode(lock)
                            + " [not found], ws=" + ws);
                }
                if (!isNsrmEnabled) {
                throw new IllegalArgumentException("Wake lock not active: " + lock
                        + " from uid " + callingUid);
                } else {
                    return;
                }
            }

            WakeLock wakeLock = mWakeLocks.get(index);
            if (DEBUG_SPEW) {
                Slog.d(TAG, "updateWakeLockWorkSourceInternal: lock=" + Objects.hashCode(lock)
                        + " [" + wakeLock.mTag + "], ws=" + ws);
            }

            if (!wakeLock.hasSameWorkSource(ws)) {
                notifyWakeLockChangingLocked(wakeLock, wakeLock.mFlags, wakeLock.mTag,
                        wakeLock.mPackageName, wakeLock.mOwnerUid, wakeLock.mOwnerPid,
                        ws, historyTag);
                wakeLock.mHistoryTag = historyTag;
                wakeLock.updateWorkSource(ws);
            }
        }
    }

    private boolean checkWorkSourceObjectId(int uid, WakeLock wl) {
        try {
            for (int index = 0; index < wl.mWorkSource.size(); index++) {
                if (uid == wl.mWorkSource.get(index)) {
                    if (DEBUG_SPEW) Slog.v(TAG, "WS uid matched");
                    return true;
                }
            }
        }
        catch (Exception e) {
            return false;
        }
        return false;
    }

    private int findWakeLockIndexLocked(IBinder lock) {
        final int count = mWakeLocks.size();
        for (int i = 0; i < count; i++) {
            if (mWakeLocks.get(i).mLock == lock) {
                return i;
            }
        }
        return -1;
    }

    private void notifyWakeLockAcquiredLocked(WakeLock wakeLock) {
        if (mSystemReady) {
            wakeLock.mNotifiedAcquired = true;
            mNotifier.onWakeLockAcquired(wakeLock.mFlags, wakeLock.mTag, wakeLock.mPackageName,
                    wakeLock.mOwnerUid, wakeLock.mOwnerPid, wakeLock.mWorkSource,
                    wakeLock.mHistoryTag);
        }
    }

    private void notifyWakeLockChangingLocked(WakeLock wakeLock, int flags, String tag,
            String packageName, int uid, int pid, WorkSource ws, String historyTag) {
        if (mSystemReady && wakeLock.mNotifiedAcquired) {
            mNotifier.onWakeLockChanging(wakeLock.mFlags, wakeLock.mTag, wakeLock.mPackageName,
                    wakeLock.mOwnerUid, wakeLock.mOwnerPid, wakeLock.mWorkSource,
                    wakeLock.mHistoryTag, flags, tag, packageName, uid, pid, ws, historyTag);
        }
    }

    private void notifyWakeLockReleasedLocked(WakeLock wakeLock) {
        if (mSystemReady && wakeLock.mNotifiedAcquired) {
            wakeLock.mNotifiedAcquired = false;
            mNotifier.onWakeLockReleased(wakeLock.mFlags, wakeLock.mTag,
                    wakeLock.mPackageName, wakeLock.mOwnerUid, wakeLock.mOwnerPid,
                    wakeLock.mWorkSource, wakeLock.mHistoryTag);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isWakeLockLevelSupportedInternal(int level) {
        synchronized (mLock) {
            switch (level) {
                case PowerManager.PARTIAL_WAKE_LOCK:
                case PowerManager.SCREEN_DIM_WAKE_LOCK:
                case PowerManager.SCREEN_BRIGHT_WAKE_LOCK:
                case PowerManager.FULL_WAKE_LOCK:
                case PowerManager.DOZE_WAKE_LOCK:
                    return true;

                case PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK:
                    return mSystemReady && mDisplayManagerInternal.isProximitySensorAvailable();

                default:
                    return false;
            }
        }
    }

    private boolean isQuickBootCall() {

        ActivityManager activityManager = (ActivityManager) mContext
                .getSystemService(Context.ACTIVITY_SERVICE);

        List<ActivityManager.RunningAppProcessInfo> runningList = activityManager
                .getRunningAppProcesses();
        int callingPid = Binder.getCallingPid();
        for (ActivityManager.RunningAppProcessInfo processInfo : runningList) {
            if (processInfo.pid == callingPid) {
                String process = processInfo.processName;
                if ("com.qapp.quickboot".equals(process))
                    return true;
            }
        }
        return false;
    }

    // Called from native code.
    private void userActivityFromNative(long eventTime, int event, int flags) {
        userActivityInternal(eventTime, event, flags, Process.SYSTEM_UID);
    }

    private void userActivityInternal(long eventTime, int event, int flags, int uid) {
        synchronized (mLock) {
            if (userActivityNoUpdateLocked(eventTime, event, flags, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    private boolean userActivityNoUpdateLocked(long eventTime, int event, int flags, int uid) {
        if (DEBUG_SPEW) {
            Slog.d(TAG, "userActivityNoUpdateLocked: eventTime=" + eventTime
                    + ", event=" + event + ", flags=0x" + Integer.toHexString(flags)
                    + ", uid=" + uid);
        }

        if (eventTime < mLastSleepTime || eventTime < mLastWakeTime
                || !mBootCompleted || !mSystemReady) {
            return false;
        }

        Trace.traceBegin(Trace.TRACE_TAG_POWER, "userActivity");
        try {
            if (eventTime > mLastInteractivePowerHintTime) {
                powerHintInternal(POWER_HINT_INTERACTION, 0);
                mLastInteractivePowerHintTime = eventTime;
            }

            mNotifier.onUserActivity(event, uid);

            if (mWakefulness == WAKEFULNESS_ASLEEP
                    || mWakefulness == WAKEFULNESS_DOZING
                    || (flags & PowerManager.USER_ACTIVITY_FLAG_INDIRECT) != 0) {
                return false;
            }

            if ((flags & PowerManager.USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS) != 0) {
                if (eventTime > mLastUserActivityTimeNoChangeLights
                        && eventTime > mLastUserActivityTime) {
                    mLastUserActivityTimeNoChangeLights = eventTime;
                    mDirty |= DIRTY_USER_ACTIVITY;
                    return true;
                }
            } else {
                if (eventTime > mLastUserActivityTime) {
                    mLastUserActivityTime = eventTime;
                    mDirty |= DIRTY_USER_ACTIVITY;
                    return true;
                }
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_POWER);
        }
        return false;
    }

    private void wakeUpInternal(long eventTime, int uid) {
        synchronized (mLock) {
            if (wakeUpNoUpdateLocked(eventTime, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    private boolean wakeUpNoUpdateLocked(long eventTime, int uid) {
        if (DEBUG_SPEW) {
            Slog.d(TAG, "wakeUpNoUpdateLocked: eventTime=" + eventTime + ", uid=" + uid);
        }

        if (eventTime < mLastSleepTime || mWakefulness == WAKEFULNESS_AWAKE
                || !mBootCompleted || !mSystemReady) {
            return false;
        }

        Trace.traceBegin(Trace.TRACE_TAG_POWER, "wakeUp");
        try {
            switch (mWakefulness) {
                case WAKEFULNESS_ASLEEP:
                    Slog.i(TAG, "Waking up from sleep (uid " + uid +")...");
                    break;
                case WAKEFULNESS_DREAMING:
                    Slog.i(TAG, "Waking up from dream (uid " + uid +")...");
                    break;
                case WAKEFULNESS_DOZING:
                    Slog.i(TAG, "Waking up from dozing (uid " + uid +")...");
                    break;
            }

            mLastWakeTime = eventTime;
            setWakefulnessLocked(WAKEFULNESS_AWAKE, 0);

            userActivityNoUpdateLocked(
                    eventTime, PowerManager.USER_ACTIVITY_EVENT_OTHER, 0, uid);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_POWER);
        }
        return true;
    }

    private void enableQbCharger(boolean enable) {
        if (SystemProperties.getInt("sys.quickboot.enable", 0) == 1 &&
                SystemProperties.getInt("sys.quickboot.poweroff", 0) != 1) {
            // only handle "charged" event, qbcharger process will handle
            // "uncharged" event itself
            if (enable && mIsPowered && !isInteractiveInternal()) {
                SystemProperties.set("sys.qbcharger.enable", "true");
            }
        }
    }

    private void goToSleepInternal(long eventTime, int reason, int flags, int uid) {
        synchronized (mLock) {
            if (goToSleepNoUpdateLocked(eventTime, reason, flags, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    // This method is called goToSleep for historical reasons but we actually start
    // dozing before really going to sleep.
    @SuppressWarnings("deprecation")
    private boolean goToSleepNoUpdateLocked(long eventTime, int reason, int flags, int uid) {
        if (DEBUG_SPEW) {
            Slog.d(TAG, "goToSleepNoUpdateLocked: eventTime=" + eventTime
                    + ", reason=" + reason + ", flags=" + flags + ", uid=" + uid);
        }

        if (eventTime < mLastWakeTime
                || mWakefulness == WAKEFULNESS_ASLEEP
                || mWakefulness == WAKEFULNESS_DOZING
                || !mBootCompleted || !mSystemReady) {
            return false;
        }

        Trace.traceBegin(Trace.TRACE_TAG_POWER, "goToSleep");
        try {
            switch (reason) {
                case PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN:
                    Slog.i(TAG, "Going to sleep due to device administration policy "
                            + "(uid " + uid +")...");
                    break;
                case PowerManager.GO_TO_SLEEP_REASON_TIMEOUT:
                    Slog.i(TAG, "Going to sleep due to screen timeout (uid " + uid +")...");
                    break;
                case PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH:
                    Slog.i(TAG, "Going to sleep due to lid switch (uid " + uid +")...");
                    break;
                case PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON:
                    Slog.i(TAG, "Going to sleep due to power button (uid " + uid +")...");
                    break;
                case PowerManager.GO_TO_SLEEP_REASON_HDMI:
                    Slog.i(TAG, "Going to sleep due to HDMI standby (uid " + uid +")...");
                    break;
                default:
                    Slog.i(TAG, "Going to sleep by application request (uid " + uid +")...");
                    reason = PowerManager.GO_TO_SLEEP_REASON_APPLICATION;
                    break;
            }

            mLastSleepTime = eventTime;
            mSandmanSummoned = true;
            setWakefulnessLocked(WAKEFULNESS_DOZING, reason);

            // Report the number of wake locks that will be cleared by going to sleep.
            int numWakeLocksCleared = 0;
            final int numWakeLocks = mWakeLocks.size();
            for (int i = 0; i < numWakeLocks; i++) {
                final WakeLock wakeLock = mWakeLocks.get(i);
                switch (wakeLock.mFlags & PowerManager.WAKE_LOCK_LEVEL_MASK) {
                    case PowerManager.FULL_WAKE_LOCK:
                    case PowerManager.SCREEN_BRIGHT_WAKE_LOCK:
                    case PowerManager.SCREEN_DIM_WAKE_LOCK:
                        numWakeLocksCleared += 1;
                        break;
                }
            }
            EventLog.writeEvent(EventLogTags.POWER_SLEEP_REQUESTED, numWakeLocksCleared);

            // Skip dozing if requested.
            if ((flags & PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE) != 0) {
                reallyGoToSleepNoUpdateLocked(eventTime, uid);
            }
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_POWER);
        }
        return true;
    }

    private void napInternal(long eventTime, int uid) {
        synchronized (mLock) {
            if (napNoUpdateLocked(eventTime, uid)) {
                updatePowerStateLocked();
            }
        }
    }

    private boolean napNoUpdateLocked(long eventTime, int uid) {
        if (DEBUG_SPEW) {
            Slog.d(TAG, "napNoUpdateLocked: eventTime=" + eventTime + ", uid=" + uid);
        }

        if (eventTime < mLastWakeTime || mWakefulness != WAKEFULNESS_AWAKE
                || !mBootCompleted || !mSystemReady) {
            return false;
        }

        Trace.traceBegin(Trace.TRACE_TAG_POWER, "nap");
        try {
            Slog.i(TAG, "Nap time (uid " + uid +")...");

            mSandmanSummoned = true;
            setWakefulnessLocked(WAKEFULNESS_DREAMING, 0);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_POWER);
        }
        return true;
    }

    // Done dozing, drop everything and go to sleep.
    private boolean reallyGoToSleepNoUpdateLocked(long eventTime, int uid) {
        if (DEBUG_SPEW) {
            Slog.d(TAG, "reallyGoToSleepNoUpdateLocked: eventTime=" + eventTime
                    + ", uid=" + uid);
        }

        if (eventTime < mLastWakeTime || mWakefulness == WAKEFULNESS_ASLEEP
                || !mBootCompleted || !mSystemReady) {
            return false;
        }

        Trace.traceBegin(Trace.TRACE_TAG_POWER, "reallyGoToSleep");
        try {
            Slog.i(TAG, "Sleeping (uid " + uid +")...");

            setWakefulnessLocked(WAKEFULNESS_ASLEEP, PowerManager.GO_TO_SLEEP_REASON_TIMEOUT);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_POWER);
        }
        return true;
    }

    private void setWakefulnessLocked(int wakefulness, int reason) {
        if (mWakefulness != wakefulness) {
            finishWakefulnessChangeLocked();

            mWakefulness = wakefulness;
            mWakefulnessChanging = true;
            mDirty |= DIRTY_WAKEFULNESS;
            mNotifier.onWakefulnessChangeStarted(wakefulness, reason);
        }
    }

    private void finishWakefulnessChangeLocked() {
        if (mWakefulnessChanging) {
            mNotifier.onWakefulnessChangeFinished(mWakefulness);
            mWakefulnessChanging = false;
        }
    }

    /**
     * Updates the global power state based on dirty bits recorded in mDirty.
     *
     * This is the main function that performs power state transitions.
     * We centralize them here so that we can recompute the power state completely
     * each time something important changes, and ensure that we do it the same
     * way each time.  The point is to gather all of the transition logic here.
     */
    private void updatePowerStateLocked() {
        if (!mSystemReady || mDirty == 0) {
            return;
        }
        if (!Thread.holdsLock(mLock)) {
            Slog.wtf(TAG, "Power manager lock was not held when calling updatePowerStateLocked");
        }

        Trace.traceBegin(Trace.TRACE_TAG_POWER, "updatePowerState");
        try {
            // Phase 0: Basic state updates.
            updateIsPoweredLocked(mDirty);
            updateStayOnLocked(mDirty);
            updateScreenBrightnessBoostLocked(mDirty);

            // Phase 1: Update wakefulness.
            // Loop because the wake lock and user activity computations are influenced
            // by changes in wakefulness.
            final long now = SystemClock.uptimeMillis();
            int dirtyPhase2 = 0;
            for (;;) {
                int dirtyPhase1 = mDirty;
                dirtyPhase2 |= dirtyPhase1;
                mDirty = 0;

                updateWakeLockSummaryLocked(dirtyPhase1);
                updateUserActivitySummaryLocked(now, dirtyPhase1);
                if (!updateWakefulnessLocked(dirtyPhase1)) {
                    break;
                }
            }

            // Phase 2: Update display power state.
            boolean displayBecameReady = updateDisplayPowerStateLocked(dirtyPhase2);

            // Phase 3: Update dream state (depends on display ready signal).
            updateDreamLocked(dirtyPhase2, displayBecameReady);

            // Phase 4: Send notifications, if needed.
            if (mDisplayReady) {
                finishWakefulnessChangeLocked();
            }

            // Phase 5: Update suspend blocker.
            // Because we might release the last suspend blocker here, we need to make sure
            // we finished everything else first!
            updateSuspendBlockerLocked();
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_POWER);
        }
    }

    /**
     * Updates the value of mIsPowered.
     * Sets DIRTY_IS_POWERED if a change occurred.
     */
    private void updateIsPoweredLocked(int dirty) {
        if ((dirty & DIRTY_BATTERY_STATE) != 0) {
            final boolean wasPowered = mIsPowered;
            final int oldPlugType = mPlugType;
            final boolean oldLevelLow = mBatteryLevelLow;
            mIsPowered = mBatteryManagerInternal.isPowered(BatteryManager.BATTERY_PLUGGED_ANY);
            mPlugType = mBatteryManagerInternal.getPlugType();
            mBatteryLevel = mBatteryManagerInternal.getBatteryLevel();
            mBatteryLevelLow = mBatteryManagerInternal.getBatteryLevelLow();

            if (DEBUG_SPEW) {
                Slog.d(TAG, "updateIsPoweredLocked: wasPowered=" + wasPowered
                        + ", mIsPowered=" + mIsPowered
                        + ", oldPlugType=" + oldPlugType
                        + ", mPlugType=" + mPlugType
                        + ", mBatteryLevel=" + mBatteryLevel);
            }

            enableQbCharger(mIsPowered);
            if (wasPowered != mIsPowered || oldPlugType != mPlugType) {
                mDirty |= DIRTY_IS_POWERED;

                // Update wireless dock detection state.
                final boolean dockedOnWirelessCharger = mWirelessChargerDetector.update(
                        mIsPowered, mPlugType, mBatteryLevel);

                // Treat plugging and unplugging the devices as a user activity.
                // Users find it disconcerting when they plug or unplug the device
                // and it shuts off right away.
                // Some devices also wake the device when plugged or unplugged because
                // they don't have a charging LED.
                final long now = SystemClock.uptimeMillis();
                if (shouldWakeUpWhenPluggedOrUnpluggedLocked(wasPowered, oldPlugType,
                        dockedOnWirelessCharger)) {
                    wakeUpNoUpdateLocked(now, Process.SYSTEM_UID);
                }
                userActivityNoUpdateLocked(
                        now, PowerManager.USER_ACTIVITY_EVENT_OTHER, 0, Process.SYSTEM_UID);

                // Tell the notifier whether wireless charging has started so that
                // it can provide feedback to the user.
                if (dockedOnWirelessCharger) {
                    mNotifier.onWirelessChargingStarted();
                }
            }

            if (wasPowered != mIsPowered || oldLevelLow != mBatteryLevelLow) {
                if (oldLevelLow != mBatteryLevelLow && !mBatteryLevelLow) {
                    if (DEBUG_SPEW) {
                        Slog.d(TAG, "updateIsPoweredLocked: resetting low power snooze");
                    }
                    mAutoLowPowerModeSnoozing = false;
                }
                updateLowPowerModeLocked();
            }
        }
    }

    private boolean shouldWakeUpWhenPluggedOrUnpluggedLocked(
            boolean wasPowered, int oldPlugType, boolean dockedOnWirelessCharger) {
        // Don't wake when powered unless configured to do so.
        if (mWakeUpWhenPluggedOrUnpluggedSetting == 0) {
            return false;
        }

       if (SystemProperties.getInt("sys.quickboot.enable", 0) == 1) {
            return false;
        }

        // Don't wake when undocked from wireless charger.
        // See WirelessChargerDetector for justification.
        if (wasPowered && !mIsPowered
                && oldPlugType == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
            return false;
        }

        // Don't wake when docked on wireless charger unless we are certain of it.
        // See WirelessChargerDetector for justification.
        if (!wasPowered && mIsPowered
                && mPlugType == BatteryManager.BATTERY_PLUGGED_WIRELESS
                && !dockedOnWirelessCharger) {
            return false;
        }

        // If already dreaming and becoming powered, then don't wake.
        if (mIsPowered && mWakefulness == WAKEFULNESS_DREAMING) {
            return false;
        }

        // Don't wake while theater mode is enabled.
        if (mTheaterModeEnabled && !mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig) {
            return false;
        }

        // Otherwise wake up!
        return true;
    }

    /**
     * Updates the value of mStayOn.
     * Sets DIRTY_STAY_ON if a change occurred.
     */
    private void updateStayOnLocked(int dirty) {
        if ((dirty & (DIRTY_BATTERY_STATE | DIRTY_SETTINGS)) != 0) {
            final boolean wasStayOn = mStayOn;
            if (mStayOnWhilePluggedInSetting != 0
                    && !isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked()) {
                switch (mStayOnWhilePluggedInSetting) {
                    case 1: // Debugging only over usb
                        mStayOn = ((mPlugType & BatteryManager.BATTERY_PLUGGED_USB) != 0)
                                && Settings.Global.getInt(mContext.getContentResolver(),
                                        Settings.Global.ADB_ENABLED, 0) != 0;;
                        break;
                    default: // charging
                        mStayOn = mIsPowered;
                        break;
                }
            } else {
                mStayOn = false;
            }

            if (mStayOn != wasStayOn) {
                mDirty |= DIRTY_STAY_ON;
            }
        }
    }

    /**
     * Updates the value of mWakeLockSummary to summarize the state of all active wake locks.
     * Note that most wake-locks are ignored when the system is asleep.
     *
     * This function must have no other side-effects.
     */
    @SuppressWarnings("deprecation")
    private void updateWakeLockSummaryLocked(int dirty) {
        if ((dirty & (DIRTY_WAKE_LOCKS | DIRTY_WAKEFULNESS)) != 0) {
            mWakeLockSummary = 0;

            final int numWakeLocks = mWakeLocks.size();
            for (int i = 0; i < numWakeLocks; i++) {
                final WakeLock wakeLock = mWakeLocks.get(i);
                switch (wakeLock.mFlags & PowerManager.WAKE_LOCK_LEVEL_MASK) {
                    case PowerManager.PARTIAL_WAKE_LOCK:
                        mWakeLockSummary |= WAKE_LOCK_CPU;
                        break;
                    case PowerManager.FULL_WAKE_LOCK:
                        mWakeLockSummary |= WAKE_LOCK_SCREEN_BRIGHT | WAKE_LOCK_BUTTON_BRIGHT;
                        break;
                    case PowerManager.SCREEN_BRIGHT_WAKE_LOCK:
                        mWakeLockSummary |= WAKE_LOCK_SCREEN_BRIGHT;
                        break;
                    case PowerManager.SCREEN_DIM_WAKE_LOCK:
                        mWakeLockSummary |= WAKE_LOCK_SCREEN_DIM;
                        break;
                    case PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK:
                        mWakeLockSummary |= WAKE_LOCK_PROXIMITY_SCREEN_OFF;
                        break;
                    case PowerManager.DOZE_WAKE_LOCK:
                        mWakeLockSummary |= WAKE_LOCK_DOZE;
                        break;
                }
            }

            // Cancel wake locks that make no sense based on the current state.
            if (mWakefulness != WAKEFULNESS_DOZING) {
                mWakeLockSummary &= ~WAKE_LOCK_DOZE;
            }
            if (mWakefulness == WAKEFULNESS_ASLEEP
                    || (mWakeLockSummary & WAKE_LOCK_DOZE) != 0) {
                mWakeLockSummary &= ~(WAKE_LOCK_SCREEN_BRIGHT | WAKE_LOCK_SCREEN_DIM
                        | WAKE_LOCK_BUTTON_BRIGHT);
                if (mWakefulness == WAKEFULNESS_ASLEEP) {
                    mWakeLockSummary &= ~WAKE_LOCK_PROXIMITY_SCREEN_OFF;
                }
            }

            // Infer implied wake locks where necessary based on the current state.
            if ((mWakeLockSummary & (WAKE_LOCK_SCREEN_BRIGHT | WAKE_LOCK_SCREEN_DIM)) != 0) {
                if (mWakefulness == WAKEFULNESS_AWAKE) {
                    mWakeLockSummary |= WAKE_LOCK_CPU | WAKE_LOCK_STAY_AWAKE;
                } else if (mWakefulness == WAKEFULNESS_DREAMING) {
                    mWakeLockSummary |= WAKE_LOCK_CPU;
                }
            }

            if (DEBUG_SPEW) {
                Slog.d(TAG, "updateWakeLockSummaryLocked: mWakefulness="
                        + PowerManagerInternal.wakefulnessToString(mWakefulness)
                        + ", mWakeLockSummary=0x" + Integer.toHexString(mWakeLockSummary));
            }
        }
    }

    /**
     * Updates the value of mUserActivitySummary to summarize the user requested
     * state of the system such as whether the screen should be bright or dim.
     * Note that user activity is ignored when the system is asleep.
     *
     * This function must have no other side-effects.
     */
    private void updateUserActivitySummaryLocked(long now, int dirty) {
        // Update the status of the user activity timeout timer.
        if ((dirty & (DIRTY_WAKE_LOCKS | DIRTY_USER_ACTIVITY
                | DIRTY_WAKEFULNESS | DIRTY_SETTINGS)) != 0) {
            mHandler.removeMessages(MSG_USER_ACTIVITY_TIMEOUT);

            long nextTimeout = 0;
            if (mWakefulness == WAKEFULNESS_AWAKE
                    || mWakefulness == WAKEFULNESS_DREAMING
                    || mWakefulness == WAKEFULNESS_DOZING) {
                final int sleepTimeout = getSleepTimeoutLocked();
                final int screenOffTimeout = getScreenOffTimeoutLocked(sleepTimeout);
                final int screenDimDuration = getScreenDimDurationLocked(screenOffTimeout);

                mUserActivitySummary = 0;
                if (mLastUserActivityTime >= mLastWakeTime) {
                    nextTimeout = mLastUserActivityTime
                            + screenOffTimeout - screenDimDuration;
                    if (now < nextTimeout) {
                        mUserActivitySummary = USER_ACTIVITY_SCREEN_BRIGHT;
                        if (mWakefulness == WAKEFULNESS_AWAKE) {
                            int buttonBrightness, keyboardBrightness;
                            if (mButtonBrightnessOverrideFromWindowManager >= 0) {
                                buttonBrightness = mButtonBrightnessOverrideFromWindowManager;
                                keyboardBrightness = mButtonBrightnessOverrideFromWindowManager;
                            } else {
                                buttonBrightness = mButtonBrightness;
                                keyboardBrightness = mKeyboardBrightness;
                            }

                            mKeyboardLight.setBrightness(mKeyboardVisible ?
                                    keyboardBrightness : 0);
                            if (mButtonTimeout != 0
                                    && now > mLastUserActivityTime + mButtonTimeout) {
                                mButtonsLight.setBrightness(0);
                            } else {
                                if (!mProximityPositive) {
                                    mButtonsLight.setBrightness(buttonBrightness);
                                    if (buttonBrightness != 0 && mButtonTimeout != 0) {
                                        nextTimeout = now + mButtonTimeout;
                                    }
                                }
                            }
                        }
                    } else {
                        nextTimeout = mLastUserActivityTime + screenOffTimeout;
                        if (now < nextTimeout) {
                            mUserActivitySummary = USER_ACTIVITY_SCREEN_DIM;
                            if (mWakefulness == WAKEFULNESS_AWAKE) {
                                mButtonsLight.setBrightness(0);
                                mKeyboardLight.setBrightness(0);
                            }
                        }
                    }
                }
                if (mUserActivitySummary == 0
                        && mLastUserActivityTimeNoChangeLights >= mLastWakeTime) {
                    nextTimeout = mLastUserActivityTimeNoChangeLights + screenOffTimeout;
                    if (now < nextTimeout) {
                        if (mDisplayPowerRequest.policy == DisplayPowerRequest.POLICY_BRIGHT) {
                            mUserActivitySummary = USER_ACTIVITY_SCREEN_BRIGHT;
                        } else if (mDisplayPowerRequest.policy == DisplayPowerRequest.POLICY_DIM) {
                            mUserActivitySummary = USER_ACTIVITY_SCREEN_DIM;
                        }
                    }
                }
                if (mUserActivitySummary == 0) {
                    if (sleepTimeout >= 0) {
                        final long anyUserActivity = Math.max(mLastUserActivityTime,
                                mLastUserActivityTimeNoChangeLights);
                        if (anyUserActivity >= mLastWakeTime) {
                            nextTimeout = anyUserActivity + sleepTimeout;
                            if (now < nextTimeout) {
                                mUserActivitySummary = USER_ACTIVITY_SCREEN_DREAM;
                            }
                        }
                    } else {
                        mUserActivitySummary = USER_ACTIVITY_SCREEN_DREAM;
                        nextTimeout = -1;
                    }
                }
                if (mUserActivitySummary != 0 && nextTimeout >= 0) {
                    Message msg = mHandler.obtainMessage(MSG_USER_ACTIVITY_TIMEOUT);
                    msg.setAsynchronous(true);
                    mHandler.sendMessageAtTime(msg, nextTimeout);
                }
            } else {
                mUserActivitySummary = 0;
            }

            if (DEBUG_SPEW) {
                Slog.d(TAG, "updateUserActivitySummaryLocked: mWakefulness="
                        + PowerManagerInternal.wakefulnessToString(mWakefulness)
                        + ", mUserActivitySummary=0x" + Integer.toHexString(mUserActivitySummary)
                        + ", nextTimeout=" + TimeUtils.formatUptime(nextTimeout));
            }
        }
    }

    /**
     * Called when a user activity timeout has occurred.
     * Simply indicates that something about user activity has changed so that the new
     * state can be recomputed when the power state is updated.
     *
     * This function must have no other side-effects besides setting the dirty
     * bit and calling update power state.  Wakefulness transitions are handled elsewhere.
     */
    private void handleUserActivityTimeout() { // runs on handler thread
        synchronized (mLock) {
            if (DEBUG_SPEW) {
                Slog.d(TAG, "handleUserActivityTimeout");
            }

            mDirty |= DIRTY_USER_ACTIVITY;
            updatePowerStateLocked();
        }
    }

    private int getSleepTimeoutLocked() {
        int timeout = mSleepTimeoutSetting;
        if (timeout <= 0) {
            return -1;
        }
        return Math.max(timeout, mMinimumScreenOffTimeoutConfig);
    }

    private int getScreenOffTimeoutLocked(int sleepTimeout) {
        int timeout = mScreenOffTimeoutSetting;
        if (isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked()) {
            timeout = Math.min(timeout, mMaximumScreenOffTimeoutFromDeviceAdmin);
        }
        if (mUserActivityTimeoutOverrideFromWindowManager >= 0) {
            timeout = (int)Math.min(timeout, mUserActivityTimeoutOverrideFromWindowManager);
        }
        if (sleepTimeout >= 0) {
            timeout = Math.min(timeout, sleepTimeout);
        }
        return Math.max(timeout, mMinimumScreenOffTimeoutConfig);
    }

    private int getScreenDimDurationLocked(int screenOffTimeout) {
        return Math.min(mMaximumScreenDimDurationConfig,
                (int)(screenOffTimeout * mMaximumScreenDimRatioConfig));
    }

    /**
     * Updates the wakefulness of the device.
     *
     * This is the function that decides whether the device should start dreaming
     * based on the current wake locks and user activity state.  It may modify mDirty
     * if the wakefulness changes.
     *
     * Returns true if the wakefulness changed and we need to restart power state calculation.
     */
    private boolean updateWakefulnessLocked(int dirty) {
        boolean changed = false;
        if ((dirty & (DIRTY_WAKE_LOCKS | DIRTY_USER_ACTIVITY | DIRTY_BOOT_COMPLETED
                | DIRTY_WAKEFULNESS | DIRTY_STAY_ON | DIRTY_PROXIMITY_POSITIVE
                | DIRTY_DOCK_STATE)) != 0) {
            if (mWakefulness == WAKEFULNESS_AWAKE && isItBedTimeYetLocked()) {
                if (DEBUG_SPEW) {
                    Slog.d(TAG, "updateWakefulnessLocked: Bed time...");
                }
                final long time = SystemClock.uptimeMillis();
                if (shouldNapAtBedTimeLocked()) {
                    changed = napNoUpdateLocked(time, Process.SYSTEM_UID);
                } else {
                    changed = goToSleepNoUpdateLocked(time,
                            PowerManager.GO_TO_SLEEP_REASON_TIMEOUT, 0, Process.SYSTEM_UID);
                }
            }
        }
        return changed;
    }

    /**
     * Returns true if the device should automatically nap and start dreaming when the user
     * activity timeout has expired and it's bedtime.
     */
    private boolean shouldNapAtBedTimeLocked() {
        return mDreamsActivateOnSleepSetting
                || (mDreamsActivateOnDockSetting
                        && mDockState != Intent.EXTRA_DOCK_STATE_UNDOCKED);
    }

    /**
     * Returns true if the device should go to sleep now.
     * Also used when exiting a dream to determine whether we should go back
     * to being fully awake or else go to sleep for good.
     */
    private boolean isItBedTimeYetLocked() {
        return mBootCompleted && !isBeingKeptAwakeLocked();
    }

    /**
     * Returns true if the device is being kept awake by a wake lock, user activity
     * or the stay on while powered setting.  We also keep the phone awake when
     * the proximity sensor returns a positive result so that the device does not
     * lock while in a phone call.  This function only controls whether the device
     * will go to sleep or dream which is independent of whether it will be allowed
     * to suspend.
     */
    private boolean isBeingKeptAwakeLocked() {
        return mStayOn
                || mProximityPositive
                || (mWakeLockSummary & WAKE_LOCK_STAY_AWAKE) != 0
                || (mUserActivitySummary & (USER_ACTIVITY_SCREEN_BRIGHT
                        | USER_ACTIVITY_SCREEN_DIM)) != 0
                || mScreenBrightnessBoostInProgress;
    }

    /**
     * Determines whether to post a message to the sandman to update the dream state.
     */
    private void updateDreamLocked(int dirty, boolean displayBecameReady) {
        if ((dirty & (DIRTY_WAKEFULNESS
                | DIRTY_USER_ACTIVITY
                | DIRTY_WAKE_LOCKS
                | DIRTY_BOOT_COMPLETED
                | DIRTY_SETTINGS
                | DIRTY_IS_POWERED
                | DIRTY_STAY_ON
                | DIRTY_PROXIMITY_POSITIVE
                | DIRTY_BATTERY_STATE)) != 0 || displayBecameReady) {
            if (mDisplayReady) {
                scheduleSandmanLocked();
            }
        }
    }

    private void scheduleSandmanLocked() {
        if (!mSandmanScheduled) {
            mSandmanScheduled = true;
            Message msg = mHandler.obtainMessage(MSG_SANDMAN);
            msg.setAsynchronous(true);
            mHandler.sendMessage(msg);
        }
    }

    /**
     * Called when the device enters or exits a dreaming or dozing state.
     *
     * We do this asynchronously because we must call out of the power manager to start
     * the dream and we don't want to hold our lock while doing so.  There is a risk that
     * the device will wake or go to sleep in the meantime so we have to handle that case.
     */
    private void handleSandman() { // runs on handler thread
        // Handle preconditions.
        final boolean startDreaming;
        final int wakefulness;
        synchronized (mLock) {
            mSandmanScheduled = false;
            wakefulness = mWakefulness;
            if (mSandmanSummoned && mDisplayReady) {
                startDreaming = canDreamLocked() || canDozeLocked();
                mSandmanSummoned = false;
            } else {
                startDreaming = false;
            }
        }

        // Start dreaming if needed.
        // We only control the dream on the handler thread, so we don't need to worry about
        // concurrent attempts to start or stop the dream.
        final boolean isDreaming;
        if (mDreamManager != null) {
            // Restart the dream whenever the sandman is summoned.
            if (startDreaming) {
                mDreamManager.stopDream(false /*immediate*/);
                mDreamManager.startDream(wakefulness == WAKEFULNESS_DOZING);
            }
            isDreaming = mDreamManager.isDreaming();
        } else {
            isDreaming = false;
        }

        // Update dream state.
        synchronized (mLock) {
            // Remember the initial battery level when the dream started.
            if (startDreaming && isDreaming) {
                mBatteryLevelWhenDreamStarted = mBatteryLevel;
                if (wakefulness == WAKEFULNESS_DOZING) {
                    Slog.i(TAG, "Dozing...");
                } else {
                    Slog.i(TAG, "Dreaming...");
                }
            }

            // If preconditions changed, wait for the next iteration to determine
            // whether the dream should continue (or be restarted).
            if (mSandmanSummoned || mWakefulness != wakefulness) {
                return; // wait for next cycle
            }

            // Determine whether the dream should continue.
            if (wakefulness == WAKEFULNESS_DREAMING) {
                if (isDreaming && canDreamLocked()) {
                    if (mDreamsBatteryLevelDrainCutoffConfig >= 0
                            && mBatteryLevel < mBatteryLevelWhenDreamStarted
                                    - mDreamsBatteryLevelDrainCutoffConfig
                            && !isBeingKeptAwakeLocked()) {
                        // If the user activity timeout expired and the battery appears
                        // to be draining faster than it is charging then stop dreaming
                        // and go to sleep.
                        Slog.i(TAG, "Stopping dream because the battery appears to "
                                + "be draining faster than it is charging.  "
                                + "Battery level when dream started: "
                                + mBatteryLevelWhenDreamStarted + "%.  "
                                + "Battery level now: " + mBatteryLevel + "%.");
                    } else {
                        return; // continue dreaming
                    }
                }

                // Dream has ended or will be stopped.  Update the power state.
                if (isItBedTimeYetLocked()) {
                    goToSleepNoUpdateLocked(SystemClock.uptimeMillis(),
                            PowerManager.GO_TO_SLEEP_REASON_TIMEOUT, 0, Process.SYSTEM_UID);
                    updatePowerStateLocked();
                } else {
                    wakeUpNoUpdateLocked(SystemClock.uptimeMillis(), Process.SYSTEM_UID);
                    updatePowerStateLocked();
                }
            } else if (wakefulness == WAKEFULNESS_DOZING) {
                if (isDreaming) {
                    return; // continue dozing
                }

                // Doze has ended or will be stopped.  Update the power state.
                reallyGoToSleepNoUpdateLocked(SystemClock.uptimeMillis(), Process.SYSTEM_UID);
                updatePowerStateLocked();
            }
        }

        // Stop dream.
        if (isDreaming) {
            mDreamManager.stopDream(false /*immediate*/);
        }
    }

    /**
     * Returns true if the device is allowed to dream in its current state.
     */
    private boolean canDreamLocked() {
        if (mWakefulness != WAKEFULNESS_DREAMING
                || !mDreamsSupportedConfig
                || !mDreamsEnabledSetting
                || !mDisplayPowerRequest.isBrightOrDim()
                || (mUserActivitySummary & (USER_ACTIVITY_SCREEN_BRIGHT
                        | USER_ACTIVITY_SCREEN_DIM | USER_ACTIVITY_SCREEN_DREAM)) == 0
                || !mBootCompleted) {
            return false;
        }
        if (!isBeingKeptAwakeLocked()) {
            if (!mIsPowered && !mDreamsEnabledOnBatteryConfig) {
                return false;
            }
            if (!mIsPowered
                    && mDreamsBatteryLevelMinimumWhenNotPoweredConfig >= 0
                    && mBatteryLevel < mDreamsBatteryLevelMinimumWhenNotPoweredConfig) {
                return false;
            }
            if (mIsPowered
                    && mDreamsBatteryLevelMinimumWhenPoweredConfig >= 0
                    && mBatteryLevel < mDreamsBatteryLevelMinimumWhenPoweredConfig) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the device is allowed to doze in its current state.
     */
    private boolean canDozeLocked() {
        return mWakefulness == WAKEFULNESS_DOZING;
    }

    /**
     * Updates the display power state asynchronously.
     * When the update is finished, mDisplayReady will be set to true.  The display
     * controller posts a message to tell us when the actual display power state
     * has been updated so we come back here to double-check and finish up.
     *
     * This function recalculates the display power state each time.
     *
     * @return True if the display became ready.
     */
    private boolean updateDisplayPowerStateLocked(int dirty) {
        final boolean oldDisplayReady = mDisplayReady;
        if ((dirty & (DIRTY_WAKE_LOCKS | DIRTY_USER_ACTIVITY | DIRTY_WAKEFULNESS
                | DIRTY_ACTUAL_DISPLAY_POWER_STATE_UPDATED | DIRTY_BOOT_COMPLETED
                | DIRTY_SETTINGS | DIRTY_SCREEN_BRIGHTNESS_BOOST)) != 0) {
            mDisplayPowerRequest.policy = getDesiredScreenPolicyLocked();

            // Determine appropriate screen brightness and auto-brightness adjustments.
            int screenBrightness = mScreenBrightnessSettingDefault;
            float screenAutoBrightnessAdjustment = 0.0f;
            boolean autoBrightness = (mScreenBrightnessModeSetting ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
            if (isValidBrightness(mScreenBrightnessOverrideFromWindowManager)) {
                screenBrightness = mScreenBrightnessOverrideFromWindowManager;
                autoBrightness = false;
            } else if (isValidBrightness(mTemporaryScreenBrightnessSettingOverride)) {
                screenBrightness = mTemporaryScreenBrightnessSettingOverride;
            } else if (isValidBrightness(mScreenBrightnessSetting)) {
                screenBrightness = mScreenBrightnessSetting;
            }
            if (autoBrightness) {
                screenBrightness = mScreenBrightnessSettingDefault;
                if (isValidAutoBrightnessAdjustment(
                        mTemporaryScreenAutoBrightnessAdjustmentSettingOverride)) {
                    screenAutoBrightnessAdjustment =
                            mTemporaryScreenAutoBrightnessAdjustmentSettingOverride;
                } else if (isValidAutoBrightnessAdjustment(
                        mScreenAutoBrightnessAdjustmentSetting)) {
                    screenAutoBrightnessAdjustment = mScreenAutoBrightnessAdjustmentSetting;
                }
            }
            screenBrightness = Math.max(Math.min(screenBrightness,
                    mScreenBrightnessSettingMaximum), mScreenBrightnessSettingMinimum);
            screenAutoBrightnessAdjustment = Math.max(Math.min(
                    screenAutoBrightnessAdjustment, 1.0f), -1.0f);

            // Update display power request.
            mDisplayPowerRequest.screenBrightness = screenBrightness;
            mDisplayPowerRequest.screenAutoBrightnessAdjustment =
                    screenAutoBrightnessAdjustment;
            mDisplayPowerRequest.useAutoBrightness = autoBrightness;
            mDisplayPowerRequest.useProximitySensor = shouldUseProximitySensorLocked();
            mDisplayPowerRequest.lowPowerMode = mLowPowerModeEnabled;
            mDisplayPowerRequest.boostScreenBrightness = mScreenBrightnessBoostInProgress;

            if (mDisplayPowerRequest.policy == DisplayPowerRequest.POLICY_DOZE) {
                mDisplayPowerRequest.dozeScreenState = mDozeScreenStateOverrideFromDreamManager;
                mDisplayPowerRequest.dozeScreenBrightness =
                        mDozeScreenBrightnessOverrideFromDreamManager;
            } else {
                mDisplayPowerRequest.dozeScreenState = Display.STATE_UNKNOWN;
                mDisplayPowerRequest.dozeScreenBrightness = PowerManager.BRIGHTNESS_DEFAULT;
            }

            mDisplayReady = mDisplayManagerInternal.requestPowerState(mDisplayPowerRequest,
                    mRequestWaitForNegativeProximity);
            mRequestWaitForNegativeProximity = false;

            if (DEBUG_SPEW) {
                Slog.d(TAG, "updateDisplayPowerStateLocked: mDisplayReady=" + mDisplayReady
                        + ", policy=" + mDisplayPowerRequest.policy
                        + ", mWakefulness=" + mWakefulness
                        + ", mWakeLockSummary=0x" + Integer.toHexString(mWakeLockSummary)
                        + ", mUserActivitySummary=0x" + Integer.toHexString(mUserActivitySummary)
                        + ", mBootCompleted=" + mBootCompleted
                        + ", mScreenBrightnessBoostInProgress="
                                + mScreenBrightnessBoostInProgress);
            }
        }
        return mDisplayReady && !oldDisplayReady;
    }

    private void updateScreenBrightnessBoostLocked(int dirty) {
        if ((dirty & DIRTY_SCREEN_BRIGHTNESS_BOOST) != 0) {
            if (mScreenBrightnessBoostInProgress) {
                final long now = SystemClock.uptimeMillis();
                mHandler.removeMessages(MSG_SCREEN_BRIGHTNESS_BOOST_TIMEOUT);
                if (mLastScreenBrightnessBoostTime > mLastSleepTime) {
                    final long boostTimeout = mLastScreenBrightnessBoostTime +
                            SCREEN_BRIGHTNESS_BOOST_TIMEOUT;
                    if (boostTimeout > now) {
                        Message msg = mHandler.obtainMessage(MSG_SCREEN_BRIGHTNESS_BOOST_TIMEOUT);
                        msg.setAsynchronous(true);
                        mHandler.sendMessageAtTime(msg, boostTimeout);
                        return;
                    }
                }
                mScreenBrightnessBoostInProgress = false;
                userActivityNoUpdateLocked(now,
                        PowerManager.USER_ACTIVITY_EVENT_OTHER, 0, Process.SYSTEM_UID);
            }
        }
    }

    private static boolean isValidBrightness(int value) {
        return value >= 0 && value <= 255;
    }

    private static boolean isValidAutoBrightnessAdjustment(float value) {
        // Handles NaN by always returning false.
        return value >= -1.0f && value <= 1.0f;
    }

    private int getDesiredScreenPolicyLocked() {
        if (mWakefulness == WAKEFULNESS_ASLEEP) {
            return DisplayPowerRequest.POLICY_OFF;
        }

        if (mWakefulness == WAKEFULNESS_DOZING) {
            if ((mWakeLockSummary & WAKE_LOCK_DOZE) != 0) {
                return DisplayPowerRequest.POLICY_DOZE;
            }
            if (mDozeAfterScreenOffConfig) {
                return DisplayPowerRequest.POLICY_OFF;
            }
            // Fall through and preserve the current screen policy if not configured to
            // doze after screen off.  This causes the screen off transition to be skipped.
        }

        if ((mWakeLockSummary & WAKE_LOCK_SCREEN_BRIGHT) != 0
                || (mUserActivitySummary & USER_ACTIVITY_SCREEN_BRIGHT) != 0
                || !mBootCompleted
                || mScreenBrightnessBoostInProgress) {
            return DisplayPowerRequest.POLICY_BRIGHT;
        }

        return DisplayPowerRequest.POLICY_DIM;
    }

    private final DisplayManagerInternal.DisplayPowerCallbacks mDisplayPowerCallbacks =
            new DisplayManagerInternal.DisplayPowerCallbacks() {
        private int mDisplayState = Display.STATE_UNKNOWN;

        @Override
        public void onStateChanged() {
            synchronized (mLock) {
                mDirty |= DIRTY_ACTUAL_DISPLAY_POWER_STATE_UPDATED;
                updatePowerStateLocked();
            }
        }

        @Override
        public void onProximityPositive() {
            synchronized (mLock) {
                mProximityPositive = true;
                mDirty |= DIRTY_PROXIMITY_POSITIVE;
                updatePowerStateLocked();
            }
        }

        @Override
        public void onProximityNegative() {
            synchronized (mLock) {
                mProximityPositive = false;
                mDirty |= DIRTY_PROXIMITY_POSITIVE;
                userActivityNoUpdateLocked(SystemClock.uptimeMillis(),
                        PowerManager.USER_ACTIVITY_EVENT_OTHER, 0, Process.SYSTEM_UID);
                updatePowerStateLocked();
            }
        }

        @Override
        public void onDisplayStateChange(int state) {
            // This method is only needed to support legacy display blanking behavior
            // where the display's power state is coupled to suspend or to the power HAL.
            // The order of operations matters here.
            synchronized (mLock) {
                if (mDisplayState != state) {
                    mDisplayState = state;
                    if (state == Display.STATE_OFF) {
                        if (!mDecoupleHalInteractiveModeFromDisplayConfig) {
                            setHalInteractiveModeLocked(false);
                        }
                        if (!mDecoupleHalAutoSuspendModeFromDisplayConfig) {
                            setHalAutoSuspendModeLocked(true);
                        }
                    } else {
                        if (!mDecoupleHalAutoSuspendModeFromDisplayConfig) {
                            setHalAutoSuspendModeLocked(false);
                        }
                        if (!mDecoupleHalInteractiveModeFromDisplayConfig) {
                            setHalInteractiveModeLocked(true);
                        }
                    }
                }
            }
        }

        @Override
        public void acquireSuspendBlocker() {
            mDisplaySuspendBlocker.acquire();
        }

        @Override
        public void releaseSuspendBlocker() {
            mDisplaySuspendBlocker.release();
        }

        @Override
        public String toString() {
            synchronized (this) {
                return "state=" + Display.stateToString(mDisplayState);
            }
        }
    };

    private boolean shouldUseProximitySensorLocked() {
        return (mWakeLockSummary & WAKE_LOCK_PROXIMITY_SCREEN_OFF) != 0;
    }

    /**
     * Updates the suspend blocker that keeps the CPU alive.
     *
     * This function must have no other side-effects.
     */
    private void updateSuspendBlockerLocked() {
        final boolean needWakeLockSuspendBlocker = ((mWakeLockSummary & WAKE_LOCK_CPU) != 0);
        final boolean needDisplaySuspendBlocker = needDisplaySuspendBlockerLocked();
        final boolean autoSuspend = !needDisplaySuspendBlocker;
        final boolean interactive = mDisplayPowerRequest.isBrightOrDim();

        // Disable auto-suspend if needed.
        // FIXME We should consider just leaving auto-suspend enabled forever since
        // we already hold the necessary wakelocks.
        if (!autoSuspend && mDecoupleHalAutoSuspendModeFromDisplayConfig) {
            setHalAutoSuspendModeLocked(false);
        }

        // First acquire suspend blockers if needed.
        if (needWakeLockSuspendBlocker && !mHoldingWakeLockSuspendBlocker) {
            mWakeLockSuspendBlocker.acquire();
            mHoldingWakeLockSuspendBlocker = true;
        }
        if (needDisplaySuspendBlocker && !mHoldingDisplaySuspendBlocker) {
            mDisplaySuspendBlocker.acquire();
            mHoldingDisplaySuspendBlocker = true;
        }

        // Inform the power HAL about interactive mode.
        // Although we could set interactive strictly based on the wakefulness
        // as reported by isInteractive(), it is actually more desirable to track
        // the display policy state instead so that the interactive state observed
        // by the HAL more accurately tracks transitions between AWAKE and DOZING.
        // Refer to getDesiredScreenPolicyLocked() for details.
        if (mDecoupleHalInteractiveModeFromDisplayConfig) {
            // When becoming non-interactive, we want to defer sending this signal
            // until the display is actually ready so that all transitions have
            // completed.  This is probably a good sign that things have gotten
            // too tangled over here...
            if (interactive || mDisplayReady) {
                setHalInteractiveModeLocked(interactive);
            }
        }

        // Then release suspend blockers if needed.
        if (!needWakeLockSuspendBlocker && mHoldingWakeLockSuspendBlocker) {
            mWakeLockSuspendBlocker.release();
            mHoldingWakeLockSuspendBlocker = false;
        }
        if (!needDisplaySuspendBlocker && mHoldingDisplaySuspendBlocker) {
            mDisplaySuspendBlocker.release();
            mHoldingDisplaySuspendBlocker = false;
        }

        // Enable auto-suspend if needed.
        if (autoSuspend && mDecoupleHalAutoSuspendModeFromDisplayConfig) {
            setHalAutoSuspendModeLocked(true);
        }
    }

    /**
     * Return true if we must keep a suspend blocker active on behalf of the display.
     * We do so if the screen is on or is in transition between states.
     */
    private boolean needDisplaySuspendBlockerLocked() {
        if (!mDisplayReady) {
            return true;
        }
        if (mDisplayPowerRequest.isBrightOrDim()) {
            // If we asked for the screen to be on but it is off due to the proximity
            // sensor then we may suspend but only if the configuration allows it.
            // On some hardware it may not be safe to suspend because the proximity
            // sensor may not be correctly configured as a wake-up source.
            if (!mDisplayPowerRequest.useProximitySensor || !mProximityPositive
                    || !mSuspendWhenScreenOffDueToProximityConfig) {
                return true;
            }
        }
        if (mScreenBrightnessBoostInProgress) {
            return true;
        }
        // Let the system suspend if the screen is off or dozing.
        return false;
    }

    private void setHalAutoSuspendModeLocked(boolean enable) {
        if (enable != mHalAutoSuspendModeEnabled) {
            if (DEBUG) {
                Slog.d(TAG, "Setting HAL auto-suspend mode to " + enable);
            }
            mHalAutoSuspendModeEnabled = enable;
            Trace.traceBegin(Trace.TRACE_TAG_POWER, "setHalAutoSuspend(" + enable + ")");
            try {
                nativeSetAutoSuspend(enable);
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_POWER);
            }
        }
    }

    private void setHalInteractiveModeLocked(boolean enable) {
        if (enable != mHalInteractiveModeEnabled) {
            if (DEBUG) {
                Slog.d(TAG, "Setting HAL interactive mode to " + enable);
            }
            mHalInteractiveModeEnabled = enable;
            Trace.traceBegin(Trace.TRACE_TAG_POWER, "setHalInteractive(" + enable + ")");
            try {
                nativeSetInteractive(enable);
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_POWER);
            }
        }
    }

    private boolean isInteractiveInternal() {
        synchronized (mLock) {
            return PowerManagerInternal.isInteractive(mWakefulness);
        }
    }

    private boolean isLowPowerModeInternal() {
        synchronized (mLock) {
            return mLowPowerModeEnabled;
        }
    }

    private boolean setLowPowerModeInternal(boolean mode) {
        synchronized (mLock) {
            if (DEBUG) Slog.d(TAG, "setLowPowerModeInternal " + mode + " mIsPowered=" + mIsPowered);
            if (mIsPowered) {
                return false;
            }
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.LOW_POWER_MODE, mode ? 1 : 0);
            mLowPowerModeSetting = mode;

            if (mAutoLowPowerModeConfigured && mBatteryLevelLow) {
                if (mode && mAutoLowPowerModeSnoozing) {
                    if (DEBUG_SPEW) {
                        Slog.d(TAG, "setLowPowerModeInternal: clearing low power mode snooze");
                    }
                    mAutoLowPowerModeSnoozing = false;
                } else if (!mode && !mAutoLowPowerModeSnoozing) {
                    if (DEBUG_SPEW) {
                        Slog.d(TAG, "setLowPowerModeInternal: snoozing low power mode");
                    }
                    mAutoLowPowerModeSnoozing = true;
                }
            }

            updateLowPowerModeLocked();
            return true;
        }
    }

    private void handleBatteryStateChangedLocked() {
        mDirty |= DIRTY_BATTERY_STATE;
        updatePowerStateLocked();
    }

    private void shutdownOrRebootInternal(final boolean shutdown, final boolean confirm,
            final String reason, boolean wait) {
        if (mHandler == null || !mSystemReady) {
            throw new IllegalStateException("Too early to call shutdown() or reboot()");
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    if (shutdown) {
                        ShutdownThread.shutdown(mContext, confirm);
                    } else {
                        ShutdownThread.reboot(mContext, reason, confirm);
                    }
                }
            }
        };

        // ShutdownThread must run on a looper capable of displaying the UI.
        Message msg = Message.obtain(mHandler, runnable);
        msg.setAsynchronous(true);
        mHandler.sendMessage(msg);

        // PowerManager.reboot() is documented not to return so just wait for the inevitable.
        if (wait) {
            synchronized (runnable) {
                while (true) {
                    try {
                        runnable.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    private void crashInternal(final String message) {
        Thread t = new Thread("PowerManagerService.crash()") {
            @Override
            public void run() {
                throw new RuntimeException(message);
            }
        };
        try {
            t.start();
            t.join();
        } catch (InterruptedException e) {
            Slog.wtf(TAG, e);
        }
    }

    private void setStayOnSettingInternal(int val) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN, val);
    }

    private void setMaximumScreenOffTimeoutFromDeviceAdminInternal(int timeMs) {
        synchronized (mLock) {
            mMaximumScreenOffTimeoutFromDeviceAdmin = timeMs;
            mDirty |= DIRTY_SETTINGS;
            updatePowerStateLocked();
        }
    }

    private boolean isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() {
        return mMaximumScreenOffTimeoutFromDeviceAdmin >= 0
                && mMaximumScreenOffTimeoutFromDeviceAdmin < Integer.MAX_VALUE;
    }

    private void setAttentionLightInternal(boolean on, int color) {
        Light light;
        synchronized (mLock) {
            if (!mSystemReady) {
                return;
            }
            light = mAttentionLight;
        }

        // Control light outside of lock.
        light.setFlashing(color, Light.LIGHT_FLASH_HARDWARE, (on ? 3 : 0), 0);
    }

    private void boostScreenBrightnessInternal(long eventTime, int uid) {
        synchronized (mLock) {
            if (!mSystemReady || mWakefulness == WAKEFULNESS_ASLEEP
                    || eventTime < mLastScreenBrightnessBoostTime) {
                return;
            }

            Slog.i(TAG, "Brightness boost activated (uid " + uid +")...");
            mLastScreenBrightnessBoostTime = eventTime;
            mScreenBrightnessBoostInProgress = true;
            mDirty |= DIRTY_SCREEN_BRIGHTNESS_BOOST;

            userActivityNoUpdateLocked(eventTime,
                    PowerManager.USER_ACTIVITY_EVENT_OTHER, 0, uid);
            updatePowerStateLocked();
        }
    }

    /**
     * Called when a screen brightness boost timeout has occurred.
     *
     * This function must have no other side-effects besides setting the dirty
     * bit and calling update power state.
     */
    private void handleScreenBrightnessBoostTimeout() { // runs on handler thread
        synchronized (mLock) {
            if (DEBUG_SPEW) {
                Slog.d(TAG, "handleScreenBrightnessBoostTimeout");
            }

            mDirty |= DIRTY_SCREEN_BRIGHTNESS_BOOST;
            updatePowerStateLocked();
        }
    }

    private void setScreenBrightnessOverrideFromWindowManagerInternal(int brightness) {
        synchronized (mLock) {
            if (mScreenBrightnessOverrideFromWindowManager != brightness) {
                mScreenBrightnessOverrideFromWindowManager = brightness;
                mDirty |= DIRTY_SETTINGS;
                updatePowerStateLocked();
            }
        }
    }

    private void setUserActivityTimeoutOverrideFromWindowManagerInternal(long timeoutMillis) {
        synchronized (mLock) {
            if (mUserActivityTimeoutOverrideFromWindowManager != timeoutMillis) {
                mUserActivityTimeoutOverrideFromWindowManager = timeoutMillis;
                mDirty |= DIRTY_SETTINGS;
                updatePowerStateLocked();
            }
        }
    }

    private void setTemporaryScreenBrightnessSettingOverrideInternal(int brightness) {
        synchronized (mLock) {
            if (mTemporaryScreenBrightnessSettingOverride != brightness) {
                mTemporaryScreenBrightnessSettingOverride = brightness;
                mDirty |= DIRTY_SETTINGS;
                updatePowerStateLocked();
            }
        }
    }

    private void setTemporaryScreenAutoBrightnessAdjustmentSettingOverrideInternal(float adj) {
        synchronized (mLock) {
            // Note: This condition handles NaN because NaN is not equal to any other
            // value, including itself.
            if (mTemporaryScreenAutoBrightnessAdjustmentSettingOverride != adj) {
                mTemporaryScreenAutoBrightnessAdjustmentSettingOverride = adj;
                mDirty |= DIRTY_SETTINGS;
                updatePowerStateLocked();
            }
        }
    }

    private void setDozeOverrideFromDreamManagerInternal(
            int screenState, int screenBrightness) {
        synchronized (mLock) {
            if (mDozeScreenStateOverrideFromDreamManager != screenState
                    || mDozeScreenBrightnessOverrideFromDreamManager != screenBrightness) {
                mDozeScreenStateOverrideFromDreamManager = screenState;
                mDozeScreenBrightnessOverrideFromDreamManager = screenBrightness;
                mDirty |= DIRTY_SETTINGS;
                updatePowerStateLocked();
            }
        }
    }

    private void powerHintInternal(int hintId, int data) {
        nativeSendPowerHint(hintId, data);
    }

    /**
     * Low-level function turn the device off immediately, without trying
     * to be clean.  Most people should use {@link ShutdownThread} for a clean shutdown.
     */
    public static void lowLevelShutdown() {
        SystemProperties.set("sys.powerctl", "shutdown");
    }

    /**
     * Low-level function to reboot the device. On success, this
     * function doesn't return. If more than 20 seconds passes from
     * the time a reboot is requested (900 seconds for reboot to
     * recovery), this method returns.
     *
     * @param reason code to pass to the kernel (e.g. "recovery"), or null.
     */
    public static void lowLevelReboot(String reason) {
        if (reason == null) {
            reason = "";
        }
        long duration;
        if (reason.equals(PowerManager.REBOOT_RECOVERY)) {
            // If we are rebooting to go into recovery, instead of
            // setting sys.powerctl directly we'll start the
            // pre-recovery service which will do some preparation for
            // recovery and then reboot for us.
            //
            // This preparation can take more than 20 seconds if
            // there's a very large update package, so lengthen the
            // timeout.  We have seen 750MB packages take 3-4 minutes.
            // Bump up the limit again to 900s for really large packages.
            // Bug: 23629892.
            SystemProperties.set("ctl.start", "pre-recovery");
            duration = 900 * 1000L;
        } else {
            SystemProperties.set("sys.powerctl", "reboot," + reason);
            duration = 20 * 1000L;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override // Watchdog.Monitor implementation
    public void monitor() {
        // Grab and release lock for watchdog monitor to detect deadlocks.
        synchronized (mLock) {
        }
    }

    private void dumpInternal(PrintWriter pw) {
        pw.println("POWER MANAGER (dumpsys power)\n");

        final WirelessChargerDetector wcd;
        synchronized (mLock) {
            pw.println("Power Manager State:");
            pw.println("  mDirty=0x" + Integer.toHexString(mDirty));
            pw.println("  mWakefulness=" + PowerManagerInternal.wakefulnessToString(mWakefulness));
            pw.println("  mWakefulnessChanging=" + mWakefulnessChanging);
            pw.println("  mIsPowered=" + mIsPowered);
            pw.println("  mPlugType=" + mPlugType);
            pw.println("  mBatteryLevel=" + mBatteryLevel);
            pw.println("  mBatteryLevelWhenDreamStarted=" + mBatteryLevelWhenDreamStarted);
            pw.println("  mDockState=" + mDockState);
            pw.println("  mStayOn=" + mStayOn);
            pw.println("  mProximityPositive=" + mProximityPositive);
            pw.println("  mBootCompleted=" + mBootCompleted);
            pw.println("  mSystemReady=" + mSystemReady);
            pw.println("  mHalAutoSuspendModeEnabled=" + mHalAutoSuspendModeEnabled);
            pw.println("  mHalInteractiveModeEnabled=" + mHalInteractiveModeEnabled);
            pw.println("  mWakeLockSummary=0x" + Integer.toHexString(mWakeLockSummary));
            pw.println("  mUserActivitySummary=0x" + Integer.toHexString(mUserActivitySummary));
            pw.println("  mRequestWaitForNegativeProximity=" + mRequestWaitForNegativeProximity);
            pw.println("  mSandmanScheduled=" + mSandmanScheduled);
            pw.println("  mSandmanSummoned=" + mSandmanSummoned);
            pw.println("  mLowPowerModeEnabled=" + mLowPowerModeEnabled);
            pw.println("  mBatteryLevelLow=" + mBatteryLevelLow);
            pw.println("  mLastWakeTime=" + TimeUtils.formatUptime(mLastWakeTime));
            pw.println("  mLastSleepTime=" + TimeUtils.formatUptime(mLastSleepTime));
            pw.println("  mLastUserActivityTime=" + TimeUtils.formatUptime(mLastUserActivityTime));
            pw.println("  mLastUserActivityTimeNoChangeLights="
                    + TimeUtils.formatUptime(mLastUserActivityTimeNoChangeLights));
            pw.println("  mLastInteractivePowerHintTime="
                    + TimeUtils.formatUptime(mLastInteractivePowerHintTime));
            pw.println("  mLastScreenBrightnessBoostTime="
                    + TimeUtils.formatUptime(mLastScreenBrightnessBoostTime));
            pw.println("  mScreenBrightnessBoostInProgress="
                    + mScreenBrightnessBoostInProgress);
            pw.println("  mDisplayReady=" + mDisplayReady);
            pw.println("  mHoldingWakeLockSuspendBlocker=" + mHoldingWakeLockSuspendBlocker);
            pw.println("  mHoldingDisplaySuspendBlocker=" + mHoldingDisplaySuspendBlocker);

            pw.println();
            pw.println("Settings and Configuration:");
            pw.println("  mDecoupleHalAutoSuspendModeFromDisplayConfig="
                    + mDecoupleHalAutoSuspendModeFromDisplayConfig);
            pw.println("  mDecoupleHalInteractiveModeFromDisplayConfig="
                    + mDecoupleHalInteractiveModeFromDisplayConfig);
            pw.println("  mWakeUpWhenPluggedOrUnpluggedConfig="
                    + mWakeUpWhenPluggedOrUnpluggedConfig);
            pw.println("  mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig="
                    + mWakeUpWhenPluggedOrUnpluggedInTheaterModeConfig);
            pw.println("  mTheaterModeEnabled="
                    + mTheaterModeEnabled);
            pw.println("  mSuspendWhenScreenOffDueToProximityConfig="
                    + mSuspendWhenScreenOffDueToProximityConfig);
            pw.println("  mDreamsSupportedConfig=" + mDreamsSupportedConfig);
            pw.println("  mDreamsEnabledByDefaultConfig=" + mDreamsEnabledByDefaultConfig);
            pw.println("  mDreamsActivatedOnSleepByDefaultConfig="
                    + mDreamsActivatedOnSleepByDefaultConfig);
            pw.println("  mDreamsActivatedOnDockByDefaultConfig="
                    + mDreamsActivatedOnDockByDefaultConfig);
            pw.println("  mDreamsEnabledOnBatteryConfig="
                    + mDreamsEnabledOnBatteryConfig);
            pw.println("  mDreamsBatteryLevelMinimumWhenPoweredConfig="
                    + mDreamsBatteryLevelMinimumWhenPoweredConfig);
            pw.println("  mDreamsBatteryLevelMinimumWhenNotPoweredConfig="
                    + mDreamsBatteryLevelMinimumWhenNotPoweredConfig);
            pw.println("  mDreamsBatteryLevelDrainCutoffConfig="
                    + mDreamsBatteryLevelDrainCutoffConfig);
            pw.println("  mDreamsEnabledSetting=" + mDreamsEnabledSetting);
            pw.println("  mDreamsActivateOnSleepSetting=" + mDreamsActivateOnSleepSetting);
            pw.println("  mDreamsActivateOnDockSetting=" + mDreamsActivateOnDockSetting);
            pw.println("  mDozeAfterScreenOffConfig=" + mDozeAfterScreenOffConfig);
            pw.println("  mLowPowerModeSetting=" + mLowPowerModeSetting);
            pw.println("  mAutoLowPowerModeConfigured=" + mAutoLowPowerModeConfigured);
            pw.println("  mAutoLowPowerModeSnoozing=" + mAutoLowPowerModeSnoozing);
            pw.println("  mMinimumScreenOffTimeoutConfig=" + mMinimumScreenOffTimeoutConfig);
            pw.println("  mMaximumScreenDimDurationConfig=" + mMaximumScreenDimDurationConfig);
            pw.println("  mMaximumScreenDimRatioConfig=" + mMaximumScreenDimRatioConfig);
            pw.println("  mScreenOffTimeoutSetting=" + mScreenOffTimeoutSetting);
            pw.println("  mSleepTimeoutSetting=" + mSleepTimeoutSetting);
            pw.println("  mMaximumScreenOffTimeoutFromDeviceAdmin="
                    + mMaximumScreenOffTimeoutFromDeviceAdmin + " (enforced="
                    + isMaximumScreenOffTimeoutFromDeviceAdminEnforcedLocked() + ")");
            pw.println("  mStayOnWhilePluggedInSetting=" + mStayOnWhilePluggedInSetting);
            pw.println("  mScreenBrightnessSetting=" + mScreenBrightnessSetting);
            pw.println("  mScreenAutoBrightnessAdjustmentSetting="
                    + mScreenAutoBrightnessAdjustmentSetting);
            pw.println("  mScreenBrightnessModeSetting=" + mScreenBrightnessModeSetting);
            pw.println("  mScreenBrightnessOverrideFromWindowManager="
                    + mScreenBrightnessOverrideFromWindowManager);
            pw.println("  mUserActivityTimeoutOverrideFromWindowManager="
                    + mUserActivityTimeoutOverrideFromWindowManager);
            pw.println("  mTemporaryScreenBrightnessSettingOverride="
                    + mTemporaryScreenBrightnessSettingOverride);
            pw.println("  mTemporaryScreenAutoBrightnessAdjustmentSettingOverride="
                    + mTemporaryScreenAutoBrightnessAdjustmentSettingOverride);
            pw.println("  mDozeScreenStateOverrideFromDreamManager="
                    + mDozeScreenStateOverrideFromDreamManager);
            pw.println("  mDozeScreenBrightnessOverrideFromDreamManager="
                    + mDozeScreenBrightnessOverrideFromDreamManager);
            pw.println("  mScreenBrightnessSettingMinimum=" + mScreenBrightnessSettingMinimum);
            pw.println("  mScreenBrightnessSettingMaximum=" + mScreenBrightnessSettingMaximum);
            pw.println("  mScreenBrightnessSettingDefault=" + mScreenBrightnessSettingDefault);

            final int sleepTimeout = getSleepTimeoutLocked();
            final int screenOffTimeout = getScreenOffTimeoutLocked(sleepTimeout);
            final int screenDimDuration = getScreenDimDurationLocked(screenOffTimeout);
            pw.println();
            pw.println("Sleep timeout: " + sleepTimeout + " ms");
            pw.println("Screen off timeout: " + screenOffTimeout + " ms");
            pw.println("Screen dim duration: " + screenDimDuration + " ms");

            pw.println();
            pw.println("Wake Locks: size=" + mWakeLocks.size());
            for (WakeLock wl : mWakeLocks) {
                pw.println("  " + wl);
            }

            pw.println();
            pw.println("Suspend Blockers: size=" + mSuspendBlockers.size());
            for (SuspendBlocker sb : mSuspendBlockers) {
                pw.println("  " + sb);
            }

            pw.println();
            pw.println("Display Power: " + mDisplayPowerCallbacks);

            wcd = mWirelessChargerDetector;
        }

        if (wcd != null) {
            wcd.dump(pw);
        }
    }

    private SuspendBlocker createSuspendBlockerLocked(String name) {
        SuspendBlocker suspendBlocker = new SuspendBlockerImpl(name);
        mSuspendBlockers.add(suspendBlocker);
        return suspendBlocker;
    }

    private static WorkSource copyWorkSource(WorkSource workSource) {
        return workSource != null ? new WorkSource(workSource) : null;
    }

    private final class BatteryReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                handleBatteryStateChangedLocked();
            }
        }
    }

    private final class DreamReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                scheduleSandmanLocked();
            }
        }
    }

    private final class UserSwitchedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                handleSettingsChangedLocked();
            }
        }
    }

    private final class DockReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                int dockState = intent.getIntExtra(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED);
                if (mDockState != dockState) {
                    mDockState = dockState;
                    mDirty |= DIRTY_DOCK_STATE;
                    updatePowerStateLocked();
                }
            }
        }
    }

    private final class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            synchronized (mLock) {
                handleSettingsChangedLocked();
            }
        }
    }

    /**
     * Handler for asynchronous operations performed by the power manager.
     */
    private final class PowerManagerHandler extends Handler {
        public PowerManagerHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_USER_ACTIVITY_TIMEOUT:
                    handleUserActivityTimeout();
                    break;
                case MSG_SANDMAN:
                    handleSandman();
                    break;
                case MSG_SCREEN_BRIGHTNESS_BOOST_TIMEOUT:
                    handleScreenBrightnessBoostTimeout();
                    break;
                case MSG_WAKE_UP:
                    cleanupProximity();
                    ((Runnable) msg.obj).run();
                    break;
            }
        }
    }

    /**
     * Represents a wake lock that has been acquired by an application.
     */
    private final class WakeLock implements IBinder.DeathRecipient {
        public final IBinder mLock;
        public int mFlags;
        public String mTag;
        public final String mPackageName;
        public WorkSource mWorkSource;
        public String mHistoryTag;
        public final int mOwnerUid;
        public final int mOwnerPid;
        public boolean mNotifiedAcquired;

        public WakeLock(IBinder lock, int flags, String tag, String packageName,
                WorkSource workSource, String historyTag, int ownerUid, int ownerPid) {
            mLock = lock;
            mFlags = flags;
            mTag = tag;
            mPackageName = packageName;
            mWorkSource = copyWorkSource(workSource);
            mHistoryTag = historyTag;
            mOwnerUid = ownerUid;
            mOwnerPid = ownerPid;
        }

        @Override
        public void binderDied() {
            PowerManagerService.this.handleWakeLockDeath(this);
        }

        public boolean hasSameProperties(int flags, String tag, WorkSource workSource,
                int ownerUid, int ownerPid) {
            return mFlags == flags
                    && mTag.equals(tag)
                    && hasSameWorkSource(workSource)
                    && mOwnerUid == ownerUid
                    && mOwnerPid == ownerPid;
        }

        public void updateProperties(int flags, String tag, String packageName,
                WorkSource workSource, String historyTag, int ownerUid, int ownerPid) {
            if (!mPackageName.equals(packageName)) {
                throw new IllegalStateException("Existing wake lock package name changed: "
                        + mPackageName + " to " + packageName);
            }
            if (mOwnerUid != ownerUid) {
                throw new IllegalStateException("Existing wake lock uid changed: "
                        + mOwnerUid + " to " + ownerUid);
            }
            if (mOwnerPid != ownerPid) {
                throw new IllegalStateException("Existing wake lock pid changed: "
                        + mOwnerPid + " to " + ownerPid);
            }
            mFlags = flags;
            mTag = tag;
            updateWorkSource(workSource);
            mHistoryTag = historyTag;
        }

        public boolean hasSameWorkSource(WorkSource workSource) {
            return Objects.equal(mWorkSource, workSource);
        }

        public void updateWorkSource(WorkSource workSource) {
            mWorkSource = copyWorkSource(workSource);
        }

        @Override
        public String toString() {
            return getLockLevelString()
                    + " '" + mTag + "'" + getLockFlagsString()
                    + " (uid=" + mOwnerUid + ", pid=" + mOwnerPid + ", ws=" + mWorkSource + ")";
        }

        @SuppressWarnings("deprecation")
        private String getLockLevelString() {
            switch (mFlags & PowerManager.WAKE_LOCK_LEVEL_MASK) {
                case PowerManager.FULL_WAKE_LOCK:
                    return "FULL_WAKE_LOCK                ";
                case PowerManager.SCREEN_BRIGHT_WAKE_LOCK:
                    return "SCREEN_BRIGHT_WAKE_LOCK       ";
                case PowerManager.SCREEN_DIM_WAKE_LOCK:
                    return "SCREEN_DIM_WAKE_LOCK          ";
                case PowerManager.PARTIAL_WAKE_LOCK:
                    return "PARTIAL_WAKE_LOCK             ";
                case PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK:
                    return "PROXIMITY_SCREEN_OFF_WAKE_LOCK";
                case PowerManager.DOZE_WAKE_LOCK:
                    return "DOZE_WAKE_LOCK                ";
                default:
                    return "???                           ";
            }
        }

        private String getLockFlagsString() {
            String result = "";
            if ((mFlags & PowerManager.ACQUIRE_CAUSES_WAKEUP) != 0) {
                result += " ACQUIRE_CAUSES_WAKEUP";
            }
            if ((mFlags & PowerManager.ON_AFTER_RELEASE) != 0) {
                result += " ON_AFTER_RELEASE";
            }
            return result;
        }
    }

    private final class SuspendBlockerImpl implements SuspendBlocker {
        private final String mName;
        private final String mTraceName;
        private int mReferenceCount;

        public SuspendBlockerImpl(String name) {
            mName = name;
            mTraceName = "SuspendBlocker (" + name + ")";
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (mReferenceCount != 0) {
                    Slog.wtf(TAG, "Suspend blocker \"" + mName
                            + "\" was finalized without being released!");
                    mReferenceCount = 0;
                    nativeReleaseSuspendBlocker(mName);
                    Trace.asyncTraceEnd(Trace.TRACE_TAG_POWER, mTraceName, 0);
                }
            } finally {
                super.finalize();
            }
        }

        @Override
        public void acquire() {
            synchronized (this) {
                mReferenceCount += 1;
                if (mReferenceCount == 1) {
                    if (DEBUG_SPEW) {
                        Slog.d(TAG, "Acquiring suspend blocker \"" + mName + "\".");
                    }
                    Trace.asyncTraceBegin(Trace.TRACE_TAG_POWER, mTraceName, 0);
                    nativeAcquireSuspendBlocker(mName);
                }
            }
        }

        @Override
        public void release() {
            synchronized (this) {
                mReferenceCount -= 1;
                if (mReferenceCount == 0) {
                    if (DEBUG_SPEW) {
                        Slog.d(TAG, "Releasing suspend blocker \"" + mName + "\".");
                    }
                    nativeReleaseSuspendBlocker(mName);
                    Trace.asyncTraceEnd(Trace.TRACE_TAG_POWER, mTraceName, 0);
                } else if (mReferenceCount < 0) {
                    Slog.wtf(TAG, "Suspend blocker \"" + mName
                            + "\" was released without being acquired!", new Throwable());
                    mReferenceCount = 0;
                }
            }
        }

        @Override
        public String toString() {
            synchronized (this) {
                return mName + ": ref count=" + mReferenceCount;
            }
        }
    }

    private void cleanupProximity() {
        synchronized (mProximityWakeLock) {
            cleanupProximityLocked();
        }
    }

    private void cleanupProximityLocked() {
        if (mProximityWakeLock.isHeld()) {
            mProximityWakeLock.release();
        }
        if (mProximityListener != null) {
            mSensorManager.unregisterListener(mProximityListener);
            mProximityListener = null;
        }
    }

    private final class BinderService extends IPowerManager.Stub {
        @Override // Binder call
        public void acquireWakeLockWithUid(IBinder lock, int flags, String tag,
                String packageName, int uid) {
            if (uid < 0) {
                uid = Binder.getCallingUid();
            }
            acquireWakeLock(lock, flags, tag, packageName, new WorkSource(uid), null);
        }

        @Override // Binder call
        public void powerHint(int hintId, int data) {
            if (!mSystemReady) {
                // Service not ready yet, so who the heck cares about power hints, bah.
                return;
            }
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER, null);
            powerHintInternal(hintId, data);
        }

        @Override // Binder call
        public void acquireWakeLock(IBinder lock, int flags, String tag, String packageName,
                WorkSource ws, String historyTag) {
            if (lock == null) {
                throw new IllegalArgumentException("lock must not be null");
            }
            if (packageName == null) {
                throw new IllegalArgumentException("packageName must not be null");
            }
            PowerManager.validateWakeLockParameters(flags, tag);

            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.WAKE_LOCK, null);
            if ((flags & PowerManager.DOZE_WAKE_LOCK) != 0) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.DEVICE_POWER, null);
            }
            if (ws != null && ws.size() != 0) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.UPDATE_DEVICE_STATS, null);
            } else {
                ws = null;
            }

            final int uid = Binder.getCallingUid();
            final int pid = Binder.getCallingPid();

            try {
                if (mAppOps != null &&
                        mAppOps.checkOperation(AppOpsManager.OP_WAKE_LOCK, uid, packageName)
                        != AppOpsManager.MODE_ALLOWED) {
                    Slog.d(TAG, "acquireWakeLock: ignoring request from " + packageName);
                    // For (ignore) accounting purposes
                    mAppOps.noteOperation(AppOpsManager.OP_WAKE_LOCK, uid, packageName);
                    // silent return
                    return;
                }
            } catch (RemoteException e) {
            }

            final long ident = Binder.clearCallingIdentity();
            try {
                acquireWakeLockInternal(lock, flags, tag, packageName, ws, historyTag, uid, pid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void releaseWakeLock(IBinder lock, int flags) {
            if (lock == null) {
                throw new IllegalArgumentException("lock must not be null");
            }

            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.WAKE_LOCK, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                releaseWakeLockInternal(lock, flags);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void updateWakeLockUids(IBinder lock, int[] uids) {
            WorkSource ws = null;

            if (uids != null) {
                ws = new WorkSource();
                // XXX should WorkSource have a way to set uids as an int[] instead of adding them
                // one at a time?
                for (int i = 0; i < uids.length; i++) {
                    ws.add(uids[i]);
                }
            }
            updateWakeLockWorkSource(lock, ws, null);
        }

        @Override // Binder call
        public void updateWakeLockWorkSource(IBinder lock, WorkSource ws, String historyTag) {
            if (lock == null) {
                throw new IllegalArgumentException("lock must not be null");
            }

            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.WAKE_LOCK, null);
            if (ws != null && ws.size() != 0) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.UPDATE_DEVICE_STATS, null);
            } else {
                ws = null;
            }

            final int callingUid = Binder.getCallingUid();
            final long ident = Binder.clearCallingIdentity();
            try {
                updateWakeLockWorkSourceInternal(lock, ws, historyTag, callingUid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public boolean isWakeLockLevelSupported(int level) {
            final long ident = Binder.clearCallingIdentity();
            try {
                return isWakeLockLevelSupportedInternal(level);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void userActivity(long eventTime, int event, int flags) {
            final long now = SystemClock.uptimeMillis();
            if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER)
                    != PackageManager.PERMISSION_GRANTED
                    && mContext.checkCallingOrSelfPermission(
                            android.Manifest.permission.USER_ACTIVITY)
                            != PackageManager.PERMISSION_GRANTED) {
                // Once upon a time applications could call userActivity().
                // Now we require the DEVICE_POWER permission.  Log a warning and ignore the
                // request instead of throwing a SecurityException so we don't break old apps.
                synchronized (mLock) {
                    if (now >= mLastWarningAboutUserActivityPermission + (5 * 60 * 1000)) {
                        mLastWarningAboutUserActivityPermission = now;
                        Slog.w(TAG, "Ignoring call to PowerManager.userActivity() because the "
                                + "caller does not have DEVICE_POWER or USER_ACTIVITY "
                                + "permission.  Please fix your app!  "
                                + " pid=" + Binder.getCallingPid()
                                + " uid=" + Binder.getCallingUid());
                    }
                }
                return;
            }

            if (eventTime > SystemClock.uptimeMillis()) {
                throw new IllegalArgumentException("event time must not be in the future");
            }

            final int uid = Binder.getCallingUid();
            final long ident = Binder.clearCallingIdentity();
            try {
                userActivityInternal(eventTime, event, flags, uid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void setKeyboardVisibility(boolean visible) {
            synchronized (mLock) {
                if (DEBUG_SPEW) {
                    Slog.d(TAG, "setKeyboardVisibility: " + visible);
                }
                if (mKeyboardVisible != visible) {
                    mKeyboardVisible = visible;
                    if (!visible) {
                        // If hiding keyboard, turn off leds
                        setKeyboardLight(false, 1);
                        setKeyboardLight(false, 2);
                    }
                    synchronized (mLock) {
                        mDirty |= DIRTY_USER_ACTIVITY;
                        updatePowerStateLocked();
                    }
                }
            }
        }

        @Override // Binder call
        public void setKeyboardLight(boolean on, int key) {
            if (key == 1) {
                if (on)
                    mCapsLight.setColor(0x00ffffff);
                else
                    mCapsLight.turnOff();
            } else if (key == 2) {
                if (on)
                    mFnLight.setColor(0x00ffffff);
                else
                    mFnLight.turnOff();
            }
        }

        /**
         * @hide
         */
        private void wakeUp(final long eventTime, boolean checkProximity) {
            if (eventTime > SystemClock.uptimeMillis()) {
                throw new IllegalArgumentException("event time must not be in the future");
            }

            // check wakeup caller under QuickBoot mode
            if (SystemProperties.getInt("sys.quickboot.enable", 0) == 1) {
                if (!isQuickBootCall()) {
                    Slog.d(TAG, "ignore wakeup request under QuickBoot");
                    return;
                }
            }

            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final int uid = Binder.getCallingUid();
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    final long ident = Binder.clearCallingIdentity();
                    try {
                        wakeUpInternal(eventTime, uid);
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                }
            };
            if (checkProximity) {
                runWithProximityCheck(r);
            } else {
                r.run();
            }
        }

        private void runWithProximityCheck(Runnable r) {
            if (mHandler.hasMessages(MSG_WAKE_UP)) {
                // There is already a message queued;
                return;
            }

            TelephonyManager tm = (TelephonyManager)mContext.getSystemService(
                    Context.TELEPHONY_SERVICE);
            boolean hasIncomingCall = tm.getCallState() == TelephonyManager.CALL_STATE_RINGING;

            if (mProximityWakeSupported && mProximityWakeEnabled && mProximitySensor != null
                    && !hasIncomingCall) {
                Message msg = mHandler.obtainMessage(MSG_WAKE_UP);
                msg.obj = r;
                mHandler.sendMessageDelayed(msg, mProximityTimeOut);
                runPostProximityCheck(r);
            } else {
                r.run();
            }
        }

        private void runPostProximityCheck(final Runnable r) {
            if (mSensorManager == null) {
                r.run();
                return;
            }
            synchronized (mProximityWakeLock) {
                mProximityWakeLock.acquire();
                mProximityListener = new SensorEventListener() {
                    @Override
                    public void onSensorChanged(SensorEvent event) {
                        cleanupProximityLocked();
                        if (!mHandler.hasMessages(MSG_WAKE_UP)) {
                            Slog.w(TAG, "The proximity sensor took too long, wake event already triggered!");
                            return;
                        }
                        mHandler.removeMessages(MSG_WAKE_UP);
                        float distance = event.values[0];
                        if (distance >= PROXIMITY_NEAR_THRESHOLD ||
                                distance >= mProximitySensor.getMaximumRange()) {
                            r.run();
                        }
                    }

                    @Override
                    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
                };
                mSensorManager.registerListener(mProximityListener,
                       mProximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
        }

        @Override // Binder call
        public void wakeUpWithProximityCheck(long eventTime) {
            wakeUp(eventTime, true);
        }

        @Override // Binder call
        public void wakeUp(long eventTime) {
            wakeUp(eventTime, false);
        }

        @Override // Binder call
        public void goToSleep(long eventTime, int reason, int flags) {
            if (eventTime > SystemClock.uptimeMillis()) {
                throw new IllegalArgumentException("event time must not be in the future");
            }

            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final int uid = Binder.getCallingUid();
            final long ident = Binder.clearCallingIdentity();
            try {
                goToSleepInternal(eventTime, reason, flags, uid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void nap(long eventTime) {
            if (eventTime > SystemClock.uptimeMillis()) {
                throw new IllegalArgumentException("event time must not be in the future");
            }

            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final int uid = Binder.getCallingUid();
            final long ident = Binder.clearCallingIdentity();
            try {
                napInternal(eventTime, uid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public boolean isInteractive() {
            final long ident = Binder.clearCallingIdentity();
            try {
                return isInteractiveInternal();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public boolean isPowerSaveMode() {
            final long ident = Binder.clearCallingIdentity();
            try {
                return isLowPowerModeInternal();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public boolean setPowerSaveMode(boolean mode) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);
            final long ident = Binder.clearCallingIdentity();
            try {
                boolean changed = setLowPowerModeInternal(mode);
                if (changed) {
                    mPerformanceManager.setPowerProfile(mLowPowerModeEnabled ?
                            PowerManager.PROFILE_POWER_SAVE : PowerManager.PROFILE_BALANCED);
                }
                return changed;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public boolean setPowerProfile(String profile) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER, null);
            final long ident = Binder.clearCallingIdentity();
            try {
                setLowPowerModeInternal(PowerManager.PROFILE_POWER_SAVE.equals(profile));
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
            return mPerformanceManager.setPowerProfile(profile);
        }

        @Override
        public String getPowerProfile() {
            return mPerformanceManager.getPowerProfile();
        }

        /**
         * Boost the CPU
         * @param duration Duration to boost the CPU for, in milliseconds.
         * @hide
         */
        @Override
        public void cpuBoost(int duration) {
            if (duration > 0 && duration <= MAX_CPU_BOOST_TIME) {
                // Don't send boosts if we're in another power profile
                String profile = mPerformanceManager.getPowerProfile();
                if (profile == null || profile.equals(PowerManager.PROFILE_BALANCED)) {
                    nativeCpuBoost(duration);
                }
            } else {
                Slog.e(TAG, "Invalid boost duration: " + duration);
            }
        }

        /**
         * Boost the CPU for an app launch
         * @hide
         */
        @Override
        public void launchBoost() {
            // Don't send boosts if we're in another power profile
            String profile = mPerformanceManager.getPowerProfile();
            if (profile == null || profile.equals(PowerManager.PROFILE_BALANCED)) {
                nativeLaunchBoost();
            }
        }

        @Override
        public void activityResumed(String componentName) {
            mPerformanceManager.activityResumed(componentName);
        }

        /**
         * Reboots the device.
         *
         * @param confirm If true, shows a reboot confirmation dialog.
         * @param reason The reason for the reboot, or null if none.
         * @param wait If true, this call waits for the reboot to complete and does not return.
         */
        @Override // Binder call
        public void reboot(boolean confirm, String reason, boolean wait) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.REBOOT, null);
            if (PowerManager.REBOOT_RECOVERY.equals(reason)) {
                mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RECOVERY, null);
            }

            final long ident = Binder.clearCallingIdentity();
            try {
                shutdownOrRebootInternal(false, confirm, reason, wait);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Shuts down the device.
         *
         * @param confirm If true, shows a shutdown confirmation dialog.
         * @param wait If true, this call waits for the shutdown to complete and does not return.
         */
        @Override // Binder call
        public void shutdown(boolean confirm, boolean wait) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.REBOOT, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                shutdownOrRebootInternal(true, confirm, null, wait);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Crash the runtime (causing a complete restart of the Android framework).
         * Requires REBOOT permission.  Mostly for testing.  Should not return.
         */
        @Override // Binder call
        public void crash(String message) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.REBOOT, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                crashInternal(message);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Set the setting that determines whether the device stays on when plugged in.
         * The argument is a bit string, with each bit specifying a power source that,
         * when the device is connected to that source, causes the device to stay on.
         * See {@link android.os.BatteryManager} for the list of power sources that
         * can be specified. Current values include
         * {@link android.os.BatteryManager#BATTERY_PLUGGED_AC}
         * and {@link android.os.BatteryManager#BATTERY_PLUGGED_USB}
         *
         * Used by "adb shell svc power stayon ..."
         *
         * @param val an {@code int} containing the bits that specify which power sources
         * should cause the device to stay on.
         */
        @Override // Binder call
        public void setStayOnSetting(int val) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.WRITE_SETTINGS, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                setStayOnSettingInternal(val);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Used by the settings application and brightness control widgets to
         * temporarily override the current screen brightness setting so that the
         * user can observe the effect of an intended settings change without applying
         * it immediately.
         *
         * The override will be canceled when the setting value is next updated.
         *
         * @param brightness The overridden brightness.
         *
         * @see android.provider.Settings.System#SCREEN_BRIGHTNESS
         */
        @Override // Binder call
        public void setTemporaryScreenBrightnessSettingOverride(int brightness) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                setTemporaryScreenBrightnessSettingOverrideInternal(brightness);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Used by the settings application and brightness control widgets to
         * temporarily override the current screen auto-brightness adjustment setting so that the
         * user can observe the effect of an intended settings change without applying
         * it immediately.
         *
         * The override will be canceled when the setting value is next updated.
         *
         * @param adj The overridden brightness, or Float.NaN to disable the override.
         *
         * @see android.provider.Settings.System#SCREEN_AUTO_BRIGHTNESS_ADJ
         */
        @Override // Binder call
        public void setTemporaryScreenAutoBrightnessAdjustmentSettingOverride(float adj) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                setTemporaryScreenAutoBrightnessAdjustmentSettingOverrideInternal(adj);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Used by the phone application to make the attention LED flash when ringing.
         */
        @Override // Binder call
        public void setAttentionLight(boolean on, int color) {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                setAttentionLightInternal(on, color);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        public void boostScreenBrightness(long eventTime) {
            if (eventTime > SystemClock.uptimeMillis()) {
                throw new IllegalArgumentException("event time must not be in the future");
            }

            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.DEVICE_POWER, null);

            final int uid = Binder.getCallingUid();
            final long ident = Binder.clearCallingIdentity();
            try {
                boostScreenBrightnessInternal(eventTime, uid);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override // Binder call
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mContext.checkCallingOrSelfPermission(Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump PowerManager from from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid());
                return;
            }

            final long ident = Binder.clearCallingIdentity();
            try {
                dumpInternal(pw);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /* updates the blocked uids, so if a wake lock is acquired for it
         * can be released.
         */
        public void updateBlockedUids(int uid, boolean isBlocked) {

            if (DEBUG_SPEW) Slog.v(TAG, "updateBlockedUids: uid = " + uid + "isBlocked = " + isBlocked);

            if (Binder.getCallingUid() != Process.SYSTEM_UID) {
                if (DEBUG_SPEW) Slog.v(TAG, "UpdateBlockedUids is not allowed");
                return;
            }

            synchronized(mLock) {
                if(isBlocked) {
                    mBlockedUids.add(new Integer(uid));
                    for (int index = 0; index < mWakeLocks.size(); index++) {
                        WakeLock wl = mWakeLocks.get(index);
                        if(wl != null) {
                            if(wl.mTag.startsWith("*sync*") && wl.mOwnerUid == Process.SYSTEM_UID) {
                                releaseWakeLockInternal(wl.mLock, wl.mFlags);
                                index--;
                                if (DEBUG_SPEW) Slog.v(TAG, "Internally releasing the wakelock"
                                        + "acquired by SyncManager");
                                continue;
                            }
                            // release the wakelock for the blocked uid
                            if (wl.mOwnerUid == uid || checkWorkSourceObjectId(uid, wl)) {
                                releaseWakeLockInternal(wl.mLock, wl.mFlags);
                                index--;
                                if (DEBUG_SPEW) Slog.v(TAG, "Internally releasing it");
                            }
                        }
                    }
                }
                else {
                    mBlockedUids.remove(new Integer(uid));
                }
            }
        }
    }

    private void setButtonBrightnessOverrideFromWindowManagerInternal(int brightness) {
        synchronized (mLock) {
            if (mButtonBrightnessOverrideFromWindowManager != brightness) {
                mButtonBrightnessOverrideFromWindowManager = brightness;
                mDirty |= DIRTY_SETTINGS;
                updatePowerStateLocked();
            }
        }
    }

    private final class LocalService extends PowerManagerInternal {
        @Override
        public void setScreenBrightnessOverrideFromWindowManager(int screenBrightness) {
            if (screenBrightness < PowerManager.BRIGHTNESS_DEFAULT
                    || screenBrightness > PowerManager.BRIGHTNESS_ON) {
                screenBrightness = PowerManager.BRIGHTNESS_DEFAULT;
            }
            setScreenBrightnessOverrideFromWindowManagerInternal(screenBrightness);
        }

        @Override
        public void setButtonBrightnessOverrideFromWindowManager(int screenBrightness) {
            mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DEVICE_POWER, null);

            final long ident = Binder.clearCallingIdentity();
            try {
                setButtonBrightnessOverrideFromWindowManagerInternal(screenBrightness);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }

        }

        @Override
        public void setDozeOverrideFromDreamManager(int screenState, int screenBrightness) {
            switch (screenState) {
                case Display.STATE_UNKNOWN:
                case Display.STATE_OFF:
                case Display.STATE_DOZE:
                case Display.STATE_DOZE_SUSPEND:
                case Display.STATE_ON:
                    break;
                default:
                    screenState = Display.STATE_UNKNOWN;
                    break;
            }
            if (screenBrightness < PowerManager.BRIGHTNESS_DEFAULT
                    || screenBrightness > PowerManager.BRIGHTNESS_ON) {
                screenBrightness = PowerManager.BRIGHTNESS_DEFAULT;
            }
            setDozeOverrideFromDreamManagerInternal(screenState, screenBrightness);
        }

        @Override
        public void setUserActivityTimeoutOverrideFromWindowManager(long timeoutMillis) {
            setUserActivityTimeoutOverrideFromWindowManagerInternal(timeoutMillis);
        }

        @Override
        public void setMaximumScreenOffTimeoutFromDeviceAdmin(int timeMs) {
            setMaximumScreenOffTimeoutFromDeviceAdminInternal(timeMs);
        }

        @Override
        public boolean getLowPowerModeEnabled() {
            synchronized (mLock) {
                return mLowPowerModeEnabled;
            }
        }

        @Override
        public void registerLowPowerModeObserver(LowPowerModeListener listener) {
            synchronized (mLock) {
                mLowPowerModeListeners.add(listener);
            }
        }
    }
}
