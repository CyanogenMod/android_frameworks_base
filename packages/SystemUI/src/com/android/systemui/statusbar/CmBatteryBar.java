package com.android.systemui.statusbar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.drawable.Animatable;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class CmBatteryBar extends ProgressBar implements Animatable, Runnable {

    private static final String TAG = CmBatteryBar.class.getSimpleName();

    // Total animation duration
    private static final int ANIM_DURATION = 5000; // 5 seconds

    // Duration between frames of charging animation
    private static final int FRAME_DURATION = ANIM_DURATION / 100;

    // Are we listening for actions?
    private boolean mAttached = false;

    // Should we show this?
    private boolean mShowCmBatteryBar = false;

    // Current battery level
    private int mBatteryLevel = 0;

    // Current "step" of charging animation
    private int mChargingLevel = -1;

    // Are we charging?
    private boolean mBatteryCharging = false;

    private Handler mHandler = new Handler();

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observer() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public CmBatteryBar(Context context) {
        this(context, null);
    }

    public CmBatteryBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmBatteryBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

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
                mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
                if (mBatteryCharging && mBatteryLevel < 100) {
                    start();
                } else {
                    stop();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stop();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mBatteryCharging && mBatteryLevel < 100) {
                    start();
                }
            }
        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mShowCmBatteryBar = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0) == 2);
        if (mShowCmBatteryBar) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }

        if (mBatteryCharging && mBatteryLevel < 100) {
            start();
        } else {
            stop();
        }
    }

    @Override
    public void run() {
        mChargingLevel++;
        if (mChargingLevel > 100) {
            mChargingLevel = mBatteryLevel;
        }
        setProgress(mChargingLevel);
        mHandler.postDelayed(this, FRAME_DURATION);
    }

    @Override
    public void start() {
        if (!isRunning()) {
            mHandler.removeCallbacks(this);
            mChargingLevel = mBatteryLevel;
            mHandler.postDelayed(this, FRAME_DURATION);
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            mHandler.removeCallbacks(this);
            mChargingLevel = -1;
        }
        setProgress(mBatteryLevel);
    }

    @Override
    public boolean isRunning() {
        return mChargingLevel != -1;
    }

}
