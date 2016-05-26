/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.keyguard;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.app.trust.TrustManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.DeadObjectException;
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
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerPolicy;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.qs.tiles.LockscreenToggleTile;
import com.android.systemui.statusbar.StatusBarState;
import cyanogenmod.app.Profile;
import cyanogenmod.app.ProfileManager;

import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardConstants;
import com.android.keyguard.KeyguardDisplayManager;
import com.android.keyguard.KeyguardSecurityView;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.SystemUI;
import com.android.systemui.statusbar.phone.FingerprintUnlockController;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.StatusBarWindowManager;
import cyanogenmod.providers.CMSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

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
 * This class is created by the initialization routine of the {@link android.view.WindowManagerPolicy},
 * and runs on its thread.  The keyguard UI is created from that thread in the
 * constructor of this class.  The apis may be called from other threads, including the
 * {@link com.android.server.input.InputManagerService}'s and {@link android.view.WindowManager}'s.
 * Therefore, methods on this class are synchronized, and any action that is pointed
 * directly to the keyguard UI is posted to a {@link android.os.Handler} to ensure it is taken on the UI
 * thread of the keyguard.
 */
public class KeyguardViewMediator extends SystemUI {
    private static final int KEYGUARD_DISPLAY_TIMEOUT_DELAY_DEFAULT = 30000;
    private static final long KEYGUARD_DONE_PENDING_TIMEOUT_MS = 3000;

    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final boolean DEBUG_SIM_STATES = KeyguardConstants.DEBUG_SIM_STATES;
    private final static boolean DBG_WAKE = false;

    private final static String TAG = "KeyguardViewMediator";

    private static final String DELAYED_KEYGUARD_ACTION =
        "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD";

    private static final String DISMISS_KEYGUARD_SECURELY_ACTION =
        "com.android.keyguard.action.DISMISS_KEYGUARD_SECURELY";

    private static final String KEYGUARD_SERVICE_ACTION_STATE_CHANGE =
            "com.android.internal.action.KEYGUARD_SERVICE_STATE_CHANGED";
    private static final String KEYGUARD_SERVICE_EXTRA_ACTIVE = "active";

    private static final String DECRYPT_STATE = "trigger_restart_framework";

    // used for handler messages
    private static final int SHOW = 2;
    private static final int HIDE = 3;
    private static final int RESET = 4;
    private static final int VERIFY_UNLOCK = 5;
    private static final int NOTIFY_FINISHED_GOING_TO_SLEEP = 6;
    private static final int NOTIFY_SCREEN_TURNING_ON = 7;
    private static final int KEYGUARD_DONE = 9;
    private static final int KEYGUARD_DONE_DRAWING = 10;
    private static final int KEYGUARD_DONE_AUTHENTICATING = 11;
    private static final int SET_OCCLUDED = 12;
    private static final int KEYGUARD_TIMEOUT = 13;
    private static final int DISMISS = 17;
    private static final int START_KEYGUARD_EXIT_ANIM = 18;
    private static final int ON_ACTIVITY_DRAWN = 19;
    private static final int KEYGUARD_DONE_PENDING_TIMEOUT = 20;
    private static final int NOTIFY_STARTED_WAKING_UP = 21;
    private static final int NOTIFY_SCREEN_TURNED_ON = 22;
    private static final int NOTIFY_SCREEN_TURNED_OFF = 23;
    private static final int NOTIFY_STARTED_GOING_TO_SLEEP = 24;

    /**
     * The default amount of time we stay awake (used for all key input)
     */
    public static final int AWAKE_INTERVAL_DEFAULT_MS = 10000;

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
     * Secure setting whether analytics are collected on the keyguard.
     */
    private static final String KEYGUARD_ANALYTICS_SETTING = "keyguard_analytics";

    /** The stream type that the lock sounds are tied to. */
    private int mUiSoundsStreamType;

    private AlarmManager mAlarmManager;
    private AudioManager mAudioManager;
    private StatusBarManager mStatusBarManager;
    private boolean mSwitchingUser;
    private boolean mSystemReady;
    private boolean mBootCompleted;
    private boolean mBootSendUserPresent;

    /** High level access to the power manager for WakeLocks */
    private PowerManager mPM;

    /** High level access to the window manager for dismissing keyguard animation */
    private IWindowManager mWM;


    /** TrustManager for letting it know when we change visibility */
    private TrustManager mTrustManager;

    /** SearchManager for determining whether or not search assistant is available */
    private SearchManager mSearchManager;

    /**
     * Used to keep the device awake while to ensure the keyguard finishes opening before
     * we sleep.
     */
    private PowerManager.WakeLock mShowKeyguardWakeLock;

    private StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;

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

    /** Cached value of #isInputRestricted */
    private boolean mInputRestricted;

    // true if the keyguard is hidden by another window
    private boolean mOccluded = false;

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

    /**
     * Whether we are bound to the service delegate
     */
    private boolean mKeyguardBound;

    // the properties of the keyguard

    private KeyguardUpdateMonitor mUpdateMonitor;

    private boolean mDeviceInteractive;
    private boolean mGoingToSleep;

    // last known state of the cellular connection
    private String mPhoneState = TelephonyManager.EXTRA_STATE_IDLE;

    /**
     * Whether a hide is pending an we are just waiting for #startKeyguardExitAnimation to be
     * called.
     * */
    private boolean mHiding;

    /**
     * Whether we are disabling the lock screen internally
     */
    private boolean mInternallyDisabled = false;

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
    private boolean mHideAnimationRun = false;

    private SoundPool mLockSounds;
    private int mLockSoundId;
    private int mUnlockSoundId;
    private int mTrustedSoundId;
    private int mLockSoundStreamId;

    /**
     * The animation used for hiding keyguard. This is used to fetch the animation timings if
     * WindowManager is not providing us with them.
     */
    private Animation mHideAnimation;

    /**
     * The volume applied to the lock/unlock sounds.
     */
    private float mLockSoundVolume;

    /**
     * For managing external displays
     */
    private KeyguardDisplayManager mKeyguardDisplayManager;

    private final ArrayList<IKeyguardStateCallback> mKeyguardStateCallbacks = new ArrayList<>();

    /**
     * When starting going to sleep, we figured out that we need to reset Keyguard state and this
     * should be committed when finished going to sleep.
     */
    private boolean mPendingReset;

    /**
     * When starting going to sleep, we figured out that we need to lock Keyguard and this should be
     * committed when finished going to sleep.
     */
    private boolean mPendingLock;

