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
package com.gsma.services.nfc;

import android.content.Context;
import android.util.Log;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.content.Intent;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Random;

import android.util.Log;
import android.view.View.OnClickListener;
import android.content.DialogInterface;
import android.app.Activity;
import com.nxp.nfc.gsma.internal.NxpNfcController;
import com.nxp.nfc.gsma.internal.NxpNfcController.NxpCallbacks;
import com.nxp.nfc.gsma.internal.NxpOffHostService;
import com.gsma.services.utils.InsufficientResourcesException;
import android.os.UserHandle;
/**
 * This class handles the NFC Controller
 * @since NFCHST4.1
 */
public class NfcController {

    static final String TAG = "NfcController";
    /**
     * The NfcController object for each application context.
     * There is a 1-1 relationship between application context and
     * NfcController object.
     */
    private static HashMap<Context, NfcController> sNfcController = new HashMap();

    private ArrayList<OffHostService> mOffHostServiceList = new ArrayList<OffHostService>();
    private  NxpNfcController mNxpNfcController = null;
    private HashMap<String, OffHostService> mOffhostService = new HashMap<String, OffHostService>();
    private Context mContext;
    private NfcController.Callbacks mCb;
    private int mUserId;
    private NxpNfcControllerCallback mNxpCallback = null;

    NfcController() {
        mUserId = UserHandle.myUserId();
        mNxpCallback = new NxpNfcControllerCallback();
        mNxpNfcController = new NxpNfcController();
    }

    NfcController(Context context) {
        mContext = context;
        mNxpCallback = new NxpNfcControllerCallback();
        mNxpNfcController = new NxpNfcController(context);
        mUserId = UserHandle.myUserId();
    }

    /** <code>NFC_RF_TECHNOLOGY_A</code> as defined by
     * <a href="http://www.nfc-forum.org" target="_blank">NFC Controller Interface (NCI)</a> specification*/
    public static final int TECHNOLOGY_NFC_A=0x01;
    /** <code>NFC_RF_TECHNOLOGY_B</code> as defined by
     * <a href="http://www.nfc-forum.org" target="_blank">NFC Controller Interface (NCI)</a> specification*/
    public static final int TECHNOLOGY_NFC_B=0x02;
    /** <code>NFC_RF_TECHNOLOGY_F</code> as defined by
     * <a href="http://www.nfc-forum.org" target="_blank">NFC Controller Interface (NCI)</a> specification*/
    public static final int TECHNOLOGY_NFC_F=0x04;

    /** <code>PROTOCOL_ISO_DEP</code> as defined by
     * <a href="http://www.nfc-forum.org" target="_blank">NFC Controller Interface (NCI)</a> specification*/
    public static final int PROTOCOL_ISO_DEP=0x10;

    /** Battery of the handset is in "Operational" mode*/
    public static final int BATTERY_OPERATIONAL_STATE=0x01;
    /** Any battery power levels*/
    public static final int BATTERY_ALL_STATES=0x02;

    /** Screen is "ON" (not in "Screen Off" mode) and locked*/
    public static final int SCREEN_ON_AND_LOCKED_MODE=0x01;
    /** Any screen mode*/
    public static final int SCREEN_ALL_MODES=0x02;


    // Callback interface

    /**
     * This interface provide callback methods for {@link NfcController} class
     * @since NFCHST4.1
     */
    public static interface Callbacks {

        /** Card Emulation mode has been disabled*/
        public static final int CARD_EMULATION_DISABLED=0x00;
        /** Card Emulation mode has been enabled*/
        public static final int CARD_EMULATION_ENABLED=0x01;
        /** An error occurred when handset tried to enable/disable Card Emulation mode*/
        public static final int CARD_EMULATION_ERROR=0x100;

        /**
         * Called when process for getting the default Controller is finished.
         * @param controller Instance of default controller or <code>null</code> if an error occurred
         * @since NFCHST5.0
         */
        public abstract void onGetDefaultController(NfcController controller);

        /**
         * Called when process for enabling the NFC Controller is finished.
         * @param success <code>true</code> if the NFC adapter is enabled; <code>false</code> otherwise
         * @since NFCHST5.0
         */
        public abstract void onEnableNfcController(boolean success);

        /**
         * Called when process for enabling/disabling the Card Emulation mode is finished.
         * @param status Status of the Card Emulation mode as defined below<BR>
         * {@link Callbacks#CARD_EMULATION_DISABLED}, {@link Callbacks#CARD_EMULATION_ENABLED},
         * {@link Callbacks#CARD_EMULATION_ERROR}
         * @since NFCHST4.1
         * @deprecated <a style="color:#FF0000">When Host Card Emulation (HCE) is supported</a>
         */
        public abstract void onCardEmulationMode(int status);

    }


