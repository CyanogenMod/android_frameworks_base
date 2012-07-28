/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (C) 2011, 2012 The CyanogenMod Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.lang.Runtime;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Message;
import android.os.AsyncResult;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import static com.android.internal.telephony.RILConstants.*;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.DataCallState;
import com.android.internal.telephony.DataConnection.FailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.IccCardApplication;
import com.android.internal.telephony.IccCardStatus;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.SignalToneUtil;

import android.text.TextUtils;
import android.util.Log;

public class SamsungCDMAv6RIL extends RIL implements CommandsInterface {

    public SamsungCDMAv6RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    // SAMSUNG SGS STATES
    static final int RIL_UNSOL_O2_HOME_ZONE_INFO = 11007;
    static final int RIL_UNSOL_DEVICE_READY_NOTI = 11008;
    static final int RIL_UNSOL_GPS_NOTI = 11009;
    static final int RIL_UNSOL_AM = 11010;
    static final int RIL_UNSOL_DATA_SUSPEND_RESUME = 11012;
    static final int RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL = 11011;
    static final int RIL_UNSOL_HSDPA_STATE_CHANGED = 11016;
    static final int RIL_REQUEST_DIAL_EMERGENCY = 10016;

    static String
    requestToString(int request) {
        switch (request) {
            case RIL_REQUEST_DIAL_EMERGENCY: return "DIAL_EMERGENCY";
            default: return RIL.requestToString(request);
        }
    }

    static String
    samsungResponseToString(int request)
    {
    switch(request) {
        // SAMSUNG STATES
        case RIL_UNSOL_AM: return "RIL_UNSOL_AM";
        case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL: return "RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL";
        case RIL_UNSOL_DATA_SUSPEND_RESUME: return "RIL_UNSOL_DATA_SUSPEND_RESUME";
        default:  return "<unknown response: "+request+">";
    }
}

