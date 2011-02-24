package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;
import com.android.wimax.WimaxConstants;
import com.android.wimax.WimaxSettingsHelper;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;

public class WimaxButton extends PowerButton{

    private static final StateTracker sWimaxState = new WimaxStateTracker();

    /**
     * Subclass of StateTracker to get/set WiMAX state.
     */
    private static final class WimaxStateTracker extends StateTracker {
        @Override
        public int getActualState(Context context) {
            final WimaxSettingsHelper helper = new WimaxSettingsHelper(context);
            if (helper.isWimaxSupported()) {
                return wimaxStateToFiveState(helper.getWimaxState());
            }
            return STATE_UNKNOWN;
        }

        @Override
        protected void requestStateChange(Context context,
                final boolean desiredState) {
            final WimaxSettingsHelper helper = new WimaxSettingsHelper(context);
            if (!helper.isWimaxSupported()) {
                Log.e(TAG, "WiMAX is not supported");
                return;
            }

            // Actually request the wifi change and persistent
            // settings write off the UI thread, as it can take a
            // user-noticeable amount of time, especially if there's
            // disk contention.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... args) {
                    helper.setWimaxEnabled(desiredState);
                    return null;
                }
            }.execute();
        }

        @Override
        public void onActualStateChange(Context context, Intent intent) {
            if (!WimaxConstants.WIMAX_ENABLED_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }
            int wimaxState = intent.getIntExtra(WimaxConstants.CURRENT_WIMAX_ENABLED_STATE, WimaxConstants.WIMAX_ENABLED_STATE_UNKNOWN);
            int widgetState = wimaxStateToFiveState(wimaxState);
            setCurrentState(context, widgetState);
        }

        /**
         * Converts WimaxController's state values into our
         * WiMAX-common state values.
         */
        private static int wimaxStateToFiveState(int wimaxState) {
            switch (wimaxState) {
                case WimaxConstants.WIMAX_ENABLED_STATE_DISABLED:
                    return STATE_DISABLED;
                case WimaxConstants.WIMAX_ENABLED_STATE_ENABLED:
                    return STATE_ENABLED;
                case WimaxConstants.WIMAX_ENABLED_STATE_ENABLING:
                    return STATE_TURNING_ON;
                case WimaxConstants.WIMAX_ENABLED_STATE_DISABLING:
                    return STATE_TURNING_OFF;
                default:
                    return STATE_UNKNOWN;
            }
        }
    }

    public WimaxButton() { mType = BUTTON_WIMAX; }

    @Override
    protected void updateState() {
        mState = sWimaxState.getTriState(mView.getContext());
        switch (mState) {
            case STATE_DISABLED:
                mIcon = R.drawable.stat_wimax_off;
                break;
            case STATE_ENABLED:
                mIcon = R.drawable.stat_wimax_on;
                break;
            case STATE_INTERMEDIATE:
                // In the transitional state, the bottom green bar
                // shows the tri-state (on, off, transitioning), but
                // the top dark-gray-or-bright-white logo shows the
                // user's intent. This is much easier to see in
                // sunlight.
                if (sWimaxState.isTurningOn()) {
                    mIcon = R.drawable.stat_wimax_on;
                } else {
                    mIcon = R.drawable.stat_wimax_off;
                }
                break;
        }
    }

    @Override
    protected void toggleState() {
        sWimaxState.toggleState(mView.getContext());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        sWimaxState.onActualStateChange(context, intent);
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WimaxConstants.WIMAX_ENABLED_CHANGED_ACTION);
        return filter;
    }
}
