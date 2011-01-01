
package com.android.systemui.statusbar.widget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class AirplaneButton extends PowerButton {

    static AirplaneButton ownButton = null;

    public void updateState(Context context) {
        if (getState(context)) {
            currentIcon = R.drawable.stat_airplane_on;
            currentState = PowerButton.STATE_ENABLED;
        } else {
            currentIcon = R.drawable.stat_airplane_off;
            currentState = PowerButton.STATE_DISABLED;
        }

    }

    /**
     * Toggles the state of Airplane
     * 
     * @param context
     */
    public void toggleState(Context context) {
        boolean state = getState(context);
        Settings.System.putInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON,
                state ? 0 : 1);
        // notify change
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", state);
        context.sendBroadcast(intent);
    }

    /**
     * Gets the state of Airplane.
     * 
     * @param context
     * @return true if enabled.
     */
    private static boolean getState(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;
    }

    public static AirplaneButton getInstance() {
        if (ownButton == null)
            ownButton = new AirplaneButton();
        return ownButton;
    }
}
