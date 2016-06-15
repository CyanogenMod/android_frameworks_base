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

import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;

/**
 * This class provides the primary API for DTA operations.
*/
public final class NfcDta {
    private static final String TAG = "NfcDta";
    private static INfcDta mService;
    public NfcDta(INfcDta mDtaService)
    {
        mService = mDtaService;
    }

    /**
     * Enable SNEP Testing.
     ** This call allows SNEP to be tested against nfc forum test cases.
     * @param cmdType  This parameter is used to give the following commands.
     *  enabledta :- this will enable  dta mode flag in the nfcservice for testing.
     *  enableserver:- this will start the extended dta server having service name "urn:nfc:sn:sneptest".
     *  disableserver :- this will disable the extended dta snep server.
     *  enableclient :- this will create a snep client with service name "urn:nfc:sn:snep" or "urn:nfc:sn:sneptest" based on the testcaseid.
     *  disableclient :- this will destroy the client.
     * @param serviceName this parameter is used by server as well client . Feasible values are "urn:nfc:sn:snep" and "urn:nfc:sn:sneptest".
     * @param serviceSap  this is used to specify the Service Access Point you wish to bind the service
     *   name of your server. Also it is used by client,  to connect to the remote server.
     *   miu :- this is used to specify the maximum information unit. For testing this value is 128 bytes. Used by server and client both.
     *   rwSize :- this is receive window size. It is used by server and client both to tell max size of window. For testing we are using 1.
     *  testCaseId :- this is used to specify the client test cases you want to run. Value 1 to 9.
     * @return the status of the current operation.
     * @throws IOException If a failure occurred during snepDtaCmd
     */
    public boolean snepDtaCmd(String cmdType, String serviceName, int serviceSap, int miu, int rwSize, int testCaseId)
    {
        try {
            return mService.snepDtaCmd(cmdType, serviceName, serviceSap, miu, rwSize, testCaseId);
        } catch (RemoteException e) {
            return false;
        }
    }
}