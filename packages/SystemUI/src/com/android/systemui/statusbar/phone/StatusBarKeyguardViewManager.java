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

package com.android.systemui.statusbar.phone;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewRootImpl;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.R;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.statusbar.CommandQueue;

import static com.android.keyguard.KeyguardHostView.OnDismissAction;

/**
 * Manages creating, showing, hiding and resetting the keyguard within the status bar. Calls back
 * via {@link ViewMediatorCallback} to poke the wake lock and report that the keyguard is done,
 * which is in turn, reported to this class by the current
 * {@link com.android.keyguard.KeyguardViewBase}.
 */
public class StatusBarKeyguardViewManager {

    // When hiding the Keyguard with timing supplied from WindowManager, better be early than late.
    private static final long HIDE_TIMING_CORRECTION_MS = -3 * 16;

    // Delay for showing the navigation bar when the bouncer appears. This should be kept in sync
    // with the appear animations of the PIN/pattern/password views.
    private static final long NAV_BAR_SHOW_DELAY_BOUNCER = 320;

    private static final long WAKE_AND_UNLOCK_SCRIM_FADEOUT_DURATION_MS = 200;

    private static String TAG = "StatusBarKeyguardViewManager";

    private final Context mContext;

    private LockPatternUtils mLockPatternUtils;
    private ViewMediatorCallback mViewMediatorCallback;
    private PhoneStatusBar mPhoneStatusBar;
    private ScrimController mScrimController;
    private FingerprintUnlockController mFingerprintUnlockController;

    private ViewGroup mContainer;
    private StatusBarWindowManager mStatusBarWindowManager;

    private boolean mDeviceInteractive = false;
    private boolean mScreenTurnedOn;
    private KeyguardBouncer mBouncer;
    private boolean mShowing;
    private boolean mOccluded;

    private boolean mFirstUpdate = true;
    private boolean mLastShowing;
    private boolean mLastOccluded;
    private boolean mLastBouncerShowing;
    private boolean mLastBouncerDismissible;
    private OnDismissAction mAfterKeyguardGoneAction;
    private boolean mDeviceWillWakeUp;
    private boolean mDeferScrimFadeOut;

    private View mUnlockFab;

    public StatusBarKeyguardViewManager(Context context, ViewMediatorCallback callback,
            LockPatternUtils lockPatternUtils) {
        mContext = context;
        mViewMediatorCallback = callback;
        mLockPatternUtils = lockPatternUtils;
    }

    public void registerStatusBar(PhoneStatusBar phoneStatusBar,
            ViewGroup container, StatusBarWindowManager statusBarWindowManager,
            ScrimController scrimController,
            FingerprintUnlockController fingerprintUnlockController) {
        mPhoneStatusBar = phoneStatusBar;
        mContainer = container;
        mStatusBarWindowManager = statusBarWindowManager;
        mScrimController = scrimController;
        if (mBouncer != null) mBouncer.removeView();
        mFingerprintUnlockController = fingerprintUnlockController;
        mBouncer = new KeyguardBouncer(mContext, mViewMediatorCallback, mLockPatternUtils,
                mStatusBarWindowManager, container);
    }

    /**
     * Show the keyguard.  Will handle creating and attaching to the view manager
     * lazily.
     */
    public void show(Bundle options) {
        mShowing = true;
        mStatusBarWindowManager.setKeyguardShowing(true);
        mScrimController.abortKeyguardFadingOut();
        reset(false);
    }

    /**
     * Shows the notification keyguard or the bouncer depending on
     * {@link KeyguardBouncer#needsFullscreenBouncer()}.
     */
    private void showBouncerOrKeyguard(boolean isBackPressed) {
        switch (mBouncer.needsFullscreenBouncer()) {
            case KeyguardBouncer.UNLOCK_SEQUENCE_FORCE_BOUNCER:
                // SIM PIN/PUK
                // The keyguard might be showing (already). So we need to hide it.
                mPhoneStatusBar.hideKeyguard();
                mBouncer.show(true /* resetSecuritySelection */);
                break;
            case KeyguardBouncer.UNLOCK_SEQUENCE_BOUNCER_FIRST:
                // Pattern/PIN/Password with "Directly pass to security view" enabled
                if (isBackPressed) {
                    mPhoneStatusBar.showKeyguard();
                    mBouncer.hide(false /* destroyView */);
                    mBouncer.prepare();
                } else {
                    // The keyguard might be showing (already). So we need to hide it.
                    mPhoneStatusBar.hideKeyguard();
                    mBouncer.show(true /* resetSecuritySelection */);
                }
                break;
            case KeyguardBouncer.UNLOCK_SEQUENCE_DEFAULT:
                mPhoneStatusBar.showKeyguard();
                mBouncer.hide(false /* destroyView */);
                mBouncer.prepare();
                break;
        }
    }

