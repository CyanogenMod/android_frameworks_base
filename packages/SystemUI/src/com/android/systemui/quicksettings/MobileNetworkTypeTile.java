package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

/*
 * This Quicksettings works as the toggle in Power Widget, so it's not functional on CM10.1
 *
 * This must be fixed before adding it to the quicksettings panel
 */

public class MobileNetworkTypeTile extends QuickSettingsTile {

    private static final String TAG = "NetworkModeQuickSettings";

 // retrieved from Phone.apk
    public static final String ACTION_NETWORK_MODE_CHANGED = "com.android.internal.telephony.NETWORK_MODE_CHANGED";
    public static final String ACTION_REQUEST_NETWORK_MODE = "com.android.internal.telephony.REQUEST_NETWORK_MODE";
    public static final String ACTION_MODIFY_NETWORK_MODE = "com.android.internal.telephony.MODIFY_NETWORK_MODE";
    public static final String EXTRA_NETWORK_MODE = "networkMode";

    private static final int NO_NETWORK_MODE_YET = -99;
    private static final int NETWORK_MODE_UNKNOWN = -100;

    private static final int CM_MODE_3G2G = 0;
    private static final int CM_MODE_3GONLY = 1;
    private static final int CM_MODE_BOTH = 2;

    private static final int ONLY_2G = 0;
    private static final int BOTH_3G_2G = 1;
    private static final int STATE_INTERMEDIATE = 2;

    private int mMode = NO_NETWORK_MODE_YET;
    private int mIntendedMode = NO_NETWORK_MODE_YET;
    private final int mInternalState = STATE_INTERMEDIATE;

    private final int mState;


    public MobileNetworkTypeTile(Context context,
            LayoutInflater inflater, QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);


        mMode = get2G3G(context);
        mState = networkModeToState(context);

        switch (mState) {
        case ONLY_2G:
            mDrawable = R.drawable.stat_2g3g_off;
            break;
        case BOTH_3G_2G:
            if (mMode == Phone.NT_MODE_WCDMA_ONLY) {
                mDrawable = R.drawable.stat_3g_on;
            } else {
                mDrawable = R.drawable.stat_2g3g_on;
            }
            break;
        }

        mLabel = mContext.getString(R.string.quick_settings_network_type);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                int currentMode = getCurrentCMMode(mContext);

                Intent intent = new Intent(ACTION_MODIFY_NETWORK_MODE);
                switch (mMode) {
                case Phone.NT_MODE_WCDMA_PREF:
                case Phone.NT_MODE_GSM_UMTS:
                    intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                    mIntendedMode = Phone.NT_MODE_GSM_ONLY;
                    break;
                case Phone.NT_MODE_WCDMA_ONLY:
                    if (currentMode == CM_MODE_3GONLY) {
                        intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                        mIntendedMode = Phone.NT_MODE_GSM_ONLY;
                    } else {
                        intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                        mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
                    }
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    if (currentMode == CM_MODE_3GONLY || currentMode == CM_MODE_BOTH) {
                        intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_ONLY);
                        mIntendedMode = Phone.NT_MODE_WCDMA_ONLY;
                    } else {
                        intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                        mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
                    }
                    break;
                }

                mMode = NETWORK_MODE_UNKNOWN;
                mContext.sendBroadcast(intent);
            }
        };

        mBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                int mode = intent.getIntExtra(EXTRA_NETWORK_MODE, 0);
                switch(mode){
                case Phone.NT_MODE_WCDMA_PREF:
                case Phone.NT_MODE_GSM_UMTS:
                    mDrawable = R.drawable.stat_2g3g_on;
                    break;
                case Phone.NT_MODE_WCDMA_ONLY:
                    mDrawable = R.drawable.stat_3g_on;
                    break;
                case Phone.NT_MODE_GSM_ONLY:
                    mDrawable = R.drawable.stat_2g3g_off;
                }
                updateQuickSettings();
            }
        };

        mIntentFilter = new IntentFilter(ACTION_MODIFY_NETWORK_MODE);
    }

    private static int get2G3G(Context context) {
        int state = 99;
        try {
            state = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
        }
        return state;
    }

    private int networkModeToState(Context context) {

        switch (mMode) {
            case Phone.NT_MODE_WCDMA_PREF:
            case Phone.NT_MODE_WCDMA_ONLY:
            case Phone.NT_MODE_GSM_UMTS:
                return BOTH_3G_2G;
            case Phone.NT_MODE_GSM_ONLY:
                return ONLY_2G;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                // need to check wtf is going on
                Log.d(TAG, "Unexpected network mode (" + mMode + ")");
                return ONLY_2G;
        }
        return STATE_INTERMEDIATE;
    }

    private static int getCurrentCMMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.EXPANDED_NETWORK_MODE,
                CM_MODE_3G2G);
    }

}
