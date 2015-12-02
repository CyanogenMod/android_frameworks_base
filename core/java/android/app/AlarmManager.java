/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.app;

import android.annotation.SdkConstant;
import android.annotation.SystemApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import libcore.util.ZoneInfoDB;

import java.io.IOException;

/**
 * This class provides access to the system alarm services.  These allow you
 * to schedule your application to be run at some point in the future.  When
 * an alarm goes off, the {@link Intent} that had been registered for it
 * is broadcast by the system, automatically starting the target application
 * if it is not already running.  Registered alarms are retained while the
 * device is asleep (and can optionally wake the device up if they go off
 * during that time), but will be cleared if it is turned off and rebooted.
 * 
 * <p>The Alarm Manager holds a CPU wake lock as long as the alarm receiver's
 * onReceive() method is executing. This guarantees that the phone will not sleep
 * until you have finished handling the broadcast. Once onReceive() returns, the
 * Alarm Manager releases this wake lock. This means that the phone will in some
 * cases sleep as soon as your onReceive() method completes.  If your alarm receiver
 * called {@link android.content.Context#startService Context.startService()}, it
 * is possible that the phone will sleep before the requested service is launched.
 * To prevent this, your BroadcastReceiver and Service will need to implement a
 * separate wake lock policy to ensure that the phone continues running until the
 * service becomes available.
 *
 * <p><b>Note: The Alarm Manager is intended for cases where you want to have
 * your application code run at a specific time, even if your application is
 * not currently running.  For normal timing operations (ticks, timeouts,
 * etc) it is easier and much more efficient to use
 * {@link android.os.Handler}.</b>
 *
 * <p class="caution"><strong>Note:</strong> Beginning with API 19
 * ({@link android.os.Build.VERSION_CODES#KITKAT}) alarm delivery is inexact:
 * the OS will shift alarms in order to minimize wakeups and battery use.  There are
 * new APIs to support applications which need strict delivery guarantees; see
 * {@link #setWindow(int, long, long, PendingIntent)} and
 * {@link #setExact(int, long, PendingIntent)}.  Applications whose {@code targetSdkVersion}
 * is earlier than API 19 will continue to see the previous behavior in which all
 * alarms are delivered exactly when requested.
 *
 * <p>You do not
 * instantiate this class directly; instead, retrieve it through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.ALARM_SERVICE)}.
 */
public class AlarmManager {
    /**
     * Alarm time in {@link System#currentTimeMillis System.currentTimeMillis()}
     * (wall clock time in UTC), which will wake up the device when
     * it goes off.
     */
    public static final int RTC_WAKEUP = 0;
    /**
     * Alarm time in {@link System#currentTimeMillis System.currentTimeMillis()}
     * (wall clock time in UTC).  This alarm does not wake the
     * device up; if it goes off while the device is asleep, it will not be
     * delivered until the next time the device wakes up.
     */
    public static final int RTC = 1;
    /**
     * Alarm time in {@link android.os.SystemClock#elapsedRealtime
     * SystemClock.elapsedRealtime()} (time since boot, including sleep),
     * which will wake up the device when it goes off.
     */
    public static final int ELAPSED_REALTIME_WAKEUP = 2;
    /**
     * Alarm time in {@link android.os.SystemClock#elapsedRealtime
     * SystemClock.elapsedRealtime()} (time since boot, including sleep).
     * This alarm does not wake the device up; if it goes off while the device
     * is asleep, it will not be delivered until the next time the device
     * wakes up.
     */
    public static final int ELAPSED_REALTIME = 3;

    /** @hide
     * Alarm time in {@link System#currentTimeMillis System.currentTimeMillis()}
     * (wall clock time in UTC), which will wake up the device when
     * it goes off. And it will power on the devices when it shuts down.
     * Set as 5 to make it be compatible with android_alarm_type.
     */
    public static final int RTC_POWEROFF_WAKEUP = 5;

