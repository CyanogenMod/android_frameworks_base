/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package javax.btobex;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.os.Handler;
import android.os.Message;
import android.os.Looper;
import android.os.HandlerThread;

/**
 * This class in an implementation of the OBEX ClientSession.
 * @hide
 */
public final class ClientSession extends ObexSession {
    private static final String TAG = "Obex ClientSession";

    private static final boolean VERBOSE = ObexHelper.VERBOSE;

    private boolean mOpen;

    // Determines if an OBEX layer connection has been established
    private boolean mObexConnected;

    private byte[] mConnectionId = null;

    /*
     * The max Packet size must be at least 256 according to the OBEX
     * specification.
     */
    private int maxPacketSize = 256;

    private static final int OBEX_RESPONSE_TIME_OUT = 1;

    /* Response timeout for OBEX request sent */
    private static final int OBEX_RESPONSE_TIME_OUT_VALUE = 30000;

    private boolean mRequestActive;

    private boolean setMTU = false;

    private final InputStream mInput;

    private final OutputStream mOutput;

    private long mTotalSize = 0;
    public ObexHelper mSrmClient;

    private ClientSessionHandler mClientSessionHandler = null;

    private HandlerThread mHandlerThread = null;

    public ClientSession(final ObexTransport trans) throws IOException {
        mInput = trans.openInputStream();
        mOutput = trans.openOutputStream();
        mOpen = true;
        mRequestActive = false;
        mSrmClient = new ObexHelper();
        mHandlerThread = new HandlerThread("OBEX Time Out Handler");
        mHandlerThread.start();
        mClientSessionHandler = new ClientSessionHandler(mHandlerThread.getLooper());
    }

    public void setMaxPacketSize(int size) {
        if (VERBOSE) Log.v(TAG, "setMaxPacketSize" + size);
        maxPacketSize = size;
    }

    public HeaderSet connect(final HeaderSet header) throws IOException {
        ensureOpen();
        if (mObexConnected) {
            throw new IOException("Already connected to server");
        }
        setRequestActive();

        int totalLength = 4;
        byte[] head = null;

        // Determine the header byte array
        if (header != null) {
            if (header.nonce != null) {
                mChallengeDigest = new byte[16];
                System.arraycopy(header.nonce, 0, mChallengeDigest, 0, 16);
            }
            head = ObexHelper.createHeader(header, false);
            totalLength += head.length;
        }
        /*
        * Write the OBEX CONNECT packet to the server.
        * Byte 0: 0x80
        * Byte 1&2: Connect Packet Length
        * Byte 3: OBEX Version Number (Presently, 0x10)
        * Byte 4: Flags (For TCP 0x00)
        * Byte 5&6: Max OBEX Packet Length
        * Byte 7 to n: headers
        */
        byte[] requestPacket = new byte[totalLength];
        // We just need to start at  byte 3 since the sendRequest() method will
        // handle the length and 0x80.
        requestPacket[0] = (byte)0x10;
        requestPacket[1] = (byte)0x00;
        requestPacket[2] = (byte)(maxPacketSize >> 8);
        requestPacket[3] = (byte)(maxPacketSize & 0xFF);
        if (head != null) {
            System.arraycopy(head, 0, requestPacket, 4, head.length);
        }

        // check with local max packet size
        if ((requestPacket.length + 3) > maxPacketSize) {
            throw new IOException("Packet size exceeds max packet size");
        }

        HeaderSet returnHeaderSet = new HeaderSet();
        sendRequest(ObexHelper.OBEX_OPCODE_CONNECT, requestPacket, returnHeaderSet, null, false,
                       false);

        /*
        * Read the response from the OBEX server.
        * Byte 0: Response Code (If successful then OBEX_HTTP_OK)
        * Byte 1&2: Packet Length
        * Byte 3: OBEX Version Number
        * Byte 4: Flags3
        * Byte 5&6: Max OBEX packet Length
        * Byte 7 to n: Optional HeaderSet
        */
        if (returnHeaderSet.responseCode == ResponseCodes.OBEX_HTTP_OK) {
            if(returnHeaderSet.mConnectionID != null ){
                System.arraycopy(returnHeaderSet.mConnectionID, 0, mConnectionId, 0, 4);
            }
            mObexConnected = true;

            Byte srm = (Byte)returnHeaderSet.getHeader(HeaderSet.SINGLE_RESPONSE_MODE);
            if (srm == ObexHelper.OBEX_SRM_SUPPORTED) {
                mSrmClient.setRemoteSrmStatus(ObexHelper.SRM_CAPABLE);
                if (VERBOSE) Log.v(TAG, "SRM status: Enabled by Server response");
            } else {
                mSrmClient.setRemoteSrmStatus(ObexHelper.SRM_INCAPABLE);
                if (VERBOSE) Log.v(TAG, "SRM status: Disabled by Server response");
            }
        }
        setRequestInactive();

        return returnHeaderSet;
    }

