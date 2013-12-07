package com.android.systemui.quicksettings;

import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.DockBatteryController;

public class DockBatteryTile extends BatteryTile {

    public DockBatteryTile(Context context, QuickSettingsController qsc,
            DockBatteryController controller) {
        super(context, qsc, controller, R.layout.quick_settings_tile_dock_battery);
    }

}
