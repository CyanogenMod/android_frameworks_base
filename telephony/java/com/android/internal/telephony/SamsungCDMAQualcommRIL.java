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
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.telephony.SmsMessage;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import android.telephony.PhoneNumberUtils;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.cdma.CdmaInformationRecords.CdmaSignalInfoRec;
import com.android.internal.telephony.cdma.SignalToneUtil;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Samsung CDMA RIL doesn't send CDMA NV in RIUM infomation format which causes the CDMA RIL stack to crash and end up not being provisioned.
 * Samsung put CDMA NV in GSM format. I forced the RIL stack to process CDMA NV request as a GSM SIM in CDMA mode.
 * Custom Qualcomm No SimReady RIL using the latest Uicc stack
 * Check for CDMA phone on RADIO_ON* event and if so set status to RUIM_NOT_READY to trigger CSIM/RUIM processing
 *
 * {@hide}
 */
public class SamsungCDMAQualcommRIL extends QualcommSharedRIL implements CommandsInterface {
    protected IccHandler mIccHandler;
    private final int RIL_INT_RADIO_OFF = 0;
    private final int RIL_INT_RADIO_UNAVALIABLE = 1;
    private final int RIL_INT_RADIO_ON = 2;
    private final int RIL_INT_RADIO_ON_NG = 10;
    private final int RIL_INT_RADIO_ON_HTC = 13;

    public SamsungCDMAQualcommRIL(Context context, int networkMode, int cdmaSubscription) {
        super(context, networkMode, cdmaSubscription);
    }

    @Override
    protected Object
    responseIccCardStatus(Parcel p) {
        IccCardApplication ca;

        IccCardStatus status = new IccCardStatus();
        status.setCardState(p.readInt());
        status.setUniversalPinState(p.readInt());
        status.setGsmUmtsSubscriptionAppIndex(p.readInt());
        status.setCdmaSubscriptionAppIndex(p.readInt());

        status.setImsSubscriptionAppIndex(p.readInt());

        int numApplications = p.readInt();

        // limit to maximum allowed applications
        if (numApplications > IccCardStatus.CARD_MAX_APPS) {
            numApplications = IccCardStatus.CARD_MAX_APPS;
        }
        status.setNumApplications(numApplications);

        for (int i = 0; i < numApplications; i++) {
            ca = new IccCardApplication();
            ca.app_type = ca.AppTypeFromRILInt(p.readInt());
            ca.app_state = ca.AppStateFromRILInt(p.readInt());
            ca.perso_substate = ca.PersoSubstateFromRILInt(p.readInt());
            ca.aid = p.readString();
            ca.app_label = p.readString();
            ca.pin1_replaced = p.readInt();
            ca.pin1 = ca.PinStateFromRILInt(p.readInt());
            ca.pin2 = ca.PinStateFromRILInt(p.readInt());
            p.readInt(); //remaining_count_pin1   - pin1_num_retries
            p.readInt(); //remaining_count_puk1   - puk1_num_retries
            p.readInt(); //remaining_count_pin2   - pin2_num_retries
            p.readInt(); //remaining_count_puk2   - puk2_num_retries
            p.readInt(); //                       - perso_unblock_retries
            status.addApplication(ca);
        }
        int appIndex = -1;
        appIndex = status.getGsmUmtsSubscriptionAppIndex();
        Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);

        if (numApplications > 0) {
            IccCardApplication application = status.getApplication(appIndex);
            mAid = application.aid;
            mUSIM = application.app_type
                      == IccCardApplication.AppType.APPTYPE_USIM;
            mSetPreferredNetworkType = mPreferredNetworkType;

            if (TextUtils.isEmpty(mAid))
               mAid = "";
            Log.d(LOG_TAG, "mAid " + mAid);
        }

