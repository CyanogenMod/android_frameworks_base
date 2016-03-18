/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.INetworkStatsService;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiDevice;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.server.IoThread;
import com.android.server.net.BaseNetworkObserver;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_CHANGED_ACTION;


/**
 * @hide
 *
 * Timeout
 *
 * TODO - look for parent classes and code sharing
 */
public class Tethering extends BaseNetworkObserver {

    private Context mContext;
    private final static String TAG = "Tethering";
    private final static boolean DBG = true;
    private final static boolean VDBG = false;

    // TODO - remove both of these - should be part of interface inspection/selection stuff
    private String[] mTetherableUsbRegexs;
    private String[] mTetherableWifiRegexs;
    private String[] mTetherableBluetoothRegexs;
    private Collection<Integer> mUpstreamIfaceTypes;

    // used to synchronize public access to members
    private Object mPublicSync;

    private static final Integer MOBILE_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE);
    private static final Integer HIPRI_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE_HIPRI);
    private static final Integer DUN_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE_DUN);

    // if we have to connect to mobile, what APN type should we use?  Calculated by examining the
    // upstream type list and the DUN_REQUIRED secure-setting
    private int mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_NONE;

    private final INetworkManagementService mNMService;
    private final INetworkStatsService mStatsService;
    private Looper mLooper;

    private HashMap<String, TetherInterfaceSM> mIfaces; // all tethered/tetherable ifaces

    private BroadcastReceiver mStateReceiver;

    private static final String USB_NEAR_IFACE_ADDR      = "192.168.42.129";
    private static final int USB_PREFIX_LENGTH        = 24;

    // USB is  192.168.42.1 and 255.255.255.0
    // Wifi is 192.168.43.1 and 255.255.255.0
    // BT is limited to max default of 5 connections. 192.168.44.1 to 192.168.48.1
    // with 255.255.255.0
    // P2P is 192.168.49.1 and 255.255.255.0

    private String[] mDhcpRange;
    private static final String[] DHCP_DEFAULT_RANGE = {
        "192.168.42.2", "192.168.42.254", "192.168.43.2", "192.168.43.254",
        "192.168.44.2", "192.168.44.254", "192.168.45.2", "192.168.45.254",
        "192.168.46.2", "192.168.46.254", "192.168.47.2", "192.168.47.254",
        "192.168.48.2", "192.168.48.254", "192.168.49.2", "192.168.49.254",
    };

    private String[] mDefaultDnsServers;
    private static final String DNS_DEFAULT_SERVER1 = "8.8.8.8";
    private static final String DNS_DEFAULT_SERVER2 = "8.8.4.4";

    private StateMachine mTetherMasterSM;

    private Notification.Builder mTetheredNotificationBuilder;
    private int mLastNotificationId;

    private boolean mRndisEnabled;       // track the RNDIS function enabled state
    private boolean mUsbTetherRequested; // true if USB tethering should be started
                                         // when RNDIS is enabled

    // Once STA established connection to hostapd, it will be added
    // to mL2ConnectedDeviceMap. Then after deviceinfo update from dnsmasq,
    // it will be added to mConnectedDeviceMap
    private HashMap<String, WifiDevice> mL2ConnectedDeviceMap = new HashMap<String, WifiDevice>();
    private HashMap<String, WifiDevice> mConnectedDeviceMap = new HashMap<String, WifiDevice>();
    private static final String dhcpLocation = "/data/misc/dhcp/dnsmasq.leases";

    // Device name polling interval(ms) and max times
    private static final int DNSMASQ_POLLING_INTERVAL = 1000;
    private static final int DNSMASQ_POLLING_MAX_TIMES = 10;

    private long mWiFiApInactivityTimeout;
    private final Handler mHandler;

    public Tethering(Context context, INetworkManagementService nmService,
            INetworkStatsService statsService, Looper looper) {
        mContext = context;
        mNMService = nmService;
        mStatsService = statsService;
        mLooper = looper;
        mHandler = new Handler(mLooper);

        mPublicSync = new Object();

        mIfaces = new HashMap<String, TetherInterfaceSM>();

        // make our own thread so we don't anr the system
        mLooper = IoThread.get().getLooper();
        mTetherMasterSM = new TetherMasterSM("TetherMaster", mLooper);
        mTetherMasterSM.start();

        mStateReceiver = new StateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_STATE);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(WIFI_AP_STATE_CHANGED_ACTION);

        mContext.registerReceiver(mStateReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNSHARED);
        filter.addDataScheme("file");
        mContext.registerReceiver(mStateReceiver, filter);

        mDhcpRange = context.getResources().getStringArray(
                com.android.internal.R.array.config_tether_dhcp_range);
        if ((mDhcpRange.length == 0) || (mDhcpRange.length % 2 ==1)) {
            mDhcpRange = DHCP_DEFAULT_RANGE;
        }

        // load device config info
        updateConfiguration();

        // TODO - remove and rely on real notifications of the current iface
        mDefaultDnsServers = new String[2];
        mDefaultDnsServers[0] = DNS_DEFAULT_SERVER1;
        mDefaultDnsServers[1] = DNS_DEFAULT_SERVER2;
    }

    // We can't do this once in the Tethering() constructor and cache the value, because the
    // CONNECTIVITY_SERVICE is registered only after the Tethering() constructor has completed.
    private ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    void updateConfiguration() {
        String[] tetherableUsbRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_usb_regexs);
        String[] tetherableWifiRegexs;
        String[] tetherableBluetoothRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_bluetooth_regexs);

        if (SystemProperties.getInt("persist.fst.rate.upgrade.en", 0) == 1) {
            tetherableWifiRegexs = new String[] {"bond0"};
        } else {
            tetherableWifiRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_wifi_regexs);
        }

        int ifaceTypes[] = mContext.getResources().getIntArray(
                com.android.internal.R.array.config_tether_upstream_types);
        Collection<Integer> upstreamIfaceTypes = new ArrayList();
        for (int i : ifaceTypes) {
            upstreamIfaceTypes.add(new Integer(i));
        }

        synchronized (mPublicSync) {
            mTetherableUsbRegexs = tetherableUsbRegexs;
            mTetherableWifiRegexs = tetherableWifiRegexs;
            mTetherableBluetoothRegexs = tetherableBluetoothRegexs;
            mUpstreamIfaceTypes = upstreamIfaceTypes;
        }

        // check if the upstream type list needs to be modified due to secure-settings
        checkDunRequired();
    }

    public void interfaceStatusChanged(String iface, boolean up) {
        if (VDBG) Log.d(TAG, "interfaceStatusChanged " + iface + ", " + up);
        boolean found = false;
        boolean usb = false;
        synchronized (mPublicSync) {
            if (isWifi(iface)) {
                found = true;
            } else if (isUsb(iface)) {
                found = true;
                usb = true;
            } else if (isBluetooth(iface)) {
                found = true;
            }
            if (found == false) return;

            TetherInterfaceSM sm = mIfaces.get(iface);
            if (up) {
                if (sm == null) {
                    sm = new TetherInterfaceSM(iface, mLooper, usb);
                    mIfaces.put(iface, sm);
                    sm.start();
                    if (isWifi(iface)) {
                        // check if the user has specified an inactivity timeout for wifi AP and
                        // if so schedule the timeout
                        final WifiManager wm =
                                (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                        final WifiConfiguration apConfig = wm.getWifiApConfiguration();
                        mWiFiApInactivityTimeout =
                                apConfig != null ? apConfig.wifiApInactivityTimeout : 0;
                        if (mWiFiApInactivityTimeout > 0 && mL2ConnectedDeviceMap.size() == 0) {
                            scheduleInactivityTimeout();
                        }
                    }
                }
            } else {
                if (isUsb(iface)) {
                    // ignore usb0 down after enabling RNDIS
                    // we will handle disconnect in interfaceRemoved instead
                    if (VDBG) Log.d(TAG, "ignore interface down for " + iface);
                } else if (sm != null) {
                    sm.sendMessage(TetherInterfaceSM.CMD_INTERFACE_DOWN);
                    mIfaces.remove(iface);
                    if (isWifi(iface)) {
                        cancelInactivityTimeout();
                    }
                }
            }
        }
    }

    public void interfaceLinkStateChanged(String iface, boolean up) {
        if (VDBG) Log.d(TAG, "interfaceLinkStateChanged " + iface + ", " + up);
        interfaceStatusChanged(iface, up);
    }

    private boolean isUsb(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableUsbRegexs) {
                if (iface.matches(regex)) return true;
            }
            return false;
        }
    }

    public boolean isWifi(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableWifiRegexs) {
                if (iface.matches(regex)) return true;
            }
            return false;
        }
    }

    public boolean isBluetooth(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableBluetoothRegexs) {
                if (iface.matches(regex)) return true;
            }
            return false;
        }
    }

    public void interfaceAdded(String iface) {
        if (VDBG) Log.d(TAG, "interfaceAdded " + iface);
        boolean found = false;
        boolean usb = false;
        synchronized (mPublicSync) {
            if (isWifi(iface)) {
                found = true;
            }
            if (isUsb(iface)) {
                found = true;
                usb = true;
            }
            if (isBluetooth(iface)) {
                found = true;
            }
            if (found == false) {
                if (VDBG) Log.d(TAG, iface + " is not a tetherable iface, ignoring");
                return;
            }

            TetherInterfaceSM sm = mIfaces.get(iface);
            if (sm != null) {
                if (VDBG) Log.d(TAG, "active iface (" + iface + ") reported as added, ignoring");
                return;
            }
            sm = new TetherInterfaceSM(iface, mLooper, usb);
            mIfaces.put(iface, sm);
            sm.start();
        }
    }

    public void interfaceRemoved(String iface) {
        if (VDBG) Log.d(TAG, "interfaceRemoved " + iface);
        synchronized (mPublicSync) {
            TetherInterfaceSM sm = mIfaces.get(iface);
            if (sm == null) {
                if (VDBG) {
                    Log.e(TAG, "attempting to remove unknown iface (" + iface + "), ignoring");
                }
                return;
            }
            sm.sendMessage(TetherInterfaceSM.CMD_INTERFACE_DOWN);
            mIfaces.remove(iface);
        }
    }

    public List<WifiDevice> getTetherConnectedSta() {
        Iterator it;
        List<WifiDevice> TetherConnectedStaList = new ArrayList<WifiDevice>();

        if (mContext.getResources().getBoolean(com.android.internal.R.bool.config_softap_extention)) {
            it = mConnectedDeviceMap.keySet().iterator();
            while(it.hasNext()) {
                String key = (String)it.next();
                WifiDevice device = (WifiDevice)mConnectedDeviceMap.get(key);
                if (VDBG) {
                    Log.d(TAG, "getTetherConnectedSta: addr=" + key + " name=" + device.deviceName);
                }
                TetherConnectedStaList.add(device);
            }
        }

        return TetherConnectedStaList;
    }

    private void sendTetherConnectStateChangedBroadcast() {
        if (!getConnectivityManager().isTetheringSupported()) return;

        Intent broadcast = new Intent(ConnectivityManager.TETHER_CONNECT_STATE_CHANGED);
        broadcast.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING |
        Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);

        mContext.sendStickyBroadcastAsUser(broadcast, UserHandle.ALL);
    }

    private boolean readDeviceInfoFromDnsmasq(WifiDevice device) {
        boolean result = false;
        FileInputStream fstream = null;
        String line;

        try {
            fstream = new FileInputStream(dhcpLocation);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            while ((null != (line = br.readLine())) && (line.length() != 0)) {
                String[] fields = line.split(" ");

                // 949295 00:0a:f5:6a:bf:70 192.168.43.32 android-93de88df9ec61bac *
                if (fields.length > 3) {
                    String addr = fields[1];
                    String name = fields[3];

                    if (addr.equals(device.deviceAddress)) {
                        device.deviceName = name;
                        result = true;
                        break;
                    }
                }
            }
        } catch (IOException ex) {
            Log.e(TAG, "readDeviceNameFromDnsmasq: " + ex);
        } finally {
            if (fstream != null) {
                try {
                    fstream.close();
                } catch (IOException ex) {}
            }
        }

        return result;
    }

    private final Runnable mDisableWifiApRunnable = new Runnable() {
        @Override
        public void run() {
            if (VDBG) Log.d(TAG, "Turning off hotpost due to inactivity");
            final WifiManager wifiManager =
                    (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiApEnabled(null, false);
        }
    };

    private void scheduleInactivityTimeout() {
        if (mWiFiApInactivityTimeout > 0) {
            if (VDBG) Log.d(TAG, "scheduleInactivityTimeout: " + mWiFiApInactivityTimeout);
            mHandler.removeCallbacks(mDisableWifiApRunnable);
            mHandler.postDelayed(mDisableWifiApRunnable, mWiFiApInactivityTimeout);
        }
    }

    private void cancelInactivityTimeout() {
        if (VDBG) Log.d(TAG, "cancelInactivityTimeout");
        mHandler.removeCallbacks(mDisableWifiApRunnable);
    }

    /*
     * DnsmasqThread is used to read the Device info from dnsmasq.
     */
    private static class DnsmasqThread extends Thread {
        private final Tethering mTethering;
        private int mInterval;
        private int mMaxTimes;
        private WifiDevice mDevice;

        public DnsmasqThread(Tethering tethering, WifiDevice device,
            int interval, int maxTimes) {
            super("Tethering");
            mTethering = tethering;
            mInterval = interval;
            mMaxTimes = maxTimes;
            mDevice = device;
        }

        public void run() {
            boolean result = false;

            try {
                while (mMaxTimes > 0) {
                    result = mTethering.readDeviceInfoFromDnsmasq(mDevice);
                    if (result) {
                        if (DBG) Log.d(TAG, "Successfully poll device info for " + mDevice.deviceAddress);
                        break;
                    }

                    mMaxTimes --;
                    Thread.sleep(mInterval);
                }
            } catch (Exception ex) {
                result = false;
                Log.e(TAG, "Pulling " + mDevice.deviceAddress +  "error" + ex);
            }

            if (!result) {
                if (DBG) Log.d(TAG, "Pulling timeout, suppose STA uses static ip " + mDevice.deviceAddress);
            }

            // When STA uses static ip, device info will be unavaiable from dnsmasq,
            // thus no matter the result is success or failure, we will broadcast the event.
            // But if the device is not in L2 connected state, it means the hostapd connection is
            // disconnected before dnsmasq get device info, so in this case, don't broadcast
            // connection event.
            WifiDevice other = mTethering.mL2ConnectedDeviceMap.get(mDevice.deviceAddress);
            if (other != null && other.deviceState == WifiDevice.CONNECTED) {
                mTethering.mConnectedDeviceMap.put(mDevice.deviceAddress, mDevice);
                mTethering.sendTetherConnectStateChangedBroadcast();
            } else {
                if (DBG) Log.d(TAG, "Device " + mDevice.deviceAddress + "already disconnected, ignoring");
            }
        }
    }

    public void interfaceMessageRecevied(String message) {
        // if softap extension feature not enabled, do nothing
        if (!mContext.getResources().getBoolean(com.android.internal.R.bool.config_softap_extention)) {
            return;
        }

        if (DBG) Log.d(TAG, "interfaceMessageRecevied: message=" + message);

        try {
            WifiDevice device = new WifiDevice(message);

            if (device.deviceState == WifiDevice.CONNECTED) {
                mL2ConnectedDeviceMap.put(device.deviceAddress, device);

                // When hostapd reported STA-connection event, it is possible that device
                // info can't fetched from dnsmasq, then we start a thread to poll the
                // device info, the thread will exit after device info avaiable.
                // For static ip case, dnsmasq don't hold the device info, thus thread
                // will exit after a timeout.
                if (readDeviceInfoFromDnsmasq(device)) {
                    mConnectedDeviceMap.put(device.deviceAddress, device);
                    sendTetherConnectStateChangedBroadcast();
                } else {
                    if (DBG) Log.d(TAG, "Starting poll device info for " + device.deviceAddress);
                    new DnsmasqThread(this, device,
                        DNSMASQ_POLLING_INTERVAL, DNSMASQ_POLLING_MAX_TIMES).start();
                }
                cancelInactivityTimeout();
            } else if (device.deviceState == WifiDevice.DISCONNECTED) {
                mL2ConnectedDeviceMap.remove(device.deviceAddress);
                mConnectedDeviceMap.remove(device.deviceAddress);
                sendTetherConnectStateChangedBroadcast();
                // schedule inactivity timeout if non-zero and no more devices are connected
                if (mWiFiApInactivityTimeout > 0 && mL2ConnectedDeviceMap.size() == 0) {
                    scheduleInactivityTimeout();
                }
            }
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "WifiDevice IllegalArgument: " + ex);
        }
    }

    public int tether(String iface) {
        if (DBG) Log.d(TAG, "Tethering " + iface);
        TetherInterfaceSM sm = null;
        synchronized (mPublicSync) {
            sm = mIfaces.get(iface);
        }
        if (sm == null) {
            Log.e(TAG, "Tried to Tether an unknown iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
        }
        if (!sm.isAvailable() && !sm.isErrored()) {
            Log.e(TAG, "Tried to Tether an unavailable iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
        }
        sm.sendMessage(TetherInterfaceSM.CMD_TETHER_REQUESTED);
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    public int untether(String iface) {
        if (DBG) Log.d(TAG, "Untethering " + iface);
        TetherInterfaceSM sm = null;
        synchronized (mPublicSync) {
            sm = mIfaces.get(iface);
        }
        if (sm == null) {
            Log.e(TAG, "Tried to Untether an unknown iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
        }
        if (sm.isErrored()) {
            Log.e(TAG, "Tried to Untethered an errored iface :" + iface + ", ignoring");
            return ConnectivityManager.TETHER_ERROR_UNAVAIL_IFACE;
        }
        sm.sendMessage(TetherInterfaceSM.CMD_TETHER_UNREQUESTED);
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    public int getLastTetherError(String iface) {
        TetherInterfaceSM sm = null;
        synchronized (mPublicSync) {
            sm = mIfaces.get(iface);
            if (sm == null) {
                Log.e(TAG, "Tried to getLastTetherError on an unknown iface :" + iface +
                        ", ignoring");
                return ConnectivityManager.TETHER_ERROR_UNKNOWN_IFACE;
            }
            return sm.getLastError();
        }
    }

    // TODO - move all private methods used only by the state machine into the state machine
    // to clarify what needs synchronized protection.
    private void sendTetherStateChangedBroadcast() {
        if (!getConnectivityManager().isTetheringSupported()) return;

        ArrayList<String> availableList = new ArrayList<String>();
        ArrayList<String> activeList = new ArrayList<String>();
        ArrayList<String> erroredList = new ArrayList<String>();

        boolean wifiTethered = false;
        boolean usbTethered = false;
        boolean bluetoothTethered = false;

        synchronized (mPublicSync) {
            Set ifaces = mIfaces.keySet();
            for (Object iface : ifaces) {
                TetherInterfaceSM sm = mIfaces.get(iface);
                if (sm != null) {
                    if (sm.isErrored()) {
                        erroredList.add((String)iface);
                    } else if (sm.isAvailable()) {
                        availableList.add((String)iface);
                    } else if (sm.isTethered()) {
                        if (isUsb((String)iface)) {
                            usbTethered = true;
                        } else if (isWifi((String)iface)) {
                            wifiTethered = true;
                      } else if (isBluetooth((String)iface)) {
                            bluetoothTethered = true;
                        }
                        activeList.add((String)iface);
                    }
                }
            }
        }
        Intent broadcast = new Intent(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        broadcast.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING |
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        broadcast.putStringArrayListExtra(ConnectivityManager.EXTRA_AVAILABLE_TETHER,
                availableList);
        broadcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ACTIVE_TETHER, activeList);
        broadcast.putStringArrayListExtra(ConnectivityManager.EXTRA_ERRORED_TETHER,
                erroredList);
        mContext.sendStickyBroadcastAsUser(broadcast, UserHandle.ALL);
        if (DBG) {
            Log.d(TAG, "sendTetherStateChangedBroadcast " + availableList.size() + ", " +
                    activeList.size() + ", " + erroredList.size());
        }

        if (usbTethered) {
            if (wifiTethered || bluetoothTethered) {
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_general);
            } else {
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_usb);
            }
        } else if (wifiTethered) {
            if (bluetoothTethered) {
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_general);
            } else {
                /* We now have a status bar icon for WifiTethering, so drop the notification */
                clearTetheredNotification();
            }
        } else if (bluetoothTethered) {
            showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_bluetooth);
        } else {
            clearTetheredNotification();
        }
    }

    private void showTetheredNotification(int icon) {
        NotificationManager notificationManager =
                (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (mLastNotificationId != 0) {
            if (mLastNotificationId == icon) {
                if (!mContext.getResources().getBoolean(
                        com.android.internal.R.bool.config_softap_extention)
                        || icon != com.android.internal.R.drawable.stat_sys_tether_wifi) {
                    // if softap extension feature is on, allow to update icon else return.
                    return;
                }
            }
            notificationManager.cancelAsUser(null, mLastNotificationId,
                    UserHandle.ALL);
            mLastNotificationId = 0;
        }

        Intent intent = new Intent();
        intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                null, UserHandle.CURRENT);

        Resources r = Resources.getSystem();
        CharSequence title = r.getText(com.android.internal.R.string.tethered_notification_title);
        CharSequence message = r.getText(com.android.internal.R.string.
                tethered_notification_message);

        if (mTetheredNotificationBuilder == null) {
            mTetheredNotificationBuilder = new Notification.Builder(mContext);
            mTetheredNotificationBuilder.setWhen(0)
                    .setOngoing(true)
                    .setColor(mContext.getColor(
                            com.android.internal.R.color.system_notification_accent_color))
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STATUS);
        }
        mTetheredNotificationBuilder.setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pi);
        mLastNotificationId = icon;

        notificationManager.notifyAsUser(null, mLastNotificationId,
                mTetheredNotificationBuilder.build(), UserHandle.ALL);
    }

    private void clearTetheredNotification() {
        NotificationManager notificationManager =
            (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && mLastNotificationId != 0) {
            notificationManager.cancelAsUser(null, mLastNotificationId,
                    UserHandle.ALL);
            mLastNotificationId = 0;
        }
    }

    private class StateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
            if (action == null) { return; }
            if (action.equals(UsbManager.ACTION_USB_STATE)) {
                synchronized (Tethering.this.mPublicSync) {
                    boolean usbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
                    mRndisEnabled = intent.getBooleanExtra(UsbManager.USB_FUNCTION_RNDIS, false);
                    // start tethering if we have a request pending
                    if (usbConnected && mRndisEnabled && mUsbTetherRequested) {
                        tetherUsb(true);
                    }
                    mUsbTetherRequested = false;
                }
            } else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo)intent.getParcelableExtra(
                        ConnectivityManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null &&
                        networkInfo.getDetailedState() != NetworkInfo.DetailedState.FAILED) {
                    if (VDBG) Log.d(TAG, "Tethering got CONNECTIVITY_ACTION");
                    mTetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED);
                }
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                updateConfiguration();
            } else if(action.equals(WIFI_AP_STATE_CHANGED_ACTION)){
                int wifiApState = intent.getIntExtra("wifi_state", WIFI_AP_STATE_DISABLED);
                if (DBG) Log.d(TAG, "WIFI_AP_STATE_CHANGED: wifiApState="  + wifiApState);
                if(wifiApState == WIFI_AP_STATE_ENABLED ||
                    wifiApState == WIFI_AP_STATE_DISABLED) {
                    mConnectedDeviceMap.clear();
                    mL2ConnectedDeviceMap.clear();
                }
            }
        }
    }

    private void tetherUsb(boolean enable) {
        if (VDBG) Log.d(TAG, "tetherUsb " + enable);

        String[] ifaces = new String[0];
        try {
            ifaces = mNMService.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces", e);
            return;
        }
        for (String iface : ifaces) {
            if (isUsb(iface)) {
                int result = (enable ? tether(iface) : untether(iface));
                if (result == ConnectivityManager.TETHER_ERROR_NO_ERROR) {
                    return;
                }
            }
        }
        Log.e(TAG, "unable start or stop USB tethering");
    }

    // configured when we start tethering and unconfig'd on error or conclusion
    private boolean configureUsbIface(boolean enabled) {
        if (VDBG) Log.d(TAG, "configureUsbIface(" + enabled + ")");

        // toggle the USB interfaces
        String[] ifaces = new String[0];
        try {
            ifaces = mNMService.listInterfaces();
        } catch (Exception e) {
            Log.e(TAG, "Error listing Interfaces", e);
            return false;
        }
        for (String iface : ifaces) {
            if (isUsb(iface)) {
                InterfaceConfiguration ifcg = null;
                try {
                    ifcg = mNMService.getInterfaceConfig(iface);
                    if (ifcg != null) {
                        InetAddress addr = NetworkUtils.numericToInetAddress(USB_NEAR_IFACE_ADDR);
                        ifcg.setLinkAddress(new LinkAddress(addr, USB_PREFIX_LENGTH));
                        if (enabled) {
                            ifcg.setInterfaceUp();
                        } else {
                            ifcg.setInterfaceDown();
                        }
                        ifcg.clearFlag("running");
                        mNMService.setInterfaceConfig(iface, ifcg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error configuring interface " + iface, e);
                    return false;
                }
            }
         }

        return true;
    }

    // TODO - return copies so people can't tamper
    public String[] getTetherableUsbRegexs() {
        return mTetherableUsbRegexs;
    }

    public String[] getTetherableWifiRegexs() {
        return mTetherableWifiRegexs;
    }

    public String[] getTetherableBluetoothRegexs() {
        return mTetherableBluetoothRegexs;
    }

    public int setUsbTethering(boolean enable) {
        if (VDBG) Log.d(TAG, "setUsbTethering(" + enable + ")");
        UsbManager usbManager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);

        synchronized (mPublicSync) {
            if (enable) {
                if (mRndisEnabled) {
                    tetherUsb(true);
                } else {
                    mUsbTetherRequested = true;
                    usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_RNDIS);
                }
            } else {
                tetherUsb(false);
                if (mRndisEnabled) {
                    usbManager.setCurrentFunction(null);
                }
                mUsbTetherRequested = false;
            }
        }
        return ConnectivityManager.TETHER_ERROR_NO_ERROR;
    }

    public int[] getUpstreamIfaceTypes() {
        int values[];
        synchronized (mPublicSync) {
            updateConfiguration();  // TODO - remove?
            values = new int[mUpstreamIfaceTypes.size()];
            Iterator<Integer> iterator = mUpstreamIfaceTypes.iterator();
            for (int i=0; i < mUpstreamIfaceTypes.size(); i++) {
                values[i] = iterator.next();
            }
        }
        return values;
    }

    public void checkDunRequired() {
        int secureSetting = 2;
        TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            secureSetting = tm.getTetherApnRequired();
        }
        // Allow override of TETHER_DUN_REQUIRED via prop
        int prop = SystemProperties.getInt("persist.sys.dun.override", -1);
        secureSetting = ((prop < 3) && (prop >= 0)) ? prop : secureSetting;

        synchronized (mPublicSync) {
            // 2 = not set, 0 = DUN not required, 1 = DUN required
            if (secureSetting != 2) {
                int requiredApn = (secureSetting == 1 ?
                        ConnectivityManager.TYPE_MOBILE_DUN :
                        ConnectivityManager.TYPE_MOBILE_HIPRI);
                if (requiredApn == ConnectivityManager.TYPE_MOBILE_DUN) {
                    while (mUpstreamIfaceTypes.contains(MOBILE_TYPE)) {
                        mUpstreamIfaceTypes.remove(MOBILE_TYPE);
                    }
                    while (mUpstreamIfaceTypes.contains(HIPRI_TYPE)) {
                        mUpstreamIfaceTypes.remove(HIPRI_TYPE);
                    }
                    if (mUpstreamIfaceTypes.contains(DUN_TYPE) == false) {
                        mUpstreamIfaceTypes.add(DUN_TYPE);
                    }
                } else {
                    while (mUpstreamIfaceTypes.contains(DUN_TYPE)) {
                        mUpstreamIfaceTypes.remove(DUN_TYPE);
                    }
                    if (mUpstreamIfaceTypes.contains(MOBILE_TYPE) == false) {
                        mUpstreamIfaceTypes.add(MOBILE_TYPE);
                    }
                    if (mUpstreamIfaceTypes.contains(HIPRI_TYPE) == false) {
                        mUpstreamIfaceTypes.add(HIPRI_TYPE);
                    }
                }
            }
            if (mUpstreamIfaceTypes.contains(DUN_TYPE)) {
                mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_MOBILE_DUN;
            } else {
                mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_MOBILE_HIPRI;
            }
        }
    }

    // TODO review API - maybe return ArrayList<String> here and below?
    public String[] getTetheredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            Set keys = mIfaces.keySet();
            for (Object key : keys) {
                TetherInterfaceSM sm = mIfaces.get(key);
                if (sm.isTethered()) {
                    list.add((String)key);
                }
            }
        }
        String[] retVal = new String[list.size()];
        for (int i=0; i < list.size(); i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

    public String[] getTetherableIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            Set keys = mIfaces.keySet();
            for (Object key : keys) {
                TetherInterfaceSM sm = mIfaces.get(key);
                if (sm.isAvailable()) {
                    list.add((String)key);
                }
            }
        }
        String[] retVal = new String[list.size()];
        for (int i=0; i < list.size(); i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

    public String[] getTetheredDhcpRanges() {
        return mDhcpRange;
    }

    public String[] getErroredIfaces() {
        ArrayList<String> list = new ArrayList<String>();
        synchronized (mPublicSync) {
            Set keys = mIfaces.keySet();
            for (Object key : keys) {
                TetherInterfaceSM sm = mIfaces.get(key);
                if (sm.isErrored()) {
                    list.add((String)key);
                }
            }
        }
        String[] retVal = new String[list.size()];
        for (int i= 0; i< list.size(); i++) {
            retVal[i] = list.get(i);
        }
        return retVal;
    }

    class TetherInterfaceSM extends StateMachine {
        // notification from the master SM that it's not in tether mode
        static final int CMD_TETHER_MODE_DEAD            =  1;
        // request from the user that it wants to tether
        static final int CMD_TETHER_REQUESTED            =  2;
        // request from the user that it wants to untether
        static final int CMD_TETHER_UNREQUESTED          =  3;
        // notification that this interface is down
        static final int CMD_INTERFACE_DOWN              =  4;
        // notification that this interface is up
        static final int CMD_INTERFACE_UP                =  5;
        // notification from the master SM that it had an error turning on cellular dun
        static final int CMD_CELL_DUN_ERROR              =  6;
        // notification from the master SM that it had trouble enabling IP Forwarding
        static final int CMD_IP_FORWARDING_ENABLE_ERROR  =  7;
        // notification from the master SM that it had trouble disabling IP Forwarding
        static final int CMD_IP_FORWARDING_DISABLE_ERROR =  8;
        // notification from the master SM that it had trouble starting tethering
        static final int CMD_START_TETHERING_ERROR       =  9;
        // notification from the master SM that it had trouble stopping tethering
        static final int CMD_STOP_TETHERING_ERROR        = 10;
        // notification from the master SM that it had trouble setting the DNS forwarders
        static final int CMD_SET_DNS_FORWARDERS_ERROR    = 11;
        // the upstream connection has changed
        static final int CMD_TETHER_CONNECTION_CHANGED   = 12;

        private State mDefaultState;

        private State mInitialState;
        private State mStartingState;
        private State mTetheredState;

        private State mUnavailableState;

        private boolean mAvailable;
        private boolean mTethered;
        int mLastError;

        String mIfaceName;
        String mMyUpstreamIfaceName;  // may change over time

        boolean mUsb;

        TetherInterfaceSM(String name, Looper looper, boolean usb) {
            super(name, looper);
            mIfaceName = name;
            mUsb = usb;
            setLastError(ConnectivityManager.TETHER_ERROR_NO_ERROR);

            mInitialState = new InitialState();
            addState(mInitialState);
            mStartingState = new StartingState();
            addState(mStartingState);
            mTetheredState = new TetheredState();
            addState(mTetheredState);
            mUnavailableState = new UnavailableState();
            addState(mUnavailableState);

            setInitialState(mInitialState);
        }

        public String toString() {
            String res = new String();
            res += mIfaceName + " - ";
            IState current = getCurrentState();
            if (current == mInitialState) res += "InitialState";
            if (current == mStartingState) res += "StartingState";
            if (current == mTetheredState) res += "TetheredState";
            if (current == mUnavailableState) res += "UnavailableState";
            if (mAvailable) res += " - Available";
            if (mTethered) res += " - Tethered";
            res += " - lastError =" + mLastError;
            return res;
        }

        public int getLastError() {
            synchronized (Tethering.this.mPublicSync) {
                return mLastError;
            }
        }

        private void setLastError(int error) {
            synchronized (Tethering.this.mPublicSync) {
                mLastError = error;

                if (isErrored()) {
                    if (mUsb) {
                        // note everything's been unwound by this point so nothing to do on
                        // further error..
                        Tethering.this.configureUsbIface(false);
                    }
                }
            }
        }

        public boolean isAvailable() {
            synchronized (Tethering.this.mPublicSync) {
                return mAvailable;
            }
        }

        private void setAvailable(boolean available) {
            synchronized (Tethering.this.mPublicSync) {
                mAvailable = available;
            }
        }

        public boolean isTethered() {
            synchronized (Tethering.this.mPublicSync) {
                return mTethered;
            }
        }

        private void setTethered(boolean tethered) {
            synchronized (Tethering.this.mPublicSync) {
                mTethered = tethered;
            }
        }

        public boolean isErrored() {
            synchronized (Tethering.this.mPublicSync) {
                return (mLastError != ConnectivityManager.TETHER_ERROR_NO_ERROR);
            }
        }

        class InitialState extends State {
            @Override
            public void enter() {
                setAvailable(true);
                setTethered(false);
                sendTetherStateChangedBroadcast();
            }

            @Override
            public boolean processMessage(Message message) {
                if (DBG) Log.d(TAG, "InitialState.processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_REQUESTED:
                        setLastError(ConnectivityManager.TETHER_ERROR_NO_ERROR);
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_REQUESTED,
                                TetherInterfaceSM.this);
                        transitionTo(mStartingState);
                        break;
                    case CMD_INTERFACE_DOWN:
                        transitionTo(mUnavailableState);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class StartingState extends State {
            @Override
            public void enter() {
                setAvailable(false);
                if (mUsb) {
                    if (!Tethering.this.configureUsbIface(true)) {
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        setLastError(ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);

                        transitionTo(mInitialState);
                        return;
                    }
                }
                sendTetherStateChangedBroadcast();

                // Skipping StartingState
                transitionTo(mTetheredState);
            }
            @Override
            public boolean processMessage(Message message) {
                if (DBG) Log.d(TAG, "StartingState.processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    // maybe a parent class?
                    case CMD_TETHER_UNREQUESTED:
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        if (mUsb) {
                            if (!Tethering.this.configureUsbIface(false)) {
                                setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);
                                break;
                            }
                        }
                        transitionTo(mInitialState);
                        break;
                    case CMD_CELL_DUN_ERROR:
                    case CMD_IP_FORWARDING_ENABLE_ERROR:
                    case CMD_IP_FORWARDING_DISABLE_ERROR:
                    case CMD_START_TETHERING_ERROR:
                    case CMD_STOP_TETHERING_ERROR:
                    case CMD_SET_DNS_FORWARDERS_ERROR:
                        setLastErrorAndTransitionToInitialState(
                                ConnectivityManager.TETHER_ERROR_MASTER_ERROR);
                        break;
                    case CMD_INTERFACE_DOWN:
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        transitionTo(mUnavailableState);
                        break;
                    default:
                        retValue = false;
                }
                return retValue;
            }
        }

        class TetheredState extends State {
            @Override
            public void enter() {
                try {
                    mNMService.tetherInterface(mIfaceName);
                } catch (Exception e) {
                    Log.e(TAG, "Error Tethering: " + e.toString());
                    setLastError(ConnectivityManager.TETHER_ERROR_TETHER_IFACE_ERROR);

                    transitionTo(mInitialState);
                    return;
                }
                if (DBG) Log.d(TAG, "Tethered " + mIfaceName);
                setAvailable(false);
                setTethered(true);
                sendTetherStateChangedBroadcast();
            }

            private void cleanupUpstream() {
                if (mMyUpstreamIfaceName != null) {
                    // note that we don't care about errors here.
                    // sometimes interfaces are gone before we get
                    // to remove their rules, which generates errors.
                    // just do the best we can.
                    try {
                        // about to tear down NAT; gather remaining statistics
                        mStatsService.forceUpdate();
                    } catch (Exception e) {
                        if (VDBG) Log.e(TAG, "Exception in forceUpdate: " + e.toString());
                    }
                    try {
                        mNMService.stopInterfaceForwarding(mIfaceName, mMyUpstreamIfaceName);
                    } catch (Exception e) {
                        if (VDBG) Log.e(
                                TAG, "Exception in removeInterfaceForward: " + e.toString());
                    }
                    try {
                        mNMService.disableNat(mIfaceName, mMyUpstreamIfaceName);
                    } catch (Exception e) {
                        if (VDBG) Log.e(TAG, "Exception in disableNat: " + e.toString());
                    }
                    mMyUpstreamIfaceName = null;
                }
                return;
            }

            @Override
            public boolean processMessage(Message message) {
                if (DBG) Log.d(TAG, "TetheredState.processMessage what=" + message.what);
                boolean retValue = true;
                boolean error = false;
                switch (message.what) {
                    case CMD_TETHER_UNREQUESTED:
                    case CMD_INTERFACE_DOWN:
                        cleanupUpstream();
                        try {
                            mNMService.untetherInterface(mIfaceName);
                        } catch (Exception e) {
                            setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_UNTETHER_IFACE_ERROR);
                            break;
                        }
                        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_TETHER_MODE_UNREQUESTED,
                                TetherInterfaceSM.this);
                        if (message.what == CMD_TETHER_UNREQUESTED) {
                            if (mUsb) {
                                if (!Tethering.this.configureUsbIface(false)) {
                                    setLastError(
                                            ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);
                                }
                            }
                            transitionTo(mInitialState);
                        } else if (message.what == CMD_INTERFACE_DOWN) {
                            transitionTo(mUnavailableState);
                        }
                        if (DBG) Log.d(TAG, "Untethered " + mIfaceName);
                        break;
                    case CMD_TETHER_CONNECTION_CHANGED:
                        String newUpstreamIfaceName = (String)(message.obj);
                        if ((mMyUpstreamIfaceName == null && newUpstreamIfaceName == null) ||
                                (mMyUpstreamIfaceName != null &&
                                mMyUpstreamIfaceName.equals(newUpstreamIfaceName))) {
                            if (VDBG) Log.d(TAG, "Connection changed noop - dropping");
                            break;
                        }
                        cleanupUpstream();
                        if (newUpstreamIfaceName != null) {
                            try {
                                mNMService.enableNat(mIfaceName, newUpstreamIfaceName);
                                mNMService.startInterfaceForwarding(mIfaceName,
                                        newUpstreamIfaceName);
                            } catch (Exception e) {
                                Log.e(TAG, "Exception enabling Nat: " + e.toString());
                                try {
                                    mNMService.disableNat(mIfaceName, newUpstreamIfaceName);
                                } catch (Exception ee) {}
                                try {
                                    mNMService.untetherInterface(mIfaceName);
                                } catch (Exception ee) {}

                                setLastError(ConnectivityManager.TETHER_ERROR_ENABLE_NAT_ERROR);
                                transitionTo(mInitialState);
                                return true;
                            }
                        }
                        mMyUpstreamIfaceName = newUpstreamIfaceName;
                        break;
                    case CMD_CELL_DUN_ERROR:
                    case CMD_IP_FORWARDING_ENABLE_ERROR:
                    case CMD_IP_FORWARDING_DISABLE_ERROR:
                    case CMD_START_TETHERING_ERROR:
                    case CMD_STOP_TETHERING_ERROR:
                    case CMD_SET_DNS_FORWARDERS_ERROR:
                        error = true;
                        // fall through
                    case CMD_TETHER_MODE_DEAD:
                        cleanupUpstream();
                        try {
                            mNMService.untetherInterface(mIfaceName);
                        } catch (Exception e) {
                            setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_UNTETHER_IFACE_ERROR);
                            break;
                        }
                        if (error) {
                            setLastErrorAndTransitionToInitialState(
                                    ConnectivityManager.TETHER_ERROR_MASTER_ERROR);
                            break;
                        }
                        if (DBG) Log.d(TAG, "Tether lost upstream connection " + mIfaceName);
                        sendTetherStateChangedBroadcast();
                        if (mUsb) {
                            if (!Tethering.this.configureUsbIface(false)) {
                                setLastError(ConnectivityManager.TETHER_ERROR_IFACE_CFG_ERROR);
                            }
                        }
                        transitionTo(mInitialState);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class UnavailableState extends State {
            @Override
            public void enter() {
                setAvailable(false);
                setLastError(ConnectivityManager.TETHER_ERROR_NO_ERROR);
                setTethered(false);
                sendTetherStateChangedBroadcast();
            }
            @Override
            public boolean processMessage(Message message) {
                boolean retValue = true;
                switch (message.what) {
                    case CMD_INTERFACE_UP:
                        transitionTo(mInitialState);
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        void setLastErrorAndTransitionToInitialState(int error) {
            setLastError(error);
            transitionTo(mInitialState);
        }

    }

    class TetherMasterSM extends StateMachine {
        // an interface SM has requested Tethering
        static final int CMD_TETHER_MODE_REQUESTED   = 1;
        // an interface SM has unrequested Tethering
        static final int CMD_TETHER_MODE_UNREQUESTED = 2;
        // upstream connection change - do the right thing
        static final int CMD_UPSTREAM_CHANGED        = 3;
        // we received notice that the cellular DUN connection is up
        static final int CMD_CELL_CONNECTION_RENEW   = 4;
        // we don't have a valid upstream conn, check again after a delay
        static final int CMD_RETRY_UPSTREAM          = 5;

        // This indicates what a timeout event relates to.  A state that
        // sends itself a delayed timeout event and handles incoming timeout events
        // should inc this when it is entered and whenever it sends a new timeout event.
        // We do not flush the old ones.
        private int mSequenceNumber;

        private State mInitialState;
        private State mTetherModeAliveState;

        private State mSetIpForwardingEnabledErrorState;
        private State mSetIpForwardingDisabledErrorState;
        private State mStartTetheringErrorState;
        private State mStopTetheringErrorState;
        private State mSetDnsForwardersErrorState;

        private ArrayList<TetherInterfaceSM> mNotifyList;

        private int mCurrentConnectionSequence;
        private int mMobileApnReserved = ConnectivityManager.TYPE_NONE;

        private String mUpstreamIfaceName = null;

        private static final int UPSTREAM_SETTLE_TIME_MS     = 10000;
        private static final int CELL_CONNECTION_RENEW_MS    = 40000;

        TetherMasterSM(String name, Looper looper) {
            super(name, looper);

            //Add states
            mInitialState = new InitialState();
            addState(mInitialState);
            mTetherModeAliveState = new TetherModeAliveState();
            addState(mTetherModeAliveState);

            mSetIpForwardingEnabledErrorState = new SetIpForwardingEnabledErrorState();
            addState(mSetIpForwardingEnabledErrorState);
            mSetIpForwardingDisabledErrorState = new SetIpForwardingDisabledErrorState();
            addState(mSetIpForwardingDisabledErrorState);
            mStartTetheringErrorState = new StartTetheringErrorState();
            addState(mStartTetheringErrorState);
            mStopTetheringErrorState = new StopTetheringErrorState();
            addState(mStopTetheringErrorState);
            mSetDnsForwardersErrorState = new SetDnsForwardersErrorState();
            addState(mSetDnsForwardersErrorState);

            mNotifyList = new ArrayList<TetherInterfaceSM>();
            setInitialState(mInitialState);
        }

        class TetherMasterUtilState extends State {
            protected final static boolean TRY_TO_SETUP_MOBILE_CONNECTION = true;
            protected final static boolean WAIT_FOR_NETWORK_TO_SETTLE     = false;

            @Override
            public boolean processMessage(Message m) {
                return false;
            }
            protected String enableString(int apnType) {
                switch (apnType) {
                case ConnectivityManager.TYPE_MOBILE_DUN:
                    return Phone.FEATURE_ENABLE_DUN_ALWAYS;
                case ConnectivityManager.TYPE_MOBILE:
                case ConnectivityManager.TYPE_MOBILE_HIPRI:
                    return Phone.FEATURE_ENABLE_HIPRI;
                }
                return null;
            }
            protected boolean turnOnUpstreamMobileConnection(int apnType) {
                boolean retValue = true;
                if (apnType == ConnectivityManager.TYPE_NONE) return false;
                if (apnType != mMobileApnReserved) turnOffUpstreamMobileConnection();
                int result = PhoneConstants.APN_REQUEST_FAILED;
                String enableString = enableString(apnType);
                if (enableString == null) return false;
                result = getConnectivityManager().startUsingNetworkFeature(
                        ConnectivityManager.TYPE_MOBILE, enableString);
                switch (result) {
                case PhoneConstants.APN_ALREADY_ACTIVE:
                case PhoneConstants.APN_REQUEST_STARTED:
                    mMobileApnReserved = apnType;
                    Message m = obtainMessage(CMD_CELL_CONNECTION_RENEW);
                    m.arg1 = ++mCurrentConnectionSequence;
                    sendMessageDelayed(m, CELL_CONNECTION_RENEW_MS);
                    break;
                case PhoneConstants.APN_REQUEST_FAILED:
                default:
                    retValue = false;
                    break;
                }

                return retValue;
            }
            protected boolean turnOffUpstreamMobileConnection() {
                // ignore pending renewal requests
                ++mCurrentConnectionSequence;
                if (mMobileApnReserved != ConnectivityManager.TYPE_NONE) {
                    getConnectivityManager().stopUsingNetworkFeature(
                            ConnectivityManager.TYPE_MOBILE, enableString(mMobileApnReserved));
                    mMobileApnReserved = ConnectivityManager.TYPE_NONE;
                }
                return true;
            }
            protected boolean turnOnMasterTetherSettings() {
                try {
                    mNMService.setIpForwardingEnabled(true);
                } catch (Exception e) {
                    transitionTo(mSetIpForwardingEnabledErrorState);
                    return false;
                }
                try {
                    mNMService.startTethering(mDhcpRange);
                } catch (Exception e) {
                    try {
                        mNMService.stopTethering();
                        mNMService.startTethering(mDhcpRange);
                    } catch (Exception ee) {
                        transitionTo(mStartTetheringErrorState);
                        return false;
                    }
                }
                return true;
            }
            protected boolean turnOffMasterTetherSettings() {
                try {
                    mNMService.stopTethering();
                } catch (Exception e) {
                    transitionTo(mStopTetheringErrorState);
                    return false;
                }
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {
                    transitionTo(mSetIpForwardingDisabledErrorState);
                    return false;
                }
                transitionTo(mInitialState);
                return true;
            }

            protected void chooseUpstreamType(boolean tryCell) {
                int upType = ConnectivityManager.TYPE_NONE;
                String iface = null;

                updateConfiguration(); // TODO - remove?

                synchronized (mPublicSync) {
                    if (VDBG) {
                        Log.d(TAG, "chooseUpstreamType has upstream iface types:");
                        for (Integer netType : mUpstreamIfaceTypes) {
                            Log.d(TAG, " " + netType);
                        }
                    }

                    for (Integer netType : mUpstreamIfaceTypes) {
                        NetworkInfo info =
                                getConnectivityManager().getNetworkInfo(netType.intValue());
                        if ((info != null) && info.isConnected()) {
                            upType = netType.intValue();
                            break;
                        }
                    }
                }

                if (DBG) {
                    Log.d(TAG, "chooseUpstreamType(" + tryCell + "), preferredApn ="
                            + mPreferredUpstreamMobileApn + ", got type=" + upType);
                }

                // if we're on DUN, put our own grab on it
                if (upType == ConnectivityManager.TYPE_MOBILE_DUN ||
                        upType == ConnectivityManager.TYPE_MOBILE_HIPRI) {
                    turnOnUpstreamMobileConnection(upType);
                } else if (upType != ConnectivityManager.TYPE_NONE) {
                    /* If we've found an active upstream connection that's not DUN/HIPRI
                     * we should stop any outstanding DUN/HIPRI start requests.
                     *
                     * If we found NONE we don't want to do this as we want any previous
                     * requests to keep trying to bring up something we can use.
                     */
                    turnOffUpstreamMobileConnection();
                }

                if (upType == ConnectivityManager.TYPE_NONE) {
                    boolean tryAgainLater = true;
                    if ((tryCell == TRY_TO_SETUP_MOBILE_CONNECTION) &&
                            (turnOnUpstreamMobileConnection(mPreferredUpstreamMobileApn) == true)) {
                        // we think mobile should be coming up - don't set a retry
                        tryAgainLater = false;
                    }
                    if (tryAgainLater) {
                        sendMessageDelayed(CMD_RETRY_UPSTREAM, UPSTREAM_SETTLE_TIME_MS);
                    }
                } else {
                    LinkProperties linkProperties =
                            getConnectivityManager().getLinkProperties(upType);
                    if (linkProperties != null) {
                        // Find the interface with the default IPv4 route. It may be the
                        // interface described by linkProperties, or one of the interfaces
                        // stacked on top of it.
                        Log.i(TAG, "Finding IPv4 upstream interface on: " + linkProperties);
                        RouteInfo ipv4Default = RouteInfo.selectBestRoute(
                            linkProperties.getAllRoutes(), Inet4Address.ANY);
                        if (ipv4Default != null) {
                            iface = ipv4Default.getInterface();
                            Log.i(TAG, "Found interface " + ipv4Default.getInterface());
                        } else {
                            Log.i(TAG, "No IPv4 upstream interface, giving up.");
                        }
                    }

                    if (iface != null) {
                        String[] dnsServers = mDefaultDnsServers;
                        Collection<InetAddress> dnses = linkProperties.getDnsServers();
                        if (dnses != null) {
                            // we currently only handle IPv4
                            ArrayList<InetAddress> v4Dnses =
                                    new ArrayList<InetAddress>(dnses.size());
                            for (InetAddress dnsAddress : dnses) {
                                if (dnsAddress instanceof Inet4Address) {
                                    v4Dnses.add(dnsAddress);
                                }
                            }
                            if (v4Dnses.size() > 0) {
                                dnsServers = NetworkUtils.makeStrings(v4Dnses);
                            }
                        }
                        try {
                            Network network = getConnectivityManager().getNetworkForType(upType);
                            if (network == null) {
                                Log.e(TAG, "No Network for upstream type " + upType + "!");
                            }
                            if (VDBG) {
                                Log.d(TAG, "Setting DNS forwarders: Network=" + network +
                                       ", dnsServers=" + Arrays.toString(dnsServers));
                            }
                            mNMService.setDnsForwarders(network, dnsServers);
                        } catch (Exception e) {
                            Log.e(TAG, "Setting DNS forwarders failed!");
                            transitionTo(mSetDnsForwardersErrorState);
                        }
                    }
                }
                notifyTetheredOfNewUpstreamIface(iface);
            }

            protected void notifyTetheredOfNewUpstreamIface(String ifaceName) {
                if (DBG) Log.d(TAG, "notifying tethered with iface =" + ifaceName);
                mUpstreamIfaceName = ifaceName;
                for (TetherInterfaceSM sm : mNotifyList) {
                    sm.sendMessage(TetherInterfaceSM.CMD_TETHER_CONNECTION_CHANGED,
                            ifaceName);
                }
            }
        }

        private final AtomicInteger mSimBcastGenerationNumber = new AtomicInteger(0);
        private SimChangeBroadcastReceiver mBroadcastReceiver = null;

        // keep consts in sync with packages/apps/Settings TetherSettings.java
        private static final int WIFI_TETHERING      = 0;
        private static final int USB_TETHERING       = 1;
        private static final int BLUETOOTH_TETHERING = 2;

        // keep consts in sync with packages/apps/Settings TetherService.java
        private static final String EXTRA_ADD_TETHER_TYPE = "extraAddTetherType";
        private static final String EXTRA_RUN_PROVISION = "extraRunProvision";

        private void startListeningForSimChanges() {
            if (DBG) Log.d(TAG, "startListeningForSimChanges");
            if (mBroadcastReceiver == null) {
                mBroadcastReceiver = new SimChangeBroadcastReceiver(
                        mSimBcastGenerationNumber.incrementAndGet());
                final IntentFilter filter = new IntentFilter();
                filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

                mContext.registerReceiver(mBroadcastReceiver, filter);
            }
        }

        private void stopListeningForSimChanges() {
            if (DBG) Log.d(TAG, "stopListeningForSimChanges");
            if (mBroadcastReceiver != null) {
                mSimBcastGenerationNumber.incrementAndGet();
                mContext.unregisterReceiver(mBroadcastReceiver);
                mBroadcastReceiver = null;
            }
        }

        class SimChangeBroadcastReceiver extends BroadcastReceiver {
            // used to verify this receiver is still current
            final private int mGenerationNumber;

            // we're interested in edge-triggered LOADED notifications, so
            // ignore LOADED unless we saw an ABSENT state first
            private boolean mSimAbsentSeen = false;

            public SimChangeBroadcastReceiver(int generationNumber) {
                super();
                mGenerationNumber = generationNumber;
            }

            @Override
            public void onReceive(Context context, Intent intent) {
                if (DBG) {
                    Log.d(TAG, "simchange mGenerationNumber=" + mGenerationNumber +
                            ", current generationNumber=" + mSimBcastGenerationNumber.get());
                }
                if (mGenerationNumber != mSimBcastGenerationNumber.get()) return;

                final String state =
                        intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);

                Log.d(TAG, "got Sim changed to state " + state + ", mSimAbsentSeen=" +
                        mSimAbsentSeen);
                if (!mSimAbsentSeen && IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(state)) {
                    mSimAbsentSeen = true;
                }

                if (mSimAbsentSeen && IccCardConstants.INTENT_VALUE_ICC_LOADED.equals(state)) {
                    mSimAbsentSeen = false;
                    try {
                        if (mContext.getResources().getString(com.android.internal.R.string.
                                config_mobile_hotspot_provision_app_no_ui).isEmpty() == false) {
                            final String tetherService = mContext.getResources().getString(
                                    com.android.internal.R.string.config_wifi_tether_enable);
                            ArrayList<Integer> tethered = new ArrayList<Integer>();
                            synchronized (mPublicSync) {
                                Set ifaces = mIfaces.keySet();
                                for (Object iface : ifaces) {
                                    TetherInterfaceSM sm = mIfaces.get(iface);
                                    if (sm != null && sm.isTethered()) {
                                        if (isUsb((String)iface)) {
                                            tethered.add(new Integer(USB_TETHERING));
                                        } else if (isWifi((String)iface)) {
                                            tethered.add(new Integer(WIFI_TETHERING));
                                        } else if (isBluetooth((String)iface)) {
                                            tethered.add(new Integer(BLUETOOTH_TETHERING));
                                        }
                                    }
                                }
                            }
                            for (int tetherType : tethered) {
                                Intent startProvIntent = new Intent();
                                startProvIntent.putExtra(EXTRA_ADD_TETHER_TYPE, tetherType);
                                startProvIntent.putExtra(EXTRA_RUN_PROVISION, true);
                                startProvIntent.setComponent(
                                        ComponentName.unflattenFromString(tetherService));
                                mContext.startServiceAsUser(startProvIntent, UserHandle.CURRENT);
                            }
                            Log.d(TAG, "re-evaluate provisioning");
                        } else {
                            Log.d(TAG, "no prov-check needed for new SIM");
                        }
                    } catch (Resources.NotFoundException e) {
                        Log.d(TAG, "no prov-check needed for new SIM");
                        // not defined, do nothing
                    }
                }
            }
        }

        class InitialState extends TetherMasterUtilState {
            @Override
            public void enter() {
            }
            @Override
            public boolean processMessage(Message message) {
                if (DBG) Log.d(TAG, "MasterInitialState.processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_MODE_REQUESTED:
                        TetherInterfaceSM who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        mNotifyList.add(who);
                        transitionTo(mTetherModeAliveState);
                        break;
                    case CMD_TETHER_MODE_UNREQUESTED:
                        who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        int index = mNotifyList.indexOf(who);
                        if (index != -1) {
                            mNotifyList.remove(who);
                        }
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class TetherModeAliveState extends TetherMasterUtilState {
            boolean mTryCell = !WAIT_FOR_NETWORK_TO_SETTLE;
            @Override
            public void enter() {
                turnOnMasterTetherSettings(); // may transition us out
                startListeningForSimChanges();

                mTryCell = !WAIT_FOR_NETWORK_TO_SETTLE; // better try something first pass
                                                        // or crazy tests cases will fail
                chooseUpstreamType(mTryCell);
                mTryCell = !mTryCell;
            }
            @Override
            public void exit() {
                turnOffUpstreamMobileConnection();
                stopListeningForSimChanges();
                notifyTetheredOfNewUpstreamIface(null);
            }
            @Override
            public boolean processMessage(Message message) {
                if (DBG) Log.d(TAG, "TetherModeAliveState.processMessage what=" + message.what);
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_MODE_REQUESTED:
                        TetherInterfaceSM who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode requested by " + who);
                        mNotifyList.add(who);
                        who.sendMessage(TetherInterfaceSM.CMD_TETHER_CONNECTION_CHANGED,
                                mUpstreamIfaceName);
                        break;
                    case CMD_TETHER_MODE_UNREQUESTED:
                        who = (TetherInterfaceSM)message.obj;
                        if (VDBG) Log.d(TAG, "Tether Mode unrequested by " + who);
                        int index = mNotifyList.indexOf(who);
                        if (index != -1) {
                            if (DBG) Log.d(TAG, "TetherModeAlive removing notifyee " + who);
                            mNotifyList.remove(index);
                            if (mNotifyList.isEmpty()) {
                                turnOffMasterTetherSettings(); // transitions appropriately
                            } else {
                                if (DBG) {
                                    Log.d(TAG, "TetherModeAlive still has " + mNotifyList.size() +
                                            " live requests:");
                                    for (Object o : mNotifyList) Log.d(TAG, "  " + o);
                                }
                            }
                        } else {
                           Log.e(TAG, "TetherModeAliveState UNREQUESTED has unknown who: " + who);
                        }
                        break;
                    case CMD_UPSTREAM_CHANGED:
                        // need to try DUN immediately if Wifi goes down
                        mTryCell = !WAIT_FOR_NETWORK_TO_SETTLE;
                        chooseUpstreamType(mTryCell);
                        mTryCell = !mTryCell;
                        break;
                    case CMD_CELL_CONNECTION_RENEW:
                        // make sure we're still using a requested connection - may have found
                        // wifi or something since then.
                        if (mCurrentConnectionSequence == message.arg1) {
                            if (VDBG) {
                                Log.d(TAG, "renewing mobile connection - requeuing for another " +
                                        CELL_CONNECTION_RENEW_MS + "ms");
                            }
                            turnOnUpstreamMobileConnection(mMobileApnReserved);
                        }
                        break;
                    case CMD_RETRY_UPSTREAM:
                        chooseUpstreamType(mTryCell);
                        mTryCell = !mTryCell;
                        break;
                    default:
                        retValue = false;
                        break;
                }
                return retValue;
            }
        }

        class ErrorState extends State {
            int mErrorNotification;
            @Override
            public boolean processMessage(Message message) {
                boolean retValue = true;
                switch (message.what) {
                    case CMD_TETHER_MODE_REQUESTED:
                        TetherInterfaceSM who = (TetherInterfaceSM)message.obj;
                        who.sendMessage(mErrorNotification);
                        break;
                    default:
                       retValue = false;
                }
                return retValue;
            }
            void notify(int msgType) {
                mErrorNotification = msgType;
                for (Object o : mNotifyList) {
                    TetherInterfaceSM sm = (TetherInterfaceSM)o;
                    sm.sendMessage(msgType);
                }
            }

        }
        class SetIpForwardingEnabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setIpForwardingEnabled");
                notify(TetherInterfaceSM.CMD_IP_FORWARDING_ENABLE_ERROR);
            }
        }

        class SetIpForwardingDisabledErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setIpForwardingDisabled");
                notify(TetherInterfaceSM.CMD_IP_FORWARDING_DISABLE_ERROR);
            }
        }

        class StartTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in startTethering");
                notify(TetherInterfaceSM.CMD_START_TETHERING_ERROR);
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {}
            }
        }

        class StopTetheringErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in stopTethering");
                notify(TetherInterfaceSM.CMD_STOP_TETHERING_ERROR);
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {}
            }
        }

        class SetDnsForwardersErrorState extends ErrorState {
            @Override
            public void enter() {
                Log.e(TAG, "Error in setDnsForwarders");
                notify(TetherInterfaceSM.CMD_SET_DNS_FORWARDERS_ERROR);
                try {
                    mNMService.stopTethering();
                } catch (Exception e) {}
                try {
                    mNMService.setIpForwardingEnabled(false);
                } catch (Exception e) {}
            }
        }
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");

        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ConnectivityService.Tether " +
                    "from from pid=" + Binder.getCallingPid() + ", uid=" +
                    Binder.getCallingUid());
                    return;
        }

        pw.println("Tethering:");
        pw.increaseIndent();
        pw.print("mUpstreamIfaceTypes:");
        synchronized (mPublicSync) {
            for (Integer netType : mUpstreamIfaceTypes) {
                pw.print(" " + ConnectivityManager.getNetworkTypeName(netType));
            }
            pw.println();

            pw.println("Tether state:");
            pw.increaseIndent();
            for (Object o : mIfaces.values()) {
                pw.println(o);
            }
            pw.decreaseIndent();
        }
        pw.decreaseIndent();
        return;
    }
}
