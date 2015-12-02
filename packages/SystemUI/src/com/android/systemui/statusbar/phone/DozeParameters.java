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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;
import android.util.MathUtils;

import com.android.systemui.doze.DozeLog;
import com.android.systemui.R;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DozeParameters {
    private static final String TAG = "DozeParameters";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int MAX_DURATION = 60 * 1000;

    private final Context mContext;

    private static PulseSchedule sPulseSchedule;

    public DozeParameters(Context context) {
        mContext = context;
    }

    public void dump(PrintWriter pw) {
        pw.println("  DozeParameters:");
        pw.print("    getDisplayStateSupported(): "); pw.println(getDisplayStateSupported());
        pw.print("    getPulseDuration(notification): "); pw.println(getPulseDuration(DozeLog.PULSE_REASON_NOTIFICATION));
        pw.print("    getPulseDuration(pickup): "); pw.println(getPulseDuration(DozeLog.PULSE_REASON_SENSOR_PICKUP));
        pw.print("    getPulseDuration(intent): "); pw.println(getPulseDuration(DozeLog.PULSE_REASON_INTENT));
        pw.print("    getPulseInDuration(notification): "); pw.println(getPulseInDuration(DozeLog.PULSE_REASON_NOTIFICATION));
        pw.print("    getPulseInDuration(pickup): "); pw.println(getPulseInDuration(DozeLog.PULSE_REASON_SENSOR_PICKUP));
        pw.print("    getPulseInDuration(intent): "); pw.println(getPulseInDuration(DozeLog.PULSE_REASON_INTENT));
        pw.print("    getPulseInVisibleDuration(): "); pw.println(getPulseVisibleDuration());
        pw.print("    getPulseOutDuration(): "); pw.println(getPulseOutDuration());
        pw.print("    getPulseOnSigMotion(): "); pw.println(getPulseOnSigMotion());
        pw.print("    getVibrateOnSigMotion(): "); pw.println(getVibrateOnSigMotion());
        pw.print("    getPulseOnPickup(): "); pw.println(getPulseOnPickup());
        pw.print("    getVibrateOnPickup(): "); pw.println(getVibrateOnPickup());
        pw.print("    getProxCheckBeforePulse(pickup): "); pw.println(getProxCheckBeforePulse(DozeLog.PULSE_REASON_SENSOR_PICKUP));
        pw.print("    getProxCheckBeforePulse(intent): "); pw.println(getProxCheckBeforePulse(DozeLog.PULSE_REASON_INTENT));
        pw.print("    getPulseOnNotifications(): "); pw.println(getPulseOnNotifications());
        pw.print("    getPulseSchedule(): "); pw.println(getPulseSchedule());
        pw.print("    getPulseScheduleResets(): "); pw.println(getPulseScheduleResets());
        pw.print("    getPickupVibrationThreshold(): "); pw.println(getPickupVibrationThreshold());
        pw.print("    getPickupPerformsProxCheck(): "); pw.println(getPickupPerformsProxCheck());
    }

    public boolean getDisplayStateSupported() {
        return getBoolean("doze.display.supported", R.bool.doze_display_state_supported);
    }

    public int getPulseDuration(int reason) {
        return getPulseInDuration(reason) + getPulseVisibleDuration() + getPulseOutDuration();
    }

    public int getPulseInDuration(int reason) {
        switch(reason) {
        case DozeLog.PULSE_REASON_SENSOR_PICKUP:
                return getInt("doze.pulse.duration.in.pickup", R.integer.doze_pulse_duration_in_pickup);
        case DozeLog.PULSE_REASON_INTENT:
                return getInt("doze.pulse.duration.in.intent", R.integer.doze_pulse_duration_in_intent);
        default:
                return getInt("doze.pulse.duration.in", R.integer.doze_pulse_duration_in);
        }
    }

    public int getPulseVisibleDuration() {
        return getInt("doze.pulse.duration.visible", R.integer.doze_pulse_duration_visible);
    }

    public int getPulseOutDuration() {
        return getInt("doze.pulse.duration.out", R.integer.doze_pulse_duration_out);
    }

    public boolean getPulseOnSigMotion() {
        return getBoolean("doze.pulse.sigmotion", R.bool.doze_pulse_on_significant_motion);
    }

    public boolean getVibrateOnSigMotion() {
        return SystemProperties.getBoolean("doze.vibrate.sigmotion", false);
    }

    public boolean getPulseOnPickup() {
        return getBoolean("doze.pulse.pickup", R.bool.doze_pulse_on_pick_up);
    }

    public boolean getVibrateOnPickup() {
        return SystemProperties.getBoolean("doze.vibrate.pickup", false);
    }

    public boolean getProxCheckBeforePulse(int reason) {
        switch(reason) {
        case DozeLog.PULSE_REASON_SENSOR_PICKUP:
                return getBoolean("doze.pulse.proxcheck.pickup", R.bool.doze_proximity_check_before_pulse);
        case DozeLog.PULSE_REASON_INTENT:
                return getBoolean("doze.pulse.proxcheck.intent", R.bool.doze_proximity_check_before_pulse_intent);
        default:
                return getBoolean("doze.pulse.proxcheck", R.bool.doze_proximity_check_before_pulse);
        }
    }

    public boolean getPickupPerformsProxCheck() {
        return getBoolean("doze.pickup.proxcheck", R.bool.doze_pickup_performs_proximity_check);
    }

    public boolean getPulseOnNotifications() {
        return getBoolean("doze.pulse.notifications", R.bool.doze_pulse_on_notifications);
    }

    public PulseSchedule getPulseSchedule() {
        final String spec = getString("doze.pulse.schedule", R.string.doze_pulse_schedule);
        if (sPulseSchedule == null || !sPulseSchedule.mSpec.equals(spec)) {
            sPulseSchedule = PulseSchedule.parse(spec);
        }
        return sPulseSchedule;
    }

    public int getPulseScheduleResets() {
        return getInt("doze.pulse.schedule.resets", R.integer.doze_pulse_schedule_resets);
    }

    public int getPickupVibrationThreshold() {
        return getInt("doze.pickup.vibration.threshold", R.integer.doze_pickup_vibration_threshold);
    }

    private boolean getBoolean(String propName, int resId) {
        return SystemProperties.getBoolean(propName, mContext.getResources().getBoolean(resId));
    }

    private int getInt(String propName, int resId) {
        int value = SystemProperties.getInt(propName, mContext.getResources().getInteger(resId));
        return MathUtils.constrain(value, 0, MAX_DURATION);
    }

    private String getString(String propName, int resId) {
        return SystemProperties.get(propName, mContext.getString(resId));
    }

    public static class PulseSchedule {
        private static final Pattern PATTERN = Pattern.compile("(\\d+?)s", 0);

        private String mSpec;
        private int[] mSchedule;

        public static PulseSchedule parse(String spec) {
            if (TextUtils.isEmpty(spec)) return null;
            try {
                final PulseSchedule rt = new PulseSchedule();
                rt.mSpec = spec;
                final String[] tokens = spec.split(",");
                rt.mSchedule = new int[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    final Matcher m = PATTERN.matcher(tokens[i]);
                    if (!m.matches()) throw new IllegalArgumentException("Bad token: " + tokens[i]);
                    rt.mSchedule[i] = Integer.parseInt(m.group(1));
                }
                if (DEBUG) Log.d(TAG, "Parsed spec [" + spec + "] as: " + rt);
                return rt;
            } catch (RuntimeException e) {
                Log.w(TAG, "Error parsing spec: " + spec, e);
                return null;
            }
        }

        @Override
        public String toString() {
            return Arrays.toString(mSchedule);
        }

        public long getNextTime(long now, long notificationTime) {
            for (int i = 0; i < mSchedule.length; i++) {
                final long time = notificationTime + mSchedule[i] * 1000;
                if (time > now) return time;
            }
            return 0;
        }
    }
}
