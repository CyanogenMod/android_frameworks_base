/*
 * Copyright (C) 2014-2015 ParanoidAndroid Project
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

package com.android.systemui.statusbar.pie;

import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.text.format.DateFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Pie Helper
 * Util class: handles system status changes and getting system state.
 * Singleton that must be intialized.
 */
public class PieHelper {

    public static int LOW_BATTERY_LEVEL;
    public static int CRITICAL_BATTERY_LEVEL;

    private static PieHelper mInstance;

    private boolean mTelephony;
    private int mBatteryLevel = 0;
    private BaseStatusBar mBar;
    private Context mContext;
    private OnClockChangedListener mClockChangedListener;

    private KeyguardManager mKeyguardManager;

    private BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            mBatteryLevel = intent.getIntExtra("level", 0);
        }
    };

    private final BroadcastReceiver mClockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mClockChangedListener == null) return;
            mClockChangedListener.onChange(getSimpleTime());
        }
    };

    public interface OnClockChangedListener {
        public abstract void onChange(String s);
    }

    public void init(Context context, BaseStatusBar bar) {
        mBar = bar;
        mContext = context;
        mContext.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        mContext.registerReceiver(mClockReceiver, filter);
        LOW_BATTERY_LEVEL = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        CRITICAL_BATTERY_LEVEL = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        mTelephony = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        mKeyguardManager = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
    }

    public static PieHelper getInstance() {
        if (mInstance == null) mInstance = new PieHelper();
        return mInstance;
    }

    public void setOnClockChangedListener(OnClockChangedListener l) {
        mClockChangedListener = l;
    }

    public boolean supportsTelephony() {
        return mTelephony;
    }

    public int getCount() {
        return mBar.getNotificationCount();
    }

    public String getWifiSsid() {
        String ssid = mContext.getString(R.string.quick_settings_wifi_not_connected);
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) mContext
                    .getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            ssid = connectionInfo.getSSID();
        }
        return ssid.toUpperCase();
    }

    public String getNetworkProvider() {
        String operatorName = mContext.getString(R.string.quick_settings_wifi_no_network);
        TelephonyManager telephonyManager = (TelephonyManager) mContext
                .getSystemService(Context.TELEPHONY_SERVICE);
        operatorName = telephonyManager.getNetworkOperatorName();
        if (operatorName == null) {
            operatorName = telephonyManager.getSimOperatorName();
        }
        return operatorName.toUpperCase();
    }

    public String getNetworkType() {
        return TelephonyManager.getDefault().getNetworkTypeName();
    }

    public String getSimpleDate() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                mContext.getString(R.string.pie_date_format));
        String date = sdf.format(new Date());
        return date.toUpperCase();
    }

    public boolean is24Hours() {
        return DateFormat.is24HourFormat(mContext);
    }

    public String getSimpleTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                mContext.getString(is24Hours() ? R.string.pie_hour_format_24 :
                        R.string.pie_hour_format_12));
        String time = sdf.format(new Date());
        return time.toUpperCase();
    }

    public String getAmPm() {
        String amPm = "";
        if (!is24Hours()) {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    mContext.getString(R.string.pie_am_pm));
            amPm = sdf.format(new Date()).toUpperCase();
        }
        return amPm.replace(".", ""); // show either PM or AM, not P.M. or A.M.
    }

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public String getBatteryLevelReadable() {
        return mContext.getString(R.string.battery_low_percent_format, mBatteryLevel)
                .toUpperCase();
    }

    public boolean isAssistantAvailable() {
        return false;
    }

    public void startAssistActivity() {
    }

    public boolean isKeyguardShowing() {
        return mKeyguardManager.isKeyguardLocked();
    }
}
