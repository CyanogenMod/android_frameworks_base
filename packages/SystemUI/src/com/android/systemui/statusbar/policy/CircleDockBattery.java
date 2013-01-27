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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.util.AttributeSet;

/***
 * Implementation of the CircleBattery widget adapted for listening dock battery status
 * @see CircleBattery
 */

public class CircleDockBattery extends CircleBattery {

    private int mLevel;
    private int mDockBatteryStatus;
    private boolean mIsCharging;

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
    }

    @Override
    protected int getLevel() {
        return mLevel;
    }

    @Override
    protected boolean isCharging() {
        return mIsCharging;
    }

    @Override
    protected boolean isBatteryStatusUnknown() {
        return mDockBatteryStatus == BatteryManager.DOCK_BATTERY_STATUS_UNKNOWN;
    }

    @Override
    protected void onBatteryStatusChange(Intent intent) {
        mLevel = intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0);
        mDockBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_DOCK_STATUS,
                BatteryManager.DOCK_BATTERY_STATUS_UNKNOWN);
        mIsCharging = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT, false);
    }

    @Override
    protected void drawCircle(Canvas canvas, int level, int animOffset, float textX, RectF drawRect) {
        super.drawCircle(canvas, level, animOffset, textX, drawRect);
    }
}
