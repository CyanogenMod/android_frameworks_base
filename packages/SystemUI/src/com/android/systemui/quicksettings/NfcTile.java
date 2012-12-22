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

    private final NfcAdapter mNfcAdapter;

    public NfcTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container,
            QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(context);

        updateTileState();

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleState();
                applySyncChanges();
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
        applySyncChanges();
    }

    private void applySyncChanges() {
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
        return mNfcAdapter.getAdapterState();
    }
}
