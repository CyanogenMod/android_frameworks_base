/*
*        Copyright (c) 2013, The Linux Foundation. All rights reserved.
*        Not a Contribution.
*
*        Copyright (C) 2007 The Android Open Source Project
*
*        Licensed under the Apache License, Version 2.0 (the "License");
*        you may not use this file except in compliance with the License.
*        You may obtain a copy of the License at
*
*        http://www.apache.org/licenses/LICENSE-2.0
*
*        Unless required by applicable law or agreed to in writing, software
*        distributed under the License is distributed on an "AS IS" BASIS,
*        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*        See the License for the specific language governing permissions and
*        limitations under the License.
*/

package com.android.server;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Messenger;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.net.RouteInfo;
import android.net.wifi.PPPOEConfig;
import android.net.wifi.PPPOEInfo;
import android.net.wifi.PPPOEInfo.Status;
import android.util.Log;
/**
 * Track the state of Wifi connectivity. All event handling is done here,
 * and all changes in connectivity state are initiated here.
 *
 * Wi-Fi now supports three modes of operation: Client, Soft Ap and Direct
 * In the current implementation, we do not support any concurrency and thus only
 * one of Client, Soft Ap or Direct operation is supported at any time.
 *
 * The WifiStateMachine supports Soft Ap and Client operations while WifiP2pService
 * handles Direct. WifiP2pService and WifiStateMachine co-ordinate to ensure only
 * one exists at a certain time.
 *
 * @hide
 */
public class PPPOEService {
    class NetdResponseCode {
        /* Keep in sync with system/netd/ResponseCode.h */
        public static final int InterfaceListResult       = 110;
        public static final int TetherInterfaceListResult = 111;
        public static final int TetherDnsFwdTgtListResult = 112;
        public static final int TtyListResult             = 113;

        public static final int CommandOkay               = 200;
        public static final int TetherStatusResult        = 210;
        public static final int IpFwdStatusResult         = 211;
        public static final int InterfaceGetCfgResult     = 213;
        public static final int SoftapStatusResult        = 214;
        public static final int InterfaceRxCounterResult  = 216;
        public static final int InterfaceTxCounterResult  = 217;
        public static final int InterfaceRxThrottleResult = 218;
        public static final int InterfaceTxThrottleResult = 219;
        public static final int QuotaCounterResult        = 220;
        public static final int TetheringStatsResult      = 221;
        public static final int DnsProxyQueryResult       = 222;
        public static final int V6RtrAdvResult            = 224;

        public static final int OperationFailed           = 400;

        public static final int InterfaceChange           = 600;
        public static final int BandwidthControl          = 601;
    }

    public static final String TAG = "PPPOEService";

    public static final int CMD_START_PPPOE               = 0;
    public static final int CMD_STOP_PPPOE                = 1;

    /*
     * below ACTION and EXTRA definition are the requirement of China Telecom
     * can not be changed
     */
    public static final String ACTION_PPPOE_COMPLETE = "android.net.wifi.PPPOE_COMPLETED_ACTION";
    public static final String ACTION_PPPOE_STATE_CHANGED = "android.net.wifi.PPPOE_STATE_CHANGED";

    public static final String EXTRA_PPPOE_RESULT_STATUS = "pppoe_result_status";
    public static final String EXTRA_PPPOE_RESULT_ERROR_CODE = "pppoe_result_error_code";
    public static final String EXTRA_PPPOE_STATE = "pppoe_state";


    public static final String NETD_TAG = "PPPOEService_Netd";
    public static final int PPPOEEXIT = 666;
    public static final String PPPOE_MODULE = "pppoe";
    public static final String PROP_NET_DNS1 ="net.dns1";
    public static final String PROP_NET_DNS2 ="net.dns2";

    /**
     * Binder context for this service
     */
    private Context mContext;

    /**
     * connector object for communicating with netd
     */
    private NativeDaemonConnector mConnector;

    private Status mPppoeStatus = Status.OFFLINE;
    private boolean mDoCommand = false;
    private long mConnectedtime;

