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

import android.nfc.NfcManager;
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
            boolean newState = getInternalState(intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE,
                    NfcAdapter.STATE_OFF));
            refreshState(newState);
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
        refreshState(newState);
    }

    @Override
    protected void handleLongClick() {
        mHost.startActivityDismissingKeyguard(new Intent("android.settings.NFC_SETTINGS"));
    }

    private boolean getInternalState(int nfcState) {
        switch (nfcState) {
            case NfcAdapter.STATE_TURNING_ON:
            case NfcAdapter.STATE_ON:
                return true;
            default:
            case NfcAdapter.STATE_TURNING_OFF:
            case NfcAdapter.STATE_OFF:
                return false;
        }
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

    private boolean isEnabled() {
        try {
            NfcAdapter nfcAdapter = NfcAdapter.getNfcAdapter(mContext);
            if (nfcAdapter == null) {
                Log.e(TAG, "tried to get NFC state, but no NFC adapter was found");
                return false;
            }
            int state = nfcAdapter.getAdapterState();
            switch (state) {
                case NfcAdapter.STATE_TURNING_ON:
                case NfcAdapter.STATE_ON:
                    return true;
                case NfcAdapter.STATE_TURNING_OFF:
                case NfcAdapter.STATE_OFF:
                default:
                    return false;
            }
        } catch (UnsupportedOperationException e) {
            // ignore
            return false;
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = mSupportsNfc;

        if (arg instanceof Boolean) {
            state.value = (boolean) arg;
        } else {
            state.value = mSupportsNfc && isEnabled();
        }

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
