
package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Power;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.ListPreferenceMultiSelect;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BrightnessButton extends PowerButton {

    private static final String TAG = "BrightnessButton";

    /**
     * Minimum and maximum brightnesses. Don't go to 0 since that makes the
     * display unusable
     */
    private static final int MIN_BACKLIGHT = Power.BRIGHTNESS_DIM + 10;
    private static final int MAX_BACKLIGHT = Power.BRIGHTNESS_ON;

    // Auto-backlight level
    private static final int AUTO_BACKLIGHT = -1;
    // Mid-range brightness values + thresholds
    private static final int LOW_BACKLIGHT = (int) (MAX_BACKLIGHT * 0.25f);
    private static final int MID_BACKLIGHT = (int) (MAX_BACKLIGHT * 0.5f);
    private static final int HIGH_BACKLIGHT = (int) (MAX_BACKLIGHT * 0.75f);

    // Defaults for now. MIN_BACKLIGHT will be replaced later
    private static final int[] BACKLIGHTS = new int[] {
            AUTO_BACKLIGHT, MIN_BACKLIGHT, LOW_BACKLIGHT, MID_BACKLIGHT, HIGH_BACKLIGHT,
            MAX_BACKLIGHT
    };

    private static final Uri BRIGHTNESS_URI = Settings.System
            .getUriFor(Settings.System.SCREEN_BRIGHTNESS);
    private static final Uri BRIGHTNESS_MODE_URI = Settings.System
            .getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE);
    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(BRIGHTNESS_URI);
        OBSERVED_URIS.add(BRIGHTNESS_MODE_URI);
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.LIGHT_SENSOR_CUSTOM));
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.LIGHT_SCREEN_DIM));
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.EXPANDED_BRIGHTNESS_MODE));
    }

    private boolean mAutoBrightnessSupported = false;

    private boolean mAutoBrightness = false;

    private int mCurrentBrightness;

    private int mCurrentBacklightIndex = 0;

    private int[] mBacklightValues = new int[] {
            0, 1, 2, 3, 4, 5
    };

    public BrightnessButton() {
        mType = BUTTON_BRIGHTNESS;
    }

    @Override
    protected void setupButton(View view) {
        super.setupButton(view);
        if (mView != null) {
            Context context = mView.getContext();
            mAutoBrightnessSupported = context.getResources().getBoolean(
                    com.android.internal.R.bool.config_automatic_brightness_available);
            updateSettings(context.getContentResolver());
        }
    }

    @Override
    protected void updateState(Context context) {
        if (mAutoBrightness) {
            mIcon = R.drawable.stat_brightness_auto;
            mState = STATE_ENABLED;
        } else if (mCurrentBrightness <= LOW_BACKLIGHT) {
            mIcon = R.drawable.stat_brightness_off;
            mState = STATE_DISABLED;
        } else if (mCurrentBrightness <= MID_BACKLIGHT) {
            mIcon = R.drawable.stat_brightness_mid;
            mState = STATE_INTERMEDIATE;
        } else {
            mIcon = R.drawable.stat_brightness_on;
            mState = STATE_ENABLED;
        }
    }

    @Override
    protected void toggleState(Context context) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        ContentResolver resolver = context.getContentResolver();

        mCurrentBacklightIndex++;
        if (mCurrentBacklightIndex > mBacklightValues.length - 1) {
            mCurrentBacklightIndex = 0;
        }

        int backlightIndex = mBacklightValues[mCurrentBacklightIndex];
        int brightness = BACKLIGHTS[backlightIndex];

        if (brightness == AUTO_BACKLIGHT) {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        } else {
            if (mAutoBrightnessSupported) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            }
            power.setBacklightBrightness(brightness);
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
        }
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    @Override
    protected void onChangeUri(ContentResolver resolver, Uri uri) {
        if (BRIGHTNESS_URI.equals(uri)) {
            mCurrentBrightness = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS, 0);
        } else if (BRIGHTNESS_MODE_URI.equals(uri)) {
            mAutoBrightness = (Settings.System.getInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        } else {
            updateSettings(resolver);
        }
    }

    private void updateSettings(ContentResolver resolver) {
        boolean lightSensorCustom = (Settings.System.getInt(resolver,
                Settings.System.LIGHT_SENSOR_CUSTOM, 0) != 0);
        if (lightSensorCustom) {
            BACKLIGHTS[1] = Settings.System.getInt(resolver, Settings.System.LIGHT_SCREEN_DIM,
                    MIN_BACKLIGHT);
        } else {
            BACKLIGHTS[1] = MIN_BACKLIGHT;
        }

        String[] modes = ListPreferenceMultiSelect.parseStoredValue(Settings.System.getString(
                resolver, Settings.System.EXPANDED_BRIGHTNESS_MODE));
        if (modes == null || modes.length == 0) {
            mBacklightValues = new int[] {
                    0, 1, 2, 3, 4, 5
            };
        } else {
            mBacklightValues = new int[modes.length];
            for (int i = 0; i < modes.length; i++) {
                mBacklightValues[i] = Integer.valueOf(modes[i]);
            }
        }

        mAutoBrightness = (Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        if (mAutoBrightness) {
            mCurrentBrightness = AUTO_BACKLIGHT;
        } else {
            mCurrentBrightness = Settings.System.getInt(resolver,
                    Settings.System.SCREEN_BRIGHTNESS, -1);
            for (int i = 0; i < BACKLIGHTS.length; i++) {
                if (mCurrentBrightness == BACKLIGHTS[i]) {
                    mCurrentBacklightIndex = i;
                    break;
                }
            }
        }
    }
}
