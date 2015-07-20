/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.util;

import android.util.Log;
import dalvik.system.PathClassLoader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.System;
import android.view.MotionEvent;
import android.util.DisplayMetrics;

/** @hide */
public class BoostFramework {

    private static final String TAG = "BoostFramework";
    private static final String PERFORMANCE_JAR = "/system/framework/QPerformance.jar";
    private static final String PERFORMANCE_CLASS = "com.qualcomm.qti.Performance";

/** @hide */
    private static boolean mIsLoaded = false;
    private static Method mAcquireFunc = null;
    private static Method mReleaseFunc = null;
    private static Method mAcquireTouchFunc = null;
    private static Constructor<Class> mConstructor = null;

/** @hide */
    private Object mPerf = null;

/** @hide */
    public BoostFramework() {

        if (mIsLoaded == false) {
            try {
                Class perfClass;
                PathClassLoader perfClassLoader;

	        perfClassLoader = new PathClassLoader(PERFORMANCE_JAR,
                                  ClassLoader.getSystemClassLoader());
                perfClass = perfClassLoader.loadClass(PERFORMANCE_CLASS);
                mConstructor = perfClass.getConstructor();

                Class[] argClasses = new Class[] {int.class, int[].class};
                mAcquireFunc =  perfClass.getDeclaredMethod("perfLockAcquire", argClasses);
                Log.v(TAG,"mAcquireFunc method = " + mAcquireFunc);

                argClasses = new Class[] {};
                mReleaseFunc =  perfClass.getDeclaredMethod("perfLockRelease", argClasses);
                Log.v(TAG,"mReleaseFunc method = " + mReleaseFunc);

                argClasses = new Class[] {MotionEvent.class, DisplayMetrics.class, int.class, int[].class};
                mAcquireTouchFunc =  perfClass.getDeclaredMethod("perfLockAcquireTouch", argClasses);
                Log.v(TAG,"mAcquireTouchFunc method = " + mAcquireTouchFunc);

                mIsLoaded = true;
            }
            catch(Exception e) {
                Log.e(TAG,"BoostFramework() : Exception_1 = " + e);
            }
        }

        try {
            if (mConstructor != null) {
                mPerf = mConstructor.newInstance();
            }
        }
        catch(Exception e) {
            Log.e(TAG,"BoostFramework() : Exception_2 = " + e);
        }

        Log.v(TAG,"BoostFramework() : mPerf = " + mPerf);
    }

/** @hide */
/*    private static void loadNative() {
        if(!isLoaded){
            //System.loadLibrary("perf_jni");
            System.loadLibrary("qti_performance");
            isLoaded=true;
        }
        return;
    }
*/

/** @hide */
    public int perfLockAcquire(int duration, int... list) {
        int ret = -1;
        try {
            Object retVal = mAcquireFunc.invoke(mPerf, duration, list);
            ret = (int)retVal;
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfLockRelease() {
        int ret = -1;
        try {
            Object retVal = mReleaseFunc.invoke(mPerf);
            ret = (int)retVal;
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }
/** @hide */
    public int perfLockAcquireTouch(MotionEvent ev, DisplayMetrics metrics,
                                   int duration, int... list) {
        int ret = -1;
        try {
            Object retVal = mAcquireTouchFunc.invoke(mPerf, ev, metrics, duration, list);
            ret = (int)retVal;
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

};
