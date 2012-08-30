/*
 * Copyright (C) 2012 The CyanogenMod Project
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

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.telephony.SmsMessage;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.cdma.CdmaInformationRecords;

import java.util.ArrayList;

/**
 * Custom Qualcomm No SimReady RIL using the latest Uicc stack
 *
 * {@hide}
 */
public class QualcommSharedRIL extends RIL implements CommandsInterface {
    protected HandlerThread mIccThread;
    protected IccHandler mIccHandler;
    protected String mAid;
    protected boolean mUSIM = false;
    protected String[] mLastDataIface = new String[20];
    boolean RILJ_LOGV = true;
    boolean RILJ_LOGD = true;

    private final int RIL_INT_RADIO_OFF = 0;
    private final int RIL_INT_RADIO_UNAVALIABLE = 1;
    private final int RIL_INT_RADIO_ON = 2;
    private final int RIL_INT_RADIO_ON_NG = 10;
    private final int RIL_INT_RADIO_ON_HTC = 13;


    public QualcommSharedRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
        mSetPreferredNetworkType = -1;
        mQANElements = 5;
    }

    @Override public void
    supplyIccPin2(String pin, Message result) {
        supplyIccPin2ForApp(pin, mAid, result);
    }

    @Override public void
    changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, mAid, result);
    }

    @Override public void
    supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, mAid, result);
    }

    @Override public void
    supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, mAid, result);
    }

    @Override
    public void
    queryFacilityLock(String facility, String password, int serviceClass,
                            Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, mAid, response);
    }

    @Override
    public void
    setFacilityLock (String facility, boolean lockState, String password,
                        int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, mAid, response);
    }

    @Override
    public void
    getIMSI(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_IMSI, result);

        rr.mp.writeInt(1);
        rr.mp.writeString(mAid);

        if (RILJ_LOGD) riljLog(rr.serialString() +
                              "> getIMSI:RIL_REQUEST_GET_IMSI " +
                              RIL_REQUEST_GET_IMSI +
                              " aid: " + mAid +
                              " " + requestToString(rr.mRequest));

        send(rr);
    }

    @Override
    public void
    iccIO (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SIM_IO, result);

        if (mUSIM)
            path = path.replaceAll("7F20$","7FFF");

        rr.mp.writeInt(command);
        rr.mp.writeInt(fileid);
        rr.mp.writeString(path);
        rr.mp.writeInt(p1);
        rr.mp.writeInt(p2);
        rr.mp.writeInt(p3);
        rr.mp.writeString(data);
        rr.mp.writeString(pin2);
        rr.mp.writeString(mAid);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> iccIO: "
                    + " aid: " + mAid + " "
                    + requestToString(rr.mRequest)
                    + " 0x" + Integer.toHexString(command)
                    + " 0x" + Integer.toHexString(fileid) + " "
                    + " path: " + path + ","
                    + p1 + "," + p2 + "," + p3);

        send(rr);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplication ca;

        IccCardStatus status = new IccCardStatus();
        status.setCardState(p.readInt());
        status.setUniversalPinState(p.readInt());
        status.setGsmUmtsSubscriptionAppIndex(p.readInt());
        status.setCdmaSubscriptionAppIndex(p.readInt());

        status.setImsSubscriptionAppIndex(p.readInt());

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        status.setNumApplications(numApplications);

        for (int i = 0; i < numApplications; i++) {
            ca = new IccCardApplication();
            ca.app_type = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            ca.aid = p.readString();
            ca.app_label = p.readString();
            ca.pin1_replaced = p.readInt();
            ca.pin1 = ca.PinStateFromRILInt(p.readInt());
            ca.pin2 = ca.PinStateFromRILInt(p.readInt());
            p.readInt(); //remaining_count_pin1
            p.readInt(); //remaining_count_puk1
            p.readInt(); //remaining_count_pin2
            p.readInt(); //remaining_count_puk2
            status.addApplication(ca);
        }
        int appIndex = -1;
        if (mPhoneType == RILConstants.CDMA_PHONE) {
            appIndex = status.getCdmaSubscriptionAppIndex();
            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
        } else {
            appIndex = status.getGsmUmtsSubscriptionAppIndex();
            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
        }

        if (numApplications > 0) {
            IccCardApplication application = status.getApplication(appIndex);
            mAid = application.aid;
            mUSIM = application.app_type
                      == IccCardApplication.AppType.APPTYPE_USIM;
            mSetPreferredNetworkType = mPreferredNetworkType;

            if (TextUtils.isEmpty(mAid))
               mAid = "";
            Log.d(LOG_TAG, "mAid " + mAid);
        }

        return status;
    }

    @Override
    protected DataCallState getDataCallState(Parcel p, int version) {
        DataCallState dataCall = new DataCallState();

        boolean oldRil = needsOldRilFeature("datacall");

        if (!oldRil && version < 5) {
            return super.getDataCallState(p, version);
        } else if (!oldRil) {
            dataCall.version = version;
            dataCall.status = p.readInt();
            dataCall.suggestedRetryTime = p.readInt();
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = p.readString();
            if ((dataCall.status == DataConnection.FailCause.NONE.getErrorCode()) &&
                    TextUtils.isEmpty(dataCall.ifname) && dataCall.active != 0) {
              throw new RuntimeException("getDataCallState, no ifname");
            }
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            String dnses = p.readString();
            if (!TextUtils.isEmpty(dnses)) {
                dataCall.dnses = dnses.split(" ");
            }
            String gateways = p.readString();
            if (!TextUtils.isEmpty(gateways)) {
                dataCall.gateways = gateways.split(" ");
            }
        } else {
            dataCall.version = 4; // was dataCall.version = version;
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = mLastDataIface[dataCall.cid];
            p.readString(); // skip APN

            if (TextUtils.isEmpty(dataCall.ifname)) {
                dataCall.ifname = mLastDataIface[0];
            }

            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            p.readInt(); // RadioTechnology
            p.readInt(); // inactiveReason

            dataCall.dnses = new String[2];
            dataCall.dnses[0] = SystemProperties.get("net."+dataCall.ifname+".dns1");
            dataCall.dnses[1] = SystemProperties.get("net."+dataCall.ifname+".dns2");
        }

        return dataCall;
    }

    @Override
    protected Object
    responseSetupDataCall(Parcel p) {
        DataCallState dataCall;

        boolean oldRil = needsOldRilFeature("datacall");

        if (!oldRil)
           return super.responseSetupDataCall(p);

        dataCall = new DataCallState();
        dataCall.version = 4;

        dataCall.cid = 0; // Integer.parseInt(p.readString());
        p.readString();
        dataCall.ifname = p.readString();
        if ((dataCall.status == DataConnection.FailCause.NONE.getErrorCode()) &&
             TextUtils.isEmpty(dataCall.ifname) && dataCall.active != 0) {
            throw new RuntimeException(
                    "RIL_REQUEST_SETUP_DATA_CALL response, no ifname");
        }
        /* Use the last digit of the interface id as the cid */
        if (!needsOldRilFeature("singlepdp")) {
            dataCall.cid =
                Integer.parseInt(dataCall.ifname.substring(dataCall.ifname.length() - 1));
        }

        mLastDataIface[dataCall.cid] = dataCall.ifname;


        String addresses = p.readString();
        if (!TextUtils.isEmpty(addresses)) {
          dataCall.addresses = addresses.split(" ");
        }

        dataCall.dnses = new String[2];
        dataCall.dnses[0] = SystemProperties.get("net."+dataCall.ifname+".dns1");
        dataCall.dnses[1] = SystemProperties.get("net."+dataCall.ifname+".dns2");
        dataCall.active = 1;
        dataCall.status = 0;

        return dataCall;
    }

    @Override
    public void getNeighboringCids(Message response) {
        if (!getRadioState().isOn())
            return;

        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_GET_NEIGHBORING_CELL_IDS, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    @Override
    public void setCurrentPreferredNetworkType() {
        if (RILJ_LOGD) riljLog("setCurrentPreferredNetworkType: " + mSetPreferredNetworkType);
        setPreferredNetworkType(mSetPreferredNetworkType, null);
    }

    @Override
    public void setPreferredNetworkType(int networkType , Message response) {
        /**
          * If not using a USIM, ignore LTE mode and go to 3G
          */
        if (!mUSIM && networkType == RILConstants.NETWORK_MODE_LTE_GSM_WCDMA &&
                 mSetPreferredNetworkType >= RILConstants.NETWORK_MODE_WCDMA_PREF) {
            networkType = RILConstants.NETWORK_MODE_WCDMA_PREF;
        }
        mSetPreferredNetworkType = networkType;

        super.setPreferredNetworkType(networkType, response);
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        boolean oldRil = needsOldRilFeature("signalstrength");
        boolean noLte = false;

        /* TODO: Add SignalStrength class to match RIL_SignalStrength */
        response = new int[numInts];
        for (int i = 0 ; i < numInts ; i++) {
            if ((oldRil || noLte) && i > 6 && i < 12) {
                response[i] = -1;
            } else {
                response[i] = p.readInt();
            }
            if (i == 7 && response[i] == 99) {
                response[i] = -1;
                noLte = true;
            }
            if (i == 8 && !(noLte || oldRil)) {
                response[i] *= -1;
            }
        }

        return response;
    }

    @Override
    protected void
    processSolicited (Parcel p) {
        int serial, error;
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Log.w(LOG_TAG, "Unexpected solicited response! sn: "
                            + serial + " error: " + error);
            return;
        }

        Object ret = null;

        if (error == 0 || p.dataAvail() > 0) {

            /* Convert RIL_REQUEST_GET_MODEM_VERSION back */
            if (SystemProperties.get("ro.cm.device").indexOf("e73") == 0 &&
                  rr.mRequest == 220) {
                rr.mRequest = RIL_REQUEST_BASEBAND_VERSION;
            }

            // either command succeeds or command fails but with data payload
            try {switch (rr.mRequest) {
            /*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
             */
            case RIL_REQUEST_GET_SIM_STATUS: ret =  responseIccCardStatus(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PIN2: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_SIM_PUK2: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_SIM_PIN2: ret =  responseInts(p); break;
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: ret =  responseInts(p); break;
            case RIL_REQUEST_GET_CURRENT_CALLS: ret =  responseCallList(p); break;
            case RIL_REQUEST_DIAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMSI: ret =  responseString(p); break;
            case RIL_REQUEST_HANGUP: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: ret =  responseVoid(p); break;
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_DATA_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
            case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
            case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseSetupDataCall(p); break;
            case RIL_REQUEST_SIM_IO: ret =  responseICC_IO(p); break;
            case RIL_REQUEST_SEND_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_CANCEL_USSD: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_CLIR: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CLIR: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: ret =  responseCallForward(p); break;
            case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_CALL_WAITING: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_CALL_WAITING: ret =  responseVoid(p); break;
            case RIL_REQUEST_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_IMEI: ret =  responseString(p); break;
            case RIL_REQUEST_GET_IMEISV: ret =  responseString(p); break;
            case RIL_REQUEST_ANSWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseOperatorInfos(p); break;
            case RIL_REQUEST_DTMF_START: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF_STOP: ret =  responseVoid(p); break;
            case RIL_REQUEST_BASEBAND_VERSION: ret =  responseString(p); break;
            case RIL_REQUEST_SEPARATE_CONNECTION: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_MUTE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_MUTE: ret =  responseInts(p); break;
            case RIL_REQUEST_QUERY_CLIP: ret =  responseInts(p); break;
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
            case RIL_REQUEST_DATA_CALL_LIST: ret =  responseDataCallList(p); break;
            case RIL_REQUEST_RESET_RADIO: ret =  responseVoid(p); break;
            case RIL_REQUEST_OEM_HOOK_RAW: ret =  responseRaw(p); break;
            case RIL_REQUEST_OEM_HOOK_STRINGS: ret =  responseStrings(p); break;
            case RIL_REQUEST_SCREEN_STATE: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_WRITE_SMS_TO_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_DELETE_SMS_ON_SIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_BAND_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_STK_GET_PROFILE: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SET_PROFILE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: ret =  responseString(p); break;
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: ret =  responseVoid(p); break;
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: ret =  responseInts(p); break;
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: ret =  responseVoid(p); break;
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseGetPreferredNetworkType(p); break;
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
            case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: ret =  responseInts(p); break;
            case RIL_REQUEST_SET_TTY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_QUERY_TTY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_FLASH: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BURST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
            case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: ret =  responseGmsBroadcastConfig(p); break;
            case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: ret =  responseCdmaBroadcastConfig(p); break;
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SUBSCRIPTION: ret =  responseStrings(p); break;
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
            case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseString(p); break;
            case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: ret = responseVoid(p); break;
            case 104: ret = responseInts(p); break; // RIL_REQUEST_VOICE_RADIO_TECH
            case 105: ret = responseInts(p); break; // RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE
            case 106: ret = responseStrings(p); break; // RIL_REQUEST_CDMA_PRL_VERSION
            case 107: ret = responseInts(p);  break; // RIL_REQUEST_IMS_REGISTRATION_STATE
            case RIL_REQUEST_VOICE_RADIO_TECH: ret = responseInts(p); break;

            default:
                throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
            //break;
            }} catch (Throwable tr) {
                // Exceptions here usually mean invalid RIL responses

                Log.w(LOG_TAG, rr.serialString() + "< "
                        + requestToString(rr.mRequest)
                        + " exception, possible invalid RIL response", tr);

                if (rr.mResult != null) {
                    AsyncResult.forMessage(rr.mResult, null, tr);
                    rr.mResult.sendToTarget();
                }
                rr.release();
                return;
            }
        }

        if (error != 0) {
            rr.onError(error, ret);
            rr.release();
            return;
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
            + " " + retToString(rr.mRequest, ret));

        if (rr.mResult != null) {
            AsyncResult.forMessage(rr.mResult, ret, null);
            rr.mResult.sendToTarget();
        }

        rr.release();
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition(); // save off position within the Parcel
        int response = p.readInt();

        /* Assume devices needing the "datacall" GB-compatibility flag are
         * running GB RILs, so skip 1031-1034 for those */
        if (needsOldRilFeature("datacall")) {
            switch(response) {
                 case 1031:
                 case 1032:
                 case 1033:
                 case 1034:
                     ret = responseVoid(p);
                     return;
            }
        }

        switch(response) {
            //case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
            case 1035: ret = responseVoid(p); break; // RIL_UNSOL_VOICE_RADIO_TECH_CHANGED
            case 1036: ret = responseVoid(p); break; // RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED
            case 1037: ret = responseVoid(p); break; // RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE
            case 1038: ret = responseVoid(p); break; // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                int state = p.readInt();
                setRadioStateFromRILInt(state);
                break;
            case RIL_UNSOL_RIL_CONNECTED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                break;
            case 1035:
            case 1036:
                break;
            case 1037: // RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;
            case 1038:
                break;
        }
    }

    /**
     * Notify all registrants that the ril has connected or disconnected.
     *
     * @param rilVer is the version of the ril or -1 if disconnected.
     */
    private void notifyRegistrantsRilConnectionChanged(int rilVer) {
        mRilVersion = rilVer;
        if (mRilConnectedRegistrants != null) {
            mRilConnectedRegistrants.notifyRegistrants(
                                new AsyncResult (null, new Integer(rilVer), null));
        }
    }

    private void setRadioStateFromRILInt (int stateCode) {
        CommandsInterface.RadioState radioState;
        HandlerThread handlerThread;
        Looper looper;
        IccHandler iccHandler;

        switch (stateCode) {
            case RIL_INT_RADIO_OFF:
                radioState = CommandsInterface.RadioState.RADIO_OFF;
                if (mIccHandler != null) {
                    mIccThread = null;
                    mIccHandler = null;
                }
                break;
            case RIL_INT_RADIO_UNAVALIABLE:
                radioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case RIL_INT_RADIO_ON:
            case RIL_INT_RADIO_ON_NG:
            case RIL_INT_RADIO_ON_HTC:
                if (mIccHandler == null) {
                    handlerThread = new HandlerThread("IccHandler");
                    mIccThread = handlerThread;

                    mIccThread.start();

                    looper = mIccThread.getLooper();
                    mIccHandler = new IccHandler(this,looper);
                    mIccHandler.run();
                }
                radioState = CommandsInterface.RadioState.RADIO_ON;
                break;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateCode);
        }

        setRadioState (radioState);
    }

    class IccHandler extends Handler implements Runnable {
        private static final int EVENT_RADIO_ON = 1;
        private static final int EVENT_ICC_STATUS_CHANGED = 2;
        private static final int EVENT_GET_ICC_STATUS_DONE = 3;
        private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 4;

        private RIL mRil;
        private boolean mRadioOn = false;

        public IccHandler (RIL ril, Looper looper) {
            super (looper);
            mRil = ril;
        }

        public void handleMessage (Message paramMessage) {
            switch (paramMessage.what) {
                case EVENT_RADIO_ON:
                    mRadioOn = true;
                    Log.d(LOG_TAG, "Radio on -> Forcing sim status update");
                    sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    AsyncResult asyncResult = (AsyncResult) paramMessage.obj;
                    if (asyncResult.exception != null) {
                        Log.e (LOG_TAG, "IccCardStatusDone shouldn't return exceptions!", asyncResult.exception);
                        break;
                    }
                    IccCardStatus status = (IccCardStatus) asyncResult.result;
                    if (status.getNumApplications() == 0) {
                        if (!mRil.getRadioState().isOn()) {
                            break;
                        }

                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                    } else {
                        int appIndex = -1;
                        if (mPhoneType == RILConstants.CDMA_PHONE) {
                            appIndex = status.getCdmaSubscriptionAppIndex();
                            Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);
                        } else {
                            appIndex = status.getGsmUmtsSubscriptionAppIndex();
                            Log.d(LOG_TAG, "This is a GSM PHONE " + appIndex);
                        }

                        IccCardApplication application = status.getApplication(appIndex);
                        IccCardApplication.AppState app_state = application.app_state;
                        IccCardApplication.AppType app_type = application.app_type;

                        switch (app_state) {
                            case APPSTATE_PIN:
                            case APPSTATE_PUK:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            case APPSTATE_READY:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RADIO_ON);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            default:
                                return;
                        }
                    }
                    break;
                case EVENT_ICC_STATUS_CHANGED:
                    if (mRadioOn) {
                        Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                         mRil.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, paramMessage.obj));
                    } else {
                         Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED while radio is not ON. Ignoring");
                    }
                    break;
                case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                    mRadioOn = false;
                    // disposeCards(); // to be verified;
                default:
                    Log.e(LOG_TAG, " Unknown Event " + paramMessage.what);
                    break;
            }
        }

        public void run () {
            mRil.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
            Message msg = obtainMessage(EVENT_RADIO_ON);
            mRil.getIccCardStatus(msg);
        }
    }


    @Override
    public void
    supplyNetworkDepersonalization(String netpin, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeInt(3);
        rr.mp.writeString(netpin);

        send(rr);
    }

    @Override
    public void
    setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + " " + operatorNumeric);

        rr.mp.writeInt(2);
        rr.mp.writeString(operatorNumeric);
        rr.mp.writeString("NOCHANGE");

        send(rr);
    }
}
