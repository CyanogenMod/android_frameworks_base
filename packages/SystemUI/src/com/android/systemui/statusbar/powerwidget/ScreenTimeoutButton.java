package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class ScreenTimeoutButton extends PowerButton {

    // timeout values
    private static final int SCREEN_TIMEOUT_MIN    =  15000;
    private static final int SCREEN_TIMEOUT_LOW    =  30000;
    private static final int SCREEN_TIMEOUT_NORMAL =  60000;
    private static final int SCREEN_TIMEOUT_HIGH   = 120000;
    private static final int SCREEN_TIMEOUT_MAX    = 300000;

    // cm modes
    private static final int CM_MODE_15_60_300 = 0;
    private static final int CM_MODE_30_120_300 = 1;

    private Toast mToast = null;

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT));
    }

    public ScreenTimeoutButton() { mType = BUTTON_SCREENTIMEOUT; }

    @Override
    protected void setupButton(View view) {
        super.setupButton(view);
        if (view == null && mToast != null) {
            mToast.cancel();
            mToast = null;
        }
    }

    @Override
    protected void updateState(Context context) {
        int timeout = getScreenTimeout(context);

        if (timeout <= SCREEN_TIMEOUT_LOW) {
            mIcon = R.drawable.stat_screen_timeout_off;
            mState = STATE_DISABLED;
        } else if (timeout <= SCREEN_TIMEOUT_HIGH) {
            mIcon = R.drawable.stat_screen_timeout_off;
            mState = STATE_INTERMEDIATE;
        } else {
            mIcon = R.drawable.stat_screen_timeout_on;
            mState = STATE_ENABLED;
        }
    }

    @Override
    protected void toggleState(Context context) {
        int screenTimeout = getScreenTimeout(context);
        int currentMode = getCurrentCMMode(context);

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

        Settings.System.putIntForUser(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, screenTimeout, UserHandle.USER_CURRENT);

        // cancel any previous toast
        if (mToast != null) {
            mToast.cancel();
        }

        // inform users of how long the timeout is now
        final String toast = makeTimeoutToastString(context, screenTimeout);
        mToast = Toast.makeText(context, toast, Toast.LENGTH_LONG);
        mToast.setGravity(Gravity.CENTER, mToast.getXOffset() / 2, mToast.getYOffset() / 2);
        mToast.show();
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    private String makeTimeoutToastString(Context context, int timeout) {
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

        return res.getString(R.string.powerwidget_screen_timeout_toast,
                timeout, res.getString(resId));
    }

    private static int getScreenTimeout(Context context) {
        return Settings.System.getIntForUser(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 0, UserHandle.USER_CURRENT);
    }

    private static int getCurrentCMMode(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.EXPANDED_SCREENTIMEOUT_MODE,
                CM_MODE_15_60_300, UserHandle.USER_CURRENT);
    }
}


