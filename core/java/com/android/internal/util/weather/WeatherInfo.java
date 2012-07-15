/*
 * Copyright (C) 2012 The AOKP Project
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

package com.android.internal.util.weather;

import android.content.Context;
import com.android.internal.R;

public class WeatherInfo {

    public static final String NODATA = "-";

    public String city, forecast_date, condition, condition_code, temp, temp_unit,
        humidity, wind, wind_dir, speed_unit, low, high;
    public long last_sync;

    public WeatherInfo() {
        this.city = NODATA;
        this.forecast_date = NODATA;
        this.condition = NODATA;
        this.condition_code = NODATA;
        this.temp = NODATA;
        this.temp_unit = NODATA;
        this.humidity = NODATA;
        this.wind = NODATA;
        this.wind_dir = NODATA;
        this.speed_unit = NODATA;
        this.low = NODATA;
        this.high = NODATA;
        this.last_sync = 0;
    }

    public WeatherInfo(Context context, String city, String fdate, String condition, String condition_code,
            String temp, String temp_unit, String humidity,
            String wind, String wind_dir, String speed_unit,
            String low, String high, long last_sync) {
        this.city = city;
        this.forecast_date = fdate;
        this.condition = condition;
        this.condition_code = condition_code;
        this.humidity = humidity + "%";
        this.wind = calcDirection(context, wind_dir) + " " + trimSpeed(wind) + speed_unit;
        this.speed_unit = speed_unit;
        this.last_sync = last_sync;
        // Only the current temperature gets the temp_unit added.
        this.temp_unit = temp_unit;
        this.temp = temp + "°" + temp_unit;
        this.low = low + "°";
        this.high = high + "°";
    }

    /**
     * find the optimal weather string (helper function for translation)
     *
     * @param conditionCode condition code from Yahoo (this is the main
     *            identifier which will be used to find a matching translation
     *            in the project's resources
     * @param providedString
     * @return either the defaultString (which should be Yahoo's weather
     *         condition text), or the translated version from resources
     */
    public static String getTranslatedConditionString(Context context, int conditionCode,
            String providedString) {
        int resID = context.getResources().getIdentifier("weather_" + conditionCode, "string",
                context.getPackageName());
        return (resID != 0) ? context.getResources().getString(resID) : providedString;
    }

    private String calcDirection(Context context, String degrees) {
        try {
            int deg = Integer.parseInt(degrees);
            if (deg >= 338 || deg <= 22)
                return context.getResources().getString(R.string.weather_N);
            else if (deg < 68)
                return context.getResources().getString(R.string.weather_NE);
            else if (deg < 113)
                return context.getResources().getString(R.string.weather_E);
            else if (deg < 158)
                return context.getResources().getString(R.string.weather_SE);
            else if (deg < 203)
                return context.getResources().getString(R.string.weather_S);
            else if (deg < 248)
                return context.getResources().getString(R.string.weather_SW);
            else if (deg < 293)
                return context.getResources().getString(R.string.weather_W);
            else if (deg < 338)
                return context.getResources().getString(R.string.weather_NW);
            else
                return "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String trimSpeed(String speed) {
        try {
            return String.valueOf(Math.round(Float.parseFloat(speed)));
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
