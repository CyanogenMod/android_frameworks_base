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

package com.android.server.power;

import com.android.server.LightsService;
import com.android.server.TwilightService;
import com.android.server.TwilightService.TwilightState;
import com.android.server.display.DisplayManagerService;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SystemSensorManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.FloatMath;
import android.util.Slog;
import android.util.Spline;
import android.util.TimeUtils;

import java.io.PrintWriter;

/**
 * Controls the power state of the display.
 *
 * Handles the proximity sensor, light sensor, and animations between states
 * including the screen off animation.
 *
 * This component acts independently of the rest of the power manager service.
 * In particular, it does not share any state and it only communicates
 * via asynchronous callbacks to inform the power manager that something has
 * changed.
 *
 * Everything this class does internally is serialized on its handler although
 * it may be accessed by other threads from the outside.
 *
 * Note that the power manager service guarantees that it will hold a suspend
 * blocker as long as the display is not ready.  So most of the work done here
 * does not need to worry about holding a suspend blocker unless it happens
 * independently of the display ready signal.
 *
 * For debugging, you can make the electron beam and brightness animations run
 * slower by changing the "animator duration scale" option in Development Settings.
 */
final class DisplayPowerController {
    private static final String TAG = "DisplayPowerController";

    private static boolean DEBUG = false;
    private static final boolean DEBUG_PRETEND_PROXIMITY_SENSOR_ABSENT = false;
    private static final boolean DEBUG_PRETEND_LIGHT_SENSOR_ABSENT = false;

    // If true, uses the electron beam on animation.
    // We might want to turn this off if we cannot get a guarantee that the screen
    // actually turns on and starts showing new content after the call to set the
    // screen state returns.  Playing the animation can also be somewhat slow.
    private static final boolean USE_ELECTRON_BEAM_ON_ANIMATION = false;

    // If true, enables the use of the screen auto-brightness adjustment setting.
    private static final boolean USE_SCREEN_AUTO_BRIGHTNESS_ADJUSTMENT =
            PowerManager.useScreenAutoBrightnessAdjustmentFeature();

    // The maximum range of gamma adjustment possible using the screen
    // auto-brightness adjustment setting.
    private static final float SCREEN_AUTO_BRIGHTNESS_ADJUSTMENT_MAX_GAMMA = 3.0f;

    // The minimum reduction in brightness when dimmed.
    private static final int SCREEN_DIM_MINIMUM_REDUCTION = 10;

    // If true, enables the use of the current time as an auto-brightness adjustment.
    // The basic idea here is to expand the dynamic range of auto-brightness
    // when it is especially dark outside.  The light sensor tends to perform
    // poorly at low light levels so we compensate for it by making an
    // assumption about the environment.
    private static final boolean USE_TWILIGHT_ADJUSTMENT =
            PowerManager.useTwilightAdjustmentFeature();

    // Specifies the maximum magnitude of the time of day adjustment.
    private static final float TWILIGHT_ADJUSTMENT_MAX_GAMMA = 1.5f;

    // The amount of time after or before sunrise over which to start adjusting
    // the gamma.  We want the change to happen gradually so that it is below the
    // threshold of perceptibility and so that the adjustment has maximum effect
    // well after dusk.
    private static final long TWILIGHT_ADJUSTMENT_TIME = DateUtils.HOUR_IN_MILLIS * 2;

    private static final int ELECTRON_BEAM_ON_ANIMATION_DURATION_MILLIS = 250;
    private static final int ELECTRON_BEAM_OFF_ANIMATION_DURATION_MILLIS = 400;

    private static final int MSG_UPDATE_POWER_STATE = 1;
    private static final int MSG_PROXIMITY_SENSOR_DEBOUNCED = 2;
    private static final int MSG_LIGHT_SENSOR_DEBOUNCED = 3;
    private static final int MSG_UPDATE_BACKLIGHT_SETTINGS = 4;

    private static final int PROXIMITY_UNKNOWN = -1;
    private static final int PROXIMITY_NEGATIVE = 0;
    private static final int PROXIMITY_POSITIVE = 1;

    // Proximity sensor debounce delay in milliseconds for positive or negative transitions.
    private static final int PROXIMITY_SENSOR_POSITIVE_DEBOUNCE_DELAY = 0;
    private static final int PROXIMITY_SENSOR_NEGATIVE_DEBOUNCE_DELAY = 500;

    // Trigger proximity if distance is less than 5 cm.
    private static final float TYPICAL_PROXIMITY_THRESHOLD = 5.0f;

    // Light sensor event rate in milliseconds.
    private static final int LIGHT_SENSOR_RATE_MILLIS = 1000;

    // A rate for generating synthetic light sensor events in the case where the light
    // sensor hasn't reported any new data in a while and we need it to update the
    // debounce filter.  We only synthesize light sensor measurements when needed.
    private static final int SYNTHETIC_LIGHT_SENSOR_RATE_MILLIS =
            LIGHT_SENSOR_RATE_MILLIS * 2;

    // Brightness animation ramp rate in brightness units per second.
    private static final int BRIGHTNESS_RAMP_RATE_FAST = 200;
    private static final int BRIGHTNESS_RAMP_RATE_SLOW = 40;

    // IIR filter time constants in milliseconds for computing two moving averages of
    // the light samples.  One is a long-term average and the other is a short-term average.
    // We can use these filters to assess trends in ambient brightness.
    // The short term average gives us a filtered but relatively low latency measurement.
    // The long term average informs us about the overall trend.
    private static final long SHORT_TERM_AVERAGE_LIGHT_TIME_CONSTANT = 1000;
    private static final long LONG_TERM_AVERAGE_LIGHT_TIME_CONSTANT = 5000;

    // Stability requirements in milliseconds for accepting a new brightness
    // level.  This is used for debouncing the light sensor.  Different constants
    // are used to debounce the light sensor when adapting to brighter or darker environments.
    // This parameter controls how quickly brightness changes occur in response to
    // an observed change in light level that exceeds the hysteresis threshold.
    private static final long BRIGHTENING_LIGHT_FAST_DEBOUNCE = 1000;
    private static final long BRIGHTENING_LIGHT_DEBOUNCE = 4000;
    private static final long DARKENING_LIGHT_DEBOUNCE = 8000;

    // Hysteresis constraints for brightening or darkening.
    // The recent lux must have changed by at least this fraction relative to the
    // current ambient lux before a change will be considered.
    private static final float BRIGHTENING_LIGHT_HYSTERESIS = 0.10f;
    private static final float DARKENING_LIGHT_HYSTERESIS = 0.20f;

    // Threshold (in lux) to select between normal and fast debounce time.
    // If the difference between short and long time average is larger than
    // this value, fast debounce is used.
    private static final float BRIGHTENING_FAST_THRESHOLD = 300f;

    private final Object mLock = new Object();

    // Notifier for sending asynchronous notifications.
    private final Notifier mNotifier;

