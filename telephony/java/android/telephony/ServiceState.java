/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.Rlog;

/**
 * Contains phone state and service related information.
 *
 * The following phone information is included in returned ServiceState:
 *
 * <ul>
 *   <li>Service state: IN_SERVICE, OUT_OF_SERVICE, EMERGENCY_ONLY, POWER_OFF
 *   <li>Roaming indicator
 *   <li>Operator name, short name and numeric id
 *   <li>Network selection mode
 * </ul>
 */
public class ServiceState implements Parcelable {

    static final String LOG_TAG = "PHONE";
    static final boolean DBG = true;

    /**
     * Normal operation condition, the phone is registered
     * with an operator either in home network or in roaming.
     */
    public static final int STATE_IN_SERVICE = 0;

    /**
     * Phone is not registered with any operator, the phone
     * can be currently searching a new operator to register to, or not
     * searching to registration at all, or registration is denied, or radio
     * signal is not available.
     */
    public static final int STATE_OUT_OF_SERVICE = 1;

    /**
     * The phone is registered and locked.  Only emergency numbers are allowed. {@more}
     */
    public static final int STATE_EMERGENCY_ONLY = 2;

    /**
     * Radio of telephony is explicitly powered off.
     */
    public static final int STATE_POWER_OFF = 3;

    /**
     * RIL level registration state values from ril.h
     * ((const char **)response)[0] is registration state 0-6,
     *              0 - Not registered, MT is not currently searching
     *                  a new operator to register
     *              1 - Registered, home network
     *              2 - Not registered, but MT is currently searching
     *                  a new operator to register
     *              3 - Registration denied
     *              4 - Unknown
     *              5 - Registered, roaming
     *             10 - Same as 0, but indicates that emergency calls
     *                  are enabled.
     *             12 - Same as 2, but indicates that emergency calls
     *                  are enabled.
     *             13 - Same as 3, but indicates that emergency calls
     *                  are enabled.
     *             14 - Same as 4, but indicates that emergency calls
     *                  are enabled.
     * @hide
     */
    public static final int RIL_REG_STATE_NOT_REG = 0;
    /** @hide */
    public static final int RIL_REG_STATE_HOME = 1;
    /** @hide */
    public static final int RIL_REG_STATE_SEARCHING = 2;
    /** @hide */
    public static final int RIL_REG_STATE_DENIED = 3;
    /** @hide */
    public static final int RIL_REG_STATE_UNKNOWN = 4;
    /** @hide */
    public static final int RIL_REG_STATE_ROAMING = 5;
    /** @hide */
    public static final int RIL_REG_STATE_NOT_REG_EMERGENCY_CALL_ENABLED = 10;
    /** @hide */
    public static final int RIL_REG_STATE_SEARCHING_EMERGENCY_CALL_ENABLED = 12;
    /** @hide */
    public static final int RIL_REG_STATE_DENIED_EMERGENCY_CALL_ENABLED = 13;
    /** @hide */
    public static final int RIL_REG_STATE_UNKNOWN_EMERGENCY_CALL_ENABLED = 14;

    /**
     * Available radio technologies for GSM, UMTS and CDMA.
     * Duplicates the constants from hardware/radio/include/ril.h
     * This should only be used by agents working with the ril.  Others
     * should use the equivalent TelephonyManager.NETWORK_TYPE_*
     */
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_UNKNOWN = 0;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_GPRS = 1;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_EDGE = 2;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_UMTS = 3;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_IS95A = 4;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_IS95B = 5;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_1xRTT = 6;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_EVDO_0 = 7;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_EVDO_A = 8;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_HSDPA = 9;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_HSUPA = 10;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_HSPA = 11;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_EVDO_B = 12;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_EHRPD = 13;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_LTE = 14;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_HSPAP = 15;
    /** @hide */
    public static final int RIL_RADIO_TECHNOLOGY_DCHSPAP = 30;
    /**
     * GSM radio technology only supports voice. It does not support data.
     * @hide
     */
    public static final int RIL_RADIO_TECHNOLOGY_GSM = 16;
    /**
     * TD-SCDMA
     * @hide
     */
    public static final int RIL_RADIO_TECHNOLOGY_TD_SCDMA = 17;

