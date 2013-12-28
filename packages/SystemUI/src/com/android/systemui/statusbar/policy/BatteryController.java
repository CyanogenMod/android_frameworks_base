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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import com.android.systemui.BatteryMeterView.BatteryMeterMode;

import java.util.ArrayList;

public class BatteryController extends BroadcastReceiver {

    protected ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    protected int mBatteryLevel = 0;
    protected int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    protected boolean mBatteryPlugged = false;
    protected boolean mBatteryPresent = true;

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn,
                int status);
        public void onBatteryMeterModeChanged(BatteryMeterMode mode);
        public void onBatteryMeterShowPercent(boolean showPercent);
    }

    public BatteryController(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(this, filter);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
        // trigger initial update
        cb.onBatteryLevelChanged(mBatteryPresent, mBatteryLevel, mBatteryPlugged, mBatteryStatus);
    }

    public void removeStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.remove(cb);
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
            mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);

            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
                cb.onBatteryLevelChanged(mBatteryPresent, mBatteryLevel, mBatteryPlugged,
                        mBatteryStatus);
            }
        }
    }

    public void onBatteryMeterModeChanged(BatteryMeterMode mode) {
        for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onBatteryMeterModeChanged(mode);
        }
    }

    public void onBatteryMeterShowPercent(boolean showPercent) {
        for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            cb.onBatteryMeterShowPercent(showPercent);
        }
    }
}
