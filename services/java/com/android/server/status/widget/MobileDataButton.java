package com.android.server.status.widget;

import com.android.internal.R;
import com.android.server.status.widget.PowerButton;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.provider.Settings;

public class MobileDataButton extends PowerButton {

    Context mContext;

    public static final String MOBILE_DATA_CHANGED = "com.android.internal.telephony.MOBILE_DATA_CHANGED";

    static MobileDataButton ownButton = null;

    static boolean stateChangeRequest = false;

    public static boolean getDataRomingEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.DATA_ROAMING,0) > 0;
    }

    /**
     * Gets the state of data
     *
     * @return true if enabled.
     */
    private static boolean getDataState(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();
    }

    /**
     * Toggles the state of data.
     *
     */
    @Override
    public void toggleState(Context context) {
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
    public void updateState(Context context) {
        mContext = context;
        boolean useCustomExp = Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.NOTIF_EXPANDED_BAR_CUSTOM, 0) == 1;

        if (stateChangeRequest) {
            if (useCustomExp) {
                currentIcon = R.drawable.stat_data_on_cust;
            } else {
                currentIcon = R.drawable.stat_data_on;
            }
            currentState = PowerButton.STATE_INTERMEDIATE;
        } else  if (getDataState(context)) {
            if (useCustomExp) {
                currentIcon = R.drawable.stat_data_on_cust;
            } else {
                currentIcon = R.drawable.stat_data_on;
            }
            currentState = PowerButton.STATE_ENABLED;
        } else {
            if (useCustomExp) {
                currentIcon = R.drawable.stat_data_off_cust;
            } else {
                currentIcon = R.drawable.stat_data_off;
            }
            currentState = PowerButton.STATE_DISABLED;
        }
    }

    public static MobileDataButton getInstance() {
        if (ownButton == null) ownButton = new MobileDataButton();

        return ownButton;
    }

    @Override
    void initButton(int position) {
    }

    public void networkModeChanged(Context context, int networkMode) {
        if (stateChangeRequest) {
            ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.setMobileDataEnabled(true);
            stateChangeRequest=false;
        }
    }

}
