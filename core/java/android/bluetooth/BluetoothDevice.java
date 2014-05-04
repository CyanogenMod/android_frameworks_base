/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.Context;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

/**
 * Represents a remote Bluetooth device. A {@link BluetoothDevice} lets you
 * create a connection with the respective device or query information about
 * it, such as the name, address, class, and bonding state.
 *
 * <p>This class is really just a thin wrapper for a Bluetooth hardware
 * address. Objects of this class are immutable. Operations on this class
 * are performed on the remote Bluetooth hardware address, using the
 * {@link BluetoothAdapter} that was used to create this {@link
 * BluetoothDevice}.
 *
 * <p>To get a {@link BluetoothDevice}, use
 * {@link BluetoothAdapter#getRemoteDevice(String)
 * BluetoothAdapter.getRemoteDevice(String)} to create one representing a device
 * of a known MAC address (which you can get through device discovery with
 * {@link BluetoothAdapter}) or get one from the set of bonded devices
 * returned by {@link BluetoothAdapter#getBondedDevices()
 * BluetoothAdapter.getBondedDevices()}. You can then open a
 * {@link BluetoothSocket} for communication with the remote device, using
 * {@link #createRfcommSocketToServiceRecord(UUID)}.
 *
 * <p class="note"><strong>Note:</strong>
 * Requires the {@link android.Manifest.permission#BLUETOOTH} permission.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about using Bluetooth, read the
 * <a href="{@docRoot}guide/topics/wireless/bluetooth.html">Bluetooth</a> developer guide.</p>
 * </div>
 *
 * {@see BluetoothAdapter}
 * {@see BluetoothSocket}
 */
public final class BluetoothDevice implements Parcelable {
    private static final String TAG = "BluetoothDevice";
    private static final boolean DBG = false;

    /**
     * Sentinel error value for this class. Guaranteed to not equal any other
     * integer constant in this class. Provided as a convenience for functions
     * that require a sentinel error value, for example:
     * <p><code>Intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
     * BluetoothDevice.ERROR)</code>
     */
    public static final int ERROR = Integer.MIN_VALUE;

    /**
     * Broadcast Action: Remote device discovered.
     * <p>Sent when a remote device is found during discovery.
     * <p>Always contains the extra fields {@link #EXTRA_DEVICE} and {@link
     * #EXTRA_CLASS}. Can contain the extra fields {@link #EXTRA_NAME} and/or
     * {@link #EXTRA_RSSI} if they are available.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
     // TODO: Change API to not broadcast RSSI if not available (incoming connection)
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_FOUND =
            "android.bluetooth.device.action.FOUND";

    /**
     * Broadcast Action: Remote device disappeared.
     * <p>Sent when a remote device that was found in the last discovery is not
     * found in the current discovery.
     * <p>Always contains the extra field {@link #EXTRA_DEVICE}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_DISAPPEARED =
            "android.bluetooth.device.action.DISAPPEARED";

    /**
     * Broadcast Action: Bluetooth class of a remote device has changed.
     * <p>Always contains the extra fields {@link #EXTRA_DEVICE} and {@link
     * #EXTRA_CLASS}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     * {@see BluetoothClass}
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CLASS_CHANGED =
            "android.bluetooth.device.action.CLASS_CHANGED";

    /**
     * Broadcast Action: Indicates a low level (ACL) connection has been
     * established with a remote device.
     * <p>Always contains the extra field {@link #EXTRA_DEVICE}.
     * <p>ACL connections are managed automatically by the Android Bluetooth
     * stack.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_ACL_CONNECTED =
            "android.bluetooth.device.action.ACL_CONNECTED";

    /**
     * Broadcast Action: Indicates that a low level (ACL) disconnection has
     * been requested for a remote device, and it will soon be disconnected.
     * <p>This is useful for graceful disconnection. Applications should use
     * this intent as a hint to immediately terminate higher level connections
     * (RFCOMM, L2CAP, or profile connections) to the remote device.
     * <p>Always contains the extra field {@link #EXTRA_DEVICE}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_ACL_DISCONNECT_REQUESTED =
            "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED";

    /**
     * Broadcast Action: Indicates a low level (ACL) disconnection from a
     * remote device.
     * <p>Always contains the extra field {@link #EXTRA_DEVICE}.
     * <p>ACL connections are managed automatically by the Android Bluetooth
     * stack.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_ACL_DISCONNECTED =
            "android.bluetooth.device.action.ACL_DISCONNECTED";

    /**
     * Broadcast Action: Indicates the friendly name of a remote device has
     * been retrieved for the first time, or changed since the last retrieval.
     * <p>Always contains the extra fields {@link #EXTRA_DEVICE} and {@link
     * #EXTRA_NAME}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_NAME_CHANGED =
            "android.bluetooth.device.action.NAME_CHANGED";

    /**
     * Broadcast Action: Indicates the alias of a remote device has been
     * changed.
     * <p>Always contains the extra field {@link #EXTRA_DEVICE}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     *
     * @hide
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_ALIAS_CHANGED =
            "android.bluetooth.device.action.ALIAS_CHANGED";

    /**
     * Broadcast Action: Indicates a change in the bond state of a remote
     * device. For example, if a device is bonded (paired).
     * <p>Always contains the extra fields {@link #EXTRA_DEVICE}, {@link
     * #EXTRA_BOND_STATE} and {@link #EXTRA_PREVIOUS_BOND_STATE}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    // Note: When EXTRA_BOND_STATE is BOND_NONE then this will also
    // contain a hidden extra field EXTRA_REASON with the result code.
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_BOND_STATE_CHANGED =
            "android.bluetooth.device.action.BOND_STATE_CHANGED";

    /**
     * Used as a Parcelable {@link BluetoothDevice} extra field in every intent
     * broadcast by this class. It contains the {@link BluetoothDevice} that
     * the intent applies to.
     */
    public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";

