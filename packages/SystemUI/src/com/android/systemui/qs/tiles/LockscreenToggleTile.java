/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.systemui.qs.tiles;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.widget.Toast;
import com.android.systemui.R;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;

public class LockscreenToggleTile extends QSTile<QSTile.BooleanState>
        implements KeyguardMonitor.Callback {

    private static final String KEYGUARD_SERVICE_ACTION_STATE_CHANGE =
            "com.android.internal.action.KEYGUARD_SERVICE_STATE_CHANGED";
    private static final String KEYGUARD_SERVICE_EXTRA_ACTIVE = "active";

    private static final String KEY_ENABLED = "lockscreen_enabled";

    private static final Intent LOCK_SCREEN_SETTINGS =
            new Intent("android.settings.LOCK_SCREEN_SETTINGS");

    private KeyguardViewMediator mKeyguardViewMediator;
    private KeyguardMonitor mKeyguard;
    private boolean mPersistedState;
    private boolean mKeyguardBound;
    private SharedPreferences mPrefs;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mKeyguardBound = intent.getBooleanExtra(KEYGUARD_SERVICE_EXTRA_ACTIVE, false);
            applyLockscreenState();
            refreshState();
        }
    };

    public LockscreenToggleTile(Host host) {
        super(host);
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);

        mKeyguard = host.getKeyguardMonitor();
        mKeyguardViewMediator =
                ((SystemUIApplication) mContext.getApplicationContext()).getComponent(KeyguardViewMediator.class);
        mPersistedState = getPersistedState();

        IntentFilter filter = new IntentFilter(KEYGUARD_SERVICE_ACTION_STATE_CHANGE);
        mContext.registerReceiver(mReceiver, filter);
    }

    @Override
    public void setListening(boolean listening) {
        if (listening) {
            mKeyguard.addCallback(this);
        } else {
            mKeyguard.removeCallback(this);
        }
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        setPersistedState(!mPersistedState);
        applyLockscreenState();
        refreshState();
    }

    @Override
    protected void handleLongClick() {
        mHost.startSettingsActivity(LOCK_SCREEN_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean lockscreenEnforced = mKeyguardViewMediator.lockscreenEnforcedByDevicePolicy();
        final boolean lockscreenEnabled = lockscreenEnforced
                || mPersistedState
                || mKeyguardViewMediator.getKeyguardEnabledInternal();

        state.visible = mKeyguardBound
                && !(mKeyguard.isShowing() && mKeyguard.isSecure());
        state.label = mContext.getString(lockscreenEnforced
                ? R.string.quick_settings_lockscreen_label_enforced
                : R.string.quick_settings_lockscreen_label);
        state.iconId = lockscreenEnabled
                ? R.drawable.ic_qs_lock_screen_on
                : R.drawable.ic_qs_lock_screen_off;
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void onKeyguardChanged() {
        refreshState();
    }

    private void applyLockscreenState() {
        if (!mKeyguardBound) {
            // do nothing yet
            return;
        }

        mKeyguardViewMediator.setKeyguardEnabledInternal(mPersistedState);
    }

    private boolean getPersistedState() {
        return mPrefs.getBoolean(KEY_ENABLED, true);
    }

    private void setPersistedState(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        mPersistedState = enabled;
    }
}
