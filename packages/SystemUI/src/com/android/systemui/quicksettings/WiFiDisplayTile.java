package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.WifiDisplayStatus;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class WiFiDisplayTile extends QuickSettingsTile{

    private boolean enabled = false;
    private boolean connected = false;

    public WiFiDisplayTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
            }
        };
        qsc.registerAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        WifiDisplayStatus status = (WifiDisplayStatus)intent.getParcelableExtra(DisplayManager.EXTRA_WIFI_DISPLAY_STATUS);
        enabled = status.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_ON;
        connected = status.getActiveDisplay() != null;
        updateResources();
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if(enabled && connected) {
            mLabel = mContext.getString(R.string.quick_settings_wifi_display_label);
            mDrawable = R.drawable.ic_qs_remote_display_connected;
        }else{
            mLabel = mContext.getString(R.string.quick_settings_wifi_display_no_connection_label);
            mDrawable = R.drawable.ic_qs_remote_display;
        }
    }

    @Override
    void updateQuickSettings() {
        mTile.setVisibility(enabled ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }
}