    public Operation get(HeaderSet header) throws IOException {

        if (!mObexConnected) {
            throw new IOException("Not connected to the server");
        }
        setRequestActive();

        ensureOpen();

        HeaderSet head;
        if (header == null) {
            head = new HeaderSet();
        } else {
            head = header;
            if (head.nonce != null) {
                mChallengeDigest = new byte[16];
                System.arraycopy(head.nonce, 0, mChallengeDigest, 0, 16);
            }
        }
        // Add the connection ID if one exists
        if (mConnectionId != null) {
            head.mConnectionID = new byte[4];
            System.arraycopy(mConnectionId, 0, head.mConnectionID, 0, 4);
        }

        return new ClientOperation(maxPacketSize, this, head, true);
    }

    /**
     * 0xCB Connection Id an identifier used for OBEX connection multiplexing
     */
    public void setConnectionID(long id) {
        if ((id < 0) || (id > 0xFFFFFFFFL)) {
            throw new IllegalArgumentException("Connection ID is not in a valid range");
        }
        mConnectionId = ObexHelper.convertToByteArray(id);
    }

    public HeaderSet delete(HeaderSet header) throws IOException {

        Operation op = put(header);
        op.getResponseCode();
        HeaderSet returnValue = op.getReceivedHeader();
        op.close();

        return returnValue;
    }

    public HeaderSet disconnect(HeaderSet header) throws IOException {
        if (!mObexConnected) {
            throw new IOException("Not connected to the server");
        }
        setRequestActive();

        ensureOpen();
        // Determine the header byte array
        byte[] head = null;
        if (header != null) {
            if (header.nonce != null) {
                mChallengeDigest = new byte[16];
                System.arraycopy(header.nonce, 0, mChallengeDigest, 0, 16);
            }
            // Add the connection ID if one exists
            if (mConnectionId != null) {
                header.mConnectionID = new byte[4];
                System.arraycopy(mConnectionId, 0, header.mConnectionID, 0, 4);
            }
            head = ObexHelper.createHeader(header, false);

            if ((head.length + 3) > maxPacketSize) {
                throw new IOException("Packet size exceeds max packet size");
            }
        } else {
            // Add the connection ID if one exists
            if (mConnectionId != null) {
                head = new byte[5];
                head[0] = (byte)HeaderSet.CONNECTION_ID;
                System.arraycopy(mConnectionId, 0, head, 1, 4);
            }
        }

        HeaderSet returnHeaderSet = new HeaderSet();
        sendRequest(ObexHelper.OBEX_OPCODE_DISCONNECT, head, returnHeaderSet, null, false, false);

        /*
         * An OBEX DISCONNECT reply from the server:
         * Byte 1: Response code
         * Bytes 2 & 3: packet size
         * Bytes 4 & up: headers
         */

        /* response code , and header are ignored
         * */

        synchronized (this) {
            mObexConnected = false;
            setRequestInactive();
        }

        /* OBEX disconnect from app.  Stop the handler thread */
        if (mHandlerThread != null ) {
          mHandlerThread.quit();
          mHandlerThread = null;
          mClientSessionHandler = null;
        }
        return returnHeaderSet;
    }

    public long getConnectionID() {

        if (mConnectionId == null) {
            return -1;
        }
        return ObexHelper.convertToLong(mConnectionId);
    }

    public void reduceMTU(boolean enable) {
        setMTU = enable;
    }

