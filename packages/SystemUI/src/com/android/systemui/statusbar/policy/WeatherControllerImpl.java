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

package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.util.Log;
import com.android.systemui.R;
import cyanogenmod.providers.WeatherContract;
import cyanogenmod.weather.CMWeatherManager;
import cyanogenmod.weather.util.WeatherUtils;

import static cyanogenmod.providers.WeatherContract.WeatherColumns.CURRENT_TEMPERATURE;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.CURRENT_CITY;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.CURRENT_TEMPERATURE_UNIT;
import static cyanogenmod.providers.WeatherContract.WeatherColumns.CURRENT_CONDITION_CODE;

import java.util.ArrayList;

public class WeatherControllerImpl implements WeatherController {

    private static final String TAG = WeatherController.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final ComponentName COMPONENT_WEATHER_FORECAST = new ComponentName(
            "com.cyanogenmod.lockclock", "com.cyanogenmod.lockclock.weather.ForecastActivity");
    public static final String ACTION_FORCE_WEATHER_UPDATE
            = "com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE";
    private static final String[] WEATHER_PROJECTION = new String[]{
            CURRENT_TEMPERATURE,
            CURRENT_TEMPERATURE_UNIT,
            CURRENT_CITY,
            CURRENT_CONDITION_CODE
    };

    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final Receiver mReceiver = new Receiver();
    private final Context mContext;

    private WeatherInfo mCachedInfo = new WeatherInfo();

    public WeatherControllerImpl(Context context) {
        mContext = context;
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        queryWeather();
        final IntentFilter filter = new IntentFilter();
        filter.addAction(CMWeatherManager.WEATHER_UPDATED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
    }

    public void addCallback(Callback callback) {
        if (callback == null || mCallbacks.contains(callback)) return;
        if (DEBUG) Log.d(TAG, "addCallback " + callback);
        mCallbacks.add(callback);
        callback.onWeatherChanged(mCachedInfo); // immediately update with current values
    }

    public void removeCallback(Callback callback) {
        if (callback == null) return;
        if (DEBUG) Log.d(TAG, "removeCallback " + callback);
        mCallbacks.remove(callback);
    }

    @Override
    public WeatherInfo getWeatherInfo() {
        return mCachedInfo;
    }

    private void queryWeather() {
        Cursor c = mContext.getContentResolver().query(
                WeatherContract.WeatherColumns.CURRENT_WEATHER_URI, WEATHER_PROJECTION,
                null, null, null);
        if (c == null) {
            if(DEBUG) Log.e(TAG, "cursor was null for temperature, forcing weather update");
            //LockClock keeps track of the user settings (temp unit, search by geo location/city)
            //so we delegate the responsibility of handling a weather update to LockClock
            mContext.sendBroadcast(new Intent(ACTION_FORCE_WEATHER_UPDATE));
        } else {
            try {
                c.moveToFirst();
                mCachedInfo.temp = WeatherUtils.formatTemperature(c.getFloat(0), c.getInt(1));
                mCachedInfo.city = c.getString(2);
                mCachedInfo.condition = mapWeatherCodeToString(c.getInt(3));
            } finally {
                c.close();
            }
        }
    }

    private String mapWeatherCodeToString(int conditionCode) {
        final Resources res = mContext.getResources();
        final int resId = res.getIdentifier("weather_" + conditionCode,
                "string", mContext.getPackageName());
        if (resId != 0) {
            return res.getString(resId);
        }
        return "";
    }

    private void fireCallback() {
        for (Callback callback : mCallbacks) {
            callback.onWeatherChanged(mCachedInfo);
        }
    }

    private final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive " + intent.getAction());
            queryWeather();
            fireCallback();
        }
    }

}
