/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.net;

import static android.content.pm.PackageManager.GET_SIGNATURES;
import static android.net.NetworkPolicy.CYCLE_NONE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.format.Time;

import com.google.android.collect.Sets;

import java.io.PrintWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Manager for creating and modifying network policy rules.
 *
 * {@hide}
 */
public class NetworkPolicyManager {

    /** No specific network policy, use system default. */
    public static final int POLICY_NONE = 0x0;
    /** Reject network usage on metered networks when application in background. */
    public static final int POLICY_REJECT_METERED_BACKGROUND = 0x1;
    /** Allow network use (metered or not) in the background in battery save mode. */
    public static final int POLICY_ALLOW_BACKGROUND_BATTERY_SAVE = 0x2;

    /** All network traffic should be allowed. */
    public static final int RULE_ALLOW_ALL = 0x0;
    /** Reject traffic on metered networks. */
    public static final int RULE_REJECT_METERED = 0x1;

    private static final boolean ALLOW_PLATFORM_APP_POLICY = true;

    /**
     * {@link Intent} extra that indicates which {@link NetworkTemplate} rule it
     * applies to.
     */
    public static final String EXTRA_NETWORK_TEMPLATE = "android.net.NETWORK_TEMPLATE";

    private INetworkPolicyManager mService;

    public NetworkPolicyManager(INetworkPolicyManager service) {
        if (service == null) {
            throw new IllegalArgumentException("missing INetworkPolicyManager");
        }
        mService = service;
    }

    public static NetworkPolicyManager from(Context context) {
        return (NetworkPolicyManager) context.getSystemService(Context.NETWORK_POLICY_SERVICE);
    }

    /**
     * Set policy flags for specific UID.
     *
     * @param policy {@link #POLICY_NONE} or combination of flags like
     * {@link #POLICY_REJECT_METERED_BACKGROUND}, {@link #POLICY_ALLOW_BACKGROUND_BATTERY_SAVE}.
     */
    public void setUidPolicy(int uid, int policy) {
        try {
            mService.setUidPolicy(uid, policy);
        } catch (RemoteException e) {
        }
    }

    /**
     * Add policy flags for specific UID.  The given policy bits will be set for
     * the uid.  Policy flags may be either
     * {@link #POLICY_REJECT_METERED_BACKGROUND} or {@link #POLICY_ALLOW_BACKGROUND_BATTERY_SAVE}.
     */
    public void addUidPolicy(int uid, int policy) {
        try {
            mService.addUidPolicy(uid, policy);
        } catch (RemoteException e) {
        }
    }

    /**
     * Clear/remove policy flags for specific UID.  The given policy bits will be set for
     * the uid.  Policy flags may be either
     * {@link #POLICY_REJECT_METERED_BACKGROUND} or {@link #POLICY_ALLOW_BACKGROUND_BATTERY_SAVE}.
     */
    public void removeUidPolicy(int uid, int policy) {
        try {
            mService.removeUidPolicy(uid, policy);
        } catch (RemoteException e) {
        }
    }

    public int getUidPolicy(int uid) {
        try {
            return mService.getUidPolicy(uid);
        } catch (RemoteException e) {
            return POLICY_NONE;
        }
    }

    public int[] getUidsWithPolicy(int policy) {
        try {
            return mService.getUidsWithPolicy(policy);
        } catch (RemoteException e) {
            return new int[0];
        }
    }

    public int[] getPowerSaveAppIdWhitelist() {
        try {
            return mService.getPowerSaveAppIdWhitelist();
        } catch (RemoteException e) {
            return new int[0];
        }
    }

    public void registerListener(INetworkPolicyListener listener) {
        try {
            mService.registerListener(listener);
        } catch (RemoteException e) {
        }
    }

    public void unregisterListener(INetworkPolicyListener listener) {
        try {
            mService.unregisterListener(listener);
        } catch (RemoteException e) {
        }
    }

    public void setNetworkPolicies(NetworkPolicy[] policies) {
        try {
            mService.setNetworkPolicies(policies);
        } catch (RemoteException e) {
        }
    }

    public NetworkPolicy[] getNetworkPolicies() {
        try {
            return mService.getNetworkPolicies();
        } catch (RemoteException e) {
            return null;
        }
    }

    public void setRestrictBackground(boolean restrictBackground) {
        try {
            mService.setRestrictBackground(restrictBackground);
        } catch (RemoteException e) {
        }
    }

