/*
 * Copyright (C) 2015-2016 The CyanogenMod Project
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

import android.content.Intent;
import android.os.UserHandle;
import com.android.systemui.R;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import cyanogenmod.providers.CMSettings;
import org.cyanogenmod.internal.logging.CMMetricsLogger;

public class LockscreenToggleTile extends QSTile<QSTile.BooleanState>
        implements KeyguardMonitor.Callback {

    private static final Intent LOCK_SCREEN_SETTINGS =
            new Intent("android.settings.LOCK_SCREEN_SETTINGS");

    private KeyguardViewMediator mKeyguardViewMediator;
    private KeyguardMonitor mKeyguard;
    private boolean mListening;

    private KeyguardViewMediator.LockscreenEnabledSettingsObserver mSettingsObserver;

    public LockscreenToggleTile(Host host) {
        super(host);

        mKeyguard = host.getKeyguardMonitor();
        mKeyguardViewMediator =
                ((SystemUIApplication)
                        mContext.getApplicationContext()).getComponent(KeyguardViewMediator.class);

        mSettingsObserver = new KeyguardViewMediator.LockscreenEnabledSettingsObserver(mContext,
                mUiHandler) {

            @Override
            public void update() {
                boolean newState = CMSettings.Secure.getIntForUser(
                        mContext.getContentResolver(),
                        CMSettings.Secure.LOCKSCREEN_INTERNALLY_ENABLED,
                        getPersistedDefaultOldSetting() ? 1 : 0,
                        UserHandle.USER_CURRENT) != 0;
                refreshState(newState);
            }
        };

    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) {
            return;
        }
        mListening = listening;
        if (listening) {
            mSettingsObserver.observe();
            mKeyguard.addCallback(this);
            refreshState();
        } else {
            mSettingsObserver.unobserve();
            mKeyguard.removeCallback(this);
        }
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        final boolean newState = !getState().value;
        setPersistedState(newState);
        refreshState(newState);
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(LOCK_SCREEN_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean lockscreenEnforced = mKeyguardViewMediator.lockscreenEnforcedByDevicePolicy();
        final boolean lockscreenEnabled = lockscreenEnforced ||
                arg != null ? (Boolean) arg : mKeyguardViewMediator.getKeyguardEnabledInternal();

        state.value = lockscreenEnabled;
        state.visible = mKeyguardViewMediator.isKeyguardBound();
        state.enabled = !mKeyguard.isShowing() || !mKeyguard.isSecure();
        state.label = mContext.getString(lockscreenEnforced
                ? R.string.quick_settings_lockscreen_label_enforced
                : R.string.quick_settings_lockscreen_label);
        if (lockscreenEnabled) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_lock_screen_on);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_lock_screen_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_lock_screen_off);
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_lock_screen_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_LOCKSCREEN_TOGGLE;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_lock_screen_changed_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_lock_screen_changed_off);
        }
    }

    @Override
    public void onKeyguardChanged() {
        refreshState();
    }

    private void setPersistedState(boolean enabled) {
        CMSettings.Secure.putIntForUser(mContext.getContentResolver(),
                CMSettings.Secure.LOCKSCREEN_INTERNALLY_ENABLED,
                enabled ? 1 : 0, UserHandle.USER_CURRENT);
    }
}