    private boolean mCryptKeeperEnabled = true;

    private boolean mWakeAndUnlocking;
    private IKeyguardDrawnCallback mDrawnCallback;

    private LockscreenEnabledSettingsObserver mSettingsObserver;
    private PhoneStatusBar mStatusBar;

    public static class LockscreenEnabledSettingsObserver extends UserContentObserver {

        private static final String KEY_ENABLED = "lockscreen_enabled";

        private boolean mObserving;
        private SharedPreferences mPrefs;
        private Context mContext;

        public LockscreenEnabledSettingsObserver(Context context, Handler handler) {
            super(handler);
            mContext = context;
            mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);
        }

        public boolean getPersistedDefaultOldSetting() {
            return mPrefs.getBoolean(KEY_ENABLED, true);
        }

        @Override
        public void observe() {
            if (mObserving) {
                return;
            }
            mObserving = true;
            mContext.getContentResolver().registerContentObserver(CMSettings.Secure.getUriFor(
                    CMSettings.Secure.LOCKSCREEN_INTERNALLY_ENABLED), false, this,
                    UserHandle.USER_ALL);
            update();
        }

        @Override
        public void unobserve() {
            if (mObserving) {
                mObserving = false;
                mContext.getContentResolver().unregisterContentObserver(this);
            }
        }

