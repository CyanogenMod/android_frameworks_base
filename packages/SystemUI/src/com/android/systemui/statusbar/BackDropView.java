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

package com.android.systemui.statusbar;

import android.animation.Animator;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.policy.MediaMonitor;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

/**
 * A view who contains media artwork.
 */
public class BackDropView extends FrameLayout implements Palette.PaletteAsyncListener,
        ValueAnimator.AnimatorUpdateListener {
    final static String TAG = BackDropView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private PhoneStatusBar mPhoneStatusBar;
    private NotificationStackScrollLayout mStack;

    // the length to animate the visualizer in and out
    private static final int VISUALIZER_ANIMATION_DURATION_IN = 300;
    private static final int VISUALIZER_ANIMATION_DURATION_OUT = 0;
    private Runnable mOnVisibilityChangedRunnable;

    private VisualizerView mVisualizer;
    private boolean mScreenOn;
    private boolean mLinked;
    private boolean mVisualizerEnabled;
    private boolean mPowerSaveModeEnabled;
    private SettingsObserver mSettingsObserver;
    private boolean mTouching;
    private MediaMonitor mMediaMonitor;
    private Handler mHandler;
    private LockscreenBarEqRenderer mBarRenderer;
    private ValueAnimator mVisualizerColorAnimator;
    private boolean mAnimatingOn;

    public BackDropView(Context context) {
        super(context);
    }

    public BackDropView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BackDropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BackDropView(Context context, AttributeSet attrs, int defStyleAttr,
                        int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mLinked;
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && mOnVisibilityChangedRunnable != null) {
            mOnVisibilityChangedRunnable.run();
        }
    }

    public void setOnVisibilityChangedRunnable(Runnable runnable) {
        mOnVisibilityChangedRunnable = runnable;
    }

    public void setService(PhoneStatusBar service, NotificationStackScrollLayout notificationStack) {
        mPhoneStatusBar = service;
        mStack = notificationStack;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContext.registerReceiver(mReceiver, new IntentFilter(
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGING));
        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(new Handler());
        }
        mSettingsObserver.observe();

        HandlerThread handlerThread = new HandlerThread(TAG,
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
        mMediaMonitor = new MediaMonitor(mContext) {
            @Override
            public void onPlayStateChanged(boolean playing) {
                if (playing) {
                    if (mVisualizerColorAnimator != null && !mVisualizerColorAnimator.isStarted()) {
                        mVisualizerColorAnimator.start();
                    }
                    requestVisualizer(true, 500);
                } else {
                    requestVisualizer(false, 0);
                }
            }
        };
        mMediaMonitor.setListening(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        mMediaMonitor.setListening(false);
        haltVisualizer();
        mHandler.getLooper().quit();
        mHandler = null;

        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (ActivityManager.isHighEndGfx()) {
            mVisualizer = (VisualizerView) findViewById(R.id.visualizerView);
            if (mVisualizer != null) {
                Paint paint = new Paint();
                Resources res = mContext.getResources();
                paint.setStrokeWidth(res.getDimensionPixelSize(
                        R.dimen.kg_visualizer_path_stroke_width));
                paint.setAntiAlias(true);
                paint.setColor(res.getColor(R.color.equalizer_fill_color));
                paint.setPathEffect(new DashPathEffect(new float[]{
                        res.getDimensionPixelSize(R.dimen.kg_visualizer_path_effect_1),
                        res.getDimensionPixelSize(R.dimen.kg_visualizer_path_effect_2)
                }, 0));

                int bars = res.getInteger(R.integer.kg_visualizer_divisions);
                mBarRenderer = new LockscreenBarEqRenderer(bars, paint,
                        res.getInteger(R.integer.kg_visualizer_db_fuzz),
                        res.getInteger(R.integer.kg_visualizer_db_fuzz_factor));
                mVisualizer.addRenderer(mBarRenderer);
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    public void updateVisualizerColor(Bitmap artwork) {
        if (artwork != null) {
            Palette.generateAsync(artwork, this);
        } else {
            if (mVisualizerColorAnimator != null) {
                mVisualizerColorAnimator.cancel();
            }
            mVisualizerColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                    mBarRenderer.mPaint.getColor(),
                    getResources().getColor(R.color.equalizer_fill_color)
            );
            mVisualizerColorAnimator.setStartDelay(500);
            mVisualizerColorAnimator.setDuration(1000);
            mVisualizerColorAnimator.addUpdateListener(this);
            if (mMediaMonitor.isAnythingPlaying()) {
                mVisualizerColorAnimator.start();
            }
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        if (mVisualizerColorAnimator != null) {
            mVisualizerColorAnimator.cancel();
        }
        mVisualizerColorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(),
                mBarRenderer.mPaint.getColor(),
                palette.getLightVibrantColor(getResources().getColor(R.color.equalizer_fill_color))
        );
        mVisualizerColorAnimator.setStartDelay(500);
        mVisualizerColorAnimator.setDuration(1000);
        mVisualizerColorAnimator.addUpdateListener(this);
        if (mMediaMonitor.isAnythingPlaying()) {
            mVisualizerColorAnimator.start();
        }
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        mBarRenderer.mPaint.setColor((Integer) animation.getAnimatedValue());
    }

    public void setTouching(boolean touching) {
        if (mTouching != touching) {
            if (DEBUG) Log.d(TAG, "setTouching() called with touching = [" + touching + "]");
            mTouching = touching;
            if (mTouching) {
                // immediately hide visualizer
                requestVisualizer(false, 0);
            } else {
                // we want to avoid requesting the visualizer when something is paused right here
                mHandler.postDelayed(mResumeVisualizerIfPlayingRunnable, 500);
            }
        }
    }

    /**
     * Show or hide the visualizer
     *
     * @param show True to request visualizer to be visible, false to hide it.
     *             request may not happen based on other conditions.
     *             Pass null to decisively pick one, or the other, based on the current state.
     * @param delay How long to wait before doing the requested action.
     */
    public void requestVisualizer(Boolean show, int delay) {
        if (mVisualizer == null || !mVisualizerEnabled || mPowerSaveModeEnabled) {
            return;
        }
        mHandler.removeCallbacks(mStartVisualizer);
        mHandler.removeCallbacks(mStopVisualizer);

        if (DEBUG) Log.v(TAG, "requestVisualizer(show: " + show + ", delay: " + delay + ")");
        if ((show == null || show) && mScreenOn
                && mPhoneStatusBar.getBarState() == StatusBarState.KEYGUARD
                && !mPhoneStatusBar.isKeyguardFadingAway()
                && !mPhoneStatusBar.isGoingToNotificationShade()
                && !mPhoneStatusBar.isInLaunchTransition()
                && !mPhoneStatusBar.isQsExpanded()
                && mPhoneStatusBar.getCurrentMediaNotificationKey() != null
                && mStack.getActivatedChild() == null) {
            if (show == null) {
                // be a little more aggresive when we're unsure of what to expect
                if (!mMediaMonitor.isAnythingPlaying()) {
                    return;
                }
                if (mTouching) {
                    mHandler.postDelayed(mResumeVisualizerIfPlayingRunnable, 500);
                    return;
                }
            }

            if (DEBUG) Log.d(TAG, "--> starting visualizer");
            mHandler.postDelayed(mStartVisualizer, delay);
        } else if ((show == null || !show)) {
            if (DEBUG) Log.d(TAG, "--> stopping visualizer");
            mHandler.postDelayed(mStopVisualizer, delay);
        }
    }

    private void haltVisualizer() {
        if (mVisualizerColorAnimator != null) {
            mVisualizerColorAnimator.end();
            mVisualizerColorAnimator = null;
        }
        mHandler.removeCallbacks(mResumeVisualizerIfPlayingRunnable);
        mHandler.removeCallbacks(mStartVisualizer);
        mHandler.removeCallbacks(mStopVisualizer);
        mHandler.post(mStopVisualizer);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                if (mPowerSaveModeEnabled) {
                    mHandler.post(mStopVisualizer);
                } else {
                    mHandler.post(mResumeVisualizerIfPlayingRunnable);
                }
            }
        }
    };

    private final Runnable mStartVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "mStartVisualizer");

            mHandler.removeCallbacks(mStartVisualizer);

            mVisualizer.animate().cancel();
            mVisualizer.animate()
                    .alpha(1f)
                    .setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mAnimatingOn = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mAnimatingOn = false;
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            mAnimatingOn = false;
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    })
                    .setDuration(VISUALIZER_ANIMATION_DURATION_IN);
            AsyncTask.execute(mLinkVisualizerRunnable);
        }
    };

    private final Runnable mStopVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "mStopVisualizer");

            mHandler.removeCallbacks(mStopVisualizer);

            mVisualizer.animate().cancel();
            mVisualizer.animate()
                    .alpha(0f)
                    .setDuration(VISUALIZER_ANIMATION_DURATION_OUT);
            AsyncTask.execute(mUnLinkVisualizerRunnable);
        }
    };

    private final Runnable mLinkVisualizerRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null && !mLinked) {
                mVisualizer.link(0);
                mLinked = true;
            }
        }
    };

    private final Runnable mUnLinkVisualizerRunnable = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null && mLinked) {
                mVisualizer.unlink();
                mLinked = false;
            }
        }
    };

    private Runnable mResumeVisualizerIfPlayingRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.i(TAG, "mResumeVisualizerIfPlayingRunnable");
            if (mMediaMonitor.isAnythingPlaying()) {
                requestVisualizer(null, 250);
            }
        }
    };

    private final KeyguardUpdateMonitorCallback mUpdateMonitorCallback =
            new KeyguardUpdateMonitorCallback() {
                @Override
                public void onUserSwitchComplete(int userId) {
                }

                @Override
                public void onScreenTurnedOn() {
                    mScreenOn = true;
                    mHandler.postDelayed(mResumeVisualizerIfPlayingRunnable, 200);
                }

                @Override
                public void onScreenTurnedOff(int why) {
                    mScreenOn = false;
                    requestVisualizer(false, 0);
                }

                @Override
                public void onKeyguardVisibilityChanged(boolean showing) {
                    mMediaMonitor.setListening(showing);

                    if (!showing) {
                        haltVisualizer();
                    } else if (mScreenOn) {
                        // in case keyguard is toggled back on even though screen never went off
                        mHandler.postDelayed(mResumeVisualizerIfPlayingRunnable, 200);
                    }
                }
            };

    private class LockscreenBarEqRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;
        private int mDbFuzz;
        private int mDbFuzzFactor;

        private boolean mDrawEmpty;
        int mFramesSinceLastEmptyRender;

        /**
         * Renders the FFT data as a series of lines, in histogram form
         *
         * @param divisions - must be a power of 2. Controls how many lines to draw
         * @param paint     - Paint to draw lines with
         * @param dbfuzz    - final dB display adjustment
         * @param dbFactor  - dbfuzz is multiplied by dbFactor.
         */
        public LockscreenBarEqRenderer(int divisions, Paint paint, int dbfuzz, int dbFactor) {
            super();
            if (DEBUG) {
                Log.d(TAG, "Lockscreen EQ Renderer; divisions:" + divisions + ", dbfuzz: "
                        + dbfuzz + "dbFactor: " + dbFactor);
            }
            mDivisions = divisions;
            mPaint = paint;
            mDbFuzz = dbfuzz;
            mDbFuzzFactor = dbFactor;
        }

        @Override
        public void onRender(Canvas canvas, AudioData data, Rect rect) {
            // Do nothing, we only display FFT data
        }

        @Override
        public void onRender(Canvas canvas, FFTData data, Rect rect) {
            if (isDataEmpty(data)) {
                mDrawEmpty = true;
                mFramesSinceLastEmptyRender = 0;
            } else {
                mFramesSinceLastEmptyRender++;
                for (int i = 0; i < data.bytes.length / mDivisions; i++) {
                    mFFTPoints[i * 4] = i * 4 * mDivisions;
                    mFFTPoints[i * 4 + 2] = i * 4 * mDivisions;
                    byte rfk = data.bytes[mDivisions * i];
                    byte ifk = data.bytes[mDivisions * i + 1];
                    float magnitude = (rfk * rfk + ifk * ifk);
                    int dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                    mFFTPoints[i * 4 + 1] = rect.height();
                    mFFTPoints[i * 4 + 3] = rect.height() - ((dbValue * mDbFuzzFactor) + mDbFuzz);
                }
            }

             /*
              * When transitioning songs, we get a bunch of empty frames, followed by 1 frame
              * as the track switch occurs, then some more empty frames until the song starts.
              *
              * We skip the first frame in between empty frames here to avoid drawing
              * anything when we're switching songs
              */
            mDrawEmpty = mFramesSinceLastEmptyRender == 1;

            if (mFramesSinceLastEmptyRender == 1) {
                //
                mDrawEmpty = true;
            }

            // if the 'on' animation is currently running, then always draw
            if (mDrawEmpty && !mAnimatingOn) {
                mVisualizer.setDrawingEnabled(false);
            } else {
                mVisualizer.setDrawingEnabled(true);
                canvas.drawLines(mFFTPoints, mPaint);
            }
        }

        private boolean isDataEmpty(FFTData data) {
            for (int i = 0; i < data.bytes.length; i++) {
                if (data.bytes[i] != 0) {
                    return false;
                }
            }
            return true;
        }

    }

    private class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Secure.getUriFor(
                            Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED),
                    false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            mVisualizerEnabled = Settings.Secure.getIntForUser(resolver,
                    Settings.Secure.LOCKSCREEN_VISUALIZER_ENABLED, 1, UserHandle.USER_CURRENT) != 0;

        }
    }
}
