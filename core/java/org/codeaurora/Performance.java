/*
 * Copyright (c) 2011-2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of The Linux Foundation nor
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
    /** @hide */ public static final int ALL_CPUS_PWR_CLPS_DIS = 0x100;

    /** @hide */ public static final int CPU0_FREQ_NONTURBO_MAX = 0x20A;
    /** @hide */ public static final int CPU0_FREQ_TURBO_MAX = 0x20F;

    /** @hide */ public static final int CPU1_FREQ_NONTURBO_MAX = 0x30A;
    /** @hide */ public static final int CPU1_FREQ_TURBO_MAX = 0x30F;

    /** @hide */ public static final int CPU2_FREQ_NONTURBO_MAX = 0x40A;
    /** @hide */ public static final int CPU2_FREQ_TURBO_MAX = 0x40F;

    /** @hide */ public static final int CPU3_FREQ_NONTURBO_MAX = 0x50A;
    /** @hide */ public static final int CPU3_FREQ_TURBO_MAX = 0x50F;

    /** @hide */ public static final int CPUS_ON_2 = 0x702;
    /** @hide */ public static final int CPUS_ON_3 = 0x703;
    /** @hide */ public static final int CPUS_ON_MAX = 0x704;

    /** @hide */ public static final int ALL_CPUS_FREQ_NONTURBO_MAX = 0x90A;
    /** @hide */ public static final int ALL_CPUS_FREQ_TURBO_MAX = 0x90F;

    /* The following are the PerfLock API return values*/
    /** @hide */ public static final int REQUEST_FAILED = -1;
    /** @hide */ public static final int REQUEST_SUCCEEDED = 0;

    /** @hide */ private int handle = 0;

    /* The following two functions are the PerfLock APIs*/
    /** &hide */
    public int perfLockAcquire(int duration, int... list) {
        int rc = REQUEST_SUCCEEDED;
        handle = native_perf_lock_acq(handle, duration, list);
        if (handle == 0)
            rc = REQUEST_FAILED;
        return rc;
    }

    /** &hide */
    public int perfLockRelease() {
        return native_perf_lock_rel(handle);
    }

    /** &hide */
    public void setCpuBoost() {
        int[] configPerfLock = new int[4];
        final int DURATION_OF_PERFLOCK = 2000;

        configPerfLock[0] = ALL_CPUS_PWR_CLPS_DIS;
        configPerfLock[1] = CPUS_ON_MAX;
        configPerfLock[2] = CPU0_FREQ_TURBO_MAX;
        configPerfLock[3] = CPU1_FREQ_TURBO_MAX;

        perfLockAcquire(DURATION_OF_PERFLOCK, configPerfLock);
    }

    /* The following are for internal use only */
    /** @hide */ public static final int CPUOPT_CPU0_PWRCLSP = 1;
    /** @hide */ public static final int CPUOPT_CPU0_FREQMIN = 2;
    /** @hide */ public static final int CPUOPT_CPU1_FREQMIN = 3;

    /** &hide */
    public int cpuSetOptions(int reqType, int reqValue) {
        return native_cpu_setoptions(reqType, reqValue);
    }

    /** &hide */
    protected void finalize() {
        native_deinit();
    }

    private native int  native_perf_lock_acq(int handle, int duration, int list[]);
    private native int  native_perf_lock_rel(int handle);
    private native int  native_cpu_setoptions(int reqtype, int reqvalue);
    private native void native_deinit();
}
