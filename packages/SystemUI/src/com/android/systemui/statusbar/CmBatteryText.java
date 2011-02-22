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
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * This widget displays the percentage of the battery as a number
 */
public class CmBatteryText extends TextView {
    private boolean mAttached;

    // weather to show this battery widget or not
    private boolean mShowCmBattery;

    Handler mHandler;

    // tracks changes to settings, so status bar is auto updated the moment the
    // setting is toggled
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_CM_BATTERY), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public CmBatteryText(Context context) {
        this(context, null);
    }

    public CmBatteryText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmBatteryText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();

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

    /**
     * Handles changes ins battery level and charger connection
     */
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                updateCmBatteryText(intent);
            }
        }
    };

    /**
     * Sets the output text. Kind of onDraw of canvas based classes
     *
     * @param intent
     */
    final void updateCmBatteryText(Intent intent) {
        int level = intent.getIntExtra("level", 0);
        setText(Integer.toString(level));
    }

    /**
     * Invoked by SettingsObserver, this method keeps track of just changed
     * settings. Also does the initial call from constructor
     */
    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mShowCmBattery = (Settings.System
                .getInt(resolver, Settings.System.STATUS_BAR_CM_BATTERY, 0) == 1);

        if (mShowCmBattery)
            setVisibility(View.VISIBLE);
        else
            setVisibility(View.GONE);
    }
}
