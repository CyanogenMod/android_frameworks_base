package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.internal.util.nameless.NamelessUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class PreferencesTile extends QuickSettingsTile {

    private Context mContext;

    public PreferencesTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContext = context;
        mOnClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SETTINGS);
            }
        };
    }

    @Override
    public void onFlingRight() {
        super.onFlingRight();
        if (NamelessUtils.isPackageInstalled(mContext, "org.namelessrom.devicecontrol")) {
            startSettingsActivity(new Intent()
                    .setAction("org.namelessrom.devicecontrol.activities.MainActivity"));
        }
    }

    @Override
    public void onFlingLeft() {
        super.onFlingLeft();
        if (NamelessUtils.isPackageInstalled(mContext, "org.namelessrom.updatecenter")) {
            startSettingsActivity(new Intent()
                    .setAction("org.namelessrom.updatecenter.activities.MainActivity"));
        }
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
        mDrawable = R.drawable.ic_qs_settings;
        mLabel = mContext.getString(R.string.quick_settings_settings_label);
    }
}
