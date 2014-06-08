/*
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

package android.telephony;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.text.TextUtils;

import com.android.internal.telephony.msim.ITelephonyMSim;
import com.android.internal.telephony.ITelephonyRegistryMSim;
import com.android.internal.telephony.msim.IPhoneSubInfoMSim;
import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyProperties;

import java.util.List;

/**
 * Provides access to information about the telephony services on
 * the device. Applications can use the methods in this class to
 * determine telephony services and states, as well as to access some
 * types of subscriber information. Applications can also register
 * a listener to receive notification of telephony state changes.
 * <p>
 * You do not instantiate this class directly; instead, you retrieve
 * a reference to an instance through
 * {@link android.content.Context#getSystemService
 * Context.getSystemService(Context.MSIM_TELEPHONY_SERVICE)}.
 * <p>
 * Note that access to some telephony information is
 * permission-protected. Your application cannot access the protected
 * information unless it has the appropriate permissions declared in
 * its manifest file. Where permissions apply, they are noted in the
 * the methods through which you access the protected information.
 * @hide
 */
public class MSimTelephonyManager {
    /** @hide */
    private static Context sContext;
    /** @hide */
    protected static ITelephonyRegistryMSim sRegistryMsim;

    protected static String multiSimConfig =
            SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG);

    /** Enum indicating multisim variants
     *  DSDS - Dual SIM Dual Standby
     *  DSDA - Dual SIM Dual Active
     *  TSTS - Triple SIM Triple Standby
     **/
    public enum MultiSimVariants {
        DSDS,
        DSDA,
        TSTS,
        UNKNOWN
    };

    /** @hide */
    public MSimTelephonyManager(Context context) {
        if (sContext == null) {
            Context appContext = context.getApplicationContext();
            if (appContext != null) {
                sContext = appContext;
            } else {
                sContext = context;
            }

            sRegistryMsim = ITelephonyRegistryMSim.Stub.asInterface(ServiceManager.getService(
                    "telephony.msim.registry"));
        }
    }

    /** @hide */
    private MSimTelephonyManager() {
    }

    private static MSimTelephonyManager sInstance = new MSimTelephonyManager();

    /** @hide
    /* @deprecated - use getSystemService as described above */
    public static MSimTelephonyManager getDefault() {
        return sInstance;
    }

    /** {@hide} */
    public static MSimTelephonyManager from(Context context) {
        return (MSimTelephonyManager) context.getSystemService(Context.MSIM_TELEPHONY_SERVICE);
    }

    public boolean isMultiSimEnabled() {
        return (multiSimConfig.equals("dsds") || multiSimConfig.equals("dsda") ||
            multiSimConfig.equals("tsts"));
    }

    /**
     * Returns the multi SIM variant
     * Returns DSDS for Dual SIM Dual Standby
     * Returns DSDA for Dual SIM Dual Active
     * Returns TSTS for Triple SIM Triple Standby
     * Returns UNKNOWN for others
     */
    public MultiSimVariants getMultiSimConfiguration() {
        String mSimConfig =
            SystemProperties.get(TelephonyProperties.PROPERTY_MULTI_SIM_CONFIG);
        if (mSimConfig.equals("dsds")) {
            return MultiSimVariants.DSDS;
        } else if (mSimConfig.equals("dsda")) {
            return MultiSimVariants.DSDA;
        } else if (mSimConfig.equals("tsts")) {
            return MultiSimVariants.TSTS;
        } else {
            return MultiSimVariants.UNKNOWN;
        }
    }


    /**
     * Returns the number of phones available.
     * Returns 1 for Single standby mode (Single SIM functionality)
     * Returns 2 for Dual standby mode.(Dual SIM functionality)
     */
    public int getPhoneCount() {
        int phoneCount = 1;
        switch (getMultiSimConfiguration()) {
            case DSDS:
            case DSDA:
                phoneCount = MSimConstants.MAX_PHONE_COUNT_DUAL_SIM;
                break;
            case TSTS:
                phoneCount = MSimConstants.MAX_PHONE_COUNT_TRI_SIM;
                break;
        }
        return phoneCount;
    }

    /**
     * Returns the software version number for the device, for example,
     * the IMEI/SV for GSM phones. Return null if the software version is
     * not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getDeviceSoftwareVersion(int subscription) {
        try {
            return getMSimSubscriberInfo().getDeviceSvn(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns the unique device ID of a subscription, for example, the IMEI for
     * GSM and the MEID for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param subscription of which deviceID is returned
     */
    public String getDeviceId(int subscription) {

        try {
            return getMSimSubscriberInfo().getDeviceId(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns a constant indicating the device phone type for a subscription.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     *
     * @param subscription for which phone type is returned
     * @hide
     */
    public int getCurrentPhoneType(int subscription) {

        try{
            ITelephonyMSim telephony = getITelephonyMSim();
            if (telephony != null) {
                return telephony.getActivePhoneType(subscription);
            } else {
                // This can happen when the ITelephonyMSim interface is not up yet.
                return getPhoneTypeFromProperty(subscription);
            }
        } catch (RemoteException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty(subscription);
        } catch (NullPointerException ex) {
            // This shouldn't happen in the normal case, as a backup we
            // read from the system property.
            return getPhoneTypeFromProperty(subscription);
        }
    }

    /**
     * Returns a constant indicating the device phone type.  This
     * indicates the type of radio used to transmit voice calls.
     *
     * @see #PHONE_TYPE_NONE
     * @see #PHONE_TYPE_GSM
     * @see #PHONE_TYPE_CDMA
     * @see #PHONE_TYPE_SIP
     *
     * @param subscription for which phone type is returned
     * @hide
     */
    public int getPhoneType(int subscription) {
        if (!TelephonyManager.getDefault().isVoiceCapable()) {
            return TelephonyManager.PHONE_TYPE_NONE;
        }
        return getCurrentPhoneType(subscription);
    }

    private int getPhoneTypeFromProperty(int subscription) {
        String type =
            getTelephonyProperty
                (TelephonyProperties.CURRENT_ACTIVE_PHONE, subscription, null);
        if (type != null) {
            return (Integer.parseInt(type));
        } else {
            return getPhoneTypeFromNetworkType(subscription);
        }
    }

    private int getPhoneTypeFromNetworkType(int subscription) {
        // When the system property CURRENT_ACTIVE_PHONE, has not been set,
        // use the system property for default network type.
        // This is a fail safe, and can only happen at first boot.
        String mode = getTelephonyProperty("ro.telephony.default_network", subscription, null);
        if (mode != null) {
            return TelephonyManager.getPhoneType(Integer.parseInt(mode));
        }
        return TelephonyManager.PHONE_TYPE_NONE;
    }

    //
    //
    // Current Network
    //
    //

    /**
     * Returns the alphabetic name of current registered operator
     * for a particular subscription.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     * @param subscription
     */
    public String getNetworkOperatorName(int subscription) {

        return getTelephonyProperty(TelephonyProperties.PROPERTY_OPERATOR_ALPHA,
                subscription, "");
    }

    /**
     * Returns the numeric name (MCC+MNC) of current registered operator
     * for a particular subscription.
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     *
     * @param subscription
     */
    public String getNetworkOperator(int subscription) {

        return getTelephonyProperty(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC,
                subscription, "");
     }

    /**
     * Returns true if the device is considered roaming on the current
     * network for a subscription.
     * <p>
     * Availability: Only when user registered to a network.
     *
     * @param subscription
     */
    public boolean isNetworkRoaming(int subscription) {
        return "true".equals(getTelephonyProperty(TelephonyProperties.PROPERTY_OPERATOR_ISROAMING,
                subscription, null));
    }

    /**
     * Returns the ISO country code equivalent of the current registered
     * operator's MCC (Mobile Country Code).
     * <p>
     * Availability: Only when user is registered to a network. Result may be
     * unreliable on CDMA networks (use {@link #getPhoneType()} to determine if
     * on a CDMA network).
     *
     * @param subscription for which Network CountryIso is returned
     * @hide
     */
    public String getNetworkCountryIso(int subscription) {
        return getTelephonyProperty(TelephonyProperties.PROPERTY_OPERATOR_ISO_COUNTRY,
                subscription, "");
    }

    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device for data transmission for a subscription
     * @return the network type
     *
     * @param subscription for which network type is returned
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_EVDO_B
     * @see #NETWORK_TYPE_1xRTT
     * @see #NETWORK_TYPE_IDEN
     * @see #NETWORK_TYPE_LTE
     * @see #NETWORK_TYPE_EHRPD
     * @see #NETWORK_TYPE_HSPAP
     */
    public int getNetworkType(int subscription) {
        try {
            ITelephonyMSim iTelephony = getITelephonyMSim();
            if (iTelephony != null) {
                return iTelephony.getNetworkType(subscription);
            } else {
                // This can happen when the ITelephony interface is not up yet.
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns a constant indicating the radio technology (network type)
     * currently in use on the device for data transmission.
     * @return the network type
     *
     * @see #NETWORK_TYPE_UNKNOWN
     * @see #NETWORK_TYPE_GPRS
     * @see #NETWORK_TYPE_EDGE
     * @see #NETWORK_TYPE_UMTS
     * @see #NETWORK_TYPE_HSDPA
     * @see #NETWORK_TYPE_HSUPA
     * @see #NETWORK_TYPE_HSPA
     * @see #NETWORK_TYPE_CDMA
     * @see #NETWORK_TYPE_EVDO_0
     * @see #NETWORK_TYPE_EVDO_A
     * @see #NETWORK_TYPE_EVDO_B
     * @see #NETWORK_TYPE_1xRTT
     * @see #NETWORK_TYPE_IDEN
     * @see #NETWORK_TYPE_LTE
     * @see #NETWORK_TYPE_EHRPD
     * @see #NETWORK_TYPE_HSPAP
     * @see #NETWORK_TYPE_TD_SCDMA
     *
     * @hide
     */
    public int getDataNetworkType(int subscription) {
        try{
            ITelephonyMSim telephony = getITelephonyMSim();
            if (telephony != null) {
                return telephony.getDataNetworkType(subscription);
            } else {
                // This can happen when the ITelephonyMSim interface is not up yet.
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns the NETWORK_TYPE_xxxx for voice
     *
     * @hide
     */
    public int getVoiceNetworkType(int subscription) {
        try{
            ITelephonyMSim telephony = getITelephonyMSim();
            if (telephony != null) {
                return telephony.getVoiceNetworkType(subscription);
            } else {
                // This can happen when the ITelephonyMSim interface is not up yet.
                return TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } catch(RemoteException ex) {
            // This shouldn't happen in the normal case
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * Returns a string representation of the radio technology (network type)
     * currently in use on the device.
     * @param subscription for which network type is returned
     * @return the name of the radio technology
     *
     * @hide pending API council review
     */
    public String getNetworkTypeName(int subscription) {
        return TelephonyManager.getNetworkTypeName(getNetworkType(subscription));
    }

    //
    //
    // SIM Card
    //
    //

    /**
     * @return true if a ICC card is present for a subscription
     *
     * @param subscription for which icc card presence is checked
     */
    public boolean hasIccCard(int subscription) {

        try {
            return getITelephonyMSim().hasIccCard(subscription);
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return false;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return false;
        }
    }

    /**
     * Returns a constant indicating the state of the
     * device SIM card in a slot.
     *
     * @param slotId
     *
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     */
    public int getSimState(int slotId) {
        String prop =
            getTelephonyProperty(TelephonyProperties.PROPERTY_SIM_STATE, slotId, "");
        if ("ABSENT".equals(prop)) {
            return TelephonyManager.SIM_STATE_ABSENT;
        }
        else if ("PIN_REQUIRED".equals(prop)) {
            return TelephonyManager.SIM_STATE_PIN_REQUIRED;
        }
        else if ("PUK_REQUIRED".equals(prop)) {
            return TelephonyManager.SIM_STATE_PUK_REQUIRED;
        }
        else if ("NETWORK_LOCKED".equals(prop)) {
            return TelephonyManager.SIM_STATE_NETWORK_LOCKED;
        }
        else if ("READY".equals(prop)) {
            return TelephonyManager.SIM_STATE_READY;
        }
        else {
            return TelephonyManager.SIM_STATE_UNKNOWN;
        }
    }

    /**
     * Returns the MCC+MNC (mobile country code + mobile network code) of the
     * provider of the SIM. 5 or 6 decimal digits.
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     *
     * @param subscription for which SimOperator is returned
     * @hide
     */
    public String getSimOperator(int subscription) {
        return getTelephonyProperty(TelephonyProperties.PROPERTY_ICC_OPERATOR_NUMERIC,
                subscription, "");
    }

    /**
     * Returns the Service Provider Name (SPN).
     * <p>
     * Availability: SIM state must be {@link #SIM_STATE_READY}
     *
     * @see #getSimState
     *
     * @param subscription for which SimOperatorName is returned
     * @hide
     */
    public String getSimOperatorName(int subscription) {
        return getTelephonyProperty(TelephonyProperties.PROPERTY_ICC_OPERATOR_ALPHA,
                subscription, "");
    }

    /**
     * Returns the ISO country code equivalent for the SIM provider's country code.
     *
     * @param subscription for which SimCountryIso is returned
     * @hide
     */
    public String getSimCountryIso(int subscription) {
        return getTelephonyProperty(TelephonyProperties.PROPERTY_ICC_OPERATOR_ISO_COUNTRY,
                subscription, "");
    }

    /**
     * Returns the serial number for the given subscription, if applicable. Return null if it is
     * unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     */
    public String getSimSerialNumber(int subscription) {
        try {
            return getMSimSubscriberInfo().getIccSerialNumber(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link Phone#LTE_ON_CDMA_TRUE}
     *
     * @hide
     */
    public int getLteOnCdmaMode(int subscription) {
        try {
            return getITelephonyMSim().getLteOnCdmaMode(subscription);
        } catch (RemoteException ex) {
            // Assume no ICC card if remote exception which shouldn't happen
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return PhoneConstants.LTE_ON_CDMA_UNKNOWN;
        }
    }

    //
    //
    // Subscriber Info
    //
    //

    /**
     * Returns the unique subscriber ID, for example, the IMSI for a GSM phone
     * for a subscription.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param subscription whose subscriber id is returned
     */
    public String getSubscriberId(int subscription) {
        try {
            return getMSimSubscriberInfo().getSubscriberId(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the phone number string for line 1, for example, the MSISDN
     * for a GSM phone for a particular subscription. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param subscription whose phone number for line 1 is returned
     */
    public String getLine1Number(int subscription) {
        try {
            return getMSimSubscriberInfo().getLine1Number(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the alphabetic identifier associated with the line 1 number
     * for a subscription.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @param subscription whose alphabetic identifier associated with line 1 is returned
     * @hide
     * nobody seems to call this.
     */
    public String getLine1AlphaTag(int subscription) {
        try {
            return getMSimSubscriberInfo().getLine1AlphaTag(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the MSISDN string.
     * for a GSM phone. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param subscription for which msisdn is returned
     * @hide
     */
    public String getMsisdn(int subscription) {
        try {
            return getMSimSubscriberInfo().getMsisdn(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the voice mail number for a subscription.
     * Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @param subscription whose voice mail number is returned
     */
    public String getVoiceMailNumber(int subscription) {
        try {
            return getMSimSubscriberInfo().getVoiceMailNumber(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * Returns the complete voice mail number. Return null if it is unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#CALL_PRIVILEGED CALL_PRIVILEGED}
     *
     * @param subscription
     * @hide
     */
    public String getCompleteVoiceMailNumber(int subscription) {
        try {
            return getMSimSubscriberInfo().getCompleteVoiceMailNumber(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }


    /**
     * Returns the voice mail count for a subscription. Return 0 if unavailable.
     * <p>
     * Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @param subscription whose voice message count is returned
     * @hide
     */
    public int getVoiceMessageCount(int subscription) {
        try {
            return getITelephonyMSim().getVoiceMessageCount(subscription);
        } catch (RemoteException ex) {
            return 0;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return 0;
        }
    }

    /**
     * Retrieves the alphabetic identifier associated with the voice
     * mail number for a subscription.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     * @param subscription whose alphabetic identifier associated with the
     * voice mail number is returned
     */
    public String getVoiceMailAlphaTag(int subscription) {
        try {
            return getMSimSubscriberInfo().getVoiceMailAlphaTag(subscription);
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            // This could happen before phone restarts due to crashing
            return null;
        }
    }

    /**
     * @hide
     */
    protected IPhoneSubInfoMSim getMSimSubscriberInfo() {
        // get it each time because that process crashes a lot
        return IPhoneSubInfoMSim.Stub.asInterface(ServiceManager.getService("iphonesubinfo_msim"));
    }

    /**
     * Returns a constant indicating the call state (cellular) on the device
     * for a subscription.
     *
     * @param subscription whose call state is returned
     */
    public int getCallState(int subscription) {
        try {
            return getITelephonyMSim().getCallState(subscription);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return TelephonyManager.CALL_STATE_IDLE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
          return TelephonyManager.CALL_STATE_IDLE;
      }
    }

    /**
     * Returns a constant indicating the type of activity on a data connection
     * (cellular).
     *
     * @see #DATA_ACTIVITY_NONE
     * @see #DATA_ACTIVITY_IN
     * @see #DATA_ACTIVITY_OUT
     * @see #DATA_ACTIVITY_INOUT
     * @see #DATA_ACTIVITY_DORMANT
     */
    public int getDataActivity() {
        try {
            return getITelephonyMSim().getDataActivity();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return TelephonyManager.DATA_ACTIVITY_NONE;
        } catch (NullPointerException ex) {
          // the phone process is restarting.
          return TelephonyManager.DATA_ACTIVITY_NONE;
      }
    }

    /**
     * Returns a constant indicating the current data connection state
     * (cellular).
     *
     * @see #DATA_DISCONNECTED
     * @see #DATA_CONNECTING
     * @see #DATA_CONNECTED
     * @see #DATA_SUSPENDED
     */
    public int getDataState() {
        try {
            return getITelephonyMSim().getDataState();
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return TelephonyManager.DATA_DISCONNECTED;
        } catch (NullPointerException ex) {
            return TelephonyManager.DATA_DISCONNECTED;
        }
    }

    private ITelephonyMSim getITelephonyMSim() {
        return ITelephonyMSim.Stub.asInterface(ServiceManager.getService(
                Context.MSIM_TELEPHONY_SERVICE));
    }

    //
    //
    // PhoneStateListener
    //
    //

    /**
     * Registers a listener object to receive notification of changes
     * in specified telephony states.
     * <p>
     * To register a listener, pass a {@link PhoneStateListener}
     * and specify at least one telephony state of interest in
     * the events argument.
     *
     * At registration, and when a specified telephony state
     * changes, the telephony manager invokes the appropriate
     * callback method on the listener object and passes the
     * current (udpated) values.
     * <p>
     * To unregister a listener, pass the listener object and set the
     * events argument to
     * {@link PhoneStateListener#LISTEN_NONE LISTEN_NONE} (0).
     *
     * @param listener The {@link PhoneStateListener} object to register
     *                 (or unregister)
     * @param events The telephony state(s) of interest to the listener,
     *               as a bitwise-OR combination of {@link PhoneStateListener}
     *               LISTEN_ flags.
     */
    public void listen(PhoneStateListener listener, int events) {
        String pkgForDebug = sContext != null ? sContext.getPackageName() : "<unknown>";
        try {
            Boolean notifyNow = (getITelephonyMSim() != null);
            sRegistryMsim.listen(pkgForDebug, listener.callback, events, notifyNow,
                                           listener.mSubscription);
        } catch (RemoteException ex) {
            // system process dead
        } catch (NullPointerException ex) {
            // system process dead
        }
    }

    /**
     * Returns the CDMA ERI icon index to display for a subscription
     *
     * @hide
     */
    public int getCdmaEriIconIndex(int subscription) {
        try {
            return getITelephonyMSim().getCdmaEriIconIndex(subscription);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI icon mode for a subscription.
     * 0 - ON
     * 1 - FLASHING
     *
     * @hide
     */
    public int getCdmaEriIconMode(int subscription) {
        try {
            return getITelephonyMSim().getCdmaEriIconMode(subscription);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return -1;
        } catch (NullPointerException ex) {
            return -1;
        }
    }

    /**
     * Returns the CDMA ERI text, of a subscription
     *
     * @hide
     */
    public String getCdmaEriText(int subscription) {
        try {
            return getITelephonyMSim().getCdmaEriText(subscription);
        } catch (RemoteException ex) {
            // the phone process is restarting.
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Returns all observed cell information of the device.
     *
     * @return List of CellInfo or null if info unavailable
     * for subscription.
     *
     * <p>Requires Permission:
     * (@link android.Manifest.permission#ACCESS_COARSE_UPDATES}
     *
     * @hide pending API review
     */
    public List<CellInfo> getAllCellInfo() {
        try {
            return getITelephonyMSim().getAllCellInfo();
        } catch (RemoteException ex) {
            return null;
        } catch (NullPointerException ex) {
            return null;
        }
    }

    /**
     * Sets the telephony property with the value specified.
     *
     * @hide
     */
    public static void setTelephonyProperty(String property, int index, String value) {
        String propVal = "";
        String p[] = null;
        String prop = SystemProperties.get(property);

        if (value == null) {
            value = "";
        }

        if (prop != null) {
            p = prop.split(",");
        }

        if (index < 0) return;

        for (int i = 0; i < index; i++) {
            String str = "";
            if ((p != null) && (i < p.length)) {
                str = p[i];
            }
            propVal = propVal + str + ",";
        }

        propVal = propVal + value;
        if (p != null) {
            for (int i = index+1; i < p.length; i++) {
                propVal = propVal + "," + p[i];
            }
        }
        SystemProperties.set(property, propVal);
    }

    /**
     * Gets the telephony property.
     *
     * @hide
     */
    public static String getTelephonyProperty(String property, int index, String defaultVal) {
        String propVal = null;
        String prop = SystemProperties.get(property);

        if ((prop != null) && (prop.length() > 0)) {
            String values[] = prop.split(",");
            if ((index >= 0) && (index < values.length) && (values[index] != null)) {
                propVal = values[index];
            }
        }
        return propVal == null ? defaultVal : propVal;
    }

    /**
     * Returns Default subscription.
     * Returns default value 0, if default subscription is not available
     */
    public int getDefaultSubscription() {
        try {
            return getITelephonyMSim().getDefaultSubscription();
        } catch (RemoteException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        } catch (NullPointerException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        }
    }

    /**
     * Returns the designated data subscription.
     */
    public int getPreferredDataSubscription() {
        try {
            return getITelephonyMSim().getPreferredDataSubscription();
        } catch (RemoteException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        } catch (NullPointerException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        }
    }

    /**
     * Returns the default preferred data subscription value.
     */
    public int getDefaultDataSubscription() {
        try {
            return getITelephonyMSim().getDefaultDataSubscription();
        } catch (RemoteException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        } catch (NullPointerException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        }
    }

    /**
     * Sets the designated data subscription.
     * This API may be used by apps which needs to switch the DDS temporarily
     * like MMS app. Default data subscription setting will not be updated by
     * this API.
     */
    public boolean setPreferredDataSubscription(int subscription) {
        try {
            return getITelephonyMSim().setPreferredDataSubscription(subscription);
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * Sets the designated data subscription and updates the default data
     * subscription setting.
     */
    public boolean setDefaultDataSubscription(int subscription) {
        try {
            return getITelephonyMSim().setDefaultDataSubscription(subscription);
        } catch (RemoteException ex) {
            return false;
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * Returns the preferred voice subscription.
     */
    public int getPreferredVoiceSubscription() {
        try {
            return getITelephonyMSim().getPreferredVoiceSubscription();
        } catch (RemoteException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        } catch (NullPointerException ex) {
            return MSimConstants.DEFAULT_SUBSCRIPTION;
        }
    }

    /**
     * Convenience function for retrieving a value from the secure settings
     * value list as an integer.  Note that internally setting values are
     * always stored as strings; this function converts the string to an
     * integer for you.
     * <p>
     * This version does not take a default value.  If the setting has not
     * been set, or the string value is not a number,
     * it throws {@link SettingNotFoundException}.
     *
     * @param cr The ContentResolver to access.
     * @param name The name of the setting to retrieve.
     * @param index The index of the list
     *
     * @throws SettingNotFoundException Thrown if a setting by the given
     * name can't be found or the setting value is not an integer.
     *
     * @return The value at the given index of settings.
     * @hide
     */
    public static int getIntAtIndex(android.content.ContentResolver cr,
            String name, int index)
            throws android.provider.Settings.SettingNotFoundException {
        String v = android.provider.Settings.Global.getString(cr, name);
        if (v != null) {
            String valArray[] = v.split(",");
            if ((index >= 0) && (index < valArray.length) && (valArray[index] != null)) {
                try {
                    return Integer.parseInt(valArray[index]);
                } catch (NumberFormatException e) {
                    //Log.e(TAG, "Exception while parsing Integer: ", e);
                }
            }
        }
        throw new android.provider.Settings.SettingNotFoundException(name);
    }

    /**
     * Convenience function for updating settings value as coma separated
     * integer values. This will either create a new entry in the table if the
     * given name does not exist, or modify the value of the existing row
     * with that name.  Note that internally setting values are always
     * stored as strings, so this function converts the given value to a
     * string before storing it.
     *
     * @param cr The ContentResolver to access.
     * @param name The name of the setting to modify.
     * @param index The index of the list
     * @param value The new value for the setting to be added to the list.
     * @return true if the value was set, false on database errors
     * @hide
     */
    public static boolean putIntAtIndex(android.content.ContentResolver cr,
            String name, int index, int value) {
        String data = "";
        String valArray[] = null;
        String v = android.provider.Settings.Global.getString(cr, name);

        if (v != null) {
            valArray = v.split(",");
        }

        // Copy the elements from valArray till index
        for (int i = 0; i < index; i++) {
            String str = "";
            if ((valArray != null) && (i < valArray.length)) {
                str = valArray[i];
            }
            data = data + str + ",";
        }

        data = data + value;

        // Copy the remaining elements from valArray if any.
        if (valArray != null) {
            for (int i = index+1; i < valArray.length; i++) {
                data = data + "," + valArray[i];
            }
        }
        return android.provider.Settings.Global.putString(cr, name, data);
    }
}
