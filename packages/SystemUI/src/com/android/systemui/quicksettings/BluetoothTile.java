package com.android.systemui.quicksettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BluetoothController;

import java.util.HashMap;
import java.util.Map;

public class BluetoothTile extends QuickSettingsTile implements BluetoothStateChangeCallback{

    private boolean mEnabled = false;
    private boolean mConnected = false;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothController mController;
    private Map<String, String> mConnectedDevices;

    public BluetoothTile(Context context, QuickSettingsController qsc, BluetoothController controller) {
        super(context, qsc);
        mConnectedDevices = new HashMap<String, String>();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mController = controller;
        checkBluetoothState();

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mEnabled){
                    mBluetoothAdapter.disable();
                }else{
                    mBluetoothAdapter.enable();
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
        qsc.registerAction(BluetoothAdapter.ACTION_STATE_CHANGED, this);
        qsc.registerAction(BluetoothDevice.ACTION_ACL_CONNECTED, this);
        qsc.registerAction(BluetoothDevice.ACTION_ACL_DISCONNECTED, this);
        qsc.registerAction(BluetoothDevice.ACTION_ALIAS_CHANGED, this);
        qsc.registerAction(BluetoothDevice.ACTION_NAME_CHANGED, this);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final Bundle extras = intent.getExtras();

        if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                || BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            checkBluetoothState();
            if (mEnabled) {
                if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                    String address = device.getAddress();
                    String name = device.getAlias() == null ? device.getName() : device.getAlias();
                    mConnectedDevices.put(address, name);
                } else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
                    String address = device.getAddress();
                    if (mConnectedDevices.containsKey(address)) {
                        mConnectedDevices.remove(address);
                    }
                }
            } else {
                mConnectedDevices.clear();
            }
            updateResources();
        } else if (BluetoothDevice.ACTION_ALIAS_CHANGED.equals(action) ||
                BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
            BluetoothDevice device = extras.getParcelable(BluetoothDevice.EXTRA_DEVICE);
            String address = device.getAddress();
            String name = device.getAlias() == null ? device.getName() : device.getAlias();
            if (mConnectedDevices.containsKey(address)) {
                mConnectedDevices.put(address, name);
                updateResources();
            }
        }
    }

    void checkBluetoothState() {
        mEnabled = mBluetoothAdapter.isEnabled() &&
                mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON;
        mConnected = mEnabled &&
                mBluetoothAdapter.getConnectionState() == BluetoothAdapter.STATE_CONNECTED;
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        if(mEnabled){
            if(mConnected){
                mDrawable = R.drawable.ic_qs_bluetooth_on;
                if (mConnectedDevices.size() == 0) {
                    mLabel = mContext.getString(R.string.quick_settings_bluetooth_label);
                } else if (mConnectedDevices.size() == 1) {
                    mLabel = mConnectedDevices.values().iterator().next();
                } else {
                    mLabel = mContext.getString(R.string.quick_settings_bluetooth_multi_label,
                            mConnectedDevices.size());
                }
            }else{
                mDrawable = R.drawable.ic_qs_bluetooth_not_connected;
                mLabel = mContext.getString(R.string.quick_settings_bluetooth_label);
            }
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
        checkBluetoothState();
        updateResources();
    }

}
