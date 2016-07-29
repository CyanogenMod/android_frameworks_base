/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * The intents that the telephony services broadcast.
 *
 * <p class="warning">
 * THESE ARE NOT THE API!  Use the {@link android.telephony.TelephonyManager} class.
 * DON'T LISTEN TO THESE DIRECTLY.
 */
public class TelephonyIntents {

    /**
     * Broadcast Action: The phone service state has changed. The intent will have the following
     * extra values:</p>
     * <ul>
     *   <li><em>state</em> - An int with one of the following values:
     *          {@link android.telephony.ServiceState#STATE_IN_SERVICE},
     *          {@link android.telephony.ServiceState#STATE_OUT_OF_SERVICE},
     *          {@link android.telephony.ServiceState#STATE_EMERGENCY_ONLY}
     *          or {@link android.telephony.ServiceState#STATE_POWER_OFF}
     *   <li><em>roaming</em> - A boolean value indicating whether the phone is roaming.</li>
     *   <li><em>operator-alpha-long</em> - The carrier name as a string.</li>
     *   <li><em>operator-alpha-short</em> - A potentially shortened version of the carrier name,
     *          as a string.</li>
     *   <li><em>operator-numeric</em> - A number representing the carrier, as a string. This is
     *          a five or six digit number consisting of the MCC (Mobile Country Code, 3 digits)
     *          and MNC (Mobile Network code, 2-3 digits).</li>
     *   <li><em>manual</em> - A boolean, where true indicates that the user has chosen to select
     *          the network manually, and false indicates that network selection is handled by the
     *          phone.</li>
     * </ul>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SERVICE_STATE_CHANGED = "android.intent.action.SERVICE_STATE";

    /**
     * <p>Broadcast Action: The radio technology has changed. The intent will have the following
     * extra values:</p>
     * <ul>
     *   <li><em>phoneName</em> - A string version of the new phone name.</li>
     * </ul>
     *
     * <p class="note">
     * You can <em>not</em> receive this through components declared
     * in manifests, only by explicitly registering for it with
     * {@link android.content.Context#registerReceiver(android.content.BroadcastReceiver,
     * android.content.IntentFilter) Context.registerReceiver()}.
     *
     * <p class="note">
     * Requires no permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_RADIO_TECHNOLOGY_CHANGED
            = "android.intent.action.RADIO_TECHNOLOGY";

    /**
     * <p>Broadcast Action: The emergency callback mode is changed.
     * <ul>
     *   <li><em>phoneinECMState</em> - A boolean value,true=phone in ECM, false=ECM off</li>
     * </ul>
     * <p class="note">
     * You can <em>not</em> receive this through components declared
     * in manifests, only by explicitly registering for it with
     * {@link android.content.Context#registerReceiver(android.content.BroadcastReceiver,
     * android.content.IntentFilter) Context.registerReceiver()}.
     *
     * <p class="note">
     * Requires no permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_EMERGENCY_CALLBACK_MODE_CHANGED
            = "android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED";

    /**
     * <p>Broadcast Action: The emergency call state is changed.
     * <ul>
     *   <li><em>phoneInEmergencyCall</em> - A boolean value, true if phone in emergency call,
     *   false otherwise</li>
     * </ul>
     * <p class="note">
     * You can <em>not</em> receive this through components declared
     * in manifests, only by explicitly registering for it with
     * {@link android.content.Context#registerReceiver(android.content.BroadcastReceiver,
     * android.content.IntentFilter) Context.registerReceiver()}.
     *
     * <p class="note">
     * Requires no permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_EMERGENCY_CALL_STATE_CHANGED
            = "android.intent.action.EMERGENCY_CALL_STATE_CHANGED";

    /**
     * Broadcast Action: The phone's signal strength has changed. The intent will have the
     * following extra values:</p>
     * <ul>
     *   <li><em>phoneName</em> - A string version of the phone name.</li>
     *   <li><em>asu</em> - A numeric value for the signal strength.
     *          An ASU is 0-31 or -1 if unknown (for GSM, dBm = -113 - 2 * asu).
     *          The following special values are defined:
     *          <ul><li>0 means "-113 dBm or less".</li><li>31 means "-51 dBm or greater".</li></ul>
     *   </li>
     * </ul>
     *
     * <p class="note">
     * You can <em>not</em> receive this through components declared
     * in manifests, only by exlicitly registering for it with
     * {@link android.content.Context#registerReceiver(android.content.BroadcastReceiver,
     * android.content.IntentFilter) Context.registerReceiver()}.
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SIGNAL_STRENGTH_CHANGED = "android.intent.action.SIG_STR";


    /**
     * Broadcast Action: The data connection state has changed for any one of the
     * phone's mobile data connections (eg, default, MMS or GPS specific connection).
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
     *   <dt>state</dt><dd>One of {@code CONNECTED}, {@code CONNECTING},
     *      or {@code DISCONNECTED}.</dd>
     *   <dt>apn</dt><dd>A string that is the APN associated with this connection.</dd>
     *   <dt>apnType</dt><dd>A string array of APN types associated with this connection.
     *      The APN type {@code *} is a special type that means this APN services all types.</dd>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_ANY_DATA_CONNECTION_STATE_CHANGED
            = "android.intent.action.ANY_DATA_STATE";

    /**
     * Broadcast Action: Occurs when a data connection connects to a provisioning apn
     * and is broadcast by the low level data connection code.
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>apn</dt><dd>A string that is the APN associated with this connection.</dd>
     *   <dt>apnType</dt><dd>A string array of APN types associated with this connection.
     *      The APN type {@code *} is a special type that means this APN services all types.</dd>
     *   <dt>linkProperties</dt><dd>{@code LinkProperties} for this APN.</dd>
     *   <dt>linkCapabilities</dt><dd>The {@code LinkCapabilities} for this APN.</dd>
     *   <dt>iface</dt><dd>A string that is the name of the interface.</dd>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN
            = "android.intent.action.DATA_CONNECTION_CONNECTED_TO_PROVISIONING_APN";

    /**
     * Broadcast Action: An attempt to establish a data connection has failed.
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
     *   <dt>state</dt><dd>One of {@code CONNECTED}, {@code CONNECTING}, or {code DISCONNECTED}.</dd>
     *   <dt>reason</dt><dd>A string indicating the reason for the failure, if available.</dd>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_DATA_CONNECTION_FAILED
            = "android.intent.action.DATA_CONNECTION_FAILED";


    /**
     * Broadcast Action: The sim card state has changed.
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
     *   <dt>ss</dt><dd>The sim state. One of:
     *     <dl>
     *       <dt>{@code ABSENT}</dt><dd>SIM card not found</dd>
     *       <dt>{@code LOCKED}</dt><dd>SIM card locked (see {@code reason})</dd>
     *       <dt>{@code READY}</dt><dd>SIM card ready</dd>
     *       <dt>{@code IMSI}</dt><dd>FIXME: what is this state?</dd>
     *       <dt>{@code LOADED}</dt><dd>SIM card data loaded</dd>
     *     </dl></dd>
     *   <dt>reason</dt><dd>The reason why ss is {@code LOCKED}; null otherwise.</dd>
     *   <dl>
     *       <dt>{@code PIN}</dt><dd>locked on PIN1</dd>
     *       <dt>{@code PUK}</dt><dd>locked on PUK1</dd>
     *       <dt>{@code NETWORK}</dt><dd>locked on network personalization</dd>
     *   </dl>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SIM_STATE_CHANGED
            = "android.intent.action.SIM_STATE_CHANGED";


    /**
     * Broadcast Action: The time was set by the carrier (typically by the NITZ string).
     * This is a sticky broadcast.
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>time</em> - The time as a long in UTC milliseconds.</li>
     * </ul>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_NETWORK_SET_TIME = "android.intent.action.NETWORK_SET_TIME";


    /**
     * Broadcast Action: The timezone was set by the carrier (typically by the NITZ string).
     * This is a sticky broadcast.
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>time-zone</em> - The java.util.TimeZone.getID() value identifying the new time
     *          zone.</li>
     * </ul>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_NETWORK_SET_TIMEZONE
            = "android.intent.action.NETWORK_SET_TIMEZONE";

    /**
     * <p>Broadcast Action: It indicates the Emergency callback mode blocks datacall/sms
     * <p class="note">.
     * This is to pop up a notice to show user that the phone is in emergency callback mode
     * and atacalls and outgoing sms are blocked.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS
            = "android.intent.action.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS";

    /**
     * Activity Action: Start this activity to invoke the carrier setup app.
     * The carrier app must be signed using a certificate that matches the UICC access rules.
     *
     * <p class="note">Callers of this should hold the android.permission.INVOKE_CARRIER_SETUP
     * permission.</p>
     */
    public static final String ACTION_CARRIER_SETUP = "android.intent.action.ACTION_CARRIER_SETUP";

