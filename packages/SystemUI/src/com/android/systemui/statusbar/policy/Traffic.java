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
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class Traffic extends TextView {
    private boolean mAttached;
    //TrafficStats mTrafficStats;
    private long totalRxBytes;
    private long lastUpdateTime;
    private static DecimalFormat decimalFormat = new DecimalFormat("##0.0");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }
    private static final int KILOBIT = 1000;
    private static final int KILOBYTE = 1024;
    private int KB = KILOBIT;
    private int MB = KB * KB;
    private int GB = MB * KB;

    private Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long td = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (td < 950) {
                if (msg.what != 1) {
                    // we just updated the view, nothing further to do
                    return;
                }
                if (td < 1) {
                    // Can't div by 0 so make sure the value displayed is minimal
                    td = Long.MAX_VALUE;
                }
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            long newData = newTotalRxBytes - totalRxBytes;

            // If bit/s convert from Bytes to bits
            String symbol;
            if (KB == KILOBYTE) {
                symbol = "B/s";
            } else {
                symbol = "b/s";
                newData = newData * 8;
            }
            long speed = (long)(newData / (td / 1000F));
            if (speed < KB) {
                setText(speed + symbol);
            } else if (speed < MB) {
                setText(decimalFormat.format(speed / (float)KB) + 'k' + symbol);
            } else if (speed < GB) {
                setText(decimalFormat.format(speed / (float)MB) + 'M' + symbol);
            } else {
                setText(decimalFormat.format(speed / (float)GB) + 'G' + symbol);
            }

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            mTrafficHandler.removeCallbacks(mRunnable);
            mTrafficHandler.postDelayed(mRunnable, 1000);
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            Uri uri = Settings.System.getUriFor(Settings.System.STATUS_BAR_TRAFFIC);
            resolver.registerContentObserver(uri, false, this);
        }

        /*
         *  @hide
         */
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    /*
     *  @hide
     */
    public Traffic(Context context) {
        this(context, null);
    }

    /*
     *  @hide
     */
    public Traffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /*
     *  @hide
     */
    public Traffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Handler mHandler = new Handler();
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
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            mContext.unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null && action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateSettings();
            }
        }
    };

    private boolean getConnectAvailable() {
        ConnectivityManager connManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = (connManager != null) ? connManager.getActiveNetworkInfo() : null;
        return network != null && network.isConnected();
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        int intState = Settings.System.getInt(resolver, Settings.System.STATUS_BAR_TRAFFIC, 0);
        if (intState == 2) {
            KB = KILOBYTE;
        } else {
            KB = KILOBIT;
        }
        MB = KB * KB;
        GB = MB * KB;

        if (intState > 0) {
            if (getConnectAvailable()) {
                if (mAttached) {
                    totalRxBytes = TrafficStats.getTotalRxBytes();
                    lastUpdateTime = SystemClock.elapsedRealtime();
                    mTrafficHandler.sendEmptyMessage(1);
                }
                setVisibility(View.VISIBLE);
                return;
            }
        } else {
            mTrafficHandler.removeCallbacks(mRunnable);
            mTrafficHandler.removeMessages(0);
            mTrafficHandler.removeMessages(1);
        }
    setVisibility(View.GONE);
    }
}
