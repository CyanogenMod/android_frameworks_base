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

import android.app.Activity;
import android.app.ActivityThread;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import android.nfc.INfcAdapter;
import android.nfc.NfcAdapter;
import android.nfc.INfcAdapterExtras;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;

import java.io.IOException;
import android.os.UserHandle;
import android.os.RemoteException;
import com.nxp.nfc.gsma.internal.INxpNfcController;

import android.util.Log;

public final class NxpNfcAdapter {
    private static final String TAG = "NXPNFC";

    // Guarded by NfcAdapter.class
    static boolean sIsInitialized = false;

    /**
     * The NfcAdapter object for each application context.
     * There is a 1-1 relationship between application context and
     * NfcAdapter object.
     */
    static HashMap<NfcAdapter, NxpNfcAdapter> sNfcAdapters = new HashMap(); //guard by NfcAdapter.class

    // Final after first constructor, except for
    // attemptDeadServiceRecovery() when NFC crashes - we accept a best effort
    // recovery
    private static INfcAdapter sService;
    private static INxpNfcAdapter sNxpService;

    private NxpNfcAdapter() {
    }
    /**
     * Returns the NxpNfcAdapter for application context,
     * or throws if NFC is not available.
     * @hide
     */
    public static synchronized NxpNfcAdapter getNxpNfcAdapter(NfcAdapter adapter) {
        if (!sIsInitialized) {
            if (adapter == null) {
                Log.v(TAG, "could not find NFC support");
                throw new UnsupportedOperationException();
            }
            sService = getServiceInterface();
            if (sService == null) {
                Log.e(TAG, "could not retrieve NFC service");
                throw new UnsupportedOperationException();
            }
            sNxpService = getNxpNfcAdapterInterface();
             if (sNxpService == null) {
                Log.e(TAG, "could not retrieve NXP NFC service");
                throw new UnsupportedOperationException();
            }
            sIsInitialized = true;
        }
        NxpNfcAdapter nxpAdapter = sNfcAdapters.get(adapter);
        if (nxpAdapter == null) {
            nxpAdapter = new NxpNfcAdapter();
            sNfcAdapters.put(adapter, nxpAdapter);
        }

        return nxpAdapter;

    }

    /** get handle to NFC service interface */
    private static INfcAdapter getServiceInterface() {
        /* get a handle to NFC service */
        IBinder b = ServiceManager.getService("nfc");
        if (b == null) {
            return null;
        }
        return INfcAdapter.Stub.asInterface(b);
    }
    /**
     * @hide
     */
    private static INxpNfcAdapter getNxpNfcAdapterInterface() {
        if (sService == null) {
            throw new UnsupportedOperationException("You need a reference from NfcAdapter to use the "
                    + " NXP NFC APIs");
        }
        try {
            return (INxpNfcAdapter) sService.getNfcAdapterVendorInterface("");
        } catch (RemoteException e) {
            return null;
        }
    }

    /**
    * Get the handle to an INxpNfcController Interface
    * @hide
    */
    public INxpNfcController getNxpNfcControllerInterface() {
        if(sService == null) {
            throw new UnsupportedOperationException("You need a reference from NfcAdapter to use the "
                    + " NXP NFC APIs");
        }
        try {
            return sNxpService.getNxpNfcControllerInterface();
        }catch(RemoteException e) {
            return null;
        }
    }

    /**
     * NFC service dead - attempt best effort recovery
     * @hide
     */
    private void attemptDeadServiceRecovery() {
        Log.e(TAG, "NFC service dead - attempting to recover");
        INfcAdapter service = getServiceInterface();
        if (service == null) {
            Log.e(TAG, "could not retrieve NFC service during service recovery");
            // nothing more can be done now, sService is still stale, we'll hit
            // this recovery path again later
            return;
        }
        // assigning to sService is not thread-safe, but this is best-effort code
        // and on a well-behaved system should never happen
        sService = service;
        sNxpService = getNxpNfcAdapterInterface();
        return;
    }

