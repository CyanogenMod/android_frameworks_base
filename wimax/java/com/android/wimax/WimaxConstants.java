package com.android.wimax;

import android.content.Context;

/* @hide */
public class WimaxConstants {

    public static final int WIMAX_ENABLED_STATE_DISABLING = 0;

    public static final int WIMAX_ENABLED_STATE_DISABLED = 1;

    public static final int WIMAX_ENABLED_STATE_ENABLING = 2;

    public static final int WIMAX_ENABLED_STATE_ENABLED = 3;

    public static final int WIMAX_ENABLED_STATE_UNKNOWN = 4;

    public static final String WIMAX_ENABLED_CHANGED_ACTION = "com.htc.net.wimax.WIMAX_ENABLED_CHANGED";

    public static final String CURRENT_WIMAX_ENABLED_STATE = "curWimaxEnabledState";

    public static final String PREVIOUS_WIMAX_ENABLED_STATE = "preWimaxEnabledState";

    public static final String NETWORK_STATE_CHANGED_ACTION = "com.htc.net.wimax.STATE_CHANGE";

    public static final String SCAN_RESULTS_AVAILABLE_ACTION = "com.htc.net.wimax.SCAN_RESULTS_AVAILABLE";
    
    public static final String RSSI_CHANGED_ACTION = "com.htc.net.wimax.RSSI_CHANGED";
    
    public static final String EXTRA_NETWORK_INFO = "networkInfo";

    public static final String EXTRA_NEW_RSSI_LEVEL = "newRssiLevel";

    public static final String EXTRA_NEW_STATE = "newState";

    public static boolean isWimaxSupported(Context context) {
        return context.getSystemService(Context.WIFI_SERVICE) != null;
    }
}
