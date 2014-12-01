/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

public class WeatherControllerImpl implements WeatherController {

    private static final String TAG = WeatherController.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final Receiver mReceiver = new Receiver();
    private final Context mContext;

    private String mCachedTemperature;

    public WeatherControllerImpl(Context context) {
        mContext = context;
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void addCallback(Callback callback) {
        if (callback == null || mCallbacks.contains(callback)) return;
        if (DEBUG) Log.d(TAG, "addCallback " + callback);
        mCallbacks.add(callback);
        mReceiver.setListening(!mCallbacks.isEmpty());
    }

    public void removeCallback(Callback callback) {
        if (callback == null) return;
        if (DEBUG) Log.d(TAG, "removeCallback " + callback);
        mCallbacks.remove(callback);
        mReceiver.setListening(!mCallbacks.isEmpty());
    }

    @Override
    public String getTemperature() {
        if (mCachedTemperature == null) {
            mCachedTemperature = queryTemperature();
        }
        return mCachedTemperature;
    }

    private String queryTemperature() {
        String[] projection = {
                "temperature"
        };

        Cursor c = mContext.getContentResolver().query(
                Uri.parse("content://com.cyanogenmod.lockclock.weather.provider/weather/current"),
                projection, null, null, null);
        if (c == null) {
            mContext.sendBroadcast(new Intent("com.cyanogenmod.lockclock.action.FORCE_WEATHER_UPDATE"));
            if(DEBUG) Log.e(TAG, "cursor was null for temperature");
            return null;
        }
        try {
            c.moveToFirst();
            String weather = c.getString(0);
            if (weather == null) {
                weather = "";
            }
            return weather;
        } finally {
            c.close();
        }
    }


    private void fireCallback(String temp) {
        for (Callback callback : mCallbacks) {
            callback.onWeatherChanged(temp);
        }
    }

    private final class Receiver extends BroadcastReceiver {
        private boolean mRegistered;

        public void setListening(boolean listening) {
            if (listening && !mRegistered) {
                if (DEBUG) Log.d(TAG, "Registering receiver");
                final IntentFilter filter = new IntentFilter();
                filter.addAction("com.cyanogenmod.lockclock.action.WEATHER_UPDATE_FINISHED");
                mContext.registerReceiver(this, filter);
                mRegistered = true;
                // first register, force a query
                goAsync();
                mCachedTemperature = queryTemperature();
                fireCallback(mCachedTemperature);
            } else if (!listening && mRegistered) {
                if (DEBUG) Log.d(TAG, "Unregistering receiver");
                mContext.unregisterReceiver(this);
                mRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive " + intent.getAction());
            mCachedTemperature = queryTemperature();
            fireCallback(mCachedTemperature);
        }
    }

}
