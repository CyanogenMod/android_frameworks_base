/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.internal.telephony;


/**
 * Interface used to interact with the telephony framework for
 * Telephony value adds.
 * {@hide}
 */
interface IExtTelephony {

    /**
     * Returns the current SIM Manual provision status.
     * @param slotId user preferred slotId.
     * @return Card provision status as integer, below are
     * possible return values.
     *   '0' - returned if Uicc Card is not provisioned.
     *   '1' - returned if Uicc Card provisioned.
     *  '-1'-  returned if there is an error @ below layers OR
     *         if framework does not received info from Modem yet.
     *  '-2'  returned when SIM card is not present in slot.
     * Requires Permission: android.Manifest.permission.READ_PHONE_STATE
     */
    int getCurrentUiccCardProvisioningStatus(int slotId);

    /**
     * Returns the user preferred Uicc card provision status.
     * @param slotId user preferred slotId.
     * @return User preference value as integer, below are
     * possible return values.
     *   '0' - returned if Uicc Card is not provisioned.
     *   '1' - returned if Uicc Card provisioned.
     *  '-1'-  returned if there is an error @ below layers OR
     *         if framework does not received info from Modem yet.
     *  '-2'  returned when SIM card is not present in slot.
     * Requires Permission: android.Manifest.permission.READ_PHONE_STATE
     */
    int getUiccCardProvisioningUserPreference(int slotId);

    /**
     * Activates the Uicc card.
     * @param slotId user preferred slotId.
     * @return Uicc card activation result as Integer, below are
     *         supported return values:
     *         '0' - Success
     *        '-1' -Generic Failure
     *        '-2' -Invalid input
     *        '-3  -Another request in progress
     * Requires Permission: android.Manifest.permission.MODIFY_PHONE_STATE
     */
    int activateUiccCard(int slotId);

    /**
     * Deactivates UICC card.
     * @param slotId user preferred slotId.
     * @return Uicc card deactivation result as Integer, below are
     *     supported return values:
     *     '0' - Success
     *     '-1' -Generic Failure
     *     '-2' -Invalid input
     *     '-3  -Another request in progress
     * Requires Permission: android.Manifest.permission.MODIFY_PHONE_STATE
     */
    int deactivateUiccCard(int slotId);

    /**
    * Check for Sms Prompt is Enabled or Not.
    * @return
    *        true - Sms Prompt is Enabled
    *        false - Sms prompt is Disabled
    * Requires Permission: android.Manifest.permission.READ_PHONE_STATE
    */
    boolean isSMSPromptEnabled();

    /**
    * Enable/Disable Sms prompt option.
    * @param - enabled
    *        true - to enable Sms prompt
    *        false - to disable Sms prompt
    * Requires Permission: android.Manifest.permission.MODIFY_PHONE_STATE
    */
    void setSMSPromptEnabled(boolean enabled);

    /**
    * Get logical phone id for Emergency call.
    * @param - void
    * @return phone id
    */
    int getPhoneIdForECall();

    /**
    * Check is FDN is enabled or not.
    * @param - void
    * @return true or false
    */
    boolean isFdnEnabled();

    /**
    * Get application count from card.
    * @param - slotId user preferred slotId
    * @return application count
    */
    int getUiccApplicationCount(int slotId);

    /**
    * Get application type by index.
    * @param - slotId user preferred slotId
    *        - appIndex application index
    * @return application type as Integer, below are
    *     supported return values:
    *     '0' - APPTYPE_UNKNOWN
    *     '1' - APPTYPE_SIM
    *     '2' - APPTYPE_USIM
    *     '3  - APPTYPE_RUIM
    *     '4' - APPTYPE_CSIM
    *     '5' - APPTYPE_ISIM
    */
    int getUiccApplicationType(int slotId, int appIndex);

    /**
    * Get application state by index.
    * @param - slotId user preferred slotId
    *        - appIndex application index
    * @return application state as Integer, below are
    *     supported return values:
    *     '0' - APPSTATE_UNKNOWN
    *     '1' - APPSTATE_DETECTED
    *     '2' - APPSTATE_PIN
    *     '3  - APPSTATE_PUK
    *     '4' - APPSTATE_SUBSCRIPTION_PERSO
    *     '5' - APPSTATE_READY
    */
    int getUiccApplicationState(int slotId, int appIndex);

    /**
    * Get primary stack phone id.
    * @param - void
    * @return phone id
    */
    int getPrimaryStackPhoneId();

}
