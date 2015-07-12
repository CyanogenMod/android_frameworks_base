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
import android.hardware.CmHardwareManager;
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
import com.android.server.twilight.TwilightListener;
import com.android.server.twilight.TwilightManager;
import com.android.server.twilight.TwilightState;

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
    private final CmHardwareManager mCmHardwareManager;

    private int mDayTemperature;
    private int mNightTemperature;

    private boolean mUseOutdoorMode;
    private boolean mUseColorEnhancement;
    private boolean mUseLowPower;

    private final float[] mColorAdjustment = new float[] { 1.0f, 1.0f, 1.0f };
    private final float[] mRGB = new float[] { 0.0f, 0.0f, 0.0f };

    private final TwilightManager mTwilightManager;
    private boolean mSunset = false;

    private final SettingsObserver mObserver = new SettingsObserver();

    private ValueAnimator mAnimator;

    private final int mDefaultDayTemperature;
    private final int mDefaultNightTemperature;
    private final int mDefaultOutdoorLux;

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
        mCmHardwareManager = (CmHardwareManager) context.getSystemService(Context.CMHW_SERVICE);

        mTwilightManager = LocalServices.getService(TwilightManager.class);
        mTwilightManager.registerListener(mTwilightListener, mHandler);


        if (mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT)) {
            mOutdoorMode = mCmHardwareManager.get(CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT);
        }

        if (mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_COLOR_ENHANCEMENT)) {
            mColorEnhancement =
                    mCmHardwareManager.get(CmHardwareManager.FEATURE_COLOR_ENHANCEMENT);
        }

        if (mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT)) {
            mLowPower = mCmHardwareManager.get(CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT);
        }

        mDefaultDayTemperature = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_dayColorTemperature);
        mDefaultNightTemperature = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_nightColorTemperature);
        mDefaultOutdoorLux = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_outdoorAmbientLux);

        // Counter used to determine when we should tell the user about this feature.
        // If it's not used after 3 sunsets, we'll show the hint once.
        mHintCounter = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.LIVE_DISPLAY_HINTED,
                -3,
                UserHandle.USER_CURRENT);

        updateSettings();
        mObserver.register(true);

        PowerManagerInternal pmi = LocalServices.getService(PowerManagerInternal.class);
        pmi.registerLowPowerModeObserver(mLowPowerModeListener);
        mLowPerformance = pmi.getLowPowerModeEnabled();
    }

    private void updateSettings() {
        mDayTemperature = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_DAY,
                mDefaultDayTemperature,
                UserHandle.USER_CURRENT);
        mNightTemperature = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_NIGHT,
                mDefaultNightTemperature,
                UserHandle.USER_CURRENT);
        mMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_MODE,
                MODE_OFF,
                UserHandle.USER_CURRENT);
        if (!mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT)) {
            mUseOutdoorMode = false;
        } else {
            mUseOutdoorMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.DISPLAY_AUTO_OUTDOOR_MODE,
                    1,
                    UserHandle.USER_CURRENT) == 1;
        }
        if (!mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT)) {
            mUseLowPower = false;
        } else {
            mUseLowPower = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.DISPLAY_LOW_POWER,
                    1,
                    UserHandle.USER_CURRENT) == 1;
        }
        if (!mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_COLOR_ENHANCEMENT)) {
            mColorEnhancement = false;
        } else {
            mColorEnhancement = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.DISPLAY_COLOR_ENHANCE,
                    1,
                    UserHandle.USER_CURRENT) == 1;
        }

        // Clear the hint forever
        if (mMode != MODE_OFF) {
            saveUserHint(1);
        }

        // Manual color adjustment will be set as a space separated string of float values
        String colorAdjustmentTemp = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_COLOR_ADJUSTMENT,
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

    private final class SettingsObserver extends ContentObserver {
        private final Uri DISPLAY_TEMPERATURE_DAY_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_TEMPERATURE_DAY);
        private final Uri DISPLAY_TEMPERATURE_NIGHT_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_TEMPERATURE_NIGHT);
        private final Uri DISPLAY_TEMPERATURE_MODE_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_TEMPERATURE_MODE);
        private final Uri DISPLAY_AUTO_OUTDOOR_MODE_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_AUTO_OUTDOOR_MODE);
        private final Uri DISPLAY_LOW_POWER_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_LOW_POWER);
        private final Uri DISPLAY_COLOR_ENHANCE_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_COLOR_ENHANCE);
        private final Uri DISPLAY_COLOR_ADJUSTMENT_URI =
                Settings.System.getUriFor(Settings.System.DISPLAY_COLOR_ADJUSTMENT);
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
            } else {
                cr.unregisterContentObserver(this);
            }
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange,  uri);
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

        final float[] rgb = temperatureToRGB(temperature);

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

        if (mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_DISPLAY_COLOR_CALIBRATION)) {
            // Clear this out in case of an upgrade
            Settings.Secure.putStringForUser(mContext.getContentResolver(),
                    Settings.Secure.LIVE_DISPLAY_COLOR_MATRIX,
                    null,
                    UserHandle.USER_CURRENT);

            int max = mCmHardwareManager.getDisplayColorCalibrationMax();
            mCmHardwareManager.setDisplayColorCalibration(new int[] {
                (int) (rgb[0] * max), (int) (rgb[1] * max), (int) (rgb[2] * max)
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
            Settings.Secure.putStringForUser(mContext.getContentResolver(),
                    Settings.Secure.LIVE_DISPLAY_COLOR_MATRIX,
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
        boolean enabled = !mLowPerformance &&
                ((mMode == MODE_OUTDOOR) ||
                 (mUseOutdoorMode && mMode == MODE_AUTO &&
                  twilight != null && !twilight.isNight() &&
                  mCurrentLux > mDefaultOutdoorLux));

        if (enabled == mOutdoorMode) {
            return;
        }

        mCmHardwareManager.set(CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT, enabled);
        mOutdoorMode = enabled;
    }

    /**
     * Color enhancement is optional, but can look bad with night mode
     */
    private synchronized void updateColorEnhancement(TwilightState twilight) {
        boolean enabled = !mLowPerformance && (mUseColorEnhancement &&
                !(mMode == MODE_NIGHT ||
                 (mMode == MODE_AUTO && twilight != null && twilight.isNight())));

        if (enabled == mColorEnhancement) {
            return;
        }

        mCmHardwareManager.set(CmHardwareManager.FEATURE_COLOR_ENHANCEMENT, enabled);
        mColorEnhancement = enabled;
    }

    /**
     * Adaptive backlight / low power mode. Turn it off when under very bright light.
     */
    private synchronized void updateLowPowerMode() {
        boolean enabled = mUseLowPower && mCurrentLux < mDefaultOutdoorLux;

        if (enabled == mLowPower) {
            return;
        }

        mCmHardwareManager.set(CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT, enabled);
        mLowPower = enabled;
    }

    /**
     * Convert a color temperature value (in Kelvin) to a RGB units as floats.
     * This can be used in a transform matrix or hardware gamma control.
     *
     * @param tempK
     * @return
     */
    private static float[] temperatureToRGB(int degreesK) {
        int k = MathUtils.constrain(degreesK, 1000, 20000);
        float a = (k % 100) / 100.0f;
        int i = ((k - 1000)/ 100) * 3;

        return new float[] { interp(i, a), interp(i+1, a), interp(i+2, a) };
    }

    private static float interp(int i, float a) {
        return MathUtils.lerp((float)sColorTable[i], (float)sColorTable[i+3], a);
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
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.LIVE_DISPLAY_HINTED,
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
                            com.android.internal.R.string.live_display_title))
                    .setContentText(mContext.getResources().getString(
                            com.android.internal.R.string.live_display_hint))
                    .setSmallIcon(com.android.internal.R.drawable.ic_livedisplay_notif)
                    .setStyle(new Notification.BigTextStyle().bigText(mContext.getResources()
                             .getString(com.android.internal.R.string.live_display_hint)))
                    .setContentIntent(result);

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
        boolean hasSunlightEnhancement =
                mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_SUNLIGHT_ENHANCEMENT);
        boolean hasColorEnhancement =
                mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_COLOR_ENHANCEMENT);
        boolean hasAdaptiveBacklight =
                mCmHardwareManager.isSupported(CmHardwareManager.FEATURE_ADAPTIVE_BACKLIGHT);

        pw.println();
        pw.println("LiveDisplay Controller Configuration:");
        pw.println("  mDayTemperature=" + mDayTemperature);
        pw.println("  mNightTemperature=" + mNightTemperature);
        pw.println("  mUseOutdoorMode=" +
                (hasSunlightEnhancement ? mUseOutdoorMode : "not available"));
        pw.println("  mUseColorEnhancement=" +
                (hasColorEnhancement ? mUseColorEnhancement : "not available"));
        pw.println("  mUseLowPower=" +
                (hasAdaptiveBacklight ? mUseLowPower : "not available"));
        pw.println();
        pw.println("LiveDisplay Controller State:");
        pw.println("  mMode=" + (mLowPerformance ? "disabled in powersave mode" : mMode));
        pw.println("  mSunset=" + mSunset);
        pw.println("  mColorTemperature=" + mColorTemperature);
        pw.println("  mColorAdjustment=[r: " + mColorAdjustment[0] + " g:" + mColorAdjustment[1] +
                " b:" + mColorAdjustment[2] + "]");
        pw.println("  mRGB=[r:" + mRGB[0] + " g:" + mRGB[1] + " b:" + mRGB[2] + "]");
        if (hasSunlightEnhancement) {
            pw.println("  mOutdoorMode=" + mOutdoorMode);
        }
        if (hasColorEnhancement) {
            pw.println("  mColorEnhancement=" + mColorEnhancement);
        }
        if (hasAdaptiveBacklight) {
            pw.println("  mLowPower=" + mLowPower);
        }
    }

    /**
     * This table is a modified version of the original blackbody chart, found here:
     * http://www.vendian.org/mncharity/dir3/blackbody/UnstableURLs/bbr_color.html
     *
     * Created by Ingo Thiel.
     */
    private static final double[] sColorTable = new double[] {
            1.00000000, 0.18172716, 0.00000000,
            1.00000000, 0.25503671, 0.00000000,
            1.00000000, 0.30942099, 0.00000000,
            1.00000000, 0.35357379, 0.00000000,
            1.00000000, 0.39091524, 0.00000000,
            1.00000000, 0.42322816, 0.00000000,
            1.00000000, 0.45159884, 0.00000000,
            1.00000000, 0.47675916, 0.00000000,
            1.00000000, 0.49923747, 0.00000000,
            1.00000000, 0.51943421, 0.00000000,
            1.00000000, 0.54360078, 0.08679949,
            1.00000000, 0.56618736, 0.14065513,
            1.00000000, 0.58734976, 0.18362641,
            1.00000000, 0.60724493, 0.22137978,
            1.00000000, 0.62600248, 0.25591950,
            1.00000000, 0.64373109, 0.28819679,
            1.00000000, 0.66052319, 0.31873863,
            1.00000000, 0.67645822, 0.34786758,
            1.00000000, 0.69160518, 0.37579588,
            1.00000000, 0.70602449, 0.40267128,
            1.00000000, 0.71976951, 0.42860152,
            1.00000000, 0.73288760, 0.45366838,
            1.00000000, 0.74542112, 0.47793608,
            1.00000000, 0.75740814, 0.50145662,
            1.00000000, 0.76888303, 0.52427322,
            1.00000000, 0.77987699, 0.54642268,
            1.00000000, 0.79041843, 0.56793692,
            1.00000000, 0.80053332, 0.58884417,
            1.00000000, 0.81024551, 0.60916971,
            1.00000000, 0.81957693, 0.62893653,
            1.00000000, 0.82854786, 0.64816570,
            1.00000000, 0.83717703, 0.66687674,
            1.00000000, 0.84548188, 0.68508786,
            1.00000000, 0.85347859, 0.70281616,
            1.00000000, 0.86118227, 0.72007777,
            1.00000000, 0.86860704, 0.73688797,
            1.00000000, 0.87576611, 0.75326132,
            1.00000000, 0.88267187, 0.76921169,
            1.00000000, 0.88933596, 0.78475236,
            1.00000000, 0.89576933, 0.79989606,
            1.00000000, 0.90198230, 0.81465502,
            1.00000000, 0.90963069, 0.82838210,
            1.00000000, 0.91710889, 0.84190889,
            1.00000000, 0.92441842, 0.85523742,
            1.00000000, 0.93156127, 0.86836903,
            1.00000000, 0.93853986, 0.88130458,
            1.00000000, 0.94535695, 0.89404470,
            1.00000000, 0.95201559, 0.90658983,
            1.00000000, 0.95851906, 0.91894041,
            1.00000000, 0.96487079, 0.93109690,
            1.00000000, 0.97107439, 0.94305985,
            1.00000000, 0.97713351, 0.95482993,
            1.00000000, 0.98305189, 0.96640795,
            1.00000000, 0.98883326, 0.97779486,
            1.00000000, 0.99448139, 0.98899179,
            1.00000000, 1.00000000, 1.00000000,
            0.98947904, 0.99348723, 1.00000000,
            0.97940448, 0.98722715, 1.00000000,
            0.96975025, 0.98120637, 1.00000000,
            0.96049223, 0.97541240, 1.00000000,
            0.95160805, 0.96983355, 1.00000000,
            0.94303638, 0.96443333, 1.00000000,
            0.93480451, 0.95923080, 1.00000000,
            0.92689056, 0.95421394, 1.00000000,
            0.91927697, 0.94937330, 1.00000000,
            0.91194747, 0.94470005, 1.00000000,
            0.90488690, 0.94018594, 1.00000000,
            0.89808115, 0.93582323, 1.00000000,
            0.89151710, 0.93160469, 1.00000000,
            0.88518247, 0.92752354, 1.00000000,
            0.87906581, 0.92357340, 1.00000000,
            0.87315640, 0.91974827, 1.00000000,
            0.86744421, 0.91604254, 1.00000000,
            0.86191983, 0.91245088, 1.00000000,
            0.85657444, 0.90896831, 1.00000000,
            0.85139976, 0.90559011, 1.00000000,
            0.84638799, 0.90231183, 1.00000000,
            0.84153180, 0.89912926, 1.00000000,
            0.83682430, 0.89603843, 1.00000000,
            0.83225897, 0.89303558, 1.00000000,
            0.82782969, 0.89011714, 1.00000000,
            0.82353066, 0.88727974, 1.00000000,
            0.81935641, 0.88452017, 1.00000000,
            0.81530175, 0.88183541, 1.00000000,
            0.81136180, 0.87922257, 1.00000000,
            0.80753191, 0.87667891, 1.00000000,
            0.80380769, 0.87420182, 1.00000000,
            0.80018497, 0.87178882, 1.00000000,
            0.79665980, 0.86943756, 1.00000000,
            0.79322843, 0.86714579, 1.00000000,
            0.78988728, 0.86491137, 1.00000000,
            0.78663296, 0.86273225, 1.00000000,
            0.78346225, 0.86060650, 1.00000000,
            0.78037207, 0.85853224, 1.00000000,
            0.77735950, 0.85650771, 1.00000000,
            0.77442176, 0.85453121, 1.00000000,
            0.77155617, 0.85260112, 1.00000000,
            0.76876022, 0.85071588, 1.00000000,
            0.76603147, 0.84887402, 1.00000000,
            0.76336762, 0.84707411, 1.00000000,
            0.76076645, 0.84531479, 1.00000000,
            0.75822586, 0.84359476, 1.00000000,
            0.75574383, 0.84191277, 1.00000000,
            0.75331843, 0.84026762, 1.00000000,
            0.75094780, 0.83865816, 1.00000000,
            0.74863017, 0.83708329, 1.00000000,
            0.74636386, 0.83554194, 1.00000000,
            0.74414722, 0.83403311, 1.00000000,
            0.74197871, 0.83255582, 1.00000000,
            0.73985682, 0.83110912, 1.00000000,
            0.73778012, 0.82969211, 1.00000000,
            0.73574723, 0.82830393, 1.00000000,
            0.73375683, 0.82694373, 1.00000000,
            0.73180765, 0.82561071, 1.00000000,
            0.72989845, 0.82430410, 1.00000000,
            0.72802807, 0.82302316, 1.00000000,
            0.72619537, 0.82176715, 1.00000000,
            0.72439927, 0.82053539, 1.00000000,
            0.72263872, 0.81932722, 1.00000000,
            0.72091270, 0.81814197, 1.00000000,
            0.71922025, 0.81697905, 1.00000000,
            0.71756043, 0.81583783, 1.00000000,
            0.71593234, 0.81471775, 1.00000000,
            0.71433510, 0.81361825, 1.00000000,
            0.71276788, 0.81253878, 1.00000000,
            0.71122987, 0.81147883, 1.00000000,
            0.70972029, 0.81043789, 1.00000000,
            0.70823838, 0.80941546, 1.00000000,
            0.70678342, 0.80841109, 1.00000000,
            0.70535469, 0.80742432, 1.00000000,
            0.70395153, 0.80645469, 1.00000000,
            0.70257327, 0.80550180, 1.00000000,
            0.70121928, 0.80456522, 1.00000000,
            0.69988894, 0.80364455, 1.00000000,
            0.69858167, 0.80273941, 1.00000000,
            0.69729688, 0.80184943, 1.00000000,
            0.69603402, 0.80097423, 1.00000000,
            0.69479255, 0.80011347, 1.00000000,
            0.69357196, 0.79926681, 1.00000000,
            0.69237173, 0.79843391, 1.00000000,
            0.69119138, 0.79761446, 1.00000000,
            0.69003044, 0.79680814, 1.00000000,
            0.68888844, 0.79601466, 1.00000000,
            0.68776494, 0.79523371, 1.00000000,
            0.68665951, 0.79446502, 1.00000000,
            0.68557173, 0.79370830, 1.00000000,
            0.68450119, 0.79296330, 1.00000000,
            0.68344751, 0.79222975, 1.00000000,
            0.68241029, 0.79150740, 1.00000000,
            0.68138918, 0.79079600, 1.00000000,
            0.68038380, 0.79009531, 1.00000000,
            0.67939381, 0.78940511, 1.00000000,
            0.67841888, 0.78872517, 1.00000000,
            0.67745866, 0.78805526, 1.00000000,
            0.67651284, 0.78739518, 1.00000000,
            0.67558112, 0.78674472, 1.00000000,
            0.67466317, 0.78610368, 1.00000000,
            0.67375872, 0.78547186, 1.00000000,
            0.67286748, 0.78484907, 1.00000000,
            0.67198916, 0.78423512, 1.00000000,
            0.67112350, 0.78362984, 1.00000000,
            0.67027024, 0.78303305, 1.00000000,
            0.66942911, 0.78244457, 1.00000000,
            0.66859988, 0.78186425, 1.00000000,
            0.66778228, 0.78129191, 1.00000000,
            0.66697610, 0.78072740, 1.00000000,
            0.66618110, 0.78017057, 1.00000000,
            0.66539706, 0.77962127, 1.00000000,
            0.66462376, 0.77907934, 1.00000000,
            0.66386098, 0.77854465, 1.00000000,
            0.66310852, 0.77801705, 1.00000000,
            0.66236618, 0.77749642, 1.00000000,
            0.66163375, 0.77698261, 1.00000000,
            0.66091106, 0.77647551, 1.00000000,
            0.66019791, 0.77597498, 1.00000000,
            0.65949412, 0.77548090, 1.00000000,
            0.65879952, 0.77499315, 1.00000000,
            0.65811392, 0.77451161, 1.00000000,
            0.65743716, 0.77403618, 1.00000000,
            0.65676908, 0.77356673, 1.00000000,
            0.65610952, 0.77310316, 1.00000000,
            0.65545831, 0.77264537, 1.00000000,
            0.65481530, 0.77219324, 1.00000000,
            0.65418036, 0.77174669, 1.00000000,
            0.65355332, 0.77130560, 1.00000000,
            0.65293404, 0.77086988, 1.00000000,
            0.65232240, 0.77043944, 1.00000000,
            0.65171824, 0.77001419, 1.00000000,
            0.65112144, 0.76959404, 1.00000000,
            0.65053187, 0.76917889, 1.00000000,
            0.64994941, 0.76876866, 1.00000000,
            0.64937392, 0.76836326, 1.00000000
    };
}
