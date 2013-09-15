/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.server.display;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.IDisplayDevice;
import android.hardware.display.IRemoteDisplayAdapter;
import android.hardware.display.WifiDisplay;
import android.hardware.display.WifiDisplayStatus;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class RemoteDisplayAdapter extends DisplayAdapter {
    private static class RemoteDisplay {
        RemoteDisplayDevice remoteDisplayDevice;
        DisplayDeviceInfo info;
        IDisplayDevice displayDevice;
        boolean hidden;
    }

    private static final String TAG = "RemoteDisplayAdapter";
    private static final String SCAN = "com.cyanogenmod.server.display.SCAN";
    private static final String STOP_SCAN = "com.cyanogenmod.server.display.STOP_SCAN";

    private Hashtable<String, RemoteDisplay> mDevices = new Hashtable<String, RemoteDisplay>();
    private boolean mScanning;
    private Handler mHandler;
    private RemoteDisplay mActiveDisplay;
    Stub mStub = new Stub();

    class Stub extends IRemoteDisplayAdapter.Stub {
        public void registerDisplayDevice(IDisplayDevice displayDevice, String name, int width,
                int height, float refreshRate, int flags, String address, boolean hidden) {
            synchronized (getSyncRoot()) {
                RemoteDisplay display = new RemoteDisplay();
                display.hidden = hidden;
                display.info = new DisplayDeviceInfo();
                display.displayDevice = displayDevice;
                mDevices.put(address, display);
                DisplayDeviceInfo info = display.info;
                info.name = name;
                info.width = width;
                info.height = height;
                info.refreshRate = refreshRate;
                info.flags = flags;
                info.type = Display.TYPE_UNKNOWN;
                info.address = address;
                info.touch = DisplayDeviceInfo.TOUCH_EXTERNAL;
                info.setAssumedDensityForExternalDisplay(width, height);
                handleSendStatusChangeBroadcast();
            }
        }

        @Override
        public void unregisterDisplayDevice(IDisplayDevice displayDevice) {
            synchronized (getSyncRoot()) {
                for (RemoteDisplay display: mDevices.values()) {
                    if (display.displayDevice == displayDevice) {
                        mDevices.remove(display.info.address);
                        if (mActiveDisplay == display) {
                            disconnectRemoteDisplay();
                        }
                        return;
                    }
                }
            }
        }

        @Override
        public void scanRemoteDisplays() {
            synchronized (getSyncRoot()) {
                mScanning = true;
                Intent scan = new Intent(SCAN);
                PackageManager pm = getContext().getPackageManager();
                List<ResolveInfo> services = pm.queryIntentServices(scan, 0);
                if (services == null)
                    return;
                for (ResolveInfo info : services) {
                    Intent intent = new Intent();
                    ComponentName name = new ComponentName(info.serviceInfo.packageName,
                            info.serviceInfo.name);
                    intent.setComponent(name);
                    intent.setAction(SCAN);
                    getContext().startService(intent);
                }
                handleSendStatusChangeBroadcast();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopScan();
                    }
                }, 10000L);
            }
        }

        @Override
        public void connectRemoteDisplay(String address) throws RemoteException {
            synchronized (getSyncRoot()) {
                RemoteDisplay display = mDevices.get(address);
                if (display == null) {
                    Log.e(TAG, "Could not find display?");
                    return;
                }
                if (display.remoteDisplayDevice != null) {
                    Log.e(TAG, "Display device is already connected?");
                    return;
                }
                Surface surface;
                try {
                    surface = display.displayDevice.createDisplaySurface();
                    if (surface == null) {
                        Log.e(TAG, "Returned null Surface");
                        return;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Error creating surface", e);
                    return;
                }
                IBinder displayToken = SurfaceControl.createDisplay(display.info.name, false);
                display.remoteDisplayDevice = new RemoteDisplayDevice(surface, displayToken,
                        display.info);
                mActiveDisplay = display;
                sendDisplayDeviceEventLocked(display.remoteDisplayDevice,
                        DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED);
                handleSendStatusChangeBroadcast();

                new Runnable() {
                    public void run() {
                        RemoteDisplay activeDisplay = mActiveDisplay;
                        if (activeDisplay == null)
                            return;
                        if (activeDisplay.displayDevice == null)
                            return;
                        if (activeDisplay.displayDevice.asBinder().isBinderAlive()) {
                            mHandler.postDelayed(this, 5000);
                            return;
                        }
                        synchronized (getSyncRoot()) {
                            stopActiveDisplayLocked();
                        }
                    }
                }.run();
            }
        }

        @Override
        public void disconnectRemoteDisplay() {
            synchronized (getSyncRoot()) {
                stopActiveDisplayLocked();
                handleSendStatusChangeBroadcast();
            }
        }

        @Override
        public void forgetRemoteDisplay(String address) {
            synchronized (getSyncRoot()) {
            }
        }

        @Override
        public void renameRemoteDisplay(String address, String alias) {
            synchronized (getSyncRoot()) {
            }
        }

        @Override
        public WifiDisplayStatus getRemoteDisplayStatus() {
            synchronized (getSyncRoot()) {
                ArrayList<WifiDisplay> availableDisplays = new ArrayList<WifiDisplay>();
                ArrayList<RemoteDisplay> list = new ArrayList<RemoteDisplay>(mDevices.values());
                for (RemoteDisplay display : list) {
                    if (!display.displayDevice.asBinder().isBinderAlive()) {
                        mDevices.remove(display.info.address);
                        if (mActiveDisplay == display) {
                            stopActiveDisplayLocked();
                        }
                        continue;
                    }
                    WifiDisplay add = new WifiDisplay(display.info.address, display.info.name,
                            display.info.name, display.hidden);
                    availableDisplays.add(add);
                }

                WifiDisplay active = null;
                if (mActiveDisplay != null)
                    active = new WifiDisplay(mActiveDisplay.info.address, mActiveDisplay.info.name,
                            mActiveDisplay.info.name);

                WifiDisplayStatus status = new WifiDisplayStatus(
                        WifiDisplayStatus.FEATURE_STATE_ON,
                        mScanning ? WifiDisplayStatus.SCAN_STATE_SCANNING
                                : WifiDisplayStatus.SCAN_STATE_NOT_SCANNING,
                        WifiDisplayStatus.DISPLAY_STATE_NOT_CONNECTED, active,
                        availableDisplays.toArray(new WifiDisplay[0]), WifiDisplay.EMPTY_ARRAY);
                return status;
            }
        }
    };

    private void stopActiveDisplayLocked() {
        if (mActiveDisplay != null && mActiveDisplay.remoteDisplayDevice != null) {
            try {
                mActiveDisplay.displayDevice.stop();
            } catch (Exception e) {
            }
            mActiveDisplay.remoteDisplayDevice.clearSurfaceLocked();
            sendDisplayDeviceEventLocked(mActiveDisplay.remoteDisplayDevice,
                    DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED);
            mActiveDisplay.remoteDisplayDevice = null;
        }
        mActiveDisplay = null;
    }

    public void stopScan() {
        mScanning = false;
        Intent scan = new Intent(SCAN);
        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> services = pm.queryIntentServices(scan, 0);
        if (services == null) {
            return;
        }
        for (ResolveInfo info : services) {
            Intent intent = new Intent();
            ComponentName name = new ComponentName(info.serviceInfo.packageName,
                    info.serviceInfo.name);
            intent.setComponent(name);
            intent.setAction(STOP_SCAN);
            getContext().startService(intent);
        }
        handleSendStatusChangeBroadcast();
    }

    // Runs on the handler.
    private void handleSendStatusChangeBroadcast() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final Intent intent = new Intent(
                        DisplayManager.ACTION_REMOTE_DISPLAY_STATUS_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                intent.putExtra(DisplayManager.EXTRA_REMOTE_DISPLAY_STATUS,
                        mStub.getRemoteDisplayStatus());

                // Send protected broadcast about wifi display status to
                // registered receivers.
                getContext().sendBroadcastAsUser(intent, UserHandle.ALL);
            }
        });
    }

    private class RemoteDisplayDevice extends DisplayDevice {
        private Surface surface;
        private DisplayDeviceInfo mInfo;

        public RemoteDisplayDevice(Surface surface, IBinder displayToken, DisplayDeviceInfo info) {
            super(RemoteDisplayAdapter.this, displayToken);
            this.surface = surface;
            this.mInfo = info;
        }

        public void clearSurfaceLocked() {
            surface = null;
            sendTraversalRequestLocked();
        }

        @Override
        public void performTraversalInTransactionLocked() {
            setSurfaceInTransactionLocked(surface);
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            synchronized (getSyncRoot()) {
                return mInfo;
            }
        }
    }

    public RemoteDisplayAdapter(DisplayManagerService.SyncRoot syncRoot, Context context,
            Handler handler, Listener listener, PersistentDataStore persistentDataStore) {
        super(syncRoot, context, handler, listener, TAG);
        this.mHandler = new Handler(handler.getLooper());
    }
}
