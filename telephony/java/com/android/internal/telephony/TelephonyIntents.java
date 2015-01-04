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

import android.content.Intent;

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

    /**
     * <p>Broadcast Action: It indicates one column of a siminfo record has been changed
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>columnName</em> - The siminfo column that is updated.</li>
     *   <li><em>stringContent</em> - The string value of the updated column.</li>
     *   <li><em>intContent</em> - The int value of the updated column.</li>
     * </ul>
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SIMINFO_CONTENT_CHANGE
            = "android.intent.action.ACTION_SIMINFO_CONTENT_CHANGE";

    /**
     * <p>Broadcast Action: It indicates one column of a subinfo record has been changed
     * The intent will have the following extra values:</p>
     * <ul>
     *   <li><em>columnName</em> - The siminfo column that is updated.</li>
     *   <li><em>stringContent</em> - The string value of the updated column.</li>
     *   <li><em>intContent</em> - The int value of the updated column.</li>
     * </ul>
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SUBINFO_CONTENT_CHANGE
            = "android.intent.action.ACTION_SUBINFO_CONTENT_CHANGE";

    /**
     * <p>Broadcast Action: It indicates siminfo update is completed when SIM inserted state change
     * The intent will have the following extra values:</p>
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SIMINFO_UPDATED
            = "android.intent.action.ACTION_SIMINFO_UPDATED";

    /**
     * <p>Broadcast Action: It indicates subinfo record update is completed
     * when SIM inserted state change
     * The intent will have the following extra values:</p>
     * <p class="note">This is a protected intent that can only be sent
     * by the system.
     */
    public static final String ACTION_SUBINFO_RECORD_UPDATED
            = "android.intent.action.ACTION_SUBINFO_RECORD_UPDATED";

    public static final String EXTRA_COLUMN_NAME = "columnName";
    public static final String EXTRA_INT_CONTENT = "intContent";
    public static final String EXTRA_STRING_CONTENT = "stringContent";

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


    /**
     * Broadcast Action: The subscription activation/deactivation request result.
     *  This has the following extra values:</p>
     * <ul>
     *   <li><em>operationResult</em> - A int, result of subscription
     *   activation/deactivation request.</li>
     *   <li><em>NewSubState</em> - A int, new sub state(activate/deactivate) clients
     *   trying to set for the current subscription.</li>
     * </ul>
     */
    public static final String ACTION_SUBSCRIPTION_SET_UICC_RESULT
            = "org.codeaurora.intent.action.ACTION_SUBSCRIPTION_SET_UICC_RESULT";

    public static final String EXTRA_RESULT  = "operationResult";
    public static final String EXTRA_NEW_SUB_STATE = "newSubState";
}
