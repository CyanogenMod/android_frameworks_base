/*
 *  Copyright (C) 2013 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.systemui.omni;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseArray;

import java.util.Calendar;

import com.android.systemui.R;

/**
 * This class manages the header images you can have on the expanded status bar
 * (ie. when you open the notification drawer)
 *
 * TODO: Make periods configurable through an XML
 */
public class StatusHeaderMachine {

    private static final String TAG = "StatusHeaderMachine";

    // Daily calendar periods
    // Night starts at 20:00
    private static final int TIME_NIGHT = 20;
    private static final int DRAWABLE_NIGHT = R.drawable.notifhead_night;
    // Morning starts at 07:00
    private static final int TIME_MORNING = 7;
    private static final int DRAWABLE_MORNING = R.drawable.notifhead_morning;
    // Afternoon starts at 13:00
    private static final int TIME_AFTERNOON = 13;
    private static final int DRAWABLE_AFTERNOON = R.drawable.notifhead_afternoon;

    // Special events
    // Christmas is on Dec 25th
    private static final Calendar CAL_CHRISTMAS = Calendar.getInstance();
    private static final int DRAWABLE_CHRISTMAS = R.drawable.notifhead_christmas;
    // New years eve is on Dec 31st
    private static final Calendar CAL_NEWYEARSEVE = Calendar.getInstance();
    private static final int DRAWABLE_NEWYEARSEVE = R.drawable.notifhead_newyearseve;

    // Default drawable (AOSP)
    private static final int DRAWABLE_DEFAULT = R.drawable.notification_panel_bg;

    // Members

    private Context mContext;
    private SparseArray<Drawable> mCache;


    // Methods

    public StatusHeaderMachine(Context context) {
        // There is one downside with this method: it will only work once a year,
        // if you don't reboot your phone. I hope you will reboot your phone once
        // in a year.
        CAL_CHRISTMAS.set(Calendar.MONTH, Calendar.DECEMBER);
        CAL_CHRISTMAS.set(Calendar.DAY_OF_MONTH, 25);

        CAL_NEWYEARSEVE.set(Calendar.MONTH, Calendar.DECEMBER);
        CAL_NEWYEARSEVE.set(Calendar.DAY_OF_MONTH, 31);

        mContext = context;
        mCache = new SparseArray<Drawable>();
    }

    public Drawable getDefault() {
        return loadOrFetch(DRAWABLE_DEFAULT);
    }

    public Drawable getCurrent() {
        // Check special events first. They have the priority over any other period.

        if (isItToday(CAL_CHRISTMAS)) {
            // Merry christmas!
            return loadOrFetch(DRAWABLE_CHRISTMAS);
        } else if (isItToday(CAL_NEWYEARSEVE)) {
            // Happy new year!
            return loadOrFetch(DRAWABLE_NEWYEARSEVE);
        }

        // Now we check normal periods
        final Calendar now = Calendar.getInstance();
        final int hour = now.get(Calendar.HOUR_OF_DAY);

        if (hour < TIME_MORNING || hour >= TIME_NIGHT) {
            // It's before morning (0 -> TIME_MORNING) or night (TIME_NIGHT -> 23)
            return loadOrFetch(DRAWABLE_NIGHT);
        } else if (hour >= TIME_MORNING && hour < TIME_AFTERNOON) {
            // It's morning, or before afternoon
            return loadOrFetch(DRAWABLE_MORNING);
        } else if (hour >= TIME_AFTERNOON && hour < TIME_NIGHT) {
            // It's afternoon
            return loadOrFetch(DRAWABLE_AFTERNOON);
        }

        // When all else fails, just be yourself
        Log.w(TAG, "No drawable for status  bar when it is " + hour + "!");
        return loadOrFetch(DRAWABLE_DEFAULT);
    }

    private Drawable loadOrFetch(int resId) {
        Drawable res = mCache.get(resId);

        if (res == null) {
            // We don't have this drawable cached, do it!
            final Resources r = mContext.getResources();
            res = r.getDrawable(resId);
            mCache.put(resId, res);
        }

        return res;
    }

    private static boolean isItToday(final Calendar date) {
        final Calendar now = Calendar.getInstance();
        return (now.get(Calendar.MONTH) == date.get(Calendar.MONTH) &&
            now.get(Calendar.DAY_OF_MONTH) == date.get(Calendar.DAY_OF_MONTH));
    }


}
