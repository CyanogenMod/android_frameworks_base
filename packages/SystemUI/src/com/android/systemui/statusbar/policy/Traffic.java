package com.android.systemui.statusbar.policy;

import java.text.DecimalFormat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import android.os.SystemClock;

public class Traffic extends TextView {
    private boolean mAttached;
    //TrafficStats mTrafficStats;
    boolean showTraffic;
    Handler mHandler;
    Handler mTrafficHandler;
    long speed;
    long totalRxBytes;
    long lastUpdateTime;
    DecimalFormat decimalFormat = new DecimalFormat("##0.0");
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                .getUriFor(Settings.System.STATUS_BAR_TRAFFIC), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public Traffic(Context context) {
        this(context, null);
    }

    public Traffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Traffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        //mTrafficStats = new TrafficStats();
        settingsObserver.observe();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            getContext().registerReceiver(mIntentReceiver, filter, null,
                getHandler());
        }
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    updateSettings();
                }
        }
    };

    public void updateTraffic() {
        mTrafficHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                long td = SystemClock.elapsedRealtime() - lastUpdateTime;

                if (td == 0) {
                    // we just updated the view, nothing further to do
                    return;
                }

                speed = (TrafficStats.getTotalRxBytes() - totalRxBytes) * 1000 / td;
                totalRxBytes = TrafficStats.getTotalRxBytes();
                lastUpdateTime = SystemClock.elapsedRealtime();

                if (((float) speed) / 1048576 >= 1) { // 1024 * 1024
                    setText(decimalFormat.format(((float) speed) / 1048576f) + "MB/s");
                } else if (((float) speed) / 1024f >= 1) {
                    setText(decimalFormat.format(((float) speed) / 1024f) + "KB/s");
                } else {
                    setText(speed + "B/s");
                }
                update();
                super.handleMessage(msg);
            }
        };
        totalRxBytes = TrafficStats.getTotalRxBytes();
        lastUpdateTime = SystemClock.elapsedRealtime();
        mTrafficHandler.sendEmptyMessage(0);
    }

    private boolean getConnectAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager.getActiveNetworkInfo().isConnected())
                return true;
            else
                return false;
        } catch (Exception ex) {
        }
        return false;
    }

    public void update() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.postDelayed(mRunnable, 1000);
    }

    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        showTraffic = (Settings.System.getInt(resolver,
            Settings.System.STATUS_BAR_TRAFFIC, 0) == 1);
        if (showTraffic && getConnectAvailable()) {
            if (mAttached) {
                updateTraffic();
            }
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }
}
