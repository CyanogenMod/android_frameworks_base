/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server;

import android.app.Activity;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.IAlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Pair;
import android.util.Slog;
import android.util.TimeUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import com.android.internal.util.LocalLog;

class AlarmManagerService extends IAlarmManager.Stub {
    // The threshold for how long an alarm can be late before we print a
    // warning message.  The time duration is in milliseconds.
    private static final long LATE_ALARM_THRESHOLD = 10 * 1000;
    
    private static final int RTC_WAKEUP_MASK = 1 << AlarmManager.RTC_WAKEUP;
    private static final int RTC_MASK = 1 << AlarmManager.RTC;
    private static final int ELAPSED_REALTIME_WAKEUP_MASK = 1 << AlarmManager.ELAPSED_REALTIME_WAKEUP; 
    private static final int ELAPSED_REALTIME_MASK = 1 << AlarmManager.ELAPSED_REALTIME;
    private static final int TIME_CHANGED_MASK = 1 << 16;

    // Alignment quantum for inexact repeating alarms
    private static final long QUANTUM = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    private static final String TAG = "AlarmManager";
    private static final String ClockReceiver_TAG = "ClockReceiver";
    private static final boolean localLOGV = false;
    private static final int ALARM_EVENT = 1;
    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    
    private static final Intent mBackgroundIntent
            = new Intent().addFlags(Intent.FLAG_FROM_BACKGROUND);
    
    private final Context mContext;

    private final LocalLog mLog = new LocalLog(TAG);

    private Object mLock = new Object();
    
    private final ArrayList<Alarm> mRtcWakeupAlarms = new ArrayList<Alarm>();
    private final ArrayList<Alarm> mRtcAlarms = new ArrayList<Alarm>();
    private final ArrayList<Alarm> mElapsedRealtimeWakeupAlarms = new ArrayList<Alarm>();
    private final ArrayList<Alarm> mElapsedRealtimeAlarms = new ArrayList<Alarm>();
    private final IncreasingTimeOrder mIncreasingTimeOrder = new IncreasingTimeOrder();
    
    private int mDescriptor;
    private int mBroadcastRefCount = 0;
    private PowerManager.WakeLock mWakeLock;
    private ArrayList<InFlight> mInFlight = new ArrayList<InFlight>();
    private final AlarmThread mWaitThread = new AlarmThread();
    private final AlarmHandler mHandler = new AlarmHandler();
    private ClockReceiver mClockReceiver;
    private UninstallReceiver mUninstallReceiver;
    private final ResultReceiver mResultReceiver = new ResultReceiver();
    private final PendingIntent mTimeTickSender;
    private final PendingIntent mDateChangeSender;

    private final AppOpsManager mAppOps;

    private static final class InFlight extends Intent {
        final PendingIntent mPendingIntent;
        final Pair<String, ComponentName> mTarget;
        final BroadcastStats mBroadcastStats;
        final FilterStats mFilterStats;

        InFlight(AlarmManagerService service, PendingIntent pendingIntent) {
            mPendingIntent = pendingIntent;
            Intent intent = pendingIntent.getIntent();
            mTarget = intent != null
                    ? new Pair<String, ComponentName>(intent.getAction(), intent.getComponent())
                    : null;
            mBroadcastStats = service.getStatsLocked(pendingIntent);
            FilterStats fs = mBroadcastStats.filterStats.get(mTarget);
            if (fs == null) {
                fs = new FilterStats(mBroadcastStats, mTarget);
                mBroadcastStats.filterStats.put(mTarget, fs);
            }
            mFilterStats = fs;
        }
    }

    private static final class FilterStats {
        final BroadcastStats mBroadcastStats;
        final Pair<String, ComponentName> mTarget;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;

        FilterStats(BroadcastStats broadcastStats, Pair<String, ComponentName> target) {
            mBroadcastStats = broadcastStats;
            mTarget = target;
        }
    }
    
    private static final class BroadcastStats {
        final String mPackageName;

        long aggregateTime;
        int count;
        int numWakeup;
        long startTime;
        int nesting;
        final HashMap<Pair<String, ComponentName>, FilterStats> filterStats
                = new HashMap<Pair<String, ComponentName>, FilterStats>();

        BroadcastStats(String packageName) {
            mPackageName = packageName;
        }
    }
    
    private final HashMap<String, BroadcastStats> mBroadcastStats
            = new HashMap<String, BroadcastStats>();
    
