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
import android.view.View;

import com.android.internal.util.cm.QuietHoursUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class QuietHoursTile extends QuickSettingsTile {

    public static String ACTION_QUIET_HOURS = "com.cyanogenmod.util.action_quiet_hours";
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
        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.QUIET_HOURS_ENABLED), this);
        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.QUIET_HOURS_FORCED), this);
        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.QUIET_HOURS_START), this);
        qsc.registerObservedContent(
                Settings.System.getUriFor(Settings.System.QUIET_HOURS_END), this);
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

        // Remove the receiver, if its set
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
        PendingIntent alarmIntent = getAlarmIntent();

        if (mEnabled && !mForced) {
            // Set an alarm for the next trigger time or cancel
            long trigger = getNextTriggerTimeInMillis(mStart, mEnd);
            if (trigger != 0) {
                am.set(AlarmManager.RTC, trigger, alarmIntent);
            } else {
                am.cancel(alarmIntent);
            }

            // Update the UI elements
            if (QuietHoursUtils.inQuietHours(mStart, mEnd)) {
                mDrawable = R.drawable.ic_qs_quiet_hours_on_timed;
            } else {
                mDrawable = R.drawable.ic_qs_quiet_hours_off_timed;
            }
            mLabel = mContext.getString(R.string.quick_settings_quiethours);
        } else if (mForced) {
            // Cancel any pending alarms, no longer needed since its forced on
            am.cancel(alarmIntent);

            // Update the UI elements
            mDrawable = R.drawable.ic_qs_quiet_hours_on;
            mLabel = mContext.getString(R.string.quick_settings_quiethours);
        } else {
            // Cancel any pending alarms, no longer needed
            am.cancel(alarmIntent);

            // Update the UI elements
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
        if (startTime == endTime) {
            return 0;
        }

        Calendar cal = Calendar.getInstance();
        int currentTime = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE);
        int nextTime;

        if (currentTime > startTime && currentTime > endTime) {
            // Past last trigger of the day, use next day's first one
            nextTime = Math.min(startTime, endTime);
            cal.roll(Calendar.DAY_OF_MONTH, 1);
        } else if (currentTime < startTime && currentTime < endTime) {
            // Before first trigger of the day, use it
            nextTime = Math.min(startTime, endTime);
        } else {
            // Between first and second trigger, use the last one
            nextTime = Math.max(startTime, endTime);
        }

        // Set the appropriate time and return the millis
        cal.set(Calendar.HOUR_OF_DAY, nextTime / 60);
        cal.set(Calendar.MINUTE, nextTime % 60);
        cal.set(Calendar.SECOND, 0);
        return cal.getTimeInMillis();
    }
}
