package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class QuietHoursTile extends QuickSettingsTile{

    public QuietHoursTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);
        updateTileState();
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.QUIET_HOURS_ENABLED, 0) == 1;
                Settings.System.putInt(mContext.getContentResolver(), Settings.System.QUIET_HOURS_ENABLED,
                        enabled ? 0 : 1);
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
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateTileState();
        updateQuickSettings();
    }

    private void updateTileState() {
        boolean enabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0) == 1;
        mLabel = mContext.getString(R.string.quick_settings_quiethours);
        if (enabled) {
            mDrawable = R.drawable.ic_qs_quiet_hours_on;
        } else {
            mDrawable = R.drawable.ic_qs_quiet_hours_off;
        }
    }

}
