package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
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
    private int mDockBatteryLevel = 0;
    private int mDockBatteryStatus;
    private Drawable mDockBatteryIcon;

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
    public void onDockBatteryLevelChanged(int level, boolean present, int status) {
        mDockBatteryLevel = level;
        mDockBatteryStatus = status;
        mPresent = present;
        updateResources();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        int drawableResId = mDockBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                ? R.drawable.qs_sys_dock_battery_charging : R.drawable.qs_sys_dock_battery;

        mTile.setVisibility(mPresent ? View.VISIBLE : View.GONE);
        mDockBatteryIcon = mContext.getResources().getDrawable(drawableResId);

        if (mDockBatteryStatus == BatteryManager.BATTERY_STATUS_FULL) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        } else if (mDockBatteryStatus == BatteryManager.BATTERY_STATUS_CHARGING) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charging_label,
                    mDockBatteryLevel);
        } else {
            mLabel = mContext.getString(R.string.status_bar_settings_battery_meter_format,
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
