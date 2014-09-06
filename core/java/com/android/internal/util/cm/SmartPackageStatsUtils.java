/*
 * Copyright (C) 2014 The CyanogenMod Project
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

package com.android.internal.util.cm;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Smart Package Stats Utils
 * @hide
 */
public class SmartPackageStatsUtils {
    private static final String TAG = "SmartPackageStatsUtils";
    private static final boolean DEBUG = false;

    public static final int NO_LIMIT = 0;

    private static final int ONE_SECOND = 1000;
    private static final int ONE_MINUTE = 60 * ONE_SECOND;
    private static final int ONE_HOUR = 60 * ONE_MINUTE;
    private static final int ONE_DAY = ONE_HOUR * 24;
    private static final int ONE_WEEK = ONE_DAY * 7;

    /**
     * Based on a quick fallback:
     * First is to get the most relevant component for the current hour interval on this
     * specific day with a 1 week look-back, if the limit is not achieved with that query
     * then fallback to most launched components overall.
     * @param limit a limit for how many components to query for
     * @param context
     * @return return a limited amount of most relevant components
     */
    public static ArrayList<ComponentName> getRelevantComponentsAWeekAgo(Context context,
            int limit) {
        ArrayList<ComponentName> infos = new ArrayList<ComponentName>();

        Calendar cal = Calendar.getInstance();
        long currentHour = cal.getTimeInMillis() - (ONE_HOUR / 2) - ONE_WEEK;
        long lookahead = currentHour + ONE_HOUR;

        ArrayList<ComponentName> intervalInfos = getRelevantComponentsForInterval(context,
                new Date(currentHour), new Date(lookahead), limit);

        for (ComponentName info : intervalInfos) {
            if (DEBUG) Log.d(TAG, "Relevant app " + info.toString());
            if (!infos.contains(info)) {
                infos.add(info);
            }
        }

        if (infos.size() < limit) {
            //TODO: This is ineffecient
            ArrayList<ComponentName> launchCountInfos = getMostLaunchedComponents(context,
                    NO_LIMIT);
            for (ComponentName info : launchCountInfos) {
                if (infos.size() < limit) {
                    if (DEBUG) Log.d(TAG, "Most launched " + info.toString());
                    if (!infos.contains(info)) {
                        infos.add(info);
                    }
                } else {
                    break;
                }
            }
        }
        return infos;
    }

    /**
     * Get the relevant component for a date interval, with a week-long look-back.
     * @param context
     * @param from a date to start from
     * @param to a date to end at
     * @param limit a limit for how many components to query for
     * @return return a limited list of most relevant component for a date interval
     */
    public static ArrayList<ComponentName> getRelevantComponentsForInterval(Context context,
            Date from, Date to, int limit) {
        ArrayList<ComponentName> infos = new ArrayList<ComponentName>();
        String selection = SmartPackageStatsContracts.RawStats.RESUMED_TIME
                + " BETWEEN '" + from.getTime() + "' AND '" + to.getTime() + "' ";

        String realLimit = getRealLimit(limit, SmartPackageStatsContracts.RawStats.USAGE_TIME);
        Cursor c = context.getContentResolver()
                .query(SmartPackageStatsContracts.RawStats.CONTENT_URI,
                        new String[]{ SmartPackageStatsContracts.RawStats.PKG_NAME,
                                SmartPackageStatsContracts.RawStats.COMP_NAME }
                        , selection, null, realLimit);

        if (c != null) {
            if (DEBUG) Log.d(TAG, "SmartPackageStatsUtils query successful , "
                    + c.getCount() + " matches");
            while (c.moveToNext()) {
                ComponentName info = createComponentNameForPackageAndComponent(context,
                        c.getString(0),
                        c.getString(1));
                if (info != null && !infos.contains(info)
                        && isPackageEnabled(context, info.getPackageName())) {
                    infos.add(info);
                }
            }
            c.close();
        }
        return infos;
    }

    /**
     * Get the most launched components overall
     * @param context
     * @param limit a limit for how many components to query for
     * @return a limited list of most launched components overall
     */
    public static ArrayList<ComponentName> getMostLaunchedComponents(Context context, int limit) {
        ArrayList<ComponentName> infos = new ArrayList<ComponentName>();
        String realLimit = getRealLimit(limit, SmartPackageStatsContracts.RawStats.LAUNCH_COUNT);
        populateListFromLimit(context, infos, realLimit);
        return infos;
    }

    /**
     * Get the components with the highest usage time overall
     * @param context
     * @param limit a limit for how many components to query for
     * @return a limited list of most used components overall
     */
    public static ArrayList<ComponentName> getHighestUsageTimeComponents(Context context,
            int limit) {
        ArrayList<ComponentName> infos = new ArrayList<ComponentName>();
        String realLimit = getRealLimit(limit, SmartPackageStatsContracts.RawStats.USAGE_TIME);
        populateListFromLimit(context, infos, realLimit);
        return infos;
    }

    private static void populateListFromLimit(Context context, ArrayList<ComponentName> infos,
            String realLimit) {
        Cursor c = context.getContentResolver()
                .query(SmartPackageStatsContracts.RawStats.CONTENT_URI,
                        new String[]{ SmartPackageStatsContracts.RawStats.PKG_NAME,
                                SmartPackageStatsContracts.RawStats.COMP_NAME }
                        , null, null, realLimit);

        if (c != null) {
            if (DEBUG) Log.d(TAG, "SmartPackageStatsUtils query successful , "
                    + c.getCount() + " matches");
            while (c.moveToNext()) {
                if (DEBUG) Log.d(TAG, "SmartPackageStatsUtils query cursor " +
                        DatabaseUtils.dumpCurrentRowToString(c));
                ComponentName info = createComponentNameForPackageAndComponent(context,
                        c.getString(0),
                        c.getString(1));
                if (info != null && !infos.contains(info)
                        && isPackageEnabled(context, info.getPackageName())) {
                    infos.add(info);
                }
            }
            c.close();
        }
    }

    private static ComponentName createComponentNameForPackageAndComponent(Context context,
            String pkgName, String compName) {
        final PackageManager packageManager = context.getPackageManager();
        if (compName == null) {
            ComponentName componentName =
                    packageManager.getLaunchIntentForPackage(pkgName).getComponent();
            return componentName;
        }
        return new ComponentName(pkgName, compName);
    }

    private static boolean isPackageEnabled(Context context, String pkgName) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.getApplicationEnabledSetting(pkgName)
                == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

    private static String getRealLimit(int limit, String type) {
        String realLimit = null;
        if (limit != NO_LIMIT) {
            realLimit = type
                    + " DESC " + " LIMIT " + limit;
        } else {
            realLimit = type + " DESC ";
        }
        return realLimit;
    }
}
