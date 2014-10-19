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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ThemeUtils;
import android.content.res.Resources;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.IConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.INetworkStatsService;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.RouteInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.wifi.ClientsList;
import com.android.server.IoThread;
import com.google.android.collect.Lists;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import static libcore.io.OsConstants.AF_INET;
import static libcore.io.OsConstants.AF_INET6;

/**
 * @hide
 *
 * Timeout
 *
 * TODO - look for parent classes and code sharing
 */
public class Tethering extends INetworkManagementEventObserver.Stub {

    private Context mContext;
    private Context mUiContext;
    private final static String TAG = "Tethering";
    private final static boolean DBG = true;
    private final static boolean VDBG = false;

    /* Intent to indicate change in upstream interface change */
    public static final String UPSTREAM_IFACE_CHANGED_ACTION =
                         "com.android.server.connectivity.UPSTREAM_IFACE_CHANGED";

    // Upstream Interface Name i.e. rmnet_data0, wlan0 etc.
    public static final String EXTRA_UPSTREAM_IFACE = "tetheringUpstreamIface";

    // Tethered Interface Name i.e. rndis0, wlan0, usb0 etc.
    public static final String EXTRA_TETHERED_IFACE = "tetheredClientIface";

    // Upstream Interface IP Type i.e IPV6 or IPV4
    public static final String EXTRA_UPSTREAM_IP_TYPE = "tetheringUpstreamIpType";

    // Update Type i.e Add upstream interface or delete upstream interface
    public static final String EXTRA_UPSTREAM_UPDATE_TYPE = "tetheringUpstreamUpdateType";

    // Default Value for Extra Infomration
    public static final int EXTRA_UPSTREAM_INFO_DEFAULT = -1;

    private enum UpstreamInfoUpdateType {
        UPSTREAM_IFACE_REMOVED,
        UPSTREAM_IFACE_ADDED
    }

    // TODO - remove both of these - should be part of interface inspection/selection stuff
    private String[] mTetherableUsbRegexs;
    private String[] mTetherableWifiRegexs;
    private String[] mTetherableP2pRegexs;
    private String[] mTetherableBluetoothRegexs;
    private Collection<Integer> mUpstreamIfaceTypes;

    // used to synchronize public access to members
    private Object mPublicSync;