    /**
     * Broadcast Action: Sent after the value returned by
     * {@link #getNextAlarmClock()} has changed.
     *
     * <p class="note">This is a protected intent that can only be sent by the system.
     * It is only sent to registered receivers.</p>
     */
    @SdkConstant(SdkConstant.SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_NEXT_ALARM_CLOCK_CHANGED =
            "android.app.action.NEXT_ALARM_CLOCK_CHANGED";

    /** @hide */
    public static final long WINDOW_EXACT = 0;
    /** @hide */
    public static final long WINDOW_HEURISTIC = -1;

    /**
     * Flag for alarms: this is to be a stand-alone alarm, that should not be batched with
     * other alarms.
     * @hide
     */
    public static final int FLAG_STANDALONE = 1<<0;

    /**
     * Flag for alarms: this alarm would like to wake the device even if it is idle.  This
     * is, for example, an alarm for an alarm clock.
     * @hide
     */
    public static final int FLAG_WAKE_FROM_IDLE = 1<<1;

    /**
     * Flag for alarms: this alarm would like to still execute even if the device is
     * idle.  This won't bring the device out of idle, just allow this specific alarm to
     * run.  Note that this means the actual time this alarm goes off can be inconsistent
     * with the time of non-allow-while-idle alarms (it could go earlier than the time
     * requested by another alarm).
     *
     * @hide
     */
    public static final int FLAG_ALLOW_WHILE_IDLE = 1<<2;

    /**
     * Flag for alarms: same as {@link #FLAG_ALLOW_WHILE_IDLE}, but doesn't have restrictions
     * on how frequently it can be scheduled.  Only available (and automatically applied) to
     * system alarms.
     *
     * @hide
     */
    public static final int FLAG_ALLOW_WHILE_IDLE_UNRESTRICTED = 1<<3;

    /**
     * Flag for alarms: this alarm marks the point where we would like to come out of idle
     * mode.  It may be moved by the alarm manager to match the first wake-from-idle alarm.
     * Scheduling an alarm with this flag puts the alarm manager in to idle mode, where it
     * avoids scheduling any further alarms until the marker alarm is executed.
     * @hide
     */
    public static final int FLAG_IDLE_UNTIL = 1<<4;

    private final IAlarmManager mService;
    private final boolean mAlwaysExact;
    private final int mTargetSdkVersion;


    /**
     * package private on purpose
     */
    AlarmManager(IAlarmManager service, Context ctx) {
        mService = service;

        mTargetSdkVersion = ctx.getApplicationInfo().targetSdkVersion;
        mAlwaysExact = (mTargetSdkVersion < Build.VERSION_CODES.KITKAT);
    }

    private long legacyExactLength() {
        return (mAlwaysExact ? WINDOW_EXACT : WINDOW_HEURISTIC);
    }

    /**
     * <p>Schedule an alarm.  <b>Note: for timing operations (ticks, timeouts,
     * etc) it is easier and much more efficient to use {@link android.os.Handler}.</b>
     * If there is already an alarm scheduled for the same IntentSender, that previous
     * alarm will first be canceled.
     *
     * <p>If the stated trigger time is in the past, the alarm will be triggered
     * immediately.  If there is already an alarm for this Intent
     * scheduled (with the equality of two intents being defined by
     * {@link Intent#filterEquals}), then it will be removed and replaced by
     * this one.
     *
     * <p>
     * The alarm is an Intent broadcast that goes to a broadcast receiver that
     * you registered with {@link android.content.Context#registerReceiver}
     * or through the &lt;receiver&gt; tag in an AndroidManifest.xml file.
     *
     * <p>
     * Alarm intents are delivered with a data extra of type int called
     * {@link Intent#EXTRA_ALARM_COUNT Intent.EXTRA_ALARM_COUNT} that indicates
     * how many past alarm events have been accumulated into this intent
     * broadcast.  Recurring alarms that have gone undelivered because the
     * phone was asleep may have a count greater than one when delivered.  
     *
     * <div class="note">
     * <p>
     * <b>Note:</b> Beginning in API 19, the trigger time passed to this method
     * is treated as inexact: the alarm will not be delivered before this time, but
     * may be deferred and delivered some time later.  The OS will use
     * this policy in order to "batch" alarms together across the entire system,
     * minimizing the number of times the device needs to "wake up" and minimizing
     * battery use.  In general, alarms scheduled in the near future will not
     * be deferred as long as alarms scheduled far in the future.
     *
     * <p>
     * With the new batching policy, delivery ordering guarantees are not as
     * strong as they were previously.  If the application sets multiple alarms,
     * it is possible that these alarms' <em>actual</em> delivery ordering may not match
     * the order of their <em>requested</em> delivery times.  If your application has
     * strong ordering requirements there are other APIs that you can use to get
     * the necessary behavior; see {@link #setWindow(int, long, long, PendingIntent)}
     * and {@link #setExact(int, long, PendingIntent)}.
     *
     * <p>
     * Applications whose {@code targetSdkVersion} is before API 19 will
     * continue to get the previous alarm behavior: all of their scheduled alarms
     * will be treated as exact.
     * </div>
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param triggerAtMillis time in milliseconds that the alarm should go
     * off, using the appropriate clock (depending on the alarm type).
     * @param operation Action to perform when the alarm goes off;
     * typically comes from {@link PendingIntent#getBroadcast
     * IntentSender.getBroadcast()}.
     *
     * @see android.os.Handler
     * @see #setExact
     * @see #setRepeating
     * @see #setWindow
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     */
    public void set(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, legacyExactLength(), 0, 0, operation, null, null);
    }

