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

import java.util.ArrayList;

public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private int mLevel = 0;
    private boolean mPluggedIn;

    private ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    // For HALO
    private ArrayList<BatteryStateChangeCallbackHalo> mChangeCallbacksHalo =
            new ArrayList<BatteryStateChangeCallbackHalo>();

    // For HALO
    public interface BatteryStateChangeCallbackHalo {
        public void onBatteryLevelChangedHalo(int level, boolean pluggedIn);
    }

    // For Halo 
    public void addStateChangedCallbackHalo(BatteryStateChangeCallbackHalo cb_Halo) {
        mChangeCallbacksHalo.add(cb_Halo);
    }

    // For Halo 
    public void removeStateChangedCallbackHalo(BatteryStateChangeCallbackHalo cb_Halo) {
        mChangeCallbacksHalo.remove(cb_Halo);
    }  

    private int mBatteryLevel = 0;
    private int mBatteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN;
    private boolean mBatteryPlugged = false;

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    public BatteryController(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(this, filter);
    }

    public void addStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public void removeStateChangedCallback(BatteryStateChangeCallback cb) {
        mChangeCallbacks.remove(cb);
    }

    public void unregisterController(Context context) {
        context.unregisterReceiver(this);
    }

    public int getBatteryLevel() {
        return mLevel;
    }

    public boolean isBatteryStatusCharging() {
        return mPluggedIn;
    }

    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            final int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);

            mPluggedIn = false;
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                case BatteryManager.BATTERY_STATUS_FULL:
                    mPluggedIn = true;
                    break;
            }

            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
                cb.onBatteryLevelChanged(mLevel, mPluggedIn);
            }
	
	    // For HALO
            for (BatteryStateChangeCallbackHalo cb_Halo : mChangeCallbacksHalo) {
                cb_Halo.onBatteryLevelChangedHalo(mBatteryLevel, mBatteryPlugged);
            }
        }
    }
}