    /**
     * <p>Broadcast Action: Indicates that the action is forbidden by network.
     * <p class="note">
     * This is for the OEM applications to understand about possible provisioning issues.
     * Used in OMA-DM applications.
     */
    public static final String ACTION_FORBIDDEN_NO_SERVICE_AUTHORIZATION
            = "android.intent.action.ACTION_FORBIDDEN_NO_SERVICE_AUTHORIZATION";

    /**
     * Broadcast Action: A "secret code" has been entered in the dialer. Secret codes are
     * of the form {@code *#*#<code>#*#*}. The intent will have the data URI:
     *
     * {@code android_secret_code://<code>}
     */
    public static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";

    /**
     * Broadcast Action: The Service Provider string(s) have been updated.  Activities or
     * services that use these strings should update their display.
     * The intent will have the following extra values:</p>
     *
     * <dl>
     *   <dt>showPlmn</dt><dd>Boolean that indicates whether the PLMN should be shown.</dd>
     *   <dt>plmn</dt><dd>The operator name of the registered network, as a string.</dd>
     *   <dt>showSpn</dt><dd>Boolean that indicates whether the SPN should be shown.</dd>
     *   <dt>spn</dt><dd>The service provider name, as a string.</dd>
     * </dl>
     *
     * Note that <em>showPlmn</em> may indicate that <em>plmn</em> should be displayed, even
     * though the value for <em>plmn</em> is null.  This can happen, for example, if the phone
     * has not registered to a network yet.  In this case the receiver may substitute an
     * appropriate placeholder string (eg, "No service").
     *
     * It is recommended to display <em>plmn</em> before / above <em>spn</em> if
     * both are displayed.
     *
     * <p>Note: this is a protected intent that can only be sent by the system.
     */
    public static final String SPN_STRINGS_UPDATED_ACTION =
            "android.provider.Telephony.SPN_STRINGS_UPDATED";