        return status;
    }

    @Override
    protected void setRadioStateFromRILInt (int stateCode) {
        CommandsInterface.RadioState radioState;
        HandlerThread handlerThread;
        Looper looper;
        IccHandler iccHandler;

        switch (stateCode) {
            case RIL_INT_RADIO_OFF:
                radioState = CommandsInterface.RadioState.RADIO_OFF;
                if (mIccHandler != null) {
                    mIccThread = null;
                    mIccHandler = null;
                }
                break;
            case RIL_INT_RADIO_UNAVALIABLE:
                radioState = CommandsInterface.RadioState.RADIO_UNAVAILABLE;
                break;
            case RIL_INT_RADIO_ON:
            case RIL_INT_RADIO_ON_NG:
            case RIL_INT_RADIO_ON_HTC:
                if (mIccHandler == null) {
                    handlerThread = new HandlerThread("IccHandler");
                    mIccThread = handlerThread;

                    mIccThread.start();

                    looper = mIccThread.getLooper();
                    mIccHandler = new IccHandler(this,looper);
                    mIccHandler.run();
                }

                if (mPhoneType == RILConstants.CDMA_PHONE) {
                    radioState = CommandsInterface.RadioState.RUIM_NOT_READY;
                } else {
                    radioState = CommandsInterface.RadioState.SIM_NOT_READY;
                }
                setRadioState(radioState);
                break;
            default:
                throw new RuntimeException("Unrecognized RIL_RadioState: " + stateCode);
        }

        setRadioState (radioState);
    }

    @Override
    protected Object
    responseSignalStrength(Parcel p) {
        int numInts = 12;
        int response[];

        // This is a mashup of algorithms used in
        // SamsungQualcommUiccRIL.java

        // Get raw data
        response = new int[numInts];
        for (int i = 0 ; i < numInts ; i++) {
            response[i] = p.readInt();
        }
        //Workaround: use cdmaecio and evdoecio to determine signal strength and it is better than no signal bars
        //TODO: find a proper fix for it
        response[2] = response[3]*4; // mutiply by 4 simulate dbm so the signal bars do not jump often to full bars
        response[4] = response[5]*4;
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

    @Override
    protected Object
    responseCallList(Parcel p) {
        int num;
        int voiceSettings;
        ArrayList<DriverCall> response;
        DriverCall dc;

        num = p.readInt();
        response = new ArrayList<DriverCall>(num);

        for (int i = 0 ; i < num ; i++) {
            dc = new DriverCall();

            dc.state = DriverCall.stateFromCLCC(p.readInt());
            dc.index = p.readInt();
            dc.TOA = p.readInt();
            dc.isMpty = (0 != p.readInt());
            dc.isMT = (0 != p.readInt());
            dc.als = p.readInt();
            voiceSettings = p.readInt();
            dc.isVoice = (0 == voiceSettings) ? false : true;
            dc.isVoicePrivacy = (0 != p.readInt());
            //Some Samsung magic data for Videocalls
            // hack taken from smdk4210ril class
            voiceSettings = p.readInt();
            //printing it to cosole for later investigation
            Log.d(LOG_TAG, "Samsung magic = " + voiceSettings);
            dc.number = p.readString();
            int np = p.readInt();
            dc.numberPresentation = DriverCall.presentationFromCLIP(np);
            dc.name = p.readString();
            dc.namePresentation = p.readInt();
            int uusInfoPresent = p.readInt();
            if (uusInfoPresent == 1) {
                dc.uusInfo = new UUSInfo();
                dc.uusInfo.setType(p.readInt());
                dc.uusInfo.setDcs(p.readInt());
                byte[] userData = p.createByteArray();
                dc.uusInfo.setUserData(userData);
                riljLogv(String.format("Incoming UUS : type=%d, dcs=%d, length=%d",
                                       dc.uusInfo.getType(), dc.uusInfo.getDcs(),
                                       dc.uusInfo.getUserData().length));
                riljLogv("Incoming UUS : data (string)="
                         + new String(dc.uusInfo.getUserData()));
                riljLogv("Incoming UUS : data (hex): "
                         + IccUtils.bytesToHexString(dc.uusInfo.getUserData()));
            } else {
                riljLogv("Incoming UUS : NOT present!");
            }

            // Make sure there's a leading + on addresses with a TOA of 145
            dc.number = PhoneNumberUtils.stringFromStringAndTOA(dc.number, dc.TOA);

            response.add(dc);

            if (dc.isVoicePrivacy) {
                mVoicePrivacyOnRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is enabled");
            } else {
                mVoicePrivacyOffRegistrants.notifyRegistrants();
                riljLog("InCall VoicePrivacy is disabled");
            }
        }

        Collections.sort(response);

        return response;
    }

    // Workaround for Samsung CDMA "ring of death" bug:
    //
    // Symptom: As soon as the phone receives notice of an incoming call, an
    //   audible "old fashioned ring" is emitted through the earpiece and
    //   persists through the duration of the call, or until reboot if the call
    //   isn't answered.
    //
    // Background: The CDMA telephony stack implements a number of "signal info
    //   tones" that are locally generated by ToneGenerator and mixed into the
    //   voice call path in response to radio RIL_UNSOL_CDMA_INFO_REC requests.
    //   One of these tones, IS95_CONST_IR_SIG_IS54B_L, is requested by the
    //   radio just prior to notice of an incoming call when the voice call
    //   path is muted.  CallNotifier is responsible for stopping all signal
    //   tones (by "playing" the TONE_CDMA_SIGNAL_OFF tone) upon receipt of a
    //   "new ringing connection", prior to unmuting the voice call path.
    //
    // Problem: CallNotifier's incoming call path is designed to minimize
    //   latency to notify users of incoming calls ASAP.  Thus,
    //   SignalInfoTonePlayer requests are handled asynchronously by spawning a
    //   one-shot thread for each.  Unfortunately the ToneGenerator API does
    //   not provide a mechanism to specify an ordering on requests, and thus,
    //   unexpected thread interleaving may result in ToneGenerator processing
    //   them in the opposite order that CallNotifier intended.  In this case,
    //   playing the "signal off" tone first, followed by playing the "old
    //   fashioned ring" indefinitely.
    //
    // Solution: An API change to ToneGenerator is required to enable
    //   SignalInfoTonePlayer to impose an ordering on requests (i.e., drop any
    //   request that's older than the most recent observed).  Such a change,
    //   or another appropriate fix should be implemented in AOSP first.
    //
    // Workaround: Intercept RIL_UNSOL_CDMA_INFO_REC requests from the radio,
    //   check for a signal info record matching IS95_CONST_IR_SIG_IS54B_L, and
    //   drop it so it's never seen by CallNotifier.  If other signal tones are
    //   observed to cause this problem, they should be dropped here as well.
    @Override
    protected void
    notifyRegistrantsCdmaInfoRec(CdmaInformationRecords infoRec) {
        final int response = RIL_UNSOL_CDMA_INFO_REC;

        if (infoRec.record instanceof CdmaSignalInfoRec) {
            CdmaSignalInfoRec sir = (CdmaSignalInfoRec)infoRec.record;
            if (sir != null && sir.isPresent &&
                sir.signalType == SignalToneUtil.IS95_CONST_IR_SIGNAL_IS54B &&
                sir.alertPitch == SignalToneUtil.IS95_CONST_IR_ALERT_MED    &&
                sir.signal     == SignalToneUtil.IS95_CONST_IR_SIG_IS54B_L) {

                Log.d(LOG_TAG, "Dropping \"" + responseToString(response) + " " +
                      retToString(response, sir) + "\" to prevent \"ring of death\" bug.");
                return;
            }
        }

        super.notifyRegistrantsCdmaInfoRec(infoRec);
    }

    class IccHandler extends Handler implements Runnable {
        private static final int EVENT_RADIO_ON = 1;
        private static final int EVENT_ICC_STATUS_CHANGED = 2;
        private static final int EVENT_GET_ICC_STATUS_DONE = 3;
        private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 4;

        private RIL mRil;
        private boolean mRadioOn = false;

        public IccHandler (RIL ril, Looper looper) {
            super (looper);
            mRil = ril;
        }

        public void handleMessage (Message paramMessage) {
            switch (paramMessage.what) {
                case EVENT_RADIO_ON:
                    mRadioOn = true;
                    Log.d(LOG_TAG, "Radio on -> Forcing sim status update");
                    sendMessage(obtainMessage(EVENT_ICC_STATUS_CHANGED));
                    break;
                case EVENT_GET_ICC_STATUS_DONE:
                    AsyncResult asyncResult = (AsyncResult) paramMessage.obj;
                    if (asyncResult.exception != null) {
                        Log.e (LOG_TAG, "IccCardStatusDone shouldn't return exceptions!", asyncResult.exception);
                        break;
                    }
                    IccCardStatus status = (IccCardStatus) asyncResult.result;
                    if (status.getNumApplications() == 0) {
                        if (!mRil.getRadioState().isOn()) {
                            break;
                        }
                        mRil.setRadioState(CommandsInterface.RadioState.SIM_LOCKED_OR_ABSENT);
                    } else {
                        int appIndex = -1;
                        appIndex = status.getGsmUmtsSubscriptionAppIndex();
                        Log.d(LOG_TAG, "This is a CDMA PHONE " + appIndex);

                        IccCardApplication application = status.getApplication(appIndex);
                        IccCardApplication.AppState app_state = application.app_state;
                        IccCardApplication.AppType app_type = application.app_type;

                        switch (app_state) {
                            case APPSTATE_PIN:
                            case APPSTATE_PUK:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.SIM_LOCKED_OR_ABSENT);
                                        break;
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RUIM_LOCKED_OR_ABSENT);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            case APPSTATE_READY:
                                switch (app_type) {
                                    case APPTYPE_SIM:
                                    case APPTYPE_USIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.SIM_READY);
                                        break;
                                    case APPTYPE_RUIM:
                                        mRil.setRadioState(CommandsInterface.RadioState.RUIM_READY);
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Currently we don't handle SIMs of type: " + app_type);
                                        return;
                                }
                                break;
                            default:
                                return;
                        }
                    }
                    break;
                case EVENT_ICC_STATUS_CHANGED:
                    if (mRadioOn) {
                        Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED, calling getIccCardStatus");
                         mRil.getIccCardStatus(obtainMessage(EVENT_GET_ICC_STATUS_DONE, paramMessage.obj));
                    } else {
                         Log.d(LOG_TAG, "Received EVENT_ICC_STATUS_CHANGED while radio is not ON. Ignoring");
                    }
                    break;
                case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                    mRadioOn = false;
                    // disposeCards(); // to be verified;
                default:
                    Log.e(LOG_TAG, " Unknown Event " + paramMessage.what);
                    break;
            }
        }

        public void run () {
            mRil.registerForIccStatusChanged(this, EVENT_ICC_STATUS_CHANGED, null);
            Message msg = obtainMessage(EVENT_RADIO_ON);
            mRil.getIccCardStatus(msg);
        }
    }


}