    /**
     * Used as a String extra field in {@link #ACTION_NAME_CHANGED} and {@link
     * #ACTION_FOUND} intents. It contains the friendly Bluetooth name.
     */
    public static final String EXTRA_NAME = "android.bluetooth.device.extra.NAME";

    /**
     * Used as an optional short extra field in {@link #ACTION_FOUND} intents.
     * Contains the RSSI value of the remote device as reported by the
     * Bluetooth hardware.
     */
    public static final String EXTRA_RSSI = "android.bluetooth.device.extra.RSSI";

    /**
     * Used as a Parcelable {@link BluetoothClass} extra field in {@link
     * #ACTION_FOUND} and {@link #ACTION_CLASS_CHANGED} intents.
     */
    public static final String EXTRA_CLASS = "android.bluetooth.device.extra.CLASS";

    /**
     * Used as an int extra field in {@link #ACTION_BOND_STATE_CHANGED} intents.
     * Contains the bond state of the remote device.
     * <p>Possible values are:
     * {@link #BOND_NONE},
     * {@link #BOND_BONDING},
     * {@link #BOND_BONDED}.
     */
    public static final String EXTRA_BOND_STATE = "android.bluetooth.device.extra.BOND_STATE";
    /**
     * Used as an int extra field in {@link #ACTION_BOND_STATE_CHANGED} intents.
     * Contains the previous bond state of the remote device.
     * <p>Possible values are:
     * {@link #BOND_NONE},
     * {@link #BOND_BONDING},
     * {@link #BOND_BONDED}.
     */
    public static final String EXTRA_PREVIOUS_BOND_STATE =
            "android.bluetooth.device.extra.PREVIOUS_BOND_STATE";
    /**
     * Indicates the remote device is not bonded (paired).
     * <p>There is no shared link key with the remote device, so communication
     * (if it is allowed at all) will be unauthenticated and unencrypted.
     */
    public static final int BOND_NONE = 10;
    /**
     * Indicates bonding (pairing) is in progress with the remote device.
     */
    public static final int BOND_BONDING = 11;
    /**
     * Indicates the remote device is bonded (paired).
     * <p>A shared link keys exists locally for the remote device, so
     * communication can be authenticated and encrypted.
     * <p><i>Being bonded (paired) with a remote device does not necessarily
     * mean the device is currently connected. It just means that the pending
     * procedure was completed at some earlier time, and the link key is still
     * stored locally, ready to use on the next connection.
     * </i>
     */
    public static final int BOND_BONDED = 12;

    /**
     * Used as an int extra field in {@link #ACTION_PAIRING_REQUEST}
     * intents for unbond reason.
     * @hide
     */
    public static final String EXTRA_REASON = "android.bluetooth.device.extra.REASON";

    /**
     * Used as an int extra field in {@link #ACTION_PAIRING_REQUEST}
     * intents to indicate pairing method used. Possible values are:
     * {@link #PAIRING_VARIANT_PIN},
     * {@link #PAIRING_VARIANT_PASSKEY_CONFIRMATION},
     */
    public static final String EXTRA_PAIRING_VARIANT =
            "android.bluetooth.device.extra.PAIRING_VARIANT";