    /**
     * Available registration states for GSM, UMTS and CDMA.
     */
    /** @hide */
    public static final int REGISTRATION_STATE_NOT_REGISTERED_AND_NOT_SEARCHING = 0;
    /** @hide */
    public static final int REGISTRATION_STATE_HOME_NETWORK = 1;
    /** @hide */
    public static final int REGISTRATION_STATE_NOT_REGISTERED_AND_SEARCHING = 2;
    /** @hide */
    public static final int REGISTRATION_STATE_REGISTRATION_DENIED = 3;
    /** @hide */
    public static final int REGISTRATION_STATE_UNKNOWN = 4;
    /** @hide */
    public static final int REGISTRATION_STATE_ROAMING = 5;

    private int mVoiceRegState = STATE_OUT_OF_SERVICE;
    private int mDataRegState = STATE_OUT_OF_SERVICE;
    private boolean mRoaming;
    private String mOperatorAlphaLong;
    private String mOperatorAlphaShort;
    private String mOperatorNumeric;
    private boolean mIsManualNetworkSelection;

    private boolean mIsEmergencyOnly;

    private int mRilVoiceRadioTechnology;
    private int mRilDataRadioTechnology;

    private boolean mCssIndicator;
    private int mNetworkId;
    private int mSystemId;
    private int mCdmaRoamingIndicator;
    private int mCdmaDefaultRoamingIndicator;
    private int mCdmaEriIconIndex;
    private int mCdmaEriIconMode;

    /**
     * Create a new ServiceState from a intent notifier Bundle
     *
     * This method is used by PhoneStateIntentReceiver and maybe by
     * external applications.
     *
     * @param m Bundle from intent notifier
     * @return newly created ServiceState
     * @hide
     */
    public static ServiceState newFromBundle(Bundle m) {
        ServiceState ret;
        ret = new ServiceState();
        ret.setFromNotifierBundle(m);
        return ret;
    }

    /**
     * Empty constructor
     */
    public ServiceState() {
    }

    /**
     * Copy constructors
     *
     * @param s Source service state
     */
    public ServiceState(ServiceState s) {
        copyFrom(s);
    }

    protected void copyFrom(ServiceState s) {
        mVoiceRegState = s.mVoiceRegState;
        mDataRegState = s.mDataRegState;
        mRoaming = s.mRoaming;
        mOperatorAlphaLong = s.mOperatorAlphaLong;
        mOperatorAlphaShort = s.mOperatorAlphaShort;
        mOperatorNumeric = s.mOperatorNumeric;
        mIsManualNetworkSelection = s.mIsManualNetworkSelection;
        mRilVoiceRadioTechnology = s.mRilVoiceRadioTechnology;
        mRilDataRadioTechnology = s.mRilDataRadioTechnology;
        mCssIndicator = s.mCssIndicator;
        mNetworkId = s.mNetworkId;
        mSystemId = s.mSystemId;
        mCdmaRoamingIndicator = s.mCdmaRoamingIndicator;
        mCdmaDefaultRoamingIndicator = s.mCdmaDefaultRoamingIndicator;
        mCdmaEriIconIndex = s.mCdmaEriIconIndex;
        mCdmaEriIconMode = s.mCdmaEriIconMode;
        mIsEmergencyOnly = s.mIsEmergencyOnly;
    }

    /**
     * Construct a ServiceState object from the given parcel.
     */
    public ServiceState(Parcel in) {
        mVoiceRegState = in.readInt();
        mDataRegState = in.readInt();
        mRoaming = in.readInt() != 0;
        mOperatorAlphaLong = in.readString();
        mOperatorAlphaShort = in.readString();
        mOperatorNumeric = in.readString();
        mIsManualNetworkSelection = in.readInt() != 0;
        mRilVoiceRadioTechnology = in.readInt();
        mRilDataRadioTechnology = in.readInt();
        mCssIndicator = (in.readInt() != 0);
        mNetworkId = in.readInt();
        mSystemId = in.readInt();
        mCdmaRoamingIndicator = in.readInt();
        mCdmaDefaultRoamingIndicator = in.readInt();
        mCdmaEriIconIndex = in.readInt();
        mCdmaEriIconMode = in.readInt();
        mIsEmergencyOnly = in.readInt() != 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mVoiceRegState);
        out.writeInt(mDataRegState);
        out.writeInt(mRoaming ? 1 : 0);
        out.writeString(mOperatorAlphaLong);
        out.writeString(mOperatorAlphaShort);
        out.writeString(mOperatorNumeric);
        out.writeInt(mIsManualNetworkSelection ? 1 : 0);
        out.writeInt(mRilVoiceRadioTechnology);
        out.writeInt(mRilDataRadioTechnology);
        out.writeInt(mCssIndicator ? 1 : 0);
        out.writeInt(mNetworkId);
        out.writeInt(mSystemId);
        out.writeInt(mCdmaRoamingIndicator);
        out.writeInt(mCdmaDefaultRoamingIndicator);
        out.writeInt(mCdmaEriIconIndex);
        out.writeInt(mCdmaEriIconMode);
        out.writeInt(mIsEmergencyOnly ? 1 : 0);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ServiceState> CREATOR =
            new Parcelable.Creator<ServiceState>() {
        public ServiceState createFromParcel(Parcel in) {
            return new ServiceState(in);
        }

        public ServiceState[] newArray(int size) {
            return new ServiceState[size];
        }
    };

