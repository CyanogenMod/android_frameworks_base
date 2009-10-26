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

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * @hide
 */

/**
 * Local transfer "database" to provide filename<->transfer mappings
 * along with transfer-specific data
 */
public class BluetoothObexDatabase {
    public static enum TransferDirection {TX, RX};

    private static final String TAG = "BluetoothObexDatabase";
    private static final boolean DBG = false;

    private HashMap<String, TransferDbItem> mFilenameIdx;
    private HashMap<String, TransferDbItem> mObjectNameIdx;
    private HashMap<String, TransferDbItem> mTransferIdx;
    private HashMap<String, SessionDbItem> mSessionIdx;
    private HashMap<String, SessionDbItem> mAddressIdx;

    public class SessionDbItem implements Cloneable {
        /** Callback for async complete */
        public Object mCallback;
        /** Session object name associated with the transfer. */
        protected String mSession;
        /** Destination BT address associated with the transfer. */
        protected String mAddress;
        /** List of ongoing transfers associated with the session */
        private List<TransferDbItem> mTransfers;

        /**
         * Create a new database item
         *
         * @param address Address associated with this transfer
         * @param session Session object name associated with this transfer
         * @param callback Callback object to use for asynchronous calls.
         */
        public SessionDbItem(String address, String session, Object callback) {
            mAddress = address;
            mSession = session;
            // TODO: throw if both address and session are null?
            mCallback = callback;
            mTransfers = new LinkedList<TransferDbItem>();
        }

        /** Copy the SessionDbItem */
        @Override
        public Object clone() {
            try {
                // TODO: verify this does the right thing.
                // Use the default object clone implementation (shallow copy)
                return super.clone();
            } catch (CloneNotSupportedException e) {
                Log.e(TAG, "SessionDbItem clone() exception!", e);
                return null;
            }
        }

        /**
         * Getters for unit testing purposes
         */
        public final String getAddress() {
            return mAddress;
    }

        public final String getSession() {
            return mSession;
        }
    }

    public class TransferDbItem implements Cloneable {
        public int mObjectSize;
        public TransferDirection mDirection;
        public boolean mIsServer;
        public int mNativeData;
        protected SessionDbItem mSession;

        /** Object name associated with the transfer (remote device filename) */
        protected String mObjectName;
        /** Filename associated with the transfer. */
        protected String mFilename;
        /** Transfer object name associated with the transfer. */
        protected String mTransfer;

        /**
         * Create a new database item
         *
         * @param filename Filename associated with this transfer
         * @param objectName Object name associated with this transfer (remote device filename)
         * @param transfer Transfer object name associated with this transfer
         * @param session Session to associate with this transfer [for FTP]
         */
        public TransferDbItem(String filename, String objectName, String transfer, SessionDbItem session) {
            mFilename = filename;
            mObjectName = objectName;
            mTransfer = transfer;
            // TODO: throw if both filename and transfer are null?
            mObjectSize = 0;
            mDirection = TransferDirection.TX;
            mIsServer = false;
            mNativeData = 0;
            mSession = session;
        }

        /**
         * Copy the TransferDbItem
         */
        @Override
        public Object clone() {
            try {
                // Use the default object clone implementation (shallow copy)
                return super.clone();
            } catch (CloneNotSupportedException e) {
                Log.e(TAG, "TransferDbItem clone() exception!", e);
                return null;
            }
        }

        /**
         * Getters for unit testing purposes
         */
        public final String getFilename() {
            return mFilename;
        }

        public final String getTransfer() {
            return mTransfer;
        }

        public final SessionDbItem getSession() {
            return mSession;
        }
    }


    /**
     * Create an empty transfer database
     */
    public BluetoothObexDatabase() {
        mFilenameIdx = new HashMap<String, TransferDbItem>();
        mObjectNameIdx = new HashMap<String, TransferDbItem>();
        mTransferIdx = new HashMap<String, TransferDbItem>();
        mSessionIdx = new HashMap<String, SessionDbItem>();
        mAddressIdx = new HashMap<String, SessionDbItem>();
    }

    /**
     * Insert a new SessionDbItem into the database keyed with a session object name
     * and/or a bluetooth address
     *
     * @param item SessionDbItem to insert
     */
    public void insert(SessionDbItem item) {
        if (item.mSession != null) {
            mSessionIdx.put(item.mSession, item);
        }

        if (item.mAddress != null) {
            mAddressIdx.put(item.mAddress, item);
        }

        // Update references to item from transfers
        ListIterator<TransferDbItem> i = item.mTransfers.listIterator();
        while (i.hasNext()) {
            i.next().mSession = item;
        }

        // Not cataloging any associated transfers here--association of sessions
        // and transfers happens when inserting TransferDbItem
    }

    /**
     * Get a reference to the SessionDbItem keyed/associated with an address.
     *
     * @param address Address to lookup item with
     *
     * @return item, if found, otherwise null
     */
    public SessionDbItem getByAddress(String address) {
        return mAddressIdx.get(address);
    }

    /**
     * Get a reference to the SessionDbItem keyed/associated with a session object name.
     *
     * @param session Session object name to lookup item with
     *
     * @return item, if found, otherwise null
     */
    public SessionDbItem getBySession(String session) {
        return mSessionIdx.get(session);
    }