    /**
     * Used as an int extra field in {@link #ACTION_PAIRING_REQUEST}
     * intents as the value of passkey.
     */
    public static final String EXTRA_PAIRING_KEY = "android.bluetooth.device.extra.PAIRING_KEY";

    /**
     * Bluetooth device type, Unknown
     */
    public static final int DEVICE_TYPE_UNKNOWN = 0;

    /**
     * Bluetooth device type, Classic - BR/EDR devices
     */
    public static final int DEVICE_TYPE_CLASSIC = 1;

    /**
     * Bluetooth device type, Low Energy - LE-only
     */
    public static final int DEVICE_TYPE_LE = 2;

    /**
     * Bluetooth device type, Dual Mode - BR/EDR/LE
     */
    public static final int DEVICE_TYPE_DUAL = 3;

    /**
     * Broadcast Action: This intent is used to broadcast the {@link UUID}
     * wrapped as a {@link android.os.ParcelUuid} of the remote device after it
     * has been fetched. This intent is sent only when the UUIDs of the remote
     * device are requested to be fetched using Service Discovery Protocol
     * <p> Always contains the extra field {@link #EXTRA_DEVICE}
     * <p> Always contains the extra field {@link #EXTRA_UUID}
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_UUID =
            "android.bluetooth.device.action.UUID";

    /**
     * Broadcast Action: Indicates a failure to retrieve the name of a remote
     * device.
     * <p>Always contains the extra field {@link #EXTRA_DEVICE}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH} to receive.
     * @hide
     */
    //TODO: is this actually useful?
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_NAME_FAILED =
            "android.bluetooth.device.action.NAME_FAILED";

    /**
     * Broadcast Action: This intent is used to broadcast PAIRING REQUEST
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN} to
     * receive.
     */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PAIRING_REQUEST =
            "android.bluetooth.device.action.PAIRING_REQUEST";
    /** @hide */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_PAIRING_CANCEL =
            "android.bluetooth.device.action.PAIRING_CANCEL";

    /** @hide */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_ACCESS_REQUEST =
            "android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST";

    /** @hide */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_ACCESS_REPLY =
            "android.bluetooth.device.action.CONNECTION_ACCESS_REPLY";

    /** @hide */
    @SdkConstant(SdkConstantType.BROADCAST_INTENT_ACTION)
    public static final String ACTION_CONNECTION_ACCESS_CANCEL =
            "android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL";

    /**
     * Used as an extra field in {@link #ACTION_CONNECTION_ACCESS_REQUEST} intent.
     * @hide
     */
    public static final String EXTRA_ACCESS_REQUEST_TYPE =
        "android.bluetooth.device.extra.ACCESS_REQUEST_TYPE";

    /**@hide*/
    public static final int REQUEST_TYPE_PROFILE_CONNECTION = 1;

    /**@hide*/
    public static final int REQUEST_TYPE_PHONEBOOK_ACCESS = 2;

    /**@hide*/
    public static final int REQUEST_TYPE_MESSAGE_ACCESS = 3;

    /**
     * Used as an extra field in {@link #ACTION_CONNECTION_ACCESS_REQUEST} intents,
     * Contains package name to return reply intent to.
     * @hide
     */
    public static final String EXTRA_PACKAGE_NAME = "android.bluetooth.device.extra.PACKAGE_NAME";

    /**
     * Used as an extra field in {@link #ACTION_CONNECTION_ACCESS_REQUEST} intents,
     * Contains class name to return reply intent to.
     * @hide
     */
    public static final String EXTRA_CLASS_NAME = "android.bluetooth.device.extra.CLASS_NAME";

    /**
     * Used as an extra field in {@link #ACTION_CONNECTION_ACCESS_REPLY} intent.
     * @hide
     */
    public static final String EXTRA_CONNECTION_ACCESS_RESULT =
        "android.bluetooth.device.extra.CONNECTION_ACCESS_RESULT";

    /**@hide*/
    public static final int CONNECTION_ACCESS_YES = 1;

    /**@hide*/
    public static final int CONNECTION_ACCESS_NO = 2;

    /**
     * Used as an extra field in {@link #ACTION_CONNECTION_ACCESS_REPLY} intents,
     * Contains boolean to indicate if the allowed response is once-for-all so that
     * next request will be granted without asking user again.
     * @hide
     */
    public static final String EXTRA_ALWAYS_ALLOWED =
        "android.bluetooth.device.extra.ALWAYS_ALLOWED";