    /**
     * Get current voice service state
     */
    public int getState() {
        return getVoiceRegState();
    }

    /**
     * Get current voice service state
     *
     * @see #STATE_IN_SERVICE
     * @see #STATE_OUT_OF_SERVICE
     * @see #STATE_EMERGENCY_ONLY
     * @see #STATE_POWER_OFF
     *
     * @hide
     */
    public int getVoiceRegState() {
        return mVoiceRegState;
    }

    /**
     * Get current data service state
     *
     * @see #STATE_IN_SERVICE
     * @see #STATE_OUT_OF_SERVICE
     * @see #STATE_EMERGENCY_ONLY
     * @see #STATE_POWER_OFF
     *
     * @hide
     */
    public int getDataRegState() {
        return mDataRegState;
    }

    /**
     * Get current roaming indicator of phone
     * (note: not just decoding from TS 27.007 7.2)
     *
     * @return true if TS 27.007 7.2 roaming is true
     *              and ONS is different from SPN
     *
     */
    public boolean getRoaming() {
        return mRoaming;
    }

    /**
     * @hide
     */
    public boolean isEmergencyOnly() {
        return mIsEmergencyOnly;
    }

    /**
     * @hide
     */
    public int getCdmaRoamingIndicator(){
        return this.mCdmaRoamingIndicator;
    }

    /**
     * @hide
     */
    public int getCdmaDefaultRoamingIndicator(){
        return this.mCdmaDefaultRoamingIndicator;
    }

    /**
     * @hide
     */
    public int getCdmaEriIconIndex() {
        return this.mCdmaEriIconIndex;
    }

    /**
     * @hide
     */
    public int getCdmaEriIconMode() {
        return this.mCdmaEriIconMode;
    }

    /**
     * Get current registered operator name in long alphanumeric format.
     *
     * In GSM/UMTS, long format can be up to 16 characters long.
     * In CDMA, returns the ERI text, if set. Otherwise, returns the ONS.
     *
     * @return long name of operator, null if unregistered or unknown
     */
    public String getOperatorAlphaLong() {
        return mOperatorAlphaLong;
    }

    /**
     * Get current registered operator name in short alphanumeric format.
     *
     * In GSM/UMTS, short format can be up to 8 characters long.
     *
     * @return short name of operator, null if unregistered or unknown
     */
    public String getOperatorAlphaShort() {
        return mOperatorAlphaShort;
    }

    /**
     * Get current registered operator numeric id.
     *
     * In GSM/UMTS, numeric format is 3 digit country code plus 2 or 3 digit
     * network code.
     *
     * @return numeric format of operator, null if unregistered or unknown
     */
    /*
     * The country code can be decoded using
     * {@link com.android.internal.telephony.MccTable#countryCodeForMcc(int)}.
     */
    public String getOperatorNumeric() {
        return mOperatorNumeric;
    }

    /**
     * Get current network selection mode.
     *
     * @return true if manual mode, false if automatic mode
     */
    public boolean getIsManualSelection() {
        return mIsManualNetworkSelection;
    }

    @Override
    public int hashCode() {
        return ((mVoiceRegState * 31)
                + (mDataRegState * 37)
                + (mRoaming ? 1 : 0)
                + (mIsManualNetworkSelection ? 1 : 0)
                + ((null == mOperatorAlphaLong) ? 0 : mOperatorAlphaLong.hashCode())
                + ((null == mOperatorAlphaShort) ? 0 : mOperatorAlphaShort.hashCode())
                + ((null == mOperatorNumeric) ? 0 : mOperatorNumeric.hashCode())
                + mCdmaRoamingIndicator
                + mCdmaDefaultRoamingIndicator
                + (mIsEmergencyOnly ? 1 : 0));
    }

