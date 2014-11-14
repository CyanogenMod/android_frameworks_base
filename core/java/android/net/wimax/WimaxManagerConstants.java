/*
 * Copyright (C) 2014 The CyanogenMod Project
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
package android.net.wimax;

/**
 * {@hide}
 */
public class WimaxManagerConstants
{

    /**
     * Used by android.net.wimax.WimaxManager for handling management of
     * Wimax access.
     */
    public static final String WIMAX_SERVICE = "WiMax";

    /**
     * Broadcast intent action indicating that Wimax has been enabled, disabled,
     * enabling, disabling, or unknown. One extra provides this state as an int.
     * Another extra provides the previous state, if available.
     */
    public static final String NET_4G_STATE_CHANGED_ACTION =
        "android.net.fourG.NET_4G_STATE_CHANGED";

    /**
     * The lookup key for an int that indicates whether Wimax is enabled,
     * disabled, enabling, disabling, or unknown.
     */
    public static final String EXTRA_4G_STATE = "4g_state";

    /**
     * Broadcast intent action indicating that Wimax state has been changed
     * state could be scanning, connecting, connected, disconnecting, disconnected
     * initializing, initialized, unknown and ready. One extra provides this state as an int.
     * Another extra provides the previous state, if available.
     */
    public static final String  WIMAX_NETWORK_STATE_CHANGED_ACTION =
        "android.net.fourG.wimax.WIMAX_NETWORK_STATE_CHANGED";

    /**
     * Broadcast intent action indicating that Wimax signal level has been changed.
     * Level varies from 0 to 3.
     */
    public static final String SIGNAL_LEVEL_CHANGED_ACTION =
        "android.net.wimax.SIGNAL_LEVEL_CHANGED";

    /**
     * The lookup key for an int that indicates whether Wimax state is
     * scanning, connecting, connected, disconnecting, disconnected
     * initializing, initialized, unknown and ready.
     */
    public static final String EXTRA_WIMAX_STATE = "WimaxState";
    public static final String EXTRA_WIMAX_STATE_INT = "WimaxStateInt";
    /**
     * The lookup key for an int that indicates whether state of Wimax
     * is idle.
     */
    public static final String EXTRA_WIMAX_STATE_DETAIL = "WimaxStateDetail";

    /**
     * The lookup key for an int that indicates Wimax signal level.
     */
    public static final String EXTRA_NEW_SIGNAL_LEVEL = "newSignalLevel";

    /**
     * Indicatates Wimax is disabled.
     */
    public static final int NET_4G_STATE_DISABLED = 1;

    /**
     * Indicatates Wimax is disabling.
     */
    public static final int NET_4G_STATE_DISABLING = 0;

    /**
     * Indicatates Wimax is enabled.
     */
    public static final int NET_4G_STATE_ENABLED = 3;

    /**
     * Indicatates Wimax is enabling.
     */
    public static final int NET_4G_STATE_ENABLING = 2;

    /**
     * Indicatates Wimax status is known.
     */
    public static final int NET_4G_STATE_UNKNOWN = 4;

    /**
     * Indicatates Wimax is in idle state.
     */
    public static final int WIMAX_IDLE = 6;

    /**
     * Indicatates Wimax is being deregistered.
     */
    public static final int WIMAX_DEREGISTRATION = 8;

    /**
     * Indicatates wimax state is unknown.
     */
    public static final int WIMAX_STATE_UNKNOWN = 0;

    /**
     * Indicatates wimax state is connected.
     */
    public static final int WIMAX_STATE_CONNECTED = 7;

    /**
     * Indicatates wimax state is disconnected.
     */
    public static final int WIMAX_STATE_DISCONNECTED = 9;

    /**
     * Constants for HTC/SQN WiMAX implementation
     */
    public static final String WIMAX_ENABLED_CHANGED_ACTION = "com.htc.net.wimax.WIMAX_ENABLED_CHANGED";
    public static final String CURRENT_WIMAX_ENABLED_STATE  = "curWimaxEnabledState";
}
