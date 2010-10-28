/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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

package android.webkit;

import android.os.Handler;
import android.util.Log;
import android.os.Message;
import android.os.Process;

import java.lang.Thread;
import java.lang.InterruptedException;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

final class DnsResolver {

    private static final String LOGTAG = "webcore";

    /* Max Thread pool size is  taken from data published by google
     * that mentions max hosts per webpage = 8 */
    private final int MAX_DNS_RESOLVER_THREAD_POOL_SIZE = 8;

    /* This number is derived by considering various factors such as OS DNS cache size,
     * Browser Cache size and realistic need to spent time on DNS prefetch
     */
    private final int MAX_PARALLEL_DNS_QUERIES_PER_PAGE = 64;

    private volatile boolean mDnsResolverThreadPoolRunning = false;

    private volatile boolean mShutDownInProgress = false;

    private static DnsResolver sDnsResolver;

    private HashMap mHostNamesToBeResolved;

    private ExecutorService  mDnsResolverThreadPool;

    private static int mDnsResolverRefCount = 0;

    /* Lock to synchronize the access to threadpool */
    private static Object mThreadPoolLock = new Object();

    public static synchronized DnsResolver createDnsResolver() {
        if (DebugFlags.WEB_VIEW_CORE)
            Log.v(LOGTAG, "Creating DNS resolver");
        if (sDnsResolver == null) {
            sDnsResolver = new DnsResolver();
        }
        ++mDnsResolverRefCount;
        return sDnsResolver;
    }

    public static DnsResolver getInstance() {
        return sDnsResolver;
    }

    private DnsResolver() {
        createDnsResolverThreadPool();
    }

    private void createDnsResolverThreadPool() {
        final Runnable startDnsResolver = new Runnable() {
            public void run() {
                /* DNS resolver priority should be same as of HTTP thread pool */
                Process.setThreadPriority( android.os.Process.THREAD_PRIORITY_DEFAULT +
                                           android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE);
                mDnsResolverThreadPool = Executors.newFixedThreadPool(MAX_DNS_RESOLVER_THREAD_POOL_SIZE);
                mHostNamesToBeResolved = new HashMap();
                boolean bResolvedPriorityHostNames = false;
                int dnsQueryCounter = 0;
                int numHosts = 0;
                while(!mShutDownInProgress) {
                    synchronized(mHostNamesToBeResolved) {
                        numHosts = mHostNamesToBeResolved.size();
                    }
                    if(numHosts <= 0) {
                        try {
                            dnsQueryCounter = 0;
                            bResolvedPriorityHostNames = false;
                            mDnsResolverThreadPoolRunning = true;
                            synchronized(mThreadPoolLock) {
                                mThreadPoolLock.wait();
                            }
                        } catch(java.lang.InterruptedException e) {
                        }
                    }
                    else {
                        synchronized(mHostNamesToBeResolved) {
                            Iterator iterator = mHostNamesToBeResolved.entrySet().iterator();
                            while(iterator.hasNext() && mDnsResolverThreadPoolRunning && (dnsQueryCounter < MAX_PARALLEL_DNS_QUERIES_PER_PAGE)) {
                                Map.Entry entry = (Map.Entry) iterator.next();
                                final String hostName = (String)entry.getKey();
                                final String priority = (String)entry.getValue();
                                if( (!bResolvedPriorityHostNames && priority.equalsIgnoreCase("1"))  ||
                                    ( bResolvedPriorityHostNames && priority.equalsIgnoreCase("0"))
                                  ) {
                                    ++dnsQueryCounter;
                                    iterator.remove();
                                    Runnable task = new Runnable() {
                                        public void run() {
                                            try {
                                                java.net.InetAddress.getByName(hostName);
                                            } catch(java.net.UnknownHostException e) {
                                            }
                                        }
                                    };
                                    mDnsResolverThreadPool.execute (task);
                                }
                            }
                            if(!mDnsResolverThreadPoolRunning || (dnsQueryCounter >= MAX_PARALLEL_DNS_QUERIES_PER_PAGE)) {
                                mHostNamesToBeResolved.clear();
                            }
                            bResolvedPriorityHostNames = (bResolvedPriorityHostNames) ? false:true;
                        }
                    }
                }
                mDnsResolverThreadPool.shutdown();
                sDnsResolver = null;
            }
        };
        Thread dnsResolver = new Thread(startDnsResolver);
        dnsResolver.setName("DNS resolver");
        dnsResolver.start();
    }

    public synchronized void destroyDnsResolver() {
        if (DebugFlags.WEB_VIEW_CORE)
            Log.v(LOGTAG, "Destroying DNS Resolver");
        --mDnsResolverRefCount;
        if(mDnsResolverRefCount == 0) {
            mShutDownInProgress = true;
            mDnsResolverThreadPoolRunning = false;
            synchronized(mThreadPoolLock) {
                mThreadPoolLock.notifyAll();
            }
        }
    }

    public void resolveDnsForHost(String hostName, String priority) {
        if(hostName == null) {
            return;
        }
        synchronized(mHostNamesToBeResolved) {
            if(mHostNamesToBeResolved.size() > 0 ) {
                return;
            }
            mHostNamesToBeResolved.put(hostName,priority);
        }
        resumeDnsResolverThreadPool();
    }

    public void resolveDnsForHostMap(HashMap hostMap) {
        if(hostMap == null) {
            return;
        }
        synchronized (mHostNamesToBeResolved) {
            mHostNamesToBeResolved.putAll(hostMap);
        }
        resumeDnsResolverThreadPool();
    }

    /* pause will flush all pending DNS queries at the DNS resolver */
    public void pauseDnsResolverThreadPool()  {
        mDnsResolverThreadPoolRunning = false;
    }

    /* resume will start DNS resolver executing DNS queries  */
    public void resumeDnsResolverThreadPool()  {
        mDnsResolverThreadPoolRunning = true;
        synchronized(mThreadPoolLock) {
            mThreadPoolLock.notifyAll();
        }
    }

    /* returns the max number of DNS queries that can be made in background for a page */
    public int getMaxParallelDnsQueryPerPage() {
        return MAX_PARALLEL_DNS_QUERIES_PER_PAGE;
    }
}
