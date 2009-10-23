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

package android.server;

import android.bluetooth.obex.BluetoothObexIntent;
import android.bluetooth.obex.IBluetoothOpp;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;


/**
 * @hide
 */
public class BluetoothOppService extends IBluetoothOpp.Stub {
    public static final String BLUETOOTH_OPP_SERVICE = "bluetooth_opp";
    private static final String TAG = "BluetoothOppService";
    private static final boolean DBG = true;
    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    protected BluetoothObexDatabase mTransferDb;
    private final Context mContext;

    public BluetoothOppService(Context context) {
        mContext = context;

        if (!initNative()) {
            throw new RuntimeException("Could not init BluetoothOppService");
        }

        mTransferDb = new BluetoothObexDatabase();
    }
    protected native boolean initNative();

    /**
     * Called when object is garbage-collected: trigger cleanup in JNI
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            cleanupNative();
        } finally {
            super.finalize();
        }
    }
    protected native void cleanupNative();

    /**
     * Helper logging function for debug messages
     */
    private static void log(String msg) {
        Log.e(TAG, msg);
    }

    /** Push an object using the OPP Bluetooth profile (async)
     *
     *  @param address Destination BT address.
     *  @param txFilename Name of file to send
     *
     *  @return false indicates immediate error
     *
     *  @see onSendFilesComplete()
     */
    public synchronized boolean pushObject(String address, String txFilename) {
        String[] txFilenames = { txFilename };

        boolean ret = sendFilesNative(address, txFilenames);
        if (ret) {
            BluetoothObexDatabase.SessionDbItem sessionDbItem =
                    mTransferDb.new SessionDbItem(address,null,null);
            BluetoothObexDatabase.TransferDbItem transferDbItem =
                    mTransferDb.new TransferDbItem(txFilename,null,null,sessionDbItem);
            transferDbItem.mDirection = BluetoothObexDatabase.TransferDirection.TX;
            mTransferDb.insert(sessionDbItem);
            mTransferDb.insert(transferDbItem);
        }

        return ret;
    }
    protected native boolean sendFilesNative(String address, String[] txFilenames);

    /**
     * Pull a business card using the OPP Bluetooth profile (async)
     *
     * @param address Remote BT address.
     * @param rxFilename Filename to store retrieved business card in.
     *
     * @return false indicates immediate error
     *
     * @see onPullBusinessCardComplete()
     */
    public synchronized boolean pullBusinessCard(String address, String rxFilename) {
        boolean ret = pullBusinessCardNative(address, rxFilename);
        if (ret) {
            BluetoothObexDatabase.SessionDbItem sessionDbItem =
                    mTransferDb.new SessionDbItem(address,null,null);
            BluetoothObexDatabase.TransferDbItem transferDbItem =
                    mTransferDb.new TransferDbItem(rxFilename,null,null,sessionDbItem);
            transferDbItem.mDirection = BluetoothObexDatabase.TransferDirection.RX;
            mTransferDb.insert(sessionDbItem);
            mTransferDb.insert(transferDbItem);
        }

        return ret;
    }
    protected native boolean pullBusinessCardNative(String address, String rxFilename);

    /** Cancel an ongoing OPP transfer
     *
     *  @param filename Filename of OPP object being transferred
     *
     *  @return false indicates immediate error
     */
    public synchronized boolean cancelTransfer(String filename) {
        BluetoothObexDatabase.TransferDbItem dbItem = mTransferDb.getByFilename(filename);

        if (dbItem == null) {
            if (DBG) log("cancelTransfer() could not find transfer with filename: " + filename);
        } else {
            mTransferDb.deleteByFilename(filename);

            // It's possible to not have a defined transfer name yet
            // (i.e., onObexRequest() hasn't been called).
            if (dbItem.mTransfer != null) {
                return cancelTransferNative(dbItem.mTransfer, dbItem.mIsServer);
            } else {
                return true;
            }
        }

        return false;
    }
    protected native boolean cancelTransferNative(String transfer, boolean isServer);

    /**
     * Determine if a given transfer is active.
     *
     * @param filename Filename of OPP object being transferred
     *
     * @return true indicates an ongoing transfer with filename.
     *         false indicates unknown/completed transfer.
     */
    public synchronized boolean isTransferActive(String filename) {
        return (mTransferDb.getByFilename(filename) != null);
    }

