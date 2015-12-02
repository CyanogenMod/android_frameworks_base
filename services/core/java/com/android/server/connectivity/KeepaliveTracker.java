/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.connectivity;

import com.android.internal.util.HexDump;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.connectivity.KeepalivePacketData;
import com.android.server.connectivity.NetworkAgentInfo;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.PacketKeepalive;
import android.net.LinkAddress;
import android.net.NetworkAgent;
import android.net.NetworkUtils;
import android.net.util.IpUtils;
import android.os.Binder;
import android.os.IBinder;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.system.OsConstants;
import android.util.Log;
import android.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

import static android.net.ConnectivityManager.PacketKeepalive.*;
import static android.net.NetworkAgent.CMD_START_PACKET_KEEPALIVE;
import static android.net.NetworkAgent.CMD_STOP_PACKET_KEEPALIVE;
import static android.net.NetworkAgent.EVENT_PACKET_KEEPALIVE;

/**
 * Manages packet keepalive requests.
 *
 * Provides methods to stop and start keepalive requests, and keeps track of keepalives across all
 * networks. This class is tightly coupled to ConnectivityService. It is not thread-safe and its
 * methods must be called only from the ConnectivityService handler thread.
 */
public class KeepaliveTracker {

    private static final String TAG = "KeepaliveTracker";
    private static final boolean DBG = true;

    public static final String PERMISSION = android.Manifest.permission.PACKET_KEEPALIVE_OFFLOAD;

    /** Keeps track of keepalive requests. */
    private final HashMap <NetworkAgentInfo, HashMap<Integer, KeepaliveInfo>> mKeepalives =
            new HashMap<> ();
    private final Handler mConnectivityServiceHandler;

    public KeepaliveTracker(Handler handler) {
        mConnectivityServiceHandler = handler;
    }

    /**
     * Tracks information about a packet keepalive.
     *
     * All information about this keepalive is known at construction time except the slot number,
     * which is only returned when the hardware has successfully started the keepalive.
     */
    class KeepaliveInfo implements IBinder.DeathRecipient {
        // Bookkeping data.
        private final Messenger mMessenger;
        private final IBinder mBinder;
        private final int mUid;
        private final int mPid;
        private final NetworkAgentInfo mNai;

        /** Keepalive slot. A small integer that identifies this keepalive among the ones handled
          * by this network. */
        private int mSlot = PacketKeepalive.NO_KEEPALIVE;

        // Packet data.
        private final KeepalivePacketData mPacket;
        private final int mInterval;

        // Whether the keepalive is started or not.
        public boolean isStarted;

