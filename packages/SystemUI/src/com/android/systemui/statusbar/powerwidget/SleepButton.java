package com.android.systemui.statusbar.powerwidget;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;

import com.android.systemui.R;

public class SleepButton extends PowerButton {
    public SleepButton() { mType = BUTTON_SLEEP; }

    @Override
    protected void updateState(Context context) {
        mIcon = R.drawable.stat_sleep;
        mState = STATE_DISABLED;
    }

    @Override
    protected void toggleState(Context context) {
        PowerManager pm = (PowerManager)
                context.getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis());
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.DISPLAY_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }
}