    /**
     * Approve/reject an incoming OPP push.  This is a response to onObexAuthorize().
     *
     * @param proposedFilename Filename identifying transfer from the AUTHORIZE_ACTION
     *                         intent OBJECT_FILENAME extra
     * @param accept true to authorize incoming OPP push, false to reject incoming OPP push
     * @param newFilename Filename to place received file in.
     *                    null indicates proposedFilename should be used.
     *
     * @return false indicates immediate error
     */
    public synchronized boolean obexAuthorizeComplete(String proposedFilename,
            boolean accept, String newFilename) {
        BluetoothObexDatabase.TransferDbItem dbItem =
                mTransferDb.getByFilename(proposedFilename);

        if (dbItem == null) {
            if (DBG) {
                log("obexAuthorizeComplete() could not find pending transfer with filename: "
                        + proposedFilename);
            }
            return false;
        }

        if (newFilename == null) {
            // Continue to use proposed filename, per function spec
            newFilename = proposedFilename;
        }

        if (accept) {
            // Update the database to reflect the new filename
            mTransferDb.updateFilenameByFilename(proposedFilename, newFilename);
        } else {
            // Remove the transfer from the transfer database
            mTransferDb.deleteByFilename(proposedFilename);
        }

        return obexAuthorizeCompleteNative(accept, newFilename, dbItem.mNativeData);
    }
    protected native boolean obexAuthorizeCompleteNative(boolean accept,
            String newFilename, int nativeData);

    /**
     * Request to authorize a push to this device (async).
     *
     * @param transfer OBEX transfer name
     * @param address Bluetooth address of remote device
     * @param name Name of incoming object
     * @param type MIME type of incoming object
     * @param length Length (in bytes) of incoming object
     * @param nativeData Optional native data that should be passed back
     *                   in obexAuthorizeCompleteNative()
     *
     * @return false indicates immediate error
     *
     * @see obexAuthorizeComplete()
     */
    public synchronized boolean onObexAuthorize(String transfer, String address,
            String name, String type, int length, int nativeData) {
        // Add filename/transfer pair to map (cache authorization)
        BluetoothObexDatabase.TransferDbItem dbItem = // TODO: make name into a filename?
                mTransferDb.new TransferDbItem(name,name,transfer,null);
        dbItem.mObjectSize = length;
        dbItem.mDirection = BluetoothObexDatabase.TransferDirection.RX;
        dbItem.mIsServer = true;
        dbItem.mNativeData = nativeData;
        mTransferDb.insert(dbItem);

        Intent intent = new Intent(BluetoothObexIntent.AUTHORIZE_ACTION);
        intent.putExtra(BluetoothObexIntent.ADDRESS, address);
        intent.putExtra(BluetoothObexIntent.OBJECT_FILENAME, name);
        intent.putExtra(BluetoothObexIntent.OBJECT_TYPE, type);
        intent.putExtra(BluetoothObexIntent.OBJECT_SIZE, length);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);

