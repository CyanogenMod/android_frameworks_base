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

package com.android.systemui;

import android.view.ViewGroup.LayoutParams;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;

import com.android.internal.R;

import com.android.systemui.BatteryMeterView;

/***
 * Note about CircleBattery Implementation:
 *
 * Unfortunately, we cannot use BatteryController or DockBatteryController here,
 * since communication between controller and this view is not possible without
 * huge changes. As a result, this Class is doing everything by itself,
 * monitoring battery level and battery settings.
 */

public class BatteryCircleMeterView extends ImageView {
    private Handler mHandler = new Handler();
    // state variables
    private boolean mAttached;      // whether or not attached to a window
    private boolean mActivated;     // whether or not activated due to system settings
    private boolean mPercentage;    // whether or not to show percentage number
    private boolean mIsCharging;    // whether or not device is currently charging
    private int     mLevel;         // current battery level
    private int     mAnimOffset;    // current level of charging animation
    private boolean mIsAnimating;   // stores charge-animation status to reliably remove callbacks
    private int     mDockLevel;     // current dock battery level
    private boolean mDockIsCharging;// whether or not dock battery is currently charging
    private boolean mIsDocked = false;      // whether or not dock battery is connected

    private int     mCircleSize;    // draw size of circle. read rather complicated from
                                     // another status bar icon, so it fits the icon size
                                     // no matter the dps and resolution
    private RectF   mRectLeft;      // contains the precalculated rect used in drawArc(), derived from mCircleSize
    private RectF   mRectRight;     // contains the precalculated rect used in drawArc() for dock battery
    private Float   mTextLeftX;     // precalculated x position for drawText() to appear centered
    private Float   mTextY;         // precalculated y position for drawText() to appear vertical-centered
    private Float   mTextRightX;    // precalculated x position for dock battery drawText()

    // quiet a lot of paint variables. helps to move cpu-usage from actual drawing to initialization
    private Paint   mPaintFont;
    private Paint   mPaintGray;
    private Paint   mPaintSystem;
    private Paint   mPaintRed;
    private DashPathEffect mPathEffect;
    private int mBatteryStyle;

    private String mCircleBatteryView;

    private int mCircleColor;
    private int mCircleTextColor;
    private int mCircleTextChargingColor;
    private int mCircleAnimSpeed;

    // runnable to invalidate view via mHandler.postDelayed() call
    private final Runnable mInvalidate = new Runnable() {
        public void run() {
            if(mActivated && mAttached) {
                invalidate();
            }
        }
    };

    // keeps track of current battery level and charger-plugged-state
    private class BatteryReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mIsCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

