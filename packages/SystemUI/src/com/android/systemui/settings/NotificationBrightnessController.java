/*
 * Copyright (C) 2012-2015 The CyanogenMod Project
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

package com.android.systemui.settings;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;

import com.android.systemui.R;

import java.lang.Exception;
import java.util.ArrayList;

import cyanogenmod.providers.CMSettings;

public class NotificationBrightnessController implements ToggleSlider.Listener {
    private static final String TAG = "StatusBar.NotificationBrightnessController";

    public static final int LIGHT_BRIGHTNESS_MINIMUM = 1;
    public static final int LIGHT_BRIGHTNESS_MAXIMUM = 255;

    // Minimum delay between LED notification updates
    private final static long LED_UPDATE_DELAY_MS = 100;

    private int mCurrentBrightness;
    private final int mMinimumBrightness;
    private final int mMaximumBrightness;

    private final Context mContext;
    private final ToggleSlider mControl;

    private ArrayList<BrightnessStateChangeCallback> mChangeCallbacks =
            new ArrayList<BrightnessStateChangeCallback>();

    private boolean mListening;

    private boolean mNotificationAllow;
    private final Bundle mNotificationBundle;
    private final Notification.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    public interface BrightnessStateChangeCallback {
        public void onBrightnessLevelChanged();
    }

    public NotificationBrightnessController(Context context, ToggleSlider control) {
        mContext = context;
        mControl = control;

        mMinimumBrightness = LIGHT_BRIGHTNESS_MINIMUM;
        mMaximumBrightness = LIGHT_BRIGHTNESS_MAXIMUM;
        mCurrentBrightness = LIGHT_BRIGHTNESS_MAXIMUM;

        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationBundle = new Bundle();
        mNotificationBuilder = new Notification.Builder(mContext);

        mNotificationBundle.putBoolean(Notification.EXTRA_FORCE_SHOW_LIGHTS, true);
        mNotificationBuilder.setExtras(mNotificationBundle)
                .setContentTitle(mContext.getString(R.string.led_notification_title))
                .setContentText(mContext.getString(R.string.led_notification_text))
                .setSmallIcon(R.drawable.ic_settings)
                .setOngoing(true);
    }

    private Handler mLedHandler = new Handler() {
        public void handleMessage(Message msg) {
            updateNotification();
        }
    };

    public void addStateChangedCallback(BrightnessStateChangeCallback cb) {
        mChangeCallbacks.add(cb);
    }

    public boolean removeStateChangedCallback(BrightnessStateChangeCallback cb) {
        return mChangeCallbacks.remove(cb);
    }

    @Override
    public void onInit(ToggleSlider control) {
        // Do nothing
    }

    public void registerCallbacks() {
        if (mListening) {
            return;
        }

        // Read the brightness and set the maximum value for preview
        mCurrentBrightness = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                mMaximumBrightness, UserHandle.USER_CURRENT);
        CMSettings.System.putIntForUser(mContext.getContentResolver(),
                CMSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                mMaximumBrightness, UserHandle.USER_CURRENT);

        // Update the slider and mode before attaching the listener so we don't
        // receive the onChanged notifications for the initial values.
        mNotificationAllow = true;
        updateSlider();

        mControl.setOnChangedListener(this);
        mListening = true;
    }

    /** Unregister all call backs, both to and from the controller */
    public void unregisterCallbacks() {
        if (!mListening) {
            return;
        }

        mNotificationAllow = false;
        mControl.setOnChangedListener(null);
        mNotificationManager.cancel(1);
        mListening = false;

        CMSettings.System.putIntForUser(mContext.getContentResolver(),
                CMSettings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                mCurrentBrightness, UserHandle.USER_CURRENT);
    }

    @Override
    public void onChanged(ToggleSlider view, boolean tracking, boolean automatic, int value,
            boolean stopTracking) {
        mCurrentBrightness = value + mMinimumBrightness;
        updateNotification();

        for (BrightnessStateChangeCallback cb : mChangeCallbacks) {
            cb.onBrightnessLevelChanged();
        }
    }

    /** Fetch the brightness from the system settings and update the slider */
    private void updateSlider() {
        mControl.setMax(mMaximumBrightness - mMinimumBrightness);
        mControl.setValue(mCurrentBrightness - mMinimumBrightness);
        updateNotification();
    }

    /** Fetch the brightness from the system settings and update the slider */
    private void updateNotification() {
        // Dampen rate of consecutive LED changes
        if (mLedHandler.hasMessages(0)) {
            return;
        }

        if (mNotificationAllow) {
            mLedHandler.sendEmptyMessageDelayed(0, LED_UPDATE_DELAY_MS);

            // Instead of canceling the notification, force it to update with the color.
            // Use a white light for a better preview of the brightness.
            int notificationColor = 0x00FFFFFF | (mCurrentBrightness << 24);
            mNotificationBuilder.setLights(notificationColor, 1, 0);
            mNotificationManager.notify(1, mNotificationBuilder.build());
        }
    }
}
