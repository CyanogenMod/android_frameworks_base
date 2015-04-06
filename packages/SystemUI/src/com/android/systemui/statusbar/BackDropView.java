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

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.PowerManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

/**
 * A view who contains media artwork.
 */
public class BackDropView extends FrameLayout {
    final static String TAG = BackDropView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private PhoneStatusBar mPhoneStatusBar;

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
        if (!isShown()) {
            requestVisualizer(false, 0);
        }
    }

    public void setOnVisibilityChangedRunnable(Runnable runnable) {
        mOnVisibilityChangedRunnable = runnable;
    }

    public void setService(PhoneStatusBar service) {
        mPhoneStatusBar = service;
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
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mReceiver);
        requestVisualizer(false, 0);
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
                mVisualizer.addRenderer(new LockscreenBarEqRenderer(bars, paint,
                        res.getInteger(R.integer.kg_visualizer_db_fuzz),
                        res.getInteger(R.integer.kg_visualizer_db_fuzz_factor)));
            }
        }
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    public void requestVisualizer(boolean show, int delay) {
        if (mVisualizer == null || !mVisualizerEnabled || mPowerSaveModeEnabled) {
            return;
        }
        removeCallbacks(mStartVisualizer);
        removeCallbacks(mStopVisualizer);
        if (DEBUG) Log.d(TAG, "requestVisualizer(show: " + show + ", delay: " + delay + ")");
        if (show && mScreenOn
                && mPhoneStatusBar.getBarState() == StatusBarState.KEYGUARD
                && !mPhoneStatusBar.isKeyguardFadingAway()
                && !mPhoneStatusBar.isGoingToNotificationShade()
                && mPhoneStatusBar.getCurrentMediaNotificationKey() != null) {
            if (DEBUG) Log.d(TAG, "--> starting visualizer");
            postDelayed(mStartVisualizer, delay);
        } else {
            if (DEBUG) Log.d(TAG, "--> stopping visualizer");
            postDelayed(mStopVisualizer, delay);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGING.equals(intent.getAction())) {
                mPowerSaveModeEnabled = intent.getBooleanExtra(PowerManager.EXTRA_POWER_SAVE_MODE,
                        false);
                requestVisualizer(true, 0);
            }
        }
    };

    private final Runnable mStartVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "mStartVisualizer");

            mVisualizer.animate()
                    .alpha(1f)
                    .setDuration(VISUALIZER_ANIMATION_DURATION_IN);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (mVisualizer != null && !mLinked) {
                        mVisualizer.link(0);
                        mLinked = true;
                    }
                }
            });
        }
    };

    private final Runnable mStopVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "mStopVisualizer");

            mVisualizer.animate()
                    .alpha(0f)
                    .setDuration(VISUALIZER_ANIMATION_DURATION_OUT);
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    if (mVisualizer != null && mLinked) {
                        mVisualizer.unlink();
                        mLinked = false;
                    }
                }
            });
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
                    requestVisualizer(true, 300);
                }

                @Override
                public void onScreenTurnedOff(int why) {
                    mScreenOn = false;
                    requestVisualizer(false, 0);
                }

                @Override
                public void onKeyguardVisibilityChanged(boolean showing) {
                    if (!showing) {
                        requestVisualizer(false, 0);
                    }
                }
            };

    private static class LockscreenBarEqRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;
        private int mDbFuzz;
        private int mDbFuzzFactor;

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

            canvas.drawLines(mFFTPoints, mPaint);
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
