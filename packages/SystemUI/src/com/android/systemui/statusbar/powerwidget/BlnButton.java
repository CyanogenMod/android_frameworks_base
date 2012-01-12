package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class BlnButton extends PowerButton {

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.USE_BUTTONS));
    }

    public BlnButton() { mType = BUTTON_BLN; }

    @Override
    protected void updateState() {
        if (getBlnState(mView.getContext()) == 1) {
            mIcon = R.drawable.stat_bln_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_bln_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        if(getBlnState(context) == 0) {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.USE_BUTTONS, 1);
        } else {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.USE_BUTTONS, 0);
        }
    }


    @Override
    protected boolean handleLongClick() {
	// we may want to look at that option later, maybe an Intent-Action would better      
	Intent intent = new Intent();
	intent.setClassName("com.cyanogenmod.cmparts", "com.cyanogenmod.cmparts.activities.led.AdvancedActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private static int getBlnState(Context context) {
        return Settings.System.getInt(
                context.getContentResolver(),
                Settings.System.USE_BUTTONS, 0);
    }
}
