package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class ScreenTimeoutTile extends QuickSettingsTile {

    // timeout values
    private static final int SCREEN_TIMEOUT_MIN    =  15000;
    private static final int SCREEN_TIMEOUT_LOW    =  30000;
    private static final int SCREEN_TIMEOUT_NORMAL =  60000;
    private static final int SCREEN_TIMEOUT_HIGH   = 120000;
    private static final int SCREEN_TIMEOUT_MAX    = 300000;

    // cm modes
    private static final int CM_MODE_15_60_300 = 0;
    private static final int CM_MODE_30_120_300 = 1;

    public ScreenTimeoutTile(Context context,
            LayoutInflater inflater, QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateTileState();

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                applyTimeoutChanges();
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT)
                , this);
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        applyTimeoutChanges();
    }

    void applyTimeoutChanges() {
        updateTileState();
        updateQuickSettings();
    }

    protected void updateTileState() {
        int timeout = getScreenTimeout();
        mLabel = makeTimeoutSummaryString(mContext, timeout);
        mDrawable = R.drawable.ic_qs_screen_timeout_off;

        /* TODO: Determine if we need an on and off state
        if (timeout <= SCREEN_TIMEOUT_LOW) {
            mDrawable = R.drawable.ic_qs_screen_timeout_off;
        } else if (timeout <= SCREEN_TIMEOUT_HIGH) {
            mDrawable = R.drawable.ic_qs_screen_timeout_off;
        } else {
            mDrawable = R.drawable.ic_qs_screen_timeout_on;
        }
        */
    }

    protected void toggleState() {
        int screenTimeout = getScreenTimeout();
        int currentMode = getCurrentCMMode();

        if (screenTimeout < SCREEN_TIMEOUT_MIN) {
            if (currentMode == CM_MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_MIN;
            } else {
                screenTimeout = SCREEN_TIMEOUT_LOW;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_LOW) {
            if (currentMode == CM_MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_NORMAL;
            } else {
                screenTimeout = SCREEN_TIMEOUT_LOW;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_NORMAL) {
            if (currentMode == CM_MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_NORMAL;
            } else {
                screenTimeout = SCREEN_TIMEOUT_HIGH;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_HIGH) {
            if (currentMode == CM_MODE_15_60_300) {
                screenTimeout = SCREEN_TIMEOUT_MAX;
            } else {
                screenTimeout = SCREEN_TIMEOUT_HIGH;
            }
        } else if (screenTimeout < SCREEN_TIMEOUT_MAX) {
            screenTimeout = SCREEN_TIMEOUT_MAX;
        } else if (currentMode == CM_MODE_30_120_300) {
            screenTimeout = SCREEN_TIMEOUT_LOW;
        } else {
            screenTimeout = SCREEN_TIMEOUT_MIN;
        }

        Settings.System.putInt(
                mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, screenTimeout);
    }

    private String makeTimeoutSummaryString(Context context, int timeout) {
        Resources res = context.getResources();
        int resId;

        /* ms -> seconds */
        timeout /= 1000;

        if (timeout >= 60 && timeout % 60 == 0) {
            /* seconds -> minutes */
            timeout /= 60;
            if (timeout >= 60 && timeout % 60 == 0) {
                /* minutes -> hours */
                timeout /= 60;
                resId = timeout == 1
                        ? com.android.internal.R.string.hour
                        : com.android.internal.R.string.hours;
            } else {
                resId = timeout == 1
                        ? com.android.internal.R.string.minute
                        : com.android.internal.R.string.minutes;
            }
        } else {
            resId = timeout == 1
                    ? com.android.internal.R.string.second
                    : com.android.internal.R.string.seconds;
        }

        return res.getString(R.string.quick_settings_screen_timeout_summary,
                timeout, res.getString(resId));
    }

    private int getScreenTimeout() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 0);
    }

    private int getCurrentCMMode() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANDED_SCREENTIMEOUT_MODE, CM_MODE_15_60_300);
    }
}