        @Override
        public void update() {
        }
    }

    KeyguardUpdateMonitorCallback mUpdateCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onUserSwitching(int userId) {
            // Note that the mLockPatternUtils user has already been updated from setCurrentUser.
            // We need to force a reset of the views, since lockNow (called by
            // ActivityManagerService) will not reconstruct the keyguard if it is already showing.
            synchronized (KeyguardViewMediator.this) {
                mSwitchingUser = true;
                resetKeyguardDonePendingLocked();
                resetStateLocked();
                adjustStatusBarLocked();
            }
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            mSwitchingUser = false;
            if (userId != UserHandle.USER_OWNER) {
                UserInfo info = UserManager.get(mContext).getUserInfo(userId);
                if (info != null && info.isGuest()) {
                    // If we just switched to a guest, try to dismiss keyguard.
                    dismiss();
                }
            }
        }

        @Override
        public void onUserInfoChanged(int userId) {
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            synchronized (KeyguardViewMediator.this) {
                if (TelephonyManager.CALL_STATE_IDLE == phoneState  // call ending
                        && !mDeviceInteractive                           // screen off
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
        }

        @Override
        public void onClockVisibilityChanged() {
            adjustStatusBarLocked();
        }

        @Override
        public void onDeviceProvisioned() {
            sendUserPresentBroadcast();
        }

        @Override
        public void onSimStateChanged(int subId, int slotId, IccCardConstants.State simState) {

            if (DEBUG_SIM_STATES) {
                Log.d(TAG, "onSimStateChanged(subId=" + subId + ", slotId=" + slotId
                        + ",state=" + simState + ")");
            }

            int size = mKeyguardStateCallbacks.size();
            boolean simPinSecure = mUpdateMonitor.isSimPinSecure();
            for (int i = size - 1; i >= 0; i--) {
                try {
                    mKeyguardStateCallbacks.get(i).onSimSecureStateChanged(simPinSecure);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onSimSecureStateChanged", e);
                    if (e instanceof DeadObjectException) {
                        mKeyguardStateCallbacks.remove(i);
                    }
                }
            }

            switch (simState) {
                case NOT_READY:
                case ABSENT:
                    // only force lock screen in case of missing sim if user hasn't
                    // gone through setup wizard
                    synchronized (this) {
                        if (shouldWaitForProvisioning() && !mShowing) {
                            if (DEBUG_SIM_STATES) Log.d(TAG, "ICC_ABSENT isn't showing,"
                                    + " we need to show the keyguard since the "
                                    + "device isn't provisioned yet.");
                            doKeyguardLocked(null);
                        } else {
                            resetStateLocked();
                        }
                    }
                    break;
                case PIN_REQUIRED:
                case PUK_REQUIRED:
                    synchronized (this) {
                        mStatusBar.hideHeadsUp();
                        if (!mShowing) {
                            if (DEBUG_SIM_STATES) Log.d(TAG,
                                    "INTENT_VALUE_ICC_LOCKED and keygaurd isn't "
                                    + "showing; need to show keyguard so user can enter sim pin");
                            doKeyguardLocked(null);
                        } else {
                            resetStateLocked();
                        }
                    }
                    break;
                case PERM_DISABLED:
                    synchronized (this) {
                        if (!mShowing) {
                            if (DEBUG_SIM_STATES) Log.d(TAG, "PERM_DISABLED and "
                                  + "keygaurd isn't showing.");
                            doKeyguardLocked(null);
                        } else {
                            if (DEBUG_SIM_STATES) Log.d(TAG, "PERM_DISABLED, resetStateLocked to"
                                  + "show permanently disabled message in lockscreen.");
                            resetStateLocked();
                        }
                    }
                    break;
                case READY:
                    synchronized (this) {
                        if ((mInternallyDisabled || isProfileDisablingKeyguard())
                                && !mUpdateMonitor.isSimPinSecure()) {
                            hideLocked();
                        } else if (mShowing) {
                            resetStateLocked();
                        }
                    }
                    break;
                default:
                    if (DEBUG_SIM_STATES) Log.v(TAG, "Ignoring state: " + simState);
                    break;
            }
        }
    };

    ViewMediatorCallback mViewMediatorCallback = new ViewMediatorCallback() {

        public void userActivity() {
            KeyguardViewMediator.this.userActivity();
        }

        public void keyguardDone(boolean strongAuth) {
            if (!mKeyguardDonePending) {
                KeyguardViewMediator.this.keyguardDone(true /* authenticated */);
            }
            if (strongAuth) {
                mUpdateMonitor.reportSuccessfulStrongAuthUnlockAttempt();
            }
        }

        public void keyguardDoneDrawing() {
            mHandler.sendEmptyMessage(KEYGUARD_DONE_DRAWING);
        }

        @Override
        public void setNeedsInput(boolean needsInput) {
            mStatusBarKeyguardViewManager.setNeedsInput(needsInput);
        }

        @Override
        public void keyguardDonePending(boolean strongAuth) {
            mKeyguardDonePending = true;
            mHideAnimationRun = true;
            mStatusBarKeyguardViewManager.startPreHideAnimation(null /* finishRunnable */);
            mHandler.sendEmptyMessageDelayed(KEYGUARD_DONE_PENDING_TIMEOUT,
                    KEYGUARD_DONE_PENDING_TIMEOUT_MS);
            if (strongAuth) {
                mUpdateMonitor.reportSuccessfulStrongAuthUnlockAttempt();
            }
        }

        @Override
        public void keyguardGone() {
            mKeyguardDisplayManager.hide();
        }

        @Override
        public void readyForKeyguardDone() {
            if (mKeyguardDonePending) {
                // Somebody has called keyguardDonePending before, which means that we are
                // authenticated
                KeyguardViewMediator.this.keyguardDone(true /* authenticated */);
            }
        }

        @Override
        public void resetKeyguard() {
            resetStateLocked();
        }

        @Override
        public void playTrustedSound() {
            KeyguardViewMediator.this.playTrustedSound();
        }

        @Override
        public boolean isInputRestricted() {
            return KeyguardViewMediator.this.isInputRestricted();
        }

        @Override
        public boolean isScreenOn() {
            return mDeviceInteractive;
        }

        @Override
        public int getBouncerPromptReason() {
            int currentUser = ActivityManager.getCurrentUser();
            if ((mUpdateMonitor.getUserTrustIsManaged(currentUser)
                    || mUpdateMonitor.isUnlockWithFingerprintPossible(currentUser))
                    && !mUpdateMonitor.getStrongAuthTracker().hasUserAuthenticatedSinceBoot()) {
                return KeyguardSecurityView.PROMPT_REASON_RESTART;
            } else if (mUpdateMonitor.isUnlockWithFingerprintPossible(currentUser)
                    && mUpdateMonitor.hasFingerprintUnlockTimedOut(currentUser)) {
                return KeyguardSecurityView.PROMPT_REASON_TIMEOUT;
            }
            return KeyguardSecurityView.PROMPT_REASON_NONE;
        }
    };

    public void userActivity() {
        mPM.userActivity(SystemClock.uptimeMillis(), false);
    }

    private void setupLocked() {
        mPM = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWM = WindowManagerGlobal.getWindowManagerService();
        mTrustManager = (TrustManager) mContext.getSystemService(Context.TRUST_SERVICE);

        mShowKeyguardWakeLock = mPM.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "show keyguard");
        mShowKeyguardWakeLock.setReferenceCounted(false);
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(DELAYED_KEYGUARD_ACTION));
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(DISMISS_KEYGUARD_SECURELY_ACTION),
                android.Manifest.permission.CONTROL_KEYGUARD, null);
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(KEYGUARD_SERVICE_ACTION_STATE_CHANGE),
                android.Manifest.permission.CONTROL_KEYGUARD, null);
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));

        mKeyguardDisplayManager = new KeyguardDisplayManager(mContext);

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);

        mLockPatternUtils = new LockPatternUtils(mContext);
        KeyguardUpdateMonitor.setCurrentUser(ActivityManager.getCurrentUser());

        // Assume keyguard is showing (unless it's disabled) until we know for sure...
        setShowingLocked(!shouldWaitForProvisioning() && !mLockPatternUtils.isLockScreenDisabled(
                KeyguardUpdateMonitor.getCurrentUser()));
        updateInputRestrictedLocked();
        mTrustManager.reportKeyguardShowingChanged();

        mStatusBarKeyguardViewManager = new StatusBarKeyguardViewManager(mContext,
                mViewMediatorCallback, mLockPatternUtils);
        final ContentResolver cr = mContext.getContentResolver();

        mDeviceInteractive = mPM.isInteractive();

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
        soundPath = Settings.Global.getString(cr, Settings.Global.TRUSTED_SOUND);
        if (soundPath != null) {
            mTrustedSoundId = mLockSounds.load(soundPath, 1);
        }
        if (soundPath == null || mTrustedSoundId == 0) {
            Log.w(TAG, "failed to load trusted sound from " + soundPath);
        }

        int lockSoundDefaultAttenuation = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lockSoundVolumeDb);
        mLockSoundVolume = (float)Math.pow(10, (float)lockSoundDefaultAttenuation/20);

        mHideAnimation = AnimationUtils.loadAnimation(mContext,
                com.android.internal.R.anim.lock_screen_behind_enter);

        mSettingsObserver = new LockscreenEnabledSettingsObserver(mContext, new Handler()) {
            @Override
            public void update() {
                boolean newDisabledState = CMSettings.Secure.getIntForUser(mContext.getContentResolver(),
                        CMSettings.Secure.LOCKSCREEN_INTERNALLY_ENABLED,
                        getPersistedDefaultOldSetting() ? 1 : 0,
                        UserHandle.USER_CURRENT) == 0;

                synchronized (KeyguardViewMediator.this) {
                    if (mKeyguardBound) {
                        if (newDisabledState != mInternallyDisabled) {
                            // it was updated,
                            setKeyguardEnabledInternal(!newDisabledState);
                        }
                    }
                }
            }
        };
    }

    @Override
    public void start() {
        synchronized (this) {
            setupLocked();
        }
        putComponent(KeyguardViewMediator.class, this);
    }

    /**
     * Let us know that the system is ready after startup.
     */
    public void onSystemReady() {
        mSearchManager = (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "onSystemReady");
            mSystemReady = true;
            doKeyguardLocked(null);
            mUpdateMonitor.registerCallback(mUpdateCallback);
        }
        // Most services aren't available until the system reaches the ready state, so we
        // send it here when the device first boots.
        maybeSendUserPresentBroadcast();
    }

    /**
     * Called to let us know the screen was turned off.
     * @param why either {@link android.view.WindowManagerPolicy#OFF_BECAUSE_OF_USER} or
     *   {@link android.view.WindowManagerPolicy#OFF_BECAUSE_OF_TIMEOUT}.
     */
    public void onStartedGoingToSleep(int why) {
        if (DEBUG) Log.d(TAG, "onStartedGoingToSleep(" + why + ")");
        synchronized (this) {
            mDeviceInteractive = false;
            mGoingToSleep = true;

            // Lock immediately based on setting if secure (user has a pin/pattern/password).
            // This also "locks" the device when not secure to provide easy access to the
            // camera while preventing unwanted input.
            int currentUser = KeyguardUpdateMonitor.getCurrentUser();
            final boolean lockImmediately =
                    mLockPatternUtils.getPowerButtonInstantlyLocks(currentUser)
                            || !mLockPatternUtils.isSecure(currentUser);
            long timeout = getLockTimeout();

            if (mExitSecureCallback != null) {
                if (DEBUG) Log.d(TAG, "pending exit secure callback cancelled");
                try {
                    mExitSecureCallback.onKeyguardExitResult(false);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
                mExitSecureCallback = null;
                if (!mInternallyDisabled && !mExternallyEnabled) {
                    hideLocked();
                }
            } else if (mShowing) {
                mPendingReset = true;
            } else if ((why == WindowManagerPolicy.OFF_BECAUSE_OF_TIMEOUT && timeout > 0)
                    || (why == WindowManagerPolicy.OFF_BECAUSE_OF_USER && !lockImmediately)) {
                doKeyguardLaterLocked(timeout);
            } else if (!mLockPatternUtils.isLockScreenDisabled(currentUser)) {
                mPendingLock = true;
            }

            if (mPendingLock) {
                playSounds(true);
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).dispatchStartedGoingToSleep(why);
        notifyStartedGoingToSleep();
    }

    public void onFinishedGoingToSleep(int why) {
        if (DEBUG) Log.d(TAG, "onFinishedGoingToSleep(" + why + ")");
        synchronized (this) {
            mDeviceInteractive = false;
            mGoingToSleep = false;

            resetKeyguardDonePendingLocked();
            mHideAnimationRun = false;

            notifyFinishedGoingToSleep();

            if (mPendingReset) {
                resetStateLocked();
                mPendingReset = false;
            }
            if (mPendingLock) {
                doKeyguardLocked(null);
                mPendingLock = false;
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).dispatchFinishedGoingToSleep(why);
    }

    private long getLockTimeout() {
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

        // From DevicePolicyAdmin
        final long policyTimeout = mLockPatternUtils.getDevicePolicyManager()
                .getMaximumTimeToLock(null, KeyguardUpdateMonitor.getCurrentUser());

        long timeout;
        if (policyTimeout > 0) {
            // policy in effect. Make sure we don't go beyond policy limit.
            displayTimeout = Math.max(displayTimeout, 0); // ignore negative values
            timeout = Math.min(policyTimeout - displayTimeout, lockAfterTimeout);
        } else {
            timeout = lockAfterTimeout;
        }
        return timeout;
    }

    private void doKeyguardLaterLocked() {
        long timeout = getLockTimeout();
        if (timeout == 0) {
            doKeyguardLocked(null);
        } else {
            doKeyguardLaterLocked(timeout);
        }
    }

    private void doKeyguardLaterLocked(long timeout) {
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

    private void cancelDoKeyguardLaterLocked() {
        mDelayedShowingSequence++;
    }

    public boolean isKeyguardBound() {
        return mKeyguardBound;
    }

    /**
     * Let's us know when the device is waking up.
     */
    public void onStartedWakingUp() {

        // TODO: Rename all screen off/on references to interactive/sleeping
        synchronized (this) {
            mDeviceInteractive = true;
            cancelDoKeyguardLaterLocked();
            if (DEBUG) Log.d(TAG, "onStartedWakingUp, seq = " + mDelayedShowingSequence);
            notifyStartedWakingUp();
        }
        KeyguardUpdateMonitor.getInstance(mContext).dispatchStartedWakingUp();
        maybeSendUserPresentBroadcast();
    }

    public void onScreenTurningOn(IKeyguardDrawnCallback callback) {
        notifyScreenOn(callback);
    }

    public void onScreenTurnedOn() {
        notifyScreenTurnedOn();
        mUpdateMonitor.dispatchScreenTurnedOn();
    }

    public void onScreenTurnedOff() {
        notifyScreenTurnedOff();
        mUpdateMonitor.dispatchScreenTurnedOff();
    }

    private void maybeSendUserPresentBroadcast() {
        if (mSystemReady && isKeyguardDisabled(
                KeyguardUpdateMonitor.getCurrentUser())) {
            // Lock screen is disabled because the user has set the preference to "None".
            // In this case, send out ACTION_USER_PRESENT here instead of in
            // handleKeyguardDone()
            sendUserPresentBroadcast();
        }
    }

    private boolean isKeyguardDisabled(int userId) {
        if (!mExternallyEnabled) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled externally");
            return true;
        }
        if (mLockPatternUtils.isLockScreenDisabled(userId)) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled by setting");
            return true;
        }
        if (mInternallyDisabled) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled internally");
            return true;
        }
        if (isProfileDisablingKeyguard()) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled by profile");
            return true;
        }
        return false;
    }

    private boolean isCryptKeeperEnabled() {
        if (!mCryptKeeperEnabled) {
            // once it's disabled, it's disabled.
            return false;
        }
        final String state = SystemProperties.get("vold.decrypt");
        mCryptKeeperEnabled = !"".equals(state) && !DECRYPT_STATE.equals(state);
        if (DEBUG) Log.w(TAG, "updated crypt keeper state to: " + mCryptKeeperEnabled);
        return mCryptKeeperEnabled;
    }

    /**
     * A dream started.  We should lock after the usual screen-off lock timeout but only
     * if there is a secure lock pattern.
     */
    public void onDreamingStarted() {
        synchronized (this) {
            if (mDeviceInteractive
                    && mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser())) {
                doKeyguardLaterLocked();
            }
        }
    }

    /**
     * A dream stopped.
     */
    public void onDreamingStopped() {
        synchronized (this) {
            if (mDeviceInteractive) {
                cancelDoKeyguardLaterLocked();
            }
        }
    }

    /**
     * Set the internal keyguard enabled state. This allows SystemUI to disable the lockscreen,
     * overriding any apps.
     * @param enabled
     */
    public void setKeyguardEnabledInternal(boolean enabled) {
        mInternallyDisabled = !enabled;
        if (!mUpdateMonitor.isSimPinSecure()) {
            // disable when sim is ready
            return;
        }
        setKeyguardEnabled(enabled);
        if (mInternallyDisabled) {
            mNeedToReshowWhenReenabled = false;
        }
    }

    public boolean getKeyguardEnabledInternal() {
        return !mInternallyDisabled;
    }

    public boolean isProfileDisablingKeyguard() {
        final Profile activeProfile = ProfileManager.getInstance(mContext).getActiveProfile();
        return activeProfile != null
                && activeProfile.getScreenLockMode().getValue() == Profile.LockMode.DISABLE;
    }

    /**
     * Same semantics as {@link android.view.WindowManagerPolicy#enableKeyguard}; provide
     * a way for external stuff to override normal keyguard behavior.  For instance
     * the phone app disables the keyguard when it receives incoming calls.
     */
    public void setKeyguardEnabled(boolean enabled) {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "setKeyguardEnabled(" + enabled + ")");
            mExternallyEnabled = enabled;
            if (mInternallyDisabled
                    && enabled
                    && !lockscreenEnforcedByDevicePolicy()) {
                // if keyguard is forcefully disabled internally (by lock screen tile), don't allow
                // it to be enabled externally, unless the device policy manager says so.
                return;
            }

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
                mNeedToReshowWhenReenabled = !isProfileDisablingKeyguard();
                updateInputRestrictedLocked();
                hideLocked();
            } else if (enabled && mNeedToReshowWhenReenabled) {
                // reenabled after previously hidden, reshow
                if (DEBUG) Log.d(TAG, "previously hidden, reshowing, reenabling "
                        + "status bar expansion");
                mNeedToReshowWhenReenabled = false;
                updateInputRestrictedLocked();

                if (mExitSecureCallback != null) {
                    if (DEBUG) Log.d(TAG, "onKeyguardExitResult(false), resetting");
                    try {
                        mExitSecureCallback.onKeyguardExitResult(false);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                    }
                    mExitSecureCallback = null;
                    resetStateLocked();
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
            if (shouldWaitForProvisioning()) {
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
            } else if (!isSecure()) {

                // Keyguard is not secure, no need to do anything, and we don't need to reshow
                // the Keyguard after the client releases the Keyguard lock.
                mExternallyEnabled = true;
                mNeedToReshowWhenReenabled = false;
                updateInputRestricted();
                try {
                    callback.onKeyguardExitResult(true);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
            } else {

                // Since we prevent apps from hiding the Keyguard if we are secure, this should be
                // a no-op as well.
                try {
                    callback.onKeyguardExitResult(false);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onKeyguardExitResult(false)", e);
                }
            }
        }
    }

    /**
     * Is the keyguard currently showing and not being force hidden?
     */
    public boolean isShowingAndNotOccluded() {
        return mShowing && !mOccluded;
    }

    /**
     * Notify us when the keyguard is occluded by another window
     */
    public void setOccluded(boolean isOccluded) {
        if (DEBUG) Log.d(TAG, "setOccluded " + isOccluded);
        mHandler.removeMessages(SET_OCCLUDED);
        Message msg = mHandler.obtainMessage(SET_OCCLUDED, (isOccluded ? 1 : 0), 0);
        mHandler.sendMessage(msg);
    }

    /**
     * Handles SET_OCCLUDED message sent by setOccluded()
     */
    private void handleSetOccluded(boolean isOccluded) {
        synchronized (KeyguardViewMediator.this) {
            if (mHiding && isOccluded) {
                // We're in the process of going away but WindowManager wants to show a
                // SHOW_WHEN_LOCKED activity instead.
                startKeyguardExitAnimation(0, 0);
            }

            if (mOccluded != isOccluded) {
                mOccluded = isOccluded;
                mStatusBarKeyguardViewManager.setOccluded(isOccluded);
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
        return mShowing || mNeedToReshowWhenReenabled;
    }

    private void updateInputRestricted() {
        synchronized (this) {
            updateInputRestrictedLocked();
        }
    }
    private void updateInputRestrictedLocked() {
        boolean inputRestricted = isInputRestricted();
        if (mInputRestricted != inputRestricted) {
            mInputRestricted = inputRestricted;
            int size = mKeyguardStateCallbacks.size();
            for (int i = size - 1; i >= 0; i--) {
                try {
                    mKeyguardStateCallbacks.get(i).onInputRestrictedStateChanged(inputRestricted);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onDeviceProvisioned", e);
                    if (e instanceof DeadObjectException) {
                        mKeyguardStateCallbacks.remove(i);
                    }
                }
            }
        }
    }

    /**
     * Enable the keyguard if the settings are appropriate.
     */
    private void doKeyguardLocked(Bundle options) {
        // if the keyguard is already showing, don't bother
        if (mStatusBarKeyguardViewManager.isShowing()) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because it is already showing");
            resetStateLocked();
            return;
        }

        // Ugly hack to ensure keyguard is not shown on top of the CryptKeeper which prevents
        // a user from being able to decrypt their device.
        if (isCryptKeeperEnabled()) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because CryptKeeper is enabled");
            resetStateLocked();
            return;
        }

        // if the setup wizard hasn't run yet, don't show
        final boolean requireSim = !SystemProperties.getBoolean("keyguard.no_require_sim", false);
        final boolean absent = SubscriptionManager.isValidSubscriptionId(
                mUpdateMonitor.getNextSubIdForState(IccCardConstants.State.ABSENT));
        final boolean disabled = SubscriptionManager.isValidSubscriptionId(
                mUpdateMonitor.getNextSubIdForState(IccCardConstants.State.PERM_DISABLED));
        final boolean lockedOrMissing = mUpdateMonitor.isSimPinSecure()
                || ((absent || disabled) && requireSim);

        if (!lockedOrMissing && shouldWaitForProvisioning()) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because device isn't provisioned"
                    + " and the sim is not locked or missing");
            return;
        }

        // if another app is disabling us, don't show
        if (!mExternallyEnabled && !lockedOrMissing) {
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

        if (isKeyguardDisabled(KeyguardUpdateMonitor.getCurrentUser())
                && !lockedOrMissing) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because lockscreen is off");
            // update state
            setShowingLocked(false);
            updateActivityLockScreenState();
            adjustStatusBarLocked();
            userActivity();
            return;
        }

        if (mLockPatternUtils.checkVoldPassword(KeyguardUpdateMonitor.getCurrentUser())) {
            if (DEBUG) Log.d(TAG, "Not showing lock screen since just decrypted");
            // Without this, settings is not enabled until the lock screen first appears
            setShowingLocked(false);
            hideLocked();
            mUpdateMonitor.reportSuccessfulStrongAuthUnlockAttempt();
            return;
        }

        if (DEBUG) Log.d(TAG, "doKeyguard: showing the lock screen");
        showLocked(options);
    }

    private boolean shouldWaitForProvisioning() {
        return !mUpdateMonitor.isDeviceProvisioned() && !isSecure();
    }

    public boolean lockscreenEnforcedByDevicePolicy() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            return dpm.requireSecureKeyguard();
        }
        return false;
    }

    /**
     * Dismiss the keyguard through the security layers.
     */
    public void handleDismiss() {
        if (mShowing && !mOccluded) {
            mStatusBarKeyguardViewManager.dismiss();
        }
    }

    public void dismiss() {
        mHandler.sendEmptyMessage(DISMISS);
    }

    public void showKeyguard() {
        // This is to prevent left edge from interfering
        // with affordances.
        if (mStatusBar.isAffordanceSwipeInProgress()
                || mStatusBar.getBarState() == StatusBarState.KEYGUARD) {
            return;
        }

        // Disable edge detector once we're back on lockscreen
        try {
            WindowManagerGlobal.getWindowManagerService()
                    .setLiveLockscreenEdgeDetector(false);
        } catch (RemoteException e){
            Log.e(TAG, e.getMessage());
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                // Hide status bar window to avoid flicker,
                // slideNotificationPanelIn will make it visible later.
                mStatusBar.getStatusBarWindow().setVisibility(View.INVISIBLE);
                // Get the keyguard into the correct state by calling mStatusBar.showKeyguard()
                mStatusBar.showKeyguard();
                // Now have the notification panel slid back into view
                mStatusBar.slideNotificationPanelIn();
            }
        });
    }

    /**
     * Send message to keyguard telling it to reset its state.
     * @see #handleReset
     */
    private void resetStateLocked() {
        if (DEBUG) Log.e(TAG, "resetStateLocked");
        Message msg = mHandler.obtainMessage(RESET);
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

    private void notifyStartedGoingToSleep() {
        if (DEBUG) Log.d(TAG, "notifyStartedGoingToSleep");
        mHandler.sendEmptyMessage(NOTIFY_STARTED_GOING_TO_SLEEP);
    }

    private void notifyFinishedGoingToSleep() {
        if (DEBUG) Log.d(TAG, "notifyFinishedGoingToSleep");
        mHandler.sendEmptyMessage(NOTIFY_FINISHED_GOING_TO_SLEEP);
    }

    private void notifyStartedWakingUp() {
        if (DEBUG) Log.d(TAG, "notifyStartedWakingUp");
        mHandler.sendEmptyMessage(NOTIFY_STARTED_WAKING_UP);
    }

    private void notifyScreenOn(IKeyguardDrawnCallback callback) {
        if (DEBUG) Log.d(TAG, "notifyScreenOn");
        Message msg = mHandler.obtainMessage(NOTIFY_SCREEN_TURNING_ON, callback);
        mHandler.sendMessage(msg);
    }

    private void notifyScreenTurnedOn() {
        if (DEBUG) Log.d(TAG, "notifyScreenTurnedOn");
        Message msg = mHandler.obtainMessage(NOTIFY_SCREEN_TURNED_ON);
        mHandler.sendMessage(msg);
    }

    private void notifyScreenTurnedOff() {
        if (DEBUG) Log.d(TAG, "notifyScreenTurnedOff");
        Message msg = mHandler.obtainMessage(NOTIFY_SCREEN_TURNED_OFF);
        mHandler.sendMessage(msg);
    }

    /**
     * Send message to keyguard telling it to show itself
     * @see #handleShow
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
        return mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser())
            || KeyguardUpdateMonitor.getInstance(mContext).isSimPinSecure();
    }

    /**
     * Update the newUserId. Call while holding WindowManagerService lock.
     * NOTE: Should only be called by KeyguardViewMediator in response to the user id changing.
     *
     * @param newUserId The id of the incoming user.
     */
    public void setCurrentUser(int newUserId) {
        KeyguardUpdateMonitor.setCurrentUser(newUserId);
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
            } else if (DISMISS_KEYGUARD_SECURELY_ACTION.equals(intent.getAction())) {
                synchronized (KeyguardViewMediator.this) {
                    dismiss();
                }
            } else if (KEYGUARD_SERVICE_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mKeyguardBound = intent.getBooleanExtra(KEYGUARD_SERVICE_EXTRA_ACTIVE, false);
                if (mKeyguardBound) {
                    mSettingsObserver.observe();
                } else {
                    mSettingsObserver.unobserve();
                }
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                mPhoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (DEBUG) Log.d(TAG, "phone state change, new state: " + mPhoneState);
            }
        }
    };

    public void keyguardDone(boolean authenticated) {
        if (DEBUG) Log.d(TAG, "keyguardDone(" + authenticated +")");
        EventLog.writeEvent(70000, 2);
        Message msg = mHandler.obtainMessage(KEYGUARD_DONE, authenticated ? 1 : 0);
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
                    handleReset();
                    break;
                case VERIFY_UNLOCK:
                    handleVerifyUnlock();
                    break;
                case NOTIFY_STARTED_GOING_TO_SLEEP:
                    handleNotifyStartedGoingToSleep();
                    break;
                case NOTIFY_FINISHED_GOING_TO_SLEEP:
                    handleNotifyFinishedGoingToSleep();
                    break;
                case NOTIFY_SCREEN_TURNING_ON:
                    handleNotifyScreenTurningOn((IKeyguardDrawnCallback) msg.obj);
                    break;
                case NOTIFY_SCREEN_TURNED_ON:
                    handleNotifyScreenTurnedOn();
                    break;
                case NOTIFY_SCREEN_TURNED_OFF:
                    handleNotifyScreenTurnedOff();
                    break;
                case NOTIFY_STARTED_WAKING_UP:
                    handleNotifyStartedWakingUp();
                    break;
                case KEYGUARD_DONE:
                    handleKeyguardDone(msg.arg1 != 0);
                    break;
                case KEYGUARD_DONE_DRAWING:
                    handleKeyguardDoneDrawing();
                    break;
                case SET_OCCLUDED:
                    handleSetOccluded(msg.arg1 != 0);
                    break;
                case KEYGUARD_TIMEOUT:
                    synchronized (KeyguardViewMediator.this) {
                        doKeyguardLocked((Bundle) msg.obj);
                    }
                    break;
                case DISMISS:
                    handleDismiss();
                    break;
                case START_KEYGUARD_EXIT_ANIM:
                    StartKeyguardExitAnimParams params = (StartKeyguardExitAnimParams) msg.obj;
                    handleStartKeyguardExitAnimation(params.startTime, params.fadeoutDuration);
                    break;
                case KEYGUARD_DONE_PENDING_TIMEOUT:
                    Log.w(TAG, "Timeout while waiting for activity drawn!");
                    // Fall through.
                case ON_ACTIVITY_DRAWN:
                    handleOnActivityDrawn();
                    break;
            }
        }
    };

    /**
     * @see #keyguardDone
     * @see #KEYGUARD_DONE
     */
    private void handleKeyguardDone(boolean authenticated) {
        if (DEBUG) Log.d(TAG, "handleKeyguardDone");
        synchronized (this) {
            resetKeyguardDonePendingLocked();
        }

        if (authenticated) {
            mUpdateMonitor.clearFailedUnlockAttempts();
        }
        mUpdateMonitor.clearFingerprintRecognized();

        if (mGoingToSleep) {
            Log.i(TAG, "Device is going to sleep, aborting keyguardDone");
            return;
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
                updateInputRestricted();
            }
        }

        handleHide();
    }

    private void sendUserPresentBroadcast() {
        synchronized (this) {
            if (mBootCompleted) {
                final UserHandle currentUser = new UserHandle(KeyguardUpdateMonitor.getCurrentUser());
                final UserManager um = (UserManager) mContext.getSystemService(
                        Context.USER_SERVICE);
                List <UserInfo> userHandles = um.getProfiles(currentUser.getIdentifier());
                for (UserInfo ui : userHandles) {
                    mContext.sendBroadcastAsUser(USER_PRESENT_INTENT, ui.getUserHandle());
                }
            } else {
                mBootSendUserPresent = true;
            }
        }
    }

    /**
     * @see #keyguardDone
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
        playSound(locked ? mLockSoundId : mUnlockSoundId);
    }

    private void playSound(int soundId) {
        if (soundId == 0) return;
        if (mInternallyDisabled) {
            Log.d(TAG, "suppressing lock screen sounds because it is disabled");
            return;
        }
        final ContentResolver cr = mContext.getContentResolver();
        if (Settings.System.getInt(cr, Settings.System.LOCKSCREEN_SOUNDS_ENABLED, 1) == 1) {

            mLockSounds.stop(mLockSoundStreamId);
            // Init mAudioManager
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (mAudioManager == null) return;
                mUiSoundsStreamType = mAudioManager.getUiSoundsStreamType();
            }
            // If the stream is muted, don't play the sound
            if (mAudioManager.isStreamMute(mUiSoundsStreamType)) return;

            mLockSoundStreamId = mLockSounds.play(soundId,
                    mLockSoundVolume, mLockSoundVolume, 1/*priortiy*/, 0/*loop*/, 1.0f/*rate*/);
        }
    }

    private void playTrustedSound() {
        playSound(mTrustedSoundId);
    }

    private void updateActivityLockScreenState() {
        try {
            ActivityManagerNative.getDefault().setLockScreenShown(mShowing && !mOccluded);
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

            setShowingLocked(true);
            mStatusBarKeyguardViewManager.show(options);
            mHiding = false;
            mWakeAndUnlocking = false;
            resetKeyguardDonePendingLocked();
            mHideAnimationRun = false;
            updateActivityLockScreenState();
            adjustStatusBarLocked();
            userActivity();

            mShowKeyguardWakeLock.release();
        }
        mKeyguardDisplayManager.show();
    }

    private final Runnable mKeyguardGoingAwayRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mStatusBarKeyguardViewManager.keyguardGoingAway();

                // Don't actually hide the Keyguard at the moment, wait for window
                // manager until it tells us it's safe to do so with
                // startKeyguardExitAnimation.
                ActivityManagerNative.getDefault().keyguardGoingAway(
                        mStatusBarKeyguardViewManager.shouldDisableWindowAnimationsForUnlock()
                                || mWakeAndUnlocking,
                        mStatusBarKeyguardViewManager.isGoingToNotificationShade(),
                        mStatusBarKeyguardViewManager.isKeyguardShowingMedia());
            } catch (RemoteException e) {
                Log.e(TAG, "Error while calling WindowManager", e);
            }
        }
    };

    /**
     * Handle message sent by {@link #hideLocked()}
     * @see #HIDE
     */
    private void handleHide() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleHide");

            mHiding = true;
            if (mShowing && !mOccluded) {
                if (!mHideAnimationRun) {
                    mStatusBarKeyguardViewManager.startPreHideAnimation(mKeyguardGoingAwayRunnable);
                } else {
                    mKeyguardGoingAwayRunnable.run();
                }
            } else {

                // Don't try to rely on WindowManager - if Keyguard wasn't showing, window
                // manager won't start the exit animation.
                handleStartKeyguardExitAnimation(
                        SystemClock.uptimeMillis() + mHideAnimation.getStartOffset(),
                        mHideAnimation.getDuration());
            }
        }
    }

    private void handleOnActivityDrawn() {
        if (DEBUG) Log.d(TAG, "handleOnActivityDrawn: mKeyguardDonePending=" + mKeyguardDonePending);
        if (mKeyguardDonePending) {
            mStatusBarKeyguardViewManager.onActivityDrawn();
        }
    }

    private void handleStartKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        synchronized (KeyguardViewMediator.this) {

            if (!mHiding) {
                return;
            }
            mHiding = false;

            if (mWakeAndUnlocking && mDrawnCallback != null) {

                // Hack level over 9000: To speed up wake-and-unlock sequence, force it to report
                // the next draw from here so we don't have to wait for window manager to signal
                // this to our ViewRootImpl.
                mStatusBarKeyguardViewManager.getViewRootImpl().setReportNextDraw();
                notifyDrawn(mDrawnCallback);
            }

            // only play "unlock" noises if not on a call (since the incall UI
            // disables the keyguard)
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(mPhoneState)) {
                if (mShowing && mDeviceInteractive) {
                    playSounds(false);
                }
            }

            setShowingLocked(false);
            mStatusBarKeyguardViewManager.hide(startTime, fadeoutDuration);
            resetKeyguardDonePendingLocked();
            mHideAnimationRun = false;
            updateActivityLockScreenState();
            adjustStatusBarLocked();
            sendUserPresentBroadcast();
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
                flags |= StatusBarManager.DISABLE_SEARCH;
            }
            if (isShowingAndNotOccluded()) {
                flags |= StatusBarManager.DISABLE_HOME;
            }

            if (DEBUG) {
                Log.d(TAG, "adjustStatusBarLocked: mShowing=" + mShowing + " mOccluded=" + mOccluded
                        + " isSecure=" + isSecure() + " --> flags=0x" + Integer.toHexString(flags));
            }

            if (!(mContext instanceof Activity)) {
                mStatusBarManager.disable(flags);
            }
        }
    }

    /**
     * Handle message sent by {@link #resetStateLocked}
     * @see #RESET
     */
    private void handleReset() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleReset");
            mStatusBarKeyguardViewManager.reset(false);
        }
    }

    /**
     * Handle message sent by {@link #verifyUnlock}
     * @see #VERIFY_UNLOCK
     */
    private void handleVerifyUnlock() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleVerifyUnlock");
            setShowingLocked(true);
            mStatusBarKeyguardViewManager.verifyUnlock();
            updateActivityLockScreenState();
        }
    }

    private void handleNotifyStartedGoingToSleep() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyStartedGoingToSleep");
            mStatusBarKeyguardViewManager.onStartedGoingToSleep();
        }
    }

    /**
     * Handle message sent by {@link #notifyFinishedGoingToSleep()}
     * @see #NOTIFY_FINISHED_GOING_TO_SLEEP
     */
    private void handleNotifyFinishedGoingToSleep() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyFinishedGoingToSleep");
            mStatusBarKeyguardViewManager.onFinishedGoingToSleep();
        }
    }

    private void handleNotifyStartedWakingUp() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyWakingUp");
            mStatusBarKeyguardViewManager.onStartedWakingUp();
        }
    }

    private void handleNotifyScreenTurningOn(IKeyguardDrawnCallback callback) {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenTurningOn");
            mStatusBarKeyguardViewManager.onScreenTurningOn();
            if (callback != null) {
                if (mWakeAndUnlocking) {
                    mDrawnCallback = callback;
                } else {
                    notifyDrawn(callback);
                }
            }
        }
    }

    private void handleNotifyScreenTurnedOn() {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenTurnedOn");
            mStatusBarKeyguardViewManager.onScreenTurnedOn();
        }
    }

    private void handleNotifyScreenTurnedOff() {
        synchronized (this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenTurnedOff");
            mStatusBarKeyguardViewManager.onScreenTurnedOff();
            mWakeAndUnlocking = false;
        }
    }

    private void notifyDrawn(final IKeyguardDrawnCallback callback) {
        try {
            callback.onDrawn();
        } catch (RemoteException e) {
            Slog.w(TAG, "Exception calling onDrawn():", e);
        }
    }

    private void resetKeyguardDonePendingLocked() {
        mKeyguardDonePending = false;
        mHandler.removeMessages(KEYGUARD_DONE_PENDING_TIMEOUT);
    }

    public void onBootCompleted() {
        mUpdateMonitor.dispatchBootCompleted();
        synchronized (this) {
            mBootCompleted = true;
            if (mBootSendUserPresent) {
                sendUserPresentBroadcast();
            }
        }
    }

    public void onWakeAndUnlocking() {
        mWakeAndUnlocking = true;
        keyguardDone(true /* authenticated */);
    }

    public StatusBarKeyguardViewManager registerStatusBar(PhoneStatusBar phoneStatusBar,
            ViewGroup container, StatusBarWindowManager statusBarWindowManager,
            ScrimController scrimController,
            FingerprintUnlockController fingerprintUnlockController) {
        mStatusBarKeyguardViewManager.registerStatusBar(phoneStatusBar, container,
                statusBarWindowManager, scrimController, fingerprintUnlockController);
        mStatusBar = phoneStatusBar;
        return mStatusBarKeyguardViewManager;
    }

    public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
        Message msg = mHandler.obtainMessage(START_KEYGUARD_EXIT_ANIM,
                new StartKeyguardExitAnimParams(startTime, fadeoutDuration));
        mHandler.sendMessage(msg);
    }

    public void onActivityDrawn() {
        mHandler.sendEmptyMessage(ON_ACTIVITY_DRAWN);
    }
    public ViewMediatorCallback getViewMediatorCallback() {
        return mViewMediatorCallback;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.print("  mSystemReady: "); pw.println(mSystemReady);
        pw.print("  mBootCompleted: "); pw.println(mBootCompleted);
        pw.print("  mBootSendUserPresent: "); pw.println(mBootSendUserPresent);
        pw.print("  mExternallyEnabled: "); pw.println(mExternallyEnabled);
        pw.print("  mNeedToReshowWhenReenabled: "); pw.println(mNeedToReshowWhenReenabled);
        pw.print("  mShowing: "); pw.println(mShowing);
        pw.print("  mInputRestricted: "); pw.println(mInputRestricted);
        pw.print("  mOccluded: "); pw.println(mOccluded);
        pw.print("  mDelayedShowingSequence: "); pw.println(mDelayedShowingSequence);
        pw.print("  mExitSecureCallback: "); pw.println(mExitSecureCallback);
        pw.print("  mDeviceInteractive: "); pw.println(mDeviceInteractive);
        pw.print("  mGoingToSleep: "); pw.println(mGoingToSleep);
        pw.print("  mHiding: "); pw.println(mHiding);
        pw.print("  mWaitingUntilKeyguardVisible: "); pw.println(mWaitingUntilKeyguardVisible);
        pw.print("  mKeyguardDonePending: "); pw.println(mKeyguardDonePending);
        pw.print("  mHideAnimationRun: "); pw.println(mHideAnimationRun);
        pw.print("  mPendingReset: "); pw.println(mPendingReset);
        pw.print("  mPendingLock: "); pw.println(mPendingLock);
        pw.print("  mWakeAndUnlocking: "); pw.println(mWakeAndUnlocking);
        pw.print("  mDrawnCallback: "); pw.println(mDrawnCallback);
    }

    private static class StartKeyguardExitAnimParams {

        long startTime;
        long fadeoutDuration;

        private StartKeyguardExitAnimParams(long startTime, long fadeoutDuration) {
            this.startTime = startTime;
            this.fadeoutDuration = fadeoutDuration;
        }
    }

    private void setShowingLocked(boolean showing) {
        if (showing != mShowing) {
            mShowing = showing;
            int size = mKeyguardStateCallbacks.size();
            for (int i = size - 1; i >= 0; i--) {
                try {
                    mKeyguardStateCallbacks.get(i).onShowingStateChanged(showing);
                } catch (RemoteException e) {
                    Slog.w(TAG, "Failed to call onShowingStateChanged", e);
                    if (e instanceof DeadObjectException) {
                        mKeyguardStateCallbacks.remove(i);
                    }
                }
            }
            updateInputRestrictedLocked();
            mTrustManager.reportKeyguardShowingChanged();
        }
    }

    public void addStateMonitorCallback(IKeyguardStateCallback callback) {
        synchronized (this) {
            mKeyguardStateCallbacks.add(callback);
            try {
                callback.onSimSecureStateChanged(mUpdateMonitor.isSimPinSecure());
                callback.onShowingStateChanged(mShowing);
                callback.onInputRestrictedStateChanged(mInputRestricted);
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call onShowingStateChanged or onSimSecureStateChanged or onInputRestrictedStateChanged", e);
            }
        }
    }
}
