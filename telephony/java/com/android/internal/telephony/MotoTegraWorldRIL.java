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
import android.os.AsyncResult;
import android.os.Message;
import android.os.Parcel;
import android.util.Log;

import static com.android.internal.telephony.RILConstants.*;

import com.android.internal.telephony.gsm.NetworkInfo;

import java.util.ArrayList;

/**
 * Motorola Tegra2 World RIL class
 *
 * {@hide}
 */
public class MotoTegraWorldRIL extends MotoTegraRIL {

    public MotoTegraWorldRIL(Context context, int networkMode, int cdmaSubscription) {
        // for testing purposes, we're forcing Global. Will be reverted in due time.
        super(context, RILConstants.NETWORK_MODE_GLOBAL, cdmaSubscription);

        Log.i("TegraWorld", "Network mode is this at creation of object: "
              + String.valueOf(networkMode) + ", but has been forced to Global");

        // BUG: On CDMA Only, networkmode still is 0. That forces GSM_Phone,
        // since it's WCDMA-Preferred... by default.
    }

}
