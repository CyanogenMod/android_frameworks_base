package com.android.systemui.statusbar.powerwidget;

import android.content.ContentResolver;
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
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.LTE_MODE));
    }

    public LTEButton() { mType = BUTTON_LTE; }

    @Override
    protected void updateState(Context context) {
        ContentResolver resolver = context.getContentResolver();
        int network = getCurrentPreferredNetworkMode(context);
        switch(network) {
            case Phone.NT_MODE_GLOBAL:
/*
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
                mIcon = R.drawable.stat_lte_on;
                mState = STATE_ENABLED;
                Settings.System.putInt(resolver, Settings.System.LTE_MODE, 1);
                break;*/
            default:
                mIcon = R.drawable.stat_lte_off;
                mState = STATE_DISABLED;
                Settings.System.putInt(resolver, Settings.System.LTE_MODE, 0);
                break;
        }
    }

    @Override
    protected void toggleState(Context context) {
        TelephonyManager tm = (TelephonyManager)
            context.getSystemService(Context.TELEPHONY_SERVICE);
        int network = getCurrentPreferredNetworkMode(context);
        ContentResolver resolver = context.getContentResolver();
        if (Phone.NT_MODE_GLOBAL == network/* ||
              Phone.NT_MODE_LTE_GSM_WCDMA == network*/) {
            //tm.toggleLTE(false);                                   // TODO: ******* Disabled for now ************
            mState = STATE_DISABLED;
            Settings.System.putInt(resolver, Settings.System.LTE_MODE, 0);
        } else if (Phone.NT_MODE_CDMA == network/* ||
                     tm.getLteOnGsmMode() != 0*/) {
            //tm.toggleLTE(true);                                    // TODO: ******* Disabled for now ************
            mState = STATE_ENABLED;
            Settings.System.putInt(resolver, Settings.System.LTE_MODE, 1);
        }
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

    private static int getCurrentPreferredNetworkMode(Context context) {
        int network = -1;
        try {
            network = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return network;
    }
}
