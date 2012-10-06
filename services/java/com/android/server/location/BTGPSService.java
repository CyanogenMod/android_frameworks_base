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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BTGPSService {
    private static final boolean D = true;
    private static final String TAG = "BTGPSService";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private WatchdogThread mWatchdogThread = null;
    private ConnectThread mConnectThread = null;
    private ConnectedThread mConnectedThread = null;
    private final int mMaxNMEABuffer=4096;
    private final char[] buffer = new char[mMaxNMEABuffer];
    int bytes;
    private long refreshRate = 1000;
    private long lastActivity = 0;
    // MAX_ACTIVITY_TIMEOUT * refresh time window should have at least one activity.
    private int MAX_ACTIVITY_TIMEOUT = 5;
    // Maximum connect retry attempt
    private int MAX_RECONNECT_RETRIES = 5;
    // time window for one single connection (ms). socket connect timeout is around 12 sec
    private int MAX_CONNECT_TIMEOUT = 13000;
    // last connected device. is used to auto reconnect.
    private BluetoothDevice lastConnectedDevice=null;

    private int mState = 0;
    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    public synchronized void setRefreshRate(long r) {
        refreshRate = r;
    }

    public synchronized long getRefreshRate() {
        return refreshRate;
    }

    public BTGPSService(Handler h) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = h;
    }

    private void sendMessage(int message, int arg, Object obj) {
        Message m = Message.obtain(mHandler, message);
        m.arg1 = arg;
        m.obj = obj;
        mHandler.sendMessage(m);
    }

    private void handleFailedConnection() {
        if (getServiceState() != STATE_NONE) {
            if (D) Log.d(TAG, "Connection failed with status != 0. try to reconnect");
            connect(lastConnectedDevice);
        } else {
            if (D) Log.d(TAG, "Connection stopped with status = 0.");
        }
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
    public synchronized int getServiceState() {
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
        lastConnectedDevice = device;
        if (D) Log.d(TAG, "connect to: " + device);
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mWatchdogThread != null) {
            mWatchdogThread.cancel();
            mWatchdogThread = null;
        }
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        // Helper thread that monitors and retries to connect after time out
        mWatchdogThread = new WatchdogThread(device);
        mWatchdogThread.start();
        return true;
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket) {
        // reset connect thread
        if (mConnectThread != null) mConnectThread = null;

        // kill watchdog, since we are connected
        if (mWatchdogThread != null) {
            mWatchdogThread.cancel();
            mWatchdogThread = null;
        }
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
        if (D) Log.d(TAG, "Stopping btsvc, Set state to None");
        setState(STATE_NONE);

        if (mWatchdogThread != null) {
            if (D) Log.d(TAG, "Cancelling watchdog thread");
            mWatchdogThread.cancel();
            mWatchdogThread = null;
        }

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
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
        }

        private void closeSocket() {
            if (D) Log.d(TAG, getId()+":close socket");
            if (mmSocket == null) {
                Log.e(TAG, getId()+":Socket not ready. Aborting Close");
                return;
            }

            try {
                mmSocket.close();
                mmSocket = null;
            } catch (IOException e) {
                Log.e(TAG, getId()+":close() of connect " + mSocketType + " socket failed", e);
            }
        }

        public void run() {
            Log.i(TAG, getId() + ":begin mConnectThread");
            BluetoothSocket tmp = null;
            // Always cancel discovery because it will slow down a connection

            try {
                tmp = mmDevice.createRfcommSocketToServiceRecord(BT_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket create() failed", e);
                return;
            }
            mmSocket = tmp;
            // Make a connection to the BluetoothSocket
            if (mAdapter.isEnabled() && mAdapter.isDiscovering()) 
                mAdapter.cancelDiscovery();
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                if (D)  Log.d(TAG, getId() + ":Connecting to socket...");
                mmSocket.connect();
                if (D) Log.d(TAG, "connected with remote device: "
                        + mmDevice.getName() + " at address " + mmDevice.getAddress());
                connected(mmSocket);
            } catch (IOException e) {
                Log.w(TAG, getId() + ":connect failed.", e);
                return;
            }
        }

        public synchronized void cancel() {
            closeSocket();
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
        private boolean cancelled = false;

        private void closeSocket() {
            if (D) Log.d(TAG, getId()+":close socket");
            if (mmSocket == null) {
                Log.e(TAG, getId()+":Socket not ready. Aborting Close");
                return;
            }
            try {
                mmSocket.close();
                mmSocket = null;
            } catch (IOException e) {
                Log.e(TAG, getId()+": close() of connect socket failed", e);
            }
        }

        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, getId() + ":begin ConnectedThread");
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
            if (D) Log.d(TAG, getId() + ":BEGIN mConnectedThread");
            java.util.Arrays.fill(buffer, (char) ' ');
            // reset refresh rate to 1000
            refreshRate = 1000;
            lastActivity = 0;
            BufferedReader reader = new BufferedReader(new InputStreamReader(mmInStream));
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    if (reader.ready()) {
                        bytes = reader.read(buffer, 0, mMaxNMEABuffer);
                        Message msg = mHandler.obtainMessage(
                                BTGpsLocationProvider.GPS_DATA_AVAILABLE,buffer);
                        lastActivity = System.currentTimeMillis();
                        msg.arg1 = bytes;
                        mHandler.sendMessage(msg);
                    }
                    if (lastActivity != 0 && (System.currentTimeMillis() - lastActivity)  >
                            MAX_ACTIVITY_TIMEOUT*refreshRate) {
                        Log.w(TAG, getId() + ":BT activity timeout.");
                        closeSocket();
                        handleFailedConnection();
                        return;
                    }
                    try {
                        // get default sleep time
                        Thread.sleep(getRefreshRate());
                    } catch (InterruptedException e) {
                        if (cancelled) {
                            closeSocket();
                            return;
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, getId() + ":disconnected.", e);
                    closeSocket();
                    handleFailedConnection();
                    return;
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
                mmOutStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                if (mmInStream != null) {
                    mmInStream.close();
                    mmInStream = null;
                }
            } catch (Exception e) {
                Log.i(TAG, "Failed to close inputstream", e);
            }

            try {
                if (mmOutStream != null) {
                    mmOutStream.close();
                    mmOutStream = null;
                }
            } catch (Exception e) {
                Log.i(TAG, "Failed to close outputstream", e);
            }

            try {
                if (mmSocket == null) {
                    Log.e(TAG, "Input stream null. Aborting Cacnel");
                    return;
                }
                mmSocket.close();
                mmSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            } finally {
                cancelled = true;
                interrupt();
            }
        }
    }
    /*
     * Thread that starts the connection thread an monitors it.
     * Thread will be cancelled if timeot occurs
     */
    private class WatchdogThread extends Thread {
        private final BluetoothDevice btdevice;
        private int retries = 0;
        private boolean sleep = false;
        private boolean cancelled = false;

        public WatchdogThread(BluetoothDevice dev) {
            btdevice = dev;
        }

        public void run() {
            while(retries < MAX_RECONNECT_RETRIES) {
                if (mConnectThread != null) {
                    mConnectThread.cancel();
                    mConnectThread = null;
                }
                if (mConnectedThread != null) {
                    mConnectedThread.cancel();
                    mConnectedThread = null;
                }

                mConnectThread = new ConnectThread(btdevice);
                mConnectThread.start();
                setState(STATE_CONNECTING);
                // monitor connection and cancel if timeout
                if (D) Log.d(TAG, getId() + ":Waiting " + MAX_CONNECT_TIMEOUT
                        + " (ms) for service to connect...");
                try {
                    sleep = true;
                    Thread.sleep(MAX_CONNECT_TIMEOUT);
                    sleep = false;
                    if (D) Log.d(TAG, getId() + ":Connecting timeout.");
                } catch (InterruptedException e) {
                    if (D) Log.d(TAG, getId() + ":Watchdog interrupted. probably by cancel.");
                }
                if (getServiceState() == STATE_CONNECTED) {
                    if (D) Log.d(TAG, getId() + ":Connected. aborting watchdog");
                    return;
                }
                if (cancelled) {
                    if (D) Log.d(TAG, getId() + ":Cancelled. aborting watchdog");
                    return;
                }
                retries++;
            }
            // max timeout, so stopping service
            if (D) Log.d(TAG, getId() + ":Max connection retries exceeded. stopping services.");
            BTGPSService.this.stop();
        }

        public void cancel() {
            cancelled = true;
            if (sleep) interrupt();
        }
    }
}