    /**
     * Get the Available Secure Element List
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @throws IOException If a failure occurred during the getAvailableSecureElementList()
     */

    public String [] getAvailableSecureElementList(String pkg) throws IOException {
        int [] seList;
        String [] arr;
        try{
            Log.d(TAG, "getAvailableSecureElementList-Enter");
            seList = sNxpService.getSecureElementList(pkg);

        if (seList!=null && seList.length != 0)
        {
            arr= new String[seList.length];
            Log.v(TAG,"getAvailableSecureElementList-"+ seList);
            for(int i=0;i<seList.length;i++)
            {
                Log.e(TAG, "getAvailableSecure seList[i]" + seList[i]);
                if(seList[i]==NxpConstants.SMART_MX_ID_TYPE)
                {
                    arr[i]= NxpConstants.SMART_MX_ID;
                }
                else if(seList[i]==NxpConstants.UICC_ID_TYPE)
                {
                    arr[i]= NxpConstants.UICC_ID;
                }
                else if (seList[i] == NxpConstants.ALL_SE_ID_TYPE) {
                    arr[i]= NxpConstants.ALL_SE_ID;
                }
                else {
                    throw new IOException("No Secure Element selected");
                }
            }
        } else {
            arr = new String[0];
        }
        return arr;
        }
        catch (RemoteException e) {
            Log.e(TAG, "getAvailableSecureElementList: failed", e);
            throw new IOException("Failure in deselecting the selected Secure Element");
        }
    }

    /**
     * Get the Active Secure Element List
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @throws IOException If a failure occurred during the getActiveSecureElementList()
     */
    public String[] getActiveSecureElementList(String pkg) throws IOException {
        int [] activeSEList;
        String [] arr;
        try{
            Log.d(TAG, "getActiveSecureElementList-Enter");
            activeSEList = sNxpService.getActiveSecureElementList(pkg);
            if (activeSEList!=null && activeSEList.length != 0)
            {
                arr= new String[activeSEList.length];
                for(int i=0;i<activeSEList.length;i++)
                {
                    Log.e(TAG, "getActiveSecureElementList activeSE[i]" + activeSEList[i]);
                    if(activeSEList[i]==NxpConstants.SMART_MX_ID_TYPE)
                    {
                        arr[i]= NxpConstants.SMART_MX_ID;
                    }
                    else if(activeSEList[i]==NxpConstants.UICC_ID_TYPE)
                    {
                        arr[i]= NxpConstants.UICC_ID;
                    }
                    else {
                        throw new IOException("No Secure Element Activeted");
                    }
                }
            } else {
                arr = new String[0];
            }
            return arr;
        } catch (RemoteException e) {
            Log.e(TAG, "getActiveSecureElementList: failed", e);
            throw new IOException("Failure in deselecting the selected Secure Element");
        }
    }

