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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;

import android.util.Log;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import org.cyanogenmod.internal.logging.CMMetricsLogger;
import org.cyanogenmod.internal.util.QSUtils;

public class NfcTile extends QSTile<QSTile.BooleanState> {

    private boolean mListening;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };
    private final boolean mSupportsNfc;

    public NfcTile(Host host) {
        super(host);
        mSupportsNfc = QSUtils.deviceSupportsNfc(mContext);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        boolean newState = !getState().value;
        setState(newState);
        refreshState();
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(new Intent("android.settings.NFC_SETTINGS"));
    }

    private void setState(boolean on) {
        try {
            NfcAdapter nfcAdapter = NfcAdapter.getNfcAdapter(mContext);
            if (nfcAdapter == null) {
                Log.e(TAG, "tried to set NFC state, but no NFC adapter was found");
                return;
            }
            if (on) {
                nfcAdapter.enable();
            } else {
                nfcAdapter.disable();
            }
        } catch (UnsupportedOperationException e) {
            // ignore
        }
    }

    private int getNfcAdapterState() {
        try {
            NfcAdapter nfcAdapter = NfcAdapter.getNfcAdapter(mContext);
            if (nfcAdapter == null) {
                Log.e(TAG, "tried to get NFC state, but no NFC adapter was found");
                return NfcAdapter.STATE_OFF;
            }
            return nfcAdapter.getAdapterState();
        } catch (UnsupportedOperationException e) {
            // ignore
            return NfcAdapter.STATE_OFF;
        }
    }

    /**
     * Helper method to encapsulate intermediate states (turning off/on) to help determine whether
     * the adapter will be on or off.
     * @param nfcState The current NFC adapter state.
     * @return boolean representing what state the adapter is/will be in
     */
    private static boolean isEnabled(int nfcState) {
        switch (nfcState) {
            case NfcAdapter.STATE_TURNING_ON:
            case NfcAdapter.STATE_ON:
                return true;
            case NfcAdapter.STATE_TURNING_OFF:
            case NfcAdapter.STATE_OFF:
            default:
                return false;
        }
    }

    /**
     * Helper method to determine intermediate states
     * @param nfcState The current NFC adapter state.
     * @return boolean representing if the adapter is in an intermediate state
     */
    private static boolean isEnablingDisabling(int nfcState) {
        switch (nfcState) {
            case NfcAdapter.STATE_TURNING_OFF:
            case NfcAdapter.STATE_TURNING_ON:
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = mSupportsNfc;
        final int nfcState = getNfcAdapterState();
        state.value = mSupportsNfc && isEnabled(nfcState);
        state.enabled = mSupportsNfc && !isEnablingDisabling(nfcState);

        state.icon = ResourceIcon.get(state.value ?
                R.drawable.ic_qs_nfc_on : R.drawable.ic_qs_nfc_off);
        state.label = mContext.getString(R.string.quick_settings_nfc_label);
    }

    @Override
    public int getMetricsCategory() {
        return CMMetricsLogger.TILE_NFC;
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
            refreshState();
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }
}