        return true;
    }

    /**
     * Request to cancel a pending authorization to this device
     *
     * @param transfer OBEX transfer name
     *
     * @return false indicates immediate error
     *
     * @see onObexAuthorize()
     */
    public synchronized boolean onObexAuthorizeCancel(String transfer) {
        BluetoothObexDatabase.TransferDbItem dbItem = mTransferDb.getByTransfer(transfer);

        if (dbItem == null) {
            if (DBG) log("onObexAuthorizeCancel() could not find pending transfer: " + transfer);
            return false;
        }

        // Remove the transfer from the transfer database
        mTransferDb.deleteByFilename(dbItem.mFilename);

        // Send an authorize cancel (failure) intent towards the application
        Intent intent = new Intent(BluetoothObexIntent.AUTHORIZE_CANCEL_ACTION);
        intent.putExtra(BluetoothObexIntent.OBJECT_FILENAME, dbItem.mFilename);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);

        return true;
    }

    /**
     * Request, in response to a push, for a filename to show the remote party.
     *
     * @param transfer OBEX transfer name
     *
     * @return String giving filename to show the remote party.  A null string
     *         indicates that the transfer was rejected/canceled at higher levels.
     */
    public synchronized String onObexRequest(String transfer) {
        // Use transfer to get properties
        TransferProperties tp = obexTransferGetPropertiesNative(transfer);
        if (tp == null) {
            if (DBG) log("onObexRequest() could not retrieve properties for transfer: "
                    + transfer);
            return null;
        } else {
            BluetoothObexDatabase.TransferDbItem dbItem =
                    mTransferDb.getByFilename(tp.mFilename);

            if (dbItem == null) {
                if (DBG) log("onObexRequest() could not find a transfer using filename: " + tp.mFilename);
                return null;
            } else {
                // Update the database to add the transfer reference...
                mTransferDb.updateTransferByFilename(tp.mFilename, transfer);

                // ...and the object size (refetch to get current record)
                dbItem = mTransferDb.getByFilename(tp.mFilename);
                if (dbItem == null) {
                    if (DBG) log("onObexRequest() could not match filename: " + tp.mFilename);
                    return null;
                } else {
                    dbItem.mObjectSize = tp.mSize;
                }

                File file = new File(tp.mFilename);
                return file.getName();
            }
        }
    }

    public class TransferProperties {
        public String mName; // Transferred object name
        public int mSize; // Tranferred object size (bytes)
        public String mFilename; // Fully-specified object filename

        public TransferProperties(String name, int size, String filename) {
            mName = name;
            mSize = size;
            mFilename = filename;
        }
    }
    protected native TransferProperties obexTransferGetPropertiesNative(String transfer);

    /**
     * Report of OBEX transfer progress
     *
     * @param transfer OBEX transfer name
     * @param bytes Number of bytes transferred
     */
    public synchronized void onObexProgress(String transfer, int bytes) {
        BluetoothObexDatabase.TransferDbItem dbItem = mTransferDb.getByTransfer(transfer);

        if (dbItem == null) {
            if (DBG) log("onObexProgress() could not match transfer: " + transfer);
        } else {
            Intent intent = new Intent(BluetoothObexIntent.PROGRESS_ACTION);
            intent.putExtra(BluetoothObexIntent.OBJECT_FILENAME, dbItem.mFilename);
            intent.putExtra(BluetoothObexIntent.OBJECT_SIZE, dbItem.mObjectSize);
            intent.putExtra(BluetoothObexIntent.BYTES_TRANSFERRED, bytes);

            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    /**
     * Report of sendFilesNative() completion (async)
     *
     * @param address BT addr of destination device
     * @param isError true if error condition, else success
     */
    public synchronized void onSendFilesComplete(String address, boolean isError) {
        BluetoothObexDatabase.TransferDbItem dbItem = mTransferDb.getTransferByAddress(address);
        if (dbItem == null) {
            if (DBG) log("onSendFilesComplete() could not find a pending transfer with address: "
                    + address);
        } else {
            // Remove transfer from pending operation list.
            mTransferDb.deleteByAddress(address);

            // Notify application layer of connection status
            Intent intent = new Intent(BluetoothObexIntent.CONNECT_STATUS_ACTION);
            intent.putExtra(BluetoothObexIntent.SUCCESS, isError ? false : true);

            if (isError == true) {
                // Remove transfer from transfer database
                mTransferDb.deleteByFilename(dbItem.mFilename);
            }

            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    /**
     * Report of pullBusinessCardNative() completion (async)
     *
     * @param address BT addr of destination device
     * @param isError true if error condition, else success
     */
    public synchronized void onPullBusinessCardComplete(String address, boolean isError) {
        BluetoothObexDatabase.TransferDbItem dbItem = mTransferDb.getTransferByAddress(address);
        if (dbItem == null) {
            if (DBG) {
                log("onPullBusinessCardComplete() could not find a pending transfer with address: "
                        + address);
            }
        } else {

            // Remove transfer from transfer database
            mTransferDb.deleteByFilename(dbItem.mFilename);

            // Notify application layer of success/failure
            Intent intent = new Intent(BluetoothObexIntent.RX_COMPLETE_ACTION);
            intent.putExtra(BluetoothObexIntent.OBJECT_FILENAME, dbItem.mFilename);
            intent.putExtra(BluetoothObexIntent.PROFILE, BluetoothObexIntent.PROFILE_OPP);
            intent.putExtra(BluetoothObexIntent.SUCCESS, !isError);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    /**
     * Report of object transfer completion (client, or server)
     *
     * @param transfer OBEX transfer name
     * @param success true if transfer successful, else false
     * @param error if success is false, should hold a user-readable error message.
     */
    public synchronized void onObexTransferComplete(String transfer,
            boolean success, String error) {
        BluetoothObexDatabase.TransferDbItem dbItem = mTransferDb.getByTransfer(transfer);

        if (dbItem == null) {
            if (DBG) log("onObexTransferComplete() could not match transfer: " + transfer);
        } else {
            Intent intent = null;
            if (dbItem.mDirection == BluetoothObexDatabase.TransferDirection.TX) {
                intent = new Intent(BluetoothObexIntent.TX_COMPLETE_ACTION);
                intent.putExtra(BluetoothObexIntent.ERROR_MESSAGE, error);
            } else { // (dbItem.mDirection == TransferDirection.RX)
                intent = new Intent(BluetoothObexIntent.RX_COMPLETE_ACTION);
            }

            if (intent != null) {
                intent.putExtra(BluetoothObexIntent.OBJECT_FILENAME, dbItem.mFilename);
                intent.putExtra(BluetoothObexIntent.PROFILE, BluetoothObexIntent.PROFILE_OPP);
                intent.putExtra(BluetoothObexIntent.SUCCESS, success);
                mContext.sendBroadcast(intent, BLUETOOTH_PERM);
            }

            // Remove transfer database entry
            mTransferDb.deleteByFilename(dbItem.mFilename);
        }
    }
}
