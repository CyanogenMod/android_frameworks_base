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
import static android.net.NetworkPolicy.CYCLE_DAILY;
import static android.net.NetworkPolicy.CYCLE_MONTHLY;
import static android.net.NetworkPolicy.CYCLE_WEEKLY;
import static android.text.format.Time.MONTH_DAY;

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
import java.util.HashSet;

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
     *            {@link #POLICY_REJECT_METERED_BACKGROUND}.
     */
    public void setUidPolicy(int uid, int policy) {
        try {
            mService.setUidPolicy(uid, policy);
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

        final Time now = new Time(policy.cycleTimezone);
        now.set(currentTime);

        // first, find cycle boundary for current cycle
        final Time cycle = new Time(now);
        cycle.hour = cycle.minute = cycle.second = 0;
        snapToCycleDay(cycle, policy.cycleDay, policy.cycleLength);

        if (Time.compare(cycle, now) >= 0) {
            // cycle boundary is beyond now, use last cycle boundary; start by
            // pushing ourselves squarely into last month, week or day
            final Time last = new Time(now);
            last.hour = last.minute = last.second = 0;

            if (policy.cycleLength == CYCLE_MONTHLY) {
                last.monthDay = 1;
                last.month -= 1;
            } else if (policy.cycleLength == CYCLE_WEEKLY) {
                last.monthDay -= 7;
            } else if (policy.cycleLength == CYCLE_DAILY) {
                last.monthDay -= 1;
            }

            last.normalize(true);

            cycle.set(last);
            snapToCycleDay(cycle, policy.cycleDay, policy.cycleLength);
        }

        return cycle.toMillis(true);
    }

    /** {@hide} */
    public static long computeNextCycleBoundary(long currentTime, NetworkPolicy policy) {
        if (policy.cycleDay == CYCLE_NONE) {
            throw new IllegalArgumentException("Unable to compute boundary without cycleDay");
        }

        final Time now = new Time(policy.cycleTimezone);
        now.set(currentTime);

        // first, find cycle boundary for current cycle
        final Time cycle = new Time(now);
        cycle.hour = cycle.minute = cycle.second = 0;
        snapToCycleDay(cycle, policy.cycleDay, policy.cycleLength);

        if (Time.compare(cycle, now) <= 0) {
            // cycle boundary is before now, use next cycle boundary; start by
            // pushing ourselves squarely into next month, week or day
            final Time next = new Time(now);
            next.hour = next.minute = next.second = 0;

            if (policy.cycleLength == CYCLE_MONTHLY) {
                next.monthDay = 1;
                next.month += 1;
            } else if (policy.cycleLength == CYCLE_WEEKLY) {
                next.monthDay += 7;
            } else if (policy.cycleLength == CYCLE_DAILY) {
                next.monthDay += 1;
            }

            next.normalize(true);

            cycle.set(next);
            snapToCycleDay(cycle, policy.cycleDay, policy.cycleLength);
        }

        return cycle.toMillis(true);
    }

    /**
     * Snap to the cycle day for the current month given; when cycle day doesn't
     * exist, it snaps to last second of current month.
     *
     * @hide
     */
    public static void snapToCycleDay(Time time, int cycleDay) {
        if (cycleDay > time.getActualMaximum(MONTH_DAY)) {
            // cycle day isn't valid this month; snap to last second of month
            time.month += 1;
            time.monthDay = 1;
            time.second = -1;
        } else {
            time.monthDay = cycleDay;
        }
        time.normalize(true);
    }

    /**
     * Snap to the cycle day for the current cycle length
     *
     * @hide
     */
    public static void snapToCycleDay(Time time, int cycleDay, int cycleLength) {
        if (cycleLength == CYCLE_MONTHLY) {
            // when cycle day doesn't exist, it snaps to last second of current month
            if (cycleDay > time.getActualMaximum(MONTH_DAY)) {
                // cycle day isn't valid this month; snap to last second of month
                time.month += 1;
                time.monthDay = 1;
                time.second = -1;
            } else {
                time.monthDay = cycleDay;
            }
        } else if (cycleLength == CYCLE_WEEKLY) {
            time.monthDay += (cycleDay - time.weekDay);
        }
        time.normalize(true);
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
