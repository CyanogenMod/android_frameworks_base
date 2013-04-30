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

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.android.systemui.R;

import java.util.ArrayList;

public class DockBatteryController extends BatteryController {
    private static final String TAG = "StatusBar.DockBatteryController";

    private int mDockBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private boolean mBatteryPlugged = false;
    private boolean mBatteryPresent = false;

    public DockBatteryController(Context context) {
        this(context, true);
    }

    public DockBatteryController(Context context, boolean ui) {
        super(context, ui);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0);
            mDockBatteryStatus = intent.getIntExtra(
                                        BatteryManager.EXTRA_DOCK_STATUS,
                                        BatteryManager.BATTERY_STATUS_UNKNOWN);
            mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_DOCK_PLUGGED, 0) != 0;
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT, false);
            updateViews(level);
            updateBattery();
        }
    }

    @Override
    protected void updateViews(int level) {
        if (isUiController()) {
            super.updateViews(level);
        }

        for (DockBatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onDockBatteryLevelChanged(level, isBatteryPresent(), isBatteryStatusCharging());
        }
    }

    private ArrayList<DockBatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<DockBatteryStateChangeCallback>();

    public interface DockBatteryStateChangeCallback {
        public void onDockBatteryLevelChanged(int level, boolean present, boolean pluggedIn);
    }

    public void addStateChangedCallback(DockBatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void removeStateChangedCallback(DockBatteryStateChangeCallback cb) {
        mChangeCallbacks.remove(cb);
    }

    @Override
    public int getIconStyleUnknown() {
        return R.drawable.stat_sys_kb_battery_unknown;
    }
    @Override
    public int getIconStyleNormal() {
        return R.drawable.stat_sys_kb_battery;
    }
    @Override
    public int getIconStyleCharge() {
        return R.drawable.stat_sys_kb_battery_charge;
    }
    @Override
    public int getIconStyleNormalMin() {
        return R.drawable.stat_sys_kb_battery_min;
    }
    @Override
    public int getIconStyleChargeMin() {
        return R.drawable.stat_sys_kb_battery_charge_min;
    }

    @Override
    protected int getBatteryStatus() {
        return mDockBatteryStatus;
    }

    @Override
    protected boolean isBatteryPlugged() {
        return mBatteryPlugged;
    }

    @Override
    protected boolean isBatteryPresent() {
        return mBatteryPresent;
    }

}
