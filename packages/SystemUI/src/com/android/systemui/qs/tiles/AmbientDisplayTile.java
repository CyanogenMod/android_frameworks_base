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

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.provider.Settings.Secure;

import com.android.systemui.qs.SecureSetting;
import com.android.systemui.qs.QSTile;
import com.android.systemui.R;
import org.cyanogenmod.internal.logging.CMMetricsLogger;

/** Quick settings tile: Ambient Display **/
public class AmbientDisplayTile extends QSTile<QSTile.BooleanState> {

    private static final Intent DISPLAY_SETTINGS = new Intent("android.settings.DISPLAY_SETTINGS");

    private final SecureSetting mSetting;

    public AmbientDisplayTile(Host host) {
        super(host);

        mSetting = new SecureSetting(mContext, mHandler, Secure.DOZE_ENABLED) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        setEnabled(!mState.value);
        refreshState();
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(DISPLAY_SETTINGS);
    }

    private void setEnabled(boolean enabled) {
        Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DOZE_ENABLED,
                enabled ? 1 : 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer)arg : mSetting.getValue();
        final boolean enable = value != 0;
        state.value = enable;
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_ambient_display_label);
        if (enable) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_ambientdisplay_on);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_ambient_display_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_ambientdisplay_off);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_ambient_display_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_AMBIENT_DISPLAY;
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_ambient_display_changed_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_ambient_display_changed_off);
        }
    }

    @Override
    public void setListening(boolean listening) {
        // Do nothing
    }
}