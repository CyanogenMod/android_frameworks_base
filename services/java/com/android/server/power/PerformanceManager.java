/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.server.power;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.regex.Pattern;

class PerformanceManager {
    private static final String TAG = "PerformanceManager";

    private final Context mContext;
    
    private final String mPerfProfileDefault;
    private final String mPerfProfileProperty;

    private Pattern[] mPatterns = null;
    private String[] mProfiles = null;
    
    private boolean mProfileSetByUser = false;
    
    private Object mLock = new Object();
    
    PerformanceManager(Context context) {
        mContext = context;
        
        String[] activities = context.getResources().getStringArray(
                com.android.internal.R.array.config_auto_perf_activities);
        if (activities != null && activities.length > 0) {
            mPatterns = new Pattern[activities.length];
            mProfiles = new String[activities.length];
            for (int i = 0; i < activities.length; i++) {
                String[] info = activities[i].split(",");
                if (info.length == 2) {
                    mPatterns[i] = Pattern.compile(info[0]);
                    mProfiles[i] = info[1];
                }
            }
        }
        
        mPerfProfileDefault = context.getResources().getString(
                com.android.internal.R.string.config_perf_profile_default_entry);
        mPerfProfileProperty = context.getResources().getString(
                com.android.internal.R.string.config_perf_profile_prop);
       
        if (hasProfiles()) {
            SystemProperties.set(mPerfProfileProperty, getPowerProfile());
        }
    }

    private boolean hasProfiles() {
        return !TextUtils.isEmpty(mPerfProfileProperty) &&
               !TextUtils.isEmpty(mPerfProfileDefault);
    }
    
    private boolean hasAppProfiles() {
        return hasProfiles() && mPatterns != null &&
               (Settings.System.getInt(mContext.getContentResolver(),
               Settings.System.APP_PERFORMANCE_PROFILES_ENABLED, 1) == 1);
    }
    
    void setPowerProfile(String profile) {
        if (!hasProfiles()) {
            throw new IllegalArgumentException("Power profiles not enabled on this device");
        }
        if (profile == null || profile.equals(getPowerProfile())) {
            return;
        }
        synchronized (mLock) {
            mProfileSetByUser = !profile.equals(mPerfProfileDefault);
            setPowerProfileLocked(profile);
        }
    }
        
    private void setPowerProfileLocked(String profile) {
        long token = Binder.clearCallingIdentity();
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.PERFORMANCE_PROFILE, profile);
        
        // TODO: Move this to the HAL
        SystemProperties.set(mPerfProfileProperty, profile);
        Binder.restoreCallingIdentity(token);
    }
    
    String getPowerProfile() {
        if (!hasProfiles()) {
            return null;
        }
        String cur = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.PERFORMANCE_PROFILE);
        return cur != null ? cur : mPerfProfileDefault;
    }
    
    private String getProfileForActivity(Intent intent) {
        if (intent != null) {
            ComponentName cn = intent.getComponent();
            if (cn != null) {
                String activity = cn.flattenToString();
               
                for (int i = 0; i < mPatterns.length; i++) {
                    if (mPatterns[i].matcher(activity).matches()) {
                        return mProfiles[i];
                    }
                }
            }
        }
        return mPerfProfileDefault;
    }
    
    void activityResumed(Intent intent) {
        if (!hasAppProfiles()) {
            return;
        }
        
        synchronized (mLock) {
            String current = getPowerProfile();
            
            // Don't mess with it if the user has manually set a profile
            if (mProfileSetByUser) {
                return;
            }
        
            String forApp = getProfileForActivity(intent);
            if (forApp.equals(current)) {
                return;
            }
            
            setPowerProfileLocked(forApp);
        }
    }
}