    /**
     * A bond attempt succeeded
     * @hide
     */
    public static final int BOND_SUCCESS = 0;

    /**
     * A bond attempt failed because pins did not match, or remote device did
     * not respond to pin request in time
     * @hide
     */
    public static final int UNBOND_REASON_AUTH_FAILED = 1;

    /**
     * A bond attempt failed because the other side explicitly rejected
     * bonding
     * @hide
     */
    public static final int UNBOND_REASON_AUTH_REJECTED = 2;

    /**
     * A bond attempt failed because we canceled the bonding process
     * @hide
     */
    public static final int UNBOND_REASON_AUTH_CANCELED = 3;

    /**
     * A bond attempt failed because we could not contact the remote device
     * @hide
     */
    public static final int UNBOND_REASON_REMOTE_DEVICE_DOWN = 4;

    /**
     * A bond attempt failed because a discovery is in progress
     * @hide
     */
    public static final int UNBOND_REASON_DISCOVERY_IN_PROGRESS = 5;

    /**
     * A bond attempt failed because of authentication timeout
     * @hide
     */
    public static final int UNBOND_REASON_AUTH_TIMEOUT = 6;

    /**
     * A bond attempt failed because of repeated attempts
     * @hide
     */
    public static final int UNBOND_REASON_REPEATED_ATTEMPTS = 7;

    /**
     * A bond attempt failed because we received an Authentication Cancel
     * by remote end
     * @hide
     */
    public static final int UNBOND_REASON_REMOTE_AUTH_CANCELED = 8;

    /**
     * An existing bond was explicitly revoked
     * @hide
     */
    public static final int UNBOND_REASON_REMOVED = 9;

    /**
     * The user will be prompted to enter a pin or
     * an app will enter a pin for user.
     */
    public static final int PAIRING_VARIANT_PIN = 0;

    /**
     * The user will be prompted to enter a passkey
     * @hide
     */
    public static final int PAIRING_VARIANT_PASSKEY = 1;

    /**
     * The user will be prompted to confirm the passkey displayed on the screen or
     * an app will confirm the passkey for the user.
     */
    public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;

    /**
     * The user will be prompted to accept or deny the incoming pairing request
     * @hide
     */
    public static final int PAIRING_VARIANT_CONSENT = 3;

    /**
     * The user will be prompted to enter the passkey displayed on remote device
     * This is used for Bluetooth 2.1 pairing.
     * @hide
     */
    public static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;

    /**
     * The user will be prompted to enter the PIN displayed on remote device.
     * This is used for Bluetooth 2.0 pairing.
     * @hide
     */
    public static final int PAIRING_VARIANT_DISPLAY_PIN = 5;

    /**
     * The user will be prompted to accept or deny the OOB pairing request
     * @hide
     */
    public static final int PAIRING_VARIANT_OOB_CONSENT = 6;

    /**
     * Used as an extra field in {@link #ACTION_UUID} intents,
     * Contains the {@link android.os.ParcelUuid}s of the remote device which
     * is a parcelable version of {@link UUID}.
     */
    public static final String EXTRA_UUID = "android.bluetooth.device.extra.UUID";

    /**
     * Lazy initialization. Guaranteed final after first object constructed, or
     * getService() called.
     * TODO: Unify implementation of sService amongst BluetoothFoo API's
     */
    private static IBluetooth sService;

    private final String mAddress;