    public Operation put(HeaderSet header) throws IOException {
        if (!mObexConnected) {
            throw new IOException("Not connected to the server");
        }
        setRequestActive();

        ensureOpen();
        HeaderSet head;
        if (header == null) {
            head = new HeaderSet();
        } else {
            head = header;
            // when auth is initiated by client ,save the digest
            if (head.nonce != null) {
                mChallengeDigest = new byte[16];
                System.arraycopy(head.nonce, 0, mChallengeDigest, 0, 16);
            }
        }

        // Add the connection ID if one exists
        if (mConnectionId != null) {

            head.mConnectionID = new byte[4];
            System.arraycopy(mConnectionId, 0, head.mConnectionID, 0, 4);
        }

        return new ClientOperation(maxPacketSize, this, head, false);
    }

    public void setAuthenticator(Authenticator auth) throws IOException {
        if (auth == null) {
            throw new IOException("Authenticator may not be null");
        }
        mAuthenticator = auth;
    }

    public HeaderSet setPath(HeaderSet header, boolean backup, boolean create) throws IOException {
        if (!mObexConnected) {
            throw new IOException("Not connected to the server");
        }
        setRequestActive();
        ensureOpen();

        int totalLength = 2;
        byte[] head = null;
        HeaderSet headset;
        if (header == null) {
            headset = new HeaderSet();
        } else {
            headset = header;
            if (headset.nonce != null) {
                mChallengeDigest = new byte[16];
                System.arraycopy(headset.nonce, 0, mChallengeDigest, 0, 16);
            }
        }

        // when auth is initiated by client ,save the digest
        if (headset.nonce != null) {
            mChallengeDigest = new byte[16];
            System.arraycopy(headset.nonce, 0, mChallengeDigest, 0, 16);
        }

        // Add the connection ID if one exists
        if (mConnectionId != null) {
            headset.mConnectionID = new byte[4];
            System.arraycopy(mConnectionId, 0, headset.mConnectionID, 0, 4);
        }

        head = ObexHelper.createHeader(headset, false);
        totalLength += head.length;

        if (totalLength > maxPacketSize) {
            throw new IOException("Packet size exceeds max packet size");
        }

        int flags = 0;
        /*
         * The backup flag bit is bit 0 so if we add 1, this will set that bit
         */
        if (backup) {
            flags++;
        }
        /*
         * The create bit is bit 1 so if we or with 2 the bit will be set.
         */
        if (!create) {
            flags |= 2;
        }

        /*
         * An OBEX SETPATH packet to the server:
         * Byte 1: 0x85
         * Byte 2 & 3: packet size
         * Byte 4: flags
         * Byte 5: constants
         * Byte 6 & up: headers
         */
        byte[] packet = new byte[totalLength];
        packet[0] = (byte)flags;
        packet[1] = (byte)0x00;
        if (headset != null) {
            System.arraycopy(head, 0, packet, 2, head.length);
        }

        HeaderSet returnHeaderSet = new HeaderSet();
        sendRequest(ObexHelper.OBEX_OPCODE_SETPATH, packet, returnHeaderSet, null, false, false);

        /*
         * An OBEX SETPATH reply from the server:
         * Byte 1: Response code
         * Bytes 2 & 3: packet size
         * Bytes 4 & up: headers
         */

        setRequestInactive();

        return returnHeaderSet;
    }

    /**
     * Verifies that the connection is open.
     * @throws IOException if the connection is closed
     */
    public synchronized void ensureOpen() throws IOException {
        if (!mOpen) {
            throw new IOException("Connection closed");
        }
    }

    /**
     * Set request inactive. Allows Put and get operation objects to tell this
     * object when they are done.
     */
    /*package*/synchronized void setRequestInactive() {
        mRequestActive = false;
    }

    /**
     * Set request to active.
     * @throws IOException if already active
     */
    private synchronized void setRequestActive() throws IOException {
        if (mRequestActive) {
            throw new IOException("OBEX request is already being performed");
        }
        mRequestActive = true;
    }

