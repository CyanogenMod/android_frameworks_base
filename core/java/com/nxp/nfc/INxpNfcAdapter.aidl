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
package com.nxp.nfc;


import com.nxp.nfc.INfcDta;
import com.nxp.nfc.INxpNfcAccessExtras;
import com.nxp.nfc.INxpNfcAdapterExtras;
import com.nxp.nfc.INfcVzw;
import com.nxp.intf.IeSEClientServicesAdapter;
import com.nxp.nfc.gsma.internal.INxpNfcController;

/**
 * @hide
 */
interface INxpNfcAdapter
{

    INfcDta getNfcDtaInterface();
    INxpNfcAccessExtras getNxpNfcAccessExtrasInterface(in String pkg);
    INfcVzw getNfcVzwInterface();
    INxpNfcAdapterExtras getNxpNfcAdapterExtrasInterface();
    INxpNfcController getNxpNfcControllerInterface();
    int[] getSecureElementList(String pkg);
    int getSelectedSecureElement(String pkg);
    int selectSecureElement(String pkg,int seId);
    int deselectSecureElement(String pkg);
    void storeSePreference(int seId);
    int setEmvCoPollProfile(boolean enable, int route);
    void MifareDesfireRouteSet(int routeLoc, boolean fullPower, boolean lowPower, boolean noPower);
    void DefaultRouteSet(int routeLoc, boolean fullPower, boolean lowPower, boolean noPower);
    void MifareCLTRouteSet(int routeLoc, boolean fullPower, boolean lowPower, boolean noPower);
    IeSEClientServicesAdapter getNfcEseClientServicesAdapterInterface();
    int getSeInterface(int type);
    byte[]  getFWVersion();
    Map getServicesAidCacheSize(int userId, String category);
    int[] getActiveSecureElementList(String pkg);
    int updateServiceState(int userId , in Map serviceState);
}
