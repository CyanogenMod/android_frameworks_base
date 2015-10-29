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

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.app.trust.TrustManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
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
import android.os.Vibrator;
import android.provider.Settings;
import android.service.fingerprint.FingerprintManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Log;
import android.util.Slog;
import android.view.IWindowManager;
import android.view.ViewGroup;
import android.view.WindowManagerGlobal;
import android.view.WindowManagerPolicy;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import com.cyanogenmod.keyguard.cmstats.KeyguardStats;
import cyanogenmod.app.Profile;
import cyanogenmod.app.ProfileManager;

import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardShowCallback;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardConstants;
import com.android.keyguard.KeyguardDisplayManager;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.MultiUserAvatarCache;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.SystemUI;
import com.android.systemui.qs.tiles.LockscreenToggleTile;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.phone.StatusBarWindowManager;

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

    final static boolean DEBUG = false;
    private static final boolean DEBUG_SIM_STATES = KeyguardConstants.DEBUG_SIM_STATES;
    private final static boolean DBG_WAKE = false;
    private final static boolean DBG_FINGERPRINT = false;

    private final static String TAG = "KeyguardViewMediator";

    private static final String DELAYED_KEYGUARD_ACTION =
        "com.android.internal.policy.impl.PhoneWindowManager.DELAYED_KEYGUARD";

    private static final String DISMISS_KEYGUARD_SECURELY_ACTION =
        "com.android.keyguard.action.DISMISS_KEYGUARD_SECURELY";

    private static final String KEYGUARD_SERVICE_ACTION_STATE_CHANGE =
            "com.android.internal.action.KEYGUARD_SERVICE_STATE_CHANGED";
    private static final String KEYGUARD_SERVICE_EXTRA_ACTIVE = "active";

    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String CRYPT_KEEPER_ACTIVITY = SETTINGS_PACKAGE + ".CryptKeeper";

    private static final AudioAttributes VIBRATION_ATTRIBUTES = new AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build();

    private static final String DECRYPT_STATE = "trigger_restart_framework";

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
    private static final int SET_OCCLUDED = 12;
    private static final int KEYGUARD_TIMEOUT = 13;
    private static final int DISMISS = 17;
    private static final int START_KEYGUARD_EXIT_ANIM = 18;
    private static final int ON_ACTIVITY_DRAWN = 19;
    private static final int KEYGUARD_DONE_PENDING_TIMEOUT = 20;
    private static final int KEYGUARD_FINGERPRINT_AUTH = 21;

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
     * How long we should wait after a failed fingerprint read before restarting
     * listening for new fingerprints. Buffer useful to not process two reported attempts
     * before the user has a change to lift their finger off the sensor (if it is sensitive).
     */
    private static final int FINGERPRINT_FAILED_RESTART_DELAY = 1000;

    /**
     * Secure setting whether analytics are collected on the keyguard.
     */
    private static final String KEYGUARD_ANALYTICS_SETTING = "keyguard_analytics";

    /** The stream type that the lock sounds are tied to. */
    private int mMasterStreamType;

    private AlarmManager mAlarmManager;
    private AudioManager mAudioManager;
    private StatusBarManager mStatusBarManager;
    private boolean mSwitchingUser;
    private ProfileManager mProfileManager;
    private boolean mSystemReady;
    private boolean mBootCompleted;
    private boolean mBootSendUserPresent;

    // Whether the next call to playSounds() should be skipped.  Defaults to
    // true because the first lock (on boot) should be silent.
    private boolean mSuppressNextLockSound = true;


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

    // the properties of the keyguard

    private KeyguardUpdateMonitor mUpdateMonitor;

    private boolean mScreenOn;

    private boolean mDismissSecurelyOnScreenOn = false;

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
     * Whether we are bound to the service delegate
     */
    private boolean mKeyguardBound;

    /**
     * Whether to immediately show the bouncer when the screen turns on
     */
    private boolean mSkipToBouncer;

    // whether to try to authenticate when the sensor becomes ready
    private boolean mStartFingerAuthOnIdle;
    // flag when the fingerprint sensor was responsible for waking the device up
    private boolean mFingerprintWakeUnlock;
    // flag whether the screen actually finished turning on the screen
    private boolean mFingerTurnedScreenOn;
    // whether a call to FingerprintManager.authenticate() is pending
    private boolean mFingerAuthenticating;

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

    private boolean mCryptKeeperEnabled = true;

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
                // When we switch users we want to bring the new user to the biometric unlock even
                // if the current user has gone to the backup.
                KeyguardUpdateMonitor.getInstance(mContext).setAlternateUnlockEnabled(true);
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
        public void onUserRemoved(int userId) {
            mLockPatternUtils.removeUser(userId);
            MultiUserAvatarCache.getInstance().clear(userId);
        }

        @Override
        public void onUserInfoChanged(int userId) {
            MultiUserAvatarCache.getInstance().clear(userId);
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            synchronized (KeyguardViewMediator.this) {
                if (TelephonyManager.CALL_STATE_IDLE == phoneState  // call ending
                        && mExternallyEnabled) {                // not disabled by any app
                    if (!mScreenOn) {
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
        }

        @Override
        public void onClockVisibilityChanged() {
            adjustStatusBarLocked();
        }

        @Override
        public void onDeviceProvisioned() {
            sendUserPresentBroadcast();
            updateInputRestricted();
        }

        @Override
        public void onSimStateChanged(int subId, int slotId, IccCardConstants.State simState) {
            if (DEBUG) Log.d(TAG, "onSimStateChangedUsingSubId: " + simState + ", subId=" + subId);

            try {
                int size = mKeyguardStateCallbacks.size();
                boolean simPinSecure = mUpdateMonitor.isSimPinSecure();
                for (int i = 0; i < size; i++) {
                    mKeyguardStateCallbacks.get(i).onSimSecureStateChanged(simPinSecure);
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call onSimSecureStateChanged", e);
            }

            switch (simState) {
                case NOT_READY:
                case ABSENT:
                    // only force lock screen in case of missing sim if user hasn't
                    // gone through setup wizard
                    synchronized (this) {
                        if (shouldWaitForProvisioning()) {
                            if (!isShowing()) {
                                if (DEBUG) Log.d(TAG, "ICC_ABSENT isn't showing,"
                                        + " we need to show the keyguard since the "
                                        + "device isn't provisioned yet.");
                                doKeyguardLocked(null);
                            } else {
                                resetStateLocked();
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
                            resetStateLocked();
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
                            resetStateLocked();
                        }
                    }
                    break;
                case READY:
                    synchronized (this) {
                        if (mInternallyDisabled) {
                            hideLocked();
                        } else if (isShowing()) {
                            resetStateLocked();
                        }
                    }
                    break;
                default:
                    if (DEBUG_SIM_STATES) Log.v(TAG, "Ignoring state: " + simState);
                    break;
            }
        }

        @Override
        public void onFingerprintStateChange(int state) {
            synchronized (KeyguardViewMediator.this) {
                if (DBG_FINGERPRINT) {
                    Log.i(TAG, "onFingerprintStateChange(state=" + state + ")");
                }
                if (!isShowingAndNotOccluded()) {
                    if (DBG_FINGERPRINT) {
                        Log.d(TAG, "kg not showing.");
                    }
                    return;
                }

                if (state == FingerprintManager.STATE_IDLE && mStartFingerAuthOnIdle) {
                    if (DBG_FINGERPRINT) {
                        Log.w(TAG, "state changed to idle and we requested an auth on idle.");
                    }
                    mStartFingerAuthOnIdle = false;
                    mHandler.obtainMessage(KEYGUARD_FINGERPRINT_AUTH, 1, 0).sendToTarget();
                }
            }
        }

        public void onFingerprintRecognized(int userId) {
            synchronized (KeyguardViewMediator.this) {
                mFingerAuthenticating = false;
                if (DBG_FINGERPRINT) {
                    Log.i(TAG, "onFingerprintRecognized(userId=" + userId + ")");
                }

                vibrateFingerprintSuccess();

                final boolean screenOn = mPM.isInteractive();

                if (!isShowingAndNotOccluded()) {
                    // fingerprint was recognized before keyguard has come back up fully
                    // cancel the pending keyguard call and wake up the device if necessary
                    if (DBG_FINGERPRINT) {
                        Log.w(TAG, "fingerprint recognized but kg not showing.");
                    }
                    cancelDoKeyguardLaterLocked();
                    if (!screenOn) {
                        mPM.wakeUp(SystemClock.uptimeMillis());
                    }
                    return;
                }

                mFingerprintWakeUnlock = !screenOn;
                hideLocked();
            }
        }

        @Override
        public void onFingerprintAttemptFailed(boolean error, int errorCode) {
            synchronized (KeyguardViewMediator.this) {
                mFingerAuthenticating = false;
                mStartFingerAuthOnIdle = false;

                final boolean screenOn = mPM.isInteractive();

                if (error) {
                    switch (errorCode) {
                        case FingerprintManager.FINGERPRINT_ERROR_HW_UNAVAILABLE:
                            // service may not be available.
                            setupFingerprint(screenOn);
                            return;
                        default:
                            Log.e(TAG, "FingerprintManager reported unhandled error: " + errorCode);
                            return;
                    }
                }

                if (!screenOn) { // mScreenOn isn't as reliable
                    if (DBG_FINGERPRINT) {
                        Log.i(TAG, "onFingerprintAttemptFailed() and screen is off");
                    }

                    if (!mUpdateMonitor.isMaxFingerprintAttemptsReached()
                            || mUpdateMonitor.isOnLastFingerprintAttempt()) {
                        vibrateFingerprintFailure(mUpdateMonitor.isMaxFingerprintAttemptsReached());

                        mSkipToBouncer = true;
                        mPM.wakeUp(SystemClock.uptimeMillis());
                    }

                } else if (mUpdateMonitor.isMaxFingerprintAttemptsReached()) {
                    if (DBG_FINGERPRINT) {
                        Log.i(TAG, "onFingerprintAttemptFailed() LIMIT REACHED and screen is on");
                    }
                    if (mUpdateMonitor.isOnLastFingerprintAttempt()) {
                        // only vibrate if we reached the max limit. if we are going over don't.
                        vibrateFingerprintFailure(mUpdateMonitor.isMaxFingerprintAttemptsReached());

                        if (!mStatusBarKeyguardViewManager.isBouncerShowing() && !mSkipToBouncer) {
                            mStatusBarKeyguardViewManager.showBouncerHideNotifications();
                        }
                    }
                } else {
                    // screen on state, restart fingerprint auth
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(KEYGUARD_FINGERPRINT_AUTH, 1, 0),
                            FINGERPRINT_FAILED_RESTART_DELAY);

                    vibrateFingerprintFailure(mUpdateMonitor.isMaxFingerprintAttemptsReached());
                }
                userActivity();
            }
        }

        private void vibrateFingerprintSuccess() {
            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                v.vibrate(FingerprintManager.FINGERPRINT_EVENT_VIBRATE_DURATION,
                        VIBRATION_ATTRIBUTES);
            }
        }

        private void vibrateFingerprintFailure(boolean graveWarning) {
            Vibrator v = (Vibrator)
                    mContext.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                final long[] pattern = graveWarning
                        ? new long[]{0, 75, 100, 75, 100, 75, 100, 75}
                        : new long[]{0, 50, 100, 50};
                v.vibrate(pattern, -1, VIBRATION_ATTRIBUTES);
            }
        }

        private void setupFingerprint(boolean screenOn) {
            if (!isFingerprintActive()) {
                return;
            }
            FingerprintManager fpm = (FingerprintManager)
                    mContext.getSystemService(Context.FINGERPRINT_SERVICE);
            if (screenOn) {
                synchronized (KeyguardViewMediator.this) {
                    if (!mPM.isInteractive()) {
                        // if keyguard was restarted while screen is off we get in this false state
                        if (DBG_FINGERPRINT) {
                            Log.e(TAG, "screen reported on but power mgr says it's off. ignoring");
                        }
                        mUpdateMonitor.setFingerprintListening(true);
                        if (fpm != null) {
                            // we're probably in a strange state here. reset everything.
                            fpm.cancel();
                            fpm.setWakeup(false);
                            fpm.setWakeup(true);
                        }
                        return;
                    }
                    if (fpm != null) {
                        fpm.setWakeup(false);
                    }

                    if (DBG_FINGERPRINT) {
                        Log.i(TAG, "onScreenTurnedOn() called" +
                                        ", mFingerprintWakeUnlock=" + mFingerprintWakeUnlock +
                                        ", mFingerTurnedScreenOn=" + mFingerTurnedScreenOn +
                                        ", mShowing= " + mShowing
                        );
                    }
                    if (mFingerTurnedScreenOn || mFingerprintWakeUnlock) {
                        mFingerTurnedScreenOn = false;
                        mFingerprintWakeUnlock = false;
                    } else if (isShowingAndNotOccluded()) {
                        mUpdateMonitor.setFingerprintListening(true);

                        if (mSkipToBouncer) {
                            // don't immediately start capturing fingerprint yet
                            mHandler.sendMessageDelayed(
                                    mHandler.obtainMessage(KEYGUARD_FINGERPRINT_AUTH, 1, 0),
                                    FINGERPRINT_FAILED_RESTART_DELAY);
                        } else {
                            mHandler.sendMessage(
                                    mHandler.obtainMessage(KEYGUARD_FINGERPRINT_AUTH, 1, 0));
                        }
                    }
                }
            } else {
                synchronized (KeyguardViewMediator.this) {
                    mUpdateMonitor.clearFingerprintRecognized();
                    mHandler.removeMessages(KEYGUARD_FINGERPRINT_AUTH);
                    mHandler.sendMessage(mHandler.obtainMessage(KEYGUARD_FINGERPRINT_AUTH, 0, 0));
                    mUpdateMonitor.setFingerprintListening(true);
                    mFingerprintWakeUnlock = false;
                    mFingerTurnedScreenOn = false;
                    mStartFingerAuthOnIdle = false;
                    mFingerAuthenticating = false;
                    if (fpm != null) {
                        fpm.setWakeup(true);
                    }
                }
            }
        }

        @Override
        public void onScreenTurnedOff(int why) {
            setupFingerprint(false);
        }

        @Override
        public void onScreenTurnedOn() {
            setupFingerprint(true);
            synchronized (KeyguardViewMediator.this) {
                if (mSkipToBouncer) {
                    mSkipToBouncer = false;
                    if (!mStatusBarKeyguardViewManager.isBouncerShowing()) {
                        mStatusBarKeyguardViewManager.showBouncerHideNotifications();
                    }
                }
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            super.onKeyguardVisibilityChanged(showing);
            if (isFingerprintActive()) {
                synchronized (KeyguardViewMediator.this) {
                    if (!showing) {
                        stopAuthenticatingFingerprint();
                    }
                    mUpdateMonitor.setFingerprintListening(showing);
                }
            }
        }
    };

    ViewMediatorCallback mViewMediatorCallback = new ViewMediatorCallback() {

        public void userActivity() {
            KeyguardViewMediator.this.userActivity();
        }

        public void keyguardDone(boolean authenticated) {
            if (!mKeyguardDonePending) {
                KeyguardViewMediator.this.keyguardDone(authenticated, true);
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
        public void onUserActivityTimeoutChanged() {
            mStatusBarKeyguardViewManager.updateUserActivityTimeout();
        }

        @Override
        public void keyguardDonePending() {
            mKeyguardDonePending = true;
            mHideAnimationRun = true;
            mStatusBarKeyguardViewManager.startPreHideAnimation(null /* finishRunnable */);
            mHandler.sendEmptyMessageDelayed(KEYGUARD_DONE_PENDING_TIMEOUT,
                    KEYGUARD_DONE_PENDING_TIMEOUT_MS);
        }

        @Override
        public void keyguardGone() {
            mKeyguardDisplayManager.hide();
            if (isFingerprintActive()) {
                synchronized (KeyguardViewMediator.this) {
                    if (mFingerprintWakeUnlock) {
                        if (DBG_FINGERPRINT) {
                            Log.w(TAG, "keyguard gone; waking up device due to fingerprint");
                        }
                        mFingerprintWakeUnlock = false;
                        mFingerTurnedScreenOn = true;

                        mPM.wakeUp(SystemClock.uptimeMillis());
                    }
                }
            }
        }

        @Override
        public void readyForKeyguardDone() {
            if (mKeyguardDonePending) {
                // Somebody has called keyguardDonePending before, which means that we are
                // authenticated
                KeyguardViewMediator.this.keyguardDone(true /* authenticated */, true /* wakeUp */);
            }
        }

        @Override
        public void playTrustedSound() {
            KeyguardViewMediator.this.playTrustedSound();
        }

        @Override
        public boolean isInputRestricted() {
            return KeyguardViewMediator.this.isInputRestricted();
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
        mProfileManager = ProfileManager.getInstance(mContext);
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(DELAYED_KEYGUARD_ACTION));
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(DISMISS_KEYGUARD_SECURELY_ACTION),
                android.Manifest.permission.CONTROL_KEYGUARD, null);
        mContext.registerReceiver(mBroadcastReceiver, new IntentFilter(KEYGUARD_SERVICE_ACTION_STATE_CHANGE),
                android.Manifest.permission.CONTROL_KEYGUARD, null);

        mKeyguardDisplayManager = new KeyguardDisplayManager(mContext);

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);

        mLockPatternUtils = new LockPatternUtils(mContext);
        mLockPatternUtils.setCurrentUser(ActivityManager.getCurrentUser());

        // Assume keyguard is showing (unless it's disabled) until we know for sure...
        setShowingLocked(!shouldWaitForProvisioning() && !mLockPatternUtils.isLockScreenDisabled());
        mTrustManager.reportKeyguardShowingChanged();

        mStatusBarKeyguardViewManager = new StatusBarKeyguardViewManager(mContext,
                mViewMediatorCallback, mLockPatternUtils);
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
     * @param why either {@link android.view.WindowManagerPolicy#OFF_BECAUSE_OF_USER} or
     *   {@link android.view.WindowManagerPolicy#OFF_BECAUSE_OF_TIMEOUT}.
     */
    public void onScreenTurnedOff(int why) {
        synchronized (this) {
            mScreenOn = false;
            if (DEBUG) Log.d(TAG, "onScreenTurnedOff(" + why + ")");

            resetKeyguardDonePendingLocked();
            mHideAnimationRun = false;

            // Lock immediately based on setting if secure (user has a pin/pattern/password).
            // This also "locks" the device when not secure to provide easy access to the
            // camera while preventing unwanted input.
            final boolean lockImmediately =
                mLockPatternUtils.getPowerButtonInstantlyLocks() || !mLockPatternUtils.isSecure()
                    || isFingerprintActive();

            notifyScreenOffLocked();

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
                resetStateLocked();
            } else if (why == WindowManagerPolicy.OFF_BECAUSE_OF_TIMEOUT
                   || (why == WindowManagerPolicy.OFF_BECAUSE_OF_USER && !lockImmediately)) {
                doKeyguardLaterLocked();
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

        // From DevicePolicyAdmin
        final long policyTimeout = mLockPatternUtils.getDevicePolicyManager()
                .getMaximumTimeToLock(null, mLockPatternUtils.getCurrentUser());

        long timeout;
        if (policyTimeout > 0) {
            // policy in effect. Make sure we don't go beyond policy limit.
            displayTimeout = Math.max(displayTimeout, 0); // ignore negative values
            timeout = Math.min(policyTimeout - displayTimeout, lockAfterTimeout);
        } else {
            timeout = lockAfterTimeout;
        }

        if (timeout <= 0) {
            // Lock now
            mSuppressNextLockSound = true;
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
            if (mDismissSecurelyOnScreenOn) {
                mDismissSecurelyOnScreenOn = false;
                dismiss();
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).dispatchScreenTurnedOn();
        maybeSendUserPresentBroadcast();
    }

    private void maybeSendUserPresentBroadcast() {
        if (mSystemReady && isKeyguardDisabled()) {
            // Lock screen is disabled because the user has set the preference to "None".
            // In this case, send out ACTION_USER_PRESENT here instead of in
            // handleKeyguardDone()
            sendUserPresentBroadcast();
        }
    }

    private boolean isKeyguardDisabled() {
        if (!mExternallyEnabled) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled externally");
            return true;
        }
        if (mInternallyDisabled) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled internally");
            return true;
        }
        if (mLockPatternUtils.isThirdPartyKeyguardEnabled()) {
            // We don't want the stock keyguard to do anything when a third party component is
            // enabled.  The view mediator will still show take care of showing the third party
            // component as usual.
            if (DEBUG) {
                Log.d(TAG, "iisKeyguardDisabled: keyguard is disabled by third party keyguard");
            }
            return true;
        }
        if (mLockPatternUtils.isLockScreenDisabled()) {
            if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled by setting");
            return true;
        }
        Profile profile = mProfileManager.getActiveProfile();
        if (profile != null) {
            if (profile.getScreenLockMode().getValue() == Profile.LockMode.DISABLE) {
                if (DEBUG) Log.d(TAG, "isKeyguardDisabled: keyguard is disabled by profile");
                return true;
            }
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

    public boolean isKeyguardBound() {
        return mKeyguardBound;
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
     * Set the internal keyguard enabled state. This allows SystemUI to disable the lockscreen,
     * overriding any apps.
     * @param enabled
     */
    public void setKeyguardEnabledInternal(boolean enabled) {
        mInternallyDisabled = !enabled;
        setKeyguardEnabled(enabled);
        if (mInternallyDisabled) {
            mNeedToReshowWhenReenabled = false;
        }
    }

    public boolean getKeyguardEnabledInternal() {
        return !mInternallyDisabled;
    }

    private boolean isProfileDisablingKeyguard() {
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

    public boolean isOccluded() {
        return mOccluded;
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
            if (mOccluded != isOccluded) {
                mOccluded = isOccluded;
                mStatusBarKeyguardViewManager.setOccluded(isOccluded);
                updateActivityLockScreenState();
                adjustStatusBarLocked();

                mHandler.obtainMessage(KEYGUARD_FINGERPRINT_AUTH,
                        isOccluded ? 0 : 1, 0).sendToTarget();
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
        return mShowing || mNeedToReshowWhenReenabled || shouldWaitForProvisioning();
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
            try {
                int size = mKeyguardStateCallbacks.size();
                for (int i = 0; i < size; i++) {
                    mKeyguardStateCallbacks.get(i).onInputRestrictedStateChanged(inputRestricted);
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call onDeviceProvisioned", e);
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
        final boolean requireSim = !SystemProperties.getBoolean("keyguard.no_require_sim",
                false);
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

        if (isKeyguardDisabled() && !lockedOrMissing) {
            if (DEBUG) Log.d(TAG, "doKeyguard: not showing because lockscreen is off");
            // update state
            setShowingLocked(false);
            updateActivityLockScreenState();
            adjustStatusBarLocked();
            userActivity();
            if (isThirdPartyKeyguardEnabled()) showThirdPartyKeyguard(false);
            return;
        }

        if (mLockPatternUtils.checkVoldPassword()) {
            if (DEBUG) Log.d(TAG, "Not showing lock screen since just decrypted");
            // Without this, settings is not enabled until the lock screen first appears
            setShowingLocked(false);
            hideLocked();
            return;
        }

        if (DEBUG) Log.d(TAG, "doKeyguard: showing the lock screen");
        showLocked(options);
    }

    private boolean shouldWaitForProvisioning() {
        return !mUpdateMonitor.isDeviceProvisioned() && !isSecure();
    }

    private boolean isSimLockedOrMissing (int subId, boolean requireSim) {
        IccCardConstants.State state = mUpdateMonitor.getSimState(subId);
        boolean simLockedOrMissing = (state != null && state.isPinLocked())
                || ((state == IccCardConstants.State.ABSENT
                || state == IccCardConstants.State.PERM_DISABLED)
                && requireSim);
        return simLockedOrMissing;
    }

    public boolean lockscreenEnforcedByDevicePolicy() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            int passwordQuality = dpm.getPasswordQuality(null);
            switch (passwordQuality) {
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC:
                case DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_COMPLEX:
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
                case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
                case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                    return true;
            }
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
     * @see #onScreenTurnedOn
     * @see #handleNotifyScreenOn
     */
    private void notifyScreenOnLocked(IKeyguardShowCallback result) {
        if (DEBUG) Log.d(TAG, "notifyScreenOnLocked");
        Message msg = mHandler.obtainMessage(NOTIFY_SCREEN_ON, result);
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
                        // Don't play lockscreen SFX if the screen went off due to timeout.
                        mSuppressNextLockSound = true;
                        doKeyguardLocked(null);
                    }
                }
            } else if (DISMISS_KEYGUARD_SECURELY_ACTION.equals(intent.getAction())) {
                synchronized (KeyguardViewMediator.this) {
                    if (mScreenOn) {
                        dismiss();
                    } else {
                        mDismissSecurelyOnScreenOn = true;
                    }
                }
            } else if (KEYGUARD_SERVICE_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mKeyguardBound = intent.getBooleanExtra(KEYGUARD_SERVICE_EXTRA_ACTIVE, false);
                context.sendBroadcast(new Intent(LockscreenToggleTile.ACTION_APPLY_LOCKSCREEN_STATE)
                        .setPackage(context.getPackageName()));
            }
        }
    };

    public void keyguardDone(boolean authenticated, boolean wakeup) {
        if (DEBUG) Log.d(TAG, "keyguardDone(" + authenticated + ")");
        EventLog.writeEvent(70000, 2);
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
                    handleReset();
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
                case KEYGUARD_FINGERPRINT_AUTH:
                    if (msg.arg1 == 1) {
                        startFingerAuthIfUsingFingerprint();
                    } else {
                        stopAuthenticatingFingerprint();
                    }
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
        synchronized (this) {
            resetKeyguardDonePendingLocked();
        }

        if (authenticated) {
            if (isFingerprintActive()) {
                KeyguardStats.sendUnlockEvent(mContext,
                        mUpdateMonitor.isFingerprintRecognized(),
                        mUpdateMonitor.getFailedFingerprintUnlockAttempts());
            }
            mUpdateMonitor.clearFailedUnlockAttempts(true /* Clear FingerprintAttempts */);
        }
        mUpdateMonitor.clearFingerprintRecognized();

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
                final UserHandle currentUser = new UserHandle(mLockPatternUtils.getCurrentUser());
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
        // User feedback for keyguard.

        if (mSuppressNextLockSound) {
            mSuppressNextLockSound = false;
            return;
        }

        playSound(locked ? mLockSoundId : mUnlockSoundId);
    }

    private void playSound(int soundId) {
        if (soundId == 0) return;
        final ContentResolver cr = mContext.getContentResolver();
        if (Settings.System.getInt(cr, Settings.System.LOCKSCREEN_SOUNDS_ENABLED, 1) == 1) {

            mLockSounds.stop(mLockSoundStreamId);
            // Init mAudioManager
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (mAudioManager == null) return;
                mMasterStreamType = mAudioManager.getMasterStreamType();
            }
            // If the stream is muted, don't play the sound
            if (mAudioManager.isStreamMute(mMasterStreamType)) return;

            mLockSoundStreamId = mLockSounds.play(soundId,
                    mLockSoundVolume, mLockSoundVolume, 1/*priortiy*/, 0/*loop*/, 1.0f/*rate*/);
        }
    }

    private void playTrustedSound() {
        if (mSuppressNextLockSound) {
            return;
        }
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
            resetKeyguardDonePendingLocked();
            mHideAnimationRun = false;
            updateActivityLockScreenState();
            adjustStatusBarLocked();
            userActivity();

            // Do this at the end to not slow down display of the keyguard.
            playSounds(true);

            mShowKeyguardWakeLock.release();
        }
        mKeyguardDisplayManager.show();
    }

    private final Runnable mKeyguardGoingAwayRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // Don't actually hide the Keyguard at the moment, wait for window
                // manager until it tells us it's safe to do so with
                // startKeyguardExitAnimation.
                mWM.keyguardGoingAway(
                        mStatusBarKeyguardViewManager.shouldDisableWindowAnimationsForUnlock(),
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
            if (!mFingerprintWakeUnlock && mShowing && !mOccluded) {
                if (!mHideAnimationRun) {
                    mStatusBarKeyguardViewManager.startPreHideAnimation(mKeyguardGoingAwayRunnable);
                } else {
                    mKeyguardGoingAwayRunnable.run();
                }
            } else {

                // Don't try to rely on WindowManager - if Keyguard wasn't showing, window
                // manager won't start the exit animation.

                long startOffset = mFingerprintWakeUnlock ? 0 : mHideAnimation.getStartOffset();
                long duration = mFingerprintWakeUnlock ? 0 : mHideAnimation.getDuration();
                handleStartKeyguardExitAnimation(
                        SystemClock.uptimeMillis() + startOffset, duration);
            }
        }
    }

    private void handleOnActivityDrawn() {
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

            // only play "unlock" noises if not on a call (since the incall UI
            // disables the keyguard)
            if (TelephonyManager.EXTRA_STATE_IDLE.equals(mPhoneState)) {
                playSounds(false);
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

    /**
     * Handle message sent by {@link #notifyScreenOffLocked()}
     * @see #NOTIFY_SCREEN_OFF
     */
    private void handleNotifyScreenOff() {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenOff");
            if (isThirdPartyKeyguardEnabled()) {
                showThirdPartyKeyguard(true);
            }
            mStatusBarKeyguardViewManager.onScreenTurnedOff();
        }
    }

    /**
     * @return Whether a third party keyguard is enabled
     */
    private boolean isThirdPartyKeyguardEnabled() {
        return mLockPatternUtils.isThirdPartyKeyguardEnabled();
    }

    /**
     * Launches the third party keyguard activity as specified by
     * {@link LockPatternUtils#getThirdPartyKeyguardComponent()}
     */
    private void showThirdPartyKeyguard(boolean playSounds) {
        ComponentName thirdPartyKeyguardComponent =
                mLockPatternUtils.getThirdPartyKeyguardComponent();
        if (thirdPartyKeyguardComponent != null) {
            Intent intent = new Intent();
            intent.setComponent(thirdPartyKeyguardComponent);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                mContext.startActivity(intent);
                if (playSounds) playSounds(true);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Unable to start third party keyguard: " + thirdPartyKeyguardComponent);
            }
        }
    }

    private void resetKeyguardDonePendingLocked() {
        mKeyguardDonePending = false;
        mHandler.removeMessages(KEYGUARD_DONE_PENDING_TIMEOUT);
    }
    /**
     * Handle message sent by {@link #notifyScreenOnLocked}
     * @see #NOTIFY_SCREEN_ON
     */
    private void handleNotifyScreenOn(IKeyguardShowCallback callback) {
        synchronized (KeyguardViewMediator.this) {
            if (DEBUG) Log.d(TAG, "handleNotifyScreenOn");
            mStatusBarKeyguardViewManager.onScreenTurnedOn(callback);
        }
    }

    public boolean isDismissable() {
        return mKeyguardDonePending || !isSecure();
    }

    private boolean isAssistantAvailable() {
        return mSearchManager != null
                && mSearchManager.getAssistIntent(mContext, false, UserHandle.USER_CURRENT) != null;
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

    public StatusBarKeyguardViewManager registerStatusBar(PhoneStatusBar phoneStatusBar,
            ViewGroup container, StatusBarWindowManager statusBarWindowManager,
            ScrimController scrimController) {
        mStatusBarKeyguardViewManager.registerStatusBar(phoneStatusBar, container,
                statusBarWindowManager, scrimController);
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
            try {
                int size = mKeyguardStateCallbacks.size();
                for (int i = 0; i < size; i++) {
                    mKeyguardStateCallbacks.get(i).onShowingStateChanged(showing);
                }
            } catch (RemoteException e) {
                Slog.w(TAG, "Failed to call onShowingStateChanged", e);
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
             } catch (RemoteException e) {
                 Slog.w(TAG, "Failed to call onShowingStateChanged or onSimSecureStateChanged", e);
             }
         }
     }

    private void startFingerAuthIfUsingFingerprint() {
        synchronized (KeyguardViewMediator.this) {
            if (DBG_FINGERPRINT) Log.i(TAG, "startFingerAuthIfUsingFingerprint()");
            if (isFingerprintActive()) {
                if (mFingerprintWakeUnlock) {
                    Log.w(TAG, "fingerprint unlock mode, not authenticating");
                    return;
                }

                FingerprintManager fpm =
                        (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);

                // Lazily authenticate if the state isn't ready yet. This can happen
                // if another app (like camera) is stopping and keyguard is resuming, but
                // camera hasn't received its onPause method yet to cleanup its fingerprint connection
                if (FingerprintManager.STATE_IDLE == fpm.getState()) {
                    if (!mFingerAuthenticating) {
                        mFingerAuthenticating = true;
                        // Fingerprint service is already idle, ready to authenticate
                        if (DBG_FINGERPRINT)
                            Log.w(TAG, "fpm.authenticate()");
                        fpm.cancel();
                        fpm.authenticate(true);
                    }
                } else {
                    if (DBG_FINGERPRINT) {
                        Log.i(TAG, "deferring authenticate until idle fingerprint state");
                    }
                    mStartFingerAuthOnIdle = true;
                }
            }
        }
    }

    private void stopAuthenticatingFingerprint() {
        synchronized (KeyguardViewMediator.this) {
            if (isFingerprintActive()) {
                if (DBG_FINGERPRINT) {
                    Log.i(TAG, "stopAuthenticatingFingerprint()");
                }
                mFingerAuthenticating = false;
                FingerprintManager fpm =
                        (FingerprintManager) mContext.getSystemService(Context.FINGERPRINT_SERVICE);
                fpm.cancel();
            }
        }
    }

    private boolean isFingerprintActive() {
        return isFingerprintActive(mContext, mLockPatternUtils);
    }

    public static boolean isFingerprintActive(Context context, LockPatternUtils lockPatternUtils) {
        FingerprintManager fp = (FingerprintManager)
                context.getSystemService(Context.FINGERPRINT_SERVICE);
        return fp != null && fp.userEnrolled() && lockPatternUtils.usingFingerprint();
    }
}