    /**
     * Sends a standard request to the client. If ignoreResponse is not set, it
     * will then wait for the reply and update the header set object provided.
     * If any authentication headers (i.e. authentication challenge or
     * authentication response) are received, they will be processed.
     *
     * If ignoreResponse is set, this will not wait for a reply and the header
     * set object will not be updated.  This is intended for use during Single
     * Response Mode operation.
     *
     * @param opCode the type of request to send to the client
     * @param head the headers to send to the client
     * @param header the header object to update with the response
     * @param privateInput the input stream used by the Operation object; null
     *        if this is called on a CONNECT, SETPATH or DISCONNECT return
     *        <code>true</code> if the operation completed successfully;
     *        <code>false</code> if an authentication response failed to pass
     * @param ignoreResponse true if a response shouldn't be received (e.g.,
     *        when operating under Single Response Mode (SRM).  false otherwise.
     * @throws IOException if an IO error occurs
     */
    public boolean sendRequest(int opCode, byte[] head, HeaderSet header,
            PrivateInputStream privateInput,
            boolean ignoreResponse,
            boolean supressSend) throws IOException {

        if (VERBOSE) Log.v(TAG, "sendRequest ignore: " + ignoreResponse
                                   + ", SRMP WAIT: " + mSrmClient.getLocalSrmpWait()
                                   + " supressSend : "+supressSend);

        //check header length with local max size
        if (head != null) {
            if ((head.length  + 3) > maxPacketSize) {
                throw new IOException("header too large ");
            }
        }

        int bytesReceived;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((byte)opCode);
        if (VERBOSE) Log.v(TAG, "sendRequest opCode = "+opCode);

        // Determine if there are any headers to send
        if (head == null) {
            out.write(0x00);
            out.write(0x03);
        } else {
            out.write((byte)((head.length + 3) >> 8));
            out.write((byte)(head.length + 3));
            out.write(head);
            if (VERBOSE) Log.v(TAG, "sendRequest head.length = "+head.length);
        }

        if(!supressSend) {
            // Write the request to the output stream and flush the stream
            mOutput.write(out.toByteArray());
            mOutput.flush();
        }

        if ( (!ignoreResponse) || (mSrmClient.getLocalSrmpWait()) ) {
            startResponseTimer();
            try {
              header.responseCode = mInput.read();
              if (VERBOSE) Log.v(TAG, "sendRequest responseCode "+header.responseCode);
            } catch (IOException e) {
                Log.v(TAG, "Response timed out. Clean up the handler: " + e);
                if (mHandlerThread != null ) {
                  mHandlerThread.quit();
                  mHandlerThread = null;
                  mClientSessionHandler = null;
                }
            }

            if (mClientSessionHandler != null) stopResponseTimer();

            int length = ((mInput.read() << 8) | (mInput.read()));
            if (VERBOSE) Log.v(TAG, "sendRequest response length "+length);

            if (length > maxPacketSize) {
                throw new IOException("Packet received exceeds packet size limit");
            }
            if (length > ObexHelper.BASE_PACKET_LENGTH) {
                byte[] data = null;
                if (opCode == ObexHelper.OBEX_OPCODE_CONNECT) {
                    @SuppressWarnings("unused")
                    int version = mInput.read();
                    @SuppressWarnings("unused")
                    int flags = mInput.read();
                    maxPacketSize = (mInput.read() << 8) + mInput.read();

                    //check with local max size
                    if (setMTU) {
                        maxPacketSize = ObexHelper.A2DP_SCO_OBEX_MAX_CLIENT_PACKET_SIZE;
                    setMTU = false;
                    } else if (maxPacketSize > ObexHelper.MAX_CLIENT_PACKET_SIZE) {
                        maxPacketSize = ObexHelper.MAX_CLIENT_PACKET_SIZE;
                    }

                    if (length > 7) {
                       data = new byte[length - 7];
                       bytesReceived = mInput.read(data);
                       while (bytesReceived != (length - 7)) {
                           bytesReceived += mInput.read(data, bytesReceived, data.length
                                - bytesReceived);
                       }
                    } else {
                        return true;
                    }
                } else {
                    data = new byte[length - 3];
                    bytesReceived = mInput.read(data);

                    while (bytesReceived != (length - 3)) {
                        bytesReceived += mInput.read(data, bytesReceived, data.length - bytesReceived);
                    }
                    if (opCode == ObexHelper.OBEX_OPCODE_ABORT) {
                        return true;
                    }
                }

                byte[] body = ObexHelper.updateHeaderSet(header, data);
                if ((privateInput != null) && (body != null)) {
                    privateInput.writeBytes(body, 1);
                    mTotalSize += (long)(body.length - 1);
                    if((body[0] == HeaderSet.END_OF_BODY) &&
                                            (header.getHeader(HeaderSet.LENGTH) == null)){
                        header.setHeader(HeaderSet.LENGTH, mTotalSize);
                        if (VERBOSE) Log.v(TAG, " header.mLength : "
                                                + header.getHeader(HeaderSet.LENGTH));
                        mTotalSize = 0;
                    }
                }

                if (header.mConnectionID != null) {
                    mConnectionId = new byte[4];
                    System.arraycopy(header.mConnectionID, 0, mConnectionId, 0, 4);
                }

                if (header.mAuthResp != null) {
                    if (!handleAuthResp(header.mAuthResp)) {
                        setRequestInactive();
                        throw new IOException("Authentication Failed");
                   }
                }

                if ((header.responseCode == ResponseCodes.OBEX_HTTP_UNAUTHORIZED)
                    && (header.mAuthChall != null)) {

                if (handleAuthChall(header)) {
                    out.write((byte)HeaderSet.AUTH_RESPONSE);
                    out.write((byte)((header.mAuthResp.length + 3) >> 8));
                    out.write((byte)(header.mAuthResp.length + 3));
                    out.write(header.mAuthResp);
                    header.mAuthChall = null;
                    header.mAuthResp = null;

                    byte[] sendHeaders = new byte[out.size() - 3];
                    System.arraycopy(out.toByteArray(), 3, sendHeaders, 0, sendHeaders.length);

                        return sendRequest(opCode, sendHeaders, header, privateInput, false, false);
                    }
                }
            }
        }

        return true;
    }

