/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    private static final int BATTERY_STYLE_NORMAL       = 0;
    private static final int BATTERY_STYLE_TEXT         = 1;
    private static final int BATTERY_STYLE_TEXT_NO_ICON = 2;
    private static final int BATTERY_STYLE_CIRCLE       = 3;
    private static final int BATTERY_STYLE_FULL         = 4;
    private static final int BATTERY_STYLE_GAUGE        = 5;
    private static final int BATTERY_STYLE_HONEYCOMB    = 6;
    private static final int BATTERY_STYLE_DROID        = 7;
    private static final int BATTERY_STYLE_SPHERE       = 8;
    private static final int BATTERY_STYLE_NUMBERS      = 9;
    private static final int BATTERY_STYLE_DIGITAL      = 10;
    private static final int BATTERY_STYLE_GONE         = 11;

    private static final int BATTERY_ICON_STYLE_NORMAL                 = R.drawable.stat_sys_battery;
    private static final int BATTERY_ICON_STYLE_CHARGE                 = R.drawable.stat_sys_battery_charge;
    private static final int BATTERY_ICON_STYLE_NORMAL_MIN             = R.drawable.stat_sys_battery_min;
    private static final int BATTERY_ICON_STYLE_CHARGE_MIN             = R.drawable.stat_sys_battery_charge_min;
    private static final int BATTERY_ICON_STYLE_CIRCLE                 = R.drawable.stat_sys_battery_circle;
    private static final int BATTERY_ICON_STYLE_CIRCLE_CHARGE          = R.drawable.stat_sys_battery_circle_charge;
    private static final int BATTERY_ICON_STYLE_FULL_CIRCLE            = R.drawable.stat_sys_battery_full_circle;
    private static final int BATTERY_ICON_STYLE_FULL_CIRCLE_CHARGE     = R.drawable.stat_sys_battery_full_circle_charge;
    private static final int BATTERY_ICON_STYLE_GAUGE                  = R.drawable.stat_sys_battery_gauge;
    private static final int BATTERY_ICON_STYLE_GAUGE_CHARGE           = R.drawable.stat_sys_battery_gauge_charge;
    private static final int BATTERY_ICON_STYLE_HONEYCOMB              = R.drawable.stat_sys_battery_honeycomb;
    private static final int BATTERY_ICON_STYLE_HONEYCOMB_CHARGE       = R.drawable.stat_sys_battery_honeycomb_charge;
    private static final int BATTERY_ICON_STYLE_DROID                  = R.drawable.stat_sys_battery_droid;
    private static final int BATTERY_ICON_STYLE_DROID_CHARGE           = R.drawable.stat_sys_battery_droid_charge;
    private static final int BATTERY_ICON_STYLE_SPHERE                 = R.drawable.stat_sys_battery_sphere;
    private static final int BATTERY_ICON_STYLE_SPHERE_CHARGE          = R.drawable.stat_sys_battery_sphere_charge;
    private static final int BATTERY_ICON_STYLE_NUMBERS                = R.drawable.stat_sys_battery_numbers;
    private static final int BATTERY_ICON_STYLE_NUMBERS_CHARGE         = R.drawable.stat_sys_battery_numbers_charge;
    private static final int BATTERY_ICON_STYLE_DIGITAL_NUMBERS        = R.drawable.stat_sys_battery_digital_numbers;
    private static final int BATTERY_ICON_STYLE_DIGITAL_NUMBERS_CHARGE = R.drawable.stat_sys_battery_digital_numbers_charge;

    private static final int BATTERY_TEXT_STYLE_NORMAL  = R.string.status_bar_settings_battery_meter_format;
    private static final int BATTERY_TEXT_STYLE_MIN     = R.string.status_bar_settings_battery_meter_min_format;

    private boolean mBatteryPlugged = false;
    private int mBatteryStyle;
    private int mBatteryIcon = BATTERY_ICON_STYLE_NORMAL;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BATTERY), false, this);
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public BatteryController(Context context) {
        mContext = context;
        mHandler = new Handler();

        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        updateSettings();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(this, filter);
    }

    public void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;

            int N = mIconViews.size();
            for (int i=0; i<N; i++) {
                ImageView v = mIconViews.get(i);
                v.setImageLevel(level);
                v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                        level));
            }
            N = mLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mLabelViews.get(i);
                v.setText(mContext.getString(BATTERY_TEXT_STYLE_MIN,
                        level));
            }
            updateBattery();
        }
    }

    private void updateBattery() {
        int mIcon = View.GONE;
        int mText = View.GONE;
        int mIconStyle = BATTERY_ICON_STYLE_NORMAL;

        if (mBatteryStyle == 0) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_CHARGE
                    : BATTERY_ICON_STYLE_NORMAL;
        } else if (mBatteryStyle == 1) {
            mIcon = (View.VISIBLE);
            mText = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_CHARGE_MIN
                    : BATTERY_ICON_STYLE_NORMAL_MIN;
        } else if (mBatteryStyle == 2) {
            mText = (View.VISIBLE);
        } else if (mBatteryStyle == 3) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_CIRCLE_CHARGE
                    : BATTERY_ICON_STYLE_CIRCLE;
        } else if (mBatteryStyle == 4) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_FULL_CIRCLE_CHARGE
                    : BATTERY_ICON_STYLE_FULL_CIRCLE;
        } else if (mBatteryStyle == 5) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_GAUGE_CHARGE
                    : BATTERY_ICON_STYLE_GAUGE;
        } else if (mBatteryStyle == 6) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_HONEYCOMB_CHARGE
                    : BATTERY_ICON_STYLE_HONEYCOMB;
        } else if (mBatteryStyle == 7) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_DROID_CHARGE
                    : BATTERY_ICON_STYLE_DROID;
        } else if (mBatteryStyle == 8) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_SPHERE_CHARGE
                    : BATTERY_ICON_STYLE_SPHERE;
        } else if (mBatteryStyle == 9) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_NUMBERS_CHARGE
                    : BATTERY_ICON_STYLE_NUMBERS;
        } else if (mBatteryStyle == 10) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_DIGITAL_NUMBERS_CHARGE
                    : BATTERY_ICON_STYLE_DIGITAL_NUMBERS;
        }

        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            v.setVisibility(mIcon);
            v.setImageResource(mIconStyle);
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            v.setVisibility(mText);
        }
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mBatteryStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0));
        updateBattery();
    }
}
