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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.pheelicks.visualizer.AudioData;
import com.pheelicks.visualizer.FFTData;
import com.pheelicks.visualizer.VisualizerView;
import com.pheelicks.visualizer.renderer.Renderer;

/**
 * A view who contains media artwork.
 */
public class BackDropView extends FrameLayout implements Palette.PaletteAsyncListener {
    private Runnable mOnVisibilityChangedRunnable;

    public BackDropView(Context context) {
        super(context);
    }

    public BackDropView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BackDropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BackDropView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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

    private boolean mLinked = false;
    private boolean mVisible = false;
    private boolean mPlaying = false;
    private boolean mAnimating = false;
    private boolean mTouching = false;
    private boolean mPowerSaveMode = false;
    private boolean mQsExpanded = false;
    private int mColor;

    private VisualizerView mVisualizer;
    private TileBarGraphRenderer mBarRenderer;
    private ObjectAnimator mVisualizerColorAnimator;

    private final Runnable mLinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                mVisualizer.link(0);
            }
        }
    };

    private final Runnable mUnlinkVisualizer = new Runnable() {
        @Override
        public void run() {
            if (mVisualizer != null) {
                mVisualizer.unlink();
            }
        }
    };

    private static class TileBarGraphRenderer extends Renderer {
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

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mVisualizer = (VisualizerView) findViewById(R.id.visualizerView);
        mVisualizer.setEnabled(false);

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
        mBarRenderer = new TileBarGraphRenderer(bars, paint,
                res.getInteger(R.integer.kg_visualizer_db_fuzz),
                res.getInteger(R.integer.kg_visualizer_db_fuzz_factor));
        mVisualizer.addRenderer(mBarRenderer);
    }

    public void setVisible(boolean visible) {
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
                mVisualizerColorAnimator = ObjectAnimator.ofArgb(mBarRenderer.mPaint, "color",
                        mBarRenderer.mPaint.getColor(), mColor);
                mVisualizerColorAnimator.setStartDelay(500);
                mVisualizerColorAnimator.setDuration(1200);
                mVisualizerColorAnimator.start();
            } else {
                mBarRenderer.mPaint.setColor(mColor);
            }
        }
    }

    private void checkStateChanged() {
        if (mVisible && mPlaying && !mAnimating && !mTouching && !mPowerSaveMode && !mQsExpanded) {
            if (!mLinked) {
                mVisualizer.animate().alpha(1f).setDuration(300);
                AsyncTask.execute(mLinkVisualizer);
                mLinked = true;
            }
        } else {
            if (mLinked) {
                mVisualizer.animate().alpha(0f).setDuration(0);
                AsyncTask.execute(mUnlinkVisualizer);
                mLinked = false;
            }
        }
    }
}
