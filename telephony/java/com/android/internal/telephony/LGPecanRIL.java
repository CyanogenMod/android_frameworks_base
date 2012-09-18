package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL;

import java.util.ArrayList;

import android.content.Context;
import android.os.Message;
import android.os.Parcel;

import com.android.internal.telephony.gsm.NetworkInfo;

public class LGPecanRIL extends RIL implements CommandsInterface {

	public LGPecanRIL(Context context) {
		super(context);
	}

    public LGPecanRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    public void
    setNetworkSelectionModeManual(String operatorNumeric, Message response) {

        RILRequest rr = RILRequest.obtain(RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL, response);

        if (RILJ_LOGD)
            riljLog(rr.serialString() + "> " + requestToString(rr.mRequest) + " " + operatorNumeric);

        rr.mp.writeInt(2);
        rr.mp.writeString(operatorNumeric);
        rr.mp.writeString("NOCHANGE");

        send(rr);
    }

    @Override
    protected Object
    responseNetworkInfos(Parcel p) {
        String strings[] = (String [])responseStrings(p);

        if (strings.length % 5 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
                + strings.length + " strings, expected multiple of 5");
        }

        ArrayList<NetworkInfo> ret = new ArrayList<NetworkInfo>(strings.length / 5);

        for (int i = 0 ; i < strings.length ; i += 5) {
            ret.add (
                new NetworkInfo(
                    strings[i+0],
                    strings[i+1],
                    strings[i+2],
                    strings[i+3]));
        }

        return ret;
    }
}
