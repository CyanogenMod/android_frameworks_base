package com.android.server.display;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.text.format.DateUtils;
import android.util.MathUtils;
import android.util.Slog;
import android.provider.Settings;

import com.android.server.LocalServices;
import com.android.server.twilight.TwilightManager;
import com.android.server.twilight.TwilightService;
import com.android.server.twilight.TwilightState;

import org.cyanogenmod.hardware.AdaptiveBacklight;
import org.cyanogenmod.hardware.ColorEnhancement;
import org.cyanogenmod.hardware.DisplayColorCalibration;
import org.cyanogenmod.hardware.SunlightEnhancement;

public class DisplayTemperature {

    private static final String TAG = "DisplayTemperature";

    private static final long TWILIGHT_ADJUSTMENT_TIME = DateUtils.HOUR_IN_MILLIS * 1;

    private static final float OFF_TEMPERATURE = 6500.0f;

    public static final float DEFAULT_DAY_TEMPERATURE = 6500.0f;
    public static final float DEFAULT_NIGHT_TEMPERATURE = 4500.0f;
    public static final float DEFAULT_OUTDOOR_LUX = 9000.0f;

    public static final int MODE_DAY = 0;
    public static final int MODE_NIGHT = 1;
    public static final int MODE_AUTO = 2;
    public static final int MODE_OUTDOOR = 3;

    private float mLastTemperature = OFF_TEMPERATURE;
    private float mCurrentLux = 0.0f;

    private int mMode;

    private boolean mOutdoorMode;
    private boolean mColorEnhancement;
    private boolean mLowPower;

    private final Context mContext;
    private final Handler mHandler;

    private float mDayTemperature;
    private float mNightTemperature;

    private boolean mUseOutdoorMode;
    private boolean mUseColorEnhancement;
    private boolean mUseLowPower;

    // The twilight service.
    private final TwilightManager mTwilightManager;
    private TwilightState mTwilight;

    private final SettingsObserver mObserver = new SettingsObserver();

    DisplayTemperature(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;

        mTwilightManager = LocalServices.getService(TwilightManager.class);

        if (SunlightEnhancement.isSupported()) {
            mOutdoorMode = SunlightEnhancement.isEnabled();
        }

        if (ColorEnhancement.isSupported()) {
            mColorEnhancement = ColorEnhancement.isEnabled();
        }

        if (AdaptiveBacklight.isSupported()) {
            mLowPower = AdaptiveBacklight.isEnabled();
        }

        updateSettings();
        mObserver.register(true);
    }