    private static final Integer MOBILE_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE);
    private static final Integer WIFI_TYPE = new Integer(ConnectivityManager.TYPE_WIFI);
    private static final Integer HIPRI_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE_HIPRI);
    private static final Integer DUN_TYPE = new Integer(ConnectivityManager.TYPE_MOBILE_DUN);

    // if we have to connect to mobile, what APN type should we use?  Calculated by examining the
    // upstream type list and the DUN_REQUIRED secure-setting
    private int mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_NONE;

    private final INetworkManagementService mNMService;
    private final INetworkStatsService mStatsService;
    private final IConnectivityManager mConnService;
    private Looper mLooper;

    private HashMap<String, TetherInterfaceSM> mIfaces; // all tethered/tetherable ifaces

    private BroadcastReceiver mStateReceiver;

    private static final String USB_NEAR_IFACE_ADDR      = "192.168.42.129";
    private static final int USB_PREFIX_LENGTH        = 24;

    // USB is  192.168.42.1 and 255.255.255.0
    // Wifi is 192.168.43.1 and 255.255.255.0
    // BT is limited to max default of 5 connections. 192.168.44.1 to 192.168.48.1
    // with 255.255.255.0

    private String[] mDhcpRange;
    private static final int TETHER_RETRY_UPSTREAM_LIMIT = 5;
    // P2p GO is 192.168.49.1 and 255.255.255.0
    private static final String[] DHCP_DEFAULT_RANGE = {
        "192.168.42.2", "192.168.42.254", "192.168.43.2", "192.168.43.254",
        "192.168.44.2", "192.168.44.254", "192.168.45.2", "192.168.45.254",
        "192.168.46.2", "192.168.46.254", "192.168.47.2", "192.168.47.254",
        "192.168.48.2", "192.168.48.254", "192.168.49.2", "192.168.49.254"
    };

    private String[] mDefaultDnsServers;
    private static final String DNS_DEFAULT_SERVER1 = "8.8.8.8";
    private static final String DNS_DEFAULT_SERVER2 = "8.8.4.4";

    private StateMachine mTetherMasterSM;

    private Notification mTetheredNotification;

    private NotificationManager mNotificationManager;

    private boolean mRndisEnabled;       // track the RNDIS function enabled state
    private boolean mUsbTetherRequested; // true if USB tethering should be started
                                         // when RNDIS is enabled
    private int mLastWifiClientCount = -1;
    private HandlerThread mScanThread;
    private Handler mScanHandler;

    public Tethering(Context context, INetworkManagementService nmService,
            INetworkStatsService statsService, IConnectivityManager connService, Looper looper) {
        mContext = context;
        mNMService = nmService;
        mStatsService = statsService;
        mConnService = connService;
        mLooper = looper;

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
        mContext.registerReceiver(mStateReceiver, filter);

        ThemeUtils.registerThemeChangeReceiver(mContext, new BroadcastReceiver() {
            @Override
            public void onReceive(Context content, Intent intent) {
                mUiContext = null;
            }
        });

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

    void updateConfiguration() {
        String[] tetherableUsbRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_usb_regexs);
        String[] tetherableWifiRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_wifi_regexs);
        String[] tetherableP2pRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_p2p_regexs);
        String[] tetherableBluetoothRegexs = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_tether_bluetooth_regexs);

        int ifaceTypes[] = mContext.getResources().getIntArray(
                com.android.internal.R.array.config_tether_upstream_types);
        Collection<Integer> upstreamIfaceTypes = new ArrayList();
        IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
        IConnectivityManager cm = IConnectivityManager.Stub.asInterface(b);

        int activeNetType = ConnectivityManager.TYPE_NONE;
        try {
            activeNetType = cm.getActiveNetworkInfo().getType();
        } catch (Exception e) {
            Log.d(TAG, "exception when get active network info:" + e);
        }

        for (int i : ifaceTypes) {
            if (i == activeNetType) {
                upstreamIfaceTypes.add(new Integer(i));
            }
        }

        for (int i : ifaceTypes) {
            if (!upstreamIfaceTypes.contains(new Integer(i))) {
                upstreamIfaceTypes.add(new Integer(i));
            }
        }
        if ((activeNetType == ConnectivityManager.TYPE_MOBILE)
                && upstreamIfaceTypes.contains(WIFI_TYPE)) {
            upstreamIfaceTypes.remove(WIFI_TYPE);
        } else if ((activeNetType == ConnectivityManager.TYPE_WIFI)
                && upstreamIfaceTypes.contains(MOBILE_TYPE)) {
            upstreamIfaceTypes.remove(MOBILE_TYPE);
        }

        synchronized (mPublicSync) {
            mTetherableUsbRegexs = tetherableUsbRegexs;
            mTetherableWifiRegexs = tetherableWifiRegexs;
            mTetherableP2pRegexs = tetherableP2pRegexs;
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
            } else if (isP2p(iface)) {
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
                }
            } else {
                if (isUsb(iface)) {
                    // ignore usb0 down after enabling RNDIS
                    // we will handle disconnect in interfaceRemoved instead
                    if (VDBG) Log.d(TAG, "ignore interface down for " + iface);
                } else if (sm != null) {
                    sm.sendMessage(TetherInterfaceSM.CMD_INTERFACE_DOWN);
                    mIfaces.remove(iface);
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

    public boolean isP2p(String iface) {
        synchronized (mPublicSync) {
            for (String regex : mTetherableP2pRegexs) {
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
            if (isP2p(iface)) {
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

    public void addressUpdated(String address, String iface, int flags, int scope) {}

    public void addressRemoved(String address, String iface, int flags, int scope) {}

    public void limitReached(String limitName, String iface) {}

    public void interfaceClassDataActivityChanged(String label, boolean active) {}

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
        try {
            if (!mConnService.isTetheringSupported()) return;
        } catch (RemoteException e) {
            return;
        }

        ArrayList<String> availableList = new ArrayList<String>();
        ArrayList<String> activeList = new ArrayList<String>();
        ArrayList<String> erroredList = new ArrayList<String>();

        boolean wifiTethered = false;
        boolean p2pTethered = false;
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
                        } else if (isP2p((String)iface)) {
                            p2pTethered = true;
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
                showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_wifi);
            }
        } else if (bluetoothTethered) {
            showTetheredNotification(com.android.internal.R.drawable.stat_sys_tether_bluetooth);
        } else {
            clearTetheredNotification();
        }

        if (wifiTethered && !bluetoothTethered) {
            mScanThread = new HandlerThread("WifiClientScanner");
            if (!mScanThread.isAlive()) {
                mScanThread.start();
                mScanHandler = new WifiClientScanner(mScanThread.getLooper());
                mScanHandler.sendEmptyMessage(0);
            }
        } else {
            if (mScanThread != null && mScanThread.isAlive()) {
                mScanThread.quit();
            }
        }
    }

    private void sendUpstreamIfaceChangeBroadcast( String upstreamIface, String tetheredIface,
                                                   int ip_type,
                                                   UpstreamInfoUpdateType update_type) {
        if (DBG) Log.d(TAG, "sendUpstreamIfaceChangeBroadcast upstreamIface:" + upstreamIface +
                            " tetheredIface:" + tetheredIface +
                            " IP Type: "+ ip_type + " update_type" + update_type);
        Intent intent = new Intent(UPSTREAM_IFACE_CHANGED_ACTION);
        intent.putExtra(EXTRA_UPSTREAM_IFACE, upstreamIface);
        intent.putExtra(EXTRA_TETHERED_IFACE, tetheredIface);
        intent.putExtra(EXTRA_UPSTREAM_IP_TYPE, ip_type);
        intent.putExtra(EXTRA_UPSTREAM_UPDATE_TYPE, update_type.ordinal());

        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void showTetheredNotification(int icon) {
        mNotificationManager =
                (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager == null) {
            return;
        }

        if (mTetheredNotification != null) {
            if (mTetheredNotification.icon == icon) {
                return;
            }
            mNotificationManager.cancelAsUser(null, mTetheredNotification.icon,
                    UserHandle.ALL);
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

        if (mTetheredNotification == null) {
            mTetheredNotification = new Notification();
            mTetheredNotification.when = 0;
        }

        mTetheredNotification.icon = icon;
        mTetheredNotification.defaults &= ~Notification.DEFAULT_SOUND;
        mTetheredNotification.flags = Notification.FLAG_ONGOING_EVENT;
        mTetheredNotification.tickerText = title;
        mTetheredNotification.setLatestEventInfo(getUiContext(), title, message, pi);

        mNotificationManager.notifyAsUser(null, mTetheredNotification.icon,
                mTetheredNotification, UserHandle.ALL);
    }

    private class WifiClientScanner extends Handler {

        public WifiClientScanner(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            final DoScan doScan = new DoScan();
            doScan.execute();
            sendEmptyMessageDelayed(0, 2000);
        }
    }

    private class DoScan extends AsyncTask<String, Void, String> {

        private int mCurrentClientCount;

        @Override
        protected String doInBackground(String... params) {
            ArrayList<ClientsList.ClientScanResult> currentClientList
                    = ClientsList.get(true, mContext);
            mCurrentClientCount = currentClientList.size();
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            new Handler(mLooper).post(new Runnable() {
                @Override
                public void run() {
                    if ((mLastWifiClientCount != mCurrentClientCount
                            || mLastWifiClientCount == -1)
                            && mTetheredNotification != null) {
                        mLastWifiClientCount = mCurrentClientCount;
                        Intent intent = new Intent();
                        intent.setClassName("com.android.settings",
                                "com.android.settings.TetheringSettings");
                        intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

                        PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0, intent, 0,
                                null, UserHandle.CURRENT);

                        Resources r = Resources.getSystem();

                        CharSequence title =
                                r.getText(
                                        com.android.internal.R.string.tethered_notification_title);
                        CharSequence message = r.getQuantityString(
                                com.android.internal.R.plurals.tethered_clients_connected,
                                mCurrentClientCount, mCurrentClientCount);
                        mTetheredNotification.setLatestEventInfo(getUiContext(),
                                title, message, pi);
                        mNotificationManager.notifyAsUser(null, mTetheredNotification.icon,
                                mTetheredNotification, UserHandle.ALL);
                    }
                }
            });
        }
    }

    private Context getUiContext() {
        if (mUiContext == null) {
            mUiContext = ThemeUtils.createUiContext(mContext);
        }
        return mUiContext != null ? mUiContext : mContext;
    }

    private void clearTetheredNotification() {
        mNotificationManager =
            (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mNotificationManager != null && mTetheredNotification != null) {
            mNotificationManager.cancelAsUser(null, mTetheredNotification.icon,
                    UserHandle.ALL);
            mTetheredNotification = null;
        }
    }

    private class StateReceiver extends BroadcastReceiver {
        public void onReceive(Context content, Intent intent) {
            String action = intent.getAction();
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
                    mTetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED, networkInfo);
                }
            } else if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                updateConfiguration();
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

    public String[] getTetherableP2pRegexs() {
        return mTetherableP2pRegexs;
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
                    usbManager.setCurrentFunction(UsbManager.USB_FUNCTION_RNDIS, false);
                }
            } else {
                tetherUsb(false);
                if (mRndisEnabled) {
                    usbManager.setCurrentFunction(null, false);
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
        int secureSetting = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.TETHER_DUN_REQUIRED, 2);
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
                /* if DUN is still available, make that a priority */
                if (mUpstreamIfaceTypes.contains(DUN_TYPE)) {
                    mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_MOBILE_DUN;
                } else {
                    mPreferredUpstreamMobileApn = ConnectivityManager.TYPE_MOBILE_HIPRI;
                }
            } else {
                /* dun_required is not set, fall back to HIPRI in that case */
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

    //TODO: Temporary handling upstream change triggered without
    //      CONNECTIVITY_ACTION. Only to accomodate interface
    //      switch during HO.
    //      @see bug/4455071
    public void handleTetherIfaceChange(NetworkInfo info) {
        mTetherMasterSM.sendMessage(TetherMasterSM.CMD_UPSTREAM_CHANGED, info);
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
        // notification from the master SM that it had trouble staring tethering
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

        public String getTethered() {
            synchronized (Tethering.this.mPublicSync) {
                return mIfaceName;
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
                        if(VDBG) Log.d(TAG, "Disabling NAT - Tethered Iface = " + mIfaceName +
                                            " mMyUpstreamIfaceName= " + mMyUpstreamIfaceName);
                        mNMService.disableNat(mIfaceName, mMyUpstreamIfaceName);
                        sendUpstreamIfaceChangeBroadcast( mMyUpstreamIfaceName,
                                                     mIfaceName,
                                                     AF_INET,
                                                     UpstreamInfoUpdateType.UPSTREAM_IFACE_REMOVED);
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
                                mMyUpstreamIfaceName.equals(newUpstreamIfaceName)) ||
                                (newUpstreamIfaceName != null &&
                                newUpstreamIfaceName.equals(mIfaceName))) {
                            if (VDBG) Log.d(TAG, "Connection changed noop - dropping");
                            break;
                        }
                        cleanupUpstream();
                        if (newUpstreamIfaceName != null) {
                            try {
                                if(VDBG) Log.d(TAG,"Enabling NAT - Tethered Iface = " + mIfaceName +
                                                   " newUpstreamIfaceName =" +newUpstreamIfaceName);
                                mNMService.enableNat(mIfaceName, newUpstreamIfaceName);
                                sendUpstreamIfaceChangeBroadcast(
                                                       newUpstreamIfaceName,
                                                       mIfaceName,
                                                       AF_INET,
                                                       UpstreamInfoUpdateType.UPSTREAM_IFACE_ADDED);
                            } catch (Exception e) {
                                Log.e(TAG, "Exception enabling Nat: " + e.toString());
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

        protected int mRetryCount;

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
                try {
                    result = mConnService.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
                            enableString, new Binder());
                } catch (Exception e) {
                }
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
                    try {
                        mConnService.stopUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE,
                                enableString(mMobileApnReserved));
                    } catch (Exception e) {
                        return false;
                    }
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
                try {
                    mNMService.setDnsForwarders(mDefaultDnsServers);
                } catch (Exception e) {
                    transitionTo(mSetDnsForwardersErrorState);
                    return false;
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

            protected void addUpstreamV6Interface(String iface) {
                IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
                INetworkManagementService service = INetworkManagementService.Stub.asInterface(b);

                Log.d(TAG, "adding v6 interface " + iface);
                try {
                    service.addUpstreamV6Interface(iface);
                    for (TetherInterfaceSM sm : mNotifyList) {
                        sendUpstreamIfaceChangeBroadcast( iface, sm.getTethered(), AF_INET6,
                                UpstreamInfoUpdateType.UPSTREAM_IFACE_ADDED);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to append v6 upstream interface");
                }
            }

            protected void removeUpstreamV6Interface(String iface) {
                IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
                INetworkManagementService service = INetworkManagementService.Stub.asInterface(b);

                Log.d(TAG, "removing v6 interface " + iface);
                try {
                    service.removeUpstreamV6Interface(iface);
                    for (TetherInterfaceSM sm : mNotifyList) {
                        sendUpstreamIfaceChangeBroadcast( iface, sm.getTethered(), AF_INET6,
                                UpstreamInfoUpdateType.UPSTREAM_IFACE_REMOVED);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to remove v6 upstream interface");
                }
            }


            boolean isIpv6Connected(IConnectivityManager cm, LinkProperties linkProps) {
                boolean ret = false;
                Collection <InetAddress> addresses = null;

                if (cm == null || linkProps == null) {
                    return false;
                }
                addresses = linkProps.getAddresses();
                for (InetAddress addr: addresses) {
                    if (addr instanceof java.net.Inet6Address) {
                        java.net.Inet6Address i6addr = (java.net.Inet6Address) addr;
                        if (!i6addr.isAnyLocalAddress() && !i6addr.isLinkLocalAddress() &&
                                !i6addr.isLoopbackAddress() && !i6addr.isMulticastAddress()) {
                            ret = true;
                            break;
                        }
                    }
                }
                return ret;
            }

            protected void chooseUpstreamType(boolean tryCell) {
                IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
                IConnectivityManager cm = IConnectivityManager.Stub.asInterface(b);
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
                        NetworkInfo info = null;
                        LinkProperties props = null;
                        boolean isV6Connected = false;
                        try {
                            info = cm.getNetworkInfo(netType.intValue());
                            if (info != null) {
                                props = cm.getLinkProperties(info.getType());
                                isV6Connected = isIpv6Connected(cm, props);
                            }
                        } catch (RemoteException e) { }
                        if ((info != null) && info.isConnected()) {
                            upType = netType.intValue();
                            if (isV6Connected) {
                                addUpstreamV6Interface(props.getInterfaceName());
                            }
                            break;
                        }
                    }
                }

                if (DBG) {
                    Log.d(TAG, "chooseUpstreamType(" + tryCell + "), preferredApn ="
                            + mPreferredUpstreamMobileApn + ", got type=" + upType);
                }

                if (upType != ConnectivityManager.TYPE_NONE) {
                    mRetryCount = 0;
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
                    try {
                        if (cm.getMobileDataEnabled()) {
                            boolean tryAgainLater = true;
                            if (mRetryCount < TETHER_RETRY_UPSTREAM_LIMIT) {
                                if ((tryCell == TRY_TO_SETUP_MOBILE_CONNECTION) &&
                                         (turnOnUpstreamMobileConnection
                                                (mPreferredUpstreamMobileApn) == true)) {
                                    // we think mobile should be coming up - don't set a retry
                                    tryAgainLater = false;
                                    mRetryCount++;
                                }
                                if (tryAgainLater) {
                                    sendMessageDelayed(CMD_RETRY_UPSTREAM, UPSTREAM_SETTLE_TIME_MS);
                                }
                            } else {
                               turnOffUpstreamMobileConnection();
                               Log.d(TAG, "chooseUpstreamType: Reached MAX, NO RETRIES");
                            }
                        } else {
                            Log.d(TAG, "Data is Disabled");
                        }
                    } catch (RemoteException e) {
                        Log.d(TAG, "Exception in getMobileDataEnabled()");
                    }
                } else {
                    LinkProperties linkProperties = null;
                    try {
                        linkProperties = mConnService.getLinkProperties(upType);
                    } catch (RemoteException e) { }
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
                        Collection<InetAddress> dnses = linkProperties.getDnses();
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
                            mNMService.setDnsForwarders(dnsServers);
                        } catch (Exception e) {
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

                mTryCell = !WAIT_FOR_NETWORK_TO_SETTLE; // better try something first pass
                                                        // or crazy tests cases will fail
                mRetryCount = 0;
                chooseUpstreamType(mTryCell);
                mTryCell = !mTryCell;
            }
            @Override
            public void exit() {
                turnOffUpstreamMobileConnection();
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
                        if(VDBG) Log.d(TAG, "CMD_UPSTREAM_CHANGED event received");
                        // need to try DUN immediately if Wifi goes down
                        NetworkInfo info = (NetworkInfo) message.obj;
                        mTryCell = !WAIT_FOR_NETWORK_TO_SETTLE;
                        chooseUpstreamType(mTryCell);
                        if ((info != null) && (!info.isConnected())) {
                            IBinder b = ServiceManager.getService(Context.CONNECTIVITY_SERVICE);
                            IConnectivityManager cm = IConnectivityManager.Stub.asInterface(b);
                            try {
                                LinkProperties props = cm.getLinkProperties(info.getType());
                                removeUpstreamV6Interface(props.getInterfaceName());
                            } catch(RemoteException e) {
                                Log.e(TAG, "Exception querying ConnectivityManager", e);
                            }
                        }
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

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.DUMP) != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ConnectivityService.Tether " +
                    "from from pid=" + Binder.getCallingPid() + ", uid=" +
                    Binder.getCallingUid());
                    return;
        }

        synchronized (mPublicSync) {
            pw.println("mUpstreamIfaceTypes: ");
            for (Integer netType : mUpstreamIfaceTypes) {
                pw.println(" " + netType);
            }

            pw.println();
            pw.println("Tether state:");
            for (Object o : mIfaces.values()) {
                pw.println(" " + o);
            }
        }
        pw.println();
        return;
    }
}
