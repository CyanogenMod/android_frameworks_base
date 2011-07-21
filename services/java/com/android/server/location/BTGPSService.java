/*
 * Copyright (C) 2011 Cuong Bui
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
package com.android.server.location;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

public class BTGPSService {
    private static final boolean D = true;
    private static final String TAG = "BTGPSService";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread = null;
    private ConnectedThread mConnectedThread = null;
    private final int mMaxNMEABuffer=4096;
    private final byte[] buffer = new byte[mMaxNMEABuffer];
    int bytes;
    private int refreshRate = 500;

    private int mState = 0;
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public synchronized void setRefreshRate(int r) {
        refreshRate = r;
    }

    public synchronized int getRefreshRate() {
        return refreshRate;
    }

    public BTGPSService(Handler h) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = h;
    }


    /**
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
        if (mState == STATE_NONE) {
            sendMessage(BTGpsLocationProvider.GPS_STATUS_UPDATE, 0, null);
        } else if (mState == STATE_CONNECTED) {
            sendMessage(BTGpsLocationProvider.GPS_STATUS_UPDATE, 1, null);
        }
    }

    /**
     * Return the current connection state. */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume() */
    public synchronized void start() {
        if (D) Log.d(TAG, "start");
        if (!mAdapter.isEnabled()) {
            setState(STATE_NONE);
            return;
        }
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        setState(STATE_LISTEN);
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     */
    public synchronized boolean connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);
        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
        return true;
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (D) Log.d(TAG, "connected with remote device: "
                + device.getName() + " at address " + device.getAddress());
        // Cancel the thread that completed the connection
        if (mConnectThread != null) mConnectThread = null;
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();
        setState(STATE_CONNECTED);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");
        if (mConnectThread != null) {
            if (D) Log.d(TAG, "Cancelling connect thread");
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            if (D) Log.d(TAG, "Cancelling connected thread");
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        if (D) Log.d(TAG, "Set state to None");
        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(out);
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = mmDevice.createRfcommSocketToServiceRecord(BT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            if (mmSocket == null) {
                Log.e(TAG, "Socket not ready. Aborting thread");
                return;
            }
            // Always cancel discovery because it will slow down a connection
            if ((mAdapter.isEnabled()) && (mAdapter.isDiscovering()))
                mAdapter.cancelDiscovery();
            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
                connected(mmSocket, mmDevice);
            } catch (IOException e) {
                Log.w(TAG, "connect failed. probably cancelled by other thread", e);
                return;
            } finally {
                // Reset the ConnectThread because we're done.
                synchronized (BTGPSService.this) {
                    if (D) Log.d(TAG, "Resetting mConnectThread");
                    mConnectThread = null;
                }
            }
        }

        public synchronized void cancel() {
            if (mmSocket == null) {
                Log.e(TAG, "Socket not ready. Aborting Cancel");
                return;
            }
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }


        public void run() {
            if (mmSocket == null || mmInStream == null) {
                Log.e(TAG, "Input stream or socket is null. Aborting thread");
                return;
            }
            if (D) Log.d(TAG, "BEGIN mConnectedThread");
            java.util.Arrays.fill(buffer, (byte) 0);
            int readTimeOut = getRefreshRate();
            BufferedInputStream reader = new BufferedInputStream(mmInStream);
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    if (reader.available() > 0) {
                        bytes = reader.read(buffer, 0, mMaxNMEABuffer);
                        Message msg = mHandler.obtainMessage(
                                BTGpsLocationProvider.GPS_DATA_AVAILABLE,buffer);
                        msg.arg1 = bytes;
                        mHandler.sendMessage(msg);
                    } 
                    try {
                        // get default sleep time
                        Thread.sleep(readTimeOut);
                    } catch (InterruptedException e) { }
                    
                    // Read from the InputStream
                    /*
                    bytes = 0;
                    int res = 0;
                    do {
                        if (mMaxNMEABuffer == bytes) {
                            Log.w(TAG,"Buffer size too small?");
                            break; // read max buffer!
                        }
                        res = mmInStream.read(buffer, bytes, mMaxNMEABuffer - bytes);
                        if (res != -1) bytes += res;
                    } while (res != -1);
                    */
                } catch (IOException e) {
                    Log.e(TAG, "disconnected. stopping services", e);
                    BTGPSService.this.stop();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            if (mmSocket == null) {
                Log.e(TAG, "Input stream null. Aborting Cacnel");
                return;
            }
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private void sendMessage(int message, int arg, Object obj) {
        Message m = Message.obtain(mHandler, message);
        m.arg1 = arg;
        m.obj = obj;
        mHandler.sendMessage(m);
    }
}
