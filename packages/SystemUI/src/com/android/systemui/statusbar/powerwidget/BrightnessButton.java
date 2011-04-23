package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IPowerManager;
import android.os.Power;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class BrightnessButton extends PowerButton {

    /**
     * Minimum and maximum brightnesses. Don't go to 0 since that makes the
     * display unusable
     */
    private static final int MIN_BACKLIGHT = Power.BRIGHTNESS_DIM + 10;
    private static final int MAX_BACKLIGHT = Power.BRIGHTNESS_ON;
    // Auto-backlight level
    private static final int AUTO_BACKLIGHT = -1;
    // Mid-range brightness values + thresholds
    private static final int DEFAULT_BACKLIGHT = (int) (android.os.Power.BRIGHTNESS_ON * 0.4f);
    private static final int LOW_BACKLIGHT = (int) (android.os.Power.BRIGHTNESS_ON * 0.25f);
    private static final int MID_BACKLIGHT = (int) (android.os.Power.BRIGHTNESS_ON * 0.5f);
    private static final int HIGH_BACKLIGHT = (int) (android.os.Power.BRIGHTNESS_ON * 0.75f);

    // whether or not backlight is supported
    private static Boolean SUPPORTS_AUTO_BACKLIGHT=null;

    // CM modes of operation
    private static final int CM_MODE_AUTO_MIN_DEF_MAX = 0;
    private static final int CM_MODE_AUTO_MIN_LOW_MID_HIGH_MAX = 1;
    private static final int CM_MODE_AUTO_LOW_MAX = 2;
    private static final int CM_MODE_MIN_MAX = 3;

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS));
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE));
    }

    public BrightnessButton() { mType = BUTTON_BRIGHTNESS; }

    @Override
    protected void updateState() {
        Context context = mView.getContext();
        if (isBrightnessSetToAutomatic(context)) {
            mIcon = R.drawable.stat_brightness_auto;
            mState = STATE_ENABLED;
        } else {
            switch(getBrightnessState(context)) {
                case STATE_ENABLED:
                    mIcon = R.drawable.stat_brightness_on;
                    mState = STATE_ENABLED;
                    break;
                case STATE_TURNING_ON:
                    mIcon = R.drawable.stat_brightness_on;
                    mState = STATE_INTERMEDIATE;
                    break;
                case STATE_TURNING_OFF:
                    mIcon = R.drawable.stat_brightness_off;
                    mState = STATE_INTERMEDIATE;
                    break;
                default:
                    mIcon = R.drawable.stat_brightness_off;
                    mState = STATE_DISABLED;
                    break;
            }
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(ServiceManager
                    .getService("power"));
            if (power != null) {
                int brightness = getNextBrightnessValue(context);
                ContentResolver contentResolver = context.getContentResolver();
                if (brightness == AUTO_BACKLIGHT) {
                    Settings.System.putInt(contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                } else {
                    if (isAutomaticModeSupported(context)) {
                        Settings.System.putInt(contentResolver,
                                Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    }
                    power.setBacklightBrightness(brightness);
                    Settings.System.putInt(contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS, brightness);
                }
            }
        } catch (RemoteException e) {
            Log.d("PowerWidget", "toggleBrightness: " + e);
        }
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private static int getMinBacklight(Context context) {
        if (Settings.System.getInt(context.getContentResolver(),
                Settings.System.LIGHT_SENSOR_CUSTOM, 0) != 0) {
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.LIGHT_SCREEN_DIM, MIN_BACKLIGHT);
        } else {
            return MIN_BACKLIGHT;
        }
    }

    private static int getNextBrightnessValue(Context context) {
        int brightness = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,0);
        int currentMode = getCurrentCMMode(context);

        if (isAutomaticModeSupported(context) && isBrightnessSetToAutomatic(context)) {
            if (currentMode == CM_MODE_AUTO_LOW_MAX) {
                return LOW_BACKLIGHT;
            } else {
                return getMinBacklight(context);
            }
        } else if (brightness < LOW_BACKLIGHT) {
            if (currentMode == CM_MODE_AUTO_LOW_MAX) {
                return LOW_BACKLIGHT;
            } else if (currentMode == CM_MODE_MIN_MAX) {
                return MAX_BACKLIGHT;
            } else {
                return DEFAULT_BACKLIGHT;
            }
        } else if (brightness < DEFAULT_BACKLIGHT) {
            if (currentMode == CM_MODE_AUTO_MIN_DEF_MAX) {
                return DEFAULT_BACKLIGHT;
            } else if (currentMode == CM_MODE_AUTO_LOW_MAX || currentMode == CM_MODE_MIN_MAX) {
                return MAX_BACKLIGHT;
            } else {
                return MID_BACKLIGHT;
            }
        } else if (brightness < MID_BACKLIGHT) {
            if (currentMode == CM_MODE_AUTO_MIN_LOW_MID_HIGH_MAX) {
                return MID_BACKLIGHT;
            } else {
                return MAX_BACKLIGHT;
            }
        } else if (brightness < HIGH_BACKLIGHT) {
            if (currentMode == CM_MODE_AUTO_MIN_LOW_MID_HIGH_MAX) {
                return HIGH_BACKLIGHT;
            } else {
                return MAX_BACKLIGHT;
            }
        } else if (brightness < MAX_BACKLIGHT) {
            return MAX_BACKLIGHT;
        } else if (isAutomaticModeSupported(context) && currentMode != CM_MODE_MIN_MAX) {
            return AUTO_BACKLIGHT;
        } else if (currentMode == CM_MODE_AUTO_LOW_MAX) {
            return LOW_BACKLIGHT;
        } else {
            return getMinBacklight(context);
        }
    }

    private static int getBrightnessState(Context context) {
        int brightness = Settings.System.getInt(context.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,0);

        int currentMode = getCurrentCMMode(context);

        if (brightness < LOW_BACKLIGHT) {
            return PowerButton.STATE_DISABLED;
        } else if (brightness < DEFAULT_BACKLIGHT) {
            return PowerButton.STATE_DISABLED;
        } else if (brightness < MID_BACKLIGHT) {
            if (currentMode == CM_MODE_AUTO_MIN_LOW_MID_HIGH_MAX) {
                return PowerButton.STATE_DISABLED;
            } else {
                return PowerButton.STATE_TURNING_OFF;
            }
        } else if (brightness < HIGH_BACKLIGHT) {
            if (currentMode == CM_MODE_AUTO_MIN_LOW_MID_HIGH_MAX) {
                return PowerButton.STATE_TURNING_OFF;
            } else {
                return PowerButton.STATE_TURNING_ON;
            }
        } else if (brightness < MAX_BACKLIGHT) {
            return PowerButton.STATE_TURNING_ON;
        } else {
            return PowerButton.STATE_ENABLED;
        }
    }

    private static boolean isAutomaticModeSupported(Context context) {
        if (SUPPORTS_AUTO_BACKLIGHT == null) {
            if (context.getResources().getBoolean(
                    com.android.internal.R.bool.config_automatic_brightness_available)) {
                SUPPORTS_AUTO_BACKLIGHT=true;
            } else {
                SUPPORTS_AUTO_BACKLIGHT=false;
            }
        }

        return SUPPORTS_AUTO_BACKLIGHT;
    }

    private static boolean isBrightnessSetToAutomatic(Context context) {
        try {
            IPowerManager power = IPowerManager.Stub.asInterface(ServiceManager
                    .getService("power"));
            if (power != null) {
                int brightnessMode = Settings.System.getInt(context
                        .getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE);
                return brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            }
        } catch (Exception e) {
            Log.d("PowerWidget", "getBrightnessMode: " + e);
        }

        return false;
    }

    private static int getCurrentCMMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.EXPANDED_BRIGHTNESS_MODE,
                CM_MODE_AUTO_MIN_DEF_MAX);
    }
}
