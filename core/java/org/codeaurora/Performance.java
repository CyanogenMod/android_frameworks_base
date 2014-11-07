/*
 * Copyright (c) 2011-2014, The Linux Foundation. All rights reserved.
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
    /** @hide */ public static final int ALL_CPUS_PC_DIS = 0x101;

    /* Please read the README.txt file for CPUx_FREQ usage and support*/
    /** @hide */ public static final int CPU0_FREQ_NONTURBO_MAX = 0x20A;
    /** @hide */ public static final int CPU0_FREQ_TURBO_MAX = 0x2FE;

    /** @hide */ public static final int CPU1_FREQ_NONTURBO_MAX = 0x30A;
    /** @hide */ public static final int CPU1_FREQ_TURBO_MAX = 0x3FE;

    /** @hide */ public static final int CPU2_FREQ_NONTURBO_MAX = 0x40A;
    /** @hide */ public static final int CPU2_FREQ_TURBO_MAX = 0x4FE;

    /** @hide */ public static final int CPU3_FREQ_NONTURBO_MAX = 0x50A;
    /** @hide */ public static final int CPU3_FREQ_TURBO_MAX = 0x5FE;

    /** @hide */ public static final int CPU0_MAX_FREQ_NONTURBO_MAX = 0x150A;

    /** @hide */ public static final int CPU1_MAX_FREQ_NONTURBO_MAX = 0x160A;

    /** @hide */ public static final int CPU2_MAX_FREQ_NONTURBO_MAX = 0x170A;

    /** @hide */ public static final int CPU3_MAX_FREQ_NONTURBO_MAX = 0x180A;

    /** @hide */ public static final int CPUS_ON_2 = 0x702;
    /** @hide */ public static final int CPUS_ON_3 = 0x703;
    /** @hide */ public static final int CPUS_ON_MAX = 0x7FF;

    /** @hide */ public static final int CPUS_ON_LIMIT_1 = 0x8FE;
    /** @hide */ public static final int CPUS_ON_LIMIT_2 = 0x8FD;
    /** @hide */ public static final int CPUS_ON_LIMIT_3 = 0x8FC;

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
    protected void finalize() {
        native_deinit();
    }

    private native int  native_perf_lock_acq(int handle, int duration, int list[]);
    private native int  native_perf_lock_rel(int handle);
    private native int  native_cpu_setoptions(int reqtype, int reqvalue);
    private native void native_deinit();
}