        public KeepaliveInfo(Messenger messenger, IBinder binder, NetworkAgentInfo nai,
                KeepalivePacketData packet, int interval) {
            mMessenger = messenger;
            mBinder = binder;
            mPid = Binder.getCallingPid();
            mUid = Binder.getCallingUid();

            mNai = nai;
            mPacket = packet;
            mInterval = interval;

            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        public NetworkAgentInfo getNai() {
            return mNai;
        }

        public String toString() {
            return new StringBuffer("KeepaliveInfo [")
                    .append(" network=").append(mNai.network)
                    .append(" isStarted=").append(isStarted)
                    .append(" ")
                    .append(IpUtils.addressAndPortToString(mPacket.srcAddress, mPacket.srcPort))
                    .append("->")
                    .append(IpUtils.addressAndPortToString(mPacket.dstAddress, mPacket.dstPort))
                    .append(" interval=" + mInterval)
                    .append(" data=" + HexDump.toHexString(mPacket.data))
                    .append(" uid=").append(mUid).append(" pid=").append(mPid)
                    .append(" ]")
                    .toString();
        }

        /** Sends a message back to the application via its PacketKeepalive.Callback. */
        void notifyMessenger(int slot, int err) {
            KeepaliveTracker.this.notifyMessenger(mMessenger, slot, err);
        }

        /** Called when the application process is killed. */
        public void binderDied() {
            // Not called from ConnectivityService handler thread, so send it a message.
            mConnectivityServiceHandler.obtainMessage(
                    NetworkAgent.CMD_STOP_PACKET_KEEPALIVE,
                    mSlot, PacketKeepalive.BINDER_DIED, mNai.network).sendToTarget();
        }

        void unlinkDeathRecipient() {
            if (mBinder != null) {
                mBinder.unlinkToDeath(this, 0);
            }
        }

        private int checkNetworkConnected() {
            if (!mNai.networkInfo.isConnectedOrConnecting()) {
                return ERROR_INVALID_NETWORK;
            }
            return SUCCESS;
        }

        private int checkSourceAddress() {
            // Check that we have the source address.
            for (InetAddress address : mNai.linkProperties.getAddresses()) {
                if (address.equals(mPacket.srcAddress)) {
                    return SUCCESS;
                }
            }
            return ERROR_INVALID_IP_ADDRESS;
        }

        private int checkInterval() {
            return mInterval >= 20 ? SUCCESS : ERROR_INVALID_INTERVAL;
        }

        private int isValid() {
            synchronized (mNai) {
                int error = checkInterval();
                if (error == SUCCESS) error = checkNetworkConnected();
                if (error == SUCCESS) error = checkSourceAddress();
                return error;
            }
        }

        void start(int slot) {
            int error = isValid();
            if (error == SUCCESS) {
                mSlot = slot;
                Log.d(TAG, "Starting keepalive " + mSlot + " on " + mNai.name());
                mNai.asyncChannel.sendMessage(CMD_START_PACKET_KEEPALIVE, slot, mInterval, mPacket);
            } else {
                notifyMessenger(NO_KEEPALIVE, error);
                return;
            }
        }

        void stop(int reason) {
            int uid = Binder.getCallingUid();
            if (uid != mUid && uid != Process.SYSTEM_UID) {
                if (DBG) {
                    Log.e(TAG, "Cannot stop unowned keepalive " + mSlot + " on " + mNai.network);
                }
            }
            if (isStarted) {
                Log.d(TAG, "Stopping keepalive " + mSlot + " on " + mNai.name());
                mNai.asyncChannel.sendMessage(CMD_STOP_PACKET_KEEPALIVE, mSlot);
            }
            // TODO: at the moment we unconditionally return failure here. In cases where the
            // NetworkAgent is alive, should we ask it to reply, so it can return failure?
            notifyMessenger(mSlot, reason);
            unlinkDeathRecipient();
        }
    }

    void notifyMessenger(Messenger messenger, int slot, int err) {
        Message message = Message.obtain();
        message.what = EVENT_PACKET_KEEPALIVE;
        message.arg1 = slot;
        message.arg2 = err;
        message.obj = null;
        try {
            messenger.send(message);
        } catch (RemoteException e) {
            // Process died?
        }
    }

    private  int findFirstFreeSlot(NetworkAgentInfo nai) {
        HashMap networkKeepalives = mKeepalives.get(nai);
        if (networkKeepalives == null) {
            networkKeepalives = new HashMap<Integer, KeepaliveInfo>();
            mKeepalives.put(nai, networkKeepalives);
        }

        // Find the lowest-numbered free slot. Slot numbers start from 1, because that's what two
        // separate chipset implementations independently came up with.
        int slot;
        for (slot = 1; slot <= networkKeepalives.size(); slot++) {
            if (networkKeepalives.get(slot) == null) {
                return slot;
            }
        }
        return slot;
    }

    public void handleStartKeepalive(Message message) {
        KeepaliveInfo ki = (KeepaliveInfo) message.obj;
        NetworkAgentInfo nai = ki.getNai();
        int slot = findFirstFreeSlot(nai);
        mKeepalives.get(nai).put(slot, ki);
        ki.start(slot);
    }

    public void handleStopAllKeepalives(NetworkAgentInfo nai, int reason) {
        HashMap <Integer, KeepaliveInfo> networkKeepalives = mKeepalives.get(nai);
        if (networkKeepalives != null) {
            for (KeepaliveInfo ki : networkKeepalives.values()) {
                ki.stop(reason);
            }
            networkKeepalives.clear();
            mKeepalives.remove(nai);
        }
    }