    public class NxpNfcControllerCallback implements NxpCallbacks {

        public void onNxpEnableNfcController(boolean success) {
            if(success == true) {
                mCb.onEnableNfcController(true);
                Log.d(TAG, "NFC Enabled");
            } else {
                mCb.onEnableNfcController(false);
                Log.d(TAG, "NFC Not Enabled");
            }
        }
    }

    // Handling the NFC Controller

    /**
     * Helper for getting an instance of the NFC Controller.
     * @param context Calling application's context
     * @param callbacks Callback interface
     * @since NFCHST4.1
     */
    public static void getDefaultController(Context context, NfcController.Callbacks callbacks) {
        if (context == null || callbacks == null) {
            throw new IllegalArgumentException("context or NfcController.Callbacks cannot be null");
        }
        NfcController controller = new NfcController(context);
        callbacks.onGetDefaultController(controller);
    }


    /**
     * Check if the NFC Controller is enabled or disabled.
     * @return <code>true</code> if the NFC adapter is enabled; <code>false</code> otherwise
     * @since NFCHST4.1 <I>(REQ_093)</I>
     */
    public boolean isEnabled() {
        return mNxpNfcController.isNxpNfcEnabled();
    }

    /**
     * Asks the system to enable the NFC Controller. User input is required to enable NFC.<BR>
     * A question will be asked if the user wants to enable NFC or not.
     * <center><img src="EnableNfcController.png" width="40%" height="40%"/></center><BR>
     * This question shall be generated within the OS context.
     * @param cb Callback interface
     * @since NFCHST4.1 <I>(REQ_093)</I>
     */
    public void enableNfcController(NfcController.Callbacks cb) {
        mCb =cb;
        if(isEnabled()) {
            mCb.onEnableNfcController(true);
        } else {
            mNxpNfcController.enableNxpNfcController(mNxpCallback);
       }
    }

    // Handling Card Emulation
    /**
     * Check if the Card Emulation mode is enabled or disabled.
     * @return <code>true</code> if the Card Emulation mode is enabled; <code>false</code> otherwise
     * @since NFCHST4.1 <I>(REQ_126)</I>
     * @deprecated <a style="color:#FF0000">When Host Card Emulation (HCE) is supported</a>
     */
    @Deprecated
    public boolean isCardEmulationEnabled() throws Exception {
        throw new InsufficientResourcesException("Host Card Emulation (HCE) is supported");
    }

    /**
     * Asks the system to enable the Card Emulation mode.<BR>
     * Change is not persistent and SHALL be overridden by the following events:<UL>
     * <LI>Turning OFF and ON the NFC Controller</LI>
     * <LI>Full power cycle of the handset</LI></UL>
     * @param cb Callback interface
     * @exception IllegalStateException <BR>Indicate that NFC Controller is not enabled.
     * @exception SecurityEception <BR>Indicate that application is not allowed to use this API.<UL>
     * <LI>When UICC is the "active" SE,
     * <BR>only applications signed with certificates stored in the UICC are granted to call this API.</LI>
     * <LI>When eSE is the "active" SE,
     * <BR>only applications signed with system certificates are granted to call this API.</LI></UL>
     * @since NFCHST4.1 <I>(REQ_126)</I>
     * @deprecated <a style="color:#FF0000">When Host Card Emulation (HCE) is supported</a>
     */
    @Deprecated
    public void enableCardEmulationMode(NfcController.Callbacks cb) throws Exception {
         throw new InsufficientResourcesException("Host Card Emulation (HCE) is supported");

    }

    /**
     * Asks the system to disable the Card Emulation mode.<BR>
     * Change is not persistent and SHALL be overridden by the following events:<UL>
     * <LI>Turning OFF and ON the NFC Controller</LI>
     * <LI>Full power cycle of the handset</LI></UL>
     * @param cb Callback interface
     * @exception SecurityException <BR>Indicate that application is not allowed to use this API.<UL>
     * <LI>When UICC is the "active" SE,
     * <BR>only applications signed with certificates stored in the UICC are granted to call this API.</LI>
     * <LI>When eSE is the "active" SE,
     * <BR>only applications signed with system certificates are granted to call this API.</LI></UL>
     * @since NFCHST4.1 <I>(REQ_126)</I>
     * @deprecated <a style="color:#FF0000">When Host Card Emulation (HCE) is supported</a>
     */
    @Deprecated
    public void disableCardEmulationMode(NfcController.Callbacks cb) throws Exception {
        throw new InsufficientResourcesException("Host Card Emulation (HCE) is supported");
    }

    /**
     *  Generate random number for creating service name
     */
    private String getRandomString() {
        Random random = new Random();
        int randomNum = 10000 + random.nextInt(10000);
        return new String("service" + Integer.toString(randomNum));
    }

