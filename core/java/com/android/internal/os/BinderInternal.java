/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.os;

import android.os.IBinder;
import android.os.SystemClock;
import android.util.EventLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.lang.Object;
/**
 * Private and debugging Binder APIs.
 * 
 * @see IBinder
 */
public class BinderInternal {
    static WeakReference<GcWatcher> sGcWatcher
            = new WeakReference<GcWatcher>(new GcWatcher());
    static ArrayList<Runnable> sGcWatchers = new ArrayList<>();
    static Runnable[] sTmpWatchers = new Runnable[1];
    static long sLastGcTime;
    /* Maximum duration a GC can be delayed. */
    private static final int GC_DELAY_MAX_DURATION = 3000;
    /**
     * Maximum number of times a GC can be delayed since the
     * original request.
     */
    private static final int POSTPONED_GC_MAX = 5;

    static final class GcWatcher {
        @Override
        protected void finalize() throws Throwable {
            handleGc();
            sLastGcTime = SystemClock.uptimeMillis();
            synchronized (sGcWatchers) {
                sTmpWatchers = sGcWatchers.toArray(sTmpWatchers);
            }
            for (int i=0; i<sTmpWatchers.length; i++) {
                if (sTmpWatchers[i] != null) {
                    sTmpWatchers[i].run();
                }
            }
            sGcWatcher = new WeakReference<GcWatcher>(new GcWatcher());
        }
    }

    public static void addGcWatcher(Runnable watcher) {
        synchronized (sGcWatchers) {
            sGcWatchers.add(watcher);
        }
    }

    /**
     * Add the calling thread to the IPC thread pool.  This function does
     * not return until the current process is exiting.
     */
    public static final native void joinThreadPool();
    
    /**
     * Return the system time (as reported by {@link SystemClock#uptimeMillis
     * SystemClock.uptimeMillis()}) that the last garbage collection occurred
     * in this process.  This is not for general application use, and the
     * meaning of "when a garbage collection occurred" will change as the
     * garbage collector evolves.
     * 
     * @return Returns the time as per {@link SystemClock#uptimeMillis
     * SystemClock.uptimeMillis()} of the last garbage collection.
     */
    public static long getLastGcTime() {
        return sLastGcTime;
    }

    /**
     * Return the global "context object" of the system.  This is usually
     * an implementation of IServiceManager, which you can use to find
     * other services.
     */
    public static final native IBinder getContextObject();
    
    /**
     * Special for system process to not allow incoming calls to run at
     * background scheduling priority.
     * @hide
     */
    public static final native void disableBackgroundScheduling(boolean disable);
    
    static native final void handleGc();
    
    public static void forceGc(String reason) {
        EventLog.writeEvent(2741, reason);
        Runtime.getRuntime().gc();
    }
    
    /**
     * TimerGc Callable : Wait for a certain time, and execute the BinderGc.
     * Set the postponed count to 0.
     */
    public static class TimerGc implements Callable<Void> {
        private long waitTime;
        public TimerGc(long timeInMillis){
            this.waitTime=timeInMillis;
        }
        @Override
        public Void call() throws Exception {
            Thread.sleep(waitTime);
            forceGc("Binder");
            postponedGcCount = 0;
            return null;
        }
    }

    /**
     * lastGcDelayRequestTime records the time-stamp of the last time
     * a GC delay request was made.
     */
    static long lastGcDelayRequestTime = SystemClock.uptimeMillis();
    static TimerGc timerGcInstance = null ;
    static FutureTask<Void> futureTaskInstance = null ;
    static ExecutorService executor = Executors.newFixedThreadPool(1);
    static int postponedGcCount = 0;
    static Object delayGcMonitorObject = new Object();

    /**
     * modifyDelayedGcParams : Call from the framework based on some special Ux event.
     * like appLaunch.
     *
     * 1. If this is the first time for the trigger event, or, if there is no scheduled
     *    task, create a new FutureTaskInstance, and set the lastGcDelayRequestTime.
     *    This will be used by forceBinderGc later.
     *
     * 2. If the postponed iterations hit a maximum limit, do nothing. Let the current
     *    task execute the gc. If not,
     *
     *    a. Set the start time.
     *    b. Increment the postponed count
     *    c. Cancel the current task and start a new one for GC_DELAY_MAX_DURATION.
     */
    public static void modifyDelayedGcParams() {
        long nowTime = SystemClock.uptimeMillis();
        synchronized(delayGcMonitorObject) {
            if ((futureTaskInstance != null) && (postponedGcCount != 0)) {
                if (postponedGcCount <= POSTPONED_GC_MAX) {
                    futureTaskInstance.cancel(true);
                    if (futureTaskInstance.isCancelled()) {
                        lastGcDelayRequestTime = nowTime;
                        postponedGcCount++;
                        timerGcInstance = new TimerGc(GC_DELAY_MAX_DURATION);
                        futureTaskInstance = new FutureTask<Void>(timerGcInstance);
                        executor.execute(futureTaskInstance);
                    }
                }
            } else {
                lastGcDelayRequestTime = nowTime;
                timerGcInstance = new TimerGc(GC_DELAY_MAX_DURATION);
                futureTaskInstance = new FutureTask<Void>(timerGcInstance);
            }
        }
    }

    /**
     * Modified forceBinderGc. The brief algorithm is as follows --
     *
     * 1. If no futureTaskInstance has been initiated, directly force a BinderGc.
     * 2. Check for the duration since the last request, and see if it was within the
     *    last GC_DELAY_MAX_DURATION secs. If yes, we need to delay the GC until
     *    GC_DELAY_MAX_DURATION.
     * 3. If there is a task scheduled (postponedGcCount != 0), we merely prevent this GC,
     *    and let the GC scheduled execute.
     * 4. If no task is scheduled, we schedule one now for (GC_DELAY_MAX_DURATION - touch duration),
     *    and update postponedGcCount.
     */
    static void forceBinderGc() {
        synchronized(delayGcMonitorObject) {
            if (futureTaskInstance != null) {
                long lastGcDelayRequestDuration = (SystemClock.uptimeMillis() - lastGcDelayRequestTime);
                if (lastGcDelayRequestDuration < GC_DELAY_MAX_DURATION) {
                    if (postponedGcCount != 0)
                        return;
                    futureTaskInstance.cancel(true);
                    timerGcInstance = new TimerGc(GC_DELAY_MAX_DURATION - lastGcDelayRequestDuration);
                    futureTaskInstance = new FutureTask<Void>(timerGcInstance);
                    postponedGcCount = 1;
                    executor.execute(futureTaskInstance);
                    return;
                }
            }
        }
        forceGc("Binder");
    }
}
