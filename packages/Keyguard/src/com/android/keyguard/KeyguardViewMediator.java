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

package com.android.keyguard;

import android.graphics.Bitmap;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardShowCallback;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Profile;
import android.app.ProfileManager;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.WindowManagerPolicy;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.util.cm.QuietHoursUtils;
import com.android.internal.widget.LockPatternUtils;

/**
 * Mediates requests related to the keyguard.  This includes queries about the
 * state of the keyguard, power management events that effect whether the keyguard
 * should be shown or reset, callbacks to the phone window manager to notify
 * it of when the keyguard is showing, and events from the keyguard view itself
 * stating that the keyguard was succesfully unlocked.
 *
 * Note that the keyguard view is shown when the screen is off (as appropriate)
 * so that once the screen comes on, it will be ready immediately.
 *
 * Example queries about the keyguard:
 * - is {movement, key} one that should wake the keygaurd?
 * - is the keyguard showing?
 * - are input events restricted due to the state of the keyguard?
 *
 * Callbacks to the phone window manager:
 * - the keyguard is showing
 *
 * Example external events that translate to keyguard view changes:
 * - screen turned off -> reset the keyguard, and show it so it will be ready
 *   next time the screen turns on
 * - keyboard is slid open -> if the keyguard is not secure, hide it
 *
 * Events from the keyguard view:
 * - user succesfully unlocked keyguard -> hide keyguard view, and no longer
 *   restrict input events.
 *
 * Note: in addition to normal power managment events that effect the state of
 * whether the keyguard should be showing, external apps and services may request
 * that the keyguard be disabled via {@link #setKeyguardEnabled(boolean)}.  When
 * false, this will override all other conditions for turning on the keyguard.
 *
 * Threading and synchronization:
 * This class is created by the initialization routine of the {@link WindowManagerPolicy},
 * and runs on its thread.  The keyguard UI is created from that thread in the
 * constructor of this class.  The apis may be called from other threads, including the
 * {@link com.android.server.input.InputManagerService}'s and {@link android.view.WindowManager}'s.
 * Therefore, methods on this class are synchronized, and any action that is pointed
 * directly to the keyguard UI is posted to a {@link Handler} to ensure it is taken on the UI
 * thread of the keyguard.
 */
public class KeyguardViewMediator {
    private static final int KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT = 30000;
    final static boolean DEBUG = false;
    private final static boolean DBG_WAKE = false;

    private final static String TAG = "KeyguardViewMediator";

    private static final String DELAYED_KEYGUARD_ACTION =
        "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD";

    // used for handler messages
    private static final int SHOW = 2;
    private static final int HIDE = 3;
    private static final int RESET = 4;
    private static final int VERIFY_UNLOCK = 5;
    private static final int NOTIFY_SCREEN_OFF = 6;
    private static final int NOTIFY_SCREEN_ON = 7;
    private static final int KEYGUARD_DONE = 9;
    private static final int KEYGUARD_DONE_DRAWING = 10;
    private static final int KEYGUARD_DONE_AUTHENTICATING = 11;
    private static final int SET_HIDDEN = 12;
    private static final int KEYGUARD_TIMEOUT = 13;
    private static final int SHOW_ASSISTANT = 14;
    private static final int DISPATCH_EVENT = 15;
    private static final int LAUNCH_CAMERA = 16;
    private static final int DISMISS = 17;

    /**
     * The default amount of time we stay awake (used for all key input)
     */
    protected static final int AWAKE_INTERVAL_DEFAULT_MS = 10000;

    /**
     * How long to wait after the screen turns off due to timeout before
     * turning on the keyguard (i.e, the user has this much time to turn
     * the screen back on without having to face the keyguard).
     */
    private static final int KEYGUARD_LOCK_AFTER_DELAY_DEFAULT = 5000;

    /**
     * How long we'll wait for the {@link ViewMediatorCallback#keyguardDoneDrawing()}
     * callback before unblocking a call to {@link #setKeyguardEnabled(boolean)}
     * that is reenabling the keyguard.
     */
    private static final int KEYGUARD_DONE_DRAWING_TIMEOUT_MS = 2000;

    /**
     * Allow the user to expand the status bar when the keyguard is engaged
     * (without a pattern or password).
     */
    private static final boolean ENABLE_INSECURE_STATUS_BAR_EXPAND = true;

    /** The stream type that the lock sounds are tied to. */
    private int mMasterStreamType;

    private Context mContext;
    private AlarmManager mAlarmManager;
    private AudioManager mAudioManager;
    private StatusBarManager mStatusBarManager;
    private boolean mSwitchingUser;

    private boolean mSystemReady;

    // Whether the next call to playSounds() should be skipped.  Defaults to
    // true because the first lock (on boot) should be silent.
    private boolean mSuppressNextLockSound = true;


    /** High level access to the power manager for WakeLocks */
    private PowerManager mPM;

    /** UserManager for querying number of users */
    private UserManager mUserManager;

    /** SearchManager for determining whether or not search assistant is available */
    private SearchManager mSearchManager;

    /**
     * Used to keep the device awake while to ensure the keyguard finishes opening before
     * we sleep.
     */
    private PowerManager.WakeLock mShowKeyguardWakeLock;

    private KeyguardViewManager mKeyguardViewManager;

    // these are protected by synchronized (this)

    /**
     * External apps (like the phone app) can tell us to disable the keygaurd.
     */
    private boolean mExternallyEnabled = true;

    /**
     * Remember if an external call to {@link #setKeyguardEnabled} with value
     * false caused us to hide the keyguard, so that we need to reshow it once
     * the keygaurd is reenabled with another call with value true.
     */
    private boolean mNeedToReshowWhenReenabled = false;

    // cached value of whether we are showing (need to know this to quickly
    // answer whether the input should be restricted)
    private boolean mShowing;

