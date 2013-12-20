package com.android.systemui.statusbar.powerwidget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

public class LTEButton extends PowerButton{

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.Global.getUriFor(Settings.Global.PREFERRED_NETWORK_MODE));
    }

    public LTEButton() { mType = BUTTON_LTE; }

    @Override
    protected void updateState(Context context) {
        TelephonyManager tm = (TelephonyManager)
            context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getLteState() == 1) {
            mIcon = R.drawable.ic_qs_lte_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.ic_qs_lte_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState(Context context) {
        TelephonyManager tm = (TelephonyManager)
            context.getSystemService(Context.TELEPHONY_SERVICE);
        tm.toggleLTE();
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.phone", "com.android.phone.Settings");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }
}
