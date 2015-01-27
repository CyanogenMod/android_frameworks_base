/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.quicksettings;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public class LockscreenStateChanger {
    private static final String KEYGUARD_SERVICE_ACTION_STATE_CHANGE =
            "com.android.internal.action.KEYGUARD_SERVICE_STATE_CHANGED";
    private static final String KEYGUARD_SERVICE_EXTRA_ACTIVE = "active";

    private static final String KEY_DISABLED = "lockscreen_disabled";

    public interface LockStateChangeListener {
        public void onLockStateChange(boolean enabled);
    }

    private List<LockStateChangeListener> mListeners;
    private boolean mLockscreenDisabled;
    private static LockscreenStateChanger sInstance;
    private Context mContext;
    private KeyguardLock mLock;
    private boolean mKeyguardBound;
    private SharedPreferences mPrefs;

    public static synchronized LockscreenStateChanger getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LockscreenStateChanger(context);
        }
        return sInstance;
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBasedOnIntent(intent);
        }
    };

    private void updateBasedOnIntent(Intent intent) {
        mKeyguardBound = intent.getBooleanExtra(KEYGUARD_SERVICE_EXTRA_ACTIVE, false);
        updateForCurrentState();
    }

    private LockscreenStateChanger(Context context) {
        mListeners = Collections.synchronizedList(new ArrayList<LockStateChangeListener>());
        mContext = context.getApplicationContext();
        // Acquire lock
        KeyguardManager keyguardManager = (KeyguardManager)
                mContext.getApplicationContext().getSystemService(Context.KEYGUARD_SERVICE);
        mLock = keyguardManager.newKeyguardLock("LockscreenTile");
        // Fetch last state
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);
        mLockscreenDisabled = mPrefs.getBoolean(KEY_DISABLED, false);
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(KEYGUARD_SERVICE_ACTION_STATE_CHANGE);
        Intent i = mContext.registerReceiver(mReceiver, filter);
        if (i != null) {
            updateBasedOnIntent(i);
        }
    }

    public synchronized void addListener(LockStateChangeListener listener) {
        if (mListeners.isEmpty()) {
            registerReceiver();
            updateForCurrentState();
        }
        mListeners.add(listener);
    }

    public synchronized void removeListener(LockStateChangeListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            if (mLockscreenDisabled) {
                // No more tiles left, let's re-enable keyguard
                toggleState();
            }
            mContext.unregisterReceiver(mReceiver);
        }
    }

    public synchronized void toggleState() {
        mLockscreenDisabled = !mLockscreenDisabled;
        mPrefs.edit().putBoolean(KEY_DISABLED, mLockscreenDisabled).apply();

        updateForCurrentState();
    }

    private synchronized void updateForCurrentState() {
        if (!mKeyguardBound) {
            return;
        }

        if (mLockscreenDisabled) {
            mLock.disableKeyguard();
        } else {
            mLock.reenableKeyguard();
        }
        informListeners();
    }

    private void informListeners() {
        for (LockStateChangeListener listener : mListeners) {
            listener.onLockStateChange(mLockscreenDisabled);
        }
    }

    public synchronized boolean isDisabled() {
        return mLockscreenDisabled;
    }
}
