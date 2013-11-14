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

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import com.android.internal.policy.IKeyguardShowCallback;
import com.android.internal.widget.LockPatternUtils;

import android.app.Activity;
import android.app.ActivityManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.internal.util.cm.TorchConstants;

/**
 * Manages creating, showing, hiding and resetting the keyguard.  Calls back
 * via {@link KeyguardViewMediator.ViewMediatorCallback} to poke
 * the wake lock and report that the keyguard is done, which is in turn,
 * reported to this class by the current {@link KeyguardViewBase}.
 */
public class KeyguardViewManager {
    private final static boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static String TAG = "KeyguardViewManager";
    public final static String IS_SWITCHING_USER = "is_switching_user";

    // Delay dismissing keyguard to allow animations to complete.
    private static final int HIDE_KEYGUARD_DELAY = 500;

    // Timeout used for keypresses
    static final int DIGIT_PRESS_WAKE_MILLIS = 5000;

    private final Context mContext;
    private final ViewManager mViewManager;
    private final KeyguardViewMediator.ViewMediatorCallback mViewMediatorCallback;

    private WindowManager.LayoutParams mWindowLayoutParams;
    private boolean mNeedsInput = false;

    private ViewManagerHost mKeyguardHost;
    private KeyguardHostView mKeyguardView;

    private boolean mScreenOn = false;
    private LockPatternUtils mLockPatternUtils;

    private boolean mUnlockKeyDown = false;

