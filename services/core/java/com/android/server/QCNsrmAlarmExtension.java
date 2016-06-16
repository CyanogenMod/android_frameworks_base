/*
 *Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 *Redistribution and use in source and binary forms, with or without
 *modification, are permitted provided that the following conditions are
 *met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 *THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.server;

import android.os.Binder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public final class QCNsrmAlarmExtension {
    static final String TAG = "QCNsrmAlarmExtn";
    static final boolean localLOGV = false;
    private AlarmManagerService almHandle;

    //track the blocked and triggered uids in AlarmManagerService
    private static final ArrayList<Integer> mTriggeredUids = new ArrayList<Integer>();
    private static final ArrayList<Integer> mBlockedUids = new ArrayList<Integer>();
    private static final int BLOCKED_UID_CHECK_INTERVAL = 1000; // 1 sec.

    public QCNsrmAlarmExtension(AlarmManagerService handle) {
        almHandle = handle;
    }

    //AlarmManagerService extension Methods
    protected void processBlockedUids(int uid, boolean isBlocked, PowerManager.WakeLock mWakeLock ){
        if (localLOGV) Slog.v(TAG, "UpdateBlockedUids: uid = " + uid +
                                  " isBlocked = " + isBlocked);
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            if (localLOGV) Slog.v(TAG, "UpdateBlockedUids is not allowed");
            return;
        }

        if(isBlocked) {
            if (localLOGV) Slog.v(TAG, "updating alarmMgr mBlockedUids "+
                                       "with uid " + uid);
            mBlockedUids.add(new Integer(uid));
            Timer checkBlockedUidTimer = new Timer();
            checkBlockedUidTimer.schedule( new CheckBlockedUidTimerTask(
                                                   uid,
                                                   mWakeLock),
                                           BLOCKED_UID_CHECK_INTERVAL);
        } else {
            if (localLOGV) Slog.v(TAG, "clearing alarmMgr mBlockedUids ");
            mBlockedUids.clear();
        }
    }

    protected void addTriggeredUid (int uid){
        if (localLOGV) Slog.v(TAG, "adding uid to mTriggeredUids uid=" + uid);
        mTriggeredUids.add(new Integer(uid));

    }

    protected void removeTriggeredUid (int uid) {
        if (localLOGV) Slog.v(TAG, "removing uid from mTriggeredUids uid= " + uid);
        mTriggeredUids.remove(new Integer(uid));
    }

    protected boolean hasBlockedUid (int uid) {

        return mBlockedUids.contains(uid);

    }

    class CheckBlockedUidTimerTask extends TimerTask {
        private int mUid;
        PowerManager.WakeLock mWakeLock;

        CheckBlockedUidTimerTask(int uid, PowerManager.WakeLock lWakeLock) {
            mUid = uid;
            mWakeLock = lWakeLock;
        }

        @Override
        public void run(){
            if (mBlockedUids.contains(mUid) && mTriggeredUids.contains(mUid)) {
                synchronized(almHandle.mLock) {
                    if (mWakeLock.isHeld()) {
                        mWakeLock.release();
                        if (localLOGV)
                            Slog.v(TAG, "CheckBlockedUidTimerTask: AM "+
                                   "WakeLock Released Internally!!");
                    }
                }
                return;
            }
        }
    }
}
