/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.doze;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.TimeUtils;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DozeLog {
    private static final String TAG = "DozeLog";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean ENABLED = true;
    private static final int SIZE = Build.IS_DEBUGGABLE ? 400 : 50;
    static final SimpleDateFormat FORMAT = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    private static final int PULSE_REASONS = 5;

    public static final int PULSE_REASON_INTENT = 0;
    public static final int PULSE_REASON_NOTIFICATION = 1;
    public static final int PULSE_REASON_SENSOR_SIGMOTION = 2;
    public static final int PULSE_REASON_SENSOR_PICKUP = 3;
    public static final int PULSE_REASON_SENSOR_DOUBLE_TAP = 4;

    private static long[] sTimes;
    private static String[] sMessages;
    private static int sPosition;
    private static int sCount;
    private static boolean sPulsing;

    private static long sSince;
    private static SummaryStats sPickupPulseNearVibrationStats;
    private static SummaryStats sPickupPulseNotNearVibrationStats;
    private static SummaryStats sNotificationPulseStats;
    private static SummaryStats sScreenOnPulsingStats;
    private static SummaryStats sScreenOnNotPulsingStats;
    private static SummaryStats sEmergencyCallStats;
    private static SummaryStats[][] sProxStats; // [reason][near/far]

    public static void tracePickupPulse(Context context, boolean withinVibrationThreshold) {
        if (!ENABLED) return;
        init(context);
        log("pickupPulse withinVibrationThreshold=" + withinVibrationThreshold);
        (withinVibrationThreshold ? sPickupPulseNearVibrationStats
                : sPickupPulseNotNearVibrationStats).append();
    }

    public static void tracePulseStart(int reason) {
        if (!ENABLED) return;
        sPulsing = true;
        log("pulseStart reason=" + pulseReasonToString(reason));
    }

    public static void tracePulseFinish() {
        if (!ENABLED) return;
        sPulsing = false;
        log("pulseFinish");
    }

    public static void traceNotificationPulse(Context context, long instance) {
        if (!ENABLED) return;
        init(context);
        log("notificationPulse instance=" + instance);
        sNotificationPulseStats.append();
    }

    private static void init(Context context) {
        synchronized (DozeLog.class) {
            if (sMessages == null) {
                sTimes = new long[SIZE];
                sMessages = new String[SIZE];
                sSince = System.currentTimeMillis();
                sPickupPulseNearVibrationStats = new SummaryStats();
                sPickupPulseNotNearVibrationStats = new SummaryStats();
                sNotificationPulseStats = new SummaryStats();
                sScreenOnPulsingStats = new SummaryStats();
                sScreenOnNotPulsingStats = new SummaryStats();
                sEmergencyCallStats = new SummaryStats();
                sProxStats = new SummaryStats[PULSE_REASONS][2];
                for (int i = 0; i < PULSE_REASONS; i++) {
                    sProxStats[i][0] = new SummaryStats();
                    sProxStats[i][1] = new SummaryStats();
                }
                log("init");
                KeyguardUpdateMonitor.getInstance(context).registerCallback(sKeyguardCallback);
            }
        }
    }

    public static void traceDozing(Context context, boolean dozing) {
        if (!ENABLED) return;
        sPulsing = false;
        init(context);
        log("dozing " + dozing);
    }

    public static void traceFling(boolean expand, boolean aboveThreshold, boolean thresholdNeeded,
            boolean screenOnFromTouch) {
        if (!ENABLED) return;
        log("fling expand=" + expand + " aboveThreshold=" + aboveThreshold + " thresholdNeeded="
                + thresholdNeeded + " screenOnFromTouch=" + screenOnFromTouch);
    }

    public static void traceEmergencyCall() {
        if (!ENABLED) return;
        log("emergencyCall");
        sEmergencyCallStats.append();
    }

    public static void traceKeyguardBouncerChanged(boolean showing) {
        if (!ENABLED) return;
        log("bouncer " + showing);
    }

    public static void traceScreenOn() {
        if (!ENABLED) return;
        log("screenOn pulsing=" + sPulsing);
        (sPulsing ? sScreenOnPulsingStats : sScreenOnNotPulsingStats).append();
        sPulsing = false;
    }

    public static void traceScreenOff(int why) {
        if (!ENABLED) return;
        log("screenOff why=" + why);
    }

    public static void traceKeyguard(boolean showing) {
        if (!ENABLED) return;
        log("keyguard " + showing);
        if (!showing) {
            sPulsing = false;
        }
    }

    public static void traceProximityResult(Context context, boolean near, long millis,
            int pulseReason) {
        if (!ENABLED) return;
        init(context);
        log("proximityResult reason=" + pulseReasonToString(pulseReason) + " near=" + near
                + " millis=" + millis);
        sProxStats[pulseReason][near ? 0 : 1].append();
    }

    public static String pulseReasonToString(int pulseReason) {
        switch (pulseReason) {
            case PULSE_REASON_INTENT: return "intent";
            case PULSE_REASON_NOTIFICATION: return "notification";
            case PULSE_REASON_SENSOR_SIGMOTION: return "sigmotion";
            case PULSE_REASON_SENSOR_PICKUP: return "pickup";
            case PULSE_REASON_SENSOR_DOUBLE_TAP: return "doubletap";
            default: throw new IllegalArgumentException("bad reason: " + pulseReason);
        }
    }

    public static void dump(PrintWriter pw) {
        synchronized (DozeLog.class) {
            if (sMessages == null) return;
            pw.println("  Doze log:");
            final int start = (sPosition - sCount + SIZE) % SIZE;
            for (int i = 0; i < sCount; i++) {
                final int j = (start + i) % SIZE;
                pw.print("    ");
                pw.print(FORMAT.format(new Date(sTimes[j])));
                pw.print(' ');
                pw.println(sMessages[j]);
            }
            pw.print("  Doze summary stats (for ");
            TimeUtils.formatDuration(System.currentTimeMillis() - sSince, pw);
            pw.println("):");
            sPickupPulseNearVibrationStats.dump(pw, "Pickup pulse (near vibration)");
            sPickupPulseNotNearVibrationStats.dump(pw, "Pickup pulse (not near vibration)");
            sNotificationPulseStats.dump(pw, "Notification pulse");
            sScreenOnPulsingStats.dump(pw, "Screen on (pulsing)");
            sScreenOnNotPulsingStats.dump(pw, "Screen on (not pulsing)");
            sEmergencyCallStats.dump(pw, "Emergency call");
            for (int i = 0; i < PULSE_REASONS; i++) {
                final String reason = pulseReasonToString(i);
                sProxStats[i][0].dump(pw, "Proximity near (" + reason + ")");
                sProxStats[i][1].dump(pw, "Proximity far (" + reason + ")");
            }
        }
    }

    private static void log(String msg) {
        synchronized (DozeLog.class) {
            if (sMessages == null) return;
            sTimes[sPosition] = System.currentTimeMillis();
            sMessages[sPosition] = msg;
            sPosition = (sPosition + 1) % SIZE;
            sCount = Math.min(sCount + 1, SIZE);
        }
        if (DEBUG) Log.d(TAG, msg);
    }

    private static class SummaryStats {
        private int mCount;

        public void append() {
            mCount++;
        }

        public void dump(PrintWriter pw, String type) {
            if (mCount == 0) return;
            pw.print("    ");
            pw.print(type);
            pw.print(": n=");
            pw.print(mCount);
            pw.print(" (");
            final double perHr = (double) mCount / (System.currentTimeMillis() - sSince)
                    * 1000 * 60 * 60;
            pw.print(perHr);
            pw.print("/hr)");
            pw.println();
        }
    }

    private static final KeyguardUpdateMonitorCallback sKeyguardCallback =
            new KeyguardUpdateMonitorCallback() {
        @Override
        public void onEmergencyCallAction() {
            traceEmergencyCall();
        }

        @Override
        public void onKeyguardBouncerChanged(boolean bouncer) {
            traceKeyguardBouncerChanged(bouncer);
        }

        @Override
        public void onStartedWakingUp() {
            traceScreenOn();
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            traceScreenOff(why);
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            traceKeyguard(showing);
        }
    };
}
