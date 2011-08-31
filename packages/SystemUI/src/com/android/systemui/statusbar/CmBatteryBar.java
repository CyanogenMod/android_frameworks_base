
package com.android.systemui.statusbar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class CmBatteryBar extends ProgressBar {

    private static final String TAG = "CmBatteryBar";

    // Are we listening for actions?
    private boolean mAttached = false;

    // Should we show this?
    private boolean mShowCmBatteryBar = false;

    // Current battery level
    private int mBatteryLevel = 0;

    // Current "step" of charging animation
    private int mChargingLevel = -1;

    // Duration between frames of charging animation
    private int mAnimDuration = 500;

    // Are we plugged into a power source
    private boolean mBatteryPlugged = false;

    private Handler mHandler = new Handler();

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observer() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_CM_BATTERY), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private final Runnable onFakeTimer = new Runnable() {
        @Override
        public void run() {
            if (mChargingLevel > -1) {
                if (mChargingLevel < 20) {
                    mChargingLevel += mChargingLevel % 5;
                } else {
                    mChargingLevel += mChargingLevel % 10;
                }
                setProgress(mChargingLevel);
                if (mChargingLevel >= 100) {
                    mChargingLevel = mBatteryLevel;
                } else {
                    if (mChargingLevel < 20) {
                        mChargingLevel += 5;
                    } else {
                        mChargingLevel += 10;
                    }
                }
                invalidate();
                mHandler.postDelayed(onFakeTimer, mAnimDuration);
            }
        }
    };

    public CmBatteryBar(Context context) {
        this(context, null);
    }

    public CmBatteryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmBatteryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        Drawable d = getProgressDrawable();
        if (d instanceof LayerDrawable) {
            Drawable background = ((LayerDrawable) d)
                    .findDrawableByLayerId(com.android.internal.R.id.background);
            background.setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.SRC);
        }

        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observer();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            mAttached = false;
            getContext().unregisterReceiver(mIntentReceiver);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                mBatteryLevel = intent.getIntExtra("level", 0);

                boolean oldBatteryPlugged = mBatteryPlugged;
                mBatteryPlugged = intent.getIntExtra("plugged", 0) != 0;
                if (mBatteryPlugged && mBatteryLevel < 100) {
                    if (!oldBatteryPlugged) {
                        startTimer();
                    }
                    if (mBatteryLevel % 10 == 0) {
                        updateAnimDuration();
                    }
                } else {
                    stopTimer();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopTimer();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mBatteryPlugged && mBatteryLevel < 100) {
                    startTimer();
                }
            }
        }
    };

    @Override
    public synchronized void setProgress(int progress) {
        super.setProgress(progress);
        Drawable d = getProgressDrawable();
        if (d instanceof LayerDrawable) {
            Drawable progressBar = ((LayerDrawable) d)
                    .findDrawableByLayerId(com.android.internal.R.id.progress);
            if (progress <= 15) {
                progressBar.setColorFilter(Color.RED, PorterDuff.Mode.SRC);
            } else if (progress <= 30) {
                progressBar.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC);
            } else {
                progressBar.clearColorFilter();
            }
        }
    }

    private void updateAnimDuration() {
        mAnimDuration = 200 + (mBatteryLevel / 10) * 50;
    }

    private void startTimer() {
        mHandler.removeCallbacks(onFakeTimer);
        updateAnimDuration();
        mChargingLevel = mBatteryLevel;
        invalidate();
        mHandler.postDelayed(onFakeTimer, mAnimDuration);
    }

    private void stopTimer() {
        mHandler.removeCallbacks(onFakeTimer);
        setProgress(mBatteryLevel);
        mChargingLevel = -1;
        invalidate();
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mShowCmBatteryBar = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_CM_BATTERY, 0) == 2);
        if (mShowCmBatteryBar) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
    }

}
