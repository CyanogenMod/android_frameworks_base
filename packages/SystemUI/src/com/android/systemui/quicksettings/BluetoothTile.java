package com.android.systemui.quicksettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BluetoothController;

public class BluetoothTile extends QuickSettingsTile implements BluetoothStateChangeCallback{

    private boolean enabled = false;
    private boolean connected = false;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothController mController;

    public BluetoothTile(Context context, QuickSettingsController qsc, BluetoothController controller) {
        super(context, qsc);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mController = controller;
        enabled = mBluetoothAdapter.isEnabled();
        connected = mBluetoothAdapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED;

        mOnClick = new OnClickListener() {

            @Override
            public void onClick(View v) {
                if(enabled){
                    mBluetoothAdapter.disable();
                }else{
                    mBluetoothAdapter.enable();
                }
                if (isFlipTilesEnabled()) {
                    flipTile(0);
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                return true;
            }
        };
        qsc.registerAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED, this);
        qsc.registerAction(BluetoothAdapter.ACTION_STATE_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean update = false;
        if(intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)){
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
            enabled = (state == BluetoothAdapter.STATE_ON);
            update = true;
        }

        if(intent.getAction().equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)){
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                    BluetoothAdapter.STATE_DISCONNECTED);
            connected = (state == BluetoothAdapter.STATE_CONNECTED);
            update = true;
        }

        if (update) {
            updateResources();
        }
    }

    void checkBluetoothState() {
        enabled = mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON;
        connected = mBluetoothAdapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED;
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if(enabled){
            if(connected){
                mDrawable = R.drawable.ic_qs_bluetooth_on;
            }else{
                mDrawable = R.drawable.ic_qs_bluetooth_not_connected;
            }
            mLabel = mContext.getString(R.string.quick_settings_bluetooth_label);
        }else{
            mDrawable = R.drawable.ic_qs_bluetooth_off;
            mLabel = mContext.getString(R.string.quick_settings_bluetooth_off_label);
        }
    }

    @Override
    void onPostCreate() {
        checkBluetoothState();
        updateTile();
        mController.addStateChangedCallback(this);
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeStateChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void onBluetoothStateChange(boolean on) {
        this.enabled = on;
        updateResources();
    }

}
