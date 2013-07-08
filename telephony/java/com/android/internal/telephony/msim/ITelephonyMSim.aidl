/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (c) 2011-2013 The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
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

package com.android.internal.telephony.msim;

import android.os.Bundle;
import java.util.List;
import android.telephony.NeighboringCellInfo;
import android.telephony.CellInfo;

/**
 * Interface used to interact with the phone.  Mostly this is used by the
 * TelephonyManager class.  A few places are still using this directly.
 * Please clean them up if possible and use TelephonyManager instead.
 *
 * {@hide}
 */
interface ITelephonyMSim {
    /**
     * Dial a number. This doesn't place the call. It displays
     * the Dialer screen for that subscription.
     * @param number the number to be dialed. If null, this
     * would display the Dialer screen with no number pre-filled.
     * @param subscription user preferred subscription.
     */
    void dial(String number, int subscription);

    /**
     * Place a call to the specified number on particular subscription.
     * @param number the number to be called.
     * @param subscription user preferred subscription.
     */
    void call(String number, int subscription);

    /**
     * If there is currently a call in progress, show the call screen.
     * The DTMF dialpad may or may not be visible initially, depending on
     * whether it was up when the user last exited the InCallScreen.
     *
     * @return true if the call screen was shown.
     */
    boolean showCallScreen();

    /**
     * Variation of showCallScreen() that also specifies whether the
     * DTMF dialpad should be initially visible when the InCallScreen
     * comes up.
     *
     * @param showDialpad if true, make the dialpad visible initially,
     *                    otherwise hide the dialpad initially.
     * @return true if the call screen was shown.
     *
     * @see showCallScreen
     */
    boolean showCallScreenWithDialpad(boolean showDialpad);

    /**
     * End call on particular subscription or go to the Home screen
     * @param subscription user preferred subscription.
     * @return whether it hung up
     */
    boolean endCall(int subscription);

    /**
     * Answer the currently-ringing call on particular subscription.
     *
     * If there's already a current active call, that call will be
     * automatically put on hold.  If both lines are currently in use, the
     * current active call will be ended.
     *
     * TODO: provide a flag to let the caller specify what policy to use
     * if both lines are in use.  (The current behavior is hardwired to
     * "answer incoming, end ongoing", which is how the CALL button
     * is specced to behave.)
     *
     * TODO: this should be a oneway call (especially since it's called
     * directly from the key queue thread).
     *
     * @param subscription user preferred subscription.
     */
    void answerRingingCall(int subscription);

    /**
     * Silence the ringer if an incoming call is currently ringing.
     * (If vibrating, stop the vibrator also.)
     *
     * It's safe to call this if the ringer has already been silenced, or
     * even if there's no incoming call.  (If so, this method will do nothing.)
     *
     * TODO: this should be a oneway call too (see above).
     *       (Actually *all* the methods here that return void can
     *       probably be oneway.)
     */
    void silenceRinger();

    /**
     * Check if a particular subscription has an active or holding call
     *
     * @param subscription user preferred subscription.
     * @return true if the phone state is OFFHOOK.
     */
    boolean isOffhook(int subscription);

    /**
     * Check if an incoming phone call is ringing or call waiting
     * on a particular subscription.
     *
     * @param subscription user preferred subscription.
     * @return true if the phone state is RINGING.
     */
    boolean isRinging(int subscription);

    /**
     * Check if the phone is idle on a particular subscription.
     *
     * @param subscription user preferred subscription.
     * @return true if the phone state is IDLE.
     */
    boolean isIdle(int subscription);

    /**
     * Check to see if the radio is on or not on particular subscription.
     * @param subscription user preferred subscription.
     * @return returns true if the radio is on.
     */
    boolean isRadioOn(int subscription);

    /**
     * Check if the SIM pin lock is enable
     * for particular subscription.
     * @param subscription user preferred subscription.
     * @return true if the SIM pin lock is enabled.
     */
    boolean isSimPinEnabled(int subscription);

    /**
     * Cancels the missed calls notification on particular subscription.
     * @param subscription user preferred subscription.
     */
    void cancelMissedCallsNotification(int subscription);

    /**
     * Supply a pin to unlock the SIM for particular subscription.
     * Blocks until a result is determined.
     * @param pin The pin to check.
     * @param subscription user preferred subscription.
     * @return whether the operation was a success.
     */
    boolean supplyPin(String pin, int subscription);

    /**
     * Supply puk to unlock the SIM and set SIM pin to new pin.
     *  Blocks until a result is determined.
     * @param puk The puk to check.
     *        pin The new pin to be set in SIM
     * @param subscription user preferred subscription.
     * @return whether the operation was a success.
     */
    boolean supplyPuk(String puk, String pin, int subscription);

