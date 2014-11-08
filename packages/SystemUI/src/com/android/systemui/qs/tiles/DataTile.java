/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Mobile data switch **/
public class DataTile extends QSTile<QSTile.BooleanState> {
    TelephonyManager mTelephonyManager;
    private DataObserver mDataObserver;
    private boolean mListening = false;

    public DataTile(Host host) {
        super(host);
        mTelephonyManager = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mDataObserver = new DataObserver(mHandler);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        if (dataSwitchEnabled()) {
            setEnabled(!mState.value);
        } else {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings$DataUsageSummaryActivity"));
            mHost.startSettingsActivity(intent);
        }
    }

    private void setEnabled(boolean enabled) {
        // Do not make mobile data on/off if airplane mode on or has no sim card
        if (Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0 || !hasIccCard()) {
            return;
        }
        int phoneCount = mTelephonyManager.getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.MOBILE_DATA + i, (enabled) ? 1 : 0);
            long[] subId = SubscriptionManager.getSubId(i);
            mTelephonyManager.setDataEnabledUsingSubId(subId[0], enabled);
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean dataOn = mTelephonyManager.getDataEnabled()
                && !isAirplaneModeOn() && hasIccCard();
        state.value = dataOn;
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_mobile_data_label);
        if (dataOn) {
            state.iconId = R.drawable.ic_qs_data_on;
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_data_on);
        } else {
            state.iconId = R.drawable.ic_qs_data_off;
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_data_off);
        }
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mDataObserver.startObserving();
        } else {
            mDataObserver.endObserving();
        }
    }

    /** ContentObserver to watch mobile data on/off **/
    private class DataObserver extends ContentObserver {
        public DataObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) Log.i(TAG, "data state change");
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.MOBILE_DATA),
                    false, this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                    false, this);
        }

        public void endObserving() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }
    }

    public boolean isAirplaneModeOn() {
        return (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) != 0);
    }

    public boolean hasIccCard() {
        if (mTelephonyManager.isMultiSimEnabled()) {
            int prfDataSlotId = SubscriptionManager.getSlotId(
                    SubscriptionManager.getDefaultDataSubId());
            int simState = mTelephonyManager.getSimState(prfDataSlotId);
            boolean active = (simState != TelephonyManager.SIM_STATE_ABSENT)
                    && (simState != TelephonyManager.SIM_STATE_UNKNOWN);
            return active && mTelephonyManager.hasIccCard(prfDataSlotId);
        } else {
            return mTelephonyManager.hasIccCard();
        }
    }

    public boolean dataSwitchEnabled() {
        return mContext.getResources().getBoolean(R.bool.config_enableDataSwitch);
    }
}
