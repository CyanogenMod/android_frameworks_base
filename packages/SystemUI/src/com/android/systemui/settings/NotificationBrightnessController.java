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
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;

import java.lang.Exception;
import java.util.ArrayList;

public class NotificationBrightnessController implements ToggleSlider.Listener {
    private static final String TAG = "StatusBar.NotificationBrightnessController";

    public static final int LIGHT_BRIGHTNESS_MINIMUM = 1;
    public static final int LIGHT_BRIGHTNESS_MAXIMUM = 255;

    private int mCurrentBrightness;
    private final int mMinimumBrightness;
    private final int mMaximumBrightness;

    private final Context mContext;
    private final ToggleSlider mControl;
    private final CurrentUserTracker mUserTracker;
    private final Handler mHandler;
    private final NotificationBrightnessObserver mBrightnessObserver;

    private ArrayList<BrightnessStateChangeCallback> mChangeCallbacks =
            new ArrayList<BrightnessStateChangeCallback>();

    private boolean mListening;
    private boolean mExternalChange;

    private boolean mNotificationAllow;
    private final Bundle mNotificationBundle;
    private final Notification.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    public interface BrightnessStateChangeCallback {
        public void onBrightnessLevelChanged();
    }

    /** ContentObserver to watch brightness **/
    private class NotificationBrightnessObserver extends ContentObserver {

        private final Uri NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_URI =
                Settings.System.getUriFor(Settings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL);

        public NotificationBrightnessObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (selfChange) return;
            try {
                mExternalChange = true;
                updateSlider();
                for (BrightnessStateChangeCallback cb : mChangeCallbacks) {
                    cb.onBrightnessLevelChanged();
                }
            } finally {
                mExternalChange = false;
            }
        }

        public void startObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
            cr.registerContentObserver(
                    NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL_URI,
                    false, this, UserHandle.USER_ALL);
        }

        public void stopObserving() {
            final ContentResolver cr = mContext.getContentResolver();
            cr.unregisterContentObserver(this);
        }

    }

    public NotificationBrightnessController(Context context, ToggleSlider control) {
        mContext = context;
        mControl = control;
        mHandler = new Handler();
        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onUserSwitched(int newUserId) {
                updateSlider();
            }
        };
        mBrightnessObserver = new NotificationBrightnessObserver(mHandler);

        mMinimumBrightness = LIGHT_BRIGHTNESS_MINIMUM;
        mMaximumBrightness = LIGHT_BRIGHTNESS_MAXIMUM;
        mCurrentBrightness = LIGHT_BRIGHTNESS_MAXIMUM;

        mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationBundle = new Bundle();
        mNotificationBuilder = new Notification.Builder(mContext);

        mNotificationBundle.putBoolean(Notification.EXTRA_FORCE_SHOW_LIGHTS, true);
        mNotificationBuilder.setExtras(mNotificationBundle);
    }

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

        // Update the slider and mode before attaching the listener so we don't
        // receive the onChanged notifications for the initial values.
        mNotificationAllow = true;
        updateSlider();

        mBrightnessObserver.startObserving();
        mUserTracker.startTracking();

        mControl.setOnChangedListener(this);
        mListening = true;
    }

    /** Unregister all call backs, both to and from the controller */
    public void unregisterCallbacks() {
        if (!mListening) {
            return;
        }

        mNotificationAllow = false;
        mBrightnessObserver.stopObserving();
        mUserTracker.stopTracking();
        mControl.setOnChangedListener(null);
        mNotificationManager.cancel(1);
        mListening = false;

        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                mCurrentBrightness, UserHandle.USER_CURRENT);
    }

    @Override
    public void onChanged(ToggleSlider view, boolean tracking, boolean automatic, int value) {
        if (mExternalChange) return;

        mCurrentBrightness = value + mMinimumBrightness;
        updateNotification();

        for (BrightnessStateChangeCallback cb : mChangeCallbacks) {
            cb.onBrightnessLevelChanged();
        }
    }

    /** Fetch the brightness from the system settings and update the slider */
    private void updateSlider() {
        mCurrentBrightness = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                mMaximumBrightness, UserHandle.USER_CURRENT);

        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_LIGHT_BRIGHTNESS_LEVEL,
                mMaximumBrightness, UserHandle.USER_CURRENT);

        mControl.setMax(mMaximumBrightness - mMinimumBrightness);
        mControl.setValue(mCurrentBrightness - mMinimumBrightness);
        updateNotification();
    }

    /** Fetch the brightness from the system settings and update the slider */
    private void updateNotification() {
        if (mNotificationAllow) {
            // Instead of canceling the notification, force it to update with the color.
            int notificationColor = mCurrentBrightness +
                                    (mCurrentBrightness << 8) +
                                    (mCurrentBrightness << 16);
            mNotificationBuilder.setLights(notificationColor, 1, 0);
            mNotificationManager.notify(1, mNotificationBuilder.build());
        }
    }

}
