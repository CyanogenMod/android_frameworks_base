package com.android.internal.telephony;

import android.content.Context;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.util.Log;
import static com.android.internal.telephony.RILConstants.*;

public class SamsungHCRIL extends RIL implements CommandsInterface {

    private boolean mSignalbarCount = SystemProperties.getInt("ro.telephony.sends_barcount", 0) == 1 ? true : false;        

    public SamsungHCRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }
    static final String LOG_TAG = "RILJ";
    //SAMSUNG SGS STATES
    static final int RIL_UNSOL_STK_SEND_SMS_RESULT = 11002;
    static final int RIL_UNSOL_O2_HOME_ZONE_INFO = 11007;
    static final int RIL_UNSOL_DEVICE_READY_NOTI = 11008;
    static final int RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST_3 = 11010;
    static final int RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST_2 = 11011;
    static final int RIL_UNSOL_HSDPA_STATE_CHANGED = 11016;
    static final int RIL_UNSOL_SAMSUNG_UNKNOWN_MAGIC_REQUEST = 11012;
    static final int RIL_REQUEST_DIAL_EMERGENCY = 10016;

    @Override
    public void
    setRadioPower(boolean on, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);
        //samsung stuff for airplane mode
        if (on) {
            rr.mp.writeInt(1);
            rr.mp.writeInt(1);
        } else {
            rr.mp.writeInt(2);
            rr.mp.writeInt(0);
            rr.mp.writeInt(0);
        }
        if (RILJ_LOGD) {
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
                    + (on ? " on" : " off"));
        }
        send(rr);
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        boolean oldRil = needsOldRilFeature("signalstrength");

        /* TODO: Add SignalStrength class to match RIL_SignalStrength */
        response = new int[numInts];
        for (int i = 0 ; i < numInts ; i++) {
            if (oldRil && i > 6 && i < 12) {
                response[i] = -1;
            } else {
                response[i] = p.readInt();
            }
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

}