    public void handleStopKeepalive(NetworkAgentInfo nai, int slot, int reason) {
        String networkName = (nai == null) ? "(null)" : nai.name();
        HashMap <Integer, KeepaliveInfo> networkKeepalives = mKeepalives.get(nai);
        if (networkKeepalives == null) {
            Log.e(TAG, "Attempt to stop keepalive on nonexistent network " + networkName);
            return;
        }
        KeepaliveInfo ki = networkKeepalives.get(slot);
        if (ki == null) {
            Log.e(TAG, "Attempt to stop nonexistent keepalive " + slot + " on " + networkName);
            return;
        }
        ki.stop(reason);
        networkKeepalives.remove(slot);
        if (networkKeepalives.isEmpty()) {
            mKeepalives.remove(nai);
        }
    }

    public void handleCheckKeepalivesStillValid(NetworkAgentInfo nai) {
        HashMap <Integer, KeepaliveInfo> networkKeepalives = mKeepalives.get(nai);
        if (networkKeepalives != null) {
            ArrayList<Pair<Integer, Integer>> invalidKeepalives = new ArrayList<>();
            for (int slot : networkKeepalives.keySet()) {
                int error = networkKeepalives.get(slot).isValid();
                if (error != SUCCESS) {
                    invalidKeepalives.add(Pair.create(slot, error));
                }
            }
            for (Pair<Integer, Integer> slotAndError: invalidKeepalives) {
                handleStopKeepalive(nai, slotAndError.first, slotAndError.second);
            }
        }
    }

    public void handleEventPacketKeepalive(NetworkAgentInfo nai, Message message) {
        int slot = message.arg1;
        int reason = message.arg2;

        KeepaliveInfo ki = null;
        try {
            ki = mKeepalives.get(nai).get(slot);
        } catch(NullPointerException e) {}
        if (ki == null) {
            Log.e(TAG, "Event for unknown keepalive " + slot + " on " + nai.name());
            return;
        }

        if (reason == SUCCESS && !ki.isStarted) {
            // Keepalive successfully started.
            if (DBG) Log.d(TAG, "Started keepalive " + slot + " on " + nai.name());
            ki.isStarted = true;
            ki.notifyMessenger(slot, reason);
        } else {
            // Keepalive successfully stopped, or error.
            ki.isStarted = false;
            if (reason == SUCCESS) {
                if (DBG) Log.d(TAG, "Successfully stopped keepalive " + slot + " on " + nai.name());
            } else {
                if (DBG) Log.d(TAG, "Keepalive " + slot + " on " + nai.name() + " error " + reason);
            }
            handleStopKeepalive(nai, slot, reason);
        }
    }

    public void startNattKeepalive(NetworkAgentInfo nai, int intervalSeconds, Messenger messenger,
            IBinder binder, String srcAddrString, int srcPort, String dstAddrString, int dstPort) {
        if (nai == null) {
            notifyMessenger(messenger, NO_KEEPALIVE, ERROR_INVALID_NETWORK);
            return;
        }

        InetAddress srcAddress, dstAddress;
        try {
            srcAddress = NetworkUtils.numericToInetAddress(srcAddrString);
            dstAddress = NetworkUtils.numericToInetAddress(dstAddrString);
        } catch (IllegalArgumentException e) {
            notifyMessenger(messenger, NO_KEEPALIVE, ERROR_INVALID_IP_ADDRESS);
            return;
        }

        KeepalivePacketData packet;
        try {
            packet = KeepalivePacketData.nattKeepalivePacket(
                    srcAddress, srcPort, dstAddress, NATT_PORT);
        } catch (KeepalivePacketData.InvalidPacketException e) {
            notifyMessenger(messenger, NO_KEEPALIVE, e.error);
            return;
        }
        KeepaliveInfo ki = new KeepaliveInfo(messenger, binder, nai, packet, intervalSeconds);
        Log.d(TAG, "Created keepalive: " + ki.toString());
        mConnectivityServiceHandler.obtainMessage(
                NetworkAgent.CMD_START_PACKET_KEEPALIVE, ki).sendToTarget();
    }

    public void dump(IndentingPrintWriter pw) {
        pw.println("Packet keepalives:");
        pw.increaseIndent();
        for (NetworkAgentInfo nai : mKeepalives.keySet()) {
            pw.println(nai.name());
            pw.increaseIndent();
            for (int slot : mKeepalives.get(nai).keySet()) {
                KeepaliveInfo ki = mKeepalives.get(nai).get(slot);
                pw.println(slot + ": " + ki.toString());
            }
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
    }
}
