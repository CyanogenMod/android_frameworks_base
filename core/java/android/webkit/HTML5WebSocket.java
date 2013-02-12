/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2013 Oleg Smirnov
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

package android.webkit;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import android.util.Log;
/**
 * @hide This is only used by the browser
 *
 * HTML5 support class for WebSockets.
 *
 * This class runs almost entirely on the WebCore thread.
 */
public final class HTML5WebSocket extends Handler {
    // Logging tag.
    private static final String LOG_TAG = "HTML5WebSocket";

    // Message ids
    private static final int WEB_SOCKET_SEND  = 100;
    private static final int WEB_SOCKET_CLOSE = 101;

    // Message ids to be handled on the WebCore thread
    private static final int WEB_SOCKET_CONNECTED = 200;
    private static final int WEB_SOCKET_CLOSED    = 201;
    private static final int WEB_SOCKET_MESSAGE   = 202;
    private static final int WEB_SOCKET_ERROR     = 203;

    // The C++ WebSocketBridge object.
    private int mNativePointer = 0;
    // The handler for WebCore thread messages;
    private Handler mWebCoreHandler = null;
    // Helper class with internal implementation
    private WebSocket mWebSocket = null;

    /** @hide */
    public void onConnected() {
        Message msg = Message.obtain(mWebCoreHandler, WEB_SOCKET_CONNECTED);
        mWebCoreHandler.sendMessage(msg);
    }

    /** @hide */
    public void onClosed() {
        Message msg = Message.obtain(mWebCoreHandler, WEB_SOCKET_CLOSED);
        mWebCoreHandler.sendMessage(msg);
    }

    /** @hide */
    public void onMessage() {
        Message msg = Message.obtain(mWebCoreHandler, WEB_SOCKET_MESSAGE);
        mWebCoreHandler.sendMessage(msg);
    }