    private void showBouncer() {
        if (mShowing) {
            mBouncer.show(false /* resetSecuritySelection */);
        }
        updateStates();
    }

    public void dismissWithAction(OnDismissAction r, Runnable cancelAction,
            boolean afterKeyguardGone) {
        if (mShowing) {
            if (!afterKeyguardGone) {
                mBouncer.showWithDismissAction(r, cancelAction);
            } else {
                mBouncer.show(false /* resetSecuritySelection */);
                mAfterKeyguardGoneAction = r;
            }
        }
        updateStates();
    }

    /**
     * Reset the state of the view.
     */
    public void reset(boolean isBackPressed) {
        if (mShowing) {
            if (mOccluded) {
                mPhoneStatusBar.hideKeyguard();
                mPhoneStatusBar.stopWaitingForKeyguardExit();
                mBouncer.hide(false /* destroyView */);
            } else {
                showBouncerOrKeyguard(isBackPressed);
            }
            KeyguardUpdateMonitor.getInstance(mContext).sendKeyguardReset();
            updateStates();
        }
    }

    public void onStartedGoingToSleep() {
        mPhoneStatusBar.onStartedGoingToSleep();
    }

    public void onFinishedGoingToSleep() {
        mDeviceInteractive = false;
        mPhoneStatusBar.onFinishedGoingToSleep();
        mBouncer.onScreenTurnedOff();
    }

    public void onStartedWakingUp() {
        mDeviceInteractive = true;
        mDeviceWillWakeUp = false;
        mPhoneStatusBar.onStartedWakingUp();
    }

    public void onScreenTurningOn() {
        mPhoneStatusBar.onScreenTurningOn();
    }

    public boolean isScreenTurnedOn() {
        return mScreenTurnedOn;
    }

    public void onScreenTurnedOn() {
        mScreenTurnedOn = true;
        if (mDeferScrimFadeOut) {
            mDeferScrimFadeOut = false;
            animateScrimControllerKeyguardFadingOut(0, WAKE_AND_UNLOCK_SCRIM_FADEOUT_DURATION_MS,
                    true /* skipFirstFrame */);
            updateStates();
        }
        mPhoneStatusBar.onScreenTurnedOn();
        mStatusBarWindowManager.onKeyguardChanged();
    }

    public void onScreenTurnedOff() {
        mScreenTurnedOn = false;
        mPhoneStatusBar.onScreenTurnedOff();
    }

    public void notifyDeviceWakeUpRequested() {
        mDeviceWillWakeUp = !mDeviceInteractive;
    }

    public void verifyUnlock() {
        dismiss();
    }

    public void setNeedsInput(boolean needsInput) {
        mStatusBarWindowManager.setKeyguardNeedsInput(needsInput);
    }

    public void setOccluded(boolean occluded) {
        if (occluded && !mOccluded && mShowing) {
            if (mPhoneStatusBar.isInLaunchTransition()) {
                mOccluded = true;
                mPhoneStatusBar.fadeKeyguardAfterLaunchTransition(null /* beforeFading */,
                        new Runnable() {
                            @Override
                            public void run() {
                                mStatusBarWindowManager.setKeyguardOccluded(mOccluded);
                                reset(false);
                            }
                        });
                return;
            }
        }
        mOccluded = occluded;
        mStatusBarWindowManager.setKeyguardOccluded(occluded);
        mPhoneStatusBar.getVisualizer().setOccluded(occluded);
        if (mUnlockFab != null && mUnlockFab.isAttachedToWindow() && !occluded) {
            hideUnlockFab();
        }
        reset(false);
    }

    public boolean isOccluded() {
        return mOccluded;
    }

