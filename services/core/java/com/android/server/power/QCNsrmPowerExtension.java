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

package com.android.server.power;

import java.util.ArrayList;

import android.os.Binder;
import android.os.PowerManager;
import android.os.Process;
import android.util.Slog;

public final class QCNsrmPowerExtension {
    static final String TAG = "QCNsrmPowerExtn";
    static final boolean localLOGV = false;
    private PowerManagerService pmHandle;

    //track the blocked uids in PowerManagerService.
    private final ArrayList<Integer> mPmsBlockedUids = new ArrayList<Integer>();

    public QCNsrmPowerExtension (PowerManagerService handle) {
        PowerManagerService pmHandle = handle ;
    }

    protected void checkPmsBlockedWakelocks (
        int uid, int pid, int flags,
        String tag, PowerManagerService.WakeLock pMwakeLock
        ) {
        if(mPmsBlockedUids.contains(new Integer(uid)) && uid != Process.myUid()) {
            // wakelock acquisition for blocked uid, disable it.
            if (localLOGV) {
                Slog.d(TAG, "uid is blocked disabling wakeLock flags=0x" +
                       Integer.toHexString(flags) + " tag=" + tag + " uid=" +
                       uid + " pid =" + pid);
            }
            updatePmsBlockedWakelock(pMwakeLock, true);
        }
    }

    private boolean checkWorkSourceObjectId (
        int uid, PowerManagerService.WakeLock wl
        ) {
        try {
            for (int index = 0; index < wl.mWorkSource.size(); index++) {
                if (uid == wl.mWorkSource.get(index)) {
                    if (localLOGV) Slog.v(TAG, "WS uid matched");
                    return true;
                }
            }
        }
        catch (Exception e) {
            return false;
        }
        return false;
    }

    protected boolean processPmsBlockedUid (
        int uid, boolean isBlocked,
        ArrayList<PowerManagerService.WakeLock> mWakeLocks
        ) {
        boolean changed = false;
        if (updatePmsBlockedUidAllowed(uid, isBlocked))
            return changed;

        for (int index = 0; index < mWakeLocks.size(); index++) {
            PowerManagerService.WakeLock wl = mWakeLocks.get(index);
            if(wl != null) {
                // update the wakelock for the blocked uid
                if ((wl.mOwnerUid == uid || checkWorkSourceObjectId(uid, wl))
                    || (wl.mTag.startsWith("*sync*") && wl.mOwnerUid ==
                        Process.SYSTEM_UID)) {
                    if(updatePmsBlockedWakelock(wl, isBlocked)) {
                        changed = true;
                    }
                }
            }
        }
        if(changed) {
            pmHandle.mDirty |= pmHandle.DIRTY_WAKE_LOCKS;
            pmHandle.updatePowerStateLocked();
        }
        return changed;
    }

    protected boolean updatePmsBlockedUidAllowed (
        int uid, boolean isBlocked
        ) {
        if (localLOGV) Slog.v(TAG, "updateBlockedUids: uid = " + uid +
                               "isBlocked = " + isBlocked);
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            if (localLOGV) Slog.v(TAG, "UpdateBlockedUids is not allowed");
            return true;
        }
        updatePmsBlockedUids(uid, isBlocked);
        return false;
    }

    private void updatePmsBlockedUids (int uid, boolean isBlocked) {
        if(isBlocked) {
            if (localLOGV) Slog.v(TAG, "adding powerMgr mPmBlockedUids "+
                                  "with uid "+ uid);
            mPmsBlockedUids.add(new Integer(uid));
        }
        else {
            if (localLOGV) Slog.v(TAG, "clearing powerMgr mPmBlockedUids ");
            mPmsBlockedUids.clear();
        }
    }

    private boolean updatePmsBlockedWakelock (
        PowerManagerService.WakeLock wakeLock, boolean update
        ) {
        if (wakeLock != null && ((wakeLock.mFlags &
                                  PowerManager.WAKE_LOCK_LEVEL_MASK
                                  ) == PowerManager.PARTIAL_WAKE_LOCK )) {
            if (wakeLock.mDisabled != update) {
                wakeLock.mDisabled = update;
                if (wakeLock.mDisabled) {
                    // This wake lock is no longer being respected.
                    pmHandle.notifyWakeLockReleasedLocked(wakeLock);
                } else {
                    pmHandle.notifyWakeLockAcquiredLocked(wakeLock);
                }
                return true;
            }
        }
        return false;
    }
}

