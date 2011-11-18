/*
 * Copyright 2007, The Android Open Source Project
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
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

package com.android.server;

import android.net.LocalSocketAddress;
import android.net.LocalSocket;
import android.os.Environment;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Config;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;
import java.net.Socket;

/**
 * @hide
 */

final class HDMIListener implements Runnable {

    private static final String TAG = "HDMIListener";

    // socket name for connecting to hdmi
    private static final String HDMI_SOCKET = "hdmid";

    // hdmi commands
    private static final String HDMI_CMD_ENABLE_HDMI = "enable_hdmi";
    private static final String HDMI_CMD_DISABLE_HDMI = "disable_hdmi";
    private static final String HDMI_CMD_CHANGE_MODE = "change_mode: ";
    private static final String HDMI_CMD_SET_ASWIDTH = "set_aswidth: ";
    private static final String HDMI_CMD_SET_ASHEIGHT = "set_asheight: ";
    private static final String HDMI_CMD_HPDOPTION = "hdmi_hpd: ";

    // hdmi events
    private static final String HDMI_EVT_CONNECTED = "hdmi_connected";
    private static final String HDMI_EVT_DISCONNECTED = "hdmi_disconnected";
    private static final String HDMI_EVT_START = "hdmi_listner_started";
    private static final String HDMI_EVT_NO_BROADCAST_ONLINE = "hdmi_no_broadcast_online";
    private static final String HDMI_EVT_AUDIO_ON = "hdmi_audio_on";
    private static final String HDMI_EVT_AUDIO_OFF = "hdmi_audio_off";

    private HDMIService mService;
    private DataOutputStream mOutputStream;
    private boolean mHDMIConnected = false;
    private boolean mHDMIEnabled = false;
    // Broadcast on HDMI connected
    private boolean mOnlineBroadCast = true;
    private int[] mEDIDs = new int[0];

    HDMIListener(HDMIService service) {
        mService = service;
    }

    private void handleEvent(String event) {
        Log.e(TAG, "handleEvent '" + event + "'");

        if (event.startsWith(HDMI_EVT_CONNECTED)) {
            String[] ids = event.substring(HDMI_EVT_CONNECTED.length() + 2).split(",");
            mEDIDs = new int[ids.length];
            for (int i = 0; i < mEDIDs.length; ++i) {
                try {
                    mEDIDs[i] = Integer.parseInt(ids[i].trim());
                } catch (NumberFormatException ex) {
                    Log.e(TAG, "NumberFormatException in handleEvent", ex);
                }
            }
            mHDMIConnected = true;
            mService.notifyHDMIConnected(mEDIDs);
        } else if (event.startsWith(HDMI_EVT_DISCONNECTED)) {
            mHDMIConnected = false;
            mService.notifyHDMIDisconnected();
        } else if (event.startsWith(HDMI_EVT_AUDIO_ON)) {
            // Notify HDMIAudio on
            mService.notifyHDMIAudioOn();
        } else if (event.startsWith(HDMI_EVT_AUDIO_OFF)) {
            // Notify HDMIAudio off
            mService.notifyHDMIAudioOff();
        } else if (event.startsWith(HDMI_EVT_NO_BROADCAST_ONLINE)) {
            // do not broadcast on connect event
            mOnlineBroadCast = false;
        }
    }

    private void writeCommand(String command, String argument) {
        synchronized (this) {
            if (mOutputStream == null) {
                Log.e(TAG, "No connection to hdmi daemon");
            } else {
                StringBuilder builder = new StringBuilder(command);
                if (argument != null) {
                    builder.append(argument);
                }
                builder.append('\0');

                try {
                    mOutputStream.write(builder.toString().getBytes());
                    mOutputStream.flush();
                    Log.e(TAG, "writeCommand: '"
                        + builder.toString().substring(0, builder.length()-1) + "'");
                } catch (IOException ex) {
                    Log.e(TAG, "IOException in writeCommand", ex);
                }
            }
        }
        Thread.yield();
    }

    private void listenToSocket() {
       LocalSocket socket = null;

        try {
            socket = new LocalSocket();
            LocalSocketAddress address = new LocalSocketAddress(HDMI_SOCKET,
                    LocalSocketAddress.Namespace.RESERVED);

            socket.connect(address);

            InputStream inputStream = socket.getInputStream();
            mOutputStream = new DataOutputStream(socket.getOutputStream());

            /*
             * All available messages in the socket are read into the buffer.
             * If Socket contians more number of messages whose total size
             * is greater than buffer size then last message may read partially.
             * Partial messages are not processed.
             * This issue has to be fixed.
             */
            byte[] buffer = new byte[512];

            writeCommand(HDMI_EVT_START, null);
            while (true) {
                int count = inputStream.read(buffer);
                if (count < 0) break;

                int start = 0;
                for (int i = 0; i < count; i++) {
                    if (buffer[i] == 0) {
                        String event = new String(buffer, start, i - start);
                        handleEvent(event);
                        start = i + 1;
                    }
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "Could not open listner socket");
        }

        synchronized (this) {
            if (mOutputStream != null) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    Log.w(TAG, "IOException closing output stream");
                }

                mOutputStream = null;
            }
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ex) {
            Log.w(TAG, "IOException closing socket");
        }

        Log.d(TAG, "Failed to connect to hdmi daemon", new IllegalStateException());
    }

    /**
     * Main loop for HDMIListener thread.
     */
    public void run() {
        try {
            listenToSocket();
        } catch (Throwable t) {
            // catch all Throwables so we don't bring down the system process
            Log.d(TAG, "Fatal error " + t + " in HDMIListener thread!");
        }
    }

    boolean isHDMIConnected() {
        return mHDMIConnected;
    }
    // returns true if we need to broadcast for Audio on cable connect
    boolean getOnlineBroadcast() {
        return mOnlineBroadCast;
    }

    public void enableHDMIOutput(boolean hdmiEnable) {
        if (mHDMIEnabled == hdmiEnable) {
            Log.d(TAG, "enableHDMIOutput ignored, unchanged!");
            return;
        }
        if (hdmiEnable) {
            writeCommand(HDMI_CMD_ENABLE_HDMI, null);
            mHDMIEnabled = true;
        }
        else {
            writeCommand(HDMI_CMD_DISABLE_HDMI, null);
            mHDMIEnabled = false;
        }
    }

    public void changeDisplayMode(int mode) {
        writeCommand(HDMI_CMD_CHANGE_MODE, new Integer(mode).toString());
    }

    public void setActionsafeWidthRatio(float asWidthRatio){
        writeCommand(HDMI_CMD_SET_ASWIDTH, new Float(asWidthRatio).toString());
    }

    public void setActionsafeHeightRatio(float asHeightRatio){
        writeCommand(HDMI_CMD_SET_ASHEIGHT, new Float(asHeightRatio).toString());
    }

    public void setHPD(boolean hpdOption) {
        writeCommand(HDMI_CMD_HPDOPTION, new Integer(hpdOption ? 1 : 0).toString());
    }
}
