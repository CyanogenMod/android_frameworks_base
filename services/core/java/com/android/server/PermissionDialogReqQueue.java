/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2006 The Android Open Source Project
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
    private List<PermissionDialogReq> resultList;

    public PermissionDialogReqQueue() {
        mDialog = null;
        resultList = new ArrayList<PermissionDialogReq>();
    }

    public void register(PermissionDialogReq res) {
        synchronized (this) {
            resultList.add(res);
        }
    }

    public void notifyAll(int mode) {
        synchronized (this) {
            while (resultList.size() != 0) {
                PermissionDialogReq res = resultList.get(0);
                res.set(mode);
                resultList.remove(0);
            }
        }
    }
}
