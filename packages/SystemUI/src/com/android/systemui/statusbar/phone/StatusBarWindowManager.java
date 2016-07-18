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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Display;
import android.view.SurfaceSession;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.keyguard.R;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LiveLockScreenController;
import cyanogenmod.providers.CMSettings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.Field;

/**
 * Encapsulates all logic for the status bar window state management.
 */
public class StatusBarWindowManager implements KeyguardMonitor.Callback {

    private final Context mContext;
    private final WindowManager mWindowManager;
    private View mStatusBarView;
    private WindowManager.LayoutParams mLp;
    private WindowManager.LayoutParams mLpChanged;
    private int mBarHeight;
    private boolean mKeyguardScreenRotation;
    private final float mScreenBrightnessDoze;
    private final boolean mBlurSupported;

    private boolean mKeyguardBlurEnabled;
    private boolean mShowingMedia;
    private BlurLayer mKeyguardBlur;
    private final SurfaceSession mFxSession;

    private final KeyguardMonitor mKeyguardMonitor;
    private int mCurrentOrientation;

    private static final int TYPE_LAYER_MULTIPLIER = 10000; // refer to WindowManagerService.TYPE_LAYER_MULTIPLIER
    private static final int TYPE_LAYER_OFFSET = 1000;      // refer to WindowManagerService.TYPE_LAYER_OFFSET

    private static final int STATUS_BAR_LAYER = 16 * TYPE_LAYER_MULTIPLIER + TYPE_LAYER_OFFSET;

    private final State mCurrentState = new State();
    private LiveLockScreenController mLiveLockScreenController;

