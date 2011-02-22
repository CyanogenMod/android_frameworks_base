/*
 * Copyright (C) 2011 The CyanogenMod Project
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
package com.android.wimax;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

/* @hide */
public class WimaxSettingsHelper {
    
    private static final String TAG = "WimaxSettingsHelper";
    
    private final Object mWimaxController;
    
    public WimaxSettingsHelper(Context context) {
        mWimaxController = context.getSystemService(Context.WIMAX_SERVICE);
    }

    public boolean isWimaxSupported() {
        return mWimaxController != null;
    }

    public boolean isWimaxEnabled() {
        boolean ret = false;
        try {
            Method m = mWimaxController.getClass().getMethod("isWimaxEnabled");
            ret = (Boolean) m.invoke(mWimaxController);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get WiMAX enabled state!", e);
        }
        return ret;
    }

    public boolean setWimaxEnabled(boolean enabled) {
        boolean ret = false;
        try {
            Method m = mWimaxController.getClass().getMethod("setWimaxEnabled", boolean.class);
            ret = (Boolean) m.invoke(mWimaxController, enabled);
        } catch (Exception e) {
            Log.e(TAG, "Unable to set WiMAX state!", e);
        }
        return ret;
    }

    public int getWimaxState() {
        int ret = 0;
        try {
            Method m = mWimaxController.getClass().getMethod("getWimaxState");
            ret = (Integer) m.invoke(mWimaxController);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get WiMAX state!", e);
        }
        return ret;
    }
    
    private Object getWimaxInfo() {
        Object wimaxInfo = null;
        try {
            Method getConnectionInfo = mWimaxController.getClass().getMethod("getConnectionInfo");
            wimaxInfo = getConnectionInfo.invoke(mWimaxController);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get a WimaxInfo object!", e);
        }
        return wimaxInfo;
    }

    public int getSignalStrength() {
        int signalStrength = 150;
        Object wimaxInfo = getWimaxInfo();
        if (wimaxInfo != null) {
            try {
                Method getSignalStrength = wimaxInfo.getClass().getMethod("getSignalStrength");
                signalStrength = (Integer) getSignalStrength.invoke(wimaxInfo);
            } catch (Exception e) {
                Log.e(TAG, "Unable to get WiMAX signal strength!", e);
            }
        }
        return signalStrength;
    }

    public int calculateSignalLevel(int rssi, int bars) {
        int signalLevel = 0;
        try {
            Method calculateSignalLevel = mWimaxController.getClass().getMethod("calculateSignalLevel", int.class, int.class);
            signalLevel = (Integer) calculateSignalLevel.invoke(null, rssi, bars);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get WiMAX signal level!", e);
        }
        return signalLevel;
    }
}
