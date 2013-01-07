/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.policy.impl.keyguard;

import android.util.Log;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Displays calendar on LockScreen (Keyguard Widget)
 */
public class KeyguardCalendar extends LinearLayout {

    private KeyguardCalendar mCalendarPanel;
    private TextView mCalendarEventTitle, mCalendarEventDetails;
    private ImageView mCalendarIcon;
    private LayoutInflater mInflater;
    public static final int  MAX_CALENDAR_ITEMS = 3;

    public KeyguardCalendar(Context context) {
        this(context, null);
    }

    public KeyguardCalendar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        refreshCalendar();
    }

    public void refreshCalendar() {
        final ContentResolver resolver = getContext().getContentResolver();
        boolean visible = false; // Assume we are not showing the view

        mCalendarPanel = (KeyguardCalendar) findViewById(R.id.keyguard_calendar_view);
        mCalendarPanel.setOrientation(LinearLayout.VERTICAL);

        if (mCalendarPanel != null) {
            // Load the settings
            boolean lockCalendar = (Settings.System.getInt(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR, 0) == 1);
            String[] calendars = parseStoredValue(Settings.System.getString(
                    resolver, Settings.System.LOCKSCREEN_CALENDARS));
            boolean lockCalendarRemindersOnly = (Settings.System.getInt(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR_REMINDERS_ONLY, 0) == 1);
            long lockCalendarLookahead = Settings.System.getLong(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR_LOOKAHEAD, 10800000);
            boolean hideAllDay = (Settings.System.getInt(resolver,
                    Settings.System.LOCKSCREEN_CALENDAR_HIDE_ALLDAY, 0) == 1);


            if (lockCalendar) {
                String[][] nextCalendar = null;
                nextCalendar = getNextCalendarAlarm(lockCalendarLookahead,
                        calendars, lockCalendarRemindersOnly, hideAllDay);
                mCalendarPanel.removeAllViews();
                for (int i = 0; i < MAX_CALENDAR_ITEMS; i++) {
                    if (nextCalendar[i][0] != null) {
                        Log.d("KeyguardCalendar", "Counter: " + i);
                        View calendarItem = mInflater.inflate(R.layout.keyguard_calendar_items, null);
                        if (!visible) {
                              mCalendarIcon = (ImageView) calendarItem.findViewById(R.id.keyguard_calendar_icon);
                              mCalendarIcon.setImageDrawable( getResources().getDrawable(R.drawable.ic_lock_idle_calendar));
                        }
                        mCalendarEventTitle = (TextView) calendarItem.findViewById(R.id.keyguard_calendar_event_title);
                        mCalendarEventTitle.setText(nextCalendar[i][0].toString());

                        if (nextCalendar[i][1] != null) {
                              mCalendarEventDetails = (TextView) calendarItem.findViewById(R.id.keyguard_calendar_event_details);
                              mCalendarEventDetails.setText(nextCalendar[i][1].toString());
                        }
                        mCalendarPanel.addView(calendarItem, mCalendarPanel.getChildCount());
                        visible = true;
                    }
                }
            }
            mCalendarPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Split the MultiSelectListPreference string based on a separator of ',' and
     * stripping off the start [ and the end ]
     * @param val
     * @return
     */
    private static String[] parseStoredValue(String val) {
        if (val == null || val.isEmpty())
            return null;
        else {
            // Strip off the start [ and the end ] before splitting
            val = val.substring(1, val.length() -1);
            return (val.split(","));
        }
    }

    /**
     * @return A formatted string of the next calendar event with a reminder
     * (for showing on the lock screen), or null if there is no next event
     * within a certain look-ahead time.
     */
    private String[][] getNextCalendarAlarm(long lookahead, String[] calendars,
            boolean remindersOnly, boolean hideAllDay) {
        long now = System.currentTimeMillis();
        long later = now + lookahead;

        // Build the 'where' clause
        StringBuilder where = new StringBuilder();
        if (remindersOnly) {
            where.append(CalendarContract.Events.HAS_ALARM + "=1");
        }

        if (calendars != null && calendars.length > 0) {
            if (remindersOnly) {
                where.append(" AND ");
            }
            where.append(CalendarContract.Events.CALENDAR_ID + " in (");
            for (int i = 0; i < calendars.length; i++) {
                where.append(calendars[i]);
                if (i != calendars.length - 1) {
                    where.append(",");
                }
            }
            where.append(") ");
        }

        // Projection array
        String[] projection = new String[] {
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_ID
        };

        // The indices for the projection array
        int TITLE_INDEX = 0;
        int BEGIN_TIME_INDEX = 1;
        int DESCRIPTION_INDEX = 2;
        int LOCATION_INDEX = 3;
        int ALL_DAY_INDEX = 4;
        int CALENDAR_ID_INDEX = 5;

        Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI,
                String.format("%d/%d", now, later));
        String[][] nextCalendarAlarm = new String[MAX_CALENDAR_ITEMS][2];
        Cursor cursor = null;

        try {
            cursor = mContext.getContentResolver().query(uri, projection,
                    where.toString(), null, "begin ASC");

            if (cursor != null) {
                cursor.moveToFirst();
                // Iterate through returned rows to a maximum number of calendar events
                for (int i = 0, eventCount = 0; i < cursor.getCount() && eventCount < MAX_CALENDAR_ITEMS; i++) {
                    String title = cursor.getString(TITLE_INDEX);
                    long begin = cursor.getLong(BEGIN_TIME_INDEX);
                    String description = cursor.getString(DESCRIPTION_INDEX);
                    String location = cursor.getString(LOCATION_INDEX);
                    boolean allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                    int calendarId = cursor.getInt(CALENDAR_ID_INDEX);

                    Log.d("KeyguardCalendar", "Event: " + title + " from calendar with id: " + calendarId);

                    // If skipping all day events, continue the loop without incementing eventCount
                    if (allDay && hideAllDay) {
                        cursor.moveToNext();
                        continue;
                    }

                    // Check the next event in the case of all day event. As UTC is used for all day
                    // events, the next event may be the one that actually starts sooner
                    if (allDay && !cursor.isLast()) {
                        cursor.moveToNext();
                        long nextBegin = cursor.getLong(BEGIN_TIME_INDEX);
                        if (nextBegin < begin + TimeZone.getDefault().getOffset(begin)) {
                            title = cursor.getString(TITLE_INDEX);
                            begin = nextBegin;
                            description = cursor.getString(DESCRIPTION_INDEX);
                            location = cursor.getString(LOCATION_INDEX);
                            allDay = cursor.getInt(ALL_DAY_INDEX) != 0;
                        }
                        // Go back since we are still iterating
                        cursor.moveToPrevious();
                    }

                    // Set the event title as the first array item
                    nextCalendarAlarm[eventCount][0] = title.toString();

                    // Start building the event details string
                    // Starting with the date
                    Date start = new Date(begin);
                    StringBuilder sb = new StringBuilder();

                    if (allDay) {
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                mContext.getString(R.string.abbrev_wday_month_day_no_year));
                        // Calendar stores all-day events in UTC -- setting the time zone ensures
                        // the correct date is shown.
                        sdf.setTimeZone(TimeZone.getTimeZone(Time.TIMEZONE_UTC));
                        sb.append(sdf.format(start));
                    } else {
                        sb.append(DateFormat.format("E", start));
                        sb.append(" ");
                        sb.append(DateFormat.getTimeFormat(mContext).format(start));
                    }

                    // Add the event location if it should be shown
                    int showLocation = Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.LOCKSCREEN_CALENDAR_SHOW_LOCATION, 0);
                    if (showLocation != 0 && !TextUtils.isEmpty(location)) {
                        switch(showLocation) {
                            case 1:
                                // Show first line
                                int end = location.indexOf('\n');
                                if(end == -1) {
                                    sb.append(": " + location);
                                } else {
                                    sb.append(": " + location.substring(0, end));
                                }
                                break;
                            case 2:
                                // Show all
                                sb.append(": " + location);
                                break;
                        }
                    }

                    // Add the event description if it should be shown
                    int showDescription = Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.LOCKSCREEN_CALENDAR_SHOW_DESCRIPTION, 0);
                    if (showDescription != 0 && !TextUtils.isEmpty(description)) {

                        // Show the appropriate separator
                        if (showLocation == 0) {
                            sb.append(": ");
                        } else {
                            sb.append(" - ");
                        }

                        switch(showDescription) {
                            case 1:
                                // Show first line
                                int end = description.indexOf('\n');
                                if(end == -1) {
                                    sb.append(description);
                                } else {
                                    sb.append(description.substring(0, end));
                                }
                                break;
                            case 2:
                                // Show all
                                sb.append(description);
                                break;
                        }
                    }

                    // Set the time, location and description as the second array item
                    nextCalendarAlarm[eventCount][1] = sb.toString();
                    cursor.moveToNext();

                    // Increment the event counter
                    eventCount++;
                }
            }
        } catch (Exception e) {
            // Do nothing
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return nextCalendarAlarm;
    }
}
