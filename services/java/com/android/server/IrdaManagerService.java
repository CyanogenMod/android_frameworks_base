/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.hardware.IIrdaManager;
import android.os.Looper;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

/** @hide */

public class IrdaManagerService extends IIrdaManager.Stub {
    private static final String TAG = "IrdaManagerService";
    private IrdaWorkerHandler mHandler;
    private Looper mServiceLooper;
    private Context mContext;
    private int mNativePointer;
    public IrdaManagerService(Context context) {
        super();
        mContext = context;
        mNativePointer = init_native();
        HandlerThread mWorker = new HandlerThread("IrdaServiceWorker");
        mWorker.start();
        mServiceLooper = mWorker.getLooper();
        mHandler = new IrdaWorkerHandler(mServiceLooper);
    }

    public void write_irsend(String irCode) {
        if (irCode != null) {
            Message msg = mHandler.obtainMessage(IrdaWorkerHandler.MESSAGE_SET, irCode);
            msg.sendToTarget();
        }
    }

    private class IrdaWorkerHandler extends Handler {
        private static final int MESSAGE_SET = 0;

        public IrdaWorkerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MESSAGE_SET) {
                String irCode = (String) msg.obj;
                byte[] buffer = irCode.getBytes();
                send_ircode_native(mNativePointer, buffer);
            }
        }
    }

    private static native int init_native();
    private static native void finalize_native(int ptr);
    private static native void send_ircode_native(int ptr, byte[] buffer);
}
