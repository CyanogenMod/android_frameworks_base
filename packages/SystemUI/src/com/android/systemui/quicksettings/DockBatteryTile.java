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
import com.android.systemui.statusbar.policy.DockBatteryController;
import com.android.systemui.statusbar.policy.DockBatteryController.DockBatteryStateChangeCallback;

public class DockBatteryTile extends QuickSettingsTile implements DockBatteryStateChangeCallback {
    private DockBatteryController mController;
    private boolean mPresent = false;
    private boolean mCharging = false;
    private int mDockBatteryLevel = 0;
    private Drawable mDockBatteryIcon;

    private LevelListDrawable mDockBatteryLevels;
    private LevelListDrawable mChargingDockBatteryLevels;

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
    public void onDockBatteryLevelChanged(int level, boolean present, boolean pluggedIn) {
        mDockBatteryLevel = level;
        mCharging = pluggedIn;
        mPresent = present;
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mTile.setVisibility(mPresent ? View.VISIBLE : View.GONE);
        mDockBatteryLevels = (LevelListDrawable) mContext.getResources().getDrawable(R.drawable.qs_sys_dock_battery);
        mChargingDockBatteryLevels = (LevelListDrawable) mContext.getResources().getDrawable(R.drawable.qs_sys_dock_battery_charging);
        mDockBatteryIcon = mCharging
                ? mChargingDockBatteryLevels :
                  mDockBatteryLevels;
        if(mDockBatteryLevel == 100) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        }else{
            mLabel = mCharging
                    ? mContext.getString(R.string.quick_settings_battery_charging_label,
                            mDockBatteryLevel)
                    : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                            mDockBatteryLevel);
        }
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.dock_battery_textview);
        tv.setText(mLabel);
        ImageView iv = (ImageView) mTile.findViewById(R.id.dock_battery_image);
        iv.setImageDrawable(mDockBatteryIcon);
        iv.setImageLevel(mDockBatteryLevel);
    }

}
