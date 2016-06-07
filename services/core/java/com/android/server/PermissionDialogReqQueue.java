/*
 * Copyright (C) 2015-2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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
package com.android.server;

import java.util.ArrayList;
import java.util.List;

public class PermissionDialogReqQueue {
    public PermissionDialog getDialog() {
        return mDialog;
    }

    public void setDialog(PermissionDialog mDialog) {
        this.mDialog = mDialog;
    }

    public final static class PermissionDialogReq {
        public void set(int res) {
            synchronized (this) {
                mHasResult = true;
                mResult = res;
                notifyAll();
            }
        }

        public int get() {
            synchronized (this) {
                while (!mHasResult) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            return mResult;
        }

        boolean mHasResult = false;
        int mResult;
    }

    private PermissionDialog mDialog;
    private List<PermissionDialogReq> mResultList;

    public PermissionDialogReqQueue() {
        mDialog = null;
        mResultList = new ArrayList<PermissionDialogReq>();
    }

    public void register(PermissionDialogReq res) {
        synchronized (this) {
            mResultList.add(res);
        }
    }

    public void notifyAll(int mode) {
        synchronized (this) {
            while (mResultList.size() != 0) {
                PermissionDialogReq res = mResultList.get(0);
                res.set(mode);
                mResultList.remove(0);
            }
        }
    }
}
