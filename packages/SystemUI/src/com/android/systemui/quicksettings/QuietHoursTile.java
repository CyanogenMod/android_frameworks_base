package com.android.systemui.quicksettings;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

    private static int ALARM_ID = 1010101;

    private boolean mEnabled;
    private boolean mForced;
    private int mStart;
    private int mEnd;
    private QuietHoursReceiver mReceiver;

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
                intent.setClassName("com.android.settings",
                        "com.android.settings.Settings$QuietHoursSettingsActivity");
                startSettingsActivity(intent);
                return true;
            }
        };
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.QUIET_HOURS_ENABLED), this);
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.QUIET_HOURS_FORCED), this);
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.QUIET_HOURS_START), this);
        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.QUIET_HOURS_END), this);
    }

    @Override
    void onPostCreate() {
        if (mReceiver == null) {
            mReceiver = new QuietHoursReceiver();
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(QuietHoursUtils.ACTION_QUIET_HOURS));
        }

        updateTile();
        super.onPostCreate();
    }


    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            // Get rid of the alarm
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            am.cancel(getQuietHoursAlarmIntent());

            // Remove the receiver
            mContext.unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        mEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
        mForced = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_FORCED, 0, UserHandle.USER_CURRENT) == 1;
        mStart = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_START, 0, UserHandle.USER_CURRENT);
        mEnd = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_END, 0, UserHandle.USER_CURRENT);

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
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED,
                mEnabled ? 1 : 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_FORCED,
                mForced ? 1 : 0, UserHandle.USER_CURRENT);
    }

    private synchronized void updateTile() {
        if (mEnabled && !mForced) {
            // Do a generic query to see if we are in the Quiet hours period
            if (QuietHoursUtils.isQuietHoursActive(mStart, mEnd)) {
                mDrawable = R.drawable.ic_qs_quiet_hours_on_timed;
            } else {
                mDrawable = R.drawable.ic_qs_quiet_hours_off_timed;
            }
            mLabel = mContext.getString(R.string.quick_settings_quiethours);

            // Set the alarm for the next trigger time
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.RTC, QuietHoursUtils.getNextTrigger(mStart, mEnd),
                    getQuietHoursAlarmIntent());
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

    private class QuietHoursReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateResources();
        }
    }

    private PendingIntent getQuietHoursAlarmIntent() {
        Intent i = new Intent();
        i.setAction(QuietHoursUtils.ACTION_QUIET_HOURS);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, ALARM_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pi;
    }
}
