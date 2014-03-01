/*
 * Copyright (C) 2006 The MoKee Open Source Project
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

import java.text.DecimalFormat;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class NetTraffic extends TextView {
    private boolean mAttached; // whether or not attached to a window
    TrafficStats mTrafficStats;
    int showTraffic;
    Handler mHandler;
    Handler mTrafficHandler;
    float speedRx;
    float speedTx;
    protected int mTrafficColor;
    private long mRefreshInterval;
    long totalRxBytes;
    long totalTxBytes;
    long lastUpdateTime;
    String strText;

    DecimalFormat decimalFormat = new DecimalFormat("###0");

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_NETWORK_STATS), false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.STATUS_BAR_NETWORK_COLOR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_NETWORK_STATS_UPDATE_INTERVAL), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public NetTraffic(Context context) {
        this(context, null);
    }

    public NetTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        mTrafficStats = new TrafficStats();
        settingsObserver.observe();

        strText = new String();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            mTrafficColor = getTextColors().getDefaultColor();
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

                lastUpdateTime = SystemClock.elapsedRealtime();

                strText = ""; // empty the string.
                if (showTraffic == 2) // Do not show "DL"/"UL" if only DL user choose to show the RX only.
                    strText = "DL: ";
                speedRx = (float) ((mTrafficStats.getTotalRxBytes() - totalRxBytes) * 1000 / td);
                if (speedRx / 1048576 >= 1) { // 1024 * 1024
                    strText += decimalFormat.format(speedRx / 1048576f) + "MB/s";
                } else if (speedRx / 1024f >= 1) {
                    strText += decimalFormat.format(speedRx / 1024f) + "KB/s";
                } else {
                    strText += speedRx > 0 ? (int) speedRx + "B/s" : showTraffic == 2 ? "0B/s" : "";
                }
                totalRxBytes = mTrafficStats.getTotalRxBytes();

                if (showTraffic == 2) {// If both RX/TX needed.
                    strText += ("\nUL: ");
                    speedTx = (float) ((mTrafficStats.getTotalTxBytes() - totalTxBytes) * 1000 / td);

                    if (speedTx / 1048576 >= 1) { // 1024 * 1024
                        strText += decimalFormat.format(speedTx / 1048576f) + "MB/s";
                    } else if (speedTx / 1024f >= 1) {
                        strText += decimalFormat.format(speedTx / 1024f) + "KB/s";
                    } else {
                        strText += speedTx > 0 ? (int) speedTx + "B/s" : "0B/s";
                    }
                }
                totalTxBytes = mTrafficStats.getTotalTxBytes();

                strText = strText.replace("\\n", "\n");
                setText(speedRx <= 0 && speedTx <= 0 ? "" : strText);

                update();
                super.handleMessage(msg);
            }
        };
        totalRxBytes = mTrafficStats.getTotalRxBytes();
        totalTxBytes = mTrafficStats.getTotalTxBytes();

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
        mTrafficHandler.postDelayed(mRunnable, mRefreshInterval);
    }

    Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mRefreshInterval = Settings.System.getLong(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_NETWORK_STATS_UPDATE_INTERVAL, 500);

        int defaultColor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_NETWORK_COLOR, 0xFFFFFFFF);

        int mTrafficColor = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_NETWORK_COLOR, -2);

        if (mTrafficColor == Integer.MIN_VALUE
                || mTrafficColor == -2) {
                // flag to reset the color
                mTrafficColor = defaultColor;
        }
        setTextColor(mTrafficColor);

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = pm.isScreenOn();

        showTraffic = Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_NETWORK_STATS, 0);

        if (showTraffic > 0 && getConnectAvailable() && isScreenOn) {
            if (mAttached) {
                updateTraffic();
            }
            setVisibility(View.VISIBLE);

            setText("");
            if (showTraffic == 2) {
                setTextSize(mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_networkSpeedIndicatorDualLineFontSize));
            } else {
                setTextSize(mContext.getResources().getInteger(
                        com.android.internal.R.integer.config_networkSpeedIndicatorSingleLineFontSize));
            }
        } else {
            setVisibility(View.GONE);
        }
    }
}
