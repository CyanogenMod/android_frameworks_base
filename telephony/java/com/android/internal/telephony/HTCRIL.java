/*
 * Copyright (C) 2011 The CyanogenMod Project
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

import android.content.Context;
import android.os.Message;
import android.os.Parcel;
import android.util.Log;

import com.android.internal.telephony.gsm.NetworkInfo;

import java.util.ArrayList;

/**
 * HTC RIL class
 *
 * {@hide}
 */
public class HTCRIL extends RIL implements CommandsInterface {
    public HTCRIL(Context context) {
        super(context);
    }

    public HTCRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseNetworkInfos(Parcel p) {
        String strings[] = (String [])responseStrings(p);
        ArrayList<NetworkInfo> ret;

        if (strings.length % 5 != 0) {
            throw new RuntimeException(
                "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
                + strings.length + " strings, expected multible of 5");
        }

        ret = new ArrayList<NetworkInfo>(strings.length / 5);

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