    /**
     * Starts the animation before we dismiss Keyguard, i.e. an disappearing animation on the
     * security view of the bouncer.
     *
     * @param finishRunnable the runnable to be run after the animation finished, or {@code null} if
     *                       no action should be run
     */
    public void startPreHideAnimation(Runnable finishRunnable) {
        if (mBouncer.isShowing()) {
            mBouncer.startPreHideAnimation(finishRunnable);
        } else if (finishRunnable != null) {
            finishRunnable.run();
        }
    }

    /**
     * Hides the keyguard view
     */
    public void hide(long startTime, final long fadeoutDuration) {
        mShowing = false;

        long uptimeMillis = SystemClock.uptimeMillis();
        long delay = Math.max(0, startTime + HIDE_TIMING_CORRECTION_MS - uptimeMillis);

        if (mPhoneStatusBar.isInLaunchTransition() ) {
            mPhoneStatusBar.fadeKeyguardAfterLaunchTransition(new Runnable() {
                @Override
                public void run() {
                    mStatusBarWindowManager.setKeyguardShowing(false);
                    mStatusBarWindowManager.setKeyguardFadingAway(true);
                    mBouncer.hide(true /* destroyView */);
                    updateStates();
                    mScrimController.animateKeyguardFadingOut(
                            PhoneStatusBar.FADE_KEYGUARD_START_DELAY,
                            PhoneStatusBar.FADE_KEYGUARD_DURATION, null,
                            false /* skipFirstFrame */);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mPhoneStatusBar.hideKeyguard();
                    mStatusBarWindowManager.setKeyguardFadingAway(false);
                    mViewMediatorCallback.keyguardGone();
                    executeAfterKeyguardGoneAction();
                }
            });
        } else {
            if (mFingerprintUnlockController.getMode()
                    == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK_PULSING) {
                mFingerprintUnlockController.startKeyguardFadingAway();
                mPhoneStatusBar.setKeyguardFadingAway(startTime, 0, 240);
                mStatusBarWindowManager.setKeyguardFadingAway(true);
                mPhoneStatusBar.fadeKeyguardWhilePulsing();
                animateScrimControllerKeyguardFadingOut(0, 240, new Runnable() {
                    @Override
                    public void run() {
                        mPhoneStatusBar.hideKeyguard();
                    }
                }, false /* skipFirstFrame */);
            } else {
                mFingerprintUnlockController.startKeyguardFadingAway();
                mPhoneStatusBar.setKeyguardFadingAway(startTime, delay, fadeoutDuration);
                boolean staying = mPhoneStatusBar.hideKeyguard();
                if (!staying) {
                    mStatusBarWindowManager.setKeyguardFadingAway(true);
                    if (mFingerprintUnlockController.getMode()
                            == FingerprintUnlockController.MODE_WAKE_AND_UNLOCK) {
                        if (!mScreenTurnedOn) {
                            mDeferScrimFadeOut = true;
                        } else {

                            // Screen is already on, don't defer with fading out.
                            animateScrimControllerKeyguardFadingOut(0,
                                    WAKE_AND_UNLOCK_SCRIM_FADEOUT_DURATION_MS,
                                    true /* skipFirstFrame */);
                        }
                    } else {
                        animateScrimControllerKeyguardFadingOut(delay, fadeoutDuration,
                                false /* skipFirstFrame */);
                    }
                } else {
                    mScrimController.animateGoingToFullShade(delay, fadeoutDuration);
                    mPhoneStatusBar.finishKeyguardFadingAway();
                }
            }
            mStatusBarWindowManager.setKeyguardShowing(false);
            mBouncer.hide(true /* destroyView */);
            mViewMediatorCallback.keyguardGone();
            executeAfterKeyguardGoneAction();
            updateStates();
        }
    }

    private void animateScrimControllerKeyguardFadingOut(long delay, long duration,
            boolean skipFirstFrame) {
        animateScrimControllerKeyguardFadingOut(delay, duration, null /* endRunnable */,
                skipFirstFrame);
    }