    // true if the keyguard is hidden by another window
    private boolean mHidden = false;

    /**
     * Helps remember whether the screen has turned on since the last time
     * it turned off due to timeout. see {@link #onScreenTurnedOff(int)}
     */
    private int mDelayedShowingSequence;

    /**
     * If the user has disabled the keyguard, then requests to exit, this is
     * how we'll ultimately let them know whether it was successful.  We use this
     * var being non-null as an indicator that there is an in progress request.
     */
    private IKeyguardExitCallback mExitSecureCallback;

    // the properties of the keyguard

    private KeyguardUpdateMonitor mUpdateMonitor;

    private boolean mScreenOn;

    // last known state of the cellular connection
    private String mPhoneState = TelephonyManager.EXTRA_STATE_IDLE;

    /**
     * we send this intent when the keyguard is dismissed.
     */
    private static final Intent USER_PRESENT_INTENT = new Intent(Intent.ACTION_USER_PRESENT)
            .addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                    | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

    /**
     * {@link #setKeyguardEnabled} waits on this condition when it reenables
     * the keyguard.
     */
    private boolean mWaitingUntilKeyguardVisible = false;
    private LockPatternUtils mLockPatternUtils;
    private boolean mKeyguardDonePending = false;

    private SoundPool mLockSounds;
    private int mLockSoundId;
    private int mUnlockSoundId;
    private int mLockSoundStreamId;

    private ProfileManager mProfileManager;

    private int mSlideLockDelay;

    /**
     * The volume applied to the lock/unlock sounds.
     */
    private final float mLockSoundVolume;

    /**
     * For managing external displays
     */
    private KeyguardDisplayManager mKeyguardDisplayManager;

    /**
     * Cache of avatar drawables, for use by KeyguardMultiUserAvatar.
     */
    private static MultiUserAvatarCache sMultiUserAvatarCache = new MultiUserAvatarCache();

    /**
     * The callback used by the keyguard view to tell the {@link KeyguardViewMediator}
     * various things.
     */
    public interface ViewMediatorCallback {
        /**
         * Reports user activity and requests that the screen stay on.
         */
        void userActivity();

        /**
         * Reports user activity and requests that the screen stay on for at least
         * the specified amount of time.
         * @param millis The amount of time in millis.  This value is currently ignored.
         */
        void userActivity(long millis);

        /**
         * Report that the keyguard is done.
         * @param authenticated Whether the user securely got past the keyguard.
         *   the only reason for this to be false is if the keyguard was instructed
         *   to appear temporarily to verify the user is supposed to get past the
         *   keyguard, and the user fails to do so.
         */
        void keyguardDone(boolean authenticated);

        /**
         * Report that the keyguard is done drawing.
         */
        void keyguardDoneDrawing();

        /**
         * Tell ViewMediator that the current view needs IME input
         * @param needsInput
         */
        void setNeedsInput(boolean needsInput);

        /**
         * Tell view mediator that the keyguard view's desired user activity timeout
         * has changed and needs to be reapplied to the window.
         */
        void onUserActivityTimeoutChanged();

        /**
         * Report that the keyguard is dismissable, pending the next keyguardDone call.
         */
        void keyguardDonePending();

        /**
         * Report when keyguard is actually gone
         */
        void keyguardGone();
    }

    KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onUserSwitching(int userId) {
            // Note that the mLockPatternUtils user has already been updated from setCurrentUser.
            // We need to force a reset of the views, since lockNow (called by
            // ActivityManagerService) will not reconstruct the keyguard if it is already showing.
            synchronized (KeyguardViewMediator.this) {
                mSwitchingUser = true;
                resetStateLocked(null);
                adjustStatusBarLocked();
                // When we switch users we want to bring the new user to the biometric unlock even
                // if the current user has gone to the backup.
                KeyguardUpdateMonitor.getInstance(mContext).setAlternateUnlockEnabled(true);
            }
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            mSwitchingUser = false;
        }

        @Override
        public void onUserRemoved(int userId) {
            mLockPatternUtils.removeUser(userId);
            sMultiUserAvatarCache.clear(userId);
        }

        @Override
        public void onUserInfoChanged(int userId) {
            sMultiUserAvatarCache.clear(userId);
        }

        @Override
        void onPhoneStateChanged(int phoneState) {
            synchronized (KeyguardViewMediator.this) {
                if (TelephonyManager.CALL_STATE_IDLE == phoneState  // call ending
                        && !mScreenOn                           // screen off
                        && mExternallyEnabled) {                // not disabled by any app

                    // note: this is a way to gracefully reenable the keyguard when the call
                    // ends and the screen is off without always reenabling the keyguard
                    // each time the screen turns off while in call (and having an occasional ugly
                    // flicker while turning back on the screen and disabling the keyguard again).
                    if (DEBUG) Log.d(TAG, "screen is off and call ended, let's make sure the "
                            + "keyguard is showing");
                    doKeyguardLocked(null);
                }
            }
        };

        @Override
        public void onClockVisibilityChanged() {
            adjustStatusBarLocked();
        }

        @Override
        public void onDeviceProvisioned() {
            sendUserPresentBroadcast();
        }

