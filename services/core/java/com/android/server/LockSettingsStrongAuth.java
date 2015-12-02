/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternUtils.StrongAuthTracker;

import android.app.trust.IStrongAuthTracker;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseIntArray;

import java.util.ArrayList;

import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_NOT_REQUIRED;

/**
 * Keeps track of requests for strong authentication.
 */
public class LockSettingsStrongAuth {

    private static final String TAG = "LockSettings";

    private static final int MSG_REQUIRE_STRONG_AUTH = 1;
    private static final int MSG_REGISTER_TRACKER = 2;
    private static final int MSG_UNREGISTER_TRACKER = 3;
    private static final int MSG_REMOVE_USER = 4;

    private final ArrayList<IStrongAuthTracker> mStrongAuthTrackers = new ArrayList<>();
    private final SparseIntArray mStrongAuthForUser = new SparseIntArray();

    private void handleAddStrongAuthTracker(IStrongAuthTracker tracker) {
        for (int i = 0; i < mStrongAuthTrackers.size(); i++) {
            if (mStrongAuthTrackers.get(i).asBinder() == tracker.asBinder()) {
                return;
            }
        }
        mStrongAuthTrackers.add(tracker);

        for (int i = 0; i < mStrongAuthForUser.size(); i++) {
            int key = mStrongAuthForUser.keyAt(i);
            int value = mStrongAuthForUser.valueAt(i);
            try {
                tracker.onStrongAuthRequiredChanged(value, key);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception while adding StrongAuthTracker.", e);
            }
        }
    }

    private void handleRemoveStrongAuthTracker(IStrongAuthTracker tracker) {
        for (int i = 0; i < mStrongAuthTrackers.size(); i++) {
            if (mStrongAuthTrackers.get(i).asBinder() == tracker.asBinder()) {
                mStrongAuthTrackers.remove(i);
                return;
            }
        }
    }

    private void handleRequireStrongAuth(int strongAuthReason, int userId) {
        if (userId == UserHandle.USER_ALL) {
            for (int i = 0; i < mStrongAuthForUser.size(); i++) {
                int key = mStrongAuthForUser.keyAt(i);
                handleRequireStrongAuthOneUser(strongAuthReason, key);
            }
        } else {
            handleRequireStrongAuthOneUser(strongAuthReason, userId);
        }
    }

    private void handleRequireStrongAuthOneUser(int strongAuthReason, int userId) {
        int oldValue = mStrongAuthForUser.get(userId, LockPatternUtils.StrongAuthTracker.DEFAULT);
        int newValue = strongAuthReason == STRONG_AUTH_NOT_REQUIRED
                ? STRONG_AUTH_NOT_REQUIRED
                : (oldValue | strongAuthReason);
        if (oldValue != newValue) {
            mStrongAuthForUser.put(userId, newValue);
            notifyStrongAuthTrackers(newValue, userId);
        }
    }

    private void handleRemoveUser(int userId) {
        int index = mStrongAuthForUser.indexOfKey(userId);
        if (index >= 0) {
            mStrongAuthForUser.removeAt(index);
            notifyStrongAuthTrackers(StrongAuthTracker.DEFAULT, userId);
        }
    }

    private void notifyStrongAuthTrackers(int strongAuthReason, int userId) {
        for (int i = 0; i < mStrongAuthTrackers.size(); i++) {
            try {
                mStrongAuthTrackers.get(i).onStrongAuthRequiredChanged(strongAuthReason, userId);
            } catch (DeadObjectException e) {
                Slog.d(TAG, "Removing dead StrongAuthTracker.");
                mStrongAuthTrackers.remove(i);
                i--;
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception while notifying StrongAuthTracker.", e);
            }
        }
    }

    public void registerStrongAuthTracker(IStrongAuthTracker tracker) {
        mHandler.obtainMessage(MSG_REGISTER_TRACKER, tracker).sendToTarget();
    }

    public void unregisterStrongAuthTracker(IStrongAuthTracker tracker) {
        mHandler.obtainMessage(MSG_UNREGISTER_TRACKER, tracker).sendToTarget();
    }

    public void removeUser(int userId) {
        mHandler.obtainMessage(MSG_REMOVE_USER, userId, 0).sendToTarget();
    }

    public void requireStrongAuth(int strongAuthReason, int userId) {
        if (userId == UserHandle.USER_ALL || userId >= UserHandle.USER_OWNER) {
            mHandler.obtainMessage(MSG_REQUIRE_STRONG_AUTH, strongAuthReason,
                    userId).sendToTarget();
        } else {
            throw new IllegalArgumentException(
                    "userId must be an explicit user id or USER_ALL");
        }
    }

    public void reportUnlock(int userId) {
        requireStrongAuth(STRONG_AUTH_NOT_REQUIRED, userId);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_TRACKER:
                    handleAddStrongAuthTracker((IStrongAuthTracker) msg.obj);
                    break;
                case MSG_UNREGISTER_TRACKER:
                    handleRemoveStrongAuthTracker((IStrongAuthTracker) msg.obj);
                    break;
                case MSG_REQUIRE_STRONG_AUTH:
                    handleRequireStrongAuth(msg.arg1, msg.arg2);
                    break;
                case MSG_REMOVE_USER:
                    handleRemoveUser(msg.arg1);
                    break;
            }
        }
    };
}