    /**
     * SessionDbItem delete helper function.
     */
    private void deleteItem(SessionDbItem dbItem) {
        if (dbItem != null) {
            mSessionIdx.remove(dbItem.mSession);
            mAddressIdx.remove(dbItem.mAddress);

            // Remove references to dbItem from transfers
            ListIterator<TransferDbItem> i = dbItem.mTransfers.listIterator();
            while (i.hasNext()) {
                i.next().mSession = null;
            }

            // Not removing any associated transfers
        }
    }

    /**
     * Delete the SessionDbItem entry associated with an address
     *
     * @param address Address to lookup item with
     */
    public void deleteByAddress(String address) {
        deleteItem(getByAddress(address));
    }

    /**
     * Update the SessionDbItem entry associated with an address to associate with a new
     * session.
     *
     * @param curAddress Address to lookup item with
     * @param newSession New session object name to associate with the session
     */
    public void updateSessionByAddress(String curAddress, String newSession) {
        SessionDbItem dbItem = getByAddress(curAddress);

        SessionDbItem newDbItem = (SessionDbItem)dbItem.clone();
        // TODO: figure out a relationship here that doesn't do this
        newDbItem.mSession = newSession;

        deleteByAddress(curAddress);
        insert(newDbItem);
    }

    /**
     * Insert a TransferDbItem into the database keyed with a filename and/or a transfer
     * object name
     *
     * @param item TransferDbItem to insert
     */
    public void insert(TransferDbItem item) {
        if (item.mFilename != null) {
            mFilenameIdx.put(item.mFilename, item);
        }

        if (item.mObjectName != null) {
            mObjectNameIdx.put(item.mObjectName, item);
        }

        if (item.mTransfer != null) {
            mTransferIdx.put(item.mTransfer, item);
        }

        // If specified, associate transfer the session
        if (item.mSession != null) {
            item.mSession.mTransfers.add(item);
        }
    }

    /**
     * Get a reference to the TransferDbItem keyed/associated with a filename.
     *
     * @param filename Filename to lookup item with
     *
     * @return item, if found, otherwise null
     */
    public TransferDbItem getByFilename(String filename) {
        return mFilenameIdx.get(filename);
    }

    /**
     * Get a reference to the TransferDbItem keyed/associated with an object name.
     *
     * @param objectName Object name to lookup item with
     *
     * @return item, if found, otherwise null
     */
    public TransferDbItem getByObjectName(String objectName) {
        return mObjectNameIdx.get(objectName);
    }

    /**
     * Get a reference to the TransferDbItem keyed/associated with a transfer object name.
     *
     * @param transfer Transfer object name to lookup item with
     *
     * @return item, if found, otherwise null
     */
    public TransferDbItem getByTransfer(String transfer) {
        return mTransferIdx.get(transfer);
    }

    /**
     * Get a reference to the TransferDbItem keyed/associated with an address.
     *
     * @param address Address to lookup item with
     *
     * @return item, if found, otherwise null
     */
    public TransferDbItem getTransferByAddress(String address) {
        TransferDbItem ret = null;
        SessionDbItem session = mAddressIdx.get(address);

        if ((session != null) && !session.mTransfers.isEmpty()) {
            // Currently hardcoded to first linked transfer--
            // this limits the client to starting transfers sequentially
            // (one at a time).
            //
            // TODO: remove this limitation by checking other parameters
            ret = session.mTransfers.get(0);
        }

        return ret;
    }


    /**
     * TransferDbItem delete helper function.
     */
    private void deleteItem(TransferDbItem dbItem) {
        if (dbItem != null) {
            mFilenameIdx.remove(dbItem.mFilename);
            mObjectNameIdx.remove(dbItem.mObjectName);
            mTransferIdx.remove(dbItem.mTransfer);

            // If session specified, remove link to transfer
            if (dbItem.mSession != null) {
                SessionDbItem assocSession = mSessionIdx.get(dbItem.mSession);
                if (assocSession != null) {
                    assocSession.mTransfers.remove(dbItem);
                }
            }
        }
    }

    /**
     * Delete the database entry associated with a filename
     *
     * @param filename Filename to lookup item with
     */
    public void deleteByFilename(String filename) {
        deleteItem(getByFilename(filename));
    }

    /**
     * Update the database entry associated with a filename to associate with a new
     * filename.
     *
     * @param curFilename Filename to lookup item with
     * @param newFilename New filename to associate with the transfer
     */
    public void updateFilenameByFilename(String curFilename, String newFilename) {
        TransferDbItem dbItem = getByFilename(curFilename);

        TransferDbItem newDbItem = (TransferDbItem)dbItem.clone();
        // TODO: figure out a relationship here that doesn't do this
        newDbItem.mFilename = newFilename;

        deleteByFilename(curFilename);
        insert(newDbItem);
    }

    /**
     * Update the database entry associated with a filename to associate with a new
     * transfer.
     *
     * @param curFilename Filename to lookup item with
     * @param newTransfer New transfer object name to associate with the transfer
     */
    public void updateTransferByFilename(String curFilename, String newTransfer) {
        TransferDbItem dbItem = getByFilename(curFilename);

        TransferDbItem newDbItem = (TransferDbItem)dbItem.clone();
        // TODO: figure out a relationship here that doesn't do this
        newDbItem.mTransfer = newTransfer;

        deleteByFilename(curFilename);
        insert(newDbItem);
    }
}
