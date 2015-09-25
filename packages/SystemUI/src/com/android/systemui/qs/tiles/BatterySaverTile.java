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

import cyanogenmod.power.PerformanceManager;

import org.cyanogenmod.internal.logging.CMMetricsLogger;

/** Quick settings tile: Battery saver **/
public class BatterySaverTile extends QSTile<QSTile.BooleanState> {

    private static final Intent BATTERY_SETTINGS = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);

    private final PowerManager mPm;
    private final PerformanceManager mPerformanceManager;
    private boolean mListening;

    public BatterySaverTile(Host host) {
        super(host);
        mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mPerformanceManager = PerformanceManager.getInstance(mContext);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        mPm.setPowerSaveMode(!mState.value);
        refreshState();
    }

    @Override
    public void handleLongClick() {
        mHost.startActivityDismissingKeyguard(BATTERY_SETTINGS);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.value = mPm.isPowerSaveMode();
        state.visible = mPerformanceManager.getNumberOfProfiles() == 0;
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

    private ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            refreshState();
        }
    };

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;

        if (listening) {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.LOW_POWER_MODE),
                    false, mObserver);
        } else {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
        }
    }
}