    public StatusBarWindowManager(Context context, KeyguardMonitor kgm) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mKeyguardScreenRotation = shouldEnableKeyguardScreenRotation();
        mScreenBrightnessDoze = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_screenBrightnessDoze) / 255f;
        mBlurSupported = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_ui_blur_enabled);

        mKeyguardMonitor = kgm;
        mKeyguardMonitor.addCallback(this);
        mFxSession = new SurfaceSession();
    }

    private boolean shouldEnableKeyguardScreenRotation() {
        Resources res = mContext.getResources();
        boolean enableAccelerometerRotation = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) != 0;
        boolean enableLockScreenRotation = CMSettings.System.getInt(mContext.getContentResolver(),
                CMSettings.System.LOCKSCREEN_ROTATION, 0) != 0;
        return SystemProperties.getBoolean("lockscreen.rot_override", false)
                || (res.getBoolean(R.bool.config_enableLockScreenRotation)
                && (enableLockScreenRotation && enableAccelerometerRotation));
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
        mLpChanged = new WindowManager.LayoutParams();
        mLpChanged.copyFrom(mLp);

        mKeyguardBlurEnabled = mBlurSupported ?
                CMSettings.Secure.getInt(mContext.getContentResolver(),
                CMSettings.Secure.LOCK_SCREEN_BLUR_ENABLED, 1) == 1 : false;
        if (mBlurSupported) {
            Display display = mWindowManager.getDefaultDisplay();
            Point xy = new Point();
            display.getRealSize(xy);
            mCurrentOrientation = mContext.getResources().getConfiguration().orientation;
            mKeyguardBlur = new BlurLayer(mFxSession, xy.x, xy.y, "KeyGuard");
            if (mKeyguardBlur != null) {
                mKeyguardBlur.setLayer(STATUS_BAR_LAYER - 2);
            }
        }

        SettingsObserver observer = new SettingsObserver(new Handler());
        observer.observe(mContext);
    }

    private void applyKeyguardFlags(State state) {
        if (state.keyguardShowing) {
            mLpChanged.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
            if (!mKeyguardBlurEnabled || mShowingMedia) {
                mLpChanged.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
            }
        } else {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
            mLpChanged.privateFlags &= ~WindowManager.LayoutParams.PRIVATE_FLAG_KEYGUARD;
            if (mKeyguardBlurEnabled && mKeyguardBlur != null) {
                mKeyguardBlur.hide();
            }
        }
    }

    private void adjustScreenOrientation(State state) {
        if (state.isKeyguardShowingAndNotOccluded()) {
            if (mKeyguardScreenRotation) {
                mLpChanged.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
            } else {
                mLpChanged.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
            }
        } else {
            mLpChanged.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    private void applyFocusableFlag(State state) {
        boolean panelFocusable = state.statusBarFocusable && state.panelExpanded;
        if (state.keyguardShowing && state.keyguardNeedsInput && state.bouncerShowing) {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else if (state.isKeyguardShowingAndNotOccluded() || panelFocusable) {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
    }

    private void applyHeight(State state) {
        boolean expanded = isExpanded(state);
        if (expanded) {
            mLpChanged.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            mLpChanged.height = mBarHeight;
        }
    }

    private boolean isExpanded(State state) {
        return !state.forceCollapsed && (state.isKeyguardShowingAndNotOccluded()
                || state.panelVisible || state.keyguardFadingAway || state.bouncerShowing
                || state.headsUpShowing);
    }

    private void applyFitsSystemWindows(State state) {
        mStatusBarView.setFitsSystemWindows(!state.isKeyguardShowingAndNotOccluded());
    }

    private void applyUserActivityTimeout(State state) {
        if (state.isKeyguardShowingAndNotOccluded()
                && state.statusBarState == StatusBarState.KEYGUARD
                && !state.qsExpanded) {
            mLpChanged.userActivityTimeout = KeyguardViewMediator.AWAKE_INTERVAL_DEFAULT_MS;
        } else {
            mLpChanged.userActivityTimeout = -1;
        }
    }

    private void applyInputFeatures(State state) {
        if (state.isKeyguardShowingAndNotOccluded()
                && state.statusBarState == StatusBarState.KEYGUARD
                && !state.qsExpanded) {
            mLpChanged.inputFeatures |=
                    WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
        } else {
            mLpChanged.inputFeatures &=
                    ~WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
        }
    }

    private void apply(State state) {
        applyKeyguardFlags(state);
        applyForceStatusBarVisibleFlag(state);
        applyFocusableFlag(state);
        adjustScreenOrientation(state);
        applyHeight(state);
        applyUserActivityTimeout(state);
        applyInputFeatures(state);
        applyFitsSystemWindows(state);
        applyModalFlag(state);
        applyBrightness(state);
        if (mLp.copyFrom(mLpChanged) != 0) {
            mWindowManager.updateViewLayout(mStatusBarView, mLp);
        }
    }

    private void applyForceStatusBarVisibleFlag(State state) {
        if (state.forceStatusBarVisible) {
            mLpChanged.privateFlags |= WindowManager
                    .LayoutParams.PRIVATE_FLAG_FORCE_STATUS_BAR_VISIBLE_TRANSPARENT;
        } else {
            mLpChanged.privateFlags &= ~WindowManager
                    .LayoutParams.PRIVATE_FLAG_FORCE_STATUS_BAR_VISIBLE_TRANSPARENT;
        }
    }

    private void applyModalFlag(State state) {
        if (state.headsUpShowing) {
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        } else {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }
    }

    private void applyBrightness(State state) {
        if (state.forceDozeBrightness) {
            mLpChanged.screenBrightness = mScreenBrightnessDoze;
        } else {
            mLpChanged.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        }
    }

    private void applyKeyguardBlurShow(){
        boolean isblur = false;
        if (mCurrentState.keyguardShowing && mKeyguardBlurEnabled
                && !mCurrentState.keyguardOccluded
                && !mShowingMedia) {
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

    public void setPanelVisible(boolean visible) {
        mCurrentState.panelVisible = visible;
        mCurrentState.statusBarFocusable = visible;
        apply(mCurrentState);
    }

    public void setStatusBarFocusable(boolean focusable) {
        mCurrentState.statusBarFocusable = focusable;
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

    public void setHeadsUpShowing(boolean showing) {
        mCurrentState.headsUpShowing = showing;
        apply(mCurrentState);
    }

    void setBlur(float b){
        if (mKeyguardBlurEnabled && mKeyguardBlur != null) {
            float minBlur = mKeyguardMonitor.isSecure() ? 1.0f : 0.0f;
            if (b < minBlur) {
                b = minBlur;
            } else if (b > 1.0f) {
                b = 1.0f;
            }
            mKeyguardBlur.setBlur(b);
        }
    }

    public void setShowingMedia(boolean showingMedia) {
        mShowingMedia = showingMedia;
        applyKeyguardBlurShow();
    }

    public void setKeyguardExternalViewFocus(boolean hasFocus) {
        mLiveLockScreenController.onLiveLockScreenFocusChanged(hasFocus);
        // make the keyguard occluded so the external view gets full focus
        setKeyguardOccluded(hasFocus);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (mKeyguardBlur != null && newConfig.orientation != mCurrentOrientation) {
            Display display = mWindowManager.getDefaultDisplay();
            Point xy = new Point();
            display.getRealSize(xy);
            mKeyguardBlur.setSize(xy.x, xy.y);
            mCurrentOrientation = newConfig.orientation;
        }
    }

    /**
     * @param state The {@link StatusBarState} of the status bar.
     */
    public void setStatusBarState(int state) {
        mCurrentState.statusBarState = state;
        apply(mCurrentState);
    }

    public void setForceStatusBarVisible(boolean forceStatusBarVisible) {
        mCurrentState.forceStatusBarVisible = forceStatusBarVisible;
        apply(mCurrentState);
    }

    /**
     * Force the window to be collapsed, even if it should theoretically be expanded.
     * Used for when a heads-up comes in but we still need to wait for the touchable regions to
     * be computed.
     */
    public void setForceWindowCollapsed(boolean force) {
        mCurrentState.forceCollapsed = force;
        apply(mCurrentState);
    }

    public void setPanelExpanded(boolean isExpanded) {
        mCurrentState.panelExpanded = isExpanded;
        apply(mCurrentState);
    }

    /**
     * Set whether the screen brightness is forced to the value we use for doze mode by the status
     * bar window.
     */
    public void setForceDozeBrightness(boolean forceDozeBrightness) {
        mCurrentState.forceDozeBrightness = forceDozeBrightness;
        apply(mCurrentState);
    }

    @Override
    public void onKeyguardChanged() {
        applyKeyguardBlurShow();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("StatusBarWindowManager state:");
        pw.println(mCurrentState);
    }

    public boolean keyguardExternalViewHasFocus() {
        return mLiveLockScreenController.getLiveLockScreenHasFocus();
    }

    public void setLiveLockscreenController(LiveLockScreenController liveLockScreenController) {
        mLiveLockScreenController = liveLockScreenController;
    }

    private static class State {
        boolean keyguardShowing;
        boolean keyguardOccluded;
        boolean keyguardNeedsInput;
        boolean panelVisible;
        boolean panelExpanded;
        boolean statusBarFocusable;
        boolean bouncerShowing;
        boolean keyguardFadingAway;
        boolean qsExpanded;
        boolean headsUpShowing;
        boolean forceStatusBarVisible;
        boolean forceCollapsed;
        boolean forceDozeBrightness;

        /**
         * The {@link BaseStatusBar} state from the status bar.
         */
        int statusBarState;

        private boolean isKeyguardShowingAndNotOccluded() {
            return keyguardShowing && !keyguardOccluded;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            String newLine = "\n";
            result.append("Window State {");
            result.append(newLine);

            Field[] fields = this.getClass().getDeclaredFields();

            // Print field names paired with their values
            for (Field field : fields) {
                result.append("  ");
                try {
                    result.append(field.getName());
                    result.append(": ");
                    //requires access to private field:
                    result.append(field.get(this));
                } catch (IllegalAccessException ex) {
                }
                result.append(newLine);
            }
            result.append("}");

            return result.toString();
        }
    }

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe(Context context) {
            context.getContentResolver().registerContentObserver(
                    CMSettings.Secure.getUriFor(CMSettings.Secure.LOCK_SCREEN_BLUR_ENABLED),
                    false,
                    this);
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    false,
                    this);
            context.getContentResolver().registerContentObserver(
                    CMSettings.System.getUriFor(CMSettings.System.LOCKSCREEN_ROTATION),
                    false,
                    this);
        }

        public void unobserve(Context context) {
            context.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mKeyguardBlurEnabled = mBlurSupported ?
                    CMSettings.Secure.getInt(mContext.getContentResolver(),
                    CMSettings.Secure.LOCK_SCREEN_BLUR_ENABLED, 1) == 1 : false;
            mKeyguardScreenRotation = shouldEnableKeyguardScreenRotation();
            // update the state
            apply(mCurrentState);
        }
    }
}
