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
import android.os.Parcel;
import android.os.AsyncResult;
import android.util.Log;
import com.android.internal.telephony.RILConstants;

/**
 * Custom RIL to handle unique behavior of Samsung's MSM7X30 devices radio
 *
 * {@hide}
 */
public class SamsungMSMRIL extends RIL implements CommandsInterface {

    public SamsungMSMRIL(Context context, int networkMode, int cdmaSubscription) {
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

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
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
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        /* TODO: Add SignalStrength class to match RIL_SignalStrength */
        response = new int[numInts];
        for (int i = 0 ; i < 7 ; i++) {
            response[i] = p.readInt();
        }
        // SamsungRIL is a v3 RIL, fill the rest with -1
        for (int i = 7; i < numInts; i++) {
            response[i] = -1;
        }

        response[0] = response[0] & 0xFF; // gsmDbm

        return response;
    }
}
