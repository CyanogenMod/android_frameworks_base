package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.internal.telephony.Phone;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

import java.util.List;

public class LteTile extends QuickSettingsTile {

    private Context mContext;

    public LteTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContext = context;

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLteState();
                updateResources();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.phone", "com.android.phone.Settings");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.Global.getUriFor(Settings.Global.PREFERRED_NETWORK_MODE), this);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        int network = getCurrentPreferredNetworkMode(mContext);
        switch(network) {
            case Phone.NT_MODE_GLOBAL:
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
                mDrawable = R.drawable.ic_qs_lte_on;
                mLabel = mContext.getString(R.string.quick_settings_lte);
                break;
            default:
                mDrawable = R.drawable.ic_qs_lte_off;
                mLabel = mContext.getString(R.string.quick_settings_lte_off);
                break;
        }
    }

    private void toggleLteState() {
        TelephonyManager tm = (TelephonyManager)
            mContext.getSystemService(Context.TELEPHONY_SERVICE);
        int network = getCurrentPreferredNetworkMode(mContext);
        switch(network) {
            case Phone.NT_MODE_GLOBAL:
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
                tm.toggleLTE(false);
                break;
            default:
                tm.toggleLTE(true);
                break;
        }
    }

    private static int getCurrentPreferredNetworkMode(Context context) {
        int network = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, -1);
        return network;
    }
}
