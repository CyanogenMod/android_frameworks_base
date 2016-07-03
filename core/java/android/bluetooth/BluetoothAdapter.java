/*
 * Copyright (C) 2009-2015 The Android Open Source Project
 * Copyright (C) 2015 Samsung LSI
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

package android.bluetooth;

import android.Manifest;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.app.ActivityThread;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the local device Bluetooth adapter. The {@link BluetoothAdapter}
 * lets you perform fundamental Bluetooth tasks, such as initiate
 * device discovery, query a list of bonded (paired) devices,
 * instantiate a {@link BluetoothDevice} using a known MAC address, and create
 * a {@link BluetoothServerSocket} to listen for connection requests from other
 * devices, and start a scan for Bluetooth LE devices.
 *
 * <p>To get a {@link BluetoothAdapter} representing the local Bluetooth
 * adapter, when running on JELLY_BEAN_MR1 and below, call the
 * static {@link #getDefaultAdapter} method; when running on JELLY_BEAN_MR2 and
 * higher, retrieve it through
 * {@link android.content.Context#getSystemService} with
 * {@link android.content.Context#BLUETOOTH_SERVICE}.
 * Fundamentally, this is your starting point for all
 * Bluetooth actions. Once you have the local adapter, you can get a set of
 * {@link BluetoothDevice} objects representing all paired devices with
 * {@link #getBondedDevices()}; start device discovery with
 * {@link #startDiscovery()}; or create a {@link BluetoothServerSocket} to
 * listen for incoming connection requests with
 * {@link #listenUsingRfcommWithServiceRecord(String,UUID)}; or start a scan for
 * Bluetooth LE devices with {@link #startLeScan(LeScanCallback callback)}.
 *
 * <p class="note"><strong>Note:</strong>
 * Most methods require the {@link android.Manifest.permission#BLUETOOTH}
 * permission and some also require the
 * {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about using Bluetooth, read the
 * <a href="{@docRoot}guide/topics/wireless/bluetooth.html">Bluetooth</a> developer guide.</p>
 * </div>
 *
 * {@see BluetoothDevice}
 * {@see BluetoothServerSocket}
 */
public final class BluetoothAdapter {
    private static final String TAG = "BluetoothAdapter";
    private static final boolean DBG = false;
    private static final boolean VDBG = false;

    /**
     * Default MAC address reported to a client that does not have the
     * android.permission.LOCAL_MAC_ADDRESS permission.
     *
     * @hide
     */
    public static final String DEFAULT_MAC_ADDRESS = "02:00:00:00:00:00";

    /**
     * Sentinel error value for this class. Guaranteed to not equal any other
     * integer constant in this class. Provided as a convenience for functions
     * that require a sentinel error value, for example:
     * <p><code>Intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
     * BluetoothAdapter.ERROR)</code>
     */
    public static final int ERROR = Integer.MIN_VALUE;

    /**
     * Broadcast Action: The state of the local Bluetooth adapter has been
     * changed.
     * <p>For example, Bluetooth has been turned on or off.
     * <p>Always contains the extra fields {@link #EXTRA_STATE} and {@link
     * #EXTRA_PREVIOUS_STATE} containing the new and old states
     * respectively.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_STATE_CHANGED =
            "android.bluetooth.adapter.action.STATE_CHANGED";

    /**
     * Used as an int extra field in {@link #ACTION_STATE_CHANGED}
     * intents to request the current power state. Possible values are:
     * {@link #STATE_OFF},
     * {@link #STATE_TURNING_ON},
     * {@link #STATE_ON},
     * {@link #STATE_TURNING_OFF},
     */
    public static final String EXTRA_STATE =
            "android.bluetooth.adapter.extra.STATE";
    /**
     * Used as an int extra field in {@link #ACTION_STATE_CHANGED}
     * intents to request the previous power state. Possible values are:
     * {@link #STATE_OFF},
     * {@link #STATE_TURNING_ON},
     * {@link #STATE_ON},
     * {@link #STATE_TURNING_OFF}
     */
    public static final String EXTRA_PREVIOUS_STATE =
            "android.bluetooth.adapter.extra.PREVIOUS_STATE";

    /** @hide */
    @IntDef({STATE_OFF, STATE_TURNING_ON, STATE_ON, STATE_TURNING_OFF, STATE_BLE_TURNING_ON,
            STATE_BLE_ON, STATE_BLE_TURNING_OFF})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AdapterState {}

    /**
     * Indicates the local Bluetooth adapter is off.
     */
    public static final int STATE_OFF = 10;
    /**
     * Indicates the local Bluetooth adapter is turning on. However local
     * clients should wait for {@link #STATE_ON} before attempting to
     * use the adapter.
     */
    public static final int STATE_TURNING_ON = 11;
    /**
     * Indicates the local Bluetooth adapter is on, and ready for use.
     */
    public static final int STATE_ON = 12;
    /**
     * Indicates the local Bluetooth adapter is turning off. Local clients
     * should immediately attempt graceful disconnection of any remote links.
     */
    public static final int STATE_TURNING_OFF = 13;

    /**
     * Indicates the local Bluetooth adapter is turning Bluetooth LE mode on.
     * @hide
     */
    public static final int STATE_BLE_TURNING_ON = 14;

    /**
     * Indicates the local Bluetooth adapter is in LE only mode.
     * @hide
     */
    public static final int STATE_BLE_ON = 15;

    /**
     * Indicates the local Bluetooth adapter is turning off LE only mode.
     * @hide
     */
    public static final int STATE_BLE_TURNING_OFF = 16;

    /**
     * Activity Action: Show a system activity that requests discoverable mode.
     * This activity will also request the user to turn on Bluetooth if it
     * is not currently enabled.
     * <p>Discoverable mode is equivalent to {@link
     * #SCAN_MODE_CONNECTABLE_DISCOVERABLE}. It allows remote devices to see
     * this Bluetooth adapter when they perform a discovery.
     * <p>For privacy, Android is not discoverable by default.
     * <p>The sender of this Intent can optionally use extra field {@link
     * #EXTRA_DISCOVERABLE_DURATION} to request the duration of
     * discoverability. Currently the default duration is 120 seconds, and
     * maximum duration is capped at 300 seconds for each request.
     * <p>Notification of the result of this activity is posted using the
     * {@link android.app.Activity#onActivityResult} callback. The
     * <code>resultCode</code>
     * will be the duration (in seconds) of discoverability or
     * {@link android.app.Activity#RESULT_CANCELED} if the user rejected
     * discoverability or an error has occurred.
     * <p>Applications can also listen for {@link #ACTION_SCAN_MODE_CHANGED}
     * for global notification whenever the scan mode changes. For example, an
     * application can be notified when the device has ended discoverability.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_DISCOVERABLE =
            "android.bluetooth.adapter.action.REQUEST_DISCOVERABLE";

    /**
     * Used as an optional int extra field in {@link
     * #ACTION_REQUEST_DISCOVERABLE} intents to request a specific duration
     * for discoverability in seconds. The current default is 120 seconds, and
     * requests over 300 seconds will be capped. These values could change.
     */
    public static final String EXTRA_DISCOVERABLE_DURATION =
            "android.bluetooth.adapter.extra.DISCOVERABLE_DURATION";

    /**
     * Activity Action: Show a system activity that allows the user to turn on
     * Bluetooth.
     * <p>This system activity will return once Bluetooth has completed turning
     * on, or the user has decided not to turn Bluetooth on.
     * <p>Notification of the result of this activity is posted using the
     * {@link android.app.Activity#onActivityResult} callback. The
     * <code>resultCode</code>
     * will be {@link android.app.Activity#RESULT_OK} if Bluetooth has been
     * turned on or {@link android.app.Activity#RESULT_CANCELED} if the user
     * has rejected the request or an error has occurred.
     * <p>Applications can also listen for {@link #ACTION_STATE_CHANGED}
     * for global notification whenever Bluetooth is turned on or off.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_ENABLE =
            "android.bluetooth.adapter.action.REQUEST_ENABLE";

    /**
     * Activity Action: Show a system activity that allows user to enable BLE scans even when
     * Bluetooth is turned off.<p>
     *
     * Notification of result of this activity is posted using
     * {@link android.app.Activity#onActivityResult}. The <code>resultCode</code> will be
     * {@link android.app.Activity#RESULT_OK} if BLE scan always available setting is turned on or
     * {@link android.app.Activity#RESULT_CANCELED} if the user has rejected the request or an
     * error occurred.
     *
     * @hide
     */
    @SystemApi
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String ACTION_REQUEST_BLE_SCAN_ALWAYS_AVAILABLE =
            "android.bluetooth.adapter.action.REQUEST_BLE_SCAN_ALWAYS_AVAILABLE";

    /**
     * Broadcast Action: Indicates the Bluetooth scan mode of the local Adapter
     * has changed.
     * <p>Always contains the extra fields {@link #EXTRA_SCAN_MODE} and {@link
     * #EXTRA_PREVIOUS_SCAN_MODE} containing the new and old scan modes
     * respectively.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_SCAN_MODE_CHANGED =
            "android.bluetooth.adapter.action.SCAN_MODE_CHANGED";

    /**
     * Used as an int extra field in {@link #ACTION_SCAN_MODE_CHANGED}
     * intents to request the current scan mode. Possible values are:
     * {@link #SCAN_MODE_NONE},
     * {@link #SCAN_MODE_CONNECTABLE},
     * {@link #SCAN_MODE_CONNECTABLE_DISCOVERABLE},
     */
    public static final String EXTRA_SCAN_MODE = "android.bluetooth.adapter.extra.SCAN_MODE";
    /**
     * Used as an int extra field in {@link #ACTION_SCAN_MODE_CHANGED}
     * intents to request the previous scan mode. Possible values are:
     * {@link #SCAN_MODE_NONE},
     * {@link #SCAN_MODE_CONNECTABLE},
     * {@link #SCAN_MODE_CONNECTABLE_DISCOVERABLE},
     */
    public static final String EXTRA_PREVIOUS_SCAN_MODE =
            "android.bluetooth.adapter.extra.PREVIOUS_SCAN_MODE";

