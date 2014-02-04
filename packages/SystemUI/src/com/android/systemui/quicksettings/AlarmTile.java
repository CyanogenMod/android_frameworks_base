package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class AlarmTile extends QuickSettingsTile {

    public AlarmTile(Context context, 
            QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(
                Settings.System.NEXT_ALARM_FORMATTED), this);
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
        mDrawable = R.drawable.ic_qs_alarm_on;
        mLabel = Settings.System.getStringForUser(mContext.getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED, UserHandle.USER_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        updateResources();
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }

    @Override
    public void updateQuickSettings() {
        mTile.setVisibility(!TextUtils.isEmpty(mLabel) ? View.VISIBLE : View.GONE);
        super.updateQuickSettings();
    }

}
