package com.android.systemui.quicksettings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class PowerMenuTile extends QuickSettingsTile{

    public PowerMenuTile(Context context, final QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            	qsc.mBar.collapseAllPanels(true);
                Intent intent = new Intent(Intent.ACTION_POWERMENU);
                mContext.sendBroadcast(intent);
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                qsc.mBar.collapseAllPanels(true);
                Intent intent = new Intent(Intent.ACTION_POWERMENU_REBOOT);
                mContext.sendBroadcast(intent);
                return true;
            }
        };
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
        mDrawable = R.drawable.ic_qs_powermenu;
        mLabel = mContext.getString(R.string.quick_settings_powermenu_label);
    }
}
