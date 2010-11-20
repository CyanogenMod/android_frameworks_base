package com.android.server.status.widget;

import com.android.internal.R;
import com.android.server.status.widget.PowerButton;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.provider.Settings;

public class SyncButton extends PowerButton {

    Context mContext;

    static SyncButton ownButton=null;

    /**
     * Toggle auto-sync
     * 
     * @param context
     */
    public void toggleState(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context
        .getSystemService(Context.CONNECTIVITY_SERVICE);
        boolean backgroundData = getBackgroundDataState(context);
        boolean sync = ContentResolver.getMasterSyncAutomatically();

        // four cases to handle:
        // setting toggled from off to on:
        // 1. background data was off, sync was off: turn on both
        if (!backgroundData && !sync) {
            connManager.setBackgroundDataSetting(true);
            ContentResolver.setMasterSyncAutomatically(true);
        }

        // 2. background data was off, sync was on: turn on background data
        if (!backgroundData && sync) {
            connManager.setBackgroundDataSetting(true);
        }

        // 3. background data was on, sync was off: turn on sync
        if (backgroundData && !sync) {
            ContentResolver.setMasterSyncAutomatically(true);
        }

        // setting toggled from on to off:
        // 4. background data was on, sync was on: turn off sync
        if (backgroundData && sync) {
            ContentResolver.setMasterSyncAutomatically(false);
        }
    }

    public void toggleState(Context context, int newState) {
        if(getSync(context) && newState==PowerButton.STATE_DISABLED) {
            toggleState(context);
        } else if(!getSync(context) && newState==PowerButton.STATE_ENABLED) {
            toggleState(context);
        }
    }

    /**
     * Gets the state of background data.
     * 
     * @param context
     * @return true if enabled
     */
    private static boolean getBackgroundDataState(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context
        .getSystemService(Context.CONNECTIVITY_SERVICE);
        return connManager.getBackgroundDataSetting();
    }

    /**
     * Gets the state of auto-sync.
     * 
     * @param context
     * @return true if enabled
     */
    private static boolean getSync(Context context) {
        boolean backgroundData = getBackgroundDataState(context);
        boolean sync = ContentResolver.getMasterSyncAutomatically();
        return backgroundData && sync;
    }



    public void updateState(Context context) {
        mContext = context;
        boolean useCustomExp = Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.NOTIF_EXPANDED_BAR_CUSTOM, 0) == 1;

        if (getSync(context)) {
            if (useCustomExp) {
                currentIcon = R.drawable.stat_sync_on_cust;
            } else {
                currentIcon = R.drawable.stat_sync_on;
            }
            currentState = PowerButton.STATE_ENABLED;
        } else {
            if (useCustomExp) {
                currentIcon = R.drawable.stat_sync_off_cust;
            } else {
                currentIcon = R.drawable.stat_sync_off;
            }
            currentState = PowerButton.STATE_DISABLED;
        }
    }

    public static SyncButton getInstance() {
        if (ownButton == null) ownButton = new SyncButton();
        return ownButton;
    }

    @Override
    void initButton(int poisition) {
    }

}