    public HeaderSet action(HeaderSet header,int action) throws IOException {
        if (!mObexConnected) {
            throw new IOException("Not connected to the server");
        }
        setRequestActive();
        ensureOpen();

        int totalLength = 2;
        byte[] head = null;
        HeaderSet headset;
        if (header == null) {
            headset = new HeaderSet();
        } else {
            headset = header;
            if (headset.nonce != null) {
                mChallengeDigest = new byte[16];
                System.arraycopy(headset.nonce, 0, mChallengeDigest, 0, 16);
            }
        }

        // when auth is initiated by client ,save the digest
        if (headset.nonce != null) {
            mChallengeDigest = new byte[16];
            System.arraycopy(headset.nonce, 0, mChallengeDigest, 0, 16);
        }

        // Add the connection ID if one exists
        if (mConnectionId != null) {
            headset.mConnectionID = new byte[4];
            System.arraycopy(mConnectionId, 0, headset.mConnectionID, 0, 4);
        }

        head = ObexHelper.createHeader(headset, false);
        totalLength += head.length;

        if (totalLength > maxPacketSize) {
            throw new IOException("Packet size exceeds max packet size");
        }

        /*
         * An OBEX ACTION packet to the server:
         * Byte 1: 0x86
         * Byte 2 & 3: packet size
         * Byte 4: 0x94
         * Byte 5: action
         * Byte 6 & up: headers
         */
        byte[] packet = new byte[totalLength];
        packet[0] = (byte)0x94;
        packet[1] = (byte)action;

        if (headset != null) {
            System.arraycopy(head, 0, packet, 2, head.length);
        }

        HeaderSet returnHeaderSet = new HeaderSet();
        sendRequest(ObexHelper.OBEX_OPCODE_ACTION, packet, returnHeaderSet, null, false, false);

        setRequestInactive();

        return returnHeaderSet;
    }

    public void close() throws IOException {
        mOpen = false;
        mInput.close();
        mOutput.close();
    }
    private final class ClientSessionHandler extends Handler {
        public ClientSessionHandler (Looper looper) {
          super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
           if (VERBOSE) Log.v(TAG, "Handler(): Response Time out. Close the socket");
           switch (msg.what) {
              case OBEX_RESPONSE_TIME_OUT:
                try {
                  close();
                } catch (IOException e) {
                    if (VERBOSE) Log.v(TAG, "Response time out: "  + e);
                }
                break;
           }
        }
    }

    private void startResponseTimer() {
        if (VERBOSE) Log.v(TAG, "OBEX: Start response timer");
        if (mClientSessionHandler != null) {
           mClientSessionHandler.sendMessageDelayed(mClientSessionHandler
              .obtainMessage(OBEX_RESPONSE_TIME_OUT), OBEX_RESPONSE_TIME_OUT_VALUE);
        }
        return;
    }

    private void stopResponseTimer() {
        if (VERBOSE) Log.v(TAG, "OBEX: Stop response timer");
        if (mClientSessionHandler != null) {
          mClientSessionHandler.removeMessages(OBEX_RESPONSE_TIME_OUT);
        }
        return;
    }
}
