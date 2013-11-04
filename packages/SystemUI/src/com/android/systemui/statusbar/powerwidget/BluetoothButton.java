package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;

public class BluetoothButton extends PowerButton {

    private static final StateTracker sBluetoothState = new BluetoothStateTracker();

    private static final class BluetoothStateTracker extends StateTracker {

        @Override
        public int getActualState(Context context) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                return STATE_UNKNOWN; // On emulator?
            }
            return bluetoothStateToFiveState(mBluetoothAdapter
                    .getState());
        }

        @Override
        protected void requestStateChange(Context context,
                final boolean desiredState) {
            // Actually request the Bluetooth change and persistent
            // settings write off the UI thread, as it can take a
            // user-noticeable amount of time, especially if there's
            // disk contention.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... args) {
                    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if(mBluetoothAdapter.isEnabled()) {
                        mBluetoothAdapter.disable();
                    } else {
                        mBluetoothAdapter.enable();
                    }
                    return null;
                }
            }.execute();
        }

        @Override
        public void onActualStateChange(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent
                    .getAction())) {
                return;
            }
            int bluetoothState = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE, -1);
            setCurrentState(context, bluetoothStateToFiveState(bluetoothState));
        }

        /**
         * Converts BluetoothAdapter's state values into our
         * Wifi/Bluetooth-common state values.
         */
        private static int bluetoothStateToFiveState(int bluetoothState) {
            switch (bluetoothState) {
                case BluetoothAdapter.STATE_OFF:
                    return STATE_DISABLED;
                case BluetoothAdapter.STATE_ON:
                    return STATE_ENABLED;
                case BluetoothAdapter.STATE_TURNING_ON:
                    return STATE_TURNING_ON;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    return STATE_TURNING_OFF;
                default:
                    return STATE_UNKNOWN;
            }
        }
    }

    public BluetoothButton() { mType = BUTTON_BLUETOOTH; }

    @Override
    protected void updateState(Context context) {
        mState = sBluetoothState.getTriState(context);
        switch (mState) {
            case STATE_DISABLED:
                mIcon = R.drawable.stat_bluetooth_off;
                break;
            case STATE_ENABLED:
                mIcon = R.drawable.stat_bluetooth_on;
                break;
            case STATE_INTERMEDIATE:
                // In the transitional state, the bottom green bar
                // shows the tri-state (on, off, transitioning), but
                // the top dark-gray-or-bright-white logo shows the
                // user's intent. This is much easier to see in
                // sunlight.
                if (sBluetoothState.isTurningOn()) {
                    mIcon = R.drawable.stat_bluetooth_on;
                } else {
                    mIcon = R.drawable.stat_bluetooth_off;
                }
                break;
        }
    }

    @Override
    protected void toggleState(Context context) {
        sBluetoothState.toggleState(context);
    }

    @Override
    protected boolean handleLongClick(Context context) {
        Intent intent = new Intent("android.settings.BLUETOOTH_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        sBluetoothState.onActualStateChange(context, intent);
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return filter;
    }
}
