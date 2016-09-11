/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.policy.keyguard;

import android.app.ActivityManager;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.policy.IKeyguardService;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.widget.LockPatternUtils;

import java.io.PrintWriter;

/**
 * Maintains a cached copy of Keyguard's state.
 * @hide
 */
public class KeyguardStateMonitor extends IKeyguardStateCallback.Stub {
    private static final String TAG = "KeyguardStateMonitor";

    // These cache the current state of Keyguard to improve performance and avoid deadlock. After
    // Keyguard changes its state, it always triggers a layout in window manager. Because
    // IKeyguardStateCallback is synchronous and because these states are declared volatile, it's
    // guaranteed that window manager picks up the new state all the time in the layout caused by
    // the state change of Keyguard. To be extra safe, assume most restrictive values until Keyguard
    // tells us the actual value.
    private volatile boolean mIsShowing = true;
    private volatile boolean mSimSecure = true;
    private volatile boolean mInputRestricted = true;
    private volatile boolean mKeyguardPanelFocused = false;

    private int mCurrentUserId;

    private final LockPatternUtils mLockPatternUtils;

    public KeyguardStateMonitor(Context context, IKeyguardService service) {
        mLockPatternUtils = new LockPatternUtils(context);
        mCurrentUserId = ActivityManager.getCurrentUser();
        try {
            service.addStateMonitorCallback(this);
        } catch (RemoteException e) {
            Slog.w(TAG, "Remote Exception", e);
        }
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    public boolean isSecure() {
        return mLockPatternUtils.isSecure(getCurrentUser()) || mSimSecure;
    }

    public boolean isInputRestricted() {
        return mInputRestricted;
    }

    public boolean isKeyguardPanelFocused() {
        return mKeyguardPanelFocused;
    }

    @Override // Binder interface
    public void onShowingStateChanged(boolean showing) {
        mIsShowing = showing;
    }

    @Override // Binder interface
    public void onSimSecureStateChanged(boolean simSecure) {
        mSimSecure = simSecure;
    }

    @Override // Binder interface
    public void onKeyguardPanelFocusChanged(boolean focused) {
        mKeyguardPanelFocused = focused;
    }

    public synchronized void setCurrentUser(int userId) {
        mCurrentUserId = userId;
    }

    private synchronized int getCurrentUser() {
        return mCurrentUserId;
    }

    @Override // Binder interface
    public void onInputRestrictedStateChanged(boolean inputRestricted) {
        mInputRestricted = inputRestricted;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + TAG);
        prefix += "  ";
        pw.println(prefix + "mIsShowing=" + mIsShowing);
        pw.println(prefix + "mSimSecure=" + mSimSecure);
        pw.println(prefix + "mInputRestricted=" + mInputRestricted);
        pw.println(prefix + "mCurrentUserId=" + mCurrentUserId);
        pw.println(prefix + "mKeyguardPanelFocused=" + mKeyguardPanelFocused);
    }
}