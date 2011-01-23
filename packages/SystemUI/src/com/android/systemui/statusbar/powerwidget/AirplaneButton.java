package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class AirplaneButton extends PowerButton {

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.AIRPLANE_MODE_ON));
    }

    public AirplaneButton() { mType = BUTTON_AIRPLANE; }

    @Override
    protected void updateState() {
        if (getState(mView.getContext())) {
            mIcon = R.drawable.stat_airplane_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_airplane_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        boolean state = getState(context);
        Settings.System.putInt(context.getContentResolver(),
            Settings.System.AIRPLANE_MODE_ON, state ? 0 : 1);
        // notify change
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", state);
        context.sendBroadcast(intent);
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private static boolean getState(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                 Settings.System.AIRPLANE_MODE_ON,0) == 1;
    }
}

