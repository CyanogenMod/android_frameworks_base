package com.android.server.status.widget;

import com.android.internal.R;
import com.android.server.status.widget.PowerButton;
import com.android.server.status.widget.StateTracker;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.provider.Settings;

public class BluetoothButton extends PowerButton{

    Context mContext;

    private static final StateTracker sBluetoothState = new BluetoothStateTracker();

    static BluetoothButton ownButton = null;


    /**
     * Subclass of StateTracker to get/set Bluetooth state.
     */
    private static final class BluetoothStateTracker extends StateTracker {

        @Override
        public int getActualState(Context context) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                return PowerButton.STATE_UNKNOWN; // On emulator?
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
                return PowerButton.STATE_DISABLED;
            case BluetoothAdapter.STATE_ON:
                return PowerButton.STATE_ENABLED;
            case BluetoothAdapter.STATE_TURNING_ON:
                return PowerButton.STATE_TURNING_ON;
            case BluetoothAdapter.STATE_TURNING_OFF:
                return PowerButton.STATE_TURNING_OFF;
            default:
                return PowerButton.STATE_UNKNOWN;
            }
        }
    }



    public static BluetoothButton getInstance() {
        if (ownButton == null) ownButton = new BluetoothButton();

        return ownButton;
    }

    @Override
    void initButton(int position) {
    }

    @Override
    public void toggleState(Context context) {
        sBluetoothState.toggleState(context);
    }

    @Override
    public void updateState(Context context) {
	mContext = context;
	boolean useCustomExp = Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.NOTIF_EXPANDED_BAR_CUSTOM, 0) == 1;

        currentState = sBluetoothState.getTriState(context);
        switch (currentState) {
        case PowerButton.STATE_DISABLED:
	    if (useCustomExp) {
		currentIcon = R.drawable.stat_bluetooth_off_cust;
	    } else {
                currentIcon = R.drawable.stat_bluetooth_off;
	    }
            break;
        case PowerButton.STATE_ENABLED:
	    if (useCustomExp) {
		currentIcon = R.drawable.stat_bluetooth_on_cust;
	    } else {
                currentIcon = R.drawable.stat_bluetooth_on;
	    }
            break;
        case PowerButton.STATE_INTERMEDIATE:
            // In the transitional state, the bottom green bar
            // shows the tri-state (on, off, transitioning), but
            // the top dark-gray-or-bright-white logo shows the
            // user's intent. This is much easier to see in
            // sunlight.
            if (sBluetoothState.isTurningOn()) {
		if (useCustomExp) {
		    currentIcon = R.drawable.stat_bluetooth_on_cust;
		} else {
                    currentIcon = R.drawable.stat_bluetooth_on;
		}
            } else {
		if (useCustomExp) {
		    currentIcon = R.drawable.stat_bluetooth_off_cust;
		} else {
                    currentIcon = R.drawable.stat_bluetooth_off;
		}
            }
            break;
        }
    }

    public void onReceive(Context context, Intent intent) {
        sBluetoothState.onActualStateChange(context, intent);
    }

    public void toggleState(Context context, int newState) {
        int curState = sBluetoothState.getTriState(context);
        if (curState != PowerButton.STATE_INTERMEDIATE &&
                curState != newState) {
            toggleState(context);
        }
    }
}
