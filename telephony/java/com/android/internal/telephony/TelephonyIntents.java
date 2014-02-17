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
     * <ul>
     *   <li><em>phoneName</em> - A string version of the phone name.</li>
     *   <li><em>state</em> - One of <code>"CONNECTED"</code>
     *      <code>"CONNECTING"</code> or <code>"DISCONNNECTED"</code></li>
     *   <li><em>apn</em> - A string that is the APN associated with this
     *      connection.</li>
     *   <li><em>apnType</em> - A string array of APN types associated with
     *      this connection.  The APN type <code>"*"</code> is a special
     *      type that means this APN services all types.</li>
     * </ul>
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
     * <ul>
     *   <li><em>apn</em> - A string that is the APN associated with this
     *      connection.</li>
     *   <li><em>apnType</em> - A string array of APN types associated with
     *      this connection.  The APN type <code>"*"</code> is a special
     *      type that means this APN services all types.</li>
     *   <li><em>linkProperties</em> - The <code>LinkProperties</code> for this APN</li>
     *   <li><em>linkCapabilities</em> - The <code>linkCapabilities</code> for this APN</li>
     *   <li><em>iface</em> - A string that is the name of the interface</li>
     * </ul>
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
     * <ul>
     *   <li><em>phoneName</em> &mdash A string version of the phone name.</li>
     *   <li><em>state</em> &mdash; One of <code>"CONNECTED"</code>
     *      <code>"CONNECTING"</code> or <code>"DISCONNNECTED"</code></li>
     * <li><em>reason</em> &mdash; A string indicating the reason for the failure, if available</li>
     * </ul>
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
     * <ul>
     *   <li><em>phoneName</em> - A string version of the phone name.</li>
     *   <li><em>ss</em> - The sim state.  One of
     *   <code>"ABSENT"</code> <code>"LOCKED"</code>
     *   <code>"READY"</code> <code>"ISMI"</code> <code>"LOADED"</code> </li>
     *   <li><em>reason</em> - The reason while ss is LOCKED, otherwise is null
     *   <code>"PIN"</code> locked on PIN1
     *   <code>"PUK"</code> locked on PUK1
     *   <code>"NETWORK"</code> locked on Network Personalization </li>
     * </ul>
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
     * To filter the intent, see {@link #CATEGORY_MCCMNC_PREFIX}.
     *
     * <p class="note">Callers of this should hold the android.permission.INVOKE_CARRIER_SETUP
     * permission.</p>
     */
    public static final String ACTION_CARRIER_SETUP = "android.intent.action.ACTION_CARRIER_SETUP";

    /**
     * A <em>prefix</em> for the MCC/MNC filtering used with {@link #ACTION_CARRIER_SETUP}.
     * The MCC/MNC will be concatenated (zero-padded to 3 digits each) to create a final
     * string of the form:
     * <br />
     * <code>android.intent.category.MCCMNC_310260</code>
     */
    public static final String CATEGORY_MCCMNC_PREFIX = "android.intent.category.MCCMNC_";

    /**
     * Broadcast Action: A "secret code" has been entered in the dialer. Secret codes are
     * of the form *#*#<code>#*#*. The intent will have the data URI:</p>
     *
     * <p><code>android_secret_code://&lt;code&gt;</code></p>
     */
    public static final String SECRET_CODE_ACTION =
            "android.provider.Telephony.SECRET_CODE";

    /**
     * Broadcast Action: The Service Provider string(s) have been updated.  Activities or
     * services that use these strings should update their display.
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>showPlmn</em> - Boolean that indicates whether the PLMN should be shown.</li>
     *   <li><em>plmn</em> - The operator name of the registered network, as a string.</li>
     *   <li><em>showSpn</em> - Boolean that indicates whether the SPN should be shown.</li>
     *   <li><em>spn</em> - The service provider name, as a string.</li>
     * </ul>
     * Note that <em>showPlmn</em> may indicate that <em>plmn</em> should be displayed, even
     * though the value for <em>plmn</em> is null.  This can happen, for example, if the phone
     * has not registered to a network yet.  In this case the receiver may substitute an
     * appropriate placeholder string (eg, "No service").
     *
     * It is recommended to display <em>plmn</em> before / above <em>spn</em> if
     * both are displayed.
     *
     * <p>Note this is a protected intent that can only be sent
     * by the system.
     */
    public static final String SPN_STRINGS_UPDATED_ACTION =
            "android.provider.Telephony.SPN_STRINGS_UPDATED";

    public static final String EXTRA_SHOW_PLMN  = "showPlmn";
    public static final String EXTRA_PLMN       = "plmn";
    public static final String EXTRA_SHOW_SPN   = "showSpn";
    public static final String EXTRA_SPN        = "spn";

     /**
     * Managed Roaming Intent. Used by Phone App to show popup to the end user that location update
     * request rejected with status as "Persistent location update reject", so user can try to do
     * location update on other Network:</p>
     *
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     * @hide
     */
    public static final String ACTION_MANAGED_ROAMING_IND
            = "codeaurora.intent.action.ACTION_MANAGED_ROAMING_IND";
}
