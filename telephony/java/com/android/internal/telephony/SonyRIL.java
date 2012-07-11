/*
 * 
 * Andreas Makris - Bin4ry - 2012
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

import android.content.*;
import android.content.res.Resources;
import android.net.*;
import android.os.*;
import android.telephony.*;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Custom Sony ril
 *
 * {@hide}
 */
public class SonyRIL extends RIL {
    String mAid = "";
    IccHandler mIccHandler;
    HandlerThread mIccThread;
    private final int RIL_REQUEST_SIM_OPEN_CHANNEL = 110;

    public SonyRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
        mIccHandler = null;
    }

    @Override
    protected RadioState getRadioStateFromInt(int stateInt) {
        RadioState state;

        /* RIL_RadioState ril.h */
        switch(stateInt) {
            case 0: state = RadioState.RADIO_OFF; break;
            case 1: state = RadioState.RADIO_UNAVAILABLE; break;
            case 2: state = RadioState.SIM_NOT_READY; break;
            case 3: state = RadioState.SIM_LOCKED_OR_ABSENT; break;
            case 4: state = RadioState.SIM_READY; break;
            case 5: state = RadioState.RUIM_NOT_READY; break;
            case 6: state = RadioState.RUIM_READY; break;
            case 7: state = RadioState.RUIM_LOCKED_OR_ABSENT; break;
            case 8: state = RadioState.NV_NOT_READY; break;
            case 9: state = RadioState.NV_READY; break;
			case 10: {
				if (mIccHandler == null)
                {
                    mIccThread = new HandlerThread("IccHandler");
                    mIccThread.start();
                    mIccHandler = new IccHandler(this, mIccThread.getLooper());
                }
                mIccHandler.run();
                state = mPhoneType == RILConstants.CDMA_PHONE ? RadioState.RUIM_NOT_READY : RadioState.SIM_NOT_READY;
                break;
            }
            default:
                throw new RuntimeException(
                            "Unrecognized RIL_RadioState: " + stateInt);
        }
        return state;
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

        for (int i = 0 ; i < numApplications ; i++) {
            ca = new IccCardApplication();
            ca.app_type       = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state      = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            ca.aid            = p.readString();
            ca.app_label      = p.readString();
            ca.pin1_replaced  = p.readInt();
            ca.pin1           = ca.PinStateFromRILInt(p.readInt());
            ca.pin2           = ca.PinStateFromRILInt(p.readInt());
            status.addApplication(ca);
        }

        updateIccType(status);
        return status;
    }
	
    private void updateIccType(IccCardStatus icccardstatus)
    {
        IccCardApplication.AppType apptype = IccCardApplication.AppType.APPTYPE_UNKNOWN;
        if(icccardstatus.getNumApplications() > 0)
        {
            CommandsInterface.RadioState radiostate = getRadioState();
            int i;
            IccCardApplication icccardapplication;
            if(radiostate == CommandsInterface.RadioState.RUIM_LOCKED_OR_ABSENT || radiostate == CommandsInterface.RadioState.RUIM_NOT_READY || radiostate == CommandsInterface.RadioState.RUIM_READY)
                i = icccardstatus.getCdmaSubscriptionAppIndex();
            else
                i = icccardstatus.getGsmUmtsSubscriptionAppIndex();
            icccardapplication = icccardstatus.getApplication(i);
            Log.i("RILJ", (new StringBuilder()).append("app_state ").append(icccardapplication.app_state).append(", perso_substate ").append(icccardapplication.perso_substate).append(", app_type ").append(icccardapplication.app_type).toString());
            mAid = icccardapplication.aid;
            riljLog((new StringBuilder()).append("Will use ").append(mAid).append(" as default application id. Hopefully you wont mind").toString());
            apptype = icccardapplication.app_type;
        }
        SystemProperties.set("ril.icctype", Integer.toString(apptype.ordinal()));
    }	

    @Override
    protected DataCallState getDataCallState(Parcel p, int version) {
        DataCallState dataCall = new DataCallState();

        dataCall.version = version;
        if (version < 5) {
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            String addresses = p.readString();
            if (!TextUtils.isEmpty(addresses)) {
                dataCall.addresses = addresses.split(" ");
            }
            // DataCallState needs an ifname. Since we don't have one use the name from the ThrottleService resource (default=rmnet0).
            dataCall.ifname = "rmnet0";
        } else {
            dataCall.status = p.readInt();
	        dataCall.suggestedRetryTime = p.readInt();
            dataCall.cid = p.readInt();
            dataCall.active = p.readInt();
            dataCall.type = p.readString();
            dataCall.ifname = p.readString();
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
        }
        return dataCall;
    }


    @Override public void
    supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, mAid, result);
    }

    @Override public void
    changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPinForApp(oldPin, newPin, mAid, result);
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
    iccIO (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, Message result) {
        //Note: This RIL request has not been renamed to ICC,
        //       but this request is also valid for SIM and RUIM
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SIM_IO, result);

        rr.mp.writeInt(command);
        rr.mp.writeInt(fileid);
        rr.mp.writeString(path);
        rr.mp.writeInt(p1);
        rr.mp.writeInt(p2);
        rr.mp.writeInt(p3);
        rr.mp.writeString(data);
        rr.mp.writeString(pin2);
        rr.mp.writeString(mAid);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> iccIO: " + requestToString(rr.mRequest)
                + " 0x" + Integer.toHexString(command)
                + " 0x" + Integer.toHexString(fileid) + " "
                + " path: " + path + ","
                + p1 + "," + p2 + "," + p3);

        send(rr);
    }

    public void iccOpenChannel(String s, Message message)
    {
        RILRequest rilrequest = RILRequest.obtain(RIL_REQUEST_SIM_OPEN_CHANNEL, message);
        rilrequest.mp.writeString(s);
        riljLog((new StringBuilder()).append(rilrequest.serialString()).append("> iccOpenChannel: ").append(requestToString(rilrequest.mRequest)).toString());
        send(rilrequest);
    }	

    @Override
    public void
    getIMSI(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_GET_IMSI, result);
        rr.mp.writeInt(1);
		rr.mp.writeString(mAid);
		
        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    @Override
    public void
    setNetworkSelectionModeAutomatic(Message response) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC,
                                    response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        rr.mp.writeString(null);
        rr.mp.writeInt(-1);

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

        rr.mp.writeString(operatorNumeric);
        rr.mp.writeInt(-1);

        send(rr);
    }

    @Override
    protected Object
    responseOperatorInfos(Parcel p) {
        String strings[] = (String [])responseStrings(p);
        ArrayList<OperatorInfo> ret;

        if (strings.length % 5 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
                + strings.length + " strings, expected multible of 5");
        }

        ret = new ArrayList<OperatorInfo>(strings.length / 4);

        for (int i = 0 ; i < strings.length ; i += 5) {
            ret.add (
                new OperatorInfo(
                    strings[i+0],
                    strings[i+1],
                    strings[i+2],
                    strings[i+3]));
        }
        return ret;
    }

    @Override
    public void
    dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);

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
        rr.mp.writeInt(255);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

	@Override
    protected void
    processUnsolicited (Parcel p) {
        int response;
        Object ret;

        response = p.readInt();

        try {switch(response) {
/*
 cat libs/telephony/ril_unsol_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: \2(rr, p); break;/'
*/

            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_NEW_SMS: ret =  responseString(p); break;
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: ret =  responseString(p); break;
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: ret =  responseInts(p); break;
            case RIL_UNSOL_ON_USSD: ret =  responseStrings(p); break;
            case RIL_UNSOL_NITZ_TIME_RECEIVED: ret =  responseString(p); break;
            case RIL_UNSOL_SIGNAL_STRENGTH: ret = responseSignalStrength(p); break;
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED: ret = responseDataCallList(p);break;
            case RIL_UNSOL_SUPP_SVC_NOTIFICATION: ret = responseSuppServiceNotification(p); break;
            case RIL_UNSOL_STK_SESSION_END: ret = responseVoid(p); break;
            case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;
            case RIL_UNSOL_STK_EVENT_NOTIFY: ret = responseString(p); break;
            case RIL_UNSOL_STK_CALL_SETUP: ret = responseInts(p); break;
            case RIL_UNSOL_SIM_SMS_STORAGE_FULL: ret =  responseVoid(p); break;
            case RIL_UNSOL_SIM_REFRESH: ret =  responseSimRefresh(p); break;
            case RIL_UNSOL_CALL_RING: ret =  responseCallRing(p); break;
            case RIL_UNSOL_RESTRICTED_STATE_CHANGED: ret = responseInts(p); break;
            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:  ret =  responseVoid(p); break;
            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:  ret =  responseCdmaSms(p); break;
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =  responseRaw(p); break;
            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:  ret =  responseVoid(p); break;
            case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_CALL_WAITING: ret = responseCdmaCallWaiting(p); break;
            case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_INFO_REC: ret = responseCdmaInformationRecord(p); break;
            case RIL_UNSOL_OEM_HOOK_RAW: ret = responseRaw(p); break;
            case RIL_UNSOL_RINGBACK_TONE: ret = responseInts(p); break;
            case RIL_UNSOL_RESEND_INCALL_MUTE: ret = responseVoid(p); break;
            case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED: ret = responseInts(p); break;
            case RIL_UNSOL_CDMA_PRL_CHANGED: ret = responseInts(p); break;
            case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
            case 1035: ret = responseInts(p); break;
			case 1038: ret = responseInts(p); break;
            //samsung stk service implementation
            case RIL_UNSOL_STK_SEND_SMS_RESULT: ret = responseInts(p); break;

            default:
                throw new RuntimeException("Unrecognized unsol response: " + response);
            //break; (implied)
        }} catch (Throwable tr) {
            Log.e(LOG_TAG, "Exception processing unsol response: " + response +
                "Exception:" + tr.toString());
            return;
        }

        switch(response) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
                /* has bonus radio state int */
                RadioState newState = getRadioStateFromInt(p.readInt());
                if (RILJ_LOGD) unsljLogMore(response, newState.toString());

                switchToRadioState(newState);
            break;
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED:
                if (RILJ_LOGD) unsljLog(response);

                mCallStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
            case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED:
                if (RILJ_LOGD) unsljLog(response);

                mVoiceNetworkStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
            case RIL_UNSOL_RESPONSE_NEW_SMS: {
                if (RILJ_LOGD) unsljLog(response);

                // FIXME this should move up a layer
                String a[] = new String[2];

                a[1] = (String)ret;

                SmsMessage sms;

                sms = SmsMessage.newFromCMT(a);
                if (mGsmSmsRegistrant != null) {
                    mGsmSmsRegistrant
                        .notifyRegistrant(new AsyncResult(null, sms, null));
                }
            break;
            }
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mSmsStatusRegistrant != null) {
                    mSmsStatusRegistrant.notifyRegistrant(
                            new AsyncResult(null, ret, null));
                }
            break;
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                int[] smsIndex = (int[])ret;

                if(smsIndex.length == 1) {
                    if (mSmsOnSimRegistrant != null) {
                        mSmsOnSimRegistrant.
                                notifyRegistrant(new AsyncResult(null, smsIndex, null));
                    }
                } else {
                    if (RILJ_LOGD) riljLog(" NEW_SMS_ON_SIM ERROR with wrong length "
                            + smsIndex.length);
                }
            break;
            case RIL_UNSOL_ON_USSD:
                String[] resp = (String[])ret;

                if (resp.length < 2) {
                    resp = new String[2];
                    resp[0] = ((String[])ret)[0];
                    resp[1] = null;
                }
                if (RILJ_LOGD) unsljLogMore(response, resp[0]);
                if (mUSSDRegistrant != null) {
                    mUSSDRegistrant.notifyRegistrant(
                        new AsyncResult (null, resp, null));
                }
            break;
            case RIL_UNSOL_NITZ_TIME_RECEIVED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                // has bonus long containing milliseconds since boot that the NITZ
                // time was received
                long nitzReceiveTime = p.readLong();

                Object[] result = new Object[2];

                result[0] = ret;
                result[1] = Long.valueOf(nitzReceiveTime);

                if (mNITZTimeRegistrant != null) {

                    mNITZTimeRegistrant
                        .notifyRegistrant(new AsyncResult (null, result, null));
                } else {
                    // in case NITZ time registrant isnt registered yet
                    mLastNITZTimeInfo = result;
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
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                boolean oldRil = needsOldRilFeature("skipbrokendatacall");
                if (oldRil && "IP".equals(((ArrayList<DataCallState>)ret).get(0).type))
                    break;

                mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
            break;

            case RIL_UNSOL_SUPP_SVC_NOTIFICATION:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mSsnRegistrant != null) {
                    mSsnRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_SESSION_END:
                if (RILJ_LOGD) unsljLog(response);

                if (mCatSessionEndRegistrant != null) {
                    mCatSessionEndRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_PROACTIVE_COMMAND:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCatProCmdRegistrant != null) {
                    mCatProCmdRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_EVENT_NOTIFY:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCatEventRegistrant != null) {
                    mCatEventRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_STK_CALL_SETUP:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCatCallSetUpRegistrant != null) {
                    mCatCallSetUpRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
                if (RILJ_LOGD) unsljLog(response);

                if (mIccSmsFullRegistrant != null) {
                    mIccSmsFullRegistrant.notifyRegistrant();
                }
                break;

            case RIL_UNSOL_SIM_REFRESH:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mIccRefreshRegistrants != null) {
                    mIccRefreshRegistrants.notifyRegistrants(
                            new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_CALL_RING:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mRingRegistrant != null) {
                    mRingRegistrant.notifyRegistrant(
                            new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_RESTRICTED_STATE_CHANGED:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mRestrictedStateRegistrant != null) {
                    mRestrictedStateRegistrant.notifyRegistrant(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:
                if (RILJ_LOGD) unsljLog(response);

                if (mIccStatusChangedRegistrants != null) {
                    mIccStatusChangedRegistrants.notifyRegistrants();
                }
                break;

            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
                if (RILJ_LOGD) unsljLog(response);

                SmsMessage sms = (SmsMessage) ret;

                if (mCdmaSmsRegistrant != null) {
                    mCdmaSmsRegistrant
                        .notifyRegistrant(new AsyncResult(null, sms, null));
                }
                break;

            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
                if (RILJ_LOGD) unsljLog(response);

                if (mGsmBroadcastSmsRegistrant != null) {
                    mGsmBroadcastSmsRegistrant
                        .notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;

            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:
                if (RILJ_LOGD) unsljLog(response);

                if (mIccSmsFullRegistrant != null) {
                    mIccSmsFullRegistrant.notifyRegistrant();
                }
                break;

            case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE:
                if (RILJ_LOGD) unsljLog(response);

                if (mEmergencyCallbackModeRegistrant != null) {
                    mEmergencyCallbackModeRegistrant.notifyRegistrant();
                }
                break;

            case RIL_UNSOL_CDMA_CALL_WAITING:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCallWaitingInfoRegistrants != null) {
                    mCallWaitingInfoRegistrants.notifyRegistrants(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mOtaProvisionRegistrants != null) {
                    mOtaProvisionRegistrants.notifyRegistrants(
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

            case RIL_UNSOL_OEM_HOOK_RAW:
				unsljLogvRet(response, IccUtils.bytesToHexString((byte[])(byte[])ret));
				ByteBuffer bytebuffer = ByteBuffer.wrap((byte[])(byte[])ret);
				bytebuffer.order(ByteOrder.nativeOrder());
				if(isQcUnsolOemHookResp(bytebuffer))
				{
					Log.d("RILJ", "OEM ID check Passed");
					processUnsolOemhookResponse(bytebuffer);
				} else
				if(super.mUnsolOemHookRawRegistrant != null)
				{
					Log.d("RILJ", "External OEM message, to be notified");
					Registrant registrant = super.mUnsolOemHookRawRegistrant;
					AsyncResult asyncresult3 = new AsyncResult(null, ret, null);
					registrant.notifyRegistrant(asyncresult3);
				}
				break;

            case RIL_UNSOL_RINGBACK_TONE:
                if (RILJ_LOGD) unsljLogvRet(response, ret);
                if (mRingbackToneRegistrants != null) {
                    boolean playtone = (((int[])ret)[0] == 1);
                    mRingbackToneRegistrants.notifyRegistrants(
                                        new AsyncResult (null, playtone, null));
                }
                break;

            case RIL_UNSOL_RESEND_INCALL_MUTE:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mResendIncallMuteRegistrants != null) {
                    mResendIncallMuteRegistrants.notifyRegistrants(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCdmaSubscriptionChangedRegistrants != null) {
                    mCdmaSubscriptionChangedRegistrants.notifyRegistrants(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_CDMA_PRL_CHANGED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mCdmaPrlChangedRegistrants != null) {
                    mCdmaPrlChangedRegistrants.notifyRegistrants(
                                        new AsyncResult (null, ret, null));
                }
                break;

            case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mExitEmergencyCallbackModeRegistrants != null) {
                    mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
                                        new AsyncResult (null, null, null));
                }
                break;

            case RIL_UNSOL_RIL_CONNECTED: {
                if (RILJ_LOGD) unsljLogRet(response, ret);

                // Initial conditions
                setRadioPower(false, null);
                setPreferredNetworkType(mPreferredNetworkType, null);
                setCdmaSubscriptionSource(mCdmaSubscription, null);
                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                break;
            }

            //samsung stk service implementation
            case RIL_UNSOL_STK_SEND_SMS_RESULT:
                if (Resources.getSystem().
                        getBoolean(com.android.internal.R.bool.config_samsung_stk)) {
                    if (RILJ_LOGD) unsljLogRet(response, ret);

                    if (mCatSendSmsResultRegistrant != null) {
                        mCatSendSmsResultRegistrant.notifyRegistrant(
                                new AsyncResult (null, ret, null));
                    }
                }
                break;
            case 1035: 
				break;
			case 1038: 
				break;

        }
    }
	
    private boolean isQcUnsolOemHookResp(ByteBuffer oemHookResponse) {
        String mOemIdentifier = "QUALCOMM";
        int INT_SIZE = 4;
        int mHeaderSize = mOemIdentifier.length() + 2 * INT_SIZE;

        /* Check OEM ID in UnsolOemHook response */
        if (oemHookResponse.capacity() < mHeaderSize) {
            /*
             * size of UnsolOemHook message is less than expected, considered as
             * External OEM's message
             */
            Log.d(LOG_TAG, "RIL_UNSOL_OEM_HOOK_RAW data size is " + oemHookResponse.capacity());
            return false;
        } else {
            byte[] oem_id_bytes = new byte[mOemIdentifier.length()];
            oemHookResponse.get(oem_id_bytes);
            String oem_id_str = new String(oem_id_bytes);
            Log.d(LOG_TAG, "Oem ID in RIL_UNSOL_OEM_HOOK_RAW is " + oem_id_str);
            if (!oem_id_str.equals(mOemIdentifier)) {
                /* OEM ID not matched, considered as External OEM's message */
                return false;
            }
        }
        return true;
    }

    private void processUnsolOemhookResponse(ByteBuffer oemHookResponse) {
        /** Starting number for QCRILHOOK request and response IDs */
        final int QCRILHOOK_BASE = 0x80000;

        /** qcrilhook unsolicited response IDs */
        final int QCRILHOOK_UNSOL_EXTENDED_DBM_INTL = QCRILHOOK_BASE + 1000;
        final int QCRILHOOK_UNSOL_CDMA_BURST_DTMF = QCRILHOOK_BASE + 1001;
        final int QCRILHOOK_UNSOL_CDMA_CONT_DTMF_START = QCRILHOOK_BASE + 1002;
        final int QCRILHOOK_UNSOL_CDMA_CONT_DTMF_STOP = QCRILHOOK_BASE + 1003;
        final int QCRILHOOK_UNSOL_CALL_REESTABLISH_IND = QCRILHOOK_BASE + 1004;

        int response_id = 0, response_size = 0;

        response_id = oemHookResponse.getInt();
        Log.d(LOG_TAG, "Response ID in RIL_UNSOL_OEM_HOOK_RAW is " + response_id);

        response_size = oemHookResponse.getInt();
        if (response_size < 0) {
            Log.e(LOG_TAG, "Response Size is Invalid " + response_size);
            return;
        }
        byte[] response_data = new byte[response_size];
        oemHookResponse.get(response_data, 0, response_size);

        switch (response_id) {
            case QCRILHOOK_UNSOL_CDMA_BURST_DTMF:
                notifyCdmaFwdBurstDtmf(response_data);
                break;

            case QCRILHOOK_UNSOL_CDMA_CONT_DTMF_START:
                notifyCdmaFwdContDtmfStart(response_data);
                break;

            case QCRILHOOK_UNSOL_CDMA_CONT_DTMF_STOP:
                notifyCdmaFwdContDtmfStop();
                break;

            case QCRILHOOK_UNSOL_CALL_REESTABLISH_IND:
                notifyCallReestablish();
                break;

            default:
                Log.d(LOG_TAG, "Response ID " + response_id + "is not served in this process.");
                Log.d(LOG_TAG, "To broadcast an Intent via the notifier to external apps");
                if (mUnsolOemHookExtAppRegistrant != null) {
                    oemHookResponse.rewind();
                    byte[] origData = oemHookResponse.array();
                    mUnsolOemHookExtAppRegistrant.notifyRegistrant(new AsyncResult(null, origData,
                            null));
                }
	             break;
        }

    }

    /** Notify registrants of FWD Burst DTMF Tone. */
    protected void notifyCdmaFwdBurstDtmf(byte[] data) {
        AsyncResult ar = new AsyncResult(null, data, null);
        mCdmaFwdBurstDtmfRegistrants.notifyRegistrants(ar);
    }

    /** Notify registrants of FWD Continuous DTMF Tone Start. */
    protected void notifyCdmaFwdContDtmfStart(byte[] data) {
        AsyncResult ar = new AsyncResult(null, data, null);
        mCdmaFwdContDtmfStartRegistrants.notifyRegistrants(ar);
    }

    /** Notify registrants of FWD Continuous DTMF Tone Stop. */
    protected void notifyCdmaFwdContDtmfStop() {
        AsyncResult ar = new AsyncResult(null, null, null);
        mCdmaFwdContDtmfStopRegistrants.notifyRegistrants(ar);
    }

    /** Notify registrants of call progress info indications */
    protected void notifyCallReestablish() {
        AsyncResult ar = new AsyncResult(null, null, null);
        mCallReestablishIndRegistrants.notifyRegistrants(ar);
    }
	
    private Object responseSimRefresh(Parcel parcel)
    {
        int ai[] = new int[2];
        ai[0] = parcel.readInt();
        parcel.readString();
        ai[1] = parcel.readInt();
        return ai;
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

                        if (mPhoneType == RILConstants.CDMA_PHONE) {
                            mRil.setRadioState(CommandsInterface.RadioState.RUIM_LOCKED_OR_ABSENT);
                        } else {
                            mRil.setRadioState(CommandsInterface.RadioState.SIM_LOCKED_OR_ABSENT);
                        }
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
                                        mRil.setRadioState(CommandsInterface.RadioState.SIM_LOCKED_OR_ABSENT);
                                        break;
                                    case APPTYPE_CSIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RUIM_LOCKED_OR_ABSENT);
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
                                        mRil.setRadioState(CommandsInterface.RadioState.SIM_READY);
                                        break;
                                    case APPTYPE_CSIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RUIM_READY);
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
					case EVENT_GET_ICC_STATUS_DONE:
                    mRadioOn = true;
                    Log.d(LOG_TAG, "Radio on -> Forcing sim status update");
                    sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
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
}