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
 * limitations under the License
 */

package com.android.systemui.statusbar;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.Animator.AnimatorListener;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.media.audiofx.Visualizer;
import android.media.audiofx.Visualizer.OnDataCaptureListener;
import android.os.AsyncTask;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.util.Log;

import com.android.systemui.R;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.renderer.Renderer;

/**
 * A scrim view that holds the lockscreen visualizer
 */
public class VisualizerScrimView extends ScrimView implements Palette.PaletteAsyncListener {

    private static final String TAG = "ScrimViewWithVisualizer";

    private Rect mRect = new Rect();
    private Visualizer mVisualizer;
    private int mAudioSessionId;

    private TileBarGraphRenderer mRenderer;

    private Paint mFadePaint = new Paint();
    private Paint mAlphaPaint = new Paint();
    private Matrix mMatrix;
    private AudioData mAudioData;
    private FFTData mFftData;

    private Bitmap mVisualizerBitmap;
    private Canvas mVisualizerCanvas;

    private boolean mLinked = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mAnimating = false;
    private boolean mTouching = false;
    private boolean mPowerSaveMode = false;
    private boolean mQsExpanded = false;
    private int mColor;

    private ObjectAnimator mVisualizerColorAnimator;
    private ObjectAnimator mAlphaAnimator;