    public static final String EXTRA_SHOW_PLMN  = "showPlmn";
    public static final String EXTRA_PLMN       = "plmn";
    public static final String EXTRA_SHOW_SPN   = "showSpn";
    public static final String EXTRA_SPN        = "spn";
    public static final String EXTRA_DATA_SPN   = "spnData";

    /**
     * <p>Broadcast Action: It indicates one column of a subinfo record has been changed
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SUBINFO_CONTENT_CHANGE
            = "android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE";

    /**
     * <p>Broadcast Action: It indicates subinfo record update is completed
     * when SIM inserted state change
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SUBINFO_RECORD_UPDATED
            = "android.intent.action.ACTION_SUBINFO_RECORD_UPDATED";

    /**
     * Broadcast Action: The default subscription has changed.  This has the following
     * extra values:</p>
     * <ul>
     *   <li><em>subscription</em> - A int, the current default subscription.</li>
     * </ul>
     */
    public static final String ACTION_DEFAULT_SUBSCRIPTION_CHANGED
            = "android.intent.action.ACTION_DEFAULT_SUBSCRIPTION_CHANGED";

    /**
     * Broadcast Action: The default data subscription has changed.  This has the following
     * extra values:</p>
     * <ul>
     *   <li><em>subscription</em> - A int, the current data default subscription.</li>
     * </ul>
     */
    public static final String ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED
            = "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED";

