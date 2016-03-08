/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package com.android.server.display;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManagerInternal;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.MathUtils;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.accessibility.DisplayAdjustmentUtils;
import com.android.server.pm.UserContentObserver;
import com.android.server.twilight.TwilightListener;
import com.android.server.twilight.TwilightManager;
import com.android.server.twilight.TwilightState;

import cyanogenmod.hardware.CMHardwareManager;
import cyanogenmod.providers.CMSettings;
import cyanogenmod.util.ColorUtils;

import java.io.PrintWriter;

public class LiveDisplayController {

    private static final String TAG = "LiveDisplay";

    private static final long TWILIGHT_ADJUSTMENT_TIME = DateUtils.HOUR_IN_MILLIS * 1;

    private static final int OFF_TEMPERATURE = 6500;

    public static final int MODE_OFF = 0;
    public static final int MODE_NIGHT = 1;
    public static final int MODE_AUTO = 2;
    public static final int MODE_OUTDOOR = 3;
    public static final int MODE_DAY = 4;

    private int mColorTemperature = OFF_TEMPERATURE;
    private float mCurrentLux = 0.0f;

    private int mHintCounter;
    private int mMode;

    private boolean mOutdoorMode;
    private boolean mColorEnhancement;
    private boolean mLowPower;

    private final Context mContext;
    private final Handler mHandler;

    private CMHardwareManager mHardware;

    private int mDayTemperature;
    private int mNightTemperature;

    private boolean mUseOutdoorMode;
    private boolean mUseColorEnhancement;
    private boolean mUseLowPower;

    private boolean mOutdoorModeIsSelfManaged;

    private final float[] mColorAdjustment = new float[] { 1.0f, 1.0f, 1.0f };
    private final float[] mRGB = new float[] { 0.0f, 0.0f, 0.0f };

    private TwilightManager mTwilightManager;
    private boolean mSunset = false;

    private SettingsObserver mObserver;

    private ValueAnimator mAnimator;

    private int mDefaultDayTemperature;
    private int mDefaultNightTemperature;
    private int mDefaultOutdoorLux;

    private boolean mInitialized = false;

    private static final int MSG_UPDATE_LIVE_DISPLAY = 1;

    // Display postprocessing can have power impact. Disable it if powersave mode is on.
    private boolean mLowPerformance = false;
    private PowerManagerInternal.LowPowerModeListener mLowPowerModeListener =
            new PowerManagerInternal.LowPowerModeListener() {
        @Override
        public void onLowPowerModeChanged(boolean enabled) {
            mLowPerformance = enabled;
            updateLiveDisplay(mCurrentLux);
         }
    };

    LiveDisplayController(Context context, Looper looper) {
        mContext = context;
        mHandler = new LiveDisplayHandler(looper);
    }

    void systemReady() {
        mHardware = CMHardwareManager.getInstance(mContext);

        mDefaultDayTemperature = mContext.getResources().getInteger(
                org.cyanogenmod.platform.internal.R.integer.config_dayColorTemperature);
        mDefaultNightTemperature = mContext.getResources().getInteger(
                org.cyanogenmod.platform.internal.R.integer.config_nightColorTemperature);
        mDefaultOutdoorLux = mContext.getResources().getInteger(
                org.cyanogenmod.platform.internal.R.integer.config_outdoorAmbientLux);

        mUseOutdoorMode =
                mHardware.isSupported(CMHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT);
        mOutdoorModeIsSelfManaged = mUseOutdoorMode ?
                mHardware.isSunlightEnhancementSelfManaged() : false;

        mUseLowPower =
                mHardware.isSupported(CMHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT);
        if (mUseLowPower) {
            mLowPower = mHardware.get(CMHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT);
        }

        mUseColorEnhancement =
                mHardware.isSupported(CMHardwareManager.FEATURE_COLOR_ENHANCEMENT);
        if (mUseColorEnhancement) {
            mColorEnhancement =
                mHardware.get(CMHardwareManager.FEATURE_COLOR_ENHANCEMENT);
        }

        updateSettings();

        mObserver = new SettingsObserver();
        mObserver.register(true);

        PowerManagerInternal pmi = LocalServices.getService(PowerManagerInternal.class);
        pmi.registerLowPowerModeObserver(mLowPowerModeListener);
        mLowPerformance = pmi.getLowPowerModeEnabled();

        mTwilightManager = LocalServices.getService(TwilightManager.class);
        mTwilightManager.registerListener(mTwilightListener, mHandler);

        mInitialized = true;
    }