    @Override
    public boolean equals (Object o) {
        ServiceState s;

        try {
            s = (ServiceState) o;
        } catch (ClassCastException ex) {
            return false;
        }

        if (o == null) {
            return false;
        }

        return (mVoiceRegState == s.mVoiceRegState
                && mDataRegState == s.mDataRegState
                && mRoaming == s.mRoaming
                && mIsManualNetworkSelection == s.mIsManualNetworkSelection
                && equalsHandlesNulls(mOperatorAlphaLong, s.mOperatorAlphaLong)
                && equalsHandlesNulls(mOperatorAlphaShort, s.mOperatorAlphaShort)
                && equalsHandlesNulls(mOperatorNumeric, s.mOperatorNumeric)
                && equalsHandlesNulls(mRilVoiceRadioTechnology, s.mRilVoiceRadioTechnology)
                && equalsHandlesNulls(mRilDataRadioTechnology, s.mRilDataRadioTechnology)
                && equalsHandlesNulls(mCssIndicator, s.mCssIndicator)
                && equalsHandlesNulls(mNetworkId, s.mNetworkId)
                && equalsHandlesNulls(mSystemId, s.mSystemId)
                && equalsHandlesNulls(mCdmaRoamingIndicator, s.mCdmaRoamingIndicator)
                && equalsHandlesNulls(mCdmaDefaultRoamingIndicator,
                        s.mCdmaDefaultRoamingIndicator)
                && mIsEmergencyOnly == s.mIsEmergencyOnly);
    }

    /**
     * Convert radio technology to String
     *
     * @param radioTechnology
     * @return String representation of the RAT
     *
     * @hide
     */
    public static String rilRadioTechnologyToString(int rt) {
        String rtString;

        switch(rt) {
            case RIL_RADIO_TECHNOLOGY_UNKNOWN:
                rtString = "Unknown";
                break;
            case RIL_RADIO_TECHNOLOGY_GPRS:
                rtString = "GPRS";
                break;
            case RIL_RADIO_TECHNOLOGY_EDGE:
                rtString = "EDGE";
                break;
            case RIL_RADIO_TECHNOLOGY_UMTS:
                rtString = "UMTS";
                break;
            case RIL_RADIO_TECHNOLOGY_IS95A:
                rtString = "CDMA-IS95A";
                break;
            case RIL_RADIO_TECHNOLOGY_IS95B:
                rtString = "CDMA-IS95B";
                break;
            case RIL_RADIO_TECHNOLOGY_1xRTT:
                rtString = "1xRTT";
                break;
            case RIL_RADIO_TECHNOLOGY_EVDO_0:
                rtString = "EvDo-rev.0";
                break;
            case RIL_RADIO_TECHNOLOGY_EVDO_A:
                rtString = "EvDo-rev.A";
                break;
            case RIL_RADIO_TECHNOLOGY_HSDPA:
                rtString = "HSDPA";
                break;
            case RIL_RADIO_TECHNOLOGY_HSUPA:
                rtString = "HSUPA";
                break;
            case RIL_RADIO_TECHNOLOGY_HSPA:
                rtString = "HSPA";
                break;
            case RIL_RADIO_TECHNOLOGY_EVDO_B:
                rtString = "EvDo-rev.B";
                break;
            case RIL_RADIO_TECHNOLOGY_EHRPD:
                rtString = "eHRPD";
                break;
            case RIL_RADIO_TECHNOLOGY_LTE:
                rtString = "LTE";
                break;
            case RIL_RADIO_TECHNOLOGY_HSPAP:
            case RIL_RADIO_TECHNOLOGY_DCHSPAP:
                rtString = "HSPAP";
                break;
            case RIL_RADIO_TECHNOLOGY_GSM:
                rtString = "GSM";
                break;
            case RIL_RADIO_TECHNOLOGY_TD_SCDMA:
                rtString = "TD-SCDMA";
                break;
            default:
                rtString = "Unexpected";
                Rlog.w(LOG_TAG, "Unexpected radioTechnology=" + rt);
                break;
        }
        return rtString;
    }

