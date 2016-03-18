/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.server.usage;

import android.app.usage.ConfigurationStats;
import android.app.usage.TimeSparseArray;
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.content.Context;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.usage.UsageStatsDatabase.StatCombiner;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A per-user UsageStatsService. All methods are meant to be called with the main lock held
 * in UsageStatsService.
 */
class UserUsageStatsService {
    private static final String TAG = "UsageStatsService";
    private static final boolean DEBUG = UsageStatsService.DEBUG;
    private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int sDateFormatFlags =
            DateUtils.FORMAT_SHOW_DATE
            | DateUtils.FORMAT_SHOW_TIME
            | DateUtils.FORMAT_SHOW_YEAR
            | DateUtils.FORMAT_NUMERIC_DATE;

    private final Context mContext;
    private final UsageStatsDatabase mDatabase;
    private final IntervalStats[] mCurrentStats;
    private IntervalStats mAppIdleRollingWindow;
    private boolean mStatsChanged = false;
    private final UnixCalendar mDailyExpiryDate;
    private final StatsUpdatedListener mListener;
    private final String mLogPrefix;
    private final int mUserId;

    interface StatsUpdatedListener {
        void onStatsUpdated();
        void onStatsReloaded();
        long getAppIdleRollingWindowDurationMillis();
    }

    UserUsageStatsService(Context context, int userId, File usageStatsDir,
            StatsUpdatedListener listener) {
        mContext = context;
        mDailyExpiryDate = new UnixCalendar(0);
        mDatabase = new UsageStatsDatabase(usageStatsDir);
        mCurrentStats = new IntervalStats[UsageStatsManager.INTERVAL_COUNT];
        mListener = listener;
        mLogPrefix = "User[" + Integer.toString(userId) + "] ";
        mUserId = userId;
    }

    void init(final long currentTimeMillis, final long deviceUsageTime) {
        mDatabase.init(currentTimeMillis);

        int nullCount = 0;
        for (int i = 0; i < mCurrentStats.length; i++) {
            mCurrentStats[i] = mDatabase.getLatestUsageStats(i);
            if (mCurrentStats[i] == null) {
                // Find out how many intervals we don't have data for.
                // Ideally it should be all or none.
                nullCount++;
            }
        }

        if (nullCount > 0) {
            if (nullCount != mCurrentStats.length) {
                // This is weird, but we shouldn't fail if something like this
                // happens.
                Slog.w(TAG, mLogPrefix + "Some stats have no latest available");
            } else {
                // This must be first boot.
            }

            // By calling loadActiveStats, we will
            // generate new stats for each bucket.
            loadActiveStats(currentTimeMillis,/*force=*/ false, /*resetBeginIdleTime=*/ false);
        } else {
            // Set up the expiry date to be one day from the latest daily stat.
            // This may actually be today and we will rollover on the first event
            // that is reported.
            mDailyExpiryDate.setTimeInMillis(
                    mCurrentStats[UsageStatsManager.INTERVAL_DAILY].beginTime);
            mDailyExpiryDate.addDays(1);
            mDailyExpiryDate.truncateToDay();
            Slog.i(TAG, mLogPrefix + "Rollover scheduled @ " +
                    sDateFormat.format(mDailyExpiryDate.getTimeInMillis()) +
                    "(" + mDailyExpiryDate.getTimeInMillis() + ")");
        }

        // Now close off any events that were open at the time this was saved.
        for (IntervalStats stat : mCurrentStats) {
            final int pkgCount = stat.packageStats.size();
            for (int i = 0; i < pkgCount; i++) {
                UsageStats pkgStats = stat.packageStats.valueAt(i);
                if (pkgStats.mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        pkgStats.mLastEvent == UsageEvents.Event.CONTINUE_PREVIOUS_DAY) {
                    stat.update(pkgStats.mPackageName, stat.lastTimeSaved,
                            UsageEvents.Event.END_OF_DAY);
                    notifyStatsChanged();
                }
            }

            stat.updateConfigurationStats(null, stat.lastTimeSaved);
        }

        refreshAppIdleRollingWindow(currentTimeMillis, deviceUsageTime);

        if (mDatabase.isNewUpdate()) {
            initializeDefaultsForApps(currentTimeMillis, deviceUsageTime,
                    mDatabase.isFirstUpdate());
        }
    }

