package com.android.systemui.quicksettings;

import java.util.Calendar;

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

    public static String ACTION_QUIET_HOURS = "com.cyanogenmod.util.action_quiet_hours";
    public static final long DAY_IN_MINUTES = 24L * 60L;
    public static final long MINUTES_IN_MILLIS = 60L * 1000L;
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
            mContext.registerReceiver(mReceiver, new IntentFilter(ACTION_QUIET_HOURS));
        }

        updateResources();
        super.onPostCreate();
    }


    @Override
    public void onDestroy() {
        // Cancel any pending alarms
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getAlarmIntent());

        if (mReceiver != null) {
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
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        if (mEnabled && !mForced) {
            // Set an alarm for the next trigger time
            am.set(AlarmManager.RTC, getNextTriggerTimeInMillis(mStart, mEnd), getAlarmIntent());
            if (QuietHoursUtils.isQuietHoursActive(mStart, mEnd)) {
                mDrawable = R.drawable.ic_qs_quiet_hours_on_timed;
            } else {
                mDrawable = R.drawable.ic_qs_quiet_hours_off_timed;
            }
            mLabel = mContext.getString(R.string.quick_settings_quiethours);
        } else if (mForced) {
            // Cancel any pending alarms, no longer needed since its forced on
            am.cancel(getAlarmIntent());
            mDrawable = R.drawable.ic_qs_quiet_hours_on;
            mLabel = mContext.getString(R.string.quick_settings_quiethours);
        } else {
            // Cancel any pending alarms, no longer needed
            am.cancel(getAlarmIntent());
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

    private PendingIntent getAlarmIntent() {
        Intent i = new Intent();
        i.setAction(ACTION_QUIET_HOURS);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, ALARM_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pi;
    }

    private long getNextTriggerTimeInMillis(int startTime, int endTime) {
        Calendar cal = Calendar.getInstance();
        long offset = 0;

        if (startTime != endTime) {
            // Get the date in "quiet hours" format.
            int currentTime = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
            if (endTime < startTime) {
                // Starts at night, ends in the morning.
                if (currentTime > startTime) {
                    // In the period, before midnight
                    // Add the remaining minutes of the current day to the end time
                    offset = endTime + (DAY_IN_MINUTES - currentTime);
                } else if (currentTime < endTime) {
                    // In the period, after midnight
                    offset = endTime - currentTime;
                } else {
                    offset = startTime - currentTime;
                }
            } else {
                // Starts in the morning, ends at night.
                if ((currentTime > startTime) && (currentTime < endTime)) {
                    // In the period, use the delta to the end
                    offset = endTime - currentTime;
                } else if (currentTime > endTime) {
                    // After the period
                    // Add the remaining minutes of the current day to the start time
                    offset = startTime + (DAY_IN_MINUTES - currentTime);
                } else {
                    // Before the period, use the delta to the start
                    offset = startTime - currentTime;
                }
            }
        }

        // Convert to millis and return the result added to current time
        offset *= MINUTES_IN_MILLIS;
        return cal.getTimeInMillis() + offset;
    }

}
