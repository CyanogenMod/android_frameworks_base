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

/**
 * @hide
 */
public class PhoneConstants {

    /**
     * The phone state. One of the following:<p>
     * <ul>
     * <li>IDLE = no phone activity</li>
     * <li>RINGING = a phone call is ringing or call waiting.
     *  In the latter case, another call is active as well</li>
     * <li>OFFHOOK = The phone is off hook. At least one call
     * exists that is dialing, active or holding and no calls are
     * ringing or waiting.</li>
     * </ul>
     */
    public enum State {
        IDLE, RINGING, OFFHOOK;
    };

   /**
     * The state of a data connection.
     * <ul>
     * <li>CONNECTED = IP traffic should be available</li>
     * <li>CONNECTING = Currently setting up data connection</li>
     * <li>DISCONNECTED = IP not available</li>
     * <li>SUSPENDED = connection is created but IP traffic is
     *                 temperately not available. i.e. voice call is in place
     *                 in 2G network</li>
     * </ul>
     */
    public enum DataState {
        CONNECTED, CONNECTING, DISCONNECTED, SUSPENDED;
    };

    public static final String STATE_KEY = "state";

    // Radio Type
    public static final int PHONE_TYPE_NONE = RILConstants.NO_PHONE;
    public static final int PHONE_TYPE_GSM = RILConstants.GSM_PHONE;
    public static final int PHONE_TYPE_CDMA = RILConstants.CDMA_PHONE;
    public static final int PHONE_TYPE_SIP = RILConstants.SIP_PHONE;

    // Modes for LTE_ON_CDMA
    public static final int LTE_ON_CDMA_UNKNOWN = RILConstants.LTE_ON_CDMA_UNKNOWN;
    public static final int LTE_ON_CDMA_FALSE = RILConstants.LTE_ON_CDMA_FALSE;
    public static final int LTE_ON_CDMA_TRUE = RILConstants.LTE_ON_CDMA_TRUE;

    // Used for preferred network type
    // Note NT_* substitute RILConstants.NETWORK_MODE_* above the Phone
    public static final int NT_MODE_WCDMA_PREF   = RILConstants.NETWORK_MODE_WCDMA_PREF;
    public static final int NT_MODE_GSM_ONLY     = RILConstants.NETWORK_MODE_GSM_ONLY;
    public static final int NT_MODE_WCDMA_ONLY   = RILConstants.NETWORK_MODE_WCDMA_ONLY;
    public static final int NT_MODE_GSM_UMTS     = RILConstants.NETWORK_MODE_GSM_UMTS;
    public static final int NT_MODE_LTE_GSM_WCDMA= RILConstants.NETWORK_MODE_LTE_GSM_WCDMA;

    public static final int NT_MODE_CDMA         = RILConstants.NETWORK_MODE_CDMA;

    public static final int NT_MODE_CDMA_NO_EVDO = RILConstants.NETWORK_MODE_CDMA_NO_EVDO;
    public static final int NT_MODE_EVDO_NO_CDMA = RILConstants.NETWORK_MODE_EVDO_NO_CDMA;
    public static final int NT_MODE_GLOBAL       = RILConstants.NETWORK_MODE_GLOBAL;
    public static final int NT_MODE_LTE_CDMA_EVDO = RILConstants.NETWORK_MODE_LTE_CDMA_EVDO;
    public static final int NT_MODE_LTE_CMDA_EVDO_GSM_WCDMA = RILConstants.NETWORK_MODE_LTE_CMDA_EVDO_GSM_WCDMA;

    public static final int NT_MODE_LTE_ONLY     = RILConstants.NETWORK_MODE_LTE_ONLY;
    public static final int PREFERRED_NT_MODE    = RILConstants.PREFERRED_NETWORK_MODE;

    // Number presentation type for caller id display (From internal/Conneciton.java)
    public static int PRESENTATION_ALLOWED = 1;    // normal
    public static int PRESENTATION_RESTRICTED = 2; // block by user
    public static int PRESENTATION_UNKNOWN = 3;    // no specified or unknown by network
    public static int PRESENTATION_PAYPHONE = 4;   // show pay phone info


    public static final String PHONE_NAME_KEY = "phoneName";
    public static final String FAILURE_REASON_KEY = "reason";
    public static final String STATE_CHANGE_REASON_KEY = "reason";
    public static final String DATA_APN_TYPE_KEY = "apnType";
    public static final String DATA_APN_KEY = "apn";
    public static final String DATA_LINK_PROPERTIES_KEY = "linkProperties";
    public static final String DATA_LINK_CAPABILITIES_KEY = "linkCapabilities";

    public static final String DATA_IFACE_NAME_KEY = "iface";
    public static final String NETWORK_UNAVAILABLE_KEY = "networkUnvailable";
    public static final String DATA_NETWORK_ROAMING_KEY = "networkRoaming";
    public static final String PHONE_IN_ECM_STATE = "phoneinECMState";

    public static final String REASON_LINK_PROPERTIES_CHANGED = "linkPropertiesChanged";

    /**
     * Return codes for supplyPinReturnResult and
     * supplyPukReturnResult APIs
     */
    public static final int PIN_RESULT_SUCCESS = 0;
    public static final int PIN_PASSWORD_INCORRECT = 1;
    public static final int PIN_GENERAL_FAILURE = 2;

    /**
     * Return codes for <code>enableApnType()</code>
     */
    public static final int APN_ALREADY_ACTIVE     = 0;
    public static final int APN_REQUEST_STARTED    = 1;
    public static final int APN_TYPE_NOT_AVAILABLE = 2;
    public static final int APN_REQUEST_FAILED     = 3;
    public static final int APN_ALREADY_INACTIVE   = 4;

    /**
     * APN types for data connections.  These are usage categories for an APN
     * entry.  One APN entry may support multiple APN types, eg, a single APN
     * may service regular internet traffic ("default") as well as MMS-specific
     * connections.<br/>
     * APN_TYPE_ALL is a special type to indicate that this APN entry can
     * service all data connections.
     */
    public static final String APN_TYPE_ALL = "*";
    /** APN type for default data traffic */
    public static final String APN_TYPE_DEFAULT = "default";
    /** APN type for MMS traffic */
    public static final String APN_TYPE_MMS = "mms";
    /** APN type for SUPL assisted GPS */
    public static final String APN_TYPE_SUPL = "supl";
    /** APN type for DUN traffic */
    public static final String APN_TYPE_DUN = "dun";
    /** APN type for HiPri traffic */
    public static final String APN_TYPE_HIPRI = "hipri";
    /** APN type for FOTA */
    public static final String APN_TYPE_FOTA = "fota";
    /** APN type for IMS */
    public static final String APN_TYPE_IMS = "ims";
    /** APN type for CBS */
    public static final String APN_TYPE_CBS = "cbs";
    /** APN type for IA Initial Attach APN */
    public static final String APN_TYPE_IA = "ia";

}