    /** @hide */
    @IntDef({SCAN_MODE_NONE, SCAN_MODE_CONNECTABLE, SCAN_MODE_CONNECTABLE_DISCOVERABLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ScanMode {}

    /**
     * Indicates that both inquiry scan and page scan are disabled on the local
     * Bluetooth adapter. Therefore this device is neither discoverable
     * nor connectable from remote Bluetooth devices.
     */
    public static final int SCAN_MODE_NONE = 20;
    /**
     * Indicates that inquiry scan is disabled, but page scan is enabled on the
     * local Bluetooth adapter. Therefore this device is not discoverable from
     * remote Bluetooth devices, but is connectable from remote devices that
     * have previously discovered this device.
     */
    public static final int SCAN_MODE_CONNECTABLE = 21;
    /**
     * Indicates that both inquiry scan and page scan are enabled on the local
     * Bluetooth adapter. Therefore this device is both discoverable and
     * connectable from remote Bluetooth devices.
     */
    public static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 23;

    /**
     * Broadcast Action: The local Bluetooth adapter has started the remote
     * device discovery process.
     * <p>This usually involves an inquiry scan of about 12 seconds, followed
     * by a page scan of each new device to retrieve its Bluetooth name.
     * <p>Register for {@link BluetoothDevice#ACTION_FOUND} to be notified as
     * remote Bluetooth devices are found.
     * <p>Device discovery is a heavyweight procedure. New connections to
     * remote Bluetooth devices should not be attempted while discovery is in
     * progress, and existing connections will experience limited bandwidth
     * and high latency. Use {@link #cancelDiscovery()} to cancel an ongoing
     * discovery.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_DISCOVERY_STARTED =
            "android.bluetooth.adapter.action.DISCOVERY_STARTED";
    /**
     * Broadcast Action: The local Bluetooth adapter has finished the device
     * discovery process.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_DISCOVERY_FINISHED =
            "android.bluetooth.adapter.action.DISCOVERY_FINISHED";

    /**
     * Broadcast Action: The local Bluetooth adapter has changed its friendly
     * Bluetooth name.
     * <p>This name is visible to remote Bluetooth devices.
     * <p>Always contains the extra field {@link #EXTRA_LOCAL_NAME} containing
     * the name.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_LOCAL_NAME_CHANGED =
            "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED";
    /**
     * Used as a String extra field in {@link #ACTION_LOCAL_NAME_CHANGED}
     * intents to request the local Bluetooth name.
     */
    public static final String EXTRA_LOCAL_NAME = "android.bluetooth.adapter.extra.LOCAL_NAME";

    /**
     * Intent used to broadcast the change in connection state of the local
     * Bluetooth adapter to a profile of the remote device. When the adapter is
     * not connected to any profiles of any remote devices and it attempts a
     * connection to a profile this intent will sent. Once connected, this intent
     * will not be sent for any more connection attempts to any profiles of any
     * remote device. When the adapter disconnects from the last profile its
     * connected to of any remote device, this intent will be sent.
     *
     * <p> This intent is useful for applications that are only concerned about
     * whether the local adapter is connected to any profile of any device and
     * are not really concerned about which profile. For example, an application
     * which displays an icon to display whether Bluetooth is connected or not
     * can use this intent.
     *
     * <p>This intent will have 3 extras:
     * {@link #EXTRA_CONNECTION_STATE} - The current connection state.
     * {@link #EXTRA_PREVIOUS_CONNECTION_STATE}- The previous connection state.
     * {@link BluetoothDevice#EXTRA_DEVICE} - The remote device.
     *
     * {@link #EXTRA_CONNECTION_STATE} or {@link #EXTRA_PREVIOUS_CONNECTION_STATE}
     * can be any of {@link #STATE_DISCONNECTED}, {@link #STATE_CONNECTING},
     * {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTING}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_STATE_CHANGED =
        "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED";

    /**
     * Extra used by {@link #ACTION_CONNECTION_STATE_CHANGED}
     *
     * This extra represents the current connection state.
     */
    public static final String EXTRA_CONNECTION_STATE =
        "android.bluetooth.adapter.extra.CONNECTION_STATE";

    /**
     * Extra used by {@link #ACTION_CONNECTION_STATE_CHANGED}
     *
     * This extra represents the previous connection state.
     */
    public static final String EXTRA_PREVIOUS_CONNECTION_STATE =
          "android.bluetooth.adapter.extra.PREVIOUS_CONNECTION_STATE";

    /**
     * Broadcast Action: The Bluetooth adapter state has changed in LE only mode.
     * @hide
     */
    @SystemApi
    public static final String ACTION_BLE_STATE_CHANGED =
        "android.bluetooth.adapter.action.BLE_STATE_CHANGED";

    /**
     * Broadcast Action: The notifys Bluetooth ACL connected event. This will be
     * by BLE Always on enabled application to know the ACL_CONNECTED event
     * when Bluetooth state in STATE_BLE_ON. This denotes GATT connection
     * as Bluetooth LE is the only feature available in STATE_BLE_ON
     *
     * This is counterpart of {@link BluetoothDevice#ACTION_ACL_CONNECTED} which
     * works in Bluetooth state STATE_ON
     * @hide
     */
    public static final String ACTION_BLE_ACL_CONNECTED =
        "android.bluetooth.adapter.action.BLE_ACL_CONNECTED";

    /**
     * Broadcast Action: The notifys Bluetooth ACL connected event. This will be
     * by BLE Always on enabled application to know the ACL_DISCONNECTED event
     * when Bluetooth state in STATE_BLE_ON. This denotes GATT disconnection as Bluetooth
     * LE is the only feature available in STATE_BLE_ON
     *
     * This is counterpart of {@link BluetoothDevice#ACTION_ACL_DISCONNECTED} which
     * works in Bluetooth state STATE_ON
     * @hide
     */
    public static final String ACTION_BLE_ACL_DISCONNECTED =
        "android.bluetooth.adapter.action.BLE_ACL_DISCONNECTED";

    /** The profile is in disconnected state */
    public static final int STATE_DISCONNECTED  = 0;
    /** The profile is in connecting state */
    public static final int STATE_CONNECTING    = 1;
    /** The profile is in connected state */
    public static final int STATE_CONNECTED     = 2;
    /** The profile is in disconnecting state */
    public static final int STATE_DISCONNECTING = 3;

    /** @hide */
    public static final String BLUETOOTH_MANAGER_SERVICE = "bluetooth_manager";
    private final IBinder mToken;


    /** When creating a ServerSocket using listenUsingRfcommOn() or
     *  listenUsingL2capOn() use SOCKET_CHANNEL_AUTO_STATIC to create
     *  a ServerSocket that auto assigns a channel number to the first
     *  bluetooth socket.
     *  The channel number assigned to this first Bluetooth Socket will
     *  be stored in the ServerSocket, and reused for subsequent Bluetooth
     *  sockets.
     * @hide */
    public static final int SOCKET_CHANNEL_AUTO_STATIC_NO_SDP = -2;


    private static final int ADDRESS_LENGTH = 17;

    private static final int CONTROLLER_ENERGY_UPDATE_TIMEOUT_MILLIS = 30;
    /** @hide */
    public static final int ACTIVITY_ENERGY_INFO_CACHED = 0;
    /** @hide */
    public static final int ACTIVITY_ENERGY_INFO_REFRESHED = 1;

    /**
     * Lazily initialized singleton. Guaranteed final after first object
     * constructed.
     */
    private static BluetoothAdapter sAdapter;

    private static BluetoothLeScanner sBluetoothLeScanner;
    private static BluetoothLeAdvertiser sBluetoothLeAdvertiser;

    private final IBluetoothManager mManagerService;
    private IBluetooth mService;

    private final Object mLock = new Object();
    private final Map<LeScanCallback, ScanCallback> mLeScanClients;

    /**
     * Get a handle to the default local Bluetooth adapter.
     * <p>Currently Android only supports one Bluetooth adapter, but the API
     * could be extended to support more. This will always return the default
     * adapter.
     * @return the default local adapter, or null if Bluetooth is not supported
     *         on this hardware platform
     */
    public static synchronized BluetoothAdapter getDefaultAdapter() {
        if (sAdapter == null) {
            IBinder b = ServiceManager.getService(BLUETOOTH_MANAGER_SERVICE);
            if (b != null) {
                IBluetoothManager managerService = IBluetoothManager.Stub.asInterface(b);
                sAdapter = new BluetoothAdapter(managerService);
            } else {
                Log.e(TAG, "Bluetooth binder is null");
            }
        }
        return sAdapter;
    }

    /**
     * Use {@link #getDefaultAdapter} to get the BluetoothAdapter instance.
     */
    BluetoothAdapter(IBluetoothManager managerService) {

        if (managerService == null) {
            throw new IllegalArgumentException("bluetooth manager service is null");
        }
        try {
            mService = managerService.registerAdapter(mManagerCallback);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        mManagerService = managerService;
        mLeScanClients = new HashMap<LeScanCallback, ScanCallback>();
        mToken = new Binder();
    }

    /**
     * Get a {@link BluetoothDevice} object for the given Bluetooth hardware
     * address.
     * <p>Valid Bluetooth hardware addresses must be upper case, in a format
     * such as "00:11:22:33:AA:BB". The helper {@link #checkBluetoothAddress} is
     * available to validate a Bluetooth address.
     * <p>A {@link BluetoothDevice} will always be returned for a valid
     * hardware address, even if this adapter has never seen that device.
     *
     * @param address valid Bluetooth MAC address
     * @throws IllegalArgumentException if address is invalid
     */
    public BluetoothDevice getRemoteDevice(String address) {
        android.util.SeempLog.record(62);
        return new BluetoothDevice(address);
    }

    /**
     * Get a {@link BluetoothDevice} object for the given Bluetooth hardware
     * address.
     * <p>Valid Bluetooth hardware addresses must be 6 bytes. This method
     * expects the address in network byte order (MSB first).
     * <p>A {@link BluetoothDevice} will always be returned for a valid
     * hardware address, even if this adapter has never seen that device.
     *
     * @param address Bluetooth MAC address (6 bytes)
     * @throws IllegalArgumentException if address is invalid
     */
    public BluetoothDevice getRemoteDevice(byte[] address) {
        android.util.SeempLog.record(62);
        if (address == null || address.length != 6) {
            throw new IllegalArgumentException("Bluetooth address must have 6 bytes");
        }
        return new BluetoothDevice(String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                address[0], address[1], address[2], address[3], address[4], address[5]));
    }

    /**
     * Returns a {@link BluetoothLeAdvertiser} object for Bluetooth LE Advertising operations.
     * Will return null if Bluetooth is turned off or if Bluetooth LE Advertising is not
     * supported on this device.
     * <p>
     * Use {@link #isMultipleAdvertisementSupported()} to check whether LE Advertising is supported
     * on this device before calling this method.
     */
    public BluetoothLeAdvertiser getBluetoothLeAdvertiser() {
        if (!getLeAccess()) return null;
        if (!isMultipleAdvertisementSupported() && !isPeripheralModeSupported()) {
            Log.e(TAG, "Bluetooth LE advertising not supported");
            return null;
        }
        synchronized(mLock) {
            if (sBluetoothLeAdvertiser == null) {
                sBluetoothLeAdvertiser = new BluetoothLeAdvertiser(mManagerService);
            }
        }
        return sBluetoothLeAdvertiser;
    }

    /**
     * Returns a {@link BluetoothLeScanner} object for Bluetooth LE scan operations.
     */
    public BluetoothLeScanner getBluetoothLeScanner() {
        if (!getLeAccess()) return null;
        synchronized(mLock) {
            if (sBluetoothLeScanner == null) {
                sBluetoothLeScanner = new BluetoothLeScanner(mManagerService);
            }
        }
        return sBluetoothLeScanner;
    }

    /**
     * Return true if Bluetooth is currently enabled and ready for use.
     * <p>Equivalent to:
     * <code>getBluetoothState() == STATE_ON</code>
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return true if the local adapter is turned on
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public boolean isEnabled() {
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.isEnabled();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Return true if Bluetooth LE(Always BLE On feature) is currently
     * enabled and ready for use
     * <p>This returns true if current state is either STATE_ON or STATE_BLE_ON
     *
     * @return true if the local Bluetooth LE adapter is turned on
     * @hide
     */
    @SystemApi
    public boolean isLeEnabled() {
       final int state = getLeState();
       if (state == BluetoothAdapter.STATE_ON) {
           if (DBG) Log.d (TAG, "STATE_ON");
       } else if (state == BluetoothAdapter.STATE_BLE_ON) {
           if (DBG) Log.d (TAG, "STATE_BLE_ON");
       } else {
           if (DBG) Log.d (TAG, "STATE_OFF");
           return false;
       }
       return true;
    }

    /**
     * Performs action based on user action to turn BT ON
     * or OFF if BT is in BLE_ON state
     */
    private void notifyUserAction(boolean enable) {
        if (mService == null) {
            Log.e(TAG, "mService is null");
            return;
        }

        try {
            if (enable) {
                mService.onLeServiceUp(); //NA:TODO implementation pending
            } else {
                mService.onBrEdrDown(); //NA:TODO implementation pending
            }
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
    }

    /**
     * Turns off Bluetooth LE which was earlier turned on by calling EnableBLE().
     *
     * <p> If the internal Adapter state is STATE_BLE_ON, this would trigger the transition
     * to STATE_OFF and completely shut-down Bluetooth
     *
     * <p> If the Adapter state is STATE_ON, This would unregister the existance of
     * special Bluetooth LE application and hence the further turning off of Bluetooth
     * from UI would ensure the complete turn-off of Bluetooth rather than staying back
     * BLE only state
     *
     * <p>This is an asynchronous call: it will return immediately, and
     * clients should listen for {@link #ACTION_BLE_STATE_CHANGED}
     * to be notified of subsequent adapter state changes If this call returns
     * true, then the adapter state will immediately transition from {@link
     * #STATE_ON} to {@link #STATE_TURNING_OFF}, and some time
     * later transition to either {@link #STATE_BLE_ON} or {@link
     * #STATE_OFF} based on the existance of the further Always BLE ON enabled applications
     * If this call returns false then there was an
     * immediate problem that will prevent the QAdapter from being turned off -
     * such as the QAadapter already being turned off.
     *
     * @return true to indicate success, or false on
     *         immediate error
     * @hide
     */
    @SystemApi
    public boolean disableBLE() {
        if (!isBleScanAlwaysAvailable()) return false;

        int state = getLeState();
        if (state == BluetoothAdapter.STATE_ON) {
            if (DBG) Log.d (TAG, "STATE_ON: shouldn't disable");
            try {
                mManagerService.updateBleAppCount(mToken, false);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
            return true;

        } else if (state == BluetoothAdapter.STATE_BLE_ON) {
            if (DBG) Log.d (TAG, "STATE_BLE_ON");
            int bleAppCnt = 0;
            try {
                bleAppCnt = mManagerService.updateBleAppCount(mToken, false);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
            if (bleAppCnt == 0) {
                // Disable only if there are no other clients
                notifyUserAction(false);
            }
            return true;
        }

        if (DBG) Log.d (TAG, "STATE_OFF: Already disabled");
        return false;
    }

    /**
     * Special Applications who want to only turn on Bluetooth Low Energy (BLE) would
     * EnableBLE, EnableBLE brings-up Bluetooth so that application can access
     * only LE related feature (Bluetooth GATT layers interfaces using the respective class)
     * EnableBLE in turn registers the existance of a special App which wants to
     * turn on Bluetooth Low enrgy part without making it visible at the settings UI
     * as Bluetooth ON.
     * <p>Invoking EnableBLE when Bluetooth is already in ON state, would just registers
     * the existance of special Application and doesn't do anything to current BT state.
     * when user turn OFF Bluetooth from UI, if there is an existance of special app, Bluetooth
     * would stay in BLE_ON state so that LE features are still acessible to the special
     * Applications.
     *
     * <p>This is an asynchronous call: it will return immediately, and
     * clients should listen for {@link #ACTION_BLE_STATE_CHANGED}
     * to be notified of subsequent adapter state changes. If this call returns
     * true, then the adapter state will immediately transition from {@link
     * #STATE_OFF} to {@link #STATE_BLE_TURNING_ON}, and some time
     * later transition to either {@link #STATE_OFF} or {@link
     * #STATE_BLE_ON}. If this call returns false then there was an
     * immediate problem that will prevent the adapter from being turned on -
     * such as Airplane mode, or the adapter is already turned on.
     * (@link #ACTION_BLE_STATE_CHANGED) returns the Bluetooth Adapter's various
     * states, It includes all the classic Bluetooth Adapter states along with
     * internal BLE only states
     *
     * @return true to indicate Bluetooth LE start-up has begun, or false on
     *         immediate error
     * @hide
     */
    @SystemApi
    public boolean enableBLE() {
        if (!isBleScanAlwaysAvailable()) return false;

        if (isLeEnabled() == true) {
            if (DBG) Log.d(TAG, "enableBLE(): BT is already enabled..!");
            try {
                mManagerService.updateBleAppCount(mToken, true);
            } catch (RemoteException e) {
                Log.e(TAG, "", e);
            }
            return true;
        }

        try {
            if (DBG) Log.d(TAG, "Calling enableBLE");
            mManagerService.updateBleAppCount(mToken, true);
            return mManagerService.enable(ActivityThread.currentPackageName());
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }

        return false;
    }

    /**
     * Get the current state of the local Bluetooth adapter.
     * <p>Possible return values are
     * {@link #STATE_OFF},
     * {@link #STATE_TURNING_ON},
     * {@link #STATE_ON},
     * {@link #STATE_TURNING_OFF}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return current state of Bluetooth adapter
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    @AdapterState
    public int getState() {
        android.util.SeempLog.record(63);
        try {
            synchronized(mManagerCallback) {
                if (mService != null)
                {
                    int state=  mService.getState();
                    if (VDBG) Log.d(TAG, "" + hashCode() + ": getState(). Returning " + state);
                    //consider all internal states as OFF
                    if (state == BluetoothAdapter.STATE_BLE_ON
                        || state == BluetoothAdapter.STATE_BLE_TURNING_ON
                        || state == BluetoothAdapter.STATE_BLE_TURNING_OFF) {
                        if (VDBG) Log.d(TAG, "Consider internal state as OFF");
                        state = BluetoothAdapter.STATE_OFF;
                    }
                    return state;
                }
                // TODO(BT) there might be a small gap during STATE_TURNING_ON that
                //          mService is null, handle that case
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return STATE_OFF;
    }

    /**
     * Get the current state of the local Bluetooth adapter
     * <p>This returns current internal state of Adapter including LE ON/OFF
     *
     * <p>Possible return values are
     * {@link #STATE_OFF},
     * {@link #STATE_BLE_TURNING_ON},
     * {@link #STATE_BLE_ON},
     * {@link #STATE_TURNING_ON},
     * {@link #STATE_ON},
     * {@link #STATE_TURNING_OFF},
     * {@link #STATE_BLE_TURNING_OFF}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return current state of Bluetooth adapter
     * @hide
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    @AdapterState
    public int getLeState() {
        try {
            synchronized(mManagerCallback) {
                if (mService != null)
                {
                    int state=  mService.getState();
                    if (VDBG) Log.d(TAG,"getLeState() returning " + state);
                    return state;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
        return BluetoothAdapter.STATE_OFF;
    }

    boolean getLeAccess() {
        if(getLeState() == STATE_ON)
            return true;

        else if (getLeState() == STATE_BLE_ON)
            return true; // TODO: FILTER SYSTEM APPS HERE <--

        return false;
    }

    /**
     * Turn on the local Bluetooth adapter&mdash;do not use without explicit
     * user action to turn on Bluetooth.
     * <p>This powers on the underlying Bluetooth hardware, and starts all
     * Bluetooth system services.
     * <p class="caution"><strong>Bluetooth should never be enabled without
     * direct user consent</strong>. If you want to turn on Bluetooth in order
     * to create a wireless connection, you should use the {@link
     * #ACTION_REQUEST_ENABLE} Intent, which will raise a dialog that requests
     * user permission to turn on Bluetooth. The {@link #enable()} method is
     * provided only for applications that include a user interface for changing
     * system settings, such as a "power manager" app.</p>
     * <p>This is an asynchronous call: it will return immediately, and
     * clients should listen for {@link #ACTION_STATE_CHANGED}
     * to be notified of subsequent adapter state changes. If this call returns
     * true, then the adapter state will immediately transition from {@link
     * #STATE_OFF} to {@link #STATE_TURNING_ON}, and some time
     * later transition to either {@link #STATE_OFF} or {@link
     * #STATE_ON}. If this call returns false then there was an
     * immediate problem that will prevent the adapter from being turned on -
     * such as Airplane mode, or the adapter is already turned on.
     * <p>Requires the {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission
     *
     * @return true to indicate adapter startup has begun, or false on
     *         immediate error
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean enable() {
        android.util.SeempLog.record(56);
        int state = STATE_OFF;
        if (isEnabled() == true){
            if (DBG) Log.d(TAG, "enable(): BT is already enabled..!");
            return true;
        }
        //Use service interface to get the exact state
        if (mService != null) {
            try {
               state = mService.getState();
            } catch (RemoteException e) {Log.e(TAG, "", e);}
        }

        if (state == BluetoothAdapter.STATE_BLE_ON) {
                Log.e(TAG, "BT is in BLE_ON State");
                notifyUserAction(true);
                return true;
        }
        try {
            return mManagerService.enable(ActivityThread.currentPackageName());
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Turn off the local Bluetooth adapter&mdash;do not use without explicit
     * user action to turn off Bluetooth.
     * <p>This gracefully shuts down all Bluetooth connections, stops Bluetooth
     * system services, and powers down the underlying Bluetooth hardware.
     * <p class="caution"><strong>Bluetooth should never be disabled without
     * direct user consent</strong>. The {@link #disable()} method is
     * provided only for applications that include a user interface for changing
     * system settings, such as a "power manager" app.</p>
     * <p>This is an asynchronous call: it will return immediately, and
     * clients should listen for {@link #ACTION_STATE_CHANGED}
     * to be notified of subsequent adapter state changes. If this call returns
     * true, then the adapter state will immediately transition from {@link
     * #STATE_ON} to {@link #STATE_TURNING_OFF}, and some time
     * later transition to either {@link #STATE_OFF} or {@link
     * #STATE_ON}. If this call returns false then there was an
     * immediate problem that will prevent the adapter from being turned off -
     * such as the adapter already being turned off.
     * <p>Requires the {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission
     *
     * @return true to indicate adapter shutdown has begun, or false on
     *         immediate error
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean disable() {
        android.util.SeempLog.record(57);
        try {
            return mManagerService.disable(true);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Turn off the local Bluetooth adapter and don't persist the setting.
     *
     * <p>Requires the {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission
     *
     * @return true to indicate adapter shutdown has begun, or false on
     *         immediate error
     * @hide
     */
    public boolean disable(boolean persist) {
        android.util.SeempLog.record(57);

        try {
            return mManagerService.disable(persist);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Returns the hardware address of the local Bluetooth adapter.
     * <p>For example, "00:11:22:AA:BB:CC".
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return Bluetooth hardware address as string
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public String getAddress() {
        try {
            return mManagerService.getAddress();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get the friendly Bluetooth name of the local Bluetooth adapter.
     * <p>This name is visible to remote Bluetooth devices.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return the Bluetooth name, or null on error
     */
    public String getName() {
        try {
            return mManagerService.getName();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * enable or disable Bluetooth HCI snoop log.
     *
     * <p>Requires the {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * permission
     *
     * @return true to indicate configure HCI log successfully, or false on
     *         immediate error
     * @hide
     */
    public boolean configHciSnoopLog(boolean enable) {
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.configHciSnoopLog(enable);
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Factory reset bluetooth settings.
     *
     * <p>Requires the {@link android.Manifest.permission#BLUETOOTH_PRIVILEGED}
     * permission
     *
     * @return true to indicate that the config file was successfully cleared
     *
     * @hide
     */
    public boolean factoryReset() {
        try {
            if (mService != null) {
                return mService.factoryReset();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get the UUIDs supported by the local Bluetooth adapter.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return the UUIDs supported by the local Bluetooth Adapter.
     * @hide
     */
    public ParcelUuid[] getUuids() {
        if (getState() != STATE_ON) return null;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.getUuids();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Set the friendly Bluetooth name of the local Bluetooth adapter.
     * <p>This name is visible to remote Bluetooth devices.
     * <p>Valid Bluetooth names are a maximum of 248 bytes using UTF-8
     * encoding, although many remote devices can only display the first
     * 40 characters, and some may be limited to just 20.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return false. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     *
     * @param name a valid Bluetooth name
     * @return     true if the name was set, false otherwise
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean setName(String name) {
        if (getState() != STATE_ON) return false;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.setName(name);
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get the current Bluetooth scan mode of the local Bluetooth adapter.
     * <p>The Bluetooth scan mode determines if the local adapter is
     * connectable and/or discoverable from remote Bluetooth devices.
     * <p>Possible values are:
     * {@link #SCAN_MODE_NONE},
     * {@link #SCAN_MODE_CONNECTABLE},
     * {@link #SCAN_MODE_CONNECTABLE_DISCOVERABLE}.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return {@link #SCAN_MODE_NONE}. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return scan mode
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    @ScanMode
    public int getScanMode() {
        if (getState() != STATE_ON) return SCAN_MODE_NONE;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.getScanMode();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return SCAN_MODE_NONE;
    }

    /**
     * Set the Bluetooth scan mode of the local Bluetooth adapter.
     * <p>The Bluetooth scan mode determines if the local adapter is
     * connectable and/or discoverable from remote Bluetooth devices.
     * <p>For privacy reasons, discoverable mode is automatically turned off
     * after <code>duration</code> seconds. For example, 120 seconds should be
     * enough for a remote device to initiate and complete its discovery
     * process.
     * <p>Valid scan mode values are:
     * {@link #SCAN_MODE_NONE},
     * {@link #SCAN_MODE_CONNECTABLE},
     * {@link #SCAN_MODE_CONNECTABLE_DISCOVERABLE}.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return false. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     * <p>Requires {@link android.Manifest.permission#WRITE_SECURE_SETTINGS}
     * <p>Applications cannot set the scan mode. They should use
     * <code>startActivityForResult(
     * BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE})
     * </code>instead.
     *
     * @param mode valid scan mode
     * @param duration time in seconds to apply scan mode, only used for
     *                 {@link #SCAN_MODE_CONNECTABLE_DISCOVERABLE}
     * @return     true if the scan mode was set, false otherwise
     * @hide
     */
    public boolean setScanMode(@ScanMode int mode, int duration) {
        if (getState() != STATE_ON) return false;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.setScanMode(mode, duration);
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /** @hide */
    public boolean setScanMode(int mode) {
        if (getState() != STATE_ON) return false;
        /* getDiscoverableTimeout() to use the latest from NV than use 0 */
        return setScanMode(mode, getDiscoverableTimeout());
    }

    /** @hide */
    public int getDiscoverableTimeout() {
        if (getState() != STATE_ON) return -1;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.getDiscoverableTimeout();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return -1;
    }

    /** @hide */
    public void setDiscoverableTimeout(int timeout) {
        if (getState() != STATE_ON) return;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) mService.setDiscoverableTimeout(timeout);
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
    }

    /**
     * Start the remote device discovery process.
     * <p>The discovery process usually involves an inquiry scan of about 12
     * seconds, followed by a page scan of each new device to retrieve its
     * Bluetooth name.
     * <p>This is an asynchronous call, it will return immediately. Register
     * for {@link #ACTION_DISCOVERY_STARTED} and {@link
     * #ACTION_DISCOVERY_FINISHED} intents to determine exactly when the
     * discovery starts and completes. Register for {@link
     * BluetoothDevice#ACTION_FOUND} to be notified as remote Bluetooth devices
     * are found.
     * <p>Device discovery is a heavyweight procedure. New connections to
     * remote Bluetooth devices should not be attempted while discovery is in
     * progress, and existing connections will experience limited bandwidth
     * and high latency. Use {@link #cancelDiscovery()} to cancel an ongoing
     * discovery. Discovery is not managed by the Activity,
     * but is run as a system service, so an application should always call
     * {@link BluetoothAdapter#cancelDiscovery()} even if it
     * did not directly request a discovery, just to be sure.
     * <p>Device discovery will only find remote devices that are currently
     * <i>discoverable</i> (inquiry scan enabled). Many Bluetooth devices are
     * not discoverable by default, and need to be entered into a special mode.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return false. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return true on success, false on error
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean startDiscovery() {
        android.util.SeempLog.record(58);
        if (getState() != STATE_ON) return false;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.startDiscovery();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Cancel the current device discovery process.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     * <p>Because discovery is a heavyweight procedure for the Bluetooth
     * adapter, this method should always be called before attempting to connect
     * to a remote device with {@link
     * android.bluetooth.BluetoothSocket#connect()}. Discovery is not managed by
     * the  Activity, but is run as a system service, so an application should
     * always call cancel discovery even if it did not directly request a
     * discovery, just to be sure.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return false. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     *
     * @return true on success, false on error
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean cancelDiscovery() {
        if (getState() != STATE_ON) return false;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.cancelDiscovery();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Return true if the local Bluetooth adapter is currently in the device
     * discovery process.
     * <p>Device discovery is a heavyweight procedure. New connections to
     * remote Bluetooth devices should not be attempted while discovery is in
     * progress, and existing connections will experience limited bandwidth
     * and high latency. Use {@link #cancelDiscovery()} to cancel an ongoing
     * discovery.
     * <p>Applications can also register for {@link #ACTION_DISCOVERY_STARTED}
     * or {@link #ACTION_DISCOVERY_FINISHED} to be notified when discovery
     * starts or completes.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return false. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     *
     * @return true if discovering
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public boolean isDiscovering() {
        if (getState() != STATE_ON) return false;
        try {
            synchronized(mManagerCallback) {
                if (mService != null ) return mService.isDiscovering();
            }
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Return true if the multi advertisement is supported by the chipset
     *
     * @return true if Multiple Advertisement feature is supported
     */
    public boolean isMultipleAdvertisementSupported() {
        if (getState() != STATE_ON) return false;
        try {
            return mService.isMultiAdvertisementSupported();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to get isMultipleAdvertisementSupported, error: ", e);
        }
        return false;
    }

    /**
     * Returns {@code true} if BLE scan is always available, {@code false} otherwise. <p>
     *
     * If this returns {@code true}, application can issue {@link BluetoothLeScanner#startScan} and
     * fetch scan results even when Bluetooth is turned off.<p>
     *
     * To change this setting, use {@link #ACTION_REQUEST_BLE_SCAN_ALWAYS_AVAILABLE}.
     *
     * @hide
     */
    @SystemApi
    public boolean isBleScanAlwaysAvailable() {
        try {
            return mManagerService.isBleScanAlwaysAvailable();
        } catch (RemoteException e) {
            Log.e(TAG, "remote expection when calling isBleScanAlwaysAvailable", e);
            return false;
        }
    }

    /**
     * Returns whether peripheral mode is supported.
     *
     * @hide
     */
    public boolean isPeripheralModeSupported() {
        if (getState() != STATE_ON) return false;
        try {
            return mService.isPeripheralModeSupported();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to get peripheral mode capability: ", e);
        }
        return false;
    }

    /**
     * Return true if offloaded filters are supported
     *
     * @return true if chipset supports on-chip filtering
     */
    public boolean isOffloadedFilteringSupported() {
        if (!getLeAccess()) return false;
        try {
            return mService.isOffloadedFilteringSupported();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to get isOffloadedFilteringSupported, error: ", e);
        }
        return false;
    }

    /**
     * Return true if offloaded scan batching is supported
     *
     * @return true if chipset supports on-chip scan batching
     */
    public boolean isOffloadedScanBatchingSupported() {
        if (!getLeAccess()) return false;
        try {
            return mService.isOffloadedScanBatchingSupported();
        } catch (RemoteException e) {
            Log.e(TAG, "failed to get isOffloadedScanBatchingSupported, error: ", e);
        }
        return false;
    }

    /**
     * Return true if hardware has entries available for matching beacons
     *
     * @return true if there are hw entries available for matching beacons
     * @hide
     */
    public boolean isHardwareTrackingFiltersAvailable() {
        if (!getLeAccess()) return false;
        try {
            IBluetoothGatt iGatt = mManagerService.getBluetoothGatt();
            if (iGatt == null) {
                // BLE is not supported
                return false;
            }
            return (iGatt.numHwTrackFiltersAvailable() != 0);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }
        return false;
    }

    /**
     * Return the record of {@link BluetoothActivityEnergyInfo} object that
     * has the activity and energy info. This can be used to ascertain what
     * the controller has been up to, since the last sample.
     * @param updateType Type of info, cached vs refreshed.
     *
     * @return a record with {@link BluetoothActivityEnergyInfo} or null if
     * report is unavailable or unsupported
     * @hide
     */
    public BluetoothActivityEnergyInfo getControllerActivityEnergyInfo(int updateType) {
        if (getState() != STATE_ON) return null;
        try {
            BluetoothActivityEnergyInfo record;
            if (!mService.isActivityAndEnergyReportingSupported()) {
                return null;
            }
            synchronized(this) {
                if (updateType == ACTIVITY_ENERGY_INFO_REFRESHED) {
                    mService.getActivityEnergyInfoFromController();
                    wait(CONTROLLER_ENERGY_UPDATE_TIMEOUT_MILLIS);
                }
                record = mService.reportActivityInfo();
                if (record.isValid()) {
                    return record;
                } else {
                    return null;
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "getControllerActivityEnergyInfoCallback wait interrupted: " + e);
        } catch (RemoteException e) {
            Log.e(TAG, "getControllerActivityEnergyInfoCallback: " + e);
        }
        return null;
    }

    /**
     * Return the set of {@link BluetoothDevice} objects that are bonded
     * (paired) to the local adapter.
     * <p>If Bluetooth state is not {@link #STATE_ON}, this API
     * will return an empty set. After turning on Bluetooth,
     * wait for {@link #ACTION_STATE_CHANGED} with {@link #STATE_ON}
     * to get the updated value.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     *
     * @return unmodifiable set of {@link BluetoothDevice}, or null on error
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public Set<BluetoothDevice> getBondedDevices() {
        android.util.SeempLog.record(61);
        if (getState() != STATE_ON) {
            return toDeviceSet(new BluetoothDevice[0]);
        }
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return toDeviceSet(mService.getBondedDevices());
            }
            return toDeviceSet(new BluetoothDevice[0]);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get the current connection state of the local Bluetooth adapter.
     * This can be used to check whether the local Bluetooth adapter is connected
     * to any profile of any other remote Bluetooth Device.
     *
     * <p> Use this function along with {@link #ACTION_CONNECTION_STATE_CHANGED}
     * intent to get the connection state of the adapter.
     *
     * @return One of {@link #STATE_CONNECTED}, {@link #STATE_DISCONNECTED},
     * {@link #STATE_CONNECTING} or {@link #STATE_DISCONNECTED}
     *
     * @hide
     */
    public int getConnectionState() {
        if (getState() != STATE_ON) return BluetoothAdapter.STATE_DISCONNECTED;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.getAdapterConnectionState();
            }
        } catch (RemoteException e) {Log.e(TAG, "getConnectionState:", e);}
        return BluetoothAdapter.STATE_DISCONNECTED;
    }

    /**
     * Get the current connection state of a profile.
     * This function can be used to check whether the local Bluetooth adapter
     * is connected to any remote device for a specific profile.
     * Profile can be one of {@link BluetoothProfile#HEALTH}, {@link BluetoothProfile#HEADSET},
     * {@link BluetoothProfile#A2DP}.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     *
     * <p> Return value can be one of
     * {@link BluetoothProfile#STATE_DISCONNECTED},
     * {@link BluetoothProfile#STATE_CONNECTING},
     * {@link BluetoothProfile#STATE_CONNECTED},
     * {@link BluetoothProfile#STATE_DISCONNECTING}
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public int getProfileConnectionState(int profile) {
        android.util.SeempLog.record(64);
        if (getState() != STATE_ON) return BluetoothProfile.STATE_DISCONNECTED;
        try {
            synchronized(mManagerCallback) {
                if (mService != null) return mService.getProfileConnectionState(profile);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getProfileConnectionState:", e);
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    /**
     * Create a listening, secure RFCOMM Bluetooth socket.
     * <p>A remote device connecting to this socket will be authenticated and
     * communication on this socket will be encrypted.
     * <p>Use {@link BluetoothServerSocket#accept} to retrieve incoming
     * connections from a listening {@link BluetoothServerSocket}.
     * <p>Valid RFCOMM channels are in range 1 to 30.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * @param channel RFCOMM channel to listen on
     * @return a listening RFCOMM BluetoothServerSocket
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions, or channel in use.
     * @hide
     */
    public BluetoothServerSocket listenUsingRfcommOn(int channel) throws IOException {
        return listenUsingRfcommOn(channel, false, false);
    }

    /**
     * Create a listening, secure RFCOMM Bluetooth socket.
     * <p>A remote device connecting to this socket will be authenticated and
     * communication on this socket will be encrypted.
     * <p>Use {@link BluetoothServerSocket#accept} to retrieve incoming
     * connections from a listening {@link BluetoothServerSocket}.
     * <p>Valid RFCOMM channels are in range 1 to 30.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     * <p>To auto assign a channel without creating a SDP record use
     * {@link SOCKET_CHANNEL_AUTO_STATIC_NO_SDP} as channel number.
     * @param channel RFCOMM channel to listen on
     * @param mitm    enforce man-in-the-middle protection for authentication.
     * @param min16DigitPin enforce a pin key length og minimum 16 digit for sec mode 2 connections.
     * @return a listening RFCOMM BluetoothServerSocket
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions, or channel in use.
     * @hide
     */
    public BluetoothServerSocket listenUsingRfcommOn(int channel, boolean mitm,
            boolean min16DigitPin)
            throws IOException {
        BluetoothServerSocket socket = new BluetoothServerSocket(
                BluetoothSocket.TYPE_RFCOMM, true, true, channel, mitm, min16DigitPin);
        int errno = socket.mSocket.bindListen();
        if (channel == SOCKET_CHANNEL_AUTO_STATIC_NO_SDP) {
            socket.setChannel(socket.mSocket.getPort());
        }
        if (errno != 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
            throw new IOException("Error: " + errno);
        }
        return socket;
    }

    /**
     * Create a listening, secure RFCOMM Bluetooth socket with Service Record.
     * <p>A remote device connecting to this socket will be authenticated and
     * communication on this socket will be encrypted.
     * <p>Use {@link BluetoothServerSocket#accept} to retrieve incoming
     * connections from a listening {@link BluetoothServerSocket}.
     * <p>The system will assign an unused RFCOMM channel to listen on.
     * <p>The system will also register a Service Discovery
     * Protocol (SDP) record with the local SDP server containing the specified
     * UUID, service name, and auto-assigned channel. Remote Bluetooth devices
     * can use the same UUID to query our SDP server and discover which channel
     * to connect to. This SDP record will be removed when this socket is
     * closed, or if this application closes unexpectedly.
     * <p>Use {@link BluetoothDevice#createRfcommSocketToServiceRecord} to
     * connect to this socket from another device using the same {@link UUID}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     * @param name service name for SDP record
     * @param uuid uuid for SDP record
     * @return a listening RFCOMM BluetoothServerSocket
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions, or channel in use.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public BluetoothServerSocket listenUsingRfcommWithServiceRecord(String name, UUID uuid)
            throws IOException {
        return createNewRfcommSocketAndRecord(name, uuid, true, true);
    }

    /**
     * Create a listening, insecure RFCOMM Bluetooth socket with Service Record.
     * <p>The link key is not required to be authenticated, i.e the communication may be
     * vulnerable to Man In the Middle attacks. For Bluetooth 2.1 devices,
     * the link will be encrypted, as encryption is mandartory.
     * For legacy devices (pre Bluetooth 2.1 devices) the link will not
     * be encrypted. Use {@link #listenUsingRfcommWithServiceRecord}, if an
     * encrypted and authenticated communication channel is desired.
     * <p>Use {@link BluetoothServerSocket#accept} to retrieve incoming
     * connections from a listening {@link BluetoothServerSocket}.
     * <p>The system will assign an unused RFCOMM channel to listen on.
     * <p>The system will also register a Service Discovery
     * Protocol (SDP) record with the local SDP server containing the specified
     * UUID, service name, and auto-assigned channel. Remote Bluetooth devices
     * can use the same UUID to query our SDP server and discover which channel
     * to connect to. This SDP record will be removed when this socket is
     * closed, or if this application closes unexpectedly.
     * <p>Use {@link BluetoothDevice#createRfcommSocketToServiceRecord} to
     * connect to this socket from another device using the same {@link UUID}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     * @param name service name for SDP record
     * @param uuid uuid for SDP record
     * @return a listening RFCOMM BluetoothServerSocket
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions, or channel in use.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public BluetoothServerSocket listenUsingInsecureRfcommWithServiceRecord(String name, UUID uuid)
            throws IOException {
        android.util.SeempLog.record(59);
        return createNewRfcommSocketAndRecord(name, uuid, false, false);
    }

     /**
     * Create a listening, encrypted,
     * RFCOMM Bluetooth socket with Service Record.
     * <p>The link will be encrypted, but the link key is not required to be authenticated
     * i.e the communication is vulnerable to Man In the Middle attacks. Use
     * {@link #listenUsingRfcommWithServiceRecord}, to ensure an authenticated link key.
     * <p> Use this socket if authentication of link key is not possible.
     * For example, for Bluetooth 2.1 devices, if any of the devices does not have
     * an input and output capability or just has the ability to display a numeric key,
     * a secure socket connection is not possible and this socket can be used.
     * Use {@link #listenUsingInsecureRfcommWithServiceRecord}, if encryption is not required.
     * For Bluetooth 2.1 devices, the link will be encrypted, as encryption is mandartory.
     * For more details, refer to the Security Model section 5.2 (vol 3) of
     * Bluetooth Core Specification version 2.1 + EDR.
     * <p>Use {@link BluetoothServerSocket#accept} to retrieve incoming
     * connections from a listening {@link BluetoothServerSocket}.
     * <p>The system will assign an unused RFCOMM channel to listen on.
     * <p>The system will also register a Service Discovery
     * Protocol (SDP) record with the local SDP server containing the specified
     * UUID, service name, and auto-assigned channel. Remote Bluetooth devices
     * can use the same UUID to query our SDP server and discover which channel
     * to connect to. This SDP record will be removed when this socket is
     * closed, or if this application closes unexpectedly.
     * <p>Use {@link BluetoothDevice#createRfcommSocketToServiceRecord} to
     * connect to this socket from another device using the same {@link UUID}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     * @param name service name for SDP record
     * @param uuid uuid for SDP record
     * @return a listening RFCOMM BluetoothServerSocket
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions, or channel in use.
     * @hide
     */
    public BluetoothServerSocket listenUsingEncryptedRfcommWithServiceRecord(
            String name, UUID uuid) throws IOException {
        return createNewRfcommSocketAndRecord(name, uuid, false, true);
    }


    private BluetoothServerSocket createNewRfcommSocketAndRecord(String name, UUID uuid,
            boolean auth, boolean encrypt) throws IOException {
        BluetoothServerSocket socket;
        socket = new BluetoothServerSocket(BluetoothSocket.TYPE_RFCOMM, auth,
                        encrypt, new ParcelUuid(uuid));
        socket.setServiceName(name);
        int errno = socket.mSocket.bindListen();
        if (errno != 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
            throw new IOException("Error: " + errno);
        }
        return socket;
    }

    /**
     * Construct an unencrypted, unauthenticated, RFCOMM server socket.
     * Call #accept to retrieve connections to this socket.
     * @return An RFCOMM BluetoothServerSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothServerSocket listenUsingInsecureRfcommOn(int port) throws IOException {
        BluetoothServerSocket socket = new BluetoothServerSocket(
                BluetoothSocket.TYPE_RFCOMM, false, false, port);
        int errno = socket.mSocket.bindListen();
        if(port == SOCKET_CHANNEL_AUTO_STATIC_NO_SDP) {
            socket.setChannel(socket.mSocket.getPort());
        }
        if (errno != 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
            throw new IOException("Error: " + errno);
        }
        return socket;
    }

     /**
     * Construct an encrypted, RFCOMM server socket.
     * Call #accept to retrieve connections to this socket.
     * @return An RFCOMM BluetoothServerSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothServerSocket listenUsingEncryptedRfcommOn(int port)
            throws IOException {
        BluetoothServerSocket socket = new BluetoothServerSocket(
                BluetoothSocket.TYPE_RFCOMM, false, true, port);
        int errno = socket.mSocket.bindListen();
        if(port == SOCKET_CHANNEL_AUTO_STATIC_NO_SDP) {
            socket.setChannel(socket.mSocket.getPort());
        }
        if (errno < 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
            throw new IOException("Error: " + errno);
        }
        return socket;
    }

    /**
     * Construct a SCO server socket.
     * Call #accept to retrieve connections to this socket.
     * @return A SCO BluetoothServerSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public static BluetoothServerSocket listenUsingScoOn() throws IOException {
        BluetoothServerSocket socket = new BluetoothServerSocket(
                BluetoothSocket.TYPE_SCO, false, false, -1);
        int errno = socket.mSocket.bindListen();
        if (errno < 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
        }
        return socket;
    }

    /**
     * Construct an encrypted, authenticated, L2CAP server socket.
     * Call #accept to retrieve connections to this socket.
     * <p>To auto assign a port without creating a SDP record use
     * {@link SOCKET_CHANNEL_AUTO_STATIC_NO_SDP} as port number.
     * @param port    the PSM to listen on
     * @param mitm    enforce man-in-the-middle protection for authentication.
     * @param min16DigitPin enforce a pin key length og minimum 16 digit for sec mode 2 connections.
     * @return An L2CAP BluetoothServerSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothServerSocket listenUsingL2capOn(int port, boolean mitm, boolean min16DigitPin)
            throws IOException {
        BluetoothServerSocket socket = new BluetoothServerSocket(
                BluetoothSocket.TYPE_L2CAP, true, true, port, mitm, min16DigitPin);
        int errno = socket.mSocket.bindListen();
        if(port == SOCKET_CHANNEL_AUTO_STATIC_NO_SDP) {
            socket.setChannel(socket.mSocket.getPort());
        }
        if (errno != 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
            throw new IOException("Error: " + errno);
        }
        return socket;
    }

    /**
     * Construct an encrypted, authenticated, L2CAP server socket.
     * Call #accept to retrieve connections to this socket.
     * <p>To auto assign a port without creating a SDP record use
     * {@link SOCKET_CHANNEL_AUTO_STATIC_NO_SDP} as port number.
     * @param port    the PSM to listen on
     * @return An L2CAP BluetoothServerSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothServerSocket listenUsingL2capOn(int port) throws IOException {
        return listenUsingL2capOn(port, false, false);
    }


    /**
     * Construct an insecure L2CAP server socket.
     * Call #accept to retrieve connections to this socket.
     * <p>To auto assign a port without creating a SDP record use
     * {@link SOCKET_CHANNEL_AUTO_STATIC_NO_SDP} as port number.
     * @param port    the PSM to listen on
     * @return An L2CAP BluetoothServerSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothServerSocket listenUsingInsecureL2capOn(int port) throws IOException {
        BluetoothServerSocket socket = new BluetoothServerSocket(
                BluetoothSocket.TYPE_L2CAP, false, false, port, false, false);
        int errno = socket.mSocket.bindListen();
        if(port == SOCKET_CHANNEL_AUTO_STATIC_NO_SDP) {
            socket.setChannel(socket.mSocket.getPort());
        }
        if (errno != 0) {
            //TODO(BT): Throw the same exception error code
            // that the previous code was using.
            //socket.mSocket.throwErrnoNative(errno);
            throw new IOException("Error: " + errno);
        }
        return socket;

    }

    /**
     * Create a client side Message Access Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * WARNING: This API requires removeSdpRecord() to be called, to avoid leaking resources!
     *          A second call to this function - either from two different apps or from the
     *          same app, without first calling removeSdpRecord() - will make the device
     *          break the Bluetooth spec, which could lead to severe IOP issues.
     * @param serviceName   The textual name of the service
     * @param rfcommChannel The RFCOMM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     * @param l2capPsm      The L2CAP PSM channel that clients can connect to
     *                      (obtain from BluetoothServerSocket)
     *                      Supply -1 to omit the L2CAP PSM from the record.
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      MAP specification)
     * @param features      The feature bit mask (As specified in the Bluetooth
     *                       MAP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     *          returns -1 if an error occure and the record was not created.
     * @hide
     */
    public int createMapMnsSdpRecord(String serviceName, int rfcommChannel,
            int l2capPsm, int version, int features) {
        try {
            return mService.createMapMnsSdpRecord(serviceName, rfcommChannel,
                    l2capPsm, version, features);
        } catch (RemoteException e) {
            Log.e(TAG, "createMapMnsSdpRecord: ", e);
        }
        return -1;
    }

    /**
     * Create a client side Phonebook Access Profile Service Record.
     * Create the record once, and reuse it for all connections.
     * If changes to a record is needed remove the old record using {@link removeSdpRecord}
     * and then create a new one.
     * WARNING: This API requires removeSdpRecord() to be called, to avoid leaking resources!
     *          A second call to this function - either from two different apps or from the
     *          same app, without first calling removeSdpRecord() - will make the device
     *          break the Bluetooth spec, which could lead to severe IOP issues.
     * @param serviceName   The textual name of the service
     * @param version       The Profile version number (As specified in the Bluetooth
     *                      PBAP specification)
     * @return a handle to the record created. The record can be removed again
     *          using {@link removeSdpRecord}(). The record is not linked to the
     *          creation/destruction of BluetoothSockets, hence SDP record cleanup
     *          is a separate process.
     *          returns -1 if an error occure and the record was not created.
     * @hide
     */
    public int createPbapPceSdpRecord(String serviceName, int version) {
        try {
            return mService.createPbapPceSdpRecord(serviceName, version);
        } catch (RemoteException e) {
            Log.e(TAG, "createPbapPceSdpRecord: ", e);
        }
        return -1;
    }

    /**
     * Remove a SDP record created using createSdpRecord().
     * This function shall be called before a new call to createSdpRecord for the same record
     * type can be made, unless the record type created supports multiple instances.
     * @param recordHandle handle of the record to remove - provided by createSdpRecord()
     * @return true if success
     * @hide
     */
    public boolean removeSdpRecord(int recordHandle){
        try {
            return mService.removeSdpRecord(recordHandle);
        } catch (RemoteException e) {
            Log.e(TAG, "removeSdpRecord: ", e);
        }
        return false;
    }

    /**
     * Read the local Out of Band Pairing Data
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return Pair<byte[], byte[]> of Hash and Randomizer
     *
     * @hide
     */
    public Pair<byte[], byte[]> readOutOfBandData() {
        if (getState() != STATE_ON) return null;
        //TODO(BT
        /*
        try {
            byte[] hash;
            byte[] randomizer;

            byte[] ret = mService.readOutOfBandData();

            if (ret  == null || ret.length != 32) return null;

            hash = Arrays.copyOfRange(ret, 0, 16);
            randomizer = Arrays.copyOfRange(ret, 16, 32);

            if (DBG) {
                Log.d(TAG, "readOutOfBandData:" + Arrays.toString(hash) +
                  ":" + Arrays.toString(randomizer));
            }
            return new Pair<byte[], byte[]>(hash, randomizer);

        } catch (RemoteException e) {Log.e(TAG, "", e);}*/
        return null;
    }

    /**
     * Get the profile proxy object associated with the profile.
     *
     * <p>Profile can be one of {@link BluetoothProfile#HEALTH}, {@link BluetoothProfile#HEADSET},
     * {@link BluetoothProfile#A2DP}, {@link BluetoothProfile#GATT}, or
     * {@link BluetoothProfile#GATT_SERVER}. Clients must implement
     * {@link BluetoothProfile.ServiceListener} to get notified of
     * the connection status and to get the proxy object.
     *
     * @param context Context of the application
     * @param listener The service Listener for connection callbacks.
     * @param profile The Bluetooth profile; either {@link BluetoothProfile#HEALTH},
     *                {@link BluetoothProfile#HEADSET}, {@link BluetoothProfile#A2DP}.
     *                {@link BluetoothProfile#GATT} or {@link BluetoothProfile#GATT_SERVER}.
     * @return true on success, false on error
     */
    public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener,
                                   int profile) {
        if (context == null || listener == null) return false;

        if (profile == BluetoothProfile.HEADSET) {
            BluetoothHeadset headset = new BluetoothHeadset(context, listener);
            return true;
        } else if (profile == BluetoothProfile.A2DP) {
            BluetoothA2dp a2dp = new BluetoothA2dp(context, listener);
            return true;
        } else if (profile == BluetoothProfile.A2DP_SINK) {
            BluetoothA2dpSink a2dpSink = new BluetoothA2dpSink(context, listener);
            return true;
        } else if (profile == BluetoothProfile.AVRCP_CONTROLLER) {
            BluetoothAvrcpController avrcp = new BluetoothAvrcpController(context, listener);
            return true;
        } else if (profile == BluetoothProfile.INPUT_DEVICE) {
            BluetoothInputDevice iDev = new BluetoothInputDevice(context, listener);
            return true;
        } else if (profile == BluetoothProfile.PAN) {
            BluetoothPan pan = new BluetoothPan(context, listener);
            return true;
        } else if (profile == BluetoothProfile.DUN) {
            BluetoothDun dun = new BluetoothDun(context, listener);
            return true;
        } else if (profile == BluetoothProfile.HEALTH) {
            BluetoothHealth health = new BluetoothHealth(context, listener);
            return true;
        } else if (profile == BluetoothProfile.MAP) {
            BluetoothMap map = new BluetoothMap(context, listener);
            return true;
        } else if (profile == BluetoothProfile.HEADSET_CLIENT) {
            BluetoothHeadsetClient headsetClient = new BluetoothHeadsetClient(context, listener);
            return true;
        } else if (profile == BluetoothProfile.SAP) {
            BluetoothSap sap = new BluetoothSap(context, listener);
            return true;
        } else if (profile == BluetoothProfile.HID_DEVICE) {
            BluetoothHidDevice hidd = new BluetoothHidDevice(context, listener);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Close the connection of the profile proxy to the Service.
     *
     * <p> Clients should call this when they are no longer using
     * the proxy obtained from {@link #getProfileProxy}.
     * Profile can be one of  {@link BluetoothProfile#HEALTH}, {@link BluetoothProfile#HEADSET} or
     * {@link BluetoothProfile#A2DP}
     *
     * @param profile
     * @param proxy Profile proxy object
     */
    public void closeProfileProxy(int profile, BluetoothProfile proxy) {
        if (proxy == null) return;

        switch (profile) {
            case BluetoothProfile.HEADSET:
                BluetoothHeadset headset = (BluetoothHeadset)proxy;
                headset.close();
                break;
            case BluetoothProfile.A2DP:
                BluetoothA2dp a2dp = (BluetoothA2dp)proxy;
                a2dp.close();
                break;
            case BluetoothProfile.A2DP_SINK:
                BluetoothA2dpSink a2dpSink = (BluetoothA2dpSink)proxy;
                a2dpSink.close();
                break;
            case BluetoothProfile.AVRCP_CONTROLLER:
                BluetoothAvrcpController avrcp = (BluetoothAvrcpController)proxy;
                avrcp.close();
                break;
            case BluetoothProfile.INPUT_DEVICE:
                BluetoothInputDevice iDev = (BluetoothInputDevice)proxy;
                iDev.close();
                break;
            case BluetoothProfile.PAN:
                BluetoothPan pan = (BluetoothPan)proxy;
                pan.close();
                break;
            case BluetoothProfile.DUN:
                BluetoothDun dun = (BluetoothDun)proxy;
                dun.close();
                break;
            case BluetoothProfile.HEALTH:
                BluetoothHealth health = (BluetoothHealth)proxy;
                health.close();
                break;
           case BluetoothProfile.GATT:
                BluetoothGatt gatt = (BluetoothGatt)proxy;
                gatt.close();
                break;
            case BluetoothProfile.GATT_SERVER:
                BluetoothGattServer gattServer = (BluetoothGattServer)proxy;
                gattServer.close();
                break;
            case BluetoothProfile.MAP:
                BluetoothMap map = (BluetoothMap)proxy;
                map.close();
                break;
            case BluetoothProfile.HEADSET_CLIENT:
                BluetoothHeadsetClient headsetClient = (BluetoothHeadsetClient)proxy;
                headsetClient.close();
                break;
            case BluetoothProfile.SAP:
                BluetoothSap sap = (BluetoothSap)proxy;
                sap.close();
                break;
            case BluetoothProfile.HID_DEVICE:
                BluetoothHidDevice hidd = (BluetoothHidDevice) proxy;
                hidd.close();
                break;
        }
    }

    final private IBluetoothManagerCallback mManagerCallback =
        new IBluetoothManagerCallback.Stub() {
            public void onBluetoothServiceUp(IBluetooth bluetoothService) {
                if (VDBG) Log.d(TAG, "onBluetoothServiceUp: " + bluetoothService);
                synchronized (mManagerCallback) {
                    mService = bluetoothService;
                    synchronized (mProxyServiceStateCallbacks) {
                        for (IBluetoothManagerCallback cb : mProxyServiceStateCallbacks ){
                            try {
                                if (cb != null) {
                                    cb.onBluetoothServiceUp(bluetoothService);
                                } else {
                                    Log.d(TAG, "onBluetoothServiceUp: cb is null!!!");
                                }
                            } catch (Exception e)  { Log.e(TAG,"",e);}
                        }
                    }
                }
            }

            public void onBluetoothServiceDown() {
                Log.d(TAG, "onBluetoothServiceDown: " + mService);
                synchronized (mManagerCallback) {
                    mService = null;
                    if (mLeScanClients != null) mLeScanClients.clear();
                    if (sBluetoothLeAdvertiser != null) sBluetoothLeAdvertiser.cleanup();
                    if (sBluetoothLeScanner != null) sBluetoothLeScanner.cleanup();
                    synchronized (mProxyServiceStateCallbacks) {
                        Log.d(TAG, "onBluetoothServiceDown: Sending callbacks to " + mProxyServiceStateCallbacks.size() + " clients");
                        for (IBluetoothManagerCallback cb : mProxyServiceStateCallbacks ){
                            try {
                                if (cb != null) {
                                    cb.onBluetoothServiceDown();
                                } else {
                                    Log.d(TAG, "onBluetoothServiceDown: cb is null!!!");
                                }
                            } catch (Exception e)  { Log.e(TAG,"",e);}
                        }
                    }
                }
                Log.d(TAG, "onBluetoothServiceDown: Finished sending callbacks to registered clients");
            }

            public void onBrEdrDown() {
                if (VDBG) Log.i(TAG, "on QBrEdrDown: ");
            }
    };

    /**
     * Enable the Bluetooth Adapter, but don't auto-connect devices
     * and don't persist state. Only for use by system applications.
     * @hide
     */
    public boolean enableNoAutoConnect() {
        if (isEnabled() == true){
            if (DBG) Log.d(TAG, "enableNoAutoConnect(): BT is already enabled..!");
            return true;
        }
        try {
            return mManagerService.enableNoAutoConnect();
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Enable control of the Bluetooth Adapter for a single application.
     *
     * <p>Some applications need to use Bluetooth for short periods of time to
     * transfer data but don't want all the associated implications like
     * automatic connection to headsets etc.
     *
     * <p> Multiple applications can call this. This is reference counted and
     * Bluetooth disabled only when no one else is using it. There will be no UI
     * shown to the user while bluetooth is being enabled. Any user action will
     * override this call. For example, if user wants Bluetooth on and the last
     * user of this API wanted to disable Bluetooth, Bluetooth will not be
     * turned off.
     *
     * <p> This API is only meant to be used by internal applications. Third
     * party applications but use {@link #enable} and {@link #disable} APIs.
     *
     * <p> If this API returns true, it means the callback will be called.
     * The callback will be called with the current state of Bluetooth.
     * If the state is not what was requested, an internal error would be the
     * reason. If Bluetooth is already on and if this function is called to turn
     * it on, the api will return true and a callback will be called.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @param on True for on, false for off.
     * @param callback The callback to notify changes to the state.
     * @hide
     */
    public boolean changeApplicationBluetoothState(boolean on,
                                                   BluetoothStateChangeCallback callback) {
        if (callback == null) return false;

        //TODO(BT)
        /*
        try {
            return mService.changeApplicationBluetoothState(on, new
                    StateChangeCallbackWrapper(callback), new Binder());
        } catch (RemoteException e) {
            Log.e(TAG, "changeBluetoothState", e);
        }*/
        return false;
    }

    /**
     * @hide
     */
    public interface BluetoothStateChangeCallback {
        public void onBluetoothStateChange(boolean on);
    }

    /**
     * @hide
     */
    public class StateChangeCallbackWrapper extends IBluetoothStateChangeCallback.Stub {
        private BluetoothStateChangeCallback mCallback;

        StateChangeCallbackWrapper(BluetoothStateChangeCallback
                callback) {
            mCallback = callback;
        }

        @Override
        public void onBluetoothStateChange(boolean on) {
            mCallback.onBluetoothStateChange(on);
        }
    }

    private Set<BluetoothDevice> toDeviceSet(BluetoothDevice[] devices) {
        Set<BluetoothDevice> deviceSet = new HashSet<BluetoothDevice>(Arrays.asList(devices));
        return Collections.unmodifiableSet(deviceSet);
    }

    protected void finalize() throws Throwable {
        try {
            mManagerService.unregisterAdapter(mManagerCallback);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        } finally {
            super.finalize();
        }
    }


    /**
     * Validate a String Bluetooth address, such as "00:43:A8:23:10:F0"
     * <p>Alphabetic characters must be uppercase to be valid.
     *
     * @param address Bluetooth address as string
     * @return true if the address is valid, false otherwise
     */
    public static boolean checkBluetoothAddress(String address) {
        if (address == null || address.length() != ADDRESS_LENGTH) {
            return false;
        }
        for (int i = 0; i < ADDRESS_LENGTH; i++) {
            char c = address.charAt(i);
            switch (i % 3) {
            case 0:
            case 1:
                if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                    // hex character, OK
                    break;
                }
                return false;
            case 2:
                if (c == ':') {
                    break;  // OK
                }
                return false;
            }
        }
        return true;
    }

    /*package*/ IBluetoothManager getBluetoothManager() {
            return mManagerService;
    }

    final private ArrayList<IBluetoothManagerCallback> mProxyServiceStateCallbacks = new ArrayList<IBluetoothManagerCallback>();

    /*package*/ IBluetooth getBluetoothService(IBluetoothManagerCallback cb) {
        synchronized (mProxyServiceStateCallbacks) {
            if (cb == null) {
                Log.w(TAG, "getBluetoothService() called with no BluetoothManagerCallback");
            } else if (!mProxyServiceStateCallbacks.contains(cb)) {
                mProxyServiceStateCallbacks.add(cb);
            }
        }
        return mService;
    }

    /*package*/ void removeServiceStateCallback(IBluetoothManagerCallback cb) {
        synchronized (mProxyServiceStateCallbacks) {
            mProxyServiceStateCallbacks.remove(cb);
        }
    }

    /**
     * Callback interface used to deliver LE scan results.
     *
     * @see #startLeScan(LeScanCallback)
     * @see #startLeScan(UUID[], LeScanCallback)
     */
    public interface LeScanCallback {
        /**
         * Callback reporting an LE device found during a device scan initiated
         * by the {@link BluetoothAdapter#startLeScan} function.
         *
         * @param device Identifies the remote device
         * @param rssi The RSSI value for the remote device as reported by the
         *             Bluetooth hardware. 0 if no RSSI value is available.
         * @param scanRecord The content of the advertisement record offered by
         *                   the remote device.
         */
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord);
    }

    /**
     * Starts a scan for Bluetooth LE devices.
     *
     * <p>Results of the scan are reported using the
     * {@link LeScanCallback#onLeScan} callback.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
     *
     * @param callback the callback LE scan results are delivered
     * @return true, if the scan was started successfully
     * @deprecated use {@link BluetoothLeScanner#startScan(List, ScanSettings, ScanCallback)}
     *             instead.
     */
    @Deprecated
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean startLeScan(LeScanCallback callback) {
        return startLeScan(null, callback);
    }

    /**
     * Starts a scan for Bluetooth LE devices, looking for devices that
     * advertise given services.
     *
     * <p>Devices which advertise all specified services are reported using the
     * {@link LeScanCallback#onLeScan} callback.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
     *
     * @param serviceUuids Array of services to look for
     * @param callback the callback LE scan results are delivered
     * @return true, if the scan was started successfully
     * @deprecated use {@link BluetoothLeScanner#startScan(List, ScanSettings, ScanCallback)}
     *             instead.
     */
    @Deprecated
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public boolean startLeScan(final UUID[] serviceUuids, final LeScanCallback callback) {
        if (DBG) Log.d(TAG, "startLeScan(): " + serviceUuids);
        if (callback == null) {
            if (DBG) Log.e(TAG, "startLeScan: null callback");
            return false;
        }
        BluetoothLeScanner scanner = getBluetoothLeScanner();
        if (scanner == null) {
            if (DBG) Log.e(TAG, "startLeScan: cannot get BluetoothLeScanner");
            return false;
        }

        synchronized(mLeScanClients) {
            if (mLeScanClients.containsKey(callback)) {
                if (DBG) Log.e(TAG, "LE Scan has already started");
                return false;
            }

            try {
                IBluetoothGatt iGatt = mManagerService.getBluetoothGatt();
                if (iGatt == null) {
                    // BLE is not supported
                    return false;
                }

                ScanCallback scanCallback = new ScanCallback() {
                    @Override
                    public void onScanResult(int callbackType, ScanResult result) {
                        if (callbackType != ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                            // Should not happen.
                            Log.e(TAG, "LE Scan has already started");
                            return;
                        }
                        ScanRecord scanRecord = result.getScanRecord();
                        if (scanRecord == null) {
                            return;
                        }
                        if (serviceUuids != null) {
                            List<ParcelUuid> uuids = new ArrayList<ParcelUuid>();
                            for (UUID uuid : serviceUuids) {
                                uuids.add(new ParcelUuid(uuid));
                            }
                            List<ParcelUuid> scanServiceUuids = scanRecord.getServiceUuids();
                            if (scanServiceUuids == null || !scanServiceUuids.containsAll(uuids)) {
                                if (DBG) Log.d(TAG, "uuids does not match");
                                return;
                            }
                        }
                        callback.onLeScan(result.getDevice(), result.getRssi(),
                                scanRecord.getBytes());
                    }
                };
                ScanSettings settings = new ScanSettings.Builder()
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

                List<ScanFilter> filters = new ArrayList<ScanFilter>();
                if (serviceUuids != null && serviceUuids.length > 0) {
                    // Note scan filter does not support matching an UUID array so we put one
                    // UUID to hardware and match the whole array in callback.
                    ScanFilter filter = new ScanFilter.Builder().setServiceUuid(
                            new ParcelUuid(serviceUuids[0])).build();
                    filters.add(filter);
                }
                scanner.startScan(filters, settings, scanCallback);

                mLeScanClients.put(callback, scanCallback);
                return true;

            } catch (RemoteException e) {
                Log.e(TAG,"",e);
            }
        }
        return false;
    }

    /**
     * Stops an ongoing Bluetooth LE device scan.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} permission.
     *
     * @param callback used to identify which scan to stop
     *        must be the same handle used to start the scan
     * @deprecated Use {@link BluetoothLeScanner#stopScan(ScanCallback)} instead.
     */
    @Deprecated
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void stopLeScan(LeScanCallback callback) {
        if (DBG) Log.d(TAG, "stopLeScan()");
        BluetoothLeScanner scanner = getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }
        synchronized (mLeScanClients) {
            ScanCallback scanCallback = mLeScanClients.remove(callback);
            if (scanCallback == null) {
                if (DBG) Log.d(TAG, "scan not started yet");
                return;
            }
            scanner.stopScan(scanCallback);
        }
    }
}
