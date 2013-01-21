package com.android.systemui.quicksettings;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class AlarmTile extends QuickSettingsTile {

    public AlarmTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc, Handler handler) {
        super(context, inflater, container, qsc);

        mDrawable = R.drawable.ic_qs_alarm_on;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.deskclock",
                        "com.android.deskclock.AlarmClock"));
                startSettingsActivity(intent);
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.NEXT_ALARM_FORMATTED), this);
        updateStatus();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateStatus();
        updateQuickSettings();
    }

    @Override
    public void updateQuickSettings() {
        mTile.setVisibility(!TextUtils.isEmpty(mLabel) ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

    /**
     * Updates the alarm status shown on the tile.
     */
    private void updateStatus() {
        mLabel = Settings.System.getString(mContext.getContentResolver(),
            Settings.System.NEXT_ALARM_FORMATTED);
    }

}
