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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.database.ContentObserver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.R;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSTile;

/** Quick settings tile: Roaming switch **/
public class RoamingTile extends QSTile<QSTile.BooleanState> {
    private final RoamingObserver mRoamingObserver;
    private boolean mListening = false;
    private boolean mIsForeignState = false;
    private boolean roamingTileVisible = false;

    public RoamingTile(Host host) {
        super(host);
        mRoamingObserver = new RoamingObserver(mHandler);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void handleClick() {
        setEnabled(!mState.value);
    }

    private void setEnabled(boolean enabled) {
        setDataRoaming(enabled, PhoneConstants.PHONE_ID1);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final boolean roaming = getDataRoaming(PhoneConstants.PHONE_ID1);
        state.value = roaming;
        state.visible = roamingTileVisible;
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_roaming_label);
        if (roaming && !isAirplaneModeOn()) {
            state.iconId = R.drawable.ic_qs_roaming_on;
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_roaming_on);
        } else {
            state.iconId = R.drawable.ic_qs_roaming_off;
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_roaming_off);
        }
    }

    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mRoamingObserver.startObserving();
            final IntentFilter filter = new IntentFilter();
            filter.addAction(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
            mContext.registerReceiver(mReceiver, filter);
        } else {
            mRoamingObserver.endObserving();
            mContext.unregisterReceiver(mReceiver);
        }
    }

    // Get Data roaming flag, from DB, as per SUB.
    private boolean getDataRoaming(int phoneId) {
        String val = TelephonyManager.getDefault().isMultiSimEnabled()
                ? String.valueOf(phoneId) : "";
        boolean enabled = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING + val, 0) != 0;
        return enabled;
    }

    // Set Data roaming flag, in DB, as per SUB.
    private void setDataRoaming(boolean enabled, int phoneId) {
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            // as per SUB, set the individual flag
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING + phoneId, enabled ? 1 : 0);
            if (phoneId == SubscriptionManager.getPhoneId(
                    SubscriptionManager.getDefaultDataSubId())) {
                Settings.Global.putInt(mContext.getContentResolver(),
                        Settings.Global.DATA_ROAMING, enabled ? 1 : 0);
            }
        } else {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.DATA_ROAMING, enabled ? 1 : 0);
        }
    }

    private class RoamingObserver extends ContentObserver {
        public RoamingObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) Log.d(TAG, "roaming is changed.");
            refreshState();
        }

        public void startObserving() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.CONTENT_URI,
                    false, this);
            mContext.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.DATA_ROAMING),
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

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (TelephonyIntents.ACTION_SERVICE_STATE_CHANGED.equals(intent.getAction())) {
                int sub = intent.getIntExtra(
                        PhoneConstants.SUBSCRIPTION_KEY, PhoneConstants.PHONE_ID1);
                if (sub == PhoneConstants.PHONE_ID1) {
                    onRoamingVisibleChanged();
                }
            }
        }
    };

    public void onRoamingVisibleChanged() {
        final String CHINA_MCC = "460";
        final String MACAO_MCC = "455";

        String numeric = "";
        boolean isForeign;

        int mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        if (mPhoneCount > 1) {
            long[] subId = SubscriptionManager.getSubId(PhoneConstants.PHONE_ID1);
            if (subId != null && subId.length >= 1) {
                numeric = TelephonyManager.getDefault().getNetworkOperator(subId[0]);
            }
        } else {
            numeric = TelephonyManager.getDefault().getNetworkOperator();
        }

        // Return if invaild values
        if (!isValidNumeric(numeric)) {
            return;
        }

        if (numeric.startsWith(CHINA_MCC) || numeric.startsWith(MACAO_MCC)) {
            isForeign = false;
        } else {
            isForeign = true;
        }

        if (isForeign != mIsForeignState) {
            if (isForeign) {
                roamingTileVisible = true;
            } else {
                roamingTileVisible = false;
            }
            refreshState();
            mIsForeignState = isForeign;
        }
    }

    private boolean isValidNumeric(String numeric) {
        if (TextUtils.isEmpty(numeric)
                || numeric.equals("null") || numeric.equals("00000")) {
            return false;
        }
        return true;
    }
}
