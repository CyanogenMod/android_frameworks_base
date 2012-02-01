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

import android.content.Context;
import android.os.Parcel;
import android.util.Log;

import com.android.internal.telephony.gsm.NetworkInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import java.util.ArrayList;

/**
 * RIL class for Motorola Wrigley 3G RILs which need
 * supplementary service notification post-processing
 *
 * {@hide}
 */
public class MotoWrigley3GRIL extends RIL {
    public MotoWrigley3GRIL(Context context) {
        super(context);
    }

    public MotoWrigley3GRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseSuppServiceNotification(Parcel p) {
        SuppServiceNotification notification = new SuppServiceNotification();

        notification.notificationType = p.readInt();
        notification.code = p.readInt();
        notification.index = p.readInt();
        notification.type = p.readInt();
        notification.number = p.readString();

        /**
         * Moto's RIL seems to confuse code2 0 ('forwarded call') and
         * 10 ('additional incoming call forwarded') and sends 10 when an
         * incoming call is forwarded and _no_ call is currently active.
         * It never sends 10 where it would be appropriate, so it's safe
         * to just convert every occurence of 10 to 0.
         */
        if (notification.notificationType == SuppServiceNotification.NOTIFICATION_TYPE_MT) {
            if (notification.code == SuppServiceNotification.MT_CODE_ADDITIONAL_CALL_FORWARDED) {
                notification.code = SuppServiceNotification.MT_CODE_FORWARDED_CALL;
            }
        }

        return notification;
    }
}
