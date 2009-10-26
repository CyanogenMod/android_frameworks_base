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
import android.bluetooth.obex.IBluetoothFtp;
import android.bluetooth.obex.IBluetoothFtpCallback;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @hide
 */
public class BluetoothFtpService extends IBluetoothFtp.Stub {
    public static final String BLUETOOTH_FTP_SERVICE = "bluetooth_ftp";
    private static final String TAG = "BluetoothFtpService";
    private static final boolean DBG = false;
    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    protected BluetoothObexDatabase mSessionDb;
    private final Context mContext;

    public BluetoothFtpService(Context context) {
        mContext = context;

        if (!initNative()) {
            throw new RuntimeException("Could not init BluetoothFtpService");
        }

        mSessionDb = new BluetoothObexDatabase();
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
        Log.d(TAG, msg);
    }

    /**
     * Getter functions for unit testing purposes
     * @return
     */
    public final BluetoothObexDatabase getSessionDb() {
        return mSessionDb;
    }

    /**
     * Create an OBEX session for FTP (async call)
     *
     * @param address Destination BT address.
     * @param callback Class instance implementing IBluetoothFtpCallback
     *
     * @return false indicates immediate error
     */
    public synchronized boolean createSession(String address, IBluetoothFtpCallback callback) {
        /*
         * Need to register callback before calling native code
         * (which could potentially call callback)
         */
        BluetoothObexDatabase.SessionDbItem dbItem =
                mSessionDb.new SessionDbItem(address,null,callback);
        mSessionDb.insert(dbItem);

        boolean ret = createSessionNative(address);

        if (!ret) {
            mSessionDb.deleteByAddress(address);
        }

        return ret;
    }
    protected native boolean createSessionNative(String address);