    /**
     * Broadcast Action: The default voice subscription has changed.  This has the following
     * extra values:</p>
     * <ul>
     *   <li><em>subscription</em> - A int, the current voice default subscription.</li>
     * </ul>
     */
    public static final String ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED
            = "android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED";

    /**
     * Broadcast Action: The default sms subscription has changed.  This has the following
     * extra values:</p>
     * <ul>
     *   <li><em>subscription</em> - A int, the current sms default subscription.</li>
     * </ul>
     */
    public static final String ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED
            = "android.intent.action.ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED";

    /*
     * Broadcast Action: An attempt to set phone radio type and access technology has changed.
     * This has the following extra values:
     * <ul>
     *   <li><em>phones radio access family </em> - A RadioAccessFamily
     *   array, contain phone ID and new radio access family for each phone.</li>
     * </ul>
     */
    public static final String ACTION_SET_RADIO_CAPABILITY_DONE =
            "android.intent.action.ACTION_SET_RADIO_CAPABILITY_DONE";

    public static final String EXTRA_RADIO_ACCESS_FAMILY = "rafs";

    /*
     * Broadcast Action: An attempt to set phone radio access family has failed.
     */
    public static final String ACTION_SET_RADIO_CAPABILITY_FAILED =
            "android.intent.action.ACTION_SET_RADIO_CAPABILITY_FAILED";

    // MTK

// M: [LTE][Low Power][UL traffic shaping] Start
    /**
     * Broadcast Action: The LTE access stratum state has changed.
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
     *   <dt>state</dt><dd>One of {@code UNKNOWN}, {@code IDLE},
     *      or {@code CONNECTED}.</dd>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_LTE_ACCESS_STRATUM_STATE_CHANGED
            = "android.intent.action.LTE_ACCESS_STRATUM_STATE_CHANGED";

    /**
     * Broadcast Action: The PS network type has changed for low power feature on.
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
     *   <dt>nwType</dt><dd>One of
     *          {@code NETWORK_TYPE_UNKNOWN},
     *          {@code NETWORK_TYPE_GPRS},
     *          {@code NETWORK_TYPE_EDGE},
     *          {@code NETWORK_TYPE_UMTS},
     *          {@code NETWORK_TYPE_HSDPA},
     *          {@code NETWORK_TYPE_HSUPA},
     *          {@code NETWORK_TYPE_HSPA} or
     *          {@code NETWORK_TYPE_LTE}.</dd>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_PS_NETWORK_TYPE_CHANGED
            = "android.intent.action.PS_NETWORK_TYPE_CHANGED";

    /**
     * Broadcast Action: The shared default apn state has changed.
     * The intent will have the following extra values:</p>
     * <dl>
     *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
     *   <dt>state</dt><dd>One of {@code TRUE} or {@code FALSE}.</dd>
     * </dl>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SHARED_DEFAULT_APN_STATE_CHANGED
            = "android.intent.action.SHARED_DEFAULT_APN_STATE_CHANGED";
// M: [LTE][Low Power][UL traffic shaping] End

    /**
     * Broadcast Action: An attempt to set phone RAT family has changed.  This has the following
     * extra values:</p>
     * <ul>
     *   <li><em>phones RAT family</em> - A PhoneRatFamily array,
     *          contain phone ID and new RAT family for each phone.</li>
     * </ul>
     * @internal
     */
    public static final String ACTION_SET_PHONE_RAT_FAMILY_DONE =
            "android.intent.action.ACTION_SET_PHONE_RAT_FAMILY_DONE";
    /**
     * @internal
     */
    public static final String EXTRA_PHONES_RAT_FAMILY = "phonesRatFamily";

    /**
     * Broadcast Action: An attempt to set phone RAT family has failed.
     * <ul>
     *   <li><em>phone ID</em> - A int, indicates the failed phone.</li>
     * </ul>
     * @internal
     */
    public static final String ACTION_SET_PHONE_RAT_FAMILY_FAILED =
            "android.intent.action.ACTION_SET_PHONE_RAT_FAMILY_FAILED";
    /**
     * @internal
     */
    public static final String EXTRA_PHONES_ID = "phoneId";

