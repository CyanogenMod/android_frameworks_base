package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import com.android.internal.telephony.Phone;

import com.android.systemui.R;

public class NetworkModeController implements CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "StatusBar.NetworkModeController";

    private Context mContext;
    private CompoundButton mCheckBox;

    private boolean mNetworkMode;

    public NetworkModeController(Context context, CompoundButton checkbox) {
        mContext = context;
        mNetworkMode = getNetworkMode();
        mCheckBox = checkbox;
        checkbox.setChecked(mNetworkMode);
        checkbox.setOnCheckedChangeListener(this);
    }

    public void onCheckedChanged(CompoundButton view, boolean checked) {
	int networkType = checked ? Phone.NT_MODE_WCDMA_PREF : Phone.NT_MODE_GSM_ONLY;
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.PREFERRED_NETWORK_MODE, networkType);
    }

    private boolean getNetworkMode() {
        int state = 99;
        try {
            state = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.PREFERRED_NETWORK_MODE);
        } catch (Exception e) {}
        return networkModeToState(state);
    }

    private static boolean networkModeToState(int state) {
        switch(state) {
            case Phone.NT_MODE_WCDMA_PREF:
                return true;
            case Phone.NT_MODE_GSM_ONLY:
                return false;
        }
        return false;
    }
}