    private Thread mThread;
    //public static PPPOEService instance
    /**
     * Constructs a new PPPOEService instance
     *
     * @param context  Binder context for this service
     */
    public PPPOEService(Context context) {
        mContext = context;

        mConnector = new NativeDaemonConnector(
                new NetdCallbackReceiver(), "netd", 10, NETD_TAG, 160);
        mThread = new Thread(mConnector, NETD_TAG);
        mThread.start();
    }

    /**
     * Netd Callback handling
     */
    private class NetdCallbackReceiver implements INativeDaemonConnectorCallbacks {
        @Override
        public void onDaemonConnected() {
        }

        @Override
        public boolean onEvent(int code, String raw, String[] cooked) {
            Log.i(TAG, "NetdCallbackReceiver onEvent " + code + raw);
            switch (code) {
                case NetdResponseCode.InterfaceChange:
                    /*
                     * a network interface change occured
                     * Format: "NNN Iface added <name>"
                     *         "NNN Iface removed <name>"
                     *         "NNN Iface changed <name> <up/down>"
                     *         "NNN Iface linkstatus <name> <up/down>"
                     */
                    if (cooked.length < 4 || !cooked[1].equals("Iface")) {
                        throw new IllegalStateException(
                                String.format("Invalid event from daemon (%s)", raw));
                    }
                    if (cooked[2].equals("added")) {
                        return true;
                    } else if (cooked[2].equals("removed")) {
                        return true;
                    } else if (cooked[2].equals("changed") && cooked.length == 5) {
                        return true;
                    } else if (cooked[2].equals("linkstate") && cooked.length == 5) {
                        if(cooked[3].startsWith("ppp")) {
                            if(cooked[4].equals("up")) {
                                if(mPppoeStatus != Status.ONLINE) {
                                    setRouteAndDNS(cooked[3]);
                                    mPppoeStatus = Status.ONLINE;
                                    mConnectedtime = System.currentTimeMillis();
                                    notifyStatusChanged("PPPOE_STATE_CONNECTED");
                                    if(mDoCommand) {
                                        sendCommandComplete("SUCCESS", "0");
                                        mDoCommand = false;
                                    }
                                }
                            } else {
                                if(mPppoeStatus == Status.ONLINE) {
                                    mPppoeStatus = Status.OFFLINE;
                                    mConnectedtime = 0;
                                    notifyStatusChanged("PPPOE_STATE_DISCONNECTED");
                                }
                            }
                        }
                        return true;
                    }
                    throw new IllegalStateException(
                            String.format("Invalid event from daemon (%s)", raw));
                case PPPOEEXIT:
                    /*
                     * pppoe process exit occured
                     * Format: "NNN pppoe exited error code <errncode>"
                     */
                    if (cooked.length > 5 && PPPOE_MODULE.equals(cooked[1])) {
                        if(mDoCommand) {
                            String errCode = cooked[5];
                            Log.i(TAG, "pppoeExit mDoCommand errcode is " + errCode);
                            sendCommandComplete("FAILURE", errCode);
                            mDoCommand = false;
                        }
                        mPppoeStatus = Status.OFFLINE;
                        mConnectedtime = 0;
                        notifyStatusChanged("PPPOE_STATE_DISCONNECTED");
                    }
                    return true;
                default: break;
            }
            return false;
        }
    }

    /**
     * Get a reference to handler. This is used by a client to establish
     * an AsyncChannel communication with PPPOEService
     */
    public Messenger getMessenger() {
        return new Messenger(mMainHandler);
    }

    private final Handler mMainHandler = new Handler(){

        public void handleMessage(Message msg) {
            Log.i(TAG, "handleMessage msg is " + msg);
            switch(msg.what) {
                case CMD_START_PPPOE:
                    PPPOEConfig config = (PPPOEConfig) msg.obj;
                    startPPPOE(config);
                    break;
                case CMD_STOP_PPPOE:
                    stopPPPOE();
                    break;
                default:
                    break;
            }
        }

    };


