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
 * limitations under the License.
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;

import com.android.internal.util.cm.QSUtils;
import com.android.systemui.qs.QSTile;
import com.android.systemui.R;

/**
 * Lazy Lte Tile
 * Created by Adnan on 1/21/15.
 */
public class LteTile extends QSTile<QSTile.BooleanState> {
    public LteTile(Host host) {
        super(host);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleLongClick() {
        super.handleLongClick();
        mHost.startSettingsActivity(new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS));
    }

    @Override
    protected void handleClick() {
        toggleLteState();
        refreshState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        // Hide the tile if device doesn't support LTE
        // or it supports Dual Sim Dual Active.
        // TODO: Should be spawning off a tile per sim
        if (!QSUtils.deviceSupportsLte(mContext)
                || QSUtils.deviceSupportsDdsSupported(mContext)) {
            state.visible = false;
            return;
        }

        state.label = mContext.getString(R.string.quick_settings_lte_label);

        switch (getCurrentPreferredNetworkMode()) {
            case Phone.NT_MODE_GLOBAL:
            case Phone.NT_MODE_LTE_CDMA_AND_EVDO:
            case Phone.NT_MODE_LTE_GSM_WCDMA:
            case Phone.NT_MODE_LTE_ONLY:
            case Phone.NT_MODE_LTE_WCDMA:
            case Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA:
            case Phone.NT_MODE_TD_SCDMA_GSM_WCDMA_LTE:
            case Phone.NT_MODE_TD_SCDMA_WCDMA_LTE:
                state.visible = true;
                state.iconId = R.drawable.ic_qs_lte_on;
                break;
            default:
                state.visible = true;
                state.iconId = R.drawable.ic_qs_lte_off;
                break;
        }
    }

    private void toggleLteState() {
        TelephonyManager tm = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);
        tm.toggleLTE(true);
    }

    private int getCurrentPreferredNetworkMode() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE, -1);
    }

    @Override
    public void setListening(boolean listening) {

    }
}
