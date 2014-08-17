/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.cm;

import android.content.Context;
import android.provider.Settings;

import java.util.Calendar;

public class QuietHoursUtils {
    private static int mQuietHoursStart = 0;
    private static int mQuietHoursEnd = 0;

    public static boolean inQuietHours(Context context) {
		return inQuietHours(context, Settings.System.QUIET_HOURS_ENABLED);
	}

    public static boolean inQuietHours(Context context, String option) {
        if (Settings.System.getInt(context.getContentResolver(),
                Settings.System.QUIET_HOURS_FORCED, 0) != 0) {
            // If Quiet hours is forced return immediately
            return true;
        }

        // Check if we are in timed Quiet hours mode
        boolean quietHoursEnabled = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0) != 0;
        boolean quietHoursOption = Settings.System.getInt(context.getContentResolver(),
                option, 0) != 0;
        boolean quietHoursEnabledTemp = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED_TEMP, 0) != 0;

        if (quietHoursEnabled && quietHoursOption) {
            if (!quietHoursEnabledTemp){
                mQuietHoursStart = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.QUIET_HOURS_START, 0);
                mQuietHoursEnd = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.QUIET_HOURS_END, 0);
            } else {
            	mQuietHoursStart = Settings.System.getInt(context.getContentResolver(),
            			Settings.System.QUIET_HOURS_START_TEMP, 0);
                mQuietHoursEnd = Settings.System.getInt(context.getContentResolver(),	
                		Settings.System.QUIET_HOURS_END_TEMP, 0);
            }
            
            return inQuietHours(context, mQuietHoursStart, mQuietHoursEnd);
            
        }

        return false;
    }

    private static boolean inQuietHours(int quietHoursStart, int quietHoursEnd){
    	return inQuietHours(null, quietHoursStart, quietHoursEnd, false);
    }

    /* Semi recursive call*/
    private static boolean inQuietHours(Context context, int quietHoursStart, int quietHoursEnd) {
        boolean quietHoursEnabledTemp = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED_TEMP, 0) != 0;
         return inQuietHours(context, quietHoursStart, quietHoursEnd, quietHoursEnabledTemp  );
    }
    
    /* Semi recursive call*/    
    private static boolean inQuietHours(Context context, int quietHoursStart, int quietHoursEnd, boolean checkQuietHours) {
        if (quietHoursStart != quietHoursEnd) {
            // Get the date in "quiet hours" format.
            Calendar cal = Calendar.getInstance();
            int minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            if (checkQuietHours && !inQuietHours(quietHoursStart, quietHoursEnd)) {
         /*     Settings.System.putInt(context.getContentResolver(), Settings.System.QUIET_HOURS_END_TEMP, 0);
                Settings.System.putInt(context.getContentResolver(), Settings.System.QUIET_HOURS_START_TEMP, 0);*/
                Settings.System.putInt(context.getContentResolver(), Settings.System.QUIET_HOURS_ENABLED_TEMP, 0);  
            }
            if (quietHoursEnd < quietHoursStart) {
                // Starts at night, ends in the morning.
                return (minutes >= quietHoursStart) || (minutes < quietHoursEnd);
            } else {
                // Starts in the morning, ends at night.
                return (minutes >= quietHoursStart) && (minutes < quietHoursEnd);
            }
        }
        return false;
    }
    public static int getQuietHoursStart(){
    	return mQuietHoursStart;
    }
    public static int getQuietHoursEnd(){
    	return mQuietHoursEnd;
    }
}
