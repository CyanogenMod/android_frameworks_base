package com.android.systemui.quicksettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.powerwidget.StateTracker;
import com.android.systemui.statusbar.powerwidget.WifiApButton;

public class WifiAPTile extends QuickSettingsTile {

    private static final StateTracker sWifiApState = new WifiApButton.WifiApStateTracker();

    public WifiAPTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);
        updateTileState();
        onClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sWifiApState.toggleState(mContext);
            }
        };
        onLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                startSettingsActivity(intent);
                return true;
            }
        };
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                 sWifiApState.onActualStateChange(context, intent);
                 updateTileState();
                 updateQuickSettings();
            }
        };
        mIntentFilter = new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
    }

    private void updateTileState() {
        switch (sWifiApState.getTriState(mContext)) {
            case WifiApButton.STATE_DISABLED:
                mDrawable = R.drawable.ic_qs_wifi_ap_off;
                mLabel = mContext.getString(R.string.quick_settings_label_disabled);
                break;
            case WifiApButton.STATE_ENABLED:
                mDrawable = R.drawable.ic_qs_wifi_ap_on;
                mLabel = mContext.getString(R.string.quick_settings_label_enabled);
                break;
            case WifiApButton.STATE_INTERMEDIATE:
                if (sWifiApState.isTurningOn()) {
                    mLabel = mContext.getString(R.string.quick_settings_label_enabled);
                    mDrawable = R.drawable.ic_qs_wifi_ap_on;
                } else {
                    mLabel = mContext.getString(R.string.quick_settings_label_disabled);
                    mDrawable = R.drawable.ic_qs_wifi_ap_off;
                }
                break;
        }
    }
}
