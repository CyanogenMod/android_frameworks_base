/*
 * Copyright (C) 2014 The MoKee OpenSource Project
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

package android.mokee.util;

import android.content.res.Resources;

import com.android.internal.R;

public class SolarHoliDay {
	public static String getSolarHoliDay(int currentMonth, int currentDayForMonth) {
           Resources res = Resources.getSystem();
           String num_date = String.format("%02d", currentMonth + 1) + "" + String.format("%02d", currentDayForMonth);
           String[] solarHolidayArray = res.getStringArray(com.android.internal.R.array.solar_holiday);
           for(int i = 0; i < solarHolidayArray.length; i++) {
                String[] solarHolidayDateStr = solarHolidayArray[i].split(" ");
                if (solarHolidayDateStr[0].equals(num_date)) {
                    return solarHolidayDateStr[1];
                }
           }
           return "";
	}
}
