/*
* Copyright (C) 2015 NXP Semiconductors
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
package com.nxp.nfc.gsma.internal;

import com.nxp.nfc.gsma.internal.NxpNfcController;
import android.os.RemoteException;
import java.lang.SecurityException;
import android.util.Log;
import com.nxp.nfc.NxpConstants;
import android.nfc.NfcAdapter;
import com.nxp.nfc.NxpNfcAdapter;
import android.content.Context;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;
/**
 * This class handles the handset configuration & properties
 */
public class NxpHandset {

    /** Device property [Contactless Frontend]*/
    private static final int HCI_SWP=0x00;
    /** Device property [Contactless Frontend]*/
    private static final int MULTIPLE_ACTIVE_CEE=0x01;

    /** Device property [NFC Technologies]*/
    private static final int FELICA=0x20;
    /** Device property [NFC Technologies]*/
    private static final int MIFARE_CLASSIC=0x21;
    /** Device property [NFC Technologies]*/
    private static final int MIFARE_DESFIRE=0x22;
    /** Device property [NFC Technologies]*/
    private static final int NFC_FORUM_TYPE3=0x23;


    /** Device property [Battery Levels]*/
    private static final int BATTERY_LOW_MODE=0x90;
    /** Device property [Battery levels]*/
    private static final int BATTERY_POWER_OFF_MODE=0x91;
    /** Device property [Battery levels]*/
    private static final int BATTERY_OPERATIONAL_MODE=0x92;
    private String TAG = "NxpHandset";

    private NfcAdapter mNfcAdapter = null;
    private NxpNfcAdapter mNxpNfcAdapter = null;
    private INxpNfcController mNfcControllerService = null;
    private Context mContext;



    private final int GSMA_NFCHST = 8000;

    public NxpHandset() {
        mContext = getContext();
        if(mNfcAdapter == null)
            mNfcAdapter = NfcAdapter.getNfcAdapter(mContext);
        if((mNxpNfcAdapter == null) && (mNfcAdapter != null))
            mNxpNfcAdapter = NxpNfcAdapter.getNxpNfcAdapter(mNfcAdapter);

        if(mNfcControllerService == null) {
            mNfcControllerService = mNxpNfcAdapter.getNxpNfcControllerInterface();
        }
    }

    private Context getContext() {
        Context context = null;
        try {
            context = (Context) Class.forName("android.app.ActivityThread")
                                       .getMethod("currentApplication").invoke(null, (Object[]) null);
        } catch (final Exception e1) {
            try {
                context = (Context) Class.forName("android.app.AppGlobals")
                                          .getMethod("getInitialApplication").invoke(null, (Object[]) null);
            } catch (final Exception e2) {
                throw new RuntimeException("Failed to get application instance");
            }
        }
        return context;
    }

    /**
     * Return the version of device requirements supported.
     */
    public int getNxpVersion() {
        return GSMA_NFCHST;
    }

    /**
     * Return handset status for the following features
     * HCI_SWP, MULTIPLE_ACTIVE_CEE FELICA,
     * MIFARE_CLASSIC, MIFARE_DESFIRE,
     * NFC_FORUM_TYPE3 OMAPI BATTERY_LOW_MODE,
     * BATTERY_POWER_OFF_MODE
     */
    public boolean getNxpProperty(int feature) {
        boolean result = false;
        if((feature != HCI_SWP) && (feature != MULTIPLE_ACTIVE_CEE) && (feature != FELICA) && (feature != MIFARE_CLASSIC)
            && (feature != MIFARE_DESFIRE) && (feature != NFC_FORUM_TYPE3) && (feature != BATTERY_LOW_MODE)
            && (feature != BATTERY_POWER_OFF_MODE))
            throw new IllegalArgumentException("Feature is inappropriate argument");

        switch(feature) {
            case HCI_SWP:
            case MULTIPLE_ACTIVE_CEE:
            case FELICA:
            case MIFARE_CLASSIC:
            case MIFARE_DESFIRE:
            case NFC_FORUM_TYPE3:
            case BATTERY_LOW_MODE:
            case BATTERY_POWER_OFF_MODE:
                 result = true;
                break;

            default:
                result = false;
        }
        return result;
    }

    public List<String> getAvailableSecureElements(int batteryLevel) {
        String pkg = mContext.getPackageName();
        String[] secureElemArray = null;
        List<String> secureElementList = new ArrayList<String>(0x03);
        switch(batteryLevel) {

        case BATTERY_LOW_MODE:
        case BATTERY_POWER_OFF_MODE:
        case BATTERY_OPERATIONAL_MODE:
            try {
                secureElemArray = mNxpNfcAdapter.getActiveSecureElementList(pkg);
            } catch(IOException e) {
                secureElemArray = null;
            }
            break;
        default:
            break;
        }
        if(secureElemArray != null && secureElemArray.length > 0x00) {
            Collections.addAll(secureElementList , secureElemArray);
            return secureElementList;
        }
        return Collections.emptyList();
    }
    /**
     * Asks the system to inform "transaction events" to any authorized/registered components via <code>BroadcastReceiver</code>.<BR>
     * Change SHALL not imply a power cycle and SHALL be valid until next handset reboot.<BR><BR>
     */
    public void enableMultiEvt_transactionReception() {
        String pkg = mContext.getPackageName();
        boolean isEnabled = false;
        Log.d(TAG,"pkg " + pkg);
        try {
            isEnabled = mNfcControllerService.enableMultiEvt_NxptransactionReception(pkg, NxpConstants.UICC_ID);
        } catch (RemoteException e) {
            Log.e(TAG, "Exception:commitOffHostService failed", e);
        }

        if(!isEnabled)
            throw new SecurityException("Application is not allowed to use this API");
    }
}
