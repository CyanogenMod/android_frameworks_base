package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.Phone;

import java.util.ArrayList;
import java.util.List;

public class NetworkModeButton extends PowerButton{
    private static final String TAG = "NetworkModeButton";

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.Secure.getUriFor(Settings.Secure.PREFERRED_NETWORK_MODE));
    }

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

    private int mMode = NO_NETWORK_MODE_YET;
    private int mIntendedMode = NO_NETWORK_MODE_YET;
    private int mInternalState = STATE_INTERMEDIATE;

    public NetworkModeButton() { mType = BUTTON_NETWORKMODE; }

    @Override
    protected void updateState(Context context) {
        mMode = get2G3G(context);
        mState = networkModeToState(context);

        switch (mState) {
        case STATE_DISABLED:
            mIcon = R.drawable.stat_2g3g_off;
            break;
        case STATE_ENABLED:
            if (mMode == Phone.NT_MODE_WCDMA_ONLY) {
                mIcon = R.drawable.stat_3g_on;
            } else {
                mIcon = R.drawable.stat_2g3g_on;
            }
            break;
        case STATE_INTERMEDIATE:
            // In the transitional state, the bottom green bar
            // shows the tri-state (on, off, transitioning), but
            // the top dark-gray-or-bright-white logo shows the
            // user's intent. This is much easier to see in
            // sunlight.
            if (mInternalState == STATE_TURNING_ON) {
                if (mIntendedMode == Phone.NT_MODE_WCDMA_ONLY) {
                    mIcon = R.drawable.stat_3g_on;
                } else {
                    mIcon = R.drawable.stat_2g3g_on;
                }
            } else {
                mIcon = R.drawable.stat_2g3g_off;
            }
            break;
        }
    }

    @Override
    protected void toggleState(Context context) {
        int currentMode = getCurrentCMMode(context);

        Intent intent = new Intent(ACTION_MODIFY_NETWORK_MODE);
        switch (mMode) {
        case Phone.NT_MODE_WCDMA_PREF:
        case Phone.NT_MODE_GSM_UMTS:
            intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
            mInternalState = STATE_TURNING_OFF;
            mIntendedMode = Phone.NT_MODE_GSM_ONLY;
            break;
        case Phone.NT_MODE_WCDMA_ONLY:
            if (currentMode == CM_MODE_3GONLY) {
                intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_GSM_ONLY);
                mInternalState = STATE_TURNING_OFF;
                mIntendedMode = Phone.NT_MODE_GSM_ONLY;
            } else {
                intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                mInternalState = STATE_TURNING_ON;
                mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
            }
            break;
        case Phone.NT_MODE_GSM_ONLY:
            if (currentMode == CM_MODE_3GONLY || currentMode == CM_MODE_BOTH) {
                intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_ONLY);
                mInternalState = STATE_TURNING_ON;
                mIntendedMode = Phone.NT_MODE_WCDMA_ONLY;
            } else {
                intent.putExtra(EXTRA_NETWORK_MODE, Phone.NT_MODE_WCDMA_PREF);
                mInternalState = STATE_TURNING_ON;
                mIntendedMode = Phone.NT_MODE_WCDMA_PREF;
            }
            break;
        }

        mMode = NETWORK_MODE_UNKNOWN;
        context.sendBroadcast(intent);
    }

    @Override
    protected boolean handleLongClick(Context context) {
        // it may be better to make an Intent action for this or find the appropriate one
        // we may want to look at that option later
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.android.phone", "com.android.phone.Settings");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getExtras() != null) {
            mMode = intent.getExtras().getInt(EXTRA_NETWORK_MODE);
            //Update to actual state
            mIntendedMode = mMode;
        }

        //need to clear intermediate states
        mInternalState = STATE_ENABLED;
        mInternalState = networkModeToState(context);
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NETWORK_MODE_CHANGED);
        return filter;
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    private static int get2G3G(Context context) {
        int state = 99;
        try {
            state = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.PREFERRED_NETWORK_MODE);
        } catch (SettingNotFoundException e) {
        }
        return state;
    }

    private int networkModeToState(Context context) {
        if (mInternalState == STATE_TURNING_ON || mInternalState == STATE_TURNING_OFF) {
            return STATE_INTERMEDIATE;
        }

        switch (mMode) {
            case Phone.NT_MODE_WCDMA_PREF:
            case Phone.NT_MODE_WCDMA_ONLY:
            case Phone.NT_MODE_GSM_UMTS:
                return STATE_ENABLED;
            case Phone.NT_MODE_GSM_ONLY:
                return STATE_DISABLED;
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_GLOBAL:
                // need to check wtf is going on
                Log.d(TAG, "Unexpected network mode (" + mMode + ")");
                return STATE_DISABLED;
        }
        return STATE_INTERMEDIATE;
    }

    private static int getCurrentCMMode(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.EXPANDED_NETWORK_MODE,
                CM_MODE_3G2G);
    }
}
