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
import android.view.MotionEvent;
import android.util.DisplayMetrics;

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

    /** @hide */ public static final int SCHED_PREFER_IDLE = 0x3E01;
    /** @hide */ public static final int SCHED_MIGRATE_COST = 0x3F01;

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

    private static boolean isFlingEnabled = false;

    /* Tunables Begin. Can be moved to config.xml */
    /* Division factor */
    private int mDivFact = 6;
    /* Min drag in horizontal,vertical (abs pixels) */
    private int mWDragPix = 12;
    private int mHDragPix = 12;
    /* fling boost duration (msec) */
    private int mFlingBoostDuration = 1500;
    /* Tunables End. */

    class TouchInfo {
        /* Touch start position */
        private int mStartX = 0;
        private int mStartY = 0;

        /* Touch current position */
        private int mCurX = 0;
        private int mCurY = 0;

        /* Width, Height to declare fling */
        private int mMinFlingW = 0;
        private int mMinFlingH = 0;

        /* Width, Height to declare drag */
        private int mMinDragW = 0;
        private int mMinDragH = 0;

        /* Note: All coordinates are density normalized values */
        private void reset() {
            mStartX = mStartY = mCurX = mCurY = 0;
            mMinFlingH = mMinFlingW = mMinDragH = mMinDragW = 0;
            isFlingEnabled = false;
        }
        /* update current x,y coordinates */
        private void setXY(int dx, int dy) {
            mCurX = dx;
            mCurY = dy;
        }
        /* Set fling min width, height */
        private void setFlingWH(int dw, int dh) {
            mMinFlingW = dw;
            mMinFlingH = dh;
        }
        /* Set drag min width, height */
        private void setDragWH(int dw, int dh) {
            mMinDragW = dw;
            mMinDragH = dh;
        }
        private void setStartXY(int dx, int dy) {
            mStartX = mCurX = dx;
            mStartY = mCurY = dy;
        }
    };
    private static TouchInfo mTouchInfo = null;


    /* The following two functions are the PerfLock APIs*/
    /** &hide */
    public int perfLockAcquireTouch(MotionEvent ev, DisplayMetrics metrics, int duration, int... list) {
        int rc = REQUEST_FAILED;
        if (mTouchInfo == null)
            mTouchInfo = new TouchInfo();
        final int actionMasked = ev.getActionMasked();
        final int pointerIndex = ev.getActionIndex();

        final int y = (int) ev.getY(pointerIndex);
        final int x = (int) ev.getX(pointerIndex);

        int dx = (int)((x * 1f)/metrics.density);
        int dy = (int)((y * 1f)/metrics.density);

        boolean ret = true;

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                mTouchInfo.reset();
                mTouchInfo.setStartXY(dx, dy);
                mTouchInfo.setFlingWH(
                       (int)((metrics.widthPixels * 1f)/(mDivFact * metrics.density)),
                       (int)((metrics.heightPixels * 1f)/(mDivFact * metrics.density)));
                mTouchInfo.setDragWH((int)(mWDragPix * 1f/metrics.density),
                                     (int)(mHDragPix * 1f/metrics.density));
                return rc;
            }

            case MotionEvent.ACTION_MOVE: {
                int xdiff = dx - mTouchInfo.mCurX;
                int ydiff = dy - mTouchInfo.mCurY;
                if (xdiff < 0) xdiff *= -1;
                if (ydiff < 0) ydiff *= -1;

                mTouchInfo.setXY(dx, dy);
                if ((xdiff > mTouchInfo.mMinDragW) ||
                    (ydiff > mTouchInfo.mMinDragH)){
                    ret = false;
                }

                if (ret == true)
                    return rc;
                break;
            }

            case MotionEvent.ACTION_UP: {
                int xdiff = dx - mTouchInfo.mCurX;
                int ydiff = dy - mTouchInfo.mCurY;
                if (xdiff < 0) xdiff *= -1;
                if (ydiff < 0) ydiff *= -1;
                if ((xdiff > mTouchInfo.mMinDragW) ||
                    (ydiff > mTouchInfo.mMinDragH)){
                    ret = false;
                }

                mTouchInfo.reset();
                if (ret == true){
                    return rc;
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mTouchInfo.reset();
                return rc;
            }

            default:
                return rc;
        }

        rc = REQUEST_SUCCEEDED;
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
