/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.systemui.statusbar.powerwidget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Slog;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.settings.ToggleSlider.Listener;
import com.android.systemui.settings.CurrentUserTracker;

public class BrightnessSlider implements ToggleSlider.Listener {
    private static final String TAG = "StatusBar.BrightnessController";

    private static final int MAXIMUM_BACKLIGHT = android.os.PowerManager.BRIGHTNESS_ON;

    private Context mContext;
    private ToggleSlider mControl;
    private IPowerManager mPower;
    private View mView;

    boolean mSystemChange;

    boolean mAutomatic = false;

    private final CurrentUserTracker mUserTracker;

    public BrightnessSlider(Context context) {
        mContext = context;
        mView = View.inflate(mContext, R.layout.brightness_slider, null);

        mControl = (ToggleSlider) mView.findViewById(R.id.brightness);

        mUserTracker = new CurrentUserTracker(mContext) {
            public void onUserSwitched(int newUserId) {
            }
        };


        boolean automaticAvailable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);

        mPower = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));

        if (automaticAvailable) {
            int automatic;
            try {
                automatic = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        mUserTracker.getCurrentUserId());
            } catch (SettingNotFoundException snfe) {
                automatic = 0;
            }
            mAutomatic = automatic != 0;
            mControl.setChecked(automatic != 0);
        } else {
            mControl.setChecked(false);
            // control.hideToggle();
        }

        int value;
        try {
            value = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    mUserTracker.getCurrentUserId());
        } catch (SettingNotFoundException ex) {
            value = MAXIMUM_BACKLIGHT;
        }

        mControl.setMax(MAXIMUM_BACKLIGHT);
        mControl.setValue(value);

        mControl.setOnChangedListener(this);

        SettingsObserver so = new SettingsObserver(new Handler());
        so.observe();
    }

    public View getView() {
        return mView;
    }

    public void onInit(ToggleSlider v) {

    }

    public void onChanged(ToggleSlider view, boolean tracking, boolean automatic, int value) {
        if(mSystemChange)
            return;

        boolean skip = false;
        if(mAutomatic != automatic)
            skip = true;
        mAutomatic = automatic;

        setMode(automatic ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        if (!automatic) {
            final int val = value;
            setBrightness(val);
            if (!tracking && !skip) {
                AsyncTask.execute(new Runnable() {
                    public void run() {
                        Settings.System.putIntForUser(mContext.getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS, val,
                                    mUserTracker.getCurrentUserId());
                    }
                });
            }
        }
    }

    private void setMode(int mode) {
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, mode,
                mUserTracker.getCurrentUserId());
    }

    private void setBrightness(int brightness) {
        try {
            mPower.setTemporaryScreenBrightnessSettingOverride(brightness);
        } catch (RemoteException ex) {
        }
    }

    private void updateValues() {

        int automatic;
        mSystemChange = true;
        try {
            automatic = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    mUserTracker.getCurrentUserId());
            mAutomatic = automatic != 0;
            mControl.setChecked(automatic != 0);

            mControl.setValue(Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    mUserTracker.getCurrentUserId()));

        } catch (SettingNotFoundException e) {
        }
        mSystemChange = false;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                            false, this, mUserTracker.getCurrentUserId());
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
                            false, this, mUserTracker.getCurrentUserId());
        }

        @Override
        public void onChange(boolean selfChange) {
            updateValues();
        }
    }
}