        @Override
        public void onSimStateChanged(IccCardConstants.State simState) {
            if (DEBUG) Log.d(TAG, "onSimStateChanged: " + simState);

            switch (simState) {
                case NOT_READY:
                case ABSENT:
                    // only force lock screen in case of missing sim if user hasn't
                    // gone through setup wizard
                    synchronized (this) {
                        if (!mUpdateMonitor.isDeviceProvisioned()) {
                            if (!isShowing()) {
                                if (DEBUG) Log.d(TAG, "ICC_ABSENT isn't showing,"
                                        + " we need to show the keyguard since the "
                                        + "device isn't provisioned yet.");
                                doKeyguardLocked(null);
                            } else {
                                resetStateLocked(null);
                            }
                        }
                    }
                    break;
                case PIN_REQUIRED:
                case PUK_REQUIRED:
                    synchronized (this) {
                        if (!isShowing()) {
                            if (DEBUG) Log.d(TAG, "INTENT_VALUE_ICC_LOCKED and keygaurd isn't "
                                    + "showing; need to show keyguard so user can enter sim pin");
                            doKeyguardLocked(null);
                        } else {
                            resetStateLocked(null);
                        }
                    }
                    break;
                case PERM_DISABLED:
                    synchronized (this) {
                        if (!isShowing()) {
                            if (DEBUG) Log.d(TAG, "PERM_DISABLED and "
                                  + "keygaurd isn't showing.");
                            doKeyguardLocked(null);
                        } else {
                            if (DEBUG) Log.d(TAG, "PERM_DISABLED, resetStateLocked to"
                                  + "show permanently disabled message in lockscreen.");
                            resetStateLocked(null);
                        }
                    }
                    break;
                case READY:
                    synchronized (this) {
                        if (isShowing()) {
                            resetStateLocked(null);
                        }
                    }
                    break;
            }
        }

    };

    ViewMediatorCallback mViewMediatorCallback = new ViewMediatorCallback() {
        public void userActivity() {
            KeyguardViewMediator.this.userActivity();
        }

        public void userActivity(long holdMs) {
            KeyguardViewMediator.this.userActivity(holdMs);
        }

        public void keyguardDone(boolean authenticated) {
            KeyguardViewMediator.this.keyguardDone(authenticated, true);
        }

        public void keyguardDoneDrawing() {
            mHandler.sendEmptyMessage(KEYGUARD_DONE_DRAWING);
        }

        @Override
        public void setNeedsInput(boolean needsInput) {
            mKeyguardViewManager.setNeedsInput(needsInput);
        }

        @Override
        public void onUserActivityTimeoutChanged() {
            mKeyguardViewManager.updateUserActivityTimeout();
        }

        @Override
        public void keyguardDonePending() {
            mKeyguardDonePending = true;
        }

        @Override
        public void keyguardGone() {
            mKeyguardDisplayManager.hide();
        }
    };

    private void userActivity() {
        userActivity(AWAKE_INTERVAL_DEFAULT_MS);
    }

    public void userActivity(long holdMs) {
        // We ignore the hold time.  Eventually we should remove it.
        // Instead, the keyguard window has an explicit user activity timeout set on it.
        mPM.userActivity(SystemClock.uptimeMillis(), false);
    }

    /**
     * Construct a KeyguardViewMediator
     * @param context
     * @param lockPatternUtils optional mock interface for LockPatternUtils
     */
    public KeyguardViewMediator(Context context, LockPatternUtils lockPatternUtils) {
        mContext = context;
        mPM = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mShowKeyguardWakeLock = mPM.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "show keyguard");
        mShowKeyguardWakeLock.setReferenceCounted(false);

        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(DELAYED_KEYGUARD_ACTION));

        mKeyguardDisplayManager = new KeyguardDisplayManager(context);

        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);

        mLockPatternUtils = lockPatternUtils != null
                ? lockPatternUtils : new LockPatternUtils(mContext);
        mLockPatternUtils.setCurrentUser(UserHandle.USER_OWNER);

        // Assume keyguard is showing (unless it's disabled) until we know for sure...
        mShowing = (mUpdateMonitor.isDeviceProvisioned() || mLockPatternUtils.isSecure())
                && !mLockPatternUtils.isLockScreenDisabled();

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        mProfileManager = (ProfileManager) context.getSystemService(Context.PROFILE_SERVICE);

        mKeyguardViewManager = new KeyguardViewManager(context, wm, mViewMediatorCallback,
                mLockPatternUtils);

        final ContentResolver cr = mContext.getContentResolver();

        mScreenOn = mPM.isScreenOn();

        mLockSounds = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
        String soundPath = Settings.Global.getString(cr, Settings.Global.LOCK_SOUND);
        if (soundPath != null) {
            mLockSoundId = mLockSounds.load(soundPath, 1);
        }
        if (soundPath == null || mLockSoundId == 0) {
            Log.w(TAG, "failed to load lock sound from " + soundPath);
        }
        soundPath = Settings.Global.getString(cr, Settings.Global.UNLOCK_SOUND);
        if (soundPath != null) {
            mUnlockSoundId = mLockSounds.load(soundPath, 1);
        }
        if (soundPath == null || mUnlockSoundId == 0) {
            Log.w(TAG, "failed to load unlock sound from " + soundPath);
        }
        int lockSoundDefaultAttenuation = context.getResources().getInteger(
                com.android.internal.R.integer.config_lockSoundVolumeDb);
        mLockSoundVolume = (float)Math.pow(10, (float)lockSoundDefaultAttenuation/20);
    }

    /**
     * Let us know that the system is ready after startup.
     */
    public void onSystemReady() {
        mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "onSystemReady");
            mSystemReady = true;
            mUpdateMonitor.registerCallback(mUpdateCallback);

            // Suppress biometric unlock right after boot until things have settled if it is the
            // selected security method, otherwise unsuppress it.  It must be unsuppressed if it is
            // not the selected security method for the following reason:  if the user starts
            // without a screen lock selected, the biometric unlock would be suppressed the first
            // time they try to use it.
            //
            // Note that the biometric unlock will still not show if it is not the selected method.
            // Calling setAlternateUnlockEnabled(true) simply says don't suppress it if it is the
            // selected method.
            if (mLockPatternUtils.usingBiometricWeak()
                    && mLockPatternUtils.isBiometricWeakInstalled()) {
                if (DEBUG) Log.d(TAG, "suppressing biometric unlock during boot");
                mUpdateMonitor.setAlternateUnlockEnabled(false);
            } else {
                mUpdateMonitor.setAlternateUnlockEnabled(true);
            }

            doKeyguardLocked(null);
        }
        // Most services aren't available until the system reaches the ready state, so we
        // send it here when the device first boots.
        maybeSendUserPresentBroadcast();
    }

    /**
     * Called to let us know the screen was turned off.
     * @param why either {@link WindowManagerPolicy#OFF_BECAUSE_OF_USER},
     *   {@link WindowManagerPolicy#OFF_BECAUSE_OF_TIMEOUT} or
     *   {@link WindowManagerPolicy#OFF_BECAUSE_OF_PROX_SENSOR}.
     */
    public void onScreenTurnedOff(int why) {
        synchronized (this) {
            mScreenOn = false;
            mSlideLockDelay = why;
            if (DEBUG) Log.d(TAG, "onScreenTurnedOff(" + why + ")");

            mKeyguardDonePending = false;

            // Prepare for handling Lock/Slide lock delay and timeout
            boolean lockImmediately = false;
            final ContentResolver cr = mContext.getContentResolver();
            boolean separateSlideLockTimeoutEnabled = Settings.System.getInt(cr,
                    Settings.System.SCREEN_LOCK_SLIDE_DELAY_TOGGLE, 0) == 1;
            if (mLockPatternUtils.isSecure()) {
                // Lock immediately based on setting if secure (user has a pin/pattern/password)
                // This is retained as-is to ensue AOSP security integrity is maintained
                lockImmediately = mLockPatternUtils.getPowerButtonInstantlyLocks();
            } else {
                // Unless a separate slide lock timeout is enabled, this "locks" the device when
                // not secure to provide easy access to the camera while preventing unwanted input
                lockImmediately = separateSlideLockTimeoutEnabled ? false
                        : mLockPatternUtils.getPowerButtonInstantlyLocks();
            }

            if (mExitSecureCallback != null) {
                if (DEBUG) Log.d(TAG, "pending exit secure callback cancelled");
                try {
                    mExitSecureCallback.onKeyguardExitResult(false);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
                mExitSecureCallback = null;
                if (!mExternallyEnabled) {
                    hideLocked();
                }
            } else if (mShowing) {
                notifyScreenOffLocked();
                resetStateLocked(null);
            } else if (why == WindowManagerPolicy.OFF_BECAUSE_OF_TIMEOUT
                   || (why == WindowManagerPolicy.OFF_BECAUSE_OF_USER && !lockImmediately)) {
                doKeyguardLaterLocked();
            } else if (why == WindowManagerPolicy.OFF_BECAUSE_OF_PROX_SENSOR) {
                // Do not enable the keyguard if the prox sensor forced the screen off.
            } else {
                doKeyguardLocked(null);
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).dispatchScreenTurndOff(why);
    }

    private void doKeyguardLaterLocked() {
        // if the screen turned off because of timeout or the user hit the power button
        // and we don't need to lock immediately, set an alarm
        // to enable it a little bit later (i.e, give the user a chance
        // to turn the screen back on within a certain window without
        // having to unlock the screen)
        final ContentResolver cr = mContext.getContentResolver();

        // From DisplaySettings
        long displayTimeout = Settings.System.getInt(cr, SCREEN_OFF_TIMEOUT,
                KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT);

        // From SecuritySettings
        final long lockAfterTimeout = Settings.Secure.getInt(cr,
                Settings.Secure.LOCK_SCREEN_LOCK_AFTER_TIMEOUT,
                KEYGUARD_LOCK_AFTER_DELAY_DEFAULT);

        // From CyanogenMod specific Settings
        // If utilizing a secured lock screen, we should not utilize the slide
        // delay and should let it default to the standard delay
        boolean separateSlideLockTimeoutEnabled;
        if (mLockPatternUtils.isSecure()) {
            separateSlideLockTimeoutEnabled = false;
        } else {
            separateSlideLockTimeoutEnabled = Settings.System.getInt(cr,
                    Settings.System.SCREEN_LOCK_SLIDE_DELAY_TOGGLE, 0) == 1;
        }

        int slideLockTimeoutDelay;
        if (mSlideLockDelay == WindowManagerPolicy.OFF_BECAUSE_OF_TIMEOUT) {
            slideLockTimeoutDelay = Settings.System.getInt(cr,
                    Settings.System.SCREEN_LOCK_SLIDE_TIMEOUT_DELAY,
                    KEYGUARD_LOCK_AFTER_DELAY_DEFAULT);
        } else {
            slideLockTimeoutDelay = Settings.System.getInt(cr,
                    Settings.System.SCREEN_LOCK_SLIDE_SCREENOFF_DELAY, 0);
        }

        // From DevicePolicyAdmin
        final long policyTimeout = mLockPatternUtils.getDevicePolicyManager()
                .getMaximumTimeToLock(null, mLockPatternUtils.getCurrentUser());

        long timeout;
        if (policyTimeout > 0) {
            // policy in effect. Make sure we don't go beyond policy limit.
            displayTimeout = Math.max(displayTimeout, 0); // ignore negative values
            timeout = Math.min(policyTimeout - displayTimeout, lockAfterTimeout);
        } else {
            timeout = separateSlideLockTimeoutEnabled ? slideLockTimeoutDelay : lockAfterTimeout;
        }

        if (timeout <= 0) {
            // Lock now
            doKeyguardLocked(null);
        } else {
            // Lock in the future
            long when = SystemClock.elapsedRealtime() + timeout;
            Intent intent = new Intent(DELAYED_KEYGUARD_ACTION);
            intent.putExtra("seq", mDelayedShowingSequence);
            PendingIntent sender = PendingIntent.getBroadcast(mContext,
                    0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, sender);
            if (DEBUG) Log.d(TAG, "setting alarm to turn off keyguard, seq = "
                             + mDelayedShowingSequence);
        }
    }

    private void cancelDoKeyguardLaterLocked() {
        mDelayedShowingSequence++;
    }

    /**
     * Let's us know the screen was turned on.
     */
    public void onScreenTurnedOn(IKeyguardShowCallback callback) {
        synchronized (this) {
            mScreenOn = true;
            cancelDoKeyguardLaterLocked();
            if (DEBUG) Log.d(TAG, "onScreenTurnedOn, seq = " + mDelayedShowingSequence);
            if (callback != null) {
                notifyScreenOnLocked(callback);
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).dispatchScreenTurnedOn();
        maybeSendUserPresentBroadcast();
    }

    private void maybeSendUserPresentBroadcast() {
        if (mSystemReady && mLockPatternUtils.isLockScreenDisabled()
                && mUserManager.getUsers(true).size() == 1) {
            // Lock screen is disabled because the user has set the preference to "None".
            // In this case, send out ACTION_USER_PRESENT here instead of in
            // handleKeyguardDone()
            sendUserPresentBroadcast();
        }
    }

    /**
     * A dream started.  We should lock after the usual screen-off lock timeout but only
     * if there is a secure lock pattern.
     */
    public void onDreamingStarted() {
        synchronized (this) {
            if (mScreenOn && mLockPatternUtils.isSecure()) {
                doKeyguardLaterLocked();
            }
        }
    }

    /**
     * A dream stopped.
     */
    public void onDreamingStopped() {
        synchronized (this) {
            if (mScreenOn) {
                cancelDoKeyguardLaterLocked();
            }
        }
    }

    /**
     * Same semantics as {@link WindowManagerPolicy#enableKeyguard}; provide
     * a way for external stuff to override normal keyguard behavior.  For instance
     * the phone app disables the keyguard when it receives incoming calls.
     */
    public void setKeyguardEnabled(boolean enabled) {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "setKeyguardEnabled(" + enabled + ")");

            mExternallyEnabled = enabled;

            if (!enabled && mShowing) {
                if (mExitSecureCallback != null) {
                    if (DEBUG) Log.d(TAG, "in process of verifyUnlock request, ignoring");
                    // we're in the process of handling a request to verify the user
                    // can get past the keyguard. ignore extraneous requests to disable / reenable
                    return;
                }

                // hiding keyguard that is showing, remember to reshow later
                if (DEBUG) Log.d(TAG, "remembering to reshow, hiding keyguard, "
                        + "disabling status bar expansion");
                mNeedToReshowWhenReenabled = true;
                hideLocked();
            } else if (enabled && mNeedToReshowWhenReenabled) {
                // reenabled after previously hidden, reshow
                if (DEBUG) Log.d(TAG, "previously hidden, reshowing, reenabling "
                        + "status bar expansion");
                mNeedToReshowWhenReenabled = false;

                if (mExitSecureCallback != null) {
                    if (DEBUG) Log.d(TAG, "onKeyguardExitResult(false), resetting");
                    try {
                        mExitSecureCallback.onKeyguardExitResult(false);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                    }
                    mExitSecureCallback = null;
                    resetStateLocked(null);
                } else {
                    showLocked(null);

                    // block until we know the keygaurd is done drawing (and post a message
                    // to unblock us after a timeout so we don't risk blocking too long
                    // and causing an ANR).
                    mWaitingUntilKeyguardVisible = true;
                    mHandler.sendEmptyMessageDelayed(KEYGUARD_DONE_DRAWING, KEYGUARD_DONE_DRAWING_TIMEOUT_MS);
                    if (DEBUG) Log.d(TAG, "waiting until mWaitingUntilKeyguardVisible is false");
                    while (mWaitingUntilKeyguardVisible) {
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (DEBUG) Log.d(TAG, "done waiting for mWaitingUntilKeyguardVisible");
                }
            }
        }
    }

    /**
     * @see android.app.KeyguardManager#exitKeyguardSecurely
     */
    public void verifyUnlock(IKeyguardExitCallback callback) {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "verifyUnlock");
            if (!mUpdateMonitor.isDeviceProvisioned()) {
                // don't allow this api when the device isn't provisioned
                if (DEBUG) Log.d(TAG, "ignoring because device isn't provisioned");
                try {
                    callback.onKeyguardExitResult(false);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
            } else if (mExternallyEnabled) {
                // this only applies when the user has externally disabled the
                // keyguard.  this is unexpected and means the user is not
                // using the api properly.
                Log.w(TAG, "verifyUnlock called when not externally disabled");
                try {
                    callback.onKeyguardExitResult(false);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
            } else if (mExitSecureCallback != null) {
                // already in progress with someone else
                try {
                    callback.onKeyguardExitResult(false);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
            } else {
                mExitSecureCallback = callback;
                verifyUnlockLocked();
            }
        }
    }

    /**
     * Is the keyguard currently showing?
     */
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Is the keyguard currently showing and not being force hidden?
     */
    public boolean isShowingAndNotHidden() {
        return mShowing && !mHidden;
    }

    /**
     * Notify us when the keyguard is hidden by another window
     */
    public void setHidden(boolean isHidden) {
        if (DEBUG) Log.d(TAG, "setHidden " + isHidden);
        mUpdateMonitor.sendKeyguardVisibilityChanged(!isHidden);
        mHandler.removeMessages(SET_HIDDEN);
        Message msg = mHandler.obtainMessage(SET_HIDDEN, (isHidden ? 1 : 0), 0);
        mHandler.sendMessage(msg);
    }

    /**
     * Handles SET_HIDDEN message sent by setHidden()
     */
    private void handleSetHidden(boolean isHidden) {
        synchronized (KeyguardViewMediator.this) {
            if (mHidden != isHidden) {
                mHidden = isHidden;
                updateActivityLockScreenState();
                adjustStatusBarLocked();
            }
        }
    }

    /**
     * Used by PhoneWindowManager to enable the keyguard due to a user activity timeout.
     * This must be safe to call from any thread and with any window manager locks held.
     */
    public void doKeyguardTimeout(Bundle options) {
        mHandler.removeMessages(KEYGUARD_TIMEOUT);
        Message msg = mHandler.obtainMessage(KEYGUARD_TIMEOUT, options);
        mHandler.sendMessage(msg);
    }

    /**
     * Given the state of the keyguard, is the input restricted?
     * Input is restricted when the keyguard is showing, or when the keyguard
     * was suppressed by an app that disabled the keyguard or we haven't been provisioned yet.
     */
    public boolean isInputRestricted() {
        return mShowing || mNeedToReshowWhenReenabled || !mUpdateMonitor.isDeviceProvisioned();
    }

    /**
     * Enable the keyguard if the settings are appropriate.
     */
    private void doKeyguardLocked(Bundle options) {
        // if another app is disabling us, don't show
        if (!mExternallyEnabled) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because externally disabled");

            // note: we *should* set mNeedToReshowWhenReenabled=true here, but that makes
            // for an occasional ugly flicker in this situation:
            // 1) receive a call with the screen on (no keyguard) or make a call
            // 2) screen times out
            // 3) user hits key to turn screen back on
            // instead, we reenable the keyguard when we know the screen is off and the call
            // ends (see the broadcast receiver below)
            // TODO: clean this up when we have better support at the window manager level
            // for apps that wish to be on top of the keyguard
            return;
        }

        // if the keyguard is already showing, don't bother
        if (mKeyguardViewManager.isShowing()) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because it is already showing");
            return;
        }

        // if the setup wizard hasn't run yet, don't show
        final boolean requireSim = !SystemProperties.getBoolean("keyguard.no_require_sim",
                false);
        final boolean provisioned = mUpdateMonitor.isDeviceProvisioned();
        final IccCardConstants.State state = mUpdateMonitor.getSimState();
        final boolean lockedOrMissing = state.isPinLocked()
                || ((state == IccCardConstants.State.ABSENT
                || state == IccCardConstants.State.PERM_DISABLED)
                && requireSim);

        if (!lockedOrMissing && !provisioned) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because device isn't provisioned"
                    + " and the sim is not locked or missing");
            return;
        }

        if (mUserManager.getUsers(true).size() < 2
                && mLockPatternUtils.isLockScreenDisabled() && !lockedOrMissing) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because lockscreen is off");
            return;
        }

        // if the current profile has disabled us, don't show
        Profile profile = mProfileManager.getActiveProfile();
        if (profile != null) {
            if (!lockedOrMissing
                    && profile.getScreenLockMode() == Profile.LockMode.DISABLE) {
                if (DEBUG) Log.d(TAG, "doKeyguard: not showing because of profile override");
                return;
            }
        }

        if (DEBUG) Log.d(TAG, "doKeyguard: showing the lock screen");
        showLocked(options);
    }

    /**
     * Dismiss the keyguard through the security layers.
     */
    public void handleDismiss() {
        if (mShowing && !mHidden) {
            mKeyguardViewManager.dismiss();
        }
    }

    public void dismiss() {
        mHandler.sendEmptyMessage(DISMISS);
    }

    /**
     * Send message to keyguard telling it to reset its state.
     * @param options options about how to show the keyguard
     * @see #handleReset()
     */
    private void resetStateLocked(Bundle options) {
        if (DEBUG) Log.e(TAG, "resetStateLocked");
        Message msg = mHandler.obtainMessage(RESET, options);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to verify unlock
     * @see #handleVerifyUnlock()
     */
    private void verifyUnlockLocked() {
        if (DEBUG) Log.d(TAG, "verifyUnlockLocked");
        mHandler.sendEmptyMessage(VERIFY_UNLOCK);
    }


    /**
     * Send a message to keyguard telling it the screen just turned on.
     * @see #onScreenTurnedOff(int)
     * @see #handleNotifyScreenOff
     */
    private void notifyScreenOffLocked() {
        if (DEBUG) Log.d(TAG, "notifyScreenOffLocked");
        mHandler.sendEmptyMessage(NOTIFY_SCREEN_OFF);
    }

    /**
     * Send a message to keyguard telling it the screen just turned on.
     * @see #onScreenTurnedOn()
     * @see #handleNotifyScreenOn
     */
    private void notifyScreenOnLocked(IKeyguardShowCallback result) {
        if (DEBUG) Log.d(TAG, "notifyScreenOnLocked");
        Message msg = mHandler.obtainMessage(NOTIFY_SCREEN_ON, result);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to show itself
     * @see #handleShow()
     */
    private void showLocked(Bundle options) {
        if (DEBUG) Log.d(TAG, "showLocked");
        // ensure we stay awake until we are finished displaying the keyguard
        mShowKeyguardWakeLock.acquire();
        Message msg = mHandler.obtainMessage(SHOW, options);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to hide itself
     * @see #handleHide()
     */
    private void hideLocked() {
        if (DEBUG) Log.d(TAG, "hideLocked");
        Message msg = mHandler.obtainMessage(HIDE);
        mHandler.sendMessage(msg);
    }

    public boolean isSecure() {
        return mLockPatternUtils.isSecure()
            || KeyguardUpdateMonitor.getInstance(mContext).isSimPinSecure();
    }

    /**
     * Update the newUserId. Call while holding WindowManagerService lock.
     * NOTE: Should only be called by KeyguardViewMediator in response to the user id changing.
     *
     * @param newUserId The id of the incoming user.
     */
    public void setCurrentUser(int newUserId) {
        mLockPatternUtils.setCurrentUser(newUserId);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DELAYED_KEYGUARD_ACTION.equals(intent.getAction())) {
                final int sequence = intent.getIntExtra("seq", 0);
                if (DEBUG) Log.d(TAG, "received DELAYED_KEYGUARD_ACTION with seq = "
                        + sequence + ", mDelayedShowingSequence = " + mDelayedShowingSequence);
                synchronized (KeyguardViewMediator.this) {
                    if (mDelayedShowingSequence == sequence) {
                        doKeyguardLocked(null);
                    }
                }
            }
        }
    };

    public void keyguardDone(boolean authenticated, boolean wakeup) {
        if (DEBUG) Log.d(TAG, "keyguardDone(" + authenticated + ")");
        EventLog.writeEvent(70000, 2);
        synchronized (this) {
            mKeyguardDonePending = false;
        }
        Message msg = mHandler.obtainMessage(KEYGUARD_DONE, authenticated ? 1 : 0, wakeup ? 1 : 0);
        mHandler.sendMessage(msg);
    }

    /**
     * This handler will be associated with the policy thread, which will also
     * be the UI thread of the keyguard.  Since the apis of the policy, and therefore
     * this class, can be called by other threads, any action that directly
     * interacts with the keyguard ui should be posted to this handler, rather
     * than called directly.
     */
    private Handler mHandler = new Handler(Looper.myLooper(), null, true /*async*/) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW:
                    handleShow((Bundle) msg.obj);
                    break;
                case HIDE:
                    handleHide();
                    break;
                case RESET:
                    handleReset((Bundle) msg.obj);
                    break;
                case VERIFY_UNLOCK:
                    handleVerifyUnlock();
                    break;
                case NOTIFY_SCREEN_OFF:
                    handleNotifyScreenOff();
                    break;
                case NOTIFY_SCREEN_ON:
                    handleNotifyScreenOn((IKeyguardShowCallback) msg.obj);
                    break;
                case KEYGUARD_DONE:
                    handleKeyguardDone(msg.arg1 != 0, msg.arg2 != 0);
                    break;
                case KEYGUARD_DONE_DRAWING:
                    handleKeyguardDoneDrawing();
                    break;
                case KEYGUARD_DONE_AUTHENTICATING:
                    keyguardDone(true, true);
                    break;
                case SET_HIDDEN:
                    handleSetHidden(msg.arg1 != 0);
                    break;
                case KEYGUARD_TIMEOUT:
                    synchronized (KeyguardViewMediator.this) {
                        doKeyguardLocked((Bundle) msg.obj);
                    }
                    break;
                case SHOW_ASSISTANT:
                    handleShowAssistant();
                    break;
                case DISPATCH_EVENT:
                    handleDispatchEvent((MotionEvent) msg.obj);
                    break;
                case LAUNCH_CAMERA:
                    handleLaunchCamera();
                    break;
                case DISMISS:
                    handleDismiss();
                    break;
            }
        }
    };

    /**
     * @see #keyguardDone
     * @see #KEYGUARD_DONE
     */
    private void handleKeyguardDone(boolean authenticated, boolean wakeup) {
        if (DEBUG) Log.d(TAG, "handleKeyguardDone");

        if (authenticated) {
            mUpdateMonitor.clearFailedUnlockAttempts();
        }

        if (mExitSecureCallback != null) {
            try {
                mExitSecureCallback.onKeyguardExitResult(authenticated);
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call onKeyguardExitResult(" + authenticated + ")", e);
            }

            mExitSecureCallback = null;

            if (authenticated) {
                // after succesfully exiting securely, no need to reshow
                // the keyguard when they've released the lock
                mExternallyEnabled = true;
                mNeedToReshowWhenReenabled = false;
            }
        }

        handleHide();
        sendUserPresentBroadcast();
    }

    protected void handleLaunchCamera() {
        mKeyguardViewManager.launchCamera();
    }

    protected void handleDispatchEvent(MotionEvent event) {
        mKeyguardViewManager.dispatch(event);
    }

    private void sendUserPresentBroadcast() {
        final UserHandle currentUser = new UserHandle(mLockPatternUtils.getCurrentUser());
        mContext.sendBroadcastAsUser(USER_PRESENT_INTENT, currentUser);
    }

    /**
     * @see #keyguardDoneDrawing
     * @see #KEYGUARD_DONE_DRAWING
     */
    private void handleKeyguardDoneDrawing() {
        synchronized(this) {
            if (DEBUG) Log.d(TAG, "handleKeyguardDoneDrawing");
            if (mWaitingUntilKeyguardVisible) {
                if (DEBUG) Log.d(TAG, "handleKeyguardDoneDrawing: notifying mWaitingUntilKeyguardVisible");
                mWaitingUntilKeyguardVisible = false;
                notifyAll();

                // there will usually be two of these sent, one as a timeout, and one
                // as a result of the callback, so remove any remaining messages from
                // the queue
                mHandler.removeMessages(KEYGUARD_DONE_DRAWING);
            }
        }
    }

    private void playSounds(boolean locked) {
        // User feedback for keyguard.

        if (mSuppressNextLockSound) {
            mSuppressNextLockSound = false;
            return;
        }

        if (QuietHoursUtils.inQuietHours(mContext, Settings.System.QUIET_HOURS_SYSTEM)) {
            return;
        }

        final ContentResolver cr = mContext.getContentResolver();
        if (Settings.System.getInt(cr, Settings.System.LOCKSCREEN_SOUNDS_ENABLED, 1) == 1) {
            final int whichSound = locked
                ? mLockSoundId
                : mUnlockSoundId;
            mLockSounds.stop(mLockSoundStreamId);
            // Init mAudioManager
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (mAudioManager == null) return;
                mMasterStreamType = mAudioManager.getMasterStreamType();
            }
            // If the stream is muted, don't play the sound
            if (mAudioManager.isStreamMute(mMasterStreamType)) return;

            // If music is playing, don't play the sound
            if (mAudioManager.isMusicActive()) return;

            mLockSoundStreamId = mLockSounds.play(whichSound,
                    mLockSoundVolume, mLockSoundVolume, 1/*priortiy*/, 0/*loop*/, 1.0f/*rate*/);
        }
    }

    private void updateActivityLockScreenState() {
        try {
            ActivityManagerNative.getDefault().setLockScreenShown(mShowing && !mHidden);
        } catch (RemoteException e) {
        }
    }

    /**
     * Handle message sent by {@link #showLocked}.
     * @see #SHOW
     */
    private void handleShow(Bundle options) {
        synchronized (KeyguardViewMediator.this) {
            if (!mSystemReady) {
                if (DEBUG) Log.d(TAG, "ignoring handleShow because system is not ready.");
                return;
            } else {
                if (DEBUG) Log.d(TAG, "handleShow");
            }

            new Thread(new Runnable() {
                public void run() {
                    playSounds(true);
                }
            }).start();

            mKeyguardViewManager.show(options);
            mShowing = true;
            mKeyguardDonePending = false;
            updateActivityLockScreenState();
            adjustStatusBarLocked();
            userActivity();
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs("lock");
            } catch (RemoteException e) {
            }

            mShowKeyguardWakeLock.release();
        }
        mKeyguardDisplayManager.show();
    }

    /**
     * Handle message sent by {@link #hideLocked()}
     * @see #HIDE
     */
    private void handleHide() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleHide");

            // only play "unlock" noises if not on a call (since the incall UI
            // disables the keyguard)
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(mPhoneState)) {
                playSounds(false);
            }

            mKeyguardViewManager.hide();
            mShowing = false;
            mKeyguardDonePending = false;
            updateActivityLockScreenState();
            adjustStatusBarLocked();
        }
    }

    private void adjustStatusBarLocked() {
        if (mStatusBarManager == null) {
            mStatusBarManager = (StatusBarManager)
                    mContext.getSystemService(Context.STATUS_BAR_SERVICE);
        }
        if (mStatusBarManager == null) {
            Log.w(TAG, "Could not get status bar manager");
        } else {
            // Disable aspects of the system/status/navigation bars that must not be re-enabled by
            // windows that appear on top, ever
            int flags = StatusBarManager.DISABLE_NONE;
            if (mShowing) {
                // Permanently disable components not available when keyguard is enabled
                // (like recents). Temporary enable/disable (e.g. the "back" button) are
                // done in KeyguardHostView.
                flags |= StatusBarManager.DISABLE_RECENT;
                if (isSecure() || !ENABLE_INSECURE_STATUS_BAR_EXPAND) {
                    // showing secure lockscreen; disable expanding.
                    flags |= StatusBarManager.DISABLE_EXPAND;
                }
                if (isSecure()) {
                    // showing secure lockscreen; disable ticker.
                    flags |= StatusBarManager.DISABLE_NOTIFICATION_TICKER;
                }
                if (!isAssistantAvailable()) {
                    flags |= StatusBarManager.DISABLE_SEARCH;
                }
            }

            if (DEBUG) {
                Log.d(TAG, "adjustStatusBarLocked: mShowing=" + mShowing + " mHidden=" + mHidden
                        + " isSecure=" + isSecure() + " --> flags=0x" + Integer.toHexString(flags));
            }

            if (!(mContext instanceof Activity)) {
                mStatusBarManager.disable(flags);
            }
        }
    }

    /**
     * Handle message sent by {@link #resetStateLocked(Bundle)}
     * @see #RESET
     */
    private void handleReset(Bundle options) {
        if (options == null) {
            options = new Bundle();
        }
        options.putBoolean(KeyguardViewManager.IS_SWITCHING_USER, mSwitchingUser);
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleReset");
            mKeyguardViewManager.reset(options);
        }
    }

    /**
     * Handle message sent by {@link #verifyUnlock}
     * @see #VERIFY_UNLOCK
     */
    private void handleVerifyUnlock() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleVerifyUnlock");
            mKeyguardViewManager.verifyUnlock();
            mShowing = true;
            updateActivityLockScreenState();
        }
    }

    /**
     * Handle message sent by {@link #notifyScreenOffLocked()}
     * @see #NOTIFY_SCREEN_OFF
     */
    private void handleNotifyScreenOff() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenOff");
            mKeyguardViewManager.onScreenTurnedOff();
        }
    }

    /**
     * Handle message sent by {@link #notifyScreenOnLocked()}
     * @see #NOTIFY_SCREEN_ON
     */
    private void handleNotifyScreenOn(IKeyguardShowCallback callback) {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenOn");
            mKeyguardViewManager.onScreenTurnedOn(callback);
        }
    }

    public boolean isDismissable() {
        return mKeyguardDonePending || !isSecure();
    }

    public void showAssistant() {
        Message msg = mHandler.obtainMessage(SHOW_ASSISTANT);
        mHandler.sendMessage(msg);
    }

    public void handleShowAssistant() {
        mKeyguardViewManager.showAssistant();
    }

    private boolean isAssistantAvailable() {
        return mSearchManager != null
                && mSearchManager.getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
    }

    public static MultiUserAvatarCache getAvatarCache() {
        return sMultiUserAvatarCache;
    }

    public void dispatch(MotionEvent event) {
        Message msg = mHandler.obtainMessage(DISPATCH_EVENT, event);
        mHandler.sendMessage(msg);
    }

    public void launchCamera() {
        Message msg = mHandler.obtainMessage(LAUNCH_CAMERA);
        mHandler.sendMessage(msg);
    }

    public void onBootCompleted() {
        mUpdateMonitor.dispatchBootCompleted();
    }
}
