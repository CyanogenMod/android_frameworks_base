/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.view.View;

public class VisualizerView
       extends View {

    private boolean mLinked = false;
    private Visualizer mVisualizer;
    private ValueAnimator[] mValueAnimators = new ValueAnimator[32];
    private float[] mFFTPoints = new float[128];
    private Paint mPaint = new Paint();
    private ObjectAnimator mVisualizerColorAnimator;

    private boolean mEnabled = false;
    private boolean mScreenOn = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mPowerSaveMode = false;
    private int mColor = Color.TRANSPARENT;

    private final Visualizer.OnDataCaptureListener mVisualizerListener =
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
                rfk = fft[i * 2 + 2];
                ifk = fft[i * 2 + 3];
                magnitude = rfk * rfk + ifk * ifk;
                dbValue = magnitude > 0 ? (int) (10 * Math.log10(magnitude)) : 0;

                mValueAnimators[i].cancel();
                mValueAnimators[i].setFloatValues(mFFTPoints[i * 4 + 1],
                        mFFTPoints[3] - (dbValue * 16f));
                mValueAnimators[i].start();
            }
        }
    };

    private final Palette.PaletteAsyncListener mPaletteAsyncListener =
            new Palette.PaletteAsyncListener() {
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
    };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mLinked) {
                return;
            }

            try {
                mVisualizer = new Visualizer(0);
            } catch (Exception e) {
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(66);
            mVisualizer.setDataCaptureListener(mVisualizerListener, Visualizer.getMaxCaptureRate(),
                    false, true);
            mVisualizer.setEnabled(true);

            mLinked = true;
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (!mLinked) {
                return;
            }

            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;

            mLinked = false;
        }
    };

    public VisualizerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);

        for (int i = 0; i < 32; i++) {
            final int j = i * 4 + 1;
            mValueAnimators[i] = new ValueAnimator();
            mValueAnimators[i].setDuration(128);
            mValueAnimators[i].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mFFTPoints[j] = (float) animation.getAnimatedValue();
                }
            });
        }

        mValueAnimators[31].addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                postInvalidate();
            }
        });
    }

    public VisualizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public VisualizerView(Context context) {
        this(context, null, 0, 0);
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

        float barUnit = (float) w / 32;
        float barWidth = barUnit * 8 / 9;
        barUnit = barWidth + (barUnit - barWidth) * 32 / 31;

        mPaint.setStrokeWidth(barWidth);

        for (int i = 0; i < 32; i++) {
            mFFTPoints[i * 4] = mFFTPoints[i * 4 + 2] = i * barUnit + (barWidth / 2);
            mFFTPoints[i * 4 + 3] = h;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mLinked) {
            canvas.drawLines(mFFTPoints, mPaint);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mLinked;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            checkStateChanged();
        }
    }

    public void setScreenOn(boolean screenOn) {
        if (mScreenOn != screenOn) {
            mScreenOn = screenOn;
            checkStateChanged();
        }
    }

    public void setVisible(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setPowerSaveMode(boolean powerSaveMode) {
        if (mPowerSaveMode != powerSaveMode) {
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    public void setBitmap(Bitmap bitmap) {
        if (mEnabled && bitmap != null) {
            Palette.generateAsync(bitmap, mPaletteAsyncListener);
        } else {
            setColor(Color.TRANSPARENT);
        }
    }

    private void setColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = Color.WHITE;
        }

        color = Color.argb(191, Color.red(color), Color.green(color), Color.blue(color));

        if (mColor != color) {
            mColor = color;

            if (mLinked) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }

                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color",
                        mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(512);
                mVisualizerColorAnimator.setDuration(1024);
                mVisualizerColorAnimator.start();
            } else {
                mPaint.setColor(mColor);
            }
        }
    }

    private void checkStateChanged() {
        if (mEnabled && mScreenOn && mVisible && mPlaying && !mPowerSaveMode) {
            if (!mLinked) {
                AsyncTask.execute(mLinkVisualizer);
                animate().alpha(1f).setDuration(1024);
            }
        } else {
            if (mLinked) {
                AsyncTask.execute(mUnlinkVisualizer);
                animate().alpha(0f).setDuration(0);
            }
        }
    }
}
