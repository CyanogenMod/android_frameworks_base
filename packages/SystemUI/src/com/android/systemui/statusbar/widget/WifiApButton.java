
package com.android.systemui.statusbar.widget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

public class WifiApButton extends PowerButton {

    static WifiApButton ownButton = null;

    private static final StateTracker sWifiApState = new WifiApStateTracker();

    /**
     * Subclass of StateTracker to get/set Wifi AP state.
     */
    private static final class WifiApStateTracker extends StateTracker {
        @Override
        public int getActualState(Context context) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                return wifiApStateToFiveState(wifiManager.getWifiApState());
            }
            return PowerButton.STATE_UNKNOWN;
        }

        @Override
        protected void requestStateChange(Context context, final boolean desiredState) {

            final WifiManager wifiManager = (WifiManager) context
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                Log.d("WifiAPManager", "No wifiManager.");
                return;
            }
            Log.i("WifiAp", "Setting: " + desiredState);

            // Actually request the Wi-Fi AP change and persistent
            // settings write off the UI thread, as it can take a
            // user-noticeable amount of time, especially if there's
            // disk contention.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... args) {
                    /**
                     * Disable Wif if enabling tethering
                     */
                    int wifiState = wifiManager.getWifiState();
                    if (desiredState
                            && ((wifiState == WifiManager.WIFI_STATE_ENABLING) || (wifiState == WifiManager.WIFI_STATE_ENABLED))) {
                        wifiManager.setWifiEnabled(false);
                    }

                    wifiManager.setWifiApEnabled(null, desiredState);
                    Log.i("WifiAp", "Async Setting: " + desiredState);
                    return null;
                }
            }.execute();
        }

        @Override
        public void onActualStateChange(Context context, Intent intent) {

            if (!WifiManager.WIFI_AP_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }
            int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_AP_STATE, -1);
            int widgetState = wifiApStateToFiveState(wifiState);
            setCurrentState(context, widgetState);
        }

        /**
         * Converts WifiManager's state values into our
         * Wifi/WifiAP/Bluetooth-common state values.
         */
        private static int wifiApStateToFiveState(int wifiState) {
            switch (wifiState) {
                case WifiManager.WIFI_AP_STATE_DISABLED:
                    return PowerButton.STATE_DISABLED;
                case WifiManager.WIFI_AP_STATE_ENABLED:
                    return PowerButton.STATE_ENABLED;
                case WifiManager.WIFI_AP_STATE_DISABLING:
                    return PowerButton.STATE_TURNING_OFF;
                case WifiManager.WIFI_AP_STATE_ENABLING:
                    return PowerButton.STATE_TURNING_ON;
                default:
                    return PowerButton.STATE_UNKNOWN;
            }
        }
    }

    public void updateState(Context context) {

        currentState = sWifiApState.getTriState(context);
        switch (currentState) {
            case PowerButton.STATE_DISABLED:
                currentIcon = R.drawable.stat_wifi_ap_off;
                break;
            case PowerButton.STATE_ENABLED:
                currentIcon = R.drawable.stat_wifi_ap_on;
                break;
            case PowerButton.STATE_INTERMEDIATE:
                // In the transitional state, the bottom green bar
                // shows the tri-state (on, off, transitioning), but
                // the top dark-gray-or-bright-white logo shows the
                // user's intent. This is much easier to see in
                // sunlight.
                if (sWifiApState.isTurningOn()) {
                    currentIcon = R.drawable.stat_wifi_ap_on;
                } else {
                    currentIcon = R.drawable.stat_wifi_ap_off;
                }
                break;
        }
    }

    public void onReceive(Context context, Intent intent) {
        sWifiApState.onActualStateChange(context, intent);
    }

    public void toggleState(Context context) {
        sWifiApState.toggleState(context);
    }

    public static WifiApButton getInstance() {
        if (ownButton == null) {
            ownButton = new WifiApButton();
        }

        return ownButton;
    }
}
