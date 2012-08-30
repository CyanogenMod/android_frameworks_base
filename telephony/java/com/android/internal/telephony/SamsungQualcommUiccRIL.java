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
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.os.AsyncResult;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.RILConstants;

import java.util.ArrayList;

/**
 * Custom RIL to handle unique behavior of Hercules/Skyrocket/Note radio
 *
 * {@hide}
 */
public class SamsungQualcommUiccRIL extends QualcommSharedRIL implements CommandsInterface {
    boolean RILJ_LOGV = true;
    boolean RILJ_LOGD = true;

    public static final int INVALID_SNR = 0x7fffffff;
    private boolean mSignalbarCount = SystemProperties.getBoolean("ro.telephony.sends_barcount", false);
    private Object mSMSLock = new Object();
    private boolean mIsSendingSMS = false;
    public static final long SEND_SMS_TIMEOUT_IN_MS = 30000;

    public SamsungQualcommUiccRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
        mQANElements = 4;
    }

    @Override
    protected void
    processUnsolicited (Parcel p) {
        Object ret;
        int dataPosition = p.dataPosition();   // save off position within the Parcel
        int response     = p.readInt();

        switch(response) {
            case RIL_UNSOL_NITZ_TIME_RECEIVED:
                handleNitzTimeReceived(p);
                return;
            case 1038: ret = responseVoid(p); break; // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch(response) {
            case 1038: // RIL_UNSOL_DATA_NETWORK_STATE_CHANGED
                if (RILJ_LOGD) unsljLog(response);

                // Notifying on voice state change since it just causes a
                // GsmServiceStateTracker::pollState() like CAF RIL does.
                mVoiceNetworkStateRegistrants
                    .notifyRegistrants(new AsyncResult(null, null, null));
            break;
        }
    }

    protected void
    handleNitzTimeReceived(Parcel p) {
        String nitz = (String)responseString(p);
        if (RILJ_LOGD) unsljLogRet(RIL_UNSOL_NITZ_TIME_RECEIVED, nitz);

        // has bonus long containing milliseconds since boot that the NITZ
        // time was received
        long nitzReceiveTime = p.readLong();

        Object[] result = new Object[2];

        String fixedNitz = nitz;
        String[] nitzParts = nitz.split(",");
        if (nitzParts.length == 4) {
            // 0=date, 1=time+zone, 2=dst, 3=garbage that confuses GsmServiceStateTracker (so remove it)
            fixedNitz = nitzParts[0]+","+nitzParts[1]+","+nitzParts[2]+",";
        }

        result[0] = fixedNitz;
        result[1] = Long.valueOf(nitzReceiveTime);

        if (mNITZTimeRegistrant != null) {

            mNITZTimeRegistrant
                .notifyRegistrant(new AsyncResult (null, result, null));
        } else {
            // in case NITZ time registrant isnt registered yet
            mLastNITZTimeInfo = result;
        }
    }

    @Override
    public void
    sendSMS (String smscPDU, String pdu, Message result) {
        // Do not send a new SMS until the response for the previous SMS has been received
        //   * for the error case where the response never comes back, time out after
        //     30 seconds and just try the next SEND_SMS
        synchronized (mSMSLock) {
            long timeoutTime  = SystemClock.elapsedRealtime() + SEND_SMS_TIMEOUT_IN_MS;
            long waitTimeLeft = SEND_SMS_TIMEOUT_IN_MS;
            while (mIsSendingSMS && (waitTimeLeft > 0)) {
                Log.d(LOG_TAG, "sendSMS() waiting for response of previous SEND_SMS");
                try {
                    mSMSLock.wait(waitTimeLeft);
                } catch (InterruptedException ex) {
                    // ignore the interrupt and rewait for the remainder
                }
                waitTimeLeft = timeoutTime - SystemClock.elapsedRealtime();
            }
            if (waitTimeLeft <= 0) {
                Log.e(LOG_TAG, "sendSms() timed out waiting for response of previous CDMA_SEND_SMS");
            }
            mIsSendingSMS = true;
        }

        super.sendSMS(smscPDU, pdu, result);
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
        status.setCdmaSubscriptionAppIndex(p.readInt() );

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
            if ((ca.app_state == IccCardApplication.AppState.APPSTATE_SUBSCRIPTION_PERSO) &&
                ((ca.perso_substate == IccCardApplication.PersoSubState.PERSOSUBSTATE_READY) ||
                (ca.perso_substate == IccCardApplication.PersoSubState.PERSOSUBSTATE_UNKNOWN))) {
                // ridiculous hack for network SIM unlock pin
                ca.app_state = IccCardApplication.AppState.APPSTATE_UNKNOWN;
                Log.d(LOG_TAG, "ca.app_state == AppState.APPSTATE_SUBSCRIPTION_PERSO");
                Log.d(LOG_TAG, "ca.perso_substate == PersoSubState.PERSOSUBSTATE_READY");
            }
            ca.aid            = p.readString();
            ca.app_label      = p.readString();
            ca.pin1_replaced  = p.readInt();
            ca.pin1           = ca.PinStateFromRILInt(p.readInt());
            ca.pin2           = ca.PinStateFromRILInt(p.readInt());

            p.readInt(); //remaining_count_pin1   - pin1_num_retries
            p.readInt(); //remaining_count_puk1   - puk1_num_retries
            p.readInt(); //remaining_count_pin2   - pin2_num_retries
            p.readInt(); //remaining_count_puk2   - puk2_num_retries
            p.readInt(); //                       - perso_unblock_retries
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
            Log.d(LOG_TAG, "mAid " + mAid + " mUSIM=" + mUSIM + " mSetPreferredNetworkType=" + mSetPreferredNetworkType);
        }

        return status;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        // This is a mashup of algorithms used in
        // LGEQualcommUiccRIL.java and SamsungHCRIL.java

        // Get raw data
        response = new int[numInts];
        for (int i = 0 ; i < numInts ; i++) {
            response[i] = p.readInt();
        }
        Log.d(LOG_TAG, "responseSignalStength BEFORE: mode=" + (mSignalbarCount ? "bars" : "raw") +
            " gsmDbm=" + response[0] + " gsmEcio=" + response[1] +
            " lteSignalStrength=" + response[7] + " lteRsrp=" + response[8] + " lteRsrq=" + response[9] +
            " lteRssnr=" + response[10] + " lteCqi=" + response[11]);

        // RIL_GW_SignalStrength
        if (mSignalbarCount) {
            //Samsung sends the count of bars that should be displayed instead of
            //a real signal strength
            int num_bars = (response[0] & 0xff00) >> 8;

            // Translate number of bars into something SignalStrength.java can understand
            switch (num_bars) {
                case 0  : response[0] = 1;     break; // map to 0 bars
                case 1  : response[0] = 3;     break; // map to 1 bar
                case 2  : response[0] = 5;     break; // map to 2 bars
                case 3  : response[0] = 8;     break; // map to 3 bars
                case 4  : response[0] = 12;    break; // map to 4 bars
                case 5  : response[0] = 15;    break; // map to 4 bars but give an extra 10 dBm
                default : response[0] &= 0xff; break; // no idea; just pass value through
            }
        } else {
            response[0] &= 0xff; //gsmDbm
        }
        response[1] = -1; // gsmEcio

        // RIL_CDMA_SignalStrength (unused)
        response[2] = -1; // cdmaDbm
        response[3] = -1; // cdmaEcio

        // RIL_EVDO_SignalStrength (unused)
        response[4] = -1; // evdoRssi
        response[5] = -1; // evdoEcio
        response[6] = -1; // evdoSNR

        // RIL_LTE_SignalStrength
        if (response[7] == 99) {
            // If LTE is not enabled, clear LTE results
            // 7-11 must be -1 for GSM signal strength to be used (see frameworks/base/telephony/java/android/telephony/SignalStrength.java)
            response[7]  = -1; // lteSignalStrength
            response[8]  = -1; // lteRsrp
            response[9]  = -1; // lteRsrq
            response[10] = -1; // lteRssnr
            response[11] = -1; // lteCqi
        } else if (mSignalbarCount) {
            int num_bars = (response[7] & 0xff00) >> 8;
            response[7] &= 0xff;        // remove the Samsung number of bars field
            response[10] = INVALID_SNR; // mark lteRssnr invalid so it doesn't get used

            // Translate number of bars into something SignalStrength.java can understand
            switch (num_bars) {
                case 0  : response[8] = -1;   break; // map to 0 bars
                case 1  : response[8] = -116; break; // map to 1 bar
                case 2  : response[8] = -115; break; // map to 2 bars
                case 3  : response[8] = -105; break; // map to 3 bars
                case 4  : response[8] = -95;  break; // map to 4 bars
                case 5  : response[8] = -85;  break; // map to 4 bars but give an extra 10 dBm
                default : response[8] *= -1;  break; // no idea; just pass value through
            }
        } else {
            response[7] &= 0xff;  // remove the Samsung number of bars field
            response[8] *= -1;
        }

        Log.d(LOG_TAG, "responseSignalStength AFTER: mode=" + (mSignalbarCount ? "bars" : "raw") +
            " gsmDbm=" + response[0] + " gsmEcio=" + response[1] +
            " lteSignalStrength=" + response[7] + " lteRsrp=" + response[8] + " lteRsrq=" + response[9] +
            " lteRssnr=" + response[10] + " lteCqi=" + response[11]);
        return response;
    }

    @Override
    protected Object
    responseSMS(Parcel p) {
        // Notify that sendSMS() can send the next SMS
        synchronized (mSMSLock) {
            mIsSendingSMS = false;
            mSMSLock.notify();
        }

        return super.responseSMS(p);
    }
}
