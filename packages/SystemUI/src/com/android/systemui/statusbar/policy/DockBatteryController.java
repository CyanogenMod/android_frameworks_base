/*
 * Copyright (C) 2013 The CyanogenMod Project
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

public class DockBatteryController extends BatteryController {

    public DockBatteryController(Context context) {
        super(context);
        mBatteryPresent = false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_DOCK_PRESENT, false);
            mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_DOCK_LEVEL, 0);
            int plugType = intent.getIntExtra(BatteryManager.EXTRA_DOCK_PLUGGED, 0);
            mBatteryPlugged = plugType == BatteryManager.BATTERY_DOCK_PLUGGED_AC;
            mBatteryStatus = intent.getIntExtra(BatteryManager.EXTRA_DOCK_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN);

            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
                cb.onBatteryLevelChanged(mBatteryPresent, mBatteryLevel, mBatteryPlugged,
                        mBatteryStatus);
            }
        }
    }
}
