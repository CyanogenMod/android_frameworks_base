/*
 * Created by Sven Dawitz; Copyright (C) 2011 CyanogenMod Project
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import java.lang.Runnable;

import com.android.internal.R;

/**
 * This widget displays the percentage of the battery as a mini icon
 */
public class CmBatteryMiniIcon extends ImageView {
    // the width of the mini bat icon
    static final int BATTERY_MINI_ICON_WIDTH_DIP = 4;

    // the margin to the right of this widget
    static final int BATTERY_MINI_ICON_MARGIN_RIGHT_DIP = 6;

    // duration of each frame in charging animation in millis
    static final int ANIM_FRAME_DURATION = 750;

    // duration of each fake-timer call to update animation in millis
    static final int ANIM_TIMER_DURATION = 333;

    // contains the current bat level, values: 0-100
    private int mBatteryLevel = 0;

    // contains current charger plugged state
    private boolean mBatteryPlugged = false;

    // recalculation of BATTERY_MINI_ICON_WIDTH_DIP to pixels
    private int mWidthPx;

    // recalculation of BATTERY_MINI_ICON_MARGIN_RIGHT_DIP to pixels
    private int mMarginRightPx;

    // weather to show this battery widget or not
    private boolean mShowCmBattery = false;

    // used for animation
    private long mLastMillis = 0;

    // used for animation
    private int mCurrentFrame;

    private boolean mAttached;

    private Matrix mMatrix = new Matrix();

    private Paint mPaint = new Paint();

    private Handler mHandler;

    private float mDensity;

    private transient Bitmap[] mMiniIconCache;

    // tracks changes to settings, so status bar is auto updated the moment the
    // setting is toggled
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_CM_BATTERY), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    // provides a fake-timer using Handler to force onDraw() events when
    // animating
    final Runnable onFakeTimer = new Runnable() {
        public void run() {
            invalidate();
            mHandler.postDelayed(onFakeTimer, ANIM_TIMER_DURATION);
        }
    };

    public CmBatteryMiniIcon(Context context) {
        this(context, null);
    }

    public CmBatteryMiniIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmBatteryMiniIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

        Resources r = getResources();

        mDensity = r.getDisplayMetrics().density;
        mWidthPx = (int) (BATTERY_MINI_ICON_WIDTH_DIP * mDensity);
        mMarginRightPx = (int) (BATTERY_MINI_ICON_MARGIN_RIGHT_DIP * mDensity);

        updateIconCache();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_BATTERY_CHANGED);

            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    /**
     * Handles changes ins battery level and charger connection
     */
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                // mIconId = intent.getIntExtra("icon-small", 0);
                mBatteryLevel = intent.getIntExtra("level", 0);
                mBatteryPlugged = intent.getIntExtra("plugged", 0) != 0;

                if (mBatteryPlugged && mBatteryLevel < 100)
                    mHandler.postDelayed(onFakeTimer, ANIM_TIMER_DURATION);
                else{
                    mHandler.removeCallbacks(onFakeTimer);
                    invalidate();
                }
            }
        }
    };

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(mWidthPx + mMarginRightPx, height);
        updateMatrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!mAttached)
            return;
        if (!mShowCmBattery)
            return;

        // set up animation when charger plugged in
        if (mBatteryPlugged && mBatteryLevel < 100) {
            if (mLastMillis == 0) {
                // just got plugged - setup animation
                mLastMillis = SystemClock.uptimeMillis();
                mCurrentFrame = mBatteryLevel / 10;
            }
            long now = SystemClock.uptimeMillis();

            while (now - mLastMillis > ANIM_FRAME_DURATION) {
                mCurrentFrame++;
                if (mCurrentFrame > 10)
                    mCurrentFrame = mBatteryLevel / 10;
                mLastMillis += ANIM_FRAME_DURATION;
            }
        } else {
            // reset the animation for next charger connection
            mLastMillis = 0;
            mCurrentFrame = 10;
        }

        int frame = (mBatteryPlugged ? mCurrentFrame : mBatteryLevel / 10);

        canvas.drawBitmap(mMiniIconCache[frame], mMatrix, mPaint);
    }

    private int getBatResourceID(int level) {
        switch (level) {
            case 0:
                // cannot use the 0% battery icon, since its completly different
                return R.drawable.stat_sys_battery_5;
            case 1:
                return R.drawable.stat_sys_battery_10;
            case 2:
                return R.drawable.stat_sys_battery_20;
            case 3:
                return R.drawable.stat_sys_battery_30;
            case 4:
                return R.drawable.stat_sys_battery_40;
            case 5:
                return R.drawable.stat_sys_battery_50;
            case 6:
                return R.drawable.stat_sys_battery_60;
            case 7:
                return R.drawable.stat_sys_battery_70;
            case 8:
                return R.drawable.stat_sys_battery_80;
            case 9:
                return R.drawable.stat_sys_battery_90;
            case 10:
            default:
                return R.drawable.stat_sys_battery_100;
        }
    }

    /**
     * Converts resource id to actual Bitmap
     *
     * @param resId the resource id
     * @return resluting bitmap
     */
    private Bitmap getBitmapFor(int resId) {
        return BitmapFactory.decodeResource(getContext().getResources(), resId);
    }

    /**
     * Invoked by SettingsObserver, this method keeps track of just changed
     * settings. Also does the initial call from constructor
     */
    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mShowCmBattery = (Settings.System
                .getInt(resolver, Settings.System.STATUS_BAR_CM_BATTERY, 0) == 1);

        if (mShowCmBattery)
            setVisibility(View.VISIBLE);
        else
            setVisibility(View.GONE);
    }

    // should be toggled to private (or inlined at constructor), once StatusBarService.updateResources properly handles theme change
    public void updateIconCache() {
        // set up the icon cache - garbage collector handles old pointer
        mMiniIconCache=new Bitmap[11];

        for(int i=0; i<=10; i++){
            // get the original battery image
            Bitmap bmBat = getBitmapFor(getBatResourceID(i));
            // cut one slice of pixels from battery image
            mMiniIconCache[i] = Bitmap.createBitmap(bmBat, 4, 0, 1, bmBat.getHeight());
        }
    }

    // set up the scaling matrix, so the width is scaled and the height is fixed if non-default-height in theme (i.e. honeybread)
    // should be toggled to private (or inlined at constructor), once StatusBarService.updateResources properly handles theme change
    public void updateMatrix() {
        mMatrix.reset();
        mMatrix.setTranslate(0, 0);
        float scaleFactor=getHeight()/(float)(mMiniIconCache[0].getHeight());
        mMatrix.postScale(mWidthPx, scaleFactor);
    }
}
