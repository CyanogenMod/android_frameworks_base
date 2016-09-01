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
import android.os.SystemProperties;
import android.content.Context;

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
    private static Method mIOPStart = null;
    private static Method mIOPStop  = null;
    private static Constructor<Class> mConstructor = null;
    private static int mLockDuration = -1;
    private static int mParamVal[];
    private static String mBoostActivityList[];
    private static long mStartTime;
    private static final int mDebugBoost = getDebugBoostProperty();

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

                argClasses = new Class[] {int.class, String.class};
                mIOPStart =  perfClass.getDeclaredMethod("perfIOPrefetchStart", argClasses);
                Log.v(TAG,"mIOPStart method = " + mIOPStart);

                argClasses = new Class[] {};
                mIOPStop =  perfClass.getDeclaredMethod("perfIOPrefetchStop", argClasses);
                Log.v(TAG,"mIOPStop method = " + mIOPStop);

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

/** @hide Reads system property
     * @return 1 if property is set
     */
    public static int getDebugBoostProperty() {
       return SystemProperties.getInt("persist.debugboost.enable", 0);
    }

/** @hide Acquires debug boost perflock
     * @param ev Touch Screen event
     */
    public void enableDebugBoost(Context context, MotionEvent ev, DisplayMetrics metrics) {

       final int NANO_TO_MILLI = 1000000;
       long elapsedMillis;
       boolean mDebugBoostPossible = false;

       /* extract the XML params */
       if (mLockDuration == -1 || mParamVal == null || mBoostActivityList == null) {
          mLockDuration = context.getResources().getInteger(
             com.android.internal.R.integer.debugBoost_timeout);
          mParamVal = context.getResources().getIntArray(
             com.android.internal.R.array.debugBoost_param_value);
          mBoostActivityList = context.getResources().getStringArray(
             com.android.internal.R.array.debugBoost_activityList);
       }

       String currentActivity = context.getPackageName();

       /* search for the current activity in list */
       for (String match : mBoostActivityList) {
          if (currentActivity.indexOf(match) != -1) {
             /* break if found */
             mDebugBoostPossible = true;
             break;
          }
       }

       elapsedMillis = (System.nanoTime() - mStartTime)/NANO_TO_MILLI;

       /* elapsed should be atleast greater than lock duration */
       if (mDebugBoostPossible == true && elapsedMillis > mLockDuration) {
          perfLockAcquireTouch(ev, metrics, mLockDuration, mParamVal);
          mStartTime = System.nanoTime();
          Log.i(TAG, "dBoost: activity = " + currentActivity + " " + "elapsed = " + elapsedMillis);
       }
    }

/** @hide sets debug boost if property is set
    */
    public boolean boostOverride(Context context, MotionEvent ev, DisplayMetrics metrics) {
       /* Enable debug boost if property is set and
        * current actiivity is present in list
        */
       if (mDebugBoost == 1) {
          enableDebugBoost(context, ev, metrics);
          return true;
       }
       return false;
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

/** @hide */
    public int perfIOPrefetchStart(int pid, String pkg_name)
    {
        int ret = -1;
        try {
            Object retVal = mIOPStart.invoke(mPerf,pid,pkg_name);
            ret = (int)retVal;
        } catch(Exception e) {
            Log.e(TAG,"Exception " + e);
        }
        return ret;
    }

/** @hide */
    public int perfIOPrefetchStop()
    {
        int ret = -1;
         try {
             Object retVal = mIOPStop.invoke(mPerf);
             ret = (int)retVal;
         } catch(Exception e) {
             Log.e(TAG,"Exception " + e);
         }
         return ret;
    }

};
