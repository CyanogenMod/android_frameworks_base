/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.internal.telephony;

import com.android.internal.util.Protocol;

/**
 * @hide
 */
public class DctConstants {
    /**
     * IDLE: ready to start data connection setup, default state
     * CONNECTING: state of issued startPppd() but not finish yet
     * SCANNING: data connection fails with one apn but other apns are available
     *           ready to start data connection on other apns (before INITING)
     * CONNECTED: IP connection is setup
     * DISCONNECTING: Connection.disconnect() has been called, but PDP
     *                context is not yet deactivated
     * FAILED: data connection fail for all apns settings
     * RETRYING: data connection failed but we're going to retry.
     *
     * getDataConnectionState() maps State to DataState
     *      FAILED or IDLE : DISCONNECTED
     *      RETRYING or CONNECTING or SCANNING: CONNECTING
     *      CONNECTED : CONNECTED or DISCONNECTING
     */
    public enum State {
        IDLE,
        CONNECTING,
        SCANNING,
        CONNECTED,
        DISCONNECTING,
        FAILED,
        RETRYING
    }

    public enum Activity {
        NONE,
        DATAIN,
        DATAOUT,
        DATAINANDOUT,
        DORMANT
    }

    /***** Event Codes *****/
    public static final int BASE = Protocol.BASE_DATA_CONNECTION_TRACKER;
    public static final int EVENT_DATA_SETUP_COMPLETE = BASE + 0;
    public static final int EVENT_RADIO_AVAILABLE = BASE + 1;
    public static final int EVENT_RECORDS_LOADED = BASE + 2;
    public static final int EVENT_TRY_SETUP_DATA = BASE + 3;
    public static final int EVENT_DATA_STATE_CHANGED = BASE + 4;
    public static final int EVENT_POLL_PDP = BASE + 5;
    public static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = BASE + 6;
    public static final int EVENT_VOICE_CALL_STARTED = BASE + 7;
    public static final int EVENT_VOICE_CALL_ENDED = BASE + 8;
    public static final int EVENT_DATA_CONNECTION_DETACHED = BASE + 9;
    public static final int EVENT_LINK_STATE_CHANGED = BASE + 10;
    public static final int EVENT_ROAMING_ON = BASE + 11;
    public static final int EVENT_ROAMING_OFF = BASE + 12;
    public static final int EVENT_ENABLE_NEW_APN = BASE + 13;
    public static final int EVENT_RESTORE_DEFAULT_APN = BASE + 14;
    public static final int EVENT_DISCONNECT_DONE = BASE + 15;
    public static final int EVENT_DATA_CONNECTION_ATTACHED = BASE + 16;
    public static final int EVENT_DATA_STALL_ALARM = BASE + 17;
    public static final int EVENT_DO_RECOVERY = BASE + 18;
    public static final int EVENT_APN_CHANGED = BASE + 19;
    public static final int EVENT_CDMA_DATA_DETACHED = BASE + 20;
    public static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = BASE + 21;
    public static final int EVENT_PS_RESTRICT_ENABLED = BASE + 22;
    public static final int EVENT_PS_RESTRICT_DISABLED = BASE + 23;
    public static final int EVENT_CLEAN_UP_CONNECTION = BASE + 24;
    public static final int EVENT_CDMA_OTA_PROVISION = BASE + 25;
    public static final int EVENT_RESTART_RADIO = BASE + 26;
    public static final int EVENT_SET_INTERNAL_DATA_ENABLE = BASE + 27;
    public static final int EVENT_RESET_DONE = BASE + 28;
    public static final int EVENT_CLEAN_UP_ALL_CONNECTIONS = BASE + 29;
    public static final int CMD_SET_USER_DATA_ENABLE = BASE + 30;
    public static final int CMD_SET_DEPENDENCY_MET = BASE + 31;
    public static final int CMD_SET_POLICY_DATA_ENABLE = BASE + 32;
    public static final int EVENT_ICC_CHANGED = BASE + 33;
    public static final int EVENT_DISCONNECT_DC_RETRYING = BASE + 34;
    public static final int EVENT_DATA_SETUP_COMPLETE_ERROR = BASE + 35;
    public static final int CMD_SET_ENABLE_FAIL_FAST_MOBILE_DATA = BASE + 36;
    public static final int CMD_ENABLE_MOBILE_PROVISIONING = BASE + 37;
    public static final int CMD_IS_PROVISIONING_APN = BASE + 38;
    public static final int EVENT_PROVISIONING_APN_ALARM = BASE + 39;
    public static final int CMD_NET_STAT_POLL = BASE + 40;
    public static final int EVENT_DATA_RAT_CHANGED = BASE + 41;
    public static final int CMD_CLEAR_PROVISIONING_SPINNER = BASE + 42;

    /***** Constants *****/

    public static final int APN_INVALID_ID = -1;
    public static final int APN_DEFAULT_ID = 0;
    public static final int APN_MMS_ID = 1;
    public static final int APN_SUPL_ID = 2;
    public static final int APN_DUN_ID = 3;
    public static final int APN_HIPRI_ID = 4;
    public static final int APN_IMS_ID = 5;
    public static final int APN_FOTA_ID = 6;
    public static final int APN_CBS_ID = 7;
    public static final int APN_IA_ID = 8;
    public static final int APN_EMERGENCY_ID = 9;
    // MTK
    public static final int APN_DM_ID = 10;
    public static final int APN_WAP_ID = 11;
    public static final int APN_NET_ID = 12;
    public static final int APN_CMMAIL_ID = 13;
    public static final int APN_RCSE_ID = 14;
    public static final int APN_XCAP_ID = 15;
    public static final int APN_RCS_ID = 16;
    public static final int APN_NUM_TYPES = APN_RCS_ID + 1;

    public static final int INVALID = -1;
    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    public static final String APN_TYPE_KEY = "apnType";
    public static final String PROVISIONING_URL_KEY = "provisioningUrl";

    // MTK

    /** M: start */
    public static final int MTK_BASE = BASE + 500;
    public static final int EVENT_DATA_ROAMING_CHANGED = MTK_BASE + 1;
    public static final int EVENT_INIT_EMERGENCY_APN_SETTINGS = MTK_BASE + 2;
    public static final int EVENT_APN_CHANGED_DONE = MTK_BASE + 3;
    public static final int EVENT_SETUP_DATA_WHEN_LOADED = MTK_BASE + 4;
    public static final int EVENT_VOICE_CALL_STARTED_PEER = MTK_BASE + 5;
    public static final int EVENT_VOICE_CALL_ENDED_PEER = MTK_BASE + 6;
    public static final int EVENT_FDN_CHANGED = MTK_BASE + 7;

    // M: CC33 LTE.
    public static final int EVENT_REMOVE_RESTRICT_EUTRAN = MTK_BASE + 25;
    public static final int EVENT_RESET_PDP_DONE = MTK_BASE + 26;
    // M: Reset Attach Apn
    public static final int EVENT_RESET_ATTACH_APN = MTK_BASE + 27;
    /* M: end */
    // M: Send this event when DcTracker is creating, and all phones has been created
    //    when handle this event. So we can do registration which need peer phone.
    public static final int EVENT_POST_CREATE_PHONE = MTK_BASE + 28;
    // M: [LTE][Low Power][UL traffic shaping] Start
    public static final int EVENT_DEFAULT_APN_REFERENCE_COUNT_CHANGED = MTK_BASE + 29;
    public static final int EVENT_LTE_ACCESS_STRATUM_STATE = MTK_BASE +30;
    // M: [LTE][Low Power][UL traffic shaping] End
}