    @Override
    public String toString() {
        String radioTechnology = rilRadioTechnologyToString(mRilVoiceRadioTechnology);
        String dataRadioTechnology = rilRadioTechnologyToString(mRilDataRadioTechnology);

        return (mVoiceRegState + " " + mDataRegState + " " + (mRoaming ? "roaming" : "home")
                + " " + mOperatorAlphaLong
                + " " + mOperatorAlphaShort
                + " " + mOperatorNumeric
                + " " + (mIsManualNetworkSelection ? "(manual)" : "")
                + " " + radioTechnology
                + " " + dataRadioTechnology
                + " " + (mCssIndicator ? "CSS supported" : "CSS not supported")
                + " " + mNetworkId
                + " " + mSystemId
                + " RoamInd=" + mCdmaRoamingIndicator
                + " DefRoamInd=" + mCdmaDefaultRoamingIndicator
                + " EmergOnly=" + mIsEmergencyOnly);
    }

    private void setNullState(int state) {
        if (DBG) Rlog.d(LOG_TAG, "[ServiceState] setNullState=" + state);
        mVoiceRegState = state;
        mDataRegState = state;
        mRoaming = false;
        mOperatorAlphaLong = null;
        mOperatorAlphaShort = null;
        mOperatorNumeric = null;
        mIsManualNetworkSelection = false;
        mRilVoiceRadioTechnology = 0;
        mRilDataRadioTechnology = 0;
        mCssIndicator = false;
        mNetworkId = -1;
        mSystemId = -1;
        mCdmaRoamingIndicator = -1;
        mCdmaDefaultRoamingIndicator = -1;
        mCdmaEriIconIndex = -1;
        mCdmaEriIconMode = -1;
        mIsEmergencyOnly = false;
    }

    public void setStateOutOfService() {
        setNullState(STATE_OUT_OF_SERVICE);
    }

    public void setStateOff() {
        setNullState(STATE_POWER_OFF);
    }

    public void setState(int state) {
        setVoiceRegState(state);
        if (DBG) Rlog.e(LOG_TAG, "[ServiceState] setState deprecated use setVoiceRegState()");
    }

    /** @hide */
    public void setVoiceRegState(int state) {
        mVoiceRegState = state;
        if (DBG) Rlog.d(LOG_TAG, "[ServiceState] setVoiceRegState=" + mVoiceRegState);
    }

    /** @hide */
    public void setDataRegState(int state) {
        mDataRegState = state;
        if (DBG) Rlog.d(LOG_TAG, "[ServiceState] setDataRegState=" + mDataRegState);
    }

    public void setRoaming(boolean roaming) {
        mRoaming = roaming;
    }


    /**
     * @hide
     */
    public void setEmergencyOnly(boolean emergencyOnly) {
        mIsEmergencyOnly = emergencyOnly;
    }

    /**
     * @hide
     */
    public void setCdmaRoamingIndicator(int roaming) {
        this.mCdmaRoamingIndicator = roaming;
    }

    /**
     * @hide
     */
    public void setCdmaDefaultRoamingIndicator (int roaming) {
        this.mCdmaDefaultRoamingIndicator = roaming;
    }

    /**
     * @hide
     */
    public void setCdmaEriIconIndex(int index) {
        this.mCdmaEriIconIndex = index;
    }

    /**
     * @hide
     */
    public void setCdmaEriIconMode(int mode) {
        this.mCdmaEriIconMode = mode;
    }

    public void setOperatorName(String longName, String shortName, String numeric) {
        mOperatorAlphaLong = longName;
        mOperatorAlphaShort = shortName;
        mOperatorNumeric = numeric;
    }

    /**
     * In CDMA, mOperatorAlphaLong can be set from the ERI text.
     * This is done from the CDMAPhone and not from the CdmaServiceStateTracker.
     *
     * @hide
     */
    public void setOperatorAlphaLong(String longName) {
        mOperatorAlphaLong = longName;
    }

    public void setIsManualSelection(boolean isManual) {
        mIsManualNetworkSelection = isManual;
    }

    /**
     * Test whether two objects hold the same data values or both are null.
     *
     * @param a first obj
     * @param b second obj
     * @return true if two objects equal or both are null
     */
    private static boolean equalsHandlesNulls (Object a, Object b) {
        return (a == null) ? (b == null) : a.equals (b);
    }

