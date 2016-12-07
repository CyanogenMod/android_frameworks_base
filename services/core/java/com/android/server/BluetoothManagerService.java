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
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothGatt;
import android.bluetooth.IBluetoothHeadset;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothProfileServiceConnection;
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
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Slog;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import java.io.FileDescriptor;
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
    // Delay for the addProxy function in msec
    private static final int ADD_PROXY_DELAY_MS = 100;

    private static final int MESSAGE_ENABLE = 1;
    private static final int MESSAGE_DISABLE = 2;
    private static final int MESSAGE_REGISTER_ADAPTER = 20;
    private static final int MESSAGE_UNREGISTER_ADAPTER = 21;
    private static final int MESSAGE_REGISTER_STATE_CHANGE_CALLBACK = 30;
    private static final int MESSAGE_UNREGISTER_STATE_CHANGE_CALLBACK = 31;
    private static final int MESSAGE_BLUETOOTH_SERVICE_CONNECTED = 40;
    private static final int MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED = 41;
    private static final int MESSAGE_RESTART_BLUETOOTH_SERVICE = 42;
    private static final int MESSAGE_BLUETOOTH_STATE_CHANGE = 60;
    private static final int MESSAGE_TIMEOUT_BIND = 100;
    private static final int MESSAGE_TIMEOUT_UNBIND = 101;
    private static final int MESSAGE_GET_NAME_AND_ADDRESS = 200;
    private static final int MESSAGE_USER_SWITCHED = 300;
    private static final int MESSAGE_USER_UNLOCKED = 301;
    private static final int MESSAGE_ADD_PROXY_DELAYED = 400;
    private static final int MESSAGE_BIND_PROFILE_SERVICE = 401;
    private static final int MAX_SAVE_RETRIES = 3;
    private static final int MAX_ERROR_RESTART_RETRIES = 6;

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

    private final Context mContext;
    private static int mBleAppCount = 0;

    // Locks are not provided for mName and mAddress.
    // They are accessed in handler or broadcast receiver, same thread context.
    private String mAddress;
    private String mName;
    private final ContentResolver mContentResolver;
    private final RemoteCallbackList<IBluetoothManagerCallback> mCallbacks;
    private final RemoteCallbackList<IBluetoothStateChangeCallback> mStateChangeCallbacks;
    private IBinder mBluetoothBinder;
    private IBluetooth mBluetooth;
    private IBluetoothGatt mBluetoothGatt;
    private final ReentrantReadWriteLock mBluetoothLock =
        new ReentrantReadWriteLock();
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
    private int mIBluetoothConnectedMsgQueued = 0;

    // Save a ProfileServiceConnections object for each of the bound
    // bluetooth profile services
    private final Map <Integer, ProfileServiceConnections> mProfileServices =
            new HashMap <Integer, ProfileServiceConnections>();

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
                if (DBG) Slog.d(TAG, "Bluetooth Adapter name changed to " + newName);
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
                    try {
                        mBluetoothLock.readLock().lock();
                        if (mBluetooth != null) {
                            st = mBluetooth.getState();
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to call getState", e);
                    } finally {
                        mBluetoothLock.readLock().unlock();
                    }
                    Slog.d(TAG, "Airplane Mode change - current state: " + st);

                    if (isAirplaneModeOn()) {
                        // Clear registered LE apps to force shut-off
                        synchronized (this) {
                            mBleAppCount = 0;
                            mBleApps.clear();
                        }
                        if (st == BluetoothAdapter.STATE_BLE_ON) {
                            //if state is BLE_ON make sure you trigger disableBLE part
                            try {
                                mBluetoothLock.readLock().lock();
                                if (mBluetooth != null) {
                                    mBluetooth.onBrEdrDown();
                                    mEnable = false;
                                    mEnableExternal = false;
                                }
                            } catch (RemoteException e) {
                                Slog.e(TAG,"Unable to call onBrEdrDown", e);
                            } finally {
                                mBluetoothLock.readLock().unlock();
                            }
                        } else if (st == BluetoothAdapter.STATE_ON){
                            // disable without persisting the setting
                            Slog.d(TAG, "Calling disable");
                            sendDisableMsg();
                        }
                    } else if (mEnableExternal) {
                        // enable without persisting the setting
                        Slog.d(TAG, "Calling enable");
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
        mBluetoothBinder = null;
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
            if (DBG) Slog.d(TAG, "Startup: Bluetooth persisted state is ON.");
            mEnableExternal = true;
        }

        int sysUiUid = -1;
        try {
            sysUiUid = mContext.getPackageManager().getPackageUidAsUser("com.android.systemui",
                    PackageManager.MATCH_SYSTEM_ONLY, UserHandle.USER_SYSTEM);
        } catch (PackageManager.NameNotFoundException e) {
            // Some platforms, such as wearables do not have a system ui.
            Slog.w(TAG, "Unable to resolve SystemUI's UID.", e);
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
        int state = Settings.Global.getInt(mContentResolver,
                                           Settings.Global.BLUETOOTH_ON, -1);
        if (DBG) Slog.d(TAG, "Bluetooth persisted state: " + state);
        return state != BLUETOOTH_OFF;
    }

    /**
     *  Returns true if the Bluetooth saved state is BLUETOOTH_ON_BLUETOOTH
     */
    private final boolean isBluetoothPersistedStateOnBluetooth() {
        return Settings.Global.getInt(mContentResolver,
                Settings.Global.BLUETOOTH_ON, BLUETOOTH_ON_BLUETOOTH) == BLUETOOTH_ON_BLUETOOTH;
    }

    /**
     *  Save the Bluetooth on/off state
     *
     */
    private void persistBluetoothSetting(int value) {
        if (DBG) Slog.d(TAG, "Persisting Bluetooth Setting: " + value);
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
        if (DBG) Slog.d(TAG, "Loading stored name and address");
        if (mContext.getResources().getBoolean
            (com.android.internal.R.bool.config_bluetooth_address_validation) &&
             Settings.Secure.getInt(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 0) == 0) {
            // if the valid flag is not set, don't load the address and name
            if (DBG) Slog.d(TAG, "invalid bluetooth name and address stored");
            return;
        }
        mName = Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_NAME);
        mAddress = Settings.Secure.getString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS);
        if (DBG) Slog.d(TAG, "Stored bluetooth Name=" + mName + ",Address=" + mAddress);
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
            if (DBG) Slog.d(TAG,"Stored Bluetooth name: " +
                Settings.Secure.getString(mContentResolver,SECURE_SETTINGS_BLUETOOTH_NAME));
        }

        if (address != null) {
            Settings.Secure.putString(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDRESS, address);
            mAddress=address;
            if (DBG)  Slog.d(TAG,"Stored Bluetoothaddress: " +
                Settings.Secure.getString(mContentResolver,SECURE_SETTINGS_BLUETOOTH_ADDRESS));
        }

        if ((name != null) && (address != null)) {
            Settings.Secure.putInt(mContentResolver, SECURE_SETTINGS_BLUETOOTH_ADDR_VALID, 1);
        }
    }

    public IBluetooth registerAdapter(IBluetoothManagerCallback callback){
        if (callback == null) {
            Slog.w(TAG, "Callback is null in registerAdapter");
            return null;
        }
        Message msg = mHandler.obtainMessage(MESSAGE_REGISTER_ADAPTER);
        msg.obj = callback;
        mHandler.sendMessage(msg);

        return mBluetooth;
    }

    public void unregisterAdapter(IBluetoothManagerCallback callback) {
        if (callback == null) {
            Slog.w(TAG, "Callback is null in unregisterAdapter");
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
            Slog.w(TAG,"isEnabled(): not allowed for non-active and non system user");
            return false;
        }

        try {
            mBluetoothLock.readLock().lock();
            if (mBluetooth != null) return mBluetooth.isEnabled();
        } catch (RemoteException e) {
            Slog.e(TAG, "isEnabled()", e);
        } finally {
            mBluetoothLock.readLock().unlock();
        }
        return false;
    }

    public int getState() {
        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!checkIfCallerIsForegroundUser())) {
            Slog.w(TAG, "getState(): not allowed for non-active and non system user");
            return BluetoothAdapter.STATE_OFF;
        }

        try {
            mBluetoothLock.readLock().lock();
            if (mBluetooth != null) return mBluetooth.getState();
        } catch (RemoteException e) {
            Slog.e(TAG, "getState()", e);
        } finally {
            mBluetoothLock.readLock().unlock();
        }
        return BluetoothAdapter.STATE_OFF;
    }

    class ClientDeathRecipient implements IBinder.DeathRecipient {
        public void binderDied() {
            if (DBG) Slog.d(TAG, "Binder is dead - unregister Ble App");
            if (mBleAppCount > 0) --mBleAppCount;

            if (mBleAppCount == 0) {
                if (DBG) Slog.d(TAG, "Disabling LE only mode after application crash");
                try {
                    mBluetoothLock.readLock().lock();
                    if (mBluetooth != null &&
                        mBluetooth.getState() == BluetoothAdapter.STATE_BLE_ON) {
                        mEnable = false;
                        mBluetooth.onBrEdrDown();
                    }
                } catch (RemoteException e) {
                     Slog.e(TAG,"Unable to call onBrEdrDown", e);
                } finally {
                    mBluetoothLock.readLock().unlock();
                }
            }
        }
    }

    /** Internal death rec list */
    Map<IBinder, ClientDeathRecipient> mBleApps = new HashMap<IBinder, ClientDeathRecipient>();

    @Override
    public boolean isBleScanAlwaysAvailable() {
        if (isAirplaneModeOn() && !mEnable) {
            return false;
        }
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
                        mBluetoothLock.readLock().lock();
                        if (mBluetooth != null) mBluetooth.onBrEdrDown();
                    } catch (RemoteException e) {
                        Slog.e(TAG, "error when disabling bluetooth", e);
                    } finally {
                        mBluetoothLock.readLock().unlock();
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
            mBluetoothLock.writeLock().lock();
            if (mBluetooth != null && (mBluetooth.getState() != BluetoothAdapter.STATE_ON)) {
                if (DBG) Slog.d(TAG, "Reseting the mEnable flag for clean disable");
                mEnable = false;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "getState()", e);
        } finally {
            mBluetoothLock.writeLock().unlock();
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
                if (DBG) Slog.d(TAG, "Registered for death Notification");
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
                if (DBG) Slog.d(TAG, "Unregistered for death Notification");
            }
        }
        if (DBG) Slog.d(TAG, "Updated BleAppCount" + mBleAppCount);
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
        if (DBG) Slog.d(TAG, "isBleAppPresent() count: " + mBleAppCount);
        return (mBleAppCount > 0);
    }

    /**
     * Action taken when GattService is turned off
     */
    private void onBluetoothGattServiceUp() {
        if (DBG) Slog.d(TAG,"BluetoothGatt Service is Up");
        try {
            mBluetoothLock.readLock().lock();
            if (isBleAppPresent() == false && mBluetooth != null
                  && mBluetooth.getState() == BluetoothAdapter.STATE_BLE_ON) {
                mBluetooth.onLeServiceUp();

                // waive WRITE_SECURE_SETTINGS permission check
                long callingIdentity = Binder.clearCallingIdentity();
                persistBluetoothSetting(BLUETOOTH_ON_BLUETOOTH);
                Binder.restoreCallingIdentity(callingIdentity);
            }
        } catch (RemoteException e) {
            Slog.e(TAG,"Unable to call onServiceUp", e);
        } finally {
            mBluetoothLock.readLock().unlock();
        }
    }

    /**
     * Inform BluetoothAdapter instances that BREDR part is down
     * and turn off all service and stack if no LE app needs it
     */
    private void sendBrEdrDownCallback() {
        if (DBG) Slog.d(TAG,"Calling sendBrEdrDownCallback callbacks");

        if (mBluetooth == null) {
            Slog.w(TAG, "Bluetooth handle is null");
            return;
        }

        if (isBleAppPresent() == false) {
            try {
                mBluetoothLock.readLock().lock();
                if (mBluetooth != null) mBluetooth.onBrEdrDown();
            } catch (RemoteException e) {
                Slog.e(TAG, "Call to onBrEdrDown() failed.", e);
            } finally {
                mBluetoothLock.readLock().unlock();
            }
        } else {
            // Need to stay at BLE ON. Disconnect all Gatt connections
            try {
                mBluetoothGatt.unregAll();
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to disconnect all apps.", e);
            }
        }
    }

    public boolean enableNoAutoConnect()
    {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permission");

        if (DBG) {
            Slog.d(TAG,"enableNoAutoConnect():  mBluetooth =" + mBluetooth +
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
            Slog.w(TAG,"enable(): not allowed for non-active and non system user");
            return false;
        }

        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permission");
        if (DBG) {
            Slog.d(TAG,"enable():  mBluetooth =" + mBluetooth +
                    " mBinding = " + mBinding + " mState = " + mState);
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
        if (DBG) Slog.d(TAG, "enable returning");
        return true;
    }

    public boolean disable(boolean persist) {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_ADMIN_PERM,
                                                "Need BLUETOOTH ADMIN permissicacheNameAndAddresson");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
            (!checkIfCallerIsForegroundUser())) {
            Slog.w(TAG,"disable(): not allowed for non-active and non system user");
            return false;
        }

        if (DBG) {
            Slog.d(TAG,"disable(): mBluetooth = " + mBluetooth +
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
            Slog.d(TAG,"unbindAndFinish(): " + mBluetooth +
                " mBinding = " + mBinding);
        }

        try {
            mBluetoothLock.writeLock().lock();
            if (mUnbinding) return;
            mUnbinding = true;
            mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
            mHandler.removeMessages(MESSAGE_BIND_PROFILE_SERVICE);
            if (mBluetooth != null) {
                //Unregister callback object
                try {
                    mBluetooth.unregisterCallback(mBluetoothCallback);
                } catch (RemoteException re) {
                    Slog.e(TAG, "Unable to unregister BluetoothCallback",re);
                }

                if (DBG) Slog.d(TAG, "Sending unbind request.");
                mBluetoothBinder = null;
                mBluetooth = null;
                //Unbind
                mContext.unbindService(mConnection);
                mUnbinding = false;
                mBinding = false;
            } else {
                mUnbinding=false;
            }
            mBluetoothGatt = null;
        } finally {
            mBluetoothLock.writeLock().unlock();
        }
    }

    public IBluetoothGatt getBluetoothGatt() {
        // sync protection
        return mBluetoothGatt;
    }

    @Override
    public boolean bindBluetoothProfileService(int bluetoothProfile,
            IBluetoothProfileServiceConnection proxy) {
        if (!mEnable) {
            if (DBG) {
                Slog.d(TAG, "Trying to bind to profile: " + bluetoothProfile +
                        ", while Bluetooth was disabled");
            }
            return false;
        }
        synchronized (mProfileServices) {
            ProfileServiceConnections psc = mProfileServices.get(new Integer(bluetoothProfile));
            if (psc == null) {
                if (DBG) {
                    Slog.d(TAG, "Creating new ProfileServiceConnections object for"
                            + " profile: " + bluetoothProfile);
                }

                if (bluetoothProfile != BluetoothProfile.HEADSET) return false;

                Intent intent = new Intent(IBluetoothHeadset.class.getName());
                psc = new ProfileServiceConnections(intent);
                if (!psc.bindService()) return false;

                mProfileServices.put(new Integer(bluetoothProfile), psc);
            }
        }

        // Introducing a delay to give the client app time to prepare
        Message addProxyMsg = mHandler.obtainMessage(MESSAGE_ADD_PROXY_DELAYED);
        addProxyMsg.arg1 = bluetoothProfile;
        addProxyMsg.obj = proxy;
        mHandler.sendMessageDelayed(addProxyMsg, ADD_PROXY_DELAY_MS);
        return true;
    }

    @Override
    public void unbindBluetoothProfileService(int bluetoothProfile,
            IBluetoothProfileServiceConnection proxy) {
        synchronized (mProfileServices) {
            ProfileServiceConnections psc = mProfileServices.get(new Integer(bluetoothProfile));
            if (psc == null) {
                return;
            }
            psc.removeProxy(proxy);
        }
    }

    private void unbindAllBluetoothProfileServices() {
        synchronized (mProfileServices) {
            for (Integer i : mProfileServices.keySet()) {
                ProfileServiceConnections psc = mProfileServices.get(i);
                try {
                    mContext.unbindService(psc);
                } catch (IllegalArgumentException e) {
                    Slog.e(TAG, "Unable to unbind service with intent: " + psc.mIntent, e);
                }
                psc.removeAllProxies();
            }
            mProfileServices.clear();
        }
    }

    /**
     * Send enable message and set adapter name and address. Called when the boot phase becomes
     * PHASE_SYSTEM_SERVICES_READY.
     */
    public void handleOnBootPhase() {
        if (DBG) Slog.d(TAG, "Bluetooth boot completed");
        if (mEnableExternal && isBluetoothPersistedStateOnBluetooth()) {
            if (DBG) Slog.d(TAG, "Auto-enabling Bluetooth.");
            sendEnableMsg(mQuietEnableExternal);
        } else if (!isNameAndAddressSet()) {
            if (DBG) Slog.d(TAG, "Getting adapter name and address");
            Message getMsg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
            mHandler.sendMessage(getMsg);
        }
    }

    /**
     * Called when switching to a different foreground user.
     */
    public void handleOnSwitchUser(int userHandle) {
        if (DBG) Slog.d(TAG, "User " + userHandle + " switched");
        mHandler.obtainMessage(MESSAGE_USER_SWITCHED, userHandle, 0).sendToTarget();
    }

    /**
     * Called when user is unlocked.
     */
    public void handleOnUnlockUser(int userHandle) {
        if (DBG) Slog.d(TAG, "User " + userHandle + " unlocked");
        mHandler.obtainMessage(MESSAGE_USER_UNLOCKED, userHandle, 0).sendToTarget();
    }

    /**
     * This class manages the clients connected to a given ProfileService
     * and maintains the connection with that service.
     */
    final private class ProfileServiceConnections implements ServiceConnection,
            IBinder.DeathRecipient {
        final RemoteCallbackList<IBluetoothProfileServiceConnection> mProxies =
                new RemoteCallbackList <IBluetoothProfileServiceConnection>();
        IBinder mService;
        ComponentName mClassName;
        Intent mIntent;
        boolean mInvokingProxyCallbacks = false;

        ProfileServiceConnections(Intent intent) {
            mService = null;
            mClassName = null;
            mIntent = intent;
        }

        private boolean bindService() {
            if (mIntent != null && mService == null &&
                    doBind(mIntent, this, 0, UserHandle.CURRENT_OR_SELF)) {
                Message msg = mHandler.obtainMessage(MESSAGE_BIND_PROFILE_SERVICE);
                msg.obj = this;
                mHandler.sendMessageDelayed(msg, TIMEOUT_BIND_MS);
                return true;
            }
            Slog.w(TAG, "Unable to bind with intent: " + mIntent);
            return false;
        }

        private void addProxy(IBluetoothProfileServiceConnection proxy) {
            mProxies.register(proxy);
            if (mService != null) {
                try{
                    proxy.onServiceConnected(mClassName, mService);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to connect to proxy", e);
                }
            } else {
                if (!mHandler.hasMessages(MESSAGE_BIND_PROFILE_SERVICE, this)) {
                    Message msg = mHandler.obtainMessage(MESSAGE_BIND_PROFILE_SERVICE);
                    msg.obj = this;
                    mHandler.sendMessage(msg);
                }
            }
        }

        private void removeProxy(IBluetoothProfileServiceConnection proxy) {
            if (proxy != null) {
                if (mProxies.unregister(proxy)) {
                    try {
                        proxy.onServiceDisconnected(mClassName);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to disconnect proxy", e);
                    }
                }
            } else {
                Slog.w(TAG, "Trying to remove a null proxy");
            }
        }

        private void removeAllProxies() {
            onServiceDisconnected(mClassName);
            mProxies.kill();
        }

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // remove timeout message
            mHandler.removeMessages(MESSAGE_BIND_PROFILE_SERVICE, this);
            mService = service;
            mClassName = className;
            try {
                mService.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to linkToDeath", e);
            }

            if (mInvokingProxyCallbacks) {
                Slog.e(TAG, "Proxy callbacks already in progress.");
                return;
            }
            mInvokingProxyCallbacks = true;

            final int n = mProxies.beginBroadcast();
            try {
                for (int i = 0; i < n; i++) {
                    try {
                        mProxies.getBroadcastItem(i).onServiceConnected(className, service);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to connect to proxy", e);
                    }
                }
            } finally {
                mProxies.finishBroadcast();
                mInvokingProxyCallbacks = false;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            if (mService == null) return;
            mService.unlinkToDeath(this, 0);
            mService = null;
            mClassName = null;

            if (mInvokingProxyCallbacks) {
                Slog.e(TAG, "Proxy callbacks already in progress.");
                return;
            }
            mInvokingProxyCallbacks = true;

            final int n = mProxies.beginBroadcast();
            try {
                for (int i = 0; i < n; i++) {
                    try {
                        mProxies.getBroadcastItem(i).onServiceDisconnected(className);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to disconnect from proxy", e);
                    }
                }
            } finally {
                mProxies.finishBroadcast();
                mInvokingProxyCallbacks = false;
            }
        }

        @Override
        public void binderDied() {
            if (DBG) {
                Slog.w(TAG, "Profile service for profile: " + mClassName
                        + " died.");
            }
            onServiceDisconnected(mClassName);
            // Trigger rebind
            Message msg = mHandler.obtainMessage(MESSAGE_BIND_PROFILE_SERVICE);
            msg.obj = this;
            mHandler.sendMessageDelayed(msg, TIMEOUT_BIND_MS);
        }
    }

    private void sendBluetoothStateCallback(boolean isUp) {
        try {
            int n = mStateChangeCallbacks.beginBroadcast();
            if (DBG) Slog.d(TAG,"Broadcasting onBluetoothStateChange("+isUp+") to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mStateChangeCallbacks.getBroadcastItem(i).onBluetoothStateChange(isUp);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call onBluetoothStateChange() on callback #" + i , e);
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
        if (DBG) Slog.d(TAG,"Calling onBluetoothServiceUp callbacks");
        try {
            int n = mCallbacks.beginBroadcast();
            Slog.d(TAG,"Broadcasting onBluetoothServiceUp() to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBluetoothServiceUp(mBluetooth);
                }  catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call onBluetoothServiceUp() on callback #" + i, e);
                }
            }
        } finally {
            mCallbacks.finishBroadcast();
        }
    }
    /**
     * Inform BluetoothAdapter instances that Adapter service is down
     */
    private void sendBluetoothServiceDownCallback() {
        if (DBG) Slog.d(TAG,"Calling onBluetoothServiceDown callbacks");
        try {
            int n = mCallbacks.beginBroadcast();
            Slog.d(TAG,"Broadcasting onBluetoothServiceDown() to " + n + " receivers.");
            for (int i=0; i <n;i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onBluetoothServiceDown();
                }  catch (RemoteException e) {
                    Slog.e(TAG, "Unable to call onBluetoothServiceDown() on callback #" + i, e);
                }
            }
        } finally {
            mCallbacks.finishBroadcast();
        }
    }

    public String getAddress() {
        mContext.enforceCallingOrSelfPermission(BLUETOOTH_PERM,
                "Need BLUETOOTH permission");

        if ((Binder.getCallingUid() != Process.SYSTEM_UID) &&
                (!checkIfCallerIsForegroundUser())) {
            Slog.w(TAG,"getAddress(): not allowed for non-active and non system user");
            return null;
        }

        if (mContext.checkCallingOrSelfPermission(Manifest.permission.LOCAL_MAC_ADDRESS)
                != PackageManager.PERMISSION_GRANTED) {
            return BluetoothAdapter.DEFAULT_MAC_ADDRESS;
        }

        try {
            mBluetoothLock.readLock().lock();
            if (mBluetooth != null) return mBluetooth.getAddress();
        } catch (RemoteException e) {
            Slog.e(TAG, "getAddress(): Unable to retrieve address remotely. Returning cached address", e);
        } finally {
            mBluetoothLock.readLock().unlock();
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
            Slog.w(TAG,"getName(): not allowed for non-active and non system user");
            return null;
        }

        try {
            mBluetoothLock.readLock().lock();
            if (mBluetooth != null) return mBluetooth.getName();
        } catch (RemoteException e) {
            Slog.e(TAG, "getName(): Unable to retrieve name remotely. Returning cached name", e);
        } finally {
            mBluetoothLock.readLock().unlock();
        }

        // mName is accessed from outside.
        // It alright without a lock. Here, bluetooth is off, no other thread is
        // changing mName
        return mName;
    }

    private class BluetoothServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DBG) Slog.d(TAG, "BluetoothServiceConnection: " + className.getClassName());
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_SERVICE_CONNECTED);
            // TBD if (className.getClassName().equals(IBluetooth.class.getName())) {
            if (className.getClassName().equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = SERVICE_IBLUETOOTH;
                // } else if (className.getClassName().equals(IBluetoothGatt.class.getName())) {
            } else if (className.getClassName().equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = SERVICE_IBLUETOOTHGATT;
            } else {
                Slog.e(TAG, "Unknown service connected: " + className.getClassName());
                return;
            }
            msg.obj = service;
            boolean sent = mHandler.sendMessage(msg);
            if (sent && (msg.arg1 == SERVICE_IBLUETOOTH))
                mIBluetoothConnectedMsgQueued++;
        }

        public void onServiceDisconnected(ComponentName className) {
            // Called if we unexpected disconnected.
            if (DBG) Slog.d(TAG, "BluetoothServiceConnection, disconnected: " +
                           className.getClassName());
            Message msg = mHandler.obtainMessage(MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED);
            if (className.getClassName().equals("com.android.bluetooth.btservice.AdapterService")) {
                msg.arg1 = SERVICE_IBLUETOOTH;
            } else if (className.getClassName().equals("com.android.bluetooth.gatt.GattService")) {
                msg.arg1 = SERVICE_IBLUETOOTHGATT;
            } else {
                Slog.e(TAG, "Unknown service disconnected: " + className.getClassName());
                return;
            }
            mHandler.sendMessage(msg);
        }
    }

    private BluetoothServiceConnection mConnection = new BluetoothServiceConnection();

    private class BluetoothHandler extends Handler {
        boolean mGetNameAddressOnly = false;

        public BluetoothHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (DBG) Slog.d (TAG, "Message: " + msg.what);
            switch (msg.what) {
                case MESSAGE_GET_NAME_AND_ADDRESS:
                    if (DBG) Slog.d(TAG, "MESSAGE_GET_NAME_AND_ADDRESS");
                    try {
                        mBluetoothLock.writeLock().lock();
                        if ((mBluetooth == null) && (!mBinding)) {
                            if (DBG) Slog.d(TAG, "Binding to service to get name and address");
                            mGetNameAddressOnly = true;
                            Message timeoutMsg = mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                            mHandler.sendMessageDelayed(timeoutMsg, TIMEOUT_BIND_MS);
                            Intent i = new Intent(IBluetooth.class.getName());
                            if (!doBind(i, mConnection,
                                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                                UserHandle.CURRENT)) {
                                mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                            } else {
                                mBinding = true;
                            }
                        } else if (mBluetooth != null) {
                            try {
                                storeNameAndAddress(mBluetooth.getName(),
                                                    mBluetooth.getAddress());
                            } catch (RemoteException re) {
                                Slog.e(TAG, "Unable to grab names", re);
                            }
                            if (mGetNameAddressOnly && !mEnable) {
                                unbindAndFinish();
                            }
                            mGetNameAddressOnly = false;
                        }
                    } finally {
                        mBluetoothLock.writeLock().unlock();
                    }
                    break;

                case MESSAGE_ENABLE:
                    if (DBG) {
                        Slog.d(TAG, "MESSAGE_ENABLE: mBluetooth = " + mBluetooth);
                    }
                    mHandler.removeMessages(MESSAGE_RESTART_BLUETOOTH_SERVICE);
                    mEnable = true;

                    // Use service interface to get the exact state
                    try {
                        mBluetoothLock.readLock().lock();
                        if (mBluetooth != null) {
                            int state = mBluetooth.getState();
                            if (state == BluetoothAdapter.STATE_BLE_ON) {
                                Slog.w(TAG, "BT is in BLE_ON State");
                                mBluetooth.onLeServiceUp();
                                break;
                            }
                        }
                    } catch (RemoteException e) {
                        Slog.e(TAG, "", e);
                    } finally {
                        mBluetoothLock.readLock().unlock();
                    }

                    mQuietEnable = (msg.arg1 == 1);
                    if (mBluetooth == null) {
                        handleEnable(mQuietEnable);
                    } else {
                        //
                        // We need to wait until transitioned to STATE_OFF and
                        // the previous Bluetooth process has exited. The
                        // waiting period has three components:
                        // (a) Wait until the local state is STATE_OFF. This
                        //     is accomplished by "waitForOnOff(false, true)".
                        // (b) Wait until the STATE_OFF state is updated to
                        //     all components.
                        // (c) Wait until the Bluetooth process exits, and
                        //     ActivityManager detects it.
                        // The waiting for (b) and (c) is accomplished by
                        // delaying the MESSAGE_RESTART_BLUETOOTH_SERVICE
                        // message. On slower devices, that delay needs to be
                        // on the order of (2 * SERVICE_RESTART_TIME_MS).
                        //
                        waitForOnOff(false, true);
                        Message restartMsg = mHandler.obtainMessage(
                                MESSAGE_RESTART_BLUETOOTH_SERVICE);
                        mHandler.sendMessageDelayed(restartMsg,
                                2 * SERVICE_RESTART_TIME_MS);
                    }
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
                    Slog.d(TAG,"Added callback: " +  (callback == null? "null": callback)  +":" +added );
                }
                    break;
                case MESSAGE_UNREGISTER_ADAPTER:
                {
                    IBluetoothManagerCallback callback = (IBluetoothManagerCallback) msg.obj;
                    boolean removed = mCallbacks.unregister(callback);
                    Slog.d(TAG,"Removed callback: " +  (callback == null? "null": callback)  +":" + removed);
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
                case MESSAGE_ADD_PROXY_DELAYED:
                {
                    ProfileServiceConnections psc = mProfileServices.get(
                            new Integer(msg.arg1));
                    if (psc == null) {
                        break;
                    }
                    IBluetoothProfileServiceConnection proxy =
                            (IBluetoothProfileServiceConnection) msg.obj;
                    psc.addProxy(proxy);
                    break;
                }
                case MESSAGE_BIND_PROFILE_SERVICE:
                {
                    ProfileServiceConnections psc = (ProfileServiceConnections) msg.obj;
                    removeMessages(MESSAGE_BIND_PROFILE_SERVICE, msg.obj);
                    if (psc == null) {
                        break;
                    }
                    psc.bindService();
                    break;
                }
                case MESSAGE_BLUETOOTH_SERVICE_CONNECTED:
                {
                    if (DBG) Slog.d(TAG,"MESSAGE_BLUETOOTH_SERVICE_CONNECTED: " + msg.arg1);

                    IBinder service = (IBinder) msg.obj;
                    try {
                        mBluetoothLock.writeLock().lock();
                        if (msg.arg1 == SERVICE_IBLUETOOTHGATT) {
                            mBluetoothGatt = IBluetoothGatt.Stub.asInterface(service);
                            onBluetoothGattServiceUp();
                            break;
                        } // else must be SERVICE_IBLUETOOTH

                        mIBluetoothConnectedMsgQueued--;

                        //Remove timeout
                        mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);

                        mBinding = false;
                        mBluetoothBinder = service;
                        mBluetooth = IBluetooth.Stub.asInterface(service);

                        if (!isNameAndAddressSet()) {
                            Message getMsg = mHandler.obtainMessage(MESSAGE_GET_NAME_AND_ADDRESS);
                            mHandler.sendMessage(getMsg);
                            if (mGetNameAddressOnly) return;
                        }

                        try {
                            boolean enableHciSnoopLog = (Settings.Secure.getInt(mContentResolver,
                                Settings.Secure.BLUETOOTH_HCI_LOG, 0) == 1);
                            if (!mBluetooth.configHciSnoopLog(enableHciSnoopLog)) {
                                Slog.e(TAG,"IBluetooth.configHciSnoopLog return false");
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG,"Unable to call configHciSnoopLog", e);
                        }

                        //Register callback object
                        try {
                            mBluetooth.registerCallback(mBluetoothCallback);
                        } catch (RemoteException re) {
                            Slog.e(TAG, "Unable to register BluetoothCallback",re);
                        }
                        //Inform BluetoothAdapter instances that service is up
                        sendBluetoothServiceUpCallback();

                        //Do enable request
                        try {
                            if (mQuietEnable == false) {
                                if(!mBluetooth.enable()) {
                                    Slog.e(TAG,"IBluetooth.enable() returned false");
                                }
                            }
                            else
                            {
                                if(!mBluetooth.enableNoAutoConnect()) {
                                    Slog.e(TAG,"IBluetooth.enableNoAutoConnect() returned false");
                                }
                            }
                        } catch (RemoteException e) {
                            Slog.e(TAG,"Unable to call enable()",e);
                        }
                    } finally {
                        mBluetoothLock.writeLock().unlock();
                    }

                    if (!mEnable) {
                        waitForOnOff(true, false);
                        handleDisable();
                        waitForOnOff(false, false);
                    }
                    break;
                }
                case MESSAGE_TIMEOUT_BIND: {
                    Slog.e(TAG, "MESSAGE_TIMEOUT_BIND");
                    mBluetoothLock.writeLock().lock();
                    mBinding = false;
                    mBluetoothLock.writeLock().unlock();

                    break;
                }
                case MESSAGE_BLUETOOTH_STATE_CHANGE:
                {
                    int prevState = msg.arg1;
                    int newState = msg.arg2;
                    if (DBG) Slog.d(TAG, "MESSAGE_BLUETOOTH_STATE_CHANGE: prevState = " + prevState + ", newState =" + newState);
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
                            (newState == BluetoothAdapter.STATE_OFF) &&
                            (mBluetooth != null) && mEnable) {
                         persistBluetoothSetting(BLUETOOTH_OFF);
                    }
                    if ((prevState == BluetoothAdapter.STATE_TURNING_ON) &&
                           (newState == BluetoothAdapter.STATE_BLE_ON) &&
                           (mBluetooth != null) && mEnable) {
                        recoverBluetoothServiceFromError();
                    }
                    // If we tried to enable BT while BT was in the process of shutting down,
                    // wait for the BT process to fully tear down and then force a restart
                    // here.  This is a bit of a hack (b/29363429).
                    if ((prevState == BluetoothAdapter.STATE_BLE_TURNING_OFF) &&
                            (newState == BluetoothAdapter.STATE_OFF)) {
                        if (mEnable) {
                            Slog.d(TAG, "Entering STATE_OFF but mEnabled is true; restarting.");
                            waitForOnOff(false, true);
                            Message restartMsg = mHandler.obtainMessage(
                                    MESSAGE_RESTART_BLUETOOTH_SERVICE);
                            mHandler.sendMessageDelayed(restartMsg, 2 * SERVICE_RESTART_TIME_MS);
                        }
                    }
                    if (newState == BluetoothAdapter.STATE_ON ||
                            newState == BluetoothAdapter.STATE_BLE_ON) {
                        // bluetooth is working, reset the counter
                        if (mErrorRecoveryRetryCounter != 0) {
                            Slog.w(TAG, "bluetooth is recovered from error");
                            mErrorRecoveryRetryCounter = 0;
                        }
                    }
                    break;
                }
                case MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED:
                {
                    Slog.e(TAG, "MESSAGE_BLUETOOTH_SERVICE_DISCONNECTED: " + msg.arg1);
                    try {
                        mBluetoothLock.writeLock().lock();
                        if (msg.arg1 == SERVICE_IBLUETOOTH) {
                            // if service is unbinded already, do nothing and return
                            if (mBluetooth == null) break;
                            mBluetooth = null;
                        } else if (msg.arg1 == SERVICE_IBLUETOOTHGATT) {
                            mBluetoothGatt = null;
                            break;
                        } else {
                            Slog.e(TAG, "Bad msg.arg1: " + msg.arg1);
                            break;
                        }
                    } finally {
                        mBluetoothLock.writeLock().unlock();
                    }

                    if (mEnable && (mIBluetoothConnectedMsgQueued == 0)) {
                        mEnable = false;
                        // Send a Bluetooth Restart message
                        Message restartMsg = mHandler.obtainMessage(
                            MESSAGE_RESTART_BLUETOOTH_SERVICE);
                        mHandler.sendMessageDelayed(restartMsg,
                            SERVICE_RESTART_TIME_MS);
                    }

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
                    break;
                }
                case MESSAGE_RESTART_BLUETOOTH_SERVICE:
                {
                    Slog.d(TAG, "MESSAGE_RESTART_BLUETOOTH_SERVICE:"
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
                    Slog.e(TAG, "MESSAGE_TIMEOUT_UNBIND");
                    mBluetoothLock.writeLock().lock();
                    mUnbinding = false;
                    mBluetoothLock.writeLock().unlock();
                    break;
                }

                case MESSAGE_USER_SWITCHED: {
                    if (DBG) Slog.d(TAG, "MESSAGE_USER_SWITCHED");
                    mHandler.removeMessages(MESSAGE_USER_SWITCHED);

                    /* disable and enable BT when detect a user switch */
                    if (mEnable && mBluetooth != null) {
                        try {
                            mBluetoothLock.readLock().lock();
                            if (mBluetooth != null) {
                                mBluetooth.unregisterCallback(mBluetoothCallback);
                            }
                        } catch (RemoteException re) {
                            Slog.e(TAG, "Unable to unregister", re);
                        } finally {
                            mBluetoothLock.readLock().unlock();
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

                        waitForOnOff(true, false);

                        if (mState == BluetoothAdapter.STATE_TURNING_ON) {
                            bluetoothStateChangeHandler(mState, BluetoothAdapter.STATE_ON);
                        }

                        unbindAllBluetoothProfileServices();
                        // disable
                        handleDisable();
                        // Pbap service need receive STATE_TURNING_OFF intent to close
                        bluetoothStateChangeHandler(BluetoothAdapter.STATE_ON,
                                                    BluetoothAdapter.STATE_TURNING_OFF);

                        boolean didDisableTimeout = !waitForOnOff(false, true);

                        bluetoothStateChangeHandler(BluetoothAdapter.STATE_TURNING_OFF,
                                                    BluetoothAdapter.STATE_OFF);
                        sendBluetoothServiceDownCallback();

                        try {
                            mBluetoothLock.writeLock().lock();
                            if (mBluetooth != null) {
                                mBluetooth = null;
                                // Unbind
                                mContext.unbindService(mConnection);
                            }
                            mBluetoothGatt = null;
                        } finally {
                            mBluetoothLock.writeLock().unlock();
                        }

                        //
                        // If disabling Bluetooth times out, wait for an
                        // additional amount of time to ensure the process is
                        // shut down completely before attempting to restart.
                        //
                        if (didDisableTimeout) {
                            SystemClock.sleep(3000);
                        } else {
                            SystemClock.sleep(100);
                        }

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
                            Slog.d(TAG, "delay MESSAGE_USER_SWITCHED " + userMsg.arg2);
                        }
                    }
                    break;
                }
                case MESSAGE_USER_UNLOCKED: {
                    if (DBG) Slog.d(TAG, "MESSAGE_USER_UNLOCKED");
                    mHandler.removeMessages(MESSAGE_USER_SWITCHED);

                    if (mEnable && !mBinding && (mBluetooth == null)) {
                        // We should be connected, but we gave up for some
                        // reason; maybe the Bluetooth service wasn't encryption
                        // aware, so try binding again.
                        if (DBG) Slog.d(TAG, "Enabled but not bound; retrying after unlock");
                        handleEnable(mQuietEnable);
                    }
                }
            }
        }
    }

    private void handleEnable(boolean quietMode) {
        mQuietEnable = quietMode;

        try {
            mBluetoothLock.writeLock().lock();
            if ((mBluetooth == null) && (!mBinding)) {
                //Start bind timeout and bind
                Message timeoutMsg=mHandler.obtainMessage(MESSAGE_TIMEOUT_BIND);
                mHandler.sendMessageDelayed(timeoutMsg,TIMEOUT_BIND_MS);
                Intent i = new Intent(IBluetooth.class.getName());
                if (!doBind(i, mConnection,Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                        UserHandle.CURRENT)) {
                    mHandler.removeMessages(MESSAGE_TIMEOUT_BIND);
                } else {
                    mBinding = true;
                }
            } else if (mBluetooth != null) {
                //Enable bluetooth
                try {
                    if (!mQuietEnable) {
                        if(!mBluetooth.enable()) {
                            Slog.e(TAG,"IBluetooth.enable() returned false");
                        }
                    }
                    else {
                        if(!mBluetooth.enableNoAutoConnect()) {
                            Slog.e(TAG,"IBluetooth.enableNoAutoConnect() returned false");
                        }
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG,"Unable to call enable()",e);
                }
            }
        } finally {
            mBluetoothLock.writeLock().unlock();
        }
    }

    boolean doBind(Intent intent, ServiceConnection conn, int flags, UserHandle user) {
        ComponentName comp = intent.resolveSystemService(mContext.getPackageManager(), 0);
        intent.setComponent(comp);
        if (comp == null || !mContext.bindServiceAsUser(intent, conn, flags, user)) {
            Slog.e(TAG, "Fail to bind to: " + intent);
            return false;
        }
        return true;
    }

    private void handleDisable() {
        try {
            mBluetoothLock.readLock().lock();
            if (mBluetooth != null) {
                if (DBG) Slog.d(TAG,"Sending off request.");
                if (!mBluetooth.disable()) {
                    Slog.e(TAG,"IBluetooth.disable() returned false");
                }
            }
        } catch (RemoteException e) {
            Slog.e(TAG,"Unable to call disable()",e);
        } finally {
            mBluetoothLock.readLock().unlock();
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
                Slog.d(TAG, "checkIfCallerIsForegroundUser: valid=" + valid
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
        if (DBG) Slog.d(TAG,"BLE State Change Intent: " + prevState + " -> " + newState);
        // Send broadcast message to everyone else
        Intent intent = new Intent(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, BLUETOOTH_PERM);
    }

    private void bluetoothStateChangeHandler(int prevState, int newState) {
        boolean isStandardBroadcast = true;
        if (DBG) Slog.d(TAG, "bluetoothStateChangeHandler: " + prevState + " ->  " + newState);
        if (prevState != newState) {
            //Notify all proxy objects first of adapter state change
            if (newState == BluetoothAdapter.STATE_BLE_ON ||
                    newState == BluetoothAdapter.STATE_OFF) {
                boolean intermediate_off = (prevState == BluetoothAdapter.STATE_TURNING_OFF
                   && newState == BluetoothAdapter.STATE_BLE_ON);

                if (newState == BluetoothAdapter.STATE_OFF) {
                    // If Bluetooth is off, send service down event to proxy objects, and unbind
                    if (DBG) Slog.d(TAG, "Bluetooth is complete turn off");
                    sendBluetoothServiceDownCallback();
                    unbindAndFinish();
                    sendBleStateChanged(prevState, newState);
                    // Don't broadcast as it has already been broadcast before
                    if(!mIntentPending)
                        isStandardBroadcast = false;
                    else
                        mIntentPending = false;

                } else if (!intermediate_off) {
                    // connect to GattService
                    if (DBG) Slog.d(TAG, "Bluetooth is in LE only mode");
                    if (mBluetoothGatt != null) {
                        if (DBG) Slog.d(TAG, "Calling BluetoothGattServiceUp");
                        onBluetoothGattServiceUp();
                    } else {
                        if (DBG) Slog.d(TAG, "Binding Bluetooth GATT service");
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
                    if (DBG) Slog.d(TAG, "Intermediate off, back to LE only mode");
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

            } else if (newState == BluetoothAdapter.STATE_BLE_TURNING_ON ||
                    newState == BluetoothAdapter.STATE_BLE_TURNING_OFF ) {
                sendBleStateChanged(prevState, newState);
                isStandardBroadcast = false;

            } else if (newState == BluetoothAdapter.STATE_TURNING_ON ||
                    newState == BluetoothAdapter.STATE_TURNING_OFF) {
                sendBleStateChanged(prevState, newState);
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
            try {
                mBluetoothLock.readLock().lock();
                if (mBluetooth == null) break;
                if (on) {
                    if (mBluetooth.getState() == BluetoothAdapter.STATE_ON) return true;
                } else if (off) {
                    if (mBluetooth.getState() == BluetoothAdapter.STATE_OFF) return true;
                } else {
                    if (mBluetooth.getState() != BluetoothAdapter.STATE_ON) return true;
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "getState()", e);
                break;
            } finally {
                mBluetoothLock.readLock().unlock();
            }
            if (on || off) {
                SystemClock.sleep(300);
            } else {
                SystemClock.sleep(50);
            }
            i++;
        }
        Slog.e(TAG,"waitForOnOff time out");
        return false;
    }

    private void sendDisableMsg() {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_DISABLE));
    }

    private void sendEnableMsg(boolean quietMode) {
        mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_ENABLE,
                             quietMode ? 1 : 0, 0));
    }

    private void recoverBluetoothServiceFromError() {
        Slog.e(TAG,"recoverBluetoothServiceFromError");
        try {
            mBluetoothLock.readLock().lock();
            if (mBluetooth != null) {
                //Unregister callback object
                mBluetooth.unregisterCallback(mBluetoothCallback);
            }
        } catch (RemoteException re) {
            Slog.e(TAG, "Unable to unregister", re);
        } finally {
            mBluetoothLock.readLock().unlock();
        }

        SystemClock.sleep(500);

        // disable
        handleDisable();

        waitForOnOff(false, true);

        // If there is a MESSAGE_BLUETOOTH_SERVICE_CONNECTED in mHandler queue, we should not
        // unbindService as below. Otherwise, after unbind, com.android.bluetooth will die, but
        // later mBluetooth will be assigned to adapterService in handleMessage of
        // MESSAGE_BLUETOOTH_SERVICE_CONNECTED. From then on, mBluetooth is not null,
        // and com.android.bluetooth is dead, but doBind will not be triggered again.
        if (mIBluetoothConnectedMsgQueued > 0) {
            Slog.e(TAG, "recoverBluetoothServiceFromError: "
                    + "MESSAGE_BLUETOOTH_SERVICE_CONNECTED exists in mHandler queue, "
                    + "should not unbindService, return directly.");
            mHandler.removeMessages(MESSAGE_BLUETOOTH_STATE_CHANGE);
            mState = BluetoothAdapter.STATE_OFF;
            return;
        }

        sendBluetoothServiceDownCallback();

        try {
            mBluetoothLock.writeLock().lock();
            if (mBluetooth != null) {
                mBluetooth = null;
                // Unbind
                mContext.unbindService(mConnection);
            }
            mBluetoothGatt = null;
        } finally {
            mBluetoothLock.writeLock().unlock();
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
        String errorMsg = null;
        if (mBluetoothBinder == null) {
            errorMsg = "Bluetooth Service not connected";
        } else {
            try {
                mBluetoothBinder.dump(fd, args);
            } catch (RemoteException re) {
                errorMsg = "RemoteException while calling Bluetooth Service";
            }
        }
        if (errorMsg != null) {
            // Silently return if we are extracting metrics in Protobuf format
            if ((args.length > 0) && args[0].startsWith("--proto"))
                return;
            writer.println(errorMsg);
        }
    }
}
