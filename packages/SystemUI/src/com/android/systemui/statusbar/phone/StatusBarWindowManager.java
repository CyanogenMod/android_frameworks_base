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

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.SystemProperties;
import android.view.Gravity;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceSession;
import android.view.WindowManager;
import android.view.WindowManagerPolicy;

import com.android.internal.policy.PolicyManager;
import com.android.keyguard.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.StatusBarState;

/**
 * Encapsulates all logic for the status bar window state management.
 */
public class StatusBarWindowManager {

    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mStatusBarView;
    private WindowManager.LayoutParams mLp;
    private int mBarHeight;
    private final boolean mKeyguardScreenRotation;

    private boolean mKeyguardBlurEnabled;
    private final int mStatusBarLayer;
    private BlurLayer mKeyguardBlur;
    private final SurfaceSession mFxSession;
    private final WindowManagerPolicy mPolicy = PolicyManager.makeNewWindowManager();

    private final PhoneStatusBar mBar;

    private static final int TYPE_LAYER_MULTIPLIER = 10000; // refer to WindowManagerService.TYPE_LAYER_MULTIPLIER
    private static final int TYPE_LAYER_OFFSET = 1000;      // refer to WindowManagerService.TYPE_LAYER_OFFSET

    private final State mCurrentState = new State();

    public StatusBarWindowManager(Context context, PhoneStatusBar bar) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardScreenRotation = shouldEnableKeyguardScreenRotation();
        mBar = bar;