    /**
     * Set ServiceState based on intent notifier map.
     *
     * @param m intent notifier map
     * @hide
     */
    private void setFromNotifierBundle(Bundle m) {
        mVoiceRegState = m.getInt("voiceRegState");
        mDataRegState = m.getInt("dataRegState");
        mRoaming = m.getBoolean("roaming");
        mOperatorAlphaLong = m.getString("operator-alpha-long");
        mOperatorAlphaShort = m.getString("operator-alpha-short");
        mOperatorNumeric = m.getString("operator-numeric");
        mIsManualNetworkSelection = m.getBoolean("manual");
        mRilVoiceRadioTechnology = m.getInt("radioTechnology");
        mRilDataRadioTechnology = m.getInt("dataRadioTechnology");
        mCssIndicator = m.getBoolean("cssIndicator");
        mNetworkId = m.getInt("networkId");
        mSystemId = m.getInt("systemId");
        mCdmaRoamingIndicator = m.getInt("cdmaRoamingIndicator");
        mCdmaDefaultRoamingIndicator = m.getInt("cdmaDefaultRoamingIndicator");
        mIsEmergencyOnly = m.getBoolean("emergencyOnly");
    }

    /**
     * Set intent notifier Bundle based on service state.
     *
     * @param m intent notifier Bundle
     * @hide
     */
    public void fillInNotifierBundle(Bundle m) {
        m.putInt("voiceRegState", mVoiceRegState);
        m.putInt("dataRegState", mDataRegState);
        m.putBoolean("roaming", Boolean.valueOf(mRoaming));
        m.putString("operator-alpha-long", mOperatorAlphaLong);
        m.putString("operator-alpha-short", mOperatorAlphaShort);
        m.putString("operator-numeric", mOperatorNumeric);
        m.putBoolean("manual", Boolean.valueOf(mIsManualNetworkSelection));
        m.putInt("radioTechnology", mRilVoiceRadioTechnology);
        m.putInt("dataRadioTechnology", mRilDataRadioTechnology);
        m.putBoolean("cssIndicator", mCssIndicator);
        m.putInt("networkId", mNetworkId);
        m.putInt("systemId", mSystemId);
        m.putInt("cdmaRoamingIndicator", mCdmaRoamingIndicator);
        m.putInt("cdmaDefaultRoamingIndicator", mCdmaDefaultRoamingIndicator);
        m.putBoolean("emergencyOnly", Boolean.valueOf(mIsEmergencyOnly));
    }

    /** @hide */
    public void setRilVoiceRadioTechnology(int rt) {
        this.mRilVoiceRadioTechnology = rt;
    }

    /** @hide */
    public void setRilDataRadioTechnology(int rt) {
        this.mRilDataRadioTechnology = rt;
        if (DBG) Rlog.d(LOG_TAG, "[ServiceState] setDataRadioTechnology=" + mRilDataRadioTechnology);
    }

    /** @hide */
    public void setCssIndicator(int css) {
        this.mCssIndicator = (css != 0);
    }

    /** @hide */
    public void setSystemAndNetworkId(int systemId, int networkId) {
        this.mSystemId = systemId;
        this.mNetworkId = networkId;
    }

    /** @hide */
    public int getRilVoiceRadioTechnology() {
        return this.mRilVoiceRadioTechnology;
    }
    /** @hide */
    public int getRilDataRadioTechnology() {
        return this.mRilDataRadioTechnology;
    }
    /**
     * @hide
     * @Deprecated to be removed Q3 2013 use {@link #getRilDataRadioTechnology} or
     * {@link #getRilVoiceRadioTechnology}
     */
    public int getRadioTechnology() {
        Rlog.e(LOG_TAG, "ServiceState.getRadioTechnology() DEPRECATED will be removed *******");
        return getRilDataRadioTechnology();
    }