    /**
     * Close an OBEX session.
     *
     * @param address Destination BT address
     *
     * @return false indicates immediate error
     */
    public synchronized boolean closeSession(String address) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("closeSession() could not find session with address: " + address);
            return false;
        }

        boolean ret = closeSessionNative(dbItem.mSession);

        if (ret) {
            mSessionDb.deleteByAddress(address);
            // TODO: any orphan transfer cleanup
        } else {
            if (DBG) {
                log("closeSessionNative() failed for session: " + dbItem.mSession
                        + ".  Retaining.");
            }
        }

        return ret;
    }
    protected native boolean closeSessionNative(String session);

    /**
     * Determine if a given connection is active.
     *
     * @param address Address of connection to query
     *
     * @return true indicates an existing connection with address
     *         false indicates unknown/closed connection.
     */
    public synchronized boolean isConnectionActive(String address) {
        return (mSessionDb.getByAddress(address) != null);
    }

    /**
     * Change the current folder of the remote device (async call)
     *
     * @param address Destination BT address
     * @param folder Name of folder to change to
     *
     * @return false indicates immediate error
     */
    public synchronized boolean changeFolder(String address, String folder) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("changeFolder() could not find session with address: " + address);
            return false;
        }

        return changeFolderNative(dbItem.mSession,folder);
    }
    protected native boolean changeFolderNative(String session, String folder);

    /**
     * Create a new folder on the remote device (async call)
     *
     * @param address Destination BT address
     * @param folder Name of folder to create
     *
     * @return false indicates immediate error
     */
    public synchronized boolean createFolder(String address, String folder) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("createFolder() could not find session with address: " + address);
            return false;
        }

        return createFolderNative(dbItem.mSession,folder);
    }
    protected native boolean createFolderNative(String session, String folder);

    /**
     * Delete the specified file/folder on the remote device (aync call)
     *
     * @param address Destination BT address
     * @param name Name of file/folder to delete
     *
     * @return false indicates immediate error
     */
    public synchronized boolean delete(String address, String name) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("delete() could not find session with address: " + address);
            return false;
        }

        return deleteNative(dbItem.mSession,name);
    }
    protected native boolean deleteNative(String session, String name);

    /**
     * List the contents of the current folder on the remote device (async call)
     *
     * @param address Destination BT address
     *
     * @return false indicates immediate error
     */
    public synchronized boolean listFolder(String address) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("listFolder() could not find session with address: " + address);
            return false;
        }

        return listFolderNative(dbItem.mSession);
    }
    protected native boolean listFolderNative(String session);

    /**
     * Copy a file from the remote device to this device (async call)
     *
     * @param address Destination BT address
     * @param localFilename Filename to save retrieved file in
     * @param remoteFilename Name of file on remote device to get
     *
     * @return false indicates immediate error
     */
    public synchronized boolean getFile(String address, String localFilename,
            String remoteFilename) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("getFile() could not find session with address: " + address);
            return false;
        }

        boolean ret = getFileNative(dbItem.mSession, localFilename, remoteFilename);
        if (ret) {
            BluetoothObexDatabase.TransferDbItem dbTransItem =
                    mSessionDb.new TransferDbItem(localFilename,remoteFilename,null,dbItem);
            dbTransItem.mDirection = BluetoothObexDatabase.TransferDirection.RX;
            mSessionDb.insert(dbTransItem);
        }

        return ret;
    }
    protected native boolean getFileNative(String session, String localFilename,
            String remoteFilename);

    /**
     * Copy a file from this device to a remote device (async call)
     *
     * @param address Destination BT address
     * @param localFilename Name of file on local device to send
     * @param remoteFilename Filename to save file in on remote device
     *
     * @return false indicates immediate error
     */
    public synchronized boolean putFile(String address, String localFilename,
            String remoteFilename) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) log("putFile() could not find session with address: " + address);
            return false;
        }

        boolean ret = putFileNative(dbItem.mSession, localFilename, remoteFilename);
        if (ret) {
            BluetoothObexDatabase.TransferDbItem dbTransItem =
                    mSessionDb.new TransferDbItem(localFilename,remoteFilename,null,dbItem);
            dbTransItem.mDirection = BluetoothObexDatabase.TransferDirection.TX;
            mSessionDb.insert(dbTransItem);
        }

        return ret;
    }
    protected native boolean putFileNative(String session, String localFilename,
            String remoteFilename);

    /**
     * Cancel an ongoing FTP transfer
     *
     * @param address Destination BT address
     * @param filename Filename of FTP object being transferred
     *
     * @return false indicates immediate error
     */
    public synchronized boolean cancelTransfer(String address, String name) {
        BluetoothObexDatabase.TransferDbItem dbItem = mSessionDb.getByFilename(name);

        if (dbItem == null) {
            if (DBG) log("cancelTransfer() could not find transfer with filename: " + name);
        } else {
            if (dbItem.mSession.mAddress.equals(address)) {
                mSessionDb.deleteByFilename(name);

                // It's possible to not have a defined transfer name yet
                // (i.e., onObexRequest() hasn't been called).
                if (dbItem.mTransfer != null) {
                    return cancelTransferNative(dbItem.mTransfer);
                } else {
                    return true;
                }
            } else {
                if (DBG) {
                    log("cancelTransfer() could not find transfer with filename: "
                        + name + " address: " + address);
                }
            }
        }

        return false;
    }
    protected native boolean cancelTransferNative(String transfer);

    /**
     * Determine if a given transfer is active.
     *
     * @param filename Filename of FTP object being transferred
     *
     * @return true indicates an ongoing transfer with filename.
     *         false indicates unknown/completed transfer.
     */
    public synchronized boolean isTransferActive(String filename) {
        return (mSessionDb.getByFilename(filename) != null);
    }

    /**
     * Report of FTP session creation completion
     *
     * @param session D-Bus object name representing session
     * @param address Remote device Bluetooth address
     * @param isError true if error during createSession() execution, false otherwise
     *
     * @see createSession()
     */
    public synchronized void onCreateSessionComplete(String session, String address,
            boolean isError) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getByAddress(address);

        if (dbItem == null) {
            if (DBG) {
                log("onCreateSessionComplete() could not find session with address: "
                        + address);
            }

            return;
        }

        if (isError) {
            // Remove session
            mSessionDb.deleteByAddress(address);
        } else {
            // Update session object
            mSessionDb.updateSessionByAddress(address, session);
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onCreateSessionComplete(isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Report of change folder completion
     *
     * @param session D-Bus object name representing session
     * @param folder Name of folder to change to
     * @param isError true if error during changeFolder() execution, false otherwise
     *
     * @see changeFolder()
     */
    public synchronized void onChangeFolderComplete(String session, String folder,
            boolean isError) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);

        if (dbItem == null) {
            if (DBG) log("onChangeFolderComplete() could not find session: " + session);
            return;
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onChangeFolderComplete(folder, isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Report of create folder completion
     *
     * @param session D-Bus object name representing session
     * @param folder Name of folder to create
     * @param isError true if error during createFolder() execution, false otherwise
     *
     * @see createFolder()
     */
    public synchronized void onCreateFolderComplete(String session, String folder,
            boolean isError) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);

        if (dbItem == null) {
            if (DBG) log("onCreateFolderComplete() could not find session: " + session);
            return;
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onCreateFolderComplete(folder, isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Report of get file completion
     *
     * @param session D-Bus object name representing session
     * @param localFilename Filename to save retrieved file in
     * @param remoteFilename Name of file on remote device to get
     * @param isError true if error during getFile() execution, false otherwise
     *
     * @see getFile()
     */
    public synchronized void onGetFileComplete(String session, String localFilename,
            String remoteFilename, boolean isError) {
        if (isError) {
            if (DBG) {
                log("onGetFileComplete() reported an error for local file: "
                        + localFilename + ".  Removing pending transfer.");
            }
            mSessionDb.deleteByFilename(localFilename);
        }

        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);
        if (dbItem == null) {
            if (DBG) log("onGetFileComplete() could not find session: " + session);
            return;
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onGetFileComplete(localFilename,
                    remoteFilename, isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Report of put file completion
     *
     * @param session D-Bus object name representing session
     * @param localFilename Filename to save retrieved file in
     * @param remoteFilename Name of file on remote device to get
     * @param isError true if error during putFile() execution, false otherwise
     *
     * @see putFile()
     */
    public synchronized void onPutFileComplete(String session, String localFilename,
            String remoteFilename, boolean isError) {
        if (isError) {
            if (DBG) {
                log("onPutFileComplete() reported an error for local file: "
                        + localFilename + ".  Removing pending transfer.");
            }
            mSessionDb.deleteByFilename(localFilename);
        }

        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);
        if (dbItem == null) {
            if (DBG) log("onPutFileComplete() could not find session: " + session);
            return;
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onPutFileComplete(localFilename,
                    remoteFilename, isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Report of delete completion
     *
     * @param session D-Bus object name representing session
     * @param name Name of file/folder to delete
     * @param isError true if error during delete() execution, false otherwise
     *
     * @see delete()
     */
    public synchronized void onDeleteComplete(String session, String name, boolean isError) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);

        if (dbItem == null) {
            if (DBG) log("onDeleteComplete() could not find session: " + session);
            return;
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onDeleteComplete(name, isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Request for a filename to show the remote party.
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
            if (DBG) {
                log("onObexRequest() could not retrieve properties for transfer: "
                        + transfer);
            }
            return null;
        } else {
            // FIXME: Work on getting this fixed "upstream"?
            //
            // The obexd "OBEX client API" defines the filename returned in org.openobex.Transfer
            // GetProperties() to be the "complete name of the file being received or sent."  In the
            // obexd implementation, however, this isn't always the fully qualified filename on the
            // the local device: it is implemented as the remote filename in the case of GetFile().  In
            // the case of GetFile() it is actually the same thing that obexd "opens"--a counterintuitive
            // implementation choice.
            //
            // We therefore need to check pending filenames (for PutFile() operations) as well as
            // pending remote/object names (for GetFile() operations).  This could present a problem
            // with multiple transfers using identical filenames--no effort is being put towards
            // these pathological corner cases.
            BluetoothObexDatabase.TransferDbItem dbItem = mSessionDb.getByFilename(tp.mFilename);
            if (dbItem == null) dbItem = mSessionDb.getByObjectName(tp.mFilename);

            if (dbItem == null) {
                if (DBG) log("onObexRequest() could not find a session using name: " + tp.mFilename);
                return null;
            } else {
                // Update the database to add the transfer reference...
                mSessionDb.updateTransferByFilename(dbItem.mFilename, transfer);

                // ...and the object size (refetch to get current record)
                dbItem = mSessionDb.getByFilename(dbItem.mFilename);
                if (dbItem == null) {
                    if (DBG) log("onObexRequest() could not match filename: " + dbItem.mFilename);
                    return null;
                } else {
                    dbItem.mObjectSize = tp.mSize;
                }

                // For an incoming file, return the pathname where we want
                // the file to go.  For an outgoing file, return the name
                // to display to remote device.
                if (dbItem.mDirection == BluetoothObexDatabase.TransferDirection.TX) {
                    File file = new File(dbItem.mFilename);
                    return file.getName();
                } else {
                    return dbItem.mFilename;
                }
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
        BluetoothObexDatabase.TransferDbItem dbItem = mSessionDb.getByTransfer(transfer);

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
     * Report of object transfer completion
     *
     * @param transfer OBEX transfer name
     * @param success true if push successful, else false
     * @param error if success is false, should hold a user-readable error message
     */
    public synchronized void onObexTransferComplete(String transfer, boolean success,
            String error) {
        BluetoothObexDatabase.TransferDbItem dbItem = mSessionDb.getByTransfer(transfer);

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
                intent.putExtra(BluetoothObexIntent.PROFILE, BluetoothObexIntent.PROFILE_FTP);
                intent.putExtra(BluetoothObexIntent.SUCCESS, success);
                mContext.sendBroadcast(intent, BLUETOOTH_PERM);
            }

            // Remove transfer database entry
            mSessionDb.deleteByFilename(dbItem.mFilename);
        }
    }

    /**
     * Report of OBEX session close (timeout, remotely initiated, etc...)
     *
     * @param session D-Bus object name representing session
     */
    public synchronized void onObexSessionClosed(String session) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);

        if (dbItem == null) {
            if (DBG) log("onObexSessionClosed() could not find session: " + session);
            return;
        }

        mSessionDb.deleteByAddress(dbItem.mAddress);

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onObexSessionClosed();
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * listFolder() completion callback.
     *
     * @param session OBEX session name
     * @param result An array of ObjectProperties
     * @param isError true if error, else false
     *
     * @see listFolder()
     */
    public synchronized void onListFolderComplete(String session, ObjectProperties[] result,
                                                  boolean isError) {
        BluetoothObexDatabase.SessionDbItem dbItem = mSessionDb.getBySession(session);

        if (dbItem == null) {
            if (DBG) log("onListFolderComplete() could not find session: " + session);
            return;
        }

        // create the map generation here
        List<Map> resultListMap = null;
        if (!isError) {
            resultListMap = (List)(new ArrayList<HashMap<String,Object>>());
            if (result != null) {
            for (int i=0;i<result.length;i++) {
                HashMap<String,Object> hm = new HashMap<String,Object>();
                hm.put("Name", result[i].mName);
                hm.put("Type", result[i].mType);
                hm.put("Size", result[i].mSize);
                hm.put("Permission", result[i].mPermission);
                hm.put("Modified", result[i].mModified);
                hm.put("Accessed", result[i].mAccessed);
                hm.put("Created", result[i].mCreated);
                resultListMap.add(hm);
            }
        }
        }

        // Send callback
        try {
            ((IBluetoothFtpCallback)dbItem.mCallback).onListFolderComplete(resultListMap,
                                                                           isError);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }
    public class ObjectProperties {
        public String mName; // object name
        public String mType; // object type: 'folder' or 'file'
        public int mSize; // object size (bytes), or number of items in folder
        public String mPermission; // object permissions (group, owner, other)
        public int mModified; // last object change timestamp
        public int mAccessed; // last object access timestamp
        public int mCreated; // object created timestamp

        public ObjectProperties(String name, String type,
                                int size, String permission,
                                int modified, int accessed,
                                int created) {
            mName = name;
            mType = type;
            mSize = size;
            mPermission = permission;
            mModified = modified;
            mAccessed = accessed;
            mCreated = created;
        }
    }

    /* TODO: Update with any new BM3-based obexd OBEX authorization (and other) enhancements. */
}
