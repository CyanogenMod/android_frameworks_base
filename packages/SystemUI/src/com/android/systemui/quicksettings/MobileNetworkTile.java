package com.android.systemui.quicksettings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public class MobileNetworkTile extends QuickSettingsTile implements NetworkSignalChangedCallback{

    private int mDataTypeIconId;
    private String dataContentDescription;
    private String signalContentDescription;
    private boolean wifiOn = false;

    public MobileNetworkTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);
        mTileLayout = R.layout.quick_settings_tile_rssi;
        onClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                ConnectivityManager conMan = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                if(tm.getDataState() == TelephonyManager.DATA_DISCONNECTED){
                    conMan.setMobileDataEnabled(true);
                }else{
                    conMan.setMobileDataEnabled(false);
                }
            }
        };
        onLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings$DataUsageSummaryActivity"));
                startSettingsActivity(intent);
                return true;
            }
        };
    }

    @Override
    void onPostCreate() {
        NetworkController controller = new NetworkController(mContext);
        controller.addNetworkSignalChangedCallback(this);
        super.onPostCreate();
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            String wifitSignalContentDescriptionId, String description) {
        wifiOn = enabled;

    }

    @Override
    public void onMobileDataSignalChanged(boolean enabled,
            int mobileSignalIconId, String mobileSignalContentDescriptionId,
            int dataTypeIconId, String dataTypeContentDescriptionId,
            String description) {
        if (deviceSupportsTelephony()) {
            // TODO: If view is in awaiting state, disable
            Resources r = mContext.getResources();
            mDrawable = enabled && (mobileSignalIconId > 0)
                    ? mobileSignalIconId
                    : R.drawable.ic_qs_signal_no_signal;
            signalContentDescription = enabled && (mobileSignalIconId > 0)
                    ? signalContentDescription
                    : r.getString(R.string.accessibility_no_signal);
            mDataTypeIconId = enabled && (dataTypeIconId > 0) && !wifiOn
                    ? dataTypeIconId
                    : 0;
            dataContentDescription = enabled && (dataTypeIconId > 0) && !wifiOn
                    ? dataContentDescription
                    : r.getString(R.string.accessibility_no_data);
            mLabel = enabled
                    ? removeTrailingPeriod(description)
                    : r.getString(R.string.quick_settings_rssi_emergency_only);
            updateQuickSettings();
        }
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        // TODO Auto-generated method stub

    }

    boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.rssi_textview);
        ImageView iv = (ImageView) mTile.findViewById(R.id.rssi_image);
        ImageView iov = (ImageView) mTile.findViewById(R.id.rssi_overlay_image);
        iv.setImageResource(mDrawable);
        if (mDataTypeIconId > 0) {
            iov.setImageResource(mDataTypeIconId);
        } else {
            iov.setImageDrawable(null);
        }
        tv.setText(mLabel);
        mTile.setContentDescription(mContext.getResources().getString(
                R.string.accessibility_quick_settings_mobile,
                signalContentDescription, dataContentDescription,
                mLabel));
    }

 // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null) return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            string.substring(0, length - 1);
        }
        return string;
    }

}
