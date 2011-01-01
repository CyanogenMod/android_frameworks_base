
package com.android.systemui.statusbar.widget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class FlashlightButton extends PowerButton {

    static FlashlightButton ownButton;

    public void updateState(Context context) {
        boolean enabled = Settings.System.getInt(context.getContentResolver(), Settings.System.TORCH_STATE, 0) == 1;
        if (enabled) {
            currentIcon = R.drawable.stat_flashlight_on;
            currentState = STATE_ENABLED;
        } else {
            currentIcon = R.drawable.stat_flashlight_off;
            currentState = STATE_DISABLED;
        }
    }

    public void toggleState(Context context) {
        boolean bright = Settings.System.getInt(context.getContentResolver(),
                Settings.System.EXPANDED_FLASH_MODE, 0) == 1;
        Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
        i.putExtra("bright", bright);
        context.sendBroadcast(new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT"));
    }

    public static FlashlightButton getInstance() {
        if (ownButton == null)
            ownButton = new FlashlightButton();
        return ownButton;
    }

}
