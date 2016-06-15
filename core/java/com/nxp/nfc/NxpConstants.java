/*
*
*  The original Work has been changed by NXP Semiconductors.
*
*  Copyright (C) 2013-2014 NXP Semiconductors
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*
*/

package com.nxp.nfc;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;

    /**
     * This class provides the constants ID types.
     */

public final class NxpConstants {
    /**
     * UICC ID to be able to select it as the default Secure Element
     */
    public static final String UICC_ID = "com.nxp.uicc.ID";

    /**
     *@hide
     */
    public static final int UICC_ID_TYPE = 2;

    /**
     * eSE ID to be able to select it as the default Secure Element
     */
    public static final String SMART_MX_ID = "com.nxp.smart_mx.ID";

    /**
     *@hide
     */
    public static final int SMART_MX_ID_TYPE = 1;
    /**
     * UICC ID to be able to select it as the default Secure Element
     */

    /**
     * ID to be able to select all Secure Elements
     * @hide
     */
    public static final String ALL_SE_ID = "com.nxp.all_se.ID";

    /**
     *@hide
     */
    public static final int ALL_SE_ID_TYPE = 3;

    public static final String HOST_ID = "com.nxp.host.ID";

    /**
     *@hide
     */
    public static final int HOST_ID_TYPE = 0;

    /**
     * Broadcast Action: Multiple card presented to emvco reader.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_EMVCO_MULTIPLE_CARD_DETECTED =
            "com.nxp.action.EMVCO_MULTIPLE_CARD_DETECTED";

    /**
     * Broadcast Action: a transaction with a secure element has been detected.
     *
     * Always contains the extra field
     * {@link com.nxp.nfc.NxpConstants#EXTRA_AID} and {@link com.nxp.nfc.NxpConstants#EXTRA_SOURCE}
     *
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_TRANSACTION_DETECTED =
            "com.nxp.action.TRANSACTION_DETECTED";

    /**
     * Mandatory byte array extra field in
     * {@link com.nxp.nfc.NxpConstants#ACTION_TRANSACTION_DETECTED}.
     * <p>
     * Contains the AID of the applet involved in the transaction.
     *
     */
    public static final String EXTRA_AID = "com.nxp.extra.AID";

    /**
     * Mandatory byte array extra field in
     * {@link com.nxp.nfc.NxpConstants#ACTION_TRANSACTION_DETECTED}.
     * <p>
     * Contains the extra data of the applet involved in the transaction.
     *
     */
    public static final String EXTRA_DATA = "com.nxp.extra.DATA";

    /**
     * Broadcast Action: a connectivity event coming from the UICC/ESE
     * has been detected.
     * <p>
     * Always contains the extra field
     * {@link com.nxp.nfc.NxpConstants#EXTRA_SOURCE}
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTIVITY_EVENT_DETECTED =
            "com.nxp.action.CONNECTIVITY_EVENT_DETECTED";

    /**
     * Mandatory string extra field in
     * {@link com.nxp.nfc.NxpConstants#ACTION_TRANSACTION_DETECTED} and
     * {@link com.nxp.nfc.NxpConstants#ACTION_CONNECTIVITY_EVENT_DETECTED}.
     * <p>
     * Contains the event source (UICC/ESE) of the transaction.
     *
     */
    public static final String EXTRA_SOURCE = "com.nxp.extra.SOURCE";

    /**
     * Intent received when the SWP Reader is Requested by Application
     *
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SWP_READER_REQUESTED = "com.nxp.nfc_extras.ACTION_SWP_READER_REQUESTED";

    /**
     * Intent received when the SWP Reader is Requested by Application
     *
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SWP_READER_REQUESTED_FAILED = "com.nxp.nfc_extras.ACTION_SWP_READER_REQUESTED_FAILED";

   /**
     * Intent received when the SWP Reader is connected to card.
     *
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SWP_READER_ACTIVATED = "com.nxp.nfc_extras.ACTION_SWP_READER_ACTIVATED";

    /**
     * Intent received when the SWP Reader is disconnected from card.
     *
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SWP_READER_DEACTIVATED = "com.nxp.nfc_extras.ACTION_SWP_READER_DEACTIVATED";

    /**
     * Intent received when the SWP Reader is started and ready to transcation.
     *
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SWP_READER_TAG_PRESENT = "com.nxp.nfc_extras.ACTION_SWP_READER_TAG_PRESENT";

    /**
     * Intent received when the SWP Reader transcation is done.
     *
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_SWP_READER_TAG_REMOVE = "com.nxp.nfc_extras.ACTION_SWP_READER_TAG_REMOVE";


    public static final String ACTION_ROUTING_TABLE_FULL = "nfc.intent.action.AID_ROUTING_TABLE_FULL";

    public static final String ACTION_MULTI_EVT_TRANSACTION = "com.gsma.services.nfc.action.TRANSACTION_EVENT";
    public static final String ACTION_CHECK_X509 = "org.simalliance.openmobileapi.service.ACTION_CHECK_X509";
    public static final String SET_PACKAGE_NAME = "org.simalliance.openmobileapi.service";
    public static final String EXTRA_SE_NAME = "org.simalliance.openmobileapi.service.EXTRA_SE_NAME";
    public static final String EXTRA_PKG = "org.simalliance.openmobileapi.service.extra.EXTRA_PKG";
    public static final String EXTRA_RESULT = "org.simalliance.openmobileapi.service.extra.EXTRA_RESULT";

    public static final String ACTION_CHECK_X509_RESULT = "org.simalliance.openmobileapi.service.ACTION_CHECK_X509_RESULT";
    public static final String PERMISSIONS_TRANSACTION_EVENT = "com.gsma.services.nfc.permission.TRANSACTION_EVENT";
    public static final String EXTRA_GSMA_AID = "com.gsma.services.nfc.extra.AID";
    public static final String EXTRA_GSMA_DATA = "com.gsma.services.nfc.extra.DATA";
    public static final String ACTION_GSMA_ENABLE_NFC = "com.gsma.services.nfc.action.ENABLE_NFC";
    public static final String ACTION_GSMA_ENABLE_SET_FLAG = "com.gsma.services.nfc.action.ENABLE_NFC_SET_FALG";


}