                if (mActivated) {
                    LayoutParams l = getLayoutParams();
                    l.width = mCircleSize + getPaddingLeft()
                            + (mIsDocked ? mCircleSize + getPaddingLeft() : 0);
                    setLayoutParams(l);

                    invalidate();
                }
            }
        }

    }
    BatteryReceiver mBatteryReceiver = new BatteryReceiver();

    /***
     * Start of CircleBattery implementation
     */
    public BatteryCircleMeterView(Context context) {
        this(context, null);
    }

    public BatteryCircleMeterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BatteryCircleMeterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray circleBatteryType = context.obtainStyledAttributes(attrs,
            com.android.systemui.R.styleable.BatteryIcon, 0, 0);

        mCircleBatteryView = circleBatteryType.getString(
                com.android.systemui.R.styleable.BatteryIcon_batteryView);

        circleBatteryType.recycle();

        if (mCircleBatteryView == null) {
            mCircleBatteryView = "statusbar";
        }

        /*
         * initialize vars
         */
        mPaintFont = new Paint();
        mPaintFont.setAntiAlias(true);
        mPaintFont.setDither(true);
        mPaintFont.setStyle(Paint.Style.STROKE);

        mPaintGray = new Paint(mPaintFont);
        mPaintSystem = new Paint(mPaintFont);
        mPaintRed = new Paint(mPaintFont);

        // could not find the darker definition anywhere in resources
        // do not want to use static 0x404040 color value. would break theming.
        Resources res = getResources();
        mPaintGray.setColor(res.getColor(R.color.darker_gray));
        mPaintRed.setColor(res.getColor(R.color.holo_red_light));

        mPaintFont.setTextAlign(Align.CENTER);
        mPaintFont.setFakeBoldText(true);

        mPathEffect = new DashPathEffect(new float[]{3,2},0);

        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            final Intent sticky = getContext().registerReceiver(mBatteryReceiver, filter);
            if (sticky != null) {
                // preload the battery level
                mBatteryReceiver.onReceive(getContext(), sticky);
            }
            mHandler.postDelayed(mInvalidate, 250);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mAttached = false;
            getContext().unregisterReceiver(mBatteryReceiver);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mCircleSize == 0) {
            initSizeMeasureIconHeight();
        }

        setMeasuredDimension(mCircleSize + getPaddingLeft()
                + (mIsDocked ? mCircleSize + getPaddingLeft() : 0), mCircleSize);
    }

    private void drawCircle(Canvas canvas, int level, int animOffset, float textX, RectF drawRect) {
        // turn red at 14% - same level android battery warning appears
        final Paint usePaint = level <= 14 ? mPaintRed : mPaintSystem;

        usePaint.setAntiAlias(true);
        if (mBatteryStyle == BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT ||
            mBatteryStyle == BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE) {
            // change usePaint from solid to dashed
            usePaint.setPathEffect(mPathEffect);
        } else {
            usePaint.setPathEffect(null);
        }

        // pad circle percentage to 100% once it reaches 97%
        // for one, the circle looks odd with a too small gap,
        // for another, some phones never reach 100% due to hardware design
        int padLevel = level;
        if (padLevel >= 97) {
            padLevel = 100;
        }

        // draw thin gray ring first
        canvas.drawArc(drawRect, 270, 360, false, mPaintGray);
        // draw colored arc representing charge level
        canvas.drawArc(drawRect, 270 + animOffset, 3.6f * padLevel, false, usePaint);
        // if chosen by options, draw percentage text in the middle
        // always skip percentage when 100, so layout doesnt break
        if (level < 100 && mPercentage) {
            if (level <= 14) {
                mPaintFont.setColor(mPaintRed.getColor());
            } else if (mIsCharging) {
                mPaintFont.setColor(mCircleTextChargingColor);
            } else {
                mPaintFont.setColor(mCircleTextColor);
            }
            canvas.drawText(Integer.toString(level), textX, mTextY, mPaintFont);
        }

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRectLeft == null) {
            initSizeBasedStuff();
        }

        updateChargeAnim();

        if (mIsDocked) {
            drawCircle(canvas, mDockLevel, (mDockIsCharging ? mAnimOffset : 0),
                    mTextLeftX, mRectLeft);
            drawCircle(canvas, mLevel, (mIsCharging ? mAnimOffset : 0), mTextRightX, mRectRight);
        } else {
            drawCircle(canvas, mLevel, (mIsCharging ? mAnimOffset : 0), mTextLeftX, mRectLeft);
        }
    }

    public void updateSettings() {
        Resources res = getResources();
        ContentResolver resolver = getContext().getContentResolver();

        mBatteryStyle = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0, UserHandle.USER_CURRENT);

        mCircleColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_COLOR, -2, UserHandle.USER_CURRENT);
        mCircleTextColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_TEXT_COLOR, -2,
                UserHandle.USER_CURRENT);
        mCircleTextChargingColor = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_BATTERY_TEXT_CHARGING_COLOR, -2,
                UserHandle.USER_CURRENT);
        mCircleAnimSpeed = Settings.System.getIntForUser(resolver,
                Settings.System.STATUS_BAR_CIRCLE_BATTERY_ANIMATIONSPEED, 3,
                UserHandle.USER_CURRENT);

        int defaultColor = res.getColor(com.android.systemui.R.color.batterymeter_charge_color);

        if (mCircleTextColor == -2) {
            mCircleTextColor = defaultColor;
        }
        if (mCircleTextChargingColor == -2) {
            mCircleTextChargingColor = defaultColor;
        }
        if (mCircleColor == -2) {
            mCircleColor = defaultColor;
        }

        mPaintSystem.setColor(mCircleColor);

        mRectLeft = null;
        mCircleSize = 0;

        mActivated = (mBatteryStyle == BatteryMeterView.BATTERY_STYLE_CIRCLE ||
                      mBatteryStyle == BatteryMeterView.BATTERY_STYLE_CIRCLE_PERCENT ||
                      mBatteryStyle == BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE ||
                      mBatteryStyle == BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT);
        mPercentage = (mBatteryStyle == BatteryMeterView.BATTERY_STYLE_CIRCLE_PERCENT ||
                       mBatteryStyle == BatteryMeterView.BATTERY_STYLE_DOTTED_CIRCLE_PERCENT);

        setVisibility(mActivated ? View.VISIBLE : View.GONE);

        invalidate();
    }

    /***
     * updates the animation counter
     * cares for timed callbacks to continue animation cycles.
     */
    private void updateChargeAnim() {
        if (!(mIsCharging || mDockIsCharging) || (mLevel >= 97 && mDockLevel >= 97)) {
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
            mAnimOffset += mCircleAnimSpeed;
        }

        mHandler.removeCallbacks(mInvalidate);
        mHandler.postDelayed(mInvalidate, 50);
    }

    /***
     * initializes all size dependent variables
     * sets stroke width and text size of all involved paints
     * YES! i think the method name is appropriate
     */
    private void initSizeBasedStuff() {
        if (mCircleSize == 0) {
            initSizeMeasureIconHeight();
        }

        mPaintFont.setTextSize(mCircleSize / 2f);

        float strokeWidth = mCircleSize / 7f;
        mPaintRed.setStrokeWidth(strokeWidth);
        mPaintSystem.setStrokeWidth(strokeWidth);
        mPaintGray.setStrokeWidth(strokeWidth / 3.5f);
        // calculate rectangle for drawArc calls
        int pLeft = getPaddingLeft();
        mRectLeft = new RectF(pLeft + strokeWidth / 2.0f, 0 + strokeWidth / 2.0f, mCircleSize
                - strokeWidth / 2.0f + pLeft, mCircleSize - strokeWidth / 2.0f);
        int off = pLeft + mCircleSize;
        mRectRight = new RectF(mRectLeft.left + off, mRectLeft.top, mRectLeft.right + off,
                mRectLeft.bottom);

        // calculate Y position for text
        Rect bounds = new Rect();
        mPaintFont.getTextBounds("99", 0, "99".length(), bounds);
        mTextLeftX = mCircleSize / 2.0f + getPaddingLeft();
        mTextRightX = mTextLeftX + off;

        mTextY = mCircleSize / 2.0f + (bounds.bottom - bounds.top) / 2.0f - strokeWidth / 2.0f;

        // balance out rounding issues. works out on all resolutions
        if (mCircleBatteryView.equals("quicksettings")) {
            mTextY = mTextY + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f,
                    getContext().getResources().getDisplayMetrics());
        } else if (mCircleBatteryView.equals("statusbar")) {
            mTextY = mTextY + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f,
                    getContext().getResources().getDisplayMetrics());
        }
        // force new measurement for wrap-content xml tag
        onMeasure(0, 0);
    }

    /***
     * we need to measure the size of the circle battery by checking another
     * resource. unfortunately, those resources have transparent/empty borders
     * so we have to count the used pixel manually and deduct the size from
     * it. quiet complicated, but the only way to fit properly into the
     * statusbar for all resolutions
     */
    private void initSizeMeasureIconHeight() {
        Bitmap measure = null;
        if (mCircleBatteryView.equals("quicksettings")) {
            measure = BitmapFactory.decodeResource(getResources(),
                    com.android.systemui.R.drawable.ic_qs_wifi_full_4);
        } else if (mCircleBatteryView.equals("statusbar")) {
            measure = BitmapFactory.decodeResource(getResources(),
                    com.android.systemui.R.drawable.stat_sys_wifi_signal_4_fully);
        }
        if (measure == null) {
            return;
        }
        final int x = measure.getWidth() / 2;

        mCircleSize = measure.getHeight();
    }

}
