/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package android.bluetooth.obex;

import android.server.BluetoothOppService;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

/**
 * Public API for controlling the Bluetooth OPP Profile Service.
 *
 * BluetoothOpp is a proxy object for controlling the Bluetooth OPP
 * Service via IPC.
 *
 * Currently the BluetoothOpp service runs in the system server and this
 * proxy object will be immediately bound to the service on construction.
 * However this may change in future releases, and error codes such as
 * BluetoothError.ERROR_IPC_NOT_READY will be returned from this API when the
 * proxy object is not yet attached.
 *
 * @hide
 */
public class BluetoothOpp {
    private static final String TAG = "BluetoothOpp";
    private final IBluetoothOpp mService;

    /**
     * Create a BluetoothOpp proxy object for interacting with the local
     * Bluetooth OPP service.
     */
    public BluetoothOpp() {
        IBinder b = ServiceManager.getService(BluetoothOppService.BLUETOOTH_OPP_SERVICE);
        if (b == null) {
            throw new RuntimeException("Bluetooth OPP service not available!");
        }
        mService = IBluetoothOpp.Stub.asInterface(b);
    }

    /**
     * Push an object using the OPP Bluetooth profile.
     *
     * @param address Destination BT address.
     * @param txFilename Name of file to send
     *
     * @return false indicates immediate error
     */
    public boolean pushObject(String address, String txFilename) {
        try {
            return mService.pushObject(address, txFilename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Pull a business card using the OPP Bluetooth profile.
     *
     * @param address Remote BT address.
     * @param rxFilename Filename to store retrieved business card in.
     *                   Passing in null is treated as a "don't care"--
     *                   BluetoothOppService will generate a filename that
     *                   the caller will receive in various intents
     *                   (e.g., RX_COMPLETE)
     *
     * @return The filename the pulled business card will be placed in.
     *         null indicated an immediate error
     */
    public String pullBusinessCard(String address, String rxFilename) {
        if (rxFilename == null)
        {
            // TODO: generate a filename for the caller here.  Until then,
            //       this is an unsupported use case.
            return null;
        }

        boolean ret = false;
        try {
            ret = mService.pullBusinessCard(address, rxFilename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}

        if (ret) {
            return rxFilename;
        }

        return null;
    }

    /**
     * Exchange business cards using the OPP Bluetooth profile.
     *
     * @param address Remote BT address.
     * @param rxFilename Filename to store retrieved business card in.
     *                   Passing in null is treated as a "don't care"--
     *                   BluetoothOppService will generate a filename that
     *                   the caller will receive in various intents
     *                   (e.g., RX_COMPLETE)
     * @param txFilename Filename of business card to send
     *
     * @return The filename the pulled business card will be placed in.
     *         null indicated an immediate error
     */
    public String exchangeBusinessCards(String address, String rxFilename, String txFilename) {
        if (rxFilename == null)
        {
            // TODO: generate a filename for the caller here.  Until then,
            //       this is an unsupported use case.
            return null;
        }

        String retRxFilename = pullBusinessCard(address, rxFilename);

        if (retRxFilename != null) {
            if (pushObject(address, txFilename)) {
                return retRxFilename;
            }
        }

        return null;
    }

    /**
     * Cancel an ongoing OPP transfer
     *
     * @param filename Filename of OPP object being transferred
     *
     * @return false indicates immediate error
     */
    public boolean cancelTransfer(String filename) {
        try {
            return mService.cancelTransfer(filename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Determine if a given transfer is active.
     *
     * @param filename Filename of OPP object being transferred
     *
     * @return true indicates an ongoing transfer with filename.
     *         false indicates unknown/completed transfer.
     */
    public boolean isTransferActive(String filename) {
        try {
            return mService.isTransferActive(filename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Approve/reject an incoming OPP push.  This is a response to the AUTHORIZE_ACTION intent.
     *
     * @param proposedFilename Filename identifying transfer from the AUTHORIZE_ACTION
     *                         intent OBJECT_FILENAME extra
     * @param accept true to authorize incoming OPP push, false to reject incoming OPP push
     * @param newFilename Filename to place received file in.
     *                    null indicates proposedFilename should be used.
     *
     * @return false indicates immediate error
     */
    public boolean obexAuthorizeComplete(String proposedFilename, boolean accept, String newFilename) {
        try {
            return mService.obexAuthorizeComplete(proposedFilename, accept, newFilename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }
}
