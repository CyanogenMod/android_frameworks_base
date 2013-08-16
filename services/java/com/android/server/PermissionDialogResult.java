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

import android.os.Parcel;
import android.os.Parcelable;

class PermissionDialogResult {

    public final static class Result implements Parcelable {

        boolean mHasResult = false;
        int mResult;

        public Result() {
        }

        public Result(Parcel in) {
            mHasResult = in.readInt() == 1 ? true : false;
            mResult = in.readInt();
        }

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
                        // Do nothing
                    }
                }
            }
            return mResult;
        }

        public static final Parcelable.Creator<Result> CREATOR =
                new Parcelable.Creator<Result>() {
            public Result createFromParcel(Parcel in) {
                return new Result(in);
            }

            public Result[] newArray(int size) {
                return new Result[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mHasResult ? 1 : 0);
            dest.writeInt(mResult);
        }
    }

    public PermissionDialog mDialog;
    public List<Result> resultList;

    public PermissionDialogResult() {
        mDialog = null;
        resultList = new ArrayList<Result>();
    }

    public void register(Result res) {
        synchronized(this) {
            resultList.add(res);
        }
    }

    public void notifyAll(int mode) {
        synchronized(this) {
            for (Result res : resultList) {
                res.set(mode);
            }
            resultList.clear();
        }
    }
}
