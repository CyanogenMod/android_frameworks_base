package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class FlashlightButton extends PowerButton {
    private static final IntentFilter STATE_FILTER =
            new IntentFilter("net.cactii.flash2.TORCH_STATE_CHANGED");
    private boolean mActive = false;

    public FlashlightButton() { mType = BUTTON_FLASHLIGHT; }

    @Override
    protected void updateState(Context context) {
        if (mActive) {
            mIcon = R.drawable.stat_flashlight_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_flashlight_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState(Context context) {
        boolean bright = Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.EXPANDED_FLASH_MODE, 0, UserHandle.USER_CURRENT) == 1;
        Intent i = new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT");
        i.putExtra("bright", bright);
        context.sendBroadcast(i);
    }

    @Override
    protected boolean handleLongClick(Context context) {
        // it may be better to make an Intent action for the Torch
        // we may want to look at that option later
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("net.cactii.flash2", "net.cactii.flash2.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        return STATE_FILTER;
    }

    @Override
    protected void onReceive(Context context, Intent intent) {
        mActive = intent.getIntExtra("state", 0) != 0;
    }
}