    public boolean getRestrictBackground() {
        try {
            return mService.getRestrictBackground();
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Compute the last cycle boundary for the given {@link NetworkPolicy}. For
     * example, if cycle day is 20th, and today is June 15th, it will return May
     * 20th. When cycle day doesn't exist in current month, it snaps to the 1st
     * of following month.
     *
     * @hide
     */
    public static long computeLastCycleBoundary(long currentTime, NetworkPolicy policy) {
        if (policy.cycleDay == CYCLE_NONE) {
            throw new IllegalArgumentException("Unable to compute boundary without cycleDay");
        }

        GregorianCalendar now = new GregorianCalendar(
                TimeZone.getTimeZone(policy.cycleTimezone), Locale.getDefault());
        now.setTimeInMillis(currentTime);

        // first, find cycle boundary for current month
        final GregorianCalendar cycle = (GregorianCalendar) now.clone();
        cycle.set(Calendar.HOUR_OF_DAY, 0);
        cycle.set(Calendar.MINUTE, 0);
        cycle.set(Calendar.SECOND, 0);
        snapToCycleDay(cycle, policy.cycleDay);

        if (cycle.compareTo(now) >= 0) {
            // cycle boundary is beyond now, use last cycle boundary; start by
            // pushing ourselves squarely into last month.
            final GregorianCalendar lastMonth = (GregorianCalendar) now.clone();
            lastMonth.set(Calendar.HOUR_OF_DAY, 0);
            lastMonth.set(Calendar.MINUTE, 0);
            lastMonth.set(Calendar.SECOND, 0);
            lastMonth.set(Calendar.DAY_OF_MONTH, 1);
            lastMonth.add(Calendar.MONTH, -1);

            cycle.setTimeInMillis(lastMonth.getTimeInMillis());
            snapToCycleDay(cycle, policy.cycleDay);
        }

        return cycle.getTimeInMillis();
    }

    /** {@hide} */
    public static long computeNextCycleBoundary(long currentTime, NetworkPolicy policy) {
        if (policy.cycleDay == CYCLE_NONE) {
            throw new IllegalArgumentException("Unable to compute boundary without cycleDay");
        }

        GregorianCalendar now = new GregorianCalendar(
                TimeZone.getTimeZone(policy.cycleTimezone), Locale.getDefault());
        now.setTimeInMillis(currentTime);

        // first, find cycle boundary for current month
        final GregorianCalendar cycle = (GregorianCalendar) now.clone();
        cycle.set(Calendar.HOUR_OF_DAY, 0);
        cycle.set(Calendar.MINUTE, 0);
        cycle.set(Calendar.SECOND, 0);
        snapToCycleDay(cycle, policy.cycleDay);

        if (cycle.compareTo(now) <= 0) {
            // cycle boundary is before now, use next cycle boundary; start by
            // pushing ourselves squarely into next month.
            final GregorianCalendar nextMonth = (GregorianCalendar) now.clone();
            nextMonth.set(Calendar.HOUR_OF_DAY, 0);
            nextMonth.set(Calendar.MINUTE, 0);
            nextMonth.set(Calendar.SECOND, 0);
            nextMonth.set(Calendar.DAY_OF_MONTH, 1);
            nextMonth.add(Calendar.MONTH, 1);

            cycle.setTimeInMillis(nextMonth.getTimeInMillis());
            snapToCycleDay(cycle, policy.cycleDay);
        }

        return cycle.getTimeInMillis();
    }

    /**
     * Snap to the cycle day for the current month given; when cycle day doesn't
     * exist, it snaps to last second of current month.
     *
     * @hide
     */
    @SuppressWarnings("deprecation")
    public static void snapToCycleDay(Time time, int cycleDay) {
        GregorianCalendar calendar = new GregorianCalendar(
                TimeZone.getTimeZone(time.timezone), Locale.getDefault());
        calendar.setTimeInMillis(time.toMillis(true));
        snapToCycleDay(calendar, cycleDay);
        time.set(calendar.getTimeInMillis());
    }

    private static void snapToCycleDay(GregorianCalendar time, int cycleDay) {
        if (cycleDay > time.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            // cycle day isn't valid this month; snap to last second of month
            time.add(Calendar.MONTH, 1);
            time.set(Calendar.DAY_OF_MONTH, 1);
            time.set(Calendar.DAY_OF_MONTH, 1);
            time.set(Calendar.HOUR_OF_DAY, 0);
            time.set(Calendar.MINUTE, 0);
            time.set(Calendar.SECOND, 0);
            time.set(Calendar.MILLISECOND, 0);
            time.add(Calendar.SECOND, -1);
        } else {
            time.set(Calendar.DAY_OF_MONTH, cycleDay);
        }
    }

    /**
     * Check if given UID can have a {@link #setUidPolicy(int, int)} defined,
     * usually to protect critical system services.
     */
    @Deprecated
    public static boolean isUidValidForPolicy(Context context, int uid) {
        // first, quick-reject non-applications
        if (!UserHandle.isApp(uid)) {
            return false;
        }

        if (!ALLOW_PLATFORM_APP_POLICY) {
            final PackageManager pm = context.getPackageManager();
            final HashSet<Signature> systemSignature;
            try {
                systemSignature = Sets.newHashSet(
                        pm.getPackageInfo("android", GET_SIGNATURES).signatures);
            } catch (NameNotFoundException e) {
                throw new RuntimeException("problem finding system signature", e);
            }

            try {
                // reject apps signed with platform cert
                for (String packageName : pm.getPackagesForUid(uid)) {
                    final HashSet<Signature> packageSignature = Sets.newHashSet(
                            pm.getPackageInfo(packageName, GET_SIGNATURES).signatures);
                    if (packageSignature.containsAll(systemSignature)) {
                        return false;
                    }
                }
            } catch (NameNotFoundException e) {
            }
        }

        // nothing found above; we can apply policy to UID
        return true;
    }

    /** {@hide} */
    public static void dumpPolicy(PrintWriter fout, int policy) {
        fout.write("[");
        if ((policy & POLICY_REJECT_METERED_BACKGROUND) != 0) {
            fout.write("REJECT_METERED_BACKGROUND");
        }
        fout.write("]");
    }

    /** {@hide} */
    public static void dumpRules(PrintWriter fout, int rules) {
        fout.write("[");
        if ((rules & RULE_REJECT_METERED) != 0) {
            fout.write("REJECT_METERED");
        }
        fout.write("]");
    }

}
