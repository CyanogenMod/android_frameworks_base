package com.android.internal.telephony;

import java.util.ArrayList;
import java.util.Collections;

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
import com.android.internal.telephony.gsm.NetworkInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.IccCardApplication;
import com.android.internal.telephony.IccCardStatus;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;

import android.util.Log;

public class SamsungRIL extends RIL implements CommandsInterface {

	private boolean mSignalbarCount = SystemProperties.getInt("ro.telephony.sends_barcount", 0) == 1 ? true : false;

	private boolean mIsSamsungCdma = SystemProperties.getBoolean("ro.ril.samsung_cdma", false);

public SamsungRIL(Context context) {
		super(context);
	}

public SamsungRIL(Context context, int networkMode, int cdmaSubscription) {
	super(context, networkMode, cdmaSubscription);
}

//SAMSUNG SGS STATES
    static final int RIL_UNSOL_STK_SEND_SMS_RESULT = 11002;
    static final int RIL_UNSOL_O2_HOME_ZONE_INFO = 11007;
    static final int RIL_UNSOL_DEVICE_READY_NOTI = 11008;
    static final int RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST_2 = 11011;
    static final int RIL_UNSOL_HSDPA_STATE_CHANGED = 11016;
    static final int RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST = 11012;

@Override
 public void
    setRadioPower(boolean on, Message result) {
        //if radio is OFF set preferred NW type and cmda subscription
        if(mInitialRadioStateChange) {
            synchronized (mStateMonitor) {
                if (!mState.isOn()) {
                    RILRequest rrPnt = RILRequest.obtain(
                                   RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, null);

                    rrPnt.mp.writeInt(1);
                    rrPnt.mp.writeInt(mNetworkMode);
                    if (RILJ_LOGD) riljLog(rrPnt.serialString() + "> "
                        + requestToString(rrPnt.mRequest) + " : " + mNetworkMode);

                    send(rrPnt);

                    RILRequest rrCs = RILRequest.obtain(
                                   RIL_REQUEST_CDMA_SET_SUBSCRIPTION, null);
                    rrCs.mp.writeInt(1);
                    rrCs.mp.writeInt(mCdmaSubscription);
                    if (RILJ_LOGD) riljLog(rrCs.serialString() + "> "
                    + requestToString(rrCs.mRequest) + " : " + mCdmaSubscription);
                    send(rrCs);
                }
            }
        }
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);

