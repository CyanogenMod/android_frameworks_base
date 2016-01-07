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

package com.android.systemui.keyboard;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import android.util.Slog;
import android.view.WindowManager;

import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.systemui.R;
import com.android.systemui.SystemUI;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeyboardUI extends SystemUI implements InputManager.OnTabletModeChangedListener {
    private static final String TAG = "KeyboardUI";
    private static final boolean DEBUG = false;

    // Give BT some time to start after SyUI comes up. This avoids flashing a dialog in the user's
    // face because BT starts a little bit later in the boot process than SysUI and it takes some
    // time for us to receive the signal that it's starting.
    private static final long BLUETOOTH_START_DELAY_MILLIS = 10 * 1000;

    private static final int STATE_NOT_ENABLED = -1;
    private static final int STATE_UNKNOWN = 0;
    private static final int STATE_WAITING_FOR_BOOT_COMPLETED = 1;
    private static final int STATE_WAITING_FOR_TABLET_MODE_EXIT = 2;
    private static final int STATE_WAITING_FOR_DEVICE_DISCOVERY = 3;
    private static final int STATE_WAITING_FOR_BLUETOOTH = 4;
    private static final int STATE_WAITING_FOR_STATE_PAIRED = 5;
    private static final int STATE_PAIRING = 6;
    private static final int STATE_PAIRED = 7;
    private static final int STATE_USER_CANCELLED = 8;
    private static final int STATE_DEVICE_NOT_FOUND = 9;

    private static final int MSG_INIT = 0;
    private static final int MSG_ON_BOOT_COMPLETED = 1;
    private static final int MSG_PROCESS_KEYBOARD_STATE = 2;
    private static final int MSG_ENABLE_BLUETOOTH = 3;
    private static final int MSG_ON_BLUETOOTH_STATE_CHANGED = 4;
    private static final int MSG_ON_DEVICE_BOND_STATE_CHANGED = 5;
    private static final int MSG_ON_BLUETOOTH_DEVICE_ADDED = 6;
    private static final int MSG_ON_BLE_SCAN_FAILED = 7;
    private static final int MSG_SHOW_BLUETOOTH_DIALOG = 8;
    private static final int MSG_DISMISS_BLUETOOTH_DIALOG = 9;

    private volatile KeyboardHandler mHandler;
    private volatile KeyboardUIHandler mUIHandler;

    protected volatile Context mContext;

    private boolean mEnabled;
    private String mKeyboardName;
    private CachedBluetoothDeviceManager mCachedDeviceManager;
    private LocalBluetoothAdapter mLocalBluetoothAdapter;
    private LocalBluetoothProfileManager mProfileManager;
    private boolean mBootCompleted;
    private long mBootCompletedTime;

    private int mInTabletMode = InputManager.SWITCH_STATE_UNKNOWN;
    private ScanCallback mScanCallback;
    private BluetoothDialog mDialog;

    private int mState;

    @Override
    public void start() {
        mContext = super.mContext;
        HandlerThread thread = new HandlerThread("Keyboard", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        mHandler = new KeyboardHandler(thread.getLooper());
        mHandler.sendEmptyMessage(MSG_INIT);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyboardUI:");
        pw.println("  mEnabled=" + mEnabled);
        pw.println("  mBootCompleted=" + mEnabled);
        pw.println("  mBootCompletedTime=" + mBootCompletedTime);
        pw.println("  mKeyboardName=" + mKeyboardName);
        pw.println("  mInTabletMode=" + mInTabletMode);
        pw.println("  mState=" + stateToString(mState));
    }

    @Override
    protected void onBootCompleted() {
        mHandler.sendEmptyMessage(MSG_ON_BOOT_COMPLETED);
    }

    @Override
    public void onTabletModeChanged(long whenNanos, boolean inTabletMode) {
        if (DEBUG) {
            Slog.d(TAG, "onTabletModeChanged(" + whenNanos + ", " + inTabletMode + ")");
        }

        if (inTabletMode && mInTabletMode != InputManager.SWITCH_STATE_ON
                || !inTabletMode && mInTabletMode != InputManager.SWITCH_STATE_OFF) {
            mInTabletMode = inTabletMode ?
                    InputManager.SWITCH_STATE_ON : InputManager.SWITCH_STATE_OFF;
            processKeyboardState();
        }
    }

    // Shoud only be called on the handler thread
    private void init() {
        Context context = mContext;
        mKeyboardName =
                context.getString(com.android.internal.R.string.config_packagedKeyboardName);
        if (TextUtils.isEmpty(mKeyboardName)) {
            if (DEBUG) {
                Slog.d(TAG, "No packaged keyboard name given.");
            }
            return;
        }

        LocalBluetoothManager bluetoothManager = LocalBluetoothManager.getInstance(context, null);
        if (bluetoothManager == null)  {
            if (DEBUG) {
                Slog.e(TAG, "Failed to retrieve LocalBluetoothManager instance");
            }
            return;
        }
        mEnabled = true;
        mCachedDeviceManager = bluetoothManager.getCachedDeviceManager();
        mLocalBluetoothAdapter = bluetoothManager.getBluetoothAdapter();
        mProfileManager = bluetoothManager.getProfileManager();
        bluetoothManager.getEventManager().registerCallback(new BluetoothCallbackHandler());

        InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        im.registerOnTabletModeChangedListener(this, mHandler);
        mInTabletMode = im.isInTabletMode();

        processKeyboardState();
        mUIHandler = new KeyboardUIHandler();
    }

    // Should only be called on the handler thread
    private void processKeyboardState() {
        mHandler.removeMessages(MSG_PROCESS_KEYBOARD_STATE);

        if (!mEnabled) {
            mState = STATE_NOT_ENABLED;
            return;
        }

        if (!mBootCompleted) {
            mState = STATE_WAITING_FOR_BOOT_COMPLETED;
            return;
        }

        if (mInTabletMode != InputManager.SWITCH_STATE_OFF) {
            if (mState == STATE_WAITING_FOR_DEVICE_DISCOVERY) {
                stopScanning();
            }
            mState = STATE_WAITING_FOR_TABLET_MODE_EXIT;
            return;
        }

        final int btState = mLocalBluetoothAdapter.getState();
        if (btState == BluetoothAdapter.STATE_TURNING_ON || btState == BluetoothAdapter.STATE_ON
                && mState == STATE_WAITING_FOR_BLUETOOTH) {
            // If we're waiting for bluetooth but it has come on in the meantime, or is coming
            // on, just dismiss the dialog. This frequently happens during device startup.
            mUIHandler.sendEmptyMessage(MSG_DISMISS_BLUETOOTH_DIALOG);
        }

        if (btState == BluetoothAdapter.STATE_TURNING_ON) {
            mState = STATE_WAITING_FOR_BLUETOOTH;
            // Wait for bluetooth to fully come on.
            return;
        }

        if (btState != BluetoothAdapter.STATE_ON) {
            mState = STATE_WAITING_FOR_BLUETOOTH;
            showBluetoothDialog();
            return;
        }

        CachedBluetoothDevice device = getPairedKeyboard();
        if (mState == STATE_WAITING_FOR_TABLET_MODE_EXIT || mState == STATE_WAITING_FOR_BLUETOOTH) {
            if (device != null) {
                // If we're just coming out of tablet mode or BT just turned on,
                // then we want to go ahead and automatically connect to the
                // keyboard. We want to avoid this in other cases because we might
                // be spuriously called after the user has manually disconnected
                // the keyboard, meaning we shouldn't try to automtically connect
                // it again.
                mState = STATE_PAIRED;
                device.connect(false);
                return;
            }
            mCachedDeviceManager.clearNonBondedDevices();
        }

        device = getDiscoveredKeyboard();
        if (device != null) {
            mState = STATE_PAIRING;
            device.startPairing();
        } else {
            mState = STATE_WAITING_FOR_DEVICE_DISCOVERY;
            startScanning();
        }
    }

    // Should only be called on the handler thread
    public void onBootCompletedInternal() {
        mBootCompleted = true;
        mBootCompletedTime = SystemClock.uptimeMillis();
        if (mState == STATE_WAITING_FOR_BOOT_COMPLETED) {
            processKeyboardState();
        }
    }

    // Should only be called on the handler thread
    private void showBluetoothDialog() {
        if (isUserSetupComplete()) {
            long now = SystemClock.uptimeMillis();
            long earliestDialogTime = mBootCompletedTime + BLUETOOTH_START_DELAY_MILLIS;
            if (earliestDialogTime < now) {
                mUIHandler.sendEmptyMessage(MSG_SHOW_BLUETOOTH_DIALOG);
            } else {
                mHandler.sendEmptyMessageAtTime(MSG_PROCESS_KEYBOARD_STATE, earliestDialogTime);
            }
        } else {
            // If we're in setup wizard and the keyboard is docked, just automatically enable BT.
            mLocalBluetoothAdapter.enable();
        }
    }

    private boolean isUserSetupComplete() {
        ContentResolver resolver = mContext.getContentResolver();
        return Secure.getIntForUser(
                resolver, Secure.USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) != 0;
    }

    private CachedBluetoothDevice getPairedKeyboard() {
        Set<BluetoothDevice> devices = mLocalBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice d : devices) {
            if (mKeyboardName.equals(d.getName())) {
                return getCachedBluetoothDevice(d);
            }
        }
        return null;
    }

    private CachedBluetoothDevice getDiscoveredKeyboard() {
        Collection<CachedBluetoothDevice> devices = mCachedDeviceManager.getCachedDevicesCopy();
        for (CachedBluetoothDevice d : devices) {
            if (d.getName().equals(mKeyboardName)) {
                return d;
            }
        }
        return null;
    }


    private CachedBluetoothDevice getCachedBluetoothDevice(BluetoothDevice d) {
        CachedBluetoothDevice cachedDevice = mCachedDeviceManager.findDevice(d);
        if (cachedDevice == null) {
            cachedDevice = mCachedDeviceManager.addDevice(
                    mLocalBluetoothAdapter, mProfileManager, d);
        }
        return cachedDevice;
    }

    private void startScanning() {
        BluetoothLeScanner scanner = mLocalBluetoothAdapter.getBluetoothLeScanner();
        ScanFilter filter = (new ScanFilter.Builder()).setDeviceName(mKeyboardName).build();
        ScanSettings settings = (new ScanSettings.Builder())
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build();
        mScanCallback = new KeyboardScanCallback();
        scanner.startScan(Arrays.asList(filter), settings, mScanCallback);
    }

    private void stopScanning() {
        if (mScanCallback != null) {
            mLocalBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
            mScanCallback = null;
        }
    }

    // Should only be called on the handler thread
    private void onDeviceAddedInternal(CachedBluetoothDevice d) {
        if (mState == STATE_WAITING_FOR_DEVICE_DISCOVERY && d.getName().equals(mKeyboardName)) {
            stopScanning();
            d.startPairing();
            mState = STATE_PAIRING;
        }
    }

    // Should only be called on the handler thread
    private void onBluetoothStateChangedInternal(int bluetoothState) {
        if (bluetoothState == BluetoothAdapter.STATE_ON && mState == STATE_WAITING_FOR_BLUETOOTH) {
            processKeyboardState();
        }
    }

    // Should only be called on the handler thread
    private void onDeviceBondStateChangedInternal(CachedBluetoothDevice d, int bondState) {
        if (d.getName().equals(mKeyboardName) && bondState == BluetoothDevice.BOND_BONDED) {
            // We don't need to manually connect to the device here because it will automatically
            // try to connect after it has been paired.
            mState = STATE_PAIRED;
        }
    }

    // Should only be called on the handler thread
    private void onBleScanFailedInternal() {
        mScanCallback = null;
        if (mState == STATE_WAITING_FOR_DEVICE_DISCOVERY) {
            mState = STATE_DEVICE_NOT_FOUND;
        }
    }

    private final class KeyboardUIHandler extends Handler {
        public KeyboardUIHandler() {
            super(Looper.getMainLooper(), null, true /*async*/);
        }
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_SHOW_BLUETOOTH_DIALOG: {
                    DialogInterface.OnClickListener listener = new BluetoothDialogClickListener();
                    mDialog = new BluetoothDialog(mContext);
                    mDialog.setTitle(R.string.enable_bluetooth_title);
                    mDialog.setMessage(R.string.enable_bluetooth_message);
                    mDialog.setPositiveButton(R.string.enable_bluetooth_confirmation_ok, listener);
                    mDialog.setNegativeButton(android.R.string.cancel, listener);
                    mDialog.show();
                    break;
                }
                case MSG_DISMISS_BLUETOOTH_DIALOG: {
                    if (mDialog != null) {
                        mDialog.dismiss();
                        mDialog = null;
                    }
                    break;
                }
            }
        }
    }

    private final class KeyboardHandler extends Handler {
        public KeyboardHandler(Looper looper) {
            super(looper, null, true /*async*/);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_INIT: {
                    init();
                    break;
                }
                case MSG_ON_BOOT_COMPLETED: {
                    onBootCompletedInternal();
                    break;
                }
                case MSG_PROCESS_KEYBOARD_STATE: {
                    processKeyboardState();
                    break;
                }
                case MSG_ENABLE_BLUETOOTH: {
                    boolean enable = msg.arg1 == 1;
                    if (enable) {
                        mLocalBluetoothAdapter.enable();
                    } else {
                        mState = STATE_USER_CANCELLED;
                    }
                }
                case MSG_ON_BLUETOOTH_STATE_CHANGED: {
                    int bluetoothState = msg.arg1;
                    onBluetoothStateChangedInternal(bluetoothState);
                    break;
                }
                case MSG_ON_DEVICE_BOND_STATE_CHANGED: {
                    CachedBluetoothDevice d = (CachedBluetoothDevice)msg.obj;
                    int bondState = msg.arg1;
                    onDeviceBondStateChangedInternal(d, bondState);
                    break;
                }
                case MSG_ON_BLUETOOTH_DEVICE_ADDED: {
                    BluetoothDevice d = (BluetoothDevice)msg.obj;
                    CachedBluetoothDevice cachedDevice = getCachedBluetoothDevice(d);
                    onDeviceAddedInternal(cachedDevice);
                    break;

                }
                case MSG_ON_BLE_SCAN_FAILED: {
                    onBleScanFailedInternal();
                    break;
                }
            }
        }
    }

    private final class BluetoothDialogClickListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            int enable = DialogInterface.BUTTON_POSITIVE == which ? 1 : 0;
            mHandler.obtainMessage(MSG_ENABLE_BLUETOOTH, enable, 0).sendToTarget();
            mDialog = null;
        }
    }

    private final class KeyboardScanCallback extends ScanCallback {

        private boolean isDeviceDiscoverable(ScanResult result) {
            final ScanRecord scanRecord = result.getScanRecord();
            final int flags = scanRecord.getAdvertiseFlags();
            final int BT_DISCOVERABLE_MASK = 0x03;

            return (flags & BT_DISCOVERABLE_MASK) != 0;
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (DEBUG) {
                Slog.d(TAG, "onBatchScanResults(" + results.size() + ")");
            }

            BluetoothDevice bestDevice = null;
            int bestRssi = Integer.MIN_VALUE;

            for (ScanResult result : results) {
                if (DEBUG) {
                    Slog.d(TAG, "onBatchScanResults: considering " + result);
                }

                if (isDeviceDiscoverable(result) && result.getRssi() > bestRssi) {
                    bestDevice = result.getDevice();
                    bestRssi = result.getRssi();
                }
            }

            if (bestDevice != null) {
                mHandler.obtainMessage(MSG_ON_BLUETOOTH_DEVICE_ADDED, bestDevice).sendToTarget();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (DEBUG) {
                Slog.d(TAG, "onScanFailed(" + errorCode + ")");
            }
            mHandler.obtainMessage(MSG_ON_BLE_SCAN_FAILED).sendToTarget();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (DEBUG) {
                Slog.d(TAG, "onScanResult(" + callbackType + ", " + result + ")");
            }

            if (isDeviceDiscoverable(result)) {
                mHandler.obtainMessage(MSG_ON_BLUETOOTH_DEVICE_ADDED,
                        result.getDevice()).sendToTarget();
            } else if (DEBUG) {
                Slog.d(TAG, "onScanResult: device " + result.getDevice() +
                       " is not discoverable, ignoring");
            }
        }
    }

    private final class BluetoothCallbackHandler implements BluetoothCallback {
        @Override
        public void onBluetoothStateChanged(int bluetoothState) {
            mHandler.obtainMessage(MSG_ON_BLUETOOTH_STATE_CHANGED,
                    bluetoothState, 0).sendToTarget();
        }

        @Override
        public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
            mHandler.obtainMessage(MSG_ON_DEVICE_BOND_STATE_CHANGED,
                    bondState, 0, cachedDevice).sendToTarget();
        }

        @Override
        public void onDeviceAdded(CachedBluetoothDevice cachedDevice) { }
        @Override
        public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) { }
        @Override
        public void onScanningStateChanged(boolean started) { }
        @Override
        public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) { }
    }

    private static String stateToString(int state) {
        switch (state) {
            case STATE_NOT_ENABLED:
                return "STATE_NOT_ENABLED";
            case STATE_WAITING_FOR_BOOT_COMPLETED:
                return "STATE_WAITING_FOR_BOOT_COMPLETED";
            case STATE_WAITING_FOR_TABLET_MODE_EXIT:
                return "STATE_WAITING_FOR_TABLET_MODE_EXIT";
            case STATE_WAITING_FOR_DEVICE_DISCOVERY:
                return "STATE_WAITING_FOR_DEVICE_DISCOVERY";
            case STATE_WAITING_FOR_BLUETOOTH:
                return "STATE_WAITING_FOR_BLUETOOTH";
            case STATE_WAITING_FOR_STATE_PAIRED:
                return "STATE_WAITING_FOR_STATE_PAIRED";
            case STATE_PAIRING:
                return "STATE_PAIRING";
            case STATE_PAIRED:
                return "STATE_PAIRED";
            case STATE_USER_CANCELLED:
                return "STATE_USER_CANCELLED";
            case STATE_DEVICE_NOT_FOUND:
                return "STATE_DEVICE_NOT_FOUND";
            case STATE_UNKNOWN:
            default:
                return "STATE_UNKNOWN";
        }
    }
}