    // Added by M begin

    /**
     * <p>Broadcast Action: To activate an application to unlock SIM lock.
     * The intent will have the following extra value:</p>
     * <dl>
     *   <dt>reason</dt><dd>The reason why ss is {@code LOCKED}; null otherwise.</dd>
     *   <dl>
     *       <dt>{@code PIN}</dt><dd>locked on PIN1</dd>
     *       <dt>{@code PUK}</dt><dd>locked on PUK1</dd>
     *       <dt>{@code NETWORK}</dt><dd>locked on network personalization</dd>
     *       <dt>{@code NETWORK_SUBSET}</dt><dd>locked on network subset personalization</dd>
     *       <dt>{@code CORPORATE}</dt><dd>locked on corporate personalization</dd>
     *       <dt>{@code SERVICE_PROVIDER}</dt><dd>locked on service proiver personalization</dd>
     *       <dt>{@code SIM}</dt><dd>locked on SIM personalization</dd>
     *   </dl>
     * </dl>
     * @internal
     */
     // FIXME: need to add subId, slotId, phoneId extra value comments.
     public static final String ACTION_UNLOCK_SIM_LOCK
            = "mediatek.intent.action.ACTION_UNLOCK_SIM_LOCK";


     /**
      * Broadcast Action: The sim card application state has changed. (only support ISIM currently)
      * The intent will have the following extra values:</p>
      * <dl>
      *   <dt>phoneName</dt><dd>A string version of the phone name.</dd>
      *   <dt>ss</dt><dd>The sim state. One of:
      *     <dl>
      *       <dt>{@code ABSENT}</dt><dd>SIM card not found</dd>
      *       <dt>{@code LOCKED}</dt><dd>SIM card locked (see {@code reason})</dd>
      *       <dt>{@code READY}</dt><dd>SIM card ready</dd>
      *       <dt>{@code IMSI}</dt><dd>FIXME: what is this state?</dd>
      *       <dt>{@code LOADED}</dt><dd>SIM card data loaded</dd>
      *     </dl></dd>
      *   <dt>reason</dt><dd>The reason why ss is {@code LOCKED}; null otherwise.</dd>
      *   <dl>
      *       <dt>{@code PIN}</dt><dd>locked on PIN1</dd>
      *       <dt>{@code PUK}</dt><dd>locked on PUK1</dd>
      *       <dt>{@code NETWORK}</dt><dd>locked on network personalization</dd>
      *   </dl>
      *   <dt>appid</dt><dd>The application id.</dd>
      * </dl>
      *
      * <p class="note">This is a protected intent that can only be sent
      * by the system.
      */
      // FIXME: need to add subId, slotId, phoneId extra value comments.
     public static final String ACTION_SIM_STATE_CHANGED_MULTI_APPLICATION
             = "mediatek.intent.action.ACTION_SIM_STATE_CHANGED_MULTI_APPLICATION";

    /**
    * Do SIM Recovery Done.
    */
    public static final String ACTION_SIM_RECOVERY_DONE = "com.android.phone.ACTION_SIM_RECOVERY_DONE";

    // ALPS00302698 ENS
    /**
       * This event is broadcasted when CSP PLMN is changed
       * @internal
       */
    public static final String ACTION_EF_CSP_CONTENT_NOTIFY = "android.intent.action.ACTION_EF_CSP_CONTENT_NOTIFY";
    public static final String EXTRA_PLMN_MODE_BIT = "plmn_mode_bit";

    // ALPS00302702 RAT balancing
    /**
       * This event is broadcasted when EF-RAT Mode is changed.
       * @internal
       */
    public static final String ACTION_EF_RAT_CONTENT_NOTIFY = "android.intent.action.ACTION_EF_RAT_CONTENT_NOTIFY";
    /**
       * To notify the content of EF-RAT
       * @internal
       */
    public static final String EXTRA_EF_RAT_CONTENT = "ef_rat_content";
    /**
       * To notify the status of EF-RAT
       * @internal
       */
    public static final String EXTRA_EF_RAT_STATUS = "ef_rat_status";

