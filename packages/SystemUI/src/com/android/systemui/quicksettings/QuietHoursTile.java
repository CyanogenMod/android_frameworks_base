package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class QuietHoursTile extends QuickSettingsTile {

    private boolean mEnabled;

    public QuietHoursTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Settings.System.putIntForUser(mContext.getContentResolver(), Settings.System.QUIET_HOURS_ENABLED,
                        mEnabled ? 0 : 1, UserHandle.USER_CURRENT);
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings", "com.android.settings.Settings$QuietHoursSettingsActivity");
                startSettingsActivity(intent);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.QUIET_HOURS_ENABLED), this);
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
        mEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        if (mEnabled) {
            mDrawable = R.drawable.ic_qs_quiet_hours_on;
            mLabel = mContext.getString(R.string.quick_settings_quiethours);
        } else {
            mDrawable = R.drawable.ic_qs_quiet_hours_off;
            mLabel = mContext.getString(R.string.quick_settings_quiethours_off);
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

}
