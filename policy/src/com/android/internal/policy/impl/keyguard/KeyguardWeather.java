/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.internal.policy.impl.keyguard;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.internal.R;

import com.android.internal.weather.HttpRetriever;
import com.android.internal.weather.WeatherInfo;
import com.android.internal.weather.WeatherUpdateService;
import com.android.internal.weather.WeatherXmlParser;
import com.android.internal.weather.YahooPlaceFinder;

import org.w3c.dom.Document;

import java.io.IOException;
import java.util.Date;

/**
 * Displays calendar on LockScreen (Keyguard Widget)
 */
public class KeyguardWeather extends RelativeLayout {

    private RelativeLayout mWeatherPanel, mWeatherTempsPanel;
    private TextView mWeatherCity, mWeatherCondition, mWeatherTemp, mWeatherLowHigh, mWeatherUpdateTime;
    private ImageView mWeatherImage;
    public static final String TAG = "KeyguardWeather";

    private static final String UPDATE_COMPLETE_ACTION = "com.android.internal.policy.impl.keyguard.UPDATE_COMPLETE";
    private static final String REFRESH_UPDATE_INTERVAL = "com.android.internal.weather.REFRESH_UPDATE_INTERVAL";
    private BroadcastReceiver mIntentReceiver;
    private final Handler mHandler = new Handler();

    private static class WeatherChangedReceiver extends BroadcastReceiver {
        private KeyguardWeather mWeather;
        private Context mContext;