    private void startPPPOE(PPPOEConfig config) {
        NativeDaemonEvent event = null;
        try {
            mPppoeStatus = Status.CONNECTING;
            notifyStatusChanged("PPPOE_STATE_CONNECTING");
            event = mConnector.execute(PPPOE_MODULE, "start", config.username, config.password,
                    config.interf, Integer.toString(config.lcp_echo_interval),
                    Integer.toString(config.lcp_echo_failure),
                    Integer.toString(config.mtu), Integer.toString(config.mru),
                    Integer.toString(config.timeout), Integer.toString(config.MSS));
            mDoCommand = true;
       } catch (NativeDaemonConnectorException e) {
           Log.wtf(TAG, "problem start pppoe", e);
       }
       if(event != null) {
           Log.i(TAG, "startPPPOE " + event.getRawEvent());
           String cooked[] = event.unescapeArgs(event.getRawEvent());
           Log.i(TAG, "cooked.length is " + cooked.length + "cooked[4] is " + cooked[4]);
           if(event.getCode() == NetdResponseCode.CommandOkay) {
               if(cooked.length == 6 && cooked[4].equals("failed")) {
                   String errno = cooked[5];
                   //sendbroadcast();
                   if(errno.equals("16")) {
                       mPppoeStatus = Status.ONLINE;
                       sendCommandComplete("ALREADY_ONLINE", errno);
                   }
               }
           }
       }
    }

    private void stopPPPOE() {
        //sendBroadcast();
        notifyStatusChanged("PPPOE_STATE_DISCONNECTING");
        try {
            mConnector.execute(PPPOE_MODULE, "stop");
       } catch (NativeDaemonConnectorException e) {
           Log.wtf(TAG, "problem stop pppoe", e);
       }
    }

    public PPPOEInfo getPPPOEInfo() {
        return new PPPOEInfo(mPppoeStatus, mConnectedtime);
    }

    private void notifyStatusChanged(String state) {
        Intent intent = new Intent(ACTION_PPPOE_STATE_CHANGED);
        intent.putExtra(EXTRA_PPPOE_STATE, state);

        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendCommandComplete(String status, String errno) {
        Intent intent = new Intent(ACTION_PPPOE_COMPLETE);
        intent.putExtra(EXTRA_PPPOE_RESULT_STATUS, status);
        intent.putExtra(EXTRA_PPPOE_RESULT_ERROR_CODE, errno);

        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void setRouteAndDNS(String iface) {
        //set ppp0 route as default.
        String gateway = null;
        INetworkManagementService mNwService;
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNwService = INetworkManagementService.Stub.asInterface(b);
        try {
            RouteInfo[] ris = mNwService.getRoutes(iface);
            for(RouteInfo ri : ris) {
                if(ri != null) {
                    gateway = ri.getDestination().getAddress().getHostAddress();
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "error: " + e);
        }
        Log.i(TAG, "setRouteAndDNS iface is " + iface + " gateway is " + gateway);
        try{
            mConnector.execute(PPPOE_MODULE, "route", "setdefault", iface, gateway);
        } catch(NativeDaemonConnectorException e) {
            Log.wtf(TAG, "problem set ppp route", e);
        }

        //set ppp0 dns.
        String pppDNS1Property = "net." + iface + ".dns1";
        String pppDNS2Property = "net." + iface + ".dns2";
        String pppDns1 = SystemProperties.get(pppDNS1Property);
        String pppDns2 = SystemProperties.get(pppDNS2Property);

        Log.i(TAG, "setRouteAndDNS prop " + pppDNS1Property + ": " + pppDns1
                + "prop " + pppDNS2Property + ": " + pppDns2);

        try {
            mNwService.setDnsServersForInterface(iface, new String[] {pppDns1, pppDns2}, null);
            mNwService.setDefaultInterfaceForDns(iface);
        } catch (RemoteException e) {
            Log.e(TAG, "error: " + e);
        }

        SystemProperties.set(PROP_NET_DNS1, pppDns1);
        SystemProperties.set(PROP_NET_DNS2, pppDns2);
    }
}