    /**
     * If any of the apps don't have a last-used entry, add one now.
     * @param currentTimeMillis the current time
     * @param firstUpdate if it is the first update, touch all installed apps, otherwise only
     *        touch the system apps
     */
    private void initializeDefaultsForApps(long currentTimeMillis, long deviceUsageTime,
            boolean firstUpdate) {
        PackageManager pm = mContext.getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0, mUserId);
        final int packageCount = packages.size();
        for (int i = 0; i < packageCount; i++) {
            final PackageInfo pi = packages.get(i);
            String packageName = pi.packageName;
            if (pi.applicationInfo != null && (firstUpdate || pi.applicationInfo.isSystemApp())
                    && getBeginIdleTime(packageName) == -1) {
                for (IntervalStats stats : mCurrentStats) {
                    stats.update(packageName, currentTimeMillis, Event.SYSTEM_INTERACTION);
                    stats.updateBeginIdleTime(packageName, deviceUsageTime);
                }
                mAppIdleRollingWindow.update(packageName, currentTimeMillis,
                        Event.SYSTEM_INTERACTION);
                mAppIdleRollingWindow.updateBeginIdleTime(packageName, deviceUsageTime);
                mStatsChanged = true;
            }
        }
        // Persist the new OTA-related access stats.
        persistActiveStats();
    }

    void onTimeChanged(long oldTime, long newTime, long deviceUsageTime,
                       boolean resetBeginIdleTime) {
        persistActiveStats();
        mDatabase.onTimeChanged(newTime - oldTime);
        loadActiveStats(newTime, /* force= */ true, resetBeginIdleTime);
        refreshAppIdleRollingWindow(newTime, deviceUsageTime);
    }

    void reportEvent(UsageEvents.Event event, long deviceUsageTime) {
        if (DEBUG) {
            Slog.d(TAG, mLogPrefix + "Got usage event for " + event.mPackage
                    + "[" + event.mTimeStamp + "]: "
                    + eventToString(event.mEventType));
        }

        if (event.mTimeStamp >= mDailyExpiryDate.getTimeInMillis()) {
            // Need to rollover
            rolloverStats(event.mTimeStamp, deviceUsageTime);
        }

        final IntervalStats currentDailyStats = mCurrentStats[UsageStatsManager.INTERVAL_DAILY];

        final Configuration newFullConfig = event.mConfiguration;
        if (event.mEventType == UsageEvents.Event.CONFIGURATION_CHANGE &&
                currentDailyStats.activeConfiguration != null) {
            // Make the event configuration a delta.
            event.mConfiguration = Configuration.generateDelta(
                    currentDailyStats.activeConfiguration, newFullConfig);
        }

        // Add the event to the daily list.
        if (currentDailyStats.events == null) {
            currentDailyStats.events = new TimeSparseArray<>();
        }
        if (event.mEventType != UsageEvents.Event.SYSTEM_INTERACTION) {
            currentDailyStats.events.put(event.mTimeStamp, event);
        }

        for (IntervalStats stats : mCurrentStats) {
            if (event.mEventType == UsageEvents.Event.CONFIGURATION_CHANGE) {
                stats.updateConfigurationStats(newFullConfig, event.mTimeStamp);
            } else {
                stats.update(event.mPackage, event.mTimeStamp, event.mEventType);
                stats.updateBeginIdleTime(event.mPackage, deviceUsageTime);
            }
        }

        if (event.mEventType != Event.CONFIGURATION_CHANGE) {
            mAppIdleRollingWindow.update(event.mPackage, event.mTimeStamp, event.mEventType);
            mAppIdleRollingWindow.updateBeginIdleTime(event.mPackage, deviceUsageTime);
        }

        notifyStatsChanged();
    }

    /**
     * Sets the beginIdleTime for each of the intervals.
     * @param beginIdleTime
     */
    void setBeginIdleTime(String packageName, long beginIdleTime) {
        for (IntervalStats stats : mCurrentStats) {
            stats.updateBeginIdleTime(packageName, beginIdleTime);
        }
        mAppIdleRollingWindow.updateBeginIdleTime(packageName, beginIdleTime);
        notifyStatsChanged();
    }

    void setSystemLastUsedTime(String packageName, long lastUsedTime) {
        for (IntervalStats stats : mCurrentStats) {
            stats.updateSystemLastUsedTime(packageName, lastUsedTime);
        }
        mAppIdleRollingWindow.updateSystemLastUsedTime(packageName, lastUsedTime);
        notifyStatsChanged();
    }

    private static final StatCombiner<UsageStats> sUsageStatsCombiner =
            new StatCombiner<UsageStats>() {
                @Override
                public void combine(IntervalStats stats, boolean mutable,
                        List<UsageStats> accResult) {
                    if (!mutable) {
                        accResult.addAll(stats.packageStats.values());
                        return;
                    }

                    final int statCount = stats.packageStats.size();
                    for (int i = 0; i < statCount; i++) {
                        accResult.add(new UsageStats(stats.packageStats.valueAt(i)));
                    }
                }
            };

    private static final StatCombiner<ConfigurationStats> sConfigStatsCombiner =
            new StatCombiner<ConfigurationStats>() {
                @Override
                public void combine(IntervalStats stats, boolean mutable,
                        List<ConfigurationStats> accResult) {
                    if (!mutable) {
                        accResult.addAll(stats.configurations.values());
                        return;
                    }

                    final int configCount = stats.configurations.size();
                    for (int i = 0; i < configCount; i++) {
                        accResult.add(new ConfigurationStats(stats.configurations.valueAt(i)));
                    }
                }
            };

    /**
     * Generic query method that selects the appropriate IntervalStats for the specified time range
     * and bucket, then calls the {@link com.android.server.usage.UsageStatsDatabase.StatCombiner}
     * provided to select the stats to use from the IntervalStats object.
     */
    private <T> List<T> queryStats(int intervalType, final long beginTime, final long endTime,
            StatCombiner<T> combiner) {
        if (intervalType == UsageStatsManager.INTERVAL_BEST) {
            intervalType = mDatabase.findBestFitBucket(beginTime, endTime);
            if (intervalType < 0) {
                // Nothing saved to disk yet, so every stat is just as equal (no rollover has
                // occurred.
                intervalType = UsageStatsManager.INTERVAL_DAILY;
            }
        }

        if (intervalType < 0 || intervalType >= mCurrentStats.length) {
            if (DEBUG) {
                Slog.d(TAG, mLogPrefix + "Bad intervalType used " + intervalType);
            }
            return null;
        }

        final IntervalStats currentStats = mCurrentStats[intervalType];

        if (DEBUG) {
            Slog.d(TAG, mLogPrefix + "SELECT * FROM " + intervalType + " WHERE beginTime >= "
                    + beginTime + " AND endTime < " + endTime);
        }

        if (beginTime >= currentStats.endTime) {
            if (DEBUG) {
                Slog.d(TAG, mLogPrefix + "Requesting stats after " + beginTime + " but latest is "
                        + currentStats.endTime);
            }
            // Nothing newer available.
            return null;
        }

        // Truncate the endTime to just before the in-memory stats. Then, we'll append the
        // in-memory stats to the results (if necessary) so as to avoid writing to disk too
        // often.
        final long truncatedEndTime = Math.min(currentStats.beginTime, endTime);

        // Get the stats from disk.
        List<T> results = mDatabase.queryUsageStats(intervalType, beginTime,
                truncatedEndTime, combiner);
        if (DEBUG) {
            Slog.d(TAG, "Got " + (results != null ? results.size() : 0) + " results from disk");
            Slog.d(TAG, "Current stats beginTime=" + currentStats.beginTime +
                    " endTime=" + currentStats.endTime);
        }

        // Now check if the in-memory stats match the range and add them if they do.
        if (beginTime < currentStats.endTime && endTime > currentStats.beginTime) {
            if (DEBUG) {
                Slog.d(TAG, mLogPrefix + "Returning in-memory stats");
            }

            if (results == null) {
                results = new ArrayList<>();
            }
            combiner.combine(currentStats, true, results);
        }

        if (DEBUG) {
            Slog.d(TAG, mLogPrefix + "Results: " + (results != null ? results.size() : 0));
        }
        return results;
    }

    List<UsageStats> queryUsageStats(int bucketType, long beginTime, long endTime) {
        return queryStats(bucketType, beginTime, endTime, sUsageStatsCombiner);
    }

    List<ConfigurationStats> queryConfigurationStats(int bucketType, long beginTime, long endTime) {
        return queryStats(bucketType, beginTime, endTime, sConfigStatsCombiner);
    }

    UsageEvents queryEvents(final long beginTime, final long endTime) {
        final ArraySet<String> names = new ArraySet<>();
        List<UsageEvents.Event> results = queryStats(UsageStatsManager.INTERVAL_DAILY,
                beginTime, endTime, new StatCombiner<UsageEvents.Event>() {
                    @Override
                    public void combine(IntervalStats stats, boolean mutable,
                            List<UsageEvents.Event> accumulatedResult) {
                        if (stats.events == null) {
                            return;
                        }

                        final int startIndex = stats.events.closestIndexOnOrAfter(beginTime);
                        if (startIndex < 0) {
                            return;
                        }

                        final int size = stats.events.size();
                        for (int i = startIndex; i < size; i++) {
                            if (stats.events.keyAt(i) >= endTime) {
                                return;
                            }

                            final UsageEvents.Event event = stats.events.valueAt(i);
                            names.add(event.mPackage);
                            if (event.mClass != null) {
                                names.add(event.mClass);
                            }
                            accumulatedResult.add(event);
                        }
                    }
                });

        if (results == null || results.isEmpty()) {
            return null;
        }

        String[] table = names.toArray(new String[names.size()]);
        Arrays.sort(table);
        return new UsageEvents(results, table);
    }

    long getBeginIdleTime(String packageName) {
        UsageStats packageUsage;
        if ((packageUsage = mAppIdleRollingWindow.packageStats.get(packageName)) == null) {
            return -1;
        } else {
            return packageUsage.getBeginIdleTime();
        }
    }

    long getSystemLastUsedTime(String packageName) {
        UsageStats packageUsage;
        if ((packageUsage = mAppIdleRollingWindow.packageStats.get(packageName)) == null) {
            return -1;
        } else {
            return packageUsage.getLastTimeSystemUsed();
        }
    }

    void persistActiveStats() {
        if (mStatsChanged) {
            Slog.i(TAG, mLogPrefix + "Flushing usage stats to disk");
            try {
                for (int i = 0; i < mCurrentStats.length; i++) {
                    mDatabase.putUsageStats(i, mCurrentStats[i]);
                }
                mStatsChanged = false;
            } catch (IOException e) {
                Slog.e(TAG, mLogPrefix + "Failed to persist active stats", e);
            }
        }
    }

    private void rolloverStats(final long currentTimeMillis, final long deviceUsageTime) {
        final long startTime = SystemClock.elapsedRealtime();
        Slog.i(TAG, mLogPrefix + "Rolling over usage stats");

        // Finish any ongoing events with an END_OF_DAY event. Make a note of which components
        // need a new CONTINUE_PREVIOUS_DAY entry.
        final Configuration previousConfig =
                mCurrentStats[UsageStatsManager.INTERVAL_DAILY].activeConfiguration;
        ArraySet<String> continuePreviousDay = new ArraySet<>();
        for (IntervalStats stat : mCurrentStats) {
            final int pkgCount = stat.packageStats.size();
            for (int i = 0; i < pkgCount; i++) {
                UsageStats pkgStats = stat.packageStats.valueAt(i);
                if (pkgStats.mLastEvent == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        pkgStats.mLastEvent == UsageEvents.Event.CONTINUE_PREVIOUS_DAY) {
                    continuePreviousDay.add(pkgStats.mPackageName);
                    stat.update(pkgStats.mPackageName, mDailyExpiryDate.getTimeInMillis() - 1,
                            UsageEvents.Event.END_OF_DAY);
                    notifyStatsChanged();
                }
            }

            stat.updateConfigurationStats(null, mDailyExpiryDate.getTimeInMillis() - 1);
        }

        persistActiveStats();
        mDatabase.prune(currentTimeMillis);
        loadActiveStats(currentTimeMillis, /*force=*/ false, /*resetBeginIdleTime=*/ false);

        final int continueCount = continuePreviousDay.size();
        for (int i = 0; i < continueCount; i++) {
            String name = continuePreviousDay.valueAt(i);
            final long beginTime = mCurrentStats[UsageStatsManager.INTERVAL_DAILY].beginTime;
            for (IntervalStats stat : mCurrentStats) {
                stat.update(name, beginTime, UsageEvents.Event.CONTINUE_PREVIOUS_DAY);
                stat.updateConfigurationStats(previousConfig, beginTime);
                notifyStatsChanged();
            }
        }
        persistActiveStats();

        refreshAppIdleRollingWindow(currentTimeMillis, deviceUsageTime);

        final long totalTime = SystemClock.elapsedRealtime() - startTime;
        Slog.i(TAG, mLogPrefix + "Rolling over usage stats complete. Took " + totalTime
                + " milliseconds");
    }

    private void notifyStatsChanged() {
        if (!mStatsChanged) {
            mStatsChanged = true;
            mListener.onStatsUpdated();
        }
    }

    /**
     * @param force To force all in-memory stats to be reloaded.
     */
    private void loadActiveStats(final long currentTimeMillis, boolean force,
            boolean resetBeginIdleTime) {
        final UnixCalendar tempCal = mDailyExpiryDate;
        for (int intervalType = 0; intervalType < mCurrentStats.length; intervalType++) {
            tempCal.setTimeInMillis(currentTimeMillis);
            UnixCalendar.truncateTo(tempCal, intervalType);

            if (!force && mCurrentStats[intervalType] != null &&
                    mCurrentStats[intervalType].beginTime == tempCal.getTimeInMillis()) {
                // These are the same, no need to load them (in memory stats are always newer
                // than persisted stats).
                continue;
            }

            final long lastBeginTime = mDatabase.getLatestUsageStatsBeginTime(intervalType);
            if (lastBeginTime >= tempCal.getTimeInMillis()) {
                if (DEBUG) {
                    Slog.d(TAG, mLogPrefix + "Loading existing stats @ " +
                            sDateFormat.format(lastBeginTime) + "(" + lastBeginTime +
                            ") for interval " + intervalType);
                }
                mCurrentStats[intervalType] = mDatabase.getLatestUsageStats(intervalType);
            } else {
                mCurrentStats[intervalType] = null;
            }

            if (mCurrentStats[intervalType] == null) {
                if (DEBUG) {
                    Slog.d(TAG, "Creating new stats @ " +
                            sDateFormat.format(tempCal.getTimeInMillis()) + "(" +
                            tempCal.getTimeInMillis() + ") for interval " + intervalType);

                }
                mCurrentStats[intervalType] = new IntervalStats();
                mCurrentStats[intervalType].beginTime = tempCal.getTimeInMillis();
                mCurrentStats[intervalType].endTime = currentTimeMillis;
            }

            if (resetBeginIdleTime) {
                for (UsageStats usageStats : mCurrentStats[intervalType].packageStats.values()) {
                    usageStats.mBeginIdleTime = 0;
                }
            }
        }

        mStatsChanged = false;
        mDailyExpiryDate.setTimeInMillis(currentTimeMillis);
        mDailyExpiryDate.addDays(1);
        mDailyExpiryDate.truncateToDay();
        Slog.i(TAG, mLogPrefix + "Rollover scheduled @ " +
                sDateFormat.format(mDailyExpiryDate.getTimeInMillis()) + "(" +
                tempCal.getTimeInMillis() + ")");

        // Tell the listener that the stats reloaded, which may have changed idle states.
        mListener.onStatsReloaded();
    }

    private static void mergePackageStats(IntervalStats dst, IntervalStats src,
                                          final long deviceUsageTime) {
        dst.endTime = Math.max(dst.endTime, src.endTime);

        final int srcPackageCount = src.packageStats.size();
        for (int i = 0; i < srcPackageCount; i++) {
            final String packageName = src.packageStats.keyAt(i);
            final UsageStats srcStats = src.packageStats.valueAt(i);
            UsageStats dstStats = dst.packageStats.get(packageName);
            if (dstStats == null) {
                dstStats = new UsageStats(srcStats);
                dst.packageStats.put(packageName, dstStats);
            } else {
                dstStats.add(src.packageStats.valueAt(i));
            }

            // App idle times can not begin in the future. This happens if we had a time change.
            if (dstStats.mBeginIdleTime > deviceUsageTime) {
                dstStats.mBeginIdleTime = deviceUsageTime;
            }
        }
    }

    /**
     * App idle operates on a rolling window of time. When we roll over time, we end up with a
     * period of time where in-memory stats are empty and we don't hit the disk for older stats
     * for performance reasons. Suddenly all apps will become idle.
     *
     * Instead, at times we do a deep query to find all the apps that have run in the past few
     * days and keep the cached data up to date.
     *
     * @param currentTimeMillis
     */
    void refreshAppIdleRollingWindow(final long currentTimeMillis, final long deviceUsageTime) {
        // Start the rolling window for AppIdle requests.
        final long startRangeMillis = currentTimeMillis -
                mListener.getAppIdleRollingWindowDurationMillis();

        List<IntervalStats> stats = mDatabase.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                startRangeMillis, currentTimeMillis, new StatCombiner<IntervalStats>() {
                    @Override
                    public void combine(IntervalStats stats, boolean mutable,
                                        List<IntervalStats> accumulatedResult) {
                        IntervalStats accum;
                        if (accumulatedResult.isEmpty()) {
                            accum = new IntervalStats();
                            accum.beginTime = stats.beginTime;
                            accumulatedResult.add(accum);
                        } else {
                            accum = accumulatedResult.get(0);
                        }

                        mergePackageStats(accum, stats, deviceUsageTime);
                    }
                });

        if (stats == null || stats.isEmpty()) {
            mAppIdleRollingWindow = new IntervalStats();
            mergePackageStats(mAppIdleRollingWindow,
                    mCurrentStats[UsageStatsManager.INTERVAL_YEARLY], deviceUsageTime);
        } else {
            mAppIdleRollingWindow = stats.get(0);
        }
    }

    //
    // -- DUMP related methods --
    //

    void checkin(final IndentingPrintWriter pw, final long screenOnTime) {
        mDatabase.checkinDailyFiles(new UsageStatsDatabase.CheckinAction() {
            @Override
            public boolean checkin(IntervalStats stats) {
                printIntervalStats(pw, stats, screenOnTime, false);
                return true;
            }
        });
    }

    void dump(IndentingPrintWriter pw, final long screenOnTime) {
        // This is not a check-in, only dump in-memory stats.
        for (int interval = 0; interval < mCurrentStats.length; interval++) {
            pw.print("In-memory ");
            pw.print(intervalToString(interval));
            pw.println(" stats");
            printIntervalStats(pw, mCurrentStats[interval], screenOnTime, true);
        }

        pw.println("AppIdleRollingWindow cache");
        printIntervalStats(pw, mAppIdleRollingWindow, screenOnTime, true);
    }

    private String formatDateTime(long dateTime, boolean pretty) {
        if (pretty) {
            return "\"" + DateUtils.formatDateTime(mContext, dateTime, sDateFormatFlags) + "\"";
        }
        return Long.toString(dateTime);
    }

    private String formatElapsedTime(long elapsedTime, boolean pretty) {
        if (pretty) {
            return "\"" + DateUtils.formatElapsedTime(elapsedTime / 1000) + "\"";
        }
        return Long.toString(elapsedTime);
    }

    void printIntervalStats(IndentingPrintWriter pw, IntervalStats stats, long screenOnTime,
            boolean prettyDates) {
        if (prettyDates) {
            pw.printPair("timeRange", "\"" + DateUtils.formatDateRange(mContext,
                    stats.beginTime, stats.endTime, sDateFormatFlags) + "\"");
        } else {
            pw.printPair("beginTime", stats.beginTime);
            pw.printPair("endTime", stats.endTime);
        }
        pw.println();
        pw.increaseIndent();
        pw.println("packages");
        pw.increaseIndent();
        final ArrayMap<String, UsageStats> pkgStats = stats.packageStats;
        final int pkgCount = pkgStats.size();
        for (int i = 0; i < pkgCount; i++) {
            final UsageStats usageStats = pkgStats.valueAt(i);
            pw.printPair("package", usageStats.mPackageName);
            pw.printPair("totalTime",
                    formatElapsedTime(usageStats.mTotalTimeInForeground, prettyDates));
            pw.printPair("lastTime", formatDateTime(usageStats.mLastTimeUsed, prettyDates));
            pw.printPair("lastTimeSystem",
                    formatDateTime(usageStats.mLastTimeSystemUsed, prettyDates));
            pw.printPair("inactiveTime",
                    formatElapsedTime(screenOnTime - usageStats.mBeginIdleTime, prettyDates));
            pw.println();
        }
        pw.decreaseIndent();

        pw.println("configurations");
        pw.increaseIndent();
        final ArrayMap<Configuration, ConfigurationStats> configStats = stats.configurations;
        final int configCount = configStats.size();
        for (int i = 0; i < configCount; i++) {
            final ConfigurationStats config = configStats.valueAt(i);
            pw.printPair("config", Configuration.resourceQualifierString(config.mConfiguration));
            pw.printPair("totalTime", formatElapsedTime(config.mTotalTimeActive, prettyDates));
            pw.printPair("lastTime", formatDateTime(config.mLastTimeActive, prettyDates));
            pw.printPair("count", config.mActivationCount);
            pw.println();
        }
        pw.decreaseIndent();

        pw.println("events");
        pw.increaseIndent();
        final TimeSparseArray<UsageEvents.Event> events = stats.events;
        final int eventCount = events != null ? events.size() : 0;
        for (int i = 0; i < eventCount; i++) {
            final UsageEvents.Event event = events.valueAt(i);
            pw.printPair("time", formatDateTime(event.mTimeStamp, prettyDates));
            pw.printPair("type", eventToString(event.mEventType));
            pw.printPair("package", event.mPackage);
            if (event.mClass != null) {
                pw.printPair("class", event.mClass);
            }
            if (event.mConfiguration != null) {
                pw.printPair("config", Configuration.resourceQualifierString(event.mConfiguration));
            }
            pw.println();
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
    }

    private static String intervalToString(int interval) {
        switch (interval) {
            case UsageStatsManager.INTERVAL_DAILY:
                return "daily";
            case UsageStatsManager.INTERVAL_WEEKLY:
                return "weekly";
            case UsageStatsManager.INTERVAL_MONTHLY:
                return "monthly";
            case UsageStatsManager.INTERVAL_YEARLY:
                return "yearly";
            default:
                return "?";
        }
    }

    private static String eventToString(int eventType) {
        switch (eventType) {
            case UsageEvents.Event.NONE:
                return "NONE";
            case UsageEvents.Event.MOVE_TO_BACKGROUND:
                return "MOVE_TO_BACKGROUND";
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
                return "MOVE_TO_FOREGROUND";
            case UsageEvents.Event.END_OF_DAY:
                return "END_OF_DAY";
            case UsageEvents.Event.CONTINUE_PREVIOUS_DAY:
                return "CONTINUE_PREVIOUS_DAY";
            case UsageEvents.Event.CONFIGURATION_CHANGE:
                return "CONFIGURATION_CHANGE";
            case UsageEvents.Event.SYSTEM_INTERACTION:
                return "SYSTEM_INTERACTION";
            case UsageEvents.Event.USER_INTERACTION:
                return "USER_INTERACTION";
            default:
                return "UNKNOWN";
        }
    }
}