    private int rilRadioTechnologyToNetworkType(int rt) {
        switch(rt) {
        case ServiceState.RIL_RADIO_TECHNOLOGY_GPRS:
            return TelephonyManager.NETWORK_TYPE_GPRS;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EDGE:
            return TelephonyManager.NETWORK_TYPE_EDGE;
        case ServiceState.RIL_RADIO_TECHNOLOGY_UMTS:
            return TelephonyManager.NETWORK_TYPE_UMTS;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA:
            return TelephonyManager.NETWORK_TYPE_HSDPA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA:
            return TelephonyManager.NETWORK_TYPE_HSUPA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSPA:
            return TelephonyManager.NETWORK_TYPE_HSPA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_IS95A:
        case ServiceState.RIL_RADIO_TECHNOLOGY_IS95B:
            return TelephonyManager.NETWORK_TYPE_CDMA;
        case ServiceState.RIL_RADIO_TECHNOLOGY_1xRTT:
            return TelephonyManager.NETWORK_TYPE_1xRTT;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_0:
            return TelephonyManager.NETWORK_TYPE_EVDO_0;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_A:
            return TelephonyManager.NETWORK_TYPE_EVDO_A;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EVDO_B:
            return TelephonyManager.NETWORK_TYPE_EVDO_B;
        case ServiceState.RIL_RADIO_TECHNOLOGY_EHRPD:
            return TelephonyManager.NETWORK_TYPE_EHRPD;
        case ServiceState.RIL_RADIO_TECHNOLOGY_LTE:
            return TelephonyManager.NETWORK_TYPE_LTE;
        case ServiceState.RIL_RADIO_TECHNOLOGY_HSPAP:
        case ServiceState.RIL_RADIO_TECHNOLOGY_DCHSPAP:
            return TelephonyManager.NETWORK_TYPE_HSPAP;
        case ServiceState.RIL_RADIO_TECHNOLOGY_GSM:
            return TelephonyManager.NETWORK_TYPE_GSM;
        case ServiceState.RIL_RADIO_TECHNOLOGY_TD_SCDMA:
            return TelephonyManager.NETWORK_TYPE_TD_SCDMA;
        default:
            return TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    /**
     * @Deprecated to be removed Q3 2013 use {@link #getVoiceNetworkType}
     * @hide
     */
    public int getNetworkType() {
        Rlog.e(LOG_TAG, "ServiceState.getNetworkType() DEPRECATED will be removed *******");
        return rilRadioTechnologyToNetworkType(mRilVoiceRadioTechnology);
    }

    /** @hide */
    public int getDataNetworkType() {
        return rilRadioTechnologyToNetworkType(mRilDataRadioTechnology);
    }

    /** @hide */
    public int getVoiceNetworkType() {
        return rilRadioTechnologyToNetworkType(mRilVoiceRadioTechnology);
    }

    /** @hide */
    public int getCssIndicator() {
        return this.mCssIndicator ? 1 : 0;
    }

    /** @hide */
    public int getNetworkId() {
        return this.mNetworkId;
    }

    /** @hide */
    public int getSystemId() {
        return this.mSystemId;
    }

    /** @hide */
    public static boolean isGsm(int radioTechnology) {
        return radioTechnology == RIL_RADIO_TECHNOLOGY_GPRS
                || radioTechnology == RIL_RADIO_TECHNOLOGY_EDGE
                || radioTechnology == RIL_RADIO_TECHNOLOGY_UMTS
                || radioTechnology == RIL_RADIO_TECHNOLOGY_HSDPA
                || radioTechnology == RIL_RADIO_TECHNOLOGY_HSUPA
                || radioTechnology == RIL_RADIO_TECHNOLOGY_HSPA
                || radioTechnology == RIL_RADIO_TECHNOLOGY_LTE
                || radioTechnology == RIL_RADIO_TECHNOLOGY_HSPAP
                || radioTechnology == RIL_RADIO_TECHNOLOGY_DCHSPAP
                || radioTechnology == RIL_RADIO_TECHNOLOGY_GSM
                || radioTechnology == RIL_RADIO_TECHNOLOGY_TD_SCDMA;
    }

    /** @hide */
    public static boolean isCdma(int radioTechnology) {
        return radioTechnology == RIL_RADIO_TECHNOLOGY_IS95A
                || radioTechnology == RIL_RADIO_TECHNOLOGY_IS95B
                || radioTechnology == RIL_RADIO_TECHNOLOGY_1xRTT
                || radioTechnology == RIL_RADIO_TECHNOLOGY_EVDO_0
                || radioTechnology == RIL_RADIO_TECHNOLOGY_EVDO_A
                || radioTechnology == RIL_RADIO_TECHNOLOGY_EVDO_B
                || radioTechnology == RIL_RADIO_TECHNOLOGY_EHRPD;
    }
}
