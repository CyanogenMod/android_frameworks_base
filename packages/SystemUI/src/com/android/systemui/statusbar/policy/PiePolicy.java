/*
 * Copyright (C) 2013 ParanoidAndroid Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.android.systemui.R;
import com.android.systemui.statusbar.util.SpnOverride;

import java.text.SimpleDateFormat;
import android.text.format.DateFormat;
import android.text.TextUtils;
import java.util.Date;

public class PiePolicy {

    public static int LOW_BATTERY_LEVEL;
    public static int CRITICAL_BATTERY_LEVEL;

    private static Context mContext;
    private static int mBatteryLevel = 0;
    private static boolean mTelephony;
    private static boolean isCN;

    private OnClockChangedListener mClockChangedListener;

    private BroadcastReceiver mBatteryReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context arg0, Intent intent) {
            mBatteryLevel = intent.getIntExtra("level", 0);
        }
    };

    private final BroadcastReceiver mClockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mClockChangedListener.onChange(getSimpleTime());
            isCN = mContext.getResources().getConfiguration().locale.getCountry().equals("CN") || mContext.getResources().getConfiguration().locale.getCountry().equals("TW");
        }
    };

    public interface OnClockChangedListener {
        public abstract void onChange(String s);
    }

    public PiePolicy(Context context) {
        mContext = context;
        mContext.registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mClockReceiver, filter);
        LOW_BATTERY_LEVEL = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_lowBatteryWarningLevel);
        CRITICAL_BATTERY_LEVEL = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_criticalBatteryWarningLevel);
        mTelephony = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        isCN = mContext.getResources().getConfiguration().locale.getCountry().equals("CN") || mContext.getResources().getConfiguration().locale.getCountry().equals("TW");
    }

    public void setOnClockChangedListener(OnClockChangedListener l){
        mClockChangedListener = l;
    }

    public boolean supportsTelephony() {
        return mTelephony;
    }

    public static String getWifiSsid() {
        String ssid = mContext.getString(R.string.quick_settings_wifi_not_connected);
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) mContext
                    .getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            ssid = NetworkController.huntForSsid(wifiManager, connectionInfo);
        }
        return ssid.toUpperCase();
    }

    public static String getNetworkProvider() {
        String operatorName = Settings.System.getString(mContext.getContentResolver(), Settings.System.CUSTOM_CARRIER_LABEL);
        if(TextUtils.isEmpty(operatorName)) {
        operatorName = mContext.getString(R.string.quick_settings_wifi_no_network);
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if(isCN) {
                String operator = telephonyManager.getNetworkOperator();
                if(TextUtils.isEmpty(operator))
                    operator = telephonyManager.getSimOperator();
                SpnOverride mSpnOverride = new SpnOverride();
                operatorName = mSpnOverride.getSpn(operator);
                if(operatorName == null) {
                    operatorName = telephonyManager.getSimOperatorName();
                }
            } else {
                operatorName = telephonyManager.getNetworkOperatorName();
                if(operatorName == null) {
                    operatorName = telephonyManager.getSimOperatorName();
                }
            }
        }
        return operatorName.toUpperCase();
    }

    public static String getSimpleDate() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                mContext.getString(R.string.pie_date_format));
        String date = sdf.format(new Date());
        return date.toUpperCase();
    }

    public static boolean is24Hours() {
        return DateFormat.is24HourFormat(mContext);
    }

    public static String getSimpleTime() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                mContext.getString(is24Hours() ? R.string.pie_hour_format_24 :
                R.string.pie_hour_format_12));
        String amPm = sdf.format(new Date());
        return amPm.toUpperCase();
    }

    public static String getAmPm() {
        String amPm = "";
        if(!is24Hours()) {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    mContext.getString(R.string.pie_am_pm));
            amPm = sdf.format(new Date()).toUpperCase();
        }
        return amPm;
    }

    public static int getBatteryLevel() {
        return mBatteryLevel;
    }

    public static String getBatteryLevelReadable() {
        return mContext.getString(R.string.battery_low_percent_format, mBatteryLevel)
                .toUpperCase();
    }
}