    public static final String ACTION_COMMON_SLOT_NO_CHANGED = "com.mediatek.phone.ACTION_COMMON_SLOT_NO_CHANGED";


  /**
      * Broadcast Action: ACMT Network Service Status Indicator
      * The intent will have the following extra values:</p>
      * <ul>
      * <li><em>CauseCode</em> - specify the reject cause code from MM/GMM/EMM</li>
      * <li><em>Cause</em> - the reject cause<li>
      * </ul>
      */
    public static final String ACTION_ACMT_NETWORK_SERVICE_STATUS_INDICATOR
            = "mediatek.intent.action.acmt_nw_service_status";

    //MTK-START [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR
    public static final String ACTION_IVSR_NOTIFY
        = "mediatek.intent.action.IVSR_NOTIFY";

    public static final String INTENT_KEY_IVSR_ACTION = "action";
    //MTK-END [mtk80589][121026][ALPS00376525] STK dialog pop up caused ISVR

   /* ALPS01139189 */
   /**
     * This event is broadcasted when frmework start/stop hiding network state update
     * @internal
     */
    public static final String ACTION_HIDE_NETWORK_STATE = "mediatek.intent.action.ACTION_HIDE_NETWORK_STATE";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_REAL_SERVICE_STATE = "state";

    /**
     * This event is broadcasted when the located PLMN is changed
     * @internal
     */
    public static final String ACTION_LOCATED_PLMN_CHANGED = "mediatek.intent.action.LOCATED_PLMN_CHANGED";
    public static final String EXTRA_ISO = "iso";

  /**
     * This extra value is the IMS registeration state
     */
    public static final String EXTRA_IMS_REG_STATE_KEY = "regState"; // 0: not registered  , 1: registered

    // Femtocell (CSG) START
    public static final String EXTRA_HNB_NAME   = "hnbName";
    public static final String EXTRA_CSG_ID     = "csgId";
    public static final String EXTRA_DOMAIN     = "domain";
    // Femtocell (CSG) END

    /**
     * Broadcast Action: The PHB state has changed.
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>ready</em> - The PHB ready state.  True for ready, false for not ready</li>
     *   <li><em>simId</em> - The SIM ID</li>
     * </ul>
     *
     * <p class="note">
     * Requires the READ_PHONE_STATE permission.
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     * @internal
     */
    public static final String ACTION_PHB_STATE_CHANGED
            = "android.intent.action.PHB_STATE_CHANGED";

    /* SIM switch start */
    /**
     * To notify the capability switch procedure start
     */
    public static String EVENT_PRE_CAPABILITY_SWITCH = "com.mediatek.PRE_CAPABILITY_SWITCH";
    /**
     * To notify the capability switch procedure end
     */
    public static String EVENT_CAPABILITY_SWITCH_DONE = "com.mediatek.CAPABILITY_SWITCH_DONE";
    /**
     * The target SIM Id where capability is going to set to.
     * This is an extra information comes with EVENT_CAPABILITY_PRE_SWITCH event.
     */
    public static String EXTRA_MAIN_PROTOCOL_SIM = "MAIN_PROTOCOL_SIM";
    // Added by M end

    /**
     * Broadcast Action: The modem type changed.
     * @internal
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>ready</em> - The modem type after switched.</li>
     * </ul>
     */
    public static final String ACTION_MD_TYPE_CHANGE
            = "android.intent.action.ACTION_MD_TYPE_CHANGE";
    /** @internal */
    public static final String EXTRA_MD_TYPE = "mdType";

    /**
    * This event is broadcasted when Stk Refresh with type REFRESH_RESULT_INIT,
    * REFRESH_RESULT_RESET, REFRESH_INIT_FULL_FILE_UPDATED, REFRESH_INIT_FILE_UPDATED
    * @internal
    */
    public static final String ACTION_REMOVE_IDLE_TEXT = "android.intent.aciton.stk.REMOVE_IDLE_TEXT";

