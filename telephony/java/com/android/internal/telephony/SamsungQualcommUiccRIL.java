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
import android.os.AsyncResult;
import android.text.TextUtils;
import android.util.Log;
import com.android.internal.telephony.RILConstants;

/**
 * Custom RIL to handle unique behavior of Hercules/Skyrocket/Note radio
 *
 * {@hide}
 */
public class SamsungQualcommUiccRIL extends LGEQualcommUiccRIL implements CommandsInterface {
    boolean RILJ_LOGV = true;
    boolean RILJ_LOGD = true;

    public SamsungQualcommUiccRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
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
    setupDataCall(String radioTechnology, String profile, String apn,
            String user, String password, String authType, String ipVersion,
            Message result) {
        RILRequest rr
                = RILRequest.obtain(RIL_REQUEST_SETUP_DATA_CALL, result);

        rr.mp.writeInt(7);

        rr.mp.writeString(radioTechnology);
        rr.mp.writeString(profile);
        rr.mp.writeString(apn);
        rr.mp.writeString(user);
        rr.mp.writeString(password);
        rr.mp.writeString(authType);
        rr.mp.writeString("IP"); // ipVersion

        if (RILJ_LOGD) riljLog(rr.serialString() + "> "
                + requestToString(rr.mRequest) + " " + radioTechnology + " "
                + profile + " " + apn + " " + user + " "
                + password + " " + authType + " " + ipVersion);

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

        // RIL_GW_SignalStrength
        boolean mSignalbarCount = SystemProperties.getBoolean("telephony.sends_barcount", true);
        if(mSignalbarCount) {
            //Samsung sends the count of bars that should be displayed instead of
            //a real signal strength
            response[0] = ((response[0] & 0xFF00) >> 8) * 3; //gsmDbm
            if ((response[0] > 0) && (response[0] != 99)) {
                response[0]--;   // correct down by 1 dBm to match stock's behavior
            }
        } else {
            response[0] = response[0] & 0xFF; //gsmDbm
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
            response[7]  = -1;
            response[8]  = -1;
            response[9]  = -1;
            response[10] = -1;
            response[11] = -1;
        } else {
            response[8] *= -1;
        }

        return response;
    }
}
