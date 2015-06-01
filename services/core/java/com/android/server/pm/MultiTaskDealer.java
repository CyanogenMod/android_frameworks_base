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
package com.android.server.pm;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import android.util.Log;

public class MultiTaskDealer {

    private static final String TAG = "MultiTaskDealer";

    private static final boolean DEBUG = false;

    private static HashMap<String, WeakReference<MultiTaskDealer>> MAP =
            new HashMap<String, WeakReference<MultiTaskDealer>>();

    public static MultiTaskDealer getDealer(String name) {
        WeakReference<MultiTaskDealer> ref = MAP.get(name);
        return ref != null ? ref.get() : null;
    }

    public static MultiTaskDealer startDealer(String name,int taskCount) {
        MultiTaskDealer dealer = getDealer(name);
        if(dealer == null) {
            dealer = new MultiTaskDealer(name, taskCount);
            WeakReference<MultiTaskDealer> ref = new WeakReference<MultiTaskDealer>(dealer);
            MAP.put(name,ref);
        }
        return dealer;
    }

    private ThreadPoolExecutor mExecutor;
    private int mTaskCount = 0;
    private boolean mNeedNotifyEnd = false;
    private Object mObjWaitAll = new Object();
    private ReentrantLock mLock = new ReentrantLock();

    private MultiTaskDealer(final String taskName, int taskCount) {

        final ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(final Runnable r) {
                if (DEBUG) {
                    Log.d(TAG, "create a new thread:" + taskName);
                }
                return new Thread(r, taskName + "-" + mCount.getAndIncrement());
            }
        };

        mExecutor = new ThreadPoolExecutor(taskCount, taskCount, 5, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), factory){
            protected void afterExecute(Runnable r, Throwable t) {
                if(t != null) {
                    Log.d(TAG, "A job from task (" + taskName + ") failed.", t);
                }
                onTaskComplete(r);
                if (DEBUG) {
                    Log.d(TAG, "A job from task (" + taskName + ") were completed.");
                }
                super.afterExecute(r,t);
            }
            protected void beforeExecute(Thread t, Runnable r) {
                if (DEBUG) {
                    Log.d(TAG, "A new job from task (" + taskName + ") started.");
                }
                super.beforeExecute(t, r);
            }
        };
    }

    public void addTask(Runnable task) {
        synchronized (mObjWaitAll) {
            mTaskCount += 1;
        }
        mExecutor.execute(task);
    }

    private void onTaskComplete(Runnable task) {
        synchronized (mObjWaitAll) {
            mTaskCount -= 1;
            if(mTaskCount <= 0 && mNeedNotifyEnd) {
                mObjWaitAll.notify();
            }
        }
    }

    public void waitAll() {
        synchronized (mObjWaitAll) {
            if(mTaskCount > 0) {
                mNeedNotifyEnd = true;
                try {
                    mObjWaitAll.wait();
                } catch (InterruptedException e) {
                }
                mNeedNotifyEnd = false;
            }
            return;
        }
    }

    public void startLock() {
        mLock.lock();
    }

    public void endLock() {
        mLock.unlock();
    }
}
