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

import android.bluetooth.obex.IBluetoothFtpCallback;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.server.BluetoothFtpService;
import android.util.Log;

/**
 * Public API for controlling the Bluetooth FTP Profile Service.
 *
 * BluetoothFtp is a proxy object for controlling the Bluetooth FTP
 * Service via IPC.
 *
 * Currently the BluetoothFtp service runs in the system server and this
 * proxy object will be immediately bound to the service on construction.
 * However this may change in future releases, and error codes such as
 * BluetoothError.ERROR_IPC_NOT_READY will be returned from this API when the
 * proxy object is not yet attached.
 *
 * @hide
 */
public class BluetoothFtp {
    private static final String TAG = "BluetoothFtp";

    private IBluetoothFtp mService;
    private String mDestBtAddr = null;

    /**
     * Create a BluetoothFtp proxy object for interacting with the local
     * Bluetooth FTP service.  Currently, only one FTP connection is allowed at a time,
     * so close() should be called when FTP session is complete.
     */
    public BluetoothFtp() {
        IBinder b = ServiceManager.getService(BluetoothFtpService.BLUETOOTH_FTP_SERVICE);
        if (b == null) {
            throw new RuntimeException("Bluetooth FTP service not available!");
        }
        mService = IBluetoothFtp.Stub.asInterface(b);
    }

    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    /**
     * Connect to a FTP server.  This is required before most other operations.
     * This is an asynchronous call.
     *
     * @param address Bluetooth address of remote device to connect to
     * @param callback Class instance implementing IBluetoothFtpCallback.
     *                 This callback is used for all asynchronous calls.
     *
     * @return false indicates immediate error
     */
    public boolean connect(String address, IBluetoothFtpCallback callback) {
        mDestBtAddr = address;

        // Create FTP session (we currently restrict to one simultaneous FTP session)
        try {
            return mService.createSession(address, callback);
        } catch (RemoteException e) {Log.e(TAG, "", e);}

        return false;
    }

    /**
     * Determine if a given connection is active.
     *
     * @param address Address of connection to query
     *
     * @return true indicates an existing connection with address
     *         false indicates unknown/closed connection.
     */
    public boolean isConnectionActive(String address) {
        try {
            return mService.isConnectionActive(address);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Close the connection to the backing service.
     * Other public functions of BluetoothFtp will return default error
     * results once close() has been called. Multiple invocations of close()
     * are ok.
     */
    public synchronized void close() {
        if (mDestBtAddr != null) {
            try {
                mService.closeSession(mDestBtAddr);
            } catch (RemoteException e) {Log.e(TAG, "", e);}

            mDestBtAddr = null;
        }
    }

    /**
     * Change the current folder on the remote device
     * This is an asynchronous call.
     *
     * @param folder Name of folder to change to.  The name should not contain
     *               any delimiters.  The empty name ("") will change to the
     *               home/root directory of the remote device.  The special
     *               name ".." will change to the parent directory.
     *
     * @return false indicates immediate error
     */
    public boolean changeFolder(String folder) {
        try {
            return mService.changeFolder(mDestBtAddr, folder);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Create a folder on the remote device
     * This is an asynchronous call.
     *
     * @param folder Name of folder to create
     *
     * @return false indicates immediate error
     */
    public boolean createFolder(String folder) {
        try {
            return mService.createFolder(mDestBtAddr, folder);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Delete the specified file/folder from the remote device
     * This is an asynchronous call.
     *
     * @param name Name of file/folder to delete
     *
     * @return false indicates immediate error
     */
    public boolean delete(String name) {
        try {
            return mService.delete(mDestBtAddr, name);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Request a list of the current folder contents on the remote device.
     * This is an asynchronous call.
     *
     * @return false indicates immediate error
     */
    public boolean listFolder() {
        try {
            return mService.listFolder(mDestBtAddr);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get a file from the remote device
     * This is an asynchronous call.
     *
     * @param localFilename Filename to place fetched file on local device
     * @param remoteFilename Filename to fetch from remote device
     *
     * @return false indicates immediate error
     */
    public boolean getFile(String localFilename, String remoteFilename) {
        try {
            return mService.getFile(mDestBtAddr, localFilename, remoteFilename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Copy a file to the remote device
     * This is an asynchronous call.
     *
     * @param localFilename Filename, on local device, to copy to remote device
     * @param remoteFilename Filename to place file in on remote device
     *
     * @return false indicates immediate error
     */
    public boolean putFile(String localFilename, String remoteFilename) {
        try {
            return mService.putFile(mDestBtAddr, localFilename, remoteFilename);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Cancel an ongoing FTP transfer
     *
     * @param name Name of FTP object being transferred
     *
     * @return false indicates immediate error
     */
    public boolean cancelTransfer(String name) {
        try {
            return mService.cancelTransfer(mDestBtAddr, name);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Determine if a given transfer is active.
     *
     * @param filename Filename of FTP object being transferred
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

    // TODO: update with any new functionality from BM3 obexd
}
