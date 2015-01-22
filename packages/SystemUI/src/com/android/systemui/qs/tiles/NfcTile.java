package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import com.android.systemui.R;
import com.android.systemui.qs.QSTile;

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
        mHost.startSettingsActivity(new Intent("android.settings.NFC_SETTINGS"));
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

    private int getNfcState() {
        return mNfcAdapter.getAdapterState();
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.value = mNfcAdapter.isEnabled();
        state.iconId = state.value ? R.drawable.ic_qs_nfc_on : R.drawable.ic_qs_nfc_off;
        state.label = mContext.getString(state.value ? R.string.quick_settings_nfc
                : R.string.quick_settings_nfc_off);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        if (listening) {
            mContext.registerReceiver(mReceiver,
                    new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
        } else {
            mContext.unregisterReceiver(mReceiver);
        }
    }
}