        public WeatherChangedReceiver(KeyguardWeather weather) {
            mWeather = weather;
            mContext = weather.getContext();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TAG, "Got action: " + action);
            if(action.equals(UPDATE_COMPLETE_ACTION)) {
                if (mWeather != null) {
                mWeather.mHandler.post(new Runnable() {
                    public void run() {
                        mWeather.refreshWeather();
                    }
                });
                }
            }
        }
    }

    public KeyguardWeather(Context context) {
        this(context, null);
    }

    public KeyguardWeather(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        refreshWeather();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mIntentReceiver == null) {
            mIntentReceiver = new WeatherChangedReceiver(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(UPDATE_COMPLETE_ACTION);
            mContext.registerReceiverAsUser(mIntentReceiver, UserHandle.OWNER, filter, null, null );
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mIntentReceiver != null) {
            mContext.unregisterReceiver(mIntentReceiver);
        }
    }

    /**
     * Reload the weather forecast
     */
    private void refreshWeather() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Context context = getContext();
        boolean showWeather = Settings.System.getInt(resolver,Settings.System.LOCKSCREEN_WEATHER, 0) == 1;

        mWeatherPanel = (RelativeLayout) findViewById(R.id.keyguard_weather_view);

        if (showWeather) {
            if (WeatherUpdateService.mWeatherInfo.last_sync == 0) {
                setNoWeatherData();
            } else {
                setWeatherData(WeatherUpdateService.mWeatherInfo);
            }
            if (mWeatherPanel != null) {
                mWeatherPanel.setOnClickListener(
                    new OnClickListener() {
                            public void onClick(View v) {
                            // Indicate we are refreshing
                           mWeatherCondition = (TextView) findViewById(R.id.keyguard_weather_condition);
                           if (mWeatherCondition != null) {
                               mWeatherCondition.setText(R.string.weather_refreshing);
                           }
                           Intent myIntent = new Intent(context, com.android.internal.weather.WeatherUpdateService.class);
                           myIntent.setAction(REFRESH_UPDATE_INTERVAL);
                           context.startService(myIntent);
                        }
                    }
                );
            }
        } else {
            // Hide the Weather panel view
            if (mWeatherPanel != null) {
                mWeatherPanel.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Display the weather information
     * @param w
     */
    private void setWeatherData(WeatherInfo w) {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        boolean showLocation = Settings.System.getInt(resolver,
                Settings.System.WEATHER_SHOW_LOCATION, 1) == 1;
        boolean showTimestamp = Settings.System.getInt(resolver,
                Settings.System.WEATHER_SHOW_TIMESTAMP, 1) == 1;
        boolean invertLowhigh = Settings.System.getInt(resolver,
                Settings.System.WEATHER_INVERT_LOWHIGH, 0) == 1;
        boolean defaultIcons = Settings.System.getInt(resolver,
                Settings.System.WEATHER_USE_ALTERNATE_ICONS, 0) == 1;

//        mWeatherPanel = (RelativeLayout) findViewById(R.id.keyguard_weather_view);
        mWeatherCity = (TextView) findViewById(R.id.keyguard_weather_city);
        mWeatherCondition = (TextView) findViewById(R.id.keyguard_weather_condition);
        mWeatherImage = (ImageView) findViewById(R.id.keyguard_weather_image);
        mWeatherTempsPanel = (RelativeLayout) findViewById(R.id.keyguard_weather_temps_panel);
        mWeatherTemp = (TextView) findViewById(R.id.keyguard_weather_temp);
        mWeatherLowHigh = (TextView) findViewById(R.id.keyguard_weather_low_high);
        mWeatherUpdateTime = (TextView) findViewById(R.id.keyguard_weather_update_time);

        if (mWeatherPanel != null) {
            if (mWeatherImage != null) {
                String conditionCode = w.condition_code;
                String condition_filename = (defaultIcons) ? "weather2_" + conditionCode : "weather_" + conditionCode;
                int resID = res.getIdentifier(condition_filename, "drawable",
                        getContext().getPackageName());

                if (resID == 0) {
                    resID = res.getIdentifier((defaultIcons) ? "weather2_na" : "weather_na", "drawable", getContext().getPackageName());
                }
                mWeatherImage.setImageDrawable(res.getDrawable(resID));
            }
            if (mWeatherCity != null) {
                mWeatherCity.setText(w.city);
                mWeatherCity.setVisibility(showLocation ? View.VISIBLE : View.GONE);
            }
            if (mWeatherCondition != null) {
                mWeatherCondition.setText(w.condition);
                mWeatherCondition.setVisibility(View.VISIBLE);
            }
            if (mWeatherUpdateTime != null) {
                Date lastTime = new Date(w.last_sync);
                String date = DateFormat.getDateFormat(getContext()).format(lastTime);
                String time = DateFormat.getTimeFormat(getContext()).format(lastTime);
                mWeatherUpdateTime.setText(date + " " + time);
                mWeatherUpdateTime.setVisibility(showTimestamp ? View.VISIBLE : View.GONE);
            }
            if (mWeatherTempsPanel != null && mWeatherTemp != null && mWeatherLowHigh != null) {
                mWeatherTemp.setText(w.temp);
                mWeatherLowHigh.setText(invertLowhigh ? w.high + " | " + w.low : w.low + " | " + w.high);
                mWeatherTempsPanel.setVisibility(View.VISIBLE);
            }

            // Show the Weather panel view
            mWeatherPanel.setVisibility(View.VISIBLE);
        }
    }

    public void setNoWeatherData() {
        final ContentResolver resolver = getContext().getContentResolver();
        final Resources res = getContext().getResources();
        boolean defaultIcons = Settings.System.getInt(resolver,
                Settings.System.WEATHER_USE_ALTERNATE_ICONS, 0) == 1;

//        mWeatherPanel = (RelativeLayout) findViewById(R.id.keyguard_weather_view);
        mWeatherCity = (TextView) findViewById(R.id.keyguard_weather_city);
        mWeatherCondition = (TextView) findViewById(R.id.keyguard_weather_condition);
        mWeatherImage = (ImageView) findViewById(R.id.keyguard_weather_image);
        mWeatherTemp = (TextView) findViewById(R.id.keyguard_weather_temp);
        mWeatherLowHigh = (TextView) findViewById(R.id.keyguard_weather_low_high);
        mWeatherUpdateTime = (TextView) findViewById(R.id.keyguard_weather_update_time);

        if (mWeatherPanel != null) {
            int resID = res.getIdentifier((defaultIcons) ? "weather2_na" : "weather_na", "drawable", getContext().getPackageName());

            if (mWeatherCity != null) {
                mWeatherCity.setText(res.getString(R.string.weather_no_data));
                mWeatherCity.setVisibility(View.VISIBLE);
            }
            if (mWeatherCondition != null) {
                mWeatherCondition.setText(res.getString(R.string.weather_tap_to_refresh));
                mWeatherCondition.setVisibility(View.VISIBLE);
            }
            if (mWeatherImage != null) {
                mWeatherImage.setImageDrawable(res.getDrawable(resID));
                mWeatherImage.setVisibility(View.VISIBLE);
            }
            if (mWeatherTemp != null) {
                mWeatherTemp.setVisibility(View.GONE);
            }
            if (mWeatherLowHigh != null) {
                mWeatherLowHigh.setVisibility(View.GONE);
            }
            if (mWeatherUpdateTime != null) {
                mWeatherUpdateTime.setVisibility(View.GONE);
            }
            mWeatherPanel.setVisibility(View.VISIBLE);
        }
    }
}
