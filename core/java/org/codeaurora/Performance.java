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
import android.view.VelocityTracker;
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
    /* Min/Max velocity pixel/second values */
    private int mMinVelocity = 150;
    private int mMaxVelocity = 24000;
    /* Tunables End. */

    class TouchInfo {
        /* Touch start position */
        private int mStartX = 0;
        private int mStartY = 0;

        /* Touch current position */
        private int mCurX = 0;
        private int mCurY = 0;

        /* Width, Height to declare drag */
        private int mMinDragW = 0;
        private int mMinDragH = 0;

        /* Note: All coordinates are density normalized values */
        private void reset() {
            mStartX = mStartY = mCurX = mCurY = 0;
            mMinDragH = mMinDragW = 0;
            isFlingEnabled = false;
        }
        /* update current x,y coordinates */
        private void setXY(int dx, int dy) {
            mCurX = dx;
            mCurY = dy;
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
    private static VelocityTracker mVelocityTracker = null;

    /* The following two functions are the PerfLock APIs*/
    /** &hide */
    public int perfLockAcquireTouch(MotionEvent ev, DisplayMetrics metrics,
                                                        int duration, int... list) {
        int rc = REQUEST_FAILED;
        final int actionMasked = ev.getActionMasked();
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        final int y = (int) ev.getY(pointerIndex);
        final int x = (int) ev.getX(pointerIndex);

        int dx = (int)((x * 1f)/metrics.density);
        int dy = (int)((y * 1f)/metrics.density);

        boolean isBoostRequired = false;

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                if(mVelocityTracker == null) {
                    // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                    mVelocityTracker = VelocityTracker.obtain();
                }
                else {
                    // Reset the velocity tracker back to its initial state.
                    mVelocityTracker.clear();
                }

                if(mVelocityTracker != null) {
                    // Add a user's movement to the tracker.
                    mVelocityTracker.addMovement(ev);
                }
                if (mTouchInfo == null) {
                    mTouchInfo = new TouchInfo();
                }
                if (mTouchInfo != null) {
                    // Reset drag boost params
                    mTouchInfo.reset();
                    // set start, current position
                    mTouchInfo.setStartXY(dx, dy);
                    // set drag movement thresholds
                    mTouchInfo.setDragWH((int)(mWDragPix * 1f/metrics.density),
                                         (int)(mHDragPix * 1f/metrics.density));
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if(mVelocityTracker != null) {
                    // Add a user's movement to the tracker.
                    mVelocityTracker.addMovement(ev);
                }

                if(mTouchInfo != null) {
                    int xdiff = Math.abs(dx - mTouchInfo.mCurX);
                    int ydiff = Math.abs(dy - mTouchInfo.mCurY);
                    //set current position
                    mTouchInfo.setXY(dx, dy);

                    if ((xdiff > mTouchInfo.mMinDragW) ||
                        (ydiff > mTouchInfo.mMinDragH)) {
                        //drag detected. Boost it.
                        isBoostRequired = true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                if(mVelocityTracker != null) {
                    // Add a user's movement to the tracker
                    mVelocityTracker.addMovement(ev);
                    // compute velocity (pixel per second)
                    mVelocityTracker.computeCurrentVelocity(1000, mMaxVelocity);
                    final int initialVelocity =
                        Math.abs((int) mVelocityTracker.getYVelocity(pointerId));
                    if (initialVelocity > mMinVelocity) {
                        //Velocity is more than min velocity.Calculate fling boost duration.
                        duration *=  ((1f * initialVelocity)/(1f * mMinVelocity));
                        //Enable boost
                        isBoostRequired = true;
                        break;
                    }
                }
                if(mTouchInfo != null) {
                    int xdiff = Math.abs(dx - mTouchInfo.mCurX);
                    int ydiff = Math.abs(dy - mTouchInfo.mCurY);

                    if ((xdiff > mTouchInfo.mMinDragW) ||
                        (ydiff > mTouchInfo.mMinDragH)) {
                        //Only drag boost. Boost it.
                        isBoostRequired = true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if(mTouchInfo != null) {
                    mTouchInfo.reset();
                }
                break;
            }

            default:
                break;
        }
        if(isBoostRequired ==  true) {
            handle = native_perf_lock_acq(handle, duration, list);
            if (handle != 0) {
               rc = REQUEST_SUCCEEDED;
            }
        }
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
