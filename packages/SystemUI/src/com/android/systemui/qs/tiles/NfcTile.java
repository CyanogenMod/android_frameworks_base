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

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import org.cyanogenmod.internal.logging.CMMetricsLogger;

public class NfcTile extends QSTile<QSTile.BooleanState> {
    private NfcAdapter mNfcAdapter;
    private boolean mListening;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshState();
        }
    };

    public NfcTile(Host host) {
        super(host);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(mContext);
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        toggleState();
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(new Intent("android.settings.NFC_SETTINGS"));
    }

    protected void toggleState() {
        int state = getNfcState();
        switch (state) {
            case NfcAdapter.STATE_TURNING_ON:
            case NfcAdapter.STATE_ON:
                mNfcAdapter.disable();
                break;
            case NfcAdapter.STATE_TURNING_OFF:
            case NfcAdapter.STATE_OFF:
                mNfcAdapter.enable();
                break;
        }
    }

    private boolean isEnabled() {
        int state = getNfcState();
        switch (state) {
            case NfcAdapter.STATE_TURNING_ON:
            case NfcAdapter.STATE_ON:
                return true;
            case NfcAdapter.STATE_TURNING_OFF:
            case NfcAdapter.STATE_OFF:
            default:
                return false;
        }
    }

    private int getNfcState() {
        return mNfcAdapter.getAdapterState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        if (mNfcAdapter == null) {
            mNfcAdapter = NfcAdapter.getDefaultAdapter(mContext);
        }
        state.visible = mNfcAdapter != null;
        state.value = mNfcAdapter != null && isEnabled();
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
            if (mNfcAdapter == null) {
                mNfcAdapter = NfcAdapter.getDefaultAdapter(mContext);
                refreshState();
            }
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }
}
