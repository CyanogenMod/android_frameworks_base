/*
 * Copyright (C) 2015 The CyanogenMod Project
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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class DockBatteryController extends BroadcastReceiver implements BatteryStateRegistar {

    private final ArrayList<BatteryStateChangeCallback> mChangeCallbacks = new ArrayList<>();

    private int mLevel;
    private boolean mPresent;
    private boolean mPluggedIn;
    private boolean mCharging;
    private boolean mCharged;
    private boolean mPowerSave;

    private int mStyle;
    private int mPercentMode;
    private int mUserId;
    private SettingsObserver mObserver;

    public DockBatteryController(Context context, Handler handler) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(this, filter);

        mObserver = new SettingsObserver(context, handler);
        mObserver.observe();
    }

    public void setUserId(int userId) {
        mUserId = userId;
        mObserver.observe();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("BatteryController state:");
        pw.print("  mLevel="); pw.println(mLevel);
        pw.print("  mPresent="); pw.println(mPresent);
        pw.print("  mPluggedIn="); pw.println(mPluggedIn);
        pw.print("  mCharging="); pw.println(mCharging);
        pw.print("  mCharged="); pw.println(mCharged);
        pw.print("  mPowerSave="); pw.println(mPowerSave);
    }

    @Override
    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
        cb.onBatteryLevelChanged(mPresent, mLevel, mPluggedIn, mCharging);
        cb.onBatteryStyleChanged(mStyle, mPercentMode);
    }

    @Override
    public void removeStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.remove(cb);
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mLevel = (int)(100f
                    * intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0)
                    / intent.getIntExtra(BatteryManager.EXTRA_DOCK_SCALE, 100));
            mPresent = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT, false);
            mPluggedIn = intent.getIntExtra(BatteryManager.EXTRA_DOCK_PLUGGED, 0) != 0;

            final int status = intent.getIntExtra(BatteryManager.EXTRA_DOCK_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);
            mCharged = status == BatteryManager.BATTERY_STATUS_FULL;
            mCharging = mPluggedIn && (mCharged || status == BatteryManager.BATTERY_STATUS_CHARGING);

            fireBatteryLevelChanged();
        }
    }

    private void fireBatteryLevelChanged() {
        final int N = mChangeCallbacks.size();
        for (int i = 0; i < N; i++) {
            mChangeCallbacks.get(i).onBatteryLevelChanged(mPresent, mLevel, mPresent, mCharging);
        }
    }

    private void fireSettingsChanged() {
        final int N = mChangeCallbacks.size();
        for (int i = 0; i < N; i++) {
            mChangeCallbacks.get(i).onBatteryStyleChanged(mStyle, mPercentMode);
        }
    }

    private final class SettingsObserver extends ContentObserver {
        private ContentResolver mResolver;
        private boolean mRegistered;

        private final Uri STYLE_URI =
                Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY_STYLE);
        private final Uri PERCENT_URI =
                Settings.System.getUriFor(Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT);

        public SettingsObserver(Context context, Handler handler) {
            super(handler);
            mResolver = context.getContentResolver();
        }

        public void observe() {
            if (mRegistered) {
                mResolver.unregisterContentObserver(this);
            }
            mResolver.registerContentObserver(STYLE_URI, false, this, mUserId);
            mResolver.registerContentObserver(PERCENT_URI, false, this, mUserId);
            mRegistered = true;

            update();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            update();
        }

        private void update() {
            mStyle = Settings.System.getIntForUser(mResolver,
                    Settings.System.STATUS_BAR_BATTERY_STYLE, 0, mUserId);
            mPercentMode = Settings.System.getIntForUser(mResolver,
                    Settings.System.STATUS_BAR_SHOW_BATTERY_PERCENT, 0, mUserId);

            fireSettingsChanged();
        }
    };
}
