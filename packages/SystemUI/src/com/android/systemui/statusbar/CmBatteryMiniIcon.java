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
import android.os.BatteryManager;
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

    // Duration of each frame during battery charging animation
    private int mAnimDuration = 500;

    // contains the current bat level, values: 0-100
    private int mBatteryLevel = 0;

    // contains current charging state of the battery.
    private boolean mBatteryCharging = false;

    // recalculation of BATTERY_MINI_ICON_WIDTH_DIP to pixels
    private int mWidthPx;

    // recalculation of BATTERY_MINI_ICON_MARGIN_RIGHT_DIP to pixels
    private int mMarginRightPx;

    // battery style preferences
    private static final int BATTERY_STYLE_PERCENT   = 1;
    private int mStatusBarBattery;

    // used for animation and still values when not charging/fully charged
    private int mCurrentFrame = 0;

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
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY), false, this);
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
            ++mCurrentFrame;
            if (mCurrentFrame > 10)
                mCurrentFrame = mBatteryLevel / 10;
            invalidate();
            mHandler.postDelayed(onFakeTimer, mAnimDuration);
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

    protected void updateAnimDuration() {
        mAnimDuration = 200 + mBatteryLevel * 5;
    }

    private void startTimer() {
        mHandler.removeCallbacks(onFakeTimer);
        updateAnimDuration();
        mCurrentFrame = mBatteryLevel / 10;
        invalidate();
        mHandler.postDelayed(onFakeTimer, mAnimDuration);
    }

    private void stopTimer() {
        mHandler.removeCallbacks(onFakeTimer);
        // As the battery is charged or it's not charging
        // apply the current status of the battery.
        mCurrentFrame = mBatteryLevel / 10;
        invalidate();
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
                boolean oldChargingState = mBatteryCharging;
                mBatteryCharging = intent.getIntExtra("status", 0) == BatteryManager.BATTERY_STATUS_CHARGING;

                if (mBatteryCharging && mBatteryLevel < 100) {
                    if (!oldChargingState)
                        startTimer();
                    if(mBatteryLevel % 10 == 0)
                        updateAnimDuration();
                } else {
                    stopTimer();
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

        if (!mAttached || mStatusBarBattery != BATTERY_STYLE_PERCENT)
            return;

        canvas.drawBitmap(mMiniIconCache[mCurrentFrame], mMatrix, mPaint);
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

        int statusBarBattery = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 2));
        mStatusBarBattery = Integer.valueOf(statusBarBattery);

        if (mStatusBarBattery == BATTERY_STYLE_PERCENT) {
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
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