    private void updateSettings() {
        mDayTemperature = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_TEMPERATURE_DAY,
                mDefaultDayTemperature,
                UserHandle.USER_CURRENT);
        mNightTemperature = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_TEMPERATURE_NIGHT,
                mDefaultNightTemperature,
                UserHandle.USER_CURRENT);
        mMode = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_TEMPERATURE_MODE,
                MODE_OFF,
                UserHandle.USER_CURRENT);
        // Counter used to determine when we should tell the user about this feature.
        // If it's not used after 3 sunsets, we'll show the hint once.
        mHintCounter = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.LIVE_DISPLAY_HINTED,
                -3,
                UserHandle.USER_CURRENT);

        // Clear the hint forever
        if (mMode != MODE_OFF) {
            saveUserHint(1);
        }

        // Manual color adjustment will be set as a space separated string of float values
        String colorAdjustmentTemp = CMSettings.System.getStringForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_COLOR_ADJUSTMENT,
                UserHandle.USER_CURRENT);
        String[] colorAdjustment = colorAdjustmentTemp == null ?
                null : colorAdjustmentTemp.split(" ");
        if (colorAdjustment == null || colorAdjustment.length != 3) {
            colorAdjustment = new String[] { "1.0", "1.0", "1.0" };
        }
        try {
            mColorAdjustment[0] = Float.parseFloat(colorAdjustment[0]);
            mColorAdjustment[1] = Float.parseFloat(colorAdjustment[1]);
            mColorAdjustment[2] = Float.parseFloat(colorAdjustment[2]);
        } catch (NumberFormatException e) {
            Slog.e(TAG, e.getMessage(), e);
            mColorAdjustment[0] = 1.0f;
            mColorAdjustment[1] = 1.0f;
            mColorAdjustment[2] = 1.0f;
        }

        updateLiveDisplay(mCurrentLux);
    }

    private final class SettingsObserver extends UserContentObserver {
        private final Uri DISPLAY_TEMPERATURE_DAY_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_TEMPERATURE_DAY);
        private final Uri DISPLAY_TEMPERATURE_NIGHT_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_TEMPERATURE_NIGHT);
        private final Uri DISPLAY_TEMPERATURE_MODE_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_TEMPERATURE_MODE);
        private final Uri DISPLAY_AUTO_OUTDOOR_MODE_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_AUTO_OUTDOOR_MODE);
        private final Uri DISPLAY_LOW_POWER_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_LOW_POWER);
        private final Uri DISPLAY_COLOR_ENHANCE_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_COLOR_ENHANCE);
        private final Uri DISPLAY_COLOR_ADJUSTMENT_URI =
                CMSettings.System.getUriFor(CMSettings.System.DISPLAY_COLOR_ADJUSTMENT);
        public SettingsObserver() {
            super(mHandler);
        }

        public void register(boolean register) {
            final ContentResolver cr = mContext.getContentResolver();
            if (register) {
                cr.registerContentObserver(DISPLAY_TEMPERATURE_DAY_URI, false, this, UserHandle.USER_ALL);
                cr.registerContentObserver(DISPLAY_TEMPERATURE_NIGHT_URI, false, this, UserHandle.USER_ALL);
                cr.registerContentObserver(DISPLAY_TEMPERATURE_MODE_URI, false, this, UserHandle.USER_ALL);
                cr.registerContentObserver(DISPLAY_AUTO_OUTDOOR_MODE_URI, false, this, UserHandle.USER_ALL);
                cr.registerContentObserver(DISPLAY_LOW_POWER_URI, false, this, UserHandle.USER_ALL);
                cr.registerContentObserver(DISPLAY_COLOR_ENHANCE_URI, false, this, UserHandle.USER_ALL);
                cr.registerContentObserver(DISPLAY_COLOR_ADJUSTMENT_URI, false, this, UserHandle.USER_ALL);
                observe();
            } else {
                cr.unregisterContentObserver(this);
                unobserve();
            }
        }

        @Override
        protected void update() {
            updateSettings();
        }
    }

    public void updateLiveDisplay() {
        updateLiveDisplay(mCurrentLux);
    }

    synchronized void updateLiveDisplay(float lux) {
        mCurrentLux = lux;
        mHandler.removeMessages(MSG_UPDATE_LIVE_DISPLAY);
        mHandler.sendEmptyMessage(MSG_UPDATE_LIVE_DISPLAY);
    }

    private synchronized void updateColorTemperature(TwilightState twilight) {
        int temperature = mDayTemperature;
        if (mMode == MODE_OFF || mLowPerformance) {
            temperature = OFF_TEMPERATURE;
        } else if (mMode == MODE_NIGHT) {
            temperature = mNightTemperature;
        } else if (mMode == MODE_AUTO) {
            temperature = getTwilightK(twilight);
        }

        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ValueAnimator.ofInt(mColorTemperature, temperature);
        mAnimator.setDuration(Math.abs(mColorTemperature - temperature) / 2);
        mAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setDisplayTemperature((Integer)animation.getAnimatedValue());
            }
        });
        mAnimator.start();
    }

    private synchronized void setDisplayTemperature(int temperature) {
        mColorTemperature = temperature;

        final float[] rgb = ColorUtils.temperatureToRGB(temperature);

        if (!mLowPerformance) {
            rgb[0] *= mColorAdjustment[0];
            rgb[1] *= mColorAdjustment[1];
            rgb[2] *= mColorAdjustment[2];
        }

        if (rgb[0] == mRGB[0] && rgb[1] == mRGB[1] && rgb[2] == mRGB[2]) {
            // no changes
            return;
        }

        System.arraycopy(rgb, 0, mRGB, 0, 3);

        Slog.d(TAG, "Adjust display temperature to " + temperature +
                "K [r=" + rgb[0] + " g=" + rgb[1] + " b=" + rgb[2] + "]");

        if (mHardware.isSupported(CMHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            // Clear this out in case of an upgrade
            CMSettings.Secure.putStringForUser(mContext.getContentResolver(),
                    CMSettings.Secure.LIVE_DISPLAY_COLOR_MATRIX,
                    null,
                    UserHandle.USER_CURRENT);

            int max = mHardware.getDisplayColorCalibrationMax();
            mHardware.setDisplayColorCalibration(new int[] {
                (int) Math.ceil(rgb[0] * max),
                (int) Math.ceil(rgb[1] * max),
                (int) Math.ceil(rgb[2] * max)
            });
            screenRefresh();
        } else {
            String colorMatrixStr = null;
            if (rgb[0] != 1.0f || rgb[1] != 1.0f || rgb[2] != 1.0f) {
                 final Float[] colorMatrix = new Float[] {
                        rgb[0], 0.0f, 0.0f, 0.0f,
                        0.0f, rgb[1], 0.0f, 0.0f,
                        0.0f, 0.0f, rgb[2], 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f };
                 colorMatrixStr = TextUtils.join(" ", colorMatrix);
            }

            // For GPU color transform, go thru DisplayAdjustmentUtils in
            // order to coexist with accessibility settings
            CMSettings.Secure.putStringForUser(mContext.getContentResolver(),
                    CMSettings.Secure.LIVE_DISPLAY_COLOR_MATRIX,
                    colorMatrixStr,
                    UserHandle.USER_CURRENT);

           DisplayAdjustmentUtils.applyAdjustments(mContext, UserHandle.USER_CURRENT);
        }
    }

    /**
     * Outdoor mode is optionally enabled when ambient lux > 10000 and it's daytime
     * Melt faces!
     *
     * TODO: Use the camera or RGB sensor to determine if it's really sunlight
     */
    private synchronized void updateOutdoorMode(TwilightState twilight) {
        if (!mUseOutdoorMode) {
            return;
        }

        boolean value = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_AUTO_OUTDOOR_MODE,
                1,
                UserHandle.USER_CURRENT) == 1;

        boolean enabled;
        if (mOutdoorModeIsSelfManaged) {
            enabled = value;
        } else {
            enabled = !mLowPerformance &&
                ((mMode == MODE_OUTDOOR) ||
                 (value && mMode == MODE_AUTO &&
                  twilight != null && !twilight.isNight() &&
                  mCurrentLux > mDefaultOutdoorLux));
        }

        if (enabled == mOutdoorMode) {
            return;
        }

        mHardware.set(CMHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT, enabled);
        mOutdoorMode = enabled;
    }

    /**
     * Color enhancement is optional, but can look bad with night mode
     */
    private synchronized void updateColorEnhancement(TwilightState twilight) {
        if (!mUseColorEnhancement) {
            return;
        }

        boolean value = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_COLOR_ENHANCE,
                1,
                UserHandle.USER_CURRENT) == 1;

        boolean enabled = !mLowPerformance && value &&
                !(mMode == MODE_NIGHT ||
                 (mMode == MODE_AUTO && twilight != null && twilight.isNight()));

        if (enabled == mColorEnhancement) {
            return;
        }

        mHardware.set(CMHardwareManager.FEATURE_COLOR_ENHANCEMENT, enabled);
        mColorEnhancement = enabled;
    }

    /**
     * Adaptive backlight / low power mode. Turn it off when under very bright light.
     */
    private synchronized void updateLowPowerMode() {
        if (!mUseLowPower) {
            return;
        }

        boolean value = CMSettings.System.getIntForUser(mContext.getContentResolver(),
                CMSettings.System.DISPLAY_LOW_POWER,
                1,
                UserHandle.USER_CURRENT) == 1;

        boolean enabled = value && (mCurrentLux < mDefaultOutdoorLux);

        if (enabled == mLowPower) {
            return;
        }

        mHardware.set(CMHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT, enabled);
        mLowPower = enabled;
    }

    /**
     * Where is the sun anyway? This calculation determines day or night, and scales
     * the value around sunset/sunrise for a smooth transition.
     *
     * @param now
     * @param sunset
     * @param sunrise
     * @return float between 0 and 1
     */
    private static float adj(long now, long sunset, long sunrise) {
        if (sunset < 0 || sunrise < 0
                || now < sunset || now > sunrise) {
            return 1.0f;
        }

        if (now < sunset + TWILIGHT_ADJUSTMENT_TIME) {
            return MathUtils.lerp(1.0f, 0.0f,
                    (float)(now - sunset) / TWILIGHT_ADJUSTMENT_TIME);
        }

        if (now > sunrise - TWILIGHT_ADJUSTMENT_TIME) {
            return MathUtils.lerp(1.0f, 0.0f,
                    (float)(sunrise - now) / TWILIGHT_ADJUSTMENT_TIME);
        }

        return 0.0f;
    }

    /**
     * Determine the color temperature we should use for the display based on
     * the position of the sun.
     *
     * @param state
     * @return color temperature in Kelvin
     */
    private int getTwilightK(TwilightState state) {
        float adjustment = 1.0f;

        if (state != null) {
            final long now = System.currentTimeMillis();
            adjustment = adj(now, state.getYesterdaySunset(), state.getTodaySunrise()) *
                         adj(now, state.getTodaySunset(), state.getTomorrowSunrise());
        }

        return (int)MathUtils.lerp(mNightTemperature, mDayTemperature, adjustment);
    }

    /**
     * Tell SurfaceFlinger to repaint the screen. This is called after updating
     * hardware registers for display calibration to have an immediate effect.
     */
    private static void screenRefresh() {
        try {
            final IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                final Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                flinger.transact(1004, data, null, 0);
                data.recycle();
            }
        } catch (RemoteException ex) {
            Slog.e(TAG, "Failed to refresh screen", ex);
        }
    }

    private void saveUserHint(int value) {
        if (mHintCounter == value) {
            return;
        }
        CMSettings.System.putIntForUser(mContext.getContentResolver(),
                CMSettings.System.LIVE_DISPLAY_HINTED,
                value,
                UserHandle.USER_CURRENT);
        mHintCounter = value;
    }

    /**
     * Show a friendly notification to the user about the potential benefits of decreasing
     * blue light at night. Do this only once if the feature has not been used after
     * three sunsets. It would be great to enable this by default, but we don't want
     * the change of screen color to be considered a "bug" by a user who doesn't
     * understand what's happening.
     *
     * @param state
     */
    private void updateUserHint(TwilightState state) {
        // check if we should send the hint only once after sunset
        if (state == null || mHintCounter == 1) {
            return;
        }
        boolean transition = state.isNight() && !mSunset;
        mSunset = state.isNight();
        if (!transition) {
            return;
        }

        if (mHintCounter <= 0) {
            mHintCounter++;
            saveUserHint(mHintCounter);
        }
        if (mHintCounter == 0) {
            //show the notification and don't come back here
            final Intent intent = new Intent("android.settings.LIVEDISPLAY_SETTINGS");
            PendingIntent result = PendingIntent.getActivity(
                    mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder builder = new Notification.Builder(mContext)
                    .setContentTitle(mContext.getResources().getString(
                            org.cyanogenmod.platform.internal.R.string.live_display_title))
                    .setContentText(mContext.getResources().getString(
                            org.cyanogenmod.platform.internal.R.string.live_display_hint))
                    .setSmallIcon(org.cyanogenmod.platform.internal.R.drawable.ic_livedisplay_notif)
                    .setStyle(new Notification.BigTextStyle().bigText(mContext.getResources()
                             .getString(
                                     org.cyanogenmod.platform.internal.R.string.live_display_hint)))
                    .setContentIntent(result)
                    .setAutoCancel(true);

            NotificationManager nm =
                    (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notifyAsUser(null, 1, builder.build(), UserHandle.CURRENT);

            saveUserHint(1);
        }
    }

    private final TwilightListener mTwilightListener = new TwilightListener() {
        @Override
        public void onTwilightStateChanged() {
            updateLiveDisplay(mCurrentLux);
        }
    };

    private final class LiveDisplayHandler extends Handler {
        public LiveDisplayHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_LIVE_DISPLAY:
                    if (!mInitialized) {
                        break;
                    }
                    TwilightState twilight = mTwilightManager.getCurrentState();

                    updateColorTemperature(twilight);
                    updateOutdoorMode(twilight);
                    updateColorEnhancement(twilight);
                    updateLowPowerMode();
                    updateUserHint(twilight);

                    boolean transition = mMode == MODE_AUTO &&
                            mColorTemperature != mDayTemperature &&
                            mColorTemperature != mNightTemperature;
                    if (transition) {
                        // fire again in a minute
                        sendEmptyMessageDelayed(MSG_UPDATE_LIVE_DISPLAY,
                                DateUtils.MINUTE_IN_MILLIS);
                    }
                    break;
            }
        }
    }

    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("LiveDisplay Controller Configuration:");
        pw.println("  mDayTemperature=" + mDayTemperature);
        pw.println("  mNightTemperature=" + mNightTemperature);
        pw.println();
        pw.println("LiveDisplay Controller State:");
        pw.println("  mMode=" + (mLowPerformance ? "disabled in powersave mode" : mMode));
        pw.println("  mSunset=" + mSunset);
        pw.println("  mColorTemperature=" + mColorTemperature);
        pw.println("  mColorAdjustment=[r: " + mColorAdjustment[0] + " g:" + mColorAdjustment[1] +
                " b:" + mColorAdjustment[2] + "]");
        pw.println("  mRGB=[r:" + mRGB[0] + " g:" + mRGB[1] + " b:" + mRGB[2] + "]");
        pw.println("  mOutdoorMode=" + (mUseOutdoorMode ? mOutdoorMode : "N/A"));
        pw.println("  mColorEnhancement=" + (mUseColorEnhancement ? mColorEnhancement : "N/A"));
        pw.println("  mLowPower=" + (mUseLowPower ? mLowPower : "N/A"));
    }
}
