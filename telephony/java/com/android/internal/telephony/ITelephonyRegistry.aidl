/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Intent;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.DataConnectionRealTimeInfo;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.CellInfo;
import android.telephony.VoLteServiceState;
import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.IOnSubscriptionsChangedListener;

interface ITelephonyRegistry {
    void addOnSubscriptionsChangedListener(String pkg,
            IOnSubscriptionsChangedListener callback);
    void removeOnSubscriptionsChangedListener(String pkg,
            IOnSubscriptionsChangedListener callback);
    void listen(String pkg, IPhoneStateListener callback, int events, boolean notifyNow);
    void listenForSubscriber(in int subId, String pkg, IPhoneStateListener callback, int events,
            boolean notifyNow);
    void notifyCallState(int state, String incomingNumber);
    void notifyCallStateForSubscriber(in int subId, int state, String incomingNumber);
    void notifyServiceStateForPhoneId(in int phoneId, in int subId, in ServiceState state);
    void notifySignalStrength(in SignalStrength signalStrength);
    void notifySignalStrengthForSubscriber(in int subId, in SignalStrength signalStrength);
    void notifyMessageWaitingChangedForPhoneId(in int phoneId, in int subId, in boolean mwi);
    void notifyCallForwardingChanged(boolean cfi);
    void notifyCallForwardingChangedForSubscriber(in int subId, boolean cfi);
    void notifyDataActivity(int state);
    void notifyDataActivityForSubscriber(in int subId, int state);
    void notifyDataConnection(int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, in LinkProperties linkProperties,
            in NetworkCapabilities networkCapabilities, int networkType, boolean roaming);
    void notifyDataConnectionForSubscriber(int subId, int state, boolean isDataConnectivityPossible,
            String reason, String apn, String apnType, in LinkProperties linkProperties,
            in NetworkCapabilities networkCapabilities, int networkType, boolean roaming);
    void notifyDataConnectionFailed(String reason, String apnType);
    void notifyDataConnectionFailedForSubscriber(int subId, String reason, String apnType);
    void notifyCellLocation(in Bundle cellLocation);
    void notifyCellLocationForSubscriber(in int subId, in Bundle cellLocation);
    void notifyOtaspChanged(in int otaspMode);
    void notifyCellInfo(in List<CellInfo> cellInfo);
    void notifyPreciseCallState(int ringingCallState, int foregroundCallState,
            int backgroundCallState);
    void notifyPreciseCallStateForSubscriber(int subId, int ringingCallState,
            int foregroundCallState, int backgroundCallState);
    void notifyDisconnectCause(int disconnectCause, int preciseDisconnectCause);
    void notifyPreciseDataConnectionFailed(String reason, String apnType, String apn,
            String failCause);
    void notifyCellInfoForSubscriber(in int subId, in List<CellInfo> cellInfo);
    void notifyDataConnectionRealTimeInfo(in DataConnectionRealTimeInfo dcRtInfo);
    void notifyVoLteServiceStateChanged(in VoLteServiceState lteState);
    void notifyOemHookRawEventForSubscriber(in int subId, in byte[] rawData);
    void notifySubscriptionInfoChanged();
    void notifyCarrierNetworkChange(in boolean active);
}
