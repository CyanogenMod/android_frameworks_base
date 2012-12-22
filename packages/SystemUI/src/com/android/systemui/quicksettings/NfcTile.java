package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;


public class NfcTile extends QuickSettingsTile {

    private NfcAdapter mNfcAdapter;
    private static final int NFC_STATE_UNKNOWN = -100;

    public NfcTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateTileState();

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                applyNfcChanges();
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent("android.settings.NFC_SETTINGS");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        applyNfcChanges();
    }

    private void applyNfcChanges() {
        updateTileState();
        updateQuickSettings();
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

    private void updateTileState() {
        // Get the initial label
        mLabel = mContext.getString(R.string.quick_settings_nfc);

        int state = getNfcState();
        switch (state) {
            case NfcAdapter.STATE_TURNING_ON:
            case NfcAdapter.STATE_ON:
                mDrawable = R.drawable.ic_qs_nfc_on;
                break;
            case NfcAdapter.STATE_TURNING_OFF:
            case NfcAdapter.STATE_OFF:
            default:
                mDrawable = R.drawable.ic_qs_nfc_off;
                mLabel += " " + mContext.getString(R.string.quick_settings_label_disabled);
                break;
        }
    }

    private int getNfcState() {
        if (mNfcAdapter != null) {
            return mNfcAdapter.getAdapterState();
        } else {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(mContext);
            if (nfcAdapter != null) {
                mNfcAdapter = nfcAdapter;
                return nfcAdapter.getAdapterState();
            }
        }
        return NFC_STATE_UNKNOWN;
    }
}