    public AlarmManagerService(Context context) {
        mContext = context;
        mDescriptor = init();

        // We have to set current TimeZone info to kernel
        // because kernel doesn't keep this after reboot
        String tz = SystemProperties.get(TIMEZONE_PROPERTY);
        if (tz != null) {
            setTimeZone(tz);
        }

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        
        mTimeTickSender = PendingIntent.getBroadcastAsUser(context, 0,
                new Intent(Intent.ACTION_TIME_TICK).addFlags(
                        Intent.FLAG_RECEIVER_REGISTERED_ONLY), 0,
                        UserHandle.ALL);
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        mDateChangeSender = PendingIntent.getBroadcastAsUser(context, 0, intent,
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT, UserHandle.ALL);
        
        // now that we have initied the driver schedule the alarm
        mClockReceiver= new ClockReceiver();
        mClockReceiver.scheduleTimeTickEvent();
        mClockReceiver.scheduleDateChangedEvent();
        mUninstallReceiver = new UninstallReceiver();
        
        if (mDescriptor != -1) {
            mWaitThread.start();
        } else {
            Slog.w(TAG, "Failed to open alarm driver. Falling back to a handler.");
        }

        mAppOps = (AppOpsManager)mContext.getSystemService(Context.APP_OPS_SERVICE);
    }
    
    protected void finalize() throws Throwable {
        try {
            close(mDescriptor);
        } finally {
            super.finalize();
        }
    }
    
    public void set(int type, long triggerAtTime, PendingIntent operation) {
        setRepeating(type, triggerAtTime, 0, operation);
    }
    
    public void setRepeating(int type, long triggerAtTime, long interval, 
            PendingIntent operation) {
        if (operation == null) {
            Slog.w(TAG, "set/setRepeating ignored because there is no intent");
            return;
        }

        boolean wakeupFiltered = false;
        if ((type == AlarmManager.RTC_WAKEUP
                        || type == AlarmManager.ELAPSED_REALTIME_WAKEUP)
                && mAppOps.checkOpNoThrow(AppOpsManager.OP_ALARM_WAKEUP,
                        operation.getCreatorUid(),
                        operation.getCreatorPackage())
                != AppOpsManager.MODE_ALLOWED) {

            if (type == AlarmManager.RTC_WAKEUP) {
                type = AlarmManager.RTC;
            } else {
                type = AlarmManager.ELAPSED_REALTIME;
            }

            wakeupFiltered = true;
        }

        synchronized (mLock) {
            Alarm alarm = new Alarm();
            alarm.type = type;
            alarm.when = triggerAtTime;
            alarm.repeatInterval = interval;
            alarm.operation = operation;

            // Remove this alarm if already scheduled.
            removeNoWakeupLocked(operation);
            final boolean foundExistingWakeup = removeWakeupLocked(operation);

            // note AppOp for accounting purposes
            // skip if the alarm already existed
            if (!foundExistingWakeup && wakeupFiltered) {
                mAppOps.noteOpNoThrow(AppOpsManager.OP_ALARM_WAKEUP,
                        operation.getCreatorUid(),
                        operation.getCreatorPackage());
            }

            if (localLOGV) Slog.v(TAG, "set: " + alarm);

            int index = addAlarmLocked(alarm);
            if (index == 0) {
                setLocked(alarm);
            }
        }
    }
    
    public void setInexactRepeating(int type, long triggerAtTime, long interval, 
            PendingIntent operation) {
        if (operation == null) {
            Slog.w(TAG, "setInexactRepeating ignored because there is no intent");
            return;
        }

        if (interval <= 0) {
            Slog.w(TAG, "setInexactRepeating ignored because interval " + interval
                    + " is invalid");
            return;
        }

        // If the requested interval isn't a multiple of 15 minutes, just treat it as exact
        if (interval % QUANTUM != 0) {
            if (localLOGV) Slog.v(TAG, "Interval " + interval + " not a quantum multiple");
            setRepeating(type, triggerAtTime, interval, operation);
            return;
        }

        // Translate times into the ELAPSED timebase for alignment purposes so that
        // alignment never tries to match against wall clock times.
        final boolean isRtc = (type == AlarmManager.RTC || type == AlarmManager.RTC_WAKEUP);
        final long skew = (isRtc)
                ? System.currentTimeMillis() - SystemClock.elapsedRealtime()
                : 0;

        // Slip forward to the next ELAPSED-timebase quantum after the stated time.  If
        // we're *at* a quantum point, leave it alone.
        final long adjustedTriggerTime;
        long offset = (triggerAtTime - skew) % QUANTUM;
        if (offset != 0) {
            adjustedTriggerTime = triggerAtTime - offset + QUANTUM;
        } else {
            adjustedTriggerTime = triggerAtTime;
        }

        // Set the alarm based on the quantum-aligned start time
        if (localLOGV) Slog.v(TAG, "setInexactRepeating: type=" + type + " interval=" + interval
                + " trigger=" + adjustedTriggerTime + " orig=" + triggerAtTime);
        setRepeating(type, adjustedTriggerTime, interval, operation);
    }

