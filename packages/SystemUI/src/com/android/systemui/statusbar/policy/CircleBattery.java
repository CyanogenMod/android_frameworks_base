/*
 * Copyright (C) 2012 Sven Dawitz for the CyanogenMod Project
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

package com.android.systemui.statusbar.policy;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.systemui.R;

public class CircleBattery extends ImageView implements BatteryController.BatteryStateChangeCallback {
    private Handler mHandler;
    private Context mContext;

    // state variables
    private boolean mAttached;      // whether or not attached to a window
    private int     mSetVisibility; // visibility setting set by our parent
    private boolean mPercentage;    // whether or not to show percentage number
    private int     mBatteryStatus; // current battery status
    private int     mLevel;         // current battery level
    private int     mWarningLevel;  // battery level under which circle should become red
    private int     mAnimOffset;    // current level of charging animation
    private boolean mIsAnimating;   // stores charge-animation status to reliably remove callbacks

    private int     mCircleSize;    // draw size of circle
    private RectF   mRectLeft;      // contains the precalculated rect used in drawArc(),
                                    // derived from mCircleSize
    private float   mTextX, mTextY; // precalculated position for drawText() to appear centered

    private int     mChargeColor;
    private int[]   mColors;

    // quite a lot of paint variables. helps to move cpu-usage from actual drawing to initialization
    private Paint   mPaintFont;
    private Paint   mPaintGray;
    private Paint   mPaintSystem;

    // runnable to invalidate view via mHandler.postDelayed() call
    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            invalidateIfVisible();
        }
    };

    /***
     * Start of CircleBattery implementation
     */
    public CircleBattery(Context context) {
        this(context, null);
    }

    public CircleBattery(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleBattery(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;
        mHandler = new Handler();

        // initialize and setup all paint variables
        // stroke width is later set in initSizeBasedStuff()
        Resources res = getResources();

        mPaintFont = new Paint();
        mPaintFont.setAntiAlias(true);
        mPaintFont.setDither(true);
        mPaintFont.setStyle(Paint.Style.STROKE);

        mPaintGray = new Paint(mPaintFont);
        mPaintSystem = new Paint(mPaintFont);

        mPaintGray.setStrokeCap(Paint.Cap.BUTT);
        mPaintSystem.setStrokeCap(Paint.Cap.BUTT);

        // could not find the darker definition anywhere in resources
        // do not want to use static 0x404040 color value. would break theming.
        mPaintGray.setColor(res.getColor(com.android.internal.R.color.darker_gray));

        // font needs some extra settings
        mPaintFont.setTextAlign(Align.CENTER);
        mPaintFont.setFakeBoldText(true);

        mChargeColor = res.getColor(R.color.batterymeter_charge_color);

        TypedArray levels = res.obtainTypedArray(R.array.batterymeter_color_levels);
        TypedArray colors = res.obtainTypedArray(R.array.batterymeter_color_values);
        final int N = levels.length();

        mColors = new int[2 * N];
        for (int i = 0; i < N; i++) {
            mColors[2 * i] = levels.getInt(i, 0);
            mColors[2 * i + 1] = colors.getColor(i, 0);
        }
        levels.recycle();
        colors.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            mHandler.postDelayed(mInvalidate, 250);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            mRectLeft = null; // makes sure, size based variables get
                              // recalculated on next attach
        }
    }

    public void setShowPercent(boolean show) {
        mPercentage = show;
        updateVisibility();
    }

    @Override
    public void setVisibility(int visibility) {
        mSetVisibility = visibility;
        super.setVisibility(visibility);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, int status) {
        mLevel = level;
        mBatteryStatus = status;
        updateVisibility();
    }

    protected void updateVisibility() {
        super.setVisibility(isBatteryPresent() ? mSetVisibility : View.GONE);
        invalidateIfVisible();
    }

    private void invalidateIfVisible() {
        if (getVisibility() == View.VISIBLE && mAttached) {
            invalidate();
        }
    }

    protected int getBatteryLevel() {
        return mLevel;
    }

    protected int getBatteryStatus() {
        return mBatteryStatus;
    }

    protected boolean isBatteryPresent() {
        return true;
    }

    protected void drawCircle(Canvas canvas, int level, int animOffset,
            float textX, RectF drawRect) {
        boolean unknownStatus = getBatteryStatus() == BatteryManager.BATTERY_STATUS_UNKNOWN;
        Paint paint;

        if (unknownStatus) {
            paint = mPaintGray;
            level = 100; // Draw all the circle;
        } else {
            paint = mPaintSystem;
            paint.setColor(getColorForLevel(level));
            if (getBatteryStatus() == BatteryManager.BATTERY_STATUS_FULL) {
                level = 100;
            }
        }

        // draw thin gray ring first
        canvas.drawArc(drawRect, 270, 360, false, mPaintGray);
        // draw colored arc representing charge level
        canvas.drawArc(drawRect, 270 + animOffset, 3.6f * level, false, paint);
        // if chosen by options, draw percentage text in the middle
        // always skip percentage when 100, so layout doesnt break
        if (unknownStatus) {
            mPaintFont.setColor(paint.getColor());
            canvas.drawText("?", textX, mTextY, mPaintFont);
        } else if (level < 100 && mPercentage) {
            mPaintFont.setColor(paint.getColor());
            canvas.drawText(Integer.toString(level), textX, mTextY, mPaintFont);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRectLeft == null) {
            initSizeBasedStuff();
        }

        updateChargeAnim();

        boolean charging = getBatteryStatus() == BatteryManager.BATTERY_STATUS_CHARGING;
        int offset = charging ? mAnimOffset : 0;

        drawCircle(canvas, getBatteryLevel(), offset, mTextX, mRectLeft);
    }

    /**
     * updates the animation counter
     * cares for timed callbacks to continue animation cycles
     * uses mInvalidate for delayed invalidate() callbacks
     */
    private void updateChargeAnim() {
        if (getBatteryStatus() != BatteryManager.BATTERY_STATUS_CHARGING) {
            if (mIsAnimating) {
                mIsAnimating = false;
                mAnimOffset = 0;
                mHandler.removeCallbacks(mInvalidate);
            }
            return;
        }

        mIsAnimating = true;

        if (mAnimOffset > 360) {
            mAnimOffset = 0;
        } else {
            mAnimOffset += 3;
        }

        mHandler.removeCallbacks(mInvalidate);
        mHandler.postDelayed(mInvalidate, 50);
    }

    /**
     * initializes all size dependent variables
     * sets stroke width and text size of all involved paints
     * YES! i think the method name is appropriate
     */
    private void initSizeBasedStuff() {
        mCircleSize = Math.min(getMeasuredWidth(), getMeasuredHeight());
        mPaintFont.setTextSize(mCircleSize / 2f);

        float strokeWidth = mCircleSize / 6.5f;
        mPaintSystem.setStrokeWidth(strokeWidth);
        mPaintGray.setStrokeWidth(strokeWidth / 3.5f);

        // calculate rectangle for drawArc calls
        int pLeft = getPaddingLeft();
        mRectLeft = new RectF(pLeft + strokeWidth / 2.0f, 0 + strokeWidth / 2.0f, mCircleSize
                - strokeWidth / 2.0f + pLeft, mCircleSize - strokeWidth / 2.0f);

        // calculate Y position for text
        Rect bounds = new Rect();
        mPaintFont.getTextBounds("99", 0, "99".length(), bounds);
        mTextX = mCircleSize / 2.0f + getPaddingLeft();
        // the +1 at end of formula balances out rounding issues. works out on all resolutions
        mTextY = mCircleSize / 2.0f + (bounds.bottom - bounds.top) / 2.0f - strokeWidth / 2.0f + 1;
    }

    private int getColorForLevel(int percent) {
        int thresh, color = 0;
        for (int i = 0; i < mColors.length; i += 2) {
            thresh = mColors[i];
            color = mColors[i + 1];
            if (percent <= thresh) {
                break;
            }
        }
        return color;
    }
}