    private void animateScrimControllerKeyguardFadingOut(long delay, long duration,
            final Runnable endRunnable, boolean skipFirstFrame) {
        Trace.asyncTraceBegin(Trace.TRACE_TAG_VIEW, "Fading out", 0);
        mScrimController.animateKeyguardFadingOut(delay, duration, new Runnable() {
            @Override
            public void run() {
                if (endRunnable != null) {
                    endRunnable.run();
                }
                mStatusBarWindowManager.setKeyguardFadingAway(false);
                mPhoneStatusBar.finishKeyguardFadingAway();
                mFingerprintUnlockController.finishKeyguardFadingAway();
                WindowManagerGlobal.getInstance().trimMemory(
                        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
                Trace.asyncTraceEnd(Trace.TRACE_TAG_VIEW, "Fading out", 0);
            }
        }, skipFirstFrame);
    }

    private void executeAfterKeyguardGoneAction() {
        if (mAfterKeyguardGoneAction != null) {
            dismiss();
            mAfterKeyguardGoneAction.onDismiss();
            mAfterKeyguardGoneAction = null;
        }
    }

    /**
     * Dismisses the keyguard by going to the next screen or making it gone.
     */
    public void dismiss() {
        dismiss(false);
    }

    public void dismiss(boolean focusKeyguardExternalView) {
        if ((mDeviceInteractive || mDeviceWillWakeUp) && !focusKeyguardExternalView) {
            showBouncer();
            hideUnlockFab();
        } else if (focusKeyguardExternalView) {
            showUnlockFab();
            mStatusBarWindowManager.setKeyguardExternalViewFocus(true);
        }
    }

    /**
     * WARNING: This method might cause Binder calls.
     */
    public boolean isSecure() {
        return mBouncer.isSecure();
    }

    /**
     * @return Whether the keyguard is showing
     */
    public boolean isShowing() {
        return mShowing;
    }

    /**
     * Notifies this manager that the back button has been pressed.
     *
     * @return whether the back press has been handled
     */
    public boolean onBackPressed() {
        if (mBouncer.isShowing()) {
            mPhoneStatusBar.endAffordanceLaunch();
            reset(true);
            return true;
        }
        return false;
    }

    public boolean isBouncerShowing() {
        return mBouncer.isShowing();
    }

    private long getNavBarShowDelay() {
        if (mPhoneStatusBar.isKeyguardFadingAway()) {
            return mPhoneStatusBar.getKeyguardFadingAwayDelay();
        } else {

            // Keyguard is not going away, thus we are showing the navigation bar because the
            // bouncer is appearing.
            return NAV_BAR_SHOW_DELAY_BOUNCER;
        }
    }

    private Runnable mMakeNavigationBarVisibleRunnable = new Runnable() {
        @Override
        public void run() {
            mPhoneStatusBar.getNavigationBarView().setVisibility(View.VISIBLE);
        }
    };

    private void updateStates() {
        int vis = mContainer.getSystemUiVisibility();
        boolean showing = mShowing;
        boolean occluded = mOccluded;
        boolean bouncerShowing = mBouncer.isShowing();
        boolean bouncerDismissible = (mBouncer.isFullscreenBouncer() !=
                KeyguardBouncer.UNLOCK_SEQUENCE_FORCE_BOUNCER);

        if ((bouncerDismissible || !showing) != (mLastBouncerDismissible || !mLastShowing)
                || mFirstUpdate) {
            if (bouncerDismissible || !showing) {
                mContainer.setSystemUiVisibility(vis & ~View.STATUS_BAR_DISABLE_BACK);
            } else {
                mContainer.setSystemUiVisibility(vis | View.STATUS_BAR_DISABLE_BACK);
            }
        }

        boolean navBarVisible = (!(showing && !occluded) || bouncerShowing);
        boolean lastNavBarVisible = (!(mLastShowing && !mLastOccluded) || mLastBouncerShowing);
        if (navBarVisible != lastNavBarVisible || mFirstUpdate) {
            if (mPhoneStatusBar.getNavigationBarView() != null) {
                if (navBarVisible) {
                    long delay = getNavBarShowDelay();
                    if (delay == 0) {
                        mMakeNavigationBarVisibleRunnable.run();
                    } else {
                        mContainer.postOnAnimationDelayed(mMakeNavigationBarVisibleRunnable,
                                delay);
                    }
                } else {
                    mContainer.removeCallbacks(mMakeNavigationBarVisibleRunnable);
                    mPhoneStatusBar.getNavigationBarView().setVisibility(View.GONE);
                }
            }
        }

        if (bouncerShowing != mLastBouncerShowing || mFirstUpdate) {
            mStatusBarWindowManager.setBouncerShowing(bouncerShowing);
            mPhoneStatusBar.setBouncerShowing(bouncerShowing);
            mScrimController.setBouncerShowing(bouncerShowing);
        }

        KeyguardUpdateMonitor updateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        if ((showing && !occluded) != (mLastShowing && !mLastOccluded) || mFirstUpdate) {
            updateMonitor.onKeyguardVisibilityChanged(showing && !occluded);
        }
        if (bouncerShowing != mLastBouncerShowing || mFirstUpdate) {
            updateMonitor.sendKeyguardBouncerChanged(bouncerShowing);
        }

        mFirstUpdate = false;
        mLastShowing = showing;
        mLastOccluded = occluded;
        mLastBouncerShowing = bouncerShowing;
        mLastBouncerDismissible = bouncerDismissible;

        mPhoneStatusBar.onKeyguardViewManagerStatesUpdated();
    }

    public boolean onMenuPressed() {
        return mBouncer.onMenuPressed();
    }

    public boolean interceptMediaKey(KeyEvent event) {
        return mBouncer.interceptMediaKey(event);
    }

    public void onActivityDrawn() {
        if (mPhoneStatusBar.isCollapsing()) {
            mPhoneStatusBar.addPostCollapseAction(new Runnable() {
                @Override
                public void run() {
                    mViewMediatorCallback.readyForKeyguardDone();
                }
            });
        } else {
            mViewMediatorCallback.readyForKeyguardDone();
        }
    }

    public boolean shouldDisableWindowAnimationsForUnlock() {
        return mPhoneStatusBar.isInLaunchTransition() ||
                mPhoneStatusBar.isShowingLiveLockScreenView();
    }

    public boolean isGoingToNotificationShade() {
        return mPhoneStatusBar.isGoingToNotificationShade();
    }

    public boolean isSecure(int userId) {
        return mBouncer.isSecure() || mLockPatternUtils.isSecure(userId);
    }

    public boolean isInputRestricted() {
        return mViewMediatorCallback.isInputRestricted();
    }

    public void keyguardGoingAway() {
        mPhoneStatusBar.keyguardGoingAway();
    }

    public void animateCollapsePanels(float speedUpFactor) {
        mPhoneStatusBar.animateCollapsePanels(CommandQueue.FLAG_EXCLUDE_NONE, true /* force */,
                false /* delayed */, speedUpFactor);
        if (mStatusBarWindowManager.keyguardExternalViewHasFocus()) {
            mStatusBarWindowManager.setKeyguardExternalViewFocus(false);
            dismiss(false);
        }
    }

    /**
     * Notifies that the user has authenticated by other means than using the bouncer, for example,
     * fingerprint.
     */
    public void notifyKeyguardAuthenticated(boolean strongAuth) {
        mBouncer.notifyKeyguardAuthenticated(strongAuth);
    }

    public void showBouncerMessage(String message, int color) {
        mBouncer.showMessage(message, color);
    }

    public ViewRootImpl getViewRootImpl() {
        return mPhoneStatusBar.getStatusBarView().getViewRootImpl();
    }

    public boolean isKeyguardShowingMedia() {
        return mPhoneStatusBar.isKeyguardShowingMedia();
    }

    public void setKeyguardExternalViewFocus(boolean hasFocus) {
        if (hasFocus) {
            showUnlockFab();
        } else {
            hideUnlockFab();
        }
        mStatusBarWindowManager.setKeyguardExternalViewFocus(hasFocus);
    }

    private void showUnlockFab() {
        if (mUnlockFab == null) {
            mUnlockFab = View.inflate(mContext, R.layout.unlock_fab, null);
        }
        if (!mUnlockFab.isAttachedToWindow()) {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.setTitle("UnlockFab");
            lp.packageName = mContext.getPackageName();
            lp.width = lp.height =
                    mContext.getResources().getDimensionPixelSize(R.dimen.unlock_fab_size);
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(mUnlockFab, lp);
            mUnlockFab.setOnClickListener(mUnlockFabClickListener);
        }
    }

    private void hideUnlockFab() {
        if (mUnlockFab != null && mUnlockFab.isAttachedToWindow()) {
            WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            wm.removeViewImmediate(mUnlockFab);
        }
    }

    private View.OnClickListener mUnlockFabClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mStatusBarWindowManager.setKeyguardExternalViewFocus(false);
            dismiss(false);
        }
    };
}
