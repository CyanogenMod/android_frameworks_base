/*
 *
 *  Copyright (C) 2015 NXP Semiconductors
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
package com.gsma.services.utils;

import com.nxp.nfc.gsma.internal.NxpHandset;
import android.content.Context;
import android.util.Log;
import java.util.List;
/**
 * This class handles the handset configuration & properties
 * @since NFCHST6.0
 */
public class Handset {

    /** Device property [Contactless Frontend]*/
    public static final int HCI_SWP=0x00;
    /** Device property [Contactless Frontend]*/
    public static final int MULTIPLE_ACTIVE_CEE=0x01;

    /** Device property [NFC Technologies]*/
    public static final int FELICA=0x20;
    /** Device property [NFC Technologies]*/
    public static final int MIFARE_CLASSIC=0x21;
    /** Device property [NFC Technologies]*/
    public static final int MIFARE_DESFIRE=0x22;
    /** Device property [NFC Technologies]*/
    public static final int NFC_FORUM_TYPE3=0x23;

    /** Device property [Framework components]*/
    public static final int OMAPI=0x50;

    /** Device property [Battery Levels]*/
    public static final int BATTERY_LOW_MODE=0x90;
    /** Device property [Battery levels]*/
    public static final int BATTERY_POWER_OFF_MODE=0x91;

    /**
     * Device property [Battery levels]
     * @since NFCHST8.0
     */
    public static final int BATTERY_OPERATIONAL_MODE=0x92;
    /** Device property [Remote Access]*/
    public static final int BIP=0x93;
    /** Device property [Remote Access]*/
    public static final int CAT_TP=0x94;

    private NxpHandset mNxpHandset = null;
    private String TAG = "Handset";


    public Handset() {
        mNxpHandset = new NxpHandset();
        if(mNxpHandset == null){
            Log.d(TAG,"mNxpHandset is Null ");
        }
    }

    /**
     * Return the version of device requirements supported.
     * @since NFCHST6.0 <I>(REQ_132)</I>
     */
    public int getVersion() {
        return mNxpHandset.getNxpVersion();
    }

    /**
     * Return handset status for the following features:<BR><UL>
     * <LI>{@link Handset#HCI_SWP}, {@link Handset#MULTIPLE_ACTIVE_CEE}</LI>
     * <LI>{@link Handset#FELICA}, {@link Handset#MIFARE_CLASSIC}, {@link Handset#MIFARE_DESFIRE},
     *     {@link Handset#NFC_FORUM_TYPE3}</LI>
     * <LI>{@link Handset#OMAPI}</LI>
     * <LI>{@link Handset#BATTERY_LOW_MODE}, {@link Handset#BATTERY_POWER_OFF_MODE}</LI>
     * <LI>{@link Handset#BIP}, {@link Handset#CAT_TP}</LI></UL>
     * @param feature Requested feature
     * @return <code>true</code> if the feature is supported; <code>false</code> otherwise
     * @exception IllegalArgumentException <BR>Indicate that a method has been passed an illegal or inappropriate argument.
     * @since NFCHST6.0 <I>(REQ_132)</I>
     */
    public boolean getProperty(int feature) {
        return mNxpHandset.getNxpProperty(feature);
    }

    /**
     * Return the list of Secure Elements which can be used by the NFC Controller
     * when handset is operating in a following battery level.<BR><UL>
     * <LI>{@link Handset#BATTERY_LOW_MODE}</LI>
     * <LI>{@link Handset#BATTERY_POWER_OFF_MODE}</LI>
     * <LI>{@link Handset#BATTERY_OPERATIONAL_MODE}</LI></UL>
     * @param batteryLevel Battery level the handset is operating
     * @return The list of secure Element names , or Collections.emptyList () if none of them are supported.
     * @since TNFCHST8.0 <I>(REQ_059/REQ_060)</I>
     * */
     public List<String> getAvailableSecureElements(int batteryLevel) {
         return mNxpHandset.getAvailableSecureElements(batteryLevel);
     }
    /**
     * Asks the system to inform "transaction events" to any authorized/registered components via <code>BroadcastReceiver</code>.<BR>
     * Change SHALL not imply a power cycle and SHALL be valid until next handset reboot.<BR><BR>
     * <I>Applications SHALL register to <code>com.gsma.services.nfc.TRANSACTION_EVENT</code> for receiving related events.</I>
     * @exception SecurityException <BR>Indicate that application is not allowed to use this API.<UL>
     * <LI>When UICC is the "active" SE,
     * <BR>only applications signed with certificates stored in the UICC are granted to call this API.</LI>
     * <LI>When eSE is the "active" SE,
     * <BR>only applications signed with system certificates are granted to call this API.</LI></UL>
     * @since NFCHST6.0 <I>(REQ_99)</I>
     */
    public void enableMultiEvt_transactionReception() throws SecurityException {
        mNxpHandset.enableMultiEvt_transactionReception();
    }

}
