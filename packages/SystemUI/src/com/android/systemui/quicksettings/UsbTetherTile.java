package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class UsbTetherTile extends QuickSettingsTile {

    private boolean mUsbEnabled = false;
    private boolean mUsbConnected = false;
    private boolean mMassStorageActive = false;

    public UsbTetherTile(Context context, LayoutInflater inflater,
            QuickSettingsContainerView container, QuickSettingsController qsc) {
        super(context, inflater, container, qsc);

        updateTileState();
        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        };
        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                startSettingsActivity(intent);
                return true;
            }
        };
        qsc.registerAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED, this);
        qsc.registerAction(UsbManager.ACTION_USB_STATE, this);
        qsc.registerAction(Intent.ACTION_MEDIA_SHARED, this);
        qsc.registerAction(Intent.ACTION_MEDIA_UNSHARED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)){
        }

        if(intent.getAction().equals(UsbManager.ACTION_USB_STATE)){
            mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
        }

        if(intent.getAction().equals(Intent.ACTION_MEDIA_SHARED)) {
            mMassStorageActive = true;
        }

        if(intent.getAction().equals(Intent.ACTION_MEDIA_UNSHARED)) {
            mMassStorageActive = false;
        }

        updateTileState();
        updateQuickSettings();
    }

    private void updateTileState() {
        if(mUsbConnected){
            mDrawable = R.drawable.ic_qs_usb_tether_connected;
            mLabel = mContext.getString(R.string.quick_settings_usbtether_connected_label);
        }else{
            mDrawable = R.drawable.ic_qs_usb_tether_off;
            mLabel = mContext.getString(R.string.quick_settings_usbtether_off_label);
        }
        updateQuickSettings();
    }

}
