/*
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *   * Neither the name of Code Aurora nor
 *     the names of its contributors may be used to endorse or promote
 *     products derived from this software without specific prior written
 *     permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server;

import android.os.Power;
import android.os.PowerManager;
import android.os.SystemProperties;

import android.app.AlarmManager;
import android.app.PendingIntent;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.text.format.Time;

import java.util.Calendar;
import android.util.Log;

class DynamicMemoryManagerService {
    private final String TAG = "DMM Service";
    private Context mContext;
    private boolean SCREEN_ON = false;
    private boolean DPD_START = false;
    private boolean START_ALARM_SET = false;
    private boolean STOP_ALARM_SET = false;
    private int prevStartHr = 0;
    private int prevStartMin = 0;
    private int prevStopHr = 0;
    private int prevStopMin = 0;
    private enum DMM_MEM_STATE {ACTIVE, DISABLED}
    private DMM_MEM_STATE mState;
    private AlarmManager mStartAlarmManager, mStopAlarmManager;
    private Intent mStartIntent, mStopIntent;
    private PendingIntent mStartPendingIntent, mStopPendingIntent;
    private static final String ACTION_DPD_START =
        "com.android.server.DMMService.action.DPD_START";
    private static final String ACTION_DPD_STOP =
        "com.android.server.DMMService.action.DPD_STOP";

    private final boolean DEBUG = false;

    public DynamicMemoryManagerService(Context context) {
        mContext = context;

        Log.w(TAG, "ro.dev.dmm.dpd.start_address = "
                + SystemProperties.get("ro.dev.dmm.dpd.start_address", "0"));

        if(SystemProperties.get("ro.dev.dmm.dpd.start_address", "0").compareTo("0") != 0) {
                mState = DMM_MEM_STATE.ACTIVE;

                registerForBroadcasts();
                mStartIntent = new Intent(ACTION_DPD_START, null);
                mStopIntent = new Intent(ACTION_DPD_STOP, null);
                mStartPendingIntent = PendingIntent.getBroadcast(mContext, 0, mStartIntent, 0);
                mStopPendingIntent = PendingIntent.getBroadcast(mContext, 0, mStopIntent, 0);
                mStartAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
                mStopAlarmManager = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);

                Log.w(TAG, "DynamicMemoryManager Service Initialized");
        }
        else
            Log.w(TAG, "DynamicMemoryManager Service Disabled.");
    }

    private void registerForBroadcasts() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(ACTION_DPD_START);
        intentFilter.addAction(ACTION_DPD_STOP);
        mContext.registerReceiver(mReceiver, intentFilter);
    }

    protected void finalize() {
        if (DEBUG) Log.w(TAG, "DynamicMemoryManagerService Finalize");
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                if (DEBUG) Log.w(TAG, "ACTION_BOOT_COMPLETED");
                manageAlarms();
            }
            else if(action.equals(Intent.ACTION_SCREEN_ON)) {
                if (DEBUG) Log.w(TAG, "ACTION_SCREEN_ON");
                enableUnstableMemory(true);
                SCREEN_ON = true;
            }
            else if(action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (DEBUG) Log.w(TAG, "ACTION_SCREEN_OFF");
                SCREEN_ON = false;

                manageAlarms();
                checkCurrentStatus();
                if (DEBUG) Log.w(TAG, "DPD_START is set to " + DPD_START);

                if(DPD_START)
                    enableUnstableMemory(false);
            }
            else if (action.equals(ACTION_DPD_START)) {
                if (DEBUG) Log.w(TAG, "ACTION_DPD_START");
                DPD_START = true;
                START_ALARM_SET = false;
                if(!SCREEN_ON)
                    enableUnstableMemory(false);
            }
            else if (action.equals(ACTION_DPD_STOP)) {
                if (DEBUG) Log.w(TAG, "ACTION_DPD_STOP");
                DPD_START = false;
                STOP_ALARM_SET = false;
            }
        }
    };

    private boolean manageAlarms() {
        if (DEBUG) Log.w(TAG, "Manage Alarms");

        if(dpdEnabled() == 1) {
            setStartAlarm();
            setStopAlarm();
        }
        else {
            cancelStartAlarm();
            cancelStopAlarm();
            DPD_START = false;
        }
        return true;
    }

    private int dpdEnabled() {
        if (DEBUG) Log.w(TAG, "DPD Enabled: " +
                SystemProperties.getInt("persist.sys.dpd", 1));
        return SystemProperties.getInt("persist.sys.dpd", 1);
    }

    private boolean setStartAlarm() {
        int startHr = SystemProperties.getInt("persist.sys.dpd.start_hr", 20);
        int startMin = SystemProperties.getInt("persist.sys.dpd.start_min", 0);
        long delay_ms = 0;

        if (DEBUG) Log.w(TAG, "DPD_START = " + DPD_START);
        if (DEBUG) Log.w(TAG, "Prev Start: Hr = " + prevStartHr + " Min = " + prevStartMin);
        if (DEBUG) Log.w(TAG, "     Start: Hr = " + startHr + " Min = " + startMin);
        if (DEBUG) Log.w(TAG, "START_ALARM_SET = " + START_ALARM_SET);

        if(startHr != prevStartHr || startMin != prevStartMin || !START_ALARM_SET) {
            prevStartHr = startHr;
            prevStartMin = startMin;
            cancelStartAlarm();
            delay_ms = getDelay(startHr, startMin);
            if (DEBUG) Log.w(TAG, "Set Start Alarm after " + delay_ms + "ms");
            mStartAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                                            delay_ms,
                                            AlarmManager.INTERVAL_DAY,
                                            mStartPendingIntent);
            START_ALARM_SET = true;
        }
        return true;
    }

    private boolean cancelStartAlarm() {
        if (DEBUG) Log.w(TAG, "Cancel Start Alarm");
        mStartAlarmManager.cancel(mStartPendingIntent);
        START_ALARM_SET = false;
        return true;
    }

    private boolean setStopAlarm() {
        int stopHr = SystemProperties.getInt("persist.sys.dpd.stop_hr", 7);
        int stopMin = SystemProperties.getInt("persist.sys.dpd.stop_min", 0);
        long delay_ms = 0;

        if (DEBUG) Log.w(TAG, "Prev Stop Hr = " + prevStopHr + " Min = " + prevStopMin);
        if (DEBUG) Log.w(TAG, "     Stop Hr = " + stopHr + " Min = " + stopMin);
        if (DEBUG) Log.w(TAG, "STOP_ALARM_SET = " + STOP_ALARM_SET);

        if(stopHr != prevStopHr || stopMin != prevStopMin || !STOP_ALARM_SET) {
            prevStopHr = stopHr;
            prevStopMin = stopMin;
            cancelStopAlarm();
            delay_ms = getDelay(stopHr, stopMin);
            if (DEBUG) Log.w(TAG, "Set Stop Alarm after " + delay_ms + "ms");
            mStopAlarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                                           delay_ms,
                                           AlarmManager.INTERVAL_DAY,
                                           mStopPendingIntent);
            STOP_ALARM_SET = true;
        }
        return true;
    }

    private boolean cancelStopAlarm() {
        if (DEBUG) Log.w(TAG, "Cancel Stop Alarm");
        mStopAlarmManager.cancel(mStopPendingIntent);
        STOP_ALARM_SET = false;
        return true;
    }

    private long getDelay(int sHr, int sMin) {
        final Calendar calendar = Calendar.getInstance();
        int dHr = sHr - calendar.get(Calendar.HOUR_OF_DAY);
        int dMin = sMin - calendar.get(Calendar.MINUTE);

        if(dHr < 0)
            dHr = 24 + dHr;
        if(dMin < 0) {
            dMin = 60 + dMin;
            if(dHr > 0)
                dHr = dHr - 1;
            else
                dHr = 24 + dHr - 1;
        }
        if (DEBUG) Log.w(TAG, "Current time " + calendar.get(Calendar.HOUR_OF_DAY) +
                    ":" +calendar.get(Calendar.MINUTE));
        if (DEBUG) Log.w(TAG, "Alarm time " + sHr + ":" + sMin);
        if (DEBUG) Log.w(TAG, "Next Alarm after " + dHr + " hrs and " + dMin + " mins");

        return (((dHr * 60) + dMin) * 60 * 1000) + System.currentTimeMillis();
    }

    private void checkCurrentStatus() {
        final Calendar calendar = Calendar.getInstance();
        int StopDayOfMonth = 0;

        if(dpdEnabled() == 1) {
            StopDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            if((prevStartHr > prevStopHr) ||
               ((prevStartHr == prevStopHr) && (prevStartMin > prevStopMin)))
                StopDayOfMonth = StopDayOfMonth + 1;

            Time NowTime = new Time();
            NowTime.setToNow();

            Time StartTime = new Time();
            StartTime.set(calendar.get(Calendar.SECOND),
                            prevStartMin,
                            prevStartHr,
                            calendar.get(Calendar.DAY_OF_MONTH),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.YEAR));

            Time StopTime = new Time();
            StopTime.set(calendar.get(Calendar.SECOND),
                            prevStopMin,
                            prevStopHr,
                            StopDayOfMonth,
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.YEAR));

            if(NowTime.after(StartTime) && NowTime.before(StopTime))
                DPD_START = true;
            else
                DPD_START = false;
        }
        else {
            DPD_START = false;
        }
    }

    private int enableUnstableMemory(boolean flag) {
        Log.w(TAG, "Enable Unstable Memory : " + flag);
        if (flag) {
            if(mState == DMM_MEM_STATE.DISABLED) {
                if(Power.SetUnstableMemoryState(flag) < 0)
                    Log.e(TAG, "Activating Unstable Memory: Failed !");
                else
                    mState = DMM_MEM_STATE.ACTIVE;
            }
        } else {
            if(mState == DMM_MEM_STATE.ACTIVE) {
                if(Power.SetUnstableMemoryState(flag) < 0)
                    Log.e(TAG, "Disabling Unstable Memory: Failed !");
                else
                        mState = DMM_MEM_STATE.DISABLED;
            }
        }
        return 0;
    }
}