    /*package*/ static IBluetooth getService() {
        synchronized (BluetoothDevice.class) {
            if (sService == null) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                sService = adapter.getBluetoothService(mStateChangeCallback);
            }
        }
        return sService;
    }

    static IBluetoothManagerCallback mStateChangeCallback = new IBluetoothManagerCallback.Stub() {

        public void onBluetoothServiceUp(IBluetooth bluetoothService)
                throws RemoteException {
            synchronized (BluetoothDevice.class) {
                sService = bluetoothService;
            }
        }

        public void onBluetoothServiceDown()
            throws RemoteException {
            synchronized (BluetoothDevice.class) {
                sService = null;
            }
        }
    };
    /**
     * Create a new BluetoothDevice
     * Bluetooth MAC address must be upper case, such as "00:11:22:33:AA:BB",
     * and is validated in this constructor.
     * @param address valid Bluetooth MAC address
     * @throws RuntimeException Bluetooth is not available on this platform
     * @throws IllegalArgumentException address is invalid
     * @hide
     */
    /*package*/ BluetoothDevice(String address) {
        getService();  // ensures sService is initialized
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            throw new IllegalArgumentException(address + " is not a valid Bluetooth address");
        }

        mAddress = address;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof BluetoothDevice) {
            return mAddress.equals(((BluetoothDevice)o).getAddress());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mAddress.hashCode();
    }

    /**
     * Returns a string representation of this BluetoothDevice.
     * <p>Currently this is the Bluetooth hardware address, for example
     * "00:11:22:AA:BB:CC". However, you should always use {@link #getAddress}
     * if you explicitly require the Bluetooth hardware address in case the
     * {@link #toString} representation changes in the future.
     * @return string representation of this BluetoothDevice
     */
    @Override
    public String toString() {
        return mAddress;
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<BluetoothDevice> CREATOR =
            new Parcelable.Creator<BluetoothDevice>() {
        public BluetoothDevice createFromParcel(Parcel in) {
            return new BluetoothDevice(in.readString());
        }
        public BluetoothDevice[] newArray(int size) {
            return new BluetoothDevice[size];
        }
    };

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mAddress);
    }

    /**
     * Returns the hardware address of this BluetoothDevice.
     * <p> For example, "00:11:22:AA:BB:CC".
     * @return Bluetooth hardware address as string
     */
    public String getAddress() {
        if (DBG) Log.d(TAG, "mAddress: " + mAddress);
        return mAddress;
    }

    /**
     * Get the friendly Bluetooth name of the remote device.
     *
     * <p>The local adapter will automatically retrieve remote names when
     * performing a device scan, and will cache them. This method just returns
     * the name for this device from the cache.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return the Bluetooth name, or null if there was a problem.
     */
    public String getName() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Remote Device name");
            return null;
        }
        try {
            return sService.getRemoteName(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get the Bluetooth device type of the remote device.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @return the device type {@link #DEVICE_TYPE_CLASSIC}, {@link #DEVICE_TYPE_LE}
     *                         {@link #DEVICE_TYPE_DUAL}.
     *         {@link #DEVICE_TYPE_UNKNOWN} if it's not available
     */
    public int getType() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Remote Device type");
            return DEVICE_TYPE_UNKNOWN;
        }
        try {
            return sService.getRemoteType(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return DEVICE_TYPE_UNKNOWN;
    }

    /**
     * Get the Bluetooth alias of the remote device.
     * <p>Alias is the locally modified name of a remote device.
     *
     * @return the Bluetooth alias, or null if no alias or there was a problem
     * @hide
     */
    public String getAlias() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Remote Device Alias");
            return null;
        }
        try {
            return sService.getRemoteAlias(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Set the Bluetooth alias of the remote device.
     * <p>Alias is the locally modified name of a remote device.
     * <p>This methoid overwrites the alias. The changed
     * alias is saved in the local storage so that the change
     * is preserved over power cycle.
     *
     * @return true on success, false on error
     * @hide
     */
    public boolean setAlias(String alias) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot set Remote Device name");
            return false;
        }
        try {
            return sService.setRemoteAlias(this, alias);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get the Bluetooth alias of the remote device.
     * If Alias is null, get the Bluetooth name instead.
     * @see #getAlias()
     * @see #getName()
     *
     * @return the Bluetooth alias, or null if no alias or there was a problem
     * @hide
     */
    public String getAliasName() {
        String name = getAlias();
        if (name == null) {
            name = getName();
        }
        return name;
    }

    /**
     * Start the bonding (pairing) process with the remote device.
     * <p>This is an asynchronous call, it will return immediately. Register
     * for {@link #ACTION_BOND_STATE_CHANGED} intents to be notified when
     * the bonding process completes, and its result.
     * <p>Android system services will handle the necessary user interactions
     * to confirm and complete the bonding process.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return false on immediate error, true if bonding will begin
     */
    public boolean createBond() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot create bond to Remote Device");
            return false;
        }
        try {
            return sService.createBond(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Start the bonding (pairing) process with the remote device using the
     * Out Of Band mechanism.
     *
     * <p>This is an asynchronous call, it will return immediately. Register
     * for {@link #ACTION_BOND_STATE_CHANGED} intents to be notified when
     * the bonding process completes, and its result.
     *
     * <p>Android system services will handle the necessary user interactions
     * to confirm and complete the bonding process.
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @param hash - Simple Secure pairing hash
     * @param randomizer - The random key obtained using OOB
     * @return false on immediate error, true if bonding will begin
     *
     * @hide
     */
    public boolean createBondOutOfBand(byte[] hash, byte[] randomizer) {
        //TODO(BT)
        /*
        try {
            return sService.createBondOutOfBand(this, hash, randomizer);
        } catch (RemoteException e) {Log.e(TAG, "", e);}*/
        return false;
    }

    /**
     * Set the Out Of Band data for a remote device to be used later
     * in the pairing mechanism. Users can obtain this data through other
     * trusted channels
     *
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @param hash Simple Secure pairing hash
     * @param randomizer The random key obtained using OOB
     * @return false on error; true otherwise
     *
     * @hide
     */
    public boolean setDeviceOutOfBandData(byte[] hash, byte[] randomizer) {
      //TODO(BT)
      /*
      try {
        return sService.setDeviceOutOfBandData(this, hash, randomizer);
      } catch (RemoteException e) {Log.e(TAG, "", e);} */
      return false;
    }

    /**
     * Cancel an in-progress bonding request started with {@link #createBond}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return true on success, false on error
     * @hide
     */
    public boolean cancelBondProcess() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot cancel Remote Device bond");
            return false;
        }
        try {
            return sService.cancelBondProcess(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Remove bond (pairing) with the remote device.
     * <p>Delete the link key associated with the remote device, and
     * immediately terminate connections to that device that require
     * authentication and encryption.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return true on success, false on error
     * @hide
     */
    public boolean removeBond() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot remove Remote Device bond");
            return false;
        }
        try {
            return sService.removeBond(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /**
     * Get the bond state of the remote device.
     * <p>Possible values for the bond state are:
     * {@link #BOND_NONE},
     * {@link #BOND_BONDING},
     * {@link #BOND_BONDED}.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     *
     * @return the bond state
     */
    public int getBondState() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get bond state");
            return BOND_NONE;
        }
        try {
            return sService.getBondState(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        catch (NullPointerException npe) {
            // Handle case where bluetooth service proxy
            // is already null.
            Log.e(TAG, "NullPointerException for getBondState() of device ("+
                getAddress()+")", npe);
        }
        return BOND_NONE;
    }

    /**
     * Get the Bluetooth class of the remote device.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     *
     * @return Bluetooth class object, or null on error
     */
    public BluetoothClass getBluetoothClass() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get Bluetooth Class");
            return null;
        }
        try {
            int classInt = sService.getRemoteClass(this);
            if (classInt == BluetoothClass.ERROR) return null;
            return new BluetoothClass(classInt);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

    /**
     * Get trust state of a remote device.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     * @hide
     */
    public boolean getTrustState() {
        //TODO(BT)
        /*
        try {
            return sService.getTrustState(this);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }*/
        return false;
    }

    /**
     * Set trust state for a remote device.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     * @param value the trust state value (true or false)
     * @hide
     */
    public boolean setTrust(boolean value) {
        //TODO(BT)
        /*
        try {
            return sService.setTrust(this, value);
        } catch (RemoteException e) {
            Log.e(TAG, "", e);
        }*/
        return false;
    }

    /**
     * Returns the supported features (UUIDs) of the remote device.
     *
     * <p>This method does not start a service discovery procedure to retrieve the UUIDs
     * from the remote device. Instead, the local cached copy of the service
     * UUIDs are returned.
     * <p>Use {@link #fetchUuidsWithSdp} if fresh UUIDs are desired.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
     *
     * @return the supported features (UUIDs) of the remote device,
     *         or null on error
     */
     public ParcelUuid[] getUuids() {
         if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot get remote device Uuids");
             return null;
         }
        try {
            return sService.getRemoteUuids(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }

     /**
      * Perform a service discovery on the remote device to get the UUIDs supported.
      *
      * <p>This API is asynchronous and {@link #ACTION_UUID} intent is sent,
      * with the UUIDs supported by the remote end. If there is an error
      * in getting the SDP records or if the process takes a long time,
      * {@link #ACTION_UUID} intent is sent with the UUIDs that is currently
      * present in the cache. Clients should use the {@link #getUuids} to get UUIDs
      * if service discovery is not to be performed.
      * <p>Requires {@link android.Manifest.permission#BLUETOOTH}.
      *
      * @return False if the sanity check fails, True if the process
      *               of initiating an ACL connection to the remote device
      *               was started.
      */
     public boolean fetchUuidsWithSdp() {
        try {
            return sService.fetchRemoteUuids(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
            return false;
    }

    /** @hide */
    public int getServiceChannel(ParcelUuid uuid) {
        //TODO(BT)
        /*
         try {
             return sService.getRemoteServiceChannel(this, uuid);
         } catch (RemoteException e) {Log.e(TAG, "", e);}*/
         return BluetoothDevice.ERROR;
    }

    /**
     * Set the pin during pairing when the pairing method is {@link #PAIRING_VARIANT_PIN}
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return true pin has been set
     *         false for error
     */
    public boolean setPin(byte[] pin) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot set Remote Device pin");
            return false;
        }
        try {
            return sService.setPin(this, true, pin.length, pin);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /** @hide */
    public boolean setPasskey(int passkey) {
        //TODO(BT)
        /*
        try {
            return sService.setPasskey(this, true, 4, passkey);
        } catch (RemoteException e) {Log.e(TAG, "", e);}*/
        return false;
    }

    /**
     * Confirm passkey for {@link #PAIRING_VARIANT_PASSKEY_CONFIRMATION} pairing.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}.
     *
     * @return true confirmation has been sent out
     *         false for error
     */
    public boolean setPairingConfirmation(boolean confirm) {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot set pairing confirmation");
            return false;
        }
        try {
            return sService.setPairingConfirmation(this, confirm);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /** @hide */
    public boolean setRemoteOutOfBandData() {
        // TODO(BT)
        /*
        try {
          return sService.setRemoteOutOfBandData(this);
      } catch (RemoteException e) {Log.e(TAG, "", e);}*/
      return false;
    }

    /** @hide */
    public boolean cancelPairingUserInput() {
        if (sService == null) {
            Log.e(TAG, "BT not enabled. Cannot create pairing user input");
            return false;
        }
        try {
            return sService.cancelBondProcess(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return false;
    }

    /** @hide */
    public boolean isBluetoothDock() {
        // TODO(BT)
        /*
        try {
            return sService.isBluetoothDock(this);
        } catch (RemoteException e) {Log.e(TAG, "", e);}*/
        return false;
    }

    /**
     * Create an RFCOMM {@link BluetoothSocket} ready to start a secure
     * outgoing connection to this remote device on given channel.
     * <p>The remote device will be authenticated and communication on this
     * socket will be encrypted.
     * <p> Use this socket only if an authenticated socket link is possible.
     * Authentication refers to the authentication of the link key to
     * prevent man-in-the-middle type of attacks.
     * For example, for Bluetooth 2.1 devices, if any of the devices does not
     * have an input and output capability or just has the ability to
     * display a numeric key, a secure socket connection is not possible.
     * In such a case, use {#link createInsecureRfcommSocket}.
     * For more details, refer to the Security Model section 5.2 (vol 3) of
     * Bluetooth Core Specification version 2.1 + EDR.
     * <p>Use {@link BluetoothSocket#connect} to initiate the outgoing
     * connection.
     * <p>Valid RFCOMM channels are in range 1 to 30.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @param channel RFCOMM channel to connect to
     * @return a RFCOMM BluetoothServerSocket ready for an outgoing connection
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions
     * @hide
     */
    public BluetoothSocket createRfcommSocket(int channel) throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_RFCOMM, -1, true, true, this, channel,
                null);
    }

    /**
     * Create an RFCOMM {@link BluetoothSocket} ready to start a secure
     * outgoing connection to this remote device using SDP lookup of uuid.
     * <p>This is designed to be used with {@link
     * BluetoothAdapter#listenUsingRfcommWithServiceRecord} for peer-peer
     * Bluetooth applications.
     * <p>Use {@link BluetoothSocket#connect} to initiate the outgoing
     * connection. This will also perform an SDP lookup of the given uuid to
     * determine which channel to connect to.
     * <p>The remote device will be authenticated and communication on this
     * socket will be encrypted.
     * <p> Use this socket only if an authenticated socket link is possible.
     * Authentication refers to the authentication of the link key to
     * prevent man-in-the-middle type of attacks.
     * For example, for Bluetooth 2.1 devices, if any of the devices does not
     * have an input and output capability or just has the ability to
     * display a numeric key, a secure socket connection is not possible.
     * In such a case, use {#link createInsecureRfcommSocketToServiceRecord}.
     * For more details, refer to the Security Model section 5.2 (vol 3) of
     * Bluetooth Core Specification version 2.1 + EDR.
     * <p>Hint: If you are connecting to a Bluetooth serial board then try
     * using the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB.
     * However if you are connecting to an Android peer then please generate
     * your own unique UUID.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @param uuid service record uuid to lookup RFCOMM channel
     * @return a RFCOMM BluetoothServerSocket ready for an outgoing connection
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions
     */
    public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_RFCOMM, -1, true, true, this, -1,
                new ParcelUuid(uuid));
    }

    /**
     * Create an RFCOMM {@link BluetoothSocket} socket ready to start an insecure
     * outgoing connection to this remote device using SDP lookup of uuid.
     * <p> The communication channel will not have an authenticated link key
     * i.e it will be subject to man-in-the-middle attacks. For Bluetooth 2.1
     * devices, the link key will be encrypted, as encryption is mandatory.
     * For legacy devices (pre Bluetooth 2.1 devices) the link key will
     * be not be encrypted. Use {@link #createRfcommSocketToServiceRecord} if an
     * encrypted and authenticated communication channel is desired.
     * <p>This is designed to be used with {@link
     * BluetoothAdapter#listenUsingInsecureRfcommWithServiceRecord} for peer-peer
     * Bluetooth applications.
     * <p>Use {@link BluetoothSocket#connect} to initiate the outgoing
     * connection. This will also perform an SDP lookup of the given uuid to
     * determine which channel to connect to.
     * <p>The remote device will be authenticated and communication on this
     * socket will be encrypted.
     * <p>Hint: If you are connecting to a Bluetooth serial board then try
     * using the well-known SPP UUID 00001101-0000-1000-8000-00805F9B34FB.
     * However if you are connecting to an Android peer then please generate
     * your own unique UUID.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
     *
     * @param uuid service record uuid to lookup RFCOMM channel
     * @return a RFCOMM BluetoothServerSocket ready for an outgoing connection
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions
     */
    public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_RFCOMM, -1, false, false, this, -1,
                new ParcelUuid(uuid));
    }

    /**
     * Construct an insecure RFCOMM socket ready to start an outgoing
     * connection.
     * Call #connect on the returned #BluetoothSocket to begin the connection.
     * The remote device will not be authenticated and communication on this
     * socket will not be encrypted.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     *
     * @param port    remote port
     * @return An RFCOMM BluetoothSocket
     * @throws IOException On error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothSocket createInsecureRfcommSocket(int port) throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_RFCOMM, -1, false, false, this, port,
                null);
    }

    /**
     * Construct a SCO socket ready to start an outgoing connection.
     * Call #connect on the returned #BluetoothSocket to begin the connection.
     * <p>Requires {@link android.Manifest.permission#BLUETOOTH_ADMIN}
     *
     * @return a SCO BluetoothSocket
     * @throws IOException on error, for example Bluetooth not available, or
     *                     insufficient permissions.
     * @hide
     */
    public BluetoothSocket createScoSocket() throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_SCO, -1, true, true, this, -1, null);
    }

    /**
     * Check that a pin is valid and convert to byte array.
     *
     * Bluetooth pin's are 1 to 16 bytes of UTF-8 characters.
     * @param pin pin as java String
     * @return the pin code as a UTF-8 byte array, or null if it is an invalid
     *         Bluetooth pin.
     * @hide
     */
    public static byte[] convertPinToBytes(String pin) {
        if (pin == null) {
            return null;
        }
        byte[] pinBytes;
        try {
            pinBytes = pin.getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
            Log.e(TAG, "UTF-8 not supported?!?");  // this should not happen
            return null;
        }
        if (pinBytes.length <= 0 || pinBytes.length > 16) {
            return null;
        }
        return pinBytes;
    }

    /**
     * Connect to GATT Server hosted by this device. Caller acts as GATT client.
     * The callback is used to deliver results to Caller, such as connection status as well
     * as any further GATT client operations.
     * The method returns a BluetoothGatt instance. You can use BluetoothGatt to conduct
     * GATT client operations.
     * @param callback GATT callback handler that will receive asynchronous callbacks.
     * @param autoConnect Whether to directly connect to the remote device (false)
     *                    or to automatically connect as soon as the remote
     *                    device becomes available (true).
     * @throws IllegalArgumentException if callback is null
     */
    public BluetoothGatt connectGatt(Context context, boolean autoConnect,
                                     BluetoothGattCallback callback) {
        // TODO(Bluetooth) check whether platform support BLE
        //     Do the check here or in GattServer?
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        IBluetoothManager managerService = adapter.getBluetoothManager();
        try {
            IBluetoothGatt iGatt = managerService.getBluetoothGatt();
            if (iGatt == null) {
                // BLE is not supported
                return null;
            }
            BluetoothGatt gatt = new BluetoothGatt(context, iGatt, this);
            gatt.connect(autoConnect, callback);
            return gatt;
        } catch (RemoteException e) {Log.e(TAG, "", e);}
        return null;
    }
}
