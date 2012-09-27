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

import static com.android.internal.telephony.RILConstants.*;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN;
import static android.telephony.TelephonyManager.NETWORK_TYPE_EDGE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_GPRS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_UMTS;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSDPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSUPA;
import static android.telephony.TelephonyManager.NETWORK_TYPE_HSPA;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.NetworkInfo;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.PowerManager.WakeLock;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;
import com.android.internal.telephony.cdma.CdmaCallWaitingNotification;
import com.android.internal.telephony.cdma.CdmaInformationRecords;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Runtime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class Smdk4210RIL extends RIL implements CommandsInterface {

    //SAMSUNG STATES
    static final int RIL_REQUEST_GET_CELL_BROADCAST_CONFIG = 10002;

    static final int RIL_REQUEST_SEND_ENCODED_USSD = 10005;
    static final int RIL_REQUEST_SET_PDA_MEMORY_STATUS = 10006;
    static final int RIL_REQUEST_GET_PHONEBOOK_STORAGE_INFO = 10007;
    static final int RIL_REQUEST_GET_PHONEBOOK_ENTRY = 10008;
    static final int RIL_REQUEST_ACCESS_PHONEBOOK_ENTRY = 10009;
    static final int RIL_REQUEST_DIAL_VIDEO_CALL = 10010;
    static final int RIL_REQUEST_CALL_DEFLECTION = 10011;
    static final int RIL_REQUEST_READ_SMS_FROM_SIM = 10012;
    static final int RIL_REQUEST_USIM_PB_CAPA = 10013;
    static final int RIL_REQUEST_LOCK_INFO = 10014;

    static final int RIL_REQUEST_DIAL_EMERGENCY = 10016;
    static final int RIL_REQUEST_GET_STOREAD_MSG_COUNT = 10017;
    static final int RIL_REQUEST_STK_SIM_INIT_EVENT = 10018;
    static final int RIL_REQUEST_GET_LINE_ID = 10019;
    static final int RIL_REQUEST_SET_LINE_ID = 10020;
    static final int RIL_REQUEST_GET_SERIAL_NUMBER = 10021;
    static final int RIL_REQUEST_GET_MANUFACTURE_DATE_NUMBER = 10022;
    static final int RIL_REQUEST_GET_BARCODE_NUMBER = 10023;
    static final int RIL_REQUEST_UICC_GBA_AUTHENTICATE_BOOTSTRAP = 10024;
    static final int RIL_REQUEST_UICC_GBA_AUTHENTICATE_NAF = 10025;
    static final int RIL_REQUEST_SIM_TRANSMIT_BASIC = 10026;
    static final int RIL_REQUEST_SIM_OPEN_CHANNEL = 10027;
    static final int RIL_REQUEST_SIM_CLOSE_CHANNEL = 10028;
    static final int RIL_REQUEST_SIM_TRANSMIT_CHANNEL = 10029;
    static final int RIL_REQUEST_SIM_AUTH = 10030;
    static final int RIL_REQUEST_PS_ATTACH = 10031;
    static final int RIL_REQUEST_PS_DETACH = 10032;
    static final int RIL_REQUEST_ACTIVATE_DATA_CALL = 10033;
    static final int RIL_REQUEST_CHANGE_SIM_PERSO = 10034;
    static final int RIL_REQUEST_ENTER_SIM_PERSO = 10035;
    static final int RIL_REQUEST_GET_TIME_INFO = 10036;
    static final int RIL_REQUEST_OMADM_SETUP_SESSION = 10037;
    static final int RIL_REQUEST_OMADM_SERVER_START_SESSION = 10038;
    static final int RIL_REQUEST_OMADM_CLIENT_START_SESSION = 10039;
    static final int RIL_REQUEST_OMADM_SEND_DATA = 10040;
    static final int RIL_REQUEST_CDMA_GET_DATAPROFILE = 10041;
    static final int RIL_REQUEST_CDMA_SET_DATAPROFILE = 10042;
    static final int RIL_REQUEST_CDMA_GET_SYSTEMPROPERTIES = 10043;
    static final int RIL_REQUEST_CDMA_SET_SYSTEMPROPERTIES = 10044;
    static final int RIL_REQUEST_SEND_SMS_COUNT = 10045;
    static final int RIL_REQUEST_SEND_SMS_MSG = 10046;
    static final int RIL_REQUEST_SEND_SMS_MSG_READ_STATUS = 10047;
    static final int RIL_REQUEST_MODEM_HANGUP = 10048;
    static final int RIL_REQUEST_SET_SIM_POWER = 10049;
    static final int RIL_REQUEST_SET_PREFERRED_NETWORK_LIST = 10050;
    static final int RIL_REQUEST_GET_PREFERRED_NETWORK_LIST = 10051;
    static final int RIL_REQUEST_HANGUP_VT = 10052;

    static final int RIL_UNSOL_RELEASE_COMPLETE_MESSAGE = 11001;
    static final int RIL_UNSOL_STK_SEND_SMS_RESULT = 11002;
    static final int RIL_UNSOL_STK_CALL_CONTROL_RESULT = 11003;
    static final int RIL_UNSOL_DUN_CALL_STATUS = 11004;

    static final int RIL_UNSOL_O2_HOME_ZONE_INFO = 11007;
    static final int RIL_UNSOL_DEVICE_READY_NOTI = 11008;
    static final int RIL_UNSOL_GPS_NOTI = 11009;
    static final int RIL_UNSOL_AM = 11010;
    static final int RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL = 11011;
    static final int RIL_UNSOL_DATA_SUSPEND_RESUME = 11012;
    static final int RIL_UNSOL_SAP = 11013;

    static final int RIL_UNSOL_SIM_SMS_STORAGE_AVAILALE = 11015;
    static final int RIL_UNSOL_HSDPA_STATE_CHANGED = 11016;
    static final int RIL_UNSOL_WB_AMR_STATE = 11017;
    static final int RIL_UNSOL_TWO_MIC_STATE = 11018;
    static final int RIL_UNSOL_DHA_STATE = 11019;
    static final int RIL_UNSOL_UART = 11020;
    static final int RIL_UNSOL_RESPONSE_HANDOVER = 11021;
    static final int RIL_UNSOL_IPV6_ADDR = 11022;
    static final int RIL_UNSOL_NWK_INIT_DISC_REQUEST = 11023;
    static final int RIL_UNSOL_RTS_INDICATION = 11024;
    static final int RIL_UNSOL_OMADM_SEND_DATA = 11025;
    static final int RIL_UNSOL_DUN = 11026;
    static final int RIL_UNSOL_SYSTEM_REBOOT = 11027;
    static final int RIL_UNSOL_VOICE_PRIVACY_CHANGED = 11028;
    static final int RIL_UNSOL_UTS_GETSMSCOUNT = 11029;
    static final int RIL_UNSOL_UTS_GETSMSMSG = 11030;
    static final int RIL_UNSOL_UTS_GET_UNREAD_SMS_STATUS = 11031;
    static final int RIL_UNSOL_MIP_CONNECT_STATUS = 11032;

    protected HandlerThread mSmdk4210Thread;
    protected ConnectivityHandler mSmdk4210Handler;
    private AudioManager audioManager;

    public Smdk4210RIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
        audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
        mQANElements = 5;
    }

    @Override
    public void setCurrentPreferredNetworkType() {
        if (RILJ_LOGD) riljLog("setCurrentPreferredNetworkType IGNORED");
        /* Google added this as a fix for crespo loosing network type after
         * taking an OTA. This messes up the data connection state for us
         * due to the way we handle network type change (disable data
         * then change then re-enable).
         */
    }

    private boolean NeedReconnect()
    {
        ConnectivityManager cm =
            (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni_active = cm.getActiveNetworkInfo();

        return ni_active != null && ni_active.getTypeName().equalsIgnoreCase( "mobile" ) &&
                ni_active.isConnected() && cm.getMobileDataEnabled();
    }

    @Override
    public void setPreferredNetworkType(int networkType , Message response) {
        /* Samsung modem implementation does bad things when a datacall is running
         * while switching the preferred networktype.
         */
        HandlerThread handlerThread;
        Looper looper;

        if(NeedReconnect())
        {
            if (mSmdk4210Handler == null) {

                handlerThread = new HandlerThread("mSmdk4210Thread");
                mSmdk4210Thread = handlerThread;

                mSmdk4210Thread.start();

                looper = mSmdk4210Thread.getLooper();
                mSmdk4210Handler = new ConnectivityHandler(mContext, looper);
            }
            mSmdk4210Handler.setPreferedNetworkType(networkType, response);
        } else {
            if (mSmdk4210Handler != null) {
                mSmdk4210Thread = null;
                mSmdk4210Handler = null;
            }
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

        public ConnectivityHandler(Context context, Looper looper)
        {
            super (looper);
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

    @Override
    protected RILRequest findAndRemoveRequestFromList(int serial) {
        long removalTime = System.currentTimeMillis();
        long timeDiff = 0;

        synchronized (mRequestsList) {
          Iterator<RILRequest> itr = mRequestsList.iterator();

          while ( itr.hasNext() ) {
            RILRequest rr = itr.next();

            if (rr.mSerial == serial) {
                itr.remove();
                if (mRequestMessagesWaiting > 0)
                    mRequestMessagesWaiting--;
                return rr;
            }
            else
            {
              // We need some special code here for the Samsung RIL,
              // which isn't responding to some requests.
              // We will print a list of such stale requests which
              // haven't yet received a response.  If the timeout fires
              // first, then the wakelock is released without debugging.
              timeDiff = removalTime - rr.creationTime;
              if ( timeDiff > mWakeLockTimeout )
              {
                Log.d(LOG_TAG,  "No response for [" + rr.mSerial + "] " +
                        requestToString(rr.mRequest) + " after " + timeDiff + " milliseconds.");

                /* Don't actually remove anything for now.  Consider uncommenting this to
                   purge stale requests */

                /*
                itr.remove();
                if (mRequestMessagesWaiting > 0) {
                    mRequestMessagesWaiting--;
                }

                // We don't handle the callback (ie. rr.mResult) for
                // RIL_REQUEST_SET_TTY_MODE, which is
                // RIL_REQUEST_QUERY_TTY_MODE.  The reason for not doing
                // so is because it will also not get a response from the
                // Samsung RIL
                rr.release();
                */
              }
            }
          }
        }
        return null;
    }

    @Override
    protected void processSolicited (Parcel p) {
    int serial, error;
    boolean found = false;

    serial = p.readInt();
    error = p.readInt();

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
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_DATA_REGISTRATION_STATE: ret =  responseStrings(p); break;
            case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
            case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
            case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
            case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
            case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseSetupDataCall(p); break;
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
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseOperatorInfos(p); break;
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
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseGetPreferredNetworkType(p); break;
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
            case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: ret =  responseVoid(p); break;
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
            case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE: ret =  responseVoid(p); break;
            case RIL_REQUEST_ISIM_AUTHENTICATION: ret =  responseString(p); break;
            case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU: ret = responseVoid(p); break;
            case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS: ret = responseICC_IO(p); break;
            case RIL_REQUEST_VOICE_RADIO_TECH: ret = responseInts(p); break;
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
        Object ret;
        int dataPosition = p.dataPosition();
        int response = p.readInt();

        switch (response) {
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS: ret = responseString(p); break;
            case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
            // SAMSUNG STATES
            case RIL_UNSOL_AM: ret = responseString(p); break;
            case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL: ret = responseVoid(p); break;
            case RIL_UNSOL_DATA_SUSPEND_RESUME: ret = responseInts(p); break;
            case RIL_UNSOL_STK_CALL_CONTROL_RESULT: ret = responseVoid(p); break;
            case RIL_UNSOL_TWO_MIC_STATE: ret = responseInts(p); break;
            case RIL_UNSOL_WB_AMR_STATE: ret = responseInts(p); break;

            default:
                // Rewind the Parcel
                p.setDataPosition(dataPosition);

                // Forward responses that we are not overriding to the super class
                super.processUnsolicited(p);
                return;
        }

        switch (response) {
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                if (mGsmBroadcastSmsRegistrant != null) {
                    mGsmBroadcastSmsRegistrant
                        .notifyRegistrant(new AsyncResult(null, ret, null));
                }
                break;
            case RIL_UNSOL_RIL_CONNECTED:
                if (RILJ_LOGD) unsljLogRet(response, ret);

                // Initial conditions
                setRadioPower(false, null);
                sendPreferedNetworktype(mPreferredNetworkType, null);
                setCdmaSubscriptionSource(mCdmaSubscription, null);
                notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
                break;
            // SAMSUNG STATES
            case RIL_UNSOL_AM:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                String amString = (String) ret;
                Log.d(LOG_TAG, "Executing AM: " + amString);

                try {
                    Runtime.getRuntime().exec("am " + amString);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e(LOG_TAG, "am " + amString + " could not be executed.");
                }
                break;
            case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_DATA_SUSPEND_RESUME:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_STK_CALL_CONTROL_RESULT:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_TWO_MIC_STATE:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                break;
            case RIL_UNSOL_WB_AMR_STATE:
                if (RILJ_LOGD) samsungUnsljLogRet(response, ret);
                setWbAmr(((int[])ret)[0]);
                break;
        }
    }

    static String
    samsungResponseToString(int request)
    {
        switch(request) {
            // SAMSUNG STATES
            case RIL_UNSOL_AM: return "RIL_UNSOL_AM";
            case RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL: return "RIL_UNSOL_DUN_PIN_CONTROL_SIGNAL";
            case RIL_UNSOL_DATA_SUSPEND_RESUME: return "RIL_UNSOL_DATA_SUSPEND_RESUME";
            case RIL_UNSOL_STK_CALL_CONTROL_RESULT: return "RIL_UNSOL_STK_CALL_CONTROL_RESULT";
            case RIL_UNSOL_TWO_MIC_STATE: return "RIL_UNSOL_TWO_MIC_STATE";
            case RIL_UNSOL_WB_AMR_STATE: return "RIL_UNSOL_WB_AMR_STATE";
            default: return "<unknown response: "+request+">";
        }
    }

    protected void samsungUnsljLog(int response) {
        riljLog("[UNSL]< " + samsungResponseToString(response));
    }

    protected void samsungUnsljLogMore(int response, String more) {
        riljLog("[UNSL]< " + samsungResponseToString(response) + " " + more);
    }

    protected void samsungUnsljLogRet(int response, Object ret) {
        riljLog("[UNSL]< " + samsungResponseToString(response) + " " + retToString(response, ret));
    }

    protected void samsungUnsljLogvRet(int response, Object ret) {
        riljLogv("[UNSL]< " + samsungResponseToString(response) + " " + retToString(response, ret));
    }

    /**
     * Notifiy all registrants that the ril has connected or disconnected.
     *
     * @param rilVer is the version of the ril or -1 if disconnected.
     */
    private void notifyRegistrantsRilConnectionChanged(int rilVer) {
        mRilVersion = rilVer;
        if (mRilConnectedRegistrants != null) {
            mRilConnectedRegistrants.notifyRegistrants(
                                new AsyncResult (null, new Integer(rilVer), null));
        }
    }

    /**
     * Set audio parameter "wb_amr" for HD-Voice (Wideband AMR).
     *
     * @param state: 0 = unsupported, 1 = supported.
     */
    private void setWbAmr(int state) {
        if (state == 1) {
            Log.d(LOG_TAG, "setWbAmr(): setting audio parameter - wb_amr=on");
            audioManager.setParameters("wb_amr=on");
        } else {
            Log.d(LOG_TAG, "setWbAmr(): setting audio parameter - wb_amr=off");
            audioManager.setParameters("wb_amr=off");
        }
    }

    @Override
    protected Object
    responseCallList(Parcel p) {
        int num;
        boolean isVideo;
        ArrayList<DriverCall> response;
        DriverCall dc;
        int dataAvail = p.dataAvail();
        int pos = p.dataPosition();
        int size = p.dataSize();

        Log.d(LOG_TAG, "Parcel size = " + size);
        Log.d(LOG_TAG, "Parcel pos = " + pos);
        Log.d(LOG_TAG, "Parcel dataAvail = " + dataAvail);

        //Samsung changes
        num = p.readInt();

        Log.d(LOG_TAG, "num = " + num);
        response = new ArrayList<DriverCall>(num);

        for (int i = 0 ; i < num ; i++) {

            dc                      = new DriverCall();
            dc.state                = DriverCall.stateFromCLCC(p.readInt());
            dc.index                = p.readInt();
            dc.TOA                  = p.readInt();
            dc.isMpty               = (0 != p.readInt());
            dc.isMT                 = (0 != p.readInt());
            dc.als                  = p.readInt();
            dc.isVoice              = (0 != p.readInt());
            isVideo                 = (0 != p.readInt());
            dc.isVoicePrivacy       = (0 != p.readInt());
            dc.number               = p.readString();
            int np                  = p.readInt();
            dc.numberPresentation   = DriverCall.presentationFromCLIP(np);
            dc.name                 = p.readString();
            dc.namePresentation     = p.readInt();
            int uusInfoPresent      = p.readInt();

            Log.d(LOG_TAG, "state = " + dc.state);
            Log.d(LOG_TAG, "index = " + dc.index);
            Log.d(LOG_TAG, "state = " + dc.TOA);
            Log.d(LOG_TAG, "isMpty = " + dc.isMpty);
            Log.d(LOG_TAG, "isMT = " + dc.isMT);
            Log.d(LOG_TAG, "als = " + dc.als);
            Log.d(LOG_TAG, "isVoice = " + dc.isVoice);
            Log.d(LOG_TAG, "isVideo = " + isVideo);
            Log.d(LOG_TAG, "number = " + dc.number);
            Log.d(LOG_TAG, "np = " + np);
            Log.d(LOG_TAG, "name = " + dc.name);
            Log.d(LOG_TAG, "namePresentation = " + dc.namePresentation);
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
    protected Object responseGetPreferredNetworkType(Parcel p) {
        int [] response = (int[]) responseInts(p);

        if (response.length >= 1) {
            // Since this is the response for getPreferredNetworkType
            // we'll assume that it should be the value we want the
            // vendor ril to take if we reestablish a connection to it.
            mPreferredNetworkType = response[0];
        }

        // When the modem responds Phone.NT_MODE_GLOBAL, it means Phone.NT_MODE_WCDMA_PREF
        if (response[0] == Phone.NT_MODE_GLOBAL) {
            Log.d(LOG_TAG, "Overriding network type response from GLOBAL to WCDMA preferred");
            response[0] = Phone.NT_MODE_WCDMA_PREF;
        }

        return response;
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        // Get raw data
        response = new int[numInts];
        for (int i = 0 ; i < numInts ; i++) {
            response[i] = p.readInt();
        }

        Log.d(LOG_TAG, "responseSignalStength BEFORE: gsmDbm=" + response[0]);

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

        response[1] = -1; //gsmEcio
        response[2] = (response[2] < 0)?-120:-response[2]; //cdmaDbm
        response[3] = (response[3] < 0)?-160:-response[3]; //cdmaEcio
        response[4] = (response[4] < 0)?-120:-response[4]; //evdoRssi
        response[5] = (response[5] < 0)?-1:-response[5]; //evdoEcio
        if (response[6] < 0 || response[6] > 8) {
            response[6] = -1;
        }

        Log.d(LOG_TAG, "responseSignalStength AFTER: gsmDbm=" + response[0]);

        return response;
    }

    @Override public void
    getVoiceRadioTechnology(Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_VOICE_RADIO_TECH, result);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        // RIL versions below 7 do not support this request
        if (mRilVersion >= 7)
            send(rr);
        else
            Log.d(LOG_TAG, "RIL_REQUEST_VOICE_RADIO_TECH blocked!!!");
    }

    @Override
    public void getCdmaSubscriptionSource(Message response) {
        RILRequest rr = RILRequest.obtain(
                RILConstants.RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE, response);

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));
        Log.d(LOG_TAG, "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE blocked!!!");
        //send(rr);
    }
}