    // Handling AID routes
    /**
     * Create a new "Off-Host" service.
     * @param description Description of the "Off-Host" service
     * @param SEName Secure Element name holding the "Off-Host" service
     * @exception UnsupportedOperationException <BR>Indicate that Host Card Emulation (HCE) is not supported.
     * @return An instance of an {@link OffHostService} class
     * @since NFCHST6.0 <I>(REQ_127)</I>
     */
    public OffHostService defineOffHostService(String description, String SEName) {
        boolean modifiable = true;
        String packageName = mContext.getPackageName();
        String serviceName = getRandomString();
        NxpOffHostService offHostService = new NxpOffHostService(mUserId, description, SEName, packageName,serviceName, modifiable);
        offHostService.setContext(mContext);
        offHostService.setNxpNfcController(mNxpNfcController);
        return new OffHostService(offHostService);
    }

    /**
     * Delete an existing dynamically created "Off-Host" service.
     * @param service Instance of an {@link OffHostService} class to be deleted
     * @since NFCHST6.0 <I>(REQ_127)</I>
     */
    public void deleteOffHostService(OffHostService service) {
        String packageName = mContext.getPackageName();
        mNxpNfcController.deleteOffHostService(mUserId, packageName, convertToNxpOffhostService(service));
    }

    /**
     * Return a list of "Off-Host" services created dynamically by the calling application.<BR>
     * <BR><I>Note: In the next release, if it is not breaking "Android CDD",<BR>
     * it is planned also to support "Off-Host" services registered statically (Manifest)</I>
     * @return A list of {@link OffHostService} instances or <code>null</code> if no such instance exists.
     * @since NFCHST6.0 <I>(REQ_127)</I>
     */
    public OffHostService[] getOffHostServices() {
        String packageName = mContext.getPackageName();
        ArrayList<NxpOffHostService> mNxpOffhost = mNxpNfcController.getOffHostServices(mUserId, packageName);
        ArrayList<OffHostService> mOffHostList = new ArrayList<OffHostService>();
        for(NxpOffHostService mHost : mNxpOffhost) {
            OffHostService  mOffHost = new OffHostService(mHost);
            mOffHostList.add(mOffHost);
        }
        OffHostService offHostServices[] = new OffHostService[mOffHostList.size()];
        return mOffHostList.toArray(offHostServices);
    }

    /**
     * Return the "Off-Host" service related to the current selected "Tap&Pay" menu entry.
     * @return A {@link OffHostService} instance or <code>null</code> if<UL>
     * <LI>The "Tap&Pay" menu entry has not been created by the calling application</IL>
     * <LI>The "Tap&Pay" menu entry has not been created dynamically</IL></UL>
     * @since NFCHST6.0 <I>(REQ_127)</I>
     */
    public OffHostService getDefaultOffHostService() {
        String packageName = mContext.getPackageName();
        NxpOffHostService service = mNxpNfcController.getDefaultOffHostService(mUserId, packageName);
        if(service != null) {
            return new OffHostService(service);
        }
        return null;
    }
    private ArrayList<android.nfc.cardemulation.AidGroup> convertToCeAidGroupList(List<com.gsma.services.nfc.AidGroup> mAidGroups) {
        ArrayList<android.nfc.cardemulation.AidGroup> mApduAidGroupList = new ArrayList<android.nfc.cardemulation.AidGroup>();
        android.nfc.cardemulation.AidGroup mCeAidGroup = null;
        List<String> aidList = new ArrayList<String>();
        for(com.gsma.services.nfc.AidGroup mGroup : mAidGroups) {
            mCeAidGroup = new android.nfc.cardemulation.AidGroup(mGroup.getCategory(), mGroup.getDescription());
            aidList = mCeAidGroup.getAids();
            for(String aid :mGroup.getAidList()) {
                aidList.add(aid);
            }
            mApduAidGroupList.add(mCeAidGroup);
        }
    return mApduAidGroupList;
    }
    private NxpOffHostService convertToNxpOffhostService(OffHostService service) {
         ArrayList<android.nfc.cardemulation.AidGroup> mAidGroupList = convertToCeAidGroupList(service.mAidGroupList);
         NxpOffHostService mNxpOffHostService = new NxpOffHostService(service.mUserId,service.mDescription, service.mSEName, service.mPackageName, service.mServiceName, service.mModifiable);
         //mNxpOffHostService.setBanner(service.mBanner);
         mNxpOffHostService.setContext(mContext);
         mNxpOffHostService.setBannerId(service.mBannerResId);
         mNxpOffHostService.mAidGroupList.addAll(mAidGroupList);
         return mNxpOffHostService;
    }
}