    /**
     * Schedule a repeating alarm.  <b>Note: for timing operations (ticks,
     * timeouts, etc) it is easier and much more efficient to use
     * {@link android.os.Handler}.</b>  If there is already an alarm scheduled
     * for the same IntentSender, it will first be canceled.
     *
     * <p>Like {@link #set}, except you can also supply a period at which
     * the alarm will automatically repeat.  This alarm continues
     * repeating until explicitly removed with {@link #cancel}.  If the stated
     * trigger time is in the past, the alarm will be triggered immediately, with an
     * alarm count depending on how far in the past the trigger time is relative
     * to the repeat interval.
     *
     * <p>If an alarm is delayed (by system sleep, for example, for non
     * _WAKEUP alarm types), a skipped repeat will be delivered as soon as
     * possible.  After that, future alarms will be delivered according to the
     * original schedule; they do not drift over time.  For example, if you have
     * set a recurring alarm for the top of every hour but the phone was asleep
     * from 7:45 until 8:45, an alarm will be sent as soon as the phone awakens,
     * then the next alarm will be sent at 9:00.
     * 
     * <p>If your application wants to allow the delivery times to drift in 
     * order to guarantee that at least a certain time interval always elapses
     * between alarms, then the approach to take is to use one-time alarms, 
     * scheduling the next one yourself when handling each alarm delivery.
     *
     * <p class="note">
     * <b>Note:</b> as of API 19, all repeating alarms are inexact.  If your
     * application needs precise delivery times then it must use one-time
     * exact alarms, rescheduling each time as described above. Legacy applications
     * whose {@code targetSdkVersion} is earlier than API 19 will continue to have all
     * of their alarms, including repeating alarms, treated as exact.
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param triggerAtMillis time in milliseconds that the alarm should first
     * go off, using the appropriate clock (depending on the alarm type).
     * @param intervalMillis interval in milliseconds between subsequent repeats
     * of the alarm.
     * @param operation Action to perform when the alarm goes off;
     * typically comes from {@link PendingIntent#getBroadcast
     * IntentSender.getBroadcast()}.
     *
     * @see android.os.Handler
     * @see #set
     * @see #setExact
     * @see #setWindow
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     */
    public void setRepeating(int type, long triggerAtMillis,
            long intervalMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, legacyExactLength(), intervalMillis, 0, operation, null,
                null);
    }

    /**
     * Schedule an alarm to be delivered within a given window of time.  This method
     * is similar to {@link #set(int, long, PendingIntent)}, but allows the
     * application to precisely control the degree to which its delivery might be
     * adjusted by the OS. This method allows an application to take advantage of the
     * battery optimizations that arise from delivery batching even when it has
     * modest timeliness requirements for its alarms.
     *
     * <p>
     * This method can also be used to achieve strict ordering guarantees among
     * multiple alarms by ensuring that the windows requested for each alarm do
     * not intersect.
     *
     * <p>
     * When precise delivery is not required, applications should use the standard
     * {@link #set(int, long, PendingIntent)} method.  This will give the OS the most
     * flexibility to minimize wakeups and battery use.  For alarms that must be delivered
     * at precisely-specified times with no acceptable variation, applications can use
     * {@link #setExact(int, long, PendingIntent)}.
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param windowStartMillis The earliest time, in milliseconds, that the alarm should
     *        be delivered, expressed in the appropriate clock's units (depending on the alarm
     *        type).
     * @param windowLengthMillis The length of the requested delivery window,
     *        in milliseconds.  The alarm will be delivered no later than this many
     *        milliseconds after {@code windowStartMillis}.  Note that this parameter
     *        is a <i>duration,</i> not the timestamp of the end of the window.
     * @param operation Action to perform when the alarm goes off;
     *        typically comes from {@link PendingIntent#getBroadcast
     *        IntentSender.getBroadcast()}.
     *
     * @see #set
     * @see #setExact
     * @see #setRepeating
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     */
    public void setWindow(int type, long windowStartMillis, long windowLengthMillis,
            PendingIntent operation) {
        setImpl(type, windowStartMillis, windowLengthMillis, 0, 0, operation, null, null);
    }

    /**
     * Schedule an alarm to be delivered precisely at the stated time.
     *
     * <p>
     * This method is like {@link #set(int, long, PendingIntent)}, but does not permit
     * the OS to adjust the delivery time.  The alarm will be delivered as nearly as
     * possible to the requested trigger time.
     *
     * <p>
     * <b>Note:</b> only alarms for which there is a strong demand for exact-time
     * delivery (such as an alarm clock ringing at the requested time) should be
     * scheduled as exact.  Applications are strongly discouraged from using exact
     * alarms unnecessarily as they reduce the OS's ability to minimize battery use.
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param triggerAtMillis time in milliseconds that the alarm should go
     *        off, using the appropriate clock (depending on the alarm type).
     * @param operation Action to perform when the alarm goes off;
     *        typically comes from {@link PendingIntent#getBroadcast
     *        IntentSender.getBroadcast()}.
     *
     * @see #set
     * @see #setRepeating
     * @see #setWindow
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     */
    public void setExact(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, WINDOW_EXACT, 0, 0, operation, null, null);
    }

    /**
     * Schedule an idle-until alarm, which will keep the alarm manager idle until
     * the given time.
     * @hide
     */
    public void setIdleUntil(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, WINDOW_EXACT, 0, FLAG_IDLE_UNTIL, operation, null, null);
    }

    /**
     * Schedule an alarm that represents an alarm clock.
     *
     * The system may choose to display information about this alarm to the user.
     *
     * <p>
     * This method is like {@link #setExact(int, long, PendingIntent)}, but implies
     * {@link #RTC_WAKEUP}.
     *
     * @param info
     * @param operation Action to perform when the alarm goes off;
     *        typically comes from {@link PendingIntent#getBroadcast
     *        IntentSender.getBroadcast()}.
     *
     * @see #set
     * @see #setRepeating
     * @see #setWindow
     * @see #setExact
     * @see #cancel
     * @see #getNextAlarmClock()
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     */
    public void setAlarmClock(AlarmClockInfo info, PendingIntent operation) {
        setImpl(RTC_WAKEUP, info.getTriggerTime(), WINDOW_EXACT, 0, 0, operation, null, info);
    }

    /** @hide */
    @SystemApi
    public void set(int type, long triggerAtMillis, long windowMillis, long intervalMillis,
            PendingIntent operation, WorkSource workSource) {
        setImpl(type, triggerAtMillis, windowMillis, intervalMillis, 0, operation, workSource,
                null);
    }

    private void setImpl(int type, long triggerAtMillis, long windowMillis, long intervalMillis,
            int flags, PendingIntent operation, WorkSource workSource, AlarmClockInfo alarmClock) {
        if (triggerAtMillis < 0) {
            /* NOTYET
            if (mAlwaysExact) {
                // Fatal error for KLP+ apps to use negative trigger times
                throw new IllegalArgumentException("Invalid alarm trigger time "
                        + triggerAtMillis);
            }
            */
            triggerAtMillis = 0;
        }

        try {
            mService.set(type, triggerAtMillis, windowMillis, intervalMillis, flags, operation,
                    workSource, alarmClock);
        } catch (RemoteException ex) {
        }
    }

    /**
     * Available inexact recurrence interval recognized by
     * {@link #setInexactRepeating(int, long, long, PendingIntent)}
     * when running on Android prior to API 19.
     */
    public static final long INTERVAL_FIFTEEN_MINUTES = 15 * 60 * 1000;

    /**
     * Available inexact recurrence interval recognized by
     * {@link #setInexactRepeating(int, long, long, PendingIntent)}
     * when running on Android prior to API 19.
     */
    public static final long INTERVAL_HALF_HOUR = 2*INTERVAL_FIFTEEN_MINUTES;

    /**
     * Available inexact recurrence interval recognized by
     * {@link #setInexactRepeating(int, long, long, PendingIntent)}
     * when running on Android prior to API 19.
     */
    public static final long INTERVAL_HOUR = 2*INTERVAL_HALF_HOUR;

    /**
     * Available inexact recurrence interval recognized by
     * {@link #setInexactRepeating(int, long, long, PendingIntent)}
     * when running on Android prior to API 19.
     */
    public static final long INTERVAL_HALF_DAY = 12*INTERVAL_HOUR;

    /**
     * Available inexact recurrence interval recognized by
     * {@link #setInexactRepeating(int, long, long, PendingIntent)}
     * when running on Android prior to API 19.
     */
    public static final long INTERVAL_DAY = 2*INTERVAL_HALF_DAY;

    /**
     * Schedule a repeating alarm that has inexact trigger time requirements;
     * for example, an alarm that repeats every hour, but not necessarily at
     * the top of every hour.  These alarms are more power-efficient than
     * the strict recurrences traditionally supplied by {@link #setRepeating}, since the
     * system can adjust alarms' delivery times to cause them to fire simultaneously,
     * avoiding waking the device from sleep more than necessary.
     *
     * <p>Your alarm's first trigger will not be before the requested time,
     * but it might not occur for almost a full interval after that time.  In
     * addition, while the overall period of the repeating alarm will be as
     * requested, the time between any two successive firings of the alarm
     * may vary.  If your application demands very low jitter, use
     * one-shot alarms with an appropriate window instead; see {@link
     * #setWindow(int, long, long, PendingIntent)} and
     * {@link #setExact(int, long, PendingIntent)}.
     *
     * <p class="note">
     * As of API 19, all repeating alarms are inexact.  Because this method has
     * been available since API 3, your application can safely call it and be
     * assured that it will get similar behavior on both current and older versions
     * of Android.
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param triggerAtMillis time in milliseconds that the alarm should first
     * go off, using the appropriate clock (depending on the alarm type).  This
     * is inexact: the alarm will not fire before this time, but there may be a
     * delay of almost an entire alarm interval before the first invocation of
     * the alarm.
     * @param intervalMillis interval in milliseconds between subsequent repeats
     * of the alarm.  Prior to API 19, if this is one of INTERVAL_FIFTEEN_MINUTES,
     * INTERVAL_HALF_HOUR, INTERVAL_HOUR, INTERVAL_HALF_DAY, or INTERVAL_DAY
     * then the alarm will be phase-aligned with other alarms to reduce the
     * number of wakeups.  Otherwise, the alarm will be set as though the
     * application had called {@link #setRepeating}.  As of API 19, all repeating
     * alarms will be inexact and subject to batching with other alarms regardless
     * of their stated repeat interval.
     * @param operation Action to perform when the alarm goes off;
     * typically comes from {@link PendingIntent#getBroadcast
     * IntentSender.getBroadcast()}.
     *
     * @see android.os.Handler
     * @see #set
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     * @see #INTERVAL_FIFTEEN_MINUTES
     * @see #INTERVAL_HALF_HOUR
     * @see #INTERVAL_HOUR
     * @see #INTERVAL_HALF_DAY
     * @see #INTERVAL_DAY
     */
    public void setInexactRepeating(int type, long triggerAtMillis,
            long intervalMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, WINDOW_HEURISTIC, intervalMillis, 0, operation, null, null);
    }

    /**
     * Like {@link #set(int, long, PendingIntent)}, but this alarm will be allowed to execute
     * even when the system is in low-power idle modes.  This type of alarm must <b>only</b>
     * be used for situations where it is actually required that the alarm go off while in
     * idle -- a reasonable example would be for a calendar notification that should make a
     * sound so the user is aware of it.  When the alarm is dispatched, the app will also be
     * added to the system's temporary whitelist for approximately 10 seconds to allow that
     * application to acquire further wake locks in which to complete its work.</p>
     *
     * <p>These alarms can significantly impact the power use
     * of the device when idle (and thus cause significant battery blame to the app scheduling
     * them), so they should be used with care.  To reduce abuse, there are restrictions on how
     * frequently these alarms will go off for a particular application.
     * Under normal system operation, it will not dispatch these
     * alarms more than about every minute (at which point every such pending alarm is
     * dispatched); when in low-power idle modes this duration may be significantly longer,
     * such as 15 minutes.</p>
     *
     * <p>Unlike other alarms, the system is free to reschedule this type of alarm to happen
     * out of order with any other alarms, even those from the same app.  This will clearly happen
     * when the device is idle (since this alarm can go off while idle, when any other alarms
     * from the app will be held until later), but may also happen even when not idle.</p>
     *
     * <p>Regardless of the app's target SDK version, this call always allows batching of the
     * alarm.</p>
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param triggerAtMillis time in milliseconds that the alarm should go
     * off, using the appropriate clock (depending on the alarm type).
     * @param operation Action to perform when the alarm goes off;
     * typically comes from {@link PendingIntent#getBroadcast
     * IntentSender.getBroadcast()}.
     *
     * @see #set(int, long, PendingIntent)
     * @see #setExactAndAllowWhileIdle
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     */
    public void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, WINDOW_HEURISTIC, 0, FLAG_ALLOW_WHILE_IDLE, operation,
                null, null);
    }

    /**
     * Like {@link #setExact(int, long, PendingIntent)}, but this alarm will be allowed to execute
     * even when the system is in low-power idle modes.  If you don't need exact scheduling of
     * the alarm but still need to execute while idle, consider using
     * {@link #setAndAllowWhileIdle}.  This type of alarm must <b>only</b>
     * be used for situations where it is actually required that the alarm go off while in
     * idle -- a reasonable example would be for a calendar notification that should make a
     * sound so the user is aware of it.  When the alarm is dispatched, the app will also be
     * added to the system's temporary whitelist for approximately 10 seconds to allow that
     * application to acquire further wake locks in which to complete its work.</p>
     *
     * <p>These alarms can significantly impact the power use
     * of the device when idle (and thus cause significant battery blame to the app scheduling
     * them), so they should be used with care.  To reduce abuse, there are restrictions on how
     * frequently these alarms will go off for a particular application.
     * Under normal system operation, it will not dispatch these
     * alarms more than about every minute (at which point every such pending alarm is
     * dispatched); when in low-power idle modes this duration may be significantly longer,
     * such as 15 minutes.</p>
     *
     * <p>Unlike other alarms, the system is free to reschedule this type of alarm to happen
     * out of order with any other alarms, even those from the same app.  This will clearly happen
     * when the device is idle (since this alarm can go off while idle, when any other alarms
     * from the app will be held until later), but may also happen even when not idle.
     * Note that the OS will allow itself more flexibility for scheduling these alarms than
     * regular exact alarms, since the application has opted into this behavior.  When the
     * device is idle it may take even more liberties with scheduling in order to optimize
     * for battery life.</p>
     *
     * @param type One of {@link #ELAPSED_REALTIME}, {@link #ELAPSED_REALTIME_WAKEUP},
     *        {@link #RTC}, or {@link #RTC_WAKEUP}.
     * @param triggerAtMillis time in milliseconds that the alarm should go
     *        off, using the appropriate clock (depending on the alarm type).
     * @param operation Action to perform when the alarm goes off;
     *        typically comes from {@link PendingIntent#getBroadcast
     *        IntentSender.getBroadcast()}.
     *
     * @see #set
     * @see #setRepeating
     * @see #setWindow
     * @see #cancel
     * @see android.content.Context#sendBroadcast
     * @see android.content.Context#registerReceiver
     * @see android.content.Intent#filterEquals
     * @see #ELAPSED_REALTIME
     * @see #ELAPSED_REALTIME_WAKEUP
     * @see #RTC
     * @see #RTC_WAKEUP
     */
    public void setExactAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
        setImpl(type, triggerAtMillis, WINDOW_EXACT, 0, FLAG_ALLOW_WHILE_IDLE, operation,
                null, null);
    }

    /**
     * Remove any alarms with a matching {@link Intent}.
     * Any alarm, of any type, whose Intent matches this one (as defined by
     * {@link Intent#filterEquals}), will be canceled.
     *
     * @param operation IntentSender which matches a previously added
     * IntentSender.
     *
     * @see #set
     */
    public void cancel(PendingIntent operation) {
        try {
            mService.remove(operation);
        } catch (RemoteException ex) {
        }
    }

    /**
     * Set the system wall clock time.
     * Requires the permission android.permission.SET_TIME.
     *
     * @param millis time in milliseconds since the Epoch
     */
    public void setTime(long millis) {
        try {
            mService.setTime(millis);
        } catch (RemoteException ex) {
        }
    }

    /**
     * Sets the system's persistent default time zone. This is the time zone for all apps, even
     * after a reboot. Use {@link java.util.TimeZone#setDefault} if you just want to change the
     * time zone within your app, and even then prefer to pass an explicit
     * {@link java.util.TimeZone} to APIs that require it rather than changing the time zone for
     * all threads.
     *
     * <p> On android M and above, it is an error to pass in a non-Olson timezone to this
     * function. Note that this is a bad idea on all Android releases because POSIX and
     * the {@code TimeZone} class have opposite interpretations of {@code '+'} and {@code '-'}
     * in the same non-Olson ID.
     *
     * @param timeZone one of the Olson ids from the list returned by
     *     {@link java.util.TimeZone#getAvailableIDs}
     */
    public void setTimeZone(String timeZone) {
        if (TextUtils.isEmpty(timeZone)) {
            return;
        }

        // Reject this timezone if it isn't an Olson zone we recognize.
        if (mTargetSdkVersion >= Build.VERSION_CODES.M) {
            boolean hasTimeZone = false;
            try {
                hasTimeZone = ZoneInfoDB.getInstance().hasTimeZone(timeZone);
            } catch (IOException ignored) {
            }

            if (!hasTimeZone) {
                throw new IllegalArgumentException("Timezone: " + timeZone + " is not an Olson ID");
            }
        }

        try {
            mService.setTimeZone(timeZone);
        } catch (RemoteException ex) {
        }
    }

    /** @hide */
    public long getNextWakeFromIdleTime() {
        try {
            return mService.getNextWakeFromIdleTime();
        } catch (RemoteException ex) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Gets information about the next alarm clock currently scheduled.
     *
     * The alarm clocks considered are those scheduled by {@link #setAlarmClock}
     * from any package of the calling user.
     *
     * @see #setAlarmClock
     * @see AlarmClockInfo
     */
    public AlarmClockInfo getNextAlarmClock() {
        return getNextAlarmClock(UserHandle.myUserId());
    }

    /**
     * Gets information about the next alarm clock currently scheduled.
     *
     * The alarm clocks considered are those scheduled by {@link #setAlarmClock}
     * from any package of the given {@parm userId}.
     *
     * @see #setAlarmClock
     * @see AlarmClockInfo
     *
     * @hide
     */
    public AlarmClockInfo getNextAlarmClock(int userId) {
        try {
            return mService.getNextAlarmClock(userId);
        } catch (RemoteException ex) {
            return null;
        }
    }

    /**
     * An immutable description of an alarm clock.
     *
     * @see AlarmManager#setAlarmClock
     * @see AlarmManager#getNextAlarmClock
     */
    public static final class AlarmClockInfo implements Parcelable {

        private final long mTriggerTime;
        private final PendingIntent mShowIntent;

        /**
         * Creates a new alarm clock description.
         *
         * @param triggerTime time at which the underlying alarm is triggered in wall time 
         *                    milliseconds since the epoch
         * @param showIntent an intent that can be used to show or edit details of
         *                        the alarm clock.
         */
        public AlarmClockInfo(long triggerTime, PendingIntent showIntent) {
            mTriggerTime = triggerTime;
            mShowIntent = showIntent;
        }

        /**
         * Use the {@link #CREATOR}
         * @hide
         */
        AlarmClockInfo(Parcel in) {
            mTriggerTime = in.readLong();
            mShowIntent = in.readParcelable(PendingIntent.class.getClassLoader());
        }

        /**
         * Returns the time at which the alarm is going to trigger.
         *
         * This value is UTC wall clock time in milliseconds, as returned by
         * {@link System#currentTimeMillis()} for example.
         */
        public long getTriggerTime() {
            return mTriggerTime;
        }

        /**
         * Returns an intent intent that can be used to show or edit details of the alarm clock in
         * the application that scheduled it.
         *
         * <p class="note">Beware that any application can retrieve and send this intent, 
         * potentially with additional fields filled in. See
         * {@link PendingIntent#send(android.content.Context, int, android.content.Intent)
         * PendingIntent.send()} and {@link android.content.Intent#fillIn Intent.fillIn()}
         * for details.
         */
        public PendingIntent getShowIntent() {
            return mShowIntent;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(mTriggerTime);
            dest.writeParcelable(mShowIntent, flags);
        }

        public static final Creator<AlarmClockInfo> CREATOR = new Creator<AlarmClockInfo>() {
            @Override
            public AlarmClockInfo createFromParcel(Parcel in) {
                return new AlarmClockInfo(in);
            }

            @Override
            public AlarmClockInfo[] newArray(int size) {
                return new AlarmClockInfo[size];
            }
        };
    }
}