    /**
     * Select the default Secure Element to be used in Card Emulation mode
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @param seId Secure Element ID to be used : {@link NxpConstants#SMART_MX_ID} or {@link NxpConstants#UICC_ID}
     * @throws IOException If a failure occurred during the Secure Element selection
     */
    public void selectDefaultSecureElement(String pkg, String seId) throws IOException {
        int [] seList;
        int seID = 0;
        boolean seSelected = false;

        if (seId.equals(NxpConstants.UICC_ID)) {
            seID = NxpConstants.UICC_ID_TYPE;
        } else if (seId.equals(NxpConstants.SMART_MX_ID)) {
            seID= NxpConstants.SMART_MX_ID_TYPE;
        } else if (seId.equals(NxpConstants.ALL_SE_ID)) {
            seID = NxpConstants.ALL_SE_ID_TYPE;
        } else {
            Log.e(TAG, "selectDefaultSecureElement: wrong Secure Element ID");
            throw new IOException("selectDefaultSecureElement failed: Wronf Secure Element ID");
        }

        /* Deselect already selected SE if ALL_SE_ID is not selected*/

        try {
            if(sNxpService.getSelectedSecureElement(pkg) != seID) {
                sNxpService.deselectSecureElement(pkg);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "selectDefaultSecureElement: getSelectedSecureElement failed", e);
            throw new IOException("Failure in deselecting the selected Secure Element");
        }

        /* Get the list of the detected Secure Element */

        try {
            seList = sNxpService.getSecureElementList(pkg);
            // ADD
            if (seList != null && seList.length != 0) {

                if (seId.compareTo(NxpConstants.ALL_SE_ID) != 0) {
                    for (int i = 0; i < seList.length; i++) {
                        if (seList[i] == seID) {
                            /* Select the Secure Element */
                            sNxpService.selectSecureElement(pkg,seID);
                            seSelected = true;
                        }
                    }
                } else {
                    /* Select all Secure Element */
                    sNxpService.selectSecureElement(pkg,seID);
                    seSelected = true;
                }
            }

            // FIXME: This should be done in case of SE selection.
            if (!seSelected) {
                if (seId.equals(NxpConstants.UICC_ID)) {
                    sNxpService.storeSePreference(seID);
                    throw new IOException("UICC not detected");
                } else if (seId.equals(NxpConstants.SMART_MX_ID)) {
                    sNxpService.storeSePreference(seID);
                    throw new IOException("SMART_MX not detected");
                } else if (seId.equals(NxpConstants.ALL_SE_ID)) {
                    sNxpService.storeSePreference(seID);
                    throw new IOException("ALL_SE not detected");
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "selectUiccCardEmulation: getSecureElementList failed", e);
        }
    }

    /**
     * Set listen mode routing table configuration for Mifare Desfire Route.
     * routeLoc is parameter which fetch the text from UI and compare
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @throws IOException If a failure occurred during Mifare Desfire Route set.
     */
    public void MifareDesfireRouteSet(String routeLoc, boolean fullPower, boolean lowPower, boolean noPower)
            throws IOException {
        try{
            int seID=0;
            boolean result = false;
            if (routeLoc.equals(NxpConstants.UICC_ID)) {
            seID = NxpConstants.UICC_ID_TYPE;
            } else if (routeLoc.equals(NxpConstants.SMART_MX_ID)) {
            seID= NxpConstants.SMART_MX_ID_TYPE;
            } else if (routeLoc.equals(NxpConstants.HOST_ID)) {
                seID = NxpConstants.HOST_ID_TYPE;
            } else {
                Log.e(TAG, "confMifareDesfireProtoRoute: wrong default route ID");
                throw new IOException("confMifareProtoRoute failed: Wrong default route ID");
            }
            Log.i(TAG, "calling Services");
            sNxpService.MifareDesfireRouteSet(seID, fullPower, lowPower, noPower);
            } catch (RemoteException e) {
            Log.e(TAG, "confMifareDesfireProtoRoute failed", e);
            throw new IOException("confMifareDesfireProtoRoute failed");
            }
    }

    /**
     * Set listen mode routing table configuration for Default Route.
     * routeLoc is parameter which fetch the text from UI and compare
     * * <p>Requires {@link android.Manifest.permission#NFC} permission.
     * @throws IOException If a failure occurred during Default Route Route set.
     */
    public void DefaultRouteSet(String routeLoc, boolean fullPower, boolean lowPower, boolean noPower)
            throws IOException {
        try {
            int seID=0;
            boolean result = false;
            if (routeLoc.equals(NxpConstants.UICC_ID)) {
            seID = NxpConstants.UICC_ID_TYPE;
            } else if (routeLoc.equals(NxpConstants.SMART_MX_ID)) {
            seID= NxpConstants.SMART_MX_ID_TYPE;
            } else if (routeLoc.equals(NxpConstants.HOST_ID)) {
              seID = NxpConstants.HOST_ID_TYPE;
            } else {
                Log.e(TAG, "DefaultRouteSet: wrong default route ID");
                throw new IOException("DefaultRouteSet failed: Wrong default route ID");
            }
               sNxpService.DefaultRouteSet(seID, fullPower, lowPower, noPower);
            } catch (RemoteException e) {
            Log.e(TAG, "confsetDefaultRoute failed", e);
            throw new IOException("confsetDefaultRoute failed");
        }
    }

    /**
     * Set listen mode routing table configuration for MifareCLTRouteSet.
     * routeLoc is parameter which fetch the text from UI and compare
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @throws IOException If a failure occurred during Mifare CLT Route set.
     */
    public void MifareCLTRouteSet(String routeLoc, boolean fullPower, boolean lowPower, boolean noPower )
            throws IOException {
        try {
            int seID=0;
            boolean result = false;
            if (routeLoc.equals(NxpConstants.UICC_ID)) {
            seID = NxpConstants.UICC_ID_TYPE;
            } else if (routeLoc.equals(NxpConstants.SMART_MX_ID)) {
            seID= NxpConstants.SMART_MX_ID_TYPE;
            } else if (routeLoc.equals(NxpConstants.HOST_ID)) {
            seID = NxpConstants.HOST_ID_TYPE;
            } else {
                Log.e(TAG, "confMifareCLT: wrong default route ID");
                throw new IOException("confMifareCLT failed: Wrong default route ID");
            }
            sNxpService.MifareCLTRouteSet(seID, fullPower, lowPower, noPower);
        } catch (RemoteException e) {
            Log.e(TAG, "confMifareCLT failed", e);
            throw new IOException("confMifareCLT failed");
        }
    }

    /**
     * Helper to create an Nfc Ala object.
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     * @return the NfcAla, or null if no NfcAla exists
    public NfcAla createNfcAla() {
         try {
             return new NfcAla(sNxpService.getNfcAlaInterface());
         } catch (RemoteException e) {
             Log.e(TAG, "createNfcAla failed", e);
             return null;
         }
    }*/

    /**
     * Helper to create an Nfc Dta object.
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @return the NfcDta, or null if no NfcDta exists
     */
    public NfcDta createNfcDta() {
         try {
             return new NfcDta(sNxpService.getNfcDtaInterface());
         } catch (RemoteException e) {
             Log.e(TAG, "createNfcDta failed", e);
             return null;
         }
    }

    /**
    * Get the handle to an INxpNfcAccessExtras Interface
    * @hide
    */
    public INxpNfcAccessExtras getNxpNfcAccessExtras(String pkg) {
        try {
            return sNxpService.getNxpNfcAccessExtrasInterface(pkg);
        } catch (RemoteException e) {
            Log.e(TAG, "getNxpNfcAccessExtras failed", e);
            return null;
        }
    }

    /**
     * Active the Single Wired Protocol (SWP).
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @deprecated Use {@link NxpNfcAdapter#selectDefaultSecureElement}
     * @throws IOException If a failure occurred during the Secure Element selection
     */
    @Deprecated
    public void activeSwp() throws IOException {
        throw new UnsupportedOperationException();
    }

   /**
     * Get the ID of the Secure Element selected
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @return Secure Element ID currently selected
     * @throws IOException If a failure occurred during the getDefaultSelectedSecureElement()
     */
    public String getDefaultSelectedSecureElement(String pkg) throws IOException {
        int seID = 0;

        /* Get Selected Secure Element */
        try {
            seID = sNxpService.getSelectedSecureElement(pkg);
            if (seID == NxpConstants.UICC_ID_TYPE/*0xABCDF0*/) {
                return NxpConstants.UICC_ID;
            } else if (seID == NxpConstants.SMART_MX_ID_TYPE/*0xABCDEF*/) {
                return NxpConstants.SMART_MX_ID;
            } else if (seID == NxpConstants.ALL_SE_ID_TYPE/*0xABCDFE*/) {
                return NxpConstants.ALL_SE_ID;
            } else {
                throw new IOException("No Secure Element selected");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getSelectedSecureElement failed", e);
            throw new IOException("getSelectedSecureElement failed");
        }
    }

    /**
     * deselect the selected Secure Element
     * <p>Requires {@link android.Manifest.permission#NFC} permission.
     *
     * @throws IOException If a failure occurred during the deselction of secure element.
     */
    public void deSelectedSecureElement(String pkg) throws IOException {
        /* deselected Secure Element */
        try {
            sNxpService.deselectSecureElement(pkg);
        } catch (RemoteException e) {
            Log.e(TAG, "deselectSecureElement failed", e);
            throw new IOException("deselectSecureElement failed");
        }
    }

    /**
     * Get the current NFCC firware version.
     * @return 2 byte array with Major ver(0 index) adn Minor ver(1 index)
     */
    public byte[] getFwVersion() throws IOException
    {
        try{
            return sNxpService.getFWVersion();
        }
        catch(RemoteException e)
        {
            Log.e(TAG, "RemoteException in getFwVersion(): ", e);
            throw new IOException("RemoteException in getFwVersion()");
        }
    }

    /**
     * This api returns the CATEGORY_OTHER (non Payment)Services registered by the user
     * along with the size of the registered aid group.
     * This api has to be called when aid routing full intent is broadcast by the system.
     * <p>This gives the list of both static and dynamic card emulation services
     * registered by the user.
     * <p> This api can be called to get the list of offhost and onhost cardemulation
     * services registered by the user.
     * <ul>
     * <li>
     * If the category is CATEGORY_PAYMENT than null value is returned.
     * <li>
     * If there are no non payment services null value is returned.
     * </ul>
     * @param UserID  The user id of current user
     * @param category The category i.e. CATEGORY_PAYMENT , CATEGORY_OTHER
     * @return The hashMap of Component Name of Non Payment Services and size of the
     *         registered aid group
     */
    public Map<String, Integer> getServicesAidCacheSize (int UserID , String category) throws IOException{
        try {
            return sNxpService.getServicesAidCacheSize(UserID ,category);
        }catch(RemoteException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * This api is called by applications to update the service state of card emualation
     * services.
     * <p>This api is implemented for  {@link android.nfc.cardemulation.CardEmulation#CATEGORY_OTHER}.
     * <p>Requires {@link android.Manifest.permission#NFC} permission.<ul>
     * <li>This api should be called only when the intent AID routing
     *     table full is sent by NfcService.
     * <li>The service state change is persistent for particular UserId.
     * <li>The service state is written to the Xml and read
     *     before every routing table  change.
     * <li>If there is any change in routing table  the routing table is updated to NFCC
     *     after calling this api.
     * </ul>
     * @param  serviceState Map of ServiceName and state of service.
     * @return whether  the update of Card Emulation services is
     *          success or not.
     *          0xFF - failure
     *          0x00 - success
     * @throws  IOException if any exception occurs during the service state change.
     */
    public int updateServiceState(Map<String , Boolean> serviceState) throws IOException{
        try {
            return sNxpService.updateServiceState(UserHandle.myUserId() , serviceState);
        }catch(RemoteException e)
        {
            e.printStackTrace();
            return 0xFF;
        }
    }
     /**
     * @hide
     */
    public INxpNfcAdapterExtras getNxpNfcAdapterExtrasInterface(INfcAdapterExtras extras) {
        if (sNxpService == null || extras == null) {
            throw new UnsupportedOperationException("You need a context on NxpNfcAdapter to use the "
                    + " NXP NFC extras APIs");
        }
        try {
            return sNxpService.getNxpNfcAdapterExtrasInterface();
        } catch (RemoteException e) {
        Log.e(TAG, "getNxpNfcAdapterExtrasInterface failed", e);
            return null;
        }
    }
}
