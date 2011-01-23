package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.os.PowerManager;
import android.os.SystemClock;

public class SleepButton extends PowerButton {
    public SleepButton() { mType = BUTTON_SLEEP; }

    @Override
    protected void updateState() {
        mIcon = R.drawable.stat_sleep;
        mState = STATE_DISABLED;
    }

    @Override
    protected void toggleState() {
        PowerManager pm = (PowerManager)mView.getContext()
                .getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis() + 1);
    }
}
