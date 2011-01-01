
package com.android.systemui.statusbar.widget;

import com.android.systemui.R;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

public class SleepButton extends PowerButton {
    static SleepButton ownButton;

    public void updateState(Context context) {
        currentIcon = R.drawable.stat_sleep;
        currentState = STATE_DISABLED;
    }

    public void toggleState(Context context) {
        PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mPowerManager.goToSleep(SystemClock.uptimeMillis() + 1);
    }

    public static SleepButton getInstance() {
        if (ownButton == null)
            ownButton = new SleepButton();

        return ownButton;
    }
}
