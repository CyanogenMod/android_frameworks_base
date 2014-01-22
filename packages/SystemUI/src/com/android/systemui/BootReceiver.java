/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.provider.Settings;
import android.util.Log;

/**
 * Performs a number of miscellaneous, non-system-critical actions
 * after the system has finished booting.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "SystemUIBootReceiver";

    private static final String KEY_MUSIC_TIMER =
            "key_music_timer";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final SharedPreferences shared = context.getSharedPreferences(
                KEY_MUSIC_TIMER, Context.MODE_PRIVATE);
        shared.edit().putBoolean("scheduled", false).commit();
        shared.edit().putInt("hour", -1).commit();
        shared.edit().putInt("minutes", -1).commit();

        try {
            // Start the load average overlay, if activated
            ContentResolver res = context.getContentResolver();
            if (Settings.Global.getInt(res, Settings.Global.SHOW_PROCESSES, 0) != 0) {
                Intent loadavg = new Intent(context, com.android.systemui.LoadAverageService.class);
                context.startService(loadavg);
            }
    	    // start the screen state service if activated
            if (Settings.System.getBoolean(res, Settings.System.START_SCREEN_STATE_SERVICE, false)) {
                Intent screenstate = new Intent(context, com.android.systemui.screenstate.ScreenStateService.class);
                context.startService(screenstate);
            }

        } catch (Exception e) {
            Log.e(TAG, "Can't start load average service", e);
        }
    }
}
