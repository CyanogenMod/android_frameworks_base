package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryTile extends QuickSettingsTile implements BatteryStateChangeCallback{
    private BatteryController mController;

    private boolean charging = false;
    private int batteryLevel = 0;
    private Drawable batteryIcon;

    private LevelListDrawable batteryLevels;
    private LevelListDrawable chargingBatteryLevels;

    public BatteryTile(Context context, QuickSettingsController qsc, BatteryController controller) {
        super(context, qsc, R.layout.quick_settings_tile_battery);

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
    public void onBatteryLevelChanged(int level, boolean pluggedIn) {
        batteryLevel = level;
        charging = pluggedIn;
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        batteryLevels = (LevelListDrawable) mContext.getResources().getDrawable(R.drawable.qs_sys_battery);
        chargingBatteryLevels = (LevelListDrawable) mContext.getResources().getDrawable(R.drawable.qs_sys_battery_charging);
        batteryIcon = charging
                ? chargingBatteryLevels :
                    batteryLevels;
        if(batteryLevel == 100) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        }else{
            mLabel = charging
                    ? mContext.getString(R.string.quick_settings_battery_charging_label,
                            batteryLevel)
                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                            batteryLevel);
        }
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.battery_textview);
        tv.setText(mLabel);
        ImageView iv = (ImageView) mTile.findViewById(R.id.battery_image);
        iv.setImageDrawable(batteryIcon);
        iv.setImageLevel(batteryLevel);
    }

}