    private AnimatorListener mAlphaAnimatorListener = new AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            invalidate();
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }
    };

    private final AnimatorUpdateListener mAlphaUpdateListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            invalidate();
        }
    };

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            link(0);
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            unlink();
        }
    };

    public VisualizerScrimView(Context context) {
        this(context, null);
    }

    public VisualizerScrimView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VisualizerScrimView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public VisualizerScrimView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mAudioData = new AudioData(null);
        mFftData = new FFTData(null);

        mMatrix = new Matrix();

        mFadePaint.setColor(Color.argb(200, 255, 255, 255));
        mFadePaint.setXfermode(new PorterDuffXfermode(Mode.MULTIPLY));


        Resources res = mContext.getResources();
        mColor = res.getColor(R.color.equalizer_fill_color);
        Paint paint = new Paint();
        paint.setStrokeWidth(res.getDimensionPixelSize(R.dimen.kg_visualizer_path_stroke_width));
        paint.setAntiAlias(true);
        paint.setColor(mColor);
        paint.setPathEffect(new DashPathEffect(new float[]{
                res.getDimensionPixelSize(R.dimen.kg_visualizer_path_effect_1),
                res.getDimensionPixelSize(R.dimen.kg_visualizer_path_effect_2)
        }, 0));

        int bars = res.getInteger(R.integer.kg_visualizer_divisions);
        mRenderer = new TileBarGraphRenderer(bars, paint,
                res.getInteger(R.integer.kg_visualizer_db_fuzz),
                res.getInteger(R.integer.kg_visualizer_db_fuzz_factor));
    }

    private void link(int audioSessionId) {
        if (mVisualizer != null && audioSessionId != mAudioSessionId) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }

        mAudioSessionId = audioSessionId;
        if (mVisualizer == null) {
            try {
                mVisualizer = new Visualizer(audioSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Error enabling visualizer!", e);
                return;
            }
            mVisualizer.setEnabled(false);
            mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);

            // Pass through Visualizer data to VisualizerView
            OnDataCaptureListener captureListener = new OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer,
                        byte[] bytes, int samplingRate){
                    updateVisualizer(bytes);
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer,
                        byte[] bytes, int samplingRate) {
                    updateVisualizerFFT(bytes);
                }
            };

            mVisualizer.setDataCaptureListener(captureListener,
                    (int) (Visualizer.getMaxCaptureRate() * 0.75), true, true);

        }
        mVisualizer.setEnabled(true);
    }

    private void unlink() {
        if (mVisualizer != null) {
            mVisualizer.setEnabled(false);
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    public void setVisualizerVisibility(boolean visible) {
        if (mVisible != visible) {
            mVisible = visible;
            if (!mVisible) {
                mAnimating = false;
                mTouching = false;
            }
            checkStateChanged();
        }
    }

    public void setPlaying(boolean playing) {
        if (mPlaying != playing) {
            mPlaying = playing;
            checkStateChanged();
        }
    }

    public void setAnimating(boolean animating) {
        if (mAnimating != animating) {
            mAnimating = animating;
            checkStateChanged();
        }
    }

    public void setTouching(boolean touching) {
        if (mTouching != touching) {
            mTouching = touching;
            checkStateChanged();
        }
    }

    public void setPowerSaveMode(boolean powerSaveMode) {
        if (mPowerSaveMode != powerSaveMode) {
            mPowerSaveMode = powerSaveMode;
            checkStateChanged();
        }
    }

    public void setQsExpanded(boolean qsExpanded) {
        if (mQsExpanded != qsExpanded) {
            mQsExpanded = qsExpanded;
            checkStateChanged();
        }
    }

    @Override
    public void onGenerated(Palette palette) {
        int color = Color.TRANSPARENT;

        color = palette.getLightVibrantColor(color);
        if (color == Color.TRANSPARENT) {
            color = palette.getVibrantColor(color);
            if (color == Color.TRANSPARENT) {
                color = palette.getDarkVibrantColor(color);
            }
        }
        setVisualizerColor(color);
    }

    public void setBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            Palette.generateAsync(bitmap, this);
        } else {
            setVisualizerColor(Color.TRANSPARENT);
        }
    }

    private void setVisualizerColor(int color) {
        if (color == Color.TRANSPARENT) {
            color = mContext.getResources().getColor(R.color.equalizer_fill_color);
        }
        if (mColor != color) {
            mColor = color;

            if (mLinked) {
                if (mVisualizerColorAnimator != null) {
                    mVisualizerColorAnimator.cancel();
                }
                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mRenderer.mPaint, "color",
                        mRenderer.mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(500);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mRenderer.mPaint.setColor(mColor);
            }
        }
    }

    private void checkStateChanged() {
        if (mVisible && mPlaying && !mAnimating && !mTouching && !mPowerSaveMode && !mQsExpanded) {
            if (!mLinked) {
                if (mAlphaAnimator != null && mAlphaAnimator.isRunning()) {
                    mAlphaAnimator.end();
                }
                mAlphaAnimator = ObjectAnimator.ofInt(
                        mAlphaPaint, "alpha", mAlphaPaint.getAlpha(), 255);
                mAlphaAnimator.setDuration(650);
                mAlphaAnimator.addListener(mAlphaAnimatorListener);
                mAlphaAnimator.addUpdateListener(mAlphaUpdateListener);
                mAlphaAnimator.start();
                AsyncTask.execute(mLinkVisualizer);
                mLinked = true;
            }
        } else {
            if (mLinked) {
                if (mAlphaAnimator != null && mAlphaAnimator.isRunning()) {
                    mAlphaAnimator.end();
                }
                mAlphaAnimator = ObjectAnimator.ofInt(
                        mAlphaPaint, "alpha", mAlphaPaint.getAlpha(), 0);
                mAlphaAnimator.setDuration(800);
                mAlphaAnimator.addListener(mAlphaAnimatorListener);
                mAlphaAnimator.addUpdateListener(mAlphaUpdateListener);
                mAlphaAnimator.start();
                AsyncTask.execute(mUnlinkVisualizer);
                mLinked = false;
            }
        }
    }

    private void updateVisualizer(byte[] bytes) {
        mAudioData.bytes = bytes;
        invalidate();
    }

    private void updateVisualizerFFT(byte[] bytes) {
        mFftData.bytes = bytes;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mRect.set(0, 0, getWidth(), getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mVisualizer != null || (mAlphaAnimator != null && mAlphaAnimator.isRunning())) {
            if (mVisualizerBitmap == null) {
                mVisualizerBitmap = Bitmap.createBitmap(
                        canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
            }
            if (mVisualizerCanvas == null) {
                mVisualizerCanvas = new Canvas(mVisualizerBitmap);
            }

            if (mAudioData.bytes != null) {
                mRenderer.render(mVisualizerCanvas, mAudioData, mRect);
            }
            if (mFftData.bytes != null) {
                mRenderer.render(mVisualizerCanvas, mFftData, mRect);
            }

            mVisualizerCanvas.drawPaint(mFadePaint);
            canvas.drawBitmap(mVisualizerBitmap, mMatrix, mAlphaPaint);
        }
    }

    private static class TileBarGraphRenderer extends Renderer {
        private int mDivisions;
        private Paint mPaint;
        private int mDbFuzz;
        private int mDbFuzzFactor;

        public TileBarGraphRenderer(int divisions, Paint paint, int dbfuzz, int dbFactor) {
            super();
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
                mFFTPoints[i * 4 + 3] = rect.height() - (dbValue * mDbFuzzFactor + mDbFuzz);
            }

            canvas.drawLines(mFFTPoints, mPaint);
        }
    }
}
