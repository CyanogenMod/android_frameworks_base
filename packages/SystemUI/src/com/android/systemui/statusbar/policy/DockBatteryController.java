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

public class DockBatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.DockBatteryController";

    private Context mContext;
    private ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    private static final int BATTERY_STYLE_NORMAL         = 0;
    private static final int BATTERY_STYLE_PERCENT        = 1;
    /***
     * BATTERY_STYLE_CIRCLE* cannot be handled in this controller, since we cannot get views from
     * statusbar here. Yet it is listed for completion and not to confuse at future updates
     * See CircleBattery.java for more info
     */
    private static final int BATTERY_STYLE_CIRCLE         = 2;
    private static final int BATTERY_STYLE_CIRCLE_PERCENT = 3;
    private static final int BATTERY_STYLE_GONE           = 4;

    private static final int BATTERY_ICON_STYLE_UNKNOWN = R.drawable.stat_sys_kb_battery_unknown;
    private static final int BATTERY_ICON_STYLE_NORMAL = R.drawable.stat_sys_kb_battery;
    private static final int BATTERY_ICON_STYLE_CHARGE = R.drawable.stat_sys_kb_battery_charge;

    private int mDockStatus = BatteryManager.DOCK_BATTERY_STATUS_UNKNOWN;
    private boolean mDockPresent = false;
    private int mBatteryStyle;

    private static final int BATTERY_TEXT_STYLE_MIN =
            R.string.status_bar_settings_battery_meter_min_format;

    private Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BATTERY), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public DockBatteryController(Context context) {
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
            final int level = intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0);
            mDockStatus = intent.getIntExtra(BatteryManager.EXTRA_DOCK_STATUS,
                    BatteryManager.DOCK_BATTERY_STATUS_UNKNOWN);
            mDockPresent = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT,
                    false);

            int N = mIconViews.size();
            for (int i = 0; i < N; i++) {
                ImageView v = mIconViews.get(i);
                v.setImageLevel(level);
                v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                        level));
            }

            N = mLabelViews.size();
            for (int i = 0; i < N; i++) {
                TextView v = mLabelViews.get(i);
                v.setText(mContext.getString(BATTERY_TEXT_STYLE_MIN, level));
            }

            updateBattery();
        }
    }

    private void updateBattery() {
        int icon = View.GONE;
        int text = View.GONE;
        int iconStyle = BATTERY_ICON_STYLE_UNKNOWN;

        if (mDockPresent && (mBatteryStyle == BATTERY_STYLE_NORMAL || mBatteryStyle == BATTERY_STYLE_PERCENT)) {
            icon = View.VISIBLE;

            if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
                text = View.VISIBLE;
            }

            if (mDockStatus == BatteryManager.DOCK_BATTERY_STATUS_NOT_CHARGING) {
                iconStyle = BATTERY_ICON_STYLE_NORMAL;
            } else if (mDockStatus == BatteryManager.DOCK_BATTERY_STATUS_CHARGING) {
                iconStyle = BATTERY_ICON_STYLE_CHARGE;
            }
        }

        int N = mIconViews.size();
        for (int i = 0; i < N; i++) {
            ImageView v = mIconViews.get(i);
            v.setVisibility(icon);
            v.setImageResource(iconStyle);
        }

        N = mLabelViews.size();
        for (int i = 0; i < N; i++) {
            TextView v = mLabelViews.get(i);
            v.setVisibility(text);
        }
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mBatteryStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, BATTERY_STYLE_NORMAL));

        updateBattery();
    }
}
