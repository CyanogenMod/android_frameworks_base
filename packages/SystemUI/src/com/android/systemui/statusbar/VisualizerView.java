/*
* Copyright (C) 2015 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android.systemui.statusbar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import cyanogenmod.providers.CMSettings;

public class VisualizerView extends View implements Palette.PaletteAsyncListener,
        KeyguardMonitor.Callback {

    private static final String TAG = VisualizerView.class.getSimpleName();
    private static final boolean DEBUG = false;

    private Paint mPaint;
    private Visualizer mVisualizer;
    private ObjectAnimator mVisualizerColorAnimator;

    private ValueAnimator[] mValueAnimators;
    private float[] mFFTPoints;

    private boolean mVisualizerEnabled = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mPowerSaveMode = false;
    private boolean mDisplaying = false; // the state we're animating to
    private boolean mDozing = false;
    private boolean mOccluded = false;

    private int mColor;
    private Bitmap mCurrentBitmap;

    private KeyguardMonitor mKeyguardMonitor;
    private SettingsObserver mObserver;

    private Visualizer.OnDataCaptureListener mVisualizerListener =
            new Visualizer.OnDataCaptureListener() {
        byte rfk, ifk;
        int dbValue;
        float magnitude;

        @Override
        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
        }

        @Override
        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            for (int i = 0; i < 32; i++) {
                mValueAnimators[i].cancel();
                rfk = fft[i * 2 + 2];
                ifk = fft[i * 2 + 3];
                magnitude = rfk * rfk + ifk * ifk;
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                        mFFTPoints[3] - (dbValue * 16f));
                mValueAnimators[i].start();
            }
        }
    };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.w(TAG, "+++ mLinkVisualizer run()");
            }

            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                Log.e(TAG, "error initializing visualizer", e);
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(mVisualizerListener,Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);

            if (DEBUG) {
                Log.w(TAG, "--- mLinkVisualizer run()");
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) {
                Log.w(TAG, "+++ mUnlinkVisualizer run(), mVisualizer: " + mVisualizer);
            }
            if (mVisualizer != null) {
                mVisualizer.setEnabled(false);
                mVisualizer.release();
                mVisualizer = null;
            }
            if (DEBUG) {
                Log.w(TAG, "--- mUninkVisualizer run()");
            }
        }
    };

    public VisualizerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mColor = Color.TRANSPARENT;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);

        mFFTPoints = new float[128];
        mValueAnimators = new ValueAnimator[32];
        for (int i = 0; i < 32; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                    postInvalidate();
                }
            });
        }
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0);
    }

    @Override
    public void onKeyguardChanged() {
        updateViewVisibility();
    }

    private void updateViewVisibility() {
        setVisibility(mKeyguardMonitor != null && mKeyguardMonitor.isShowing()
                && mVisualizerEnabled ? View.VISIBLE : View.GONE);
        checkStateChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mKeyguardMonitor != null) {
            mKeyguardMonitor.addCallback(this);
        }
        mObserver = new SettingsObserver(new Handler());
        mObserver.observe();
        mObserver.update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mKeyguardMonitor != null) {
            mKeyguardMonitor.removeCallback(this);
        }
        mObserver.unobserve();
        mObserver = null;
        mCurrentBitmap = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float barUnit = w / 32f;
        float barWidth = barUnit * 8f / 9f;
        barUnit = barWidth + (barUnit - barWidth) * 32f / 31f;
        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 1] = h;
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mVisualizerEnabled && mDisplaying;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mVisualizer != null) {
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    public void setKeyguardMonitor(KeyguardMonitor kgm) {
        mKeyguardMonitor = kgm;
        if (isAttachedToWindow()) {
            // otherwise we might never register ourselves
            mKeyguardMonitor.removeCallback(this);
            mKeyguardMonitor.addCallback(this);
            updateViewVisibility();
        }
    }

    public void setVisible(boolean visible) {
        if (mVisible != visible) {
            if (DEBUG) {
                Log.i(TAG, "setVisible() called with visible = [" + visible + "]");
            }
            mVisible = visible;
            checkStateChanged();
        }
    }

    public void setDozing(boolean dozing) {
        if (mDozing != dozing) {
            if (DEBUG) {
                Log.i(TAG, "setDozing() called with dozing = [" + dozing + "]");
            }
            mDozing = dozing;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            if (DEBUG) {
                Log.i(TAG, "setPlaying() called with playing = [" + playing + "]");
            }
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setPowerSaveMode(boolean powerSaveMode) {
        if (mPowerSaveMode != powerSaveMode) {
            if (DEBUG) {
                Log.i(TAG, "setPowerSaveMode() called with powerSaveMode = [" + powerSaveMode + "]");
            }
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    public void setOccluded(boolean occluded) {
        if (mOccluded != occluded) {
            if (DEBUG) {
                Log.i(TAG, "setOccluded() called with occluded = [" + occluded + "]");
            }
            mOccluded = occluded;
            checkStateChanged();
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mCurrentBitmap == bitmap) {
            return;
        }
        mCurrentBitmap = bitmap;
        if (bitmap != null) {
            Palette.generateAsync(bitmap, this);
        } else {
            setColor(Color.TRANSPARENT);
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        int color = Color.TRANSPARENT;

        color = palette.getVibrantColor(color);
        if (color == Color.TRANSPARENT) {
            color = palette.getLightVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
            }
        }

        setColor(color);
    }

    private void setColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = Color.WHITE;
        }

        color = Color.argb(140, Color.red(color), Color.green(color), Color.blue(color));

        if (mColor != color) {
            mColor = color;

            if (mVisualizer != null) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(600);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mPaint.setColor(mColor);
            }
        }
    }

    private void checkStateChanged() {
        if (getVisibility() == View.VISIBLE && mVisible && mPlaying && !mDozing && !mPowerSaveMode
                && mVisualizerEnabled && !mOccluded) {
            if (!mDisplaying) {
                mDisplaying = true;
                AsyncTask.execute(mLinkVisualizer);
                animate()
                        .alpha(1f)
                        .withEndAction(null)
                        .setDuration(800);
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false;
                if (mVisible) {
                    animate()
                            .alpha(0f)
                            .withEndAction(mUnlinkVisualizer)
                            .setDuration(600);
                } else {
                    AsyncTask.execute(mUnlinkVisualizer);
                    animate().
                            alpha(0f)
                            .withEndAction(null)
                            .setDuration(0);
                }
            }
        }
    }

    private class SettingsObserver extends UserContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void update() {
            mVisualizerEnabled = CMSettings.Secure.getInt(getContext().getContentResolver(),
                    CMSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED, 1) != 0;
            checkStateChanged();
            updateViewVisibility();
        }

        @Override
        protected void observe() {
            super.observe();
            getContext().getContentResolver().registerContentObserver(
                    CMSettings.Secure.getUriFor(CMSettings.Secure.LOCKSCREEN_VISUALIZER_ENABLED),
                    false, this, UserHandle.USER_CURRENT);
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            getContext().getContentResolver().unregisterContentObserver(this);
        }
    }
}