    private void updateSettings() {
        mDayTemperature = Settings.System.getFloatForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_DAY,
                DEFAULT_DAY_TEMPERATURE,
                UserHandle.USER_CURRENT);
        mNightTemperature = Settings.System.getFloatForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_NIGHT,
                DEFAULT_NIGHT_TEMPERATURE,
                UserHandle.USER_CURRENT);
        mMode = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_TEMPERATURE_MODE,
                MODE_DAY,
                UserHandle.USER_CURRENT);
        mUseOutdoorMode = (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_AUTO_OUTDOOR_MODE,
                1,
                UserHandle.USER_CURRENT) == 1) && SunlightEnhancement.isSupported();
        mUseLowPower = (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_LOW_POWER,
                1,
                UserHandle.USER_CURRENT) == 1) && AdaptiveBacklight.isSupported();
        mColorEnhancement = (Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.DISPLAY_COLOR_ENHANCE,
                1,
                UserHandle.USER_CURRENT) == 1) && ColorEnhancement.isSupported();

        mTwilight = mTwilightManager.getCurrentState();

        if (mMode == MODE_AUTO) {
            updateLiveDisplay(mCurrentLux);
        } else if (mMode == MODE_NIGHT) {
            updateLiveDisplayInternal(mNightTemperature);
        } else {
            updateLiveDisplayInternal(mDayTemperature);
        }
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

        public SettingsObserver() {
            super(mHandler);
        }

        public void register(boolean register) {
            final ContentResolver cr = mContext.getContentResolver();
            if (register) {
                cr.registerContentObserver(DISPLAY_TEMPERATURE_DAY_URI, false, this);
                cr.registerContentObserver(DISPLAY_TEMPERATURE_NIGHT_URI, false, this);
                cr.registerContentObserver(DISPLAY_TEMPERATURE_MODE_URI, false, this);
                cr.registerContentObserver(DISPLAY_AUTO_OUTDOOR_MODE_URI, false, this);
                cr.registerContentObserver(DISPLAY_LOW_POWER_URI, false, this);
                cr.registerContentObserver(DISPLAY_COLOR_ENHANCE_URI, false, this);
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

    public synchronized void updateLiveDisplay(float lux) {
        mTwilight = mTwilightManager.getCurrentState();
        mCurrentLux = lux;

        if (mMode == MODE_AUTO) {
            updateLiveDisplayInternal(getTwilightK(mTwilight));
        }
    }

    private synchronized void updateLiveDisplayInternal(float temperature) {
        updateColorTemperature(temperature);
        updateOutdoorMode();
        updateColorEnhancement();
        updateLowPowerMode();

        Slog.d(TAG, "lux=" + mCurrentLux + " night=" + (mTwilight != null && mTwilight.isNight()) +
                " temperature=" + temperature + " outdoor=" + mOutdoorMode +
                " enhance=" + mColorEnhancement + " cabc=" + mLowPower);
    }

    private synchronized void updateColorTemperature(float temperature) {
        if (temperature == mLastTemperature) {
            // no need to do anything here
            return;
        }

        mLastTemperature = temperature;

        float[] rgb = temperatureToRGB(temperature);
        Slog.d(TAG, "Adjust display temperature to " + temperature +
                "K [r=" + rgb[0] + " g=" + rgb[1] + " b=" + rgb[2] + "]");

        if (DisplayColorCalibration.isSupported()) {
            StringBuilder sb = new StringBuilder();
            sb.append((int)(rgb[0] * DisplayColorCalibration.getMaxValue())).append(" ");
            sb.append((int)(rgb[1] * DisplayColorCalibration.getMaxValue())).append(" ");
            sb.append((int)(rgb[2] * DisplayColorCalibration.getMaxValue()));
            DisplayColorCalibration.setColors(sb.toString());
        } else {

            if (temperature == OFF_TEMPERATURE) {
                setColorTransform(null);
                return;
            }
            setColorTransform(new float[] { rgb[0], 0, 0, 0,
                                            0, rgb[1], 0, 0,
                                            0, 0, rgb[2], 0,
                                            0, 0, 0, 1 });
        }
    }

    /**
     * Outdoor mode is optionally enabled when ambient lux > 10000 and it's daytime
     * Melt faces!
     *
     * TODO: Use the camera or RGB sensor to determine if it's really sunlight
     */
    private synchronized void updateOutdoorMode() {
        boolean enabled = (mMode == MODE_OUTDOOR) ||
                (mUseOutdoorMode && mMode == MODE_AUTO &&
                 mTwilight != null && !mTwilight.isNight() &&
                 mCurrentLux > DEFAULT_OUTDOOR_LUX);

        if (enabled == mOutdoorMode) {
            return;
        }

        SunlightEnhancement.setEnabled(enabled);
        mOutdoorMode = enabled;
    }

    /**
     * Color enhancement is optional, but can look bad with night mode
     */
    private synchronized void updateColorEnhancement() {
        boolean enabled = mUseColorEnhancement && !(mMode == MODE_NIGHT ||
                (mMode == MODE_AUTO && mTwilight != null && mTwilight.isNight()));

        if (enabled == mColorEnhancement) {
            return;
        }

        ColorEnhancement.setEnabled(enabled);
        mColorEnhancement = enabled;
    }

    /**
     * Adaptive backlight / low power mode. Turn it off when under very bright light.
     */
    private synchronized void updateLowPowerMode() {
        boolean enabled = mUseLowPower && mCurrentLux < DEFAULT_OUTDOOR_LUX;

        if (enabled == mLowPower) {
            return;
        }

        AdaptiveBacklight.setEnabled(enabled);
        mLowPower = enabled;
    }

    private static float sat(float v) {
        return MathUtils.constrain(v, 0.0f, 1.0f);
    }

    /**
     * Convert a color temperature value (in Kelvin) to a RGB units as floats.
     * This can be used in a transform matrix or hardware gamma control.
     * 
     * Algorithm by Tanner Helland, described here;
     * http://www.tannerhelland.com/4435/convert-temperature-rgb-algorithm-code/
     * 
     * @param tempK
     * @return
     */
    private static float[] temperatureToRGB(float tempK) {
        float[] rgb = new float[] { 1.0f, 1.0f, 1.0f };
        float k = MathUtils.constrain(tempK, 1000.0f, 40000.0f) / 100.0f;
        
        if (k <= 66.0f) {
            rgb[1] = sat(0.39008157876901960784f * (float)Math.log(k) - 0.63184144378862745098f);
        } else {
            float t = k - 60.0f;
            rgb[0] = sat(1.29293618606274509804f * (float)Math.pow(t, -0.1332047592f));
            rgb[1] = sat(1.12989086089529411765f * (float)Math.pow(t, -0.0755148492f));
        }
        
        if (k <= 19.0) {
            rgb[2] = 0.0f;
        } else if (k < 66.0f) {
            rgb[2] = sat(0.54320678911019607843f * (float)Math.log(k - 10.0f) - 1.19625408914f);
        }
        
        return rgb;
    }
    
    /**
     * Where is the sun anyway? This calculation determines day or night, and scales
     * the value around sunset/sunrise for a smooth transition.
     * 
     * @param now
     * @param lastSunset
     * @param nextSunrise
     * @return float between 1.0 and 2.0
     */
    private static float adj(long now, long lastSunset, long nextSunrise) {
        if (lastSunset < 0 || nextSunrise < 0
                || now < lastSunset || now > nextSunrise) {
            return 1.0f;
        }

        if (now < lastSunset + TWILIGHT_ADJUSTMENT_TIME) {
            return MathUtils.lerp(1.0f, 2.0f,
                    (float)(now - lastSunset) / TWILIGHT_ADJUSTMENT_TIME);
        }

        if (now > nextSunrise - TWILIGHT_ADJUSTMENT_TIME) {
            return MathUtils.lerp(1.0f, 2.0f,
                    (float)(nextSunrise - now) / TWILIGHT_ADJUSTMENT_TIME);
        }

        return 2.0f;
    }
    
    /**
     * Determine the color temperature we should use for the display based on
     * the position of the sun.
     * 
     * @param state
     * @return color temperature in Kelvin
     */
    private float getTwilightK(TwilightState state) {
        float adjustment = 1.0f;
        
        if (state != null) {
            final long now = System.currentTimeMillis();
            adjustment = adj(now, state.getYesterdaySunset(), state.getTodaySunrise()) *
                         adj(now, state.getTodaySunset(), state.getTomorrowSunrise());
        }
        
        return MathUtils.lerp(mDayTemperature, mNightTemperature, adjustment - 1.0f);
    }
    
    /**
     * Sets the surface flinger's color transformation as a 4x4 matrix. If the
     * matrix is null, color transformations are disabled.
     *
     * TODO: Integrate with hardware interfaces such as KCAL and PCC.
     * 
     * @param m the float array that holds the transformation matrix, or null to
     *            disable transformation
     */
    private static void setColorTransform(float[] m) {
        try {
            final IBinder flinger = ServiceManager.getService("SurfaceFlinger");
            if (flinger != null) {
                final Parcel data = Parcel.obtain();
                data.writeInterfaceToken("android.ui.ISurfaceComposer");
                if (m != null) {
                    data.writeInt(1);
                    for (int i = 0; i < 16; i++) {
                        data.writeFloat(m[i]);
                    }
                } else {
                    data.writeInt(0);
                }
                flinger.transact(1015, data, null, 0);
                data.recycle();
            }
        } catch (RemoteException ex) {
            Slog.e(TAG, "Failed to set color transform", ex);
        }
    }
}
