/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.internal.weather;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import org.w3c.dom.Document;

import java.io.IOException;

import com.android.internal.R;

import com.android.internal.weather.HttpRetriever;
import com.android.internal.weather.WeatherInfo;
import com.android.internal.weather.WeatherXmlParser;
import com.android.internal.weather.YahooPlaceFinder;

public class WeatherUpdateService extends Service {

    private static final String URL_YAHOO_API_WEATHER = "http://weather.yahooapis.com/forecastrss?w=%s&u=";
    public static WeatherInfo mWeatherInfo = new WeatherInfo();
    private static final boolean D = true;
    private static final String TAG = "KeyguardWeatherService";
    private static String CachedLocation, CachedWoeid;
    private static boolean isFirstStart = true;

    private static final String UPDATE_COMPLETE_ACTION = "com.android.internal.policy.impl.keyguard.UPDATE_COMPLETE";
    private static final String REFRESH_UPDATE_INTERVAL = "com.android.internal.weather.REFRESH_UPDATE_INTERVAL";
    private static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";

    private WeatherUpdateTask mTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (D) Log.d(TAG, "onStartCommand");

        final boolean enabled = Settings.System.getInt(this.getContentResolver(),
                    Settings.System.LOCKSCREEN_WEATHER, 0) == 1;
        final long updateInterval = Settings.System.getLong(this.getContentResolver(),
                    Settings.System.WEATHER_UPDATE_INTERVAL, 60); // Default to hourly

        String action = intent.getAction();
        if (action != null) {
            Log.d(TAG, "StartCommand got action: " + action);
            if(action.equals(BOOT_COMPLETED)) {
                scheduleUpdates(this, 30 * 1000);
            }
            if(action.equals(REFRESH_UPDATE_INTERVAL)) {
                if (!enabled)
                {
                    cancelUpdates(this);
                } else if (!isFirstStart) {
                    scheduleUpdates(this, updateInterval * 60 * 1000);
               }
            }
        }

        if (shouldUpdate())
        {
            if (isFirstStart)
            {
                scheduleUpdates(this, updateInterval * 60 * 1000);
                isFirstStart = false;
            }
            mTask = new WeatherUpdateTask();
            mTask.execute();
            return Service.START_REDELIVER_INTENT;
        } else {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
    }

    private boolean shouldUpdate() {
        if (Settings.System.getInt(this.getContentResolver(),
                    Settings.System.LOCKSCREEN_WEATHER, 0) == 0) {
            if (D) Log.d(TAG, "Keyguard Weather is disabled");
            return false;
        }

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();

        if (info == null || !info.isConnected()) {
            if (D) Log.d(TAG, "No network connection is available for weather update");
            return false;
        }

        return true;
    }

    private static void scheduleUpdates(Context context, long period) {

        if (period != 0) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), period, getUpdateIntent(context));
            Log.v(TAG, "Setting up periodical updates: " + period + "ms");
        } else {
            Log.v(TAG, "Disabling periodical updates");
            cancelUpdates(context);
        }
    }

    public static void cancelUpdates(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getUpdateIntent(context));
    }

    public static PendingIntent getUpdateIntent(Context context) {
        Intent i = new Intent(context, WeatherUpdateService.class);
        return PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private class WeatherUpdateTask extends AsyncTask<Void, Void, WeatherInfo> {
        private WakeLock mWakeLock;
        private Context mContext;

        private static final int RESULT_SUCCESS = 0;
        private static final int RESULT_FAILURE = 1;
        private static final int RESULT_CANCELLED = 2;

        public WeatherUpdateTask() {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            mContext = WeatherUpdateService.this;
        }

        @Override
        protected void onPreExecute() {
            mWakeLock.acquire();
        }

        private String getWoeidForCustomLocation(String location) {
            // first try with the cached woeid, no need to constantly query constant information
            if (CachedLocation == null) {
                CachedLocation = location;
                try {
                    CachedWoeid = YahooPlaceFinder.GeoCode(mContext, location);
                } catch (Exception e) {
                    Log.e(TAG, "Error during GeoCode");
                }
            } else if (CachedLocation != location) {
                try {
                    CachedLocation = location;
                    CachedWoeid = YahooPlaceFinder.GeoCode(mContext, location);
                } catch (Exception e) {
                    Log.e(TAG, "Error during GeoCode");
                }
            } else if (CachedWoeid == null) {
                try {
                    CachedWoeid = YahooPlaceFinder.GeoCode(mContext, location);
                } catch (Exception e) {
                    Log.e(TAG, "Error during GeoCode");
                }
            }
            if (D) Log.v(TAG, "Yahoo location code for " + location + " is " + CachedWoeid);
            return CachedWoeid;
        }

        private String getWoeidForCurrentLocation(Location location) {
            String woeid;
            try {
                woeid = YahooPlaceFinder.reverseGeoCode(mContext,
                    location.getLatitude(), location.getLongitude());
            } catch (Exception e) {
                Log.e(TAG, "Error during reverseGeoCod");
                return null;
            }
            if (woeid == null) {
                // we couldn't fetch up-to-date information, fall back to cache
                woeid = CachedWoeid;
            }
            if (D) Log.v(TAG, "Yahoo location code for current geolocation " + location + " is " + woeid);
            return woeid;
        }

        private Location getCurrentLocation() {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            try {
                Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                if (D) Log.v(TAG, "Current location is " + location);
                return location;
            } catch (Exception e) {
                Log.e(TAG, "Error getting LastKnownLocation");
                return null;
            }
        }

        private Document getDocument(String woeid) {
            boolean celcius = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.WEATHER_USE_METRIC, 1) == 1;
            String urlWithUnit = URL_YAHOO_API_WEATHER + (celcius ? "c" : "f");

            try {
                return new HttpRetriever().getDocumentFromURL(String.format(urlWithUnit, woeid));
            } catch (IOException e) {
                Log.e(TAG, "Couldn't fetch weather data", e);
            }
            return null;
        }

        @Override
        protected WeatherInfo doInBackground(Void... params) {
            String customLocation = null;
            String woeid;


            if (Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.WEATHER_USE_CUSTOM_LOCATION, 0) == 1) {
                customLocation = Settings.System.getString(mContext.getContentResolver(),
                                    Settings.System.WEATHER_CUSTOM_LOCATION);
            }

            if (customLocation != null) {
                woeid = getWoeidForCustomLocation(customLocation);
            } else {
                Location location = getCurrentLocation();
                if (location != null || isCancelled()) {
                    woeid = getWoeidForCurrentLocation(location);
                } else {
                    return null;
                }
            }

            if (woeid == null || isCancelled()) {
                return null;
            }

            Document doc = getDocument(woeid);
            if (doc == null || isCancelled()) {
                return null;
            }

            return new WeatherXmlParser(mContext).parseWeatherResponse(doc);
        }

        @Override
        protected void onPostExecute(WeatherInfo result) {
            finish(result);
        }

        @Override
        protected void onCancelled() {
            finish(null);
        }

        private void finish(WeatherInfo result) {

            final long updateInterval = Settings.System.getLong(mContext.getContentResolver(),
                                Settings.System.WEATHER_UPDATE_INTERVAL, 60); // Default to hourly

            if (result != null) {
                mWeatherInfo = result;
                Intent updateIntent = new Intent();
                updateIntent.setAction(UPDATE_COMPLETE_ACTION);
                sendBroadcast(updateIntent);
            } else if (isCancelled()) {
                /* cancelled, likely due to lost network - we'll get restarted
                 * when network comes back */
            } else {
                /* failure */
            }

            mWakeLock.release();
            stopSelf();
        }
    }

}