        //samsung crap for airplane mode
		if (on)
		{
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
        boolean found = false;

        serial = p.readInt();
        error = p.readInt();

	      Log.d(LOG_TAG,"Serial: "+ serial);
        Log.d(LOG_TAG,"Error: "+ error);

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
            case RIL_REQUEST_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_GPRS_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
            case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
            case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseStrings(p); break;
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
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseNetworkInfos(p); break;
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
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseInts(p); break;
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
            case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION: ret =  responseVoid(p); break;
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
            //ugly fix for Samsung messing up SMS_SEND request fail in binary RIL
	         if(!(error == -1 && rr.mRequest == RIL_REQUEST_SEND_SMS))
	         {
                rr.onError(error, ret);
                rr.release();
                return;
	         } else {
		        try
                {
                    ret =  responseSMS(p);
                } catch (Throwable tr) {
                        Log.w(LOG_TAG, rr.serialString() + "< "
                        + requestToString(rr.mRequest)
                        + " exception, Processing Samsung SMS fix ", tr);
                        rr.onError(error, ret);
                        rr.release();
                        return;
                }
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
	            case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
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
	            case RIL_UNSOL_SIM_REFRESH: ret =  responseInts(p); break;
	            case RIL_UNSOL_CALL_RING: ret =  responseCallRing(p); break;
	            case RIL_UNSOL_RESTRICTED_STATE_CHANGED: ret = responseInts(p); break;
	            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:  ret =  responseVoid(p); break;
	            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:  ret =  responseCdmaSms(p); break;
	            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =  responseString(p); break;
	            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:  ret =  responseVoid(p); break;
	            case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
	            case RIL_UNSOL_CDMA_CALL_WAITING: ret = responseCdmaCallWaiting(p); break;
	            case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: ret = responseInts(p); break;
	            case RIL_UNSOL_CDMA_INFO_REC: ret = responseCdmaInformationRecord(p); break;
	            case RIL_UNSOL_OEM_HOOK_RAW: ret = responseRaw(p); break;
	            case RIL_UNSOL_RINGBACK_TONE: ret = responseInts(p); break;
	            case RIL_UNSOL_RESEND_INCALL_MUTE: ret = responseVoid(p); break;
	            case RIL_UNSOL_HSDPA_STATE_CHANGED: ret = responseVoid(p); break;

	            //fixing anoying Exceptions caused by the new Samsung states
	            //FIXME figure out what the states mean an what data is in the parcel

	            case RIL_UNSOL_O2_HOME_ZONE_INFO: ret = responseVoid(p); break;
	            case RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST: ret = responseVoid(p); break;
	            case RIL_UNSOL_STK_SEND_SMS_RESULT: ret = responseVoid(p); break;
	            case RIL_UNSOL_DEVICE_READY_NOTI: ret = responseVoid(p); break;
	            case RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST_2: ret = responseVoid(p); break;

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
	            case RIL_UNSOL_HSDPA_STATE_CHANGED:
	                if (RILJ_LOGD) unsljLog(response);

	                mNetworkStateRegistrants
	                    .notifyRegistrants(new AsyncResult(null, null, null));
	            break;

	            case RIL_UNSOL_RESPONSE_NETWORK_STATE_CHANGED:
	                if (RILJ_LOGD) unsljLog(response);

	                mNetworkStateRegistrants
	                    .notifyRegistrants(new AsyncResult(null, null, null));
	            break;
	            case RIL_UNSOL_RESPONSE_NEW_SMS: {
	                if (RILJ_LOGD) unsljLog(response);

	                // FIXME this should move up a layer
	                String a[] = new String[2];

	                a[1] = (String)ret;

	                SmsMessage sms;

	                sms = SmsMessage.newFromCMT(a);
	                if (mSMSRegistrant != null) {
	                    mSMSRegistrant
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

			String nitz = (String)ret;
			if (RILJ_LOGD) riljLog(" RIL_UNSOL_NITZ_TIME_RECEIVED length = "
	                            + nitz.split("[/:,+-]").length);
			//remove the tailing information that samsung added to the string
			//it will screw the NITZ parser
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
	            case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
	                if (RILJ_LOGD) unsljLogRet(response, ret);

	                mDataConnectionRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
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

	                if (mStkSessionEndRegistrant != null) {
	                    mStkSessionEndRegistrant.notifyRegistrant(
	                                        new AsyncResult (null, ret, null));
	                }
	                break;

	            case RIL_UNSOL_STK_PROACTIVE_COMMAND:
	                if (RILJ_LOGD) unsljLogRet(response, ret);

	                if (mStkProCmdRegistrant != null) {
	                    mStkProCmdRegistrant.notifyRegistrant(
	                                        new AsyncResult (null, ret, null));
	                }
	                break;

	            case RIL_UNSOL_STK_EVENT_NOTIFY:
	                if (RILJ_LOGD) unsljLogRet(response, ret);

	                if (mStkEventRegistrant != null) {
	                    mStkEventRegistrant.notifyRegistrant(
	                                        new AsyncResult (null, ret, null));
	                }
	                break;

	            case RIL_UNSOL_STK_CALL_SETUP:
	                if (RILJ_LOGD) unsljLogRet(response, ret);

	                if (mStkCallSetUpRegistrant != null) {
	                    mStkCallSetUpRegistrant.notifyRegistrant(
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

	                if (mIccRefreshRegistrant != null) {
	                    mIccRefreshRegistrant.notifyRegistrant(
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

	                if (mSMSRegistrant != null) {
	                    mSMSRegistrant
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
	                if (RILJ_LOGD) unsljLogvRet(response, IccUtils.bytesToHexString((byte[])ret));
	                if (mUnsolOemHookRawRegistrant != null) {
	                    mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
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
	        }
	    }

	@Override
	 protected Object
	    responseCallList(Parcel p) {
			int num;
			int voiceSettings;
			ArrayList<DriverCall> response;
			DriverCall dc;
			int dataAvail = p.dataAvail();
			int pos = p.dataPosition();
			int size = p.dataSize();

			Log.d(LOG_TAG, "Parcel size = " + size);
			Log.d(LOG_TAG, "Parcel pos = " + pos);
			Log.d(LOG_TAG, "Parcel dataAvail = " + dataAvail);

			//Samsung fucked up here

            num = p.readInt();

            Log.d(LOG_TAG, "num = " + num);
            response = new ArrayList<DriverCall>(num);

            for (int i = 0 ; i < num ; i++) {
                if (mIsSamsungCdma)
                    dc = new SamsungDriverCall();
                else
                    dc = new DriverCall();

                dc.state = DriverCall.stateFromCLCC(p.readInt());
                Log.d(LOG_TAG, "state = " + dc.state);
                dc.index = p.readInt();
                Log.d(LOG_TAG, "index = " + dc.index);
                dc.TOA = p.readInt();
                Log.d(LOG_TAG, "state = " + dc.TOA);
                dc.isMpty = (0 != p.readInt());
                Log.d(LOG_TAG, "isMpty = " + dc.isMpty);
                dc.isMT = (0 != p.readInt());
                Log.d(LOG_TAG, "isMT = " + dc.isMT);
                dc.als = p.readInt();
                Log.d(LOG_TAG, "als = " + dc.als);
                voiceSettings = p.readInt();
                dc.isVoice = (0 == voiceSettings) ? false : true;
                Log.d(LOG_TAG, "isVoice = " + dc.isVoice);
                dc.isVoicePrivacy =  (0 != p.readInt());
                //Some Samsung magic data for Videocalls
                voiceSettings = p.readInt();
                //printing it to cosole for later investigation
                Log.d(LOG_TAG, "Samsung magic = " + voiceSettings);
                dc.number = p.readString();
                Log.d(LOG_TAG, "number = " + dc.number);
                int np = p.readInt();
                Log.d(LOG_TAG, "np = " + np);
                dc.numberPresentation = DriverCall.presentationFromCLIP(np);
                dc.name = p.readString();
                Log.d(LOG_TAG, "name = " + dc.name);
                dc.namePresentation = p.readInt();
                Log.d(LOG_TAG, "namePresentation = " + dc.namePresentation);
                int uusInfoPresent = p.readInt();
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
	protected Object
	    responseSignalStrength(Parcel p) {
	        int numInts = 7;
	        int response[];

	        /* TODO: Add SignalStrength class to match RIL_SignalStrength */
	        response = new int[numInts];
	        for (int i = 0 ; i < numInts ; i++) {
	            response[i] = p.readInt();
	        }

		/* Matching Samsung signal strength to asu.
		   Method taken from Samsungs cdma/gsmSignalStateTracker */
		if(mSignalbarCount)
		{
			//Samsung sends the count of bars that should be displayed instead of
			//a real signal strength
			response[0] = ((response[0] & 0xFF00) >> 8) * 3; //gsmDbm
		} else {
			response[0] = response[0] & 0xFF; //gsmDbm
		}
		response[1] = -1; //gsmEcio
		response[2] = (response[2] < 0)?-120:-response[2]; //cdmaDbm
		response[3] = (response[3] < 0)?-160:-response[3]; //cdmaEcio
		response[4] = (response[4] < 0)?-120:-response[4]; //evdoRssi
		response[5] = (response[5] < 0)?-1:-response[5]; //evdoEcio
		if(response[6] < 0 || response[6] > 8)
			response[6] = -1;

	        return response;
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
			sendPreferedNetworktype(networkType, response);
		}
    }


	//Sends the real RIL request to the modem.
	private void sendPreferedNetworktype(int networkType, Message response) {
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
						sendPreferedNetworktype(mDesiredNetworkType, obtainMessage(MESSAGE_SET_PREFERRED_NETWORK_TYPE));
						mDesiredNetworkType = -1;
		            }
		        }
		    }
		}
	}
