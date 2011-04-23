package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.provider.Settings;

import com.android.internal.telephony.TelephonyIntents;

public class MobileDataButton extends PowerButton {

    public static final String MOBILE_DATA_CHANGED = "com.android.internal.telephony.MOBILE_DATA_CHANGED";

    public static boolean STATE_CHANGE_REQUEST = false;

    public MobileDataButton() { mType = BUTTON_MOBILEDATA; }

    @Override
    protected void updateState() {
        if (STATE_CHANGE_REQUEST) {
            mIcon = R.drawable.stat_data_on;
            mState = STATE_INTERMEDIATE;
        } else  if (getDataState(mView.getContext())) {
            mIcon = R.drawable.stat_data_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_data_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        boolean enabled = getDataState(context);

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (enabled) {
            cm.setMobileDataEnabled(false);
        } else {
            cm.setMobileDataEnabled(true);
        }
    }

    @Override
    protected boolean handleLongClick() {
        // it may be better to make an Intent action for this or find the appropriate one
        // we may want to look at that option later
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.phone", "com.android.phone.Settings");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        return filter;
    }

    private static boolean getDataRomingEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.DATA_ROAMING,0) > 0;
    }

    private static boolean getDataState(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();
    }

    public void networkModeChanged(Context context, int networkMode) {
        if (STATE_CHANGE_REQUEST) {
            ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.setMobileDataEnabled(true);
            STATE_CHANGE_REQUEST=false;
        }
    }

}