    protected void samsungUnsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + samsungResponseToString(response) + " " + retToString(response, ret));
    }

    @Override
    public void
    setRadioPower(boolean on, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);

        if (on) {
            rr.mp.writeInt(1);
            rr.mp.writeInt(1);
        } else {
            rr.mp.writeInt(2);
            rr.mp.writeInt(0);
            rr.mp.writeInt(0);
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        send(rr);
    }

    @Override
    protected void
    processSolicited (Parcel p) {
        int serial, error;

        serial = p.readInt();
        error = p.readInt();

        Log.d(LOG_TAG, "Serial: " + serial);
        Log.d(LOG_TAG, "Error: " + error);

        RILRequest rr;

        rr = findAndRemoveRequestFromList(serial);

        if (rr == null) {
            Log.w(LOG_TAG, "Unexpected solicited response! sn: "
                    + serial + " error: " + error);
            return;
        }

        Object ret = null;

        if (error == 0 || p.dataAvail() > 0) {
            // either command succeeds or command fails but with data payload
            try {switch (rr.mRequest) {
            /*
            cat libs/telephony/ril_commands.h \
            | egrep "^ *{RIL_" \
            | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
             */
            case RIL_REQUEST_GET_SIM_STATUS: ret = responseIccCardStatus(p); break;
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
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseLastCallFailCause(p); break;
            case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  responseVoiceRegistrationState(p); break;
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
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseNetworkType(p); break;
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
            case RIL_REQUEST_CDMA_SUBSCRIPTION: ret =  responseCdmaSubscription(p); break;
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
            case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
            case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseString(p); break;
            case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: ret = responseVoid(p); break;
            case RIL_REQUEST_DIAL_EMERGENCY: ret = responseVoid(p); break;
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
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
            // Ugly fix for Samsung messing up SMS_SEND request fail in binary RIL
            if (error == -1 && rr.mRequest == RIL_REQUEST_SEND_SMS)
            {
                try
                {
                    ret = responseSMS(p);
                } catch (Throwable tr) {
                    Log.w(LOG_TAG, rr.serialString() + "< "
                            + requestToString(rr.mRequest)
                            + " exception, Processing Samsung SMS fix ", tr);
                    rr.onError(error, ret);
                    rr.release();
                    return;
                }
            } else {
                rr.onError(error, ret);
                rr.release();
                return;
            }
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
    public void
    dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr;

        rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);
        rr.mp.writeString(address);
        rr.mp.writeInt(clirMode);
        rr.mp.writeInt(0); // UUS information is absent

        if (uusInfo == null) {
            rr.mp.writeInt(0); // UUS information is absent
        } else {
            rr.mp.writeInt(1); // UUS information is present
            rr.mp.writeInt(uusInfo.getType());
            rr.mp.writeInt(uusInfo.getDcs());
            rr.mp.writeByteArray(uusInfo.getUserData());
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    public void
    dialEmergencyCall(String address, int clirMode, Message result) {
        RILRequest rr;
        Log.v(LOG_TAG, "Emergency dial: " + address);

        rr = RILRequest.obtain(RIL_REQUEST_DIAL_EMERGENCY, result);
        rr.mp.writeString(address + "/");
        rr.mp.writeInt(clirMode);
        rr.mp.writeInt(0);
        rr.mp.writeInt(0);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        int response;
        Object ret;
        int dataPosition = p.dataPosition();

        response = p.readInt();

        switch(response) {
        /*
                cat libs/telephony/ril_unsol_commands.h \
                | egrep "^ *{RIL_" \
                | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: \2(rr, p); break;/'
         */

        case RIL_UNSOL_NITZ_TIME_RECEIVED: ret =  responseString(p); break;
        case RIL_UNSOL_SIGNAL_STRENGTH: ret = responseSignalStrength(p); break;
        case RIL_UNSOL_CDMA_INFO_REC: ret = responseCdmaInformationRecord(p); break;
        case RIL_UNSOL_HSDPA_STATE_CHANGED: ret = responseInts(p); break;
        case RIL_UNSOL_O2_HOME_ZONE_INFO: ret = responseVoid(p); break;
        case RIL_UNSOL_DEVICE_READY_NOTI: ret = responseVoid(p); break;
        case RIL_UNSOL_GPS_NOTI: ret = responseVoid(p); break; // Ignored in TW RIL.
        // SAMSUNG STATES
        case RIL_UNSOL_AM: ret = responseString(p); break;
        case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL: ret = responseVoid(p); break;
        case RIL_UNSOL_DATA_SUSPEND_RESUME: ret = responseInts(p); break;
        case RIL_UNSOL_RIL_CONNECTED: ret = responseString(p); break;

        default:
            // Rewind the Parcel
            p.setDataPosition(dataPosition);

            // Forward responses that we are not overriding to the super class
            super.processUnsolicited(p);
            return;
        }

        switch(response) {
            case RIL_UNSOL_HSDPA_STATE_CHANGED:
                if (RILJ_LOGD) unsljLog(response);

                boolean newHsdpa = ((int[])ret)[0] == 1;
                String curState = SystemProperties.get(TelephonyProperties.PROPERTY_DATA_NETWORK_TYPE);
                boolean curHsdpa = false;

                if (curState.equals("HSDPA:9")) {
                    curHsdpa = true;
                } else if (!curState.equals("UMTS:3")) {
                    // Don't send poll request if not on 3g
                    break;
                }

                if (curHsdpa != newHsdpa) {
                    mVoiceNetworkStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
                }
                break;

            case RIL_UNSOL_NITZ_TIME_RECEIVED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                // has bonus long containing milliseconds since boot that the NITZ
                // time was received
                long nitzReceiveTime = p.readLong();

                Object[] result = new Object[2];

                String nitz = (String)ret;
                if (RILJ_LOGD) riljLog(" RIL_UNSOL_NITZ_TIME_RECEIVED length = "
                                         + nitz.split("[/:,+-]").length);

                // remove the tailing information that samsung added to the string
                if(nitz.split("[/:,+-]").length >= 9)
                nitz = nitz.substring(0,(nitz.lastIndexOf(",")));

                if (RILJ_LOGD) riljLog(" RIL_UNSOL_NITZ_TIME_RECEIVED striped nitz = "
                                         + nitz);

                result[0] = nitz;
                result[1] = Long.valueOf(nitzReceiveTime);

                if (mNITZTimeRegistrant != null) {

                    mNITZTimeRegistrant
                    .notifyRegistrant(new AsyncResult (null, result, null));
                } else {
                    // in case NITZ time registrant isnt registered yet
                    mLastNITZTimeInfo = nitz;
                }
                break;

            case RIL_UNSOL_SIGNAL_STRENGTH:
                // Note this is set to "verbose" because it happens
                // frequently
                if (RILJ_LOGV) unsljLogvRet(response, ret);

                if (mSignalStrengthRegistrant != null) {
                    mSignalStrengthRegistrant.notifyRegistrant(
                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_CDMA_INFO_REC:
                ArrayList<CdmaInformationRecords> listInfoRecs;

                try {
                    listInfoRecs = (ArrayList<CdmaInformationRecords>)ret;
                } catch (ClassCastException e) {
                    Log.e(LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
                    break;
                }

                for (CdmaInformationRecords rec : listInfoRecs) {
                    if (RILJ_LOGD) unsljLogRet(response, rec);
                    notifyRegistrantsCdmaInfoRec(rec);
                }
                break;

            case RIL_UNSOL_RIL_CONNECTED:
                // FIXME: Processing this state breaks data call.
                break;
            // SAMSUNG STATES
            case RIL_UNSOL_AM:
                String amString = (String) ret;
                Log.d(LOG_TAG, "Executing AM: " + amString);

                try {
                    Runtime.getRuntime().exec("am " + amString);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(LOG_TAG, "am " + amString + " could not be executed.");
                }
                break;
            case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_DATA_SUSPEND_RESUME:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                break;
        }
    }

    @Override
    protected Object
    responseCallList(Parcel p) {
        int num;
        boolean isVideo;
        ArrayList<DriverCall> response;
        DriverCall dc;
        int dataAvail = p.dataAvail();
        int pos = p.dataPosition();
        int size = p.dataSize();

        Log.d(LOG_TAG, "Parcel size = " + size);
        Log.d(LOG_TAG, "Parcel pos = " + pos);
        Log.d(LOG_TAG, "Parcel dataAvail = " + dataAvail);

        num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        for (int i = 0 ; i < num ; i++) {
            dc = new SamsungDriverCall();

            dc.state                = DriverCall.stateFromCLCC(p.readInt());
            dc.index                = p.readInt();
            dc.TOA                  = p.readInt();
            dc.isMpty               = (0 != p.readInt());
            dc.isMT                 = (0 != p.readInt());
            dc.als                  = p.readInt();
            dc.isVoice              = (0 != p.readInt());
            isVideo                 = (0 != p.readInt());
            dc.isVoicePrivacy       = (0 != p.readInt());
            dc.number               = p.readString();
            int np                  = p.readInt();
            dc.numberPresentation   = DriverCall.presentationFromCLIP(np);
            dc.name                 = p.readString();
            dc.namePresentation     = p.readInt();
            int uusInfoPresent      = p.readInt();

            Log.d(LOG_TAG, "state = " + dc.state);
            Log.d(LOG_TAG, "index = " + dc.index);
            Log.d(LOG_TAG, "state = " + dc.TOA);
            Log.d(LOG_TAG, "isMpty = " + dc.isMpty);
            Log.d(LOG_TAG, "isMT = " + dc.isMT);
            Log.d(LOG_TAG, "als = " + dc.als);
            Log.d(LOG_TAG, "isVoice = " + dc.isVoice);
            Log.d(LOG_TAG, "isVideo = " + isVideo);
            Log.d(LOG_TAG, "number = " + dc.number);
            Log.d(LOG_TAG, "numberPresentation = " + np);
            Log.d(LOG_TAG, "name = " + dc.name);
            Log.d(LOG_TAG, "namePresentation = " + dc.namePresentation);
            Log.d(LOG_TAG, "uusInfoPresent = " + uusInfoPresent);

            if (uusInfoPresent == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                byte[] userData = p.createByteArray();
                dc.uusInfo.setUserData(userData);
                Log
                .v(LOG_TAG, String.format("Incoming UUS : type=%d, dcs=%d, length=%d",
                        dc.uusInfo.getType(), dc.uusInfo.getDcs(),
                        dc.uusInfo.getUserData().length));
                Log.v(LOG_TAG, "Incoming UUS : data (string)="
                        + new String(dc.uusInfo.getUserData()));
                Log.v(LOG_TAG, "Incoming UUS : data (hex): "
                        + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                Log.v(LOG_TAG, "Incoming UUS : NOT present!");
            }

            // Make sure there's a leading + on addresses with a TOA of 145
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);

            response.add(dc);

            if (dc.isVoicePrivacy) {
                mVoicePrivacyOnRegistrants.notifyRegistrants();
                Log.d(LOG_TAG, "InCall VoicePrivacy is enabled");
            } else {
                mVoicePrivacyOffRegistrants.notifyRegistrants();
                Log.d(LOG_TAG, "InCall VoicePrivacy is disabled");
            }
        }

        Collections.sort(response);

        return response;
    }

    @Override
    protected DataCallState getDataCallState(Parcel p, int version) {
        DataCallState dataCall = new DataCallState();

        dataCall.version = version;
        dataCall.status = p.readInt();
        dataCall.suggestedRetryTime = p.readInt();
        dataCall.cid = p.readInt();
        dataCall.active = p.readInt();
        dataCall.type = p.readString();
        dataCall.ifname = SystemProperties.get("net.cdma.ppp.interface");
        if ((dataCall.status == DataConnection.FailCause.NONE.getErrorCode()) &&
                TextUtils.isEmpty(dataCall.ifname)) {
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
        return dataCall;
    }

    protected Object
    responseLastCallFailCause(Parcel p) {
        int response[] = (int[])responseInts(p);

        if (response.length > 0 &&
            response[0] == com.android.internal.telephony.cdma.CallFailCause.ERROR_UNSPECIFIED) {

            // Far-end hangup returns ERROR_UNSPECIFIED, which shows "Call Lost" dialog.
            Log.d(LOG_TAG, "Overriding ERROR_UNSPECIFIED fail cause with NORMAL_CLEARING.");
            response[0] = com.android.internal.telephony.cdma.CallFailCause.NORMAL_CLEARING;
        }

        return response;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        /* TODO: Add SignalStrength class to match RIL_SignalStrength */
        response = new int[numInts];
        for (int i = 0 ; i < 7 ; i++) {
            response[i] = p.readInt();
        }

        if(response[3] < 0){
           response[3] = -response[3];
        }
        // Scale cdmaDbm so Samsung's -95..-105 range for SIGNAL_STRENGTH_POOR
        // fits in AOSP's -95..-100 range
        if(response[2] > 95){
        //   Log.d(LOG_TAG, "SignalStrength: Scaling cdmaDbm \"" + response[2] + "\" for smaller SIGNAL_STRENGTH_POOR bucket.");
           response[2] = ((response[2]-96)/2)+96;
        }
        // Framework takes care of the rest for us.
        return response;
    }

    protected Object
    responseVoiceRegistrationState(Parcel p) {
        String response[] = (String[])responseStrings(p);

        // These values are provided in hex, convert to dec.
        response[4] = Integer.toString(Integer.parseInt(response[4], 16)); // baseStationId
        response[5] = Integer.toString(Integer.parseInt(response[5], 16)); // baseStationLatitude
        response[6] = Integer.toString(Integer.parseInt(response[6], 16)); // baseStationLongitude

        return response;
    }

    protected Object
    responseNetworkType(Parcel p) {
        int response[] = (int[]) responseInts(p);

        return response;
    }

    @Override
    protected Object
    responseSetupDataCall(Parcel p) {
        DataCallState dataCall = new DataCallState();
        String strings[] = (String []) responseStrings(p);

        if (strings.length >= 2) {
            dataCall.cid = Integer.parseInt(strings[0]);

            // We're responsible for starting/stopping the pppd_cdma service.
            if (!startPppdCdmaService(strings[1])) {
                // pppd_cdma service didn't respond timely.
                dataCall.status = FailCause.ERROR_UNSPECIFIED.getErrorCode();
                return dataCall;
            }

            // pppd_cdma service responded, pull network parameters set by ip-up script.
            dataCall.ifname = SystemProperties.get("net.cdma.ppp.interface");
            String   ifprop = "net." + dataCall.ifname;

            dataCall.addresses = new String[] {SystemProperties.get(ifprop + ".local-ip")};
            dataCall.gateways  = new String[] {SystemProperties.get(ifprop + ".remote-ip")};
            dataCall.dnses     = new String[] {SystemProperties.get(ifprop + ".dns1"),
                                               SystemProperties.get(ifprop + ".dns2")};
        } else {
            // On rare occasion the pppd_cdma service is left active from a stale
            // session, causing the data call setup to fail.  Make sure that pppd_cdma
            // is stopped now, so that the next setup attempt may succeed.
            Log.d(LOG_TAG, "Set ril.cdma.data_state=0 to make sure pppd_cdma is stopped.");
            SystemProperties.set("ril.cdma.data_state", "0");

            dataCall.status = FailCause.ERROR_UNSPECIFIED.getErrorCode(); // Who knows?
        }

        return dataCall;
    }

    private boolean startPppdCdmaService(String ttyname) {
        SystemProperties.set("net.cdma.datalinkinterface", ttyname);

        // Connecting: Set ril.cdma.data_state=1 to (re)start pppd_cdma service,
        // which responds by setting ril.cdma.data_state=2 once connection is up.
        SystemProperties.set("ril.cdma.data_state", "1");
        Log.d(LOG_TAG, "Set ril.cdma.data_state=1, waiting for ril.cdma.data_state=2.");

        // Typically takes < 200 ms on my Epic, so sleep in 100 ms intervals.
        for (int i = 0; i < 10; i++) {
            try {Thread.sleep(100);} catch (InterruptedException e) {}

            if (SystemProperties.getInt("ril.cdma.data_state", 1) == 2) {
                Log.d(LOG_TAG, "Got ril.cdma.data_state=2, connected.");
                return true;
            }
        }

        // Taking > 1 s here, try up to 10 s, which is hopefully long enough.
        for (int i = 1; i < 10; i++) {
            try {Thread.sleep(1000);} catch (InterruptedException e) {}

            if (SystemProperties.getInt("ril.cdma.data_state", 1) == 2) {
                Log.d(LOG_TAG, "Got ril.cdma.data_state=2, connected.");
                return true;
            }
        }

        // Disconnect: Set ril.cdma.data_state=0 to stop pppd_cdma service.
        Log.d(LOG_TAG, "Didn't get ril.cdma.data_state=2 timely, aborting.");
        SystemProperties.set("ril.cdma.data_state", "0");

        return false;
    }

    @Override
    public void
    deactivateDataCall(int cid, int reason, Message result) {
        // Disconnect: Set ril.cdma.data_state=0 to stop pppd_cdma service.
        Log.d(LOG_TAG, "Set ril.cdma.data_state=0.");
        SystemProperties.set("ril.cdma.data_state", "0");

        super.deactivateDataCall(cid, reason, result);
    }

    protected Object
    responseCdmaSubscription(Parcel p) {
        String response[] = (String[])responseStrings(p);

        if (response.length == 4) {
            // PRL version is missing in subscription parcel, add it from properties.
            String prlVersion = SystemProperties.get("ril.prl_ver_1").split(":")[1];
            response          = new String[] {response[0], response[1], response[2],
                                              response[3], prlVersion};
        }

        return response;
    }

    // Workaround for Samsung CDMA "ring of death" bug:
    //
    // Symptom: As soon as the phone receives notice of an incoming call, an
    //   audible "old fashioned ring" is emitted through the earpiece and
    //   persists through the duration of the call, or until reboot if the call
    //   isn't answered.
    //
    // Background: The CDMA telephony stack implements a number of "signal info
    //   tones" that are locally generated by ToneGenerator and mixed into the
    //   voice call path in response to radio RIL_UNSOL_CDMA_INFO_REC requests.
    //   One of these tones, IS95_CONST_IR_SIG_IS54B_L, is requested by the
    //   radio just prior to notice of an incoming call when the voice call
    //   path is muted.  CallNotifier is responsible for stopping all signal
    //   tones (by "playing" the TONE_CDMA_SIGNAL_OFF tone) upon receipt of a
    //   "new ringing connection", prior to unmuting the voice call path.
    //
    // Problem: CallNotifier's incoming call path is designed to minimize
    //   latency to notify users of incoming calls ASAP.  Thus,
    //   SignalInfoTonePlayer requests are handled asynchronously by spawning a
    //   one-shot thread for each.  Unfortunately the ToneGenerator API does
    //   not provide a mechanism to specify an ordering on requests, and thus,
    //   unexpected thread interleaving may result in ToneGenerator processing
    //   them in the opposite order that CallNotifier intended.  In this case,
    //   playing the "signal off" tone first, followed by playing the "old
    //   fashioned ring" indefinitely.
    //
    // Solution: An API change to ToneGenerator is required to enable
    //   SignalInfoTonePlayer to impose an ordering on requests (i.e., drop any
    //   request that's older than the most recent observed).  Such a change,
    //   or another appropriate fix should be implemented in AOSP first.
    //
    // Workaround: Intercept RIL_UNSOL_CDMA_INFO_REC requests from the radio,
    //   check for a signal info record matching IS95_CONST_IR_SIG_IS54B_L, and
    //   drop it so it's never seen by CallNotifier.  If other signal tones are
    //   observed to cause this problem, they should be dropped here as well.
    @Override
    protected void
    notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        final int response = RIL_UNSOL_CDMA_INFO_REC;

        if (infoRec.record instanceof CdmaSignalInfoRec) {
            CdmaSignalInfoRec sir = (CdmaSignalInfoRec)infoRec.record;
            if (sir != null && sir.isPresent &&
                sir.signalType == SignalToneUtil.IS95_CONST_IR_SIGNAL_IS54B &&
                sir.alertPitch == SignalToneUtil.IS95_CONST_IR_ALERT_MED    &&
                sir.signal     == SignalToneUtil.IS95_CONST_IR_SIG_IS54B_L) {

                Log.d(LOG_TAG, "Dropping \"" + responseToString(response) + " " +
                      retToString(response, sir) + "\" to prevent \"ring of death\" bug.");
                return;
            }
        }

        super.notifyRegistrantsCdmaInfoRec(infoRec);
    }

    protected class SamsungDriverCall extends DriverCall {
        @Override
        public String
        toString() {
            // Samsung CDMA devices' call parcel is formatted differently
            // fake unused data for video calls, and fix formatting
            // so that voice calls' information can be correctly parsed
            return "id=" + index + ","
            + state + ","
            + "toa=" + TOA + ","
            + (isMpty ? "conf" : "norm") + ","
            + (isMT ? "mt" : "mo") + ","
            + "als=" + als + ","
            + (isVoice ? "voc" : "nonvoc") + ","
            + "nonvid" + ","
            + number + ","
            + "cli=" + numberPresentation + ","
            + "name=" + name + ","
            + namePresentation;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentPreferredNetworkType() {
        if (RILJ_LOGD) riljLog("setCurrentPreferredNetworkType IGNORED");
        /* Google added this as a fix for crespo loosing network type after
         * taking an OTA. This messes up the data connection state for us
         * due to the way we handle network type change (disable data
         * then change then re-enable).
         */
    }

    @Override
    public void setPreferredNetworkType(int networkType , Message response) {
        /* Samsung modem implementation does bad things when a datacall is running
         * while switching the preferred networktype.
         */
        ConnectivityManager cm =
            (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if(cm.getMobileDataEnabled())
        {
            ConnectivityHandler handler = new ConnectivityHandler(mContext);
            handler.setPreferedNetworkType(networkType, response);
        } else {
            sendPreferredNetworkType(networkType, response);
        }
    }


    //Sends the real RIL request to the modem.
    private void sendPreferredNetworkType(int networkType, Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, response);

        rr.mp.writeInt(1);
        rr.mp.writeInt(networkType);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                + " : " + networkType);

        send(rr);
    }
    /* private class that does the handling for the dataconnection
     * dataconnection is done async, so we send the request for disabling it,
     * wait for the response, set the prefered networktype and notify the
     * real sender with its result.
     */
    private class ConnectivityHandler extends Handler{

        private static final int MESSAGE_SET_PREFERRED_NETWORK_TYPE = 30;
        private Context mContext;
        private int mDesiredNetworkType;
        //the original message, we need it for calling back the original caller when done
        private Message mNetworktypeResponse;
        private ConnectivityBroadcastReceiver mConnectivityReceiver =  new ConnectivityBroadcastReceiver();

        public ConnectivityHandler(Context context)
        {
            mContext = context;
        }

        private void startListening() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mConnectivityReceiver, filter);
        }

        private synchronized void stopListening() {
            mContext.unregisterReceiver(mConnectivityReceiver);
        }

        public void setPreferedNetworkType(int networkType, Message response)
        {
            Log.d(LOG_TAG, "Mobile Dataconnection is online setting it down");
            mDesiredNetworkType = networkType;
            mNetworktypeResponse = response;
            ConnectivityManager cm =
                (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            //start listening for the connectivity change broadcast
            startListening();
            cm.setMobileDataEnabled(false);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
            //networktype was set, now we can enable the dataconnection again
            case MESSAGE_SET_PREFERRED_NETWORK_TYPE:
                ConnectivityManager cm =
                    (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

                Log.d(LOG_TAG, "preferred NetworkType set upping Mobile Dataconnection");

                cm.setMobileDataEnabled(true);
                //everything done now call back that we have set the networktype
                AsyncResult.forMessage(mNetworktypeResponse, null, null);
                mNetworktypeResponse.sendToTarget();
                mNetworktypeResponse = null;
                break;
            default:
                throw new RuntimeException("unexpected event not handled");
            }
        }

        private class ConnectivityBroadcastReceiver extends BroadcastReceiver {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    Log.w(LOG_TAG, "onReceived() called with " + intent);
                    return;
                }
                boolean noConnectivity =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

                if (noConnectivity) {
                    //Ok dataconnection is down, now set the networktype
                    Log.w(LOG_TAG, "Mobile Dataconnection is now down setting preferred NetworkType");
                    stopListening();
                    sendPreferredNetworkType(mDesiredNetworkType, obtainMessage(MESSAGE_SET_PREFERRED_NETWORK_TYPE));
                    mDesiredNetworkType = -1;
                }
            }
        }
    }
}
