/*
 * Copyright (C) 2012 The CyanogenMod Project
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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.util.AttributeSet;

import com.android.systemui.R;

/***
 * Implementation of the CircleBattery widget adapted for listening dock battery status
 * @see CircleBattery
 */

public class CircleDockBattery extends CircleBattery {

    private int mLevel;
    private int mDockBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private boolean mBatteryPlugged = false;
    private boolean mBatteryPresent = false;

    private final Context mContext;
    private boolean mAttached;

    private Bitmap mDockIcon;
    private Paint mPaint;

    /***
     * Start of CircleDockBattery implementation
     */
    public CircleDockBattery(Context context) {
        this(context, null);
    }

    public CircleDockBattery(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircleDockBattery(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        if (!mAttached) {
            // Load the resource to display as dock icon
            mDockIcon = BitmapFactory.decodeResource(
                                mContext.getResources(),
                                R.drawable.stat_sys_kb_battery_icon);

            // Use an anti-alias while drawing the dock icon
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setFilterBitmap(true);
            mPaint.setDither(true);

            mAttached = true;
        }
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDockIcon != null) {
            mDockIcon.recycle();
        }
        mDockIcon = null;
        mPaint = null;
        mAttached = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected int getLevel() {
        return mLevel;
    }

    @Override
    protected int getBatteryStatus() {
        return mDockBatteryStatus;
    }

    @Override
    protected boolean isBatteryPlugged() {
        return mBatteryPlugged;
    }

    @Override
    protected boolean isBatteryPresent() {
        return mBatteryPresent;
    }

    @Override
    protected void onBatteryStatusChange(Intent intent) {
        mLevel = intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0);
        mDockBatteryStatus = intent.getIntExtra(
                                    BatteryManager.EXTRA_DOCK_STATUS,
                                    BatteryManager.BATTERY_STATUS_UNKNOWN);
        mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_DOCK_PLUGGED, 0) != 0;
        mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT, false);
    }

    @Override
    protected void drawCircle(Canvas canvas, int level, int animOffset, float textX, RectF drawRect) {
        super.drawCircle(canvas, level, animOffset, textX, drawRect);
        if (mDockIcon != null) {
            Rect src = new Rect(0, 0, mDockIcon.getWidth(), mDockIcon.getHeight());
            float h = getHeight() - getPaddingBottom();
            float w = getWidth() - getPaddingLeft() - getPaddingRight();
            RectF dst = new RectF(
                            getPaddingLeft() + (w / 2) - (src.width() / 2),
                            h - src.height(),
                            getWidth() - getPaddingRight() - (w / 2) + (src.width() / 2),
                            h);
            canvas.drawBitmap(mDockIcon, src, dst, mPaint);
        }
    }
}
