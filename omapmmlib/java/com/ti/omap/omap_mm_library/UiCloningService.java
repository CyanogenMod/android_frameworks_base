/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.ti.omap.omap_mm_library;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import android.util.Log;

public final class UiCloningService {
    private String TAG = "UiCloningService";

    static {
        /*
         * Load the library.  If it's already loaded, this does nothing.
         */
        System.loadLibrary("omap_mm_library_jni");
    }

    // All possible Display Types on OMAP4.
    private static final int DISPLAY_TYPE_NONE          = -1;
    private static final int DISPLAY_TYPE_LCD_SECONDARY = 1;
    private static final int DISPLAY_TYPE_HDMI_TV       = 2;

    private static final String ACTION_HDMI_PLUG = "android.intent.action.HDMI_PLUG";
    // Broadcast receiver for device connections intent broadcasts
    private final BroadcastReceiver mReceiver = new UiCloningBroadcastReceiver();

    public UiCloningService(Context context) {
        IntentFilter intentFilter = new IntentFilter(ACTION_HDMI_PLUG);
        context.registerReceiver(mReceiver, intentFilter);
    }

    private class UiCloningBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(ACTION_HDMI_PLUG)) {
                int state = intent.getIntExtra("state", 0);

                if(state==1) {
                    Log.d(TAG, "UiCloningBroadcastReceiver.onReceive() : Cloning UI to HDMI-TV");
                    CloneUiToDisplay(DISPLAY_TYPE_HDMI_TV);
                } else {
                    Log.d(TAG, "UiCloningBroadcastReceiver.onReceive() : Stopping UI on HDMI-TV");
                    CloneUiToDisplay(DISPLAY_TYPE_NONE);
                }
            }
        }
    }

    native public void CloneUiToDisplay(int displayId);
}

