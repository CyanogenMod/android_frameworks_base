/*
 * Copyright (C) 2013 The ChameleonOS Open Source Project
 * Copyright (C) 2013 The OmniROM Project
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

package com.android.systemui.recent;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.util.Log;

public class CircleMemoryMeter extends ImageView {
    private final Handler mHandler;
    private final Context mContext;

    // state variables
    private boolean mAttached;      // whether or not attached to a window
    private long mLevel;            // current meter level

    private int mCircleSize;        // draw size of circle.
    private RectF mRectLeft;        // contains the precalculated rect used in drawArc(),

    // quiet a lot of paint variables.
    // helps to move cpu-usage from actual drawing to initialization
    private final Paint mPaintText;
    private final Paint mPaintBackground;
    private final Paint mPaintGreen;
    private final Paint mPaintOrange;
    private final Paint mPaintRed;

    private Path mTextArc;

    private long mLowLevel;
    private long mMediumLevel;
    private long mHighLevel;
    private float mArcOffset;

    private String mAvailableMemory;
    private String mTotalMemory;


    public CircleMemoryMeter(Context context) {
        this(context, null);
    }

    public CircleMemoryMeter(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleMemoryMeter(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mHandler = new Handler();

        // initialize and setup all paint variables
        // stroke width is later set in initSizeBasedStuff()

        mPaintText = new Paint();
        mPaintText.setAntiAlias(true);
        mPaintText.setDither(true);
        mPaintText.setStyle(Paint.Style.STROKE);

        mPaintBackground = new Paint(mPaintText);
        mPaintGreen = new Paint(mPaintText);
        mPaintOrange = new Paint(mPaintText);
        mPaintRed = new Paint(mPaintText);

        mPaintBackground.setStrokeCap(Paint.Cap.BUTT);
        mPaintGreen.setStrokeCap(Paint.Cap.BUTT);
        mPaintOrange.setStrokeCap(Paint.Cap.BUTT);
        mPaintRed.setStrokeCap(Paint.Cap.BUTT);

        mPaintBackground.setColor(Color.argb(200, 255, 255, 255));
        mPaintGreen.setColor(Color.argb(170, 166, 198, 61));
        mPaintOrange.setColor(Color.argb(170, 255, 187, 51));
        mPaintRed.setColor(Color.argb(170, 255, 68, 68));

        mPaintText.setColor(Color.BLACK);
        mPaintText.setTextAlign(Align.CENTER);
        mPaintText.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaintText.setFakeBoldText(true);
    }

    private void setLevels(long lowLevel, long mediumLevel, long highLevel) {
        mLowLevel = lowLevel;
        mMediumLevel = mediumLevel;
        mHighLevel = highLevel;
    }

    private long getLevel() {
        return mLevel;
    }

    private void setCurrentLevel(long level) {
        mLevel = level;
        mAvailableMemory = "" + (level / 1048576L) + "M";
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            mRectLeft = null;
            mCircleSize = 0;
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        MemoryInfo mi = new MemoryInfo();
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        long free = mi.availMem;
        // threshold is where the system would consider it low memory and start killing off
        // processes so let's deduct that from the total so the indicator will be in the red
        // well before that so the user knows memory is getting low
        long total = mi.totalMem;
        mTotalMemory = "" + (total / 1048576L) + "M";
        setLevels((long) (total * 0.2f), (long) (total * 0.5f), total);
        setCurrentLevel(free);
    }

    public void updateMemoryInfo() {
        MemoryInfo mi = new MemoryInfo();
        ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        am.getMemoryInfo(mi);
        long free = mi.availMem;
        setCurrentLevel(free);
        invalidate();
    }

    void drawCircle(Canvas canvas, long level, RectF drawRect) {
        Paint usePaint;

        if (level <= mLowLevel)
            usePaint = mPaintRed;
        else if (level <= mMediumLevel)
            usePaint = mPaintOrange;
        else
            usePaint = mPaintGreen;

        int normalizedLevel = (int) ((float) level / (float) mHighLevel * 100f);

        // draw background ring first
        canvas.drawArc(drawRect, 270, 360, false, mPaintBackground);
        // draw colored arc representing charge level
        canvas.drawArc(drawRect, 180, 3.6f * normalizedLevel, false, usePaint);
        canvas.drawTextOnPath(
                mAvailableMemory + "/" + mTotalMemory, mTextArc, 0, mArcOffset, mPaintText);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRectLeft == null) {
            init();
        }
        drawCircle(canvas, getLevel(), mRectLeft);
    }

    /**
     * initializes all size dependent variables
     * sets stroke width and text size of all involved paints
     */
    private void init() {
        if (mCircleSize == 0) {
            initSizeMeasureIconHeight();
        }

        float strokeWidth = mCircleSize / 6.5f;
        float levelStrokeWidth = strokeWidth / 1.5f; // needed for centering the text and level
        mPaintRed.setStrokeWidth(levelStrokeWidth);
        mPaintGreen.setStrokeWidth(levelStrokeWidth);
        mPaintOrange.setStrokeWidth(levelStrokeWidth);
        mPaintBackground.setStrokeWidth(strokeWidth);

        // calculate rectangle for drawArc calls
        int pLeft = getPaddingLeft();
        mRectLeft = new RectF(pLeft + strokeWidth / 2.0f, 0 + strokeWidth / 2.0f, mCircleSize
                - strokeWidth / 2.0f + pLeft, mCircleSize - strokeWidth / 2.0f);

        mTextArc = new Path();
        mTextArc.addArc(mRectLeft, 180, 180);
        mPaintText.setTextSize(strokeWidth);
        mArcOffset = (strokeWidth - levelStrokeWidth);

        // force new measurement for wrap-content xml tag
        onMeasure(0, 0);
    }

    private void initSizeMeasureIconHeight() {
        mCircleSize = Math.min(getWidth(), getHeight());
    }
}