    // The display blanker.
    private final DisplayBlanker mDisplayBlanker;

    // Our context
    private final Context mContext;

    // Our handler.
    private final DisplayControllerHandler mHandler;

    // Asynchronous callbacks into the power manager service.
    // Only invoked from the handler thread while no locks are held.
    private final Callbacks mCallbacks;
    private Handler mCallbackHandler;

    // The lights service.
    private final LightsService mLights;

    // The twilight service.
    private final TwilightService mTwilight;

    // The display manager.
    private final DisplayManagerService mDisplayManager;

    // The sensor manager.
    private final SensorManager mSensorManager;

    // The proximity sensor, or null if not available or needed.
    private Sensor mProximitySensor;

    // The light sensor, or null if not available or needed.
    private Sensor mLightSensor;

    // The dim screen brightness.
    private final int mScreenBrightnessDimConfig;

    // The minimum allowed brightness.
    private final int mScreenBrightnessRangeMinimum;

    // The maximum allowed brightness.
    private final int mScreenBrightnessRangeMaximum;

    // True if auto-brightness should be used.
    private boolean mUseSoftwareAutoBrightnessConfig;

    // True if twilight adjustment is enabled
    private boolean mTwilightAdjustmentEnabled;

    // The auto-brightness spline adjustment.
    // The brightness values have been scaled to a range of 0..1.
    private Spline mScreenAutoBrightnessSpline;

    // Amount of time to delay auto-brightness after screen on while waiting for
    // the light sensor to warm-up in milliseconds.
    // May be 0 if no warm-up is required.
    private int mLightSensorWarmUpTimeConfig;

    // True if we should fade the screen while turning it off, false if we should play
    // a stylish electron beam animation instead.
    private boolean mElectronBeamFadesConfig;

    // The pending power request.
    // Initially null until the first call to requestPowerState.
    // Guarded by mLock.
    private DisplayPowerRequest mPendingRequestLocked;

    // True if a request has been made to wait for the proximity sensor to go negative.
    // Guarded by mLock.
    private boolean mPendingWaitForNegativeProximityLocked;

    // True if the pending power request or wait for negative proximity flag
    // has been changed since the last update occurred.
    // Guarded by mLock.
    private boolean mPendingRequestChangedLocked;

    // Set to true when the important parts of the pending power request have been applied.
    // The important parts are mainly the screen state.  Brightness changes may occur
    // concurrently.
    // Guarded by mLock.
    private boolean mDisplayReadyLocked;

    // Set to true if a power state update is required.
    // Guarded by mLock.
    private boolean mPendingUpdatePowerStateLocked;

    /* The following state must only be accessed by the handler thread. */

    // The currently requested power state.
    // The power controller will progressively update its internal state to match
    // the requested power state.  Initially null until the first update.
    private DisplayPowerRequest mPowerRequest;

    // The current power state.
    // Must only be accessed on the handler thread.
    private DisplayPowerState mPowerState;

    // True if the device should wait for negative proximity sensor before
    // waking up the screen.  This is set to false as soon as a negative
    // proximity sensor measurement is observed or when the device is forced to
    // go to sleep by the user.  While true, the screen remains off.
    private boolean mWaitingForNegativeProximity;

    // The actual proximity sensor threshold value.
    private float mProximityThreshold;

    // Set to true if the proximity sensor listener has been registered
    // with the sensor manager.
    private boolean mProximitySensorEnabled;

    // The debounced proximity sensor state.
    private int mProximity = PROXIMITY_UNKNOWN;

    // The raw non-debounced proximity sensor state.
    private int mPendingProximity = PROXIMITY_UNKNOWN;
    private long mPendingProximityDebounceTime;

    // True if the screen was turned off because of the proximity sensor.
    // When the screen turns on again, we report user activity to the power manager.
    private boolean mScreenOffBecauseOfProximity;

    // True if the screen on is being blocked.
    private boolean mScreenOnWasBlocked;

    // The elapsed real time when the screen on was blocked.
    private long mScreenOnBlockStartRealTime;

    // Set to true if the light sensor is enabled.
    private boolean mLightSensorEnabled;

    // The time when the light sensor was enabled.
    private long mLightSensorEnableTime;

    // The currently accepted nominal ambient light level.
    private float mAmbientLux;

    // True if mAmbientLux holds a valid value.
    private boolean mAmbientLuxValid;

    // The most recent light sample.
    private float mLastObservedLux;

    // The time of the most light recent sample.
    private long mLastObservedLuxTime;

    // The number of light samples collected since the light sensor was enabled.
    private int mRecentLightSamples;

    // The long-term and short-term filtered light measurements.
    private float mRecentShortTermAverageLux;
    private float mRecentLongTermAverageLux;

    // The direction in which the average lux is moving relative to the current ambient lux.
    //    0 if not changing or within hysteresis threshold.
    //    1 if brightening beyond hysteresis threshold.
    //   -1 if darkening beyond hysteresis threshold.
    private int mDebounceLuxDirection;

    // The time when the average lux last changed direction.
    private long mDebounceLuxTime;

    // The screen brightness level that has been chosen by the auto-brightness
    // algorithm.  The actual brightness should ramp towards this value.
    // We preserve this value even when we stop using the light sensor so
    // that we can quickly revert to the previous auto-brightness level
    // while the light sensor warms up.
    // Use -1 if there is no current auto-brightness value available.
    private int mScreenAutoBrightness = -1;

    // The last screen auto-brightness gamma.  (For printing in dump() only.)
    private float mLastScreenAutoBrightnessGamma = 1.0f;

    // True if the screen auto-brightness value is actually being used to
    // set the display brightness.
    private boolean mUsingScreenAutoBrightness;

    // Animators.
    private ObjectAnimator mElectronBeamOnAnimator;
    private ObjectAnimator mElectronBeamOffAnimator;
    private RampAnimator<DisplayPowerState> mScreenBrightnessRampAnimator;

    // Twilight changed.  We might recalculate auto-brightness values.
    private boolean mTwilightChanged;
    private boolean mAutoBrightnessSettingsChanged;

    /**
     * Creates the display power controller.
     */
    public DisplayPowerController(Looper looper, Context context, Notifier notifier,
            LightsService lights, TwilightService twilight, SensorManager sensorManager,
            DisplayManagerService displayManager,
            DisplayBlanker displayBlanker,
            Callbacks callbacks, Handler callbackHandler) {
        mContext = context;
        mHandler = new DisplayControllerHandler(looper);
        mNotifier = notifier;
        mDisplayBlanker = displayBlanker;
        mCallbacks = callbacks;
        mCallbackHandler = callbackHandler;

        mLights = lights;
        mTwilight = twilight;
        mSensorManager = sensorManager;
        mDisplayManager = displayManager;

        final Resources resources = context.getResources();

        mScreenBrightnessDimConfig = clampAbsoluteBrightness(resources.getInteger(
                com.android.internal.R.integer.config_screenBrightnessDim));

        int screenBrightnessMinimum = Math.min(resources.getInteger(
                com.android.internal.R.integer.config_screenBrightnessSettingMinimum),
                mScreenBrightnessDimConfig);

        mScreenBrightnessRangeMinimum = clampAbsoluteBrightness(screenBrightnessMinimum);
        mScreenBrightnessRangeMaximum = PowerManager.BRIGHTNESS_ON;

        mUseSoftwareAutoBrightnessConfig = resources.getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);