    private KeyguardUpdateMonitorCallback mBackgroundChanger = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSetBackground(Bitmap bmp) {
            mKeyguardHost.setCustomBackground(bmp != null ?
                    new BitmapDrawable(mContext.getResources(), bmp) : null);
            updateShowWallpaper(bmp == null);
        }
    };

    public interface ShowListener {
        void onShown(IBinder windowToken);
    };

    /**
     * @param context Used to create views.
     * @param viewManager Keyguard will be attached to this.
     * @param callback Used to notify of changes.
     * @param lockPatternUtils
     */
    public KeyguardViewManager(Context context, ViewManager viewManager,
            KeyguardViewMediator.ViewMediatorCallback callback,
            LockPatternUtils lockPatternUtils) {
        mContext = context;
        mViewManager = viewManager;
        mViewMediatorCallback = callback;
        mLockPatternUtils = lockPatternUtils;
    }

    /**
     * Show the keyguard.  Will handle creating and attaching to the view manager
     * lazily.
     */
    public synchronized void show(Bundle options) {
        if (DEBUG) Log.d(TAG, "show(); mKeyguardView==" + mKeyguardView);

        boolean enableScreenRotation = shouldEnableScreenRotation();

        maybeCreateKeyguardLocked(enableScreenRotation, false, options);
        maybeEnableScreenRotation(enableScreenRotation);

        // Disable common aspects of the system/status/navigation bars that are not appropriate or
        // useful on any keyguard screen but can be re-shown by dialogs or SHOW_WHEN_LOCKED
        // activities. Other disabled bits are handled by the KeyguardViewMediator talking
        // directly to the status bar service.
        int visFlags = View.STATUS_BAR_DISABLE_HOME;
        if (shouldEnableTranslucentDecor()) {
            mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                                       | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        }
        if (DEBUG) Log.v(TAG, "show:setSystemUiVisibility(" + Integer.toHexString(visFlags)+")");
        mKeyguardHost.setSystemUiVisibility(visFlags);

        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
        mKeyguardHost.setVisibility(View.VISIBLE);
        mKeyguardView.show();
        mKeyguardView.requestFocus();
    }

    private boolean shouldEnableScreenRotation() {
        Resources res = mContext.getResources();
        boolean enableLockScreenRotation = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_ROTATION, 0) != 0;
        boolean enableAccelerometerRotation = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) != 0;
        return SystemProperties.getBoolean("lockscreen.rot_override",false)
                || (enableLockScreenRotation && enableAccelerometerRotation);
    }

    private boolean shouldEnableTranslucentDecor() {
        Resources res = mContext.getResources();
        return res.getBoolean(R.bool.config_enableLockScreenTranslucentDecor);
    }

    class ViewManagerHost extends FrameLayout {
        private static final int BACKGROUND_COLOR = 0x70000000;

        private Drawable mCustomBackground;

        // This is a faster way to draw the background on devices without hardware acceleration
        private final Drawable mBackgroundDrawable = new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                drawToCanvas(canvas, mCustomBackground);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter cf) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };

        private TransitionDrawable mTransitionBackground = null;

        public ViewManagerHost(Context context) {
            super(context);
            setBackground(mBackgroundDrawable);
        }

        public void drawToCanvas(Canvas canvas, Drawable drawable) {
            if (drawable != null) {
                final Rect bounds = drawable.getBounds();
                final int vWidth = getWidth();
                final int vHeight = getHeight();

                final int restore = canvas.save();
                canvas.translate(-(bounds.width() - vWidth) / 2,
                        -(bounds.height() - vHeight) / 2);
                drawable.draw(canvas);
                canvas.restoreToCount(restore);
            } else {
                canvas.drawColor(BACKGROUND_COLOR, PorterDuff.Mode.SRC);
            }
        }

        public void setCustomBackground(Drawable d) {
            if (!ActivityManager.isHighEndGfx() || !mScreenOn) {
                mCustomBackground = d;
                if (d != null) {
                    d.setColorFilter(BACKGROUND_COLOR, PorterDuff.Mode.SRC_OVER);
                }
                computeCustomBackgroundBounds(mCustomBackground);
                setBackground(mBackgroundDrawable);
            } else {
                Drawable old = mCustomBackground;
                if (old == null && d == null) {
                    return;
                }
                boolean newIsNull = false;
                if (old == null) {
                    old = new ColorDrawable(BACKGROUND_COLOR);
                }
                if (d == null) {
                    d = new ColorDrawable(BACKGROUND_COLOR);
                    newIsNull = true;
                } else {
                    d.setColorFilter(BACKGROUND_COLOR, PorterDuff.Mode.SRC_OVER);
                }
                computeCustomBackgroundBounds(d);
                Bitmap b = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                drawToCanvas(c, d);

                Drawable dd = new BitmapDrawable(mContext.getResources(), b);

                mTransitionBackground = new TransitionDrawable(new Drawable[] {old, dd});
                mTransitionBackground.setCrossFadeEnabled(true);
                setBackground(mTransitionBackground);

                mTransitionBackground.startTransition(200);

                mCustomBackground = newIsNull ? null : dd;

            }
            invalidate();
        }

        private void computeCustomBackgroundBounds(Drawable background) {
            if (background == null) return; // Nothing to do
            if (!isLaidOut()) return; // We'll do this later

            final int bgWidth = background.getIntrinsicWidth();
            final int bgHeight = background.getIntrinsicHeight();
            final int vWidth = getWidth();
            final int vHeight = getHeight();

            final float bgAspect = (float) bgWidth / bgHeight;
            final float vAspect = (float) vWidth / vHeight;

            if (bgAspect > vAspect) {
                background.setBounds(0, 0, (int) (vHeight * bgAspect), vHeight);
            } else {
                background.setBounds(0, 0, vWidth, (int) (vWidth / bgAspect));
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            computeCustomBackgroundBounds(mCustomBackground);
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            if (mKeyguardHost.getVisibility() == View.VISIBLE) {
                // only propagate configuration messages if we're currently showing
                maybeCreateKeyguardLocked(shouldEnableScreenRotation(), true, null);
            } else {
                if (DEBUG) Log.v(TAG, "onConfigurationChanged: view not visible");
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (mKeyguardView != null) {
                int keyCode = event.getKeyCode();
                int action = event.getAction();

                if (action == KeyEvent.ACTION_DOWN) {
                    if (handleKeyDown(keyCode, event)) {
                        return true;
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    if (handleKeyUp(keyCode, event)) {
                        return true;
                    }
                }
                // Always process media keys, regardless of focus
                if (mKeyguardView.dispatchKeyEvent(event)) {
                    return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() == 0) {
            mUnlockKeyDown = true;
            // We check for Camera key press in handleKeyDown, because
            // it gives us "instant" unlock, when user depresses
            // the button.
            if (keyCode == KeyEvent.KEYCODE_CAMERA) {
                if (mKeyguardView.handleCameraKey()) {
                    return true;
                }
            }
        }
        if (event.isLongPress()) {
            String action = null;
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    action = Settings.System.LOCKSCREEN_LONG_BACK_ACTION;
                    break;
                case KeyEvent.KEYCODE_HOME:
                    action = Settings.System.LOCKSCREEN_LONG_HOME_ACTION;
                    break;
                case KeyEvent.KEYCODE_MENU:
                    action = Settings.System.LOCKSCREEN_LONG_MENU_ACTION;
                    break;
            }

            if (action != null) {
                mUnlockKeyDown = false;
                String uri = Settings.System.getString(mContext.getContentResolver(), action);
                if (uri != null && runAction(mContext, uri)) {
                    long[] pattern = getLongPressVibePattern(mContext);
                    if (pattern != null) {
                        Vibrator v = (Vibrator) mContext.getSystemService(mContext.VIBRATOR_SERVICE);
                        if (pattern.length == 1) {
                            v.vibrate(pattern[0]);
                        } else {
                            v.vibrate(pattern, -1);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean handleKeyUp(int keyCode, KeyEvent event) {
        if (mUnlockKeyDown) {
            mUnlockKeyDown = false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (mKeyguardView.handleBackKey()) {
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_HOME:
                    if (mKeyguardView.handleHomeKey()) {
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_MENU:
                    if (mKeyguardView.handleMenuKey()) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    private static boolean runAction(Context context, String uri) {
        if ("FLASHLIGHT".equals(uri)) {
            context.sendBroadcast(new Intent(TorchConstants.ACTION_TOGGLE_STATE));
            return true;
        } else if ("NEXT".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT);
            return true;
        } else if ("PREVIOUS".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            return true;
        } else if ("PLAYPAUSE".equals(uri)) {
            sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return true;
        } else if ("SOUND".equals(uri)) {
            toggleSilentMode(context);
            return true;
        } else if ("SLEEP".equals(uri)) {
            sendToSleep(context);
            return true;
        }

        return false;
    }

    private static void sendMediaButtonEvent(Context context, int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        context.sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        context.sendOrderedBroadcast(upIntent, null);
    }

    private static void toggleSilentMode(Context context) {
        final AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        final boolean hasVib = vib == null ? false : vib.hasVibrator();
        if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
            am.setRingerMode(hasVib
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }
    }

    private static long[] getLongPressVibePattern(Context context) {
        if (Settings.System.getInt(context.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 0) {
            return null;
        }

        int[] defaultPattern = context.getResources().getIntArray(
                com.android.internal.R.array.config_longPressVibePattern);
        if (defaultPattern == null) {
            return null;
        }

        long[] pattern = new long[defaultPattern.length];
        for (int i = 0; i < defaultPattern.length; i++) {
            pattern[i] = defaultPattern[i];
        }

        return pattern;
    }

    private static void sendToSleep(Context context) {
        final PowerManager pm;
        pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }

    SparseArray<Parcelable> mStateContainer = new SparseArray<Parcelable>();

    private void maybeCreateKeyguardLocked(boolean enableScreenRotation, boolean force,
            Bundle options) {
        if (mKeyguardHost != null) {
            mKeyguardHost.saveHierarchyState(mStateContainer);
        }

        if (mKeyguardHost == null) {
            if (DEBUG) Log.d(TAG, "keyguard host is null, creating it...");

            mKeyguardHost = new ViewManagerHost(mContext);

            int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                    | WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;

            if (!mNeedsInput) {
                flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }

            final int stretch = ViewGroup.LayoutParams.MATCH_PARENT;
            final int type = WindowManager.LayoutParams.TYPE_KEYGUARD;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    stretch, stretch, type, flags, PixelFormat.TRANSLUCENT);
            lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            lp.windowAnimations = R.style.Animation_LockScreen;
            lp.screenOrientation = enableScreenRotation ?
                    ActivityInfo.SCREEN_ORIENTATION_USER : ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;

            if (ActivityManager.isHighEndGfx()) {
                lp.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                lp.privateFlags |=
                        WindowManager.LayoutParams.PRIVATE_FLAG_FORCE_HARDWARE_ACCELERATED;
            }
            lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SET_NEEDS_MENU_KEY;
            lp.inputFeatures |= WindowManager.LayoutParams.INPUT_FEATURE_DISABLE_USER_ACTIVITY;
            lp.setTitle("Keyguard");
            mWindowLayoutParams = lp;
            mViewManager.addView(mKeyguardHost, lp);

            KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mBackgroundChanger);
        }

        if (force || mKeyguardView == null) {
            mKeyguardHost.setCustomBackground(null);
            mKeyguardHost.removeAllViews();
            inflateKeyguardView(options);
            mKeyguardView.requestFocus();
        }
        updateUserActivityTimeoutInWindowLayoutParams();
        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);

        mKeyguardHost.restoreHierarchyState(mStateContainer);
    }

    private void inflateKeyguardView(Bundle options) {
        View v = mKeyguardHost.findViewById(R.id.keyguard_host_view);
        if (v != null) {
            mKeyguardHost.removeView(v);
        }
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.keyguard_host_view, mKeyguardHost, true);
        mKeyguardView = (KeyguardHostView) view.findViewById(R.id.keyguard_host_view);
        mKeyguardView.setLockPatternUtils(mLockPatternUtils);
        mKeyguardView.setViewMediatorCallback(mViewMediatorCallback);
        mKeyguardView.initializeSwitchingUserState(options != null &&
                options.getBoolean(IS_SWITCHING_USER));

        // HACK
        // The keyguard view will have set up window flags in onFinishInflate before we set
        // the view mediator callback. Make sure it knows the correct IME state.
        if (mViewMediatorCallback != null) {
            KeyguardPasswordView kpv = (KeyguardPasswordView) mKeyguardView.findViewById(
                    R.id.keyguard_password_view);

            if (kpv != null) {
                mViewMediatorCallback.setNeedsInput(kpv.needsInput());
            }
        }

        if (options != null) {
            int widgetToShow = options.getInt(LockPatternUtils.KEYGUARD_SHOW_APPWIDGET,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetToShow != AppWidgetManager.INVALID_APPWIDGET_ID) {
                mKeyguardView.goToWidget(widgetToShow);
            }
        }
    }

    public void updateUserActivityTimeout() {
        updateUserActivityTimeoutInWindowLayoutParams();
        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
    }

    private void updateUserActivityTimeoutInWindowLayoutParams() {
        // Use the user activity timeout requested by the keyguard view, if any.
        if (mKeyguardView != null) {
            long timeout = mKeyguardView.getUserActivityTimeout();
            if (timeout >= 0) {
                mWindowLayoutParams.userActivityTimeout = timeout;
                return;
            }
        }

        // Otherwise, use the default timeout.
        mWindowLayoutParams.userActivityTimeout = KeyguardViewMediator.AWAKE_INTERVAL_DEFAULT_MS;
    }

    private void maybeEnableScreenRotation(boolean enableScreenRotation) {
        // TODO: move this outside
        if (enableScreenRotation) {
            if (DEBUG) Log.d(TAG, "Rotation sensor for lock screen On!");
            mWindowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
        } else {
            if (DEBUG) Log.d(TAG, "Rotation sensor for lock screen Off!");
            mWindowLayoutParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
        }
        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
    }

    void updateShowWallpaper(boolean show) {
        if (show) {
            mWindowLayoutParams.flags |= WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        } else {
            mWindowLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        }

        mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
    }

    public void setNeedsInput(boolean needsInput) {
        mNeedsInput = needsInput;
        if (mWindowLayoutParams != null) {
            if (needsInput) {
                mWindowLayoutParams.flags &=
                    ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            } else {
                mWindowLayoutParams.flags |=
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }

            try {
                mViewManager.updateViewLayout(mKeyguardHost, mWindowLayoutParams);
            } catch (java.lang.IllegalArgumentException e) {
                // TODO: Ensure this method isn't called on views that are changing...
                Log.w(TAG,"Can't update input method on " + mKeyguardHost + " window not attached");
            }
        }
    }

    /**
     * Reset the state of the view.
     */
    public synchronized void reset(Bundle options) {
        if (DEBUG) Log.d(TAG, "reset()");
        // User might have switched, check if we need to go back to keyguard
        // TODO: It's preferable to stay and show the correct lockscreen or unlock if none
        maybeCreateKeyguardLocked(shouldEnableScreenRotation(), true, options);
    }

    public synchronized void onScreenTurnedOff() {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOff()");
        mScreenOn = false;
        if (mKeyguardView != null) {
            mKeyguardView.onScreenTurnedOff();
        }
    }

    public synchronized void onScreenTurnedOn(final IKeyguardShowCallback callback) {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOn()");
        mScreenOn = true;

        // If keyguard is not showing, we need to inform PhoneWindowManager with a null
        // token so it doesn't wait for us to draw...
        final IBinder token = isShowing() ? mKeyguardHost.getWindowToken() : null;

        if (DEBUG && token == null) Slog.v(TAG, "send wm null token: "
                + (mKeyguardHost == null ? "host was null" : "not showing"));

        if (mKeyguardView != null) {
            mKeyguardView.onScreenTurnedOn();

            // Caller should wait for this window to be shown before turning
            // on the screen.
            if (callback != null) {
                if (mKeyguardHost.getVisibility() == View.VISIBLE) {
                    // Keyguard may be in the process of being shown, but not yet
                    // updated with the window manager...  give it a chance to do so.
                    mKeyguardHost.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                callback.onShown(token);
                            } catch (RemoteException e) {
                                Slog.w(TAG, "Exception calling onShown():", e);
                            }
                        }
                    });
                } else {
                    try {
                        callback.onShown(token);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Exception calling onShown():", e);
                    }
                }
            }
        } else if (callback != null) {
            try {
                callback.onShown(token);
            } catch (RemoteException e) {
                Slog.w(TAG, "Exception calling onShown():", e);
            }
        }
    }

    public synchronized void verifyUnlock() {
        if (DEBUG) Log.d(TAG, "verifyUnlock()");
        show(null);
        mKeyguardView.verifyUnlock();
    }

    /**
     * Hides the keyguard view
     */
    public synchronized void hide() {
        if (DEBUG) Log.d(TAG, "hide()");

        if (mKeyguardHost != null) {
            mKeyguardHost.setVisibility(View.GONE);

            // We really only want to preserve keyguard state for configuration changes. Hence
            // we should clear state of widgets (e.g. Music) when we hide keyguard so it can
            // start with a fresh state when we return.
            mStateContainer.clear();

            // Don't do this right away, so we can let the view continue to animate
            // as it goes away.
            if (mKeyguardView != null) {
                final KeyguardViewBase lastView = mKeyguardView;
                mKeyguardView = null;
                mKeyguardHost.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (KeyguardViewManager.this) {
                            lastView.cleanUp();
                            // Let go of any large bitmaps.
                            mKeyguardHost.setCustomBackground(null);
                            updateShowWallpaper(true);
                            mKeyguardHost.removeView(lastView);
                            mViewMediatorCallback.keyguardGone();
                        }
                    }
                }, HIDE_KEYGUARD_DELAY);
            }
        }
    }

    /**
     * Dismisses the keyguard by going to the next screen or making it gone.
     */
    public synchronized void dismiss() {
        if (mScreenOn) {
            mKeyguardView.dismiss();
        }
    }

    /**
     * @return Whether the keyguard is showing
     */
    public synchronized boolean isShowing() {
        return (mKeyguardHost != null && mKeyguardHost.getVisibility() == View.VISIBLE);
    }

    public void showAssistant() {
        if (mKeyguardView != null) {
            mKeyguardView.showAssistant();
        }
    }

    public void dispatch(MotionEvent event) {
        if (mKeyguardView != null) {
            mKeyguardView.dispatch(event);
        }
    }

    public void launchCamera() {
        if (mKeyguardView != null) {
            mKeyguardView.launchCamera();
        }
    }
}
