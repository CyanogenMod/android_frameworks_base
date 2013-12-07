package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.DockBatteryController;
import com.android.systemui.statusbar.policy.DockBatteryController.BatteryStateChangeCallback;

public class DockBatteryTile extends QuickSettingsTile implements BatteryStateChangeCallback{
    private DockBatteryController mController;

    private int mBatteryLevel = 0;
    private boolean mPluggedIn;

    public DockBatteryTile(Context context, QuickSettingsController qsc, DockBatteryController controller) {
        super(context, qsc, R.layout.quick_settings_tile_dock_battery); 

        mController = controller;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        mController.addStateChangedCallback(this);
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeStateChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, int status) {
        mBatteryLevel = level;
        mPluggedIn = pluggedIn;
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if (mBatteryLevel == 100) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        } else {
            mLabel = mPluggedIn
                ? mContext.getString(R.string.quick_settings_battery_charging_label,
                        mBatteryLevel)
                : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                        mBatteryLevel);
        }
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        tv.setText(mLabel);
    }

}
