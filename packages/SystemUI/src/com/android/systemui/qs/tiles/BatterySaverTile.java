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
import android.database.ContentObserver;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import com.android.systemui.qs.QSTile;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryStateRegistar;

import cyanogenmod.power.PerformanceManager;

import org.cyanogenmod.internal.logging.CMMetricsLogger;

/** Quick settings tile: Battery saver **/
public class BatterySaverTile extends QSTile<QSTile.BooleanState> {

    private static final Intent BATTERY_SETTINGS = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);

    private final PowerManager mPm;
    private final boolean mHasPowerProfiles;

    private boolean mListening;
    private boolean mPluggedIn;

    public BatterySaverTile(Host host) {
        super(host);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mHasPowerProfiles = PerformanceManager.getInstance(mContext).getNumberOfProfiles() > 0;
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        mPm.setPowerSaveMode(!mState.value);
        refreshState(!mState.value);
    }

    @Override
    public void handleLongClick() {
        mHost.startActivityDismissingKeyguard(BATTERY_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = arg instanceof Boolean ? (boolean) arg : mPm.isPowerSaveMode();
        state.visible =  !mHasPowerProfiles;
        state.label = mContext.getString(R.string.quick_settings_battery_saver_label);
        if (state.value) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_battery_saver_on);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_battery_saver_on);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_battery_saver_off);
            state.contentDescription =  mContext.getString(
                    R.string.accessibility_quick_settings_battery_saver_off);
        }

        state.enabled = !mPluggedIn;
        if (mPluggedIn) {
            state.label = mContext.getString(R.string.quick_settings_battery_saver_label_charging);
        }
    }

    @Override
    protected String composeChangeAnnouncement() {
        if (mState.value) {
            return mContext.getString(
                    R.string.accessibility_quick_settings_battery_saver_changed_on);
        } else {
            return mContext.getString(
                    R.string.accessibility_quick_settings_battery_saver_changed_off);
        }
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_BATTERY_SAVER;
    }

    private BatteryStateRegistar.BatteryStateChangeCallback mBatteryState
            = new BatteryStateRegistar.BatteryStateChangeCallback() {
        @Override
        public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn,
                boolean charging) {
            mPluggedIn = pluggedIn || charging;
            refreshState();
        }

        @Override
        public void onPowerSaveChanged() {
            refreshState();
        }

        @Override
        public void onBatteryStyleChanged(int style, int percentMode) {
            // ignore
        }
    };

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;

        if (listening) {
            getHost().getBatteryController().addStateChangedCallback(mBatteryState);
        } else {
            getHost().getBatteryController().removeStateChangedCallback(mBatteryState);
        }
    }
}
