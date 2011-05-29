/*
 * Copyright (C) 2011 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wimax.WimaxHelper;
import android.net.wimax.WimaxManagerConstants;
import android.os.AsyncTask;
import android.util.Log;

public class WimaxButton extends PowerButton {

    private static final StateTracker sWimaxState = new WimaxStateTracker();

    /**
     * Subclass of StateTracker to get/set WiMAX state.
     */
    private static final class WimaxStateTracker extends StateTracker {
        @Override
        public int getActualState(Context context) {
            if (WimaxHelper.isWimaxSupported(context)) {
                return wimaxStateToFiveState(WimaxHelper.getWimaxState(context));
            }
            return STATE_UNKNOWN;
        }

        @Override
        protected void requestStateChange(final Context context,
                final boolean desiredState) {
            if (!WimaxHelper.isWimaxSupported(context)) {
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
                    WimaxHelper.setWimaxEnabled(context, desiredState);
                    return null;
                }
            }.execute();
        }

        @Override
        public void onActualStateChange(Context context, Intent intent) {
            if (!WimaxManagerConstants.WIMAX_ENABLED_CHANGED_ACTION.equals(intent.getAction())) {
                return;
            }
            int wimaxState = intent.getIntExtra(WimaxManagerConstants.CURRENT_WIMAX_ENABLED_STATE, WimaxManagerConstants.WIMAX_ENABLED_STATE_UNKNOWN);
            int widgetState = wimaxStateToFiveState(wimaxState);
            setCurrentState(context, widgetState);
        }

        /**
         * Converts WimaxController's state values into our
         * WiMAX-common state values.
         */
        private static int wimaxStateToFiveState(int wimaxState) {
            switch (wimaxState) {
                case WimaxManagerConstants.WIMAX_ENABLED_STATE_DISABLED:
                    return STATE_DISABLED;
                case WimaxManagerConstants.WIMAX_ENABLED_STATE_ENABLED:
                    return STATE_ENABLED;
                case WimaxManagerConstants.WIMAX_ENABLED_STATE_ENABLING:
                    return STATE_TURNING_ON;
                case WimaxManagerConstants.WIMAX_ENABLED_STATE_DISABLING:
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
    protected boolean handleLongClick() {
        Intent intent = new Intent("android.settings.WIMAX_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mView.getContext().startActivity(intent);
        return true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        sWimaxState.onActualStateChange(context, intent);
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WimaxManagerConstants.WIMAX_ENABLED_CHANGED_ACTION);
        return filter;
    }
}