    /**
    * @hide
    */
    public static final String ACTION_REMOVE_IDLE_TEXT_2 = "android.intent.aciton.stk.REMOVE_IDLE_TEXT_2";

    /// M: IMS feature for SS Runtime  Indication. @{
    public static final String ACTION_LTE_MESSAGE_WAITING_INDICATION = "android.intent.action.lte.mwi";
    public static final String EXTRA_LTE_MWI_BODY = "lte_mwi_body";
    /// @}

    /// M: c2k modify, intents. @{
    // MCC MNC Change
    public static final String ACTION_MCC_MNC_CHANGED = "android.intent.action.MCC_MNC_CHANGED";
    public static final String EXTRA_MCC_MNC_CHANGED_MCC = "mcc";
    public static final String EXTRA_MCC_MNC_CHANGED_MNC = "mnc";
    // RADIO AVAILABLE
    public static final String ACTION_RADIO_AVAILABLE = "android.intent.action.RADIO_AVAILABLE";
    public static final String EXTRA_RADIO_AVAILABLE_STATE = "radio_available_state";
    /// @}

    /**
     * This event is for abnormal event for logger
    */
    public static final String ACTION_EXCEPTION_HAPPENED
        = "com.mediatek.log2server.EXCEPTION_HAPPEND";
    /**
    * To identify CDMA card  type.
    * For CT request, this type used for CDMA modem identify card type and report to framework
    * <P>Type: int</P>
    */
    // MTK START
    public static final String ACTION_CDMA_CARD_TYPE = "android.intent.action.CDMA_CARD_TYPE";
    public static final String INTENT_KEY_CDMA_CARD_TYPE = "cdma_card_type";
    public static final String INTENT_KEY_CDMA_CARD_NEW = "cdma_card_new";

    public static final String ACTION_CDMA_CARD_IMSI = "android.intent.action.CDMA_CARD_IMSI";
    public static final String INTENT_KEY_CDMA_CARD_CSIM_IMSI = "cdma_card_csim_imsi";
    public static final String INTENT_KEY_CDMA_CARD_USIM_IMSI = "cdma_card_usim_imsi";
    public static final String INTENT_KEY_SVLTE_MODE_SLOT_ID = "svlte_mode_slot_id";

    public static final String ACTION_SVLTE_CARD_TYPE = "android.intent.action.SVLTE_CARD_TYPE";
    public static final String INTENT_KEY_SVLTE_CARD_TYPE = "svlte_card_type";
    // MTK END
    /**
     * Broadcast Action: The modem rat changed.
     * @hide
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>ready</em> - The modem rat after changed.</li>
     * </ul>
     */
    public static final String ACTION_RAT_CHANGED
            = "android.intent.action.ACTION_RAT_CHANGED";
    /** @hide */
    public static final String EXTRA_RAT = "rat";

    ///M: C2k WP s2 @{
    /**
     * Broadcast after all Radio technology switch done.
     */
    public static final String ACTION_SET_RADIO_TECHNOLOGY_DONE =
                    "com.mediatek.phone.ACTION_SET_RADIO_TECHNOLOGY_DONE";

    /**
     * Broadcast Radio technology switch start.
     */
    public static final String ACTION_SET_RADIO_TECHNOLOGY_START =
                    "com.mediatek.phone.ACTION_SET_RADIO_TECHNOLOGY_START";
    ///@}

    /**
     * Broadcast Action: The world mode changed.
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>worldModeState</em> - An int with one of the following values:
     *          {@link com.mediatek.internal.telephony.worldphone.WorldMode#MD_WM_CHANGED_START} or
     *          {@link com.mediatek.internal.telephony.worldphone.WorldMode#MD_WM_CHANGED_END}
     *   </li>
     * </ul>
     */
    public static final String ACTION_WORLD_MODE_CHANGED
            = "android.intent.action.ACTION_WORLD_MODE_CHANGED";
    /**
     * Broadcast world mode change state.
     */
    public static final String EXTRA_WORLD_MODE_CHANGE_STATE = "worldModeState";
}
