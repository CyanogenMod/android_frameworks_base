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
    protected void updateState() {
        ContentResolver resolver = mView.getContext().getContentResolver();
        int network = getCurrentPreferredNetworkMode(mView.getContext());
        switch(network) {
            case Phone.NT_MODE_GLOBAL:
                mIcon = R.drawable.stat_lte_on;
                mState = STATE_ENABLED;
                Settings.System.putInt(resolver, Settings.System.LTE_MODE, 1);
                break;
            case Phone.NT_MODE_CDMA:
                mIcon = R.drawable.stat_lte_off;
                mState = STATE_DISABLED;
                Settings.System.putInt(resolver, Settings.System.LTE_MODE, 0);
                break;
        }
    }

    @Override
    protected void toggleState() {
        TelephonyManager tm = (TelephonyManager) mView.getContext()
            .getSystemService(Context.TELEPHONY_SERVICE);
        int network = getCurrentPreferredNetworkMode(mView.getContext());
        ContentResolver resolver = mView.getContext().getContentResolver();
        if (Phone.NT_MODE_GLOBAL == network) {
            tm.toggleLTE(false);
            mState = STATE_DISABLED;
            Settings.System.putInt(resolver, Settings.System.LTE_MODE, 0);
        } else if (Phone.NT_MODE_CDMA == network) {
            tm.toggleLTE(true);
            mState = STATE_ENABLED;
            Settings.System.putInt(resolver, Settings.System.LTE_MODE, 1);
        }
    }

    @Override
    protected boolean handleLongClick() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.phone", "com.android.phone.Settings");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
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
                    Settings.Secure.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        return network;
    }
}