        if (mUseSoftwareAutoBrightnessConfig) {
            final ContentResolver cr = mContext.getContentResolver();
            final ContentObserver observer = new ContentObserver(mHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    // As both LUX and BACKLIGHT might be changed at the same time, there's
                    // a potential race condition. As the settings provider API doesn't give
                    // us transactions to avoid them, wait a little until things settle down
                    mHandler.removeMessages(MSG_UPDATE_BACKLIGHT_SETTINGS);
                    mHandler.sendEmptyMessageDelayed(MSG_UPDATE_BACKLIGHT_SETTINGS, 1000);
                }
            };

            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.AUTO_BRIGHTNESS_LUX),
                    false, observer, UserHandle.USER_ALL);
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.AUTO_BRIGHTNESS_BACKLIGHT),
                    false, observer, UserHandle.USER_ALL);
            cr.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.AUTO_BRIGHTNESS_TWILIGHT_ADJUSTMENT),
                    false, observer, UserHandle.USER_ALL);

            mLightSensorWarmUpTimeConfig = resources.getInteger(
                    com.android.internal.R.integer.config_lightSensorWarmupTime);
            updateAutomaticBrightnessSettings();
        }

        mElectronBeamFadesConfig = resources.getBoolean(
                com.android.internal.R.bool.config_animateScreenLights);

        if (!DEBUG_PRETEND_PROXIMITY_SENSOR_ABSENT) {
            mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            if (mProximitySensor != null) {
                mProximityThreshold = Math.min(mProximitySensor.getMaximumRange(),
                        TYPICAL_PROXIMITY_THRESHOLD);
            }
        }

        if (mUseSoftwareAutoBrightnessConfig
                && !DEBUG_PRETEND_LIGHT_SENSOR_ABSENT) {
            mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        if (mUseSoftwareAutoBrightnessConfig && USE_TWILIGHT_ADJUSTMENT) {
            mTwilight.registerListener(mTwilightListener, mHandler);
        }
    }

    private void updateAutomaticBrightnessSettings() {
        int[] lux = getIntArrayForSetting(Settings.System.AUTO_BRIGHTNESS_LUX);
        int[] values = getIntArrayForSetting(Settings.System.AUTO_BRIGHTNESS_BACKLIGHT);
        Resources res = mContext.getResources();

        mScreenAutoBrightnessSpline = null;
        mUseSoftwareAutoBrightnessConfig = true;

        if (lux != null && values != null) {
            mScreenAutoBrightnessSpline = createAutoBrightnessSpline(lux, values);
            if (mScreenAutoBrightnessSpline == null) {
                Slog.w(TAG, "Found invalid auto-brightness configuration, falling back to default");
            }
        }

        if (mScreenAutoBrightnessSpline == null) {
            lux = res.getIntArray(com.android.internal.R.array.config_autoBrightnessLevels);
            values = res.getIntArray(com.android.internal.R.array.config_autoBrightnessLcdBacklightValues);
            mScreenAutoBrightnessSpline = createAutoBrightnessSpline(lux, values);
        }

        if (mScreenAutoBrightnessSpline == null) {
            Slog.e(TAG, "Error in config.xml.  config_autoBrightnessLcdBacklightValues "
                    + "(size " + values.length + ") "
                    + "must be monotic and have exactly one more entry than "
                    + "config_autoBrightnessLevels (size " + lux.length + ") "
                    + "which must be strictly increasing.  "
                    + "Auto-brightness will be disabled.");
            mUseSoftwareAutoBrightnessConfig = false;
            return;
        }

        mTwilightAdjustmentEnabled = Settings.System.getIntForUser(
                mContext.getContentResolver(), Settings.System.AUTO_BRIGHTNESS_TWILIGHT_ADJUSTMENT,
                0, UserHandle.USER_CURRENT) != 0;
    }

    private int[] getIntArrayForSetting(String setting) {
        final String value = Settings.System.getStringForUser(
                mContext.getContentResolver(), setting, UserHandle.USER_CURRENT);
        if (value == null) {
            return null;
        }
        String[] items = value.split(",");
        if (items == null || items.length == 0) {
            return null;
        }

        int[] values = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            try {
                values[i] = Integer.valueOf(items[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return values;
    }

    private static Spline createAutoBrightnessSpline(int[] lux, int[] brightness) {
        if (lux.length < 2 || lux.length != (brightness.length - 1)) {
            return null;
        }

        try {
            final int n = brightness.length;
            float[] x = new float[n];
            float[] y = new float[n];
            y[0] = normalizeAbsoluteBrightness(brightness[0]);
            for (int i = 1; i < n; i++) {
                x[i] = lux[i - 1];
                y[i] = normalizeAbsoluteBrightness(brightness[i]);
            }

            if (DEBUG) {
                for (int i = 0; i < n; i++) {
                    Slog.d(TAG, "Spline data[" + i + "]: x = " + x[i] + " y = " + y[i]);
                }
            }

            Spline spline = Spline.createMonotoneCubicSpline(x, y);
            if (DEBUG) {
                Slog.d(TAG, "Auto-brightness spline: " + spline);
                for (float v = 1f; v < lux[lux.length - 1] * 1.25f; v *= 1.25f) {
                    Slog.d(TAG, String.format("  %7.1f: %7.1f", v, spline.interpolate(v)));
                }
            }
            return spline;
        } catch (IllegalArgumentException ex) {
            Slog.e(TAG, "Could not create auto-brightness spline.", ex);
            return null;
        }
    }

    /**
     * Returns true if the proximity sensor screen-off function is available.
     */
    public boolean isProximitySensorAvailable() {
        return mProximitySensor != null;
    }

    /**
     * Requests a new power state.
     * The controller makes a copy of the provided object and then
     * begins adjusting the power state to match what was requested.
     *
     * @param request The requested power state.
     * @param waitForNegativeProximity If true, issues a request to wait for
     * negative proximity before turning the screen back on, assuming the screen
     * was turned off by the proximity sensor.
     * @return True if display is ready, false if there are important changes that must
     * be made asynchronously (such as turning the screen on), in which case the caller
     * should grab a wake lock, watch for {@link Callbacks#onStateChanged()} then try
     * the request again later until the state converges.
     */
    public boolean requestPowerState(DisplayPowerRequest request,
            boolean waitForNegativeProximity) {
        if (DEBUG) {
            Slog.d(TAG, "requestPowerState: "
                    + request + ", waitForNegativeProximity=" + waitForNegativeProximity);
        }

        synchronized (mLock) {
            boolean changed = false;

            if (waitForNegativeProximity
                    && !mPendingWaitForNegativeProximityLocked) {
                mPendingWaitForNegativeProximityLocked = true;
                changed = true;
            }

            if (mPendingRequestLocked == null) {
                mPendingRequestLocked = new DisplayPowerRequest(request);
                changed = true;
            } else if (!mPendingRequestLocked.equals(request)) {
                mPendingRequestLocked.copyFrom(request);
                changed = true;
            }

            if (changed) {
                mDisplayReadyLocked = false;
            }

            if (changed && !mPendingRequestChangedLocked) {
                mPendingRequestChangedLocked = true;
                sendUpdatePowerStateLocked();
            }

            return mDisplayReadyLocked;
        }
    }

    private void sendUpdatePowerState() {
        synchronized (mLock) {
            sendUpdatePowerStateLocked();
        }
    }

    private void sendUpdatePowerStateLocked() {
        if (!mPendingUpdatePowerStateLocked) {
            mPendingUpdatePowerStateLocked = true;
            Message msg = mHandler.obtainMessage(MSG_UPDATE_POWER_STATE);
            msg.setAsynchronous(true);
            mHandler.sendMessage(msg);
        }
    }

    private void initialize() {
        mPowerState = new DisplayPowerState(
                new ElectronBeam(mDisplayManager), mDisplayBlanker,
                mLights.getLight(LightsService.LIGHT_ID_BACKLIGHT));

        mElectronBeamOnAnimator = ObjectAnimator.ofFloat(
                mPowerState, DisplayPowerState.ELECTRON_BEAM_LEVEL, 0.0f, 1.0f);
        mElectronBeamOnAnimator.setDuration(ELECTRON_BEAM_ON_ANIMATION_DURATION_MILLIS);
        mElectronBeamOnAnimator.addListener(mAnimatorListener);

        mElectronBeamOffAnimator = ObjectAnimator.ofFloat(
                mPowerState, DisplayPowerState.ELECTRON_BEAM_LEVEL, 1.0f, 0.0f);
        mElectronBeamOffAnimator.setDuration(ELECTRON_BEAM_OFF_ANIMATION_DURATION_MILLIS);
        mElectronBeamOffAnimator.addListener(mAnimatorListener);

        mScreenBrightnessRampAnimator = new RampAnimator<DisplayPowerState>(
                mPowerState, DisplayPowerState.SCREEN_BRIGHTNESS);
    }

    private final Animator.AnimatorListener mAnimatorListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            sendUpdatePowerState();
        }
        @Override
        public void onAnimationRepeat(Animator animation) {
        }
        @Override
        public void onAnimationCancel(Animator animation) {
        }
    };

    private void updatePowerState() {
        // Update the power state request.
        final boolean mustNotify;
        boolean mustInitialize = false;
        boolean updateAutoBrightness = mTwilightChanged || mAutoBrightnessSettingsChanged;
        boolean wasDim = false;

        mTwilightChanged = false;
        mAutoBrightnessSettingsChanged = false;

        synchronized (mLock) {
            mPendingUpdatePowerStateLocked = false;
            if (mPendingRequestLocked == null) {
                return; // wait until first actual power request
            }

            if (mPowerRequest == null) {
                mPowerRequest = new DisplayPowerRequest(mPendingRequestLocked);
                mWaitingForNegativeProximity = mPendingWaitForNegativeProximityLocked;
                mPendingWaitForNegativeProximityLocked = false;
                mPendingRequestChangedLocked = false;
                mustInitialize = true;
            } else if (mPendingRequestChangedLocked) {
                if (mPowerRequest.screenAutoBrightnessAdjustment
                        != mPendingRequestLocked.screenAutoBrightnessAdjustment) {
                    updateAutoBrightness = true;
                }
                wasDim = (mPowerRequest.screenState == DisplayPowerRequest.SCREEN_STATE_DIM);
                mPowerRequest.copyFrom(mPendingRequestLocked);
                mWaitingForNegativeProximity |= mPendingWaitForNegativeProximityLocked;
                mPendingWaitForNegativeProximityLocked = false;
                mPendingRequestChangedLocked = false;
                mDisplayReadyLocked = false;
            }

            mustNotify = !mDisplayReadyLocked;
        }

        // Initialize things the first time the power state is changed.
        if (mustInitialize) {
            initialize();
        }

        // Apply the proximity sensor.
        if (mProximitySensor != null) {
            if (mPowerRequest.useProximitySensor
                    && mPowerRequest.screenState != DisplayPowerRequest.SCREEN_STATE_OFF) {
                setProximitySensorEnabled(true);
                if (!mScreenOffBecauseOfProximity
                        && mProximity == PROXIMITY_POSITIVE) {
                    mScreenOffBecauseOfProximity = true;
                    sendOnProximityPositive();
                    setScreenOn(false);
                }
            } else if (mWaitingForNegativeProximity
                    && mScreenOffBecauseOfProximity
                    && mProximity == PROXIMITY_POSITIVE
                    && mPowerRequest.screenState != DisplayPowerRequest.SCREEN_STATE_OFF) {
                setProximitySensorEnabled(true);
            } else {
                setProximitySensorEnabled(false);
                mWaitingForNegativeProximity = false;
            }
            if (mScreenOffBecauseOfProximity
                    && mProximity != PROXIMITY_POSITIVE) {
                mScreenOffBecauseOfProximity = false;
                sendOnProximityNegative();
            }
        } else {
            mWaitingForNegativeProximity = false;
        }

        // Turn on the light sensor if needed.
        if (mLightSensor != null) {
            setLightSensorEnabled(mPowerRequest.useAutoBrightness
                    && wantScreenOn(mPowerRequest.screenState), updateAutoBrightness);
        }

        // Set the screen brightness.
        if (wantScreenOn(mPowerRequest.screenState)) {
            int target;
            boolean slow;
            if (mScreenAutoBrightness >= 0 && mLightSensorEnabled) {
                // Use current auto-brightness value.
                target = mScreenAutoBrightness;
                slow = mUsingScreenAutoBrightness;
                mUsingScreenAutoBrightness = true;
            } else {
                // Light sensor is disabled or not ready yet.
                // Use the current brightness setting from the request, which is expected
                // provide a nominal default value for the case where auto-brightness
                // is not ready yet.
                target = mPowerRequest.screenBrightness;
                slow = false;
                mUsingScreenAutoBrightness = false;
            }
            if (mPowerRequest.screenState == DisplayPowerRequest.SCREEN_STATE_DIM) {
                // Dim quickly by at least some minimum amount.
                target = Math.min(target - SCREEN_DIM_MINIMUM_REDUCTION,
                        mScreenBrightnessDimConfig);
                slow = false;
            } else if (wasDim) {
                // Brighten quickly.
                slow = false;
            }
            animateScreenBrightness(clampScreenBrightness(target),
                    slow ? BRIGHTNESS_RAMP_RATE_SLOW : BRIGHTNESS_RAMP_RATE_FAST);
        } else {
            // Screen is off.  Don't bother changing the brightness.
            mUsingScreenAutoBrightness = false;
        }

        // Animate the screen on or off.
        if (!mScreenOffBecauseOfProximity) {
            if (wantScreenOn(mPowerRequest.screenState)) {
                // Want screen on.
                // Wait for previous off animation to complete beforehand.
                // It is relatively short but if we cancel it and switch to the
                // on animation immediately then the results are pretty ugly.
                if (!mElectronBeamOffAnimator.isStarted()) {
                    // Turn the screen on.  The contents of the screen may not yet
                    // be visible if the electron beam has not been dismissed because
                    // its last frame of animation is solid black.
                    setScreenOn(true);

                    if (mPowerRequest.blockScreenOn
                            && mPowerState.getElectronBeamLevel() == 0.0f) {
                        blockScreenOn();
                    } else {
                        unblockScreenOn();
                        if (USE_ELECTRON_BEAM_ON_ANIMATION) {
                            if (!mElectronBeamOnAnimator.isStarted()) {
                                if (mPowerState.getElectronBeamLevel() == 1.0f) {
                                    mPowerState.dismissElectronBeam();
                                } else if (mPowerState.prepareElectronBeam(
                                        mElectronBeamFadesConfig ?
                                                ElectronBeam.MODE_FADE :
                                                        ElectronBeam.MODE_WARM_UP)) {
                                    mElectronBeamOnAnimator.start();
                                } else {
                                    mElectronBeamOnAnimator.end();
                                }
                            }
                        } else {
                            mPowerState.setElectronBeamLevel(1.0f);
                            mPowerState.dismissElectronBeam();
                        }
                    }
                }
            } else {
                // Want screen off.
                // Wait for previous on animation to complete beforehand.
                if (!mElectronBeamOnAnimator.isStarted()) {
                    if (!mElectronBeamOffAnimator.isStarted()) {
                        if (mPowerState.getElectronBeamLevel() == 0.0f) {
                            setScreenOn(false);
                        } else if (mPowerState.prepareElectronBeam(
                                mElectronBeamFadesConfig ?
                                        ElectronBeam.MODE_FADE :
                                        ElectronBeam.MODE_COOL_DOWN)
                                && mPowerState.isScreenOn()
                                && useScreenOffAnimation()) {
                            mElectronBeamOffAnimator.start();
                        } else {
                            mElectronBeamOffAnimator.end();
                        }
                    }
                }
            }
        }

        // Report whether the display is ready for use.
        // We mostly care about the screen state here, ignoring brightness changes
        // which will be handled asynchronously.
        if (mustNotify
                && !mScreenOnWasBlocked
                && !mElectronBeamOnAnimator.isStarted()
                && !mElectronBeamOffAnimator.isStarted()
                && mPowerState.waitUntilClean(mCleanListener)) {
            synchronized (mLock) {
                if (!mPendingRequestChangedLocked) {
                    mDisplayReadyLocked = true;

                    if (DEBUG) {
                        Slog.d(TAG, "Display ready!");
                    }
                }
            }
            sendOnStateChanged();
        }
    }

    private void blockScreenOn() {
        if (!mScreenOnWasBlocked) {
            mScreenOnWasBlocked = true;
            if (DEBUG) {
                Slog.d(TAG, "Blocked screen on.");
                mScreenOnBlockStartRealTime = SystemClock.elapsedRealtime();
            }
        }
    }

    private void unblockScreenOn() {
        if (mScreenOnWasBlocked) {
            mScreenOnWasBlocked = false;
            if (DEBUG) {
                Slog.d(TAG, "Unblocked screen on after " +
                        (SystemClock.elapsedRealtime() - mScreenOnBlockStartRealTime) + " ms");
            }
        }
    }

    private void setScreenOn(boolean on) {
        if (!mPowerState.isScreenOn() == on) {
            mPowerState.setScreenOn(on);
            if (on) {
                mNotifier.onScreenOn();
            } else {
                mLights.getLight(LightsService.LIGHT_ID_BUTTONS).setBrightness(0);
                mLights.getLight(LightsService.LIGHT_ID_KEYBOARD).setBrightness(0);
                mNotifier.onScreenOff();
            }
        }
    }

    private int clampScreenBrightness(int value) {
        return clamp(value, mScreenBrightnessRangeMinimum, mScreenBrightnessRangeMaximum);
    }

    private static int clampAbsoluteBrightness(int value) {
        return clamp(value, PowerManager.BRIGHTNESS_OFF, PowerManager.BRIGHTNESS_ON);
    }

    private static int clamp(int value, int min, int max) {
        if (value <= min) {
            return min;
        }
        if (value >= max) {
            return max;
        }
        return value;
    }

    private static float normalizeAbsoluteBrightness(int value) {
        return (float)clampAbsoluteBrightness(value) / PowerManager.BRIGHTNESS_ON;
    }

    private void animateScreenBrightness(int target, int rate) {
        if (mScreenBrightnessRampAnimator.animateTo(target, rate)) {
            mNotifier.onScreenBrightness(target);
        }
    }

    private final Runnable mCleanListener = new Runnable() {
        @Override
        public void run() {
            sendUpdatePowerState();
        }
    };

    private void setProximitySensorEnabled(boolean enable) {
        if (enable) {
            if (!mProximitySensorEnabled) {
                mProximitySensorEnabled = true;
                mPendingProximity = PROXIMITY_UNKNOWN;
                mSensorManager.registerListener(mProximitySensorListener, mProximitySensor,
                        SensorManager.SENSOR_DELAY_NORMAL, mHandler);
            }
        } else {
            if (mProximitySensorEnabled) {
                mProximitySensorEnabled = false;
                mProximity = PROXIMITY_UNKNOWN;
                mHandler.removeMessages(MSG_PROXIMITY_SENSOR_DEBOUNCED);
                mSensorManager.unregisterListener(mProximitySensorListener);
            }
        }
    }

    private void handleProximitySensorEvent(long time, boolean positive) {
        if (mPendingProximity == PROXIMITY_NEGATIVE && !positive) {
            return; // no change
        }
        if (mPendingProximity == PROXIMITY_POSITIVE && positive) {
            return; // no change
        }

        // Only accept a proximity sensor reading if it remains
        // stable for the entire debounce delay.
        mHandler.removeMessages(MSG_PROXIMITY_SENSOR_DEBOUNCED);
        if (positive) {
            mPendingProximity = PROXIMITY_POSITIVE;
            mPendingProximityDebounceTime = time + PROXIMITY_SENSOR_POSITIVE_DEBOUNCE_DELAY;
        } else {
            mPendingProximity = PROXIMITY_NEGATIVE;
            mPendingProximityDebounceTime = time + PROXIMITY_SENSOR_NEGATIVE_DEBOUNCE_DELAY;
        }
        debounceProximitySensor();
    }

    private void debounceProximitySensor() {
        if (mPendingProximity != PROXIMITY_UNKNOWN) {
            final long now = SystemClock.uptimeMillis();
            if (mPendingProximityDebounceTime <= now) {
                mProximity = mPendingProximity;
                sendUpdatePowerState();
            } else {
                Message msg = mHandler.obtainMessage(MSG_PROXIMITY_SENSOR_DEBOUNCED);
                msg.setAsynchronous(true);
                mHandler.sendMessageAtTime(msg, mPendingProximityDebounceTime);
            }
        }
    }

    private void setLightSensorEnabled(boolean enable, boolean updateAutoBrightness) {
        if (enable) {
            if (!mLightSensorEnabled) {
                updateAutoBrightness = true;
                mLightSensorEnabled = true;
                mLightSensorEnableTime = SystemClock.uptimeMillis();

                int updateRateMillis = (int)
                        (mPowerRequest.responsitivityFactor * LIGHT_SENSOR_RATE_MILLIS);
                mSensorManager.registerListener(mLightSensorListener, mLightSensor,
                        updateRateMillis * 1000, mHandler);
            }
        } else {
            if (mLightSensorEnabled) {
                mLightSensorEnabled = false;
                mAmbientLuxValid = false;
                mRecentLightSamples = 0;
                mHandler.removeMessages(MSG_LIGHT_SENSOR_DEBOUNCED);
                mSensorManager.unregisterListener(mLightSensorListener);
            }
        }
        if (updateAutoBrightness) {
            updateAutoBrightness(false);
        }
    }

    private void handleLightSensorEvent(long time, float lux) {
        mHandler.removeMessages(MSG_LIGHT_SENSOR_DEBOUNCED);

        applyLightSensorMeasurement(time, lux);
        updateAmbientLux(time);
    }

    private void applyLightSensorMeasurement(long time, float lux) {
        // Update our filters.
        mRecentLightSamples += 1;
        if (mRecentLightSamples == 1) {
            mRecentShortTermAverageLux = lux;
            mRecentLongTermAverageLux = lux;
        } else {
            final long timeDelta = time - mLastObservedLuxTime;
            final long shortTermConstant = (long)
                    (mPowerRequest.responsitivityFactor * SHORT_TERM_AVERAGE_LIGHT_TIME_CONSTANT);
            final long longTermConstant = (long)
                    (mPowerRequest.responsitivityFactor * LONG_TERM_AVERAGE_LIGHT_TIME_CONSTANT);
            mRecentShortTermAverageLux += (lux - mRecentShortTermAverageLux)
                    * timeDelta / (shortTermConstant + timeDelta);
            mRecentLongTermAverageLux += (lux - mRecentLongTermAverageLux)
                    * timeDelta / (longTermConstant + timeDelta);
        }

        if (DEBUG) {
            Slog.d(TAG, "applyLightSensorMeasurement: lux=" + lux
                    + ", mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                    +", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux);
        }

        // Remember this sample value.
        mLastObservedLux = lux;
        mLastObservedLuxTime = time;
    }

    private void updateAmbientLux(long time) {
        // If the light sensor was just turned on then immediately update our initial
        // estimate of the current ambient light level.
        if (!mAmbientLuxValid
                || (time - mLightSensorEnableTime) < mLightSensorWarmUpTimeConfig) {
            mAmbientLux = mRecentShortTermAverageLux;
            mAmbientLuxValid = true;
            mDebounceLuxDirection = 0;
            mDebounceLuxTime = time;
            if (DEBUG) {
                Slog.d(TAG, "updateAmbientLux: Initializing: "
                        + "mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                        + ", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux
                        + ", mAmbientLux=" + mAmbientLux);
            }
            updateAutoBrightness(true);
            return;
        }

        // Determine whether the ambient environment appears to be brightening.
        float brighteningLuxThreshold = mAmbientLux * (1.0f + BRIGHTENING_LIGHT_HYSTERESIS);
        if (mRecentShortTermAverageLux > brighteningLuxThreshold
                && mRecentLongTermAverageLux > brighteningLuxThreshold) {
            long debounceDelay;

            if (mRecentShortTermAverageLux - mRecentLongTermAverageLux > BRIGHTENING_FAST_THRESHOLD) {
                debounceDelay = BRIGHTENING_LIGHT_FAST_DEBOUNCE;
            } else {
                debounceDelay = BRIGHTENING_LIGHT_DEBOUNCE;
            }
            debounceDelay = (long) (mPowerRequest.responsitivityFactor * debounceDelay);

            if (mDebounceLuxDirection <= 0) {
                mDebounceLuxDirection = 1;
                mDebounceLuxTime = time;
                if (DEBUG) {
                    Slog.d(TAG, "updateAmbientLux: Possibly brightened, waiting for "
                            + debounceDelay + " ms: "
                            + "brighteningLuxThreshold=" + brighteningLuxThreshold
                            + ", mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                            + ", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux
                            + ", mAmbientLux=" + mAmbientLux);
                }
            }

            long debounceTime = mDebounceLuxTime + debounceDelay;

            if (time >= debounceTime) {
                mAmbientLux = mRecentShortTermAverageLux;
                if (DEBUG) {
                    Slog.d(TAG, "updateAmbientLux: Brightened: "
                            + "brighteningLuxThreshold=" + brighteningLuxThreshold
                            + ", mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                            + ", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux
                            + ", mAmbientLux=" + mAmbientLux);
                }
                updateAutoBrightness(true);
            } else {
                mHandler.sendEmptyMessageAtTime(MSG_LIGHT_SENSOR_DEBOUNCED, debounceTime);
            }
            return;
        }

        // Determine whether the ambient environment appears to be darkening.
        float darkeningLuxThreshold = mAmbientLux * (1.0f - DARKENING_LIGHT_HYSTERESIS);
        if (mRecentShortTermAverageLux < darkeningLuxThreshold
                && mRecentLongTermAverageLux < darkeningLuxThreshold) {
            long debounceDelay = (long)
                    (mPowerRequest.responsitivityFactor * DARKENING_LIGHT_DEBOUNCE);
            if (mDebounceLuxDirection >= 0) {
                mDebounceLuxDirection = -1;
                mDebounceLuxTime = time;
                if (DEBUG) {
                    Slog.d(TAG, "updateAmbientLux: Possibly darkened, waiting for "
                            + debounceDelay + " ms: "
                            + "darkeningLuxThreshold=" + darkeningLuxThreshold
                            + ", mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                            + ", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux
                            + ", mAmbientLux=" + mAmbientLux);
                }
            }
            long debounceTime = mDebounceLuxTime + debounceDelay;
            if (time >= debounceTime) {
                // Be conservative about reducing the brightness, only reduce it a little bit
                // at a time to avoid having to bump it up again soon.
                mAmbientLux = Math.max(mRecentShortTermAverageLux, mRecentLongTermAverageLux);
                if (DEBUG) {
                    Slog.d(TAG, "updateAmbientLux: Darkened: "
                            + "darkeningLuxThreshold=" + darkeningLuxThreshold
                            + ", mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                            + ", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux
                            + ", mAmbientLux=" + mAmbientLux);
                }
                updateAutoBrightness(true);
            } else {
                mHandler.sendEmptyMessageAtTime(MSG_LIGHT_SENSOR_DEBOUNCED, debounceTime);
            }
            return;
        }

        // No change or change is within the hysteresis thresholds.
        if (mDebounceLuxDirection != 0) {
            mDebounceLuxDirection = 0;
            mDebounceLuxTime = time;
            if (DEBUG) {
                Slog.d(TAG, "updateAmbientLux: Canceled debounce: "
                        + "brighteningLuxThreshold=" + brighteningLuxThreshold
                        + ", darkeningLuxThreshold=" + darkeningLuxThreshold
                        + ", mRecentShortTermAverageLux=" + mRecentShortTermAverageLux
                        + ", mRecentLongTermAverageLux=" + mRecentLongTermAverageLux
                        + ", mAmbientLux=" + mAmbientLux);
            }
        }

        // If the light level does not change, then the sensor may not report
        // a new value.  This can cause problems for the auto-brightness algorithm
        // because the filters might not be updated.  To work around it, we want to
        // make sure to update the filters whenever the observed light level could
        // possibly exceed one of the hysteresis thresholds.
        if (mLastObservedLux > brighteningLuxThreshold
                || mLastObservedLux < darkeningLuxThreshold) {
            long synthesizedDelay = (long)
                    (mPowerRequest.responsitivityFactor * SYNTHETIC_LIGHT_SENSOR_RATE_MILLIS);
            mHandler.sendEmptyMessageAtTime(MSG_LIGHT_SENSOR_DEBOUNCED, time + synthesizedDelay);
        }
    }

    private void debounceLightSensor() {
        if (mLightSensorEnabled) {
            long time = SystemClock.uptimeMillis();
            long synthesizedDelay = (long)
                    (mPowerRequest.responsitivityFactor * SYNTHETIC_LIGHT_SENSOR_RATE_MILLIS);
            if (time >= mLastObservedLuxTime + synthesizedDelay) {
                if (DEBUG) {
                    Slog.d(TAG, "debounceLightSensor: Synthesizing light sensor measurement "
                            + "after " + (time - mLastObservedLuxTime) + " ms.");
                }
                applyLightSensorMeasurement(time, mLastObservedLux);
            }
            updateAmbientLux(time);
        }
    }

    private void updateAutoBrightness(boolean sendUpdate) {
        if (!mAmbientLuxValid) {
            return;
        }

        float value = mScreenAutoBrightnessSpline.interpolate(mAmbientLux);
        float gamma = 1.0f;

        if (DEBUG) {
            Slog.d(TAG, "updateAutoBrightness: mAmbientLux=" + mAmbientLux + " -> value=" + value);
        }

        if (USE_SCREEN_AUTO_BRIGHTNESS_ADJUSTMENT
                && mPowerRequest.screenAutoBrightnessAdjustment != 0.0f) {
            final float adjGamma = FloatMath.pow(SCREEN_AUTO_BRIGHTNESS_ADJUSTMENT_MAX_GAMMA,
                    Math.min(1.0f, Math.max(-1.0f,
                            -mPowerRequest.screenAutoBrightnessAdjustment)));
            gamma *= adjGamma;
            if (DEBUG) {
                Slog.d(TAG, "updateAutoBrightness: adjGamma=" + adjGamma);
            }
        }

        if (USE_TWILIGHT_ADJUSTMENT && mTwilightAdjustmentEnabled) {
            TwilightState state = mTwilight.getCurrentState();
            if (state != null && state.isNight()) {
                final long now = System.currentTimeMillis();
                final float earlyGamma =
                        getTwilightGamma(now, state.getYesterdaySunset(), state.getTodaySunrise());
                final float lateGamma =
                        getTwilightGamma(now, state.getTodaySunset(), state.getTomorrowSunrise());
                gamma *= earlyGamma * lateGamma;
                if (DEBUG) {
                    Slog.d(TAG, "updateAutoBrightness: earlyGamma=" + earlyGamma
                            + ", lateGamma=" + lateGamma);
                }
            }
        }

        if (gamma != 1.0f) {
            final float in = value;
            value = FloatMath.pow(value, gamma);
            if (DEBUG) {
                Slog.d(TAG, "updateAutoBrightness: gamma=" + gamma
                        + ", in=" + in + ", out=" + value);
            }
        }

        int newScreenAutoBrightness = clampScreenBrightness(
                Math.round(value * PowerManager.BRIGHTNESS_ON));
        if (mScreenAutoBrightness != newScreenAutoBrightness) {
            if (DEBUG) {
                Slog.d(TAG, "updateAutoBrightness: mScreenAutoBrightness="
                        + mScreenAutoBrightness + ", newScreenAutoBrightness="
                        + newScreenAutoBrightness);
            }

            mScreenAutoBrightness = newScreenAutoBrightness;
            mLastScreenAutoBrightnessGamma = gamma;
            if (sendUpdate) {
                sendUpdatePowerState();
            }
        }
    }

    private static float getTwilightGamma(long now, long lastSunset, long nextSunrise) {
        if (lastSunset < 0 || nextSunrise < 0
                || now < lastSunset || now > nextSunrise) {
            return 1.0f;
        }

        if (now < lastSunset + TWILIGHT_ADJUSTMENT_TIME) {
            return lerp(1.0f, TWILIGHT_ADJUSTMENT_MAX_GAMMA,
                    (float)(now - lastSunset) / TWILIGHT_ADJUSTMENT_TIME);
        }

        if (now > nextSunrise - TWILIGHT_ADJUSTMENT_TIME) {
            return lerp(1.0f, TWILIGHT_ADJUSTMENT_MAX_GAMMA,
                    (float)(nextSunrise - now) / TWILIGHT_ADJUSTMENT_TIME);
        }

        return TWILIGHT_ADJUSTMENT_MAX_GAMMA;
    }

    private static float lerp(float x, float y, float alpha) {
        return x + (y - x) * alpha;
    }

    private void sendOnStateChanged() {
        mCallbackHandler.post(mOnStateChangedRunnable);
    }

    private final Runnable mOnStateChangedRunnable = new Runnable() {
        @Override
        public void run() {
            mCallbacks.onStateChanged();
        }
    };

    private void sendOnProximityPositive() {
        mCallbackHandler.post(mOnProximityPositiveRunnable);
    }

    private final Runnable mOnProximityPositiveRunnable = new Runnable() {
        @Override
        public void run() {
            mCallbacks.onProximityPositive();
        }
    };

    private void sendOnProximityNegative() {
        mCallbackHandler.post(mOnProximityNegativeRunnable);
    }

    private final Runnable mOnProximityNegativeRunnable = new Runnable() {
        @Override
        public void run() {
            mCallbacks.onProximityNegative();
        }
    };

    public void dump(final PrintWriter pw) {
        synchronized (mLock) {
            pw.println();
            pw.println("Display Controller Locked State:");
            pw.println("  mDisplayReadyLocked=" + mDisplayReadyLocked);
            pw.println("  mPendingRequestLocked=" + mPendingRequestLocked);
            pw.println("  mPendingRequestChangedLocked=" + mPendingRequestChangedLocked);
            pw.println("  mPendingWaitForNegativeProximityLocked="
                    + mPendingWaitForNegativeProximityLocked);
            pw.println("  mPendingUpdatePowerStateLocked=" + mPendingUpdatePowerStateLocked);
        }

        pw.println();
        pw.println("Display Controller Configuration:");
        pw.println("  mScreenBrightnessDimConfig=" + mScreenBrightnessDimConfig);
        pw.println("  mScreenBrightnessRangeMinimum=" + mScreenBrightnessRangeMinimum);
        pw.println("  mScreenBrightnessRangeMaximum=" + mScreenBrightnessRangeMaximum);
        pw.println("  mUseSoftwareAutoBrightnessConfig="
                + mUseSoftwareAutoBrightnessConfig);
        pw.println("  mScreenAutoBrightnessSpline=" + mScreenAutoBrightnessSpline);
        pw.println("  mLightSensorWarmUpTimeConfig=" + mLightSensorWarmUpTimeConfig);

        mHandler.runWithScissors(new Runnable() {
            @Override
            public void run() {
                dumpLocal(pw);
            }
        }, 1000);
    }

    private void dumpLocal(PrintWriter pw) {
        pw.println();
        pw.println("Display Controller Thread State:");
        pw.println("  mPowerRequest=" + mPowerRequest);
        pw.println("  mWaitingForNegativeProximity=" + mWaitingForNegativeProximity);

        pw.println("  mProximitySensor=" + mProximitySensor);
        pw.println("  mProximitySensorEnabled=" + mProximitySensorEnabled);
        pw.println("  mProximityThreshold=" + mProximityThreshold);
        pw.println("  mProximity=" + proximityToString(mProximity));
        pw.println("  mPendingProximity=" + proximityToString(mPendingProximity));
        pw.println("  mPendingProximityDebounceTime="
                + TimeUtils.formatUptime(mPendingProximityDebounceTime));
        pw.println("  mScreenOffBecauseOfProximity=" + mScreenOffBecauseOfProximity);

        pw.println("  mLightSensor=" + mLightSensor);
        pw.println("  mLightSensorEnabled=" + mLightSensorEnabled);
        pw.println("  mLightSensorEnableTime="
                + TimeUtils.formatUptime(mLightSensorEnableTime));
        pw.println("  mAmbientLux=" + mAmbientLux);
        pw.println("  mAmbientLuxValid=" + mAmbientLuxValid);
        pw.println("  mLastObservedLux=" + mLastObservedLux);
        pw.println("  mLastObservedLuxTime="
                + TimeUtils.formatUptime(mLastObservedLuxTime));
        pw.println("  mRecentLightSamples=" + mRecentLightSamples);
        pw.println("  mRecentShortTermAverageLux=" + mRecentShortTermAverageLux);
        pw.println("  mRecentLongTermAverageLux=" + mRecentLongTermAverageLux);
        pw.println("  mDebounceLuxDirection=" + mDebounceLuxDirection);
        pw.println("  mDebounceLuxTime=" + TimeUtils.formatUptime(mDebounceLuxTime));
        pw.println("  mScreenAutoBrightness=" + mScreenAutoBrightness);
        pw.println("  mUsingScreenAutoBrightness=" + mUsingScreenAutoBrightness);
        pw.println("  mLastScreenAutoBrightnessGamma=" + mLastScreenAutoBrightnessGamma);
        pw.println("  mTwilight.getCurrentState()=" + mTwilight.getCurrentState());

        if (mElectronBeamOnAnimator != null) {
            pw.println("  mElectronBeamOnAnimator.isStarted()=" +
                    mElectronBeamOnAnimator.isStarted());
        }
        if (mElectronBeamOffAnimator != null) {
            pw.println("  mElectronBeamOffAnimator.isStarted()=" +
                    mElectronBeamOffAnimator.isStarted());
        }

        if (mPowerState != null) {
            mPowerState.dump(pw);
        }
    }

    private static String proximityToString(int state) {
        switch (state) {
            case PROXIMITY_UNKNOWN:
                return "Unknown";
            case PROXIMITY_NEGATIVE:
                return "Negative";
            case PROXIMITY_POSITIVE:
                return "Positive";
            default:
                return Integer.toString(state);
        }
    }

    private static boolean wantScreenOn(int state) {
        switch (state) {
            case DisplayPowerRequest.SCREEN_STATE_BRIGHT:
            case DisplayPowerRequest.SCREEN_STATE_DIM:
                return true;
        }
        return false;
    }

    /**
     * Asynchronous callbacks from the power controller to the power manager service.
     */
    public interface Callbacks {
        void onStateChanged();
        void onProximityPositive();
        void onProximityNegative();
    }

    private final class DisplayControllerHandler extends Handler {
        public DisplayControllerHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_POWER_STATE:
                    updatePowerState();
                    break;

                case MSG_PROXIMITY_SENSOR_DEBOUNCED:
                    debounceProximitySensor();
                    break;

                case MSG_LIGHT_SENSOR_DEBOUNCED:
                    debounceLightSensor();
                    break;

                case MSG_UPDATE_BACKLIGHT_SETTINGS:
                    mAutoBrightnessSettingsChanged = true;
                    updateAutomaticBrightnessSettings();
                    updatePowerState();
                    break;
            }
        }
    }

    private final SensorEventListener mProximitySensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mProximitySensorEnabled) {
                final long time = SystemClock.uptimeMillis();
                final float distance = event.values[0];
                boolean positive = distance >= 0.0f && distance < mProximityThreshold;
                handleProximitySensorEvent(time, positive);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used.
        }
    };

    private final SensorEventListener mLightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mLightSensorEnabled) {
                final long time = SystemClock.uptimeMillis();
                final float lux = event.values[0];
                handleLightSensorEvent(time, lux);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Not used.
        }
    };

    private final TwilightService.TwilightListener mTwilightListener =
            new TwilightService.TwilightListener() {
        @Override
        public void onTwilightStateChanged() {
            mTwilightChanged = true;
            updatePowerState();
        }
    };

    private boolean useScreenOffAnimation() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_ANIMATION, 1) == 1;
    }
}