    /** @hide */
    public void onError(Throwable t) {
        Message msg = Message.obtain(mWebCoreHandler, WEB_SOCKET_ERROR);
        mWebCoreHandler.sendMessage(msg);
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case WEB_SOCKET_SEND: {
                mWebSocket.send();
                break;
            }
            case WEB_SOCKET_CLOSE: {
                mWebSocket.close();
                break;
            }
            default: {
                break;
            }
        }
    }

    /** @hide */
    private static class WebSocket implements Runnable {
        private static final String LOG_TAG = "WebSocket";

        // Handler on HTML5WebSocket
        private HTML5WebSocket mCurrentWebSocket;

        private SocketChannel mSocketChannel;
        private Selector mSelector;
        private boolean mRunning = false;

        private boolean mIsSecure = false;

        private static final int BUFFER_SIZE = 4096;

        private BlockingQueue<ByteBuffer> mBufferWriteQueue;
        private BlockingQueue<ByteBuffer> mBufferReadQueue;

        private String mHost = null;
        private int mPort = 80;

        private ByteBuffer mRecvBuffer = null;

        /** @hide */
        public WebSocket(HTML5WebSocket webSocket) throws NoSuchAlgorithmException, KeyManagementException {
            this.mCurrentWebSocket = webSocket;

            this.mBufferWriteQueue = new LinkedBlockingQueue<ByteBuffer>();
            this.mBufferReadQueue = new LinkedBlockingQueue<ByteBuffer>();
        }

        /** @hide */
        public Thread connect(URI uri) throws IOException {
            mHost = uri.getHost();
            mPort = uri.getPort();

            mIsSecure = uri.getScheme().equalsIgnoreCase("https") ? true : false;

            this.setSocketRunning(true);
            this.mSocketChannel = SocketChannel.open();
            this.mSocketChannel.configureBlocking(false);
            this.mSocketChannel.connect(new InetSocketAddress(mHost, mPort));

            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("java.net.preferIPv6Addresses", "false");

            this.mSelector = Selector.open();
            this.mSocketChannel.register(mSelector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ);

            if (mIsSecure) {
                 // TODO: SSL web sockets are not implemented
                 this.setSocketRunning(false);
                 Thread th = null;
                 return th;
            }
            Thread th = new Thread(this);
            th.start();
            return th;
        }

        @Override
        public void run() {
            while (isSocketRunning()) {
                try {
                    handleRunnable();
                } catch (IOException e) {
                    this.mCurrentWebSocket.onError(e);
                } catch (InterruptedException e) {
                    this.mCurrentWebSocket.onError(e);
                }
            }
        }

        /** @hide */
        public void close() {
            try {
                closeImpl();
            } catch (IOException e) {
                return;
            }
        }

        /** @hide */
        public void send() {
            try {
                this.mSocketChannel.register(this.mSelector, SelectionKey.OP_CONNECT | SelectionKey.OP_READ
                                                            | SelectionKey.OP_WRITE);
            } catch (ClosedChannelException e) {
                this.mCurrentWebSocket.onError(e);
            }
        }

        /** @hide */
        public ByteBuffer getData() {
            ByteBuffer data = null;
            ByteBuffer msg = null;
            do {
                msg = getReadQueueData();
                if (msg == null) {
                    break;
                }
                ByteBuffer ch = ByteBuffer.allocate((data != null ? data.capacity() : 0) + msg.capacity());

                if (data != null) {
                    data.rewind();
                    ch.put(data);
                }
                msg.rewind();
                ch.put(msg.array(), 0, msg.capacity());
                data = ch;

                if (data.capacity() > 2 * BUFFER_SIZE) {
                    break;
                }
            } while (msg != null);
            return data;
        }

        /** @hide */
        synchronized public ByteBuffer getReadQueueData() {
            return this.mBufferReadQueue.poll();
        }

        /** @hide */
        synchronized public ByteBuffer getWriteQueueData() {
            return this.mBufferWriteQueue.poll();
        }

        /** @hide */
        synchronized public void putReadQueueData(ByteBuffer data) throws InterruptedException {
            data.rewind();
            this.mBufferReadQueue.put(data);
        }

        /** @hide */
        synchronized public void putWriteQueueData(ByteBuffer data) throws InterruptedException {
            data.rewind();
            this.mBufferWriteQueue.put(data);
        }

        synchronized private boolean isSocketRunning() {
            if (this.mRunning && this.mSocketChannel != null && !this.mSocketChannel.socket().isClosed()) {
                return true;
            }
            return false;
        }

        synchronized private void setSocketRunning(boolean running) {
            this.mRunning = running;
        }

        private void handleRunnable() throws InterruptedException, IOException {
            if (!isSocketRunning()) {
                return;
            }
            if (!this.mSelector.isOpen()) {
                return;
            }

            try {
                if (this.mSelector.select() == 0) {
                    return;
                }
            } catch (IOException e) {
                return;
            } catch (ClosedSelectorException e) {
                return;
            } catch (IllegalArgumentException e) {
                return;
            }

            Set<SelectionKey> keys = mSelector.selectedKeys();
            Iterator<SelectionKey> iter = keys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if (!key.isValid())
                    continue;

                if (key.isConnectable()) {
                    handleConnectable(key);
                    continue;
                }
                if (key.isWritable()) {
                    handleWritable(key);
                    continue;
                }
                if (key.isReadable()) {
                    handleReadable(key);
                    continue;
                }
            }
        }

        private void handleConnectable(SelectionKey key) throws IOException {
                if (this.mSocketChannel.isConnectionPending()) {
                    this.mSocketChannel.finishConnect();
                }

                this.mRecvBuffer = ByteBuffer.allocate(BUFFER_SIZE);
                this.mCurrentWebSocket.onConnected();
        }

        private void handleWritable(SelectionKey key) throws IOException {
            try {
                int count = 0;
                ByteBuffer data = this.getWriteQueueData();

                if (data != null) {
                    count = writeImpl(data);
                }

                if (count > 0) {
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                }
            } catch (IOException ex) {
                this.mCurrentWebSocket.onError(ex);
                key.cancel();
            }
        }

        private void handleReadable(SelectionKey key) throws IOException, InterruptedException {
            try {
                int count = this.readImpl();

                if (count < 0) {
                    this.mCurrentWebSocket.onMessage();
                    handleWritable(key);
                }
            } catch (IOException ex) {
                this.mCurrentWebSocket.onError(ex);
                key.cancel();
            }
        }

        private int writeImpl(ByteBuffer data) throws IOException {
            int plainDataCount = -1;
            if (data == null) {
                return plainDataCount;
            }

            byte[] bytes = data.array();
            int dataLength = bytes.length;

            do {
                if (!isSocketRunning()) {
                    break;
                }
                ByteBuffer b = ByteBuffer.allocate(bytes.length);
                b.put(bytes);
                b.rewind();
                plainDataCount = this.mSocketChannel.write(b);
            } while (plainDataCount < dataLength);

            return plainDataCount;
        }

        private int readImpl() throws IOException, InterruptedException {
            int plainDataCount = -1;
            do {
                if (!isSocketRunning()) {
                    break;
                }
                this.mRecvBuffer.clear();
                plainDataCount = this.mSocketChannel.read(this.mRecvBuffer);

                if (plainDataCount <= 0) {
                    plainDataCount = -1;
                    break;
                }
                ByteBuffer data = ByteBuffer.allocate(plainDataCount);
                if (data != null) {
                    data.put(mRecvBuffer.array(), 0, plainDataCount);
                    putReadQueueData(data);
                }
            } while (plainDataCount > 0);

            return plainDataCount;
        }

        private void closeImpl() throws IOException {
            this.setSocketRunning(false);
            this.mCurrentWebSocket.onClosed();

            if (this.mSocketChannel != null) {
                this.mSocketChannel.close();
            }
            if (this.mSelector != null) {
                this.mSelector.wakeup();
            }
        }
    }

    /**
     * Private constructor.
     * @param nativePtr is the C++ pointer to the WebSocketBridge object.
     * @param uri is a server uri for WebSocket object.
     */
    private HTML5WebSocket(int nativePtr, String uri) {
        // This handler is for the main (UI) thread.
        super(Looper.getMainLooper());
        mNativePointer = nativePtr;
        // Create the message handler for this thread
        createWebCoreHandler();

        Thread th = null;
        try {
            mWebSocket = new WebSocket(this);
            th = mWebSocket.connect(new URI(uri));
        } catch (Exception e) {
            if (th != null) {
                th.interrupt();
            }
        }
    }

    /**
     * Message handler
     */
    private void createWebCoreHandler() {
        mWebCoreHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case WEB_SOCKET_CONNECTED: {
                        nativeOnWebSocketConnected(mNativePointer);
                        break;
                    }
                    case WEB_SOCKET_CLOSED: {
                        nativeOnWebSocketClosed(mNativePointer);
                        break;
                    }
                    case WEB_SOCKET_MESSAGE: {
                        ByteBuffer msgData = null;
                        do {
                            msgData = mWebSocket.getData();
                            if (msgData == null) {
                                break;
                            }
                            nativeOnWebSocketMessage(mNativePointer, msgData.array(), msgData.capacity());
                        } while (msg != null);
                        break;
                    }
                    case WEB_SOCKET_ERROR: {
                        nativeOnWebSocketError(mNativePointer);
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
        };
    }

     /**
     * Send data to web socket.
     * @param bytes is sened data.
     */
    public void send(byte[] bytes) {
        if (bytes == null) {
            return;
        }
        ByteBuffer data = ByteBuffer.allocate(bytes.length);
        data.put(bytes);
        try {
            mWebSocket.putWriteQueueData(data);
        } catch (InterruptedException e) {
            this.onError(e);
        }
        Message message = obtainMessage(WEB_SOCKET_SEND);
        sendMessage(message);
    }

    /**
     * Close web socket.
     */
    public void close() {
        Message message = obtainMessage(WEB_SOCKET_CLOSE);
        sendMessage(message);
    }

    /**
     * The factory for HTML5WebSocket instances.
     * @param uri is the URL that is requesting
     *
     * @return a new HTML5WebSocket object.
     * @hide
     */
    public static HTML5WebSocket getInstance(int nativePtr, String uri) {
        return new HTML5WebSocket(nativePtr, uri);
    }

    private native void nativeOnWebSocketConnected(int nativePointer);
    private native void nativeOnWebSocketClosed(int nativePointer);
    private native void nativeOnWebSocketMessage(int nativePointer, byte[] data, int length);
    private native void nativeOnWebSocketError(int nativePointer);
};

