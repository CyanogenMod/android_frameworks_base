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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
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

    public static final int NO_LIMIT = -1;

    private static final int ONE_SECOND = 1000;
    private static final int ONE_MINUTE = 60 * ONE_SECOND;
    private static final int ONE_HOUR = 60 * ONE_MINUTE;

    /**
     * Based on a quick fallback:
     * First is to get the most relevant package for the current hour interval on this specific day
     * with a 1 week look-back, if the limit is not achieved with that query then fallback to most
     * launched packages overall.
     * @param limit
     * @param context
     * @return return a limited amount of most relevant packages
     */
    public static ArrayList<PackageInfo> getRelevantPackagesForRightNow(Context context, int limit) {
        ArrayList<PackageInfo> infos = new ArrayList<PackageInfo>();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long currentHour = cal.getTimeInMillis();
        long lookahead = currentHour + ONE_HOUR;
        ArrayList<PackageInfo> intervalInfos = getRelevantPackagesForInterval(context,
                new Date(currentHour), new Date(lookahead), limit);

        for (PackageInfo info : intervalInfos) {
            if (!infos.contains(info)) {
                infos.add(info);
            }
        }

        if (infos.size() < limit) {
            ArrayList<PackageInfo> launchCountInfos = getMostLaunchedPackages(context, limit
                    - infos.size());
            for (PackageInfo info : launchCountInfos) {
                if (!infos.contains(info)) {
                    infos.add(info);
                }
            }
        }
        return infos;
    }

    /**
     * Get the relevant packages for a date interval, with a week-long look-back.
     * @param context
     * @param from
     * @param to
     * @param limit
     * @return return a limited list of most relevant packages for a date interval
     */
    public static ArrayList<PackageInfo> getRelevantPackagesForInterval(Context context,
            Date from, Date to, int limit) {

        ArrayList<PackageInfo> infos = new ArrayList<PackageInfo>();
        String selection = SmartPackageStatsContracts.RawStats.RESUMED_TIME
                + " BETWEEN '" + from.getTime() + "' AND '" + to.getTime() + "' ";

        String realLimit = getRealLimit(limit, SmartPackageStatsContracts.RawStats.USAGE_TIME);
        Cursor c = context.getContentResolver()
                .query(SmartPackageStatsContracts.RawStats.CONTENT_URI,
                        new String[]{ SmartPackageStatsContracts.RawStats.PKG_NAME }
                        , selection, null, realLimit);

        if (c != null) {
            if (DEBUG) Log.d(TAG, "SmartPackageStatsUtils query successful , "
                    + c.getCount() + " matches");
            while (c.moveToNext()) {
                PackageInfo info = getPackageInfoForPackageName(context, c.getString(0));
                if (info != null && !infos.contains(info)) {
                    infos.add(info);
                }
            }
            c.close();
        }
        return infos;
    }

    /**
     *
     * @param context
     * @param limit
     * @return a limited list of most launched packages overall
     */
    public static ArrayList<PackageInfo> getMostLaunchedPackages(Context context, int limit) {
        ArrayList<PackageInfo> infos = new ArrayList<PackageInfo>();
        String realLimit = getRealLimit(limit, SmartPackageStatsContracts.RawStats.LAUNCH_COUNT);
        populateListFromLimit(context, infos, realLimit);
        return infos;
    }

    /**
     *
     * @param context
     * @return a limited list of most used packages overall
     */
    public static ArrayList<PackageInfo> getHighestUsageTimePackages(Context context, int limit) {
        ArrayList<PackageInfo> infos = new ArrayList<PackageInfo>();
        String realLimit = getRealLimit(limit, SmartPackageStatsContracts.RawStats.USAGE_TIME);
        populateListFromLimit(context, infos, realLimit);
        return infos;
    }

    private static void populateListFromLimit(Context context, ArrayList<PackageInfo> infos, String realLimit) {
        Cursor c = context.getContentResolver()
                .query(SmartPackageStatsContracts.RawStats.CONTENT_URI,
                        new String[]{ SmartPackageStatsContracts.RawStats.PKG_NAME }
                        , null, null, realLimit);

        if (c != null) {
            if (DEBUG) Log.d(TAG, "SmartPackageStatsUtils query successful , " + c.getCount() + " matches");
            while (c.moveToNext()) {
                PackageInfo info = getPackageInfoForPackageName(context, c.getString(0));
                if (info != null && !infos.contains(info)) {
                    infos.add(info);
                }
            }
            c.close();
        }
    }

    private static PackageInfo getPackageInfoForPackageName(Context context, String pkgName) {
        final PackageManager packageManager = context.getPackageManager();
        try {
            return packageManager.getPackageInfo(pkgName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static String getRealLimit(int limit, String type) {
        String realLimit = null;
        if (limit != NO_LIMIT) {
            realLimit = type
                    + " DESC " + " LIMIT " + limit;
        }
        return realLimit;
    }
}
