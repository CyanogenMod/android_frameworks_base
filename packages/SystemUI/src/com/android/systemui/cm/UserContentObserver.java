/*
 * Copyright (C) 2014 The CyanogenMod Project
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
 * limitations under the License
 */
package com.android.systemui.cm;

import android.app.ActivityManagerNative;
import android.app.IUserSwitchObserver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.util.Log;

/**
 * Simple extension of ContentObserver that also listens for user switch events to call update
 */
public abstract class UserContentObserver extends ContentObserver {
    private static final String TAG = "UserContentObserver";

    private Runnable mUpdateRunnable;

    private IUserSwitchObserver mUserSwitchObserver = new IUserSwitchObserver.Stub() {
        @Override
        public void onUserSwitching(int newUserId, IRemoteCallback reply) {
        }
        @Override
        public void onUserSwitchComplete(int newUserId) throws RemoteException {
            mHandler.post(mUpdateRunnable);
        }
    };

    private Handler mHandler;

    public UserContentObserver(Handler handler) {
        super(handler);
        mHandler = handler;
        mUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                update();
            }
        };
    }

    protected void observe() {
        try {
            ActivityManagerNative.getDefault().registerUserSwitchObserver(mUserSwitchObserver);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to register user switch observer!", e);
        }
    }

    protected void unobserve() {
        try {
            mHandler.removeCallbacks(mUpdateRunnable);
            ActivityManagerNative.getDefault().unregisterUserSwitchObserver(mUserSwitchObserver);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to unregister user switch observer!", e);
        }
    }

    protected abstract void update();

    @Override
    public void onChange(boolean selfChange) {
        update();
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        update();
    }
}
