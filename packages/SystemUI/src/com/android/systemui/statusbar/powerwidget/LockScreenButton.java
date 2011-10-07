package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class LockScreenButton extends PowerButton {

    private static Boolean LOCK_SCREEN_STATE = null;

    public LockScreenButton() { mType = BUTTON_LOCKSCREEN; }

    @Override
    protected void updateState() {
        getState(mView.getContext());
        if (LOCK_SCREEN_STATE) {
            mIcon = R.drawable.stat_lock_screen_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_lock_screen_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        getState(context);
        Settings.System.putInt(context.getContentResolver(),
                    Settings.System.LOCKSCREEN_DISABLED, LOCK_SCREEN_STATE ? 1 : 0);
        update();
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.SECURITY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    private static boolean getState(Context context) {
        LOCK_SCREEN_STATE = (Settings.System.getInt(context.getContentResolver(),
                Settings.System.LOCKSCREEN_DISABLED, 0) == 0);
        return LOCK_SCREEN_STATE;
    }
}

