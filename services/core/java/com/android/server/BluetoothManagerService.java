/*
 * Copyright (c) 2013-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

import android.Manifest;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

class BluetoothManagerService extends IBluetoothManager.Stub {
    private static final String TAG = "BluetoothManagerService";
    private static final boolean DBG = true;

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;
    private static final String ACTION_SERVICE_STATE_CHANGED="com.android.bluetooth.btservice.action.STATE_CHANGED";
    private static final String EXTRA_ACTION="action";
    private static final String SECURE_SETTINGS_BLUETOOTH_ADDR_VALID="bluetooth_addr_valid";
    private static final String SECURE_SETTINGS_BLUETOOTH_ADDRESS="bluetooth_address";
    private static final String SECURE_SETTINGS_BLUETOOTH_NAME="bluetooth_name";
    private static final int TIMEOUT_BIND_MS = 3000; //Maximum msec to wait for a bind
    private static final int TIMEOUT_SAVE_MS = 500; //Maximum msec to wait for a save
    //Maximum msec to wait for service restart
    private static final int SERVICE_RESTART_TIME_MS = 200;
    //Maximum msec to wait for restart due to error
    private static final int ERROR_RESTART_TIME_MS = 3000;
    //Maximum msec to delay MESSAGE_USER_SWITCHED
    private static final int USER_SWITCHED_TIME_MS = 200;

    private static final int MESSAGE_ENABLE = 1;
    private static final int MESSAGE_DISABLE = 2;
    private static final int MESSAGE_REGISTER_ADAPTER = 20;
    private static final int MESSAGE_UNREGISTER_ADAPTER = 21;
    private static final int MESSAGE_REGISTER_STATE_CHANGE_CALLBACK = 30;
    private static final int MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK = 31;
    private static final int MESSAGE_BLUETOOTH_SERVICE_CONNECTED = 40;
    private static final int MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED = 41;
    private static final int MESSAGE_RESTART_BLUETOOTH_SERVICE = 42;
    private static final int MESSAGE_BLUETOOTH_STATE_CHANGE=60;
    private static final int MESSAGE_TIMEOUT_BIND =100;
    private static final int MESSAGE_TIMEOUT_UNBIND =101;
    private static final int MESSAGE_GET_NAME_AND_ADDRESS=200;
    private static final int MESSAGE_SAVE_NAME_AND_ADDRESS=201;
    private static final int MESSAGE_USER_SWITCHED = 300;
    private static final int MAX_SAVE_RETRIES=3;
    private static final int MAX_ERROR_RESTART_RETRIES=6;

    // Bluetooth persisted setting is off
    private static final int BLUETOOTH_OFF=0;
    // Bluetooth persisted setting is on
    // and Airplane mode won't affect Bluetooth state at start up
    private static final int BLUETOOTH_ON_BLUETOOTH=1;
    // Bluetooth persisted setting is on
    // but Airplane mode will affect Bluetooth state at start up
    // and Airplane mode will have higher priority.
    private static final int BLUETOOTH_ON_AIRPLANE=2;

    private static final int SERVICE_IBLUETOOTH = 1;
    private static final int SERVICE_IBLUETOOTHGATT = 2;

    private static final String[] DEVICE_TYPE_NAMES = new String[] {
            "???",
            "BR/EDR",
            "LE",
            "DUAL"
        };

    private final Context mContext;
    private static int mBleAppCount = 0;

    // Locks are not provided for mName and mAddress.
    // They are accessed in handler or broadcast receiver, same thread context.
    private String mAddress;
    private String mName;
    private final ContentResolver mContentResolver;
    private final RemoteCallbackList<IBluetoothManagerCallback> mCallbacks;
    private final RemoteCallbackList<IBluetoothStateChangeCallback> mStateChangeCallbacks;
    private IBluetooth mBluetooth;
    private IBluetoothGatt mBluetoothGatt;
    private boolean mBinding;
    private boolean mUnbinding;
    // used inside handler thread
    private boolean mQuietEnable = false;
    // configuarion from external IBinder call which is used to
    // synchronize with broadcast receiver.
    private boolean mQuietEnableExternal;
    // configuarion from external IBinder call which is used to
    // synchronize with broadcast receiver.
    private boolean mEnableExternal;
    // used inside handler thread
    private boolean mEnable;
    private int mState;
    private final BluetoothHandler mHandler;
    private int mErrorRecoveryRetryCounter;
    private final int mSystemUiUid;
    private boolean mIntentPending = false;

    private void registerForAirplaneMode(IntentFilter filter) {
        final ContentResolver resolver = mContext.getContentResolver();
        final String airplaneModeRadios = Settings.Global.getString(resolver,
                Settings.Global.AIRPLANE_MODE_RADIOS);
        final String toggleableRadios = Settings.Global.getString(resolver,
                Settings.Global.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        boolean mIsAirplaneSensitive = airplaneModeRadios == null ? true :
                airplaneModeRadios.contains(Settings.Global.RADIO_BLUETOOTH);
        if (mIsAirplaneSensitive) {
            filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        }
    }

    private final IBluetoothCallback mBluetoothCallback =  new IBluetoothCallback.Stub() {
        @Override
        public void onBluetoothStateChange(int prevState, int newState) throws RemoteException  {
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_STATE_CHANGE,prevState,newState);
            mHandler.sendMessage(msg);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED.equals(action)) {
                String newName = intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
                if (DBG) Log.d(TAG, "Bluetooth Adapter name changed to " + newName);
                if (newName != null) {
                    storeNameAndAddress(newName, null);
                }
            } else if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
                synchronized(mReceiver) {
                    if (isBluetoothPersistedStateOn()) {
                        if (isAirplaneModeOn()) {
                            persistBluetoothSetting(BLUETOOTH_ON_AIRPLANE);
                        } else {
                            persistBluetoothSetting(BLUETOOTH_ON_BLUETOOTH);
                        }
                    }

                    int st = BluetoothAdapter.STATE_OFF;
                    if (mBluetooth != null) {
                        try {
                            st = mBluetooth.getState();
                        } catch (RemoteException e) {
                            Log.e(TAG,"Unable to call getState", e);
                        }
                    }
                    Log.d(TAG, "state" + st);

                    if (isAirplaneModeOn()) {
                        // Clear registered LE apps to force shut-off
                        synchronized (this) {
                            mBleAppCount = 0;
                            mBleApps.clear();
                        }
                        if (st == BluetoothAdapter.STATE_BLE_ON) {
                            //if state is BLE_ON make sure you trigger disableBLE part
                            try {
                                if (mBluetooth != null) {
                                    mBluetooth.onBrEdrDown();
                                    mEnableExternal = false;
                                }
                            } catch(RemoteException e) {
                                Log.e(TAG,"Unable to call onBrEdrDown", e);
                            }
                        } else if (st == BluetoothAdapter.STATE_ON){
                            // disable without persisting the setting
                            Log.d(TAG, "Calling disable");
                            sendDisableMsg();
                        }
                    } else if (mEnableExternal) {
                        // enable without persisting the setting
                        Log.d(TAG, "Calling enable");
                        sendEnableMsg(mQuietEnableExternal);
                    }
                }
            }
        }
    };

    BluetoothManagerService(Context context) {
        mHandler = new BluetoothHandler(IoThread.get().getLooper());

        mContext = context;
        mBluetooth = null;
        mBluetoothGatt = null;
        mBinding = false;
        mUnbinding = false;
        mEnable = false;
        mState = BluetoothAdapter.STATE_OFF;
        mQuietEnableExternal = false;
        mEnableExternal = false;
        mAddress = null;
        mName = null;
        mErrorRecoveryRetryCounter = 0;
        mContentResolver = context.getContentResolver();
        // Observe BLE scan only mode settings change.
        registerForBleScanModeChange();
        mCallbacks = new RemoteCallbackList<IBluetoothManagerCallback>();
        mStateChangeCallbacks = new RemoteCallbackList<IBluetoothStateChangeCallback>();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
        registerForAirplaneMode(filter);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mReceiver, filter);
        loadStoredNameAndAddress();
        if (isBluetoothPersistedStateOn()) {
            mEnableExternal = true;
        }

        int sysUiUid = -1;
        try {
            sysUiUid = mContext.getPackageManager().getPackageUid("com.android.systemui",
                    UserHandle.USER_OWNER);
        } catch (PackageManager.NameNotFoundException e) {
            // Some platforms, such as wearables do not have a system ui.
            Log.w(TAG, "Unable to resolve SystemUI's UID.", e);
        }
        mSystemUiUid = sysUiUid;
    }

    /**
     *  Returns true if airplane mode is currently on
     */
    private final boolean isAirplaneModeOn() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    /**
     *  Returns true if the Bluetooth saved state is "on"
     */
    private final boolean isBluetoothPersistedStateOn() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.BLUETOOTH_ON, 0) != BLUETOOTH_OFF;
    }

    /**
     *  Returns true if the Bluetooth saved state is BLUETOOTH_ON_BLUETOOTH
     */
    private final boolean isBluetoothPersistedStateOnBluetooth() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.BLUETOOTH_ON, 0) == BLUETOOTH_ON_BLUETOOTH;
    }

    /**
     *  Save the Bluetooth on/off state
     *
     */
    private void persistBluetoothSetting(int value) {
        Settings.Global.putInt(mContext.getContentResolver(),
                               Settings.Global.BLUETOOTH_ON,
                               value);
    }

    /**
     * Returns true if the Bluetooth Adapter's name and address is
     * locally cached
     * @return
     */
    private boolean isNameAndAddressSet() {
        return mName !=null && mAddress!= null && mName.length()>0 && mAddress.length()>0;
    }

    /**
     * Retrieve the Bluetooth Adapter's name and address and save it in
     * in the local cache
     */
    private void loadStoredNameAndAddress() {
        if (DBG) Log.d(TAG, "Loading stored name and address");
        if (mContext.getResources().getBoolean
            (com.android.internal.R.bool.config_bluetooth_address_validation) &&
             Settings.Secure.getInt(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 0) == 0) {
            // if the valid flag is not set, don't load the address and name
            if (DBG) Log.d(TAG, "invalid bluetooth name and address stored");
            return;
        }
        mName = Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME);
        mAddress = Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS);
        if (DBG) Log.d(TAG, "Stored bluetooth Name=" + mName + ",Address=" + mAddress);
    }

    /**
     * Save the Bluetooth name and address in the persistent store.
     * Only non-null values will be saved.
     * @param name
     * @param address
     */
    private void storeNameAndAddress(String name, String address) {
        if (name != null) {
            Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME, name);
            mName = name;
            if (DBG) Log.d(TAG,"Stored Bluetooth name: " +
                Settings.Secure.getString(mContentResolver,SECURE_SETTINGS_BLUETOOTH_NAME));
        }

        if (address != null) {
            Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS, address);
            mAddress=address;
            if (DBG)  Log.d(TAG,"Stored Bluetoothaddress: " +
                Settings.Secure.getString(mContentResolver,SECURE_SETTINGS_BLUETOOTH_ADDRESS));
        }

        if ((name != null) && (address != null)) {
            Settings.Secure.putInt(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 1);
        }
    }

    public IBluetooth registerAdapter(IBluetoothManagerCallback callback){
        if (callback == null) {
            Log.w(TAG, "Callback is null in registerAdapter");
            return null;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_ADAPTER);
        msg.obj = callback;
        mHandler.sendMessage(msg);
        synchronized(mConnection) {
            return mBluetooth;
        }
    }

    public void unregisterAdapter(IBluetoothManagerCallback callback) {
        if (callback == null) {
            Log.w(TAG, "Callback is null in unregisterAdapter");
            return;
        }
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_UNREGISTER_ADAPTER);
        msg.obj = callback;
        mHandler.sendMessage(msg);
    }

    public void registerStateChangeCallback(IBluetoothStateChangeCallback callback) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_STATE_CHANGE_CALLBACK);
        msg.obj = callback;
        mHandler.sendMessage(msg);
    }

    public void unregisterStateChangeCallback(IBluetoothStateChangeCallback callback) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");
        Message msg = mHandler.obtainMessage(MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK);
        msg.obj = callback;
        mHandler.sendMessage(msg);
    }

    public boolean isEnabled() {
        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"isEnabled(): not allowed for non-active and non system user");
            return false;
        }

        synchronized(mConnection) {
            try {
                return (mBluetooth != null && mBluetooth.isEnabled());
            } catch (RemoteException e) {
                Log.e(TAG, "isEnabled()", e);
            }
        }
        return false;
    }

    class ClientDeathRecipient implements IBinder.DeathRecipient {
        public void binderDied() {
            if (DBG) Log.d(TAG, "Binder is dead -  unregister Ble App");
            if (mBleAppCount > 0) --mBleAppCount;

            if (mBleAppCount == 0) {
                if (DBG) Log.d(TAG, "Disabling LE only mode after application crash");
                try {
                    if (mBluetooth != null) {
                        mBluetooth.onBrEdrDown();
                    }
                } catch(RemoteException e) {
                     Log.e(TAG,"Unable to call onBrEdrDown", e);
                }
            }
        }
    }

    /** Internal death rec list */
    Map<IBinder, ClientDeathRecipient> mBleApps = new HashMap<IBinder, ClientDeathRecipient>();

    @Override
    public boolean isBleScanAlwaysAvailable() {
        try {
            return (Settings.Global.getInt(mContentResolver,
                    Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE)) != 0;
        } catch (SettingNotFoundException e) {
        }
        return false;
    }

    // Monitor change of BLE scan only mode settings.
    private void registerForBleScanModeChange() {
        ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                if (!isBleScanAlwaysAvailable()) {
                    disableBleScanMode();
                    clearBleApps();
                    try {
                        if (mBluetooth != null) mBluetooth.onBrEdrDown();
                    } catch (RemoteException e) {
                        Log.e(TAG, "error when disabling bluetooth", e);
                    }
                }
            }
        };

        mContentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.BLE_SCAN_ALWAYS_AVAILABLE),
                false, contentObserver);
    }

    // Disable ble scan only mode.
    private void disableBleScanMode() {
        try {
            if (mBluetooth != null && (mBluetooth.getState() != BluetoothAdapter.STATE_ON)) {
                if (DBG) Log.d(TAG, "Reseting the mEnable flag for clean disable");
                mEnable = false;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getState()", e);
        }
    }

    public int updateBleAppCount(IBinder token, boolean enable) {
        if (enable) {
            ClientDeathRecipient r = mBleApps.get(token);
            if (r == null) {
                ClientDeathRecipient deathRec = new ClientDeathRecipient();
                try {
                    token.linkToDeath(deathRec, 0);
                } catch (RemoteException ex) {
                    throw new IllegalArgumentException("Wake lock is already dead.");
                }
                mBleApps.put(token, deathRec);
                synchronized (this) {
                    ++mBleAppCount;
                }
                if (DBG) Log.d(TAG, "Registered for death Notification");
            }

        } else  {
            ClientDeathRecipient r = mBleApps.get(token);
            if (r != null) {
                // Unregister death recipient as the app goes away.
                token.unlinkToDeath(r, 0);
                mBleApps.remove(token);
                synchronized (this) {
                    if (mBleAppCount > 0) --mBleAppCount;
                }
                if (DBG) Log.d(TAG, "Unregistered for death Notification");
            }
        }
        if (DBG) Log.d(TAG, "Updated BleAppCount" + mBleAppCount);
        if (mBleAppCount == 0 && mEnable) {
            disableBleScanMode();
        }
        return mBleAppCount;
    }

    // Clear all apps using BLE scan only mode.
    private void clearBleApps() {
        synchronized (this) {
            mBleApps.clear();
            mBleAppCount = 0;
        }
    }

    /** @hide*/
    public boolean isBleAppPresent() {
        if (DBG) Log.d(TAG, "isBleAppPresent() count: " + mBleAppCount);
        return (mBleAppCount > 0);
    }

    /**
     * Action taken when GattService is turned off
     */
    private void onBluetoothGattServiceUp() {
        if (DBG) Log.d(TAG,"BluetoothGatt Service is Up");
        try{
            if (isBleAppPresent() == false && mBluetooth != null
                  && mBluetooth.getState() == BluetoothAdapter.STATE_BLE_ON) {
                mBluetooth.onLeServiceUp();

                // waive WRITE_SECURE_SETTINGS permission check
                long callingIdentity = Binder.clearCallingIdentity();
                persistBluetoothSetting(BLUETOOTH_ON_BLUETOOTH);
                Binder.restoreCallingIdentity(callingIdentity);
            }
        } catch(RemoteException e) {
                Log.e(TAG,"Unable to call onServiceUp", e);
        }
    }

    /**
     * Inform BluetoothAdapter instances that BREDR part is down
     * and turn off all service and stack if no LE app needs it
     */
    private void sendBrEdrDownCallback() {
        if (DBG) Log.d(TAG,"Calling sendBrEdrDownCallback callbacks");

        if(mBluetooth == null) {
            Log.w(TAG, "Bluetooth handle is null");
            return;
        }

        if (isBleAppPresent() == false) {
            try {
                mBluetooth.onBrEdrDown();
            } catch(RemoteException e) {
                Log.e(TAG, "Call to onBrEdrDown() failed.", e);
            }
        } else {
            // Need to stay at BLE ON. Disconnect all Gatt connections
            try{
                mBluetoothGatt.unregAll();
            } catch(RemoteException e) {
                Log.e(TAG, "Unable to disconnect all apps.", e);
            }
        }
    }

    /** @hide*/
    public void getNameAndAddress() {
        if (DBG) {
            Log.d(TAG,"getNameAndAddress(): mBluetooth = " + mBluetooth +
                  " mBinding = " + mBinding);
        }
        Message msg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
        mHandler.sendMessage(msg);
    }
    public boolean enableNoAutoConnect()
    {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permission");

        if (DBG) {
            Log.d(TAG,"enableNoAutoConnect():  mBluetooth =" + mBluetooth +
                    " mBinding = " + mBinding);
        }
        int callingAppId = UserHandle.getAppId(Binder.getCallingUid());

        if (callingAppId != Process.NFC_UID) {
            throw new SecurityException("no permission to enable Bluetooth quietly");
        }

        synchronized(mReceiver) {
            mQuietEnableExternal = true;
            mEnableExternal = true;
            sendEnableMsg(true);
        }
        return true;

    }
    public boolean enable(String callingPackage) {
        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"enable(): not allowed for non-active and non system user");
            return false;
        }

        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permission");
        if (DBG) {
            Log.d(TAG,"enable():  mBluetooth =" + mBluetooth +
                    " mBinding = " + mBinding);
        }

        AppOpsManager appOps = (AppOpsManager) mContext
                .getSystemService(Context.APP_OPS_SERVICE);
        int callingUid = Binder.getCallingUid();
        if (appOps.noteOp(AppOpsManager.OP_BLUETOOTH_CHANGE, callingUid,
                callingPackage) != AppOpsManager.MODE_ALLOWED)
            return false;

        synchronized(mReceiver) {
            mQuietEnableExternal = false;
            mEnableExternal = true;
            // waive WRITE_SECURE_SETTINGS permission check
            sendEnableMsg(false);
        }
        if (DBG) Log.d(TAG, "enable returning");
        return true;
    }

    public boolean disable(boolean persist) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permissicacheNameAndAddresson");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"disable(): not allowed for non-active and non system user");
            return false;
        }

        if (DBG) {
            Log.d(TAG,"disable(): mBluetooth = " + mBluetooth +
                " mBinding = " + mBinding);
        }

        synchronized(mReceiver) {
            if (persist) {
                // waive WRITE_SECURE_SETTINGS permission check
                long callingIdentity = Binder.clearCallingIdentity();
                persistBluetoothSetting(BLUETOOTH_OFF);
                Binder.restoreCallingIdentity(callingIdentity);
            }
            mEnableExternal = false;
            sendDisableMsg();
        }
        return true;
    }

    public void unbindAndFinish() {
        if (DBG) {
            Log.d(TAG,"unbindAndFinish(): " + mBluetooth +
                " mBinding = " + mBinding);
        }

        synchronized (mConnection) {
            if (mUnbinding) return;
            mUnbinding = true;
            if (mBluetooth != null) {
                if (!mConnection.isGetNameAddressOnly()) {
                    //Unregister callback object
                    try {
                        mBluetooth.unregisterCallback(mBluetoothCallback);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Unable to unregister BluetoothCallback",re);
                    }
                }
                if (DBG) Log.d(TAG, "Sending unbind request.");
                mBluetooth = null;
                //Unbind
                mContext.unbindService(mConnection);
                mUnbinding = false;
                mBinding = false;
            } else {
                mUnbinding=false;
            }
            mBluetoothGatt = null;
        }
    }

    public IBluetoothGatt getBluetoothGatt() {
        // sync protection
        return mBluetoothGatt;
    }

    /**
     * Send enable message and set adapter name and address. Called when the boot phase becomes
     * PHASE_SYSTEM_SERVICES_READY.
     */
    public void handleOnBootPhase() {
        if (DBG) Log.d(TAG, "Bluetooth boot completed");
        if (mEnableExternal && isBluetoothPersistedStateOnBluetooth()) {
            if (DBG) Log.d(TAG, "Auto-enabling Bluetooth.");
            sendEnableMsg(mQuietEnableExternal);
        }
        if (!isNameAndAddressSet()) {
            // Sync the Bluetooth name and address from the Bluetooth Adapter
            if (DBG) Log.d(TAG, "Retrieving Bluetooth Adapter name and address...");
            getNameAndAddress();
        }
    }

    /**
     * Called when switching to a different foreground user.
     */
    public void handleOnSwitchUser(int userHandle) {
        if (DBG) Log.d(TAG, "Bluetooth user switched");
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_USER_SWITCHED, userHandle, 0));
    }

    private void sendBluetoothStateCallback(boolean isUp) {
        try {
            int n = mStateChangeCallbacks.beginBroadcast();
            if (DBG) Log.d(TAG,"Broadcasting onBluetoothStateChange("+isUp+") to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mStateChangeCallbacks.getBroadcastItem(i).onBluetoothStateChange(isUp);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to call onBluetoothStateChange() on callback #" + i , e);
                }
            }
        } finally {
            mStateChangeCallbacks.finishBroadcast();
        }
    }

    /**
     * Inform BluetoothAdapter instances that Adapter service is up
     */
    private void sendBluetoothServiceUpCallback() {
        if (!mConnection.isGetNameAddressOnly()) {
            if (DBG) Log.d(TAG,"Calling onBluetoothServiceUp callbacks");
            try {
                int n = mCallbacks.beginBroadcast();
                Log.d(TAG,"Broadcasting onBluetoothServiceUp() to " + n + " receivers.");
                for (int i=0; i <n;i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onBluetoothServiceUp(mBluetooth);
                    }  catch (RemoteException e) {
                        Log.e(TAG, "Unable to call onBluetoothServiceUp() on callback #" + i, e);
                    }
                }
            } finally {
                mCallbacks.finishBroadcast();
            }
        }
    }
    /**
     * Inform BluetoothAdapter instances that Adapter service is down
     */
    private void sendBluetoothServiceDownCallback() {
        if (!mConnection.isGetNameAddressOnly()) {
            if (DBG) Log.d(TAG,"Calling onBluetoothServiceDown callbacks");
            try {
                int n = mCallbacks.beginBroadcast();
                Log.d(TAG,"Broadcasting onBluetoothServiceDown() to " + n + " receivers.");
                for (int i=0; i <n;i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).onBluetoothServiceDown();
                    }  catch (RemoteException e) {
                        Log.e(TAG, "Unable to call onBluetoothServiceDown() on callback #" + i, e);
                    }
                }
            } finally {
                mCallbacks.finishBroadcast();
            }
        }
    }

    public String getAddress() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                "Need BLUETOOTH permission");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"getAddress(): not allowed for non-active and non system user");
            return null;
        }

        if (mContext.checkCallingOrSelfPermission(Manifest.permission.LOCAL_MAC_ADDRESS)
                != PackageManager.PERMISSION_GRANTED) {
            return BluetoothAdapter.DEFAULT_MAC_ADDRESS;
        }

        synchronized(mConnection) {
            if (mBluetooth != null) {
                try {
                    return mBluetooth.getAddress();
                } catch (RemoteException e) {
                    Log.e(TAG, "getAddress(): Unable to retrieve address remotely..Returning cached address",e);
                }
            }
        }
        // mAddress is accessed from outside.
        // It is alright without a lock. Here, bluetooth is off, no other thread is
        // changing mAddress
        return mAddress;
    }

    public String getName() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                                                "Need BLUETOOTH permission");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Log.w(TAG,"getName(): not allowed for non-active and non system user");
            return null;
        }

        synchronized(mConnection) {
            if (mBluetooth != null) {
                try {
                    return mBluetooth.getName();
                } catch (RemoteException e) {
                    Log.e(TAG, "getName(): Unable to retrieve name remotely..Returning cached name",e);
                }
            }
        }
        // mName is accessed from outside.
        // It alright without a lock. Here, bluetooth is off, no other thread is
        // changing mName
        return mName;
    }

    private class BluetoothServiceConnection implements ServiceConnection {

        private boolean mGetNameAddressOnly;

        public void setGetNameAddressOnly(boolean getOnly) {
            mGetNameAddressOnly = getOnly;
        }

        public boolean isGetNameAddressOnly() {
            return mGetNameAddressOnly;
        }

        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Log.d(TAG, "BluetoothServiceConnection: " + className.getClassName());
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_SERVICE_CONNECTED);
            // TBD if (className.getClassName().equals(IBluetooth.class.getName())) {
            if (className.getClassName().equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = SERVICE_IBLUETOOTH;
                // } else if (className.getClassName().equals(IBluetoothGatt.class.getName())) {
            } else if (className.getClassName().equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = SERVICE_IBLUETOOTHGATT;
            } else {
                Log.e(TAG, "Unknown service connected: " + className.getClassName());
                return;
            }
            msg.obj = service;
            mHandler.sendMessage(msg);
        }

        public void onServiceDisconnected(ComponentName className) {
            // Called if we unexpected disconnected.
            if (DBG) Log.d(TAG, "BluetoothServiceConnection, disconnected: " +
                           className.getClassName());
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED);
            if (className.getClassName().equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = SERVICE_IBLUETOOTH;
            } else if (className.getClassName().equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = SERVICE_IBLUETOOTHGATT;
            } else {
                Log.e(TAG, "Unknown service disconnected: " + className.getClassName());
                return;
            }
            mHandler.sendMessage(msg);
        }
    }

    private BluetoothServiceConnection mConnection = new BluetoothServiceConnection();

    private class BluetoothHandler extends Handler {
        public BluetoothHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DBG) Log.d (TAG, "Message: " + msg.what);
            switch (msg.what) {
                case MESSAGE_GET_NAME_AND_ADDRESS: {
                    if (DBG) Log.d(TAG,"MESSAGE_GET_NAME_AND_ADDRESS");
                    synchronized(mConnection) {
                        //Start bind request
                        if ((mBluetooth == null) && (!mBinding)) {
                            if (DBG) Log.d(TAG, "Binding to service to get name and address");
                            mConnection.setGetNameAddressOnly(true);
                            //Start bind timeout and bind
                            Message timeoutMsg = mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                            mHandler.sendMessageDelayed(timeoutMsg,TIMEOUT_BIND_MS);
                            Intent i = new Intent(IBluetooth.class.getName());
                            if (!doBind(i, mConnection,
                                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                                    UserHandle.CURRENT)) {
                                mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                            } else {
                                mBinding = true;
                            }
                        }
                        else {
                            Message saveMsg= mHandler.obtainMessage(MESSAGE_SAVE_NAME_AND_ADDRESS);
                            saveMsg.arg1 = 0;
                            if (mBluetooth != null) {
                                mHandler.sendMessage(saveMsg);
                            } else {
                                // if enable is also called to bind the service
                                // wait for MESSAGE_BLUETOOTH_SERVICE_CONNECTED
                                mHandler.sendMessageDelayed(saveMsg, TIMEOUT_SAVE_MS);
                            }
                        }
                    }
                    break;
                }
                case MESSAGE_SAVE_NAME_AND_ADDRESS: {
                    boolean unbind = false;
                    if (DBG) Log.d(TAG,"MESSAGE_SAVE_NAME_AND_ADDRESS");
                    synchronized(mConnection) {
                        if (!mEnable && mBluetooth != null && !mConnection.isGetNameAddressOnly()) {
                            try {
                                mBluetooth.enable();
                            } catch (RemoteException e) {
                                Log.e(TAG,"Unable to call enable()",e);
                            }
                        }
                    }
                    if (mBluetooth != null && !mConnection.isGetNameAddressOnly()) waitForOnOff(true, false);
                    synchronized(mConnection) {
                        if (mBluetooth != null) {
                            String name =  null;
                            String address = null;
                            try {
                                name =  mBluetooth.getName();
                                address = mBluetooth.getAddress();
                            } catch (RemoteException re) {
                                Log.e(TAG,"",re);
                            }

                            if (name != null && address != null) {
                                storeNameAndAddress(name,address);
                                if (mConnection.isGetNameAddressOnly()) {
                                    unbind = true;
                                }
                            } else {
                                if (msg.arg1 < MAX_SAVE_RETRIES) {
                                    Message retryMsg = mHandler.obtainMessage(MESSAGE_SAVE_NAME_AND_ADDRESS);
                                    retryMsg.arg1= 1+msg.arg1;
                                    if (DBG) Log.d(TAG,"Retrying name/address remote retrieval and save.....Retry count =" + retryMsg.arg1);
                                    mHandler.sendMessageDelayed(retryMsg, TIMEOUT_SAVE_MS);
                                } else {
                                    Log.w(TAG,"Maximum name/address remote retrieval retry exceeded");
                                    if (mConnection.isGetNameAddressOnly()) {
                                        unbind = true;
                                    }
                                }
                            }
                            if (!mEnable && !mConnection.isGetNameAddressOnly()) {
                                try {
                                    mBluetooth.disable();
                                } catch (RemoteException e) {
                                    Log.e(TAG,"Unable to call disable()",e);
                                }
                            }
                        } else {
                            // rebind service by Request GET NAME AND ADDRESS
                            // if service is unbinded by disable or
                            // MESSAGE_BLUETOOTH_SERVICE_CONNECTED is not received
                            Message getMsg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
                            mHandler.sendMessage(getMsg);
                        }
                    }
                    if (!mEnable && mBluetooth != null && !mConnection.isGetNameAddressOnly()) {
                        waitForOnOff(false, true);
                    }
                    if (unbind) {
                        unbindAndFinish();
                    }
                    break;
                }
                case MESSAGE_ENABLE:
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_ENABLE: mBluetooth = " + mBluetooth);
                    }
                    mHandler.removeMessages(MESSAGE_RESTART_BLUETOOTH_SERVICE);
                    mEnable = true;
                    handleEnable(msg.arg1 == 1);
                    break;

                case MESSAGE_DISABLE:
                    mHandler.removeMessages(MESSAGE_RESTART_BLUETOOTH_SERVICE);
                    if (mEnable && mBluetooth != null) {
                        waitForOnOff(true, false);
                        mEnable = false;
                        handleDisable();
                        waitForOnOff(false, false);
                    } else {
                        mEnable = false;
                        handleDisable();
                    }
                    break;

                case MESSAGE_REGISTER_ADAPTER:
                {
                    IBluetoothManagerCallback callback = (IBluetoothManagerCallback) msg.obj;
                    boolean added = mCallbacks.register(callback);
                    Log.d(TAG,"Added callback: " +  (callback == null? "null": callback)  +":" +added );
                }
                    break;
                case MESSAGE_UNREGISTER_ADAPTER:
                {
                    IBluetoothManagerCallback callback = (IBluetoothManagerCallback) msg.obj;
                    boolean removed = mCallbacks.unregister(callback);
                    Log.d(TAG,"Removed callback: " +  (callback == null? "null": callback)  +":" + removed);
                    break;
                }
                case MESSAGE_REGISTER_STATE_CHANGE_CALLBACK:
                {
                    IBluetoothStateChangeCallback callback = (IBluetoothStateChangeCallback) msg.obj;
                    if (callback != null) {
                        mStateChangeCallbacks.register(callback);
                    }
                    break;
                }
                case MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK:
                {
                    IBluetoothStateChangeCallback callback = (IBluetoothStateChangeCallback) msg.obj;
                    if (callback != null) {
                        mStateChangeCallbacks.unregister(callback);
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_SERVICE_CONNECTED:
                {
                    if (DBG) Log.d(TAG,"MESSAGE_BLUETOOTH_SERVICE_CONNECTED: " + msg.arg1);

                    IBinder service = (IBinder) msg.obj;
                    synchronized(mConnection) {
                        if (msg.arg1 == SERVICE_IBLUETOOTHGATT) {
                            mBluetoothGatt = IBluetoothGatt.Stub.asInterface(service);
                            onBluetoothGattServiceUp();
                            break;
                        } // else must be SERVICE_IBLUETOOTH

                        //Remove timeout
                        mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);

                        mBinding = false;
                        mBluetooth = IBluetooth.Stub.asInterface(service);

                        try {
                            boolean enableHciSnoopLog = (Settings.Secure.getInt(mContentResolver,
                                Settings.Secure.BLUETOOTH_HCI_LOG, 0) == 1);
                            if (!mBluetooth.configHciSnoopLog(enableHciSnoopLog)) {
                                Log.e(TAG,"IBluetooth.configHciSnoopLog return false");
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG,"Unable to call configHciSnoopLog", e);
                        }

                        if (mConnection.isGetNameAddressOnly()) {
                            //Request GET NAME AND ADDRESS
                            Message getMsg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
                            mHandler.sendMessage(getMsg);
                            if (!mEnable) return;
                        }

                        mConnection.setGetNameAddressOnly(false);
                        //Register callback object
                        try {
                            mBluetooth.registerCallback(mBluetoothCallback);
                        } catch (RemoteException re) {
                            Log.e(TAG, "Unable to register BluetoothCallback",re);
                        }
                        //Inform BluetoothAdapter instances that service is up
                        sendBluetoothServiceUpCallback();

                        //Do enable request
                        try {
                            if (mQuietEnable == false) {
                                if(!mBluetooth.enable()) {
                                    Log.e(TAG,"IBluetooth.enable() returned false");
                                }
                            }
                            else
                            {
                                if(!mBluetooth.enableNoAutoConnect()) {
                                    Log.e(TAG,"IBluetooth.enableNoAutoConnect() returned false");
                                }
                            }
                        } catch (RemoteException e) {
                            Log.e(TAG,"Unable to call enable()",e);
                        }
                    }

                    if (!mEnable) {
                        waitForOnOff(true, false);
                        handleDisable();
                        waitForOnOff(false, false);
                    }
                    break;
                }
                case MESSAGE_TIMEOUT_BIND: {
                    Log.e(TAG, "MESSAGE_TIMEOUT_BIND");
                    synchronized(mConnection) {
                        mBinding = false;
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_STATE_CHANGE:
                {
                    int prevState = msg.arg1;
                    int newState = msg.arg2;
                    if (DBG) Log.d(TAG, "MESSAGE_BLUETOOTH_STATE_CHANGE: prevState = " + prevState + ", newState=" + newState);
                    mState = newState;
                    bluetoothStateChangeHandler(prevState, newState);
                    // handle error state transition case from TURNING_ON to OFF
                    // unbind and rebind bluetooth service and enable bluetooth
                    if ((prevState == BluetoothAdapter.STATE_BLE_TURNING_ON) &&
                        (newState == BluetoothAdapter.STATE_OFF) &&
                        (mBluetooth != null) && mEnable) {
                        recoverBluetoothServiceFromError();
                    }
                    if ((prevState == BluetoothAdapter.STATE_TURNING_ON) &&
                        (newState == BluetoothAdapter.STATE_BLE_ON) &&
                        (mBluetooth != null) && mEnable) {
                        recoverBluetoothServiceFromError();
                    }
                    if (newState == BluetoothAdapter.STATE_ON ||
                        newState == BluetoothAdapter.STATE_BLE_ON) {
                        // bluetooth is working, reset the counter
                        if (mErrorRecoveryRetryCounter != 0) {
                            Log.w(TAG, "bluetooth is recovered from error");
                            mErrorRecoveryRetryCounter = 0;
                        }
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED:
                {
                    Log.e(TAG, "MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED: " + msg.arg1);
                    synchronized(mConnection) {
                        if (msg.arg1 == SERVICE_IBLUETOOTH) {
                            // if service is unbinded already, do nothing and return
                            if (mBluetooth == null) break;
                            mBluetooth = null;
                        } else if (msg.arg1 == SERVICE_IBLUETOOTHGATT) {
                            mBluetoothGatt = null;
                            break;
                        } else {
                            Log.e(TAG, "Bad msg.arg1: " + msg.arg1);
                            break;
                        }
                    }

                    if (mEnable) {
                        mEnable = false;
                        // Send a Bluetooth Restart message
                        Message restartMsg = mHandler.obtainMessage(
                            MESSAGE_RESTART_BLUETOOTH_SERVICE);
                        mHandler.sendMessageDelayed(restartMsg,
                            SERVICE_RESTART_TIME_MS);
                    }

                    if (!mConnection.isGetNameAddressOnly()) {
                        sendBluetoothServiceDownCallback();

                        // Send BT state broadcast to update
                        // the BT icon correctly
                        if ((mState == BluetoothAdapter.STATE_TURNING_ON) ||
                            (mState == BluetoothAdapter.STATE_ON)) {
                            bluetoothStateChangeHandler(BluetoothAdapter.STATE_ON,
                                                        BluetoothAdapter.STATE_TURNING_OFF);
                            mState = BluetoothAdapter.STATE_TURNING_OFF;
                        }
                        if (mState == BluetoothAdapter.STATE_TURNING_OFF) {
                            bluetoothStateChangeHandler(BluetoothAdapter.STATE_TURNING_OFF,
                                                        BluetoothAdapter.STATE_OFF);
                        }

                        mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
                        mState = BluetoothAdapter.STATE_OFF;
                    }
                    break;
                }
                case MESSAGE_RESTART_BLUETOOTH_SERVICE:
                {
                    Log.d(TAG, "MESSAGE_RESTART_BLUETOOTH_SERVICE:"
                        +" Restart IBluetooth service");
                    /* Enable without persisting the setting as
                     it doesnt change when IBluetooth
                     service restarts */
                    mEnable = true;
                    handleEnable(mQuietEnable);
                    break;
                }

                case MESSAGE_TIMEOUT_UNBIND:
                {
                    Log.e(TAG, "MESSAGE_TIMEOUT_UNBIND");
                    synchronized(mConnection) {
                        mUnbinding = false;
                    }
                    break;
                }

                case MESSAGE_USER_SWITCHED:
                {
                    if (DBG) {
                        Log.d(TAG, "MESSAGE_USER_SWITCHED");
                    }
                    mHandler.removeMessages(MESSAGE_USER_SWITCHED);
                    /* disable and enable BT when detect a user switch */
                    if (mEnable && mBluetooth != null) {
                        synchronized (mConnection) {
                            if (mBluetooth != null) {
                                //Unregister callback object
                                try {
                                    mBluetooth.unregisterCallback(mBluetoothCallback);
                                } catch (RemoteException re) {
                                    Log.e(TAG, "Unable to unregister",re);
                                }
                            }
                        }

                        if (mState == BluetoothAdapter.STATE_TURNING_OFF) {
                            // MESSAGE_USER_SWITCHED happened right after MESSAGE_ENABLE
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_OFF);
                            mState = BluetoothAdapter.STATE_OFF;
                        }
                        if (mState == BluetoothAdapter.STATE_OFF) {
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_TURNING_ON);
                            mState = BluetoothAdapter.STATE_TURNING_ON;
                        }

                        waitForMonitoredOnOff(true, false);

                        if (mState == BluetoothAdapter.STATE_TURNING_ON) {
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_ON);
                        }

                        // disable
                        handleDisable();
                        // Pbap service need receive STATE_TURNING_OFF intent to close
                        bluetoothStateChangeHandler(BluetoothAdapter.STATE_ON,
                                                    BluetoothAdapter.STATE_TURNING_OFF);

                        waitForMonitoredOnOff(false, true);

                        bluetoothStateChangeHandler(BluetoothAdapter.STATE_TURNING_OFF,
                                                    BluetoothAdapter.STATE_OFF);
                        sendBluetoothServiceDownCallback();
                        synchronized (mConnection) {
                            if (mBluetooth != null) {
                                mBluetooth = null;
                                //Unbind
                                mContext.unbindService(mConnection);
                            }
                            mBluetoothGatt = null;
                        }
                        SystemClock.sleep(100);

                        mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
                        mState = BluetoothAdapter.STATE_OFF;
                        // enable
                        handleEnable(mQuietEnable);
                    } else if (mBinding || mBluetooth != null) {
                        Message userMsg = mHandler.obtainMessage(MESSAGE_USER_SWITCHED);
                        userMsg.arg2 = 1 + msg.arg2;
                        // if user is switched when service is being binding
                        // delay sending MESSAGE_USER_SWITCHED
                        mHandler.sendMessageDelayed(userMsg, USER_SWITCHED_TIME_MS);
                        if (DBG) {
                            Log.d(TAG, "delay MESSAGE_USER_SWITCHED " + userMsg.arg2);
                        }
                    }
                    break;
                }
            }
        }
    }

    private void handleEnable(boolean quietMode) {
        mQuietEnable = quietMode;

        synchronized(mConnection) {
            if ((mBluetooth == null) && (!mBinding)) {
                //Start bind timeout and bind
                Message timeoutMsg=mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                mHandler.sendMessageDelayed(timeoutMsg,TIMEOUT_BIND_MS);
                mConnection.setGetNameAddressOnly(false);
                Intent i = new Intent(IBluetooth.class.getName());
                if (!doBind(i, mConnection,Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                        UserHandle.CURRENT)) {
                    mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                } else {
                    mBinding = true;
                }
            } else if (mBluetooth != null) {
                if (mConnection.isGetNameAddressOnly()) {
                    // if GetNameAddressOnly is set, we can clear this flag,
                    // so the service won't be unbind
                    // after name and address are saved
                    mConnection.setGetNameAddressOnly(false);
                    //Register callback object
                    try {
                        mBluetooth.registerCallback(mBluetoothCallback);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Unable to register BluetoothCallback",re);
                    }
                    //Inform BluetoothAdapter instances that service is up
                    sendBluetoothServiceUpCallback();
                }

                //Enable bluetooth
                try {
                    if (!mQuietEnable) {
                        if(!mBluetooth.enable()) {
                            Log.e(TAG,"IBluetooth.enable() returned false");
                        }
                    }
                    else {
                        if(!mBluetooth.enableNoAutoConnect()) {
                            Log.e(TAG,"IBluetooth.enableNoAutoConnect() returned false");
                        }
                    }
                } catch (RemoteException e) {
                    Log.e(TAG,"Unable to call enable()",e);
                }
            }
        }
    }

    boolean doBind(Intent intent, ServiceConnection conn, int flags, UserHandle user) {
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, conn, flags, user)) {
            Log.e(TAG, "Fail to bind to: " + intent);
            return false;
        }
        return true;
    }

    private void handleDisable() {
        synchronized(mConnection) {
            // don't need to disable if GetNameAddressOnly is set,
            // service will be unbinded after Name and Address are saved
            if ((mBluetooth != null) && (!mConnection.isGetNameAddressOnly())) {
                if (DBG) Log.d(TAG,"Sending off request.");

                try {
                    if(!mBluetooth.disable()) {
                        Log.e(TAG,"IBluetooth.disable() returned false");
                    }
                } catch (RemoteException e) {
                    Log.e(TAG,"Unable to call disable()",e);
                }
            }
        }
    }

    private boolean checkIfCallerIsForegroundUser() {
        int foregroundUser;
        int callingUser = UserHandle.getCallingUserId();
        int callingUid = Binder.getCallingUid();
        long callingIdentity = Binder.clearCallingIdentity();
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        UserInfo ui = um.getProfileParent(callingUser);
        int parentUser = (ui != null) ? ui.id : UserHandle.USER_NULL;
        int callingAppId = UserHandle.getAppId(callingUid);
        boolean valid = false;
        try {
            foregroundUser = ActivityManager.getCurrentUser();
            valid = (callingUser == foregroundUser) ||
                    parentUser == foregroundUser    ||
                    callingAppId == Process.NFC_UID ||
                    callingAppId == mSystemUiUid;
            if (DBG) {
                Log.d(TAG, "checkIfCallerIsForegroundUser: valid=" + valid
                    + " callingUser=" + callingUser
                    + " parentUser=" + parentUser
                    + " foregroundUser=" + foregroundUser);
            }
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
        return valid;
    }

    private void sendBleStateChanged(int prevState, int newState) {
        if (DBG) Log.d(TAG,"BLE State Change Intent: " + prevState + " -> " + newState);
        // Send broadcast message to everyone else
        Intent intent = new Intent(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_PERM);
    }

    private void bluetoothStateChangeHandler(int prevState, int newState) {
        boolean isStandardBroadcast = true;
        if (prevState != newState) {
            //Notify all proxy objects first of adapter state change
            if (newState == BluetoothAdapter.STATE_BLE_ON
                   || newState == BluetoothAdapter.STATE_OFF) {
                boolean intermediate_off = (prevState == BluetoothAdapter.STATE_TURNING_OFF
                   && newState == BluetoothAdapter.STATE_BLE_ON);

                if (newState == BluetoothAdapter.STATE_OFF) {
                    // If Bluetooth is off, send service down event to proxy objects, and unbind
                    if (DBG) Log.d(TAG, "Bluetooth is complete turn off");
                    if (canUnbindBluetoothService()) {
                        if (DBG) Log.d(TAG, "Good to unbind!");
                        sendBluetoothServiceDownCallback();
                        unbindAndFinish();
                        sendBleStateChanged(prevState, newState);
                        // Don't broadcast as it has already been broadcast before
                        if(!mIntentPending) {
                            isStandardBroadcast = false;
                        } else {
                            mIntentPending = false;
                        }
                    }

                } else if (!intermediate_off) {
                    // connect to GattService
                    if (DBG) Log.d(TAG, "Bluetooth is in LE only mode");
                    if (mBluetoothGatt != null) {
                        if (DBG) Log.d(TAG, "Calling BluetoothGattServiceUp");
                        onBluetoothGattServiceUp();
                    } else {
                        if (DBG) Log.d(TAG, "Binding Bluetooth GATT service");
                        if (mContext.getPackageManager().hasSystemFeature(
                                                        PackageManager.FEATURE_BLUETOOTH_LE)) {
                            Intent i = new Intent(IBluetoothGatt.class.getName());
                            doBind(i, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.CURRENT);
                        }
                    }
                    sendBleStateChanged(prevState, newState);
                    //Don't broadcase this as std intent
                    isStandardBroadcast = false;

                } else if (intermediate_off){
                    if (DBG) Log.d(TAG, "Intermediate off, back to LE only mode");
                    // For LE only mode, broadcast as is
                    sendBleStateChanged(prevState, newState);
                    sendBluetoothStateCallback(false); // BT is OFF for general users
                    // Broadcast as STATE_OFF
                    newState = BluetoothAdapter.STATE_OFF;
                    sendBrEdrDownCallback();
                    if(!isBleAppPresent()){
                        isStandardBroadcast = false;
                        mIntentPending = true;
                    } else {
                        mIntentPending = false;
                        isStandardBroadcast = true;
                    }
                }
            } else if (newState == BluetoothAdapter.STATE_ON) {
                boolean isUp = (newState==BluetoothAdapter.STATE_ON);
                sendBluetoothStateCallback(isUp);
                sendBleStateChanged(prevState, newState);

            } else if (newState == BluetoothAdapter.STATE_BLE_TURNING_ON
                || newState == BluetoothAdapter.STATE_BLE_TURNING_OFF ) {
                sendBleStateChanged(prevState, newState);
                isStandardBroadcast = false;

            } else if (newState == BluetoothAdapter.STATE_TURNING_ON
                || newState == BluetoothAdapter.STATE_TURNING_OFF) {
                sendBleStateChanged(prevState, newState);
            }

            if( newState == BluetoothAdapter.STATE_TURNING_ON
               && prevState == BluetoothAdapter.STATE_BLE_ON) {
                mEnable = true;
            }

            if (isStandardBroadcast) {
                if (prevState == BluetoothAdapter.STATE_BLE_ON) {
                    // Show prevState of BLE_ON as OFF to standard users
                    prevState = BluetoothAdapter.STATE_OFF;
                }
                else if (prevState == BluetoothAdapter.STATE_BLE_TURNING_OFF) {
                    // show prevState to TURNING_OFF
                    prevState = BluetoothAdapter.STATE_TURNING_OFF;
                }
                Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
                intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prevState);
                intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
                intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                mContext.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_PERM);
            }
        }
    }

    /**
     *  if on is true, wait for state become ON
     *  if off is true, wait for state become OFF
     *  if both on and off are false, wait for state not ON
     */
    private boolean waitForOnOff(boolean on, boolean off) {
        int i = 0;
        while (i < 10) {
            synchronized(mConnection) {
                try {
                    if (mBluetooth == null) break;
                    if (on) {
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_ON) return true;
                    } else if (off) {
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_OFF) return true;
                    } else {
                        if (mBluetooth.getState() != BluetoothAdapter.STATE_ON) return true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "getState()", e);
                    break;
                }
            }
            if (on || off) {
                SystemClock.sleep(300);
            } else {
                SystemClock.sleep(50);
            }
            i++;
        }
        Log.e(TAG,"waitForOnOff time out");
        return false;
    }

    /**
     *  if on is true, wait for state become ON
     *  if off is true, wait for state become OFF
     *  if both on and off are false, wait for state not ON
     */
    private boolean waitForMonitoredOnOff(boolean on, boolean off) {
        int i = 0;
        while (i < 10) {
            synchronized(mConnection) {
                try {
                    if (mBluetooth == null) break;
                    if (on) {
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_ON) return true;
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_BLE_ON) {
                            bluetoothStateChangeHandler(BluetoothAdapter.STATE_BLE_TURNING_ON,
                                                        BluetoothAdapter.STATE_BLE_ON);
                        }
                    } else if (off) {
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_OFF) return true;
                        if (mBluetooth.getState() == BluetoothAdapter.STATE_BLE_ON) {
                            bluetoothStateChangeHandler(BluetoothAdapter.STATE_TURNING_OFF,
                                                        BluetoothAdapter.STATE_BLE_ON);
                        }
                    } else {
                        if (mBluetooth.getState() != BluetoothAdapter.STATE_ON) return true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "getState()", e);
                    break;
                }
            }
            if (on || off) {
                SystemClock.sleep(800);
            } else {
                SystemClock.sleep(50);
            }
            i++;
        }
        Log.e(TAG,"waitForOnOff time out");
        return false;
    }

    private void sendDisableMsg() {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_DISABLE));
    }

    private void sendEnableMsg(boolean quietMode) {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ENABLE,
                             quietMode ? 1 : 0, 0));
    }

    private boolean canUnbindBluetoothService() {
        synchronized(mConnection) {
            //Only unbind with mEnable flag not set
            //For race condition: disable and enable back-to-back
            //Avoid unbind right after enable due to callback from disable
            //Only unbind with Bluetooth at OFF state
            //Only unbind without any MESSAGE_BLUETOOTH_STATE_CHANGE message
            try {
                if (mEnable || (mBluetooth == null)) return false;
                if (mHandler.hasMessages(MESSAGE_BLUETOOTH_STATE_CHANGE)) return false;
                return (mBluetooth.getState() == BluetoothAdapter.STATE_OFF);
            } catch (RemoteException e) {
                Log.e(TAG, "getState()", e);
            }
        }
        return false;
    }

    private void recoverBluetoothServiceFromError() {
        Log.e(TAG,"recoverBluetoothServiceFromError");
        synchronized (mConnection) {
            if (mBluetooth != null) {
                //Unregister callback object
                try {
                    mBluetooth.unregisterCallback(mBluetoothCallback);
                } catch (RemoteException re) {
                    Log.e(TAG, "Unable to unregister",re);
                }
            }
        }

        SystemClock.sleep(500);

        // disable
        handleDisable();

        waitForOnOff(false, true);

        sendBluetoothServiceDownCallback();
        synchronized (mConnection) {
            if (mBluetooth != null) {
                mBluetooth = null;
                //Unbind
                mContext.unbindService(mConnection);
            }
            mBluetoothGatt = null;
        }

        mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
        mState = BluetoothAdapter.STATE_OFF;

        mEnable = false;

        if (mErrorRecoveryRetryCounter++ < MAX_ERROR_RESTART_RETRIES) {
            // Send a Bluetooth Restart message to reenable bluetooth
            Message restartMsg = mHandler.obtainMessage(
                             MESSAGE_RESTART_BLUETOOTH_SERVICE);
            mHandler.sendMessageDelayed(restartMsg, ERROR_RESTART_TIME_MS);
        } else {
            // todo: notify user to power down and power up phone to make bluetooth work.
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.DUMP, TAG);

        writer.println("Bluetooth Status");
        writer.println("  enabled: " + mEnable);
        writer.println("  state: " + mState);
        writer.println("  address: " + mAddress);
        writer.println("  name: " + mName + "\n");
        writer.flush();

        if (mBluetooth == null) {
            writer.println("Bluetooth Service not connected");
        } else {
            ParcelFileDescriptor pfd = null;
            try {
                writer.println("Bonded devices:");
                for (BluetoothDevice device : mBluetooth.getBondedDevices()) {
                    writer.println("  " + device.getAddress() +
                            " [" + DEVICE_TYPE_NAMES[device.getType()] + "] " +
                            device.getName());
                }
                writer.flush();

                pfd = ParcelFileDescriptor.dup(fd);
                mBluetooth.dump(pfd);
            } catch (RemoteException re) {
                writer.println("RemoteException while calling Bluetooth Service");
            } catch (IOException ioe) {
                writer.println("IOException attempting to dup() fd");
            } finally {
                if (pfd != null) {
                    try {
                        pfd.close();
                    } catch (IOException ioe) {
                        writer.println("IOException attempting to close() fd");
                    }
                }
            }
        }
    }
}