    public void setTime(long millis) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME",
                "setTime");

        SystemClock.setCurrentTimeMillis(millis);
    }

    public void setTimeZone(String tz) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME_ZONE",
                "setTimeZone");

        long oldId = Binder.clearCallingIdentity();
        try {
            if (TextUtils.isEmpty(tz)) return;
            TimeZone zone = TimeZone.getTimeZone(tz);
            // Prevent reentrant calls from stepping on each other when writing
            // the time zone property
            boolean timeZoneWasChanged = false;
            synchronized (this) {
                String current = SystemProperties.get(TIMEZONE_PROPERTY);
                if (current == null || !current.equals(zone.getID())) {
                    if (localLOGV) {
                        Slog.v(TAG, "timezone changed: " + current + ", new=" + zone.getID());
                    }
                    timeZoneWasChanged = true;
                    SystemProperties.set(TIMEZONE_PROPERTY, zone.getID());
                }

                // Update the kernel timezone information
                // Kernel tracks time offsets as 'minutes west of GMT'
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
            }

            TimeZone.setDefault(null);

            if (timeZoneWasChanged) {
                Intent intent = new Intent(Intent.ACTION_TIMEZONE_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
                intent.putExtra("time-zone", zone.getID());
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        } finally {
            Binder.restoreCallingIdentity(oldId);
        }
    }
    
    public void remove(PendingIntent operation) {
        if (operation == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(operation);
        }
    }
    
    public void removeLocked(PendingIntent operation) {
        removeLocked(mRtcWakeupAlarms, operation);
        removeLocked(mRtcAlarms, operation);
        removeLocked(mElapsedRealtimeWakeupAlarms, operation);
        removeLocked(mElapsedRealtimeAlarms, operation);
    }

    private boolean removeWakeupLocked(PendingIntent operation) {
        final boolean rtcwake = removeLocked(mRtcWakeupAlarms, operation);
        final boolean realtimewake = removeLocked(mElapsedRealtimeWakeupAlarms, operation);

        return rtcwake || realtimewake;
    }

    private void removeNoWakeupLocked(PendingIntent operation) {
        removeLocked(mRtcAlarms, operation);
        removeLocked(mElapsedRealtimeAlarms, operation);
    }

    private boolean removeLocked(ArrayList<Alarm> alarmList,
            PendingIntent operation) {
        if (alarmList.size() <= 0) {
            return false;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();
        
        boolean found = false;
        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (alarm.operation.equals(operation)) {
                found = true;
                it.remove();
            }
        }

        return found;
    }

    public void removeLocked(String packageName) {
        removeLocked(mRtcWakeupAlarms, packageName);
        removeLocked(mRtcAlarms, packageName);
        removeLocked(mElapsedRealtimeWakeupAlarms, packageName);
        removeLocked(mElapsedRealtimeAlarms, packageName);
    }

    private void removeLocked(ArrayList<Alarm> alarmList,
            String packageName) {
        if (alarmList.size() <= 0) {
            return;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();
        
        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (alarm.operation.getTargetPackage().equals(packageName)) {
                it.remove();
            }
        }
    }

    public void removeUserLocked(int userHandle) {
        removeUserLocked(mRtcWakeupAlarms, userHandle);
        removeUserLocked(mRtcAlarms, userHandle);
        removeUserLocked(mElapsedRealtimeWakeupAlarms, userHandle);
        removeUserLocked(mElapsedRealtimeAlarms, userHandle);
    }

    private void removeUserLocked(ArrayList<Alarm> alarmList, int userHandle) {
        if (alarmList.size() <= 0) {
            return;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();

        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (UserHandle.getUserId(alarm.operation.getCreatorUid()) == userHandle) {
                it.remove();
            }
        }
    }
    
    public boolean lookForPackageLocked(String packageName) {
        return lookForPackageLocked(mRtcWakeupAlarms, packageName)
                || lookForPackageLocked(mRtcAlarms, packageName)
                || lookForPackageLocked(mElapsedRealtimeWakeupAlarms, packageName)
                || lookForPackageLocked(mElapsedRealtimeAlarms, packageName);
    }

    private boolean lookForPackageLocked(ArrayList<Alarm> alarmList, String packageName) {
        for (int i=alarmList.size()-1; i>=0; i--) {
            if (alarmList.get(i).operation.getTargetPackage().equals(packageName)) {
                return true;
            }
        }
        return false;
    }
    
    private ArrayList<Alarm> getAlarmList(int type) {
        switch (type) {
            case AlarmManager.RTC_WAKEUP:              return mRtcWakeupAlarms;
            case AlarmManager.RTC:                     return mRtcAlarms;
            case AlarmManager.ELAPSED_REALTIME_WAKEUP: return mElapsedRealtimeWakeupAlarms;
            case AlarmManager.ELAPSED_REALTIME:        return mElapsedRealtimeAlarms;
        }
        
        return null;
    }
    
    private int addAlarmLocked(Alarm alarm) {
        ArrayList<Alarm> alarmList = getAlarmList(alarm.type);
        
        int index = Collections.binarySearch(alarmList, alarm, mIncreasingTimeOrder);
        if (index < 0) {
            index = 0 - index - 1;
        }
        if (localLOGV) Slog.v(TAG, "Adding alarm " + alarm + " at " + index);
        alarmList.add(index, alarm);

        if (localLOGV) {
            // Display the list of alarms for this alarm type
            Slog.v(TAG, "alarms: " + alarmList.size() + " type: " + alarm.type);
            int position = 0;
            for (Alarm a : alarmList) {
                Time time = new Time();
                time.set(a.when);
                String timeStr = time.format("%b %d %I:%M:%S %p");
                Slog.v(TAG, position + ": " + timeStr
                        + " " + a.operation.getTargetPackage());
                position += 1;
            }
        }
        
        return index;
    }
    
    public long timeToNextAlarm() {
        long nextAlarm = Long.MAX_VALUE;
        synchronized (mLock) {
            for (int i=AlarmManager.RTC_WAKEUP;
                    i<=AlarmManager.ELAPSED_REALTIME; i++) {
                ArrayList<Alarm> alarmList = getAlarmList(i);
                if (alarmList.size() > 0) {
                    Alarm a = alarmList.get(0);
                    if (a.when < nextAlarm) {
                        nextAlarm = a.when;
                    }
                }
            }
        }
        return nextAlarm;
    }
    
    private void setLocked(Alarm alarm)
    {
        if (mDescriptor != -1)
        {
            // The kernel never triggers alarms with negative wakeup times
            // so we ensure they are positive.
            long alarmSeconds, alarmNanoseconds;
            if (alarm.when < 0) {
                alarmSeconds = 0;
                alarmNanoseconds = 0;
            } else {
                alarmSeconds = alarm.when / 1000;
                alarmNanoseconds = (alarm.when % 1000) * 1000 * 1000;
            }
            
            set(mDescriptor, alarm.type, alarmSeconds, alarmNanoseconds);
        }
        else
        {
            Message msg = Message.obtain();
            msg.what = ALARM_EVENT;
            
            mHandler.removeMessages(ALARM_EVENT);
            mHandler.sendMessageAtTime(msg, alarm.when);
        }
    }
    
    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump AlarmManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        
        synchronized (mLock) {
            pw.println("Current Alarm Manager state:");
            if (mRtcWakeupAlarms.size() > 0 || mRtcAlarms.size() > 0) {
                final long now = System.currentTimeMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                pw.println(" ");
                pw.print("  Realtime wakeup (now=");
                        pw.print(sdf.format(new Date(now))); pw.println("):");
                if (mRtcWakeupAlarms.size() > 0) {
                    dumpAlarmList(pw, mRtcWakeupAlarms, "  ", "RTC_WAKEUP", now);
                }
                if (mRtcAlarms.size() > 0) {
                    dumpAlarmList(pw, mRtcAlarms, "  ", "RTC", now);
                }
            }
            if (mElapsedRealtimeWakeupAlarms.size() > 0 || mElapsedRealtimeAlarms.size() > 0) {
                final long now = SystemClock.elapsedRealtime();
                pw.println(" ");
                pw.print("  Elapsed realtime wakeup (now=");
                        TimeUtils.formatDuration(now, pw); pw.println("):");
                if (mElapsedRealtimeWakeupAlarms.size() > 0) {
                    dumpAlarmList(pw, mElapsedRealtimeWakeupAlarms, "  ", "ELAPSED_WAKEUP", now);
                }
                if (mElapsedRealtimeAlarms.size() > 0) {
                    dumpAlarmList(pw, mElapsedRealtimeAlarms, "  ", "ELAPSED", now);
                }
            }

            pw.println();
            pw.print("  Broadcast ref count: "); pw.println(mBroadcastRefCount);
            pw.println();

            if (mLog.dump(pw, "  Recent problems", "    ")) {
                pw.println();
            }

            final FilterStats[] topFilters = new FilterStats[10];
            final Comparator<FilterStats> comparator = new Comparator<FilterStats>() {
                @Override
                public int compare(FilterStats lhs, FilterStats rhs) {
                    if (lhs.aggregateTime < rhs.aggregateTime) {
                        return 1;
                    } else if (lhs.aggregateTime > rhs.aggregateTime) {
                        return -1;
                    }
                    return 0;
                }
            };
            int len = 0;
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                for (Map.Entry<Pair<String, ComponentName>, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    FilterStats fs = fe.getValue();
                    int pos = len > 0
                            ? Arrays.binarySearch(topFilters, 0, len, fs, comparator) : 0;
                    if (pos < 0) {
                        pos = -pos - 1;
                    }
                    if (pos < topFilters.length) {
                        int copylen = topFilters.length - pos - 1;
                        if (copylen > 0) {
                            System.arraycopy(topFilters, pos, topFilters, pos+1, copylen);
                        }
                        topFilters[pos] = fs;
                        if (len < topFilters.length) {
                            len++;
                        }
                    }
                }
            }
            if (len > 0) {
                pw.println("  Top Alarms:");
                for (int i=0; i<len; i++) {
                    FilterStats fs = topFilters[i];
                    pw.print("    ");
                    if (fs.nesting > 0) pw.print("*ACTIVE* ");
                    TimeUtils.formatDuration(fs.aggregateTime, pw);
                    pw.print(" running, "); pw.print(fs.numWakeup);
                    pw.print(" wakeups, "); pw.print(fs.count);
                    pw.print(" alarms: "); pw.print(fs.mBroadcastStats.mPackageName);
                    pw.println();
                    pw.print("      ");
                    if (fs.mTarget.first != null) {
                        pw.print(" act="); pw.print(fs.mTarget.first);
                    }
                    if (fs.mTarget.second != null) {
                        pw.print(" cmp="); pw.print(fs.mTarget.second.toShortString());
                    }
                    pw.println();
                }
            }

            pw.println(" ");
            pw.println("  Alarm Stats:");
            final ArrayList<FilterStats> tmpFilters = new ArrayList<FilterStats>();
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                pw.print("  ");
                if (bs.nesting > 0) pw.print("*ACTIVE* ");
                pw.print(be.getKey());
                pw.print(" "); TimeUtils.formatDuration(bs.aggregateTime, pw);
                        pw.print(" running, "); pw.print(bs.numWakeup);
                        pw.println(" wakeups:");
                tmpFilters.clear();
                for (Map.Entry<Pair<String, ComponentName>, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    tmpFilters.add(fe.getValue());
                }
                Collections.sort(tmpFilters, comparator);
                for (int i=0; i<tmpFilters.size(); i++) {
                    FilterStats fs = tmpFilters.get(i);
                    pw.print("    ");
                            if (fs.nesting > 0) pw.print("*ACTIVE* ");
                            TimeUtils.formatDuration(fs.aggregateTime, pw);
                            pw.print(" "); pw.print(fs.numWakeup);
                            pw.print(" wakes " ); pw.print(fs.count);
                            pw.print(" alarms:");
                            if (fs.mTarget.first != null) {
                                pw.print(" act="); pw.print(fs.mTarget.first);
                            }
                            if (fs.mTarget.second != null) {
                                pw.print(" cmp="); pw.print(fs.mTarget.second.toShortString());
                            }
                            pw.println();
                }
            }
        }
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list,
            String prefix, String label, long now) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            pw.print(prefix); pw.print(label); pw.print(" #"); pw.print(i);
                    pw.print(": "); pw.println(a);
            a.dump(pw, prefix + "  ", now);
        }
    }
    
    private native int init();
    private native void close(int fd);
    private native void set(int fd, int type, long seconds, long nanoseconds);
    private native int waitForAlarm(int fd);
    private native int setKernelTimezone(int fd, int minuteswest);

    private void triggerAlarmsLocked(ArrayList<Alarm> alarmList,
                                     ArrayList<Alarm> triggerList,
                                     long now)
    {
        Iterator<Alarm> it = alarmList.iterator();
        ArrayList<Alarm> repeats = new ArrayList<Alarm>();
        
        while (it.hasNext())
        {
            Alarm alarm = it.next();

            if (localLOGV) Slog.v(TAG, "Checking active alarm when=" + alarm.when + " " + alarm);

            if (alarm.when > now) {
                // don't fire alarms in the future
                break;
            }
            
            // If the alarm is late, then print a warning message.
            // Note that this can happen if the user creates a new event on
            // the Calendar app with a reminder that is in the past. In that
            // case, the reminder alarm will fire immediately.
            if (localLOGV && now - alarm.when > LATE_ALARM_THRESHOLD) {
                Slog.v(TAG, "alarm is late! alarm time: " + alarm.when
                        + " now: " + now + " delay (in seconds): "
                        + (now - alarm.when) / 1000);
            }

            // Recurring alarms may have passed several alarm intervals while the
            // phone was asleep or off, so pass a trigger count when sending them.
            if (localLOGV) Slog.v(TAG, "Alarm triggering: " + alarm);
            alarm.count = 1;
            if (alarm.repeatInterval > 0) {
                // this adjustment will be zero if we're late by
                // less than one full repeat interval
                alarm.count += (now - alarm.when) / alarm.repeatInterval;
            }
            triggerList.add(alarm);
            
            // remove the alarm from the list
            it.remove();
            
            // if it repeats queue it up to be read-added to the list
            if (alarm.repeatInterval > 0) {
                repeats.add(alarm);
            }
        }

        // reset any repeating alarms.
        it = repeats.iterator();
        while (it.hasNext()) {
            Alarm alarm = it.next();
            alarm.when += alarm.count * alarm.repeatInterval;
            addAlarmLocked(alarm);
        }
        
        if (alarmList.size() > 0) {
            setLocked(alarmList.get(0));
        }
    }
    
    /**
     * This Comparator sorts Alarms into increasing time order.
     */
    public static class IncreasingTimeOrder implements Comparator<Alarm> {
        public int compare(Alarm a1, Alarm a2) {
            long when1 = a1.when;
            long when2 = a2.when;
            if (when1 - when2 > 0) {
                return 1;
            }
            if (when1 - when2 < 0) {
                return -1;
            }
            return 0;
        }
    }
    
    private static class Alarm {
        public int type;
        public int count;
        public long when;
        public long repeatInterval;
        public PendingIntent operation;
        
        public Alarm() {
            when = 0;
            repeatInterval = 0;
            operation = null;
        }
        
        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(128);
            sb.append("Alarm{");
            sb.append(Integer.toHexString(System.identityHashCode(this)));
            sb.append(" type ");
            sb.append(type);
            sb.append(" ");
            sb.append(operation.getTargetPackage());
            sb.append('}');
            return sb.toString();
        }

        public void dump(PrintWriter pw, String prefix, long now) {
            pw.print(prefix); pw.print("type="); pw.print(type);
                    pw.print(" when="); TimeUtils.formatDuration(when, now, pw);
                    pw.print(" repeatInterval="); pw.print(repeatInterval);
                    pw.print(" count="); pw.println(count);
            pw.print(prefix); pw.print("operation="); pw.println(operation);
        }
    }
    
    private class AlarmThread extends Thread
    {
        public AlarmThread()
        {
            super("AlarmManager");
        }
        
        public void run()
        {
            while (true)
            {
                int result = waitForAlarm(mDescriptor);
                
                ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                
                if ((result & TIME_CHANGED_MASK) != 0) {
                    remove(mTimeTickSender);
                    mClockReceiver.scheduleTimeTickEvent();
                    Intent intent = new Intent(Intent.ACTION_TIME_CHANGED);
                    intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                            | Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                }
                
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    if (localLOGV) Slog.v(
                        TAG, "Checking for alarms... rtc=" + nowRTC
                        + ", elapsed=" + nowELAPSED);

                    if ((result & RTC_WAKEUP_MASK) != 0)
                        triggerAlarmsLocked(mRtcWakeupAlarms, triggerList, nowRTC);
                    
                    if ((result & RTC_MASK) != 0)
                        triggerAlarmsLocked(mRtcAlarms, triggerList, nowRTC);
                    
                    if ((result & ELAPSED_REALTIME_WAKEUP_MASK) != 0)
                        triggerAlarmsLocked(mElapsedRealtimeWakeupAlarms, triggerList, nowELAPSED);
                    
                    if ((result & ELAPSED_REALTIME_MASK) != 0)
                        triggerAlarmsLocked(mElapsedRealtimeAlarms, triggerList, nowELAPSED);
                    
                    // now trigger the alarms
                    Iterator<Alarm> it = triggerList.iterator();
                    while (it.hasNext()) {
                        Alarm alarm = it.next();
                        try {
                            if (localLOGV) Slog.v(TAG, "sending alarm " + alarm);
                            alarm.operation.send(mContext, 0,
                                    mBackgroundIntent.putExtra(
                                            Intent.EXTRA_ALARM_COUNT, alarm.count),
                                    mResultReceiver, mHandler);
                            
                            // we have an active broadcast so stay awake.
                            if (mBroadcastRefCount == 0) {
                                setWakelockWorkSource(alarm.operation);
                                mWakeLock.acquire();
                            }
                            final InFlight inflight = new InFlight(AlarmManagerService.this,
                                    alarm.operation);
                            mInFlight.add(inflight);
                            mBroadcastRefCount++;

                            final BroadcastStats bs = inflight.mBroadcastStats;
                            bs.count++;
                            if (bs.nesting == 0) {
                                bs.nesting = 1;
                                bs.startTime = nowELAPSED;
                            } else {
                                bs.nesting++;
                            }
                            final FilterStats fs = inflight.mFilterStats;
                            fs.count++;
                            if (fs.nesting == 0) {
                                fs.nesting = 1;
                                fs.startTime = nowELAPSED;
                            } else {
                                fs.nesting++;
                            }
                            if (alarm.type == AlarmManager.ELAPSED_REALTIME_WAKEUP
                                    || alarm.type == AlarmManager.RTC_WAKEUP) {
                                bs.numWakeup++;
                                fs.numWakeup++;
                                ActivityManagerNative.noteWakeupAlarm(
                                        alarm.operation);
                                // AppOps accounting
                                mAppOps.noteOpNoThrow(AppOpsManager.OP_ALARM_WAKEUP,
                                        alarm.operation.getCreatorUid(),
                                        alarm.operation.getCreatorPackage());
                            }
                        } catch (PendingIntent.CanceledException e) {
                            if (alarm.repeatInterval > 0) {
                                // This IntentSender is no longer valid, but this
                                // is a repeating alarm, so toss the hoser.
                                remove(alarm.operation);
                            }
                        } catch (RuntimeException e) {
                            Slog.w(TAG, "Failure sending alarm.", e);
                        }
                    }
                }
            }
        }
    }

    void setWakelockWorkSource(PendingIntent pi) {
        try {
            final int uid = ActivityManagerNative.getDefault()
                    .getUidForIntentSender(pi.getTarget());
            if (uid >= 0) {
                mWakeLock.setWorkSource(new WorkSource(uid));
                return;
            }
        } catch (Exception e) {
        }

        // Something went wrong; fall back to attributing the lock to the OS
        mWakeLock.setWorkSource(null);
    }

    private class AlarmHandler extends Handler {
        public static final int ALARM_EVENT = 1;
        public static final int MINUTE_CHANGE_EVENT = 2;
        public static final int DATE_CHANGE_EVENT = 3;
        
        public AlarmHandler() {
        }
        
        public void handleMessage(Message msg) {
            if (msg.what == ALARM_EVENT) {
                ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    triggerAlarmsLocked(mRtcWakeupAlarms, triggerList, nowRTC);
                    triggerAlarmsLocked(mRtcAlarms, triggerList, nowRTC);
                    triggerAlarmsLocked(mElapsedRealtimeWakeupAlarms, triggerList, nowRTC);
                    triggerAlarmsLocked(mElapsedRealtimeAlarms, triggerList, nowRTC);
                }
                
                // now trigger the alarms without the lock held
                Iterator<Alarm> it = triggerList.iterator();
                while (it.hasNext())
                {
                    Alarm alarm = it.next();
                    try {
                        alarm.operation.send();
                    } catch (PendingIntent.CanceledException e) {
                        if (alarm.repeatInterval > 0) {
                            // This IntentSender is no longer valid, but this
                            // is a repeating alarm, so toss the hoser.
                            remove(alarm.operation);
                        }
                    }
                }
            }
        }
    }
    
    class ClockReceiver extends BroadcastReceiver {
        public ClockReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            mContext.registerReceiver(this, filter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
            	scheduleTimeTickEvent();
            } else if (intent.getAction().equals(Intent.ACTION_DATE_CHANGED)) {
                // Since the kernel does not keep track of DST, we need to
                // reset the TZ information at the beginning of each day
                // based off of the current Zone gmt offset + userspace tracked
                // daylight savings information.
                TimeZone zone = TimeZone.getTimeZone(SystemProperties.get(TIMEZONE_PROPERTY));
                int gmtOffset = zone.getOffset(System.currentTimeMillis());
                setKernelTimezone(mDescriptor, -(gmtOffset / 60000));
            	scheduleDateChangedEvent();
            }
        }
        
        public void scheduleTimeTickEvent() {
            final long currentTime = System.currentTimeMillis();
            final long nextTime = 60000 * ((currentTime / 60000) + 1);

            // Schedule this event for the amount of time that it would take to get to
            // the top of the next minute.
            final long tickEventDelay = nextTime - currentTime;

            set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + tickEventDelay,
                    mTimeTickSender);
        }
	
        public void scheduleDateChangedEvent() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
      
            set(AlarmManager.RTC, calendar.getTimeInMillis(), mDateChangeSender);
        }
    }
    
    class UninstallReceiver extends BroadcastReceiver {
        public UninstallReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            filter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
            filter.addDataScheme("package");
            mContext.registerReceiver(this, filter);
             // Register for events related to sdcard installation.
            IntentFilter sdFilter = new IntentFilter();
            sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
            sdFilter.addAction(Intent.ACTION_USER_STOPPED);
            mContext.registerReceiver(this, sdFilter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                String action = intent.getAction();
                String pkgList[] = null;
                if (Intent.ACTION_QUERY_PACKAGE_RESTART.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                    for (String packageName : pkgList) {
                        if (lookForPackageLocked(packageName)) {
                            setResultCode(Activity.RESULT_OK);
                            return;
                        }
                    }
                    return;
                } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                    pkgList = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                } else if (Intent.ACTION_USER_STOPPED.equals(action)) {
                    int userHandle = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);
                    if (userHandle >= 0) {
                        removeUserLocked(userHandle);
                    }
                } else {
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                            && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        // This package is being updated; don't kill its alarms.
                        return;
                    }
                    Uri data = intent.getData();
                    if (data != null) {
                        String pkg = data.getSchemeSpecificPart();
                        if (pkg != null) {
                            pkgList = new String[]{pkg};
                        }
                    }
                }
                if (pkgList != null && (pkgList.length > 0)) {
                    for (String pkg : pkgList) {
                        removeLocked(pkg);
                        mBroadcastStats.remove(pkg);
                    }
                }
            }
        }
    }
    
    private final BroadcastStats getStatsLocked(PendingIntent pi) {
        String pkg = pi.getTargetPackage();
        BroadcastStats bs = mBroadcastStats.get(pkg);
        if (bs == null) {
            bs = new BroadcastStats(pkg);
            mBroadcastStats.put(pkg, bs);
        }
        return bs;
    }

    class ResultReceiver implements PendingIntent.OnFinished {
        public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            synchronized (mLock) {
                InFlight inflight = null;
                for (int i=0; i<mInFlight.size(); i++) {
                    if (mInFlight.get(i).mPendingIntent == pi) {
                        inflight = mInFlight.remove(i);
                        break;
                    }
                }
                if (inflight != null) {
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    BroadcastStats bs = inflight.mBroadcastStats;
                    bs.nesting--;
                    if (bs.nesting <= 0) {
                        bs.nesting = 0;
                        bs.aggregateTime += nowELAPSED - bs.startTime;
                    }
                    FilterStats fs = inflight.mFilterStats;
                    fs.nesting--;
                    if (fs.nesting <= 0) {
                        fs.nesting = 0;
                        fs.aggregateTime += nowELAPSED - fs.startTime;
                    }
                } else {
                    mLog.w("No in-flight alarm for " + pi + " " + intent);
                }
                mBroadcastRefCount--;
                if (mBroadcastRefCount == 0) {
                    mWakeLock.release();
                    if (mInFlight.size() > 0) {
                        mLog.w("Finished all broadcasts with " + mInFlight.size()
                                + " remaining inflights");
                        for (int i=0; i<mInFlight.size(); i++) {
                            mLog.w("  Remaining #" + i + ": " + mInFlight.get(i));
                        }
                        mInFlight.clear();
                    }
                } else {
                    // the next of our alarms is now in flight.  reattribute the wakelock.
                    if (mInFlight.size() > 0) {
                        setWakelockWorkSource(mInFlight.get(0).mPendingIntent);
                    } else {
                        // should never happen
                        mLog.w("Alarm wakelock still held but sent queue empty");
                        mWakeLock.setWorkSource(null);
                    }
                }
            }
        }
    }
}