    /**
    * Gets the number of attempts remaining for PIN1/PUK1 unlock.
    * @param subscription for which attempts remaining is required.
    */
    int getIccPin1RetryCount(int subscription);

    /**
     * Handles PIN MMI commands (PIN/PIN2/PUK/PUK2), which are initiated
     * without SEND (so <code>dial</code> is not appropriate) for
     * a particular subscription.
     * @param dialString the MMI command to be executed.
     * @param subscription user preferred subscription.
     * @return true if MMI command is executed.
     */
    boolean handlePinMmi(String dialString, int subscription);

    /**
     * Toggles the radio on or off on particular subscription.
     * @param subscription user preferred subscription.
     */
    void toggleRadioOnOff(int subscription);

    /**
     * Set the radio to on or off on particular subscription.
     * @param subscription user preferred subscription.
     */
    boolean setRadio(boolean turnOn, int subscription);

    /**
     * Request to update location information for a subscrition in service state
     * @param subscription user preferred subscription.
     */
    void updateServiceLocation(int subscription);

    /**
     * Enable a specific APN type.
     */
    int enableApnType(String type);

    /**
     * Disable a specific APN type.
     */
    int disableApnType(String type);

    /**
     * Allow mobile data connections.
     */
    boolean enableDataConnectivity();

    /**
     * Disallow mobile data connections.
     */
    boolean disableDataConnectivity();

    /**
     * Report whether data connectivity is possible.
     */
    boolean isDataConnectivityPossible();

    /**
     * Returns the call state for a subscription.
     */
     int getCallState(int subscription);
     int getDataActivity();
     int getDataState();

    /**
     * Returns the current active phone type as integer for particular subscription.
     * Returns TelephonyManager.PHONE_TYPE_CDMA if RILConstants.CDMA_PHONE
     * and TelephonyManager.PHONE_TYPE_GSM if RILConstants.GSM_PHONE
     * @param subscription user preferred subscription.
     */
    int getActivePhoneType(int subscription);


    /**
     * Returns the CDMA ERI icon index to display on particular subscription.
     * @param subscription user preferred subscription.
     */
    int getCdmaEriIconIndex(int subscription);

    /**
     * Returns the CDMA ERI icon mode on particular subscription,
     * 0 - ON
     * 1 - FLASHING
     * @param subscription user preferred subscription.
     */
    int getCdmaEriIconMode(int subscription);

    /**
     * Returns the CDMA ERI text for particular subscription,
     * @param subscription user preferred subscription.
     */
    String getCdmaEriText(int subscription);

    /**
     * Returns true if OTA service provisioning needs to run.
     * Only relevant on some technologies, others will always
     * return false.
     */
    boolean needsOtaServiceProvisioning();

    /**
     * Returns the unread count of voicemails for a subscription.
     * @param subscription user preferred subscription.
     * Returns the unread count of voicemails
     */
    int getVoiceMessageCount(int subscription);

    /**
     * Returns the network type of a subscription.
     * @param subscription user preferred subscription.
     * Returns the network type
     */
    int getNetworkType(int subscription);

    /**
      * Returns the data network type of a subscription
      * @param subscription user preferred subscription.
      * Returns the network type
      */
    int getDataNetworkType(int subscription);

    /**
      * Returns the voice network type of a subscription
      * @param subscription user preferred subscription.
      * Returns the network type
      */
    int getVoiceNetworkType(int subscription);

    /**
     * Return true if an ICC card is present for a subscription.
     * @param subscription user preferred subscription.
     * Return true if an ICC card is present
     */
    boolean hasIccCard(int subscription);

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link Phone#LTE_ON_CDMA_UNKNOWN}, {@link Phone#LTE_ON_CDMA_FALSE}
     * or {@link PHone#LTE_ON_CDMA_TRUE}
     */
    int getLteOnCdmaMode(int subscription);

    /**
     * Returns the all observed cell information of the device.
     */
    List<CellInfo> getAllCellInfo();

    /**
     * get default subscription
     * @return subscription id
     */
    int getDefaultSubscription();

    /**
     * get user prefered voice subscription
     * @return subscription id
     */
    int getPreferredVoiceSubscription();

    /**
     * get user prefered data subscription
     * @return subscription id
     */
    int getPreferredDataSubscription();

    /*
     * Set user prefered data subscription
     * @return true if success
     */
    boolean setPreferredDataSubscription(int subscription);
}