        mKeyguardBlurEnabled = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_ui_blur_enabled);
        mFxSession = new SurfaceSession();
        mStatusBarLayer = mPolicy.windowTypeToLayerLw(WindowManager.LayoutParams.TYPE_STATUS_BAR)
                          * TYPE_LAYER_MULTIPLIER + TYPE_LAYER_OFFSET;
    }

    private boolean shouldEnableKeyguardScreenRotation() {
        Resources res = mContext.getResources();
        return SystemProperties.getBoolean("lockscreen.rot_override", false)
                || res.getBoolean(R.bool.config_enableLockScreenRotation);
    }

    /**
     * Adds the status bar view to the window manager.
     *
     * @param statusBarView The view to add.
     * @param barHeight The height of the status bar in collapsed state.
     */
    public void add(View statusBarView, int barHeight) {
        // Now that the status bar window encompasses the sliding panel and its
        // translucent backdrop, the entire thing is made TRANSLUCENT and is
        // hardware-accelerated.
        mLp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                barHeight,
                WindowManager.LayoutParams.TYPE_STATUS_BAR,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT);
        mLp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mLp.gravity = Gravity.TOP;
        mLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mLp.setTitle("StatusBar");
        mLp.packageName = mContext.getPackageName();
        mStatusBarView = statusBarView;
        mBarHeight = barHeight;
        mWindowManager.addView(mStatusBarView, mLp);

        if (mKeyguardBlurEnabled) {
            Display display = mWindowManager.getDefaultDisplay();
            Point xy = new Point();
            display.getRealSize(xy);
            mKeyguardBlur = new BlurLayer(mFxSession, xy.x, xy.y, "KeyGuard");
            if (mKeyguardBlur != null) {
                mKeyguardBlur.setLayer(mStatusBarLayer -2);
            }
        }
    }

    private void applyKeyguardFlags(State state) {
        if (state.keyguardShowing) {
            mLp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
            if (!mKeyguardBlurEnabled) {
                mLp.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
            }
        } else {
            mLp.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
            mLp.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
            if (mKeyguardBlurEnabled) {
                mKeyguardBlur.hide();
            }
        }
    }

    private void adjustScreenOrientation(State state) {
        if (state.isKeyguardShowingAndNotOccluded()) {
            if (mKeyguardScreenRotation) {
                mLp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
            } else {
                mLp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            }
        } else {
            mLp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    private void applyFocusableFlag(State state) {
        if (state.isKeyguardShowingAndNotOccluded() && state.keyguardNeedsInput
                && state.bouncerShowing) {
            mLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLp.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else if (state.isKeyguardShowingAndNotOccluded() || state.statusBarFocusable) {
            mLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLp.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            mLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLp.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
    }

    private void applyHeight(State state) {
        boolean expanded = state.isKeyguardShowingAndNotOccluded() || state.statusBarExpanded
                || state.keyguardFadingAway || state.bouncerShowing;
        if (expanded) {
            mLp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            mLp.height = mBarHeight;
        }
    }

    private void applyFitsSystemWindows(State state) {
        mStatusBarView.setFitsSystemWindows(!state.isKeyguardShowingAndNotOccluded());
    }

    private void applyUserActivityTimeout(State state) {
        if (state.isKeyguardShowingAndNotOccluded()
                && state.statusBarState == StatusBarState.KEYGUARD
                && !state.qsExpanded) {
            mLp.userActivityTimeout = state.keyguardUserActivityTimeout;
        } else {
            mLp.userActivityTimeout = -1;
        }
    }

    private void applyInputFeatures(State state) {
        if (state.isKeyguardShowingAndNotOccluded()
                && state.statusBarState == StatusBarState.KEYGUARD
                && !state.qsExpanded) {
            mLp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
        } else {
            mLp.inputFeatures &= ~WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
        }
    }

    private void apply(State state) {
        applyKeyguardFlags(state);
        applyFocusableFlag(state);
        adjustScreenOrientation(state);
        applyHeight(state);
        applyUserActivityTimeout(state);
        applyInputFeatures(state);
        applyFitsSystemWindows(state);
        mWindowManager.updateViewLayout(mStatusBarView, mLp);
    }

    private void applyKeyguardBlurShow(){
        boolean isblur = false;
        if (mCurrentState.keyguardShowing && mKeyguardBlurEnabled && !mCurrentState.keyguardOccluded) {
            isblur = true;
        }
        if (mKeyguardBlur != null) {
            if (isblur) {
                mKeyguardBlur.show();
            } else {
                mKeyguardBlur.hide();
            }
        }
    }

    public void setKeyguardShowing(boolean showing) {
        mCurrentState.keyguardShowing = showing;
        apply(mCurrentState);
    }

    void onScreenTurnedOn(){
        applyKeyguardBlurShow();
    }

    public void setKeyguardOccluded(boolean occluded) {
        final boolean oldOccluded = mCurrentState.keyguardOccluded;
        mCurrentState.keyguardOccluded = occluded;
        if (oldOccluded != occluded) {
            applyKeyguardBlurShow();
        }
        apply(mCurrentState);
    }

    public void setKeyguardNeedsInput(boolean needsInput) {
        mCurrentState.keyguardNeedsInput = needsInput;
        apply(mCurrentState);
    }

    public void setStatusBarExpanded(boolean expanded) {
        mCurrentState.statusBarExpanded = expanded;
        mCurrentState.statusBarFocusable = expanded;
        apply(mCurrentState);
    }

    public void setStatusBarFocusable(boolean focusable) {
        mCurrentState.statusBarFocusable = focusable;
        apply(mCurrentState);
    }

    public void setKeyguardUserActivityTimeout(long timeout) {
        mCurrentState.keyguardUserActivityTimeout = timeout;
        apply(mCurrentState);
    }

    public void setBouncerShowing(boolean showing) {
        mCurrentState.bouncerShowing = showing;
        apply(mCurrentState);
    }

    public void setKeyguardFadingAway(boolean keyguardFadingAway) {
        mCurrentState.keyguardFadingAway = keyguardFadingAway;
        apply(mCurrentState);
    }

    public void setQsExpanded(boolean expanded) {
        mCurrentState.qsExpanded = expanded;
        apply(mCurrentState);
    }

    void setBlur(float b){
        if (mKeyguardBlurEnabled && mKeyguardBlur != null) {
            float minBlur = mBar.isSecure() ? 1.0f : 0.0f;
            if (b < minBlur) {
                b = minBlur;
            } else if (b > 1.0f) {
                b = 1.0f;
            }
            mKeyguardBlur.setBlur(b);
        }
    }

    /**
     * @param state The {@link StatusBarState} of the status bar.
     */
    public void setStatusBarState(int state) {
        mCurrentState.statusBarState = state;
        apply(mCurrentState);
    }

    private static class State {
        boolean keyguardShowing;
        boolean keyguardOccluded;
        boolean keyguardNeedsInput;
        boolean statusBarExpanded;
        boolean statusBarFocusable;
        long keyguardUserActivityTimeout;
        boolean bouncerShowing;
        boolean keyguardFadingAway;
        boolean qsExpanded;

        /**
         * The {@link BaseStatusBar} state from the status bar.
         */
        int statusBarState;

        private boolean isKeyguardShowingAndNotOccluded() {
            return keyguardShowing && !keyguardOccluded;
        }
    }
}
