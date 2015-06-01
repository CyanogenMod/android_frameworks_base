/*
* Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
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

    public static final String TAG = "MultiTaskDealer";
    public static final String PACKAGEMANAGER_SCANER = "packagescan";
    private static final boolean DEBUG_TASK = false;

    private static HashMap<String, WeakReference<MultiTaskDealer>> map = new HashMap<String, WeakReference<MultiTaskDealer>>();

    public static MultiTaskDealer getDealer(String name) {
        WeakReference<MultiTaskDealer> ref = map.get(name);
        MultiTaskDealer dealer = ref!=null?ref.get():null;
        return dealer;
    }

    public static MultiTaskDealer startDealer(String name,int taskCount) {
        MultiTaskDealer dealer = getDealer(name);
        if(dealer==null) {
            dealer = new MultiTaskDealer(name,taskCount);
            WeakReference<MultiTaskDealer> ref = new WeakReference<MultiTaskDealer>(dealer);
            map.put(name,ref);
        }
        return dealer;
    }

    public void startLock() {
        mLock.lock();
    }

    public void endLock() {
        mLock.unlock();
    }

    private ThreadPoolExecutor mExecutor;
    private int mTaskCount = 0;
    private boolean mNeedNotifyEnd = false;
    private Object mObjWaitAll = new Object();
    private ReentrantLock mLock = new ReentrantLock();

    public MultiTaskDealer(String name,int taskCount) {
        final String taskName = name;
        ThreadFactory factory = new ThreadFactory()
        {
            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(final Runnable r) {
                if (DEBUG_TASK) Log.d(TAG, "create a new thread:" + taskName);
                return new Thread(r, taskName + "-" + mCount.getAndIncrement());
            }
        };
        mExecutor = new ThreadPoolExecutor(taskCount, taskCount, 5, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), factory){
            protected void afterExecute(Runnable r, Throwable t) {
                if(t!=null) {
                    t.printStackTrace();
                }
                MultiTaskDealer.this.TaskCompleteNotify(r);
                if (DEBUG_TASK) Log.d(TAG, "end task");
                super.afterExecute(r,t);
            }
            protected void beforeExecute(Thread t, Runnable r) {
                if (DEBUG_TASK) Log.d(TAG, "start task");
                super.beforeExecute(t,r);
            }
        };
    }

    public void addTask(Runnable task) {
        synchronized (mObjWaitAll) {
            mTaskCount+=1;
        }
        mExecutor.execute(task);
        if (DEBUG_TASK) Log.d(TAG, "addTask");
    }

    private void TaskCompleteNotify(Runnable task) {
        synchronized (mObjWaitAll) {
            mTaskCount-=1;
            if(mTaskCount<=0 && mNeedNotifyEnd) {
                if (DEBUG_TASK) Log.d(TAG, "complete notify");
                mObjWaitAll.notify();
            }
        }
    }

    public void waitAll() {
        if (DEBUG_TASK) Log.d(TAG, "start wait all");
        synchronized (mObjWaitAll) {
            if(mTaskCount>0) {
                mNeedNotifyEnd = true;
                try {
                    mObjWaitAll.wait();
                } catch (Exception e) {
                }
                mNeedNotifyEnd = false;
            }
            if (DEBUG_TASK) Log.d(TAG, "wait finish");
            return;
        }
    }
}
