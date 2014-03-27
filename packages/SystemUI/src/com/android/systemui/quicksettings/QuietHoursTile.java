package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.util.cm.QuietHoursUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class QuietHoursTile extends QuickSettingsTile {

    private boolean mEnabled;
    private boolean mForced;

    public QuietHoursTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
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
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.QUIET_HOURS_FORCED), this);
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

    private void toggleState() {
        if (!mEnabled && !mForced) {
            mEnabled = true;
        } else if (mEnabled && !mForced) {
            mForced = true;
        } else if (mEnabled && mForced) {
            mEnabled = false;
            mForced = false;
        }

        // Store the setting
        Settings.System.putIntForUser(mContext.getContentResolver(), Settings.System.QUIET_HOURS_ENABLED,
                mEnabled ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(mContext.getContentResolver(), Settings.System.QUIET_HOURS_FORCED,
                mForced ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private synchronized void updateTile() {
        mEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mForced = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_FORCED, 0, UserHandle.USER_CURRENT) == 1;
        if (mEnabled && !mForced) {
            // Do a generic query to see if we are in the Quiet hours period
            if (QuietHoursUtils.inQuietHours(mContext, null)) {
                mDrawable = R.drawable.ic_qs_quiet_hours_on_timed;
            } else {
                mDrawable = R.drawable.ic_qs_quiet_hours_off_timed;
            }
            mLabel = mContext.getString(R.string.quick_settings_quiethours);
        } else if (mForced) {
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
