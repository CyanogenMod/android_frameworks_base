/*
 * Copyright (c) 2011-2012, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
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

package org.codeaurora;

import android.util.Log;

public class Performance
{
    private static final String TAG = "Perf";

    /** &hide */
    public Performance() {
        //Log.d(TAG, "Perf module initialized");
    }

    /* The following defined constants are to be used for PerfLock APIs*/
    /** @hide */ public static final int PWR_CLSP_A = 1100;
    /** @hide */ public static final int HEAP_OPT_A = 2100;

    /** @hide */ public static final int CPUS_ON_LVL_MAX = 3900;
    /** @hide */ public static final int CPUS_ON_LVL_3 = 3300;
    /** @hide */ public static final int CPUS_ON_LVL_2 = 3200;
    /** @hide */ public static final int CPUS_ON_LVL_1 = 3100;

    /** @hide */ public static final int CPU0_FREQ_LVL_NONTURBO = 4200;
    /** @hide */ public static final int CPU0_FREQ_LVL_TURBO = 4300;
    /** @hide */ public static final int CPU0_FREQ_LVL_MAX = 4900;

    /** @hide */ public static final int CPU1_FREQ_LVL_NONTURBO = 5200;
    /** @hide */ public static final int CPU1_FREQ_LVL_TURBO = 5300;
    /** @hide */ public static final int CPU1_FREQ_LVL_MAX = 5900;

    /* The following are the PerfLock API return values*/
    /** @hide */ public static final int REQUEST_FAILED = 0;
    /** @hide */ public static final int REQUEST_SUCCEEDED = 1;
    /** @hide */ public static final int REQUEST_PENDING = 2;

    private int HANDLE = 0;

    /* The following two functions are the PerfLock APIs*/
    /** &hide */
    public int perfLockAcquire(int... list) {
        int rc = 0;
        rc = native_perf_lock_acq(list);
        if (rc == 0)
            return REQUEST_FAILED;
        if (rc > 1000) {
            HANDLE = rc;
        }
        return REQUEST_SUCCEEDED;
    }

    /** &hide */
    public int perfLockRelease() {
        int rc = 0;
        if (HANDLE > 1000)
            rc = native_perf_lock_rel(HANDLE);
            if (rc > 0)
                HANDLE = 0;
        return rc;
    }

    /* The following are for internal use only */
    /** @hide */ public static final int CPUOPT_CPU0_PWRCLSP = 1;
    /** @hide */ public static final int CPUOPT_CPU0_FREQMIN = 2;
    /** @hide */ public static final int CPUOPT_CPU1_FREQMIN = 3;

    /** &hide */
    public int pulseFreqBoost(int duration, int freq) {
        return native_pulse_freq_boost(duration, freq);
    }

    /** &hide */
    public void cpuBoost(int ntasks) {
        native_cpu_boost(ntasks);
    }

    /** &hide */
    public int cpuSetOptions(int reqType, int reqValue) {
        return native_cpu_setoptions(reqType, reqValue);
    }

    /** &hide */
    protected void finalize() {
        native_deinit();
    }

    private native int  native_perf_lock_acq(int list[]);
    private native int  native_perf_lock_rel(int handle);
    private native int  native_pulse_freq_boost(int duration, int freq);
    private native void native_cpu_boost(int ntasks);
    private native int  native_cpu_setoptions(int reqtype, int reqvalue);
    private native void native_deinit();
}
